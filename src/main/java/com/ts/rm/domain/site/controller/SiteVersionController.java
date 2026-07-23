package com.ts.rm.domain.site.controller;

import com.ts.rm.domain.site.dto.SiteVersionDto;
import com.ts.rm.domain.site.service.SiteVersionService;
import com.ts.rm.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이트별 컴포넌트 현재 버전 / 다음 패치 범위 조회 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/sites/{siteId}/projects/{projectId}")
@RequiredArgsConstructor
public class SiteVersionController implements SiteVersionControllerDocs {

    private final SiteVersionService siteVersionService;

    /**
     * 사이트 컴포넌트 현재 버전 목록 조회
     */
    @Override
    @GetMapping("/versions")
    public ApiResponse<List<SiteVersionDto.SiteVersionResponse>> getSiteVersions(
            @PathVariable Long siteId,
            @PathVariable String projectId) {

        log.info("사이트 버전 조회 요청 - siteId: {}, projectId: {}", siteId, projectId);

        List<SiteVersionDto.SiteVersionResponse> response =
                siteVersionService.findBySiteAndProject(siteId, projectId);

        return ApiResponse.success(response);
    }

    /**
     * 패치 생성 폼 자동 채우기용 다음 패치 범위 제안
     */
    @Override
    @GetMapping("/next-patch-range")
    public ApiResponse<SiteVersionDto.NextPatchRangeResponse> getNextPatchRange(
            @PathVariable Long siteId,
            @PathVariable String projectId) {

        log.info("다음 패치 범위 조회 요청 - siteId: {}, projectId: {}", siteId, projectId);

        SiteVersionDto.NextPatchRangeResponse response =
                siteVersionService.getNextPatchRange(siteId, projectId);

        return ApiResponse.success(response);
    }
}
