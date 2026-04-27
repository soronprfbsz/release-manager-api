package com.ts.rm.domain.releaseversion.service;

import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.file.BuildZipValidator;
import com.ts.rm.global.file.FileChecksumUtil;
import com.ts.rm.global.file.ZipExtractUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 빌드 ZIP 파일 업로드 서비스.
 *
 * <p>책임:
 * <ul>
 *   <li>빌드 버전 검증 (build_version &gt; 0 인 ReleaseVersion 만 허용)</li>
 *   <li>업로드된 ZIP 의 루트 디렉토리 검증 (web/, engine/, etc/ 만)</li>
 *   <li>지정된 빌드 베이스 경로 아래에 ZIP 압축 해제</li>
 *   <li>각 추출 파일에 대응하는 {@link ReleaseFile} 엔티티 생성/저장</li>
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

    @Value("${app.release.base-path:data/release-manager}")
    private String baseReleasePath;

    /**
     * 빌드 ZIP 업로드 결과
     *
     * @param buildVersionId 빌드 버전 ID
     * @param createdFiles   생성된 ReleaseFile 행
     */
    public record UploadResult(Long buildVersionId, List<ReleaseFile> createdFiles) {}

    /**
     * 빌드 버전 생성과 ZIP 업로드를 한 번에 처리하는 오케스트레이터.
     *
     * <p>흐름:
     * <ol>
     *   <li>{@link ReleaseVersionService#createBuild} 호출 → 빌드 행 생성</li>
     *   <li>{@code zipBytes} 가 비어있지 않으면 {@link #uploadBuildZip} 으로 검증/추출/등록</li>
     *   <li>응답에 업로드된 파일 개수 포함하여 반환</li>
     * </ol>
     *
     * <p>두 단계가 같은 트랜잭션 안에서 수행되므로 ZIP 업로드 실패 시 빌드 행 생성도 롤백된다.
     *
     * @param baseVersionId  빌드 원본 버전 ID
     * @param request        빌드 생성 요청 (comment, buildVersion?)
     * @param zipBytes       업로드 ZIP 바이트 (null/빈 배열이면 빌드만 생성)
     * @param createdByEmail 생성자 이메일
     * @return CreateBuildResponse (uploadedFileCount 포함)
     */
    @Transactional
    public ReleaseVersionDto.CreateBuildResponse createBuildWithZip(
            Long baseVersionId,
            ReleaseVersionDto.CreateBuildRequest request,
            byte[] zipBytes,
            String createdByEmail) {

        // 1. 빌드 버전 행 생성
        ReleaseVersionDto.CreateBuildResponse buildResponse =
                releaseVersionService.createBuild(baseVersionId, request, createdByEmail);

        // 2. ZIP 이 있으면 추출/등록
        int uploadedCount = 0;
        if (zipBytes != null && zipBytes.length > 0) {
            UploadResult uploadResult = uploadBuildZip(buildResponse.buildVersionId(), zipBytes, createdByEmail);
            uploadedCount = uploadResult.createdFiles().size();
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
     * <p>기존 빌드의 ReleaseFile rows + 빌드 디렉토리 산출물을 모두 삭제한 뒤
     * 새 ZIP 으로 다시 업로드한다. 동일 트랜잭션에서 수행되므로 신규 업로드가 실패하면
     * 삭제도 롤백된다.
     *
     * @param buildVersionId 업로드 대상 빌드 버전 ID
     * @param zipBytes       새 ZIP 바이트 (빈 배열 불가)
     * @param uploadedByEmail 업로드자 이메일
     * @return UploadBuildZipResponse (uploadedFileCount 포함)
     */
    @Transactional
    public ReleaseVersionDto.UploadBuildZipResponse replaceBuildZip(
            Long buildVersionId, byte[] zipBytes, String uploadedByEmail) {

        if (zipBytes == null || zipBytes.length == 0) {
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
        UploadResult result = uploadBuildZip(buildVersionId, zipBytes, uploadedByEmail);

        return new ReleaseVersionDto.UploadBuildZipResponse(
                buildVersionId,
                build.getFullVersion(),
                result.createdFiles().size()
        );
    }

    /**
     * 빌드 ZIP 업로드 처리.
     *
     * <p>처리 단계:
     * <ol>
     *   <li>buildVersionId 로 빌드 행 조회 + 빌드 여부 검증</li>
     *   <li>{@link BuildZipValidator#validate} 로 ZIP 루트 구조 검증 (web/engine/etc 만)</li>
     *   <li>ZIP 을 빌드 디렉토리 아래에 압축 해제</li>
     *   <li>추출된 각 파일에 대해 ReleaseFile 행 생성 (file_category 는 루트 디렉토리에서 결정)</li>
     * </ol>
     *
     * @param buildVersionId 업로드 대상 빌드 버전 ID (build_version &gt; 0 이어야 함)
     * @param zipBytes       ZIP 파일 바이트 배열
     * @param uploadedByEmail 업로드자 이메일 (description 에 기록)
     * @return 업로드 결과
     */
    @Transactional
    public UploadResult uploadBuildZip(Long buildVersionId, byte[] zipBytes, String uploadedByEmail) {
        log.info("빌드 ZIP 업로드 요청 - buildVersionId: {}, sizeBytes: {}",
                buildVersionId, zipBytes != null ? zipBytes.length : 0);

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

        // 2. ZIP 검증 (web/engine/etc 외 거부)
        BuildZipValidator.validate(zipBytes);

        // 3. 빌드 디렉토리 준비 + 압축 해제
        Path buildBasePath = fileSystemService.resolveBuildBasePath(baseVersion, build.getBuildVersion());
        try {
            Files.createDirectories(buildBasePath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "빌드 디렉토리 준비 실패: " + e.getMessage());
        }

        List<ZipExtractUtil.ExtractedFileInfo> extracted = ZipExtractUtil.extract(
                new ByteArrayInputStream(zipBytes), buildBasePath);

        if (extracted.isEmpty()) {
            log.warn("빌드 ZIP 에서 추출된 파일이 없음 - buildVersionId: {}", buildVersionId);
            return new UploadResult(buildVersionId, List.of());
        }

        // 4. ReleaseFile 행 생성
        int maxOrder = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(buildVersionId)
                .stream()
                .mapToInt(ReleaseFile::getExecutionOrder)
                .max()
                .orElse(0);
        int order = maxOrder + 1;

        Path baseRoot = Paths.get(baseReleasePath).toAbsolutePath().normalize();
        List<ReleaseFile> created = new ArrayList<>();

        for (ZipExtractUtil.ExtractedFileInfo info : extracted) {
            String topLevel = topLevelOf(info.relativePath());
            FileCategory category = mapTopLevelToCategory(topLevel);
            if (category == null) {
                // BuildZipValidator 가 이미 막아준 케이스라 도달 불가하지만 안전망
                log.warn("알 수 없는 루트 디렉토리: {} (skip)", topLevel);
                continue;
            }

            String relativeFromBase = relativizeFromBase(baseRoot, info.absolutePath());
            String checksum;
            try {
                checksum = FileChecksumUtil.calculateChecksum(info.absolutePath());
            } catch (IOException e) {
                log.warn("체크섬 계산 실패 (계속 진행): {}", info.absolutePath(), e);
                checksum = null;
            }

            ReleaseFile file = ReleaseFile.builder()
                    .releaseVersion(build)
                    .fileType(determineFileType(info.fileName()))
                    .fileCategory(category)
                    .subCategory(null)
                    .fileName(info.fileName())
                    .filePath(relativeFromBase)
                    .fileSize(info.fileSize())
                    .checksum(checksum)
                    .executionOrder(order++)
                    .description(uploadedByEmail != null
                            ? uploadedByEmail + " 가 빌드 ZIP 으로 업로드"
                            : "빌드 ZIP 업로드")
                    .build();

            created.add(releaseFileRepository.save(file));
        }

        log.info("빌드 ZIP 업로드 완료 - buildVersionId: {}, 추가된 파일 수: {}",
                buildVersionId, created.size());
        return new UploadResult(buildVersionId, created);
    }

    /**
     * 추출된 파일의 baseReleasePath 기준 상대 경로를 반환.
     *
     * <p>예: {@code /app/data/.../builds/260427/web/foo.war} → {@code versions/.../builds/260427/web/foo.war}
     */
    private String relativizeFromBase(Path baseRoot, Path absolute) {
        Path absNormalized = absolute.toAbsolutePath().normalize();
        if (absNormalized.startsWith(baseRoot)) {
            return baseRoot.relativize(absNormalized).toString().replace('\\', '/');
        }
        // baseRoot 기준이 아닌 경우 (드물지만) 안전하게 절대 경로 그대로 저장
        log.warn("base 경로 밖에 위치한 파일: {} (baseRoot={})", absNormalized, baseRoot);
        return absNormalized.toString().replace('\\', '/');
    }

    /**
     * ZIP 내부 상대 경로의 최상위 디렉토리명 반환 (예: "web/sub/foo.war" → "web").
     */
    private String topLevelOf(String zipRelativePath) {
        if (zipRelativePath == null) return "";
        String normalized = zipRelativePath.replace('\\', '/');
        int slash = normalized.indexOf('/');
        return slash < 0 ? normalized : normalized.substring(0, slash);
    }

    /**
     * 루트 디렉토리명 → FileCategory 매핑.
     */
    private FileCategory mapTopLevelToCategory(String topLevel) {
        if ("web".equals(topLevel)) return FileCategory.WEB;
        if ("engine".equals(topLevel)) return FileCategory.ENGINE;
        if ("etc".equals(topLevel)) return FileCategory.ETC;
        return null;
    }

    /**
     * 파일 확장자 기반 fileType 결정 (대문자, 확장자 없으면 "OTHER").
     */
    private String determineFileType(String fileName) {
        if (fileName == null) return "OTHER";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "OTHER";
        return fileName.substring(dot + 1).toUpperCase();
    }
}
