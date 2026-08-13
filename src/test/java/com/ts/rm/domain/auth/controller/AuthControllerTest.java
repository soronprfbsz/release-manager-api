package com.ts.rm.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ts.rm.domain.account.dto.AccountDto;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.account.service.AccountService;
import com.ts.rm.domain.auth.dto.PasswordResetRequest;
import com.ts.rm.domain.auth.dto.SignInRequest;
import com.ts.rm.domain.auth.dto.TokenResponse;
import com.ts.rm.domain.auth.dto.SignUpRequest;
import com.ts.rm.domain.auth.dto.SignUpResponse;
import com.ts.rm.domain.auth.service.AuthService;
import com.ts.rm.domain.message.service.AccountRequestService;
import com.ts.rm.domain.refreshtoken.service.RefreshTokenService;
import com.ts.rm.global.exception.GlobalExceptionHandler;
import com.ts.rm.global.config.MessageConfig;
import com.ts.rm.global.logging.service.ApiLogService;
import com.ts.rm.global.security.jwt.JwtTokenProvider;
import com.ts.rm.domain.common.service.CustomUserDetailsService;
import com.ts.rm.global.filter.JwtAuthenticationFilter;
import java.time.LocalDateTime;
import java.util.List;
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

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private AccountRequestService accountRequestService;

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
                .recipientAccountIds(List.of(1L))
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
    @DisplayName("POST /api/auth/signup - 유효성 검증 실패 (recipientAccountIds 누락)")
    void signUp_MissingRecipientAccountIds_ValidationFails() throws Exception {
        // given
        signUpRequest.setRecipientAccountIds(null);

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 유효성 검증 실패 (recipientAccountIds 원소가 null)")
    void signUp_RecipientAccountIdsWithNullElement_ValidationFails() throws Exception {
        // given
        String body = """
                {
                  "email": "test@example.com",
                  "password": "password123!",
                  "accountName": "홍길동",
                  "recipientAccountIds": [null]
                }""";

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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

    @Test
    @DisplayName("GET /api/auth/admins - ADMIN+OPERATOR 활성 계정 반환 (부서 있음)")
    void getAdminContacts_ReturnsList() throws Exception {
        // given
        List<AccountDto.AdminContactResponse> contacts = List.of(
                new AccountDto.AdminContactResponse(1L, "인프라기술팀", "김관리자", "admin@example.com", "ADMIN"),
                new AccountDto.AdminContactResponse(2L, "운영팀", "이운영자", "operator@example.com", "OPERATOR")
        );
        when(accountService.getAdminContacts()).thenReturn(contacts);

        // when & then
        mockMvc.perform(get("/api/auth/admins"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].accountId").value(1))
                .andExpect(jsonPath("$.data[0].departmentName").value("인프라기술팀"))
                .andExpect(jsonPath("$.data[0].accountName").value("김관리자"))
                .andExpect(jsonPath("$.data[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$.data[0].role").value("ADMIN"))
                // 민감 필드 미노출 검증 (accountId 는 담당자 선택용으로 의도적으로 노출됨)
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].phone").doesNotExist())
                .andExpect(jsonPath("$.data[0].position").doesNotExist())
                .andExpect(jsonPath("$.data[0].status").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/admins - 부서 없는 계정은 '부서 없음' 반환")
    void getAdminContacts_NoDepartment_ReturnsDepartmentNone() throws Exception {
        // given
        List<AccountDto.AdminContactResponse> contacts = List.of(
                new AccountDto.AdminContactResponse(3L, "부서 없음", "박관리자", "admin2@example.com", "ADMIN")
        );
        when(accountService.getAdminContacts()).thenReturn(contacts);

        // when & then
        mockMvc.perform(get("/api/auth/admins"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].departmentName").value("부서 없음"));
    }

    @Test
    @DisplayName("GET /api/auth/admins - 활성 관리자 없으면 빈 배열 반환")
    void getAdminContacts_NoActiveAdmins_ReturnsEmptyList() throws Exception {
        // given
        when(accountService.getAdminContacts()).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/auth/admins"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /**
     * 이 슬라이스({@code @WebMvcTest} + {@code excludeAutoConfiguration =
     * SecurityAutoConfiguration.class} + {@code addFilters = false})는 보안 필터가
     * 전부 꺼져 있어 어떤 엔드포인트든 인증 없이 200을 준다. 즉 이 테스트는 "공개
     * 접근성" 자체를 입증하지 못한다 — 그 근거는 {@code SecurityConfig} 의
     * {@code /api/auth/**} permitAll 이며, 필터가 살아 있는 별도 슬라이스가 있어야
     * 진짜 접근성을 검증할 수 있다. 이 테스트가 실제로 고정하는 것은 요청이 성공적으로
     * 처리되면 200과 접수 메시지를 반환한다는 것뿐이다.
     */
    @Test
    @DisplayName("요청이 접수되면 200과 접수 메시지를 반환한다")
    void requestPasswordReset_returnsAcceptedMessage() throws Exception {
        String body = """
                {
                  "email": "user@test.com",
                  "memo": "내선 1234",
                  "recipientAccountIds": [1, 2]
                }""";

        mockMvc.perform(post("/api/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("요청이 접수되었습니다."));
    }

    /**
     * {@code AccountRequestService} 가 목이라 "미등록 이메일"이 실제로 모델링되지는
     * 않는다 — 이 테스트가 고정하는 것은 컨트롤러가 입력값에 따라 분기하지 않는다는 것과
     * 응답 본문의 결정성이다. "미등록이어도 예외 없이 조용히 반환한다"는 서비스 계층
     * 통합 테스트({@code AccountRequestServiceTest})가 담당한다.
     */
    @Test
    @DisplayName("컨트롤러는 입력값에 따라 분기하지 않아 응답이 항상 동일하다")
    void requestPasswordReset_doesNotBranchOnInput_returnsIdenticalResponse() throws Exception {
        String knownBody = """
                {"email": "user@test.com", "recipientAccountIds": [1]}""";
        String unknownBody = """
                {"email": "nobody@test.com", "recipientAccountIds": [1]}""";

        String knownResponse = mockMvc.perform(post("/api/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(knownBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String unknownResponse = mockMvc.perform(post("/api/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownResponse).isEqualTo(knownResponse);
    }

    @Test
    @DisplayName("담당자를 선택하지 않으면 400")
    void requestPasswordReset_withoutRecipients_returnsBadRequest() throws Exception {
        String body = """
                {"email": "user@test.com", "recipientAccountIds": []}""";

        mockMvc.perform(post("/api/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /**
     * {@code @Size(max = 500)} 은 단순 입력 검증이 아니라
     * {@code AccountRequestService.MEMO_MAX_LENGTH}(1000)와 맺은 계약이다. 이 상한이
     * 1000을 넘겨 완화되면 정상 입력이 400도 알림도 없이 조용히 잘린다.
     */
    @Test
    @DisplayName("memo가 500자를 넘으면 400")
    void requestPasswordReset_memoTooLong_returnsBadRequest() throws Exception {
        String tooLongMemo = "a".repeat(501);
        PasswordResetRequest request = PasswordResetRequest.builder()
                .email("user@test.com")
                .memo(tooLongMemo)
                .recipientAccountIds(List.of(1L))
                .build();

        mockMvc.perform(post("/api/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
