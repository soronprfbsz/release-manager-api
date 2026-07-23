package com.ts.rm.domain.site.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * SiteNote DTO 통합 클래스
 */
public final class SiteNoteDto {

    private SiteNoteDto() {
    }

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * 특이사항 생성 요청
     */
    @Builder
    @Schema(description = "특이사항 생성 요청")
    public record CreateRequest(
            @Schema(description = "제목", example = "시스템 점검 필요")
            @NotBlank(message = "제목은 필수입니다")
            @Size(max = 200, message = "제목은 200자 이하여야 합니다")
            String title,

            @Schema(description = "내용", example = "2024년 1월 정기 점검 예정")
            @NotBlank(message = "내용은 필수입니다")
            String content
    ) {

    }

    /**
     * 특이사항 수정 요청
     */
    @Builder
    @Schema(description = "특이사항 수정 요청")
    public record UpdateRequest(
            @Schema(description = "제목", example = "시스템 점검 완료")
            @Size(max = 200, message = "제목은 200자 이하여야 합니다")
            String title,

            @Schema(description = "내용", example = "점검 완료 및 이상 없음")
            String content
    ) {

    }

    // ========================================
    // Response DTOs
    // ========================================

    /**
     * 특이사항 응답
     */
    @Schema(description = "특이사항 응답")
    public record Response(
            @Schema(description = "특이사항 ID", example = "1")
            Long noteId,

            @Schema(description = "사이트 ID", example = "1")
            Long siteId,

            @Schema(description = "제목", example = "시스템 점검 필요")
            String title,

            @Schema(description = "내용", example = "2024년 1월 정기 점검 예정")
            String content,

            @Schema(description = "작성자 이메일", example = "admin@company.com")
            String createdByEmail,

            @Schema(description = "작성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "작성자 아바타 스타일", example = "lorelei")
            String createdByAvatarStyle,

            @Schema(description = "작성자 아바타 시드", example = "abc123")
            String createdByAvatarSeed,

            @Schema(description = "작성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "수정자 이메일", example = "admin@company.com")
            String updatedByEmail,

            @Schema(description = "수정자 이름", example = "홍길동")
            String updatedByName,

            @Schema(description = "수정자 아바타 스타일", example = "lorelei")
            String updatedByAvatarStyle,

            @Schema(description = "수정자 아바타 시드", example = "def456")
            String updatedByAvatarSeed,

            @Schema(description = "수정자 탈퇴 여부", example = "false")
            Boolean isDeletedUpdater,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "수정일시")
            LocalDateTime updatedAt
    ) {

    }
}
