package com.ts.rm.domain.publishing.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.publishing.entity.Publishing;
import com.ts.rm.domain.publishing.entity.QPublishing;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Publishing Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 복잡한 퍼블리싱 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class PublishingRepositoryImpl implements PublishingRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QPublishing publishing = QPublishing.publishing;

    @Override
    public List<Publishing> findAllWithFilters(
            String publishingCategory,
            String subCategory,
            Long siteId,
            String keyword
    ) {
        return queryFactory
                .selectFrom(publishing)
                .leftJoin(publishing.site).fetchJoin()
                .where(
                        publishingCategoryCondition(publishingCategory),
                        subCategoryCondition(subCategory),
                        siteCondition(siteId),
                        keywordCondition(keyword)
                )
                .orderBy(publishing.sortOrder.asc(), publishing.createdAt.desc())
                .fetch();
    }

    /**
     * 퍼블리싱 카테고리 조건
     */
    private BooleanExpression publishingCategoryCondition(String publishingCategory) {
        return (publishingCategory != null && !publishingCategory.trim().isEmpty())
                ? publishing.publishingCategory.equalsIgnoreCase(publishingCategory.trim())
                : null;
    }

    /**
     * 서브 카테고리 조건
     */
    private BooleanExpression subCategoryCondition(String subCategory) {
        return (subCategory != null && !subCategory.trim().isEmpty())
                ? publishing.subCategory.equalsIgnoreCase(subCategory.trim())
                : null;
    }

    /**
     * 사이트 조건
     * siteId가 null이면 전체, 0이면 표준(site가 null)만, 그 외는 해당 사이트
     */
    private BooleanExpression siteCondition(Long siteId) {
        if (siteId == null) {
            return null;
        }
        if (siteId == 0L) {
            return publishing.site.isNull();
        }
        return publishing.site.siteId.eq(siteId);
    }

    /**
     * 키워드 검색 조건 (퍼블리싱명, 설명 통합 검색)
     */
    private BooleanExpression keywordCondition(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String trimmedKeyword = keyword.trim();
        return publishing.publishingName.containsIgnoreCase(trimmedKeyword)
                .or(publishing.description.containsIgnoreCase(trimmedKeyword));
    }

    @Override
    public Integer findMaxSortOrderByPublishingCategory(String publishingCategory) {
        Integer maxSortOrder = queryFactory
                .select(publishing.sortOrder.max())
                .from(publishing)
                .where(publishing.publishingCategory.eq(publishingCategory))
                .fetchOne();
        return maxSortOrder != null ? maxSortOrder : 0;
    }
}
