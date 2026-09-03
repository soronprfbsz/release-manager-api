package com.ts.rm.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountRole;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.repository.MessageRepository;
import com.ts.rm.global.exception.BusinessException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountRequestService 단위 테스트 - 트랜잭션 경계")
class AccountRequestServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageNotificationPublisher notificationPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private AccountRequestService accountRequestService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                accountRequestService, "systemSenderEmail", "system@test.com");
        ReflectionTestUtils.setField(accountRequestService, "cooldownMinutes", 10);
    }

    @Test
    @DisplayName("동시 요청으로 UNIQUE 제약이 깨져도 호출자에게 예외가 전파되지 않는다 (제약명 기반 판정)")
    void requestPasswordReset_uniqueViolation_isAbsorbed() {
        Account sender = account(1L, "system@test.com", "시스템",
                AccountRole.ADMIN.getCodeId());
        Account admin = account(2L, "admin@test.com", "관리자",
                AccountRole.ADMIN.getCodeId());
        Account requester = account(3L, "user@test.com", "요청자",
                AccountRole.USER.getCodeId());

        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(sender));
        given(accountRepository.findAllById(any())).willReturn(List.of(admin));
        given(accountRepository.findByEmail("user@test.com")).willReturn(Optional.of(requester));
        // 저장 전 사전 체크(쿨다운)만 호출된다 — 제약명으로 판정되면 재조회는 일어나지 않는다
        given(messageRepository.existsByDedupKey(any())).willReturn(false);

        // TransactionTemplate 이 콜백을 실행하다 제약 위반을 만나는 상황을 재현한다
        given(transactionTemplate.execute(any()))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    callback.doInTransaction(new SimpleTransactionStatus());
                    return null;
                });
        // 대소문자가 달라도(UK_MSG_DEDUP_KEY vs uk_msg_dedup_key) 판정되는지 함께 확인한다
        ConstraintViolationException dedupKeyViolation = new ConstraintViolationException(
                "could not execute statement",
                new SQLException("Duplicate entry for key 'UK_MSG_DEDUP_KEY'"),
                "UK_MSG_DEDUP_KEY");
        given(messageRepository.saveAndFlush(any(Message.class)))
                .willThrow(new DataIntegrityViolationException("could not execute statement",
                        dedupKeyViolation));

        assertThatCode(() -> accountRequestService.requestPasswordReset(
                "user@test.com", null, List.of(2L)))
                .doesNotThrowAnyException();

        // 재조회 없이 제약명만으로 판정했다면 existsByDedupKey 는 사전 체크 1번만 호출된다
        verify(messageRepository, times(1)).existsByDedupKey(any());
    }

    @Test
    @DisplayName("MySQL 이 제약명을 '테이블.인덱스' 로 수식해도 흡수된다 (마지막 '.' 뒤 세그먼트 비교)")
    void requestPasswordReset_qualifiedConstraintName_isAbsorbed() {
        Account sender = account(1L, "system@test.com", "시스템",
                AccountRole.ADMIN.getCodeId());
        Account admin = account(2L, "admin@test.com", "관리자",
                AccountRole.ADMIN.getCodeId());
        Account requester = account(3L, "user@test.com", "요청자",
                AccountRole.USER.getCodeId());

        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(sender));
        given(accountRepository.findAllById(any())).willReturn(List.of(admin));
        given(accountRepository.findByEmail("user@test.com")).willReturn(Optional.of(requester));
        // 제약명만으로 판정되면 재조회는 사전 체크 1번만 일어난다
        given(messageRepository.existsByDedupKey(any())).willReturn(false);

        given(transactionTemplate.execute(any()))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    callback.doInTransaction(new SimpleTransactionStatus());
                    return null;
                });
        // MySQL 8.0.19+ 는 제약명을 "테이블.인덱스" 로 수식해 돌려준다
        ConstraintViolationException qualifiedViolation = new ConstraintViolationException(
                "could not execute statement",
                new SQLException("Duplicate entry for key 'message.uk_msg_dedup_key'"),
                "message.uk_msg_dedup_key");
        given(messageRepository.saveAndFlush(any(Message.class)))
                .willThrow(new DataIntegrityViolationException("could not execute statement",
                        qualifiedViolation));

        assertThatCode(() -> accountRequestService.requestPasswordReset(
                "user@test.com", null, List.of(2L)))
                .doesNotThrowAnyException();

        // 수식된 이름도 세그먼트 비교로 판정됐다면 폴백(재조회) 없이 사전 체크 1번만 호출된다
        verify(messageRepository, times(1)).existsByDedupKey(any());
    }

    @Test
    @DisplayName("dedupKey 와 무관한 제약 위반은 이름이 달라 흡수되지 않고 그대로 전파한다 (제약명 기반 판정)")
    void requestPasswordReset_differentConstraintViolation_isRethrown() {
        Account sender = account(1L, "system@test.com", "시스템",
                AccountRole.ADMIN.getCodeId());
        Account admin = account(2L, "admin@test.com", "관리자",
                AccountRole.ADMIN.getCodeId());
        Account requester = account(3L, "user@test.com", "요청자",
                AccountRole.USER.getCodeId());

        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(sender));
        given(accountRepository.findAllById(any())).willReturn(List.of(admin));
        given(accountRepository.findByEmail("user@test.com")).willReturn(Optional.of(requester));
        given(messageRepository.existsByDedupKey(any())).willReturn(false);

        given(transactionTemplate.execute(any()))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    callback.doInTransaction(new SimpleTransactionStatus());
                    return null;
                });
        ConstraintViolationException otherViolation = new ConstraintViolationException(
                "could not execute statement",
                new SQLException("Cannot add or update a child row"),
                "fk_message_sender_account_id");
        given(messageRepository.saveAndFlush(any(Message.class)))
                .willThrow(new DataIntegrityViolationException("could not execute statement",
                        otherViolation));

        assertThatThrownBy(() -> accountRequestService.requestPasswordReset(
                "user@test.com", null, List.of(2L)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 제약명이 dedupKey 것과 다르므로 재조회(폴백) 없이 바로 거부됐다
        verify(messageRepository, times(1)).existsByDedupKey(any());
    }

    @Test
    @DisplayName("dedupKey 충돌이 아닌 무결성 위반은 흡수하지 않고 그대로 전파한다")
    void requestPasswordReset_nonDedupIntegrityViolation_isRethrown() {
        Account sender = account(1L, "system@test.com", "시스템",
                AccountRole.ADMIN.getCodeId());
        Account admin = account(2L, "admin@test.com", "관리자",
                AccountRole.ADMIN.getCodeId());
        Account requester = account(3L, "user@test.com", "요청자",
                AccountRole.USER.getCodeId());

        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(sender));
        given(accountRepository.findAllById(any())).willReturn(List.of(admin));
        given(accountRepository.findByEmail("user@test.com")).willReturn(Optional.of(requester));
        // 저장 실패 후 재확인해도 dedupKey 충돌이 아니다(FK 위반 등 다른 원인) — 흡수 대상이
        // 아니므로 계속 false
        given(messageRepository.existsByDedupKey(any())).willReturn(false);

        given(transactionTemplate.execute(any()))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    callback.doInTransaction(new SimpleTransactionStatus());
                    return null;
                });
        given(messageRepository.saveAndFlush(any(Message.class)))
                .willThrow(new DataIntegrityViolationException("foreign key constraint fails"));

        assertThatThrownBy(() -> accountRequestService.requestPasswordReset(
                "user@test.com", null, List.of(2L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("제약명을 뽑을 수 없어도 재조회(폴백)가 행을 찾으면 흡수한다 (폴백 존재 이유)")
    void requestPasswordReset_fallbackFindsExistingRow_isAbsorbed() {
        Account sender = account(1L, "system@test.com", "시스템",
                AccountRole.ADMIN.getCodeId());
        Account admin = account(2L, "admin@test.com", "관리자",
                AccountRole.ADMIN.getCodeId());
        Account requester = account(3L, "user@test.com", "요청자",
                AccountRole.USER.getCodeId());

        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(sender));
        given(accountRepository.findAllById(any())).willReturn(List.of(admin));
        given(accountRepository.findByEmail("user@test.com")).willReturn(Optional.of(requester));
        // 1번째 호출(저장 전 사전 체크)은 false 여야 저장까지 진행된다.
        // 2번째 호출(제약명을 못 뽑아 도는 폴백 재조회)은 true — 이게 흡수 방향이다.
        given(messageRepository.existsByDedupKey(any())).willReturn(false, true);

        given(transactionTemplate.execute(any()))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    callback.doInTransaction(new SimpleTransactionStatus());
                    return null;
                });
        // 원인 체인에 ConstraintViolationException 이 없어 제약명을 뽑을 수 없는 상황
        given(messageRepository.saveAndFlush(any(Message.class)))
                .willThrow(new DataIntegrityViolationException("could not execute statement"));

        assertThatCode(() -> accountRequestService.requestPasswordReset(
                "user@test.com", null, List.of(2L)))
                .doesNotThrowAnyException();

        // 두 번의 existsByDedupKey 호출(사전 체크 / 폴백 재조회)이 같은 dedupKey 를 썼는지 고정
        ArgumentCaptor<String> dedupKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageRepository, times(2)).existsByDedupKey(dedupKeyCaptor.capture());
        List<String> calls = dedupKeyCaptor.getAllValues();
        assertThat(calls.get(0)).isEqualTo(calls.get(1));
    }

    @Test
    @DisplayName("담당자 재검증 - 다른 담당자가 있으면 시스템 계정 ID 를 직접 보내도 거부한다")
    void findEligibleRecipients_excludesSystemSenderWhenRealAdminExists() {
        // given - 목록 API 에서 빠져 있어도 ID 는 직접 보낼 수 있다
        Account sender = account(1L, "system@test.com", "시스템",
                AccountRole.ADMIN.getCodeId());
        Account admin = account(2L, "admin@test.com", "관리자",
                AccountRole.ADMIN.getCodeId());

        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(sender));
        given(accountRepository.findAllById(Set.of(1L))).willReturn(List.of(sender));
        // 실계정 담당자가 존재하므로 시스템 계정으로의 우회를 허용하지 않는다
        given(accountRepository.findActiveAdminContacts()).willReturn(List.of(sender, admin));

        // when & then
        assertThatThrownBy(() ->
                accountRequestService.requestPasswordReset("user@test.com", null, List.of(1L)))
                .isInstanceOf(BusinessException.class);

        then(messageRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("담당자 재검증 - 시스템 계정이 유일한 담당자면 수신자로 허용한다")
    void findEligibleRecipients_allowsSystemSenderWhenItIsTheOnlyContact() {
        // given - 실계정 담당자가 아직 없는 환경 (신규 구축 직후)
        Account sender = account(1L, "system@test.com", "시스템",
                AccountRole.ADMIN.getCodeId());
        Account requester = account(3L, "user@test.com", "요청자",
                AccountRole.USER.getCodeId());

        given(accountRepository.findByEmail("system@test.com")).willReturn(Optional.of(sender));
        given(accountRepository.findAllById(Set.of(1L))).willReturn(List.of(sender));
        given(accountRepository.findActiveAdminContacts()).willReturn(List.of(sender));
        given(accountRepository.findByEmail("user@test.com")).willReturn(Optional.of(requester));
        given(messageRepository.existsByDedupKey(any())).willReturn(false);
        given(transactionTemplate.execute(any()))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    callback.doInTransaction(new SimpleTransactionStatus());
                    return null;
                });
        given(messageRepository.saveAndFlush(any(Message.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        assertThatCode(() -> accountRequestService.requestPasswordReset(
                "user@test.com", null, List.of(1L)))
                .doesNotThrowAnyException();

        // then - 시스템 계정 본인이 수신자로 담긴다
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        then(messageRepository).should(times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRecipients()).hasSize(1);
        assertThat(captor.getValue().getRecipients().get(0).getRecipientEmail())
                .isEqualTo("system@test.com");
    }

    private Account account(Long id, String email, String name, String role) {
        return Account.builder()
                .accountId(id)
                .email(email)
                .password("encoded")
                .accountName(name)
                .role(role)
                .status(AccountStatus.ACTIVE.name())
                .build();
    }
}
