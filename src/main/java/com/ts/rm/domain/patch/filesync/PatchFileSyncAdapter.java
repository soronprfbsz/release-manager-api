package com.ts.rm.domain.patch.filesync;

import static com.ts.rm.global.util.MapExtractUtil.extractLong;
import static com.ts.rm.global.util.MapExtractUtil.extractString;
import static com.ts.rm.global.util.MapExtractUtil.extractStringOrDefault;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.site.repository.SiteRepository;
import com.ts.rm.domain.filesync.adapter.FileSyncAdapter;
import com.ts.rm.domain.filesync.dto.FileSyncMetadata;
import com.ts.rm.domain.filesync.enums.FileSyncTarget;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 패치 파일 동기화 어댑터
 *
 * <p>Patch 도메인의 파일 동기화를 담당합니다.
 * <p>패치는 폴더 단위로 관리되며, outputPath가 폴더 경로입니다.
 * <p>폴더 존재 여부로 동기화 상태를 판단합니다 (파일 크기/체크섬 비교 없음).
 *
 * <p>실제 폴더 구조: patches/{projectId}/{patchName}
 * <p>예: patches/infraeye2/20251226123045_1.0.0_1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatchFileSyncAdapter implements FileSyncAdapter {

    private final PatchRepository patchRepository;
    private final ProjectRepository projectRepository;
    private final SiteRepository siteRepository;
    private final AccountRepository accountRepository;
    private final AccountLookupService accountLookupService;

    /**
     * 패치 폴더명 파싱 패턴
     * <p>형식: {timestamp}_{fromVersion}_{toVersion}
     * <p>예: 202512241547_1.0.0_1.1.0 (12자리) 또는 20251226123045_1.0.0_1.1.0 (14자리)
     */
    private static final Pattern PATCH_FOLDER_PATTERN = Pattern.compile(
            "^(\\d{12,14})_([0-9.]+)_([0-9.]+)$"
    );

    /**
     * 커스텀 패치 폴더명 파싱 패턴
     * <p>형식: {timestamp}_{siteCode}_{fromVersion}_{toVersion}
     * <p>예: 202512241547_site1_1.0.0_1.1.0 (12자리) 또는 20251226123045_site1_1.0.0_1.1.0 (14자리)
     */
    private static final Pattern CUSTOM_PATCH_FOLDER_PATTERN = Pattern.compile(
            "^(\\d{12,14})_([a-zA-Z0-9_-]+)_([0-9.]+)_([0-9.]+)$"
    );

    @Override
    public FileSyncTarget getTarget() {
        return FileSyncTarget.PATCH_FILE;
    }

    @Override
    public String getBaseScanPath() {
        return "patches";
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileSyncMetadata> getRegisteredFiles(@Nullable String subPath) {
        List<Patch> patches;

        if (subPath != null && !subPath.isEmpty()) {
            patches = patchRepository.findAll().stream()
                    .filter(p -> p.getOutputPath() != null && p.getOutputPath().startsWith(subPath))
                    .toList();
        } else {
            patches = patchRepository.findAllByOrderByCreatedAtDesc(
                    org.springframework.data.domain.Pageable.unpaged()).getContent();
        }

        return patches.stream()
                .map(this::toMetadata)
                .toList();
    }

    @Override
    @Transactional
    public Long registerFile(FileSyncMetadata metadata, @Nullable Map<String, Object> additionalData) {
        // 경로에서 projectId와 폴더명 추출
        // 경로 형식: patches/{projectId}/{patchName}
        String filePath = metadata.getFilePath();
        String[] pathParts = filePath.split("/");

        if (pathParts.length < 3) {
            throw new BusinessException(ErrorCode.INVALID_PATCH_FOLDER_NAME,
                    "패치 경로 형식이 올바르지 않습니다: " + filePath +
                    ". 예상 형식: patches/{projectId}/{patchName}");
        }

        String pathProjectId = pathParts[1];
        String folderName = pathParts[2];

        // 폴더명에서 패치 정보 추출 시도
        PatchFolderInfo folderInfo = parsePatchFolderName(folderName);

        if (folderInfo == null) {
            throw new BusinessException(ErrorCode.INVALID_PATCH_FOLDER_NAME,
                    "패치 폴더명 형식이 올바르지 않습니다: " + folderName +
                    ". 예상 형식: {yyyyMMddHHmmss}_{fromVersion}_{toVersion}");
        }

        // additionalData에서 오버라이드 가능
        String projectId = extractStringOrDefault(additionalData, "projectId", pathProjectId);
        String releaseType = extractStringOrDefault(additionalData, "releaseType", folderInfo.releaseType);
        String fromVersion = extractStringOrDefault(additionalData, "fromVersion", folderInfo.fromVersion);
        String toVersion = extractStringOrDefault(additionalData, "toVersion", folderInfo.toVersion);
        String siteCode = extractStringOrDefault(additionalData, "siteCode", folderInfo.siteCode);
        String description = extractString(additionalData, "description");
        String createdByEmail = extractStringOrDefault(additionalData, "createdByEmail", "SYSTEM_SYNC");
        Long assigneeId = extractLong(additionalData, "assigneeId");

        // 프로젝트 조회
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND,
                        "프로젝트를 찾을 수 없습니다: " + projectId));

        // 커스텀 패치인 경우 사이트 조회
        Site site = null;
        if ("CUSTOM".equalsIgnoreCase(releaseType) && siteCode != null) {
            site = siteRepository.findBySiteCode(siteCode)
                    .orElse(null); // 사이트가 없어도 등록은 진행
            if (site == null) {
                log.warn("사이트를 찾을 수 없습니다: {}. 사이트 없이 패치를 등록합니다.", siteCode);
            }
        }

        // 담당자 조회
        Account assignee = null;
        if (assigneeId != null) {
            assignee = accountRepository.findByAccountId(assigneeId).orElse(null);
            if (assignee == null) {
                log.warn("담당자를 찾을 수 없습니다: {}. 담당자 없이 패치를 등록합니다.", assigneeId);
            }
        }

        // 패치명 생성
        String patchName = folderName;

        // 생성자 Account 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        Patch patch = Patch.builder()
                .project(project)
                .releaseType(releaseType.toUpperCase())
                .site(site)
                .assignee(assignee)
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .patchName(patchName)
                .outputPath(metadata.getFilePath())
                .description(description)
                .creator(creator)
                .createdByEmail(createdByEmail)
                .build();

        Patch saved = patchRepository.save(patch);
        log.info("패치 동기화 등록: {} (ID: {})", metadata.getFilePath(), saved.getPatchId());

        return saved.getPatchId();
    }

    @Override
    @Transactional
    public void updateMetadata(Long id, FileSyncMetadata newMetadata) {
        // 패치는 폴더 단위이므로 메타데이터 갱신이 필요 없음 (크기/체크섬 없음)
        // outputPath만 갱신 가능
        Patch patch = patchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATCH_NOT_FOUND,
                        "패치를 찾을 수 없습니다: " + id));

        patch.setOutputPath(newMetadata.getFilePath());
        patchRepository.save(patch);
        log.info("패치 메타데이터 갱신: {} (ID: {})", newMetadata.getFilePath(), id);
    }

    @Override
    @Transactional
    public void deleteMetadata(Long id) {
        if (!patchRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.PATCH_NOT_FOUND,
                    "패치를 찾을 수 없습니다: " + id);
        }

        patchRepository.deleteById(id);
        log.info("패치 메타데이터 삭제: ID {}", id);
    }

    /**
     * 패치는 폴더 단위로 스캔
     * <p>확장자 필터 없이 폴더만 스캔합니다.
     */
    @Override
    public List<String> getAllowedExtensions() {
        // null 반환 시 모든 항목 포함 (폴더 포함)
        return null;
    }

    @Override
    public List<String> getExcludedDirectories() {
        // 제외할 디렉토리 없음
        return null;
    }

    /**
     * 패치는 폴더 기반 동기화
     */
    @Override
    public boolean isFolderBased() {
        return true;
    }

    /**
     * patches/{projectId}/{patchName} 형식이므로 깊이 2
     */
    @Override
    public int getFolderScanDepth() {
        return 2;
    }

    /**
     * 유효한 동기화 경로인지 확인
     *
     * <p>패치 폴더는 patches/{projectId}/{patchName} 형식이어야 합니다.
     * <p>폴더명이 패치 명명 규칙을 따르는지 확인합니다.
     *
     * @param filePath 파일/폴더 경로
     * @return true면 동기화 대상, false면 무시
     */
    @Override
    public boolean isValidSyncPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        // 경로 형식: patches/{projectId}/{patchName}
        String[] pathParts = filePath.split("/");
        if (pathParts.length < 3) {
            log.debug("패치 경로 형식 불일치 (최소 3단계 필요): {}", filePath);
            return false;
        }

        // 첫 번째 부분이 "patches"인지 확인
        if (!"patches".equalsIgnoreCase(pathParts[0])) {
            log.debug("패치 경로는 'patches/'로 시작해야 합니다: {}", filePath);
            return false;
        }

        // 두 번째 부분이 projectId (프로젝트 존재 여부는 등록 시 확인)
        String projectId = pathParts[1];
        if (projectId == null || projectId.isEmpty()) {
            log.debug("프로젝트 ID가 없습니다: {}", filePath);
            return false;
        }

        // 세 번째 부분이 패치 폴더명 - 패치 명명 규칙 확인
        String folderName = pathParts[2];
        if (parsePatchFolderName(folderName) == null) {
            log.debug("패치 폴더명이 명명 규칙에 맞지 않습니다: {}", folderName);
            return false;
        }

        return true;
    }

    /**
     * Patch 엔티티를 FileSyncMetadata로 변환
     */
    private FileSyncMetadata toMetadata(Patch patch) {
        return FileSyncMetadata.builder()
                .id(patch.getPatchId())
                .filePath(patch.getOutputPath())
                .fileName(patch.getPatchName())
                // 패치는 폴더 단위이므로 파일 크기/체크섬 없음
                .fileSize(null)
                .checksum(null)
                .registeredAt(patch.getCreatedAt())
                .target(FileSyncTarget.PATCH_FILE)
                .build();
    }

    /**
     * 패치 폴더명에서 정보 파싱
     *
     * @param folderName 패치 폴더명
     * @return 파싱된 정보 또는 null (파싱 실패 시)
     */
    private PatchFolderInfo parsePatchFolderName(String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            return null;
        }

        // 표준 패치 형식 시도: {timestamp}_{fromVersion}_{toVersion}
        // 예: 20251226123045_1.0.0_1.1.0
        Matcher standardMatcher = PATCH_FOLDER_PATTERN.matcher(folderName);
        if (standardMatcher.matches()) {
            return new PatchFolderInfo(
                    standardMatcher.group(2),  // fromVersion
                    standardMatcher.group(3),  // toVersion
                    "STANDARD",                // releaseType
                    null                       // siteCode
            );
        }

        // 커스텀 패치 형식 시도: {timestamp}_{siteCode}_{fromVersion}_{toVersion}
        // 예: 20251226123045_site1_1.0.0_1.1.0
        Matcher customMatcher = CUSTOM_PATCH_FOLDER_PATTERN.matcher(folderName);
        if (customMatcher.matches()) {
            return new PatchFolderInfo(
                    customMatcher.group(3),    // fromVersion
                    customMatcher.group(4),    // toVersion
                    "CUSTOM",                  // releaseType
                    customMatcher.group(2)     // siteCode
            );
        }

        return null;
    }

    /**
     * 패치 폴더명 파싱 결과를 담는 내부 클래스
     */
    private record PatchFolderInfo(
            String fromVersion,
            String toVersion,
            String releaseType,
            String siteCode
    ) {}
}
