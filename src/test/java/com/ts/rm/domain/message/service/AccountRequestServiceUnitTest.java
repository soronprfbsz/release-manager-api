package com.ts.rm.domain.message.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountRole;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.repository.MessageRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @DisplayName("동시 요청으로 UNIQUE 제약이 깨져도 호출자에게 예외가 전파되지 않는다")
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
        given(messageRepository.existsByDedupKey(any())).willReturn(false);

        // TransactionTemplate 이 콜백을 실행하다 제약 위반을 만나는 상황을 재현한다
        given(transactionTemplate.execute(any()))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    callback.doInTransaction(new SimpleTransactionStatus());
                    return null;
                });
        given(messageRepository.saveAndFlush(any(Message.class)))
                .willThrow(new DataIntegrityViolationException("duplicate dedup_key"));

        assertThatCode(() -> accountRequestService.requestPasswordReset(
                "user@test.com", null, List.of(2L)))
                .doesNotThrowAnyException();
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
