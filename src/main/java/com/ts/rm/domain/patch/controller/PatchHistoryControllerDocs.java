package com.ts.rm.domain.patch.controller;

import com.ts.rm.domain.patch.dto.PatchHistoryDto;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * PatchHistoryController Swagger 문서화 인터페이스
 */
@Tag(name = "패치 이력", description = "패치 이력 조회 API (영구 보존)")
@SwaggerResponse
public interface PatchHistoryControllerDocs {

    @Operation(
            summary = "패치 이력 목록 조회",
            description = """
                    패치 이력을 조회합니다.

                    - 패치 파일(patch_file)이 삭제되어도 이력은 영구 보존됩니다.
                    - projectId: 프로젝트별 필터링 (미입력 시 전체)
                    - siteId: 사이트별 필터링 (미입력 시 전체)
                    - 정렬: createdAt, patchName, fromVersion, toVersion 지원
                    """,
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PatchHistoryListApiResponse.class)
                    )
            )
    )
    ApiResponse<Page<PatchHistoryDto.ListResponse>> listHistories(
            @Parameter(description = "프로젝트 ID (미입력 시 전체)", example = "infraeye2")
            @RequestParam(required = false) String projectId,

            @Parameter(description = "사이트 ID (미입력 시 전체)", example = "1")
            @RequestParam(required = false) Long siteId,

            @ParameterObject Pageable pageable
    );

    @Operation(
            summary = "패치 이력 삭제",
            description = """
                    패치 이력을 삭제합니다.

                    **주의사항**:
                    - 삭제된 이력은 복구할 수 없습니다.
                    - 패치 파일(patch_file)과는 무관하게 이력만 삭제됩니다.
                    """,
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공"
            )
    )
    ApiResponse<Void> deleteHistory(
            @Parameter(description = "이력 ID", example = "1")
            @PathVariable Long id
    );

    /**
     * Swagger 응답 스키마용 클래스
     */
    @Schema(description = "패치 이력 목록 API 응답")
    class PatchHistoryListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "패치 이력 목록 데이터 (페이징)")
        public Page<PatchHistoryDto.ListResponse> data;
    }
}
