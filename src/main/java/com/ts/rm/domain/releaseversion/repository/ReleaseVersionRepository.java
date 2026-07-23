package com.ts.rm.domain.releaseversion.repository;

import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ReleaseVersion Repository
 *
 * <p>릴리즈 버전 조회 및 관리를 위한 Repository
 * <p>Spring Data JPA 메서드 네이밍으로 CRUD 처리
 * <p>복잡한 쿼리(LIMIT 포함 등)는 ReleaseVersionRepositoryCustom에서 QueryDSL로 처리
 */
public interface ReleaseVersionRepository extends JpaRepository<ReleaseVersion, Long>,
        ReleaseVersionRepositoryCustom {

    /**
     * 버전으로 릴리즈 버전 조회
     *
     * @param version 버전 (예: 1.1.0)
     * @return ReleaseVersion
     */
    Optional<ReleaseVersion> findByVersion(String version);

    /**
     * 프로젝트와 버전으로 릴리즈 버전 조회
     *
     * @param projectId 프로젝트 ID
     * @param version   버전 (예: 1.1.0)
     * @return ReleaseVersion
     */
    Optional<ReleaseVersion> findByProject_ProjectIdAndVersion(String projectId, String version);

    /**
     * 릴리즈 타입과 버전으로 조회
     *
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param version     버전
     * @return ReleaseVersion
     */
    Optional<ReleaseVersion> findByReleaseTypeAndVersion(String releaseType, String version);

    /**
     * 프로젝트, 릴리즈 타입, 버전으로 조회
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param version     버전
     * @return ReleaseVersion
     */
    Optional<ReleaseVersion> findByProject_ProjectIdAndReleaseTypeAndVersion(
            String projectId, String releaseType, String version);

    /**
     * 릴리즈 타입별 버전 목록 조회 (최신순)
     *
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @return 버전 목록
     */
    List<ReleaseVersion> findAllByReleaseTypeOrderByCreatedAtDesc(String releaseType);

    /**
     * 프로젝트별 릴리즈 타입별 버전 목록 조회 (최신순)
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @return 버전 목록
     */
    List<ReleaseVersion> findAllByProject_ProjectIdAndReleaseTypeOrderByCreatedAtDesc(
            String projectId, String releaseType);

    /**
     * 사이트별 커스텀 버전 목록 조회 (최신순)
     *
     * @param siteId 사이트 ID
     * @return 버전 목록
     */
    List<ReleaseVersion> findAllBySite_SiteIdOrderByCreatedAtDesc(Long siteId);

    /**
     * Major, Minor 버전으로 버전 목록 조회
     *
     * @param majorVersion Major 버전 (예: 1)
     * @param minorVersion Minor 버전 (예: 1)
     * @return 버전 목록
     */
    List<ReleaseVersion> findAllByMajorVersionAndMinorVersionOrderByPatchVersionDesc(
            Integer majorVersion, Integer minorVersion);

    /**
     * 버전 존재 여부 확인
     *
     * @param version 버전
     * @return 존재 여부
     */
    boolean existsByVersion(String version);

    /**
     * 프로젝트 내 버전 존재 여부 확인
     *
     * @param projectId 프로젝트 ID
     * @param version   버전
     * @return 존재 여부
     */
    boolean existsByProject_ProjectIdAndVersion(String projectId, String version);

    /**
     * 프로젝트, 릴리즈 타입, 버전으로 존재 여부 확인
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (standard/custom)
     * @param version     버전
     * @return 존재 여부
     */
    boolean existsByProject_ProjectIdAndReleaseTypeAndVersion(
            String projectId, String releaseType, String version);

    /**
     * 프로젝트, 릴리즈 타입, 사이트, 버전으로 존재 여부 확인
     *
     * @param projectId    프로젝트 ID
     * @param releaseType  릴리즈 타입 (standard/custom)
     * @param siteCode 사이트 코드
     * @param version      버전
     * @return 존재 여부
     */
    boolean existsByProject_ProjectIdAndReleaseTypeAndSite_SiteCodeAndVersion(
            String projectId, String releaseType, String siteCode, String version);

    /**
     * 사이트별 커스텀 버전 존재 여부 확인 (중복 검증용)
     *
     * @param siteId         사이트 ID
     * @param customMajorVersion 커스텀 메이저 버전
     * @param customMinorVersion 커스텀 마이너 버전
     * @param customPatchVersion 커스텀 패치 버전
     * @return 존재 여부
     */
    boolean existsBySite_SiteIdAndCustomMajorVersionAndCustomMinorVersionAndCustomPatchVersion(
            Long siteId, Integer customMajorVersion, Integer customMinorVersion, Integer customPatchVersion);

    /**
     * 사이트에 커스텀 버전이 존재하는지 확인
     *
     * @param siteId 사이트 ID
     * @return 커스텀 버전 존재 여부
     */
    boolean existsBySite_SiteId(Long siteId);

    /**
     * 프로젝트, 릴리즈 타입 내 미승인 버전 존재 여부 확인
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param isApproved  승인 여부
     * @return 미승인 버전 존재 여부
     */
    boolean existsByProject_ProjectIdAndReleaseTypeAndIsApproved(
            String projectId, String releaseType, boolean isApproved);

    /**
     * 프로젝트, 릴리즈 타입 내 미승인 버전 목록 조회
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param isApproved  승인 여부
     * @return 미승인 버전 목록
     */
    List<ReleaseVersion> findAllByProject_ProjectIdAndReleaseTypeAndIsApproved(
            String projectId, String releaseType, boolean isApproved);

    /**
     * 사이트 내 미승인 커스텀 버전 존재 여부 확인
     *
     * @param siteId 사이트 ID
     * @param isApproved 승인 여부
     * @return 미승인 버전 존재 여부
     */
    boolean existsBySite_SiteIdAndIsApproved(Long siteId, boolean isApproved);

    /**
     * 사이트 내 미승인 커스텀 버전 목록 조회
     *
     * @param siteId 사이트 ID
     * @param isApproved 승인 여부
     * @return 미승인 버전 목록
     */
    List<ReleaseVersion> findAllBySite_SiteIdAndIsApproved(Long siteId, boolean isApproved);

    // ========================================
    // Hotfix 관련 메서드
    // ========================================

    /**
     * 특정 버전의 핫픽스 목록 조회 (핫픽스 버전 순)
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @return 핫픽스 목록
     */
    List<ReleaseVersion> findAllByHotfixBaseVersion_ReleaseVersionIdOrderByHotfixVersionAsc(Long hotfixBaseVersionId);

    /**
     * 프로젝트, 릴리즈 타입, 버전, 핫픽스 버전으로 조회
     *
     * @param projectId     프로젝트 ID
     * @param releaseType   릴리즈 타입 (STANDARD/CUSTOM)
     * @param version       기본 버전 (예: 1.3.2)
     * @param hotfixVersion 핫픽스 버전 (예: 1)
     * @return ReleaseVersion
     */
    Optional<ReleaseVersion> findByProject_ProjectIdAndReleaseTypeAndVersionAndHotfixVersion(
            String projectId, String releaseType, String version, Integer hotfixVersion);

    /**
     * 핫픽스 버전 존재 여부 확인
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @param hotfixVersion       핫픽스 버전
     * @return 존재 여부
     */
    boolean existsByHotfixBaseVersion_ReleaseVersionIdAndHotfixVersion(Long hotfixBaseVersionId, Integer hotfixVersion);

    /**
     * 특정 버전에 핫픽스가 존재하는지 확인
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @return 핫픽스 존재 여부
     */
    boolean existsByHotfixBaseVersion_ReleaseVersionId(Long hotfixBaseVersionId);

    /**
     * 일반 버전만 조회 (핫픽스 제외)
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @return 일반 버전 목록
     */
    List<ReleaseVersion> findAllByProject_ProjectIdAndReleaseTypeAndHotfixVersionOrderByCreatedAtDesc(
            String projectId, String releaseType, Integer hotfixVersion);

    // ========================================
    // Build 관련 메서드
    // ========================================

    /**
     * 특정 버전의 빌드 목록 조회 (빌드 버전 내림차순)
     *
     * @param buildBaseVersionId 빌드 원본 버전 ID
     * @return 빌드 목록 (build_version DESC)
     */
    List<ReleaseVersion> findAllByBuildBaseVersion_ReleaseVersionIdOrderByBuildVersionDesc(Long buildBaseVersionId);

    /**
     * 같은 base + 같은 buildVersion(yyMMdd) 안에서 build_iteration 이 가장 큰 행.
     * caller 는 {@code .map(ReleaseVersion::getBuildIteration)} 으로 max 값을 얻을 수 있다.
     */
    Optional<ReleaseVersion> findTopByBuildBaseVersion_ReleaseVersionIdAndBuildVersionOrderByBuildIterationDesc(
            Long buildBaseVersionId, Integer buildVersion);

    /**
     * 프로젝트, 릴리즈 타입, 버전, 핫픽스 버전, 빌드 버전으로 정확히 1행 조회.
     *
     * <p>build_version 도입 후 (version, hotfix_version) 만으로는 row 가 유일하지 않으므로
     * 빌드까지 포함해 정확히 일치하는 단일 row 를 찾는다.
     *
     * @param projectId     프로젝트 ID
     * @param releaseType   릴리즈 타입 (STANDARD/CUSTOM)
     * @param version       기본 버전 (예: 1.1.0 또는 1.1.0-companyA.1.0.0)
     * @param hotfixVersion 핫픽스 버전 (일반/빌드는 0)
     * @param buildVersion  빌드 버전 (일반은 0)
     * @return 정확히 일치하는 ReleaseVersion
     */
    Optional<ReleaseVersion> findByProject_ProjectIdAndReleaseTypeAndVersionAndHotfixVersionAndBuildVersion(
            String projectId, String releaseType, String version, Integer hotfixVersion, Integer buildVersion);

    /**
     * 프로젝트 내 (version, hotfix, build) 모두 일치하는 row 존재 여부.
     *
     * <p>base 업로드 중복 검사용 — base 는 (version, 0, 0) 으로 검사해
     * 같은 version 의 빌드/핫픽스 자식이 있어도 base 재등록을 막지 않는다.
     */
    boolean existsByProject_ProjectIdAndVersionAndHotfixVersionAndBuildVersion(
            String projectId, String version, Integer hotfixVersion, Integer buildVersion);

    // ========================================
    // Custom base 자식 조회 (deleteVersion cascade 거부 검사용)
    // ========================================

    /**
     * 주어진 표준 버전을 {@code custom_base_version_id} 로 가진 커스텀 버전 목록.
     * <p>base 삭제 시 종속 커스텀 자식 존재 여부 검사에 사용.
     */
    List<ReleaseVersion> findAllByCustomBaseVersion_ReleaseVersionIdOrderByCreatedAtDesc(Long customBaseVersionId);
}
