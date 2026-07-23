package com.ts.rm.domain.releaseversion.repository;

import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import java.util.List;

/**
 * ReleaseVersionHierarchy Custom Repository
 *
 * <p>클로저 테이블 기반 계층 구조 조회를 위한 커스텀 쿼리
 */
public interface ReleaseVersionHierarchyRepositoryCustom {

    /**
     * 특정 릴리즈 타입의 모든 버전을 계층 구조로 조회
     *
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @return 릴리즈 버전 목록 (버전 순서대로 정렬)
     */
    List<ReleaseVersion> findAllByReleaseTypeWithHierarchy(String releaseType);

    /**
     * 프로젝트 + 릴리즈 타입별 모든 버전을 계층 구조로 조회
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @return 릴리즈 버전 목록 (버전 순서대로 정렬)
     */
    List<ReleaseVersion> findAllByProjectIdAndReleaseTypeWithHierarchy(String projectId,
            String releaseType);

    /**
     * 특정 릴리즈 타입 + 사이트 코드의 모든 버전을 계층 구조로 조회
     *
     * @param releaseType  릴리즈 타입 (CUSTOM)
     * @param siteCode 사이트 코드
     * @return 릴리즈 버전 목록 (버전 순서대로 정렬)
     */
    List<ReleaseVersion> findAllByReleaseTypeAndSiteWithHierarchy(String releaseType,
            String siteCode);

    /**
     * 프로젝트 + 릴리즈 타입 + 사이트 코드의 모든 버전을 계층 구조로 조회
     *
     * @param projectId    프로젝트 ID
     * @param releaseType  릴리즈 타입 (CUSTOM)
     * @param siteCode 사이트 코드
     * @return 릴리즈 버전 목록 (버전 순서대로 정렬)
     */
    List<ReleaseVersion> findAllByProjectIdAndReleaseTypeAndSiteWithHierarchy(String projectId,
            String releaseType, String siteCode);

    /**
     * Descendant ID로 계층 구조 삭제
     */
    void deleteByDescendantId(Long descendantId);

    /**
     * Ancestor ID로 계층 구조 삭제
     */
    void deleteByAncestorId(Long ancestorId);
}
