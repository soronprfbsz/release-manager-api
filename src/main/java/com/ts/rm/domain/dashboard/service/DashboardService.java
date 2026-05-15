package com.ts.rm.domain.dashboard.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.dashboard.dto.DashboardDto;
import com.ts.rm.domain.dashboard.dto.DashboardDto.RecentBuildVersion;
import com.ts.rm.domain.dashboard.dto.DashboardDto.RecentPatch;
import com.ts.rm.domain.dashboard.dto.DashboardDto.RecentVersion;
import com.ts.rm.domain.patch.entity.PatchHistory;
import com.ts.rm.domain.patch.repository.PatchHistoryRepository;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
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

        // 3. 파일이 삭제된(=정상 완료된) 항목만 RecentPatch 변환
        List<RecentPatch> patches = patchHistories.stream()
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
        List<String> fileCategories = rv.getReleaseFiles().stream()
                .filter(rf -> rf.getFileCategory() != null)
                .map(rf -> rf.getFileCategory().name())
                .distinct()
                .sorted()
                .toList();

        Customer customer = rv.getCustomer();
        Long customerId = customer != null ? customer.getCustomerId() : null;
        String customerCode = customer != null ? customer.getCustomerCode() : null;
        String customerName = customer != null ? customer.getCustomerName() : null;

        return new RecentBuildVersion(
                rv.getReleaseVersionId(),
                rv.getFullVersion(),
                rv.getReleaseType(),
                rv.getCreatedAt(),
                rv.getComment(),
                fileCategories,
                customerId,
                customerCode,
                customerName,
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
        Customer customer = patchHistory.getCustomer();
        Long customerId = customer != null ? customer.getCustomerId() : null;
        String customerCode = customer != null ? customer.getCustomerCode() : null;
        String customerName = customer != null ? customer.getCustomerName() : null;

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
                customerId,
                customerCode,
                customerName,
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
}
