package com.ts.rm.global.progress.controller;

import com.ts.rm.global.progress.dto.ServerProgressDto;
import com.ts.rm.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 서버 작업 진행 상황 조회 API Swagger 문서화 인터페이스.
 */
@Tag(name = "Progress", description = "서버 작업 진행 상황 polling API")
public interface ProgressControllerDocs {

    @Operation(
            summary = "서버 작업 진행 상황 조회",
            description = """
                    X-Progress-Id 헤더로 전달한 UUID 와 동일한 ID 로 진행 상황을 조회합니다.

                    frontend 는 mutation 호출 직후부터 1초 간격으로 polling 하고,
                    completed=true 를 받으면 polling 을 중단합니다.

                    대상 작업:
                    - 패치 생성 (POST /api/patches/*/generate)
                    - 표준 버전 업로드 (POST /api/releases/versions/standard)
                    - 커스텀 버전 업로드 (POST /api/releases/versions/custom)
                    - 빌드 생성 (POST /api/releases/versions/{id}/builds)
                    """
    )
    ApiResponse<ServerProgressDto.ProgressResponse> getProgress(
            @Parameter(description = "X-Progress-Id 헤더로 전달한 UUID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            String progressId);
}
