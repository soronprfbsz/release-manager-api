package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.customer.repository.CustomerProjectRepository;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.patch.util.ScriptGenerator;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
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

/**
 * PatchGenerationService 단위 테스트
 *
 * <p>패치 생성 시 임시버전(미승인 버전) 검증 로직 테스트
 */
@ExtendWith(MockitoExtension.class)
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
    private ScriptGenerator mariaDBScriptGenerator;

    @Mock
    private ScriptGenerator crateDBScriptGenerator;

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
                projectId, fromVersionId, toVersionId, null, createdBy, null, null, null, false))
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
                projectId, fromVersionId, toVersionId, null, createdBy, null, null, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("버전 범위 내에 미승인 버전이 존재합니다")
                .hasMessageContaining("1.0.1")
                .hasMessageContaining("1.0.3");
    }
}
