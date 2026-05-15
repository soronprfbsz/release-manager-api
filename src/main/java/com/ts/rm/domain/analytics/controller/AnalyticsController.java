package com.ts.rm.domain.analytics.controller;

import com.ts.rm.domain.analytics.dto.AnalyticsDto.MonthlyPatchResponse;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.TopCustomersResponse;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.VersionCustomerDistributionResponse;
import com.ts.rm.domain.analytics.service.AnalyticsService;
import com.ts.rm.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분석 API Controller
 *
 * <p>패치 관련 분석 조회 API
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{id}/analytics")
@RequiredArgsConstructor
public class AnalyticsController implements AnalyticsControllerDocs {

    private final AnalyticsService analyticsService;

    @Override
    @GetMapping("/patches/top-customers")
    public ApiResponse<TopCustomersResponse> getTopCustomersByPatchCount(
            @PathVariable String id,
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(defaultValue = "5") int topN) {

        log.info("프로젝트별 고객사별 패치 Top-{} 조회 요청 - projectId: {}, 최근 {}개월", topN, id, months);

        TopCustomersResponse response = analyticsService.getTopCustomersByPatchCount(id, months, topN);

        return ApiResponse.success(response);
    }

    @Override
    @GetMapping("/patches/monthly")
    public ApiResponse<MonthlyPatchResponse> getMonthlyPatchCounts(
            @PathVariable String id,
            @RequestParam(defaultValue = "6") int months) {

        log.info("프로젝트별 월별+고객별 패치 통계 조회 요청 - projectId: {}, 최근 {}개월", id, months);

        MonthlyPatchResponse response = analyticsService.getMonthlyPatchCounts(id, months);

        return ApiResponse.success(response);
    }

    @Override
    @GetMapping("/patches/version-customers")
    public ApiResponse<VersionCustomerDistributionResponse> getVersionCustomerDistribution(
            @PathVariable String id) {

        log.info("프로젝트별 버전별 고객사 분포 조회 요청 - projectId: {}", id);

        VersionCustomerDistributionResponse response = analyticsService.getVersionCustomerDistribution(id);

        return ApiResponse.success(response);
    }
}
