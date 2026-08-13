package com.ts.rm.domain.message.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountRole;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.enums.MessageType;
import com.ts.rm.domain.message.repository.MessageRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계정 관련 요청(비밀번호 재설정 / 가입 처리) 메시지 발송
 *
 * <p>비밀번호 재설정 요청은 <b>미인증 상태</b>에서 들어온다. 따라서 계정 존재 여부와
 * 쿨다운 중복 여부를 응답으로 구분할 수 없어야 한다 — 어느 쪽이든 조용히 정상 반환한다.
 *
 * <p>발신자는 언제나 시스템 계정이다. 요청자 계정을 발신자로 쓰면 이메일만 아는 사람이
 * 타인 명의로 메시지를 심을 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRequestService {

    private static final String REF_TYPE_ACCOUNT = "ACCOUNT";
    /** dedupKey UNIQUE 인덱스명 — V24__add_message.sql 에서 정의한 값과 반드시 일치해야 한다 */
    private static final String DEDUP_KEY_CONSTRAINT_NAME = "uk_msg_dedup_key";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter DEDUP_BUCKET =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DISPLAY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 요청자가 남긴 메모의 최대 길이.
     *
     * <p>{@code content} 는 TEXT(65,535바이트)라 이론상 훨씬 긴 메모도 담을 수 있다.
     * 하지만 길이를 열어 두면 memo 크기만 바꿔가며 저장 성공(200)/실패(500)를 관찰해
     * 계정 존재 여부를 알아낼 수 있다 — 계정 열거 오라클이 된다. Task 3 의 DTO 에도
     * {@code @Size(max = 500)} 이 있지만, 이 서비스의 보안 속성이 다른 파일의 어노테이션에
     * 의존하면 그게 지워질 때 조용히 깨진다. 500 보다 크게 잡아 정상 경로에서는 절대
     * 발동하지 않도록 하면서, 이 서비스 자체가 자기완결적으로 상한을 강제한다.
     */
    private static final int MEMO_MAX_LENGTH = 1000;

    private final AccountRepository accountRepository;
    private final MessageRepository messageRepository;
    private final MessageNotificationPublisher notificationPublisher;
    private final TransactionTemplate transactionTemplate;

    @Value("${message.system-sender-email:admin@tscientific.co.kr}")
    private String systemSenderEmail;

    @Value("${account.request.password-reset-cooldown-minutes:10}")
    private int cooldownMinutes;

    /**
     * 비밀번호 재설정 요청 발송 (미인증 진입점)
     *
     * <p>이 메서드에는 트랜잭션을 걸지 않는다. 저장이 UNIQUE 제약으로 실패하면 그
     * 트랜잭션이 rollback-only 로 마킹되는데, 진입 메서드까지 같은 트랜잭션이면 예외를
     * 삼켜도 커밋 시점에 {@code UnexpectedRollbackException} 이 터져 500 이 나간다.
     * 그러면 미등록 이메일(항상 200)과 응답이 갈려 계정 열거 방지가 무너진다.
     *
     * <p><b>전제조건</b>: 호출자는 반드시 비트랜잭셔널이어야 한다. 이 메서드는 내부에서
     * {@link #saveAndNotify}를 통해 별도 트랜잭션을 새로 연다(전파 기본값 REQUIRED). 만약
     * 호출자에 {@code @Transactional}을 붙이면 그 트랜잭션에 편승하게 되어, UNIQUE 충돌이
     * 나도 호출자 트랜잭션 전체가 rollback-only 로 마킹된다 — 결국 커밋 시점에
     * {@code UnexpectedRollbackException}이 터져 500이 나가고, 미등록 이메일(항상 200)과
     * 응답이 갈려 계정 열거 방지가 무너진다. 컨트롤러에서 이 메서드를 호출할 때는 그 호출
     * 경로에 {@code @Transactional}이 없는지 반드시 확인해야 한다.
     *
     * @param email               요청자 이메일 (미등록이어도 예외를 던지지 않는다)
     * @param memo                요청자가 남긴 메모 (선택)
     * @param recipientAccountIds 요청을 받을 담당자 계정 ID 목록
     */
    public void requestPasswordReset(String email, String memo, List<Long> recipientAccountIds) {
        Account sender = findSystemSender();
        List<Account> recipients = findEligibleRecipients(recipientAccountIds);

        Account requester = accountRepository.findByEmail(email).orElse(null);
        if (requester == null) {
            // 계정 열거 방지 — 호출자에게는 성공과 구분되지 않아야 한다
            log.info("비밀번호 재설정 요청 무시 - 미등록 이메일");
            return;
        }

        LocalDateTime nowUtc = LocalDateTime.now(UTC);
        String dedupKey = passwordResetDedupKey(requester.getAccountId(), nowUtc);
        if (messageRepository.existsByDedupKey(dedupKey)) {
            log.info("비밀번호 재설정 요청 건너뜀 - 쿨다운 중 (accountId: {})",
                    requester.getAccountId());
            return;
        }

        Message message = Message.builder()
                .sender(sender)
                .senderEmail(sender.getEmail())
                .senderName(sender.getAccountName())
                .messageType(MessageType.PASSWORD_RESET_REQUEST)
                .title(String.format("[요청] 비밀번호 재설정 — %s", requester.getAccountName()))
                .content(buildPasswordResetContent(requester, memo, nowUtc))
                .refType(REF_TYPE_ACCOUNT)
                .refId(requester.getAccountId())
                .dedupKey(dedupKey)
                .build();
        recipients.forEach(message::addRecipient);

        saveAndNotify(message, dedupKey);
    }

    /**
     * 신규 가입 처리 요청 발송
     *
     * <p>{@code AuthServiceImpl.signUp} 의 트랜잭션 안에서 호출된다. 계정이 방금 생성돼
     * {@code dedupKey} 충돌이 구조적으로 불가능하므로 별도 트랜잭션 분리가 필요 없다.
     *
     * @param newAccount          가입한 계정
     * @param recipientAccountIds 요청을 받을 담당자 계정 ID 목록
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requestSignupApproval(Account newAccount, List<Long> recipientAccountIds) {
        Account sender = findSystemSender();
        List<Account> recipients = findEligibleRecipients(recipientAccountIds);

        Message message = Message.builder()
                .sender(sender)
                .senderEmail(sender.getEmail())
                .senderName(sender.getAccountName())
                .messageType(MessageType.SIGNUP_APPROVAL_REQUEST)
                .title(String.format("[요청] 신규 가입 처리 — %s (권한·부서 배치)",
                        newAccount.getAccountName()))
                .content(buildSignupContent(newAccount, LocalDateTime.now(UTC)))
                .refType(REF_TYPE_ACCOUNT)
                .refId(newAccount.getAccountId())
                .dedupKey("SIGNUP_APPROVAL_REQUEST:" + newAccount.getAccountId())
                .build();
        recipients.forEach(message::addRecipient);

        Message saved = messageRepository.save(message);
        notificationPublisher.publishAfterCommit(saved, saved.getRecipients());

        log.info("가입 처리 요청 발송 - accountId: {}, 수신자 {}명",
                newAccount.getAccountId(), recipients.size());
    }

    // === 내부 ===

    /**
     * 저장 + 알림만 별도 트랜잭션으로 실행한다.
     *
     * <p>전파는 기본값(REQUIRED)이다. 진입 메서드가 비트랜잭셔널이라 여기서 새 트랜잭션이
     * 열리고, 제약 위반이 나도 이 트랜잭션만 롤백된다. REQUIRES_NEW 를 쓰면
     * {@code @Transactional} 통합 테스트에서 아직 커밋되지 않은 계정을 볼 수 없어 FK
     * 위반이 난다.
     */
    private void saveAndNotify(Message message, String dedupKey) {
        try {
            transactionTemplate.execute(status -> {
                Message saved = messageRepository.saveAndFlush(message);
                notificationPublisher.publishAfterCommit(saved, saved.getRecipients());
                return null;
            });
            log.info("비밀번호 재설정 요청 발송 - 수신자 {}명", message.getRecipients().size());
        } catch (DataIntegrityViolationException e) {
            if (!isDedupKeyConstraintViolation(e, dedupKey)) {
                // dedupKey 충돌이 아니면(FK 위반, 컬럼 길이 초과 등) 진짜 장애다 — 삼키지 않는다
                log.warn("비밀번호 재설정 요청 저장 실패 - dedupKey 충돌 아님 (dedupKey: {})",
                        dedupKey, e);
                throw e;
            }
            // 동시 요청이 UNIQUE 제약에 걸린 경우 — 성공과 구분되지 않아야 한다
            log.info("비밀번호 재설정 요청 건너뜀 - 멱등 키 충돌 (dedupKey: {})", dedupKey);
        }
    }

    /**
     * 잡은 예외가 dedupKey UNIQUE 제약({@value #DEDUP_KEY_CONSTRAINT_NAME}) 위반인지 판정한다.
     *
     * <p>원인 체인에서 Hibernate {@link ConstraintViolationException} 을 찾아 제약명으로
     * 판정하는 것이 1순위다 — DB 재조회가 없어 세션 상태에 의존하지 않는다.
     * {@code open-in-view} 가 기본값(true)이라 재조회가 방금 flush 에 실패한 것과 같은
     * EntityManager 에서 실행될 수 있고, 그러면 Hibernate 가 자동 flush 를 재시도하며 실패한
     * INSERT 를 다시 던져 catch 블록 안에서 500 이 날 수 있다.
     *
     * <p>제약명을 뽑아낼 수 없을 때(null/빈 값 — 드라이버·버전에 따라 있을 수 있다)만
     * 기존 재조회 방식으로 폴백한다. 오늘 동작보다 안전성이 떨어지지 않도록 하기 위함이다.
     */
    private boolean isDedupKeyConstraintViolation(DataIntegrityViolationException e, String dedupKey) {
        String constraintName = extractConstraintName(e);
        if (constraintName != null && !constraintName.isBlank()) {
            return matchesDedupKeyConstraintName(constraintName);
        }
        return messageRepository.existsByDedupKey(dedupKey);
    }

    /**
     * 제약명이 dedupKey UNIQUE 제약({@value #DEDUP_KEY_CONSTRAINT_NAME})을 가리키는지 비교한다.
     *
     * <p>MySQL 8.0.19+ 는 제약명을 {@code 테이블.인덱스} 형태로 수식해 돌려준다(예:
     * {@code message.uk_msg_dedup_key}). 완전일치로 비교하면 그 환경에서는 non-blank 인데
     * 불일치가 나 폴백(재조회)도 못 타고 그대로 rethrow 되는데, 이는 dedup 충돌에도 500 을
     * 내보내 계정 열거 방지가 막으려던 차등 응답 그 자체가 된다. 마지막 {@code .} 뒤
     * 세그먼트만 비교해 수식 여부와 무관하게 판정한다(구분자가 없으면 전체 문자열이 그
     * 세그먼트다). 현재 스택(MariaDB 10.11.5)은 제약명을 수식하지 않아 오늘은 결함이 아니지만,
     * DB 이전 시 조용히 되살아나는 것을 막기 위해 미리 경화한다.
     */
    private boolean matchesDedupKeyConstraintName(String constraintName) {
        int lastDot = constraintName.lastIndexOf('.');
        String unqualified = lastDot >= 0 ? constraintName.substring(lastDot + 1) : constraintName;
        return DEDUP_KEY_CONSTRAINT_NAME.equalsIgnoreCase(unqualified);
    }

    private String extractConstraintName(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
        }
        return null;
    }

    private Account findSystemSender() {
        return accountRepository.findByEmail(systemSenderEmail)
                .orElseThrow(() -> {
                    log.error("요청 발송 실패 - 시스템 발신 계정 없음: {}", systemSenderEmail);
                    return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                            "요청을 처리할 수 없습니다. 관리자에게 문의해 주세요.");
                });
    }

    /**
     * 수신자 재검증 — 프론트가 보낸 ID를 신뢰하지 않는다.
     *
     * <p>어떤 계정이 왜 걸러졌는지는 응답에 담지 않는다. 미인증 엔드포인트에서 계정
     * 상태를 알려주면 그 자체가 정보 노출이다.
     */
    private List<Account> findEligibleRecipients(List<Long> recipientAccountIds) {
        Set<Long> distinctIds = new LinkedHashSet<>(recipientAccountIds);

        List<Account> eligible = accountRepository.findAllById(distinctIds).stream()
                .filter(account -> AccountRole.ADMIN.getCodeId().equals(account.getRole())
                        || AccountRole.OPERATOR.getCodeId().equals(account.getRole()))
                .filter(account -> AccountStatus.ACTIVE.name().equals(account.getStatus()))
                .toList();

        if (eligible.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "유효한 담당자를 선택해 주세요.");
        }
        return eligible;
    }

    /**
     * 쿨다운 버킷으로 내린 멱등 키
     *
     * <p>같은 버킷에 떨어지는 요청은 같은 키를 갖는다. {@code cooldownMinutes} 는 60 의
     * 약수여야 시간 경계가 어긋나지 않는다 (기본 10). 고정 윈도 방식이라 버킷 경계
     * 직전/직후(예: 09:09:59 와 09:10:01)는 몇 초 차이인데도 서로 다른 버킷에 떨어져
     * 둘 다 발송될 수 있다 — 완벽한 슬라이딩 윈도가 아닌, 저비용 근사치다.
     *
     * <p>{@code cooldownMinutes} 가 0 이하이거나 60 을 넘으면(설정 오류) 나눗셈 예외나
     * 의미 없는 버킷이 나올 수 있어 1~60 범위로 클램프한다 — 미인증 엔드포인트에서
     * 설정 오류가 500 으로 새는 것은 계정 열거 방지가 막으려던 차등 응답 그 자체다.
     */
    private String passwordResetDedupKey(Long accountId, LocalDateTime nowUtc) {
        int bucketMinutes = Math.max(1, Math.min(60, cooldownMinutes));
        LocalDateTime bucket = nowUtc
                .withMinute(nowUtc.getMinute() / bucketMinutes * bucketMinutes)
                .withSecond(0)
                .withNano(0);
        return "PASSWORD_RESET_REQUEST:" + accountId + ":" + bucket.format(DEDUP_BUCKET);
    }

    private String buildPasswordResetContent(Account requester, String memo,
            LocalDateTime nowUtc) {
        String departmentName = requester.getDepartment() != null
                ? requester.getDepartment().getDepartmentName()
                : "부서 없음";
        String memoLine = (memo == null || memo.isBlank())
                ? ""
                : String.format("%n· 남긴 메모: %s", truncateMemo(memo));

        return String.format("""
                        %s(%s) 님이 비밀번호 재설정을 요청했습니다.

                        · 요청자: %s / %s
                        · 이메일: %s
                        · 요청 일시: %s%s

                        계정 관리 화면에서 해당 계정의 비밀번호를 초기화한 뒤,
                        발급된 임시 비밀번호를 요청자에게 직접 전달해 주세요.""",
                requester.getAccountName(), requester.getEmail(),
                requester.getAccountName(), departmentName,
                requester.getEmail(),
                toKstText(nowUtc), memoLine);
    }

    private String buildSignupContent(Account newAccount, LocalDateTime nowUtc) {
        return String.format("""
                        %s(%s) 님이 신규 가입했습니다.
                        현재 권한이 GUEST 라 조회 외 기능이 제한됩니다.

                        · 이름: %s
                        · 이메일: %s
                        · 직급: %s
                        · 연락처: %s
                        · 가입 일시: %s

                        계정 관리 화면에서 권한과 부서를 지정해 주세요.""",
                newAccount.getAccountName(), newAccount.getEmail(),
                newAccount.getAccountName(),
                newAccount.getEmail(),
                blankToDash(newAccount.getPosition()),
                blankToDash(newAccount.getPhone()),
                toKstText(nowUtc));
    }

    private String blankToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    /**
     * memo 를 {@link #MEMO_MAX_LENGTH} 이내로 자른다 — 왜 자르는지는 상수 Javadoc 참고
     * (계정 열거 오라클 차단). 잘렸다는 사실이 드러나도록 말줄임표를 붙인다.
     */
    private String truncateMemo(String memo) {
        return memo.length() > MEMO_MAX_LENGTH
                ? memo.substring(0, MEMO_MAX_LENGTH) + "…"
                : memo;
    }

    private String toKstText(LocalDateTime nowUtc) {
        return nowUtc.atZone(UTC).withZoneSameInstant(KST).format(DISPLAY_DATE_TIME);
    }
}
