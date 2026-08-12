package com.ts.rm.domain.message.controller;

import com.ts.rm.domain.message.dto.MessageDto;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * MessageController Swagger 문서화 인터페이스
 */
@Tag(name = "메시지", description = "사용자간 메시지 및 시스템 알림 API")
@SwaggerResponse
public interface MessageControllerDocs {

    @Operation(
            summary = "메시지 발송",
            description = "선택한 수신자들에게 메시지를 발송합니다. 존재하지 않거나 비활성인 계정이 "
                    + "포함되면 발송 전체가 거부됩니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "발송됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageDetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<MessageDto.Detail>> send(
            @Valid @RequestBody MessageDto.SendRequest request
    );

    @Operation(
            summary = "수신함 조회",
            description = "내가 받은 메시지를 최신순으로 조회합니다. 숨김 처리한 메시지는 제외됩니다."
    )
    ResponseEntity<ApiResponse<Page<MessageDto.InboxItem>>> getInbox(
            @Parameter(description = "안읽은 메시지만 조회", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,

            @Parameter(description = "제목/내용/발신자명 검색어")
            @RequestParam(required = false) String keyword,

            Pageable pageable
    );

    @Operation(
            summary = "발신함 조회",
            description = "내가 보낸 메시지를 최신순으로 조회합니다. 복수 수신자 메시지도 1건으로 "
                    + "묶여 수신자별 읽음 현황이 함께 반환됩니다."
    )
    ResponseEntity<ApiResponse<Page<MessageDto.OutboxItem>>> getOutbox(
            @Parameter(description = "제목/내용 검색어")
            @RequestParam(required = false) String keyword,

            Pageable pageable
    );

    @Operation(
            summary = "메시지 상세 조회",
            description = "발신자 본인 또는 수신자만 열람할 수 있습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageDetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<MessageDto.Detail>> getDetail(
            @Parameter(description = "메시지 ID", required = true)
            @PathVariable Long messageId
    );

    @Operation(
            summary = "읽음 처리",
            description = "수신한 메시지를 읽음으로 표시합니다. 이미 읽은 메시지는 최초 읽은 시각이 유지됩니다."
    )
    ResponseEntity<ApiResponse<Void>> markAsRead(
            @Parameter(description = "메시지 ID", required = true)
            @PathVariable Long messageId
    );

    @Operation(
            summary = "수신함에서 삭제",
            description = "내 수신함에서만 숨깁니다. 발신자의 발신함에는 그대로 남습니다."
    )
    ResponseEntity<ApiResponse<Void>> deleteFromInbox(
            @Parameter(description = "메시지 ID", required = true)
            @PathVariable Long messageId
    );

    @Operation(
            summary = "발신함에서 삭제",
            description = "내 발신함에서만 숨깁니다. 수신자의 수신함에는 그대로 남습니다."
    )
    ResponseEntity<ApiResponse<Void>> deleteFromOutbox(
            @Parameter(description = "메시지 ID", required = true)
            @PathVariable Long messageId
    );

    @Operation(
            summary = "안읽은 메시지 개수",
            description = "탑바 알림 배지에 표시할 안읽은 메시지 개수를 반환합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UnreadCountApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<MessageDto.UnreadCount>> getUnreadCount();

    /**
     * Swagger 스키마용 wrapper 클래스 - 메시지 상세 응답
     */
    @Schema(description = "메시지 상세 API 응답")
    class MessageDetailApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "메시지 상세")
        public MessageDto.Detail data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 안읽은 개수 응답
     */
    @Schema(description = "안읽은 메시지 개수 API 응답")
    class UnreadCountApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "안읽은 메시지 개수")
        public MessageDto.UnreadCount data;
    }
}
