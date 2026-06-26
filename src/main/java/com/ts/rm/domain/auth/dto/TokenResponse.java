package com.ts.rm.domain.auth.dto;

import com.ts.rm.domain.menu.dto.MenuDto.MenuResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 토큰 응답 DTO (Access Token + Refresh Token)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "토큰 응답")
public class TokenResponse {

    @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Schema(description = "토큰 타입", example = "Bearer")
    private String tokenType;

    @Schema(description = "액세스 토큰 만료 시간 (초)", example = "3600")
    private Long expiresIn;

    @Schema(description = "리프레시 토큰 만료 시간 (초)", example = "604800")
    private Long refreshExpiresIn;

    @Schema(description = "계정 정보")
    private AccountInfo accountInfo;

    @Schema(description = "메뉴 목록 (계층 구조)")
    private List<MenuResponse> menus;

    /**
     * 계정 정보 내부 클래스
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "계정 정보")
    public static class AccountInfo {

        @Schema(description = "계정 ID", example = "1")
        private Long accountId;

        @Schema(description = "이메일", example = "user@example.com")
        private String email;

        @Schema(description = "계정 이름", example = "홍길동")
        private String accountName;

        @Schema(description = "권한", example = "USER")
        private String role;

        @Schema(description = "부서 ID", example = "3")
        private Long departmentId;

        @Schema(description = "부서명", example = "개발팀")
        private String departmentName;

        @Schema(description = "직급 코드", example = "MANAGER")
        private String position;

        @Schema(description = "직급명", example = "과장")
        private String positionName;

        @Schema(description = "아바타 스타일", example = "bottts")
        private String avatarStyle;

        @Schema(description = "아바타 시드", example = "default-seed")
        private String avatarSeed;

        @Schema(description = "강제 비밀번호 변경 필요 여부 (임시 비밀번호로 로그인한 상태)", example = "false")
        private boolean mustChangePassword;
    }
}
