package com.ts.rm.domain.analytics.repository;

import com.ts.rm.domain.analytics.dto.AnalyticsDto.SitePatchCount;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.MonthlySitePatchRaw;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.VersionSiteRaw;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 패치 분석 Repository
 *
 * <p>QueryDSL을 사용한 패치 분석 집계 쿼리
 */
public interface PatchAnalyticsRepository {

    /**
     * 프로젝트별 기간 내 사이트별 패치 건수 Top-N 조회
     *
     * @param projectId 프로젝트 ID
     * @param startDate 시작일시
     * @param topN      상위 N개
     * @return 사이트별 패치 건수 목록 (내림차순)
     */
    List<SitePatchCount> findTopSitesByPatchCount(String projectId, LocalDateTime startDate, int topN);

    /**
     * 프로젝트별 기간 내 월별+고객별 패치 건수 조회
     *
     * @param projectId 프로젝트 ID
     * @param startDate 시작일시
     * @return 월별+고객별 패치 건수 목록 (연월 오름차순, 고객명 오름차순)
     */
    List<MonthlySitePatchRaw> findMonthlySitePatchCounts(String projectId, LocalDateTime startDate);

    /**
     * 프로젝트별 각 사이트의 최신 완료 패치 to_version 조회
     *
     * <p>사이트별로 가장 최근 완료된 patch_history 의 to_version 을 1건씩 반환.
     * 그룹화 / 정렬은 서비스 레이어에서 수행.
     *
     * @param projectId 프로젝트 ID
     * @return (version, site*) 목록
     */
    List<VersionSiteRaw> findLatestVersionBySite(String projectId);
}
