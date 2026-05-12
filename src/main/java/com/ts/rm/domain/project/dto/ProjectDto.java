package com.ts.rm.domain.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * Project DTO 통합 클래스
 */
public final class ProjectDto {

    private ProjectDto() {
    }

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * 프로젝트 생성 요청
     */
    @Builder
    @Schema(description = "프로젝트 생성 요청")
    public record CreateRequest(
            @Schema(description = "프로젝트 ID (영문 소문자, 숫자, 언더스코어만 허용)", example = "infraeye1")
            @NotBlank(message = "프로젝트 ID는 필수입니다")
            @Size(max = 50, message = "프로젝트 ID는 50자 이하여야 합니다")
            @Pattern(regexp = "^[a-z0-9_]+$", message = "프로젝트 ID는 영문 소문자, 숫자, 언더스코어만 허용됩니다")
            String projectId,

            @Schema(description = "프로젝트명", example = "Infraeye 1")
            @NotBlank(message = "프로젝트명은 필수입니다")
            @Size(max = 100, message = "프로젝트명은 100자 이하여야 합니다")
            String projectName,

            @Schema(description = "설명", example = "Infraeye 1.0 - 레거시 NMS 솔루션")
            String description,

            @Schema(description = "활성 여부 (미입력 시 true)", example = "true")
            Boolean isEnabled,

            @Schema(description = "카드 좌상단 글리프 텍스트 (1~3자)", example = "IE")
            @Size(max = 3, message = "글리프 텍스트는 3자 이하여야 합니다")
            String glyphText,

            @Schema(description = "글리프 배경 색상 키", example = "mint")
            @Size(max = 30, message = "글리프 배경 색상 키는 30자 이하여야 합니다")
            String glyphBackgroundColor
    ) {

    }

    /**
     * 프로젝트 수정 요청
     */
    @Builder
    @Schema(description = "프로젝트 수정 요청")
    public record UpdateRequest(
            @Schema(description = "프로젝트명", example = "Infraeye 1")
            @Size(max = 100, message = "프로젝트명은 100자 이하여야 합니다")
            String projectName,

            @Schema(description = "설명", example = "Infraeye 1.0 - 레거시 NMS 솔루션")
            String description,

            @Schema(description = "활성 여부", example = "true")
            Boolean isEnabled,

            @Schema(description = "카드 좌상단 글리프 텍스트 (1~3자, null=미변경, \"\"=제거)", example = "IE")
            @Size(max = 3, message = "글리프 텍스트는 3자 이하여야 합니다")
            String glyphText,

            @Schema(description = "글리프 배경 색상 키 (null=미변경, \"\"=제거)", example = "mint")
            @Size(max = 30, message = "글리프 배경 색상 키는 30자 이하여야 합니다")
            String glyphBackgroundColor
    ) {

    }

    // ========================================
    // Response DTOs
    // ========================================

    /**
     * 프로젝트 상세 응답
     */
    @Schema(description = "프로젝트 상세 응답")
    public record DetailResponse(
            @Schema(description = "프로젝트 ID", example = "infraeye1")
            String projectId,

            @Schema(description = "프로젝트명", example = "Infraeye 1")
            String projectName,

            @Schema(description = "설명", example = "Infraeye 1.0 - 레거시 NMS 솔루션")
            String description,

            @Schema(description = "활성 여부", example = "true")
            Boolean isEnabled,

            @Schema(description = "카드 좌상단 글리프 텍스트 (1~3자)", example = "IE")
            String glyphText,

            @Schema(description = "글리프 배경 색상 키", example = "mint")
            String glyphBackgroundColor,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "생성자 이메일", example = "홍길동")
            String createdByEmail,

            @Schema(description = "생성자 아바타 스타일", example = "lorelei")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123")
            String createdByAvatarSeed,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator
    ) {

    }

    // ========================================
    // Onboarding File DTOs
    // ========================================

    /**
     * 온보딩 파일 트리 노드 (디렉토리 또는 파일)
     */
    @Schema(description = "온보딩 파일 트리 노드")
    public record OnboardingFileNode(
            @Schema(description = "파일/디렉토리 이름", example = "mariadb")
            String name,

            @Schema(description = "전체 경로", example = "/mariadb")
            String path,

            @Schema(description = "파일 경로 (onboardings/{projectId}/ 포함)", example = "onboardings/infraeye2/mariadb/init.sql")
            String filePath,

            @Schema(description = "타입 (file 또는 directory)", example = "directory")
            String type,

            @Schema(description = "파일 크기 (파일인 경우만, bytes)", example = "1024")
            Long size,

            @Schema(description = "수정 날짜 (파일인 경우만)", example = "2025-01-22T10:30:00")
            LocalDateTime modifiedAt,

            @Schema(description = "파일 유형/MIME 타입 (파일인 경우만)", example = "text/x-sql")
            String mimeType,

            @Schema(description = "하위 노드 (디렉토리인 경우만)")
            List<OnboardingFileNode> children
    ) {
        /**
         * 디렉토리 노드 생성
         */
        public static OnboardingFileNode directory(String name, String path, String filePath, List<OnboardingFileNode> children) {
            return new OnboardingFileNode(name, path, filePath, "directory", null, null, null, children);
        }

        /**
         * 파일 노드 생성
         */
        public static OnboardingFileNode file(String name, String path, String filePath, Long size,
                LocalDateTime modifiedAt, String mimeType) {
            return new OnboardingFileNode(name, path, filePath, "file", size, modifiedAt, mimeType, null);
        }
    }

    /**
     * 온보딩 디렉토리 생성 응답
     */
    @Schema(description = "온보딩 디렉토리 생성 응답")
    public record OnboardingDirectoryResponse(
            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "생성된 디렉토리 경로", example = "/mariadb/scripts")
            String createdPath,

            @Schema(description = "메시지", example = "디렉토리가 생성되었습니다.")
            String message
    ) {
    }

    /**
     * 온보딩 파일 삭제 응답
     */
    @Schema(description = "온보딩 파일 삭제 응답")
    public record OnboardingDeleteResponse(
            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "삭제된 파일 경로", example = "/mariadb/init_schema.sql")
            String deletedPath,

            @Schema(description = "메시지", example = "파일이 삭제되었습니다.")
            String message
    ) {
    }

    /**
     * 온보딩 파일 트리 응답
     */
    @Schema(description = "온보딩 파일 트리 응답")
    public record OnboardingFilesResponse(
            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "프로젝트명", example = "Infraeye 2")
            String projectName,

            @Schema(description = "온보딩 파일 존재 여부", example = "true")
            boolean hasFiles,

            @Schema(description = "총 파일 수", example = "5")
            int totalFileCount,

            @Schema(description = "총 파일 크기 (bytes)", example = "102400")
            long totalSize,

            @Schema(description = "파일 트리")
            OnboardingFileNode files
    ) {
    }

    /**
     * 온보딩 파일 업로드 응답
     */
    @Schema(description = "온보딩 파일 업로드 응답")
    public record OnboardingUploadResponse(
            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "업로드된 파일 수", example = "3")
            int uploadedFileCount,

            @Schema(description = "업로드된 파일 목록")
            java.util.List<UploadedFileInfo> uploadedFiles,

            @Schema(description = "메시지", example = "3개 파일이 업로드되었습니다.")
            String message
    ) {
    }

    /**
     * 업로드된 파일 정보
     */
    @Schema(description = "업로드된 파일 정보")
    public record UploadedFileInfo(
            @Schema(description = "파일명", example = "init_schema.sql")
            String fileName,

            @Schema(description = "파일 경로", example = "/mariadb/init_schema.sql")
            String path,

            @Schema(description = "파일 크기 (bytes)", example = "1024")
            long size
    ) {
    }

}
