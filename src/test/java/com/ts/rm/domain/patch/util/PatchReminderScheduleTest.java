package com.ts.rm.domain.patch.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 패치 독촉 스케줄 계산 단위 테스트
 *
 * <p>UTC 저장 / KST 실행이 섞여 있어 하루가 어긋나기 쉬운 지점이다. 경계값을 못 박는다.
 */
@DisplayName("PatchReminderSchedule 테스트")
class PatchReminderScheduleTest {

    private static final int RETENTION_DAYS = 30;
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    @DisplayName("삭제 예정 시각 - 만료가 05:00(KST) 이전이면 같은 날 05:00 에 삭제된다")
    void resolveDeletionAt_ExpiresBeforeCleanup_SameDay() {
        // 2026-07-11 19:30 UTC = 2026-07-12 04:30 KST → +30일 = 2026-08-11 04:30 KST
        // 그날 05:00 정리 작업이 아직 오지 않았으므로 당일 삭제
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 11, 19, 30);

        ZonedDateTime deletionAt = PatchReminderSchedule.resolveDeletionAt(createdAt, RETENTION_DAYS);

        assertThat(deletionAt.toLocalDate()).isEqualTo("2026-08-11");
        assertThat(deletionAt.toLocalTime()).isEqualTo("05:00");
    }

    @Test
    @DisplayName("삭제 예정 시각 - 만료가 05:00(KST) 이후면 다음 날 05:00 에 삭제된다")
    void resolveDeletionAt_ExpiresAfterCleanup_NextDay() {
        // 2026-07-13 02:38 UTC = 2026-07-13 11:38 KST → +30일 = 2026-08-12 11:38 KST
        // 그날 05:00 은 이미 지났으므로 다음 날 삭제 (운영 IGISAM_260713 실제 케이스)
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 13, 2, 38, 12);

        ZonedDateTime deletionAt = PatchReminderSchedule.resolveDeletionAt(createdAt, RETENTION_DAYS);

        assertThat(deletionAt.toLocalDate()).isEqualTo("2026-08-13");
        assertThat(deletionAt.toLocalTime()).isEqualTo("05:00");
    }

    @Test
    @DisplayName("삭제 예정 시각 - 만료가 정확히 05:00(KST) 이면 그날 삭제된다")
    void resolveDeletionAt_ExpiresExactlyAtCleanup_SameDay() {
        // 2026-07-12 20:00 UTC = 2026-07-13 05:00 KST → +30일 = 2026-08-12 05:00 KST
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 12, 20, 0);

        ZonedDateTime deletionAt = PatchReminderSchedule.resolveDeletionAt(createdAt, RETENTION_DAYS);

        assertThat(deletionAt.toLocalDate()).isEqualTo("2026-08-12");
    }

    @Test
    @DisplayName("남은 일수 - 운영 실제 케이스(IGISAM_260713)는 2026-08-12 기준 D-1")
    void daysUntilDeletion_RealCase() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 13, 2, 38, 12);
        // 2026-08-12 05:10 KST 실행 = 2026-08-11 20:10 UTC
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 20, 10);

        long remaining = PatchReminderSchedule.daysUntilDeletion(createdAt, RETENTION_DAYS, now);

        assertThat(remaining).isEqualTo(1);
    }

    @ParameterizedTest(name = "D-{0} 은 독촉 대상")
    @ValueSource(longs = {15, 10, 5, 4, 3, 2, 1})
    @DisplayName("마일스톤 잔여일이면 독촉을 보낸다")
    void shouldRemind_OnMilestones(long daysBefore) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 20, 10); // 08-12 05:10 KST
        LocalDateTime createdAt = createdAtForRemaining(daysBefore, now);

        assertThat(PatchReminderSchedule.daysUntilDeletion(createdAt, RETENTION_DAYS, now))
                .isEqualTo(daysBefore);
        assertThat(PatchReminderSchedule.shouldRemind(createdAt, RETENTION_DAYS, now)).isTrue();
    }

    @ParameterizedTest(name = "D-{0} 은 독촉 대상이 아님")
    @ValueSource(longs = {30, 20, 16, 14, 11, 9, 6, 0})
    @DisplayName("마일스톤이 아닌 잔여일에는 보내지 않는다")
    void shouldRemind_OffMilestones(long daysBefore) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 20, 10);
        LocalDateTime createdAt = createdAtForRemaining(daysBefore, now);

        assertThat(PatchReminderSchedule.shouldRemind(createdAt, RETENTION_DAYS, now)).isFalse();
    }

    @Test
    @DisplayName("이미 삭제 시점이 지난 패치는 독촉하지 않는다")
    void shouldRemind_AlreadyExpired() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 20, 10);
        LocalDateTime createdAt = now.minusDays(60);

        assertThat(PatchReminderSchedule.daysUntilDeletion(createdAt, RETENTION_DAYS, now))
                .isNegative();
        assertThat(PatchReminderSchedule.shouldRemind(createdAt, RETENTION_DAYS, now)).isFalse();
    }

    @ParameterizedTest(name = "보관기간 {0}일에서도 마일스톤이 유지된다")
    @CsvSource({"30, 15", "45, 15", "60, 10", "20, 5"})
    @DisplayName("보관기간이 달라져도 삭제예정일 기준으로 판정한다")
    void shouldRemind_DifferentRetention(int retentionDays, long daysBefore) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 20, 10);
        LocalDateTime createdAt = createdAtForRemaining(daysBefore, now, retentionDays);

        assertThat(PatchReminderSchedule.shouldRemind(createdAt, retentionDays, now)).isTrue();
    }

    @Test
    @DisplayName("후보 컷 - D-15 보다 최근에 만들어진 패치는 후보에서 제외된다")
    void earliestCandidateCreatedAt() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 20, 10);

        LocalDateTime cut = PatchReminderSchedule.earliestCandidateCreatedAt(RETENTION_DAYS, now);

        // 30일 보관 / D-15 → 최소 14일 전에 만들어진 패치부터 후보
        assertThat(cut).isEqualTo(now.minusDays(14));
        // 방금 만든 패치는 컷보다 뒤이므로 후보가 아니다
        assertThat(now.isAfter(cut)).isTrue();
    }

    @Test
    @DisplayName("마일스톤 목록은 내림차순 7개")
    void reminderDaysBefore() {
        assertThat(PatchReminderSchedule.reminderDaysBefore())
                .containsExactly(15L, 10L, 5L, 4L, 3L, 2L, 1L);
    }

    // === Helpers ===

    private LocalDateTime createdAtForRemaining(long daysBefore, LocalDateTime now) {
        return createdAtForRemaining(daysBefore, now, RETENTION_DAYS);
    }

    /**
     * 지정한 잔여일이 나오도록 생성 시각을 역산한다.
     *
     * <p>보관기간 만료가 04:00(KST) — 즉 그날 cleanup 직전 — 이 되도록 잡는다.
     * 그래야 삭제 예정일이 밀리지 않고 {@code 오늘 + daysBefore} 로 딱 떨어진다.
     */
    private LocalDateTime createdAtForRemaining(long daysBefore, LocalDateTime now,
            int retentionDays) {
        LocalDate today = now.atZone(UTC).withZoneSameInstant(PatchReminderSchedule.KST)
                .toLocalDate();

        ZonedDateTime expiresAtKst = today.plusDays(daysBefore)
                .atTime(4, 0)
                .atZone(PatchReminderSchedule.KST);

        return expiresAtKst.minusDays(retentionDays)
                .withZoneSameInstant(UTC)
                .toLocalDateTime();
    }
}
