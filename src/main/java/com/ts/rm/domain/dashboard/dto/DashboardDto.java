package com.ts.rm.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 대시보드 DTO
 */
public class DashboardDto {

    /**
     * 최근 릴리즈 버전 정보 (표준본)
     */
    @Schema(description = "최근 릴리즈 버전 정보")
    public record RecentVersion(
            @Schema(description = "릴리즈 버전 ID", example = "2")
            Long releaseVersionId,

            @Schema(description = "버전", example = "1.1.0")
            String version,

            @Schema(description = "릴리즈 타입", example = "STANDARD")
            String releaseType,

            @Schema(description = "생성일시")
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime createdAt,

            @Schema(description = "설명")
            String comment,

            @Schema(description = "파일 카테고리 목록", example = "[\"DATABASE\", \"WEB\"]")
            List<String> fileCategories,

            @Schema(description = "생성자명", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 이메일", example = "hong@example.com")
            String createdByEmail,

            @Schema(description = "생성자 아바타 스타일", example = "adventurer")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123xyz")
            String createdByAvatarSeed
    ) {
    }

    /**
     * 최근 생성 패치 정보
     */
    @Schema(description = "최근 생성 패치 정보")
    public record RecentPatch(
            @Schema(description = "패치 이력 ID", example = "1")
            Long historyId,

            @Schema(description = "패치명", example = "standard_1.0.0_to_1.1.0_patch.zip")
            String patchName,

            @Schema(description = "시작 버전", example = "1.0.0")
            String fromVersion,

            @Schema(description = "종료 버전", example = "1.1.0")
            String toVersion,

            @Schema(description = "릴리즈 타입", example = "STANDARD")
            String releaseType,

            @Schema(description = "생성일시")
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime createdAt,

            @Schema(description = "설명")
            String description,

            @Schema(description = "고객사 ID (CUSTOM 타입인 경우)", example = "1")
            Long customerId,

            @Schema(description = "고객사 코드 (CUSTOM 타입인 경우)", example = "CUST001")
            String customerCode,

            @Schema(description = "고객사명 (CUSTOM 타입인 경우)", example = "A회사")
            String customerName,

            @Schema(description = "담당자명", example = "홍길동")
            String assigneeName,

            @Schema(description = "담당자 이메일", example = "hong@example.com")
            String assigneeEmail,

            @Schema(description = "담당자 아바타 스타일", example = "adventurer")
            String assigneeAvatarStyle,

            @Schema(description = "담당자 아바타 시드", example = "abc123xyz")
            String assigneeAvatarSeed,

            @Schema(description = "생성자명", example = "admin")
            String createdByName,

            @Schema(description = "생성자 이메일", example = "admin@example.com")
            String createdByEmail,

            @Schema(description = "생성자 아바타 스타일", example = "adventurer")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "xyz789abc")
            String createdByAvatarSeed
    ) {
    }

    /**
     * 표준본 최신 릴리즈 버전 응답
     */
    @Schema(description = "표준본 최신 릴리즈 버전 응답")
    public record RecentStandardResponse(
            @Schema(description = "표준본 최신 릴리즈 버전 목록")
            List<RecentVersion> versions
    ) {
    }

    /**
     * 최신 빌드 버전 정보 (표준 + 커스텀 통합)
     *
     * <p>version 필드는 핫픽스/빌드 라벨을 포함한 full 버전 (예: "1.1.0.260501-1")
     */
    @Schema(description = "최신 빌드 버전 정보")
    public record RecentBuildVersion(
            @Schema(description = "릴리즈 버전 ID", example = "2")
            Long releaseVersionId,

            @Schema(description = "빌드 라벨이 포함된 전체 버전", example = "1.1.0.260501-1")
            String version,

            @Schema(description = "릴리즈 타입", example = "STANDARD")
            String releaseType,

            @Schema(description = "생성일시")
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime createdAt,

            @Schema(description = "설명")
            String comment,

            @Schema(description = "파일 카테고리 목록", example = "[\"DATABASE\", \"WEB\"]")
            List<String> fileCategories,

            @Schema(description = "고객사 ID (CUSTOM 타입인 경우)", example = "1")
            Long customerId,

            @Schema(description = "고객사 코드 (CUSTOM 타입인 경우)", example = "CUST001")
            String customerCode,

            @Schema(description = "고객사명 (CUSTOM 타입인 경우)", example = "A회사")
            String customerName,

            @Schema(description = "생성자명", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 이메일", example = "hong@example.com")
            String createdByEmail,

            @Schema(description = "생성자 아바타 스타일", example = "adventurer")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123xyz")
            String createdByAvatarSeed
    ) {
    }

    /**
     * 최신 빌드 버전 응답 (표준 + 커스텀 통합)
     */
    @Schema(description = "최신 빌드 버전 응답")
    public record RecentBuildResponse(
            @Schema(description = "최신 빌드 버전 목록")
            List<RecentBuildVersion> versions
    ) {
    }

    /**
     * 최근 생성 패치 응답 (표준+커스텀)
     */
    @Schema(description = "최근 생성 패치 응답")
    public record RecentPatchResponse(
            @Schema(description = "최근 생성 패치 목록 (표준+커스텀)")
            List<RecentPatch> patches
    ) {
    }
}
