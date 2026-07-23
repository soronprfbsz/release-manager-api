package com.ts.rm.domain.publishing.repository;

import com.ts.rm.domain.publishing.entity.Publishing;
import java.util.List;

/**
 * Publishing Repository Custom Interface
 *
 * <p>QueryDSL을 사용한 복잡한 퍼블리싱 쿼리 인터페이스
 */
public interface PublishingRepositoryCustom {

    /**
     * 퍼블리싱 목록 조회 (카테고리 필터링 + 키워드 검색)
     *
     * @param publishingCategory 퍼블리싱 카테고리 (null이면 전체)
     * @param subCategory 서브 카테고리 (null이면 전체)
     * @param siteId 사이트 ID (null이면 전체, 0이면 표준만)
     * @param keyword 검색 키워드 (퍼블리싱명, 설명)
     * @return 퍼블리싱 목록
     */
    List<Publishing> findAllWithFilters(
            String publishingCategory,
            String subCategory,
            Long siteId,
            String keyword
    );

    /**
     * 카테고리별 최대 sortOrder 조회
     */
    Integer findMaxSortOrderByPublishingCategory(String publishingCategory);
}
