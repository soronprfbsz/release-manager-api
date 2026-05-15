package com.ts.rm.domain.dashboard.controller;

import com.ts.rm.domain.dashboard.dto.DashboardDto;
import com.ts.rm.domain.dashboard.service.DashboardService;
import com.ts.rm.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{id}/dashboard")
@RequiredArgsConstructor
public class DashboardController implements DashboardControllerDocs {

    private final DashboardService dashboardService;

    @Override
    @GetMapping("/recent/standard")
    public ResponseEntity<ApiResponse<DashboardDto.RecentStandardResponse>> getRecentStandardVersions(
            @PathVariable String id,
            @RequestParam(defaultValue = "5") int limit) {

        DashboardDto.RecentStandardResponse response = dashboardService.getRecentStandardVersions(id, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Override
    @GetMapping("/recent/build")
    public ResponseEntity<ApiResponse<DashboardDto.RecentBuildResponse>> getRecentBuildVersions(
            @PathVariable String id,
            @RequestParam(defaultValue = "5") int limit) {

        DashboardDto.RecentBuildResponse response = dashboardService.getRecentBuildVersions(id, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Override
    @GetMapping("/recent/patch")
    public ResponseEntity<ApiResponse<DashboardDto.RecentPatchResponse>> getRecentPatches(
            @PathVariable String id,
            @RequestParam(defaultValue = "5") int limit) {

        DashboardDto.RecentPatchResponse response = dashboardService.getRecentPatches(id, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
