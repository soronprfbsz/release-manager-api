package com.ts.rm.domain.releaseversion.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.mapper.ReleaseVersionDtoMapper;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionHierarchyRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.util.VersionParser;
import com.ts.rm.domain.releaseversion.util.VersionParser.VersionInfo;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ReleaseVersion Service
 *
 * <p>릴리즈 버전 관리 비즈니스 로직 (CRUD 및 기본 조회)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseVersionService {

    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseFileRepository releaseFileRepository;
    private final ReleaseVersionHierarchyRepository hierarchyRepository;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final AccountLookupService accountLookupService;
    private final ReleaseVersionDtoMapper mapper;

    // 분리된 서비스들
    private final ReleaseVersionFileSystemService fileSystemService;
    private final ReleaseVersionTreeService treeService;

    /**
     * 표준 릴리즈 버전 생성
     *
     * @param request 버전 생성 요청
     * @return 생성된 버전 상세 정보
     */
    @Transactional
    public ReleaseVersionDto.DetailResponse createStandardVersion(
            ReleaseVersionDto.CreateRequest request) {
        log.info("Creating standard release version: {}", request.version());

        // 버전 생성
        return createVersion("STANDARD", null, request);
    }

    /**
     * 커스텀 릴리즈 버전 생성
     *
     * @param request 버전 생성 요청
     * @return 생성된 버전 상세 정보
     */
    @Transactional
    public ReleaseVersionDto.DetailResponse createCustomVersion(
            ReleaseVersionDto.CreateRequest request) {
        log.info("Creating custom release version: {} for customerId: {}",
                request.version(), request.customerId());

        // 고객사 ID 필수 검증
        if (request.customerId() == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_ID_REQUIRED);
        }

        // Customer 조회
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        // 버전 생성
        return createVersion("CUSTOM", customer, request);
    }

    /**
     * 릴리즈 버전 조회 (ID)
     *
     * @param versionId 버전 ID
     * @return 버전 상세 정보
     */
    public ReleaseVersionDto.DetailResponse getVersionById(Long versionId) {
        ReleaseVersion version = findVersionById(versionId);
        return mapper.toDetailResponse(version);
    }

    /**
     * 타입별 버전 목록 조회
     *
     * @param typeName 릴리즈 타입 (standard/custom)
     * @return 버전 목록
     */
    public List<ReleaseVersionDto.SimpleResponse> getVersionsByType(String typeName) {
        String releaseType = typeName.toUpperCase();
        List<ReleaseVersion> versions = releaseVersionRepository
                .findAllByReleaseTypeOrderByCreatedAtDesc(releaseType);
        List<ReleaseVersionDto.SimpleResponse> responses = mapper.toSimpleResponseList(versions);
        return enrichWithCategories(responses);
    }

    /**
     * 버전 범위 조회
     *
     * @param projectId   프로젝트 ID
     * @param typeName    릴리즈 타입
     * @param fromVersion 시작 버전
     * @param toVersion   종료 버전
     * @return 버전 목록
     */
    public List<ReleaseVersionDto.SimpleResponse> getVersionsBetween(String projectId, String typeName,
            String fromVersion, String toVersion) {
        String releaseType = typeName.toUpperCase();
        List<ReleaseVersion> versions = releaseVersionRepository.findVersionsBetween(
                projectId, releaseType, fromVersion, toVersion);
        List<ReleaseVersionDto.SimpleResponse> responses = mapper.toSimpleResponseList(versions);
        return enrichWithCategories(responses);
    }

    /**
     * 버전 정보 수정
     *
     * @param versionId 버전 ID
     * @param request   수정 요청
     * @return 수정된 버전 상세 정보
     */
    @Transactional
    public ReleaseVersionDto.DetailResponse updateVersion(Long versionId,
            ReleaseVersionDto.UpdateRequest request) {
        log.info("Updating release version with versionId: {}", versionId);

        // 엔티티 조회
        ReleaseVersion releaseVersion = findVersionById(versionId);

        // Setter를 통한 수정 (JPA Dirty Checking)
        if (request.comment() != null) {
            releaseVersion.setComment(request.comment());
        }

        // 트랜잭션 커밋 시 자동으로 UPDATE 쿼리 실행 (Dirty Checking)
        log.info("Release version updated successfully with versionId: {}", versionId);
        return mapper.toDetailResponse(releaseVersion);
    }

    /**
     * 버전 삭제
     *
     * @param versionId 버전 ID
     */
    @Transactional
    public void deleteVersion(Long versionId) {
        log.info("버전 삭제 시작 - versionId: {}", versionId);

        // 1. 버전 존재 검증
        ReleaseVersion version = findVersionById(versionId);
        String versionNumber = version.getVersion();

        try {
            // 2. 파일 시스템 삭제 (DB 작업 이전에 수행)
            //    - 영속성 컨텍스트가 살아 있는 동안 lazy 관계(buildBaseVersion 등) 안전하게 접근 가능
            //    - 파일 삭제 실패 시 BusinessException → 트랜잭션 롤백 → DB 행 보존 (사용자에게 명시적 에러)
            if (version.isHotfix()) {
                fileSystemService.deleteHotfixDirectory(version);
            } else if (version.isBuild()) {
                fileSystemService.deleteBuildDirectory(version);
            } else {
                fileSystemService.deleteVersionDirectory(version);
            }

            // 3. release_file 삭제 (명시적으로)
            List<ReleaseFile> releaseFiles = releaseFileRepository
                    .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(versionId);
            releaseFileRepository.deleteAll(releaseFiles);
            log.info("release_file 삭제 완료 - {} 개", releaseFiles.size());

            // 4. release_version_hierarchy 삭제
            hierarchyRepository.deleteByDescendantId(versionId);
            hierarchyRepository.deleteByAncestorId(versionId);
            log.info("release_version_hierarchy 삭제 완료");

            // 5. release_version 삭제
            releaseVersionRepository.delete(version);
            log.info("release_version 삭제 완료");

            log.info("버전 삭제 완료 - version: {}", versionNumber);

        } catch (BusinessException e) {
            // 파일/DB 삭제 도중 발생한 BusinessException 은 메시지를 보존하여 그대로 전파
            log.error("버전 삭제 실패 - versionId: {}, version: {}, reason: {}", versionId, versionNumber, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("버전 삭제 실패 - versionId: {}, version: {}", versionId, versionNumber, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "버전 삭제 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // === Private Helper Methods ===

    /**
     * 공통 버전 생성 로직
     */
    private ReleaseVersionDto.DetailResponse createVersion(String releaseType,
            Customer customer, ReleaseVersionDto.CreateRequest request) {

        // 프로젝트 ID 필수 검증
        if (request.projectId() == null || request.projectId().isBlank()) {
            throw new BusinessException(ErrorCode.PROJECT_ID_REQUIRED);
        }

        // Project 조회
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 생성자(Account) 조회 - 이메일로 조회
        Account creator = accountLookupService.findByEmail(request.createdByEmail());

        // 버전 파싱
        VersionInfo versionInfo = VersionParser.parse(request.version());

        // 중복 검증 (프로젝트 내에서 동일 버전 확인)
        if (releaseVersionRepository.existsByProject_ProjectIdAndVersion(request.projectId(), request.version())) {
            throw new BusinessException(ErrorCode.RELEASE_VERSION_CONFLICT);
        }

        // Entity 생성
        ReleaseVersion version = ReleaseVersion.builder()
                .project(project)
                .releaseType(releaseType)
                .customer(customer)
                .version(request.version())
                .majorVersion(versionInfo.getMajorVersion())
                .minorVersion(versionInfo.getMinorVersion())
                .patchVersion(versionInfo.getPatchVersion())
                .creator(creator)
                .createdByEmail(request.createdByEmail())
                .comment(request.comment())
                .isApproved(request.isApproved() != null ? request.isApproved() : false)
                .customMajorVersion(request.customMajorVersion())
                .customMinorVersion(request.customMinorVersion())
                .customPatchVersion(request.customPatchVersion())
                .build();

        ReleaseVersion savedVersion = releaseVersionRepository.save(version);

        // 클로저 테이블에 계층 구조 데이터 추가
        treeService.createHierarchyForNewVersion(savedVersion, releaseType);

        // 디렉토리 구조 생성
        fileSystemService.createDirectoryStructure(savedVersion, customer);

        log.info("Release version created successfully with id: {}, projectId: {}",
                savedVersion.getReleaseVersionId(), project.getProjectId());
        return mapper.toDetailResponse(savedVersion);
    }

    /**
     * ReleaseVersion 조회 (존재하지 않으면 예외 발생)
     */
    public ReleaseVersion findVersionById(Long versionId) {
        return releaseVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND));
    }

    /**
     * SimpleResponse 리스트에 categories 필드 설정
     */
    private List<ReleaseVersionDto.SimpleResponse> enrichWithCategories(
            List<ReleaseVersionDto.SimpleResponse> responses) {
        return responses.stream()
                .map(this::enrichWithCategories)
                .toList();
    }

    /**
     * 단일 SimpleResponse에 fileCategories 필드 설정
     */
    private ReleaseVersionDto.SimpleResponse enrichWithCategories(
            ReleaseVersionDto.SimpleResponse response) {
        List<FileCategory> fileCategoryEnums = releaseFileRepository
                .findCategoriesByVersionId(response.releaseVersionId());

        List<String> fileCategories = fileCategoryEnums.stream()
                .map(FileCategory::getCode)
                .toList();

        return new ReleaseVersionDto.SimpleResponse(
                response.releaseVersionId(),
                response.projectId(),
                response.releaseType(),
                response.customerCode(),
                response.version(),
                response.hotfixVersion(),      // hotfixVersion
                response.isHotfix(),           // isHotfix
                response.buildVersion(),       // buildVersion
                response.isBuild(),            // isBuild
                response.fullVersion(),        // fullVersion
                response.majorMinor(),
                response.createdByEmail(),
                response.createdByName(),      // 생성자 이름
                response.createdByAvatarStyle(),
                response.createdByAvatarSeed(),
                response.isDeletedCreator(),   // 생성자 탈퇴 여부
                response.comment(),
                response.isApproved(),
                response.approvedBy(),
                response.approvedByName(),     // 승인자 이름
                response.isDeletedApprover(),  // 승인자 탈퇴 여부
                response.approvedAt(),
                fileCategories,
                response.createdAt(),
                response.patchFileCount()
        );
    }

    /**
     * 프로젝트별 표준본 버전 목록 조회 (셀렉트박스용)
     *
     * @param projectId 프로젝트 ID
     * @return 표준본 버전 목록 (value: versionId, name: version)
     */
    public List<ReleaseVersionDto.VersionSelectOption> getStandardVersionsForSelect(String projectId) {
        log.info("표준본 버전 셀렉트박스 목록 조회 - projectId: {}", projectId);

        List<ReleaseVersion> versions = releaseVersionRepository
                .findAllByProject_ProjectIdAndReleaseTypeOrderByCreatedAtDesc(projectId, "STANDARD");

        // 핫픽스 제외. base 버전과 빌드 버전(1.1.0.260427) 둘 다 포함하여
        // 패치 from/to 셀렉터에서 구분 선택할 수 있게 함.
        return versions.stream()
                .filter(v -> !v.isHotfix())
                .map(v -> new ReleaseVersionDto.VersionSelectOption(
                        v.getReleaseVersionId(),  // versionId
                        v.getFullVersion(),       // 빌드 인식: base="1.1.0", build="1.1.0.260427"
                        v.getIsApproved()
                ))
                .toList();
    }

    /**
     * 릴리즈 버전 승인
     *
     * @param versionId       버전 ID
     * @param approvedByEmail 승인자 이메일
     * @return 승인된 버전 상세 정보
     */
    @Transactional
    public ReleaseVersionDto.DetailResponse approveReleaseVersion(Long versionId, String approvedByEmail) {
        log.info("릴리즈 버전 승인 요청 - versionId: {}, approvedByEmail: {}", versionId, approvedByEmail);

        // 엔티티 조회
        ReleaseVersion releaseVersion = findVersionById(versionId);

        // 승인자(Account) 조회 - 이메일로 조회
        Account approver = accountLookupService.findByEmail(approvedByEmail);

        // 승인 처리
        releaseVersion.setIsApproved(true);
        releaseVersion.setApprover(approver);
        releaseVersion.setApprovedByEmail(approvedByEmail);
        releaseVersion.setApprovedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));

        // 트랜잭션 커밋 시 자동으로 UPDATE 쿼리 실행 (Dirty Checking)
        log.info("릴리즈 버전 승인 완료 - versionId: {}, approvedByEmail: {}, approvedAt: {}",
                versionId, approvedByEmail, releaseVersion.getApprovedAt());
        return mapper.toDetailResponse(releaseVersion);
    }

    // ========================================
    // Hotfix 관련 메서드
    // ========================================

    /**
     * 핫픽스 생성
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @param comment             코멘트
     * @param createdByEmail      생성자 이메일
     * @return 생성된 핫픽스 버전 상세 정보
     */
    @Transactional
    public ReleaseVersionDto.CreateHotfixResponse createHotfix(Long hotfixBaseVersionId, String comment, String createdByEmail) {
        log.info("핫픽스 생성 요청 - hotfixBaseVersionId: {}, createdByEmail: {}", hotfixBaseVersionId, createdByEmail);

        // 1. 원본 버전 조회
        ReleaseVersion baseVersion = findVersionById(hotfixBaseVersionId);

        // 2. 원본 버전이 핫픽스인지 확인 (핫픽스의 핫픽스는 불가)
        if (baseVersion.isHotfix()) {
            throw new BusinessException(ErrorCode.INVALID_HOTFIX_PARENT,
                    "핫픽스 버전에는 추가 핫픽스를 생성할 수 없습니다.");
        }

        // 3. 생성자(Account) 조회 - 이메일로 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        // 4. 다음 핫픽스 버전 번호 결정
        Integer maxHotfixVersion = releaseVersionRepository.findMaxHotfixVersionByHotfixBaseVersionId(hotfixBaseVersionId);
        int nextHotfixVersion = maxHotfixVersion + 1;

        // 5. 핫픽스 버전 생성
        ReleaseVersion hotfixVersion = ReleaseVersion.builder()
                .project(baseVersion.getProject())
                .releaseType(baseVersion.getReleaseType())
                .customer(baseVersion.getCustomer())
                .version(baseVersion.getVersion())  // 기본 버전은 동일
                .majorVersion(baseVersion.getMajorVersion())
                .minorVersion(baseVersion.getMinorVersion())
                .patchVersion(baseVersion.getPatchVersion())
                .hotfixVersion(nextHotfixVersion)
                .hotfixBaseVersion(baseVersion)
                .creator(creator)
                .createdByEmail(createdByEmail)
                .comment(comment)
                .isApproved(false)
                .customMajorVersion(baseVersion.getCustomMajorVersion())
                .customMinorVersion(baseVersion.getCustomMinorVersion())
                .customPatchVersion(baseVersion.getCustomPatchVersion())
                .customBaseVersion(baseVersion.getCustomBaseVersion())
                .build();

        ReleaseVersion savedHotfix = releaseVersionRepository.save(hotfixVersion);

        // 6. 핫픽스용 디렉토리 생성
        fileSystemService.createHotfixDirectoryStructure(savedHotfix, baseVersion);

        log.info("핫픽스 생성 완료 - hotfixVersionId: {}, fullVersion: {}",
                savedHotfix.getReleaseVersionId(), savedHotfix.getFullVersion());

        return new ReleaseVersionDto.CreateHotfixResponse(
                savedHotfix.getReleaseVersionId(),
                savedHotfix.getProject().getProjectId(),
                hotfixBaseVersionId,
                baseVersion.getVersion(),
                savedHotfix.getMajorVersion(),
                savedHotfix.getMinorVersion(),
                savedHotfix.getPatchVersion(),
                savedHotfix.getHotfixVersion(),
                savedHotfix.getFullVersion(),
                savedHotfix.getMajorMinor(),
                savedHotfix.getCreatedByName(),
                savedHotfix.getComment(),
                savedHotfix.getCreatedAt(),
                List.of()  // 아직 파일이 없음
        );
    }

    /**
     * 특정 버전의 핫픽스 목록 조회
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @return 핫픽스 목록 응답
     */
    public ReleaseVersionDto.HotfixListResponse getHotfixesByHotfixBaseVersionId(Long hotfixBaseVersionId) {
        log.info("핫픽스 목록 조회 - hotfixBaseVersionId: {}", hotfixBaseVersionId);

        // 1. 원본 버전 조회
        ReleaseVersion baseVersion = findVersionById(hotfixBaseVersionId);

        // 2. 핫픽스 목록 조회
        List<ReleaseVersion> hotfixes = releaseVersionRepository
                .findAllByHotfixBaseVersion_ReleaseVersionIdOrderByHotfixVersionAsc(hotfixBaseVersionId);

        // 3. DTO 변환 및 카테고리 정보 추가
        List<ReleaseVersionDto.HotfixItem> hotfixItems = hotfixes.stream()
                .map(this::toHotfixItemWithCategories)
                .toList();

        return new ReleaseVersionDto.HotfixListResponse(
                hotfixBaseVersionId,
                baseVersion.getVersion(),
                hotfixItems
        );
    }

    /**
     * 핫픽스 항목 변환 (카테고리 정보 포함)
     */
    private ReleaseVersionDto.HotfixItem toHotfixItemWithCategories(ReleaseVersion hotfix) {
        List<FileCategory> fileCategoryEnums = releaseFileRepository
                .findCategoriesByVersionId(hotfix.getReleaseVersionId());

        List<String> fileCategories = fileCategoryEnums.stream()
                .map(FileCategory::getCode)
                .toList();

        return new ReleaseVersionDto.HotfixItem(
                hotfix.getReleaseVersionId(),
                hotfix.getHotfixVersion(),
                hotfix.getFullVersion(),
                hotfix.getCreatedAt() != null ? hotfix.getCreatedAt().toLocalDate().toString() : null,
                hotfix.getCreatedByName(),
                hotfix.getCreatedByEmail(),
                hotfix.getComment(),
                hotfix.getIsApproved(),
                fileCategories
        );
    }

    /**
     * 버전에 핫픽스가 존재하는지 확인
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @return 핫픽스 존재 여부
     */
    public boolean hasHotfixes(Long hotfixBaseVersionId) {
        return releaseVersionRepository.existsByHotfixBaseVersion_ReleaseVersionId(hotfixBaseVersionId);
    }

    /**
     * 핫픽스 개수 조회
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @return 핫픽스 개수
     */
    public Long getHotfixCount(Long hotfixBaseVersionId) {
        return releaseVersionRepository.countHotfixesByHotfixBaseVersionId(hotfixBaseVersionId);
    }

    /**
     * 버전 범위 조회 (핫픽스 제외)
     *
     * @param projectId   프로젝트 ID
     * @param typeName    릴리즈 타입
     * @param fromVersion 시작 버전
     * @param toVersion   종료 버전
     * @return 버전 목록 (핫픽스 제외)
     */
    public List<ReleaseVersionDto.SimpleResponse> getVersionsBetweenExcludingHotfixes(
            String projectId, String typeName, String fromVersion, String toVersion) {
        String releaseType = typeName.toUpperCase();
        List<ReleaseVersion> versions = releaseVersionRepository.findVersionsBetweenExcludingHotfixes(
                projectId, releaseType, fromVersion, toVersion);
        List<ReleaseVersionDto.SimpleResponse> responses = mapper.toSimpleResponseList(versions);
        return enrichWithCategories(responses);
    }

    // ========================================
    // Build 관련 메서드
    // ========================================

    /**
     * 빌드 버전 충돌 시 retry 최대 횟수 (방어선)
     */
    static final int MAX_BUILD_VERSION_RETRY = 100;

    /**
     * 빌드 버전 생성
     *
     * <p>비즈니스 룰:
     * <ul>
     *   <li>핫픽스 버전 위에는 빌드를 생성할 수 없음</li>
     *   <li>빌드 버전 위에는 빌드를 생성할 수 없음</li>
     *   <li>buildVersion 은 항상 오늘 날짜(yyMMdd) 자동, 사용자 입력 무시</li>
     *   <li>buildIteration 은 같은 base + 같은 buildVersion 안에서 max+1 (최초는 1)</li>
     *   <li>동시성 race 로 UNIQUE 충돌 시 +1 후 재시도 (최대 MAX_BUILD_VERSION_RETRY)</li>
     *   <li>빌드는 즉시 활성: {@code is_approved=true}, approver/approvedAt 자동 설정</li>
     * </ul>
     *
     * @param baseVersionId  빌드 원본 버전 ID
     * @param request        생성 요청 (comment) — buildVersion 필드는 사용하지 않음
     * @param createdByEmail 생성자 이메일
     * @return 생성된 빌드 응답
     */
    @Transactional
    public ReleaseVersionDto.CreateBuildResponse createBuild(
            Long baseVersionId,
            ReleaseVersionDto.CreateBuildRequest request,
            String createdByEmail) {
        log.info("빌드 생성 요청 - baseVersionId: {}, createdByEmail: {}", baseVersionId, createdByEmail);

        // 1. base 조회
        ReleaseVersion baseVersion = findVersionById(baseVersionId);

        // 2. 핫픽스/빌드 위 생성 거부
        if (baseVersion.isHotfix()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "핫픽스 버전 위에는 빌드를 생성할 수 없습니다.");
        }
        if (baseVersion.isBuild()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "빌드 버전 위에는 빌드를 생성할 수 없습니다.");
        }

        // 3. 생성자 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        // 4. buildVersion = 오늘 yyMMdd (고정)
        final int buildVersion = todayYyMmDd();

        // 5. 시작 iteration = 같은 base+buildVersion 의 max(iteration) + 1
        int candidateIteration = releaseVersionRepository
                .findTopByBuildBaseVersion_ReleaseVersionIdAndBuildVersionOrderByBuildIterationDesc(
                        baseVersionId, buildVersion)
                .map(ReleaseVersion::getBuildIteration)
                .orElse(0) + 1;

        // 6. 동시성 race 회피 retry (최대 MAX_BUILD_VERSION_RETRY)
        for (int i = 0; i < MAX_BUILD_VERSION_RETRY; i++) {
            try {
                ReleaseVersion saved = saveBuildEntity(baseVersion, creator, buildVersion, candidateIteration,
                        request.comment(), createdByEmail);

                // 클로저 테이블에 self-row(depth=0) 등록.
                // 트리 조회(findAllByProjectIdAndReleaseTypeWithHierarchy)는 hierarchy 와의
                // INNER JOIN 으로 결과를 추리므로 이 호출이 빠지면 빌드가 트리에 표시되지 않는다.
                treeService.createHierarchyForNewVersion(saved, baseVersion.getReleaseType());

                fileSystemService.createBuildDirectoryStructure(saved, baseVersion);

                log.info("빌드 생성 완료 - buildVersionId: {}, fullVersion: {}",
                        saved.getReleaseVersionId(), saved.getFullVersion());

                return new ReleaseVersionDto.CreateBuildResponse(
                        saved.getReleaseVersionId(),
                        baseVersion.getVersion(),
                        saved.getBuildVersion(),
                        saved.getFullVersion(),
                        0  // 빌드만 생성 (파일 업로드 없음). createBuildWithZip 가 enrich.
                );
            } catch (DataIntegrityViolationException e) {
                // 동시성 race: 다른 트랜잭션이 같은 (buildVersion, iteration) 을 선점한 경우
                log.warn("빌드 저장 시 UNIQUE 충돌 (build_version={}, iteration={}). +1 후 재시도",
                        buildVersion, candidateIteration);
                candidateIteration++;
            }
        }

        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                String.format("빌드 버전 충돌이 %d회 발생하여 생성을 중단했습니다.", MAX_BUILD_VERSION_RETRY));
    }

    /**
     * 빌드 엔티티 저장 (saveAndFlush 로 UNIQUE 위반을 즉시 감지)
     */
    private ReleaseVersion saveBuildEntity(ReleaseVersion baseVersion, Account creator, int buildVersion,
            int buildIteration, String comment, String createdByEmail) {
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        ReleaseVersion build = ReleaseVersion.builder()
                .project(baseVersion.getProject())
                .releaseType(baseVersion.getReleaseType())
                .customer(baseVersion.getCustomer())
                .version(baseVersion.getVersion())
                .majorVersion(baseVersion.getMajorVersion())
                .minorVersion(baseVersion.getMinorVersion())
                .patchVersion(baseVersion.getPatchVersion())
                .hotfixVersion(0)
                .buildVersion(buildVersion)
                .buildIteration(buildIteration)
                .buildBaseVersion(baseVersion)
                .isApproved(true)
                .approver(creator)
                .approvedByEmail(createdByEmail)
                .approvedAt(nowUtc)
                .creator(creator)
                .createdByEmail(createdByEmail)
                .comment(comment)
                .customMajorVersion(baseVersion.getCustomMajorVersion())
                .customMinorVersion(baseVersion.getCustomMinorVersion())
                .customPatchVersion(baseVersion.getCustomPatchVersion())
                .customBaseVersion(baseVersion.getCustomBaseVersion())
                .build();
        return releaseVersionRepository.saveAndFlush(build);
    }

    /**
     * 오늘 날짜를 yyMMdd 정수로 반환 (예: 260427)
     *
     * <p>package-private 으로 노출하여 테스트에서 검증 가능하도록 함.
     */
    static int todayYyMmDd() {
        return Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")));
    }

    /**
     * 특정 버전의 빌드 목록 조회
     *
     * @param baseVersionId 빌드 원본 버전 ID
     * @return 빌드 목록 응답 (build_version DESC)
     */
    public ReleaseVersionDto.BuildListResponse getBuilds(Long baseVersionId) {
        log.info("빌드 목록 조회 - baseVersionId: {}", baseVersionId);

        ReleaseVersion baseVersion = findVersionById(baseVersionId);
        List<ReleaseVersion> builds = releaseVersionRepository
                .findAllByBuildBaseVersion_ReleaseVersionIdOrderByBuildVersionDesc(baseVersionId);

        List<ReleaseVersionDto.BuildItem> items = builds.stream()
                .map(this::toBuildItem)
                .toList();

        return new ReleaseVersionDto.BuildListResponse(
                baseVersionId,
                baseVersion.getVersion(),
                items
        );
    }

    private ReleaseVersionDto.BuildItem toBuildItem(ReleaseVersion build) {
        return new ReleaseVersionDto.BuildItem(
                build.getReleaseVersionId(),
                build.getBuildVersion(),
                build.getVersion(),
                build.getFullVersion(),
                build.getIsApproved(),
                build.getCreatedAt() != null ? build.getCreatedAt().toString() : null,
                build.getCreatedByEmail(),
                build.getCreatedByName(),
                build.getComment()
        );
    }

}
