package com.ts.rm.domain.releaseversion.service;

import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto.FileTreeNode;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersionHierarchy;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionHierarchyRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ReleaseVersion Tree Service
 *
 * <p>릴리즈 버전의 트리/계층 구조 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseVersionTreeService {

    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseVersionHierarchyRepository hierarchyRepository;
    private final ReleaseFileRepository releaseFileRepository;
    private final ReleaseVersionFileSystemService fileSystemService;

    /**
     * 표준 릴리즈 버전 트리 조회 (프로젝트별)
     *
     * @param projectId 프로젝트 ID
     * @return 릴리즈 버전 트리
     */
    public ReleaseVersionDto.TreeResponse getStandardReleaseTree(String projectId) {
        log.info("Getting standard release tree for project: {}", projectId);
        return buildReleaseTree(projectId, "STANDARD", null);
    }

    /**
     * 커스텀 릴리즈 버전 트리 조회 (프로젝트별, 특정 고객사)
     *
     * @param projectId    프로젝트 ID
     * @param customerCode 고객사 코드
     * @return 릴리즈 버전 트리
     */
    public ReleaseVersionDto.TreeResponse getCustomReleaseTree(String projectId, String customerCode) {
        log.info("Getting custom release tree for project: {}, customer: {}", projectId, customerCode);
        return buildReleaseTree(projectId, "CUSTOM", customerCode);
    }

    /**
     * 전체 커스텀 릴리즈 버전 트리 조회 (프로젝트별, 모든 고객사)
     *
     * <p>고객사별로 그룹화된 커스텀 버전 트리를 반환합니다.
     *
     * @param projectId 프로젝트 ID
     * @return 전체 커스텀 버전 트리 (고객사별 그룹화)
     */
    public ReleaseVersionDto.CustomTreeResponse getAllCustomReleaseTree(String projectId) {
        log.info("Getting all custom release tree for project: {}", projectId);

        try {
            // 프로젝트 내의 모든 CUSTOM 릴리즈 버전 조회
            List<ReleaseVersion> allCustomVersions = hierarchyRepository
                    .findAllByProjectIdAndReleaseTypeWithHierarchy(projectId, "CUSTOM");

            if (allCustomVersions.isEmpty()) {
                log.warn("No custom versions found for projectId: {}", projectId);
                return new ReleaseVersionDto.CustomTreeResponse("CUSTOM", List.of());
            }

            // 고객사별로 그룹화
            Map<Long, List<ReleaseVersion>> groupedByCustomer = new java.util.LinkedHashMap<>();
            for (ReleaseVersion version : allCustomVersions) {
                if (version.getCustomer() != null) {
                    groupedByCustomer.computeIfAbsent(
                            version.getCustomer().getCustomerId(),
                            k -> new ArrayList<>()
                    ).add(version);
                }
            }

            // CustomerNode 목록 생성
            List<ReleaseVersionDto.CustomerNode> customerNodes = new ArrayList<>();
            for (Map.Entry<Long, List<ReleaseVersion>> entry : groupedByCustomer.entrySet()) {
                List<ReleaseVersion> customerVersions = entry.getValue();

                // 첫 번째 버전에서 고객사 정보 및 기준 표준본 정보 추출
                ReleaseVersion firstVersion = customerVersions.get(0);
                Long customerId = firstVersion.getCustomer().getCustomerId();
                String customerCode = firstVersion.getCustomer().getCustomerCode();
                String customerName = firstVersion.getCustomer().getCustomerName();

                // 기준 표준본 정보 (고객사 내 모든 커스텀 버전은 동일한 기준 표준본 사용)
                Long customBaseVersionId = firstVersion.getCustomBaseVersion() != null
                        ? firstVersion.getCustomBaseVersion().getReleaseVersionId()
                        : null;
                String customBaseVersion = firstVersion.getCustomBaseVersion() != null
                        ? firstVersion.getCustomBaseVersion().getVersion()
                        : null;

                // 커스텀 버전의 majorMinor로 그룹화 (customMajorMinor 사용)
                List<ReleaseVersionDto.CustomMajorMinorNode> majorMinorGroups =
                        buildCustomMajorMinorGroups(customerVersions);

                customerNodes.add(new ReleaseVersionDto.CustomerNode(
                        customerId,
                        customerCode,
                        customerName,
                        customBaseVersionId,
                        customBaseVersion,
                        majorMinorGroups
                ));
            }

            return new ReleaseVersionDto.CustomTreeResponse("CUSTOM", customerNodes);

        } catch (Exception e) {
            log.error("Failed to build all custom release tree", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "커스텀 릴리즈 트리 조회 중 오류가 발생했습니다");
        }
    }

    /**
     * 커스텀 버전 목록을 Major.Minor로 그룹핑 (핫픽스 포함)
     *
     * @param versions 커스텀 릴리즈 버전 목록
     * @return 커스텀 Major.Minor 그룹 목록
     */
    private List<ReleaseVersionDto.CustomMajorMinorNode> buildCustomMajorMinorGroups(
            List<ReleaseVersion> versions) {

        // 원본 / 핫픽스 / 빌드 버전 분리
        List<ReleaseVersion> mainVersions = new ArrayList<>();
        Map<Long, List<ReleaseVersion>> hotfixesByParentId = new java.util.HashMap<>();
        Map<Long, List<ReleaseVersion>> buildsByParentId = new java.util.HashMap<>();

        for (ReleaseVersion version : versions) {
            if (version.isHotfix()) {
                Long parentId = version.getHotfixBaseVersion() != null
                        ? version.getHotfixBaseVersion().getReleaseVersionId()
                        : null;
                if (parentId != null) {
                    hotfixesByParentId.computeIfAbsent(parentId, k -> new ArrayList<>())
                            .add(version);
                }
            } else if (version.isBuild()) {
                Long parentId = version.getBuildBaseVersion() != null
                        ? version.getBuildBaseVersion().getReleaseVersionId()
                        : null;
                if (parentId != null) {
                    buildsByParentId.computeIfAbsent(parentId, k -> new ArrayList<>())
                            .add(version);
                }
            } else {
                // 원본 버전
                mainVersions.add(version);
            }
        }

        // 커스텀 버전의 MajorMinor로 그룹핑 (원본 버전만)
        Map<String, List<ReleaseVersion>> groupedByMajorMinor = new java.util.LinkedHashMap<>();

        for (ReleaseVersion version : mainVersions) {
            String customMajorMinor = version.getCustomMajorMinor();
            if (customMajorMinor != null) {
                groupedByMajorMinor.computeIfAbsent(customMajorMinor, k -> new ArrayList<>())
                        .add(version);
            }
        }

        // CustomMajorMinorNode 생성
        List<ReleaseVersionDto.CustomMajorMinorNode> majorMinorNodes = new ArrayList<>();

        for (Map.Entry<String, List<ReleaseVersion>> entry : groupedByMajorMinor.entrySet()) {
            String majorMinor = entry.getKey();
            List<ReleaseVersion> versionsInGroup = entry.getValue();

            // 그룹 내에서 커스텀 패치 버전 내림차순 정렬
            versionsInGroup.sort((v1, v2) ->
                    Integer.compare(
                            v2.getCustomPatchVersion() != null ? v2.getCustomPatchVersion() : 0,
                            v1.getCustomPatchVersion() != null ? v1.getCustomPatchVersion() : 0
                    )
            );

            // 각 버전에 대한 CustomVersionNode 생성 (핫픽스/빌드 포함)
            List<ReleaseVersionDto.CustomVersionNode> versionNodes = versionsInGroup.stream()
                    .map(v -> buildCustomVersionNodeWithHotfixes(v, hotfixesByParentId, buildsByParentId))
                    .toList();

            majorMinorNodes.add(new ReleaseVersionDto.CustomMajorMinorNode(majorMinor, versionNodes));
        }

        return majorMinorNodes;
    }

    /**
     * ReleaseVersion 엔티티로부터 CustomVersionNode 생성 (핫픽스 포함)
     *
     * @param version            릴리즈 버전 엔티티
     * @param hotfixesByParentId 부모 버전 ID별 핫픽스 Map
     * @return CustomVersionNode (핫픽스 포함)
     */
    private ReleaseVersionDto.CustomVersionNode buildCustomVersionNodeWithHotfixes(
            ReleaseVersion version,
            Map<Long, List<ReleaseVersion>> hotfixesByParentId,
            Map<Long, List<ReleaseVersion>> buildsByParentId) {

        // createdAt을 ISO-8601 형식으로 포맷 (시간 포함)
        String createdAt = version.getCreatedAt() != null
                ? version.getCreatedAt().toString()
                : null;

        // fileCategories 조회
        List<FileCategory> fileCategoryEnums = releaseFileRepository
                .findCategoriesByVersionId(version.getReleaseVersionId());
        List<String> fileCategories = fileCategoryEnums.stream()
                .map(FileCategory::getCode)
                .toList();

        // approvedAt 포매팅 (시간 포함)
        String approvedAt = version.getApprovedAt() != null
                ? version.getApprovedAt().toString()
                : null;

        // 승인자 아바타 정보 (approver FK 직접 사용)
        String approvedByAvatarStyle = version.getApprovedByAvatarStyle();
        String approvedByAvatarSeed = version.getApprovedByAvatarSeed();

        // 핫픽스 목록 조회 및 정렬
        List<ReleaseVersionDto.HotfixNode> hotfixNodes = new ArrayList<>();
        List<ReleaseVersion> hotfixes = hotfixesByParentId.get(version.getReleaseVersionId());
        if (hotfixes != null && !hotfixes.isEmpty()) {
            hotfixes.sort((h1, h2) -> Integer.compare(h1.getHotfixVersion(), h2.getHotfixVersion()));
            hotfixNodes = hotfixes.stream()
                    .map(this::buildHotfixNode)
                    .toList();
        }

        // 빌드 목록 조회 및 정렬 (build_version DESC)
        List<ReleaseVersionDto.BuildNode> buildNodes = new ArrayList<>();
        List<ReleaseVersion> builds = buildsByParentId.get(version.getReleaseVersionId());
        if (builds != null && !builds.isEmpty()) {
            builds.sort((b1, b2) -> Integer.compare(b2.getBuildVersion(), b1.getBuildVersion()));
            buildNodes = builds.stream()
                    .map(this::buildBuildNode)
                    .toList();
        }

        return new ReleaseVersionDto.CustomVersionNode(
                version.getReleaseVersionId(),
                version.getVersion(),  // 전체 버전 문자열 (예: 1.1.0-companyA.1.0.0)
                createdAt,
                version.getCreatedByName(),
                version.getCreatedByEmail(),
                version.getCreatedByAvatarStyle(),
                version.getCreatedByAvatarSeed(),
                version.getCreator() == null,  // isDeletedCreator
                version.getComment(),
                version.getIsApproved(),
                version.getApprovedByEmail(),
                version.getApprovedByName(),
                approvedByAvatarStyle,
                approvedByAvatarSeed,
                version.getApprover() == null && version.getApprovedByEmail() != null,  // isDeletedApprover
                approvedAt,
                fileCategories,
                hotfixNodes,
                buildNodes
        );
    }

    /**
     * 릴리즈 버전 트리 빌드 (DB 기반)
     *
     * @param projectId    프로젝트 ID
     * @param releaseType  릴리즈 타입 (STANDARD, CUSTOM)
     * @param customerCode 고객사 코드 (CUSTOM인 경우 필수)
     * @return 릴리즈 버전 트리
     */
    private ReleaseVersionDto.TreeResponse buildReleaseTree(String projectId, String releaseType,
            String customerCode) {
        try {
            // 클로저 테이블을 통한 버전 조회
            List<ReleaseVersion> versions;
            if ("CUSTOM".equals(releaseType) && customerCode != null) {
                versions = hierarchyRepository.findAllByProjectIdAndReleaseTypeAndCustomerWithHierarchy(
                        projectId, releaseType, customerCode);
            } else {
                versions = hierarchyRepository.findAllByProjectIdAndReleaseTypeWithHierarchy(
                        projectId, releaseType);
            }

            if (versions.isEmpty()) {
                log.warn("No versions found for projectId: {}, releaseType: {}, customerCode: {}",
                        projectId, releaseType, customerCode);
                return new ReleaseVersionDto.TreeResponse(releaseType, customerCode, List.of());
            }

            // Major.Minor 그룹으로 묶기
            List<ReleaseVersionDto.MajorMinorNode> majorMinorGroups = buildMajorMinorGroupsFromDb(
                    versions);

            return new ReleaseVersionDto.TreeResponse(releaseType, customerCode, majorMinorGroups);

        } catch (Exception e) {
            log.error("Failed to build release tree", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "릴리즈 트리 조회 중 오류가 발생했습니다");
        }
    }

    /**
     * DB에서 조회한 버전들을 Major.Minor로 그룹핑 (DB 기반)
     *
     * <p>핫픽스 버전은 원본 버전의 하위 계층으로 표시됩니다.
     *
     * @param versions 릴리즈 버전 목록
     * @return Major.Minor 그룹 목록
     */
    public List<ReleaseVersionDto.MajorMinorNode> buildMajorMinorGroupsFromDb(
            List<ReleaseVersion> versions) {

        // 원본 / 핫픽스 / 빌드 버전 분리
        List<ReleaseVersion> mainVersions = new ArrayList<>();
        Map<Long, List<ReleaseVersion>> hotfixesByParentId = new java.util.HashMap<>();
        Map<Long, List<ReleaseVersion>> buildsByParentId = new java.util.HashMap<>();

        for (ReleaseVersion version : versions) {
            if (version.isHotfix()) {
                Long parentId = version.getHotfixBaseVersion() != null
                        ? version.getHotfixBaseVersion().getReleaseVersionId()
                        : null;
                if (parentId != null) {
                    hotfixesByParentId.computeIfAbsent(parentId, k -> new ArrayList<>())
                            .add(version);
                }
            } else if (version.isBuild()) {
                Long parentId = version.getBuildBaseVersion() != null
                        ? version.getBuildBaseVersion().getReleaseVersionId()
                        : null;
                if (parentId != null) {
                    buildsByParentId.computeIfAbsent(parentId, k -> new ArrayList<>())
                            .add(version);
                }
            } else {
                // 원본 버전: 메인 리스트에 추가
                mainVersions.add(version);
            }
        }

        // Major.Minor로 그룹핑 (원본 버전만)
        java.util.Map<String, List<ReleaseVersion>> groupedByMajorMinor = new java.util.LinkedHashMap<>();

        for (ReleaseVersion version : mainVersions) {
            groupedByMajorMinor.computeIfAbsent(version.getMajorMinor(), k -> new ArrayList<>())
                    .add(version);
        }

        // MajorMinorNode 생성
        List<ReleaseVersionDto.MajorMinorNode> majorMinorNodes = new ArrayList<>();

        for (java.util.Map.Entry<String, List<ReleaseVersion>> entry : groupedByMajorMinor.entrySet()) {
            String majorMinor = entry.getKey();
            List<ReleaseVersion> versionsInGroup = entry.getValue();

            // 그룹 내에서 패치 버전 내림차순 정렬
            versionsInGroup.sort((v1, v2) -> Integer.compare(v2.getPatchVersion(), v1.getPatchVersion()));

            // 각 버전에 대한 VersionNode 생성 (핫픽스/빌드 포함)
            List<ReleaseVersionDto.VersionNode> versionNodes = versionsInGroup.stream()
                    .map(v -> buildVersionNodeWithHotfixes(v, hotfixesByParentId, buildsByParentId))
                    .toList();

            majorMinorNodes.add(new ReleaseVersionDto.MajorMinorNode(majorMinor, versionNodes));
        }

        return majorMinorNodes;
    }

    /**
     * ReleaseVersion 엔티티로부터 VersionNode 생성 (핫픽스 포함)
     *
     * @param version             릴리즈 버전 엔티티
     * @param hotfixesByParentId  부모 버전 ID별 핫픽스 Map
     * @return VersionNode (핫픽스 포함)
     */
    private ReleaseVersionDto.VersionNode buildVersionNodeWithHotfixes(
            ReleaseVersion version,
            Map<Long, List<ReleaseVersion>> hotfixesByParentId,
            Map<Long, List<ReleaseVersion>> buildsByParentId) {

        // createdAt을 ISO-8601 형식으로 포맷 (시간 포함)
        String createdAt = version.getCreatedAt() != null
                ? version.getCreatedAt().toString()
                : null;

        // fileCategories 조회
        List<FileCategory> fileCategoryEnums = releaseFileRepository
                .findCategoriesByVersionId(version.getReleaseVersionId());
        List<String> fileCategories = fileCategoryEnums.stream()
                .map(FileCategory::getCode)
                .toList();

        // approvedAt 포매팅 (시간 포함)
        String approvedAt = version.getApprovedAt() != null
                ? version.getApprovedAt().toString()
                : null;

        // 승인자 아바타 정보 (approver FK 직접 사용)
        String approvedByAvatarStyle = version.getApprovedByAvatarStyle();
        String approvedByAvatarSeed = version.getApprovedByAvatarSeed();

        // 핫픽스 목록 조회 및 정렬
        List<ReleaseVersionDto.HotfixNode> hotfixNodes = new ArrayList<>();
        List<ReleaseVersion> hotfixes = hotfixesByParentId.get(version.getReleaseVersionId());
        if (hotfixes != null && !hotfixes.isEmpty()) {
            hotfixes.sort((h1, h2) -> Integer.compare(h1.getHotfixVersion(), h2.getHotfixVersion()));
            hotfixNodes = hotfixes.stream()
                    .map(this::buildHotfixNode)
                    .toList();
        }

        // 빌드 목록 조회 및 정렬 (build_version DESC = 최신이 위로)
        List<ReleaseVersionDto.BuildNode> buildNodes = new ArrayList<>();
        List<ReleaseVersion> builds = buildsByParentId.get(version.getReleaseVersionId());
        if (builds != null && !builds.isEmpty()) {
            builds.sort((b1, b2) -> Integer.compare(b2.getBuildVersion(), b1.getBuildVersion()));
            buildNodes = builds.stream()
                    .map(this::buildBuildNode)
                    .toList();
        }

        return new ReleaseVersionDto.VersionNode(
                version.getReleaseVersionId(),
                version.getVersion(),
                createdAt,
                version.getCreatedByName(),
                version.getCreatedByEmail(),
                version.getCreatedByAvatarStyle(),
                version.getCreatedByAvatarSeed(),
                version.getCreator() == null,  // isDeletedCreator
                version.getComment(),
                version.getIsApproved(),
                version.getApprovedByEmail(),
                version.getApprovedByName(),
                approvedByAvatarStyle,
                approvedByAvatarSeed,
                version.getApprover() == null && version.getApprovedByEmail() != null,  // isDeletedApprover
                approvedAt,
                fileCategories,
                hotfixNodes,
                buildNodes
        );
    }

    /**
     * ReleaseVersion 엔티티로부터 BuildNode 생성.
     *
     * <p>빌드는 자식을 가질 수 없으므로 핫픽스와 같은 leaf 노드 구조를 사용한다.
     */
    private ReleaseVersionDto.BuildNode buildBuildNode(ReleaseVersion version) {
        String createdAt = version.getCreatedAt() != null
                ? version.getCreatedAt().toString()
                : null;

        List<FileCategory> fileCategoryEnums = releaseFileRepository
                .findCategoriesByVersionId(version.getReleaseVersionId());
        List<String> fileCategories = fileCategoryEnums.stream()
                .map(FileCategory::getCode)
                .toList();

        return new ReleaseVersionDto.BuildNode(
                version.getReleaseVersionId(),
                version.getBuildVersion(),
                version.getFullVersion(),
                createdAt,
                version.getCreatedByName(),
                version.getCreatedByEmail(),
                version.getCreatedByAvatarStyle(),
                version.getCreatedByAvatarSeed(),
                version.getCreator() == null,
                version.getComment(),
                version.getIsApproved(),
                fileCategories
        );
    }

    /**
     * ReleaseVersion 엔티티로부터 HotfixNode 생성
     *
     * <p>핫픽스는 자식을 가질 수 없으므로 별도의 간단한 DTO를 사용합니다.
     *
     * @param version 릴리즈 버전 엔티티 (핫픽스)
     * @return HotfixNode
     */
    private ReleaseVersionDto.HotfixNode buildHotfixNode(ReleaseVersion version) {
        // createdAt을 ISO-8601 형식으로 포맷 (시간 포함)
        String createdAt = version.getCreatedAt() != null
                ? version.getCreatedAt().toString()
                : null;

        // fileCategories 조회
        List<FileCategory> fileCategoryEnums = releaseFileRepository
                .findCategoriesByVersionId(version.getReleaseVersionId());
        List<String> fileCategories = fileCategoryEnums.stream()
                .map(FileCategory::getCode)
                .toList();

        // approvedAt 포매팅 (시간 포함)
        String approvedAt = version.getApprovedAt() != null
                ? version.getApprovedAt().toString()
                : null;

        // 승인자 아바타 정보 (approver FK 직접 사용)
        String approvedByAvatarStyle = version.getApprovedByAvatarStyle();
        String approvedByAvatarSeed = version.getApprovedByAvatarSeed();

        return new ReleaseVersionDto.HotfixNode(
                version.getReleaseVersionId(),
                version.getHotfixVersion(),
                version.getFullVersion(),
                createdAt,
                version.getCreatedByName(),
                version.getCreatedByEmail(),
                version.getCreatedByAvatarStyle(),
                version.getCreatedByAvatarSeed(),
                version.getCreator() == null,  // isDeletedCreator
                version.getComment(),
                version.getIsApproved(),
                version.getApprovedByEmail(),
                version.getApprovedByName(),
                approvedByAvatarStyle,
                approvedByAvatarSeed,
                version.getApprover() == null && version.getApprovedByEmail() != null,  // isDeletedApprover
                approvedAt,
                fileCategories
        );
    }

    /**
     * 새 버전에 대한 계층 구조 데이터 생성 (클로저 테이블)
     *
     * @param newVersion  새로 생성된 버전
     * @param releaseType 릴리즈 타입
     */
    @Transactional
    public void createHierarchyForNewVersion(ReleaseVersion newVersion, String releaseType) {
        // 1. 자기 자신과의 관계 (depth=0) - 필수
        ReleaseVersionHierarchy selfRelation = ReleaseVersionHierarchy.builder()
                .ancestor(newVersion)
                .descendant(newVersion)
                .depth(0)
                .build();
        hierarchyRepository.save(selfRelation);

        // 2. 이전 버전들과의 관계 설정 (선택적 - 버전 순서 기반)
        List<ReleaseVersion> previousVersions = releaseVersionRepository
                .findAllByReleaseTypeOrderByCreatedAtDesc(releaseType);

        int depth = 1;
        for (ReleaseVersion prevVersion : previousVersions) {
            // 자기 자신은 제외
            if (prevVersion.getReleaseVersionId().equals(newVersion.getReleaseVersionId())) {
                continue;
            }

            // 이전 버전 -> 새 버전 관계 생성
            ReleaseVersionHierarchy relation = ReleaseVersionHierarchy.builder()
                    .ancestor(prevVersion)
                    .descendant(newVersion)
                    .depth(depth++)
                    .build();
            hierarchyRepository.save(relation);
        }

        log.info("Hierarchy data created for version: {}", newVersion.getVersion());
    }

    /**
     * 릴리즈 버전의 파일 트리 구조 조회
     *
     * @param versionId 릴리즈 버전 ID
     * @param version   릴리즈 버전 엔티티
     * @return 파일 트리 응답
     */
    public ReleaseVersionDto.FileTreeResponse getVersionFileTree(Long versionId, ReleaseVersion version) {
        if (version.isBuild()) {
            return new ReleaseVersionDto.FileTreeResponse(
                    version.getReleaseVersionId(),
                    version.getFullVersion(),
                    buildBuildFileTreeFromFileSystem(version)
            );
        }

        // 모든 파일 조회 (relativePath 순으로 정렬)
        List<ReleaseFile> files = releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(versionId);

        // 파일 트리 생성
        ReleaseVersionDto.FileTreeNode rootNode = buildFileTree(files);

        return new ReleaseVersionDto.FileTreeResponse(
                version.getReleaseVersionId(),
                version.getVersion(),
                rootNode
        );
    }

    private ReleaseVersionDto.FileTreeNode buildBuildFileTreeFromFileSystem(ReleaseVersion buildVersion) {
        ReleaseVersion baseVersion = buildVersion.getBuildBaseVersion();
        if (baseVersion == null) {
            log.warn("빌드 원본 버전이 없어 파일 트리를 비워 반환: {}", buildVersion.getFullVersion());
            return ReleaseVersionDto.FileTreeNode.directory("", "", new ArrayList<>());
        }

        Path buildBasePath = fileSystemService.resolveBuildBasePath(baseVersion, buildVersion.getBuildVersion());
        if (!Files.exists(buildBasePath)) {
            log.warn("빌드 산출물 디렉토리가 없어 파일 트리를 비워 반환: {}", buildBasePath);
            return ReleaseVersionDto.FileTreeNode.directory("", "", new ArrayList<>());
        }

        Map<String, FileTreeNode> nodeMap = new java.util.HashMap<>();
        List<ReleaseVersionDto.FileTreeNode> rootChildren = new ArrayList<>();

        try (var stream = Files.walk(buildBasePath)) {
            stream
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> addFileSystemFileNode(buildBasePath, filePath, nodeMap, rootChildren));
        } catch (IOException e) {
            log.error("빌드 파일 트리 생성 실패: {}", buildBasePath, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "빌드 파일 트리 생성 실패: " + e.getMessage());
        }

        return ReleaseVersionDto.FileTreeNode.directory("", "", rootChildren);
    }

    private void addFileSystemFileNode(Path buildBasePath, Path filePath, Map<String, FileTreeNode> nodeMap,
            List<ReleaseVersionDto.FileTreeNode> rootChildren) {
        String relativePath = buildBasePath.relativize(filePath).toString().replace('\\', '/');
        String[] parts = relativePath.split("/");
        StringBuilder currentPath = new StringBuilder();
        List<ReleaseVersionDto.FileTreeNode> currentChildren = rootChildren;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (currentPath.length() > 0) {
                currentPath.append("/");
            }
            currentPath.append(part);
            String pathKey = currentPath.toString();
            boolean isFile = (i == parts.length - 1);

            if (isFile) {
                Long size = null;
                try {
                    size = Files.size(filePath);
                } catch (IOException e) {
                    log.warn("빌드 파일 크기 조회 실패: {}", filePath, e);
                }
                currentChildren.add(ReleaseVersionDto.FileTreeNode.file(
                        part,
                        pathKey,
                        size,
                        null,
                        filePath.toString().replace('\\', '/')
                ));
            } else if (!nodeMap.containsKey(pathKey)) {
                List<ReleaseVersionDto.FileTreeNode> newChildren = new ArrayList<>();
                ReleaseVersionDto.FileTreeNode dirNode = ReleaseVersionDto.FileTreeNode.directory(
                        part,
                        pathKey,
                        newChildren
                );
                nodeMap.put(pathKey, dirNode);
                currentChildren.add(dirNode);
                currentChildren = newChildren;
            } else {
                currentChildren = nodeMap.get(pathKey).children();
            }
        }
    }

    /**
     * ReleaseFile 목록으로부터 파일 트리 구조 생성
     *
     * @param files ReleaseFile 목록
     * @return 루트 FileTreeNode
     */
    public ReleaseVersionDto.FileTreeNode buildFileTree(List<ReleaseFile> files) {
        // 루트 노드 생성
        Map<String, FileTreeNode> nodeMap = new java.util.HashMap<>();

        // 루트 노드를 빈 경로로 시작
        List<ReleaseVersionDto.FileTreeNode> rootChildren = new ArrayList<>();

        for (ReleaseFile file : files) {
            String relativePath = file.getRelativePath();
            if (relativePath == null || relativePath.isEmpty()) {
                continue;
            }

            // 경로를 / 로 분리 (예: install/file.sql -> ["install", "file.sql"])
            String[] parts = relativePath.split("/");

            // 현재 경로 추적
            StringBuilder currentPath = new StringBuilder();
            List<ReleaseVersionDto.FileTreeNode> currentChildren = rootChildren;

            // 각 경로 부분을 순회하며 트리 구축
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) {
                    continue;  // 빈 문자열 건너뜀
                }
                // 선행 슬래시 없이 경로 구성 (예: install/file.sql)
                if (currentPath.length() > 0) {
                    currentPath.append("/");
                }
                currentPath.append(part);
                String pathKey = currentPath.toString();

                // 마지막 부분 (파일)인지 확인
                boolean isFile = (i == parts.length - 1);

                if (isFile) {
                    // 파일 노드 생성 (filePath는 다운로드용 실제 경로)
                    ReleaseVersionDto.FileTreeNode fileNode = ReleaseVersionDto.FileTreeNode.file(
                            part,
                            pathKey,
                            file.getFileSize(),
                            file.getReleaseFileId(),
                            file.getFilePath()
                    );
                    currentChildren.add(fileNode);
                } else {
                    // 디렉토리 노드 처리
                    if (!nodeMap.containsKey(pathKey)) {
                        // 새 디렉토리 노드 생성
                        List<ReleaseVersionDto.FileTreeNode> newChildren = new ArrayList<>();
                        ReleaseVersionDto.FileTreeNode dirNode = ReleaseVersionDto.FileTreeNode.directory(
                                part,
                                pathKey,
                                newChildren
                        );
                        nodeMap.put(pathKey, dirNode);
                        currentChildren.add(dirNode);
                        currentChildren = newChildren;
                    } else {
                        // 기존 디렉토리 노드 사용
                        ReleaseVersionDto.FileTreeNode existingNode = nodeMap.get(pathKey);
                        currentChildren = existingNode.children();
                    }
                }
            }
        }

        // 루트 노드 반환 (path는 빈 문자열)
        return ReleaseVersionDto.FileTreeNode.directory("", "", rootChildren);
    }
}
