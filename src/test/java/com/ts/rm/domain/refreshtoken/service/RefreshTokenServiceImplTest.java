package com.ts.rm.domain.refreshtoken.service;

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
import com.ts.rm.domain.auth.dto.TokenResponse;
import com.ts.rm.domain.refreshtoken.entity.RefreshToken;
import com.ts.rm.domain.refreshtoken.repository.RefreshTokenRepository;
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

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private Account testAccount;
    private RefreshToken testRefreshToken;

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .accountId(1L)
                .email("test@example.com")
                .password("encodedPassword")
                .accountName("Test User")
                .role("USER")
                .status("ACTIVE")
                .build();
        testAccount.setAccountId(1L);

        // Redis 기반 RefreshToken
        testRefreshToken = RefreshToken.builder()
                .token("valid-refresh-token")
                .accountId(testAccount.getAccountId())
                .email(testAccount.getEmail())
                .accountName(testAccount.getAccountName())
                .role(testAccount.getRole())
                .createdAt(LocalDateTime.now())
                .ttl(604800L) // 7일 (초 단위)
                .build();
    }

    @Test
    @DisplayName("Refresh Token 생성 - 성공")
    void createRefreshToken_Success() {
        // given
        String tokenValue = "new-refresh-token";
        when(jwtTokenProvider.generateRefreshToken(testAccount.getEmail())).thenReturn(tokenValue);
        when(jwtTokenProvider.getRefreshExpirationInSeconds()).thenReturn(604800L); // 7 days
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        // when
        RefreshToken result = refreshTokenService.createRefreshToken(testAccount);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo(tokenValue);
        assertThat(result.getAccountId()).isEqualTo(testAccount.getAccountId());
        assertThat(result.getEmail()).isEqualTo(testAccount.getEmail());
        verify(refreshTokenRepository).deleteByAccountId(testAccount.getAccountId());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Refresh Token 생성 - 기존 토큰 삭제 후 생성")
    void createRefreshToken_DeleteExistingToken() {
        // given
        String newTokenValue = "new-refresh-token";
        when(jwtTokenProvider.generateRefreshToken(testAccount.getEmail())).thenReturn(newTokenValue);
        when(jwtTokenProvider.getRefreshExpirationInSeconds()).thenReturn(604800L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        // when
        RefreshToken result = refreshTokenService.createRefreshToken(testAccount);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo(newTokenValue);
        verify(refreshTokenRepository).deleteByAccountId(testAccount.getAccountId());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Access Token 갱신 - 성공")
    void refreshAccessToken_Success() {
        // given
        String refreshTokenValue = "valid-refresh-token";
        String newAccessToken = "new-access-token";
        String newRefreshTokenValue = "new-refresh-token";

        when(jwtTokenProvider.validateRefreshToken(refreshTokenValue)).thenReturn(true);
        when(refreshTokenRepository.findById(refreshTokenValue))
                .thenReturn(Optional.of(testRefreshToken));
        when(accountRepository.findByAccountId(testAccount.getAccountId()))
                .thenReturn(Optional.of(testAccount));
        when(jwtTokenProvider.generateToken(anyLong(), anyString(), nullable(Long.class)))
                .thenReturn(newAccessToken);
        when(jwtTokenProvider.generateRefreshToken(testAccount.getEmail()))
                .thenReturn(newRefreshTokenValue);
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(3600L);
        when(jwtTokenProvider.getRefreshExpirationInSeconds()).thenReturn(604800L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        // when
        TokenResponse result = refreshTokenService.refreshAccessToken(refreshTokenValue);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo(newAccessToken);
        assertThat(result.getRefreshToken()).isEqualTo(newRefreshTokenValue);
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getAccountInfo().getEmail()).isEqualTo(testAccount.getEmail());
        // 기존 토큰 삭제 후 새 토큰 생성 (Rotation)
        verify(refreshTokenRepository).deleteById(refreshTokenValue);
        verify(refreshTokenRepository).deleteByAccountId(testAccount.getAccountId());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Access Token 갱신 - 유효하지 않은 Refresh Token")
    void refreshAccessToken_InvalidToken() {
        // given
        String invalidToken = "invalid-refresh-token";
        when(jwtTokenProvider.validateRefreshToken(invalidToken)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(invalidToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("유효하지 않은 리프레시 토큰입니다.");

        verify(refreshTokenRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("Access Token 갱신 - Redis에 존재하지 않는 Refresh Token")
    void refreshAccessToken_TokenNotFound() {
        // given
        String notFoundToken = "not-found-token";
        when(jwtTokenProvider.validateRefreshToken(notFoundToken)).thenReturn(true);
        when(refreshTokenRepository.findById(notFoundToken)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(notFoundToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("존재하지 않는 리프레시 토큰입니다.");
    }

    @Test
    @DisplayName("Refresh Token 삭제 - 성공")
    void deleteRefreshToken_Success() {
        // given
        String tokenValue = "valid-refresh-token";
        when(refreshTokenRepository.existsById(tokenValue)).thenReturn(true);

        // when
        refreshTokenService.deleteRefreshToken(tokenValue);

        // then
        verify(refreshTokenRepository).existsById(tokenValue);
        verify(refreshTokenRepository).deleteById(tokenValue);
    }

    @Test
    @DisplayName("Refresh Token 삭제 - 존재하지 않는 토큰 (예외 발생 안함)")
    void deleteRefreshToken_TokenNotFound() {
        // given
        String tokenValue = "not-found-token";
        when(refreshTokenRepository.existsById(tokenValue)).thenReturn(false);

        // when
        refreshTokenService.deleteRefreshToken(tokenValue);

        // then
        verify(refreshTokenRepository).existsById(tokenValue);
        verify(refreshTokenRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("계정의 모든 Refresh Token 삭제")
    void deleteAllByAccountId() {
        // given
        Long accountId = 1L;

        // when
        refreshTokenService.deleteAllByAccountId(accountId);

        // then
        verify(refreshTokenRepository).deleteByAccountId(accountId);
    }

    @Test
    @DisplayName("만료된 Refresh Token 일괄 삭제 - Redis TTL 자동 처리")
    void deleteExpiredTokens() {
        // Redis TTL이 자동으로 만료 처리하므로 항상 0 반환

        // when
        int result = refreshTokenService.deleteExpiredTokens();

        // then
        assertThat(result).isEqualTo(0);
    }
}
