package com.ts.rm.domain.analytics.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringTemplate;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.SitePatchCount;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.MonthlySitePatchRaw;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.VersionSiteRaw;
import com.ts.rm.domain.site.entity.QSite;
import com.ts.rm.domain.patch.entity.QPatchHistory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 패치 분석 Repository 구현체
 *
 * <p>QueryDSL을 사용한 패치 분석 집계 쿼리 구현
 * <p>patch_history 테이블 사용 (patch_file은 용량 문제로 삭제될 수 있으므로)
 */
@Repository
@RequiredArgsConstructor
public class PatchAnalyticsRepositoryImpl implements PatchAnalyticsRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 프로젝트별 기간 내 사이트별 패치 건수 Top-N 조회
     *
     * <p>CUSTOM 타입 패치만 집계 (STANDARD는 사이트가 없음)
     *
     * @param projectId 프로젝트 ID
     * @param startDate 시작일시
     * @param topN      상위 N개
     * @return 사이트별 패치 건수 목록 (내림차순)
     */
    @Override
    public List<SitePatchCount> findTopSitesByPatchCount(String projectId,
            LocalDateTime startDate, int topN) {
        QPatchHistory patchHistory = QPatchHistory.patchHistory;
        QSite site = QSite.site;

        return queryFactory
                .select(Projections.constructor(SitePatchCount.class,
                        site.siteId,
                        site.siteCode,
                        site.siteName,
                        patchHistory.count()))
                .from(patchHistory)
                .join(patchHistory.site, site)
                .where(
                        patchHistory.project.projectId.eq(projectId),
                        patchHistory.createdAt.goe(startDate),
                        patchHistory.site.isNotNull()
                )
                .groupBy(
                        site.siteId,
                        site.siteCode,
                        site.siteName
                )
                .orderBy(patchHistory.count().desc(), site.siteName.asc())
                .limit(topN)
                .fetch();
    }

    /**
     * 프로젝트별 기간 내 월별+고객별 패치 건수 조회
     *
     * <p>CUSTOM 타입 패치만 집계 (사이트별 통계이므로)
     *
     * @param projectId 프로젝트 ID
     * @param startDate 시작일시
     * @return 월별+고객별 패치 건수 목록 (연월 오름차순, 고객명 오름차순)
     */
    @Override
    public List<MonthlySitePatchRaw> findMonthlySitePatchCounts(String projectId,
            LocalDateTime startDate) {
        QPatchHistory patchHistory = QPatchHistory.patchHistory;
        QSite site = QSite.site;

        // DATE_FORMAT(created_at, '%Y-%m') 형식으로 월별 그룹화
        StringTemplate yearMonthTemplate = Expressions.stringTemplate(
                "DATE_FORMAT({0}, '%Y-%m')",
                patchHistory.createdAt
        );

        return queryFactory
                .select(Projections.constructor(MonthlySitePatchRaw.class,
                        yearMonthTemplate,
                        site.siteName,
                        patchHistory.count()))
                .from(patchHistory)
                .join(patchHistory.site, site)
                .where(
                        patchHistory.project.projectId.eq(projectId),
                        patchHistory.createdAt.goe(startDate),
                        patchHistory.site.isNotNull()
                )
                .groupBy(yearMonthTemplate, site.siteName)
                .orderBy(yearMonthTemplate.asc(), site.siteName.asc())
                .fetch();
    }

    /**
     * 프로젝트별 각 사이트의 최신 완료 patch_history.to_version 조회.
     *
     * <p>서브쿼리로 각 site 의 MAX(completed_at) 인 row 만 선택.
     */
    @Override
    public List<VersionSiteRaw> findLatestVersionBySite(String projectId) {
        QPatchHistory ph = QPatchHistory.patchHistory;
        QPatchHistory ph2 = new QPatchHistory("ph2");
        QSite site = QSite.site;

        return queryFactory
                .select(Projections.constructor(VersionSiteRaw.class,
                        ph.toVersion,
                        site.siteId,
                        site.siteCode,
                        site.siteName))
                .from(ph)
                .join(ph.site, site)
                .where(
                        ph.project.projectId.eq(projectId),
                        ph.site.isNotNull(),
                        ph.completedAt.eq(
                                JPAExpressions
                                        .select(ph2.completedAt.max())
                                        .from(ph2)
                                        .where(ph2.site.eq(ph.site)
                                                .and(ph2.project.projectId.eq(projectId)))
                        )
                )
                .orderBy(site.siteName.asc())
                .fetch();
    }
}
