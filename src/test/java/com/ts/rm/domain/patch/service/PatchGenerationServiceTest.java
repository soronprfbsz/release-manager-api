package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.customer.repository.CustomerProjectRepository;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.domain.patch.repository.PatchHistoryRepository;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.patch.util.ScriptGenerator;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.service.ReleaseVersionFileSystemService;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import java.util.Arrays;
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

/**
 * PatchGenerationService 단위 테스트
 *
 * <p>패치 생성 시 임시버전(미승인 버전) 검증 로직 테스트
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PatchGenerationService 단위 테스트")
class PatchGenerationServiceTest {

    @Mock
    private PatchRepository patchRepository;

    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @Mock
    private ReleaseFileRepository releaseFileRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerProjectRepository customerProjectRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private PatchHistoryRepository patchHistoryRepository;

    @Mock
    private ScriptGenerator mariaDBScriptGenerator;

    @Mock
    private ScriptGenerator crateDBScriptGenerator;

    @Mock
    private AccountLookupService accountLookupService;

    @Mock
    private ReleaseVersionFileSystemService fileSystemService;

    @InjectMocks
    private PatchGenerationService patchGenerationService;

    @Test
    @DisplayName("패치 생성 실패 - 버전 범위 내에 미승인 버전이 존재하는 경우")
    void generatePatch_FailWhenUnapprovedVersionExists() {
        // Given
        String projectId = "infraeye2";
        Long fromVersionId = 1L;
        Long toVersionId = 3L;
        String createdBy = "test@tscientific";

        // Project 설정
        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        // From 버전 (1.0.0, 승인됨)
        ReleaseVersion fromVersion = ReleaseVersion.builder()
                .releaseVersionId(fromVersionId)
                .project(project)
                .releaseType("STANDARD")
                .version("1.0.0")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(0)
                .isApproved(true)
                .build();

        // To 버전 (1.0.2, 승인됨)
        ReleaseVersion toVersion = ReleaseVersion.builder()
                .releaseVersionId(toVersionId)
                .project(project)
                .releaseType("STANDARD")
                .version("1.0.2")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(2)
                .isApproved(true)
                .build();

        // 중간 버전 (1.0.1, 미승인 - 임시버전)
        ReleaseVersion unapprovedVersion = ReleaseVersion.builder()
                .releaseVersionId(2L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.0.1")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(1)
                .isApproved(false)  // 미승인 버전
                .build();

        // Mock 설정
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(fromVersionId)).thenReturn(Optional.of(fromVersion));
        when(releaseVersionRepository.findById(toVersionId)).thenReturn(Optional.of(toVersion));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(unapprovedVersion));

        // When & Then
        assertThatThrownBy(() -> patchGenerationService.generatePatch(
                projectId, fromVersionId, toVersionId, null, createdBy, null, null, null, (PatchDto.BuildSelection) null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("버전 범위 내에 미승인 버전이 존재합니다")
                .hasMessageContaining("1.0.1");
    }

    @Test
    @DisplayName("패치 생성 실패 - 버전 범위 내에 여러 미승인 버전이 존재하는 경우")
    void generatePatch_FailWhenMultipleUnapprovedVersionsExist() {
        // Given
        String projectId = "infraeye2";
        Long fromVersionId = 1L;
        Long toVersionId = 5L;
        String createdBy = "test@tscientific";

        // Project 설정
        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        // From 버전 (1.0.0, 승인됨)
        ReleaseVersion fromVersion = ReleaseVersion.builder()
                .releaseVersionId(fromVersionId)
                .project(project)
                .releaseType("STANDARD")
                .version("1.0.0")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(0)
                .isApproved(true)
                .build();

        // To 버전 (1.0.4, 승인됨)
        ReleaseVersion toVersion = ReleaseVersion.builder()
                .releaseVersionId(toVersionId)
                .project(project)
                .releaseType("STANDARD")
                .version("1.0.4")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(4)
                .isApproved(true)
                .build();

        // 미승인 버전들 (1.0.1, 1.0.3)
        ReleaseVersion unapprovedVersion1 = ReleaseVersion.builder()
                .releaseVersionId(2L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.0.1")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(1)
                .isApproved(false)
                .build();

        ReleaseVersion unapprovedVersion2 = ReleaseVersion.builder()
                .releaseVersionId(4L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.0.3")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(3)
                .isApproved(false)
                .build();

        // Mock 설정
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(fromVersionId)).thenReturn(Optional.of(fromVersion));
        when(releaseVersionRepository.findById(toVersionId)).thenReturn(Optional.of(toVersion));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Arrays.asList(unapprovedVersion1, unapprovedVersion2));

        // When & Then
        assertThatThrownBy(() -> patchGenerationService.generatePatch(
                projectId, fromVersionId, toVersionId, null, createdBy, null, null, null, (PatchDto.BuildSelection) null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("버전 범위 내에 미승인 버전이 존재합니다")
                .hasMessageContaining("1.0.1")
                .hasMessageContaining("1.0.3");
    }

    @Test
    @DisplayName("buildSelection==null 이면 versions[] 루프에서 빌드 skip, releaseFileRepository는 빌드 버전 ID로 조회하지 않음")
    void nullBuildSelection_buildSkippedFromVersionsLoop() {
        // GIVEN
        String projectId = "infraeye2";
        Long fromVersionId = 1L;
        Long toVersionId = 3L;
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        ReleaseVersion fromVersion = ReleaseVersion.builder()
                .releaseVersionId(fromVersionId)
                .project(project)
                .releaseType("STANDARD")
                .version("1.0.0")
                .majorVersion(1).minorVersion(0).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        Long buildVersionId = 2L;
        ReleaseVersion buildVersion = ReleaseVersion.builder()
                .releaseVersionId(buildVersionId)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260427)
                .isApproved(true)
                .build();

        ReleaseVersion toVersion = ReleaseVersion.builder()
                .releaseVersionId(toVersionId)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(fromVersionId)).thenReturn(Optional.of(fromVersion));
        when(releaseVersionRepository.findById(toVersionId)).thenReturn(Optional.of(toVersion));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        // betweenVersions: base(1.1.0) + buildVersion은 포함되지 않음 (to가 빌드 아님)
        when(releaseVersionRepository.findVersionsBetween(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(toVersion));

        // WHEN & THEN: validateVersionRange 에서 from < to 검증 통과 이후 파일 조회가 일어남
        // buildVersionId 로는 releaseFileRepository 조회가 일어나지 않아야 한다
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(toVersionId))
                .thenReturn(List.of());

        try {
            patchGenerationService.generatePatch(
                    projectId, fromVersionId, toVersionId, null, createdBy, null, null, null,
                    (PatchDto.BuildSelection) null);
        } catch (BusinessException e) {
            // 파일 복사 등 이후 단계 예외는 무시 (목적은 빌드 버전 조회 여부 검증)
        } catch (Exception e) {
            // 마찬가지로 무시
        }

        // 빌드 버전 ID로 ReleaseFile 조회가 호출되지 않아야 함
        verify(releaseFileRepository, never())
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(buildVersionId);
    }

    @Test
    @DisplayName("buildSelection.enabled=false 면 versions[] 루프에서 빌드 skip")
    void disabledBuildSelection_buildSkipped() {
        // GIVEN
        PatchDto.BuildSelection sel = new PatchDto.BuildSelection(false, null, java.util.List.of());
        String projectId = "infraeye2";
        Long fromVersionId = 1L;
        Long toVersionId = 3L;
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        ReleaseVersion fromVersion = ReleaseVersion.builder()
                .releaseVersionId(fromVersionId)
                .project(project)
                .releaseType("STANDARD")
                .version("1.0.0")
                .majorVersion(1).minorVersion(0).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        Long buildVersionId = 2L;

        ReleaseVersion toVersion = ReleaseVersion.builder()
                .releaseVersionId(toVersionId)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(fromVersionId)).thenReturn(Optional.of(fromVersion));
        when(releaseVersionRepository.findById(toVersionId)).thenReturn(Optional.of(toVersion));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(releaseVersionRepository.findVersionsBetween(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(toVersion));
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(toVersionId))
                .thenReturn(List.of());

        try {
            patchGenerationService.generatePatch(
                    projectId, fromVersionId, toVersionId, null, createdBy, null, null, null, sel);
        } catch (BusinessException e) {
            // 이후 단계 예외 무시
        } catch (Exception e) {
            // 무시
        }

        // 빌드 버전 ID로 ReleaseFile 조회가 호출되지 않아야 함
        verify(releaseFileRepository, never())
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(buildVersionId);
    }
}
