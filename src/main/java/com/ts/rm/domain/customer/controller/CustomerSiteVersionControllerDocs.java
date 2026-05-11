package com.ts.rm.domain.customer.controller;

import com.ts.rm.domain.customer.dto.CustomerSiteVersionDto;
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
 * CustomerSiteVersionController Swagger 문서화 인터페이스
 */
@Tag(name = "사이트 버전", description = "사이트별 컴포넌트 현재 버전 조회 API")
@SwaggerResponse
public interface CustomerSiteVersionControllerDocs {

    @Operation(
            summary = "사이트 컴포넌트 현재 버전 조회",
            description = "고객사-프로젝트 조합의 BASE/WEB/ENGINE 컴포넌트별 현재 버전을 조회합니다.\n\n"
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
    ApiResponse<List<CustomerSiteVersionDto.SiteVersionResponse>> getSiteVersions(
            @Parameter(description = "고객사 ID", example = "1", required = true)
            @PathVariable Long customerId,

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
        public List<CustomerSiteVersionDto.SiteVersionResponse> data;
    }
}
