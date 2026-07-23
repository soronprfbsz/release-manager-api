package com.ts.rm.domain.site.repository;

import com.ts.rm.domain.site.entity.SiteNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * SiteNote Repository
 *
 * <p>사이트 특이사항 데이터 접근 레이어
 */
@Repository
public interface SiteNoteRepository extends JpaRepository<SiteNote, Long> {

    /**
     * 사이트 ID로 특이사항 목록 조회 (최신순)
     *
     * @param siteId 사이트 ID
     * @return 특이사항 목록
     */
    List<SiteNote> findAllBySite_SiteIdOrderByCreatedAtDesc(Long siteId);

    /**
     * 사이트 ID로 특이사항 개수 조회
     *
     * @param siteId 사이트 ID
     * @return 특이사항 개수
     */
    long countBySite_SiteId(Long siteId);
}
