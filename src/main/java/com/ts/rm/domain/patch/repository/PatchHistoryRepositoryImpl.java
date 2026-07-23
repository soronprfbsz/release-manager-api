package com.ts.rm.domain.patch.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.patch.entity.PatchHistory;
import com.ts.rm.domain.patch.entity.QPatchHistory;
import com.ts.rm.global.querydsl.QuerydslPaginationUtil;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * PatchHistory Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 복잡한 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class PatchHistoryRepositoryImpl implements PatchHistoryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QPatchHistory patchHistory = QPatchHistory.patchHistory;

    @Override
    public Page<PatchHistory> findAllWithFilters(String projectId, Long siteId, Pageable pageable) {
        // 1. Content 쿼리
        JPAQuery<PatchHistory> contentQuery = queryFactory
                .selectFrom(patchHistory)
                .where(
                        projectIdCondition(projectId),
                        siteIdCondition(siteId)
                );

        // 2. Count 쿼리
        JPAQuery<Long> countQuery = queryFactory
                .select(patchHistory.count())
                .from(patchHistory)
                .where(
                        projectIdCondition(projectId),
                        siteIdCondition(siteId)
                );

        // 3. 정렬 필드 매핑
        Map<String, com.querydsl.core.types.Expression<?>> sortMapping = Map.of(
                "historyId", patchHistory.historyId,
                "patchName", patchHistory.patchName,
                "releaseType", patchHistory.releaseType,
                "fromVersion", patchHistory.fromVersion,
                "toVersion", patchHistory.toVersion,
                "createdAt", patchHistory.createdAt
        );

        // 4. 페이징 적용
        return QuerydslPaginationUtil.applyPagination(
                contentQuery,
                countQuery,
                pageable,
                sortMapping,
                (java.util.List<com.querydsl.core.types.OrderSpecifier<?>>) null,
                patchHistory.createdAt.desc()
        );
    }

    @Override
    public List<PatchHistory> findRecentByProjectIdAndReleaseType(String projectId, String releaseType, int limit) {
        return queryFactory
                .selectFrom(patchHistory)
                .where(
                        patchHistory.project.projectId.eq(projectId),
                        patchHistory.releaseType.eq(releaseType)
                )
                .orderBy(patchHistory.createdAt.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<PatchHistory> findRecentByProjectId(String projectId, int limit) {
        return queryFactory
                .selectFrom(patchHistory)
                .where(patchHistory.project.projectId.eq(projectId))
                .orderBy(patchHistory.createdAt.desc())
                .limit(limit)
                .fetch();
    }

    /**
     * 프로젝트 ID 조건
     */
    private BooleanExpression projectIdCondition(String projectId) {
        return (projectId != null && !projectId.isBlank())
                ? patchHistory.project.projectId.eq(projectId)
                : null;
    }

    /**
     * 사이트 ID 조건
     */
    private BooleanExpression siteIdCondition(Long siteId) {
        return (siteId != null)
                ? patchHistory.site.siteId.eq(siteId)
                : null;
    }
}
