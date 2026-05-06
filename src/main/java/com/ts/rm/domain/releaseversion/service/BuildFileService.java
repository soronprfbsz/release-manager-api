package com.ts.rm.domain.releaseversion.service;

import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.file.BuildZipValidator;
import com.ts.rm.global.file.ZipExtractUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 빌드 ZIP 파일 업로드 서비스.
 *
 * <p>책임:
 * <ul>
 *   <li>빌드 버전 검증 (build_version &gt; 0 인 ReleaseVersion 만 허용)</li>
 *   <li>업로드된 ZIP 의 루트 디렉토리 검증 (web/, engine/ 만)</li>
 *   <li>지정된 빌드 베이스 경로 아래에 ZIP 압축 해제</li>
 * </ul>
 *
 * <p>버전 자체의 생성은 {@link ReleaseVersionService#createBuild} 가 담당하며 본 서비스는
 * 이미 생성된 빌드 행에 파일을 적재하는 역할로 분리되어 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildFileService {

    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseFileRepository releaseFileRepository;
    private final ReleaseVersionFileSystemService fileSystemService;
    private final ReleaseVersionService releaseVersionService;

    /**
     * 빌드 ZIP 업로드 결과
     *
     * @param buildVersionId 빌드 버전 ID
     * @param uploadedFileCount 추출된 파일 수
     */
    public record UploadResult(Long buildVersionId, int uploadedFileCount) {}

    /**
     * 빌드 버전 생성과 ZIP 업로드를 한 번에 처리하는 오케스트레이터.
     *
     * <p>흐름:
     * <ol>
     *   <li>{@link ReleaseVersionService#createBuild} 호출 → 빌드 행 생성</li>
     *   <li>{@code zipPath} 가 null 이 아니면 {@link #uploadBuildZip} 으로 검증/추출/등록</li>
     *   <li>응답에 업로드된 파일 개수 포함하여 반환</li>
     * </ol>
     *
     * <p>두 단계가 같은 트랜잭션 안에서 수행되므로 ZIP 업로드 실패 시 빌드 행 생성도 롤백된다.
     *
     * <p>대용량 ZIP(수백 MB ~ GB) 을 힙에 올리지 않도록 byte 배열이 아닌 디스크 경로(temp file)
     * 를 입력으로 받는다. 컨트롤러는 multipart 를 직접 디스크로 전송한 뒤 그 경로를 넘긴다.
     *
     * @param baseVersionId  빌드 원본 버전 ID
     * @param request        빌드 생성 요청 (comment, buildVersion?)
     * @param zipPath        업로드된 ZIP 파일의 디스크 경로 (null 이면 빌드만 생성)
     * @param createdByEmail 생성자 이메일
     * @return CreateBuildResponse (uploadedFileCount 포함)
     */
    @Transactional
    public ReleaseVersionDto.CreateBuildResponse createBuildWithZip(
            Long baseVersionId,
            ReleaseVersionDto.CreateBuildRequest request,
            Path zipPath,
            String createdByEmail) {

        // 1. 빌드 버전 행 생성
        ReleaseVersionDto.CreateBuildResponse buildResponse =
                releaseVersionService.createBuild(baseVersionId, request, createdByEmail);

        // 2. ZIP 이 있으면 추출/등록
        int uploadedCount = 0;
        if (zipPath != null) {
            UploadResult uploadResult = uploadBuildZip(buildResponse.buildVersionId(), zipPath, createdByEmail);
            uploadedCount = uploadResult.uploadedFileCount();
        }

        // 3. 파일 개수가 enrich 된 응답 반환
        return new ReleaseVersionDto.CreateBuildResponse(
                buildResponse.buildVersionId(),
                buildResponse.version(),
                buildResponse.buildVersion(),
                buildResponse.fullVersion(),
                uploadedCount
        );
    }

    /**
     * 빌드 ZIP 재업로드 (교체 시맨틱).
     *
     * <p>기존 빌드 디렉토리 산출물을 삭제한 뒤 새 ZIP 으로 다시 업로드한다.
     * 과거 빌드에서 생성된 ReleaseFile rows 가 있으면 호환성을 위해 함께 정리한다.
     *
     * @param buildVersionId 업로드 대상 빌드 버전 ID
     * @param zipPath        새 ZIP 파일 경로 (null 불가)
     * @param uploadedByEmail 업로드자 이메일
     * @return UploadBuildZipResponse (uploadedFileCount 포함)
     */
    @Transactional
    public ReleaseVersionDto.UploadBuildZipResponse replaceBuildZip(
            Long buildVersionId, Path zipPath, String uploadedByEmail) {

        if (zipPath == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "재업로드할 ZIP 파일이 비어 있습니다.");
        }

        // 1. 빌드 검증
        ReleaseVersion build = releaseVersionRepository.findById(buildVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                        "빌드 버전을 찾을 수 없습니다: " + buildVersionId));
        if (!build.isBuild()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "빌드가 아닌 버전에는 빌드 ZIP 을 재업로드할 수 없습니다.");
        }

        // 2. 기존 ReleaseFile rows 삭제
        List<ReleaseFile> existing = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(buildVersionId);
        if (!existing.isEmpty()) {
            releaseFileRepository.deleteAll(existing);
            log.info("기존 빌드 파일 row 삭제 - buildVersionId: {}, count: {}", buildVersionId, existing.size());
        }

        // 3. 빌드 디렉토리 삭제 (uploadBuildZip 가 다시 생성)
        fileSystemService.deleteBuildDirectory(build);

        // 4. 새 ZIP 업로드
        UploadResult result = uploadBuildZip(buildVersionId, zipPath, uploadedByEmail);

        return new ReleaseVersionDto.UploadBuildZipResponse(
                buildVersionId,
                build.getFullVersion(),
                result.uploadedFileCount()
        );
    }

    /**
     * 빌드 ZIP 업로드 처리.
     *
     * <p>처리 단계:
     * <ol>
     *   <li>buildVersionId 로 빌드 행 조회 + 빌드 여부 검증</li>
     *   <li>{@link BuildZipValidator#validate} 로 ZIP 루트 구조 검증 (web/engine 만)</li>
     *   <li>ZIP 을 빌드 디렉토리 아래에 압축 해제</li>
     * </ol>
     *
     * @param buildVersionId 업로드 대상 빌드 버전 ID (build_version &gt; 0 이어야 함)
     * @param zipPath        ZIP 파일 경로 (디스크 저장된 multipart temp file)
     * @param uploadedByEmail 업로드자 이메일 (description 에 기록)
     * @return 업로드 결과
     */
    @Transactional
    public UploadResult uploadBuildZip(Long buildVersionId, Path zipPath, String uploadedByEmail) {
        long zipSize;
        try {
            zipSize = Files.size(zipPath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                    "ZIP 파일 크기 조회 실패: " + e.getMessage());
        }
        log.info("빌드 ZIP 업로드 요청 - buildVersionId: {}, sizeBytes: {}", buildVersionId, zipSize);

        // 1. 빌드 버전 조회 + 검증
        ReleaseVersion build = releaseVersionRepository.findById(buildVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                        "빌드 버전을 찾을 수 없습니다: " + buildVersionId));
        if (!build.isBuild()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "빌드가 아닌 버전에는 빌드 ZIP 을 업로드할 수 없습니다.");
        }
        ReleaseVersion baseVersion = build.getBuildBaseVersion();
        if (baseVersion == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "빌드의 원본 버전이 없습니다 (build_base_version_id 누락).");
        }

        // 2. ZIP 검증 (web/engine 외 거부) — 스트리밍
        BuildZipValidator.validate(zipPath);

        // 3. 빌드 디렉토리 준비 + 압축 해제
        Path buildBasePath = fileSystemService.resolveBuildBasePath(build);
        try {
            Files.createDirectories(buildBasePath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "빌드 디렉토리 준비 실패: " + e.getMessage());
        }

        List<ZipExtractUtil.ExtractedFileInfo> extracted = ZipExtractUtil.extract(zipPath, buildBasePath);

        if (extracted.isEmpty()) {
            log.warn("빌드 ZIP 에서 추출된 파일이 없음 - buildVersionId: {}", buildVersionId);
            return new UploadResult(buildVersionId, 0);
        }

        int uploadedFileCount = extracted.size();
        log.info("빌드 ZIP 업로드 완료 - buildVersionId: {}, 추출된 파일 수: {} (ReleaseFile row 저장 생략)",
                buildVersionId, uploadedFileCount);
        return new UploadResult(buildVersionId, uploadedFileCount);
    }
}
