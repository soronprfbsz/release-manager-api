package com.ts.rm.domain.patch.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 패치 자동삭제 독촉 스케줄 계산
 *
 * <p>이 클래스가 존재하는 이유는 시간대 때문이다. 앱 컨테이너는 {@code TZ=UTC} 라
 * {@code patch_file.created_at} 은 UTC 지만, 정리 스케줄({@code patch-cleanup})은
 * {@code CronTrigger} 가 {@code Asia/Seoul} 로 돌린다. 두 기준을 섞으면 "며칠에
 * 삭제된다"는 안내가 하루씩 어긋난다 (ADR-0005).
 *
 * <p>따라서 삭제 예정 시각은 "보관기간이 지난 뒤 <b>처음 도래하는 cleanup 실행 시각</b>"
 * 으로 정의하고, D-N 은 KST 날짜끼리 비교한다.
 */
public final class PatchReminderSchedule {

    /** 스케줄러가 사용하는 표시/실행 기준 시간대 */
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** patch-cleanup 잡의 실행 시각 (V15: cron {@code 0 0 5 * * *}, timezone Asia/Seoul) */
    public static final LocalTime CLEANUP_TIME = LocalTime.of(5, 0);

    /** 독촉을 보내는 잔여일 — 마감이 다가올수록 촘촘해진다 */
    private static final Set<Long> REMINDER_DAYS_BEFORE = Set.of(15L, 10L, 5L, 4L, 3L, 2L, 1L);

    private PatchReminderSchedule() {
    }

    /**
     * 패치가 실제로 자동 삭제될 시각 (KST)
     *
     * <p>{@code created_at + retentionDays} 자체가 아니라, 그 시점이 지난 뒤 처음
     * 도래하는 05:00(KST) 이다. cleanup 이 하루 한 번만 돌기 때문이다.
     *
     * @param createdAtUtc  패치 생성 시각 (UTC)
     * @param retentionDays 보관 기간 (일)
     * @return 자동 삭제 예정 시각 (KST)
     */
    public static ZonedDateTime resolveDeletionAt(LocalDateTime createdAtUtc, int retentionDays) {
        ZonedDateTime expiresAtKst = createdAtUtc.atZone(ZoneId.of("UTC"))
                .plusDays(retentionDays)
                .withZoneSameInstant(KST);

        ZonedDateTime sameDayCleanup = expiresAtKst.toLocalDate()
                .atTime(CLEANUP_TIME)
                .atZone(KST);

        // 보관기간 만료가 그날 05:00 이후라면 그날 정리 작업은 이미 지나갔다 → 다음 날
        return expiresAtKst.isAfter(sameDayCleanup)
                ? sameDayCleanup.plusDays(1)
                : sameDayCleanup;
    }

    /**
     * 삭제까지 남은 일수 (KST 날짜 기준)
     *
     * <p>시각이 아니라 날짜 차이로 센다 — 사용자에게 보이는 "N일 후"와 일치시키기 위해서다.
     *
     * @param createdAtUtc  패치 생성 시각 (UTC)
     * @param retentionDays 보관 기간 (일)
     * @param nowUtc        기준 시각 (UTC)
     * @return 남은 일수 (이미 지났으면 0 이하)
     */
    public static long daysUntilDeletion(LocalDateTime createdAtUtc, int retentionDays,
            LocalDateTime nowUtc) {
        LocalDate deletionDate = resolveDeletionAt(createdAtUtc, retentionDays).toLocalDate();
        LocalDate today = nowUtc.atZone(ZoneId.of("UTC")).withZoneSameInstant(KST).toLocalDate();

        return ChronoUnit.DAYS.between(today, deletionDate);
    }

    /**
     * 오늘 독촉을 보내야 하는 패치인지 판정
     *
     * @param createdAtUtc  패치 생성 시각 (UTC)
     * @param retentionDays 보관 기간 (일)
     * @param nowUtc        기준 시각 (UTC)
     * @return 남은 일수가 마일스톤(15/10/5/4/3/2/1)에 해당하면 true
     */
    public static boolean shouldRemind(LocalDateTime createdAtUtc, int retentionDays,
            LocalDateTime nowUtc) {
        return REMINDER_DAYS_BEFORE.contains(
                daysUntilDeletion(createdAtUtc, retentionDays, nowUtc));
    }

    /**
     * 독촉 마일스톤 목록 (내림차순) — 문서화 / 테스트 용
     */
    public static List<Long> reminderDaysBefore() {
        return REMINDER_DAYS_BEFORE.stream().sorted((a, b) -> Long.compare(b, a)).toList();
    }

    /**
     * 독촉 대상 후보를 고르기 위한 가장 이른 생성 시각
     *
     * <p>가장 큰 마일스톤(D-15)보다 더 최근에 만들어진 패치는 아직 독촉 대상이 아니다.
     * 전체 스캔 대신 이 값으로 1차 컷을 둔다.
     *
     * @param retentionDays 보관 기간 (일)
     * @param nowUtc        기준 시각 (UTC)
     * @return 이 시각보다 이전에 생성된 패치만 후보가 된다
     */
    public static LocalDateTime earliestCandidateCreatedAt(int retentionDays,
            LocalDateTime nowUtc) {
        long maxDaysBefore = REMINDER_DAYS_BEFORE.stream().max(Long::compare).orElse(15L);
        // 여유 하루를 더 둬서 경계에서 후보가 빠지지 않게 한다
        return nowUtc.minus(Duration.ofDays(retentionDays - maxDaysBefore - 1));
    }
}
