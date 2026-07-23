package com.ts.rm.domain.site.controller;

import com.ts.rm.domain.site.dto.SiteVersionDto;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * SiteVersionController Swagger 문서화 인터페이스
 */
@Tag(name = "사이트 버전", description = "사이트별 컴포넌트 현재 버전 조회 API")
@SwaggerResponse
public interface SiteVersionControllerDocs {

    @Operation(
            summary = "사이트 컴포넌트 현재 버전 조회",
            description = "사이트-프로젝트 조합의 BASE/WEB/ENGINE 컴포넌트별 현재 버전을 조회합니다.\n\n"
                    + "**컴포넌트 구분**:\n"
                    + "- `BASE` — DB 버전 (major.minor.patch, 예: 1.1.0)\n"
                    + "- `WEB` — WAS 빌드 버전 (fullVersion, 예: 1.1.0.260511-1)\n"
                    + "- `ENGINE` — 엔진 빌드 버전 (fullVersion, 예: 1.1.0.260511-1)\n\n"
                    + "패치 완료(POST /api/patches/{id}/complete) 시점마다 갱신됩니다.\n"
                    + "빌드 미포함 패치 완료 시 BASE 만 갱신되고 WEB/ENGINE 은 이전 값 유지.\n"
                    + "아직 패치 완료 이력이 없으면 빈 배열을 반환합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SiteVersionListApiResponse.class)
                    )
            )
    )
    ApiResponse<List<SiteVersionDto.SiteVersionResponse>> getSiteVersions(
            @Parameter(description = "사이트 ID", example = "1", required = true)
            @PathVariable Long siteId,

            @Parameter(description = "프로젝트 ID", example = "infraeye2", required = true)
            @PathVariable String projectId
    );

    @Operation(
            summary = "다음 패치 범위 자동 제안",
            description = "패치 생성 폼의 from/to 버전을 자동으로 채우기 위한 제안 값을 반환합니다.\n\n"
                    + "**반환 규칙**:\n"
                    + "- `currentVersion`: `customer_project.last_patched_version` 의 base(major.minor.patch). "
                    + "패치 이력 없으면 null.\n"
                    + "- `suggestedFromVersion`: 현재 버전 직후 표준 base 버전. "
                    + "사이트가 이미 최신이거나 등록 버전 없으면 null.\n"
                    + "- `suggestedToVersion`: 프로젝트 최신 승인 표준 base 버전. "
                    + "등록 버전 없으면 null.\n\n"
                    + "**null 케이스 요약**:\n"
                    + "- 사이트가 이미 최신 버전 → `suggestedFromVersion` null\n"
                    + "- 프로젝트에 승인 버전 없음 → `suggestedToVersion` null\n"
                    + "- 아직 패치 이력 없음 → `currentVersion` null, `suggestedFromVersion` 가장 오래된 버전",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NextPatchRangeApiResponse.class)
                    )
            )
    )
    ApiResponse<SiteVersionDto.NextPatchRangeResponse> getNextPatchRange(
            @Parameter(description = "사이트 ID", example = "1", required = true)
            @PathVariable Long siteId,

            @Parameter(description = "프로젝트 ID", example = "infraeye2", required = true)
            @PathVariable String projectId
    );

    /**
     * Swagger 스키마용 wrapper 클래스
     */
    @Schema(description = "사이트 버전 목록 API 응답")
    class SiteVersionListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "컴포넌트 버전 목록")
        public List<SiteVersionDto.SiteVersionResponse> data;
    }

    @Schema(description = "다음 패치 범위 자동 제안 API 응답")
    class NextPatchRangeApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "다음 패치 범위 제안")
        public SiteVersionDto.NextPatchRangeResponse data;
    }
}
