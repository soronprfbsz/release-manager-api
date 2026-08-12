package com.ts.rm.domain.maintenance.controller;

import com.ts.rm.domain.maintenance.dto.MaintenanceResultDto;
import com.ts.rm.domain.maintenance.service.BoardImageCleanupService;
import com.ts.rm.domain.maintenance.service.ReleaseDirectoryCleanupService;
import com.ts.rm.domain.message.service.PatchReminderService;
import com.ts.rm.domain.patch.service.PatchService;
import com.ts.rm.domain.scheduler.service.ScheduleJobHistoryService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.logging.service.ApiLogService;
import com.ts.rm.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maintenance Controller
 *
 * <p>시스템 유지보수 API (스케줄러에서 호출)
 */
@Slf4j
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController implements MaintenanceControllerDocs {

    private final BoardImageCleanupService boardImageCleanupService;
    private final ScheduleJobHistoryService scheduleJobHistoryService;
    private final ApiLogService apiLogService;
    private final PatchService patchService;
    private final ReleaseDirectoryCleanupService releaseDirectoryCleanupService;
    private final PatchReminderService patchReminderService;

    private static final String SCHEDULER_HEADER = "X-Schedule-Job";

    /**
     * 게시판 유령 이미지 정리
     *
     * <p>post_id가 NULL이고 업로드 후 일정 시간이 지난 이미지를 삭제
     *
     * @param retentionHours 보관 시간 (기본값: 24시간)
     */
    @Override
    @DeleteMapping("/board-images")
    public ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupBoardImages(
            @RequestParam(defaultValue = "24") int retentionHours,
            HttpServletRequest request) {
        validateMaintenanceAccess(request);
        log.info("게시판 유령 이미지 정리 API 호출 - retentionHours: {}", retentionHours);
        MaintenanceResultDto.CleanupResult result = boardImageCleanupService.cleanupOrphanImages(retentionHours);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 스케줄 실행 이력 정리
     *
     * <p>일정 기간이 지난 스케줄 실행 이력을 삭제
     *
     * @param retentionDays 보관 기간 (기본값: 90일)
     */
    @Override
    @DeleteMapping("/schedule-histories")
    public ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupScheduleHistories(
            @RequestParam(defaultValue = "90") int retentionDays,
            HttpServletRequest request) {
        validateMaintenanceAccess(request);
        log.info("스케줄 실행 이력 정리 API 호출 - retentionDays: {}", retentionDays);

        int deletedCount = scheduleJobHistoryService.deleteOldHistories(retentionDays);

        MaintenanceResultDto.CleanupResult result = MaintenanceResultDto.CleanupResult.of(
                "schedule-histories-cleanup",
                deletedCount,
                String.format("%d일 이상 지난 스케줄 실행 이력 %d건 삭제 완료", retentionDays, deletedCount));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * API 로그 정리
     *
     * <p>일정 기간이 지난 API 로그를 삭제
     *
     * @param retentionDays 보관 기간 (기본값: 30일)
     */
    @Override
    @DeleteMapping("/api-logs")
    public ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupApiLogs(
            @RequestParam(defaultValue = "30") int retentionDays,
            HttpServletRequest request) {
        validateMaintenanceAccess(request);
        log.info("API 로그 정리 API 호출 - retentionDays: {}", retentionDays);

        long deletedCount = apiLogService.deleteOldLogs(retentionDays);

        MaintenanceResultDto.CleanupResult result = MaintenanceResultDto.CleanupResult.of(
                "api-log-cleanup",
                (int) deletedCount,
                String.format("%d일 이상 지난 API 로그 %d건 삭제 완료", retentionDays, deletedCount));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 오래된 패치 파일 정리
     *
     * <p>생성 후 일정 기간이 지난 패치 파일(디렉토리 + patch_file row)을 삭제
     *
     * @param retentionDays 보관 기간 (기본값: patch.cleanup.retention-days, 미설정 시 30일)
     */
    @Override
    @DeleteMapping("/patches")
    public ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupPatches(
            @RequestParam(defaultValue = "${patch.cleanup.retention-days:30}") int retentionDays,
            HttpServletRequest request) {
        validateMaintenanceAccess(request);
        log.info("오래된 패치 정리 API 호출 - retentionDays: {}", retentionDays);

        long deletedCount = patchService.deleteOldPatches(retentionDays);

        MaintenanceResultDto.CleanupResult result = MaintenanceResultDto.CleanupResult.of(
                "patch-cleanup",
                (int) deletedCount,
                String.format("%d일 이상 지난 패치 %d건 삭제 완료", retentionDays, deletedCount));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * orphan 릴리즈 디렉토리 정리
     *
     * <p>DB(release_version)에 없는 버전/빌드/핫픽스 디렉토리 삭제.
     * NAS(SMB) 핸들 지연으로 best-effort 삭제가 남긴 잔존물 정리 (#SMB핸들)
     *
     * @param quietHours 이 시간 이상 변경이 없는 디렉토리만 삭제 (기본값: 24시간, 진행 중 업로드 보호)
     */
    @Override
    @DeleteMapping("/orphan-directories")
    public ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupOrphanDirectories(
            @RequestParam(defaultValue = "24") int quietHours,
            HttpServletRequest request) {
        validateMaintenanceAccess(request);
        log.info("orphan 디렉토리 정리 API 호출 - quietHours: {}", quietHours);
        MaintenanceResultDto.CleanupResult result =
                releaseDirectoryCleanupService.cleanupOrphanDirectories(quietHours);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 패치 처리 독촉 발송
     *
     * <p>자동 삭제 예정일까지 남은 일수가 마일스톤(D-15/10/5/4/3/2/1)에 해당하는
     * 미처리 패치의 생성자에게 독촉 메시지를 보낸다.
     *
     * <p>정리(cleanup)가 아니라 발송이므로 POST 를 쓴다.
     */
    @Override
    @PostMapping("/patch-reminders")
    public ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> sendPatchReminders(
            HttpServletRequest request) {
        validateMaintenanceAccess(request);
        log.info("패치 독촉 발송 API 호출");

        int sentCount = patchReminderService.sendReminders(LocalDateTime.now());

        MaintenanceResultDto.CleanupResult result = MaintenanceResultDto.CleanupResult.of(
                "patch-reminder",
                sentCount,
                String.format("패치 처리 독촉 %d건 발송 완료", sentCount));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 유지보수 API 접근 검증
     *
     * <p>스케줄러 내부 호출(X-Scheduled-Job 헤더) 또는 인증된 사용자만 접근 가능
     */
    private void validateMaintenanceAccess(HttpServletRequest request) {
        // 스케줄러 내부 호출인 경우 허용
        String schedulerHeader = request.getHeader(SCHEDULER_HEADER);
        if (schedulerHeader != null && !schedulerHeader.isBlank()) {
            log.debug("스케줄러 내부 호출 - jobName: {}", schedulerHeader);
            return;
        }

        // 인증된 사용자인 경우 허용
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            log.debug("인증된 사용자 호출 - user: {}", authentication.getName());
            return;
        }

        // 그 외의 경우 거부
        throw new BusinessException(ErrorCode.FORBIDDEN, "유지보수 API 접근 권한이 없습니다.");
    }
}
