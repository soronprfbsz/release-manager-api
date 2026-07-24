package com.ts.rm.domain.site.dto;

import com.ts.rm.domain.site.enums.SiteCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * Site DTO 통합 클래스
 */
public final class SiteDto {

    private SiteDto() {
    }

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * 사이트 생성 요청
     */
    @Builder
    @Schema(description = "사이트 생성 요청")
    public record CreateRequest(
            @Schema(description = "사이트 코드", example = "company_a") @NotBlank(message = "사이트 코드는 필수입니다") @Size(max = 50, message = "사이트 코드는 50자 이하여야 합니다")
            String siteCode,

            @Schema(description = "사이트명", example = "A회사") @NotBlank(message = "사이트명은 필수입니다") @Size(max = 100, message = "사이트명은 100자 이하여야 합니다")
            String siteName,

            @Schema(description = "사이트 구분 (CUSTOMER=고객사 / INTERNAL_TEST=내부 테스트)", example = "CUSTOMER", defaultValue = "CUSTOMER")
            SiteCategory siteCategory,

            @Schema(description = "설명", example = "사이트 설명")
            String description,

            @Schema(description = "활성 여부", example = "true", defaultValue = "true")
            Boolean isActive,

            @Schema(description = "사용 프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "카드 글리프 텍스트 (1~3자)", example = "A") @Size(max = 3, message = "글리프 텍스트는 3자 이하여야 합니다")
            String glyphText,

            @Schema(description = "카드 글리프 배경 색상 키", example = "mint") @Size(max = 30, message = "글리프 배경 색상 키는 30자 이하여야 합니다")
            String glyphBackgroundColor
    ) {

        public CreateRequest {
            if (isActive == null) {
                isActive = true;
            }
            if (siteCategory == null) {
                siteCategory = SiteCategory.CUSTOMER;
            }
        }
    }

    /**
     * 사이트 수정 요청
     *
     * <p>프로젝트 정보는 수정 불가 (사이트 생성 시에만 설정 가능)
     */
    @Builder
    @Schema(description = "사이트 수정 요청")
    public record UpdateRequest(
            @Schema(description = "사이트명", example = "A회사") @Size(max = 100, message = "사이트명은 100자 이하여야 합니다")
            String siteName,

            @Schema(description = "사이트 구분 (CUSTOMER=고객사 / INTERNAL_TEST=내부 테스트, null=미변경)", example = "CUSTOMER")
            SiteCategory siteCategory,

            @Schema(description = "설명", example = "사이트 설명")
            String description,

            @Schema(description = "활성 여부", example = "true")
            Boolean isActive,

            @Schema(description = "카드 글리프 텍스트 (1~3자, 빈 문자열이면 제거)", example = "A") @Size(max = 3, message = "글리프 텍스트는 3자 이하여야 합니다")
            String glyphText,

            @Schema(description = "카드 글리프 배경 색상 키 (빈 문자열이면 제거)", example = "mint") @Size(max = 30, message = "글리프 배경 색상 키는 30자 이하여야 합니다")
            String glyphBackgroundColor
    ) {

    }

    // ========================================
    // Response DTOs
    // ========================================

    /**
     * 프로젝트 정보 (사이트 응답에 포함)
     */
    @Schema(description = "사이트 프로젝트 정보")
    public record ProjectInfo(
            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "프로젝트명", example = "Infraeye 2")
            String projectName,

            @Schema(description = "마지막 패치 버전", example = "1.2.0")
            String lastPatchedVersion,

            @Schema(description = "마지막 패치 일시")
            LocalDateTime lastPatchedAt
    ) {

    }

    /**
     * 사이트 상세 응답
     */
    @Schema(description = "사이트 상세 응답")
    public record DetailResponse(
            @Schema(description = "사이트 ID", example = "1")
            Long siteId,

            @Schema(description = "사이트 코드", example = "company_a")
            String siteCode,

            @Schema(description = "사이트명", example = "A회사")
            String siteName,

            @Schema(description = "사이트 구분 (CUSTOMER=고객사 / INTERNAL_TEST=내부 테스트)", example = "CUSTOMER")
            SiteCategory siteCategory,

            @Schema(description = "설명", example = "사이트 설명")
            String description,

            @Schema(description = "활성 여부", example = "true")
            Boolean isActive,

            @Schema(description = "커스텀 버전 존재 여부", example = "true")
            Boolean hasCustomVersion,

            @Schema(description = "사용 프로젝트 정보")
            ProjectInfo project,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "생성자 이메일", example = "홍길동")
            String createdByEmail,

            @Schema(description = "생성자 아바타 스타일", example = "lorelei")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123")
            String createdByAvatarSeed,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "수정일시")
            LocalDateTime updatedAt,

            @Schema(description = "수정자 이메일", example = "admin@company.com")
            String updatedByEmail,

            @Schema(description = "수정자 아바타 스타일", example = "lorelei")
            String updatedByAvatarStyle,

            @Schema(description = "수정자 아바타 시드", example = "def456")
            String updatedByAvatarSeed,

            @Schema(description = "수정자 탈퇴 여부", example = "false")
            Boolean isDeletedUpdater,

            @Schema(description = "카드 글리프 텍스트", example = "A")
            String glyphText,

            @Schema(description = "카드 글리프 배경 색상 키", example = "mint")
            String glyphBackgroundColor
    ) {

    }

    /**
     * 사이트 간단 응답
     */
    @Schema(description = "사이트 간단 응답")
    public record SimpleResponse(
            @Schema(description = "사이트 ID", example = "1")
            Long siteId,

            @Schema(description = "사이트 코드", example = "company_a")
            String siteCode,

            @Schema(description = "사이트명", example = "A회사")
            String siteName,

            @Schema(description = "활성 여부", example = "true")
            Boolean isActive
    ) {

    }

    /**
     * 사이트 목록 응답 (페이징용)
     */
    @Schema(description = "사이트 목록 응답")
    public record ListResponse(
            @Schema(description = "행 번호", example = "1")
            Long rowNumber,

            @Schema(description = "사이트 ID", example = "1")
            Long siteId,

            @Schema(description = "사이트 코드", example = "company_a")
            String siteCode,

            @Schema(description = "사이트명", example = "A회사")
            String siteName,

            @Schema(description = "사이트 구분 (CUSTOMER=고객사 / INTERNAL_TEST=내부 테스트)", example = "CUSTOMER")
            SiteCategory siteCategory,

            @Schema(description = "설명", example = "사이트 설명")
            String description,

            @Schema(description = "활성 여부", example = "true")
            Boolean isActive,

            @Schema(description = "커스텀 버전 존재 여부", example = "true")
            Boolean hasCustomVersion,

            @Schema(description = "사용 프로젝트 정보")
            ProjectInfo project,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "카드 글리프 텍스트", example = "A")
            String glyphText,

            @Schema(description = "카드 글리프 배경 색상 키", example = "mint")
            String glyphBackgroundColor
    ) {

    }

    /**
     * 사이트 초기화 결과 응답 DTO.
     *
     * <p>삭제된 건수를 각 테이블별로 반환
     */
    public record ResetPatchStateResponse(
            @Schema(description = "삭제된 사이트 버전 건수", example = "3")
            long deletedSiteVersionCount,

            @Schema(description = "초기화된 customer_project 건수", example = "1")
            long resetSiteProjectCount,

            @Schema(description = "삭제된 패치 이력 건수", example = "10")
            long deletedPatchHistoryCount
    ) {

    }
}
