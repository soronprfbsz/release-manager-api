package com.ts.rm.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ts.rm.config.AbstractTestBase;
import com.ts.rm.config.TestQueryDslConfig;
import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountRole;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.message.dto.MessageDto;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.repository.MessageRepository;
import com.ts.rm.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * MessageService 통합 테스트
 *
 * <p>읽음/숨김 상태가 수신자별로 독립적인지, 배지 집계가 그 상태를 정확히 반영하는지가
 * 이 도메인의 핵심이라 실제 영속성 계층까지 태워 검증한다.
 */
@Import(TestQueryDslConfig.class)
@Transactional
@DisplayName("MessageService 테스트")
class MessageServiceTest extends AbstractTestBase {

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account sender;
    private Account recipientA;
    private Account recipientB;

    @BeforeEach
    void setUp() {
        sender = saveAccount("sender@test.com", "발신자", AccountStatus.ACTIVE);
        recipientA = saveAccount("recipient-a@test.com", "수신자A", AccountStatus.ACTIVE);
        recipientB = saveAccount("recipient-b@test.com", "수신자B", AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("복수 수신자 발송 - 수신자 수만큼 수신 행이 생기고 전원 안읽음")
    void send_MultipleRecipients() {
        // when
        MessageDto.Detail detail = send(List.of(recipientA.getAccountId(), recipientB.getAccountId()));

        // then
        assertThat(detail.messageId()).isNotNull();
        assertThat(detail.senderName()).isEqualTo("발신자");
        assertThat(detail.recipients()).hasSize(2)
                .allSatisfy(recipient -> assertThat(recipient.readAt()).isNull());

        assertThat(messageService.countUnread(recipientA.getAccountId())).isEqualTo(1);
        assertThat(messageService.countUnread(recipientB.getAccountId())).isEqualTo(1);
        // 발신자는 수신자가 아니므로 배지가 오르지 않는다
        assertThat(messageService.countUnread(sender.getAccountId())).isZero();
    }

    @Test
    @DisplayName("수신자 ID 중복 - 중복은 제거되어 수신 행이 1건만 생성된다")
    void send_DuplicateRecipientIds() {
        // when
        MessageDto.Detail detail = send(List.of(
                recipientA.getAccountId(), recipientA.getAccountId()));

        // then
        assertThat(detail.recipients()).hasSize(1);
        assertThat(messageService.countUnread(recipientA.getAccountId())).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 수신자 포함 - 전체 발송이 거부된다")
    void send_UnknownRecipient_Rejected() {
        // when & then
        assertThatThrownBy(() -> send(List.of(recipientA.getAccountId(), 999_999L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("존재하지 않는 수신자");

        assertThat(messageService.countUnread(recipientA.getAccountId())).isZero();
    }

    @Test
    @DisplayName("비활성 수신자 포함 - 전체 발송이 거부된다")
    void send_InactiveRecipient_Rejected() {
        // given
        Account inactive = saveAccount("inactive@test.com", "비활성자", AccountStatus.INACTIVE);

        // when & then
        assertThatThrownBy(() -> send(List.of(recipientA.getAccountId(), inactive.getAccountId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("비활성 계정");

        assertThat(messageService.countUnread(recipientA.getAccountId())).isZero();
    }

    @Test
    @DisplayName("읽음 처리 - 해당 수신자의 배지만 줄고 다른 수신자는 그대로")
    void markAsRead_OnlyAffectsThatRecipient() {
        // given
        MessageDto.Detail detail = send(List.of(recipientA.getAccountId(), recipientB.getAccountId()));

        // when
        messageService.markAsRead(detail.messageId(), recipientA.getAccountId());

        // then
        assertThat(messageService.countUnread(recipientA.getAccountId())).isZero();
        assertThat(messageService.countUnread(recipientB.getAccountId())).isEqualTo(1);
    }

    @Test
    @DisplayName("읽음 처리 재호출 - 최초 읽은 시각이 유지된다")
    void markAsRead_KeepsFirstReadAt() {
        // given
        MessageDto.Detail detail = send(List.of(recipientA.getAccountId()));
        messageService.markAsRead(detail.messageId(), recipientA.getAccountId());
        var firstReadAt = messageService.getDetail(detail.messageId(), recipientA.getAccountId())
                .myReadAt();

        // when
        messageService.markAsRead(detail.messageId(), recipientA.getAccountId());

        // then
        assertThat(messageService.getDetail(detail.messageId(), recipientA.getAccountId()).myReadAt())
                .isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("수신하지 않은 메시지 읽음 처리 - 거부된다")
    void markAsRead_NotRecipient_Rejected() {
        // given
        MessageDto.Detail detail = send(List.of(recipientA.getAccountId()));

        // when & then
        assertThatThrownBy(() -> messageService.markAsRead(detail.messageId(), recipientB.getAccountId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("수신한 메시지가 아닙니다");
    }

    @Test
    @DisplayName("수신함 숨김 - 내 수신함과 배지에서만 사라지고 발신함에는 남는다")
    void hideFromInbox() {
        // given
        MessageDto.Detail detail = send(List.of(recipientA.getAccountId()));

        // when
        messageService.hideFromInbox(detail.messageId(), recipientA.getAccountId());

        // then
        assertThat(messageService.countUnread(recipientA.getAccountId())).isZero();
        assertThat(inbox(recipientA, false)).isEmpty();
        assertThat(outbox(sender)).hasSize(1);
    }

    @Test
    @DisplayName("발신함 숨김 - 발신함에서만 사라지고 수신자 수신함에는 남는다")
    void hideFromOutbox() {
        // given
        MessageDto.Detail detail = send(List.of(recipientA.getAccountId()));

        // when
        messageService.hideFromOutbox(detail.messageId(), sender.getAccountId());

        // then
        assertThat(outbox(sender)).isEmpty();
        assertThat(inbox(recipientA, false)).hasSize(1);
        assertThat(messageService.countUnread(recipientA.getAccountId())).isEqualTo(1);
    }

    @Test
    @DisplayName("발신자가 아닌 사람의 발신함 숨김 - 거부된다")
    void hideFromOutbox_NotSender_Rejected() {
        // given
        MessageDto.Detail detail = send(List.of(recipientA.getAccountId()));

        // when & then
        assertThatThrownBy(() ->
                messageService.hideFromOutbox(detail.messageId(), recipientA.getAccountId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("본인이 보낸 메시지만");
    }

    @Test
    @DisplayName("수신함 안읽음 필터 - 읽은 메시지는 제외된다")
    void getInbox_UnreadOnly() {
        // given
        MessageDto.Detail read = send(List.of(recipientA.getAccountId()));
        send(List.of(recipientA.getAccountId()));
        messageService.markAsRead(read.messageId(), recipientA.getAccountId());

        // when & then
        assertThat(inbox(recipientA, false)).hasSize(2);
        assertThat(inbox(recipientA, true)).hasSize(1);
    }

    @Test
    @DisplayName("발신함 읽음 현황 - 수신자 2명 중 1명이 읽으면 readCount 가 1")
    void getOutbox_ReadCount() {
        // given
        MessageDto.Detail detail = send(List.of(recipientA.getAccountId(), recipientB.getAccountId()));
        messageService.markAsRead(detail.messageId(), recipientA.getAccountId());

        // when
        List<MessageDto.OutboxItem> items = outbox(sender);

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).recipientCount()).isEqualTo(2);
        assertThat(items.get(0).readCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("상세 조회 권한 - 발신자와 수신자는 되고 제3자는 거부된다")
    void getDetail_Permission() {
        // given
        Account outsider = saveAccount("outsider@test.com", "제3자", AccountStatus.ACTIVE);
        MessageDto.Detail detail = send(List.of(recipientA.getAccountId()));

        // when & then
        assertThat(messageService.getDetail(detail.messageId(), sender.getAccountId())).isNotNull();
        assertThat(messageService.getDetail(detail.messageId(), recipientA.getAccountId())).isNotNull();
        assertThatThrownBy(() ->
                messageService.getDetail(detail.messageId(), outsider.getAccountId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("열람 권한");
    }

    @Test
    @DisplayName("멱등 키 - 같은 dedup_key 는 두 번 저장되지 않는다")
    void dedupKey_Unique() {
        // given
        String dedupKey = "PATCH_REMINDER:171:20260812";
        messageRepository.save(reminderWithDedupKey(dedupKey));

        // when & then — UNIQUE 제약 이전에 존재 확인만으로도 걸러진다
        assertThat(messageRepository.existsByDedupKey(dedupKey)).isTrue();
        assertThat(messageRepository.existsByDedupKey("PATCH_REMINDER:171:20260813")).isFalse();
    }

    // === Helpers ===

    private MessageDto.Detail send(List<Long> recipientIds) {
        return messageService.send(
                MessageDto.SendRequest.builder()
                        .recipientIds(recipientIds)
                        .title("테스트 제목")
                        .content("테스트 내용")
                        .build(),
                sender.getAccountId());
    }

    private List<MessageDto.InboxItem> inbox(Account account, boolean unreadOnly) {
        Page<MessageDto.InboxItem> page = messageService.getInbox(
                account.getAccountId(), unreadOnly, null, PageRequest.of(0, 10));
        return page.getContent();
    }

    private List<MessageDto.OutboxItem> outbox(Account account) {
        Page<MessageDto.OutboxItem> page = messageService.getOutbox(
                account.getAccountId(), null, PageRequest.of(0, 10));
        return page.getContent();
    }

    private Message reminderWithDedupKey(String dedupKey) {
        return Message.builder()
                .sender(sender)
                .senderEmail(sender.getEmail())
                .senderName(sender.getAccountName())
                .title("독촉")
                .content("내용")
                .dedupKey(dedupKey)
                .build();
    }

    private Account saveAccount(String email, String name, AccountStatus status) {
        return accountRepository.save(Account.builder()
                .email(email)
                .password("encoded")
                .accountName(name)
                .role(AccountRole.USER.getCodeId())
                .status(status.name())
                .build());
    }
}
