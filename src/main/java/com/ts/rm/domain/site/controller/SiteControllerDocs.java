package com.ts.rm.domain.site.controller;

import com.ts.rm.domain.site.dto.SiteDto;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * SiteController Swagger 문서화 인터페이스
 */
@Tag(name = "사이트", description = "사이트 관리 API")
@SwaggerResponse
public interface SiteControllerDocs {

    @Operation(
            summary = "사이트 생성",
            description = "새로운 사이트를 생성합니다. Authorization 헤더에 JWT 토큰 필수 (Bearer {token})",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "생성됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SiteDetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<SiteDto.DetailResponse>> createSite(
            @Valid @RequestBody SiteDto.CreateRequest request
    );

    @Operation(
            summary = "사이트 조회 (ID)",
            description = "ID로 사이트 정보를 조회합니다",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SiteDetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<SiteDto.DetailResponse>> getSiteById(
            @Parameter(description = "사이트 ID", required = true)
            @PathVariable Long id
    );

    @Operation(
            summary = "사이트 목록 조회",
            description = "사이트 목록 조회합니다. projectId로 프로젝트별 필터링, isActive로 활성화 여부 필터링, keyword로 사이트명 검색 가능. page, size, sort 파라미터 사용 가능",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SiteListApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<Page<SiteDto.ListResponse>>> getSites(
            @Parameter(description = "프로젝트 ID (예: infraeye1, infraeye2)")
            @RequestParam(required = false) String projectId,

            @Parameter(description = "활성화 여부 (true: 활성화만, false: 비활성화만, null: 전체)")
            @RequestParam(required = false) Boolean isActive,

            @Parameter(description = "사이트명 검색 키워드")
            @RequestParam(required = false) String keyword,

            @ParameterObject Pageable pageable
    );

    @Operation(
            summary = "사이트 정보 수정",
            description = "사이트 정보를 수정합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SiteDetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<SiteDto.DetailResponse>> updateSite(
            @Parameter(description = "사이트 ID", required = true)
            @PathVariable Long id,

            @Valid @RequestBody SiteDto.UpdateRequest request
    );

    @Operation(
            summary = "사이트 삭제",
            description = "사이트를 삭제합니다",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    example = "{\"status\": \"success\", \"data\": null}"
                            )
                    )
            )
    )
    ResponseEntity<ApiResponse<Void>> deleteSite(
            @Parameter(description = "사이트 ID", required = true)
            @PathVariable Long id
    );

    @Operation(
            summary = "사이트 패치 상태 초기화 (ADMIN 전용)",
            description = "사이트의 패치 관련 데이터를 초기화합니다. " +
                    "customer_site_version 삭제, customer_project.last_patched_* 초기화, patch_history 삭제를 수행합니다. " +
                    "ADMIN 권한만 실행 가능합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "초기화 성공",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = SiteResetApiResponse.class)
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403",
                            description = "권한 없음 (ADMIN 전용)"
                    )
            }
    )
    ResponseEntity<ApiResponse<SiteDto.ResetPatchStateResponse>> resetPatchState(
            @Parameter(description = "초기화할 사이트 ID", required = true)
            @PathVariable Long siteId
    );

    /**
     * Swagger 스키마용 wrapper 클래스 - 사이트 상세 응답
     */
    @Schema(description = "사이트 상세 API 응답")
    class SiteDetailApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "사이트 상세 정보")
        public SiteDto.DetailResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 사이트 목록 응답
     */
    @Schema(description = "사이트 목록 API 응답")
    class SiteListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "페이징된 사이트 목록")
        public PageData data;

        @Schema(description = "페이지 데이터")
        static class PageData {
            @Schema(description = "사이트 목록")
            public List<SiteDto.ListResponse> content;

            @Schema(description = "전체 페이지 수", example = "1")
            public int totalPages;

            @Schema(description = "전체 요소 수", example = "10")
            public long totalElements;

            @Schema(description = "페이지 크기", example = "10")
            public int size;

            @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
            public int number;
        }
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 사이트 초기화 응답
     */
    @Schema(description = "사이트 패치 상태 초기화 API 응답")
    class SiteResetApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "초기화 결과")
        public SiteDto.ResetPatchStateResponse data;
    }
}
