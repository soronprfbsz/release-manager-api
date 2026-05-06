package com.ts.rm.domain.patch.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.entity.CustomerProject;
import com.ts.rm.domain.customer.repository.CustomerProjectRepository;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.entity.PatchHistory;
import com.ts.rm.domain.patch.entity.PatchHotfixInRange;
import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import com.ts.rm.domain.patch.repository.PatchHistoryRepository;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.patch.util.ScriptGenerator;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.service.ReleaseVersionFileSystemService;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 패치 생성 서비스
 *
 * <p>패치 파일 생성, SQL 파일 복사, 스크립트 생성 등의 로직을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatchGenerationService {

    /**
     * 패치 생성 결과 record.
     *
     * <p>생성된 Patch 엔티티와 응답 보강 정보(isBuildOnly, hotfixesInRange, includedBuilds)를 함께 전달한다.
     */
    public record GenerateResult(
            Patch patch,
            boolean isBuildOnly,
            java.util.List<PatchDto.HotfixInRangeInfo> hotfixesInRange,
            PatchDto.IncludedBuilds includedBuilds
    ) {}

    private final PatchRepository patchRepository;
    private final PatchHistoryRepository patchHistoryRepository;
    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseFileRepository releaseFileRepository;
    private final CustomerRepository customerRepository;
    private final CustomerProjectRepository customerProjectRepository;
    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final ScriptGenerator mariaDBScriptGenerator;
    private final ScriptGenerator crateDBScriptGenerator;
    private final AccountLookupService accountLookupService;
    private final ReleaseVersionFileSystemService fileSystemService;

    @Value("${app.release.base-path:data/release-manager}")
    private String releaseBasePath;

    /**
     * 패치 생성 (버전 문자열 기반) - 표준 버전용
     *
     * @param projectId      프로젝트 ID
     * @param releaseType    릴리즈 타입 (STANDARD/CUSTOM)
     * @param customerId     고객사 ID (CUSTOM인 경우)
     * @param fromVersion    From 버전 (예: 1.0.0)
     * @param toVersion      To 버전 (예: 1.1.1)
     * @param createdByEmail 생성자
     * @param description    설명 (선택)
     * @param assigneeId     패치 담당자 ID (선택)
     * @param patchName      패치 이름 (선택, 미입력 시 자동 생성)
     * @param buildSelection 빌드 파일 선택 (null 이면 빌드 미포함)
     * @return 생성된 패치
     */
    @Transactional
    public GenerateResult generatePatchByVersion(String projectId, String releaseType, Long customerId,
            String fromVersion, String toVersion, String createdByEmail, String description,
            Long assigneeId, String patchName, PatchDto.BuildSelection buildSelection) {

        // 버전 조회 (프로젝트 내에서, 핫픽스 제외, 빌드 인식)
        ParsedInputVersion fromParsed = parseInputVersion(fromVersion);
        ParsedInputVersion toParsed = parseInputVersion(toVersion);

        ReleaseVersion from = releaseVersionRepository
                .findByProject_ProjectIdAndReleaseTypeAndVersionAndHotfixVersionAndBuildVersion(
                        projectId, releaseType.toUpperCase(),
                        fromParsed.baseVersionString(), 0, fromParsed.buildVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                        "From 버전을 찾을 수 없습니다: " + fromVersion));

        ReleaseVersion to = releaseVersionRepository
                .findByProject_ProjectIdAndReleaseTypeAndVersionAndHotfixVersionAndBuildVersion(
                        projectId, releaseType.toUpperCase(),
                        toParsed.baseVersionString(), 0, toParsed.buildVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                        "To 버전을 찾을 수 없습니다: " + toVersion));

        return generatePatch(projectId, from.getReleaseVersionId(), to.getReleaseVersionId(),
                customerId, createdByEmail, description, assigneeId, patchName, buildSelection);
    }

    /**
     * 커스텀 패치 생성 (커스텀 버전 문자열 기반)
     *
     * @param projectId    프로젝트 ID
     * @param customerId   고객사 ID
     * @param fromVersion  From 커스텀 버전 (예: 1.0.0)
     * @param toVersion    To 커스텀 버전 (예: 1.0.2)
     * @param createdByEmail    생성자
     * @param description  설명 (선택)
     * @param assigneeId   패치 담당자 ID (선택)
     * @param patchName    패치 이름 (선택, 미입력 시 자동 생성)
     * @return 생성된 패치
     */
    @Transactional
    public Patch generateCustomPatchByVersion(String projectId, Long customerId,
            String fromVersion, String toVersion, String createdByEmail, String description,
            Long assigneeId, String patchName) {

        // 고객사 조회
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND,
                        "고객사를 찾을 수 없습니다: " + customerId));

        // From 버전 조회 (베이스 버전 또는 커스텀 버전, 빌드 인식)
        // 베이스 버전 형식: 1.1.0(.260427), 커스텀 버전 형식: 1.1.0-companyA.1.0.0(.260427)
        ParsedInputVersion fromParsed = parseInputVersion(fromVersion);
        ParsedInputVersion toParsed = parseInputVersion(toVersion);

        ReleaseVersion from;
        if (fromVersion.contains("-")) {
            // 커스텀 버전: 고객사 내에서 (version, build) 일치 row
            from = releaseVersionRepository.findAllByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
                    .stream()
                    .filter(v -> fromParsed.baseVersionString().equals(v.getVersion()))
                    .filter(v -> v.getHotfixVersion() == 0)
                    .filter(v -> (v.getBuildVersion() != null ? v.getBuildVersion() : 0)
                            == fromParsed.buildVersion())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                            "From 커스텀 버전을 찾을 수 없습니다: " + fromVersion));
        } else {
            // 베이스(표준) 버전
            from = releaseVersionRepository
                    .findByProject_ProjectIdAndReleaseTypeAndVersionAndHotfixVersionAndBuildVersion(
                            projectId, "STANDARD", fromParsed.baseVersionString(), 0, fromParsed.buildVersion())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                            "From 베이스 버전을 찾을 수 없습니다: " + fromVersion));
        }

        // To 버전 조회 (커스텀 버전만 허용, 빌드 인식)
        ReleaseVersion to = releaseVersionRepository.findAllByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .filter(v -> toParsed.baseVersionString().equals(v.getVersion()))
                .filter(v -> v.getHotfixVersion() == 0)
                .filter(v -> (v.getBuildVersion() != null ? v.getBuildVersion() : 0)
                        == toParsed.buildVersion())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                        "To 커스텀 버전을 찾을 수 없습니다: " + toVersion));

        return generateCustomPatch(projectId, customerId, from, to,
                createdByEmail, description, assigneeId, patchName);
    }

    /**
     * 커스텀 패치 생성 (ReleaseVersion 기반)
     *
     * <p>fromVersion이 베이스 버전(STANDARD)인 경우와 커스텀 버전인 경우를 모두 지원합니다.
     */
    @Transactional
    public Patch generateCustomPatch(String projectId, Long customerId,
            ReleaseVersion fromVersion, ReleaseVersion toVersion,
            String createdByEmail, String description, Long assigneeId, String patchName) {
        try {
            // 프로젝트 조회
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND,
                            "프로젝트를 찾을 수 없습니다: " + projectId));

            // 고객사 조회
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND,
                            "고객사를 찾을 수 없습니다: " + customerId));

            // 핫픽스 버전은 패치 생성 대상이 아님
            if (fromVersion.isHotfix()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "핫픽스 버전은 패치 생성의 From 버전으로 사용할 수 없습니다: " + fromVersion.getVersion());
            }
            if (toVersion.isHotfix()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "핫픽스 버전은 패치 생성의 To 버전으로 사용할 수 없습니다: " + toVersion.getVersion());
            }

            // 1. 버전 검증
            validateCustomVersionRange(fromVersion, toVersion);

            // fromVersion이 베이스 버전인지 확인
            boolean isFromBaseVersion = isBaseVersion(fromVersion);

            // 2. 중간 버전 목록 조회 (fromVersion <= customVersion <= toVersion)
            // 빌드-only 패치 (같은 base): to 빌드 row 만 포함
            List<ReleaseVersion> betweenVersions;
            if (isSameBaseVersion(fromVersion, toVersion)) {
                betweenVersions = toVersion.isBuild() ? List.of(toVersion) : List.of();
            } else {
                // 베이스 버전에서 시작하는 경우 모든 커스텀 버전을 포함 (fromCustomVersion = "0.0.-1")
                String fromCustomVersionForQuery = isFromBaseVersion ? "0.0.-1" : fromVersion.getCustomVersion();
                List<ReleaseVersion> baseVersions = releaseVersionRepository.findCustomVersionsBetween(
                        customerId,
                        fromCustomVersionForQuery,
                        toVersion.getCustomVersion()
                );
                // to 가 빌드면 끝에 추가하여 WEB/ENGINE 가 to 빌드 파일이 되도록
                if (toVersion.isBuild() && !baseVersions.contains(toVersion)) {
                    List<ReleaseVersion> enriched = new ArrayList<>(baseVersions);
                    enriched.add(toVersion);
                    betweenVersions = enriched;
                } else {
                    betweenVersions = baseVersions;
                }
            }

            if (betweenVersions.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        String.format("From %s와 To %s 사이에 패치할 커스텀 버전이 없습니다.",
                                fromVersion.getFullVersion(), toVersion.getFullVersion()));
            }

            log.info("커스텀 패치 생성 시작 - Project: {}, Customer: {}, From: {}{}, To: {}, 포함 버전: {}",
                    projectId, customer.getCustomerCode(),
                    fromVersion.getVersion(), isFromBaseVersion ? " (베이스)" : "",
                    toVersion.getVersion(),
                    betweenVersions.stream().map(ReleaseVersion::getVersion).toList());

            // 3. 담당자 조회 (assigneeId가 있는 경우)
            Account assignee = null;
            if (assigneeId != null) {
                assignee = accountRepository.findByAccountId(assigneeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                                "담당자를 찾을 수 없습니다: " + assigneeId));
            }

            // 4. 패치 이름 결정 (전체 버전 형식 사용)
            String resolvedPatchName = resolvePatchName(patchName, fromVersion.getVersion(), toVersion.getVersion());

            // 5. 출력 디렉토리 생성 (커스텀 패치용)
            String outputPath = createCustomOutputDirectory(resolvedPatchName, projectId, customer.getCustomerCode());

            // 6. SQL 파일 복사 (커스텀 패치는 base 버전의 ReleaseFile 기반)
            copySqlFiles(betweenVersions, outputPath);

            // 7. 패치 스크립트 생성
            String assigneeEmail = assignee != null ? assignee.getEmail() : null;
            generatePatchScripts(fromVersion, toVersion, betweenVersions, outputPath, assigneeEmail);

            // 8. README 생성
            generateCustomReadme(fromVersion, toVersion, betweenVersions, outputPath, customer);
            // 커스텀 패치는 빌드 picker 미사용 → build_* 라인 없음
            generateBuildVersionFile(fromVersion, toVersion, outputPath, null, null);

            // 9. 생성자 Account 조회
            Account creator = accountLookupService.findByEmail(createdByEmail);

            // 10. 패치 저장 (전체 버전 형식 저장 — 빌드/핫픽스 정보 포함)
            Patch patch = Patch.builder()
                    .project(project)
                    .releaseType("CUSTOM")
                    .customer(customer)
                    .fromVersion(fromVersion.getFullVersion())
                    .toVersion(toVersion.getFullVersion())
                    .patchName(resolvedPatchName)
                    .outputPath(outputPath)
                    .creator(creator)
                    .createdByEmail(creator.getEmail())
                    .description(description)
                    .assignee(assignee)
                    .build();

            Patch saved = patchRepository.save(patch);

            // 10. 패치 이력 저장 (영구 보존)
            savePatchHistory(saved);

            // 11. CustomerProject 마지막 패치 정보 업데이트
            updateCustomerProjectPatchInfo(customer, project, toVersion.getVersion());

            log.info("커스텀 패치 생성 완료 - ID: {}, Path: {}", saved.getPatchId(), outputPath);

            return saved;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("커스텀 패치 생성 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "커스텀 패치 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 커스텀 버전 범위 검증
     *
     * <p>fromVersion이 베이스 버전(STANDARD)인 경우와 커스텀 버전인 경우를 구분하여 처리합니다.
     */
    private void validateCustomVersionRange(ReleaseVersion fromVersion, ReleaseVersion toVersion) {
        boolean isFromBaseVersion = isBaseVersion(fromVersion);

        if (isFromBaseVersion) {
            // 베이스 버전에서 시작하는 경우:
            // - 베이스 버전이 toVersion의 customBaseVersion과 일치하는지 확인
            if (toVersion.getCustomBaseVersion() == null ||
                    !fromVersion.getReleaseVersionId().equals(toVersion.getCustomBaseVersion().getReleaseVersionId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        String.format("From 베이스 버전(%s)은 To 버전(%s)의 기반 버전이어야 합니다.",
                                fromVersion.getVersion(), toVersion.getVersion()));
            }
            // 베이스 버전에서 시작하므로 모든 커스텀 버전이 더 높은 버전임 - 추가 검증 불필요

        } else {
            // 커스텀 버전에서 시작하는 경우:
            // 버전 비교: fromCustomVersion < toCustomVersion
            if (compareCustomVersions(fromVersion, toVersion) >= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        String.format("From 버전은 To 버전보다 작아야 합니다. (From: %s, To: %s)",
                                fromVersion.getVersion(), toVersion.getVersion()));
            }

            // 같은 고객사 검증
            if (!fromVersion.getCustomer().getCustomerId().equals(toVersion.getCustomer().getCustomerId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "From 버전과 To 버전은 같은 고객사의 버전이어야 합니다.");
            }
        }

        // 미승인 버전 검증 (커스텀 버전 범위 내)
        // 베이스 버전에서 시작하는 경우 fromCustomVersion은 "0.0.0" 이전이므로 모든 커스텀 버전 포함
        String fromCustomVersion = isFromBaseVersion ? "0.0.-1" : fromVersion.getCustomVersion();
        List<ReleaseVersion> unapprovedVersions = releaseVersionRepository.findUnapprovedCustomVersionsBetween(
                toVersion.getCustomer().getCustomerId(),
                fromCustomVersion,
                toVersion.getCustomVersion()
        );

        if (!unapprovedVersions.isEmpty()) {
            String unapprovedVersionList = unapprovedVersions.stream()
                    .map(ReleaseVersion::getVersion)
                    .reduce((v1, v2) -> v1 + ", " + v2)
                    .orElse("");

            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("버전 범위 내에 미승인 버전이 존재합니다. 패치를 생성할 수 없습니다. (미승인 버전: %s)",
                            unapprovedVersionList));
        }
    }

    /**
     * 베이스 버전(STANDARD) 여부 확인
     */
    private boolean isBaseVersion(ReleaseVersion version) {
        return "STANDARD".equals(version.getReleaseType()) || version.getCustomMajorVersion() == null;
    }

    /**
     * 커스텀 버전 비교 (v1 < v2 이면 -1, v1 == v2 이면 0, v1 > v2 이면 1)
     *
     * <p>두 버전 모두 커스텀 버전이어야 합니다.
     */
    private int compareCustomVersions(ReleaseVersion v1, ReleaseVersion v2) {
        // null 체크 - 베이스 버전인 경우 customMajorVersion이 null
        if (v1.getCustomMajorVersion() == null || v2.getCustomMajorVersion() == null) {
            throw new IllegalArgumentException("커스텀 버전 비교는 두 버전 모두 커스텀 버전이어야 합니다.");
        }

        if (!v1.getCustomMajorVersion().equals(v2.getCustomMajorVersion())) {
            return Integer.compare(v1.getCustomMajorVersion(), v2.getCustomMajorVersion());
        }
        if (!v1.getCustomMinorVersion().equals(v2.getCustomMinorVersion())) {
            return Integer.compare(v1.getCustomMinorVersion(), v2.getCustomMinorVersion());
        }
        if (!v1.getCustomPatchVersion().equals(v2.getCustomPatchVersion())) {
            return Integer.compare(v1.getCustomPatchVersion(), v2.getCustomPatchVersion());
        }
        // 같은 커스텀 triple → 빌드 비교 (빌드 도입 후 추가)
        int b1 = v1.getBuildVersion() != null ? v1.getBuildVersion() : 0;
        int b2 = v2.getBuildVersion() != null ? v2.getBuildVersion() : 0;
        return Integer.compare(b1, b2);
    }

    /**
     * 커스텀 패치 출력 디렉토리 생성
     */
    private String createCustomOutputDirectory(String patchName, String projectId, String customerCode) {
        try {
            // 출력 경로: patches/{projectId}/custom/{customerCode}/{patchName}
            String relativePath = String.format("patches/%s/custom/%s/%s", projectId, customerCode, patchName);

            Path outputDir = Paths.get(releaseBasePath, relativePath);
            Files.createDirectories(outputDir);

            log.info("커스텀 패치 출력 디렉토리 생성 완료: {}", outputDir.toAbsolutePath());

            return relativePath;

        } catch (IOException e) {
            log.error("커스텀 패치 출력 디렉토리 생성 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "커스텀 패치 출력 디렉토리 생성 실패");
        }
    }

    /**
     * 커스텀 패치 README.md 생성
     *
     * <p>fromVersion이 베이스 버전(STANDARD)인 경우와 커스텀 버전인 경우를 모두 지원합니다.
     */
    private void generateCustomReadme(ReleaseVersion fromVersion, ReleaseVersion toVersion,
            List<ReleaseVersion> includedVersions, String outputPath, Customer customer) {
        try {
            Path readmePath = Paths.get(releaseBasePath, outputPath, "README.md");

            // VERSION 라인은 base/custom 구분 없이 version 필드 그대로,
            // Build VERSION 라인은 to 가 빌드 행일 때만 표시
            String fromBaseLabel = isBaseVersion(fromVersion) ? " (베이스)" : "";
            String buildVersionStr = toVersion.isBuild() ? toVersion.getFullVersion() : null;
            String includedStr = includedVersions.stream()
                    .map(ReleaseVersion::getVersion)
                    .distinct()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            StringBuilder content = new StringBuilder();
            content.append("# 생성 정보\n");
            content.append(String.format("- 패치 생성일시: %s\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            content.append(String.format("- 고객사: %s (%s)\n", customer.getCustomerName(), customer.getCustomerCode()));
            content.append(String.format("- VERSION: %s%s -> %s\n",
                    fromVersion.getVersion(), fromBaseLabel, toVersion.getVersion()));
            if (buildVersionStr != null) {
                content.append(String.format("- Build VERSION: %s\n", buildVersionStr));
            }
            content.append(String.format("- 포함된 버전: %s\n\n", includedStr));

            content.append("## 패치 방법\n");
            content.append("1. `InfraEye info version` — 사이트 버전 확인 (사전)\n");
            content.append("2. 본 패치 파일을 `/{설치경로}/infraeye/patch/` 에 복사 후 압축 해제\n");
            content.append("3. `InfraEye db patch` — DB 패치 (mariadb / cratedb)\n");
            content.append("4. `InfraEye was patch` — WAS 패치\n");
            content.append("5. `InfraEye eng patch` — 엔진 패치\n");
            content.append("6. `InfraEye info version` — 변경된 사이트 버전 확인 (사후)\n\n");

            content.append("## 주의\n");
            content.append("- 실행 전 반드시 백업 수행\n");
            content.append("- 오류 발생 시 패치 디렉토리의 `logs/` 확인\n\n");

            content.append("---\nCREATED BY - Release Manager (Custom Patch)\n");

            Files.writeString(readmePath, content.toString());

            log.info("커스텀 패치 README.md 생성 완료: {}", readmePath);

        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "README 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 패치 생성 (버전 ID 기반)
     *
     * @param projectId      프로젝트 ID
     * @param fromVersionId  From 버전 ID
     * @param toVersionId    To 버전 ID
     * @param customerId     고객사 ID (선택)
     * @param createdByEmail 생성자
     * @param description    설명 (선택)
     * @param assigneeId     패치 담당자 ID (선택)
     * @param patchName      패치 이름 (선택, 미입력 시 자동 생성)
     * @param buildSelection 빌드 파일 선택 (null 이면 빌드 미포함)
     * @return 생성된 패치
     */
    @Transactional
    public GenerateResult generatePatch(String projectId, Long fromVersionId, Long toVersionId, Long customerId,
            String createdByEmail, String description, Long assigneeId, String patchName,
            PatchDto.BuildSelection buildSelection) {
        try {
            // 프로젝트 조회
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND,
                            "프로젝트를 찾을 수 없습니다: " + projectId));

            // 1. 버전 조회 및 검증
            ReleaseVersion fromVersion = releaseVersionRepository.findById(fromVersionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                            "From 버전을 찾을 수 없습니다: " + fromVersionId));

            ReleaseVersion toVersion = releaseVersionRepository.findById(toVersionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                            "To 버전을 찾을 수 없습니다: " + toVersionId));

            // 핫픽스 버전은 패치 생성 대상이 아님
            if (fromVersion.isHotfix()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "핫픽스 버전은 패치 생성의 From 버전으로 사용할 수 없습니다: " + fromVersion.getVersion());
            }
            if (toVersion.isHotfix()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "핫픽스 버전은 패치 생성의 To 버전으로 사용할 수 없습니다: " + toVersion.getVersion());
            }

            validateVersionRange(fromVersion, toVersion);

            // 2. 중간 버전 목록 조회 (fromVersion <= version <= toVersion, 빌드 인식)
            List<ReleaseVersion> betweenVersions = collectBetweenVersionsWithBuild(
                    projectId, fromVersion, toVersion);

            if (betweenVersions.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        String.format("From %s와 To %s 사이에 패치할 버전이 없습니다.",
                                fromVersion.getFullVersion(), toVersion.getFullVersion()));
            }

            log.info("패치 생성 시작 - Project: {}, From: {}, To: {}, 포함 버전: {}",
                    projectId, fromVersion.getFullVersion(), toVersion.getFullVersion(),
                    betweenVersions.stream().map(ReleaseVersion::getFullVersion).toList());

            // 3. 고객사 조회 (customerId가 있는 경우)
            Customer customer = null;
            if (customerId != null) {
                customer = customerRepository.findById(customerId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND,
                                "고객사를 찾을 수 없습니다: " + customerId));
            }

            // 3-1. 담당자 조회 (assigneeId가 있는 경우)
            Account assignee = null;
            if (assigneeId != null) {
                assignee = accountRepository.findByAccountId(assigneeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                                "담당자를 찾을 수 없습니다: " + assigneeId));
            }

            // 4. 패치 이름 결정 (입력값이 없으면 자동 생성: YYYYMMDDHHMMSS_fromversion_toversion)
            String resolvedPatchName = resolvePatchName(patchName, fromVersion.getVersion(), toVersion.getVersion());

            // 5. 출력 디렉토리 생성 (패치 이름으로)
            String outputPath = createOutputDirectory(resolvedPatchName, projectId);

            // 6. SQL 파일 복사 (base 버전의 ReleaseFile 기반 - 빌드는 buildSelection 단계에서 처리)
            copySqlFiles(betweenVersions, outputPath);

            // ---- buildSelection 별도 단계 (spec §5.1 / Q-S2) ----
            Map<Long, ReleaseVersion> selectedBuilds;
            if (buildSelection != null && buildSelection.enabled()) {
                selectedBuilds = applyBuildSelection(Paths.get(releaseBasePath, outputPath), buildSelection);
            } else {
                selectedBuilds = Map.of();
            }

            // 7. 패치 스크립트 생성
            String assigneeEmail = assignee != null ? assignee.getEmail() : null;
            generatePatchScripts(fromVersion, toVersion, betweenVersions, outputPath, assigneeEmail);

            // 8. README / 빌드 메타 생성
            // README 의 To 표기에는 web 빌드 fullVersion 만 사용. .build_version 메타파일에는 web + 모든 engine 기록.
            ReleaseVersion webBuildForMeta = null;
            if (buildSelection != null && buildSelection.enabled() && buildSelection.web() != null) {
                webBuildForMeta = selectedBuilds.get(buildSelection.web().buildVersionId());
            }
            generateReadme(fromVersion, toVersion, betweenVersions, outputPath, webBuildForMeta);
            generateBuildVersionFile(fromVersion, toVersion, outputPath, buildSelection, selectedBuilds);

            // 9. 생성자 Account 조회
            Account creator = accountLookupService.findByEmail(createdByEmail);

            // 10. 패치 저장 (빌드/핫픽스 정보 포함된 fullVersion 저장)
            Patch patch = Patch.builder()
                    .project(project)
                    .releaseType(fromVersion.getReleaseType())
                    .customer(customer)
                    .fromVersion(fromVersion.getFullVersion())
                    .toVersion(toVersion.getFullVersion())
                    .patchName(resolvedPatchName)
                    .outputPath(outputPath)
                    .creator(creator)
                    .createdByEmail(creator.getEmail())
                    .description(description)
                    .assignee(assignee)
                    .build();

            Patch saved = patchRepository.save(patch);

            // 10. 패치 이력 저장 (영구 보존)
            savePatchHistory(saved);

            // 11. CustomerProject 마지막 패치 정보 업데이트 (고객사가 지정된 경우)
            if (customer != null) {
                updateCustomerProjectPatchInfo(customer, project, toVersion.getVersion());
            }

            log.info("패치 생성 완료 - ID: {}, Path: {}", saved.getPatchId(),
                    outputPath);

            // 12. 메타 영구 저장 + 캐시 boolean 갱신 (spec §5.1)
            // findHotfixesInBaseRange 는 단 1회 호출하여 메타 저장과 응답 매핑이 공유
            boolean isBuildOnly = isSameBaseVersion(fromVersion, toVersion);
            List<ReleaseVersion> hotfixVersions = releaseVersionRepository
                    .findHotfixesInBaseRange(projectId, fromVersionId, toVersionId, customerId);

            saved.setIsBuildOnly(isBuildOnly);
            saved.setIsBuildIncluded(buildSelection != null && buildSelection.enabled());
            persistIncludedBuilds(saved, buildSelection, selectedBuilds);
            persistHotfixesInRange(saved, hotfixVersions);
            saved = patchRepository.save(saved);  // cascade 로 메타 행도 저장됨

            // 응답용 hotfixesInRange / includedBuilds 매핑
            List<PatchDto.HotfixInRangeInfo> hotfixes = hotfixVersions.stream()
                    .map(h -> new PatchDto.HotfixInRangeInfo(h.getReleaseVersionId(), h.getFullVersion()))
                    .toList();
            PatchDto.IncludedBuilds includedBuilds = buildIncludedBuilds(buildSelection, selectedBuilds);

            return new GenerateResult(saved, isBuildOnly, hotfixes, includedBuilds);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("패치 생성 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "패치 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 응답 보강용 IncludedBuilds 생성. selectedBuilds 캐시 (applyBuildSelection 결과) 를 활용해
     * loadBuildVersion 중복 호출을 피한다.
     *
     * @param sel           빌드 선택 (null 또는 enabled=false 면 WEB/engines 모두 비어있는 객체 반환)
     * @param selectedBuilds applyBuildSelection 이 반환한 buildVersionId → ReleaseVersion 캐시
     * @return IncludedBuilds
     */
    private PatchDto.IncludedBuilds buildIncludedBuilds(PatchDto.BuildSelection sel,
                                                        Map<Long, ReleaseVersion> selectedBuilds) {
        if (sel == null || !sel.enabled()) {
            return new PatchDto.IncludedBuilds(null, List.of());
        }
        PatchDto.IncludedWeb web = null;
        if (sel.web() != null) {
            ReleaseVersion bv = selectedBuilds.get(sel.web().buildVersionId());
            if (bv == null) bv = loadBuildVersion(sel.web().buildVersionId());
            web = new PatchDto.IncludedWeb(bv.getReleaseVersionId(), bv.getFullVersion());
        }
        List<PatchDto.IncludedEngine> engines = new ArrayList<>();
        if (sel.engines() != null) {
            for (PatchDto.SelectedEngine se : sel.engines()) {
                ReleaseVersion bv = selectedBuilds.get(se.buildVersionId());
                if (bv == null) bv = loadBuildVersion(se.buildVersionId());
                engines.add(new PatchDto.IncludedEngine(se.engineName(), bv.getReleaseVersionId(), bv.getFullVersion()));
            }
        }
        return new PatchDto.IncludedBuilds(web, engines);
    }

    /**
     * 패치 이름 결정
     *
     * @param patchName   입력된 패치 이름 (nullable)
     * @param fromVersion From 버전
     * @param toVersion   To 버전
     * @return 최종 패치 이름 (형식: YYYYMMDDHHmm_fromVersion_toVersion)
     */
    private String resolvePatchName(String patchName, String fromVersion, String toVersion) {
        if (StringUtils.hasText(patchName)) {
            return patchName;
        }
        // 기본값: 날짜시분_fromversion_toversion (예: 202511271430_1.0.0_1.1.1)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return String.format("%s_%s_%s", timestamp, fromVersion, toVersion);
    }

    /**
     * 버전 범위 검증
     */
    private void validateVersionRange(ReleaseVersion fromVersion, ReleaseVersion toVersion) {
        // 버전 비교: fromVersion < toVersion
        if (compareVersions(fromVersion, toVersion) >= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("From 버전은 To 버전보다 작아야 합니다. (From: %s, To: %s)",
                            fromVersion.getVersion(), toVersion.getVersion()));
        }

        // 릴리즈 타입 일치 검증
        if (!fromVersion.getReleaseType().equals(toVersion.getReleaseType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "From 버전과 To 버전의 릴리즈 타입이 일치하지 않습니다.");
        }

        // 임시버전(미승인 버전) 검증
        // 프로젝트 ID가 필요하므로 from 또는 to 버전에서 추출
        String projectIdForValidation = fromVersion.getProject().getProjectId();
        List<ReleaseVersion> unapprovedVersions = releaseVersionRepository.findUnapprovedVersionsBetween(
                projectIdForValidation,
                fromVersion.getReleaseType(),
                fromVersion.getVersion(),
                toVersion.getVersion()
        );

        if (!unapprovedVersions.isEmpty()) {
            String unapprovedVersionList = unapprovedVersions.stream()
                    .map(ReleaseVersion::getVersion)
                    .reduce((v1, v2) -> v1 + ", " + v2)
                    .orElse("");

            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("버전 범위 내에 미승인 버전이 존재합니다. 패치를 생성할 수 없습니다. (미승인 버전: %s)",
                            unapprovedVersionList));
        }
    }

    /**
     * 두 버전이 같은 base (major.minor.patch) 인지 확인.
     *
     * <p>빌드만 다른 패치(예: 1.1.0 → 1.1.0.260427) 판정에 사용.
     */
    private boolean isSameBaseVersion(ReleaseVersion v1, ReleaseVersion v2) {
        return java.util.Objects.equals(v1.getMajorVersion(), v2.getMajorVersion())
                && java.util.Objects.equals(v1.getMinorVersion(), v2.getMinorVersion())
                && java.util.Objects.equals(v1.getPatchVersion(), v2.getPatchVersion());
    }

    /**
     * 표준 패치용 betweenVersions 수집 (빌드 인식).
     *
     * <ul>
     *   <li>{@code from} 과 {@code to} 가 같은 base 인 경우 (빌드-only 패치):
     *       to 빌드 row 만 포함하여 base 의 DB SQL 미포함 보장</li>
     *   <li>그 외, to 가 빌드면 base versions 끝에 추가 → 마지막 버전의 WEB/ENGINE 가 to 빌드의 파일이 됨</li>
     * </ul>
     */
    private List<ReleaseVersion> collectBetweenVersionsWithBuild(
            String projectId, ReleaseVersion fromVersion, ReleaseVersion toVersion) {
        if (isSameBaseVersion(fromVersion, toVersion)) {
            // 빌드-only: to 빌드만 (둘 다 base/같은 build 는 validateVersionRange 에서 거름)
            return toVersion.isBuild() ? List.of(toVersion) : List.of();
        }

        List<ReleaseVersion> baseVersions = releaseVersionRepository.findVersionsBetween(
                projectId,
                fromVersion.getReleaseType(),
                fromVersion.getVersion(),
                toVersion.getVersion()
        );

        if (toVersion.isBuild() && !baseVersions.contains(toVersion)) {
            List<ReleaseVersion> enriched = new ArrayList<>(baseVersions);
            enriched.add(toVersion);
            return enriched;
        }
        return baseVersions;
    }

    /**
     * 버전 비교 (v1 &lt; v2 이면 -1, v1 == v2 이면 0, v1 &gt; v2 이면 1)
     *
     * <p>4단 비교: major.minor.patch.build 순. build_version 이 null 이면 0 으로 취급.
     *
     * <p>예시: 1.1.0 &lt; 1.1.0.260427 &lt; 1.1.0.260428 &lt; 1.1.1
     */
    private int compareVersions(ReleaseVersion v1, ReleaseVersion v2) {
        if (!java.util.Objects.equals(v1.getMajorVersion(), v2.getMajorVersion())) {
            return Integer.compare(v1.getMajorVersion(), v2.getMajorVersion());
        }
        if (!java.util.Objects.equals(v1.getMinorVersion(), v2.getMinorVersion())) {
            return Integer.compare(v1.getMinorVersion(), v2.getMinorVersion());
        }
        if (!java.util.Objects.equals(v1.getPatchVersion(), v2.getPatchVersion())) {
            return Integer.compare(v1.getPatchVersion(), v2.getPatchVersion());
        }
        int b1 = v1.getBuildVersion() != null ? v1.getBuildVersion() : 0;
        int b2 = v2.getBuildVersion() != null ? v2.getBuildVersion() : 0;
        return Integer.compare(b1, b2);
    }

    /**
     * 입력 버전 문자열 파싱 결과 (lookup 에서 사용).
     *
     * @param baseVersionString 룩업의 {@code version} 컬럼 값 (예: "1.1.0", "1.1.0-companyA.1.0.0")
     * @param buildVersion      빌드 버전 (없으면 0)
     */
    record ParsedInputVersion(String baseVersionString, int buildVersion) {}

    /**
     * 입력 버전 문자열 파싱.
     *
     * <p>지원 포맷:
     * <ul>
     *   <li>표준 일반: {@code "1.1.0"} → base="1.1.0", build=0</li>
     *   <li>표준 빌드: {@code "1.1.0.260427"} → base="1.1.0", build=260427</li>
     *   <li>커스텀 일반: {@code "1.1.0-companyA.1.0.0"} → base="1.1.0-companyA.1.0.0", build=0</li>
     *   <li>커스텀 빌드: {@code "1.1.0-companyA.1.0.0.260427"} → base="1.1.0-companyA.1.0.0", build=260427</li>
     * </ul>
     */
    static ParsedInputVersion parseInputVersion(String input) {
        if (input == null || input.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_VERSION_FORMAT, "버전이 비어있습니다.");
        }
        int dashIndex = input.indexOf('-');
        if (dashIndex < 0) {
            // 표준 버전
            String[] parts = input.split("\\.");
            if (parts.length == 3) {
                return new ParsedInputVersion(input, 0);
            }
            if (parts.length == 4) {
                String base = parts[0] + "." + parts[1] + "." + parts[2];
                return new ParsedInputVersion(base, parseInt(parts[3], input));
            }
            throw new BusinessException(ErrorCode.INVALID_VERSION_FORMAT,
                    "표준 버전 형식이 잘못되었습니다 (예상 3 또는 4파트): " + input);
        }
        // 커스텀 버전
        String afterDash = input.substring(dashIndex + 1);
        String[] customParts = afterDash.split("\\.");
        // customParts: [customerCode, customMaj, customMin, customPatch] (4) 또는 + build (5)
        if (customParts.length == 4) {
            return new ParsedInputVersion(input, 0);
        }
        if (customParts.length == 5) {
            String base = input.substring(0, input.lastIndexOf('.'));
            return new ParsedInputVersion(base, parseInt(customParts[4], input));
        }
        throw new BusinessException(ErrorCode.INVALID_VERSION_FORMAT,
                "커스텀 버전 형식이 잘못되었습니다 (예상 4 또는 5 파트): " + input);
    }

    private static int parseInt(String s, String fullInput) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_VERSION_FORMAT,
                    "버전 숫자 파싱 실패: " + fullInput);
        }
    }

    /**
     * 출력 디렉토리 생성
     *
     * @param patchName 패치 이름 (디렉토리명으로 사용)
     * @return 상대 경로 (예: patches/{projectId}/20251127143025_1.0.0_1.1.1)
     */
    private String createOutputDirectory(String patchName, String projectId) {
        try {
            // 출력 경로: patches/{projectId}/{patchName}
            String relativePath = String.format("patches/%s/%s", projectId, patchName);

            Path outputDir = Paths.get(releaseBasePath, relativePath);

            // 루트 디렉토리만 생성 (하위 디렉토리는 파일 복사 시 동적 생성)
            Files.createDirectories(outputDir);

            log.info("출력 디렉토리 생성 완료: {}", outputDir.toAbsolutePath());

            return relativePath;

        } catch (IOException e) {
            log.error("출력 디렉토리 생성 실패: releaseBasePath={}, projectId={}, patchName={}",
                    releaseBasePath, projectId, patchName, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "출력 디렉토리 생성 실패: " + releaseBasePath + "/patches/" + projectId + "/" + patchName);
        }
    }

    /**
     * 모든 파일 복사 (버전별 디렉토리 구조 유지)
     *
     * <p>빌드 버전은 versions[] 루프에서 skip 한다. 빌드 파일 복사는 buildSelection 별도 단계에서 처리.
     * <p>⚠️ WEB 카테고리는 해당 파일이 있는 마지막 base 버전만 포함됩니다.
     * <p>⚠️ ENGINE 카테고리는 sub_category(NC_SMS, NC_FAULT_MS 등)별로 각각 마지막 base 버전만 포함됩니다.
     *
     * @param versions   복사할 버전 목록
     * @param outputPath 출력 경로
     */
    private void copySqlFiles(List<ReleaseVersion> versions, String outputPath) {
        try {
            Path outputDir = Paths.get(releaseBasePath, outputPath);

            // ENGINE: sub_category → 해당 sub_category 파일이 있는 마지막 base 버전 ID
            Long lastVersionIdForWeb = null;
            Map<String, Long> lastVersionIdByEngineSubCategory = new HashMap<>();

            if (!versions.isEmpty()) {
                // 모든 버전의 파일을 역순으로 조회하여 마지막 base 버전 찾기
                for (int i = versions.size() - 1; i >= 0; i--) {
                    ReleaseVersion v = versions.get(i);
                    if (v.isBuild()) {
                        continue;  // 빌드는 picker 입력 단계에서 별도 처리
                    }

                    List<ReleaseFile> files = releaseFileRepository
                            .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(v.getReleaseVersionId());

                    for (ReleaseFile file : files) {
                        if (file.getFileCategory() == null) continue;

                        // WEB: 아직 찾지 못했으면 이 버전이 마지막
                        if (lastVersionIdForWeb == null && file.getFileCategory() == FileCategory.WEB) {
                            lastVersionIdForWeb = v.getReleaseVersionId();
                            log.info("WEB 카테고리는 버전 {}의 파일만 포함됩니다.", v.getVersion());
                        }

                        // ENGINE: sub_category별로 아직 찾지 못했으면 이 버전이 해당 sub_category의 마지막
                        if (file.getFileCategory() == FileCategory.ENGINE) {
                            String subCategory = file.getSubCategory() != null ? file.getSubCategory() : "ETC";
                            if (!lastVersionIdByEngineSubCategory.containsKey(subCategory)) {
                                lastVersionIdByEngineSubCategory.put(subCategory, v.getReleaseVersionId());
                                log.info("ENGINE/{} 카테고리는 버전 {}의 파일만 포함됩니다.", subCategory, v.getVersion());
                            }
                        }
                    }
                }
            }

            for (ReleaseVersion version : versions) {
                if (version.isBuild()) {
                    continue;  // 빌드는 picker 입력 단계에서 별도 처리 (§5.3 Q-S2)
                }

                // 모든 파일 조회
                List<ReleaseFile> files = releaseFileRepository
                        .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(
                                version.getReleaseVersionId());

                if (files.isEmpty()) {
                    log.warn("버전 {}의 패치 대상 파일이 없습니다.", version.getVersion());
                    continue;
                }

                int copiedCount = 0;
                int skippedBuildCount = 0;

                for (ReleaseFile file : files) {
                    // WEB/ENGINE 카테고리 필터링 (base 버전 기반)
                    if (file.getFileCategory() != null) {
                        boolean shouldSkip = false;

                        // WEB: 마지막 base 버전이 아니면 건너뛰기
                        if (file.getFileCategory() == FileCategory.WEB) {
                            if (lastVersionIdForWeb == null
                                    || !version.getReleaseVersionId().equals(lastVersionIdForWeb)) {
                                shouldSkip = true;
                            }
                        }

                        // ENGINE: 해당 sub_category의 마지막 base 버전이 아니면 건너뛰기
                        if (file.getFileCategory() == FileCategory.ENGINE) {
                            String subCategory = file.getSubCategory() != null ? file.getSubCategory() : "ETC";
                            Long lastVersionId = lastVersionIdByEngineSubCategory.get(subCategory);
                            if (lastVersionId == null
                                    || !version.getReleaseVersionId().equals(lastVersionId)) {
                                shouldSkip = true;
                            }
                        }

                        if (shouldSkip) {
                            skippedBuildCount++;
                            continue;
                        }
                    }
                    copyFileByCategory(file, version, outputDir);
                    copiedCount++;
                }

                if (skippedBuildCount > 0) {
                    log.info("버전 {} 파일 복사 완료 - {}개 (WEB/ENGINE 빌드 파일 {}개 건너뜀)",
                            version.getVersion(), copiedCount, skippedBuildCount);
                } else {
                    log.info("버전 {} 파일 복사 완료 - {}개", version.getVersion(), copiedCount);
                }
            }

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "파일 복사 실패: " + e.getMessage());
        }
    }

    /**
     * picker 입력에 따라 빌드 디렉토리에서 web/, engine/{engineName} 파일을 outputDir 로 복사.
     *
     * <p>빌드의 etc 는 더 이상 다루지 않는다 — 운영자 자산은 release version 의 ETC ReleaseFile
     * 로 등록되어 patch 의 etc/{version}/* 으로 자연 포함된다 (DATABASE 카테고리와 동일 패턴).
     *
     * <p>반환 selectedBuilds map 은 buildIncludedBuilds / persistIncludedBuilds 가
     * 동일한 ReleaseVersion 객체를 재사용할 수 있도록 호출자에게 노출한다.
     *
     * @return buildVersionId → ReleaseVersion 매핑 (insertion order 유지).
     */
    private Map<Long, ReleaseVersion> applyBuildSelection(Path outputDir, PatchDto.BuildSelection sel) throws IOException {
        log.info("picker 복사 시작 - WEB: {}, ENGINE: {}",
                sel.web() == null ? "(없음)" : sel.web().buildVersionId(),
                sel.engines() == null ? List.of() : sel.engines());

        Map<Long, ReleaseVersion> selectedBuilds = new LinkedHashMap<>();

        // a. WEB 부분 복사
        if (sel.web() != null) {
            ReleaseVersion bv = loadBuildVersion(sel.web().buildVersionId());
            Path src = fileSystemService.resolveBuildBasePath(bv).resolve("web");
            copyDirectoryReplaceExisting(src, outputDir.resolve("web"));
            selectedBuilds.put(bv.getReleaseVersionId(), bv);
        }

        // b. ENGINE 부분 복사 (단일 파일)
        if (sel.engines() != null) {
            for (PatchDto.SelectedEngine se : sel.engines()) {
                ReleaseVersion bv = loadBuildVersion(se.buildVersionId());
                Path src = fileSystemService.resolveBuildBasePath(bv)
                        .resolve("engine").resolve(se.engineName());
                if (!Files.isRegularFile(src)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                            "엔진 빌드 파일이 존재하지 않습니다: " + src);
                }
                Path dst = outputDir.resolve("engine").resolve(se.engineName());
                Files.createDirectories(dst.getParent());
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                // 실행 비트 보존: source 의 posix permission 을 dst 에 복사 (POSIX FS 일 때만)
                try {
                    var perms = Files.getPosixFilePermissions(src);
                    Files.setPosixFilePermissions(dst, perms);
                } catch (UnsupportedOperationException ignored) {
                    // 비-POSIX FS (Windows) 는 setExecutable 폴백
                    dst.toFile().setExecutable(true, false);
                }
                selectedBuilds.putIfAbsent(bv.getReleaseVersionId(), bv);
            }
        }

        return selectedBuilds;
    }

    /**
     * picker 로 선택된 빌드들을 PatchIncludedBuild 로 저장. patch 의 양방향 관계를 통해 cascade.
     *
     * <p>kind 명세 (spec §3.1):
     * <ul>
     *   <li>WEB: kind="WEB", engine_name=NULL</li>
     *   <li>ENGINE: kind="ENGINE", engine_name=engineName</li>
     * </ul>
     */
    private void persistIncludedBuilds(Patch patch, PatchDto.BuildSelection sel,
                                       Map<Long, ReleaseVersion> selectedBuilds) {
        if (sel == null || !sel.enabled()) return;
        if (sel.web() != null) {
            ReleaseVersion bv = selectedBuilds.get(sel.web().buildVersionId());
            if (bv == null) bv = loadBuildVersion(sel.web().buildVersionId());
            PatchIncludedBuild row = PatchIncludedBuild.builder()
                    .kind("WEB")
                    .engineName(null)
                    .buildVersion(bv)
                    .fullVersion(bv.getFullVersion())
                    .build();
            patch.addIncludedBuild(row);
        }
        if (sel.engines() != null) {
            for (PatchDto.SelectedEngine se : sel.engines()) {
                ReleaseVersion bv = selectedBuilds.get(se.buildVersionId());
                if (bv == null) bv = loadBuildVersion(se.buildVersionId());
                PatchIncludedBuild row = PatchIncludedBuild.builder()
                        .kind("ENGINE")
                        .engineName(se.engineName())
                        .buildVersion(bv)
                        .fullVersion(bv.getFullVersion())
                        .build();
                patch.addIncludedBuild(row);
            }
        }
    }

    /**
     * 범위 안의 핫픽스를 PatchHotfixInRange 로 저장. patch 의 양방향 관계를 통해 cascade.
     */
    private void persistHotfixesInRange(Patch patch, List<ReleaseVersion> hotfixesInRange) {
        for (ReleaseVersion h : hotfixesInRange) {
            PatchHotfixInRange row = PatchHotfixInRange.builder()
                    .hotfixVersion(h)
                    .fullVersion(h.getFullVersion())
                    .hotfixVersionNumber(h.getHotfixVersion())
                    .build();
            patch.addHotfixInRange(row);
        }
    }

    private ReleaseVersion loadBuildVersion(Long buildVersionId) {
        return releaseVersionRepository.findById(buildVersionId)
                .filter(ReleaseVersion::isBuild)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        "선택한 빌드 버전을 찾을 수 없습니다: " + buildVersionId));
    }

    /**
     * source 디렉토리 전체를 target 으로 복사 (REPLACE_EXISTING).
     * source 가 존재하지 않으면 no-op.
     */
    private void copyDirectoryReplaceExisting(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            stream.forEach(p -> {
                try {
                    Path rel = source.relativize(p);
                    Path dst = target.resolve(rel.toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new BuildFileCopyException(p, e);
                }
            });
        } catch (BuildFileCopyException e) {
            throw new IOException("빌드 파일 복사 실패: " + e.sourcePath, e);
        }
    }

    private static class BuildFileCopyException extends RuntimeException {
        private final Path sourcePath;

        private BuildFileCopyException(Path sourcePath, IOException cause) {
            super(cause);
            this.sourcePath = sourcePath;
        }
    }

    /**
     * 개별 파일 복사 (카테고리 기반)
     * <p>Phase 5: 파일 카테고리별 디렉토리 구조 생성
     */
    private void copyFileByCategory(ReleaseFile file, ReleaseVersion version, Path outputDir) {
        try {
            // 원본 파일 경로
            Path sourcePath = Paths.get(releaseBasePath, file.getFilePath());

            if (!Files.exists(sourcePath)) {
                log.warn("파일이 존재하지 않습니다: {}", sourcePath);
                return;
            }

            // 대상 파일 경로 결정 (카테고리별)
            Path targetPath = determineTargetPath(file, version, outputDir);

            // 대상 디렉토리 생성
            Files.createDirectories(targetPath.getParent());

            // 파일 복사
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.debug("파일 복사: {} -> {}", sourcePath.getFileName(), targetPath);

        } catch (IOException e) {
            log.error("파일 복사 실패: {}", file.getFileName(), e);
        }
    }

    /**
     * 대상 파일 경로 결정 (카테고리 기반)
     * <p>디렉토리 구조:
     * <ul>
     *   <li>DATABASE: database/{db_type}/{version}/{file_name}</li>
     *   <li>WEB: web/{version}/{file_name}</li>
     *   <li>ENGINE: engine/{sub_category} (단일 파일, sub_category 가 엔진명)</li>
     *   <li>그 외 (ETC / CONFIG / RESOURCE / null): etc/{version}/{file_name}</li>
     * </ul>
     * etc 도 DATABASE 와 동일하게 버전별 디렉토리 구조를 가져 모든 포함 버전의 파일이 보존된다.
     */
    private Path determineTargetPath(ReleaseFile file, ReleaseVersion version, Path outputDir) {
        FileCategory category = file.getFileCategory();

        if (category == null) {
            return outputDir.resolve(
                    String.format("etc/%s/%s",
                            version.getVersion(),
                            file.getFileName())
            );
        }

        switch (category) {
            case DATABASE:
                // sub_category를 소문자로 변환
                String subCategory = file.getSubCategory() != null
                        ? file.getSubCategory().toLowerCase()
                        : "database";
                return outputDir.resolve(
                        String.format("database/%s/%s/%s",
                                subCategory,
                                version.getVersion(),
                                file.getFileName())
                );

            case WEB:
                return outputDir.resolve(
                        String.format("web/%s/%s",
                                version.getVersion(),
                                file.getFileName())
                );

            case ENGINE:
                // sub_category 가 엔진명 (예: NC_SMS) → engine/<엔진명> 단일 파일로 평탄화
                String engineName = file.getSubCategory() != null
                        ? file.getSubCategory()
                        : file.getFileName();
                return outputDir.resolve("engine").resolve(engineName);

            default:
                // ETC / CONFIG / RESOURCE 등 — 버전별 디렉토리로 보존
                return outputDir.resolve(
                        String.format("etc/%s/%s",
                                version.getVersion(),
                                file.getFileName())
                );
        }
    }

    /**
     * 패치 스크립트 생성 (MariaDB, CrateDB)
     */
    private void generatePatchScripts(ReleaseVersion fromVersion, ReleaseVersion toVersion,
            List<ReleaseVersion> versions, String outputPath, String patchedBy) {
        try {
            // 빌드-only 패치 (같은 base): DB 변경이 없으므로 mariadb/cratedb 스크립트 생성 생략
            if (isSameBaseVersion(fromVersion, toVersion)) {
                log.info("같은 base 버전 ({}) 내 빌드 패치이므로 DB 스크립트 생성 생략",
                        fromVersion.getBaseVersionString());
                return;
            }

            // SQL 실행은 base 버전 단위로 한 번만 수행해야 한다.
            // betweenVersions 에는 빌드 행이 enriched 로 포함될 수 있고, 빌드는 base 와 같은
            // version 문자열을 가지므로 그대로 두면 동일 SQL 디렉토리가 여러 번 cd/execute 된다.
            // → 빌드 행 제거 + version 문자열 기준 distinct (copySqlFiles 와 동일 정책).
            java.util.Set<String> seenVersions = new java.util.LinkedHashSet<>();
            List<ReleaseVersion> sqlVersions = versions.stream()
                    .filter(v -> !v.isBuild())
                    .filter(v -> seenVersions.add(v.getVersion()))
                    .toList();

            if (sqlVersions.isEmpty()) {
                log.info("SQL 실행 대상 base 버전이 없어 DB 스크립트 생성을 생략합니다.");
                return;
            }

            // 프로젝트 ID 추출 (sqlVersions 의 첫 번째 버전에서)
            String projectId = sqlVersions.get(0).getProject().getProjectId();

            List<ReleaseFile> mariadbFiles = releaseFileRepository.findReleaseFilesBetweenVersionsBySubCategory(
                    projectId,
                    sqlVersions.get(0).getVersion(),
                    sqlVersions.get(sqlVersions.size() - 1).getVersion(),
                    "MARIADB"
            );

            List<ReleaseFile> cratedbFiles = releaseFileRepository.findReleaseFilesBetweenVersionsBySubCategory(
                    projectId,
                    sqlVersions.get(0).getVersion(),
                    sqlVersions.get(sqlVersions.size() - 1).getVersion(),
                    "CRATEDB"
            );

            // MariaDB 스크립트는 항상 생성 (VERSION_HISTORY INSERT를 위해 필수 - 단, infraeye1/infraeye2만)
            // SQL 파일이 없더라도 VERSION_HISTORY에 버전 이력을 기록해야 함
            mariaDBScriptGenerator.generatePatchScript(projectId, fromVersion.getVersion(),
                    toVersion.getVersion(), sqlVersions, mariadbFiles, outputPath, patchedBy);
            if (mariadbFiles.isEmpty()) {
                log.info("MariaDB SQL 파일은 없지만 스크립트 생성: {}/{}", outputPath, mariaDBScriptGenerator.getScriptFileName());
            } else {
                log.info("MariaDB 패치 스크립트 생성 완료: {}/{} (SQL 파일 {}개, base 버전 {}개)",
                        outputPath, mariaDBScriptGenerator.getScriptFileName(), mariadbFiles.size(), sqlVersions.size());
            }

            // CrateDB 스크립트는 파일이 있을 때만 생성
            if (!cratedbFiles.isEmpty()) {
                crateDBScriptGenerator.generatePatchScript(projectId, fromVersion.getVersion(),
                        toVersion.getVersion(), sqlVersions, cratedbFiles, outputPath, null);
                log.info("CrateDB 패치 스크립트 생성 완료: {}/{}", outputPath, crateDBScriptGenerator.getScriptFileName());
            } else {
                log.info("CrateDB 파일이 없어 스크립트를 생성하지 않습니다.");
            }

        } catch (Exception e) {
            log.error("패치 스크립트 생성 실패: {}", outputPath, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "패치 스크립트 생성 실패: " + e.getMessage());
        }
    }

    /**
     * README.md 생성
     *
     * @param webBuild 빌드 picker 로 선택된 WEB 빌드 (없으면 null) — To 표기에 우선 사용
     */
    private void generateReadme(ReleaseVersion fromVersion, ReleaseVersion toVersion,
            List<ReleaseVersion> includedVersions, String outputPath, ReleaseVersion webBuild) {
        try {
            Path readmePath = Paths.get(releaseBasePath, outputPath, "README.md");

            // VERSION 라인은 base 만, Build VERSION 라인은 빌드 정보가 있을 때만 표시
            String buildVersionStr = null;
            if (webBuild != null) {
                buildVersionStr = webBuild.getFullVersion();
            } else if (toVersion.isBuild()) {
                buildVersionStr = toVersion.getFullVersion();
            }
            String includedStr = includedVersions.stream()
                    .map(ReleaseVersion::getVersion)
                    .distinct()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            StringBuilder content = new StringBuilder();
            content.append("# 생성 정보\n");
            content.append(String.format("- 패치 생성일시: %s\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            content.append(String.format("- VERSION: %s -> %s\n",
                    fromVersion.getVersion(), toVersion.getVersion()));
            if (buildVersionStr != null) {
                content.append(String.format("- Build VERSION: %s\n", buildVersionStr));
            }
            content.append(String.format("- 포함된 버전: %s\n\n", includedStr));

            content.append("## 패치 방법\n");
            content.append("1. `InfraEye info version` — 사이트 버전 확인 (사전)\n");
            content.append("2. 본 패치 파일을 `/{설치경로}/infraeye/patch/` 에 복사 후 압축 해제\n");
            content.append("3. `InfraEye db patch` — DB 패치 (mariadb / cratedb)\n");
            content.append("4. `InfraEye was patch` — WAS 패치\n");
            content.append("5. `InfraEye eng patch` — 엔진 패치\n");
            content.append("6. `InfraEye info version` — 변경된 사이트 버전 확인 (사후)\n\n");

            content.append("## 주의\n");
            content.append("- 실행 전 반드시 백업 수행\n");
            content.append("- 오류 발생 시 패치 디렉토리의 `logs/` 확인\n\n");

            content.append("---\nCREATED BY - Release Manager\n");

            Files.writeString(readmePath, content.toString());
            log.info("README.md 생성 완료: {}", readmePath);

        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "README 생성 실패: " + e.getMessage());
        }
    }

    /**
     * CustomerProject 마지막 패치 정보 업데이트
     *
     * <p>고객사-프로젝트 매핑이 없으면 새로 생성하고, 있으면 업데이트합니다.
     *
     * @param customer  고객사
     * @param project   프로젝트
     * @param toVersion 패치된 버전 (to_version)
     */
    private void updateCustomerProjectPatchInfo(Customer customer, Project project, String toVersion) {
        CustomerProject customerProject = customerProjectRepository
                .findByCustomer_CustomerIdAndProject_ProjectId(customer.getCustomerId(), project.getProjectId())
                .orElseGet(() -> {
                    // 매핑이 없으면 새로 생성
                    log.info("고객사-프로젝트 매핑 생성 - customerId: {}, projectId: {}",
                            customer.getCustomerId(), project.getProjectId());
                    return CustomerProject.create(customer, project);
                });

        // 마지막 패치 정보 업데이트
        customerProject.updateLastPatchInfo(toVersion, LocalDateTime.now());
        customerProjectRepository.save(customerProject);

        log.info("CustomerProject 업데이트 완료 - customerId: {}, projectId: {}, lastPatchedVersion: {}",
                customer.getCustomerId(), project.getProjectId(), toVersion);
    }

    /**
     * InfraEye CLI가 DB 패치 없이도 WAS/ENG 패치 성공 후 빌드 버전을 기록할 수 있도록
     * 패치 루트에 fullVersion 메타파일(.build_version)을 생성한다.
     *
     * <p>빌드 picker 로 WEB 빌드가 포함된 경우 추가로 {@code web_build_full_version} 줄을
     * 기록한다. CLI 의 {@code _read_patch_to_version} 은 이 값이 있으면 우선 사용해
     * Build Version 표기에 빌드 번호 + 회차 (예: 1.1.0.260429-1) 까지 포함되도록 한다.
     *
     * <p>빌드 picker 로 선택된 web/engine 빌드들을 모두 별도 키로 기록한다:
     * <pre>
     * build_web=1.1.0.260429-1
     * build_engine_NC_SMS=1.2.0.260301-1
     * build_engine_NC_CONF=1.8.2.260402-2
     * </pre>
     *
     * @param buildSelection  빌드 picker 입력 (null/disabled 면 build_* 라인 없음)
     * @param selectedBuilds  applyBuildSelection 의 결과 (buildVersionId → ReleaseVersion)
     */
    private void generateBuildVersionFile(ReleaseVersion fromVersion, ReleaseVersion toVersion,
                                           String outputPath,
                                           PatchDto.BuildSelection buildSelection,
                                           Map<Long, ReleaseVersion> selectedBuilds) {
        try {
            Path versionPath = Paths.get(releaseBasePath, outputPath, ".build_version");

            List<String> lines = new ArrayList<>();
            lines.add("from_version=" + fromVersion.getFullVersion());
            lines.add("to_version=" + toVersion.getFullVersion());
            lines.add("generated_at=" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            int recorded = 0;
            if (buildSelection != null && buildSelection.enabled() && selectedBuilds != null) {
                if (buildSelection.web() != null) {
                    ReleaseVersion bv = selectedBuilds.get(buildSelection.web().buildVersionId());
                    if (bv != null) {
                        // bv.getFullVersion() 은 항상 yyMMdd-iteration 형태 (예: 1.0.0.260401-1)
                        lines.add("build_web=" + bv.getFullVersion());
                        recorded++;
                    }
                }
                if (buildSelection.engines() != null) {
                    for (PatchDto.SelectedEngine se : buildSelection.engines()) {
                        ReleaseVersion bv = selectedBuilds.get(se.buildVersionId());
                        if (bv == null) continue;
                        lines.add("build_engine_" + se.engineName() + "=" + bv.getFullVersion());
                        recorded++;
                    }
                }
            }
            lines.add("");

            Files.writeString(versionPath, String.join("\n", lines));
            log.info("InfraEye 빌드 버전 메타파일 생성 완료: {} (build entries={})", versionPath, recorded);
        } catch (IOException e) {
            log.error("InfraEye 빌드 버전 메타파일 생성 실패: {}", outputPath, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "빌드 버전 메타파일 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 패치 이력 저장 (영구 보존)
     *
     * <p>patch_file 삭제와 무관하게 패치 이력을 영구 보존합니다.
     *
     * @param patch 생성된 Patch 엔티티
     */
    private void savePatchHistory(Patch patch) {
        PatchHistory history = PatchHistory.fromPatch(patch);
        PatchHistory saved = patchHistoryRepository.save(history);
        log.info("패치 이력 저장 완료 - historyId: {}, patchName: {}",
                saved.getHistoryId(), saved.getPatchName());
    }
}
