package com.ts.rm.domain.patch.controller;

import com.ts.rm.domain.patch.dto.PatchHistoryDto;
import com.ts.rm.domain.patch.service.PatchHistoryService;
import com.ts.rm.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 패치 이력 API Controller
 *
 * <p>패치 이력 조회 전용 (patch_file 삭제와 무관하게 영구 보존된 이력)
 */
@Slf4j
@RestController
@RequestMapping("/api/patch-histories")
@RequiredArgsConstructor
public class PatchHistoryController implements PatchHistoryControllerDocs {

    private final PatchHistoryService patchHistoryService;

    /**
     * 패치 이력 목록 조회 (필터링 + 페이징)
     */
    @Override
    @GetMapping
    public ApiResponse<Page<PatchHistoryDto.ListResponse>> listHistories(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Long siteId,
            @ParameterObject Pageable pageable) {

        log.info("패치 이력 목록 조회 요청 - projectId: {}, siteId: {}", projectId, siteId);

        Page<PatchHistoryDto.ListResponse> histories = patchHistoryService.listHistoriesWithPaging(
                projectId, siteId, pageable);

        return ApiResponse.success(histories);
    }

    /**
     * 패치 이력 삭제
     */
    @Override
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteHistory(@PathVariable Long id) {

        log.info("패치 이력 삭제 요청 - historyId: {}", id);

        patchHistoryService.deleteHistory(id);

        return ApiResponse.success(null);
    }
}
