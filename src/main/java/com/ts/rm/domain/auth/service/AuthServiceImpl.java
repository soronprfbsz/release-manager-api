package com.ts.rm.domain.auth.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.auth.dto.SignInRequest;
import com.ts.rm.domain.auth.dto.SignUpRequest;
import com.ts.rm.domain.auth.dto.SignUpResponse;
import com.ts.rm.domain.auth.dto.TokenResponse;
import com.ts.rm.domain.common.repository.CodeRepository;
import com.ts.rm.domain.message.service.AccountRequestService;
import com.ts.rm.domain.refreshtoken.entity.RefreshToken;
import com.ts.rm.domain.refreshtoken.service.RefreshTokenService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String POSITION_CODE_TYPE = "POSITION";
    private static final String GUEST = "GUEST";
    private static final String ACTIVE = "ACTIVE";

    private final AccountRepository accountRepository;
    private final CodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AccountRequestService accountRequestService;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 10;

    @Override
    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        // 1. 이메일 중복 체크
        if (accountRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다: " + request.getEmail());
        }

        // 2. 직급 코드 유효성 검증
        if (request.getPosition() != null && !request.getPosition().isBlank()) {
            validatePositionCode(request.getPosition());
        }

        // 3. 계정 생성
        Account account = Account.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .accountName(request.getAccountName())
                .phone(request.getPhone())
                .position(request.getPosition())
                .avatarStyle(request.getAvatarStyle())
                .avatarSeed(request.getAvatarSeed())
                .role(GUEST)
                .status(ACTIVE)
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("New account created: {}", savedAccount.getEmail());

        // 4. 담당자에게 가입 처리 요청 발송 (같은 트랜잭션 — 요청 없는 GUEST 계정을 만들지 않는다)
        accountRequestService.requestSignupApproval(
                savedAccount, request.getRecipientAccountIds());

        // 5. 응답 생성
        return SignUpResponse.builder()
                .accountId(savedAccount.getAccountId())
                .email(savedAccount.getEmail())
                .accountName(savedAccount.getAccountName())
                .phone(savedAccount.getPhone())
                .position(savedAccount.getPosition())
                .positionName(getPositionName(savedAccount.getPosition()))
                .role(savedAccount.getRole())
                .createdAt(savedAccount.getCreatedAt())
                .build();
    }

    /**
     * 직급 코드 유효성 검증
     */
    private void validatePositionCode(String positionCode) {
        boolean exists = codeRepository.existsByCodeTypeIdAndCodeId(POSITION_CODE_TYPE, positionCode);
        if (!exists) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
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
    public TokenResponse signIn(SignInRequest request) {
        // 1. 계정 조회
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다."));

        // 2. 계정 상태 검증
        validateAccountStatus(account);

        // 3. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            handleLoginFailure(account);  // 별도 트랜잭션으로 저장 후 예외 발생
            throw new BadCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        // 4. 로그인 성공 처리
        handleLoginSuccess(account);

        // 5. Access Token 생성 (accountId 기반, 부서 ID 포함)
        Long departmentId = account.getDepartment() != null ? account.getDepartment().getDepartmentId() : null;
        String accessToken = jwtTokenProvider.generateToken(account.getAccountId(), account.getRole(), departmentId);

        // 6. Refresh Token 생성 및 저장
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(account);

        log.info("User signed in: {}", account.getEmail());

        // 7. 응답 생성
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
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
     * 계정 상태 검증 (계정 잠금 및 상태 확인)
     *
     * @param account 계정 엔티티
     * @throws BusinessException LOCKED, SUSPENDED 또는 INACTIVE 상태일 경우
     */
    private void validateAccountStatus(Account account) {
        // 1. 계정 잠금 확인 (10분 임시 잠금)
        if (account.getLockedUntil() != null && LocalDateTime.now().isBefore(account.getLockedUntil())) {
            log.warn("Login attempt for locked account: {} (locked until: {})",
                    account.getEmail(), account.getLockedUntil());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        // 2. 잠금 시간이 지났으면 자동 해제
        if (account.getLockedUntil() != null && LocalDateTime.now().isAfter(account.getLockedUntil())) {
            account.setLockedUntil(null);
            account.setLoginAttemptCount(0);
            accountRepository.save(account);
            log.info("Account lock expired and reset: {}", account.getEmail());
        }

        // 3. 계정 상태 확인
        AccountStatus status = AccountStatus.valueOf(account.getStatus());

        switch (status) {
            case SUSPENDED:
                log.warn("Login attempt for suspended account: {}", account.getEmail());
                throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
            case INACTIVE:
                log.warn("Login attempt for inactive account: {}", account.getEmail());
                throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
            case ACTIVE:
                // 정상 상태, 계속 진행
                break;
            default:
                log.error("Unknown account status: {}", status);
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 로그인 실패 처리 (시도 횟수 증가 및 10분 계정 잠금)
     *
     * <p>별도 트랜잭션으로 실행하여 예외 발생 시에도 DB에 저장되도록 함
     *
     * @param account 계정 엔티티
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleLoginFailure(Account account) {
        int attemptCount = account.getLoginAttemptCount() + 1;
        account.setLoginAttemptCount(attemptCount);

        log.warn("Login failed for {}: attempt {}/{}", account.getEmail(), attemptCount, MAX_LOGIN_ATTEMPTS);

        // 5회 이상 실패 시 10분간 계정 잠금
        if (attemptCount >= MAX_LOGIN_ATTEMPTS) {
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
            account.setLockedUntil(lockUntil);
            log.warn("Account locked until {} due to {} failed login attempts: {}",
                    lockUntil, attemptCount, account.getEmail());
        }

        accountRepository.save(account);
    }

    /**
     * 로그인 성공 처리 (마지막 로그인 시간 기록, 시도 횟수 초기화)
     *
     * @param account 계정 엔티티
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleLoginSuccess(Account account) {
        account.setLastLoginAt(LocalDateTime.now());
        account.setLoginAttemptCount(0);
        accountRepository.save(account);

        log.info("Login success for {}: last login updated", account.getEmail());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.deleteRefreshToken(refreshToken);
        log.info("User logged out");
    }
}
