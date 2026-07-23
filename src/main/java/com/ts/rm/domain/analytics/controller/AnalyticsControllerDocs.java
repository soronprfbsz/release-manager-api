package com.ts.rm.domain.analytics.controller;

import com.ts.rm.domain.analytics.dto.AnalyticsDto.MonthlyPatchResponse;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.TopSitesResponse;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.VersionSiteDistributionResponse;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AnalyticsController Swagger 문서화 인터페이스
 */
@Tag(name = "데이터 분석", description = "데이터 분석 API")
@SwaggerResponse
public interface AnalyticsControllerDocs {

    @Operation(
            summary = "사이트별 패치 Top-N 조회",
            description = "프로젝트별 최근 n개월간 패치가 가장 많이 나간 사이트 Top-N을 조회합니다.\n\n"
                    + "**파라미터**:\n"
                    + "- `months`: 조회 기간 (개월, 기본값: 6)\n"
                    + "- `topN`: 상위 N개 (기본값: 5)\n\n"
                    + "**참고**:\n"
                    + "- CUSTOM 타입 패치만 집계됩니다 (STANDARD는 사이트 정보 없음)\n"
                    + "- 패치 건수 기준 내림차순 정렬",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TopSitesApiResponse.class)
                    )
            )
    )
    ApiResponse<TopSitesResponse> getTopSitesByPatchCount(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId,

            @Parameter(description = "조회 기간 (개월)", example = "6")
            @RequestParam(defaultValue = "6") int months,

            @Parameter(description = "상위 N개", example = "5")
            @RequestParam(defaultValue = "5") int topN
    );

    @Operation(
            summary = "월별+고객별 패치 통계 조회",
            description = "프로젝트별 최근 n개월간 월별+고객별 패치 생성 건수를 조회합니다.\n\n"
                    + "**파라미터**:\n"
                    + "- `months`: 조회 기간 (개월, 기본값: 6)\n\n"
                    + "**참고**:\n"
                    + "- CUSTOM 타입 패치만 집계 (사이트별 통계)\n"
                    + "- 연월(YYYY-MM) 기준 오름차순 정렬\n"
                    + "- 패치가 없는 월/고객은 0으로 표시\n"
                    + "- siteCounts는 Map<String, Long> 형태로 사이트명을 키로 사용",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MonthlyPatchApiResponse.class),
                            examples = @ExampleObject(
                                    name = "월별+고객별 패치 통계 조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": {
                                                "months": 6,
                                                "sites": ["A회사", "B회사", "C회사"],
                                                "monthly": [
                                                  {
                                                    "yearMonth": "2025-07",
                                                    "siteCounts": {
                                                      "A회사": 3,
                                                      "B회사": 2,
                                                      "C회사": 0
                                                    }
                                                  },
                                                  {
                                                    "yearMonth": "2025-08",
                                                    "siteCounts": {
                                                      "A회사": 2,
                                                      "B회사": 0,
                                                      "C회사": 1
                                                    }
                                                  },
                                                  {
                                                    "yearMonth": "2025-09",
                                                    "siteCounts": {
                                                      "A회사": 5,
                                                      "B회사": 3,
                                                      "C회사": 2
                                                    }
                                                  },
                                                  {
                                                    "yearMonth": "2025-10",
                                                    "siteCounts": {
                                                      "A회사": 1,
                                                      "B회사": 4,
                                                      "C회사": 0
                                                    }
                                                  },
                                                  {
                                                    "yearMonth": "2025-11",
                                                    "siteCounts": {
                                                      "A회사": 4,
                                                      "B회사": 1,
                                                      "C회사": 3
                                                    }
                                                  },
                                                  {
                                                    "yearMonth": "2025-12",
                                                    "siteCounts": {
                                                      "A회사": 2,
                                                      "B회사": 2,
                                                      "C회사": 1
                                                    }
                                                  }
                                                ]
                                              }
                                            }
                                            """
                            )
                    )
            )
    )
    ApiResponse<MonthlyPatchResponse> getMonthlyPatchCounts(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId,

            @Parameter(description = "조회 기간 (개월)", example = "6")
            @RequestParam(defaultValue = "6") int months
    );

    @Operation(
            summary = "버전별 사이트 분포 조회",
            description = "각 사이트의 최신 완료 patch_history.to_version 을 기준으로 버전별로 사이트를 그룹화하여 반환합니다.\n\n"
                    + "**참고**:\n"
                    + "- CUSTOM 타입 패치만 집계 (STANDARD 는 사이트 정보 없음)\n"
                    + "- 버전 내림차순 정렬",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = VersionSiteDistributionApiResponse.class)
                    )
            )
    )
    ApiResponse<VersionSiteDistributionResponse> getVersionSiteDistribution(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId
    );

    /**
     * Swagger 스키마용 wrapper 클래스 - 사이트 Top-N 응답
     */
    @Schema(description = "사이트 Top-N API 응답")
    class TopSitesApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "사이트 Top-N 데이터")
        public TopSitesResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 월별 패치 통계 응답
     */
    @Schema(description = "월별 패치 통계 API 응답")
    class MonthlyPatchApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "월별 패치 통계 데이터")
        public MonthlyPatchResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 버전별 사이트 분포 응답
     */
    @Schema(description = "버전별 사이트 분포 API 응답")
    class VersionSiteDistributionApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "버전별 사이트 분포 데이터")
        public VersionSiteDistributionResponse data;
    }
}
