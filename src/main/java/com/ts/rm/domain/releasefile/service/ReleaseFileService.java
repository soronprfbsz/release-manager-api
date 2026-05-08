package com.ts.rm.domain.releasefile.service;

import com.ts.rm.domain.common.service.FileStorageService;
import com.ts.rm.domain.releasefile.dto.ReleaseFileDto;
import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.mapper.ReleaseFileDtoMapper;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.service.ReleaseVersionFileSystemService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.file.FileContentUtil;
import com.ts.rm.global.file.StreamingZipUtil;
import com.ts.rm.global.file.StreamingZipUtil.ZipFileEntry;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * ReleaseFile 오케스트레이터 서비스
 *
 * <p>파일 CRUD, 조회, 다운로드를 담당하며 업로드는 {@link ReleaseFileUploadService}에 위임합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseFileService {

    private final ReleaseFileRepository releaseFileRepository;
    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseFileDtoMapper mapper;
    private final FileStorageService fileStorageService;
    private final ReleaseFileUploadService uploadService;
    private final ReleaseVersionFileSystemService fileSystemService;

    /**
     * 릴리즈 파일 메타데이터 생성 (물리적 파일 없이)
     *
     * @param request 생성 요청 정보
     * @return 생성된 파일 정보
     */
    @Transactional
    public ReleaseFileDto.DetailResponse createReleaseFile(ReleaseFileDto.CreateRequest request) {
        log.info("Creating release file: {} for versionId: {}", request.fileName(),
                request.releaseVersionId());

        ReleaseVersion releaseVersion = findReleaseVersionById(request.releaseVersionId());

        // fileCategory와 subCategory는 request에서 명시적으로 제공되어야 함
        // null이면 기본값 사용
        FileCategory fileCategory = request.fileCategory() != null
                ? FileCategory.fromCode(request.fileCategory())
                : FileCategory.DATABASE; // 기본값

        String subCategory = request.subCategory();

        ReleaseFile releaseFile = ReleaseFile.builder()
                .releaseVersion(releaseVersion)
                .fileCategory(fileCategory)
                .subCategory(subCategory)
                .fileName(request.fileName())
                .filePath(request.filePath())
                .fileSize(request.fileSize())
                .checksum(request.checksum())
                .executionOrder(request.executionOrder())
                .description(request.description())
                .build();

        ReleaseFile savedReleaseFile = releaseFileRepository.save(releaseFile);

        log.info("Release file created successfully with id: {}",
                savedReleaseFile.getReleaseFileId());
        return mapper.toDetailResponse(savedReleaseFile);
    }

    public ReleaseFileDto.DetailResponse getReleaseFileById(Long releaseFileId) {
        ReleaseFile releaseFile = findReleaseFileById(releaseFileId);
        return mapper.toDetailResponse(releaseFile);
    }

    public List<ReleaseFileDto.SimpleResponse> getReleaseFilesByVersion(Long versionId) {
        findReleaseVersionById(versionId);

        List<ReleaseFile> releaseFiles = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(versionId);
        return mapper.toSimpleResponseList(releaseFiles);
    }

    public List<ReleaseFileDto.SimpleResponse> getReleaseFilesByVersionAndCategory(Long versionId,
            FileCategory fileCategory) {
        findReleaseVersionById(versionId);

        List<ReleaseFile> releaseFiles = releaseFileRepository
                .findByReleaseVersion_ReleaseVersionIdAndFileCategoryOrderByExecutionOrderAsc(versionId, fileCategory);
        return mapper.toSimpleResponseList(releaseFiles);
    }

    public List<ReleaseFileDto.SimpleResponse> getReleaseFilesByVersionAndSubCategory(Long versionId,
            String subCategory) {
        findReleaseVersionById(versionId);

        List<ReleaseFile> releaseFiles = releaseFileRepository
                .findByReleaseVersion_ReleaseVersionIdAndSubCategoryOrderByExecutionOrderAsc(versionId, subCategory);
        return mapper.toSimpleResponseList(releaseFiles);
    }

    public ReleaseFileDto.DetailResponse getReleaseFileByPath(String filePath) {
        ReleaseFile releaseFile = releaseFileRepository.findByFilePath(filePath)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATCH_FILE_NOT_FOUND));
        return mapper.toDetailResponse(releaseFile);
    }

    public List<ReleaseFileDto.SimpleResponse> getReleaseFilesBetweenVersions(String projectId,
            String fromVersion, String toVersion) {

        List<ReleaseFile> releaseFiles = releaseFileRepository
                .findReleaseFilesBetweenVersions(projectId, fromVersion, toVersion);
        return mapper.toSimpleResponseList(releaseFiles);
    }

    @Transactional
    public ReleaseFileDto.DetailResponse updateReleaseFile(Long releaseFileId,
            ReleaseFileDto.UpdateRequest request) {
        log.info("Updating release file with releaseFileId: {}", releaseFileId);

        ReleaseFile releaseFile = findReleaseFileById(releaseFileId);

        if (request.description() != null) {
            releaseFile.setDescription(request.description());
        }
        if (request.executionOrder() != null) {
            releaseFile.setExecutionOrder(request.executionOrder());
        }

        log.info("Release file updated successfully with releaseFileId: {}", releaseFileId);
        return mapper.toDetailResponse(releaseFile);
    }

    @Transactional
    public void deleteReleaseFile(Long releaseFileId) {
        log.info("Deleting release file with releaseFileId: {}", releaseFileId);

        ReleaseFile releaseFile = findReleaseFileById(releaseFileId);
        releaseFileRepository.delete(releaseFile);

        log.info("Release file deleted successfully with releaseFileId: {}", releaseFileId);
    }

    /**
     * 릴리즈 파일 업로드 (위임)
     *
     * @param versionId 릴리즈 버전 ID
     * @param files     업로드할 파일 목록
     * @param request   업로드 요청 정보
     * @return 생성된 파일 정보 목록
     */
    @Transactional
    public List<ReleaseFileDto.DetailResponse> uploadReleaseFiles(Long versionId,
            List<MultipartFile> files, ReleaseFileDto.UploadRequest request) {
        return uploadService.uploadReleaseFiles(versionId, files, request);
    }

    public Resource downloadReleaseFile(Long releaseFileId) {
        log.info("Downloading release file with releaseFileId: {}", releaseFileId);

        ReleaseFile releaseFile = findReleaseFileById(releaseFileId);

        Resource resource = fileStorageService.loadFile(releaseFile.getFilePath());

        log.info("Release file downloaded successfully: {}", releaseFile.getFileName());
        return resource;
    }

    /**
     * 릴리즈 파일 내용 조회
     *
     * <p>텍스트 파일은 UTF-8 문자열로, 바이너리 파일은 Base64 인코딩하여 반환합니다.
     *
     * @param releaseFileId 릴리즈 파일 ID
     * @return 파일 내용 응답 (MIME 타입, 바이너리 여부, 내용 포함)
     * @throws BusinessException 파일을 찾을 수 없거나 읽기 실패 시
     */
    public ReleaseFileDto.FileContentResponse getFileContent(Long releaseFileId) {
        log.info("릴리즈 파일 내용 조회 - releaseFileId: {}", releaseFileId);

        ReleaseFile releaseFile = findReleaseFileById(releaseFileId);

        // 절대 경로 조회
        Path filePath = fileStorageService.getAbsolutePath(releaseFile.getFilePath());

        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND,
                    "파일을 찾을 수 없습니다: " + releaseFile.getFileName());
        }

        // 공통 유틸리티로 파일 내용 조회
        FileContentUtil.FileContentResult result = FileContentUtil.readFileContent(filePath);

        log.info("릴리즈 파일 내용 조회 완료 - releaseFileId: {}, fileName: {}, size: {} bytes, isBinary: {}",
                releaseFileId, result.fileName(), result.size(), result.isBinary());

        return new ReleaseFileDto.FileContentResponse(
                releaseFileId,
                releaseFile.getFilePath(),
                result.fileName(),
                result.size(),
                result.mimeType(),
                result.isBinary(),
                result.content()
        );
    }

    /**
     * 버전별 파일을 스트리밍 방식으로 ZIP 압축
     *
     * <p>메모리에 전체 ZIP을 생성하지 않고 OutputStream에 직접 스트리밍합니다.
     * 메모리 사용량이 O(1)로 대용량 파일도 안전하게 처리 가능합니다.
     *
     * @param versionId    릴리즈 버전 ID
     * @param outputStream 출력 스트림 (HttpServletResponse.getOutputStream())
     * @throws BusinessException 파일이 없거나 압축 실패 시
     */
    public void streamVersionFilesAsZip(Long versionId, OutputStream outputStream) {
        log.info("버전별 파일 스트리밍 다운로드 요청 - versionId: {}", versionId);

        ReleaseVersion releaseVersion = findReleaseVersionById(versionId);

        // 빌드 버전은 release_file row 가 없으므로 디렉토리 walk 로 직접 스트리밍
        if (releaseVersion.isBuild()) {
            List<ZipFileEntry> buildEntries = buildZipEntriesFromBuildDirectory(releaseVersion);
            validateFilesExist(buildEntries);
            log.info("빌드 스트리밍 ZIP 압축 시작 - 버전: {}, 파일 개수: {}",
                    releaseVersion.getFullVersion(), buildEntries.size());
            StreamingZipUtil.compressFilesToStream(outputStream, buildEntries);
            log.info("빌드 {} 스트리밍 압축 완료 - {} 개 파일",
                    releaseVersion.getFullVersion(), buildEntries.size());
            return;
        }

        List<ReleaseFile> releaseFiles = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(versionId);

        if (releaseFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "버전 " + releaseVersion.getVersion() + "에 파일이 없습니다");
        }

        List<ZipFileEntry> zipEntries = buildZipEntries(releaseFiles);

        // 스트리밍 시작 전 파일 존재 여부 검증 (응답 헤더 설정 후 예외 발생 방지)
        validateFilesExist(zipEntries);

        log.info("스트리밍 ZIP 압축 시작 - 버전: {}, 파일 개수: {}",
                releaseVersion.getVersion(), zipEntries.size());

        // 스트리밍 방식으로 압축 (메모리 효율적)
        StreamingZipUtil.compressFilesToStream(outputStream, zipEntries);

        log.info("버전 {} 스트리밍 압축 완료 - {} 개 파일",
                releaseVersion.getVersion(), releaseFiles.size());
    }

    /**
     * ReleaseFile 목록을 ZipFileEntry 목록으로 변환
     */
    private List<ZipFileEntry> buildZipEntries(List<ReleaseFile> releaseFiles) {
        List<ZipFileEntry> zipEntries = new ArrayList<>();

        for (ReleaseFile file : releaseFiles) {
            Path sourcePath = fileStorageService.getAbsolutePath(file.getFilePath());

            String zipEntryPath;
            if (file.getFileCategory() != null) {
                // ZIP 경로는 소문자로 변환
                String categoryPath = file.getFileCategory().getCode().toLowerCase();
                if (file.getSubCategory() != null && !file.getSubCategory().isEmpty()) {
                    zipEntryPath = String.format("%s/%s/%s",
                            categoryPath, file.getSubCategory().toLowerCase(), file.getFileName());
                } else {
                    zipEntryPath = String.format("%s/%s", categoryPath, file.getFileName());
                }
            } else {
                zipEntryPath = file.getFileName();
            }

            log.debug("ZIP 엔트리 추가 - 카테고리: {}, 하위카테고리: {}, 파일: {}, ZIP경로: {}",
                    file.getFileCategory(), file.getSubCategory(), file.getFileName(), zipEntryPath);

            zipEntries.add(new ZipFileEntry(sourcePath, zipEntryPath));
        }

        return zipEntries;
    }

    /**
     * 빌드 디렉토리를 walk 하여 ZipFileEntry 목록 생성.
     *
     * <p>빌드 버전은 release_file row 를 저장하지 않으므로
     * 빌드 베이스 디렉토리를 직접 탐색하여 엔트리를 구성한다.
     * ZIP 내 경로는 빌드 베이스 디렉토리 기준 상대경로 (예: web/app.war).
     *
     * @param buildVersion 빌드 버전 엔티티
     * @return ZIP 엔트리 목록 (비어 있으면 BusinessException)
     */
    private List<ZipFileEntry> buildZipEntriesFromBuildDirectory(ReleaseVersion buildVersion) {
        Path buildBasePath = fileSystemService.resolveBuildBasePath(buildVersion);
        if (!Files.isDirectory(buildBasePath)) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "빌드 " + buildVersion.getFullVersion() + "에 파일이 없습니다");
        }
        List<ZipFileEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(buildBasePath)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                // ZIP 내 경로는 빌드 베이스 디렉토리 기준 상대경로 (역슬래시 정규화)
                String rel = buildBasePath.relativize(p).toString().replace('\\', '/');
                entries.add(new ZipFileEntry(p, rel));
            });
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "빌드 디렉토리 탐색 실패: " + e.getMessage());
        }
        if (entries.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "빌드 " + buildVersion.getFullVersion() + "에 파일이 없습니다");
        }
        return entries;
    }

    /**
     * ZIP 엔트리 파일들의 존재 여부 검증
     *
     * <p>스트리밍 시작 전에 모든 파일이 존재하는지 확인합니다.
     * Content-Type 설정 후 예외 발생을 방지하기 위한 사전 검증입니다.
     *
     * @param zipEntries ZIP 엔트리 목록
     * @throws BusinessException 파일이 존재하지 않을 경우
     */
    private void validateFilesExist(List<ZipFileEntry> zipEntries) {
        List<String> missingFiles = new ArrayList<>();

        for (ZipFileEntry entry : zipEntries) {
            if (!Files.exists(entry.sourcePath())) {
                missingFiles.add(entry.zipEntryPath());
                log.warn("파일이 존재하지 않습니다: {}", entry.sourcePath());
            }
        }

        if (!missingFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    String.format("압축할 파일이 존재하지 않습니다. 누락된 파일 수: %d - %s",
                            missingFiles.size(),
                            missingFiles.size() <= 5
                                ? String.join(", ", missingFiles)
                                : String.join(", ", missingFiles.subList(0, 5)) + "..."
                    ));
        }
    }

    /**
     * 버전별 파일의 압축 전 총 크기 계산
     *
     * <p>프론트엔드에서 진행률 표시를 위해 사용합니다.
     * 실제 압축된 크기는 압축률에 따라 다르지만, 대략적인 진행률 표시에 충분합니다.
     *
     * @param versionId 릴리즈 버전 ID
     * @return 압축 전 총 크기 (바이트)
     * @throws BusinessException 파일이 없거나 크기 계산 실패 시
     */
    public long calculateUncompressedSize(Long versionId) {
        log.debug("압축 전 크기 계산 시작 - versionId: {}", versionId);

        ReleaseVersion releaseVersion = findReleaseVersionById(versionId);

        // 빌드 버전은 release_file row 가 없으므로 디렉토리 walk 로 크기 합산
        if (releaseVersion.isBuild()) {
            Path buildBasePath = fileSystemService.resolveBuildBasePath(releaseVersion);
            if (!Files.isDirectory(buildBasePath)) {
                throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "빌드 " + releaseVersion.getFullVersion() + "에 파일이 없습니다");
            }
            try (Stream<Path> stream = Files.walk(buildBasePath)) {
                long totalSize = stream
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> {
                            try {
                                return Files.size(p);
                            } catch (IOException e) {
                                log.warn("빌드 파일 크기 조회 실패 - {}: {}", p, e.getMessage());
                                return 0L;
                            }
                        })
                        .sum();
                log.info("빌드 압축 전 총 크기 계산 완료 - versionId: {}, totalSize: {} bytes ({} MB)",
                        versionId, totalSize, totalSize / (1024.0 * 1024.0));
                return totalSize;
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "빌드 디렉토리 크기 계산 실패: " + e.getMessage());
            }
        }

        List<ReleaseFile> releaseFiles = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(versionId);

        if (releaseFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "버전 " + releaseVersion.getVersion() + "에 파일이 없습니다");
        }

        long totalSize = 0L;
        for (ReleaseFile file : releaseFiles) {
            Path sourcePath = fileStorageService.getAbsolutePath(file.getFilePath());
            try {
                if (Files.exists(sourcePath) && Files.isRegularFile(sourcePath)) {
                    long fileSize = Files.size(sourcePath);
                    totalSize += fileSize;
                    log.debug("파일 크기 추가 - {}: {} bytes", file.getFileName(), fileSize);
                } else {
                    log.warn("파일을 찾을 수 없음 - {}: {}", file.getFileName(), sourcePath);
                }
            } catch (IOException e) {
                log.error("파일 크기 계산 실패 - {}: {}", file.getFileName(), e.getMessage());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "파일 크기 계산 실패: " + file.getFileName());
            }
        }

        log.info("압축 전 총 크기 계산 완료 - versionId: {}, totalSize: {} bytes ({} MB)",
                versionId, totalSize, totalSize / (1024.0 * 1024.0));

        return totalSize;
    }

    /**
     * 버전별 ZIP 파일명 생성
     *
     * @param versionId 릴리즈 버전 ID
     * @return ZIP 파일명 (예: release_1.1.0.zip, 핫픽스: release_1.1.0.1.zip)
     */
    public String getVersionZipFileName(Long versionId) {
        ReleaseVersion releaseVersion = findReleaseVersionById(versionId);
        return String.format("release_%s.zip", releaseVersion.getFullVersion());
    }

    /**
     * 릴리즈 버전 조회
     *
     * @param versionId 버전 ID
     * @return ReleaseVersion 엔티티
     * @throws BusinessException 버전을 찾을 수 없을 경우
     */
    private ReleaseVersion findReleaseVersionById(Long versionId) {
        return releaseVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND));
    }

    /**
     * 릴리즈 파일 조회
     *
     * @param releaseFileId 파일 ID
     * @return ReleaseFile 엔티티
     * @throws BusinessException 파일을 찾을 수 없을 경우
     */
    private ReleaseFile findReleaseFileById(Long releaseFileId) {
        return releaseFileRepository.findById(releaseFileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATCH_FILE_NOT_FOUND));
    }
}
