package com.ts.rm.domain.releaseversion.repository;

import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import java.util.List;
import java.util.Optional;

/**
 * ReleaseVersion Repository Custom Interface
 *
 * <p>QueryDSL을 사용한 복잡한 쿼리 구현을 위한 인터페이스
 * <p>단순 업데이트는 JPA Dirty Checking 사용 (Service에서 엔티티 조회 후 setter 호출)
 */
public interface ReleaseVersionRepositoryCustom {

    /**
     * 버전 범위 조회 (from ~ to)
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param fromVersion 시작 버전
     * @param toVersion   종료 버전
     * @return 버전 목록
     */
    List<ReleaseVersion> findVersionsBetween(String projectId, String releaseType, String fromVersion, String toVersion);

    /**
     * 프로젝트별 릴리즈 타입으로 최신 버전 1개 조회
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @return 최신 버전
     */
    Optional<ReleaseVersion> findLatestByProjectIdAndReleaseType(String projectId, String releaseType);

    /**
     * 프로젝트별 릴리즈 타입으로 최근 N개 조회
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param limit       조회 개수
     * @return 최근 버전 목록
     */
    List<ReleaseVersion> findRecentByProjectIdAndReleaseType(String projectId, String releaseType, int limit);

    /**
     * 버전 범위 내 미승인 버전 조회 (from ~ to)
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param fromVersion 시작 버전
     * @param toVersion   종료 버전
     * @return 미승인 버전 목록 (isApproved = false)
     */
    List<ReleaseVersion> findUnapprovedVersionsBetween(String projectId, String releaseType, String fromVersion, String toVersion);

    /**
     * 고객사별 커스텀 버전 범위 조회 (from ~ to)
     *
     * @param customerId  고객사 ID
     * @param fromVersion 시작 버전 (커스텀 버전)
     * @param toVersion   종료 버전 (커스텀 버전)
     * @return 커스텀 버전 목록
     */
    List<ReleaseVersion> findCustomVersionsBetween(Long customerId, String fromVersion, String toVersion);

    /**
     * 고객사별 커스텀 버전 범위 내 미승인 버전 조회 (from ~ to)
     *
     * @param customerId  고객사 ID
     * @param fromVersion 시작 버전 (커스텀 버전)
     * @param toVersion   종료 버전 (커스텀 버전)
     * @return 미승인 버전 목록 (isApproved = false)
     */
    List<ReleaseVersion> findUnapprovedCustomVersionsBetween(Long customerId, String fromVersion, String toVersion);

    /**
     * 커스텀 버전이 존재하는 고객사 ID 목록 조회
     *
     * @param projectId 프로젝트 ID
     * @return 고객사 ID 목록 (중복 제거)
     */
    List<Long> findCustomerIdsWithCustomVersions(String projectId);

    // ========================================
    // Hotfix 관련 메서드
    // ========================================

    /**
     * 버전 범위 조회 (핫픽스 제외)
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param fromVersion 시작 버전
     * @param toVersion   종료 버전
     * @return 버전 목록 (핫픽스 제외)
     */
    List<ReleaseVersion> findVersionsBetweenExcludingHotfixes(String projectId, String releaseType,
            String fromVersion, String toVersion);

    /**
     * 특정 버전의 최대 핫픽스 버전 조회
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @return 최대 핫픽스 버전 (없으면 0)
     */
    Integer findMaxHotfixVersionByHotfixBaseVersionId(Long hotfixBaseVersionId);

    /**
     * 특정 버전의 핫픽스 개수 조회
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @return 핫픽스 개수
     */
    Long countHotfixesByHotfixBaseVersionId(Long hotfixBaseVersionId);

    /**
     * 프로젝트별 핫픽스가 있는 버전 목록 조회
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @return 핫픽스가 있는 버전 ID 목록
     */
    List<Long> findVersionIdsWithHotfixes(String projectId, String releaseType);

    // ========================================
    // Build 관련 메서드
    // ========================================

    /**
     * 특정 base 버전의 최대 build_version 조회
     *
     * <p>충돌 회피용 retry 시작점 결정에 사용.
     *
     * @param buildBaseVersionId 빌드 원본 버전 ID
     * @return 최대 build_version (빌드가 하나도 없으면 Optional.empty)
     */
    Optional<Integer> findMaxBuildVersionByBaseId(Long buildBaseVersionId);
}
