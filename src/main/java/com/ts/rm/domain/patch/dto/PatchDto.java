package com.ts.rm.domain.patch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * Patch DTO 통합 클래스
 */
public final class PatchDto {

    private PatchDto() {
    }

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * 패치 생성 요청
     */
    @Builder
    @Schema(description = "패치 생성 요청")
    public record GenerateRequest(
            @Schema(description = "프로젝트 ID", example = "infraeye2")
            @NotBlank(message = "프로젝트 ID는 필수입니다")
            @Size(max = 50, message = "프로젝트 ID는 50자 이하여야 합니다")
            String projectId,

            @Schema(description = "릴리즈 타입", example = "standard")
            @NotBlank(message = "릴리즈 타입은 필수입니다")
            String type,

            @Schema(description = "고객사 ID (커스텀인 경우)", example = "1")
            Long customerId,

            @Schema(description = "시작 버전", example = "1.0.0")
            @NotBlank(message = "시작 버전은 필수입니다")
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전 형식이 올바르지 않습니다 (예: 1.0.0)")
            String fromVersion,

            @Schema(description = "종료 버전", example = "1.1.1")
            @NotBlank(message = "종료 버전은 필수입니다")
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전 형식이 올바르지 않습니다 (예: 1.1.1)")
            String toVersion,

            @Schema(description = "생성자 이메일", example = "admin@tscientific")
            @NotBlank(message = "생성자 이메일은 필수입니다")
            @Size(max = 100, message = "생성자 이메일은 100자 이하여야 합니다")
            String createdByEmail,

            @Schema(description = "설명", example = "1.0.0에서 1.1.1로 업그레이드용 누적 패치")
            String description,

            @Schema(description = "패치 담당자 ID", example = "1")
            Long assigneeId,

            @Schema(description = "패치 이름 (미입력 시 자동 생성)", example = "20251125_1.0.0_1.1.1")
            @Size(max = 100, message = "패치 이름은 100자 이하여야 합니다")
            String patchName,

            @Schema(description = "빌드 파일 선택 (null 또는 enabled=false 면 빌드 미포함)")
            BuildSelection buildSelection
    ) {}

    @Builder
    @Schema(description = "패치에 포함할 빌드 파일 선택")
    public record BuildSelection(
            @Schema(description = "토글 ON 여부 (false 면 빌드 미포함)", example = "true")
            boolean enabled,

            @Schema(description = "WEB 선택 (null 이면 WEB 미포함)")
            SelectedWeb web,

            @Schema(description = "엔진별 선택 (미포함 엔진은 배열에서 제외)")
            java.util.List<SelectedEngine> engines
    ) {}

    @Schema(description = "WEB 빌드 선택")
    public record SelectedWeb(
            @Schema(description = "선택한 빌드 버전 ID", example = "42")
            @jakarta.validation.constraints.NotNull(message = "WEB buildVersionId 는 필수입니다")
            Long buildVersionId
    ) {}

    @Schema(description = "엔진 빌드 선택")
    public record SelectedEngine(
            @Schema(description = "엔진명", example = "NC_SMS")
            @NotBlank(message = "engineName 은 필수입니다")
            String engineName,

            @Schema(description = "선택한 빌드 버전 ID", example = "42")
            @jakarta.validation.constraints.NotNull(message = "engine buildVersionId 는 필수입니다")
            Long buildVersionId
    ) {}

    // ========================================
    // Response DTOs
    // ========================================

    @Schema(description = "패치에 실제 포함된 빌드 정보 (응답)")
    public record IncludedBuilds(
            @Schema(description = "WEB 포함 정보 (없으면 null)")
            IncludedWeb web,

            @Schema(description = "엔진별 포함 정보")
            java.util.List<IncludedEngine> engines
    ) {}

    @Schema(description = "패치에 포함된 WEB 빌드 정보")
    public record IncludedWeb(
            @Schema(description = "빌드 버전 ID", example = "42")
            Long buildVersionId,

            @Schema(description = "전체 버전 문자열", example = "1.1.0.260428")
            String fullVersion
    ) {}

    @Schema(description = "패치에 포함된 엔진 빌드 정보")
    public record IncludedEngine(
            @Schema(description = "엔진명", example = "NC_SMS")
            String engineName,

            @Schema(description = "빌드 버전 ID", example = "42")
            Long buildVersionId,

            @Schema(description = "전체 버전 문자열", example = "1.1.0.260428")
            String fullVersion
    ) {}

    @Schema(description = "범위 안의 핫픽스 메타정보 (응답)")
    public record HotfixInRangeInfo(
            @Schema(description = "핫픽스 버전 ID", example = "33")
            Long versionId,

            @Schema(description = "전체 버전 문자열", example = "1.0.0.1")
            String fullVersion
    ) {}

    /**
     * 패치 생성 응답.
     * <p>Task 10 에서 PatchController 가 본 record 를 응답으로 반환하도록 연결될 예정.
     */
    @Schema(description = "패치 생성 응답")
    public record GenerateResponse(
            @Schema(description = "생성된 패치 ID", example = "1")
            Long patchId,

            @Schema(description = "패치 이름")
            String patchName,

            @Schema(description = "출력 경로")
            String outputPath,

            @Schema(description = "Build-only 패치 여부 (from == to)", example = "false")
            boolean isBuildOnly,

            @Schema(description = "범위 안의 핫픽스 (별도 적용 안내용, 비어있으면 빈 배열)")
            java.util.List<HotfixInRangeInfo> hotfixesInRange,

            @Schema(description = "패치에 실제 포함된 빌드 정보")
            IncludedBuilds includedBuilds
    ) {}

    /**
     * 패치 일괄 삭제 결과
     */
    @Schema(description = "패치 일괄 삭제 결과")
    public record BatchDeleteResponse(
            @Schema(description = "삭제된 패치 수", example = "3")
            int deletedCount,

            @Schema(description = "메시지", example = "3개 패치가 삭제되었습니다.")
            String message
    ) {
    }

    /**
     * 패치 상세 응답
     */
    @Schema(description = "패치 상세 응답")
    public record DetailResponse(
            @Schema(description = "패치 ID", example = "1")
            Long patchId,

            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "프로젝트명", example = "Infraeye 2")
            String projectName,

            @Schema(description = "릴리즈 타입", example = "standard")
            String releaseType,

            @Schema(description = "고객사 코드", example = "company_a")
            String customerCode,

            @Schema(description = "고객사명", example = "A 회사")
            String customerName,

            @Schema(description = "시작 버전", example = "1.0.0")
            String fromVersion,

            @Schema(description = "종료 버전", example = "1.1.1")
            String toVersion,

            @Schema(description = "패치 이름", example = "202511271430_1.0.0_1.1.1")
            String patchName,

            @Schema(description = "출력 경로", example = "patches/202511271430_1.0.0_1.1.1")
            String outputPath,

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

            @Schema(description = "설명", example = "1.0.0에서 1.1.1로 업그레이드용 누적 패치")
            String description,

            @Schema(description = "패치 담당자 ID", example = "1")
            Long assigneeId,

            @Schema(description = "패치 담당자 이름", example = "홍길동")
            String assigneeName,

            @Schema(description = "등록일시")
            LocalDateTime createdAt,

            @Schema(description = "수정일시")
            LocalDateTime updatedAt,

            @Schema(description = "Build-only 패치 여부", example = "false")
            Boolean isBuildOnly,

            @Schema(description = "빌드 포함 여부", example = "true")
            Boolean isBuildIncluded,

            @Schema(description = "포함된 빌드 정보 (빈 객체 가능)")
            IncludedBuilds includedBuilds,

            @Schema(description = "범위 안의 핫픽스 (별도 적용 안내용, 비어있으면 빈 배열)")
            java.util.List<HotfixInRangeInfo> hotfixesInRange
    ) {

    }

    /**
     * 패치 간단 응답
     */
    @Schema(description = "패치 간단 응답")
    public record SimpleResponse(
            @Schema(description = "패치 ID", example = "1")
            Long patchId,

            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "릴리즈 타입", example = "standard")
            String releaseType,

            @Schema(description = "고객사 코드", example = "company_a")
            String customerCode,

            @Schema(description = "고객사명", example = "A 회사")
            String customerName,

            @Schema(description = "시작 버전", example = "1.0.0")
            String fromVersion,

            @Schema(description = "종료 버전", example = "1.1.1")
            String toVersion,

            @Schema(description = "패치 이름", example = "20251125_1.0.0_1.1.1")
            String patchName,

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

            @Schema(description = "설명", example = "1.0.0에서 1.1.1로 업그레이드용 누적 패치")
            String description,

            @Schema(description = "패치 담당자 ID", example = "1")
            Long assigneeId,

            @Schema(description = "패치 담당자 이름", example = "홍길동")
            String assigneeName,

            @Schema(description = "등록일시")
            LocalDateTime createdAt
    ) {

    }

    /**
     * 패치 목록 응답 (페이징용)
     */
    @Schema(description = "패치 목록 응답")
    public record ListResponse(
            @Schema(description = "행 번호", example = "1")
            Long rowNumber,

            @Schema(description = "패치 ID", example = "1")
            Long patchId,

            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "릴리즈 타입", example = "standard")
            String releaseType,

            @Schema(description = "고객사 코드", example = "company_a")
            String customerCode,

            @Schema(description = "고객사명", example = "A 회사")
            String customerName,

            @Schema(description = "시작 버전", example = "1.0.0")
            String fromVersion,

            @Schema(description = "종료 버전", example = "1.1.1")
            String toVersion,

            @Schema(description = "패치 이름", example = "20251125_1.0.0_1.1.1")
            String patchName,

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

            @Schema(description = "설명", example = "1.0.0에서 1.1.1로 업그레이드용 누적 패치")
            String description,

            @Schema(description = "패치 담당자 ID", example = "1")
            Long assigneeId,

            @Schema(description = "패치 담당자 이름", example = "홍길동")
            String assigneeName,

            @Schema(description = "등록일시")
            LocalDateTime createdAt,

            @Schema(description = "수정일시")
            LocalDateTime updatedAt,

            @Schema(description = "Build-only 패치 여부", example = "false")
            Boolean isBuildOnly,

            @Schema(description = "빌드 포함 여부", example = "true")
            Boolean isBuildIncluded,

            @Schema(description = "포함된 빌드 요약 (예: 'WEB · NC_SMS · NC_FAULT_MS', 빌드 미포함 시 null)", example = "WEB · NC_SMS · NC_FAULT_MS")
            String includedBuildsSummary
    ) {

    }

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
            @Schema(description = "파일명", example = "1.patch_mariadb.sql")
            String name,

            @Schema(description = "파일 크기 (bytes)", example = "1024")
            long size,

            @Schema(description = "타입", example = "file")
            String type,

            @Schema(description = "경로", example = "mariadb/source_files/1.1.0/1.patch_mariadb.sql")
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
            @Schema(description = "디렉토리명", example = "mariadb")
            String name,

            @Schema(description = "타입", example = "directory")
            String type,

            @Schema(description = "경로", example = "mariadb/source_files")
            String path,

            @Schema(description = "자식 노드 목록 (파일 또는 디렉토리)")
            java.util.List<FileNode> children
    ) implements FileNode {
        public String filePath() {
            return null;
        }
    }

    /**
     * 패치 파일 구조 응답
     */
    @Schema(description = "패치 파일 구조 응답")
    public record FileStructureResponse(
            @Schema(description = "패치 ID", example = "1")
            Long patchId,

            @Schema(description = "패치 이름", example = "patch_1.0.0_to_1.1.0")
            String patchName,

            @Schema(description = "루트 디렉토리")
            DirectoryNode root
    ) {

    }

    /**
     * 파일 내용 응답
     */
    @Schema(description = "파일 내용 응답")
    public record FileContentResponse(
            @Schema(description = "패치 ID", example = "1")
            Long patchId,

            @Schema(description = "파일 경로", example = "mariadb/source_files/1.1.1/1.patch_mariadb_ddl.sql")
            String path,

            @Schema(description = "파일명", example = "1.patch_mariadb_ddl.sql")
            String fileName,

            @Schema(description = "파일 크기 (bytes)", example = "4867")
            long size,

            @Schema(description = "MIME 타입", example = "text/x-sql")
            String mimeType,

            @Schema(description = "바이너리 파일 여부 (true면 content는 Base64 인코딩됨)", example = "false")
            boolean isBinary,

            @Schema(description = "파일 내용 (텍스트 또는 Base64)")
            String content
    ) {

    }

    // ========================================
    // 커스텀 패치용 DTOs
    // ========================================

    /**
     * 커스텀 패치 생성 요청
     */
    @Builder
    @Schema(description = "커스텀 패치 생성 요청")
    public record GenerateCustomPatchRequest(
            @Schema(description = "프로젝트 ID", example = "infraeye2")
            @NotBlank(message = "프로젝트 ID는 필수입니다")
            @Size(max = 50, message = "프로젝트 ID는 50자 이하여야 합니다")
            String projectId,

            @Schema(description = "고객사 ID", example = "1")
            @jakarta.validation.constraints.NotNull(message = "고객사 ID는 필수입니다")
            Long customerId,

            @Schema(description = "시작 버전 (베이스 버전 또는 커스텀 버전)", example = "1.1.0-companyA.1.0.0")
            @NotBlank(message = "시작 버전은 필수입니다")
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9_]+\\.\\d+\\.\\d+\\.\\d+)?$",
                    message = "버전 형식이 올바르지 않습니다 (예: 1.1.0 또는 1.1.0-companyA.1.0.0)")
            String fromVersion,

            @Schema(description = "종료 커스텀 버전", example = "1.1.0-companyA.1.0.2")
            @NotBlank(message = "종료 버전은 필수입니다")
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+-[a-zA-Z0-9_]+\\.\\d+\\.\\d+\\.\\d+$",
                    message = "버전 형식이 올바르지 않습니다 (예: 1.1.0-companyA.1.0.0)")
            String toVersion,

            @Schema(description = "생성자 이메일", example = "admin@tscientific")
            @NotBlank(message = "생성자 이메일은 필수입니다")
            @Size(max = 100, message = "생성자는 100자 이하여야 합니다")
            String createdByEmail,

            @Schema(description = "설명", example = "A사 1.0.0에서 1.0.2로 업그레이드용 커스텀 패치")
            String description,

            @Schema(description = "패치 담당자 ID", example = "1")
            Long assigneeId,

            @Schema(description = "패치 이름 (미입력 시 자동 생성)", example = "20251225_1.0.0_1.0.2")
            @Size(max = 100, message = "패치 이름은 100자 이하여야 합니다")
            String patchName
    ) {

    }

    /**
     * 커스텀 버전 보유 고객사 응답
     */
    @Schema(description = "커스텀 버전 보유 고객사")
    public record CustomerWithCustomVersions(
            @Schema(description = "고객사 ID", example = "1")
            Long customerId,

            @Schema(description = "고객사 코드", example = "companyA")
            String customerCode,

            @Schema(description = "고객사명", example = "A 회사")
            String customerName
    ) {

    }

    /**
     * 커스텀 버전 셀렉트 옵션
     */
    @Schema(description = "커스텀 버전 셀렉트 옵션")
    public record CustomVersionSelectOption(
            @Schema(description = "버전 ID", example = "101")
            Long versionId,

            @Schema(description = "버전 번호 (커스텀 버전 또는 베이스 버전)", example = "1.0.0")
            String version,

            @Schema(description = "승인 여부", example = "true")
            Boolean isApproved,

            @Schema(description = "베이스 버전 여부 (true: 표준 베이스 버전, false: 커스텀 버전)", example = "false")
            Boolean isBaseVersion
    ) {

    }
}
