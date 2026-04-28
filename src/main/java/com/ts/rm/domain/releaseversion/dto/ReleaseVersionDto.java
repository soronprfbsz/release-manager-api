package com.ts.rm.domain.releaseversion.dto;

import com.ts.rm.domain.releasefile.dto.ReleaseFileDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * ReleaseVersion DTO 통합 클래스
 */
public final class ReleaseVersionDto {

    private ReleaseVersionDto() {
    }

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * 릴리즈 버전 생성 요청
     */
    @Builder
    @Schema(description = "릴리즈 버전 생성 요청")
    public record CreateRequest(
            @Schema(description = "프로젝트 ID", example = "infraeye2") @NotBlank(message = "프로젝트 ID는 필수입니다") @Size(max = 50, message = "프로젝트 ID는 50자 이하여야 합니다")
            String projectId,

            @Schema(description = "버전 (예: 1.1.0)", example = "1.1.0") @NotBlank(message = "버전은 필수입니다") @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전 형식이 올바르지 않습니다 (예: 1.1.0)")
            String version,

            @Schema(description = "생성자 이메일", example = "jhlee@tscientific") @NotBlank(message = "생성자는 필수입니다") @Size(max = 100, message = "생성자는 100자 이하여야 합니다")
            String createdByEmail,

            @Schema(description = "버전 코멘트", example = "새로운 기능 추가")
            String comment,

            @Schema(description = "승인 여부", example = "false", defaultValue = "false")
            Boolean isApproved,

            @Schema(description = "고객사 ID (커스텀 릴리즈인 경우)", example = "1")
            Long customerId,

            @Schema(description = "커스텀 메이저 버전 (커스텀 릴리즈인 경우)", example = "1")
            Integer customMajorVersion,

            @Schema(description = "커스텀 마이너 버전 (커스텀 릴리즈인 경우)", example = "0")
            Integer customMinorVersion,

            @Schema(description = "커스텀 패치 버전 (커스텀 릴리즈인 경우)", example = "0")
            Integer customPatchVersion
    ) {

    }

    /**
     * 릴리즈 버전 수정 요청
     */
    @Builder
    @Schema(description = "릴리즈 버전 수정 요청")
    public record UpdateRequest(
            @Schema(description = "버전 코멘트", example = "수정된 코멘트")
            String comment
    ) {

    }

    // ========================================
    // Response DTOs
    // ========================================

    /**
     * 릴리즈 버전 상세 응답
     */
    @Schema(description = "릴리즈 버전 상세 응답")
    public record DetailResponse(
            @Schema(description = "릴리즈 버전 ID", example = "1")
            Long releaseVersionId,

            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "프로젝트명", example = "Infraeye 2")
            String projectName,

            @Schema(description = "릴리즈 타입", example = "standard")
            String releaseType,

            @Schema(description = "고객사 코드 (커스텀인 경우)", example = "company_a")
            String customerCode,

            @Schema(description = "버전", example = "1.1.0")
            String version,

            @Schema(description = "메이저 버전", example = "1")
            Integer majorVersion,

            @Schema(description = "마이너 버전", example = "1")
            Integer minorVersion,

            @Schema(description = "패치 버전", example = "0")
            Integer patchVersion,

            @Schema(description = "핫픽스 버전 (0이면 일반 버전)", example = "0")
            Integer hotfixVersion,

            @Schema(description = "핫픽스 여부", example = "false")
            Boolean isHotfix,

            @Schema(description = "빌드 버전 (0이면 일반, 1+=빌드, 예: 260427)", example = "0")
            Integer buildVersion,

            @Schema(description = "빌드 여부", example = "false")
            Boolean isBuild,

            @Schema(description = "전체 버전 (핫픽스/빌드 포함, 예: 1.1.0 또는 1.1.0.1 또는 1.1.0.260427)", example = "1.1.0")
            String fullVersion,

            @Schema(description = "메이저.마이너", example = "1.1.x")
            String majorMinor,

            @Schema(description = "생성자 이메일", example = "jhlee@tscientific")
            String createdByEmail,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 아바타 스타일", example = "lorelei")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123")
            String createdByAvatarSeed,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "코멘트", example = "새로운 기능 추가")
            String comment,

            @Schema(description = "승인 여부", example = "false")
            Boolean isApproved,

            @Schema(description = "승인자 이메일", example = "admin@tscientific.co.kr")
            String approvedBy,

            @Schema(description = "승인자 이름", example = "김관리자")
            String approvedByName,

            @Schema(description = "승인자 탈퇴 여부", example = "false")
            Boolean isDeletedApprover,

            @Schema(description = "승인일시")
            LocalDateTime approvedAt,

            @Schema(description = "커스텀 메이저 버전", example = "1")
            Integer customMajorVersion,

            @Schema(description = "커스텀 마이너 버전", example = "0")
            Integer customMinorVersion,

            @Schema(description = "커스텀 패치 버전", example = "0")
            Integer customPatchVersion,

            @Schema(description = "커스텀 버전 (조합)", example = "1.0.0")
            String customVersion,

            @Schema(description = "기준 표준 버전 ID (커스텀인 경우)", example = "2")
            Long customBaseVersionId,

            @Schema(description = "기준 표준 버전 번호 (커스텀인 경우)", example = "1.1.0")
            String customBaseVersion,

            @Schema(description = "핫픽스 원본 버전 ID (핫픽스인 경우)", example = "5")
            Long hotfixBaseVersionId,

            @Schema(description = "핫픽스 원본 버전 번호 (핫픽스인 경우)", example = "1.1.0")
            String hotfixBaseVersion,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "릴리즈 파일 목록")
            List<ReleaseFileDto.SimpleResponse> releaseFiles
    ) {

    }

    /**
     * 릴리즈 버전 간단 응답
     */
    @Schema(description = "릴리즈 버전 간단 응답")
    public record SimpleResponse(
            @Schema(description = "릴리즈 버전 ID", example = "1")
            Long releaseVersionId,

            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "릴리즈 타입", example = "standard")
            String releaseType,

            @Schema(description = "고객사 코드", example = "company_a")
            String customerCode,

            @Schema(description = "버전", example = "1.1.0")
            String version,

            @Schema(description = "핫픽스 버전 (0이면 일반 버전)", example = "0")
            Integer hotfixVersion,

            @Schema(description = "핫픽스 여부", example = "false")
            Boolean isHotfix,

            @Schema(description = "빌드 버전 (0이면 일반, 1+=빌드, 예: 260427)", example = "0")
            Integer buildVersion,

            @Schema(description = "빌드 여부", example = "false")
            Boolean isBuild,

            @Schema(description = "전체 버전 (핫픽스/빌드 포함)", example = "1.1.0")
            String fullVersion,

            @Schema(description = "메이저.마이너", example = "1.1.x")
            String majorMinor,

            @Schema(description = "생성자 이메일", example = "jhlee@tscientific")
            String createdByEmail,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 아바타 스타일", example = "lorelei")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123")
            String createdByAvatarSeed,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "코멘트", example = "새로운 기능 추가")
            String comment,

            @Schema(description = "승인 여부", example = "false")
            Boolean isApproved,

            @Schema(description = "승인자 이메일", example = "admin@tscientific.co.kr")
            String approvedBy,

            @Schema(description = "승인자 이름", example = "김관리자")
            String approvedByName,

            @Schema(description = "승인자 탈퇴 여부", example = "false")
            Boolean isDeletedApprover,

            @Schema(description = "승인일시")
            LocalDateTime approvedAt,

            @Schema(description = "포함된 파일 카테고리 목록", example = "[\"DATABASE\", \"WEB\", \"ENGINE\", \"ETC\"]")
            List<String> fileCategories,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "패치 파일 개수", example = "5")
            Integer patchFileCount
    ) {

    }

    /**
     * 릴리즈 버전 트리 응답 (프론트엔드 트리 렌더링용)
     */
    @Schema(description = "릴리즈 버전 트리 응답")
    public record TreeResponse(
            @Schema(description = "릴리즈 타입", example = "STANDARD")
            String releaseType,

            @Schema(description = "고객사 코드 (커스텀인 경우)", example = "company_a")
            String customerCode,

            @Schema(description = "메이저.마이너 그룹 목록")
            List<MajorMinorNode> majorMinorGroups
    ) {

    }

    /**
     * 메이저.마이너 버전 그룹 노드 (예: 1.1.x)
     */
    @Schema(description = "메이저.마이너 버전 그룹 노드")
    public record MajorMinorNode(
            @Schema(description = "메이저.마이너", example = "1.1.x")
            String majorMinor,

            @Schema(description = "해당 메이저.마이너에 속한 버전 목록")
            List<VersionNode> versions
    ) {

    }

    /**
     * 버전 노드 (예: 1.1.0) - 일반 버전용
     */
    @Schema(description = "버전 노드")
    public record VersionNode(
            @Schema(description = "버전 ID", example = "1")
            Long versionId,

            @Schema(description = "버전", example = "1.1.0")
            String version,

            @Schema(description = "생성일시", example = "2025-11-20T14:30:00")
            String createdAt,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 이메일", example = "jhlee@tscientific")
            String createdByEmail,

            @Schema(description = "생성자 아바타 스타일", example = "lorelei")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123")
            String createdByAvatarSeed,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "코멘트", example = "새로운 기능 추가")
            String comment,

            @Schema(description = "승인 여부", example = "false")
            Boolean isApproved,

            @Schema(description = "승인자 이메일", example = "admin@tscientific.co.kr")
            String approvedBy,

            @Schema(description = "승인자 이름", example = "김관리자")
            String approvedByName,

            @Schema(description = "승인자 아바타 스타일", example = "lorelei")
            String approvedByAvatarStyle,

            @Schema(description = "승인자 아바타 시드", example = "xyz789")
            String approvedByAvatarSeed,

            @Schema(description = "승인자 탈퇴 여부", example = "false")
            Boolean isDeletedApprover,

            @Schema(description = "승인일시")
            String approvedAt,

            @Schema(description = "포함된 파일 카테고리 목록", example = "[\"DATABASE\", \"WEB\", \"ENGINE\", \"ETC\"]")
            List<String> fileCategories,

            @Schema(description = "핫픽스 버전 목록")
            List<HotfixNode> hotfixes,

            @Schema(description = "빌드 버전 목록")
            List<BuildNode> builds
    ) {

    }

    /**
     * 빌드 노드 (예: 1.1.0.260427) - 빌드는 자식을 가질 수 없음
     */
    @Schema(description = "빌드 노드")
    public record BuildNode(
            @Schema(description = "버전 ID", example = "30")
            Long versionId,

            @Schema(description = "빌드 버전 번호", example = "260427")
            Integer buildVersion,

            @Schema(description = "전체 버전 (빌드 포함)", example = "1.1.0.260427")
            String fullVersion,

            @Schema(description = "생성일시", example = "2026-04-27T10:30:00")
            String createdAt,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 이메일", example = "jhlee@tscientific")
            String createdByEmail,

            @Schema(description = "생성자 아바타 스타일")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드")
            String createdByAvatarSeed,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "코멘트", example = "WEB 빌드 업데이트")
            String comment,

            @Schema(description = "승인 여부 (빌드는 항상 true)", example = "true")
            Boolean isApproved,

            @Schema(description = "포함된 파일 카테고리 목록", example = "[\"WEB\", \"ENGINE\"]")
            List<String> fileCategories
    ) {

    }

    /**
     * 핫픽스 노드 (예: 1.1.0.1) - 핫픽스는 자식을 가질 수 없음
     */
    @Schema(description = "핫픽스 노드")
    public record HotfixNode(
            @Schema(description = "버전 ID", example = "25")
            Long versionId,

            @Schema(description = "핫픽스 버전 번호", example = "1")
            Integer hotfixVersion,

            @Schema(description = "전체 버전 (핫픽스 포함)", example = "1.1.0.1")
            String fullVersion,

            @Schema(description = "생성일시", example = "2025-11-20T14:30:00")
            String createdAt,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 이메일", example = "jhlee@tscientific")
            String createdByEmail,

            @Schema(description = "생성자 아바타 스타일", example = "lorelei")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123")
            String createdByAvatarSeed,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "코멘트", example = "긴급 버그 수정")
            String comment,

            @Schema(description = "승인 여부", example = "false")
            Boolean isApproved,

            @Schema(description = "승인자 이메일", example = "admin@tscientific.co.kr")
            String approvedBy,

            @Schema(description = "승인자 이름", example = "김관리자")
            String approvedByName,

            @Schema(description = "승인자 아바타 스타일", example = "lorelei")
            String approvedByAvatarStyle,

            @Schema(description = "승인자 아바타 시드", example = "xyz789")
            String approvedByAvatarSeed,

            @Schema(description = "승인자 탈퇴 여부", example = "false")
            Boolean isDeletedApprover,

            @Schema(description = "승인일시")
            String approvedAt,

            @Schema(description = "포함된 파일 카테고리 목록", example = "[\"DATABASE\", \"WEB\"]")
            List<String> fileCategories
    ) {

    }

    /**
     * 데이터베이스 노드 (예: MARIADB, CRATEDB)
     */
    @Schema(description = "데이터베이스 노드")
    public record DatabaseNode(
            @Schema(description = "데이터베이스 타입", example = "MARIADB")
            String databaseType,

            @Schema(description = "SQL 파일 목록")
            List<String> files
    ) {

    }

    /**
     * 커스텀 릴리즈 버전 전체 트리 응답 (고객사별 그룹화)
     */
    @Schema(description = "커스텀 릴리즈 버전 전체 트리 응답")
    public record CustomTreeResponse(
            @Schema(description = "릴리즈 타입", example = "CUSTOM")
            String releaseType,

            @Schema(description = "고객사별 커스텀 버전 목록")
            List<CustomerNode> customers
    ) {

    }

    /**
     * 고객사 노드 (커스텀 트리의 최상위 노드)
     */
    @Schema(description = "고객사 노드")
    public record CustomerNode(
            @Schema(description = "고객사 ID", example = "1")
            Long customerId,

            @Schema(description = "고객사 코드", example = "companyA")
            String customerCode,

            @Schema(description = "고객사명", example = "A회사")
            String customerName,

            @Schema(description = "기준 표준본 버전 ID", example = "5")
            Long customBaseVersionId,

            @Schema(description = "기준 표준본 버전", example = "1.1.0")
            String customBaseVersion,

            @Schema(description = "커스텀 메이저.마이너 그룹 목록")
            List<CustomMajorMinorNode> majorMinorGroups
    ) {

    }

    /**
     * 커스텀 메이저.마이너 버전 그룹 노드 (예: 1.0.x)
     */
    @Schema(description = "커스텀 메이저.마이너 버전 그룹 노드")
    public record CustomMajorMinorNode(
            @Schema(description = "커스텀 메이저.마이너", example = "1.0.x")
            String majorMinor,

            @Schema(description = "해당 메이저.마이너에 속한 커스텀 버전 목록")
            List<CustomVersionNode> versions
    ) {

    }

    /**
     * 커스텀 버전 노드
     */
    @Schema(description = "커스텀 버전 노드")
    public record CustomVersionNode(
            @Schema(description = "버전 ID", example = "101")
            Long versionId,

            @Schema(description = "커스텀 버전", example = "1.0.0")
            String version,

            @Schema(description = "생성일시", example = "2025-12-01T14:30:00")
            String createdAt,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 이메일", example = "jhlee@tscientific")
            String createdByEmail,

            @Schema(description = "생성자 아바타 스타일", example = "lorelei")
            String createdByAvatarStyle,

            @Schema(description = "생성자 아바타 시드", example = "abc123")
            String createdByAvatarSeed,

            @Schema(description = "생성자 탈퇴 여부", example = "false")
            Boolean isDeletedCreator,

            @Schema(description = "코멘트", example = "A사 커스텀 패치")
            String comment,

            @Schema(description = "승인 여부", example = "false")
            Boolean isApproved,

            @Schema(description = "승인자 이메일", example = "admin@tscientific.co.kr")
            String approvedBy,

            @Schema(description = "승인자 이름", example = "김관리자")
            String approvedByName,

            @Schema(description = "승인자 아바타 스타일", example = "lorelei")
            String approvedByAvatarStyle,

            @Schema(description = "승인자 아바타 시드", example = "xyz789")
            String approvedByAvatarSeed,

            @Schema(description = "승인자 탈퇴 여부", example = "false")
            Boolean isDeletedApprover,

            @Schema(description = "승인일시")
            String approvedAt,

            @Schema(description = "포함된 파일 카테고리 목록", example = "[\"DATABASE\", \"WEB\"]")
            List<String> fileCategories,

            @Schema(description = "핫픽스 버전 목록")
            List<HotfixNode> hotfixes,

            @Schema(description = "빌드 버전 목록")
            List<BuildNode> builds
    ) {

    }

    /**
     * 표준 릴리즈 버전 생성 요청 (Multipart Form Data)
     */
    @Builder
    @Schema(description = "표준 릴리즈 버전 생성 요청")
    public record CreateStandardVersionRequest(
            @Schema(description = "프로젝트 ID", example = "infraeye2", required = true)
            @NotBlank(message = "프로젝트 ID는 필수입니다")
            @Size(max = 50, message = "프로젝트 ID는 50자 이하여야 합니다")
            String projectId,

            @Schema(description = "버전 (예: 1.1.3)", example = "1.1.3", required = true)
            @NotBlank(message = "버전은 필수입니다")
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전 형식이 올바르지 않습니다 (예: 1.1.3)")
            String version,

            @Schema(description = "패치 노트 내용", example = "새로운 기능 추가", required = true)
            @NotBlank(message = "패치 노트 내용은 필수입니다")
            @Size(max = 500, message = "패치 노트 내용은 500자 이하여야 합니다")
            String comment,

            @Schema(description = "승인 여부 (true: 승인됨, false: 미승인)", example = "false", defaultValue = "false")
            Boolean isApproved
    ) {

    }

    /**
     * 커스텀 릴리즈 버전 생성 요청 (Multipart Form Data)
     */
    @Builder
    @Schema(description = "커스텀 릴리즈 버전 생성 요청")
    public record CreateCustomVersionRequest(
            @Schema(description = "프로젝트 ID", example = "infraeye2", required = true)
            @NotBlank(message = "프로젝트 ID는 필수입니다")
            @Size(max = 50, message = "프로젝트 ID는 50자 이하여야 합니다")
            String projectId,

            @Schema(description = "고객사 ID", example = "1", required = true)
            @NotNull(message = "고객사 ID는 필수입니다")
            Long customerId,

            @Schema(description = "기준 표준 버전 ID (파생 원본). 해당 고객사의 최초 커스텀 버전 생성 시 필수", example = "2")
            Long customBaseVersionId,

            @Schema(description = "커스텀 버전 (예: 1.0.0)", example = "1.0.0", required = true)
            @NotBlank(message = "커스텀 버전은 필수입니다")
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전 형식이 올바르지 않습니다 (예: 1.0.0)")
            String customVersion,

            @Schema(description = "패치 노트 내용", example = "A사 커스텀 패치", required = true)
            @NotBlank(message = "패치 노트 내용은 필수입니다")
            @Size(max = 500, message = "패치 노트 내용은 500자 이하여야 합니다")
            String comment,

            @Schema(description = "승인 여부 (true: 승인됨, false: 미승인)", example = "false", defaultValue = "false")
            Boolean isApproved
    ) {

    }

    /**
     * 파일 트리 노드 (디렉토리 또는 파일)
     */
    @Schema(description = "파일 트리 노드")
    public record FileTreeNode(
            @Schema(description = "파일/디렉토리 이름", example = "mariadb")
            String name,

            @Schema(description = "상대 경로 (UI 표시용)", example = "database/MARIADB")
            String path,

            @Schema(description = "타입 (file 또는 directory)", example = "directory")
            String type,

            @Schema(description = "파일 크기 (파일인 경우만)", example = "1024")
            Long size,

            @Schema(description = "릴리즈 파일 ID (파일인 경우만)", example = "123")
            Long releaseFileId,

            @Schema(description = "다운로드용 파일 경로 (파일인 경우만)", example = "versions/infraeye2/standard/1.1.x/1.1.0/database/MARIADB/file.sql")
            String filePath,

            @Schema(description = "하위 노드 (디렉토리인 경우만)")
            List<FileTreeNode> children
    ) {
        // 디렉토리 노드 생성
        public static FileTreeNode directory(String name, String path, List<FileTreeNode> children) {
            return new FileTreeNode(name, path, "directory", null, null, null, children);
        }

        // 파일 노드 생성
        public static FileTreeNode file(String name, String path, Long size, Long releaseFileId, String filePath) {
            return new FileTreeNode(name, path, "file", size, releaseFileId, filePath, null);
        }
    }

    /**
     * 릴리즈 버전 파일 트리 응답
     */
    @Schema(description = "릴리즈 버전 파일 트리 응답")
    public record FileTreeResponse(
            @Schema(description = "릴리즈 버전 ID", example = "1")
            Long releaseVersionId,

            @Schema(description = "버전", example = "1.1.0")
            String version,

            @Schema(description = "파일 트리 루트")
            FileTreeNode files
    ) {
    }

    /**
     * 릴리즈 버전 생성 응답
     */
    @Schema(description = "릴리즈 버전 생성 응답")
    public record CreateVersionResponse(
            @Schema(description = "릴리즈 버전 ID", example = "5")
            Long releaseVersionId,

            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "버전", example = "1.1.3")
            String version,

            @Schema(description = "메이저 버전", example = "1")
            Integer majorVersion,

            @Schema(description = "마이너 버전", example = "1")
            Integer minorVersion,

            @Schema(description = "패치 버전", example = "3")
            Integer patchVersion,

            @Schema(description = "핫픽스 버전 (0이면 일반 버전)", example = "0")
            Integer hotfixVersion,

            @Schema(description = "전체 버전 (핫픽스 포함)", example = "1.1.3")
            String fullVersion,

            @Schema(description = "메이저.마이너", example = "1.1.x")
            String majorMinor,

            @Schema(description = "생성자 이메일", example = "admin@tscientific")
            String createdByEmail,

            @Schema(description = "패치 노트 내용", example = "새로운 기능 추가")
            String comment,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "생성된 파일 목록 (상대 경로)")
            List<String> filesCreated
    ) {

    }

    /**
     * 커스텀 릴리즈 버전 생성 응답
     */
    @Schema(description = "커스텀 릴리즈 버전 생성 응답")
    public record CreateCustomVersionResponse(
            @Schema(description = "릴리즈 버전 ID", example = "5")
            Long releaseVersionId,

            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "고객사 코드", example = "customerA")
            String customerCode,

            @Schema(description = "고객사명", example = "A회사")
            String customerName,

            @Schema(description = "기준 표준 버전 ID", example = "2")
            Long customBaseVersionId,

            @Schema(description = "기준 표준 버전 번호", example = "1.1.0")
            String customBaseVersion,

            @Schema(description = "커스텀 메이저 버전", example = "1")
            Integer customMajorVersion,

            @Schema(description = "커스텀 마이너 버전", example = "0")
            Integer customMinorVersion,

            @Schema(description = "커스텀 패치 버전", example = "0")
            Integer customPatchVersion,

            @Schema(description = "커스텀 버전 (조합)", example = "1.0.0")
            String customVersion,

            @Schema(description = "커스텀 메이저.마이너", example = "1.0.x")
            String customMajorMinor,

            @Schema(description = "생성자 이메일", example = "admin@tscientific")
            String createdByEmail,

            @Schema(description = "패치 노트 내용", example = "A사 커스텀 패치")
            String comment,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "생성된 파일 목록 (상대 경로)")
            List<String> filesCreated
    ) {

    }

    /**
     * 셀렉트박스용 버전 옵션 (커스텀 버전의 기준 표준본 선택용)
     */
    @Schema(description = "셀렉트박스용 버전 옵션")
    public record VersionSelectOption(
            @Schema(description = "버전 ID", example = "5")
            Long versionId,

            @Schema(description = "버전", example = "1.1.0")
            String version,

            @Schema(description = "승인 여부", example = "true")
            Boolean isApproved
    ) {

    }

    // ========================================
    // Hotfix DTOs
    // ========================================

    /**
     * 핫픽스 생성 요청 (Multipart Form Data)
     */
    @Builder
    @Schema(description = "핫픽스 생성 요청")
    public record CreateHotfixRequest(
            @Schema(description = "패치 노트 내용", example = "특정 버그 수정", required = true)
            @NotBlank(message = "패치 노트 내용은 필수입니다")
            @Size(max = 500, message = "패치 노트 내용은 500자 이하여야 합니다")
            String comment,

            @Schema(description = "담당 엔지니어 ID (선택, 패치 스크립트의 기본 담당자로 사용)", example = "5")
            Long engineerId,

            @Schema(description = "승인 여부 (기본값: false)", example = "true")
            Boolean isApproved
    ) {
        /**
         * 승인 여부 반환 (null인 경우 false 반환)
         */
        public boolean isApprovedOrDefault() {
            return isApproved != null && isApproved;
        }
    }

    /**
     * 핫픽스 생성 응답
     */
    @Schema(description = "핫픽스 생성 응답")
    public record CreateHotfixResponse(
            @Schema(description = "릴리즈 버전 ID", example = "25")
            Long releaseVersionId,

            @Schema(description = "프로젝트 ID", example = "infraeye2")
            String projectId,

            @Schema(description = "핫픽스 원본 버전 ID", example = "15")
            Long hotfixBaseVersionId,

            @Schema(description = "핫픽스 원본 버전", example = "1.3.2")
            String hotfixBaseVersion,

            @Schema(description = "메이저 버전", example = "1")
            Integer majorVersion,

            @Schema(description = "마이너 버전", example = "3")
            Integer minorVersion,

            @Schema(description = "패치 버전", example = "2")
            Integer patchVersion,

            @Schema(description = "핫픽스 버전", example = "1")
            Integer hotfixVersion,

            @Schema(description = "전체 버전 (핫픽스 포함)", example = "1.3.2.1")
            String fullVersion,

            @Schema(description = "메이저.마이너", example = "1.3.x")
            String majorMinor,

            @Schema(description = "생성자 이메일", example = "admin@tscientific")
            String createdByEmail,

            @Schema(description = "패치 노트 내용", example = "특정 버그 수정")
            String comment,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "생성된 파일 목록 (상대 경로)")
            List<String> filesCreated
    ) {

    }

    /**
     * 핫픽스 목록 조회 응답 (특정 버전의 핫픽스들)
     */
    @Schema(description = "핫픽스 목록 조회 응답")
    public record HotfixListResponse(
            @Schema(description = "핫픽스 원본 버전 ID", example = "15")
            Long hotfixBaseVersionId,

            @Schema(description = "핫픽스 원본 버전", example = "1.3.2")
            String hotfixBaseVersion,

            @Schema(description = "핫픽스 목록")
            List<HotfixItem> hotfixes
    ) {

    }

    /**
     * 핫픽스 항목
     */
    @Schema(description = "핫픽스 항목")
    public record HotfixItem(
            @Schema(description = "핫픽스 버전 ID", example = "25")
            Long releaseVersionId,

            @Schema(description = "핫픽스 버전 번호", example = "1")
            Integer hotfixVersion,

            @Schema(description = "전체 버전", example = "1.3.2.1")
            String fullVersion,

            @Schema(description = "생성일시", example = "2025-12-01T14:30:00")
            String createdAt,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "생성자 이메일", example = "jhlee@tscientific")
            String createdByEmail,

            @Schema(description = "코멘트", example = "특정 버그 수정")
            String comment,

            @Schema(description = "승인 여부", example = "false")
            Boolean isApproved,

            @Schema(description = "포함된 파일 카테고리 목록", example = "[\"DATABASE\", \"WEB\"]")
            List<String> fileCategories
    ) {

    }

    // ========================================
    // Build DTOs
    // ========================================

    /**
     * 빌드 버전 생성 요청 (Multipart Form Data)
     * <p>build_version 이 null 이면 서버가 오늘 날짜 YYMMDD 로 채웁니다 (Phase 2 에서 처리).
     */
    @Builder
    @Schema(description = "빌드 버전 생성 요청")
    public record CreateBuildRequest(
            @Schema(description = "패치 노트 내용", example = "WEB 빌드 업데이트", required = true)
            @NotBlank(message = "패치 노트 내용은 필수입니다")
            @Size(max = 500, message = "패치 노트 내용은 500자 이하여야 합니다")
            String comment,

            @Schema(description = "빌드 버전 번호 (YYMMDD, 예: 260427). null 이면 서버가 오늘 날짜로 자동 채움", example = "260427")
            Integer buildVersion
    ) {
    }

    /**
     * 빌드 버전 생성 응답
     */
    @Schema(description = "빌드 버전 생성 응답")
    public record CreateBuildResponse(
            @Schema(description = "빌드 버전 ID (신규 release_version_id)", example = "30")
            Long buildVersionId,

            @Schema(description = "기준 버전 (예: 1.1.0)", example = "1.1.0")
            String version,

            @Schema(description = "빌드 버전 번호 (예: 260427)", example = "260427")
            Integer buildVersion,

            @Schema(description = "전체 버전 (예: 1.1.0.260427)", example = "1.1.0.260427")
            String fullVersion,

            @Schema(description = "업로드된 파일 개수 (ZIP 미동봉 시 0)", example = "5")
            Integer uploadedFileCount
    ) {

    }

    /**
     * 빌드 목록 조회 응답 (특정 버전의 빌드들)
     */
    @Schema(description = "빌드 목록 조회 응답")
    public record BuildListResponse(
            @Schema(description = "기준 버전 ID", example = "15")
            Long releaseVersionId,

            @Schema(description = "기준 버전 (예: 1.1.0)", example = "1.1.0")
            String version,

            @Schema(description = "빌드 목록")
            List<BuildItem> builds
    ) {

    }

    /**
     * 빌드 항목
     */
    @Schema(description = "빌드 항목")
    public record BuildItem(
            @Schema(description = "빌드 버전 ID (release_version_id)", example = "30")
            Long buildVersionId,

            @Schema(description = "빌드 버전 번호 (예: 260427)", example = "260427")
            Integer buildVersion,

            @Schema(description = "기준 버전 (예: 1.1.0)", example = "1.1.0")
            String version,

            @Schema(description = "전체 버전 (예: 1.1.0.260427)", example = "1.1.0.260427")
            String fullVersion,

            @Schema(description = "승인 여부", example = "true")
            Boolean isApproved,

            @Schema(description = "생성일시", example = "2026-04-27T10:30:00")
            String createdAt,

            @Schema(description = "생성자 이메일", example = "jhlee@tscientific")
            String createdByEmail,

            @Schema(description = "생성자 이름", example = "홍길동")
            String createdByName,

            @Schema(description = "코멘트", example = "WEB 빌드 업데이트")
            String comment
    ) {

    }

    /**
     * 빌드 ZIP 재업로드 응답 (기존 빌드의 파일을 교체)
     */
    @Schema(description = "빌드 ZIP 재업로드 응답")
    public record UploadBuildZipResponse(
            @Schema(description = "빌드 버전 ID", example = "30")
            Long buildVersionId,

            @Schema(description = "전체 버전 (예: 1.1.0.260427)", example = "1.1.0.260427")
            String fullVersion,

            @Schema(description = "업로드된 파일 개수", example = "5")
            Integer uploadedFileCount
    ) {

    }

    // ========================================
    // builds-in-range Response DTOs
    // ========================================

    @Schema(description = "빌드 후보 1건")
    public record BuildCandidate(
            @Schema(description = "빌드 버전 ID", example = "42")
            Long buildVersionId,

            @Schema(description = "전체 버전 문자열", example = "1.1.0.260428")
            String fullVersion,

            @Schema(description = "생성 시각")
            LocalDateTime createdAt,

            @Schema(description = "후보 그룹 안에서 가장 최신인지 여부", example = "true")
            boolean isLatest
    ) {}

    @Schema(description = "엔진 후보 그룹")
    public record EngineGroup(
            @Schema(description = "엔진명 (engine/{engineName} 디렉토리명, 직속 파일이면 UNKNOWN)", example = "NC_SMS")
            String engineName,

            @Schema(description = "이 엔진을 가진 빌드 후보들")
            List<BuildCandidate> candidates
    ) {}

    @Schema(description = "범위 안의 핫픽스 메타정보")
    public record HotfixInRangeInfo(
            @Schema(description = "핫픽스 버전 ID", example = "33")
            Long versionId,

            @Schema(description = "전체 버전 문자열", example = "1.0.0.1")
            String fullVersion,

            @Schema(description = "핫픽스 버전 (4번째 자리)", example = "1")
            Integer hotfixVersion
    ) {}

    @Schema(description = "패치 범위 안의 빌드 후보 응답")
    public record BuildsInRangeResponse(
            @Schema(description = "WEB 후보 (없으면 빈 배열)")
            List<BuildCandidate> web,

            @Schema(description = "엔진별 후보 그룹 (엔진명 기준 정렬)")
            List<EngineGroup> engines,

            @Schema(description = "범위 안의 핫픽스 메타정보 (없으면 빈 배열)")
            List<HotfixInRangeInfo> hotfixesInRange
    ) {}
}
