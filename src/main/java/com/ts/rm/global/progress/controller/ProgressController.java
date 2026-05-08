package com.ts.rm.global.progress.controller;

import com.ts.rm.global.progress.ServerProgressService;
import com.ts.rm.global.progress.dto.ServerProgressDto;
import com.ts.rm.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 작업 진행 상황 공용 조회 컨트롤러.
 *
 * <p>패치 생성 / 버전 업로드 / 빌드 업로드 등 장시간 수행 API 의
 * 진행 상황을 frontend 가 polling 할 때 사용.
 */
@Slf4j
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController implements ProgressControllerDocs {

    private final ServerProgressService progressService;

    /**
     * 서버 작업 진행 상황 조회 (frontend polling).
     *
     * <p>frontend 가 mutation 호출 시 생성한 progressId 와 같은 ID 로 GET.
     * 진행 중이면 step/totalSteps/message 반환, 끝나면 completed=true.
     * 미존재 progressId 는 null 응답 — frontend 가 시작 전이거나 만료된 상태로 해석.
     */
    @Override
    @GetMapping("/{progressId}")
    public ApiResponse<ServerProgressDto.ProgressResponse> getProgress(
            @PathVariable String progressId) {
        log.debug("진행 상황 조회 요청 - progressId: {}", progressId);
        return ApiResponse.success(progressService.get(progressId));
    }
}
