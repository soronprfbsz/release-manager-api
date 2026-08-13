package com.ts.rm.domain.account.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ts.rm.domain.account.dto.AccountDto;
import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountRole;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.mapper.AccountDtoMapper;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.common.repository.CodeRepository;
import com.ts.rm.domain.department.entity.Department;
import com.ts.rm.domain.department.repository.DepartmentHierarchyRepository;
import com.ts.rm.domain.department.repository.DepartmentRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.pagination.PageRowNumberUtil;
import com.ts.rm.global.security.SecurityUtil;
import com.ts.rm.global.util.PasswordGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Account Service
 * - MapStruct를 사용한 Entity ↔ DTO 자동 변환
 * - AccountDto 단일 클래스에서 Request/Response DTO 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private static final String POSITION_CODE_TYPE = "POSITION";

    private final AccountRepository accountRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentHierarchyRepository departmentHierarchyRepository;
    private final CodeRepository codeRepository;
    private final AccountDtoMapper mapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AccountDto.DetailResponse createAccount(AccountDto.CreateRequest request) {
        log.info("Creating account with email: {}", request.email());

        if (accountRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.ACCOUNT_EMAIL_CONFLICT);
        }

        Account account = mapper.toEntity(request);

        // 부서 설정
        if (request.departmentId() != null) {
            Department department = findDepartmentById(request.departmentId());
            account.setDepartment(department);
        }

        Account savedAccount = accountRepository.save(account);

        log.info("Account created successfully with id: {}", savedAccount.getAccountId());
        return toDetailResponseWithPositionName(savedAccount);
    }

    public AccountDto.DetailResponse getAccountByAccountId(Long accountId) {
        Account account = findAccountByAccountId(accountId);
        return toDetailResponseWithPositionName(account);
    }

    /**
     * 계정 목록 조회 (필터링 및 검색, 페이징)
     *
     * @param status 계정 상태 필터
     * @param departmentId 부서 ID 필터
     * @param includeSubDepartments 하위 부서 포함 여부 (true: 해당 부서 + 모든 하위 부서, false: 해당 부서만)
     * @param departmentType 부서 유형 필터 (DEVELOPMENT: 개발, ENGINEER: 엔지니어)
     * @param unassigned 미배치 계정만 조회 (true: department가 null인 계정만)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 계정 목록 페이지
     */
    public Page<AccountDto.ListResponse> getAccounts(
            AccountStatus status, Long departmentId, Boolean includeSubDepartments,
            String departmentType, boolean unassigned, String keyword, Pageable pageable) {
        String statusName = status != null ? status.name() : null;

        // 부서 ID 목록 생성
        List<Long> departmentIds = buildDepartmentIds(departmentId, includeSubDepartments);

        // 하위 부서 포함 조회 시, 요청한 부서의 계정을 우선 정렬하기 위해 primaryDepartmentId 전달
        Long primaryDepartmentId = Boolean.TRUE.equals(includeSubDepartments) ? departmentId : null;

        Page<Account> accountPage = accountRepository.findAllWithFilters(
                statusName, departmentIds, primaryDepartmentId, departmentType, unassigned, keyword, pageable);

        return PageRowNumberUtil.mapWithRowNumber(accountPage, (account, rowNumber) ->
                new AccountDto.ListResponse(
                        rowNumber,
                        account.getAccountId(),
                        account.getEmail(),
                        account.getAccountName(),
                        account.getPhone(),
                        account.getPosition(),
                        getPositionName(account.getPosition()),
                        account.getDepartment() != null ? account.getDepartment().getDepartmentId() : null,
                        account.getDepartment() != null ? account.getDepartment().getDepartmentName() : null,
                        account.getAvatarStyle(),
                        account.getAvatarSeed(),
                        account.getRole(),
                        account.getStatus(),
                        account.getLastLoginAt(),
                        account.getCreatedAt()
                )
        );
    }

    /**
     * 부서 ID 목록 생성 (하위 부서 포함 여부에 따라)
     */
    private List<Long> buildDepartmentIds(Long departmentId, Boolean includeSubDepartments) {
        if (departmentId == null) {
            return null;
        }

        List<Long> departmentIds = new ArrayList<>();
        departmentIds.add(departmentId);

        // 하위 부서 포함인 경우 하위 부서 ID 목록 추가
        if (Boolean.TRUE.equals(includeSubDepartments)) {
            List<Long> descendantIds = departmentHierarchyRepository.findDescendantIds(departmentId);
            departmentIds.addAll(descendantIds);
        }

        return departmentIds;
    }

    @Transactional
    public AccountDto.DetailResponse updateAccount(Long accountId, AccountDto.UpdateRequest request) {
        log.info("Updating account with accountId: {}", accountId);

        Account account = findAccountByAccountId(accountId);

        if (request.accountName() != null) {
            account.setAccountName(request.accountName());
        }

        log.info("Account updated successfully with accountId: {}", accountId);
        return toDetailResponseWithPositionName(account);
    }

    @Transactional
    public void deleteAccount(Long accountId) {
        log.info("Deleting account with accountId: {}", accountId);

        Account account = findAccountByAccountId(accountId);

        if (AccountRole.ADMIN.getCodeId().equals(account.getRole())) {
            long adminCount = accountRepository.countByRole(AccountRole.ADMIN.getCodeId());
            if (adminCount <= 1) {
                log.warn("Cannot delete last ADMIN account - accountId: {}", accountId);
                throw new BusinessException(ErrorCode.LAST_ADMIN_CANNOT_DELETE);
            }
        }

        accountRepository.delete(account);
        log.info("Account deleted successfully with accountId: {}", accountId);
    }

    @Transactional
    public void updateAccountStatus(Long accountId, AccountStatus status) {
        log.info("Updating account status - accountId: {}, status: {}", accountId, status);

        Account account = findAccountByAccountId(accountId);
        account.setStatus(status.name());

        log.info("Account status updated - accountId: {}, status: {}", accountId, status);
    }

    /**
     * 계정 수정 (ADMIN 전용 - 이름, 권한, 상태, 부서, 직급 수정)
     */
    @Transactional
    public AccountDto.DetailResponse adminUpdateAccount(Long accountId, AccountDto.AdminUpdateRequest request) {
        log.info("Admin updating account with accountId: {}", accountId);

        Account account = findAccountByAccountId(accountId);

        // 이름 수정
        if (request.accountName() != null && !request.accountName().isBlank()) {
            account.setAccountName(request.accountName());
            log.debug("Account name updated to {} for accountId: {}", request.accountName(), accountId);
        }

        // 연락처 수정
        if (request.phone() != null) {
            account.setPhone(request.phone());
            log.debug("Phone updated for accountId: {}", accountId);
        }

        // 직급 수정
        if (request.position() != null) {
            validatePositionCode(request.position());
            account.setPosition(request.position());
            log.debug("Position updated to {} for accountId: {}", request.position(), accountId);
        }

        // 부서 수정
        //  - unassignDepartment = true : 부서 배치 해제
        //  - departmentId 지정         : 해당 부서로 배치
        //  - 둘 다 미전송               : 부서 변경 없음
        if (Boolean.TRUE.equals(request.unassignDepartment())) {
            account.setDepartment(null);
            log.debug("Department unassigned for accountId: {}", accountId);
        } else if (request.departmentId() != null) {
            Department department = findDepartmentById(request.departmentId());
            account.setDepartment(department);
            log.debug("Department updated to {} for accountId: {}", request.departmentId(), accountId);
        }

        // 권한 수정
        if (request.role() != null && !request.role().isBlank()) {
            try {
                AccountRole requestedRole = AccountRole.valueOf(request.role());

                // ADMIN 권한 부여는 ADMIN만 가능
                if (requestedRole == AccountRole.ADMIN) {
                    String currentRole = SecurityUtil.getCurrentRole();
                    if (!AccountRole.ADMIN.name().equals(currentRole)) {
                        log.warn("Non-ADMIN user attempted to assign ADMIN role. currentRole: {}, targetAccountId: {}", currentRole, accountId);
                        throw new BusinessException(ErrorCode.FORBIDDEN);
                    }
                }

                account.setRole(request.role());
                log.debug("Role updated to {} for accountId: {}", request.role(), accountId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role value: {}", request.role());
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        // 상태 수정
        if (request.status() != null && !request.status().isBlank()) {
            try {
                AccountStatus.valueOf(request.status());
                account.setStatus(request.status());
                log.debug("Status updated to {} for accountId: {}", request.status(), accountId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", request.status());
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        log.info("Account updated successfully by admin with accountId: {}", accountId);
        return toDetailResponseWithPositionName(account);
    }

    /**
     * 내 정보 조회
     */
    public AccountDto.DetailResponse getMyAccount() {
        String email = SecurityUtil.getTokenInfo().email();
        log.info("Getting my account info - email: {}", email);

        Account account = findAccountByEmail(email);
        return toDetailResponseWithPositionName(account);
    }

    /**
     * 내 정보 수정 (본인만 가능)
     */
    @Transactional
    public AccountDto.DetailResponse updateMyAccount(AccountDto.UpdateRequest request) {
        String email = SecurityUtil.getTokenInfo().email();
        log.info("Updating my account - email: {}", email);

        Account account = findAccountByEmail(email);

        // 이름 수정
        if (request.accountName() != null && !request.accountName().isBlank()) {
            account.setAccountName(request.accountName());
            log.debug("Account name updated to {} for email: {}", request.accountName(), email);
        }

        // 연락처 수정
        if (request.phone() != null) {
            account.setPhone(request.phone());
            log.debug("Phone updated for email: {}", email);
        }

        // 직급 수정
        if (request.position() != null) {
            validatePositionCode(request.position());
            account.setPosition(request.position());
            log.debug("Position updated to {} for email: {}", request.position(), email);
        }

        // 아바타 스타일 수정
        if (request.avatarStyle() != null) {
            account.setAvatarStyle(request.avatarStyle());
            log.debug("Avatar style updated to {} for email: {}", request.avatarStyle(), email);
        }

        // 아바타 시드 수정
        if (request.avatarSeed() != null) {
            account.setAvatarSeed(request.avatarSeed());
            log.debug("Avatar seed updated for email: {}", email);
        }

        log.info("My account updated successfully - email: {}", email);
        return toDetailResponseWithPositionName(account);
    }

    /**
     * positionName을 포함한 DetailResponse 생성
     */
    private AccountDto.DetailResponse toDetailResponseWithPositionName(Account account) {
        String positionName = getPositionName(account.getPosition());

        return new AccountDto.DetailResponse(
                account.getAccountId(),
                account.getEmail(),
                account.getAccountName(),
                account.getPhone(),
                account.getPosition(),
                positionName,
                account.getDepartment() != null ? account.getDepartment().getDepartmentId() : null,
                account.getDepartment() != null ? account.getDepartment().getDepartmentName() : null,
                account.getAvatarStyle(),
                account.getAvatarSeed(),
                account.getRole(),
                account.getStatus(),
                account.isMustChangePassword(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    /**
     * position 코드로 직급명 조회
     */
    private String getPositionName(String positionCode) {
        if (positionCode == null || positionCode.isBlank()) {
            return null;
        }
        return codeRepository.findByCodeTypeIdAndCodeId(POSITION_CODE_TYPE, positionCode)
                .map(code -> code.getCodeName())
                .orElse(null);
    }

    /**
     * position 코드 유효성 검증
     */
    private void validatePositionCode(String positionCode) {
        if (positionCode == null || positionCode.isBlank()) {
            return;
        }
        boolean exists = codeRepository.existsByCodeTypeIdAndCodeId(POSITION_CODE_TYPE, positionCode);
        if (!exists) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Account findAccountByAccountId(Long accountId) {
        return accountRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private Account findAccountByEmail(String email) {
        return accountRepository
                .findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private Department findDepartmentById(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    /**
     * 비밀번호 변경 (본인 자가 변경 / 강제 변경 게이트 공용)
     *
     * <p>현재 비밀번호를 검증한 뒤에만 변경한다. 새 비밀번호가 현재와 동일하면 거부한다.
     * 성공 시 강제 변경 플래그를 해제하고 변경 시각을 기록한다.
     *
     * @param request 현재 비밀번호 + 새 비밀번호
     */
    @Transactional
    public void changeMyPassword(AccountDto.ChangePasswordRequest request) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        log.info("Changing my password - accountId: {}", accountId);

        Account account = findAccountByAccountId(accountId);

        // 1. 현재 비밀번호 검증
        if (!passwordEncoder.matches(request.currentPassword(), account.getPassword())) {
            log.warn("Invalid current password on change - accountId: {}", accountId);
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        // 2. 새 비밀번호가 현재와 동일한지 검증
        if (passwordEncoder.matches(request.newPassword(), account.getPassword())) {
            log.warn("New password same as current - accountId: {}", accountId);
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_CURRENT);
        }

        // 3. 변경 (인코딩 + 강제 변경 플래그 해제 + 변경 시각 기록)
        account.changePassword(passwordEncoder.encode(request.newPassword()));

        log.info("Password changed successfully - accountId: {}", accountId);
    }

    /**
     * 비밀번호 초기화 (ADMIN/OPERATOR가 다른 계정을 임시 비밀번호로 덮어쓰기)
     *
     * <p>권한 매트릭스(PRD-001 §7)를 서버에서 강제한다.
     * <ul>
     *   <li>ADMIN: 본인 제외 전 계정 초기화 가능</li>
     *   <li>OPERATOR: ADMIN 대상 금지 + 본인 제외</li>
     *   <li>그 외 역할: 전부 금지(FORBIDDEN)</li>
     * </ul>
     * <p>처리: 임시 비밀번호 생성 → 해시 저장, 강제 변경 플래그 ON, 로그인 잠금 해제.
     * 평문 임시 비밀번호는 응답으로만 1회 반환하며 어디에도 저장하지 않는다.
     *
     * @param targetAccountId 초기화 대상 계정 ID
     * @return 평문 임시 비밀번호 (1회성)
     */
    @Transactional
    public AccountDto.ResetPasswordResponse resetPassword(Long targetAccountId) {
        Long callerId = SecurityUtil.getCurrentAccountId();
        String callerRole = SecurityUtil.getCurrentRole();
        log.info("Reset password requested - callerId: {}, callerRole: {}, targetId: {}",
                callerId, callerRole, targetAccountId);

        // 1. 본인 초기화 금지 (변경 사용)
        if (callerId.equals(targetAccountId)) {
            log.warn("Self reset rejected - accountId: {}", callerId);
            throw new BusinessException(ErrorCode.CANNOT_RESET_SELF);
        }

        // 2. 호출자 권한 검증 (ADMIN / OPERATOR 만 허용)
        if (!AccountRole.ADMIN.getCodeId().equals(callerRole)
                && !AccountRole.OPERATOR.getCodeId().equals(callerRole)) {
            log.warn("Forbidden reset attempt - callerRole: {}, targetId: {}", callerRole, targetAccountId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 3. 대상 계정 조회
        Account target = findAccountByAccountId(targetAccountId);

        // 4. OPERATOR 는 ADMIN 대상 초기화 금지 (권한 상승 방지)
        if (AccountRole.OPERATOR.getCodeId().equals(callerRole)
                && AccountRole.ADMIN.getCodeId().equals(target.getRole())) {
            log.warn("OPERATOR attempted to reset ADMIN - callerId: {}, targetId: {}", callerId, targetAccountId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 5. 임시 비밀번호 생성 → 해시 저장 (강제 변경 플래그 ON, 잠금 해제)
        String temporaryPassword = PasswordGenerator.generate();
        target.resetPassword(passwordEncoder.encode(temporaryPassword));

        // 평문은 로그에 남기지 않는다 (대상 ID만 기록)
        log.info("Password reset completed - targetId: {}", targetAccountId);

        return new AccountDto.ResetPasswordResponse(temporaryPassword);
    }

    /**
     * 비밀번호 재설정 안내용 활성 관리자/운영자 연락처 목록 조회 (비인증 공개 API)
     *
     * <p>role IN ('ADMIN','OPERATOR') AND status = 'ACTIVE' 계정을 조회하며,
     * ADMIN 우선 → 이름 오름차순 정렬. 민감 필드는 응답에서 제외한다.
     *
     * @return 관리자/운영자 연락처 목록
     */
    public List<AccountDto.AdminContactResponse> getAdminContacts() {
        log.info("관리자 연락처 목록 조회");

        List<Account> accounts = accountRepository.findActiveAdminContacts();

        List<AccountDto.AdminContactResponse> result = accounts.stream()
                .map(account -> new AccountDto.AdminContactResponse(
                        account.getAccountId(),
                        account.getDepartment() != null
                                ? account.getDepartment().getDepartmentName()
                                : "부서 없음",
                        account.getAccountName(),
                        account.getEmail(),
                        account.getRole()
                ))
                .toList();

        log.info("관리자 연락처 조회 완료 - count: {}", result.size());
        return result;
    }

    /**
     * 계정 일괄 부서 이동 (ADMIN 전용)
     *
     * @param request 일괄 부서 이동 요청 (계정 ID 목록, 대상 부서 ID)
     * @return 이동 결과
     */
    @Transactional
    public AccountDto.BatchTransferDepartmentResponse batchTransferDepartment(
            AccountDto.BatchTransferDepartmentRequest request) {
        log.info("계정 일괄 부서 이동 요청 - accountIds: {}, targetDepartmentId: {}",
                request.accountIds(), request.targetDepartmentId());

        // 1. 대상 부서 조회 (null이면 배치 해제)
        Department targetDepartment = null;
        String targetDepartmentName = "미배치";

        if (request.targetDepartmentId() != null) {
            targetDepartment = findDepartmentById(request.targetDepartmentId());
            targetDepartmentName = targetDepartment.getDepartmentName();
        }

        // 2. 계정 일괄 조회 및 검증
        List<Account> accounts = accountRepository.findAllById(request.accountIds());

        if (accounts.size() != request.accountIds().size()) {
            log.warn("일부 계정을 찾을 수 없음 - 요청: {}, 조회: {}",
                    request.accountIds().size(), accounts.size());
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        // 3. 일괄 부서 변경
        for (Account account : accounts) {
            account.setDepartment(targetDepartment);
        }

        // 4. 응답 반환
        String message = String.format("%d개 계정이 %s(으)로 이동되었습니다.",
                accounts.size(), targetDepartmentName);

        log.info("계정 일괄 부서 이동 완료 - {}", message);

        return new AccountDto.BatchTransferDepartmentResponse(
                accounts.size(),
                request.targetDepartmentId(),
                targetDepartmentName,
                message
        );
    }
}
