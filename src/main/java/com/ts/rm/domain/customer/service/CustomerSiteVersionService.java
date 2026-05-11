package com.ts.rm.domain.customer.service;

import com.ts.rm.domain.customer.dto.CustomerSiteVersionDto;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.entity.CustomerSiteVersion;
import com.ts.rm.domain.customer.repository.CustomerProjectRepository;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.customer.repository.CustomerSiteVersionRepository;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
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
public class CustomerSiteVersionService {

    private final CustomerSiteVersionRepository siteVersionRepository;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final CustomerProjectRepository customerProjectRepository;
    private final ReleaseVersionRepository releaseVersionRepository;

    /** base 버전 추출 정규식 (major.minor.patch) */
    private static final Pattern BASE_VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)");

    /**
     * 사이트 컴포넌트 버전 upsert.
     *
     * <p>UNIQUE KEY (customer_id, project_id, component) 기준으로
     * row 가 없으면 INSERT, 있으면 currentVersion / updatedBy / updatedAt UPDATE.
     *
     * @param customerId 고객사 ID
     * @param projectId  프로젝트 ID
     * @param component  컴포넌트 구분 (BASE/WEB/ENGINE)
     * @param version    갱신할 버전 문자열
     * @param updatedBy  갱신자 이메일
     * @param updatedAt  갱신 일시
     */
    @Transactional
    public void upsert(Long customerId, String projectId, String component,
            String version, String updatedBy, LocalDateTime updatedAt) {

        siteVersionRepository
                .findByCustomer_CustomerIdAndProject_ProjectIdAndComponent(
                        customerId, projectId, component)
                .ifPresentOrElse(
                        existing -> {
                            // 기존 row 업데이트
                            existing.updateVersion(version, updatedBy, updatedAt);
                            siteVersionRepository.save(existing);
                            log.info("사이트 버전 갱신 - customerId: {}, projectId: {}, component: {}, version: {}",
                                    customerId, projectId, component, version);
                        },
                        () -> {
                            // 신규 row 생성
                            Customer customer = customerRepository.findById(customerId)
                                    .orElseThrow(() -> new BusinessException(
                                            ErrorCode.CUSTOMER_NOT_FOUND,
                                            "고객사를 찾을 수 없습니다: " + customerId));
                            Project project = projectRepository.findById(projectId)
                                    .orElseThrow(() -> new BusinessException(
                                            ErrorCode.PROJECT_NOT_FOUND,
                                            "프로젝트를 찾을 수 없습니다: " + projectId));
                            CustomerSiteVersion newEntry = CustomerSiteVersion.create(
                                    customer, project, component, version, updatedBy, updatedAt);
                            siteVersionRepository.save(newEntry);
                            log.info("사이트 버전 신규 등록 - customerId: {}, projectId: {}, component: {}, version: {}",
                                    customerId, projectId, component, version);
                        }
                );
    }

    /**
     * 고객사 + 프로젝트의 컴포넌트 버전 목록 조회 (UI 표시용).
     *
     * @param customerId 고객사 ID
     * @param projectId  프로젝트 ID
     * @return 컴포넌트 버전 목록 (최대 3건: BASE/WEB/ENGINE)
     */
    public List<CustomerSiteVersionDto.SiteVersionResponse> findByCustomerAndProject(
            Long customerId, String projectId) {

        List<CustomerSiteVersion> rows =
                siteVersionRepository.findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByComponent(
                        customerId, projectId);

        return rows.stream()
                .map(r -> new CustomerSiteVersionDto.SiteVersionResponse(
                        r.getComponent(),
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
     *   <li>suggestedFrom: currentVersion 직후 버전. 없으면(최신 상태) null</li>
     *   <li>suggestedTo: 목록의 가장 최신 버전. 없으면 null</li>
     * </ol>
     *
     * @param customerId 고객사 ID
     * @param projectId  프로젝트 ID
     * @return 다음 패치 범위 제안 (suggested* 는 null 가능)
     */
    public CustomerSiteVersionDto.NextPatchRangeResponse getNextPatchRange(
            Long customerId, String projectId) {

        // 1) 사이트 현재 base 버전 조회
        String currentBase = customerProjectRepository
                .findByCustomer_CustomerIdAndProject_ProjectId(customerId, projectId)
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
            return new CustomerSiteVersionDto.NextPatchRangeResponse(
                    currentBase, null, null, null, null);
        }

        // 3) suggestedTo: 목록 중 가장 최신
        ReleaseVersion toCandidate = baseVersions.get(baseVersions.size() - 1);

        // 4) suggestedFrom: currentBase 직후 버전
        ReleaseVersion fromCandidate = null;
        if (currentBase != null) {
            // currentBase 보다 큰 가장 작은 버전
            fromCandidate = baseVersions.stream()
                    .filter(v -> compareBase(v.getVersion(), currentBase) > 0)
                    .findFirst()
                    .orElse(null); // 이미 최신 → null
        } else {
            // 아직 패치 이력 없음 → 가장 오래된 버전을 from 으로 제안
            fromCandidate = baseVersions.get(0);
        }

        // from == to 이면 (버전이 1개뿐이고 아직 패치 없음) 그대로 허용 — 호출자 판단에 맡김
        return new CustomerSiteVersionDto.NextPatchRangeResponse(
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
