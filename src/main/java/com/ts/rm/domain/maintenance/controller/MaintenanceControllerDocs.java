package com.ts.rm.domain.maintenance.controller;

import com.ts.rm.domain.maintenance.dto.MaintenanceResultDto;
import com.ts.rm.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

/**
 * Maintenance Controller Swagger Documentation
 */
@Tag(name = "Maintenance", description = "시스템 유지보수 API")
public interface MaintenanceControllerDocs {

    @Operation(summary = "게시판 유령 이미지 정리",
            description = "게시글에 연결되지 않고 일정 시간이 지난 이미지를 삭제합니다. 스케줄러 내부 호출 또는 인증된 사용자만 접근 가능합니다.")
    ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupBoardImages(
            @Parameter(description = "보관 시간 (시간)", example = "24") int retentionHours,
            @Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "스케줄 실행 이력 정리",
            description = "일정 기간이 지난 스케줄 실행 이력을 삭제합니다. 스케줄러 내부 호출 또는 인증된 사용자만 접근 가능합니다.")
    ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupScheduleHistories(
            @Parameter(description = "보관 기간 (일)", example = "90") int retentionDays,
            @Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "API 로그 정리",
            description = "일정 기간이 지난 API 로그를 삭제합니다. 스케줄러 내부 호출 또는 인증된 사용자만 접근 가능합니다.")
    ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupApiLogs(
            @Parameter(description = "보관 기간 (일)", example = "30") int retentionDays,
            @Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "오래된 패치 파일 정리",
            description = "생성 후 일정 기간이 지난 패치 파일(디렉토리 + DB 레코드)을 삭제합니다. 스케줄러 내부 호출 또는 인증된 사용자만 접근 가능합니다.")
    ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupPatches(
            @Parameter(description = "보관 기간 (일)", example = "30") int retentionDays,
            @Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "orphan 릴리즈 디렉토리 정리",
            description = "DB(release_version)에 없는 버전/빌드/핫픽스 디렉토리를 정리합니다. "
                    + "NAS(SMB) 핸들 지연으로 best-effort 삭제가 남긴 잔존물이 대상이며, "
                    + "quiet 시간 내에 변경된 디렉토리는 건너뜁니다. 스케줄러 내부 호출 또는 인증된 사용자만 접근 가능합니다.")
    ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> cleanupOrphanDirectories(
            @Parameter(description = "이 시간 이상 변경 없는 디렉토리만 삭제 (시간)", example = "24") int quietHours,
            @Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "패치 처리 독촉 발송",
            description = "자동 삭제 예정일까지 남은 일수가 마일스톤(D-15/10/5/4/3/2/1)에 해당하는 "
                    + "미처리 패치의 생성자에게 독촉 메시지를 발송합니다. 같은 날 중복 발송은 "
                    + "멱등 키로 차단됩니다.")
    ResponseEntity<ApiResponse<MaintenanceResultDto.CleanupResult>> sendPatchReminders(
            @Parameter(hidden = true) HttpServletRequest request);
}
