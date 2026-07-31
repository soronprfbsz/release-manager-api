package com.ts.rm.domain.site.service;

import com.ts.rm.domain.site.dto.SiteVersionDto;
import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.site.entity.SiteVersion;
import com.ts.rm.domain.site.repository.SiteProjectRepository;
import com.ts.rm.domain.site.repository.SiteRepository;
import com.ts.rm.domain.site.repository.SiteVersionRepository;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트별 컴포넌트 버전 추적 서비스
 *
 * <p>패치 완료(completePatch) 시 BASE/WEB/ENGINE 컴포넌트 별 현재 버전을 upsert 하여
 * InfraEye {@code info version} 과 동일한 버전 상태를 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteVersionService {

    private final SiteVersionRepository siteVersionRepository;
    private final SiteRepository siteRepository;
    private final ProjectRepository projectRepository;
    private final SiteProjectRepository siteProjectRepository;
    private final ReleaseVersionRepository releaseVersionRepository;

    /** base 버전 추출 정규식 (major.minor.patch) */
    private static final Pattern BASE_VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)");

    /**
     * 컴포넌트 버전 적용용 빌드 스냅샷 표현.
     * patch_included_build / patch_history_build 양쪽의 공통 입력 형태.
     */
    public record BuildSnapshot(String kind, String engineName, String fullVersion) {}

    /**
     * 사이트 컴포넌트 버전 upsert.
     *
     * <p>UNIQUE KEY (customer_id, project_id, component, engine_name) 기준 upsert.
     * BASE/WEB 는 engineName=null. ENGINE 은 엔진명을 전달.
     *
     * @param siteId 사이트 ID
     * @param projectId  프로젝트 ID
     * @param component  컴포넌트 구분 (BASE/WEB/ENGINE)
     * @param engineName 엔진명 (ENGINE 일 때만; BASE/WEB 은 null)
     * @param version    갱신할 버전 문자열
     * @param updatedBy  갱신자 이메일
     * @param updatedAt  갱신 일시
     */
    @Transactional
    public void upsert(Long siteId, String projectId, String component, String engineName,
            String version, String updatedBy, LocalDateTime updatedAt) {

        Optional<SiteVersion> existing = (engineName == null)
                ? siteVersionRepository
                        .findBySite_SiteIdAndProject_ProjectIdAndComponentAndEngineNameIsNull(
                                siteId, projectId, component)
                : siteVersionRepository
                        .findBySite_SiteIdAndProject_ProjectIdAndComponentAndEngineName(
                                siteId, projectId, component, engineName);

        existing.ifPresentOrElse(
                row -> {
                    row.updateVersion(version, updatedBy, updatedAt);
                    siteVersionRepository.save(row);
                    log.info("사이트 버전 갱신 - siteId: {}, projectId: {}, component: {}/{}, version: {}",
                            siteId, projectId, component, engineName, version);
                },
                () -> {
                    Site site = siteRepository.findById(siteId)
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.SITE_NOT_FOUND,
                                    "사이트를 찾을 수 없습니다: " + siteId));
                    Project project = projectRepository.findById(projectId)
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.PROJECT_NOT_FOUND,
                                    "프로젝트를 찾을 수 없습니다: " + projectId));
                    SiteVersion newEntry = SiteVersion.create(
                            site, project, component, engineName, version, updatedBy, updatedAt);
                    siteVersionRepository.save(newEntry);
                    log.info("사이트 버전 신규 등록 - siteId: {}, projectId: {}, component: {}/{}, version: {}",
                            siteId, projectId, component, engineName, version);
                }
        );
    }

    /**
     * 버전 문자열에서 BASE(major.minor.patch) 추출. 미일치 시 원본, null→null.
     * <p>사이트 BASE 버전 기록용. (다음 패치 범위 추천용 {@code extractBase} 는 미일치 시 null 로 의미가 다르다.)
     */
    public String extractBaseVersion(String version) {
        if (version == null) {
            return null;
        }
        Matcher m = BASE_VERSION_PATTERN.matcher(version);
        return m.find() ? m.group(1) : version;
    }

    /**
     * BASE/WEB/ENGINE 컴포넌트 버전을 일괄 upsert.
     *
     * <p>패치 완료(완료 시 patch_included_build) 와 이력 삭제 재계산(patch_history_build)
     * 양쪽에서 재사용되는 공통 로직. BASE 는 항상 갱신, WEB 은 첫 WEB 빌드, ENGINE 은 엔진명별.
     * builds 가 비면 BASE 만 갱신하고 WEB/ENGINE 은 손대지 않는다(이전 값 유지).
     */
    @Transactional
    public void applyComponentVersions(Long siteId, String projectId, String baseVersion,
            List<BuildSnapshot> builds, String updatedBy, LocalDateTime updatedAt) {
        // BASE — 항상 갱신
        upsert(siteId, projectId, "BASE", null, baseVersion, updatedBy, updatedAt);

        if (builds == null || builds.isEmpty()) {
            return;
        }
        // WEB — 첫 WEB 빌드
        builds.stream()
                .filter(b -> "WEB".equals(b.kind()))
                .map(BuildSnapshot::fullVersion)
                .findFirst()
                .ifPresent(v -> upsert(siteId, projectId, "WEB", null, v, updatedBy, updatedAt));
        // ENGINE — 엔진명별
        builds.stream()
                .filter(b -> "ENGINE".equals(b.kind()))
                .filter(b -> b.engineName() != null)
                .forEach(b -> upsert(siteId, projectId, "ENGINE", b.engineName(),
                        b.fullVersion(), updatedBy, updatedAt));
    }

    /**
     * 사이트 + 프로젝트의 사이트 버전 전부 삭제 (재계산 전 초기화용).
     *
     * <p><b>주의:</b> 이 삭제는 영속성 컨텍스트를 경유하는 파생 delete 여야 한다 —
     * 직후 재계산(replay)의 upsert auto-flush 가 DELETE→INSERT 순서를 보장하므로,
     * 성능 목적이라도 {@code @Modifying} 벌크 delete 로 바꾸지 말 것(UNIQUE 충돌/순서 불일치 위험).
     */
    @Transactional
    public void clearBySiteAndProject(Long siteId, String projectId) {
        siteVersionRepository.deleteAllBySite_SiteIdAndProject_ProjectId(siteId, projectId);
    }

    /**
     * 사이트 + 프로젝트의 컴포넌트 버전 목록 조회 (UI 표시용).
     *
     * @param siteId 사이트 ID
     * @param projectId  프로젝트 ID
     * @return 컴포넌트 버전 목록 (최대 3건: BASE/WEB/ENGINE)
     */
    public List<SiteVersionDto.SiteVersionResponse> findBySiteAndProject(
            Long siteId, String projectId) {

        List<SiteVersion> rows =
                siteVersionRepository.findAllBySite_SiteIdAndProject_ProjectIdOrderByComponent(
                        siteId, projectId);

        return rows.stream()
                .map(r -> new SiteVersionDto.SiteVersionResponse(
                        r.getComponent(),
                        r.getEngineName(),
                        r.getCurrentVersion(),
                        r.getUpdatedAt(),
                        r.getUpdatedBy()))
                .toList();
    }

    /**
     * 패치 생성 폼 자동 채우기용 다음 패치 범위 제안.
     *
     * <p>로직:
     * <ol>
     *   <li>customer_project.last_patched_version 에서 base 추출 → currentVersion</li>
     *   <li>프로젝트의 승인된 표준 base 버전 목록을 semver 오름차순 정렬</li>
     *   <li>suggestedFrom: currentVersion 자신(inclusive). 사이트가 이미 최신이면 suggestedTo 와 같아진다.
     *       이미 적용된 버전에 사후 추가된 빌드/파일을 다음 패치가 회수하도록 하한을 포함시킨다.</li>
     *   <li>suggestedTo: 목록의 가장 최신 버전. 없으면 null</li>
     * </ol>
     *
     * @param siteId 사이트 ID
     * @param projectId  프로젝트 ID
     * @return 다음 패치 범위 제안 (suggested* 는 null 가능)
     */
    public SiteVersionDto.NextPatchRangeResponse getNextPatchRange(
            Long siteId, String projectId) {

        // 1) 사이트 현재 base 버전 조회
        String currentBase = siteProjectRepository
                .findBySite_SiteIdAndProject_ProjectId(siteId, projectId)
                .map(cp -> extractBase(cp.getLastPatchedVersion()))
                .orElse(null);

        // 2) 프로젝트의 승인된 표준 base 버전 목록 (빌드·핫픽스 제외) semver 오름차순
        List<ReleaseVersion> baseVersions = releaseVersionRepository
                .findAllByProject_ProjectIdAndReleaseTypeAndIsApproved(projectId, "STANDARD", true)
                .stream()
                .filter(v -> !v.isBuild() && !v.isHotfix())
                .sorted(Comparator
                        .comparingInt(ReleaseVersion::getMajorVersion)
                        .thenComparingInt(ReleaseVersion::getMinorVersion)
                        .thenComparingInt(ReleaseVersion::getPatchVersion))
                .toList();

        if (baseVersions.isEmpty()) {
            // 등록된 승인 버전 없음 — 제안 불가
            return new SiteVersionDto.NextPatchRangeResponse(
                    currentBase, null, null, null, null);
        }

        // 3) suggestedTo: 목록 중 가장 최신
        ReleaseVersion toCandidate = baseVersions.get(baseVersions.size() - 1);

        // 4) suggestedFrom: currentBase 직후 버전
        ReleaseVersion fromCandidate = null;
        if (currentBase != null) {
            // currentBase 보다 큰 가장 작은 버전
            fromCandidate = baseVersions.stream()
                    .filter(v -> compareBase(v.getVersion(), currentBase) >= 0)
                    .findFirst()
                    .orElse(null); // currentBase 가 승인 목록에 없고 그보다 큰 버전도 없음
        } else {
            // 아직 패치 이력 없음 → 가장 오래된 버전을 from 으로 제안
            fromCandidate = baseVersions.get(0);
        }

        // from == to 이면 (버전이 1개뿐이고 아직 패치 없음) 그대로 허용 — 호출자 판단에 맡김
        return new SiteVersionDto.NextPatchRangeResponse(
                currentBase,
                fromCandidate != null ? fromCandidate.getVersion() : null,
                fromCandidate != null ? fromCandidate.getReleaseVersionId() : null,
                toCandidate.getVersion(),
                toCandidate.getReleaseVersionId()
        );
    }

    /**
     * 버전 문자열에서 major.minor.patch base 부분만 추출.
     * null 이거나 형식 불일치 시 null 반환.
     */
    private String extractBase(String version) {
        if (version == null) {
            return null;
        }
        Matcher m = BASE_VERSION_PATTERN.matcher(version);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 두 base 버전 문자열을 semver (major.minor.patch) 기준으로 비교.
     *
     * @return 음수 / 0 / 양수
     */
    private int compareBase(String v1, String v2) {
        int[] p1 = parseParts(v1);
        int[] p2 = parseParts(v2);
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(p1[i], p2[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /**
     * "major.minor.patch" 를 int[3] 으로 파싱. 파싱 실패 시 {0,0,0} 반환.
     */
    private int[] parseParts(String version) {
        if (version == null) {
            return new int[]{0, 0, 0};
        }
        // base 부분만 추출 (build suffix 제거)
        Matcher m = BASE_VERSION_PATTERN.matcher(version);
        String base = m.find() ? m.group(1) : version;
        String[] parts = base.split("\\.", -1);
        int[] result = new int[3];
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }
}
