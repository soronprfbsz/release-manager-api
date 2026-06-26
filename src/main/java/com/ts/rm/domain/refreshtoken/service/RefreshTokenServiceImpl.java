package com.ts.rm.domain.refreshtoken.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.auth.dto.TokenResponse;
import com.ts.rm.domain.common.repository.CodeRepository;
import com.ts.rm.domain.refreshtoken.entity.RefreshToken;
import com.ts.rm.domain.refreshtoken.repository.RefreshTokenRepository;
import com.ts.rm.global.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * Refresh Token 서비스 구현체 (Redis 기반)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String POSITION_CODE_TYPE = "POSITION";

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;
    private final CodeRepository codeRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public RefreshToken createRefreshToken(Account account) {
        // 1. 기존 Refresh Token 삭제 (한 계정당 하나의 Refresh Token만 유지)
        refreshTokenRepository.deleteByAccountId(account.getAccountId());

        // 2. 새 Refresh Token 생성
        String tokenValue = jwtTokenProvider.generateRefreshToken(account.getEmail());
        long ttlInSeconds = jwtTokenProvider.getRefreshExpirationInSeconds();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .accountId(account.getAccountId())
                .email(account.getEmail())
                .accountName(account.getAccountName())
                .role(account.getRole())
                .departmentId(account.getDepartment() != null ? account.getDepartment().getDepartmentId() : null)
                .avatarStyle(account.getAvatarStyle())
                .avatarSeed(account.getAvatarSeed())
                .createdAt(LocalDateTime.now())
                .ttl(ttlInSeconds)
                .build();

        RefreshToken savedToken = refreshTokenRepository.save(refreshToken);
        log.info("Refresh Token created for account: {} (TTL: {}s)", account.getEmail(), ttlInSeconds);

        return savedToken;
    }

    @Override
    public TokenResponse refreshAccessToken(String refreshTokenValue) {
        // 1. Refresh Token 검증
        if (!jwtTokenProvider.validateRefreshToken(refreshTokenValue)) {
            throw new BadCredentialsException("유효하지 않은 리프레시 토큰입니다.");
        }

        // 2. Redis에서 Refresh Token 조회
        RefreshToken refreshToken = refreshTokenRepository.findById(refreshTokenValue)
                .orElseThrow(() -> new BadCredentialsException("존재하지 않는 리프레시 토큰입니다."));

        // 3. DB에서 최신 계정 정보 조회 (계정 상태 변경, 부서 변경 등 반영)
        Long accountId = refreshToken.getAccountId();
        Account account = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BadCredentialsException("존재하지 않는 계정입니다."));

        // 4. 새로운 Access Token 생성 (accountId 기반, 최신 부서 ID 포함)
        Long departmentId = account.getDepartment() != null ? account.getDepartment().getDepartmentId() : null;
        String newAccessToken = jwtTokenProvider.generateToken(accountId, account.getRole(), departmentId);

        // 5. Refresh Token Rotation: 기존 토큰 삭제 및 새 토큰 발급
        refreshTokenRepository.deleteById(refreshTokenValue);
        RefreshToken newRefreshToken = createRefreshToken(account);

        log.info("Token refreshed for account: {}", account.getEmail());

        // 6. 응답 생성 (최신 계정 정보 반영)
        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .refreshExpiresIn(jwtTokenProvider.getRefreshExpirationInSeconds())
                .accountInfo(TokenResponse.AccountInfo.builder()
                        .accountId(account.getAccountId())
                        .email(account.getEmail())
                        .accountName(account.getAccountName())
                        .role(account.getRole())
                        .departmentId(departmentId)
                        .departmentName(account.getDepartment() != null ? account.getDepartment().getDepartmentName() : null)
                        .position(account.getPosition())
                        .positionName(getPositionName(account.getPosition()))
                        .avatarStyle(account.getAvatarStyle())
                        .avatarSeed(account.getAvatarSeed())
                        .mustChangePassword(account.isMustChangePassword())
                        .build())
                .build();
    }

    /**
     * position 코드로 직급명 조회
     */
    private String getPositionName(String positionCode) {
        if (positionCode == null || positionCode.isBlank()) {
            return null;
        }
        return codeRepository.findByCodeTypeIdAndCodeId(POSITION_CODE_TYPE, positionCode)
                .map(code -> code.getCodeName())
                .orElse(null);
    }

    @Override
    public void deleteRefreshToken(String refreshTokenValue) {
        if (refreshTokenRepository.existsById(refreshTokenValue)) {
            refreshTokenRepository.deleteById(refreshTokenValue);
            String maskedToken = refreshTokenValue.length() > 20
                    ? refreshTokenValue.substring(0, 20) + "..."
                    : refreshTokenValue.substring(0, Math.min(refreshTokenValue.length(), 10)) + "***";
            log.info("Refresh Token deleted: {}", maskedToken);
        }
    }

    @Override
    public void deleteAllByAccountId(Long accountId) {
        refreshTokenRepository.deleteByAccountId(accountId);
        log.info("All Refresh Tokens deleted for account ID: {}", accountId);
    }

    @Override
    public int deleteExpiredTokens() {
        // Redis TTL이 자동으로 만료 처리하므로 별도 구현 불필요
        log.debug("Redis TTL handles expired token cleanup automatically");
        return 0;
    }
}
