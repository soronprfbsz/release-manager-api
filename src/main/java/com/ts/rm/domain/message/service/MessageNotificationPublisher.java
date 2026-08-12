package com.ts.rm.domain.message.service;

import com.ts.rm.domain.message.dto.MessageDto;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.entity.MessageRecipient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 신규 메시지 실시간 알림 발행
 *
 * <p>푸시는 <b>트랜잭션 커밋 이후</b>에 실행한다. 커밋 전에 보내면 아직 조회되지 않는
 * 메시지를 알리게 되고, 반대로 푸시 실패가 메시지 저장을 되돌려서도 안 된다 (ADR-0004).
 *
 * <p>수신자 Principal 이름은 accountId 다 —
 * {@code StompAuthChannelInterceptor} 가 동일한 값으로 Principal 을 세운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageNotificationPublisher {

    /** 클라이언트 구독 경로는 /user/queue/messages 가 된다 */
    private static final String USER_QUEUE_MESSAGES = "/queue/messages";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 커밋 후 수신자 전원에게 신규 메시지 알림을 보낸다.
     *
     * <p>트랜잭션 밖에서 호출되면 즉시 발행한다.
     *
     * @param message    저장된 메시지
     * @param recipients 수신 행 목록
     */
    public void publishAfterCommit(Message message, List<MessageRecipient> recipients) {
        MessageDto.NewMessageEvent event = toEvent(message);
        List<Long> accountIds = recipients.stream()
                .map(MessageRecipient::getRecipient)
                .filter(account -> account != null)
                .map(account -> account.getAccountId())
                .toList();

        if (accountIds.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(accountIds, event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(accountIds, event);
            }
        });
    }

    /**
     * 실제 발행. 한 수신자에게 실패해도 나머지 전달은 계속한다 —
     * 실시간 알림이 없어도 폴링과 로그인 시 조회로 복구되기 때문이다.
     */
    private void publish(List<Long> accountIds, MessageDto.NewMessageEvent event) {
        accountIds.forEach(accountId -> {
            try {
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(accountId), USER_QUEUE_MESSAGES, event);
                log.debug("신규 메시지 푸시 - accountId: {}, messageId: {}",
                        accountId, event.messageId());
            } catch (Exception e) {
                log.warn("신규 메시지 푸시 실패 - accountId: {}, messageId: {}, cause: {}",
                        accountId, event.messageId(), e.getMessage());
            }
        });
    }

    private MessageDto.NewMessageEvent toEvent(Message message) {
        return new MessageDto.NewMessageEvent(
                message.getMessageId(),
                message.getMessageType(),
                message.getTitle(),
                message.getSenderName());
    }
}
