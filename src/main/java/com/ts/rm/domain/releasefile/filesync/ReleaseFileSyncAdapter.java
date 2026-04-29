package com.ts.rm.domain.releasefile.filesync;

import static com.ts.rm.global.util.FileTypeExtractor.extractFileType;
import static com.ts.rm.global.util.MapExtractUtil.extractInteger;
import static com.ts.rm.global.util.MapExtractUtil.extractLong;
import static com.ts.rm.global.util.MapExtractUtil.extractString;

import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.domain.filesync.adapter.FileSyncAdapter;
import com.ts.rm.domain.filesync.dto.FileSyncMetadata;
import com.ts.rm.domain.filesync.enums.FileSyncTarget;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴리즈 파일 동기화 어댑터
 *
 * <p>ReleaseFile 도메인의 파일 동기화를 담당합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseFileSyncAdapter implements FileSyncAdapter {

    private final ReleaseFileRepository releaseFileRepository;
    private final ReleaseVersionRepository releaseVersionRepository;

    @Override
    public FileSyncTarget getTarget() {
        return FileSyncTarget.RELEASE_FILE;
    }

    @Override
    public String getBaseScanPath() {
        return "versions";
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileSyncMetadata> getRegisteredFiles(@Nullable String subPath) {
        List<ReleaseFile> files;

        if (subPath != null && !subPath.isEmpty()) {
            // filePath가 subPath로 시작하는 파일들 조회
            files = releaseFileRepository.findAll().stream()
                    .filter(f -> f.getFilePath() != null && f.getFilePath().startsWith(subPath))
                    .toList();
        } else {
            files = releaseFileRepository.findAll();
        }

        return files.stream()
                .map(this::toMetadata)
                .toList();
    }

    @Override
    @Transactional
    public Long registerFile(FileSyncMetadata metadata, @Nullable Map<String, Object> additionalData) {
        // additionalData에서 필수 정보 추출
        Long releaseVersionId = extractLong(additionalData, "releaseVersionId");
        if (releaseVersionId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "릴리즈 파일 등록에는 releaseVersionId가 필요합니다");
        }

        ReleaseVersion releaseVersion = releaseVersionRepository.findById(releaseVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                        "릴리즈 버전을 찾을 수 없습니다: " + releaseVersionId));

        // 파일 확장자에서 fileType 추출
        String fileType = extractFileType(metadata.getFileName());

        // 기본값 설정
        Integer executionOrder = extractInteger(additionalData, "executionOrder");
        if (executionOrder == null) {
            executionOrder = 99;
        }

        String description = extractString(additionalData, "description");
        FileCategory fileCategory = extractFileCategory(additionalData, "fileCategory");
        String subCategory = extractString(additionalData, "subCategory");

        ReleaseFile releaseFile = ReleaseFile.builder()
                .releaseVersion(releaseVersion)
                .fileType(fileType)
                .fileCategory(fileCategory)
                .subCategory(subCategory)
                .fileName(metadata.getFileName())
                .filePath(metadata.getFilePath())
                .fileSize(metadata.getFileSize())
                .checksum(metadata.getChecksum())
                .executionOrder(executionOrder)
                .description(description)
                .build();

        ReleaseFile saved = releaseFileRepository.save(releaseFile);
        log.info("릴리즈 파일 동기화 등록: {} (ID: {})", metadata.getFilePath(), saved.getReleaseFileId());

        return saved.getReleaseFileId();
    }

    @Override
    @Transactional
    public void updateMetadata(Long id, FileSyncMetadata newMetadata) {
        ReleaseFile releaseFile = releaseFileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND,
                        "릴리즈 파일을 찾을 수 없습니다: " + id));

        releaseFile.setFileSize(newMetadata.getFileSize());
        releaseFile.setChecksum(newMetadata.getChecksum());

        releaseFileRepository.save(releaseFile);
        log.info("릴리즈 파일 메타데이터 갱신: {} (ID: {})", newMetadata.getFilePath(), id);
    }

    @Override
    @Transactional
    public void deleteMetadata(Long id) {
        if (!releaseFileRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND,
                    "릴리즈 파일을 찾을 수 없습니다: " + id);
        }

        releaseFileRepository.deleteById(id);
        log.info("릴리즈 파일 메타데이터 삭제: ID {}", id);
    }

    @Override
    public List<String> getAllowedExtensions() {
        // 모든 파일 확장자 허용 (hotfix 등에 다양한 파일 형식 포함 가능)
        return List.of();
    }

    /**
     * 주어진 경로가 동기화 대상으로 유효한지 확인
     *
     * <p>해당 경로에 대응하는 ReleaseVersion이 DB에 존재하는 경우에만 true를 반환합니다.
     * 존재하지 않는 버전의 파일은 UNREGISTERED로 간주하지 않습니다.
     *
     * <p>경로 형식 (createDirectoryStructure / createCustomVersionDirectory 참조):
     * <ul>
     *   <li>Standard: versions/{projectId}/standard/{majorMinor}/{version}/...</li>
     *   <li>Custom:   versions/{projectId}/custom/{customerCode}/{majorMinor}/{version}/...</li>
     * </ul>
     *
     * @param filePath 확인할 파일 경로
     * @return true면 동기화 대상 (ReleaseVersion 존재), false면 무시
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isValidSyncPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        String[] pathParts = filePath.split("/");
        // 최소 5개 필요: versions/{projectId}/{releaseType}/{majorMinor}/{version}/...
        if (pathParts.length < 5) {
            return false;
        }

        // pathParts[0] = "versions"
        String projectId = pathParts[1];
        String releaseType = pathParts[2].toLowerCase();

        if ("standard".equals(releaseType)) {
            // Standard: versions/{projectId}/standard/{majorMinor}/{version}/...
            // pathParts[3] = majorMinor (예: "1.0.x"), pathParts[4] = version (예: "1.0.0")
            String version = pathParts[4];
            if (!isValidVersionFormat(version)) {
                return false;
            }
            return releaseVersionRepository.existsByProject_ProjectIdAndReleaseTypeAndVersion(
                    projectId, "STANDARD", version);
        } else if ("custom".equals(releaseType)) {
            // Custom: versions/{projectId}/custom/{customerCode}/{majorMinor}/{version}/...
            if (pathParts.length < 6) {
                return false;
            }
            String customerCode = pathParts[3];
            String version = pathParts[5];
            if (!isValidVersionFormat(version)) {
                return false;
            }
            return releaseVersionRepository.existsByProject_ProjectIdAndReleaseTypeAndCustomer_CustomerCodeAndVersion(
                    projectId, "CUSTOM", customerCode, version);
        }

        return false;
    }

    /**
     * 버전 형식 유효성 검사 (예: 1.0.0, 1.1.0, 2.0.0)
     */
    private boolean isValidVersionFormat(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        // 간단한 버전 형식 검사: x.y.z 또는 x.y 형태
        return version.matches("\\d+\\.\\d+(\\.\\d+)?");
    }

    /**
     * ReleaseFile 엔티티를 FileSyncMetadata로 변환
     */
    private FileSyncMetadata toMetadata(ReleaseFile file) {
        return FileSyncMetadata.builder()
                .id(file.getReleaseFileId())
                .filePath(file.getFilePath())
                .fileName(file.getFileName())
                .fileSize(file.getFileSize())
                .checksum(file.getChecksum())
                .registeredAt(file.getCreatedAt())
                .target(FileSyncTarget.RELEASE_FILE)
                .build();
    }

    private FileCategory extractFileCategory(Map<String, Object> data, String key) {
        String value = extractString(data, key);
        if (value == null) {
            return FileCategory.ETC;
        }
        try {
            return FileCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return FileCategory.ETC;
        }
    }
}
