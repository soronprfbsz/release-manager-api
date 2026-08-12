package com.ts.rm.domain.message.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.message.entity.Message;
import com.ts.rm.domain.message.entity.MessageRecipient;
import com.ts.rm.domain.message.enums.MessageType;
import com.ts.rm.domain.message.repository.MessageRecipientRepository;
import com.ts.rm.domain.message.repository.MessageRepository;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.patch.util.PatchReminderSchedule;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 패치 자동삭제 독촉 발송
 *
 * <p>미처리 패치({@code patch_file} 에 남아 있는 행)를 훑어 삭제 예정일까지 남은 일수가
 * 마일스톤(D-15/10/5/4/3/2/1)에 해당하면 생성자에게 독촉 메시지를 보낸다 (ADR-0005).
 *
 * <p>패치를 완료/삭제하면 {@code patch_file} 에서 행이 사라지므로, 이 테이블에 남아
 * 있다는 것 자체가 "아직 아무 처리도 하지 않았다"는 뜻이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatchReminderService {

    private static final String REF_TYPE_PATCH = "PATCH";
    private static final DateTimeFormatter DEDUP_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PatchRepository patchRepository;
    private final MessageRepository messageRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final AccountRepository accountRepository;
    private final MessageNotificationPublisher notificationPublisher;

    @Value("${patch.cleanup.retention-days:30}")
    private int retentionDays;

    @Value("${message.system-sender-email:admin@tscientific.co.kr}")
    private String systemSenderEmail;

    /**
     * 독촉 발송 (스케줄러 patch-reminder 호출)
     *
     * @param nowUtc 기준 시각 (UTC). 테스트에서 시점을 고정하기 위해 파라미터로 받는다.
     * @return 발송된 메시지 수
     */
    @Transactional
    public int sendReminders(LocalDateTime nowUtc) {
        Account sender = accountRepository.findByEmail(systemSenderEmail).orElse(null);
        if (sender == null) {
            // 발신 계정이 없다고 스케줄 전체를 실패시키지는 않는다 — 운영 알람만 남긴다
            log.error("독촉 발송 건너뜀 - 시스템 발신 계정을 찾을 수 없습니다: {}", systemSenderEmail);
            return 0;
        }

        LocalDateTime candidateCutoff =
                PatchReminderSchedule.earliestCandidateCreatedAt(retentionDays, nowUtc);
        List<Patch> candidates = patchRepository.findByCreatedAtBefore(candidateCutoff);

        log.info("패치 독촉 시작 - retentionDays: {}, 후보: {}건", retentionDays, candidates.size());

        int sentCount = 0;
        for (Patch patch : candidates) {
            if (!PatchReminderSchedule.shouldRemind(patch.getCreatedAt(), retentionDays, nowUtc)) {
                continue;
            }
            if (sendReminder(patch, sender, nowUtc)) {
                sentCount++;
            }
        }

        log.info("패치 독촉 완료 - 발송 {}건", sentCount);
        return sentCount;
    }

    /**
     * 패치 1건에 대한 독촉 발송
     *
     * @return 실제로 발송했으면 true (중복/수신자 없음으로 건너뛰면 false)
     */
    private boolean sendReminder(Patch patch, Account sender, LocalDateTime nowUtc) {
        Account creator = patch.getCreator();
        if (creator == null) {
            log.warn("독촉 건너뜀 - 생성자 계정 없음 (patchId: {}, patchName: {})",
                    patch.getPatchId(), patch.getPatchName());
            return false;
        }

        String dedupKey = dedupKey(patch, nowUtc);
        if (messageRepository.existsByDedupKey(dedupKey)) {
            log.debug("독촉 건너뜀 - 오늘 이미 발송됨 (dedupKey: {})", dedupKey);
            return false;
        }

        long daysLeft = PatchReminderSchedule.daysUntilDeletion(
                patch.getCreatedAt(), retentionDays, nowUtc);
        ZonedDateTime deletionAt = PatchReminderSchedule.resolveDeletionAt(
                patch.getCreatedAt(), retentionDays);

        Message message = Message.builder()
                .sender(sender)
                .senderEmail(sender.getEmail())
                .senderName(sender.getAccountName())
                .messageType(MessageType.PATCH_REMINDER)
                .title(buildTitle(patch, daysLeft))
                .content(buildContent(patch, deletionAt))
                .refType(REF_TYPE_PATCH)
                .refId(patch.getPatchId())
                .refProjectId(patch.getProject() != null ? patch.getProject().getProjectId() : null)
                .refReleaseType(patch.getReleaseType())
                .dedupKey(dedupKey)
                .build();
        message.addRecipient(creator);

        try {
            Message saved = messageRepository.saveAndFlush(message);
            notificationPublisher.publishAfterCommit(saved, saved.getRecipients());
            log.info("독촉 발송 - patchId: {}, patchName: {}, D-{}, 수신자: {}",
                    patch.getPatchId(), patch.getPatchName(), daysLeft, creator.getEmail());
            return true;
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약이 최종 방어선 — 동시 실행/수동 실행이 겹쳐도 하루 1건만 남는다
            log.debug("독촉 건너뜀 - 멱등 키 충돌 (dedupKey: {})", dedupKey);
            return false;
        }
    }

    /**
     * 패치 처리 완료/삭제 시 남아 있는 독촉을 수신함에서 숨긴다.
     *
     * <p>호출자(패치 완료/삭제)의 트랜잭션에 그대로 참여한다. 패치 처리가 롤백되면
     * 독촉도 되살아나야 정합성이 맞고, 하는 일이 조회 + 필드 갱신뿐이라 별도
     * 트랜잭션으로 떼어낼 이유가 없다 — 일괄 정리에서는 건수만큼 트랜잭션이
     * 새로 열리는 낭비도 생긴다.
     *
     * @param patchId 처리된 패치 ID
     */
    @Transactional
    public void hideRemindersFor(Long patchId) {
        if (patchId == null) {
            return;
        }

        List<MessageRecipient> reminders = messageRecipientRepository
                .findByMessage_RefTypeAndMessage_RefIdAndDeletedAtIsNull(REF_TYPE_PATCH, patchId);

        reminders.forEach(MessageRecipient::hide);

        if (!reminders.isEmpty()) {
            log.info("패치 처리 완료 - 관련 독촉 {}건 숨김 (patchId: {})", reminders.size(), patchId);
        }
    }

    // === 메시지 문구 ===

    private String dedupKey(Patch patch, LocalDateTime nowUtc) {
        String today = nowUtc.atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(PatchReminderSchedule.KST)
                .format(DEDUP_DATE);
        return "PATCH_REMINDER:" + patch.getPatchId() + ":" + today;
    }

    private String buildTitle(Patch patch, long daysLeft) {
        return String.format("[자동알림] %s 패치가 %d일 후 자동 삭제됩니다",
                patch.getPatchName(), daysLeft);
    }

    private String buildContent(Patch patch, ZonedDateTime deletionAt) {
        String createdAtKst = patch.getCreatedAt().atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(PatchReminderSchedule.KST)
                .format(DISPLAY_DATE_TIME);

        String projectName = patch.getProject() != null
                ? patch.getProject().getProjectName()
                : "-";
        String siteName = patch.getSite() != null ? patch.getSite().getSiteName() : null;
        String target = siteName != null ? projectName + " / " + siteName : projectName;

        return String.format("""
                        %s 에 생성한 패치 '%s' 가 아직 처리되지 않았습니다.

                        · 대상: %s
                        · 버전: %s → %s
                        · 자동 삭제 예정: %s 05시

                        삭제 전에 패치 관리 화면에서 완료 처리하거나 직접 삭제해 주세요.
                        자동 삭제된 패치는 복구할 수 없습니다.""",
                createdAtKst,
                patch.getPatchName(),
                target,
                patch.getFromVersion(),
                patch.getToVersion(),
                deletionAt.format(DISPLAY_DATE));
    }
}
