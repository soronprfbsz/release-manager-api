package com.ts.rm.domain.site.repository;

import com.ts.rm.domain.site.entity.SiteProject;
import java.util.List;

/**
 * SiteProject Repository Custom Interface
 *
 * <p>QueryDSL을 사용한 사이트-프로젝트 매핑 쿼리 인터페이스
 */
public interface SiteProjectRepositoryCustom {

    /**
     * 사이트 ID로 프로젝트 매핑 목록 조회 (Project fetch join)
     *
     * @param siteId 사이트 ID
     * @return SiteProject 목록 (Project 포함)
     */
    List<SiteProject> findAllBySiteIdWithProject(Long siteId);

    /**
     * 프로젝트 ID로 사이트 매핑 목록 조회 (Site fetch join)
     *
     * @param projectId 프로젝트 ID
     * @return SiteProject 목록 (Site 포함)
     */
    List<SiteProject> findAllByProjectIdWithSite(String projectId);
}
