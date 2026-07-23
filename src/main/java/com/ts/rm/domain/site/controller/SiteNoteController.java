package com.ts.rm.domain.site.controller;

import com.ts.rm.domain.site.dto.SiteNoteDto;
import com.ts.rm.domain.site.service.SiteNoteService;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.security.SecurityUtil;
import com.ts.rm.global.security.TokenInfo;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SiteNote Controller
 *
 * <p>사이트 특이사항 관리 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/sites/{siteId}/notes")
@RequiredArgsConstructor
public class SiteNoteController implements SiteNoteControllerDocs {

    private final SiteNoteService siteNoteService;

    /**
     * 특이사항 목록 조회
     *
     * @param siteId 사이트 ID
     * @return 특이사항 목록
     */
    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<List<SiteNoteDto.Response>>> getNotes(
            @PathVariable Long siteId) {

        log.info("특이사항 목록 조회 요청 - siteId: {}", siteId);

        List<SiteNoteDto.Response> response = siteNoteService.getNotes(siteId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 특이사항 생성
     *
     * @param siteId 사이트 ID
     * @param request    생성 요청
     * @return 생성된 특이사항
     */
    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<SiteNoteDto.Response>> createNote(
            @PathVariable Long siteId,
            @Valid @RequestBody SiteNoteDto.CreateRequest request) {

        log.info("특이사항 생성 요청 - siteId: {}, title: {}", siteId, request.title());

        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();

        SiteNoteDto.Response response = siteNoteService.createNote(
                siteId, request, tokenInfo.email());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * 특이사항 수정
     *
     * <p>ADMIN 역할이거나 작성자만 수정 가능
     *
     * @param siteId 사이트 ID
     * @param noteId     특이사항 ID
     * @param request    수정 요청
     * @return 수정된 특이사항
     */
    @Override
    @PutMapping("/{noteId}")
    public ResponseEntity<ApiResponse<SiteNoteDto.Response>> updateNote(
            @PathVariable Long siteId,
            @PathVariable Long noteId,
            @Valid @RequestBody SiteNoteDto.UpdateRequest request) {

        log.info("특이사항 수정 요청 - siteId: {}, noteId: {}", siteId, noteId);

        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();

        SiteNoteDto.Response response = siteNoteService.updateNote(
                siteId, noteId, request, tokenInfo.email(), tokenInfo.role());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 특이사항 삭제
     *
     * <p>ADMIN 역할이거나 작성자만 삭제 가능
     *
     * @param siteId 사이트 ID
     * @param noteId     특이사항 ID
     * @return 성공 응답
     */
    @Override
    @DeleteMapping("/{noteId}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @PathVariable Long siteId,
            @PathVariable Long noteId) {

        log.info("특이사항 삭제 요청 - siteId: {}, noteId: {}", siteId, noteId);

        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();

        siteNoteService.deleteNote(siteId, noteId, tokenInfo.email(), tokenInfo.role());

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
