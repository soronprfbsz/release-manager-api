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
import org.mockito.Mock;
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
    private AccountDto.DetailResponse detailResponse;

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

        detailResponse = new AccountDto.DetailResponse(
                1L,
                "test@example.com",
                "테스트계정",
                null, null, null, null, null,
                null, null,
                "USER",
                "ACTIVE",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("계정 생성 - 성공")
    void createAccount_Success() {
        // given
        given(accountRepository.existsByEmail(anyString())).willReturn(false);
        given(mapper.toEntity(any(AccountDto.CreateRequest.class))).willReturn(testAccount);
        given(accountRepository.save(any(Account.class))).willReturn(testAccount);
        given(mapper.toDetailResponse(any(Account.class))).willReturn(detailResponse);

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
        given(mapper.toDetailResponse(any(Account.class))).willReturn(detailResponse);

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

        given(accountRepository.findAll(any(Pageable.class))).willReturn(accountPage);

        // when
        Page<AccountDto.ListResponse> result = accountService.getAccounts(null, null, null, null, false, null, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        then(accountRepository).should(times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("상태별 계정 조회 - 성공")
    void getAccountsByStatus_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        List<Account> accounts = List.of(testAccount);
        Page<Account> accountPage = new PageImpl<>(accounts, pageable, 1);

        given(accountRepository.findAllByStatus(anyString(), any(Pageable.class))).willReturn(accountPage);

        // when
        Page<AccountDto.ListResponse> result = accountService.getAccounts(
                AccountStatus.ACTIVE, null, null, null, false, null, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        then(accountRepository).should(times(1)).findAllByStatus("ACTIVE", pageable);
    }

    @Test
    @DisplayName("계정 수정 - 성공")
    void updateAccount_Success() {
        // given
        AccountDto.UpdateRequest updateRequest = AccountDto.UpdateRequest.builder()
                .accountName("새이름")
                .password("newPassword")
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
        given(mapper.toDetailResponse(any(Account.class))).willReturn(detailResponse);

        // when
        AccountDto.DetailResponse result = accountService.updateAccount(1L, updateRequest);

        // then
        assertThat(result).isNotNull();
        // JPA Dirty Checking 사용 - 엔티티 조회만 검증
        then(accountRepository).should(times(1)).findByAccountId(1L);
        then(mapper).should(times(1)).toDetailResponse(any(Account.class));
    }

    @Test
    @DisplayName("계정 삭제 - 성공")
    void deleteAccount_Success() {
        // given - JpaRepository의 deleteById 사용

        // when
        accountService.deleteAccount(1L);

        // then
        then(accountRepository).should(times(1)).deleteById(1L);
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

        given(accountRepository.findByAccountNameContaining(anyString(), any(Pageable.class))).willReturn(accountPage);

        // when
        Page<AccountDto.ListResponse> result = accountService.getAccounts(null, null, null, null, false, "테스트", pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        then(accountRepository).should(times(1)).findByAccountNameContaining("테스트", pageable);
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

        AccountDto.DetailResponse expectedResponse = new AccountDto.DetailResponse(
                1L, "test@example.com", "테스트계정", null, null, null, null, null, null, null, "USER", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));
        given(mapper.toDetailResponse(any(Account.class))).willReturn(expectedResponse);

        // when
        AccountDto.DetailResponse result = accountService.adminUpdateAccount(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(1L);
        then(accountRepository).should(times(1)).findByAccountId(1L);
    }

    @Test
    @DisplayName("관리자 계정 수정 - role만 수정")
    void adminUpdateAccount_UpdateRoleOnly_Success() {
        // given
        AccountDto.AdminUpdateRequest request = AccountDto.AdminUpdateRequest.builder()
                .role("ADMIN")
                .build();

        AccountDto.DetailResponse expectedResponse = new AccountDto.DetailResponse(
                1L, "test@example.com", "테스트계정", null, null, null, null, null, null, null, "ADMIN", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));
        given(mapper.toDetailResponse(any(Account.class))).willReturn(expectedResponse);

        // when
        AccountDto.DetailResponse result = accountService.adminUpdateAccount(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.role()).isEqualTo("ADMIN");
        then(accountRepository).should(times(1)).findByAccountId(1L);
    }

    @Test
    @DisplayName("관리자 계정 수정 - status만 수정")
    void adminUpdateAccount_UpdateStatusOnly_Success() {
        // given
        AccountDto.AdminUpdateRequest request = AccountDto.AdminUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        AccountDto.DetailResponse expectedResponse = new AccountDto.DetailResponse(
                1L, "test@example.com", "테스트계정", null, null, null, null, null, null, null, "USER", "INACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));
        given(mapper.toDetailResponse(any(Account.class))).willReturn(expectedResponse);

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

        AccountDto.DetailResponse expectedResponse = new AccountDto.DetailResponse(
                1L, "test@example.com", "테스트계정", null, null, null, null, null, null, null, "ADMIN", "INACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );

        given(accountRepository.findByAccountId(anyLong())).willReturn(Optional.of(testAccount));
        given(mapper.toDetailResponse(any(Account.class))).willReturn(expectedResponse);

        // when
        AccountDto.DetailResponse result = accountService.adminUpdateAccount(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(result.status()).isEqualTo("INACTIVE");
        then(accountRepository).should(times(1)).findByAccountId(1L);
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
}
