package com.ts.rm.domain.dashboard.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.dashboard.dto.DashboardDto;
import com.ts.rm.domain.dashboard.dto.DashboardDto.RecentBuildVersion;
import com.ts.rm.domain.dashboard.dto.DashboardDto.RecentPatch;
import com.ts.rm.domain.dashboard.dto.DashboardDto.RecentVersion;
import com.ts.rm.domain.patch.entity.PatchHistory;
import com.ts.rm.domain.patch.repository.PatchHistoryRepository;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.service.ReleaseVersionFileSystemService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final ReleaseVersionRepository releaseVersionRepository;
    private final PatchHistoryRepository patchHistoryRepository;
    private final PatchRepository patchRepository;
    private final ReleaseVersionFileSystemService fileSystemService;

    private static final String RELEASE_TYPE_STANDARD = "STANDARD";

    /**
     * 표준본 최신 릴리즈 버전 조회
     *
     * @param projectId 프로젝트 ID
     * @param limit     조회 개수
     * @return 표준본 최신 릴리즈 버전 응답
     */
    public DashboardDto.RecentStandardResponse getRecentStandardVersions(String projectId, int limit) {
        log.info("표준본 최신 릴리즈 버전 조회 - projectId: {}, limit: {}", projectId, limit);

        List<RecentVersion> versions = releaseVersionRepository
                .findRecentByProjectIdAndReleaseType(projectId, RELEASE_TYPE_STANDARD, limit)
                .stream()
                .map(this::toRecentVersion)
                .toList();

        log.info("표준본 최신 릴리즈 버전 조회 완료 - {}개", versions.size());
        return new DashboardDto.RecentStandardResponse(versions);
    }

    /**
     * 최신 빌드 버전 조회 (표준 + 커스텀 통합)
     *
     * @param projectId 프로젝트 ID
     * @param limit     조회 개수
     * @return 최신 빌드 버전 응답
     */
    public DashboardDto.RecentBuildResponse getRecentBuildVersions(String projectId, int limit) {
        log.info("최신 빌드 버전 조회 - projectId: {}, limit: {}", projectId, limit);

        List<RecentBuildVersion> versions = releaseVersionRepository
                .findRecentBuildsByProjectId(projectId, limit)
                .stream()
                .map(this::toRecentBuildVersion)
                .toList();

        log.info("최신 빌드 버전 조회 완료 - {}개", versions.size());
        return new DashboardDto.RecentBuildResponse(versions);
    }

    /**
     * 최근 생성 패치 조회 (표준+커스텀)
     *
     * <p>patch_history 는 패치 완료 시점에 기록되므로 이미 "완료된" 패치만 들어있다.
     * 단, 정상 흐름이라면 완료 시 파일이 삭제되므로 patch_file 에 남아있는 항목은
     * 비정상 케이스로 보고 제외한다.
     *
     * @param projectId 프로젝트 ID
     * @param limit     조회 개수
     * @return 최근 생성 패치 응답
     */
    public DashboardDto.RecentPatchResponse getRecentPatches(String projectId, int limit) {
        log.info("최근 생성 패치 조회 (표준+커스텀) - projectId: {}, limit: {}", projectId, limit);

        // 1. 최근 패치 이력 조회 (표준+커스텀)
        List<PatchHistory> patchHistories = patchHistoryRepository
                .findRecentByProjectId(projectId, limit);

        // 2. patch_file 에 남아있는 패치명 조회 (완료 후 파일이 삭제된 항목만 남기기 위함)
        Set<String> patchNames = patchHistories.stream()
                .map(PatchHistory::getPatchName)
                .collect(Collectors.toSet());
        Set<String> existingPatchNames = patchRepository.findExistingPatchNames(patchNames);

        // 3. 패치 완료 버튼(=completePatch)으로 처리된 항목만 RecentPatch 변환
        //    - completedBy IS NOT NULL : 패치 완료 버튼으로 처리한 것 (직접 insert 등 비정상 row 제외)
        //    - site IS NOT NULL    : 사이트 지정 패치
        //    - patch_file row 삭제됨   : 정상 완료 흐름 통과 (파일이 정리됨)
        List<RecentPatch> patches = patchHistories.stream()
                .filter(ph -> ph.getCompletedBy() != null && !ph.getCompletedBy().isBlank())
                .filter(ph -> ph.getSite() != null)
                .filter(ph -> !existingPatchNames.contains(ph.getPatchName()))
                .map(this::toRecentPatch)
                .toList();

        log.info("최근 생성 패치 조회 완료 - {}개", patches.size());
        return new DashboardDto.RecentPatchResponse(patches);
    }

    /**
     * ReleaseVersion -> RecentVersion 변환
     */
    private RecentVersion toRecentVersion(ReleaseVersion rv) {
        // 해당 버전의 파일 카테고리 목록 조회 (fileCategory가 null인 경우 제외)
        List<String> fileCategories = rv.getReleaseFiles().stream()
                .filter(rf -> rf.getFileCategory() != null)
                .map(rf -> rf.getFileCategory().name())
                .distinct()
                .sorted()
                .toList();

        return new RecentVersion(
                rv.getReleaseVersionId(),
                rv.getVersion(),
                rv.getReleaseType(),
                rv.getCreatedAt(),
                rv.getComment(),
                fileCategories,
                rv.getCreatedByName(),
                rv.getCreatedByEmail(),
                rv.getCreatedByAvatarStyle(),
                rv.getCreatedByAvatarSeed()
        );
    }

    /**
     * ReleaseVersion -> RecentBuildVersion 변환
     *
     * <p>version 필드는 getFullVersion() 으로 빌드 라벨까지 포함 (예: "1.1.0.260501-1").
     */
    private RecentBuildVersion toRecentBuildVersion(ReleaseVersion rv) {
        // 빌드는 ZIP 업로드 시 ReleaseFile row 를 저장하지 않으므로 (BuildFileService 참조)
        // releaseFiles 기반으로 카테고리를 구하면 항상 빈 리스트가 된다.
        // 빌드 디렉토리(.../builds/{ver-iter}/web | engine) 의 실제 존재 여부로 카테고리를 채운다.
        List<String> fileCategories = resolveBuildFileCategories(rv);

        Site site = rv.getSite();
        Long siteId = site != null ? site.getSiteId() : null;
        String siteCode = site != null ? site.getSiteCode() : null;
        String siteName = site != null ? site.getSiteName() : null;

        return new RecentBuildVersion(
                rv.getReleaseVersionId(),
                rv.getFullVersion(),
                rv.getReleaseType(),
                rv.getCreatedAt(),
                rv.getComment(),
                fileCategories,
                siteId,
                siteCode,
                siteName,
                rv.getCreatedByName(),
                rv.getCreatedByEmail(),
                rv.getCreatedByAvatarStyle(),
                rv.getCreatedByAvatarSeed()
        );
    }

    /**
     * PatchHistory -> RecentPatch 변환
     */
    private RecentPatch toRecentPatch(PatchHistory patchHistory) {
        Site site = patchHistory.getSite();
        Long siteId = site != null ? site.getSiteId() : null;
        String siteCode = site != null ? site.getSiteCode() : null;
        String siteName = site != null ? site.getSiteName() : null;

        Account assignee = patchHistory.getAssignee();
        String assigneeAvatarStyle = assignee != null ? assignee.getAvatarStyle() : null;
        String assigneeAvatarSeed = assignee != null ? assignee.getAvatarSeed() : null;

        Account creator = patchHistory.getCreator();
        String createdByAvatarStyle = creator != null ? creator.getAvatarStyle() : null;
        String createdByAvatarSeed = creator != null ? creator.getAvatarSeed() : null;

        return new RecentPatch(
                patchHistory.getHistoryId(),
                patchHistory.getPatchName(),
                patchHistory.getFromVersion(),
                patchHistory.getToVersion(),
                patchHistory.getReleaseType(),
                patchHistory.getCreatedAt(),
                patchHistory.getDescription(),
                siteId,
                siteCode,
                siteName,
                patchHistory.getAssigneeName(),
                patchHistory.getAssigneeEmail(),
                assigneeAvatarStyle,
                assigneeAvatarSeed,
                patchHistory.getCreatedByName(),
                patchHistory.getCreatedByEmail(),
                createdByAvatarStyle,
                createdByAvatarSeed
        );
    }

    /**
     * 빌드 ReleaseVersion 의 디렉토리(web/ engine/) 존재 여부를 확인해 fileCategories 를 만든다.
     *
     * <p>빌드는 ZIP 업로드 시 ReleaseFile 행을 저장하지 않으므로 (BuildFileService) DB 가 아닌
     * 빌드 디렉토리의 실제 자산을 진실의 원천으로 사용한다.
     *
     * <p>빌드가 아닌 경우 (잘못 들어온 row) 빈 리스트 반환.
     *
     * @return ["WEB"], ["ENGINE"], ["ENGINE","WEB"] 중 하나 (sorted), 또는 빈 리스트
     */
    private List<String> resolveBuildFileCategories(ReleaseVersion rv) {
        if (rv == null || !rv.isBuild() || rv.getBuildBaseVersion() == null) {
            return List.of();
        }
        Path base;
        try {
            base = fileSystemService.resolveBuildBasePath(rv);
        } catch (Exception e) {
            log.warn("빌드 경로 계산 실패 - releaseVersionId: {}, reason: {}",
                    rv.getReleaseVersionId(), e.toString());
            return List.of();
        }
        List<String> categories = new ArrayList<>();
        if (hasAnyRegularFile(base.resolve("engine"))) {
            categories.add("ENGINE");
        }
        if (hasAnyRegularFile(base.resolve("web"))) {
            categories.add("WEB");
        }
        return categories;
    }

    /**
     * 디렉토리 안에 정규 파일이 1개 이상 있는지 검사. 디렉토리 자체가 없으면 false.
     */
    private boolean hasAnyRegularFile(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.walk(dir)) {
            return stream.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            log.warn("빌드 디렉토리 walk 실패 - {}: {}", dir, e.toString());
            return false;
        }
    }
}
