package com.ts.rm.domain.releaseversion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.patch.util.ScriptGenerator;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.mapper.ReleaseVersionDtoMapper;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.site.repository.SiteRepository;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.domain.common.service.FileStorageService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.progress.ServerProgressService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 신규 버전 생성 차단 가드 단위 테스트
 *
 * <p>가드(validateNoUnapproved*VersionExists)는 ZIP 검증보다 먼저 실행되므로
 * 빈 파일로도 판정 자체를 검증할 수 있다.
 *
 * <p>핵심 계약: 미승인 <b>base</b> 만 신규 버전 생성을 막고, 미승인 핫픽스는 막지 않는다.
 * (핫픽스는 base 계보가 아니라 특정 base 에 매달리는 별도 산출물이며,
 * 패치 생성 게이트 findUnapprovedVersionsBetween 도 동일하게 핫픽스를 제외한다)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReleaseVersionUploadService 신규 버전 생성 가드")
class ReleaseVersionUploadServiceGuardTest {

    private static final String PROJECT_ID = "infraeye2";

    @Mock private ReleaseVersionRepository releaseVersionRepository;
    @Mock private ReleaseFileRepository releaseFileRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountLookupService accountLookupService;
    @Mock private FileStorageService fileStorageService;
    @Mock private ReleaseVersionFileSystemService fileSystemService;
    @Mock private ReleaseVersionTreeService treeService;
    @Mock private ReleaseVersionDtoMapper mapper;
    @Mock private ScriptGenerator mariaDBScriptGenerator;
    @Mock private ScriptGenerator crateDBScriptGenerator;
    @Mock private ServerProgressService progressService;

    @InjectMocks
    private ReleaseVersionUploadService uploadService;

    private MockMultipartFile emptyZip() {
        return new MockMultipartFile("patchFiles", "p.zip", "application/zip", new byte[0]);
    }

    @Test
    @DisplayName("미승인 base 버전이 있으면 신규 표준 버전 생성이 차단된다")
    void standard_blockedByUnapprovedBase() {
        Project project = Project.builder().projectId(PROJECT_ID).projectName("InfraEye 2.0").build();
        ReleaseVersion unapprovedBase = ReleaseVersion.builder()
                .releaseVersionId(1L).project(project).releaseType("STANDARD")
                .version("1.1.5").majorVersion(1).minorVersion(1).patchVersion(5)
                .hotfixVersion(0).isApproved(false)
                .build();

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(releaseVersionRepository
                .existsByProject_ProjectIdAndReleaseTypeAndIsApprovedAndHotfixVersion(
                        anyString(), anyString(), eq(false), eq(0)))
                .thenReturn(true);
        when(releaseVersionRepository
                .findAllByProject_ProjectIdAndReleaseTypeAndIsApprovedAndHotfixVersion(
                        anyString(), anyString(), eq(false), eq(0)))
                .thenReturn(List.of(unapprovedBase));

        assertThatThrownBy(() -> uploadService.createStandardVersionWithZip(
                PROJECT_ID, "1.1.6", "코멘트", emptyZip(), "test@tscientific", progressService))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("미승인 버전이 존재하여 새 버전을 생성할 수 없습니다")
                .hasMessageContaining("1.1.5");
    }

    @Test
    @DisplayName("미승인 핫픽스만 있으면 신규 표준 버전 생성이 막히지 않는다 (가드가 hotfixVersion=0 으로 조회)")
    void standard_notBlockedByUnapprovedHotfix() {
        Project project = Project.builder().projectId(PROJECT_ID).projectName("InfraEye 2.0").build();

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        // base 한정 조회이므로 미승인 핫픽스는 결과에 잡히지 않는다
        when(releaseVersionRepository
                .existsByProject_ProjectIdAndReleaseTypeAndIsApprovedAndHotfixVersion(
                        anyString(), anyString(), eq(false), eq(0)))
                .thenReturn(false);

        // 가드를 통과하면 이후 단계(ZIP 검증)에서 실패한다 — 가드 메시지가 아니어야 한다
        assertThatThrownBy(() -> uploadService.createStandardVersionWithZip(
                PROJECT_ID, "1.1.6", "코멘트", emptyZip(), "test@tscientific", progressService))
                .isInstanceOf(BusinessException.class)
                .hasMessageNotContaining("미승인 버전이 존재하여");

        // 반드시 base 한정(hotfixVersion=0)으로 조회해야 한다
        verify(releaseVersionRepository)
                .existsByProject_ProjectIdAndReleaseTypeAndIsApprovedAndHotfixVersion(
                        PROJECT_ID, "STANDARD", false, 0);
    }

    @Test
    @DisplayName("미승인 커스텀 base 가 있으면 신규 커스텀 버전 생성이 차단된다")
    void custom_blockedByUnapprovedBase() {
        Long siteId = 7L;
        Project project = Project.builder().projectId(PROJECT_ID).projectName("InfraEye 2.0").build();
        Site site = Site.builder().siteId(siteId).siteCode("company_a").siteName("A사").build();

        ReleaseVersion existing = ReleaseVersion.builder()
                .releaseVersionId(10L).project(project).releaseType("CUSTOM").site(site)
                .version("1.1.0-company_a.1.0.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .customMajorVersion(1).customMinorVersion(0).customPatchVersion(0)
                .hotfixVersion(0).isApproved(false)
                .build();

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(releaseVersionRepository.findAllBySite_SiteIdOrderByCreatedAtDesc(siteId))
                .thenReturn(List.of(existing));
        when(releaseVersionRepository
                .existsBySite_SiteIdAndIsApprovedAndHotfixVersion(siteId, false, 0))
                .thenReturn(true);
        when(releaseVersionRepository
                .findAllBySite_SiteIdAndIsApprovedAndHotfixVersion(siteId, false, 0))
                .thenReturn(List.of(existing));

        ReleaseVersionDto.CreateCustomVersionRequest request =
                ReleaseVersionDto.CreateCustomVersionRequest.builder()
                        .projectId(PROJECT_ID)
                        .siteId(siteId)
                        .customVersion("1.0.1")
                        .comment("코멘트")
                        .build();

        assertThatThrownBy(() -> uploadService.createCustomVersionWithZip(
                request, emptyZip(), "test@tscientific", progressService))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("미승인 커스텀 버전이 존재하여 새 버전을 생성할 수 없습니다");
    }

    @Test
    @DisplayName("미승인 커스텀 핫픽스만 있으면 신규 커스텀 버전 생성이 막히지 않는다 (가드가 hotfixVersion=0 으로 조회)")
    void custom_notBlockedByUnapprovedHotfix() {
        Long siteId = 7L;
        Project project = Project.builder().projectId(PROJECT_ID).projectName("InfraEye 2.0").build();
        Site site = Site.builder().siteId(siteId).siteCode("company_a").siteName("A사").build();

        ReleaseVersion approvedBase = ReleaseVersion.builder()
                .releaseVersionId(10L).project(project).releaseType("CUSTOM").site(site)
                .version("1.1.0-company_a.1.0.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .customMajorVersion(1).customMinorVersion(0).customPatchVersion(0)
                .hotfixVersion(0).isApproved(true)
                .build();

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(releaseVersionRepository.findAllBySite_SiteIdOrderByCreatedAtDesc(siteId))
                .thenReturn(List.of(approvedBase));
        // base 한정 조회이므로 미승인 핫픽스는 결과에 잡히지 않는다
        when(releaseVersionRepository
                .existsBySite_SiteIdAndIsApprovedAndHotfixVersion(siteId, false, 0))
                .thenReturn(false);

        ReleaseVersionDto.CreateCustomVersionRequest request =
                ReleaseVersionDto.CreateCustomVersionRequest.builder()
                        .projectId(PROJECT_ID)
                        .siteId(siteId)
                        .customVersion("1.0.1")
                        .comment("코멘트")
                        .build();

        // 가드를 통과하면 이후 단계에서 실패한다 — 가드 메시지가 아니어야 한다
        assertThatThrownBy(() -> uploadService.createCustomVersionWithZip(
                request, emptyZip(), "test@tscientific", progressService))
                .isInstanceOf(BusinessException.class)
                .hasMessageNotContaining("미승인 커스텀 버전이 존재하여");

        // 반드시 base 한정(hotfixVersion=0)으로 조회해야 한다
        verify(releaseVersionRepository)
                .existsBySite_SiteIdAndIsApprovedAndHotfixVersion(siteId, false, 0);
    }
}
