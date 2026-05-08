package com.ts.rm.global.progress.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 서버 진행 상황 공용 DTO.
 *
 * <p>패치 생성 / 버전 업로드 / 빌드 업로드 등 장시간 수행 API 의
 * 진행 상황을 frontend 가 polling 할 때 사용하는 응답 DTO.
 */
public class ServerProgressDto {

    private ServerProgressDto() {}

    /**
     * 진행 상황 polling 응답.
     *
     * <p>frontend 가 mutation 호출 시 X-Progress-Id 헤더로 보낸 UUID 를 키로 조회.
     */
    @Schema(description = "서버 작업 진행 상황")
    public record ProgressResponse(
            @Schema(description = "현재 단계 (1-based)", example = "3")
            int step,

            @Schema(description = "총 단계 수", example = "8")
            int totalSteps,

            @Schema(description = "현재 단계 메시지", example = "파일 복사 중")
            String message,

            @Schema(description = "완료 여부 — true 면 frontend polling 중단", example = "false")
            boolean completed
    ) {}
}
