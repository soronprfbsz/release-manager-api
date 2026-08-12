package com.ts.rm.domain.message.mapper;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.message.dto.MessageDto;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.entity.MessageRecipient;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Message Entity → DTO 변환
 *
 * <p>다른 도메인은 MapStruct 를 쓰지만, 메시지 응답은 수신자 집계·조회 주체별
 * 읽음 상태처럼 <b>호출 컨텍스트에 의존하는 값</b>이 많아 선언적 매핑으로
 * 표현하기 어렵다. 변환 책임을 한곳에 모으되 구현은 명시적으로 둔다.
 */
@Component
public class MessageDtoMapper {

    /**
     * 수신함 항목 변환
     *
     * @param recipient 내 수신 행 (message / sender 는 fetch join 되어 있어야 함)
     */
    public MessageDto.InboxItem toInboxItem(MessageRecipient recipient) {
        Message message = recipient.getMessage();
        Account sender = message.getSender();

        return MessageDto.InboxItem.builder()
                .messageId(message.getMessageId())
                .messageType(message.getMessageType())
                .title(message.getTitle())
                .content(message.getContent())
                .senderAccountId(sender != null ? sender.getAccountId() : null)
                .senderName(message.getSenderName())
                .senderEmail(message.getSenderEmail())
                .senderAvatarStyle(sender != null ? sender.getAvatarStyle() : null)
                .senderAvatarSeed(sender != null ? sender.getAvatarSeed() : null)
                .isDeletedSender(sender == null)
                .refType(message.getRefType())
                .refId(message.getRefId())
                .refProjectId(message.getRefProjectId())
                .refReleaseType(message.getRefReleaseType())
                .readAt(recipient.getReadAt())
                .createdAt(message.getCreatedAt())
                .build();
    }

    /**
     * 발신함 항목 변환
     *
     * @param message    발신 메시지
     * @param recipients 해당 메시지의 수신 행 목록
     */
    public MessageDto.OutboxItem toOutboxItem(Message message, List<MessageRecipient> recipients) {
        int readCount = (int) recipients.stream()
                .filter(r -> r.getReadAt() != null)
                .count();

        return MessageDto.OutboxItem.builder()
                .messageId(message.getMessageId())
                .messageType(message.getMessageType())
                .title(message.getTitle())
                .content(message.getContent())
                .recipients(toRecipientInfoList(recipients))
                .recipientCount(recipients.size())
                .readCount(readCount)
                .createdAt(message.getCreatedAt())
                .build();
    }

    /**
     * 메시지 상세 변환
     *
     * @param message    메시지
     * @param recipients 수신 행 목록
     * @param myReadAt   조회 주체가 수신자일 때의 읽은 시각 (발신자로 조회하면 null)
     */
    public MessageDto.Detail toDetail(Message message, List<MessageRecipient> recipients,
            LocalDateTime myReadAt) {
        Account sender = message.getSender();

        return MessageDto.Detail.builder()
                .messageId(message.getMessageId())
                .messageType(message.getMessageType())
                .title(message.getTitle())
                .content(message.getContent())
                .senderAccountId(sender != null ? sender.getAccountId() : null)
                .senderName(message.getSenderName())
                .senderEmail(message.getSenderEmail())
                .senderAvatarStyle(sender != null ? sender.getAvatarStyle() : null)
                .senderAvatarSeed(sender != null ? sender.getAvatarSeed() : null)
                .isDeletedSender(sender == null)
                .recipients(toRecipientInfoList(recipients))
                .refType(message.getRefType())
                .refId(message.getRefId())
                .refProjectId(message.getRefProjectId())
                .refReleaseType(message.getRefReleaseType())
                .myReadAt(myReadAt)
                .createdAt(message.getCreatedAt())
                .build();
    }

    /**
     * 수신자 정보 목록 변환
     */
    public List<MessageDto.RecipientInfo> toRecipientInfoList(List<MessageRecipient> recipients) {
        return recipients.stream()
                .map(this::toRecipientInfo)
                .toList();
    }

    private MessageDto.RecipientInfo toRecipientInfo(MessageRecipient recipient) {
        Account account = recipient.getRecipient();
        String departmentName = account != null && account.getDepartment() != null
                ? account.getDepartment().getDepartmentName()
                : null;

        return MessageDto.RecipientInfo.builder()
                .accountId(account != null ? account.getAccountId() : null)
                .accountName(recipient.getRecipientName())
                .email(recipient.getRecipientEmail())
                .departmentName(departmentName)
                .readAt(recipient.getReadAt())
                .build();
    }
}
