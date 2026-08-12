package com.ts.rm.domain.message.controller;

import com.ts.rm.domain.message.dto.MessageDto;
import com.ts.rm.domain.message.service.MessageService;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.security.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Message Controller
 *
 * <p>사용자간 메시지 REST API. 모든 엔드포인트가 로그인 사용자 본인 기준으로 동작한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController implements MessageControllerDocs {

    private final MessageService messageService;

    /**
     * 메시지 발송
     */
    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<MessageDto.Detail>> send(
            @Valid @RequestBody MessageDto.SendRequest request) {

        log.info("POST /api/messages - recipients: {}, title: {}",
                request.recipientIds(), request.title());

        MessageDto.Detail response = messageService.send(request, SecurityUtil.getCurrentAccountId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * 수신함 조회
     */
    @Override
    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<Page<MessageDto.InboxItem>>> getInbox(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable) {

        log.info("GET /api/messages/inbox - unreadOnly: {}, keyword: {}", unreadOnly, keyword);

        Page<MessageDto.InboxItem> response = messageService.getInbox(
                SecurityUtil.getCurrentAccountId(), unreadOnly, keyword, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 발신함 조회
     */
    @Override
    @GetMapping("/outbox")
    public ResponseEntity<ApiResponse<Page<MessageDto.OutboxItem>>> getOutbox(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable) {

        log.info("GET /api/messages/outbox - keyword: {}", keyword);

        Page<MessageDto.OutboxItem> response = messageService.getOutbox(
                SecurityUtil.getCurrentAccountId(), keyword, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 메시지 상세 조회
     */
    @Override
    @GetMapping("/{messageId}")
    public ResponseEntity<ApiResponse<MessageDto.Detail>> getDetail(
            @PathVariable Long messageId) {

        log.info("GET /api/messages/{}", messageId);

        MessageDto.Detail response = messageService.getDetail(
                messageId, SecurityUtil.getCurrentAccountId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 읽음 처리
     */
    @Override
    @PatchMapping("/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long messageId) {

        log.info("PATCH /api/messages/{}/read", messageId);

        messageService.markAsRead(messageId, SecurityUtil.getCurrentAccountId());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 수신함에서 삭제 (숨김)
     */
    @Override
    @DeleteMapping("/{messageId}/inbox")
    public ResponseEntity<ApiResponse<Void>> deleteFromInbox(@PathVariable Long messageId) {

        log.info("DELETE /api/messages/{}/inbox", messageId);

        messageService.hideFromInbox(messageId, SecurityUtil.getCurrentAccountId());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 발신함에서 삭제 (숨김)
     */
    @Override
    @DeleteMapping("/{messageId}/outbox")
    public ResponseEntity<ApiResponse<Void>> deleteFromOutbox(@PathVariable Long messageId) {

        log.info("DELETE /api/messages/{}/outbox", messageId);

        messageService.hideFromOutbox(messageId, SecurityUtil.getCurrentAccountId());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 안읽은 메시지 개수 (탑바 배지)
     */
    @Override
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<MessageDto.UnreadCount>> getUnreadCount() {

        long unreadCount = messageService.countUnread(SecurityUtil.getCurrentAccountId());

        return ResponseEntity.ok(ApiResponse.success(new MessageDto.UnreadCount(unreadCount)));
    }
}
