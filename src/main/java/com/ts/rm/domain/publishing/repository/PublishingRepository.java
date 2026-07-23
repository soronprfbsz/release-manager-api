package com.ts.rm.domain.publishing.repository;

import com.ts.rm.domain.publishing.entity.Publishing;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Publishing Repository
 */
@Repository
public interface PublishingRepository extends JpaRepository<Publishing, Long>, PublishingRepositoryCustom {

    /**
     * 카테고리별 조회 (정렬순서 오름차순, 생성일시 내림차순)
     */
    List<Publishing> findByPublishingCategoryOrderBySortOrderAscCreatedAtDesc(String publishingCategory);

    /**
     * 사이트별 퍼블리싱 조회
     */
    List<Publishing> findBySite_SiteIdOrderBySortOrderAscCreatedAtDesc(Long siteId);


    /**
     * 퍼블리싱명 중복 확인
     */
    boolean existsByPublishingName(String publishingName);

    /**
     * 퍼블리싱명 중복 확인 (자기 자신 제외)
     */
    boolean existsByPublishingNameAndPublishingIdNot(String publishingName, Long publishingId);
}
