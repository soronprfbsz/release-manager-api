package com.ts.rm.domain.message.dto;

import com.ts.rm.domain.message.enums.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * Message DTO 통합 클래스
 */
public final class MessageDto {

    private MessageDto() {
    }

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * 메시지 발송 요청
     */
    @Builder
    @Schema(description = "메시지 발송 요청")
    public record SendRequest(
            @Schema(description = "수신자 계정 ID 목록", example = "[3, 7]")
            @NotEmpty(message = "수신자를 한 명 이상 선택해야 합니다")
            List<Long> recipientIds,

            @Schema(description = "제목", example = "빌드 확인 부탁드립니다")
            @NotBlank(message = "제목은 필수입니다")
            @Size(max = 200, message = "제목은 200자 이하여야 합니다")
            String title,

            @Schema(description = "내용", example = "1.1.18 빌드 확인 후 회신 부탁드립니다.")
            @NotBlank(message = "내용은 필수입니다")
            String content
    ) {

    }

    // ========================================
    // Response DTOs
    // ========================================

    /**
     * 수신함 항목
     */
    @Builder
    @Schema(description = "수신함 항목")
    public record InboxItem(
            @Schema(description = "메시지 ID", example = "12")
            Long messageId,

            @Schema(description = "메시지 유형")
            MessageType messageType,

            @Schema(description = "제목", example = "[자동알림] demo_260812 패치가 1일 후 자동 삭제됩니다")
            String title,

            @Schema(description = "내용")
            String content,

            @Schema(description = "발신자 계정 ID (탈퇴 시 null)", example = "1")
            Long senderAccountId,

            @Schema(description = "발신자 이름", example = "시스템 관리자")
            String senderName,

            @Schema(description = "발신자 이메일", example = "admin@tscientific.co.kr")
            String senderEmail,

            @Schema(description = "발신자 아바타 스타일", example = "pixelArt")
            String senderAvatarStyle,

            @Schema(description = "발신자 아바타 시드", example = "abc123")
            String senderAvatarSeed,

            @Schema(description = "발신자 탈퇴 여부", example = "false")
            Boolean isDeletedSender,

            @Schema(description = "참조 대상 유형", example = "PATCH")
            String refType,

            @Schema(description = "참조 대상 ID", example = "171")
            Long refId,

            @Schema(description = "참조 대상 프로젝트 ID (딥링크용)", example = "infraeye2")
            String refProjectId,

            @Schema(description = "참조 대상 릴리즈 타입 (딥링크용)", example = "STANDARD")
            String refReleaseType,

            @Schema(description = "읽은 일시 (null 이면 안읽음)")
            LocalDateTime readAt,

            @Schema(description = "수신 일시")
            LocalDateTime createdAt
    ) {

    }

    /**
     * 발신함 항목
     *
     * <p>복수 수신자에게 보낸 메시지도 1건으로 묶어 보여주고, 읽음 현황만 집계한다.
     */
    @Builder
    @Schema(description = "발신함 항목")
    public record OutboxItem(
            @Schema(description = "메시지 ID", example = "12")
            Long messageId,

            @Schema(description = "메시지 유형")
            MessageType messageType,

            @Schema(description = "제목", example = "빌드 확인 부탁드립니다")
            String title,

            @Schema(description = "내용")
            String content,

            @Schema(description = "수신자 목록")
            List<RecipientInfo> recipients,

            @Schema(description = "수신자 수", example = "3")
            Integer recipientCount,

            @Schema(description = "읽은 수신자 수", example = "2")
            Integer readCount,

            @Schema(description = "발송 일시")
            LocalDateTime createdAt
    ) {

    }

    /**
     * 수신자 정보 (발신함 / 상세에서 사용)
     */
    @Builder
    @Schema(description = "수신자 정보")
    public record RecipientInfo(
            @Schema(description = "수신자 계정 ID (탈퇴 시 null)", example = "7")
            Long accountId,

            @Schema(description = "수신자 이름", example = "홍길동")
            String accountName,

            @Schema(description = "수신자 이메일", example = "user@example.com")
            String email,

            @Schema(description = "수신자 부서명", example = "인프라기술팀")
            String departmentName,

            @Schema(description = "읽은 일시 (null 이면 안읽음)")
            LocalDateTime readAt
    ) {

    }

    /**
     * 메시지 상세
     *
     * <p>수신자와 발신자가 같은 응답을 쓴다. 수신자로 열었을 때만 {@code myReadAt}
     * 이 채워진다.
     */
    @Builder
    @Schema(description = "메시지 상세")
    public record Detail(
            @Schema(description = "메시지 ID", example = "12")
            Long messageId,

            @Schema(description = "메시지 유형")
            MessageType messageType,

            @Schema(description = "제목")
            String title,

            @Schema(description = "내용")
            String content,

            @Schema(description = "발신자 계정 ID (탈퇴 시 null)")
            Long senderAccountId,

            @Schema(description = "발신자 이름")
            String senderName,

            @Schema(description = "발신자 이메일")
            String senderEmail,

            @Schema(description = "발신자 아바타 스타일")
            String senderAvatarStyle,

            @Schema(description = "발신자 아바타 시드")
            String senderAvatarSeed,

            @Schema(description = "발신자 탈퇴 여부")
            Boolean isDeletedSender,

            @Schema(description = "수신자 목록")
            List<RecipientInfo> recipients,

            @Schema(description = "참조 대상 유형", example = "PATCH")
            String refType,

            @Schema(description = "참조 대상 ID", example = "171")
            Long refId,

            @Schema(description = "참조 대상 프로젝트 ID")
            String refProjectId,

            @Schema(description = "참조 대상 릴리즈 타입")
            String refReleaseType,

            @Schema(description = "내가 읽은 일시 (수신자로 조회한 경우)")
            LocalDateTime myReadAt,

            @Schema(description = "발송 일시")
            LocalDateTime createdAt
    ) {

    }

    /**
     * 안읽은 메시지 개수 (탑바 배지)
     */
    @Schema(description = "안읽은 메시지 개수")
    public record UnreadCount(
            @Schema(description = "안읽은 메시지 개수", example = "3")
            long unreadCount
    ) {

    }

    /**
     * 신규 메시지 실시간 알림 payload
     *
     * <p>WebSocket 으로 밀어주는 값은 토스트에 필요한 최소 정보뿐이다. 본문은
     * 사용자가 실제로 열 때 상세 API 로 가져간다.
     */
    @Schema(description = "신규 메시지 알림")
    public record NewMessageEvent(
            @Schema(description = "메시지 ID", example = "12")
            Long messageId,

            @Schema(description = "메시지 유형")
            MessageType messageType,

            @Schema(description = "제목")
            String title,

            @Schema(description = "발신자 이름", example = "홍길동")
            String senderName
    ) {

    }
}
