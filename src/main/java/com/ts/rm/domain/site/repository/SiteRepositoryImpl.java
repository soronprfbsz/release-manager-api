package com.ts.rm.domain.site.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.site.entity.QSite;
import com.ts.rm.domain.site.entity.QSiteProject;
import com.ts.rm.domain.project.entity.QProject;
import com.ts.rm.domain.releaseversion.entity.QReleaseVersion;
import com.ts.rm.global.querydsl.QuerydslPaginationUtil;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Site Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 복잡한 사이트 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class SiteRepositoryImpl implements SiteRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QSite site = QSite.site;
    private static final QSiteProject siteProject = QSiteProject.siteProject;
    private static final QProject project = QProject.project;
    private static final QReleaseVersion releaseVersion = QReleaseVersion.releaseVersion;

    @Override
    public Page<Site> findAllWithProjectInfo(String projectId, Boolean isActive, String keyword, Pageable pageable) {
        // 1. 기본 쿼리 생성
        JPAQuery<Site> contentQuery = queryFactory
                .selectDistinct(site)
                .from(site)
                .leftJoin(siteProject).on(siteProject.site.siteId.eq(site.siteId))
                .leftJoin(project).on(siteProject.project.projectId.eq(project.projectId))
                .where(
                        projectIdCondition(projectId),
                        isActiveCondition(isActive),
                        keywordCondition(keyword)
                );

        // 2. Count 쿼리 생성 (동일 조건)
        JPAQuery<Long> countQuery = queryFactory
                .select(site.countDistinct())
                .from(site)
                .leftJoin(siteProject).on(siteProject.site.siteId.eq(site.siteId))
                .leftJoin(project).on(siteProject.project.projectId.eq(project.projectId))
                .where(
                        projectIdCondition(projectId),
                        isActiveCondition(isActive),
                        keywordCondition(keyword)
                );

        // 3. 정렬 필드 매핑 정의
        // hasCustomVersion: 커스텀 버전 존재 여부 (EXISTS 서브쿼리 → CASE WHEN으로 1/0 변환)
        NumberExpression<Integer> hasCustomVersionExpression = new CaseBuilder()
                .when(JPAExpressions.selectOne()
                        .from(releaseVersion)
                        .where(releaseVersion.site.siteId.eq(site.siteId))
                        .exists())
                .then(1)
                .otherwise(0);

        Map<String, com.querydsl.core.types.Expression<?>> sortMapping = new HashMap<>();
        // Site 필드
        sortMapping.put("siteId", site.siteId);
        sortMapping.put("siteCode", site.siteCode);
        sortMapping.put("siteName", site.siteName);
        sortMapping.put("isActive", site.isActive);
        sortMapping.put("createdAt", site.createdAt);
        sortMapping.put("updatedAt", site.updatedAt);
        // Project 필드
        sortMapping.put("project.projectName", project.projectName);
        sortMapping.put("project.projectId", project.projectId);
        // SiteProject 필드
        sortMapping.put("lastPatchedVersion", siteProject.lastPatchedVersion);
        sortMapping.put("lastPatchedAt", siteProject.lastPatchedAt);
        // 커스텀 버전 존재 여부 (서브쿼리)
        sortMapping.put("hasCustomVersion", hasCustomVersionExpression);

        // 4. 공통 유틸리티로 페이징/정렬 적용
        return QuerydslPaginationUtil.applyPagination(
                contentQuery,
                countQuery,
                pageable,
                sortMapping,
                site.siteName.asc() // 기본 정렬: 사이트명 오름차순
        );
    }

    /**
     * 프로젝트 ID 조건
     */
    private BooleanExpression projectIdCondition(String projectId) {
        return (projectId != null && !projectId.isBlank())
                ? siteProject.project.projectId.eq(projectId)
                : null;
    }

    /**
     * 활성화 여부 조건
     */
    private BooleanExpression isActiveCondition(Boolean isActive) {
        return isActive != null ? site.isActive.eq(isActive) : null;
    }

    /**
     * 키워드 검색 조건 (사이트코드, 사이트명, 설명 통합 검색)
     */
    private BooleanExpression keywordCondition(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String trimmedKeyword = keyword.trim();
        return site.siteCode.containsIgnoreCase(trimmedKeyword)
                .or(site.siteName.containsIgnoreCase(trimmedKeyword))
                .or(site.description.containsIgnoreCase(trimmedKeyword));
    }
}
