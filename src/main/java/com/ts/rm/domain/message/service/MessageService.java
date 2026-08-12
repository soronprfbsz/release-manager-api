package com.ts.rm.domain.message.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.message.dto.MessageDto;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.entity.MessageRecipient;
import com.ts.rm.domain.message.mapper.MessageDtoMapper;
import com.ts.rm.domain.message.repository.MessageRecipientRepository;
import com.ts.rm.domain.message.repository.MessageRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Message Service
 *
 * <p>사용자간 메시지 발송 / 수신함 / 발신함 / 읽음 처리 비즈니스 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final AccountRepository accountRepository;
    private final MessageDtoMapper mapper;
    private final MessageNotificationPublisher notificationPublisher;

    /**
     * 메시지 발송
     *
     * <p>수신자 중 존재하지 않거나 비활성인 계정이 있으면 전체를 거부한다 —
     * 일부만 전달된 채 성공으로 응답하면 발신자가 오해한다.
     *
     * @param request         발송 요청
     * @param senderAccountId 발신자 계정 ID
     * @return 발송된 메시지 상세
     */
    @Transactional
    public MessageDto.Detail send(MessageDto.SendRequest request, Long senderAccountId) {
        log.info("메시지 발송 - senderAccountId: {}, recipients: {}, title: {}",
                senderAccountId, request.recipientIds(), request.title());

        Account sender = findAccountById(senderAccountId);
        List<Account> recipients = findActiveRecipients(request.recipientIds());

        Message message = Message.builder()
                .sender(sender)
                .senderEmail(sender.getEmail())
                .senderName(sender.getAccountName())
                .title(request.title())
                .content(request.content())
                .build();
        recipients.forEach(message::addRecipient);

        Message saved = messageRepository.save(message);

        // 커밋 후에 실시간 알림 — 푸시 실패가 저장을 되돌리지 않는다
        notificationPublisher.publishAfterCommit(saved, saved.getRecipients());

        log.info("메시지 발송 완료 - messageId: {}, 수신자 {}명",
                saved.getMessageId(), recipients.size());

        return mapper.toDetail(saved, saved.getRecipients(), null);
    }

    /**
     * 수신함 조회
     *
     * @param accountId  조회자 계정 ID
     * @param unreadOnly 안읽은 것만 조회할지 여부
     * @param keyword    제목/내용/발신자명 검색어
     * @param pageable   페이징
     * @return 수신 메시지 페이지
     */
    public Page<MessageDto.InboxItem> getInbox(Long accountId, boolean unreadOnly, String keyword,
            Pageable pageable) {
        log.debug("수신함 조회 - accountId: {}, unreadOnly: {}, keyword: {}",
                accountId, unreadOnly, keyword);

        return messageRecipientRepository.findInbox(accountId, unreadOnly, keyword, pageable)
                .map(mapper::toInboxItem);
    }

    /**
     * 발신함 조회
     *
     * @param accountId 조회자 계정 ID
     * @param keyword   제목/내용 검색어
     * @param pageable  페이징
     * @return 발신 메시지 페이지 (수신자별 읽음 현황 포함)
     */
    public Page<MessageDto.OutboxItem> getOutbox(Long accountId, String keyword,
            Pageable pageable) {
        log.debug("발신함 조회 - accountId: {}, keyword: {}", accountId, keyword);

        Page<Message> messages = messageRepository.findOutbox(accountId, keyword, pageable);
        if (messages.isEmpty()) {
            return messages.map(message -> mapper.toOutboxItem(message, List.of()));
        }

        Map<Long, List<MessageRecipient>> recipientsByMessageId = loadRecipients(
                messages.getContent().stream().map(Message::getMessageId).toList());

        return messages.map(message -> mapper.toOutboxItem(message,
                recipientsByMessageId.getOrDefault(message.getMessageId(), List.of())));
    }

    /**
     * 메시지 상세 조회
     *
     * <p>발신자 본인 또는 수신자만 열람할 수 있다.
     *
     * @param messageId 메시지 ID
     * @param accountId 조회자 계정 ID
     * @return 메시지 상세
     */
    public MessageDto.Detail getDetail(Long messageId, Long accountId) {
        log.debug("메시지 상세 조회 - messageId: {}, accountId: {}", messageId, accountId);

        Message message = findMessageById(messageId);
        List<MessageRecipient> recipients = messageRecipientRepository
                .findByMessage_MessageIdIn(List.of(messageId));

        MessageRecipient mine = findMyRecipient(recipients, accountId);
        boolean isSender = isSender(message, accountId);

        if (mine == null && !isSender) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "열람 권한이 없는 메시지입니다.");
        }

        return mapper.toDetail(message, recipients, mine != null ? mine.getReadAt() : null);
    }

    /**
     * 읽음 처리
     *
     * <p>이미 읽은 메시지는 최초 읽은 시각을 유지한다.
     *
     * @param messageId 메시지 ID
     * @param accountId 수신자 계정 ID
     */
    @Transactional
    public void markAsRead(Long messageId, Long accountId) {
        log.debug("메시지 읽음 처리 - messageId: {}, accountId: {}", messageId, accountId);

        MessageRecipient recipient = findMyRecipientOrThrow(messageId, accountId);
        recipient.markAsRead();
    }

    /**
     * 수신함에서 숨김
     *
     * @param messageId 메시지 ID
     * @param accountId 수신자 계정 ID
     */
    @Transactional
    public void hideFromInbox(Long messageId, Long accountId) {
        log.info("수신함 숨김 - messageId: {}, accountId: {}", messageId, accountId);

        MessageRecipient recipient = findMyRecipientOrThrow(messageId, accountId);
        recipient.hide();
    }

    /**
     * 발신함에서 숨김
     *
     * <p>수신자의 수신함에는 그대로 남는다.
     *
     * @param messageId 메시지 ID
     * @param accountId 발신자 계정 ID
     */
    @Transactional
    public void hideFromOutbox(Long messageId, Long accountId) {
        log.info("발신함 숨김 - messageId: {}, accountId: {}", messageId, accountId);

        Message message = findMessageById(messageId);
        if (!isSender(message, accountId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 보낸 메시지만 삭제할 수 있습니다.");
        }

        message.hideForSender();
    }

    /**
     * 안읽은 메시지 개수 (탑바 배지)
     *
     * @param accountId 수신자 계정 ID
     * @return 안읽고 숨기지 않은 메시지 수
     */
    public long countUnread(Long accountId) {
        return messageRecipientRepository
                .countByRecipient_AccountIdAndReadAtIsNullAndDeletedAtIsNull(accountId);
    }

    // === Private Helper Methods ===

    /**
     * 수신자 계정 조회 및 검증
     *
     * <p>중복 ID 는 제거하고, 존재하지 않거나 ACTIVE 가 아닌 계정이 하나라도 있으면 거부한다.
     */
    private List<Account> findActiveRecipients(List<Long> recipientIds) {
        List<Long> distinctIds = new LinkedHashSet<>(recipientIds).stream().toList();

        List<Account> accounts = accountRepository.findAllById(distinctIds);
        if (accounts.size() != distinctIds.size()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                    "존재하지 않는 수신자가 포함되어 있습니다.");
        }

        List<String> inactiveNames = accounts.stream()
                .filter(account -> !AccountStatus.ACTIVE.name().equals(account.getStatus()))
                .map(Account::getAccountName)
                .toList();
        if (!inactiveNames.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE,
                    "비활성 계정에는 메시지를 보낼 수 없습니다: " + String.join(", ", inactiveNames));
        }

        return accounts;
    }

    /**
     * 메시지 ID 목록의 수신 행을 메시지별로 묶어 반환
     */
    private Map<Long, List<MessageRecipient>> loadRecipients(List<Long> messageIds) {
        return messageRecipientRepository.findByMessage_MessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(
                        recipient -> recipient.getMessage().getMessageId()));
    }

    private Account findAccountById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private Message findMessageById(Long messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "메시지를 찾을 수 없습니다: " + messageId));
    }

    private MessageRecipient findMyRecipientOrThrow(Long messageId, Long accountId) {
        return messageRecipientRepository
                .findByMessage_MessageIdAndRecipient_AccountId(messageId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN,
                        "수신한 메시지가 아닙니다."));
    }

    private MessageRecipient findMyRecipient(List<MessageRecipient> recipients, Long accountId) {
        return recipients.stream()
                .filter(recipient -> recipient.getRecipient() != null
                        && recipient.getRecipient().getAccountId().equals(accountId))
                .findFirst()
                .orElse(null);
    }

    private boolean isSender(Message message, Long accountId) {
        return message.getSender() != null
                && message.getSender().getAccountId().equals(accountId);
    }
}
