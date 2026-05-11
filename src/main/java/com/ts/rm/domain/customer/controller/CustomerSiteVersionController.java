package com.ts.rm.domain.customer.controller;

import com.ts.rm.domain.customer.dto.CustomerSiteVersionDto;
import com.ts.rm.domain.customer.service.CustomerSiteVersionService;
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
@RequestMapping("/api/customers/{customerId}/projects/{projectId}")
@RequiredArgsConstructor
public class CustomerSiteVersionController implements CustomerSiteVersionControllerDocs {

    private final CustomerSiteVersionService siteVersionService;

    /**
     * 사이트 컴포넌트 현재 버전 목록 조회
     */
    @Override
    @GetMapping("/site-versions")
    public ApiResponse<List<CustomerSiteVersionDto.SiteVersionResponse>> getSiteVersions(
            @PathVariable Long customerId,
            @PathVariable String projectId) {

        log.info("사이트 버전 조회 요청 - customerId: {}, projectId: {}", customerId, projectId);

        List<CustomerSiteVersionDto.SiteVersionResponse> response =
                siteVersionService.findByCustomerAndProject(customerId, projectId);

        return ApiResponse.success(response);
    }

    /**
     * 패치 생성 폼 자동 채우기용 다음 패치 범위 제안
     */
    @Override
    @GetMapping("/next-patch-range")
    public ApiResponse<CustomerSiteVersionDto.NextPatchRangeResponse> getNextPatchRange(
            @PathVariable Long customerId,
            @PathVariable String projectId) {

        log.info("다음 패치 범위 조회 요청 - customerId: {}, projectId: {}", customerId, projectId);

        CustomerSiteVersionDto.NextPatchRangeResponse response =
                siteVersionService.getNextPatchRange(customerId, projectId);

        return ApiResponse.success(response);
    }
}
