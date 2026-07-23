package com.ts.rm.domain.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * 분석 API DTO
 */
public final class AnalyticsDto {

    private AnalyticsDto() {
    }

    /**
     * 사이트별 패치 통계 응답
     *
     * @param siteId   사이트 ID
     * @param siteCode 사이트 코드
     * @param siteName 사이트명
     * @param patchCount   패치 건수
     */
    @Schema(description = "사이트별 패치 통계")
    public record SitePatchCount(
            @Schema(description = "사이트 ID", example = "1")
            Long siteId,

            @Schema(description = "사이트 코드", example = "SITE_A")
            String siteCode,

            @Schema(description = "사이트명", example = "A사")
            String siteName,

            @Schema(description = "패치 건수", example = "15")
            Long patchCount
    ) {
    }

    /**
     * 사이트별 패치 Top-N 응답
     *
     * @param months    조회 기간 (개월)
     * @param topN      상위 N개
     * @param sites 사이트별 패치 통계 목록
     */
    @Schema(description = "사이트별 패치 Top-N 응답")
    public record TopSitesResponse(
            @Schema(description = "조회 기간 (개월)", example = "6")
            int months,

            @Schema(description = "상위 N개", example = "5")
            int topN,

            @Schema(description = "사이트별 패치 통계 목록")
            List<SitePatchCount> sites
    ) {
    }

    /**
     * 월별+고객별 패치 원본 데이터 (내부 사용)
     *
     * @param yearMonth    연월 (YYYY-MM)
     * @param siteName 사이트명
     * @param patchCount   패치 건수
     */
    public record MonthlySitePatchRaw(
            String yearMonth,
            String siteName,
            Long patchCount
    ) {
    }

    /**
     * 월별+고객별 패치 통계
     *
     * @param yearMonth      연월 (YYYY-MM)
     * @param siteCounts 사이트별 패치 건수 (사이트명 -> 패치 건수)
     */
    @Schema(description = "월별+고객별 패치 통계")
    public record MonthlySitePatchCount(
            @Schema(description = "연월 (YYYY-MM)", example = "2025-06")
            String yearMonth,

            @Schema(description = "사이트별 패치 건수", example = "{\"A회사\": 3, \"B회사\": 2}")
            Map<String, Long> siteCounts
    ) {
    }

    /**
     * 월별+고객별 패치 통계 응답
     *
     * @param months    조회 기간 (개월)
     * @param sites 사이트 목록
     * @param monthly   월별+고객별 패치 통계 목록
     */
    @Schema(description = "월별+고객별 패치 통계 응답")
    public record MonthlyPatchResponse(
            @Schema(description = "조회 기간 (개월)", example = "6")
            int months,

            @Schema(description = "사이트 목록", example = "[\"A회사\", \"B회사\", \"C회사\"]")
            List<String> sites,

            @Schema(description = "월별+고객별 패치 통계 목록")
            List<MonthlySitePatchCount> monthly
    ) {
    }

    /**
     * 버전별 사이트 분포 - 원본 (내부 사용)
     */
    public record VersionSiteRaw(
            String version,
            Long siteId,
            String siteCode,
            String siteName
    ) {
    }

    /**
     * 사이트 간단 정보
     */
    @Schema(description = "사이트 간단 정보")
    public record SiteInfo(
            @Schema(description = "사이트 ID", example = "1")
            Long siteId,

            @Schema(description = "사이트 코드", example = "SITE_A")
            String siteCode,

            @Schema(description = "사이트명", example = "A회사")
            String siteName
    ) {
    }

    /**
     * 버전별 사이트 그룹
     *
     * @param version   버전 (PatchHistory.toVersion 그대로)
     * @param count     해당 버전을 운영중인 사이트 수
     * @param sites 사이트 목록
     */
    @Schema(description = "버전별 사이트 그룹")
    public record VersionSiteGroup(
            @Schema(description = "버전", example = "1.1.0")
            String version,

            @Schema(description = "해당 버전을 운영중인 사이트 수", example = "3")
            Long count,

            @Schema(description = "사이트 목록")
            List<SiteInfo> sites
    ) {
    }

    /**
     * 버전별 사이트 분포 응답
     *
     * <p>각 사이트의 최근 완료 patch_history.to_version 기준으로 집계.
     */
    @Schema(description = "버전별 사이트 분포 응답")
    public record VersionSiteDistributionResponse(
            @Schema(description = "버전별 그룹 목록 (버전 내림차순)")
            List<VersionSiteGroup> versions
    ) {
    }
}
