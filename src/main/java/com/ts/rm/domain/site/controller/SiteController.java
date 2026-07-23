package com.ts.rm.domain.site.controller;

import com.ts.rm.domain.site.dto.SiteDto;
import com.ts.rm.domain.site.service.SiteService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.security.SecurityUtil;
import com.ts.rm.global.security.TokenInfo;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Site Controller
 *
 * <p>사이트 관리 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController implements SiteControllerDocs {

    private final SiteService siteService;

    /**
     * 사이트 생성
     *
     * @param request 사이트 생성 요청
     * @return 생성된 사이트 정보
     */
    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<SiteDto.DetailResponse>> createSite(
            @Valid @RequestBody SiteDto.CreateRequest request) {

        log.info("사이트 생성 요청 - siteCode: {}, siteName: {}",
                request.siteCode(), request.siteName());

        // SecurityContext에서 인증 정보 추출
        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();
        log.info("사이트 생성자 정보: email={}, role={}", tokenInfo.email(), tokenInfo.role());

        SiteDto.DetailResponse response = siteService.createSite(request, tokenInfo.email());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * 사이트 조회 (ID)
     *
     * @param id 사이트 ID
     * @return 사이트 상세 정보
     */
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SiteDto.DetailResponse>> getSiteById(@PathVariable Long id) {
        SiteDto.DetailResponse response = siteService.getSiteById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 사이트 목록 조회 (페이징)
     *
     * @param projectId 프로젝트 ID (optional)
     * @param isActive  활성화 여부 필터 (true: 활성화만, false: 비활성화만, null: 전체)
     * @param keyword   사이트명 검색 키워드 (optional)
     * @param pageable  페이징 정보
     * @return 사이트 페이지
     */
    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SiteDto.ListResponse>>> getSites(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String keyword,
            @ParameterObject Pageable pageable) {
        Page<SiteDto.ListResponse> response = siteService.getSitesWithPaging(projectId, isActive, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 사이트 정보 수정
     *
     * @param id      사이트 ID
     * @param request 수정 요청
     * @return 수정된 사이트 정보
     */
    @Override
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SiteDto.DetailResponse>> updateSite(
            @PathVariable Long id,
            @Valid @RequestBody SiteDto.UpdateRequest request) {

        log.info("사이트 수정 요청 - id: {}", id);

        // SecurityContext에서 인증 정보 추출
        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();
        log.info("사이트 수정자 정보: email={}, role={}", tokenInfo.email(), tokenInfo.role());

        SiteDto.DetailResponse response = siteService.updateSite(id, request, tokenInfo.email());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 사이트 삭제
     *
     * @param id 사이트 ID
     * @return 성공 응답
     */
    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSite(@PathVariable Long id) {
        siteService.deleteSite(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 사이트 패치 상태 초기화 (ADMIN 전용)
     *
     * <p>customer_site_version / customer_project last_patched_* / patch_history 를 삭제·초기화한다.
     *
     * @param siteId 사이트 ID
     * @return 각 테이블별 처리 건수
     */
    @Override
    @PostMapping("/{siteId}/reset-patch-state")
    public ResponseEntity<ApiResponse<SiteDto.ResetPatchStateResponse>> resetPatchState(
            @PathVariable Long siteId) {

        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();
        log.info("사이트 패치 상태 초기화 요청 - siteId: {}, role: {}, email: {}",
                siteId, tokenInfo.role(), tokenInfo.email());

        if (!"ADMIN".equals(tokenInfo.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "ADMIN 권한만 사이트 초기화를 실행할 수 있습니다.");
        }

        SiteDto.ResetPatchStateResponse response =
                siteService.resetPatchState(siteId, tokenInfo.email());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
