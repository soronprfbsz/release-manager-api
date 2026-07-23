package com.ts.rm.domain.patch.service;

import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.site.entity.SiteProject;
import com.ts.rm.domain.site.repository.SiteProjectRepository;
import com.ts.rm.domain.site.service.SiteVersionService;
import com.ts.rm.domain.patch.dto.PatchHistoryDto;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.entity.PatchHistory;
import com.ts.rm.domain.patch.entity.PatchHistoryBuild;
import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import com.ts.rm.domain.patch.repository.PatchHistoryBuildRepository;
import com.ts.rm.domain.patch.repository.PatchHistoryRepository;
import com.ts.rm.domain.patch.repository.PatchIncludedBuildRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.pagination.PageRowNumberUtil;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PatchHistory Service
 *
 * <p>패치 이력 관리 서비스 (조회 및 저장)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatchHistoryService {

    private final PatchHistoryRepository patchHistoryRepository;
    private final PatchIncludedBuildRepository patchIncludedBuildRepository;
    private final PatchHistoryBuildRepository patchHistoryBuildRepository;
    private final SiteVersionService siteVersionService;
    private final SiteProjectRepository siteProjectRepository;

    /**
     * 패치 완료 시점 이력 저장 (영구 보존)
     *
     * <p>패치 완료(적용) 시 호출되어 이력을 영구 보존합니다.
     * 완료 처리자와 완료 일시를 함께 기록합니다.
     *
     * @param patch       완료 처리할 Patch 엔티티
     * @param completedBy 완료 처리자 이메일
     * @param completedAt 완료 일시
     * @return 저장된 PatchHistory 엔티티
     */
    @Transactional
    public PatchHistory saveFromPatch(Patch patch, String completedBy, LocalDateTime completedAt) {
        PatchHistory history = PatchHistory.fromPatch(patch, completedBy, completedAt);
        PatchHistory savedHistory = patchHistoryRepository.save(history);

        // 빌드 포함 패치면 WEB/ENGINE 빌드 스냅샷을 이력 쪽에 복사 보존
        // (patch_included_build 는 패치 완료 시 CASCADE 삭제되므로 재계산 근거가 사라짐)
        int savedBuildCount = 0;
        if (Boolean.TRUE.equals(patch.getIsBuildIncluded())) {
            List<PatchHistoryBuild> snapshots = patchIncludedBuildRepository
                    .findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(patch.getPatchId())
                    .stream()
                    .map(b -> PatchHistoryBuild.of(savedHistory, b.getKind(), b.getEngineName(),
                            b.getFullVersion(), completedAt))
                    .toList();
            if (!snapshots.isEmpty()) {
                patchHistoryBuildRepository.saveAll(snapshots);
            }
            savedBuildCount = snapshots.size();
        }

        log.info("패치 이력 저장 완료 - historyId: {}, patchName: {}, completedBy: {}, 빌드스냅샷: {}건",
                savedHistory.getHistoryId(), savedHistory.getPatchName(), completedBy,
                savedBuildCount);
        return savedHistory;
    }

    /**
     * 패치 이력 삭제
     *
     * <p>사이트 지정 이력이면, 삭제 후 남은 이력을 완료순으로 재생하여 사이트 버전 정보를 재계산한다.
     *
     * @param historyId 이력 ID
     */
    @Transactional
    public void deleteHistory(Long historyId) {
        PatchHistory history = patchHistoryRepository.findById(historyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "패치 이력을 찾을 수 없습니다. ID: " + historyId));

        Site site = history.getSite();

        // 빌드 스냅샷 먼저 제거 후 이력 삭제 (ORM 레벨에서 명시 삭제)
        patchHistoryBuildRepository.deleteAllByHistory_HistoryId(historyId);
        patchHistoryRepository.delete(history);
        log.info("패치 이력 삭제 완료 - historyId: {}, patchName: {}",
                historyId, history.getPatchName());

        // 사이트 지정 이력이면, 남은 이력 기준으로 버전 정보 재계산
        if (site != null) {
            String projectId = history.getProject().getProjectId();
            recomputeSiteVersions(site.getSiteId(), projectId);
        }
    }

    /**
     * 남은 이력을 완료순으로 재생하여 사이트 버전 정보를 재구성한다.
     *
     * <p>사이트 버전을 모두 비운 뒤 남은 이력을 완료순으로 재생하므로,
     * 삭제한 패치에서만 등장한 엔진 행은 자연 소멸한다. 남은 이력이 없으면
     * last_patched 정보를 비워 "패치 미적용" 상태로 만든다.
     */
    private void recomputeSiteVersions(Long siteId, String projectId) {
        siteVersionService.clearBySiteAndProject(siteId, projectId);

        List<PatchHistory> remaining = patchHistoryRepository
                .findAllBySite_SiteIdAndProject_ProjectIdOrderByCompletedAtAscCreatedAtAsc(
                        siteId, projectId);

        if (remaining.isEmpty()) {
            siteProjectRepository
                    .findBySite_SiteIdAndProject_ProjectId(siteId, projectId)
                    .ifPresent(cp -> {
                        cp.updateLastPatchInfo(null, null);
                        siteProjectRepository.save(cp);
                    });
            log.info("패치 이력 재계산 - 남은 이력 없음, 버전 초기화. siteId: {}, projectId: {}",
                    siteId, projectId);
            return;
        }

        for (PatchHistory h : remaining) {
            String baseVersion = siteVersionService.extractBaseVersion(h.getToVersion());
            List<SiteVersionService.BuildSnapshot> builds = patchHistoryBuildRepository
                    .findAllByHistory_HistoryIdOrderByPatchHistoryBuildIdAsc(h.getHistoryId())
                    .stream()
                    .map(b -> new SiteVersionService.BuildSnapshot(
                            b.getKind(), b.getEngineName(), b.getFullVersion()))
                    .toList();
            siteVersionService.applyComponentVersions(
                    siteId, projectId, baseVersion, builds, h.getCompletedBy(), h.getCompletedAt());
        }

        PatchHistory last = remaining.get(remaining.size() - 1);
        SiteProject cp = siteProjectRepository
                .findBySite_SiteIdAndProject_ProjectId(siteId, projectId)
                .orElseGet(() -> SiteProject.create(last.getSite(), last.getProject()));
        cp.updateLastPatchInfo(last.getToVersion(), last.getCompletedAt());
        siteProjectRepository.save(cp);

        log.info("패치 이력 재계산 완료 - siteId: {}, projectId: {}, lastPatchedVersion: {}",
                siteId, projectId, last.getToVersion());
    }

    /**
     * 패치 이력 목록 조회 (필터링 + 페이징)
     *
     * @param projectId  프로젝트 ID (null이면 전체)
     * @param siteId 사이트 ID (null이면 전체)
     * @param pageable   페이징 정보
     * @return 패치 이력 목록 페이지 (rowNumber 포함)
     */
    @Transactional(readOnly = true)
    public Page<PatchHistoryDto.ListResponse> listHistoriesWithPaging(
            String projectId, Long siteId, Pageable pageable) {
        Page<PatchHistory> histories = patchHistoryRepository.findAllWithFilters(
                projectId, siteId, pageable);

        // rowNumber 계산 (공통 유틸리티 사용)
        return PageRowNumberUtil.mapWithRowNumber(histories, this::toListResponse);
    }

    /**
     * PatchHistory 엔티티를 ListResponse DTO로 변환
     */
    private PatchHistoryDto.ListResponse toListResponse(PatchHistory history, Long rowNumber) {
        return new PatchHistoryDto.ListResponse(
                rowNumber,
                history.getHistoryId(),
                history.getProject().getProjectId(),
                history.getReleaseType(),
                history.getSite() != null ? history.getSite().getSiteId() : null,
                history.getSite() != null ? history.getSite().getSiteCode() : null,
                history.getSite() != null ? history.getSite().getSiteName() : null,
                history.getFromVersion(),
                history.getToVersion(),
                history.getPatchName(),
                history.getDescription(),
                history.getAssignee() != null ? history.getAssignee().getAccountId() : null,
                history.getAssigneeName(),
                history.getAssigneeEmail(),
                history.getAssignee() != null ? history.getAssignee().getAvatarStyle() : null,
                history.getAssignee() != null ? history.getAssignee().getAvatarSeed() : null,
                history.getAssignee() == null && history.getAssigneeEmail() != null,
                history.getCreatedByEmail(),
                history.getCreatedByName(),
                history.getCreator() != null ? history.getCreator().getAvatarStyle() : null,
                history.getCreator() != null ? history.getCreator().getAvatarSeed() : null,
                history.getCreator() == null,
                history.getCreatedAt(),
                history.getCompletedAt(),
                history.getCompletedBy()
        );
    }
}
