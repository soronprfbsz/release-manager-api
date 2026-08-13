package com.ts.rm.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ts.rm.config.AbstractTestBase;
import com.ts.rm.config.TestQueryDslConfig;
import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountRole;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.enums.MessageType;
import com.ts.rm.domain.message.repository.MessageRepository;
import com.ts.rm.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Import(TestQueryDslConfig.class)
@Transactional
@DisplayName("AccountRequestService 테스트")
class AccountRequestServiceTest extends AbstractTestBase {

    @Autowired
    private AccountRequestService accountRequestService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MessageRepository messageRepository;

    private Account systemSender;
    private Account admin;
    private Account operator;
    private Account requester;

    @BeforeEach
    void setUp() {
        systemSender = saveAccount("system@test.com", "시스템", AccountRole.ADMIN.getCodeId(),
                AccountStatus.ACTIVE);
        admin = saveAccount("admin@test.com", "관리자", AccountRole.ADMIN.getCodeId(),
                AccountStatus.ACTIVE);
        operator = saveAccount("op@test.com", "운영자", AccountRole.OPERATOR.getCodeId(),
                AccountStatus.ACTIVE);
        requester = saveAccount("user@test.com", "요청자", AccountRole.USER.getCodeId(),
                AccountStatus.ACTIVE);

        ReflectionTestUtils.setField(
                accountRequestService, "systemSenderEmail", systemSender.getEmail());
        ReflectionTestUtils.setField(accountRequestService, "cooldownMinutes", 10);
    }

    @Test
    @DisplayName("정상 요청 - 선택한 담당자 전원에게 메시지가 발송된다")
    void requestPasswordReset_sendsToSelectedRecipients() {
        accountRequestService.requestPasswordReset(
                requester.getEmail(), "내선 1234로 연락 주세요",
                List.of(admin.getAccountId(), operator.getAccountId()));

        List<Message> messages = messageRepository.findAll();
        assertThat(messages).hasSize(1);

        Message message = messages.get(0);
        assertThat(message.getMessageType()).isEqualTo(MessageType.PASSWORD_RESET_REQUEST);
        assertThat(message.getSenderEmail()).isEqualTo(systemSender.getEmail());
        assertThat(message.getRefType()).isEqualTo("ACCOUNT");
        assertThat(message.getRefId()).isEqualTo(requester.getAccountId());
        assertThat(message.getContent()).contains("내선 1234로 연락 주세요");
        assertThat(message.getRecipients())
                .extracting(recipient -> recipient.getRecipient().getAccountId())
                .containsExactlyInAnyOrder(admin.getAccountId(), operator.getAccountId());
    }

    @Test
    @DisplayName("미등록 이메일 - 메시지를 만들지 않고 예외 없이 반환한다")
    void requestPasswordReset_unknownEmail_isSilentlyIgnored() {
        accountRequestService.requestPasswordReset(
                "nobody@test.com", null, List.of(admin.getAccountId()));

        assertThat(messageRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("쿨다운 - 같은 버킷 안의 재요청은 메시지를 늘리지 않는다")
    void requestPasswordReset_withinCooldown_doesNotDuplicate() {
        accountRequestService.requestPasswordReset(
                requester.getEmail(), null, List.of(admin.getAccountId()));
        accountRequestService.requestPasswordReset(
                requester.getEmail(), null, List.of(admin.getAccountId()));

        assertThat(messageRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("수신자 필터 - ADMIN/OPERATOR 이면서 ACTIVE 인 계정만 남는다")
    void requestPasswordReset_filtersIneligibleRecipients() {
        Account developer = saveAccount("dev@test.com", "개발자",
                AccountRole.DEVELOPER.getCodeId(), AccountStatus.ACTIVE);
        Account inactiveAdmin = saveAccount("old@test.com", "퇴사자",
                AccountRole.ADMIN.getCodeId(), AccountStatus.INACTIVE);

        accountRequestService.requestPasswordReset(requester.getEmail(), null,
                List.of(admin.getAccountId(), developer.getAccountId(),
                        inactiveAdmin.getAccountId()));

        Message message = messageRepository.findAll().get(0);
        assertThat(message.getRecipients())
                .extracting(recipient -> recipient.getRecipient().getAccountId())
                .containsExactly(admin.getAccountId());
    }

    @Test
    @DisplayName("유효 수신자 0명 - 계정 정보를 노출하지 않는 400 예외")
    void requestPasswordReset_noEligibleRecipient_throwsWithoutAccountInfo() {
        Account developer = saveAccount("dev2@test.com", "개발자2",
                AccountRole.DEVELOPER.getCodeId(), AccountStatus.ACTIVE);

        assertThatThrownBy(() -> accountRequestService.requestPasswordReset(
                requester.getEmail(), null, List.of(developer.getAccountId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효한 담당자")
                .hasMessageNotContaining("개발자2")
                .hasMessageNotContaining("dev2@test.com");
    }

    @Test
    @DisplayName("시스템 발신 계정이 없으면 500 예외")
    void requestPasswordReset_missingSystemSender_throws() {
        ReflectionTestUtils.setField(
                accountRequestService, "systemSenderEmail", "missing@test.com");

        assertThatThrownBy(() -> accountRequestService.requestPasswordReset(
                requester.getEmail(), null, List.of(admin.getAccountId())))
                .isInstanceOf(BusinessException.class);
    }

    private Account saveAccount(String email, String name, String role, AccountStatus status) {
        return accountRepository.save(Account.builder()
                .email(email)
                .password("encoded")
                .accountName(name)
                .role(role)
                .status(status.name())
                .build());
    }
}
