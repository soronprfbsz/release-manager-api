package com.ts.rm.domain.site.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.site.entity.SiteProject;
import com.ts.rm.domain.site.entity.QSite;
import com.ts.rm.domain.site.entity.QSiteProject;
import com.ts.rm.domain.project.entity.QProject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * SiteProject Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 사이트-프로젝트 매핑 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class SiteProjectRepositoryImpl implements SiteProjectRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QSiteProject siteProject = QSiteProject.siteProject;
    private static final QSite site = QSite.site;
    private static final QProject project = QProject.project;

    @Override
    public List<SiteProject> findAllBySiteIdWithProject(Long siteId) {
        return queryFactory
                .selectFrom(siteProject)
                .join(siteProject.project, project).fetchJoin()
                .where(siteProject.site.siteId.eq(siteId))
                .orderBy(project.projectName.asc())
                .fetch();
    }

    @Override
    public List<SiteProject> findAllByProjectIdWithSite(String projectId) {
        return queryFactory
                .selectFrom(siteProject)
                .join(siteProject.site, site).fetchJoin()
                .where(siteProject.project.projectId.eq(projectId))
                .orderBy(site.siteName.asc())
                .fetch();
    }
}
