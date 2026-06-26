package com.ts.rm.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ts.rm.domain.account.dto.AccountDto;
import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.mapper.AccountDtoMapper;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.common.repository.CodeRepository;
import com.ts.rm.domain.department.repository.DepartmentHierarchyRepository;
import com.ts.rm.domain.department.repository.DepartmentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Account Service 단위 테스트
 *
 * <p>[@ExtendWith(MockitoExtension.class)] - Mockito를 사용한 단위 테스트 - Repository와 Mapper를
 * Mock으로 대체 - 비즈니스 로직만 집중 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 테스트")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountDtoMapper mapper;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DepartmentHierarchyRepository departmentHierarchyRepository;

    @Mock
    private CodeRepository codeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;
    private AccountDto.CreateRequest createRequest;

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .accountId(1L)
                .email("test@example.com")
                .password("password123")
                .accountName("테스트계정")
                .role("USER")
                .status("ACTIVE")
                .build();

        createRequest = AccountDto.CreateRequest.builder()
                .email("test@example.com")
                .password("password123")
                .accountName("테스트계정")
                .role("USER")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("계정 생성 - 성공")
    void createAccount_Success() {
        // given
        given(accountRepository.existsByEmail(anyString())).willReturn(false);
        given(mapper.toEntity(any(AccountDto.CreateRequest.class))).willReturn(testAccount);
        given(accountRepository.save(any(Account.class))).willReturn(testAccount);

        // when
        AccountDto.DetailResponse result = accountService.createAccount(createRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("test@example.com");

        then(accountRepository).should(times(1)).existsByEmail(anyString());
        then(accountRepository).should(times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("계정 생성 - 이메일 중복 실패")
    void createAccount_DuplicateEmail() {
        // given
        given(accountRepository.existsByEmail(anyString())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> accountService.createAccount(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_EMAIL_CONFLICT);

        then(accountRepository).should(times(1)).existsByEmail(anyString());
        then(accountRepository).should(never()).save(any(Account.class));
    }

    @Test
    @DisplayName("계정 조회 - 성공")
    void getAccountByAccountId_Success() {
        // given
        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        // when
        AccountDto.DetailResponse result = accountService.getAccountByAccountId(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(1L);

        then(accountRepository).should(times(1)).findByAccountId(1L);
    }

    @Test
    @DisplayName("계정 조회 - 존재하지 않음")
    void getAccountByAccountId_NotFound() {
        // given
        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> accountService.getAccountByAccountId(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_FOUND);

        then(accountRepository).should(times(1)).findByAccountId(999L);
    }

    @Test
    @DisplayName("전체 계정 조회 - 성공")
    void getAllAccounts_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        List<Account> accounts = List.of(testAccount);
        Page<Account> accountPage = new PageImpl<>(accounts, pageable, 1);

        given(accountRepository.findAllWithFilters(
                isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), any(Pageable.class)))
                .willReturn(accountPage);

        // when
        Page<AccountDto.ListResponse> result = accountService.getAccounts(null, null, null, null, false, null, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        then(accountRepository).should(times(1))
                .findAllWithFilters(isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(pageable));
    }

    @Test
    @DisplayName("상태별 계정 조회 - 성공")
    void getAccountsByStatus_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        List<Account> accounts = List.of(testAccount);
        Page<Account> accountPage = new PageImpl<>(accounts, pageable, 1);

        given(accountRepository.findAllWithFilters(
                eq("ACTIVE"), isNull(), isNull(), isNull(), eq(false), isNull(), any(Pageable.class)))
                .willReturn(accountPage);

        // when
        Page<AccountDto.ListResponse> result = accountService.getAccounts(
                AccountStatus.ACTIVE, null, null, null, false, null, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        then(accountRepository).should(times(1))
                .findAllWithFilters(eq("ACTIVE"), isNull(), isNull(), isNull(), eq(false), isNull(), eq(pageable));
    }

    @Test
    @DisplayName("계정 수정 - 성공")
    void updateAccount_Success() {
        // given
        AccountDto.UpdateRequest updateRequest = AccountDto.UpdateRequest.builder()
                .accountName("새이름")
                .build();

        Account updatedAccount = Account.builder()
                .accountId(1L)
                .email("test@example.com")
                .password("newPassword")
                .accountName("새이름")
                .role("USER")
                .status("ACTIVE")
                .build();

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        // when
        AccountDto.DetailResponse result = accountService.updateAccount(1L, updateRequest);

        // then - 서비스가 직접 DetailResponse 를 조립(toDetailResponseWithPositionName)
        assertThat(result).isNotNull();
        assertThat(result.accountName()).isEqualTo("새이름");
        // JPA Dirty Checking 사용 - 엔티티 조회만 검증
        then(accountRepository).should(times(1)).findByAccountId(1L);
    }

    @Test
    @DisplayName("계정 삭제 - 성공")
    void deleteAccount_Success() {
        // given - USER 계정 조회 후 delete (ADMIN 최소 1명 보호 로직 미적용 경로)
        given(accountRepository.findByAccountId(1L)).willReturn(Optional.of(testAccount));

        // when
        accountService.deleteAccount(1L);

        // then
        then(accountRepository).should(times(1)).findByAccountId(1L);
        then(accountRepository).should(times(1)).delete(testAccount);
    }

    @Test
    @DisplayName("계정 활성화 - 성공")
    void activateAccount_Success() {
        // given
        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        // when
        accountService.updateAccountStatus(1L, AccountStatus.ACTIVE);

        // then
        // JPA Dirty Checking 사용 - 엔티티 조회만 검증
        then(accountRepository).should(times(1)).findByAccountId(1L);
    }

    @Test
    @DisplayName("계정 비활성화 - 성공")
    void deactivateAccount_Success() {
        // given
        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        // when
        accountService.updateAccountStatus(1L, AccountStatus.INACTIVE);

        // then
        // JPA Dirty Checking 사용 - 엔티티 조회만 검증
        then(accountRepository).should(times(1)).findByAccountId(1L);
    }

    @Test
    @DisplayName("이름으로 계정 검색 - 성공")
    void searchAccountsByName_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        List<Account> accounts = List.of(testAccount);
        Page<Account> accountPage = new PageImpl<>(accounts, pageable, 1);

        given(accountRepository.findAllWithFilters(
                isNull(), isNull(), isNull(), isNull(), eq(false), eq("테스트"), any(Pageable.class)))
                .willReturn(accountPage);

        // when
        Page<AccountDto.ListResponse> result = accountService.getAccounts(null, null, null, null, false, "테스트", pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        then(accountRepository).should(times(1))
                .findAllWithFilters(isNull(), isNull(), isNull(), isNull(), eq(false), eq("테스트"), eq(pageable));
    }

    // ========================================
    // adminUpdateAccount 테스트
    // ========================================

    @Test
    @DisplayName("관리자 계정 수정 - 이름만 수정")
    void adminUpdateAccount_UpdateNameOnly_Success() {
        // given
        AccountDto.AdminUpdateRequest request = AccountDto.AdminUpdateRequest.builder()
                .accountName("새로운이름")
                .build();

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        // when
        AccountDto.DetailResponse result = accountService.adminUpdateAccount(1L, request);

        // then - 서비스가 직접 DetailResponse 조립(dirty checking 반영)
        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(1L);
        assertThat(result.accountName()).isEqualTo("새로운이름");
        then(accountRepository).should(times(1)).findByAccountId(1L);
    }

    @Test
    @DisplayName("관리자 계정 수정 - role만 수정 (ADMIN 부여는 ADMIN 호출자만)")
    void adminUpdateAccount_UpdateRoleOnly_Success() {
        // given
        AccountDto.AdminUpdateRequest request = AccountDto.AdminUpdateRequest.builder()
                .role("ADMIN")
                .build();

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            // ADMIN 권한 부여는 ADMIN 호출자만 가능
            securityUtil.when(SecurityUtil::getCurrentRole).thenReturn("ADMIN");

            // when
            AccountDto.DetailResponse result = accountService.adminUpdateAccount(1L, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.role()).isEqualTo("ADMIN");
            then(accountRepository).should(times(1)).findByAccountId(1L);
        }
    }

    @Test
    @DisplayName("관리자 계정 수정 - status만 수정")
    void adminUpdateAccount_UpdateStatusOnly_Success() {
        // given
        AccountDto.AdminUpdateRequest request = AccountDto.AdminUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        // when
        AccountDto.DetailResponse result = accountService.adminUpdateAccount(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("INACTIVE");
        then(accountRepository).should(times(1)).findByAccountId(1L);
    }

    @Test
    @DisplayName("관리자 계정 수정 - 모든 필드 수정")
    void adminUpdateAccount_UpdateAllFields_Success() {
        // given
        AccountDto.AdminUpdateRequest request = AccountDto.AdminUpdateRequest.builder()
                .accountName("새로운이름")
                .role("ADMIN")
                .status("INACTIVE")
                .build();

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            // ADMIN 권한 부여는 ADMIN 호출자만 가능
            securityUtil.when(SecurityUtil::getCurrentRole).thenReturn("ADMIN");

            // when
            AccountDto.DetailResponse result = accountService.adminUpdateAccount(1L, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.role()).isEqualTo("ADMIN");
            assertThat(result.status()).isEqualTo("INACTIVE");
            then(accountRepository).should(times(1)).findByAccountId(1L);
        }
    }

    @Test
    @DisplayName("관리자 계정 수정 - 잘못된 role 값으로 실패")
    void adminUpdateAccount_InvalidRole_Fail() {
        // given
        AccountDto.AdminUpdateRequest request = AccountDto.AdminUpdateRequest.builder()
                .role("INVALID_ROLE")
                .build();

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> accountService.adminUpdateAccount(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("관리자 계정 수정 - 잘못된 status 값으로 실패")
    void adminUpdateAccount_InvalidStatus_Fail() {
        // given
        AccountDto.AdminUpdateRequest request = AccountDto.AdminUpdateRequest.builder()
                .status("INVALID_STATUS")
                .build();

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> accountService.adminUpdateAccount(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("관리자 계정 수정 - 존재하지 않는 계정 ID로 실패")
    void adminUpdateAccount_AccountNotFound_Fail() {
        // given
        AccountDto.AdminUpdateRequest request = AccountDto.AdminUpdateRequest.builder()
                .accountName("새로운이름")
                .build();

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> accountService.adminUpdateAccount(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_FOUND);
    }

    // ========================================
    // resetPassword 테스트 (PRD-001 §5.2, §7)
    //
    // SecurityUtil 의 정적 메서드(getCurrentAccountId/getCurrentRole)는
    // Mockito.mockStatic 으로 호출 단위에서만 스텁한다.
    // ========================================

    private Account buildAccount(Long accountId, String role) {
        return Account.builder()
                .accountId(accountId)
                .email(accountId + "@example.com")
                .password("encodedOriginalPassword")
                .accountName("계정" + accountId)
                .role(role)
                .status("ACTIVE")
                .loginAttemptCount(5)
                .lockedUntil(LocalDateTime.now().plusMinutes(30))
                .mustChangePassword(false)
                .build();
    }

    @Test
    @DisplayName("비밀번호 초기화 - ADMIN이 USER 초기화 성공 (부수효과 검증)")
    void resetPassword_AdminResetsUser_Success() {
        // given
        Long callerId = 1L;
        Long targetId = 2L;
        Account target = buildAccount(targetId, "USER");

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(callerId);
            securityUtil.when(SecurityUtil::getCurrentRole).thenReturn("ADMIN");

            given(accountRepository.findByAccountId(targetId)).willReturn(Optional.of(target));
            given(passwordEncoder.encode(anyString())).willReturn("encodedTemporaryPassword");

            // when
            AccountDto.ResetPasswordResponse result = accountService.resetPassword(targetId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.temporaryPassword()).isNotBlank();

            // 부수효과(§5.2): 임시비번 encode 저장, 강제변경 ON, 잠금 해제
            assertThat(target.getPassword()).isEqualTo("encodedTemporaryPassword");
            assertThat(target.isMustChangePassword()).isTrue();
            assertThat(target.getLoginAttemptCount()).isZero();
            assertThat(target.getLockedUntil()).isNull();
            assertThat(target.getLastPasswordChangedAt()).isNotNull();

            then(passwordEncoder).should(times(1)).encode(result.temporaryPassword());
        }
    }

    @Test
    @DisplayName("비밀번호 초기화 - OPERATOR가 USER 초기화 성공")
    void resetPassword_OperatorResetsUser_Success() {
        // given
        Long callerId = 1L;
        Long targetId = 2L;
        Account target = buildAccount(targetId, "USER");

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(callerId);
            securityUtil.when(SecurityUtil::getCurrentRole).thenReturn("OPERATOR");

            given(accountRepository.findByAccountId(targetId)).willReturn(Optional.of(target));
            given(passwordEncoder.encode(anyString())).willReturn("encodedTemporaryPassword");

            // when
            AccountDto.ResetPasswordResponse result = accountService.resetPassword(targetId);

            // then
            assertThat(result.temporaryPassword()).isNotBlank();
            assertThat(target.isMustChangePassword()).isTrue();
        }
    }

    @Test
    @DisplayName("비밀번호 초기화 - OPERATOR가 ADMIN 초기화 시 FORBIDDEN")
    void resetPassword_OperatorResetsAdmin_Forbidden() {
        // given
        Long callerId = 1L;
        Long targetId = 2L;
        Account target = buildAccount(targetId, "ADMIN");

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(callerId);
            securityUtil.when(SecurityUtil::getCurrentRole).thenReturn("OPERATOR");

            given(accountRepository.findByAccountId(targetId)).willReturn(Optional.of(target));

            // when & then
            assertThatThrownBy(() -> accountService.resetPassword(targetId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

            then(passwordEncoder).should(never()).encode(anyString());
        }
    }

    @Test
    @DisplayName("비밀번호 초기화 - 권한 없는 역할(USER)은 FORBIDDEN")
    void resetPassword_UserRole_Forbidden() {
        // given
        Long callerId = 1L;
        Long targetId = 2L;

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(callerId);
            securityUtil.when(SecurityUtil::getCurrentRole).thenReturn("USER");

            // when & then
            assertThatThrownBy(() -> accountService.resetPassword(targetId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

            then(accountRepository).should(never()).findByAccountId(anyLong());
        }
    }

    @Test
    @DisplayName("비밀번호 초기화 - 권한 없는 역할(DEVELOPER)은 FORBIDDEN")
    void resetPassword_DeveloperRole_Forbidden() {
        // given
        Long callerId = 1L;
        Long targetId = 2L;

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(callerId);
            securityUtil.when(SecurityUtil::getCurrentRole).thenReturn("DEVELOPER");

            // when & then
            assertThatThrownBy(() -> accountService.resetPassword(targetId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        }
    }

    @Test
    @DisplayName("비밀번호 초기화 - 본인 계정 초기화 시 CANNOT_RESET_SELF")
    void resetPassword_Self_CannotResetSelf() {
        // given
        Long accountId = 1L;

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(accountId);
            securityUtil.when(SecurityUtil::getCurrentRole).thenReturn("ADMIN");

            // when & then
            assertThatThrownBy(() -> accountService.resetPassword(accountId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_RESET_SELF);

            then(accountRepository).should(never()).findByAccountId(anyLong());
        }
    }

    @Test
    @DisplayName("비밀번호 초기화 - 대상 계정 없음 ACCOUNT_NOT_FOUND")
    void resetPassword_TargetNotFound() {
        // given
        Long callerId = 1L;
        Long targetId = 999L;

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(callerId);
            securityUtil.when(SecurityUtil::getCurrentRole).thenReturn("ADMIN");

            given(accountRepository.findByAccountId(targetId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> accountService.resetPassword(targetId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    // ========================================
    // changeMyPassword 테스트 (PRD-001 §5.1, §6.1)
    // ========================================

    @Test
    @DisplayName("비밀번호 변경 - 현재 비밀번호 불일치 INVALID_CURRENT_PASSWORD")
    void changeMyPassword_InvalidCurrentPassword() {
        // given
        Long accountId = 1L;
        Account account = buildAccount(accountId, "USER");
        AccountDto.ChangePasswordRequest request = AccountDto.ChangePasswordRequest.builder()
                .currentPassword("wrongPassword")
                .newPassword("newPassword123")
                .build();

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(accountId);

            given(accountRepository.findByAccountId(accountId)).willReturn(Optional.of(account));
            given(passwordEncoder.matches("wrongPassword", account.getPassword())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> accountService.changeMyPassword(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CURRENT_PASSWORD);

            then(passwordEncoder).should(never()).encode(anyString());
        }
    }

    @Test
    @DisplayName("비밀번호 변경 - 새 비밀번호가 현재와 동일 PASSWORD_SAME_AS_CURRENT")
    void changeMyPassword_SameAsCurrent() {
        // given
        Long accountId = 1L;
        Account account = buildAccount(accountId, "USER");
        AccountDto.ChangePasswordRequest request = AccountDto.ChangePasswordRequest.builder()
                .currentPassword("currentPassword")
                .newPassword("currentPassword")
                .build();

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(accountId);

            given(accountRepository.findByAccountId(accountId)).willReturn(Optional.of(account));
            given(passwordEncoder.matches("currentPassword", account.getPassword())).willReturn(true);

            // when & then
            assertThatThrownBy(() -> accountService.changeMyPassword(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_SAME_AS_CURRENT);

            then(passwordEncoder).should(never()).encode(anyString());
        }
    }

    @Test
    @DisplayName("비밀번호 변경 - 정상 변경 (encode, 강제변경 해제, 변경시각 기록)")
    void changeMyPassword_Success() {
        // given
        Long accountId = 1L;
        Account account = buildAccount(accountId, "USER");
        account.setMustChangePassword(true); // 강제 변경 게이트 시나리오
        AccountDto.ChangePasswordRequest request = AccountDto.ChangePasswordRequest.builder()
                .currentPassword("currentPassword")
                .newPassword("newPassword123")
                .build();

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentAccountId).thenReturn(accountId);

            given(accountRepository.findByAccountId(accountId)).willReturn(Optional.of(account));
            given(passwordEncoder.matches("currentPassword", "encodedOriginalPassword")).willReturn(true);
            given(passwordEncoder.matches("newPassword123", "encodedOriginalPassword")).willReturn(false);
            given(passwordEncoder.encode("newPassword123")).willReturn("encodedNewPassword");

            // when
            accountService.changeMyPassword(request);

            // then
            assertThat(account.getPassword()).isEqualTo("encodedNewPassword");
            assertThat(account.isMustChangePassword()).isFalse();
            assertThat(account.getLastPasswordChangedAt()).isNotNull();

            then(passwordEncoder).should(times(1)).encode("newPassword123");
        }
    }
}
