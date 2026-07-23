package com.ts.rm.domain.dashboard.controller;

import com.ts.rm.domain.dashboard.dto.DashboardDto;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * DashboardController Swagger 문서화 인터페이스
 */
@Tag(name = "데이터 분석", description = "데이터 분석 API")
@SwaggerResponse
public interface DashboardControllerDocs {

    @Operation(
            summary = "표준본 최신 릴리즈 버전 조회",
            description = "프로젝트별 표준본(STANDARD) 최신 릴리즈 버전을 조회합니다.\n\n"
                    + "**응답 정보**:\n"
                    + "- 버전 정보, 파일 카테고리 목록\n"
                    + "- 생성자 정보 (이름, 이메일, 아바타)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecentStandardApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<DashboardDto.RecentStandardResponse>> getRecentStandardVersions(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId,

            @Parameter(description = "조회 개수", example = "5")
            @RequestParam(defaultValue = "5") int limit
    );

    @Operation(
            summary = "최신 빌드 버전 조회 (표준+커스텀)",
            description = "프로젝트별 최신 빌드 버전을 조회합니다 (buildVersion > 0).\n\n"
                    + "**응답 정보**:\n"
                    + "- 빌드 라벨 포함 전체 버전 (예: 1.1.0.260501-1)\n"
                    + "- 파일 카테고리 목록\n"
                    + "- 사이트 정보 (CUSTOM 타입인 경우)\n"
                    + "- 생성자 정보 (이름, 이메일, 아바타)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecentBuildApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<DashboardDto.RecentBuildResponse>> getRecentBuildVersions(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId,

            @Parameter(description = "조회 개수", example = "5")
            @RequestParam(defaultValue = "5") int limit
    );

    @Operation(
            summary = "최근 생성 패치 조회 (표준+커스텀)",
            description = "프로젝트별 최근 완료된 패치를 조회합니다 (표준+커스텀 모두).\n\n"
                    + "**필터링**: patch_history 에 기록된 항목 중 patch_file 에서 파일이 삭제된\n"
                    + "정상 완료 패치만 반환합니다.\n\n"
                    + "**응답 정보**:\n"
                    + "- 패치 정보 (이름, 버전 범위, 릴리즈 타입)\n"
                    + "- 사이트 정보 (CUSTOM 타입인 경우)\n"
                    + "- 담당자 정보 (이름, 이메일, 아바타)\n"
                    + "- 생성자 정보 (이름, 이메일, 아바타)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecentPatchApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<DashboardDto.RecentPatchResponse>> getRecentPatches(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId,

            @Parameter(description = "조회 개수", example = "5")
            @RequestParam(defaultValue = "5") int limit
    );

    /**
     * Swagger 스키마용 wrapper 클래스 - 표준본 최신 버전 응답
     */
    @Schema(description = "표준본 최신 릴리즈 버전 API 응답")
    class RecentStandardApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "표준본 최신 버전 데이터")
        public DashboardDto.RecentStandardResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 최신 빌드 버전 응답
     */
    @Schema(description = "최신 빌드 버전 API 응답")
    class RecentBuildApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "최신 빌드 버전 데이터")
        public DashboardDto.RecentBuildResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 최근 패치 응답
     */
    @Schema(description = "최근 생성 패치 API 응답")
    class RecentPatchApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "최근 패치 데이터")
        public DashboardDto.RecentPatchResponse data;
    }
}
