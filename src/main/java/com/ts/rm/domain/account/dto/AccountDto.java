package com.ts.rm.domain.account.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Account DTO 통합 클래스
 * - Request/Response DTO를 내부 클래스로 통합 관리
 * - Request: 상단 배치
 * - Response: 하단 배치
 */
public final class AccountDto {

    private AccountDto() {
    }

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * 계정 생성 요청
     */
    @Builder
    @Schema(description = "계정 생성 요청")
    public record CreateRequest(
            @Schema(description = "이메일", example = "account@example.com")
            @NotBlank(message = "이메일은 필수입니다")
            @Email(message = "올바른 이메일 형식이 아닙니다")
            String email,

            @Schema(description = "비밀번호 (8~100자)", example = "password1234")
            @NotBlank(message = "비밀번호는 필수입니다")
            @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다")
            String password,

            @Schema(description = "이름 (2~50자)", example = "홍길동")
            @NotBlank(message = "이름은 필수입니다")
            @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다")
            String accountName,

            @Schema(description = "연락처", example = "010-1234-5678")
            @Size(max = 20, message = "연락처는 20자 이하여야 합니다")
            String phone,

            @Schema(description = "직급 코드 (POSITION 코드)", example = "MANAGER")
            @Size(max = 100, message = "직급 코드는 100자 이하여야 합니다")
            String position,

            @Schema(description = "부서 ID", example = "2")
            Long departmentId,

            @Schema(description = "아바타 스타일 (DiceBear 스타일명)", example = "adventurer")
            @Size(max = 50, message = "아바타 스타일은 50자 이하여야 합니다")
            String avatarStyle,

            @Schema(description = "아바타 시드 (랜덤 문자열)", example = "abc123xyz")
            @Size(max = 100, message = "아바타 시드는 100자 이하여야 합니다")
            String avatarSeed,

            @Schema(description = "계정 권한", example = "USER", defaultValue = "USER")
            String role,

            @Schema(description = "계정 상태", example = "ACTIVE", defaultValue = "ACTIVE")
            String status
    ) {
        public CreateRequest {
            if (role == null || role.isBlank()) {
                role = "USER";
            }
            if (status == null || status.isBlank()) {
                status = "ACTIVE";
            }
        }
    }

    /**
     * 계정 수정 요청 (본인 정보 수정용)
     *
     * <p>비밀번호는 별도 엔드포인트({@code POST /api/accounts/me/password})로 분리됨.
     * 현재 비밀번호 검증 없는 변경 경로를 폐쇄하기 위해 password 필드를 제거했다.
     */
    @Builder
    @Schema(description = "계정 수정 요청")
    public record UpdateRequest(
            @Schema(description = "이름 (2~50자)", example = "홍길동")
            @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다")
            String accountName,

            @Schema(description = "연락처", example = "010-1234-5678")
            @Size(max = 20, message = "연락처는 20자 이하여야 합니다")
            String phone,

            @Schema(description = "직급 코드 (POSITION 코드)", example = "MANAGER")
            @Size(max = 100, message = "직급 코드는 100자 이하여야 합니다")
            String position,

            @Schema(description = "아바타 스타일 (DiceBear 스타일명)", example = "adventurer")
            @Size(max = 50, message = "아바타 스타일은 50자 이하여야 합니다")
            String avatarStyle,

            @Schema(description = "아바타 시드 (랜덤 문자열)", example = "abc123xyz")
            @Size(max = 100, message = "아바타 시드는 100자 이하여야 합니다")
            String avatarSeed
    ) {
    }

    /**
     * 계정 수정 요청 (ADMIN용 - 이름, 권한, 상태, 부서, 직급 수정)
     *
     * <p>부서 변경 방식:
     * <ul>
     *   <li>departmentId / unassignDepartment 둘 다 미전송: 부서 변경 없음</li>
     *   <li>unassignDepartment = true: 부서 배치 해제</li>
     *   <li>departmentId = 5: 해당 부서로 배치</li>
     * </ul>
     * <p>Optional 대신 명시적 플래그를 쓰는 이유: Jackson 의 Jdk8Module 은 JSON 키
     * 미전송(absent)과 명시적 null 을 모두 Optional.empty 로 처리하여 "변경 없음"과
     * "배치 해제"를 구분할 수 없기 때문.
     */
    @Builder
    @Schema(description = "계정 수정 요청 (ADMIN 전용)")
    public record AdminUpdateRequest(
            @Schema(description = "이름 (2~50자)", example = "홍길동")
            @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다")
            String accountName,

            @Schema(description = "연락처", example = "010-1234-5678")
            @Size(max = 20, message = "연락처는 20자 이하여야 합니다")
            String phone,

            @Schema(description = "직급 코드 (POSITION 코드)", example = "MANAGER")
            @Size(max = 100, message = "직급 코드는 100자 이하여야 합니다")
            String position,

            @Schema(description = "배치할 부서 ID (지정 시 해당 부서로 배치, 미전송 시 변경 없음)", example = "2")
            Long departmentId,

            @Schema(description = "부서 배치 해제 여부 (true 시 미배치 상태로)", example = "false")
            Boolean unassignDepartment,

            @Schema(description = "권한 (ADMIN, USER)", example = "USER")
            String role,

            @Schema(description = "상태 (ACTIVE, INACTIVE)", example = "ACTIVE")
            String status
    ) {
    }

    /**
     * 일괄 부서 이동 요청 (ADMIN 전용)
     */
    @Schema(description = "일괄 부서 이동 요청")
    public record BatchTransferDepartmentRequest(
            @Schema(description = "이동할 계정 ID 목록", example = "[1, 2, 3]")
            @NotEmpty(message = "계정 ID 목록은 비어있을 수 없습니다")
            List<Long> accountIds,

            @Schema(description = "대상 부서 ID (null이면 부서 배치 해제)", example = "5")
            Long targetDepartmentId
    ) {
    }

    /**
     * 비밀번호 변경 요청 (본인 자가 변경 / 강제 변경 게이트 공용)
     *
     * <p>현재 비밀번호를 입력해 본인이 알고 있음을 증명한 뒤에만 변경된다.
     * 비밀번호 정책(8~64자)은 서버에서도 검증한다(프론트 검증 우회 방지).
     */
    @Builder
    @Schema(description = "비밀번호 변경 요청")
    public record ChangePasswordRequest(
            @Schema(description = "현재 비밀번호", example = "currentPw123")
            @NotBlank(message = "현재 비밀번호는 필수입니다")
            String currentPassword,

            @Schema(description = "새 비밀번호 (8~64자)", example = "newPassword123")
            @NotBlank(message = "새 비밀번호는 필수입니다")
            @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다")
            String newPassword
    ) {
    }

    // ========================================
    // Response DTOs
    // ========================================

    /**
     * 계정 상세 응답
     */
    @Schema(description = "계정 상세 응답")
    public record DetailResponse(
            @Schema(description = "계정 ID", example = "1")
            Long accountId,

            @Schema(description = "이메일", example = "account@example.com")
            String email,

            @Schema(description = "이름", example = "홍길동")
            String accountName,

            @Schema(description = "연락처", example = "010-1234-5678")
            String phone,

            @Schema(description = "직급 코드", example = "MANAGER")
            String position,

            @Schema(description = "직급명", example = "과장")
            String positionName,

            @Schema(description = "부서 ID", example = "2")
            Long departmentId,

            @Schema(description = "부서명", example = "인프라기술팀")
            String departmentName,

            @Schema(description = "아바타 스타일 (DiceBear 스타일명)", example = "adventurer")
            String avatarStyle,

            @Schema(description = "아바타 시드 (랜덤 문자열)", example = "abc123xyz")
            String avatarSeed,

            @Schema(description = "권한", example = "USER")
            String role,

            @Schema(description = "상태", example = "ACTIVE")
            String status,

            @Schema(description = "강제 비밀번호 변경 필요 여부 (임시 비밀번호로 로그인한 상태)", example = "false")
            boolean mustChangePassword,

            @Schema(description = "생성일시")
            LocalDateTime createdAt,

            @Schema(description = "수정일시")
            LocalDateTime updatedAt
    ) {
    }

    /**
     * 일괄 부서 이동 결과
     */
    @Schema(description = "일괄 부서 이동 결과")
    public record BatchTransferDepartmentResponse(
            @Schema(description = "이동된 계정 수", example = "3")
            int transferredCount,

            @Schema(description = "대상 부서 ID", example = "5")
            Long targetDepartmentId,

            @Schema(description = "대상 부서명", example = "개발1팀")
            String targetDepartmentName,

            @Schema(description = "메시지", example = "3개 계정이 개발1팀으로 이동되었습니다.")
            String message
    ) {
    }

    /**
     * 비밀번호 초기화 응답 (임시 비밀번호 1회 노출)
     *
     * <p>평문 임시 비밀번호는 이 응답에서만 1회 노출되며 어디에도 저장되지 않는다.
     * 응답 본문이 로그에 남지 않도록 주의한다.
     */
    @Schema(description = "비밀번호 초기화 응답")
    public record ResetPasswordResponse(
            @Schema(description = "임시 비밀번호 (1회성, 재조회 불가)", example = "Kp7@xQ9mR3$z")
            String temporaryPassword
    ) {
    }

    /**
     * 비밀번호 재설정 안내용 관리자 연락처 응답 (비인증 공개 API)
     *
     * <p>accountId 는 비밀번호 재설정 요청 시 담당자를 선택하기 위해 노출된다. 그 외
     * 민감 필드(password, phone, position, status 등)는 절대 포함하지 않는다.
     */
    @Schema(description = "관리자 연락처 응답")
    public record AdminContactResponse(
            @Schema(description = "계정 ID", example = "1")
            Long accountId,

            @Schema(description = "부서명 (부서 없으면 '부서 없음')", example = "인프라기술팀")
            String departmentName,

            @Schema(description = "이름", example = "홍길동")
            String accountName,

            @Schema(description = "이메일", example = "admin@example.com")
            String email,

            @Schema(description = "권한 (ADMIN | OPERATOR)", example = "ADMIN")
            String role
    ) {
    }

    /**
     * 계정 간단 응답
     */
    @Schema(description = "계정 간단 응답")
    public record SimpleResponse(
            @Schema(description = "계정 ID", example = "1")
            Long accountId,

            @Schema(description = "이메일", example = "account@example.com")
            String email,

            @Schema(description = "이름", example = "홍길동")
            String accountName,

            @Schema(description = "부서명", example = "인프라기술팀")
            String departmentName,

            @Schema(description = "상태", example = "ACTIVE")
            String status
    ) {
    }

    /**
     * 계정 목록 응답 (페이징용)
     */
    @Schema(description = "계정 목록 응답")
    public record ListResponse(
            @Schema(description = "행 번호", example = "1")
            Long rowNumber,

            @Schema(description = "계정 ID", example = "1")
            Long accountId,

            @Schema(description = "이메일", example = "account@example.com")
            String email,

            @Schema(description = "이름", example = "홍길동")
            String accountName,

            @Schema(description = "연락처", example = "010-1234-5678")
            String phone,

            @Schema(description = "직급 코드", example = "MANAGER")
            String position,

            @Schema(description = "직급명", example = "과장")
            String positionName,

            @Schema(description = "부서 ID", example = "2")
            Long departmentId,

            @Schema(description = "부서명", example = "인프라기술팀")
            String departmentName,

            @Schema(description = "아바타 스타일 (DiceBear 스타일명)", example = "adventurer")
            String avatarStyle,

            @Schema(description = "아바타 시드 (랜덤 문자열)", example = "abc123xyz")
            String avatarSeed,

            @Schema(description = "권한", example = "USER")
            String role,

            @Schema(description = "상태", example = "ACTIVE")
            String status,

            @Schema(description = "마지막 로그인 일시")
            LocalDateTime lastLoginAt,

            @Schema(description = "생성일시")
            LocalDateTime createdAt
    ) {
    }
}
