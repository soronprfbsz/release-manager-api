package com.ts.rm.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.auth.dto.SignInRequest;
import com.ts.rm.domain.auth.dto.TokenResponse;
import com.ts.rm.domain.auth.dto.SignUpRequest;
import com.ts.rm.domain.auth.dto.SignUpResponse;
import com.ts.rm.domain.auth.service.AuthService;
import com.ts.rm.domain.refreshtoken.service.RefreshTokenService;
import com.ts.rm.global.exception.GlobalExceptionHandler;
import com.ts.rm.global.config.MessageConfig;
import com.ts.rm.global.logging.service.ApiLogService;
import com.ts.rm.global.security.jwt.JwtTokenProvider;
import com.ts.rm.domain.common.service.CustomUserDetailsService;
import com.ts.rm.global.filter.JwtAuthenticationFilter;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, MessageConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private MessageSource messageSource;

    // Security 관련 MockBean 추가
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ApiLogService apiLogService;

    private SignUpRequest signUpRequest;
    private SignUpResponse signUpResponse;
    private SignInRequest signInRequest;
    private TokenResponse tokenResponse;

    @BeforeEach
    void setUp() {
        signUpRequest = SignUpRequest.builder()
                .email("test@example.com")
                .password("password123!")
                .accountName("홍길동")
                .build();

        signUpResponse = SignUpResponse.builder()
                .accountId(1L)
                .email("test@example.com")
                .accountName("홍길동")
                .role("USER")
                .createdAt(LocalDateTime.now())
                .build();

        signInRequest = SignInRequest.builder()
                .email("test@example.com")
                .password("password123!")
                .build();

        tokenResponse = TokenResponse.builder()
                .accessToken("generated.jwt.token")
                .refreshToken("generated.refresh.token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .refreshExpiresIn(604800L)
                .accountInfo(TokenResponse.AccountInfo.builder()
                        .accountId(1L)
                        .email("test@example.com")
                        .accountName("홍길동")
                        .role("USER")
                        .build())
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/signup - 회원가입 성공")
    void signUp_Success() throws Exception {
        // given
        when(authService.signUp(any(SignUpRequest.class))).thenReturn(signUpResponse);

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accountId").value(1))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.accountName").value("홍길동"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 유효성 검증 실패 (이메일 형식 오류)")
    void signUp_InvalidEmail_ValidationFails() throws Exception {
        // given
        signUpRequest.setEmail("invalid-email");

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 유효성 검증 실패 (비밀번호 길이 부족)")
    void signUp_ShortPassword_ValidationFails() throws Exception {
        // given
        signUpRequest.setPassword("short");

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 유효성 검증 실패 (필수 필드 누락)")
    void signUp_MissingRequiredFields_ValidationFails() throws Exception {
        // given
        SignUpRequest invalidRequest = SignUpRequest.builder().build();

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signin - 로그인 성공")
    void signIn_Success() throws Exception {
        // given
        when(authService.signIn(any(SignInRequest.class))).thenReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signInRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                // Access Token은 Response Body로 전달
                .andExpect(jsonPath("$.data.accessToken").value("generated.jwt.token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                // Refresh Token은 HttpOnly Cookie로 전달 (Response Body에는 없음)
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshExpiresIn").doesNotExist())
                // Cookie 검증
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().value("refreshToken", "generated.refresh.token"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/auth"))
                // Account 정보 검증
                .andExpect(jsonPath("$.data.accountInfo.accountId").value(1))
                .andExpect(jsonPath("$.data.accountInfo.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.accountInfo.accountName").value("홍길동"))
                .andExpect(jsonPath("$.data.accountInfo.role").value("USER"));
    }

    @Test
    @DisplayName("POST /api/auth/signin - 로그인 실패 (잘못된 인증 정보)")
    void signIn_InvalidCredentials_ReturnsUnauthorized() throws Exception {
        // given
        when(authService.signIn(any(SignInRequest.class)))
                .thenThrow(new BadCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다."));

        // when & then
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signInRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/signin - 유효성 검증 실패 (필수 필드 누락)")
    void signIn_MissingRequiredFields_ValidationFails() throws Exception {
        // given
        SignInRequest invalidRequest = SignInRequest.builder().build();

        // when & then
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
