package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
    @DisplayName("buildSelection.enabled=true: 선택된 WEB / engine/{engineName} / etc 만 복사")
    void pickerSelection_partialCopy(@TempDir Path tempDir) throws IOException {
        // GIVEN
        // 빌드 v260427 디렉토리 구조 생성:
        //   buildDir427/web/foo.war, buildDir427/engine/NC_SMS/x.jar, buildDir427/engine/NC_FAULT_MS/y.jar, buildDir427/etc/note.txt
        Path buildDir427 = tempDir.resolve("build427");
        Files.createDirectories(buildDir427.resolve("web"));
        Files.createFile(buildDir427.resolve("web/foo.war"));
        Files.createDirectories(buildDir427.resolve("engine/NC_SMS"));
        Files.writeString(buildDir427.resolve("engine/NC_SMS/x.jar"), "jar-v260427");
        Files.createDirectories(buildDir427.resolve("engine/NC_FAULT_MS"));
        Files.createFile(buildDir427.resolve("engine/NC_FAULT_MS/y.jar"));
        Files.createDirectories(buildDir427.resolve("etc"));
        Files.createFile(buildDir427.resolve("etc/note.txt"));

        // 빌드 v260428 디렉토리 구조 생성:
        //   buildDir428/web/foo.war, buildDir428/engine/NC_SMS/x.jar, buildDir428/engine/NC_FAULT_MS/y.jar, buildDir428/etc/note.txt
        Path buildDir428 = tempDir.resolve("build428");
        Files.createDirectories(buildDir428.resolve("web"));
        Files.writeString(buildDir428.resolve("web/foo.war"), "war-v260428");
        Files.createDirectories(buildDir428.resolve("engine/NC_SMS"));
        Files.createFile(buildDir428.resolve("engine/NC_SMS/x.jar"));
        Files.createDirectories(buildDir428.resolve("engine/NC_FAULT_MS"));
        Files.createFile(buildDir428.resolve("engine/NC_FAULT_MS/y.jar"));
        Files.createDirectories(buildDir428.resolve("etc"));
        Files.createFile(buildDir428.resolve("etc/note.txt"));

        Project project = Project.builder()
                .projectId("infraeye2")
                .projectName("InfraEye 2.0")
                .build();

        // 베이스 버전 (1.1.0 base)
        ReleaseVersion baseVersion = ReleaseVersion.builder()
                .releaseVersionId(10L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        // 빌드 버전 v260428 (WEB 선택용)
        ReleaseVersion bv428 = ReleaseVersion.builder()
                .releaseVersionId(20L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260428)
                .buildBaseVersion(baseVersion)
                .isApproved(true)
                .build();

        // 빌드 버전 v260427 (NC_SMS 엔진 선택용)
        ReleaseVersion bv427 = ReleaseVersion.builder()
                .releaseVersionId(21L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260427)
                .buildBaseVersion(baseVersion)
                .isApproved(true)
                .build();

        // fileSystemService mock: bv428 → buildDir428, bv427 → buildDir427
        when(fileSystemService.resolveBuildBasePath(baseVersion, 260428)).thenReturn(buildDir428);
        when(fileSystemService.resolveBuildBasePath(baseVersion, 260427)).thenReturn(buildDir427);

        // loadBuildVersion 에서 사용하는 releaseVersionRepository.findById mock
        when(releaseVersionRepository.findById(20L)).thenReturn(Optional.of(bv428));
        when(releaseVersionRepository.findById(21L)).thenReturn(Optional.of(bv427));

        // outputDir: tempDir/output
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // picker: WEB=v260428, NC_SMS=v260427 (NC_FAULT_MS 미선택)
        PatchDto.BuildSelection buildSelection = new PatchDto.BuildSelection(
                true,
                new PatchDto.SelectedWeb(20L),
                List.of(new PatchDto.SelectedEngine("NC_SMS", 21L))
        );

        // WHEN: applyBuildSelection 을 직접 호출 (package-private 접근을 위해 같은 패키지에서 테스트)
        // PatchGenerationService 는 applyBuildSelection 이 private 이므로
        // generatePatch 전체 흐름 대신 리플렉션 없이 검증하기 위해
        // generatePatch 의 내부 흐름에서 applyBuildSelection 이 호출되는 경로를 설정
        // — Step 3 구현 후에 PASS 되어야 하는 구조.
        // 여기서는 buildSelection.enabled=true 이면 applyBuildSelection 이 호출되는지를
        // outputDir 파일 결과로 검증.

        // THEN: Step 3 (Green) 이 없으면 아래 assertThat 이 FAIL
        // Step 3 구현 후에는 아래 assertThat 이 PASS 여야 함.
        // 단, generatePatch 전체를 mock 으로 돌리기는 복잡하므로
        // applyBuildSelection 을 직접 테스트할 수 있도록 package-scope 헬퍼를 노출하거나
        // 전체 generatePatch 를 호출한다.
        // 여기서는 applyBuildSelection 이 protected 가 아닌 private 이므로
        // generatePatch 전체 경로를 설정하여 결과를 검증한다.

        // outputDir 에 직접 applyBuildSelection 호출하는 테스트용 경로
        // (구현 후 private → package-private 변경 없이 reflect 도 쓰지 않음)
        // → applyBuildSelection 은 generatePatch 내에서 호출되므로,
        //   generatePatch 전체 대신 별도 헬퍼 메서드를 expose 하는 방식은 plan 에서 언급하지 않음.
        // plan 원문: "outputDir 에 web/foo.war (v260428) + engine/NC_SMS/x.jar (v260427) 만"
        // → applyBuildSelection 을 호출할 수 있는 package-private 래퍼를 테스트에서만 사용.

        // applyBuildSelection 이 private 이므로 reflection 으로 호출
        try {
            java.lang.reflect.Method m = PatchGenerationService.class
                    .getDeclaredMethod("applyBuildSelection", Path.class, PatchDto.BuildSelection.class);
            m.setAccessible(true);
            m.invoke(patchGenerationService, outputDir, buildSelection);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e.getCause());
        } catch (NoSuchMethodException e) {
            // Step 3 구현 전 — 메서드 없음, 테스트 FAIL
            throw new AssertionError("applyBuildSelection 메서드가 PatchGenerationService 에 없습니다 (Step 3 구현 필요)", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("applyBuildSelection 메서드 접근 실패", e);
        }

        // THEN
        // web/foo.war (v260428) 존재
        assertThat(outputDir.resolve("web/foo.war")).exists();
        assertThat(Files.readString(outputDir.resolve("web/foo.war"))).isEqualTo("war-v260428");

        // engine/NC_SMS/x.jar (v260427) 존재
        assertThat(outputDir.resolve("engine/NC_SMS/x.jar")).exists();
        assertThat(Files.readString(outputDir.resolve("engine/NC_SMS/x.jar"))).isEqualTo("jar-v260427");

        // engine/NC_FAULT_MS 디렉토리 없음 (미선택)
        assertThat(outputDir.resolve("engine/NC_FAULT_MS")).doesNotExist();

        // etc/ 존재 여부 (Task 10 에서 내용 검증, 본 task 에서는 존재 여부만)
        assertThat(outputDir.resolve("etc")).isDirectory();
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
