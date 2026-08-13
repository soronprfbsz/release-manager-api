package com.ts.rm.domain.auth.controller;

import com.ts.rm.domain.account.dto.AccountDto;
import com.ts.rm.domain.auth.dto.AccessTokenResponse;
import com.ts.rm.domain.auth.dto.PasswordResetRequest;
import com.ts.rm.domain.auth.dto.SignInRequest;
import com.ts.rm.domain.auth.dto.SignUpRequest;
import com.ts.rm.domain.auth.dto.SignUpResponse;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AuthController Swagger 문서화 인터페이스
 */
@Tag(name = "인증", description = "회원가입, 로그인, 토큰 갱신 API")
@SwaggerResponse
public interface AuthControllerDocs {

    @Operation(
            summary = "회원가입",
            description = "새로운 계정을 생성합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "생성됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SignUpApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<SignUpResponse>> signUp(
            @RequestBody SignUpRequest request
    );

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호로 로그인합니다. Access Token은 응답 본문에, Refresh Token은 HttpOnly Cookie로 전달됩니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccessTokenApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<AccessTokenResponse>> signIn(
            @RequestBody SignInRequest request,
            HttpServletResponse response
    );

    @Operation(
            summary = "토큰 갱신",
            description = "Cookie의 Refresh Token을 사용하여 새로운 Access Token과 Refresh Token을 발급받습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccessTokenApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<AccessTokenResponse>> refreshToken(
            @CookieValue(name = "refreshToken", required = true) String refreshToken,
            HttpServletResponse response
    );

    @Operation(
            summary = "로그아웃",
            description = "Cookie의 Refresh Token을 무효화하고 로그아웃합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    example = "{\"status\": \"success\", \"data\": {\"message\": \"로그아웃되었습니다.\"}}"
                            )
                    )
            )
    )
    ResponseEntity<ApiResponse<Map<String, String>>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    );

    @Operation(
            summary = "비밀번호 재설정 안내용 관리자 연락처 조회",
            description = "비인증 공개 API. 활성 상태인 ADMIN/OPERATOR 계정의 연락처(이름·이메일·부서·역할)를 반환합니다. 민감 정보(비밀번호, 전화번호 등)는 포함되지 않습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminContactListApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<List<AccountDto.AdminContactResponse>>> getAdminContacts();

    @Operation(
            summary = "비밀번호 재설정 요청",
            description = "비인증 공개 API. 선택한 담당자(ADMIN/OPERATOR)에게 비밀번호 재설정 요청 메시지를 발송합니다. "
                    + "계정 열거 방지를 위해 등록되지 않은 이메일이거나 쿨다운(기본 10분) 중이어도 동일한 성공 응답을 반환합니다."
    )
    ResponseEntity<ApiResponse<Map<String, String>>> requestPasswordReset(
            @RequestBody PasswordResetRequest request);

    /**
     * Swagger 스키마용 wrapper 클래스 - 회원가입 응답
     */
    @Schema(description = "회원가입 API 응답")
    class SignUpApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "회원가입 정보")
        public SignUpResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - Access Token 응답
     */
    @Schema(description = "Access Token API 응답")
    class AccessTokenApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "Access Token 정보")
        public AccessTokenResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 관리자 연락처 목록 응답
     */
    @Schema(description = "관리자 연락처 목록 API 응답")
    class AdminContactListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "관리자 연락처 목록")
        public List<AccountDto.AdminContactResponse> data;
    }
}
