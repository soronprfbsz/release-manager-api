package com.ts.rm.domain.customer.controller;

import com.ts.rm.domain.customer.dto.CustomerDto;
import com.ts.rm.domain.customer.service.CustomerService;
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
 * Customer Controller
 *
 * <p>고객사 관리 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController implements CustomerControllerDocs {

    private final CustomerService customerService;

    /**
     * 고객사 생성
     *
     * @param request 고객사 생성 요청
     * @return 생성된 고객사 정보
     */
    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerDto.DetailResponse>> createCustomer(
            @Valid @RequestBody CustomerDto.CreateRequest request) {

        log.info("고객사 생성 요청 - customerCode: {}, customerName: {}",
                request.customerCode(), request.customerName());

        // SecurityContext에서 인증 정보 추출
        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();
        log.info("고객사 생성자 정보: email={}, role={}", tokenInfo.email(), tokenInfo.role());

        CustomerDto.DetailResponse response = customerService.createCustomer(request, tokenInfo.email());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * 고객사 조회 (ID)
     *
     * @param id 고객사 ID
     * @return 고객사 상세 정보
     */
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDto.DetailResponse>> getCustomerById(@PathVariable Long id) {
        CustomerDto.DetailResponse response = customerService.getCustomerById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 고객사 목록 조회 (페이징)
     *
     * @param projectId 프로젝트 ID (optional)
     * @param isActive  활성화 여부 필터 (true: 활성화만, false: 비활성화만, null: 전체)
     * @param keyword   고객사명 검색 키워드 (optional)
     * @param pageable  페이징 정보
     * @return 고객사 페이지
     */
    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CustomerDto.ListResponse>>> getCustomers(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String keyword,
            @ParameterObject Pageable pageable) {
        Page<CustomerDto.ListResponse> response = customerService.getCustomersWithPaging(projectId, isActive, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 고객사 정보 수정
     *
     * @param id      고객사 ID
     * @param request 수정 요청
     * @return 수정된 고객사 정보
     */
    @Override
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDto.DetailResponse>> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerDto.UpdateRequest request) {

        log.info("고객사 수정 요청 - id: {}", id);

        // SecurityContext에서 인증 정보 추출
        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();
        log.info("고객사 수정자 정보: email={}, role={}", tokenInfo.email(), tokenInfo.role());

        CustomerDto.DetailResponse response = customerService.updateCustomer(id, request, tokenInfo.email());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 고객사 삭제
     *
     * @param id 고객사 ID
     * @return 성공 응답
     */
    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 고객사 패치 상태 초기화 (ADMIN 전용)
     *
     * <p>customer_site_version / customer_project last_patched_* / patch_history 를 삭제·초기화한다.
     *
     * @param customerId 고객사 ID
     * @return 각 테이블별 처리 건수
     */
    @Override
    @PostMapping("/{customerId}/reset-patch-state")
    public ResponseEntity<ApiResponse<CustomerDto.ResetPatchStateResponse>> resetPatchState(
            @PathVariable Long customerId) {

        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();
        log.info("고객사 패치 상태 초기화 요청 - customerId: {}, role: {}, email: {}",
                customerId, tokenInfo.role(), tokenInfo.email());

        if (!"ADMIN".equals(tokenInfo.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "ADMIN 권한만 고객사 초기화를 실행할 수 있습니다.");
        }

        CustomerDto.ResetPatchStateResponse response =
                customerService.resetPatchState(customerId, tokenInfo.email());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
