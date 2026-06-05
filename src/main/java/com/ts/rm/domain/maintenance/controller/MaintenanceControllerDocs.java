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
}
