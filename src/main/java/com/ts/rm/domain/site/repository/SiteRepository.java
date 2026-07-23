package com.ts.rm.domain.site.repository;

import com.ts.rm.domain.site.entity.Site;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Site Repository
 *
 * <p>사이트 정보 조회 및 관리를 위한 Repository
 * <p>업데이트는 JPA Dirty Checking 사용 (Service에서 엔티티 조회 후 setter 호출)
 */
public interface SiteRepository extends JpaRepository<Site, Long>,
        SiteRepositoryCustom {

    /**
     * 사이트 코드로 사이트 조회
     *
     * @param siteCode 사이트 코드
     * @return Site
     */
    Optional<Site> findBySiteCode(String siteCode);

    /**
     * 활성화된 사이트 목록 조회
     *
     * @param isActive 활성 여부
     * @return 사이트 목록
     */
    List<Site> findAllByIsActive(Boolean isActive);

    /**
     * 활성화된 사이트 페이징 조회
     *
     * @param isActive 활성 여부
     * @param pageable 페이징 정보
     * @return 사이트 페이지
     */
    Page<Site> findAllByIsActive(Boolean isActive, Pageable pageable);

    /**
     * 사이트 코드 존재 여부 확인
     *
     * @param siteCode 사이트 코드
     * @return 존재 여부
     */
    boolean existsBySiteCode(String siteCode);

    /**
     * 사이트명으로 검색 (부분 일치)
     *
     * @param keyword 검색 키워드
     * @return 사이트 목록
     */
    List<Site> findBySiteNameContaining(String keyword);

    /**
     * 사이트명으로 검색 페이징 (부분 일치)
     *
     * @param keyword  검색 키워드
     * @param pageable 페이징 정보
     * @return 사이트 페이지
     */
    Page<Site> findBySiteNameContaining(String keyword, Pageable pageable);
}
