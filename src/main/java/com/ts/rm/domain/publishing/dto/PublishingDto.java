package com.ts.rm.domain.publishing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Publishing DTO
 */
public final class PublishingDto {

    private PublishingDto() {
    }

    /**
     * 퍼블리싱 생성 요청 (ZIP 업로드)
     */
    @Schema(description = "퍼블리싱 생성 요청")
    public record CreateRequest(
            @Schema(description = "퍼블리싱 명", example = "대시보드 v1.0")
            @NotBlank(message = "퍼블리싱 명은 필수입니다")
            String publishingName,

            @Schema(description = "퍼블리싱 설명", example = "Infraeye 2 대시보드 퍼블리싱")
            String description,

            @Schema(description = "카테고리",
                    example = "INFRAEYE2",
                    allowableValues = {"INFRAEYE1", "INFRAEYE2", "COMMON", "ETC"})
            @NotBlank(message = "카테고리는 필수입니다")
            String publishingCategory,

            @Schema(description = "서브 카테고리\n" +
                    "- INFRAEYE1: DASHBOARD, REPORT, ETC\n" +
                    "- INFRAEYE2: DASHBOARD, REPORT, MONITORING, ETC\n" +
                    "- COMMON: COMPONENT, LAYOUT, ETC",
                    example = "DASHBOARD")
            String subCategory,

            @Schema(description = "고객사 ID (커스터마이징인 경우)", example = "1")
            Long customerId,

            @Schema(description = "카드 글리프 텍스트 (1~3자)", example = "UI")
            @Size(max = 3, message = "글리프 텍스트는 3자 이하여야 합니다")
            String glyphText,

            @Schema(description = "카드 글리프 배경 색상 키", example = "mint")
            @Size(max = 30, message = "글리프 배경 색상 키는 30자 이하여야 합니다")
            String glyphBackgroundColor,

            @Schema(description = "생성자 이메일", example = "admin@company.com")
            @NotBlank(message = "생성자 이메일은 필수입니다")
            String createdByEmail
    ) {
    }

    /**
     * 퍼블리싱 수정 요청
     */
    @Schema(description = "퍼블리싱 수정 요청")
    public record UpdateRequest(
            @Schema(description = "퍼블리싱 명", example = "대시보드 v2.0")
            @NotBlank(message = "퍼블리싱 명은 필수입니다")
            String publishingName,

            @Schema(description = "퍼블리싱 설명", example = "Infraeye 2 대시보드 퍼블리싱 (개선)")
            String description,

            @Schema(description = "카테고리",
                    example = "INFRAEYE2",
                    allowableValues = {"INFRAEYE1", "INFRAEYE2", "COMMON", "ETC"})
            @NotBlank(message = "카테고리는 필수입니다")
            String publishingCategory,

            @Schema(description = "서브 카테고리", example = "DASHBOARD")
            String subCategory,

            @Schema(description = "고객사 ID (커스터마이징인 경우)", example = "1")
            Long customerId,

            @Schema(description = "카드 글리프 텍스트 (1~3자, 빈 문자열이면 제거)", example = "UI")
            @Size(max = 3, message = "글리프 텍스트는 3자 이하여야 합니다")
            String glyphText,

            @Schema(description = "카드 글리프 배경 색상 키 (빈 문자열이면 제거)", example = "mint")
            @Size(max = 30, message = "글리프 배경 색상 키는 30자 이하여야 합니다")
            String glyphBackgroundColor,

            @Schema(description = "수정자 이메일 (서버에서 자동 설정)", hidden = true)
            String updatedByEmail
    ) {
    }

    /**
     * 퍼블리싱 상세 응답
     */
    @Schema(description = "퍼블리싱 상세 응답")
    public record DetailResponse(
            @Schema(description = "퍼블리싱 ID", example = "1")
            Long publishingId,

            @Schema(description = "퍼블리싱 명", example = "대시보드 v1.0")
            String publishingName,

            @Schema(description = "퍼블리싱 설명", example = "Infraeye 2 대시보드 퍼블리싱")
            String description,

            @Schema(description = "카테고리", example = "INFRAEYE2")
            String publishingCategory,

            @Schema(description = "서브 카테고리", example = "DASHBOARD")
            String subCategory,

            @Schema(description = "고객사 ID", example = "1")
            Long customerId,

            @Schema(description = "고객사명", example = "A회사")
            String customerName,

            @Schema(description = "정렬 순서", example = "1")
            Integer sortOrder,

            @Schema(description = "카드 글리프 텍스트 (1~3자, null이면 미설정)", example = "UI")
            String glyphText,

            @Schema(description = "카드 글리프 배경 색상 키 (null이면 미설정)", example = "mint")
            String glyphBackgroundColor,

            @Schema(description = "파일 개수", example = "15")
            Integer fileCount,

            @Schema(description = "총 파일 크기 (bytes)", example = "1048576")
            Long totalFileSize,

            @Schema(description = "파일 목록")
            List<PublishingFileDto.SimpleResponse> files,

            @Schema(description = "HTML 파일 목록 (브라우저에서 열 수 있는 파일)")
            List<HtmlFileInfo> htmlFiles,

            @Schema(description = "생성자 이메일", example = "admin@company.com")
            String createdByEmail,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "수정자 이메일", example = "admin@company.com")
            String updatedByEmail,

            @Schema(description = "수정자 이름", example = "김철수")
            String updatedByName,

            @Schema(description = "수정자 탈퇴 여부", example = "false")
            Boolean isDeletedUpdater,

            @Schema(description = "생성일시", example = "2025-12-04T10:30:00")
            LocalDateTime createdAt,

            @Schema(description = "수정일시", example = "2025-12-04T10:30:00")
            LocalDateTime updatedAt
    ) {
    }

    /**
     * HTML 파일 정보 (브라우저에서 열기용)
     */
    @Schema(description = "HTML 파일 정보")
    public record HtmlFileInfo(
            @Schema(description = "파일명", example = "index01.html")
            String fileName,

            @Schema(description = "서빙 URL", example = "/api/publishing/1/serve/index01.html")
            String serveUrl
    ) {
    }

    /**
     * 퍼블리싱 목록 응답 (간략)
     */
    @Schema(description = "퍼블리싱 목록 응답")
    public record SimpleResponse(
            @Schema(description = "퍼블리싱 ID", example = "1")
            Long publishingId,

            @Schema(description = "퍼블리싱 명", example = "대시보드 v1.0")
            String publishingName,

            @Schema(description = "퍼블리싱 설명", example = "Infraeye 2 대시보드 퍼블리싱")
            String description,

            @Schema(description = "카테고리", example = "INFRAEYE2")
            String publishingCategory,

            @Schema(description = "서브 카테고리", example = "DASHBOARD")
            String subCategory,

            @Schema(description = "고객사명", example = "A회사")
            String customerName,

            @Schema(description = "정렬 순서", example = "1")
            Integer sortOrder,

            @Schema(description = "카드 글리프 텍스트 (1~3자, null이면 미설정)", example = "UI")
            String glyphText,

            @Schema(description = "카드 글리프 배경 색상 키 (null이면 미설정)", example = "mint")
            String glyphBackgroundColor,

            @Schema(description = "파일 개수", example = "15")
            Integer fileCount,

            @Schema(description = "HTML 파일 목록 (브라우저에서 열 수 있는 파일)")
            List<HtmlFileInfo> htmlFiles,

            @Schema(description = "생성일시", example = "2025-12-04T10:30:00")
            LocalDateTime createdAt
    ) {
    }

    /**
     * 퍼블리싱 순서 변경 요청
     */
    @Schema(description = "퍼블리싱 순서 변경 요청")
    public record ReorderRequest(
            @Schema(description = "퍼블리싱 카테고리",
                    example = "INFRAEYE2",
                    allowableValues = {"INFRAEYE1", "INFRAEYE2", "COMMON", "ETC"})
            @NotBlank(message = "카테고리는 필수입니다")
            String publishingCategory,

            @Schema(description = "정렬할 퍼블리싱 ID 목록 (순서대로)", example = "[1, 3, 2, 4]")
            @NotEmpty(message = "퍼블리싱 ID 목록은 비어있을 수 없습니다")
            List<Long> publishingIds
    ) {
    }

    /**
     * 카테고리 가이드 응답
     */
    @Schema(description = "퍼블리싱 카테고리 가이드")
    public record CategoryGuideResponse(
            @Schema(description = "카테고리 코드", example = "INFRAEYE2")
            String code,

            @Schema(description = "카테고리 표시명", example = "Infraeye 2")
            String displayName,

            @Schema(description = "카테고리 설명", example = "Infraeye 2 제품 퍼블리싱")
            String description,

            @Schema(description = "하위 카테고리 목록")
            List<SubCategoryInfo> subCategories
    ) {
    }

    /**
     * 하위 카테고리 정보
     */
    @Schema(description = "하위 카테고리 정보")
    public record SubCategoryInfo(
            @Schema(description = "하위 카테고리 코드", example = "DASHBOARD")
            String code,

            @Schema(description = "하위 카테고리 표시명", example = "대시보드")
            String displayName,

            @Schema(description = "하위 카테고리 설명", example = "대시보드 관련 퍼블리싱")
            String description
    ) {
    }

    // ========================================
    // 파일 트리 구조 조회용 DTOs
    // ========================================

    /**
     * 파일 노드 (파일 또는 디렉토리)
     */
    @Schema(description = "파일 노드 (파일 또는 디렉토리)")
    public sealed interface FileNode permits FileInfo, DirectoryNode {
        String name();
        String path();
        String type();
        String filePath();
    }

    /**
     * 파일 정보
     */
    @Schema(description = "파일 정보")
    public record FileInfo(
            @Schema(description = "파일명", example = "style.css")
            String name,

            @Schema(description = "파일 크기 (bytes)", example = "2048")
            long size,

            @Schema(description = "타입", example = "file")
            String type,

            @Schema(description = "경로", example = "css/style.css")
            String path,

            @Schema(description = "다운로드용 파일 경로 (파일인 경우만)")
            String filePath
    ) implements FileNode {
    }

    /**
     * 디렉토리 노드 (재귀 구조)
     */
    @Schema(description = "디렉토리 노드")
    public record DirectoryNode(
            @Schema(description = "디렉토리명", example = "css")
            String name,

            @Schema(description = "타입", example = "directory")
            String type,

            @Schema(description = "경로", example = "css")
            String path,

            @Schema(description = "자식 노드 목록 (파일 또는 디렉토리)")
            List<FileNode> children
    ) implements FileNode {
        public String filePath() {
            return null;
        }
    }

    /**
     * 퍼블리싱 파일 구조 응답
     */
    @Schema(description = "퍼블리싱 파일 구조 응답")
    public record FileStructureResponse(
            @Schema(description = "퍼블리싱 ID", example = "1")
            Long publishingId,

            @Schema(description = "퍼블리싱 이름", example = "대시보드 v1.0")
            String publishingName,

            @Schema(description = "루트 디렉토리")
            DirectoryNode root
    ) {
    }

    /**
     * 파일 내용 응답
     */
    @Schema(description = "파일 내용 응답")
    public record FileContentResponse(
            @Schema(description = "퍼블리싱 ID", example = "1")
            Long publishingId,

            @Schema(description = "파일 경로", example = "css/style.css")
            String path,

            @Schema(description = "파일명", example = "style.css")
            String fileName,

            @Schema(description = "파일 크기 (bytes)", example = "2048")
            long size,

            @Schema(description = "MIME 타입", example = "text/css")
            String mimeType,

            @Schema(description = "바이너리 파일 여부 (true면 content는 Base64 인코딩됨)", example = "false")
            boolean isBinary,

            @Schema(description = "파일 내용 (텍스트 또는 Base64)")
            String content
    ) {
    }
}
