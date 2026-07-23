package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.PatchHistory;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * PatchHistory Repository Custom Interface
 *
 * <p>QueryDSL을 사용한 복잡한 쿼리 인터페이스
 */
public interface PatchHistoryRepositoryCustom {

    /**
     * 프로젝트/사이트별 패치 이력 조회 (필터링 + 페이징)
     *
     * @param projectId  프로젝트 ID (null이면 전체)
     * @param siteId 사이트 ID (null이면 전체)
     * @param pageable   페이징 정보
     * @return 패치 이력 페이지
     */
    Page<PatchHistory> findAllWithFilters(String projectId, Long siteId, Pageable pageable);

    /**
     * 프로젝트별 최근 패치 이력 조회 (대시보드용)
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param limit       조회 개수
     * @return 최근 패치 이력 목록 (생성일 내림차순)
     */
    List<PatchHistory> findRecentByProjectIdAndReleaseType(String projectId, String releaseType, int limit);

    /**
     * 프로젝트별 최근 패치 이력 조회 (표준+커스텀 전체)
     *
     * @param projectId 프로젝트 ID
     * @param limit     조회 개수
     * @return 최근 패치 이력 목록 (생성일 내림차순)
     */
    List<PatchHistory> findRecentByProjectId(String projectId, int limit);
}
