package com.ts.rm.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.auth.dto.SignInRequest;
import com.ts.rm.domain.auth.dto.TokenResponse;
import com.ts.rm.domain.auth.dto.SignUpRequest;
import com.ts.rm.domain.auth.dto.SignUpResponse;
import com.ts.rm.domain.refreshtoken.entity.RefreshToken;
import com.ts.rm.domain.refreshtoken.service.RefreshTokenService;
import com.ts.rm.global.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private SignUpRequest signUpRequest;
    private SignInRequest signInRequest;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        signUpRequest = SignUpRequest.builder()
                .email("test@example.com")
                .password("password123!")
                .accountName("홍길동")
                .build();

        signInRequest = SignInRequest.builder()
                .email("test@example.com")
                .password("password123!")
                .build();

        testAccount = Account.builder()
                .accountId(1L)
                .email("test@example.com")
                .password("encodedPassword")
                .accountName("홍길동")
                .role("USER")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("회원가입 성공")
    void signUp_Success() {
        // given
        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // when
        SignUpResponse response = authService.signUp(signUpRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccountId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getAccountName()).isEqualTo("홍길동");
        assertThat(response.getRole()).isEqualTo("USER");

        verify(accountRepository).findByEmail("test@example.com");
        verify(passwordEncoder).encode("password123!");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signUp_DuplicateEmail_ThrowsException() {
        // given
        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> authService.signUp(signUpRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 이메일입니다");

        verify(accountRepository).findByEmail("test@example.com");
        verify(passwordEncoder, never()).encode(anyString());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("로그인 성공")
    void signIn_Success() {
        // given
        String accessToken = "generated.jwt.token";
        String refreshTokenValue = "generated.refresh.token";
        long expirationTime = 3600L;
        long refreshExpirationTime = 604800L;

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .accountId(testAccount.getAccountId())
                .email(testAccount.getEmail())
                .accountName(testAccount.getAccountName())
                .role(testAccount.getRole())
                .createdAt(LocalDateTime.now())
                .ttl(refreshExpirationTime)
                .build();

        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenProvider.generateToken(anyLong(), anyString(), nullable(Long.class))).thenReturn(accessToken);
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(expirationTime);
        when(jwtTokenProvider.getRefreshExpirationInSeconds()).thenReturn(refreshExpirationTime);
        when(refreshTokenService.createRefreshToken(any(Account.class))).thenReturn(refreshToken);

        // when
        TokenResponse response = authService.signIn(signInRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo(accessToken);
        assertThat(response.getRefreshToken()).isEqualTo(refreshTokenValue);
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(expirationTime);
        assertThat(response.getRefreshExpiresIn()).isEqualTo(refreshExpirationTime);
        assertThat(response.getAccountInfo()).isNotNull();
        assertThat(response.getAccountInfo().getAccountId()).isEqualTo(1L);
        assertThat(response.getAccountInfo().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getAccountInfo().getAccountName()).isEqualTo("홍길동");
        assertThat(response.getAccountInfo().getRole()).isEqualTo("USER");

        verify(accountRepository).findByEmail("test@example.com");
        verify(passwordEncoder).matches("password123!", "encodedPassword");
        verify(jwtTokenProvider).generateToken(anyLong(), anyString(), nullable(Long.class));
        verify(refreshTokenService).createRefreshToken(testAccount);
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 계정")
    void signIn_AccountNotFound_ThrowsException() {
        // given
        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.signIn(signInRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("이메일 또는 비밀번호가 일치하지 않습니다");

        verify(accountRepository).findByEmail("test@example.com");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateToken(anyLong(), anyString(), nullable(Long.class));
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void signIn_InvalidPassword_ThrowsException() {
        // given
        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.signIn(signInRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("이메일 또는 비밀번호가 일치하지 않습니다");

        verify(accountRepository).findByEmail("test@example.com");
        verify(passwordEncoder).matches("password123!", "encodedPassword");
        verify(jwtTokenProvider, never()).generateToken(anyLong(), anyString(), nullable(Long.class));
    }

    @Test
    @DisplayName("회원가입 시 비밀번호 암호화 확인")
    void signUp_PasswordIsEncoded() {
        // given
        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123!")).thenReturn("encodedPassword");
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // when
        authService.signUp(signUpRequest);

        // then
        verify(passwordEncoder).encode("password123!");
    }

    @Test
    @DisplayName("회원가입 시 기본 역할은 USER")
    void signUp_DefaultRoleIsUser() {
        // given
        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // when
        SignUpResponse response = authService.signUp(signUpRequest);

        // then
        assertThat(response.getRole()).isEqualTo("USER");
    }
}
