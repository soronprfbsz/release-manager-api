package com.ts.rm.domain.account.controller;

import com.ts.rm.domain.account.dto.AccountDto;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.service.AccountService;
import com.ts.rm.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
 * 계정 관리 API
 *
 * <p>계정 목록 조회, 수정, 삭제 기능 제공
 * <p>수정 및 삭제는 ADMIN 권한 필수
 */
@Slf4j
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController implements AccountControllerDocs {

    private final AccountService accountService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AccountDto.DetailResponse>> getMyAccount() {
        log.info("GET /api/accounts/me");

        AccountDto.DetailResponse response = accountService.getMyAccount();

        log.info("My account retrieved successfully - accountId: {}", response.accountId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Override
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<AccountDto.DetailResponse>> updateMyAccount(
            @Valid @RequestBody AccountDto.UpdateRequest request) {
        log.info("PATCH /api/accounts/me - request: {}", request);

        AccountDto.DetailResponse response = accountService.updateMyAccount(request);

        log.info("My account updated successfully - accountId: {}", response.accountId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Override
    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @Valid @RequestBody AccountDto.ChangePasswordRequest request) {
        // 비밀번호 평문은 로깅하지 않는다
        log.info("POST /api/accounts/me/password");

        accountService.changeMyPassword(request);

        log.info("My password changed successfully");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Override
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<AccountDto.ResetPasswordResponse>> resetPassword(
            @PathVariable Long id) {
        log.info("POST /api/accounts/{}/reset-password", id);

        // 응답에 평문 임시 비밀번호가 포함되므로 response 객체를 로깅하지 않는다
        AccountDto.ResetPasswordResponse response = accountService.resetPassword(id);

        log.info("Password reset successfully - accountId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AccountDto.ListResponse>>> getAccounts(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false, defaultValue = "false") Boolean includeSubDepartments,
            @RequestParam(required = false) String departmentType,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "accountId", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/accounts - status: {}, departmentId: {}, includeSubDepartments: {}, departmentType: {}, keyword: {}, pageable: {}",
                status, departmentId, includeSubDepartments, departmentType, keyword, pageable);

        // departmentId 파싱: "null" → 미배치 조회, 숫자 → 해당 부서 조회, 없음 → 전체 조회
        Long parsedDepartmentId = null;
        boolean unassigned = false;

        if (departmentId != null && !departmentId.isBlank()) {
            if ("null".equalsIgnoreCase(departmentId.trim())) {
                unassigned = true;
            } else {
                try {
                    parsedDepartmentId = Long.parseLong(departmentId.trim());
                } catch (NumberFormatException e) {
                    log.warn("Invalid departmentId format: {}", departmentId);
                }
            }
        }

        Page<AccountDto.ListResponse> accountPage = accountService.getAccounts(
                status, parsedDepartmentId, includeSubDepartments, departmentType, unassigned, keyword, pageable);

        log.info("Found {} accounts (page {}/{})", accountPage.getNumberOfElements(),
                accountPage.getNumber() + 1, accountPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(accountPage));
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountDto.DetailResponse>> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountDto.AdminUpdateRequest request) {
        log.info("PUT /api/accounts/{} - request: {}", id, request);

        AccountDto.DetailResponse response = accountService.adminUpdateAccount(id, request);

        log.info("Account updated successfully - accountId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long id) {
        log.info("DELETE /api/accounts/{}", id);

        accountService.deleteAccount(id);

        log.info("Account deleted successfully - accountId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Override
    @PatchMapping("/batch-transfer-department")
    public ResponseEntity<ApiResponse<AccountDto.BatchTransferDepartmentResponse>> batchTransferDepartment(
            @Valid @RequestBody AccountDto.BatchTransferDepartmentRequest request) {
        log.info("PATCH /api/accounts/batch-transfer-department - accountIds: {}, targetDepartmentId: {}",
                request.accountIds(), request.targetDepartmentId());

        AccountDto.BatchTransferDepartmentResponse response =
                accountService.batchTransferDepartment(request);

        log.info("Batch transfer completed - {}", response.message());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
