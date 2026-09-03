package com.ts.rm.domain.message.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.common.repository.CodeRepository;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.enums.MessageType;
import com.ts.rm.domain.message.repository.MessageRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.security.SecurityUtil;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 타인의 계정을 손댔을 때 대상자에게 보내는 통지
 *
 * <p>발신자는 <b>시스템 계정</b>이고, 실제로 작업한 사람은 <b>본문에 담는다</b>. 자동 통지가
 * 개인의 발신함을 채우지 않게 하면서도 대상자는 "누가 내 정보를 바꿨나"를 알 수 있다
 * (ADR-0003 의 시스템 발신 알림과 같은 구조).
 *
 * <p>호출자 트랜잭션에 참여한다({@link Propagation#MANDATORY}). 계정 변경은 커밋됐는데 통지만
 * 사라지는 상태를 만들지 않기 위함이며, {@code AccountRequestService.requestSignupApproval} 과
 * 같은 방침이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountChangeNotifier {

    private static final String REF_TYPE_ACCOUNT = "ACCOUNT";
    private static final String POSITION_CODE_TYPE = "POSITION";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter DISPLAY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AccountRepository accountRepository;
    private final MessageRepository messageRepository;
    private final CodeRepository codeRepository;
    private final MessageNotificationPublisher notificationPublisher;

    @Value("${message.system-sender-email:admin@tscientific.co.kr}")
    private String systemSenderEmail;

    /**
     * 계정 정보 변경 통지
     *
     * <p>실제로 값이 바뀐 항목만 담긴 목록을 받는다 — 요청에 담겼는지가 아니라 전후 값이 달라졌는지가
     * 기준이다. 목록이 비면 발송하지 않는다.
     *
     * @param target  변경 대상 계정
     * @param changes 실제 변경된 항목 목록 (표시용 문자열로 이미 변환된 상태)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyAccountUpdated(Account target, List<FieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }

        Account actor = findActor(target);
        if (actor == null) {
            return;
        }

        Account sender = findSystemSender();
        if (sender == null) {
            return;
        }

        LocalDateTime nowUtc = LocalDateTime.now(UTC);
        String content = String.format("""
                        %s 님이 회원님의 계정 정보를 변경했습니다.

                        %s

                        변경 일시: %s
                        문의사항은 %s 님에게 문의해 주세요.""",
                describeActor(actor), formatChanges(changes), toKstText(nowUtc),
                actor.getAccountName());

        send(sender, target, MessageType.ACCOUNT_UPDATED,
                "[계정] 회원 정보가 변경되었습니다", content);

        log.info("계정 변경 통지 발송 - targetAccountId: {}, actorAccountId: {}, 변경 {}건",
                target.getAccountId(), actor.getAccountId(), changes.size());
    }

    /**
     * 비밀번호 초기화 통지
     *
     * <p><b>임시 비밀번호는 본문에 담지 않는다.</b> 담더라도 대상자는 그 비밀번호로 로그인해야
     * 쪽지를 볼 수 있어 전달 수단이 되지 못하고, 평문만 DB 에 남는다. 이 쪽지는 "누가 언제
     * 초기화했는지"의 사후 기록이다.
     *
     * @param target 초기화 대상 계정
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyPasswordReset(Account target) {
        Account actor = findActor(target);
        if (actor == null) {
            return;
        }

        Account sender = findSystemSender();
        if (sender == null) {
            return;
        }

        LocalDateTime nowUtc = LocalDateTime.now(UTC);
        String content = String.format("""
                        %s 님이 회원님의 비밀번호를 초기화했습니다.

                        임시 비밀번호는 보안상 이 쪽지에 담지 않습니다.
                        %s 님에게 직접 전달받은 임시 비밀번호로 로그인한 뒤,
                        안내에 따라 새 비밀번호로 변경해 주세요.

                        처리 일시: %s""",
                describeActor(actor), actor.getAccountName(), toKstText(nowUtc));

        send(sender, target, MessageType.PASSWORD_RESET_DONE,
                "[계정] 비밀번호가 초기화되었습니다", content);

        log.info("비밀번호 초기화 통지 발송 - targetAccountId: {}, actorAccountId: {}",
                target.getAccountId(), actor.getAccountId());
    }

    // === 내부 ===

    /**
     * 통지를 저장하고 커밋 후 실시간 알림을 예약한다.
     *
     * <p>수신자가 ACTIVE 인지는 확인하지 않는다 — 계정을 INACTIVE 로 내리는 변경이야말로 통지가
     * 필요한 경우라, {@code MessageService.send} 의 비활성 수신자 거부 규칙을 여기서는 적용하지
     * 않는다.
     */
    private void send(Account sender, Account target, MessageType type, String title,
            String content) {
        Message message = Message.builder()
                .sender(sender)
                .senderEmail(sender.getEmail())
                .senderName(sender.getAccountName())
                .messageType(type)
                .title(title)
                .content(content)
                .refType(REF_TYPE_ACCOUNT)
                .refId(target.getAccountId())
                .build();
        message.addRecipient(target);

        Message saved = messageRepository.save(message);
        notificationPublisher.publishAfterCommit(saved, saved.getRecipients());
    }

    /**
     * 본문에 표기할 작업자(변경을 수행한 사람) 계정을 찾는다.
     *
     * <p>본인이 본인을 수정한 경우와 인증 주체를 특정할 수 없는 경우(스케줄러 등 비인증 경로)에는
     * null 을 돌려 통지를 건너뛴다. 통지는 부가 기능이므로 작업자를 못 찾았다고 계정 변경 자체를
     * 실패시키지 않는다.
     */
    private Account findActor(Account target) {
        Long actorId;
        try {
            actorId = SecurityUtil.getCurrentAccountId();
        } catch (BusinessException e) {
            log.debug("계정 변경 통지 건너뜀 - 인증 주체 없음 (targetAccountId: {})",
                    target.getAccountId());
            return null;
        }

        if (actorId == null || actorId.equals(target.getAccountId())) {
            return null;
        }

        Account actor = accountRepository.findById(actorId).orElse(null);
        if (actor == null) {
            log.warn("계정 변경 통지 건너뜀 - 작업자 계정 없음 (actorAccountId: {})", actorId);
        }
        return actor;
    }

    /**
     * 발신자로 쓸 시스템 계정을 찾는다.
     *
     * <p>설정된 계정이 없으면 통지만 건너뛴다. {@code AccountRequestService} 는 같은 상황에서
     * 예외를 던지지만, 거기서는 통지가 요청의 목적 그 자체다. 여기서는 계정 변경이 목적이므로
     * 통지 실패로 그것을 되돌리지 않는다.
     */
    private Account findSystemSender() {
        Account sender = accountRepository.findByEmail(systemSenderEmail).orElse(null);
        if (sender == null) {
            log.error("계정 변경 통지 건너뜀 - 시스템 발신 계정 없음: {}", systemSenderEmail);
        }
        return sender;
    }

    /**
     * 본문의 작업자 표기 — 직급 코드는 표시명으로 바꾼다. 직급이 없으면 이름만 쓴다.
     */
    private String describeActor(Account actor) {
        String position = actor.getPosition();
        if (position == null || position.isBlank()) {
            return actor.getAccountName();
        }
        String positionName = codeRepository
                .findByCodeTypeIdAndCodeId(POSITION_CODE_TYPE, position)
                .map(code -> code.getCodeName())
                .orElse(position);
        return String.format("%s(%s)", actor.getAccountName(), positionName);
    }

    private String formatChanges(List<FieldChange> changes) {
        return changes.stream()
                .map(change -> String.format("· %s: %s → %s",
                        change.label(), change.before(), change.after()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String toKstText(LocalDateTime nowUtc) {
        return nowUtc.atZone(UTC).withZoneSameInstant(KST).format(DISPLAY_DATE_TIME);
    }

    /**
     * 변경 항목 한 줄
     *
     * @param label  항목명 (예: 부서)
     * @param before 변경 전 표시값 (예: 미배치)
     * @param after  변경 후 표시값 (예: 서비스기술팀)
     */
    public record FieldChange(String label, String before, String after) {
    }
}
