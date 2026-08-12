package com.ts.rm.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.config.AbstractTestBase;
import com.ts.rm.config.TestQueryDslConfig;
import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.enums.AccountRole;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.message.dto.MessageDto;
import com.ts.rm.domain.message.enums.MessageType;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.patch.util.PatchReminderSchedule;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 패치 독촉 발송 통합 테스트
 *
 * <p>패치 생성 시각을 조작하는 대신 <b>기준 시각(nowUtc)을 미래로 옮겨</b> 마일스톤을
 * 재현한다. {@code created_at} 은 {@code @CreatedDate updatable=false} 라 JPA 로는
 * 바꿀 수 없기 때문이다.
 */
@Import(TestQueryDslConfig.class)
@Transactional
@DisplayName("PatchReminderService 테스트")
class PatchReminderServiceTest extends AbstractTestBase {

    private static final int RETENTION_DAYS = 30;
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Autowired
    private PatchReminderService patchReminderService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private PatchRepository patchRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account systemSender;
    private Account creator;
    private Project project;

    @BeforeEach
    void setUp() {
        systemSender = saveAccount("admin@tscientific.co.kr", "시스템 관리자");
        creator = saveAccount("creator@test.com", "생성자");
        project = projectRepository.save(Project.builder()
                .projectId("test-project")
                .projectName("테스트 프로젝트")
                .build());

        ReflectionTestUtils.setField(patchReminderService, "retentionDays", RETENTION_DAYS);
        ReflectionTestUtils.setField(
                patchReminderService, "systemSenderEmail", systemSender.getEmail());
    }

    @Test
    @DisplayName("D-15 시점 - 생성자에게 독촉이 발송된다")
    void sendReminders_OnMilestone() {
        // given
        Patch patch = savePatch("demo_260812");

        // when
        int sent = patchReminderService.sendReminders(nowAtDaysBefore(patch, 15));

        // then
        assertThat(sent).isEqualTo(1);

        List<MessageDto.InboxItem> inbox = inboxOf(creator);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).messageType()).isEqualTo(MessageType.PATCH_REMINDER);
        assertThat(inbox.get(0).senderName()).isEqualTo("시스템 관리자");
        assertThat(inbox.get(0).title()).contains("demo_260812").contains("15일 후");
        assertThat(inbox.get(0).refId()).isEqualTo(patch.getPatchId());
        assertThat(inbox.get(0).refProjectId()).isEqualTo("test-project");
        assertThat(inbox.get(0).readAt()).isNull();

        assertThat(messageService.countUnread(creator.getAccountId())).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 날 재실행 - 멱등 키로 중복 발송이 차단된다")
    void sendReminders_SameDayTwice_Deduplicated() {
        // given
        Patch patch = savePatch("demo_260812");
        LocalDateTime now = nowAtDaysBefore(patch, 15);

        // when — 스케줄러 "지금 실행"을 두 번 누른 상황
        int first = patchReminderService.sendReminders(now);
        int second = patchReminderService.sendReminders(now.plusMinutes(30));

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(inboxOf(creator)).hasSize(1);
    }

    @Test
    @DisplayName("마일스톤이 아닌 날 - 발송하지 않는다")
    void sendReminders_OffMilestone() {
        // given
        Patch patch = savePatch("demo_260812");

        // when
        int sent = patchReminderService.sendReminders(nowAtDaysBefore(patch, 14));

        // then
        assertThat(sent).isZero();
        assertThat(inboxOf(creator)).isEmpty();
    }

    @Test
    @DisplayName("마일스톤 7회 - 각 시점마다 한 번씩, 총 7건이 쌓인다")
    void sendReminders_AllMilestones() {
        // given
        Patch patch = savePatch("demo_260812");

        // when — 30일 동안 매일 스케줄이 돈다고 가정
        int totalSent = 0;
        for (long daysBefore = 29; daysBefore >= 1; daysBefore--) {
            totalSent += patchReminderService.sendReminders(nowAtDaysBefore(patch, daysBefore));
        }

        // then — 마일스톤에 해당하는 7일에만 발송된다
        assertThat(totalSent).isEqualTo(7);
        assertThat(inboxOf(creator)).hasSize(7);
    }

    @Test
    @DisplayName("생성자 계정이 없는 패치 - 건너뛰고 나머지는 계속 발송한다")
    void sendReminders_PatchWithoutCreator_Skipped() {
        // given
        Patch orphan = savePatch("orphan_260812");
        orphan.setCreator(null);
        patchRepository.saveAndFlush(orphan);
        Patch normal = savePatch("normal_260812");

        // when
        int sent = patchReminderService.sendReminders(nowAtDaysBefore(normal, 15));

        // then
        assertThat(sent).isEqualTo(1);
        assertThat(inboxOf(creator)).hasSize(1);
    }

    @Test
    @DisplayName("시스템 발신 계정을 못 찾으면 - 예외 없이 0건으로 끝난다")
    void sendReminders_MissingSystemSender_ReturnsZero() {
        // given
        savePatch("demo_260812");
        ReflectionTestUtils.setField(
                patchReminderService, "systemSenderEmail", "nobody@nowhere.com");

        // when
        int sent = patchReminderService.sendReminders(LocalDateTime.now());

        // then
        assertThat(sent).isZero();
    }

    @Test
    @DisplayName("패치 처리 시 - 남아 있던 독촉이 수신함과 배지에서 사라진다")
    void hideRemindersFor() {
        // given
        Patch patch = savePatch("demo_260812");
        patchReminderService.sendReminders(nowAtDaysBefore(patch, 15));
        assertThat(messageService.countUnread(creator.getAccountId())).isEqualTo(1);

        // when
        patchReminderService.hideRemindersFor(patch.getPatchId());

        // then
        assertThat(messageService.countUnread(creator.getAccountId())).isZero();
        assertThat(inboxOf(creator)).isEmpty();
    }

    @Test
    @DisplayName("독촉 본문 - 생성일시와 삭제 예정일이 들어간다")
    void reminderContent() {
        // given
        Patch patch = savePatch("demo_260812");

        // when
        patchReminderService.sendReminders(nowAtDaysBefore(patch, 15));

        // then
        String content = inboxOf(creator).get(0).content();
        String deletionDate = PatchReminderSchedule
                .resolveDeletionAt(patch.getCreatedAt(), RETENTION_DAYS)
                .toLocalDate()
                .toString();

        assertThat(content)
                .contains("demo_260812")
                .contains("테스트 프로젝트")
                .contains("1.0.0")
                .contains("1.1.0")
                .contains(deletionDate)
                .contains("복구할 수 없습니다");
    }

    // === Helpers ===

    /**
     * 해당 패치의 삭제 예정일 기준 {@code daysBefore} 일 전, 스케줄 실행 시각(05:10 KST)을
     * UTC 로 돌려준다.
     */
    private LocalDateTime nowAtDaysBefore(Patch patch, long daysBefore) {
        LocalDate deletionDate = PatchReminderSchedule
                .resolveDeletionAt(patch.getCreatedAt(), RETENTION_DAYS)
                .toLocalDate();

        return deletionDate.minusDays(daysBefore)
                .atTime(5, 10)
                .atZone(PatchReminderSchedule.KST)
                .withZoneSameInstant(UTC)
                .toLocalDateTime();
    }

    private List<MessageDto.InboxItem> inboxOf(Account account) {
        return messageService
                .getInbox(account.getAccountId(), false, null, PageRequest.of(0, 20))
                .getContent();
    }

    private Patch savePatch(String patchName) {
        return patchRepository.saveAndFlush(Patch.builder()
                .project(project)
                .releaseType("STANDARD")
                .fromVersion("1.0.0")
                .toVersion("1.1.0")
                .patchName(patchName)
                .outputPath("patches/" + patchName)
                .creator(creator)
                .createdByEmail(creator.getEmail())
                .build());
    }

    private Account saveAccount(String email, String name) {
        return accountRepository.save(Account.builder()
                .email(email)
                .password("encoded")
                .accountName(name)
                .role(AccountRole.USER.getCodeId())
                .status(AccountStatus.ACTIVE.name())
                .build());
    }
}
