package com.ts.rm.domain.patch.service;

import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.entity.CustomerProject;
import com.ts.rm.domain.customer.repository.CustomerProjectRepository;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.customer.service.CustomerSiteVersionService;
import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import com.ts.rm.domain.patch.mapper.PatchDtoMapper;
import com.ts.rm.domain.patch.repository.PatchIncludedBuildRepository;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.service.ReleaseVersionFileSystemService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.pagination.PageRowNumberUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ts.rm.global.security.SecurityUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 패치 서비스 (오케스트레이터)
 *
 * <p>패치 CRUD 및 다른 서비스들을 조율하는 역할을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatchService {

    private final PatchRepository patchRepository;
    private final PatchIncludedBuildRepository patchIncludedBuildRepository;
    private final PatchDtoMapper patchDtoMapper;
    private final PatchGenerationService patchGenerationService;
    private final PatchDownloadService patchDownloadService;
    private final PatchHistoryService patchHistoryService;
    private final CustomerSiteVersionService customerSiteVersionService;
    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseVersionFileSystemService releaseVersionFileSystemService;
    private final CustomerRepository customerRepository;
    private final CustomerProjectRepository customerProjectRepository;

    /** BASE 버전 추출 정규식 (major.minor.patch) */
    private static final Pattern BASE_VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)");

    @Value("${app.release.base-path:data/release-manager}")
    private String releaseBasePath;

    /**
     * 패치 생성 (버전 문자열 기반) - 위임
     */
    @Transactional
    public PatchGenerationService.GenerateResult generatePatchByVersion(String projectId, String releaseType,
            Long customerId, String fromVersion, String toVersion, String createdByEmail, String description,
            Long engineerId, String patchName, PatchDto.BuildSelection buildSelection) {
        validateBuildSelection(buildSelection);
        return patchGenerationService.generatePatchByVersion(
                projectId, releaseType, customerId, fromVersion, toVersion,
                createdByEmail, description, engineerId, patchName, buildSelection);
    }

    /**
     * 패치 생성 (버전 ID 기반) - 위임
     */
    @Transactional
    public PatchGenerationService.GenerateResult generatePatch(String projectId, Long fromVersionId,
            Long toVersionId, Long customerId, String createdByEmail, String description, Long engineerId,
            String patchName, PatchDto.BuildSelection buildSelection) {
        validateBuildSelection(buildSelection);
        return patchGenerationService.generatePatch(
                projectId, fromVersionId, toVersionId, customerId,
                createdByEmail, description, engineerId, patchName, buildSelection);
    }

    /**
     * buildSelection 의 spec §4.3 검증 룰을 검사한다.
     *
     * @param selection  요청에서 받은 buildSelection (null 가능)
     * @throws BusinessException INVALID_INPUT_VALUE 룰에 위배되면
     */
    public static void validateBuildSelection(PatchDto.BuildSelection selection) {
        boolean enabled = selection != null && selection.enabled();
        boolean pickerEmpty = selection == null
                || (selection.web() == null && (selection.engines() == null || selection.engines().isEmpty()));

        if (enabled && pickerEmpty) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "빌드 미포함이면 토글을 OFF 로 두십시오");
        }
    }

    /**
     * 패치 조회
     */
    @Transactional(readOnly = true)
    public Patch getPatch(Long patchId) {
        return patchRepository.findById(patchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "패치를 찾을 수 없습니다: " + patchId));
    }

    /**
     * 패치 목록 페이징 조회
     *
     * @param projectId    프로젝트 ID (null이면 전체)
     * @param releaseType  릴리즈 타입 (STANDARD/CUSTOM, null이면 전체)
     * @param customerCode 고객사 코드 (null이면 전체)
     * @param pageable     페이징 정보
     * @return 패치 목록 페이지 (rowNumber 포함)
     */
    @Transactional(readOnly = true)
    public Page<PatchDto.ListResponse> listPatchesWithPaging(String projectId, String releaseType,
            String customerCode, Pageable pageable) {
        Page<Patch> patches = patchRepository.findAllWithFilters(projectId, releaseType, customerCode, pageable);

        // 페이징 결과의 patch_id 들에 대해 batch 1번으로 PatchIncludedBuild 행 조회 (N+1 방지)
        List<Long> patchIds = patches.getContent().stream()
                .map(Patch::getPatchId)
                .toList();
        Map<Long, List<PatchIncludedBuild>> includedByPatch = patchIds.isEmpty()
                ? Map.of()
                : patchIncludedBuildRepository.findAllByPatch_PatchIdIn(patchIds).stream()
                        .collect(Collectors.groupingBy(r -> r.getPatch().getPatchId()));

        // rowNumber 계산 (공통 유틸리티 사용)
        return PageRowNumberUtil.mapWithRowNumber(patches, (patch, rowNumber) -> {
            PatchDto.ListResponse base = patchDtoMapper.toListResponse(patch);
            String summary = buildIncludedBuildsSummary(
                    includedByPatch.getOrDefault(patch.getPatchId(), List.of()));
            return new PatchDto.ListResponse(
                    rowNumber,
                    base.patchId(),
                    base.projectId(),
                    base.releaseType(),
                    base.customerCode(),
                    base.customerName(),
                    base.fromVersion(),
                    base.toVersion(),
                    base.patchName(),
                    base.createdByEmail(),
                    base.createdByName(),
                    base.createdByAvatarStyle(),
                    base.createdByAvatarSeed(),
                    base.isDeletedCreator(),
                    base.description(),
                    base.assigneeId(),
                    base.assigneeName(),
                    base.assigneeEmail(),
                    base.assigneeAvatarStyle(),
                    base.assigneeAvatarSeed(),
                    base.createdAt(),
                    base.updatedAt(),
                    patch.getIsBuildOnly(),
                    patch.getIsBuildIncluded(),
                    summary
            );
        });
    }

    /**
     * 패치 목록의 빌드 포함 요약. 엔진 종류는 많아질 수 있어 개별로 노출하지 않고
     * 'WEB' / 'ENGINE' 두 토큰으로만 표기한다 (예: "WEB", "ENGINE", "WEB,ENGINE").
     * 빌드 미포함 시 null.
     */
    private String buildIncludedBuildsSummary(List<PatchIncludedBuild> rows) {
        if (rows.isEmpty()) return null;
        List<String> tokens = new ArrayList<>();
        if (rows.stream().anyMatch(r -> "WEB".equals(r.getKind()))) {
            tokens.add("WEB");
        }
        if (rows.stream().anyMatch(r -> "ENGINE".equals(r.getKind()))) {
            tokens.add("ENGINE");
        }
        return tokens.isEmpty() ? null : String.join(",", tokens);
    }

    /**
     * 패치를 스트리밍 방식으로 ZIP 압축하여 출력 스트림에 작성 - 위임
     */
    @Transactional(readOnly = true)
    public void streamPatchAsZip(Long patchId, OutputStream outputStream) {
        Patch patch = getPatch(patchId);
        patchDownloadService.streamPatchAsZip(patch, outputStream);
    }

    /**
     * 패치 ZIP 파일명 생성 - 위임
     */
    public String getZipFileName(Long patchId) {
        Patch patch = getPatch(patchId);
        return patchDownloadService.getZipFileName(patch);
    }

    /**
     * 패치 디렉토리의 압축 전 총 크기 계산 - 위임
     */
    public long calculateUncompressedSize(Long patchId) {
        Patch patch = getPatch(patchId);
        return patchDownloadService.calculateUncompressedSize(patch);
    }

    /**
     * 패치 ZIP 파일 내부 구조 조회 - 위임
     */
    @Transactional(readOnly = true)
    public PatchDto.DirectoryNode getZipFileStructure(Long patchId) {
        Patch patch = getPatch(patchId);
        return patchDownloadService.getZipFileStructure(patch);
    }

    /**
     * 패치 파일 내용 조회 - 위임
     */
    @Transactional(readOnly = true)
    public PatchDto.FileContentResponse getFileContent(Long patchId, String relativePath) {
        Patch patch = getPatch(patchId);
        return patchDownloadService.getFileContent(patch, relativePath);
    }

    /**
     * 패치 완료 처리 (적용 완료)
     *
     * <p>처리 순서:
     * <ol>
     *   <li>패치 이력(patch_history) 영구 저장 — 완료 시점 / 완료자 기록</li>
     *   <li>CustomerProject.last_patched_* 갱신 (고객사 지정 패치인 경우만)</li>
     *   <li>디스크 패치 디렉토리 삭제</li>
     *   <li>patch_file row 삭제</li>
     * </ol>
     *
     * <p>row 삭제 후에는 patch_history 에서만 이력을 확인할 수 있습니다.
     *
     * @param patchId     완료 처리할 패치 ID
     * @param completedBy 완료 처리자 이메일 (현재 로그인 사용자)
     */
    @Transactional
    public void completePatch(Long patchId, String completedBy) {
        // 1. 패치 조회
        Patch patch = patchRepository.findById(patchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATCH_NOT_FOUND,
                        "패치를 찾을 수 없습니다: " + patchId));

        // 권한 검사 — USER 는 본인 생성 패치만 처리 가능
        requireOwnerOrPrivilegedRole(patch);

        LocalDateTime now = LocalDateTime.now();

        // 2. 패치 이력 영구 저장 (완료 시점)
        patchHistoryService.saveFromPatch(patch, completedBy, now);

        // 3. 고객사 지정 패치인 경우 — CustomerProject 갱신 + 사이트 버전 upsert
        if (patch.getCustomer() != null) {
            updateCustomerProjectPatchInfo(patch.getCustomer(), patch.getProject(),
                    patch.getToVersion(), now);
            applyCustomerSiteVersions(patch, completedBy, now);
        }

        // 4. 디스크 패치 디렉토리 삭제
        Path patchDir = Paths.get(releaseBasePath, patch.getOutputPath());
        if (Files.exists(patchDir)) {
            try {
                deleteDirectoryRecursively(patchDir);
                log.info("패치 완료 — 디렉토리 삭제 완료: {}", patchDir.toAbsolutePath());
            } catch (IOException e) {
                log.error("패치 완료 — 디렉토리 삭제 실패: {}", patchDir, e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "패치 파일 삭제 중 오류가 발생했습니다: " + e.getMessage());
            }
        } else {
            log.warn("패치 완료 — 디렉토리가 이미 없음: {}", patchDir);
        }

        // 5. patch_file row 삭제
        patchRepository.delete(patch);

        log.info("패치 완료 처리 완료 - patchId: {}, patchName: {}, completedBy: {}",
                patchId, patch.getPatchName(), completedBy);
    }

    /**
     * CustomerProject 마지막 패치 정보 갱신
     *
     * <p>고객사-프로젝트 매핑이 없으면 새로 생성하고, 있으면 업데이트합니다.
     * 패치 완료 시점에 호출됩니다.
     *
     * @param customer    고객사
     * @param project     프로젝트
     * @param toVersion   완료된 패치의 toVersion
     * @param completedAt 완료 일시
     */
    private void updateCustomerProjectPatchInfo(Customer customer, Project project,
            String toVersion, LocalDateTime completedAt) {
        CustomerProject customerProject = customerProjectRepository
                .findByCustomer_CustomerIdAndProject_ProjectId(
                        customer.getCustomerId(), project.getProjectId())
                .orElseGet(() -> {
                    log.info("고객사-프로젝트 매핑 신규 생성 - customerId: {}, projectId: {}",
                            customer.getCustomerId(), project.getProjectId());
                    return CustomerProject.create(customer, project);
                });

        customerProject.updateLastPatchInfo(toVersion, completedAt);
        customerProjectRepository.save(customerProject);

        log.info("CustomerProject 업데이트 완료 - customerId: {}, projectId: {}, lastPatchedVersion: {}",
                customer.getCustomerId(), project.getProjectId(), toVersion);
    }

    /**
     * 패치 완료 시 사이트별 컴포넌트 버전 upsert.
     *
     * <ul>
     *   <li>BASE — to_version 에서 major.minor.patch 추출하여 항상 갱신</li>
     *   <li>WEB  — 빌드 포함 패치인 경우 patch_included_build 의 WEB fullVersion 으로 갱신</li>
     *   <li>ENGINE — 빌드 포함 패치인 경우 엔진명(engine_name) 별로 fullVersion 갱신.
     *                같은 엔진이 여러 행이면 patch 적재 순서상 마지막 값으로 덮어쓴다.</li>
     * </ul>
     * 빌드 미포함 패치는 BASE 만 갱신, WEB/ENGINE 은 이전 값 유지.
     *
     * @param patch     완료 처리된 패치
     * @param updatedBy 갱신자 이메일
     * @param now       갱신 일시
     */
    private void applyCustomerSiteVersions(Patch patch, String updatedBy, LocalDateTime now) {
        Long customerId = patch.getCustomer().getCustomerId();
        String projectId = patch.getProject().getProjectId();

        // 1) BASE — to_version 에서 major.minor.patch 추출하여 항상 갱신
        String baseVersion = extractBaseVersion(patch.getToVersion());
        customerSiteVersionService.upsert(customerId, projectId, "BASE", null, baseVersion, updatedBy, now);

        // 2) 빌드 포함 패치인 경우 WEB / ENGINE 갱신
        if (Boolean.TRUE.equals(patch.getIsBuildIncluded())) {
            List<PatchIncludedBuild> builds =
                    patchIncludedBuildRepository.findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(
                            patch.getPatchId());

            // WEB: 통상 1개. 있으면 그 fullVersion 으로 갱신 (engineName=null)
            builds.stream()
                    .filter(b -> "WEB".equals(b.getKind()))
                    .map(PatchIncludedBuild::getFullVersion)
                    .findFirst()
                    .ifPresent(v -> customerSiteVersionService.upsert(
                            customerId, projectId, "WEB", null, v, updatedBy, now));

            // ENGINE: 엔진명 별로 별도 upsert. 같은 엔진이 여러 행이면 마지막 값으로 덮어쓴다.
            builds.stream()
                    .filter(b -> "ENGINE".equals(b.getKind()))
                    .filter(b -> b.getEngineName() != null)
                    .forEach(b -> customerSiteVersionService.upsert(
                            customerId, projectId, "ENGINE", b.getEngineName(),
                            b.getFullVersion(), updatedBy, now));
        }
    }

    /**
     * 버전 문자열에서 BASE 버전(major.minor.patch)만 추출.
     *
     * <p>예: "1.1.0.260511-1" → "1.1.0", "1.1.0" → "1.1.0"
     *
     * @param toVersion 패치 to_version 문자열
     * @return major.minor.patch 형태 문자열 (파싱 실패 시 원본 반환)
     */
    private String extractBaseVersion(String toVersion) {
        if (toVersion == null) {
            return null;
        }
        Matcher m = BASE_VERSION_PATTERN.matcher(toVersion);
        return m.find() ? m.group(1) : toVersion;
    }

    /**
     * 패치 삭제 (DB 레코드 + 실제 파일)
     *
     * @param patchId 패치 ID
     */
    @Transactional
    public void deletePatch(Long patchId) {
        // 1. 패치 조회
        Patch patch = getPatch(patchId);

        // 권한 검사 — USER 는 본인 생성 패치만 삭제 가능
        requireOwnerOrPrivilegedRole(patch);

        // 2. 실제 파일 디렉토리 삭제
        Path patchDir = Paths.get(releaseBasePath, patch.getOutputPath());

        if (Files.exists(patchDir)) {
            try {
                deleteDirectoryRecursively(patchDir);
                log.info("패치 디렉토리 삭제 완료: {}", patchDir.toAbsolutePath());
            } catch (IOException e) {
                log.error("패치 디렉토리 삭제 실패: {}", patchDir, e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "패치 파일 삭제 중 오류가 발생했습니다: " + e.getMessage());
            }
        } else {
            log.warn("패치 디렉토리가 존재하지 않습니다: {}", patchDir);
        }

        // 3. DB 레코드 삭제
        patchRepository.delete(patch);

        log.info("패치 삭제 완료 - ID: {}, Name: {}", patchId, patch.getPatchName());
    }

    /**
     * 디렉토리 재귀적 삭제
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {
            stream.sorted((p1, p2) -> -p1.compareTo(p2)) // 역순 정렬 (하위 항목부터 삭제)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.debug("파일/디렉토리 삭제: {}", path);
                        } catch (IOException e) {
                            log.warn("파일/디렉토리 삭제 실패: {}", path, e);
                        }
                    });
        }
    }

    // ========================================
    // 커스텀 패치용 메서드
    // ========================================

    /**
     * 커스텀 버전 보유 고객사 목록 조회
     *
     * @param projectId 프로젝트 ID
     * @return 커스텀 버전이 있는 고객사 목록
     */
    @Transactional(readOnly = true)
    public List<PatchDto.CustomerWithCustomVersions> getCustomersWithCustomVersions(String projectId) {
        log.info("커스텀 버전 보유 고객사 목록 조회 - projectId: {}", projectId);

        List<Long> customerIds = releaseVersionRepository.findCustomerIdsWithCustomVersions(projectId);

        return customerIds.stream()
                .map(customerId -> customerRepository.findById(customerId)
                        .map(customer -> new PatchDto.CustomerWithCustomVersions(
                                customer.getCustomerId(),
                                customer.getCustomerCode(),
                                customer.getCustomerName()
                        ))
                        .orElse(null))
                .filter(dto -> dto != null)
                .toList();
    }

    /**
     * 고객사별 커스텀 버전 목록 조회 (셀렉트박스용)
     *
     * <p>베이스 버전(표준본)을 첫 번째로, 이후 커스텀 버전들을 반환합니다.
     * 프론트엔드에서 From 버전 선택 시 베이스 버전부터 선택 가능합니다.
     *
     * @param projectId  프로젝트 ID
     * @param customerId 고객사 ID
     * @return 버전 목록 (베이스 버전 + 커스텀 버전들)
     */
    @Transactional(readOnly = true)
    public List<PatchDto.CustomVersionSelectOption> getCustomVersionsByCustomer(String projectId, Long customerId) {
        log.info("고객사별 커스텀 버전 목록 조회 - projectId: {}, customerId: {}", projectId, customerId);

        // 고객사 존재 확인
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND,
                        "고객사를 찾을 수 없습니다: " + customerId));

        List<ReleaseVersion> customVersions = releaseVersionRepository.findAllByCustomer_CustomerIdOrderByCreatedAtDesc(customerId);

        List<PatchDto.CustomVersionSelectOption> result = new java.util.ArrayList<>();

        // 베이스 버전 추가 (첫 번째 커스텀 버전의 customBaseVersion에서 가져옴)
        if (!customVersions.isEmpty()) {
            ReleaseVersion firstCustomVersion = customVersions.get(customVersions.size() - 1); // 가장 오래된 버전
            ReleaseVersion customBaseVersion = firstCustomVersion.getCustomBaseVersion();

            if (customBaseVersion != null) {
                result.add(new PatchDto.CustomVersionSelectOption(
                        customBaseVersion.getReleaseVersionId(),
                        customBaseVersion.getVersion(),
                        true, // 표준 버전은 항상 승인됨
                        true  // 베이스 버전
                ));
            }
        }

        // 커스텀 버전들 추가 (최신순)
        // 핫픽스/빌드는 version 필드가 base 와 동일하므로 fullVersion 으로 식별 가능하게 노출
        // (예: 1.1.0-companyA.1.0.0, 1.1.0-companyA.1.0.0.1, 1.1.0-companyA.1.0.0.260427)
        customVersions.forEach(v -> result.add(new PatchDto.CustomVersionSelectOption(
                v.getReleaseVersionId(),
                v.getFullVersion(),
                v.getIsApproved(),
                false // 커스텀 버전
        )));

        return result;
    }

    /**
     * 커스텀 패치 생성 (버전 문자열 기반) - 위임
     */
    @Transactional
    public Patch generateCustomPatchByVersion(String projectId, Long customerId,
            String fromVersion, String toVersion, String createdByEmail, String description,
            Long engineerId, String patchName, PatchDto.BuildSelection buildSelection) {
        validateBuildSelection(buildSelection);
        return patchGenerationService.generateCustomPatchByVersion(
                projectId, customerId, fromVersion, toVersion,
                createdByEmail, description, engineerId, patchName, buildSelection);
    }

    /**
     * 패치 일괄 삭제 (DB 레코드 + 실제 파일)
     *
     * @param patchIds 삭제할 패치 ID 목록
     * @return 삭제 결과
     */
    @Transactional
    public PatchDto.BatchDeleteResponse batchDeletePatches(List<Long> patchIds) {
        log.info("패치 일괄 삭제 요청 - patchIds: {}", patchIds);

        if (patchIds == null || patchIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "패치 ID 목록은 비어있을 수 없습니다");
        }

        // 1. 패치 일괄 조회 및 검증
        List<Patch> patches = patchRepository.findAllById(patchIds);

        if (patches.size() != patchIds.size()) {
            log.warn("일부 패치를 찾을 수 없음 - 요청: {}, 조회: {}",
                    patchIds.size(), patches.size());
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "일부 패치를 찾을 수 없습니다");
        }

        // 권한 검사 — USER 는 본인 생성 패치만 일괄 삭제 가능
        for (Patch patch : patches) {
            requireOwnerOrPrivilegedRole(patch);
        }

        // 2. 각 패치의 실제 파일 디렉토리 삭제
        for (Patch patch : patches) {
            Path patchDir = Paths.get(releaseBasePath, patch.getOutputPath());

            if (Files.exists(patchDir)) {
                try {
                    deleteDirectoryRecursively(patchDir);
                    log.info("패치 디렉토리 삭제 완료: {}", patchDir.toAbsolutePath());
                } catch (IOException e) {
                    log.error("패치 디렉토리 삭제 실패: {}", patchDir, e);
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                            "패치 파일 삭제 중 오류가 발생했습니다: " + e.getMessage());
                }
            } else {
                log.warn("패치 디렉토리가 존재하지 않습니다: {}", patchDir);
            }
        }

        // 3. DB 레코드 일괄 삭제
        patchRepository.deleteAll(patches);

        String message = String.format("%d개 패치가 삭제되었습니다.", patches.size());
        log.info("패치 일괄 삭제 완료 - {}", message);

        return new PatchDto.BatchDeleteResponse(patches.size(), message);
    }

    /**
     * 보관 기간이 지난 패치 일괄 정리 (스케줄러 patch-cleanup 호출).
     *
     * <p>{@code created_at < now() - retentionDays} 인 patch_file 행과 대응 디렉토리를
     * 일괄 삭제한다. 완료 여부와 무관하게 운영 정책상 일괄 정리한다.
     *
     * <ul>
     *   <li>디렉토리 삭제는 best-effort — NAS 부분 실패 시 로그만 남기고 진행한다
     *       ({@link ReleaseVersionFileSystemService#deleteDirectory}).</li>
     *   <li>patch_file row 삭제 시 patch_included_build / patch_hotfix_in_range 메타 행은
     *       FK ON DELETE CASCADE 로 동반 삭제된다 (V6 migration 참조).</li>
     * </ul>
     *
     * @param retentionDays 보관 기간 (일)
     * @return 삭제된 패치 수
     */
    @Transactional
    public long deleteOldPatches(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Patch> oldPatches = patchRepository.findByCreatedAtBefore(cutoff);
        log.info("오래된 패치 정리 시작 - retentionDays: {}, cutoff: {}, 대상: {}건",
                retentionDays, cutoff, oldPatches.size());

        // 1. 각 패치 디렉토리 best-effort 삭제 (NAS 부분 실패는 로그만 남기고 진행)
        for (Patch patch : oldPatches) {
            Path patchDir = Paths.get(releaseBasePath, patch.getOutputPath());
            releaseVersionFileSystemService.deleteDirectory(patchDir);
        }

        // 2. patch_file row 일괄 삭제 (메타 행은 cascade 로 동반 삭제)
        patchRepository.deleteAll(oldPatches);

        log.info("오래된 패치 정리 완료 - retentionDays: {}, deletedCount: {}",
                retentionDays, oldPatches.size());
        return oldPatches.size();
    }

    /**
     * 자동 생성될 패치명을 미리 계산해 반환 — 프론트 미리보기 용.
     *
     * <p>{@link PatchGenerationService#resolvePatchName} 와 동일한 규칙:
     *  {@code {customerCode|undefined}_{yyMMdd}} 형태, 이미 존재하면
     *  {@code -2}, {@code -3} ... suffix 부여.
     *
     * @param customerCode 고객사 코드 (null / blank 이면 "undefined")
     */
    public String previewAutoPatchName(String customerCode) {
        String prefix = StringUtils.hasText(customerCode) ? customerCode : "undefined";
        String date = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ofPattern("yyMMdd"));
        String base = prefix + "_" + date;
        if (!patchRepository.existsByPatchName(base)) return base;
        int suffix = 2;
        while (patchRepository.existsByPatchName(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    /**
     * USER 권한 사용자는 본인이 생성한 패치만 액션 가능 — OPERATOR / DEVELOPER /
     * ADMIN 은 모든 패치 가능. SecurityContext 가 없는 호출 (시스템 / FileSync
     * 등) 은 검사 생략.
     */
    private void requireOwnerOrPrivilegedRole(Patch patch) {
        String role;
        try {
            role = SecurityUtil.getCurrentRole();
        } catch (BusinessException e) {
            // SecurityContext 없음 — 시스템 호출로 간주
            return;
        }
        if (!"USER".equals(role)) return;

        String email = SecurityUtil.getCurrentEmail();
        if (!Objects.equals(patch.getCreatedByEmail(), email)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "본인이 생성한 패치만 처리할 수 있습니다.");
        }
    }
}
