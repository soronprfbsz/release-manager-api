package com.ts.rm.domain.patch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * PatchHistory DTO 통합 클래스
 */
public final class PatchHistoryDto {

    private PatchHistoryDto() {
    }

    /**
     * 패치 이력 목록 응답 (페이징용)
     */
    @Schema(description = "패치 이력 목록 응답")
    public record ListResponse(
            @Schema(description = "행 번호", example = "1")
            Long rowNumber,

            @Schema(description = "이력 ID", example = "1")
            Long historyId,

            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "릴리즈 타입", example = "STANDARD")
            String releaseType,

            @Schema(description = "고객사 ID", example = "1")
            Long customerId,

            @Schema(description = "고객사 코드", example = "companyA")
            String customerCode,

            @Schema(description = "고객사명", example = "A 회사")
            String customerName,

            @Schema(description = "시작 버전", example = "1.0.0")
            String fromVersion,

            @Schema(description = "종료 버전", example = "1.1.1")
            String toVersion,

            @Schema(description = "패치 이름", example = "20251125_1.0.0_1.1.1")
            String patchName,

            @Schema(description = "설명", example = "1.0.0에서 1.1.1로 업그레이드용 누적 패치")
            String description,

            @Schema(description = "담당자 ID", example = "1")
            Long assigneeId,

            @Schema(description = "담당자 이름", example = "홍길동")
            String assigneeName,

            @Schema(description = "담당자 이메일", example = "hong@company.com")
            String assigneeEmail,

            @Schema(description = "담당자 아바타 스타일", example = "lorelei")
            String assigneeAvatarStyle,

            @Schema(description = "담당자 아바타 시드", example = "abc123")
            String assigneeAvatarSeed,

            @Schema(description = "담당자 탈퇴 여부", example = "false")
            Boolean isDeletedAssignee,

            @Schema(description = "생성자 이메일", example = "admin@tscientific")
            String createdByEmail,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 아바타 스타일", example = "lorelei")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123")
            String createdByAvatarSeed,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "등록일시")
            LocalDateTime createdAt,

            @Schema(description = "완료 일시")
            LocalDateTime completedAt,

            @Schema(description = "완료 처리자 이메일", example = "admin@tscientific.com")
            String completedBy
    ) {

    }
}
