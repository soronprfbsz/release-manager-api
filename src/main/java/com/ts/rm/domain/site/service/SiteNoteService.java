package com.ts.rm.domain.site.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.site.dto.SiteNoteDto;
import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.site.entity.SiteNote;
import com.ts.rm.domain.site.mapper.SiteNoteDtoMapper;
import com.ts.rm.domain.site.repository.SiteNoteRepository;
import com.ts.rm.domain.site.repository.SiteRepository;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SiteNote Service
 *
 * <p>사이트 특이사항 관리 비즈니스 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteNoteService {

    private final SiteNoteRepository siteNoteRepository;
    private final SiteRepository siteRepository;
    private final SiteNoteDtoMapper mapper;
    private final AccountLookupService accountLookupService;

    /**
     * 사이트 특이사항 목록 조회
     *
     * @param siteId 사이트 ID
     * @return 특이사항 목록 (최신순)
     */
    public List<SiteNoteDto.Response> getNotes(Long siteId) {
        log.debug("특이사항 목록 조회 - siteId: {}", siteId);

        // 사이트 존재 확인
        validateSiteExists(siteId);

        List<SiteNote> notes = siteNoteRepository.findAllBySite_SiteIdOrderByCreatedAtDesc(siteId);
        return mapper.toResponseList(notes);
    }

    /**
     * 사이트 특이사항 생성
     *
     * @param siteId     사이트 ID
     * @param request        생성 요청
     * @param createdByEmail 생성자 이메일
     * @return 생성된 특이사항
     */
    @Transactional
    public SiteNoteDto.Response createNote(Long siteId, SiteNoteDto.CreateRequest request,
            String createdByEmail) {
        log.info("특이사항 생성 - siteId: {}, title: {}", siteId, request.title());

        // 사이트 존재 확인
        Site site = findSiteById(siteId);

        // 생성자 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        // 엔티티 생성 및 저장
        SiteNote note = mapper.toEntity(request);
        note.setSite(site);
        note.setCreator(creator);
        note.setCreatedByEmail(creator.getEmail());
        note.setUpdater(creator);
        note.setUpdatedByEmail(creator.getEmail());

        SiteNote savedNote = siteNoteRepository.save(note);

        log.info("특이사항 생성 완료 - noteId: {}", savedNote.getNoteId());
        return mapper.toResponse(savedNote);
    }

    /**
     * 사이트 특이사항 수정
     *
     * <p>ADMIN 역할이거나 작성자만 수정 가능
     *
     * @param siteId     사이트 ID
     * @param noteId         특이사항 ID
     * @param request        수정 요청
     * @param updatedByEmail 수정자 이메일
     * @param role           수정자 역할
     * @return 수정된 특이사항
     */
    @Transactional
    public SiteNoteDto.Response updateNote(Long siteId, Long noteId,
            SiteNoteDto.UpdateRequest request, String updatedByEmail, String role) {
        log.info("특이사항 수정 - siteId: {}, noteId: {}", siteId, noteId);

        // 특이사항 조회 및 사이트 일치 검증
        SiteNote note = findNoteByIdAndSiteId(noteId, siteId);

        // 권한 검증: ADMIN이거나 작성자만 수정 가능
        validateModifyPermission(note, updatedByEmail, role);

        // 수정자 조회
        Account updater = accountLookupService.findByEmail(updatedByEmail);

        // 수정 (null이 아닌 필드만)
        if (request.title() != null) {
            note.setTitle(request.title());
        }
        if (request.content() != null) {
            note.setContent(request.content());
        }
        note.setUpdater(updater);
        note.setUpdatedByEmail(updater.getEmail());

        log.info("특이사항 수정 완료 - noteId: {}", noteId);
        return mapper.toResponse(note);
    }

    /**
     * 사이트 특이사항 삭제
     *
     * <p>ADMIN 역할이거나 작성자만 삭제 가능
     *
     * @param siteId    사이트 ID
     * @param noteId        특이사항 ID
     * @param requestEmail  요청자 이메일
     * @param role          요청자 역할
     */
    @Transactional
    public void deleteNote(Long siteId, Long noteId, String requestEmail, String role) {
        log.info("특이사항 삭제 - siteId: {}, noteId: {}", siteId, noteId);

        // 특이사항 조회 및 사이트 일치 검증
        SiteNote note = findNoteByIdAndSiteId(noteId, siteId);

        // 권한 검증: ADMIN이거나 작성자만 삭제 가능
        validateModifyPermission(note, requestEmail, role);

        siteNoteRepository.delete(note);

        log.info("특이사항 삭제 완료 - noteId: {}", noteId);
    }

    // === Private Helper Methods ===

    private static final String ROLE_ADMIN = "ADMIN";

    /**
     * 수정/삭제 권한 검증
     *
     * <p>ADMIN 역할이거나 작성자만 수정/삭제 가능
     *
     * @param note         특이사항 엔티티
     * @param requestEmail 요청자 이메일
     * @param role         요청자 역할
     */
    private void validateModifyPermission(SiteNote note, String requestEmail, String role) {
        // ADMIN은 모든 특이사항 수정/삭제 가능
        if (ROLE_ADMIN.equals(role)) {
            return;
        }

        // 작성자 본인만 수정/삭제 가능
        if (note.getCreatedByEmail() != null && note.getCreatedByEmail().equals(requestEmail)) {
            return;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN,
                "특이사항 수정/삭제 권한이 없습니다. ADMIN 또는 작성자만 가능합니다.");
    }

    /**
     * 사이트 존재 확인
     */
    private void validateSiteExists(Long siteId) {
        if (!siteRepository.existsById(siteId)) {
            throw new BusinessException(ErrorCode.SITE_NOT_FOUND);
        }
    }

    /**
     * 사이트 조회
     */
    private Site findSiteById(Long siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_NOT_FOUND));
    }

    /**
     * 특이사항 조회 및 사이트 일치 검증
     */
    private SiteNote findNoteByIdAndSiteId(Long noteId, Long siteId) {
        SiteNote note = siteNoteRepository.findById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "특이사항을 찾을 수 없습니다: " + noteId));

        // 사이트 ID 일치 검증
        if (!note.getSite().getSiteId().equals(siteId)) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "해당 사이트의 특이사항이 아닙니다: noteId=" + noteId + ", siteId=" + siteId);
        }

        return note;
    }
}
