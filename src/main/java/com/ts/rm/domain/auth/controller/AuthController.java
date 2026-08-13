package com.ts.rm.domain.auth.controller;

import com.ts.rm.domain.account.dto.AccountDto;
import com.ts.rm.domain.account.service.AccountService;
import com.ts.rm.domain.auth.dto.AccessTokenResponse;
import com.ts.rm.domain.auth.dto.PasswordResetRequest;
import com.ts.rm.domain.auth.dto.SignInRequest;
import com.ts.rm.domain.auth.dto.SignUpRequest;
import com.ts.rm.domain.auth.dto.SignUpResponse;
import com.ts.rm.domain.auth.dto.TokenResponse;
import com.ts.rm.domain.auth.service.AuthService;
import com.ts.rm.domain.message.service.AccountRequestService;
import com.ts.rm.domain.refreshtoken.service.RefreshTokenService;
import com.ts.rm.global.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 API 컨트롤러
 * - Access Token: Response Body로 반환
 * - Refresh Token: HttpOnly Cookie로 반환 (XSS 방어)
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AccountService accountService;
    private final AccountRequestService accountRequestService;

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean secureCookie;

    /**
     * 회원가입 API
     *
     * @param request 회원가입 요청 DTO
     * @return 회원가입 응답 DTO
     */
    @Override
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        log.info("Sign up request received for email: {}", request.getEmail());
        SignUpResponse response = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * 로그인 API
     * - Access Token: Response Body
     * - Refresh Token: HttpOnly Cookie
     *
     * @param request 로그인 요청 DTO
     * @param response HTTP 응답 (Cookie 설정용)
     * @return AccessTokenResponse (Access Token만 포함)
     */
    @Override
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> signIn(
            @Valid @RequestBody SignInRequest request,
            HttpServletResponse response) {
        log.info("Sign in request received for email: {}", request.getEmail());

        TokenResponse tokenResponse = authService.signIn(request);

        // Refresh Token을 HttpOnly Cookie로 설정
        setRefreshTokenCookie(response, tokenResponse.getRefreshToken());

        // Access Token만 Response Body로 반환
        AccessTokenResponse accessTokenResponse = AccessTokenResponse.from(tokenResponse);
        return ResponseEntity.ok(ApiResponse.success(accessTokenResponse));
    }

    /**
     * Access Token 갱신 API
     * - Refresh Token을 Cookie에서 읽어서 검증
     * - 새로운 Access Token: Response Body
     * - 새로운 Refresh Token: HttpOnly Cookie
     *
     * @param refreshToken Cookie에서 읽은 Refresh Token
     * @param response HTTP 응답 (Cookie 설정용)
     * @return 새로운 AccessTokenResponse
     */
    @Override
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refreshToken(
            @CookieValue(name = "refreshToken", required = true) String refreshToken,
            HttpServletResponse response) {
        log.info("Token refresh request received");

        TokenResponse tokenResponse = refreshTokenService.refreshAccessToken(refreshToken);

        // 새로운 Refresh Token을 HttpOnly Cookie로 설정
        setRefreshTokenCookie(response, tokenResponse.getRefreshToken());

        // Access Token만 Response Body로 반환
        AccessTokenResponse accessTokenResponse = AccessTokenResponse.from(tokenResponse);
        return ResponseEntity.ok(ApiResponse.success(accessTokenResponse));
    }

    /**
     * 로그아웃 API
     * - Cookie에서 Refresh Token을 읽어서 무효화
     * - Refresh Token Cookie 삭제
     *
     * @param refreshToken Cookie에서 읽은 Refresh Token
     * @param response HTTP 응답 (Cookie 삭제용)
     * @return 로그아웃 완료 메시지
     */
    @Override
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        log.info("Logout request received");

        if (refreshToken != null) {
            // Refresh Token DB에서 삭제
            authService.logout(refreshToken);
        }

        // Refresh Token Cookie 삭제
        deleteRefreshTokenCookie(response);

        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "로그아웃되었습니다.")));
    }

    /**
     * 비밀번호 재설정 안내용 관리자 연락처 조회 API
     *
     * <p>비인증 공개 엔드포인트 — /api/auth/** 는 SecurityConfig에서 이미 permitAll 처리됨.
     */
    @Override
    @GetMapping("/admins")
    public ResponseEntity<ApiResponse<List<AccountDto.AdminContactResponse>>> getAdminContacts() {
        log.info("GET /api/auth/admins");

        List<AccountDto.AdminContactResponse> response = accountService.getAdminContacts();

        log.info("관리자 연락처 조회 완료 - count: {}", response.size());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 비밀번호 재설정 요청 API
     *
     * <p>비인증 공개 엔드포인트. 계정 존재 여부·쿨다운 중복 여부와 무관하게 항상 같은
     * 응답을 준다 — 응답이 갈리면 그 자체가 계정 존재 신호가 된다.
     */
    @Override
    @PostMapping("/password-reset-requests")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        log.info("POST /api/auth/password-reset-requests");

        accountRequestService.requestPasswordReset(
                request.getEmail(), request.getMemo(), request.getRecipientAccountIds());

        return ResponseEntity.ok(
                ApiResponse.success(Map.of("message", "요청이 접수되었습니다.")));
    }

    /**
     * Refresh Token을 HttpOnly Cookie로 설정하는 헬퍼 메서드
     *
     * @param response HTTP 응답
     * @param refreshToken Refresh Token 값
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);  // XSS 공격 방어
        cookie.setSecure(secureCookie);  // HTTPS only (프로필별 설정)
        cookie.setPath("/api/auth");  // Cookie 사용 경로 제한
        cookie.setMaxAge((int) (refreshTokenExpirationMs / 1000));  // 만료 시간 (초)
        // SameSite=Lax는 Spring Boot 2.6+ 기본값

        response.addCookie(cookie);
        log.debug("Refresh token cookie set with maxAge: {} seconds", cookie.getMaxAge());
    }

    /**
     * Refresh Token Cookie를 삭제하는 헬퍼 메서드
     *
     * @param response HTTP 응답
     */
    private void deleteRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);  // 즉시 만료

        response.addCookie(cookie);
        log.debug("Refresh token cookie deleted");
    }
}
