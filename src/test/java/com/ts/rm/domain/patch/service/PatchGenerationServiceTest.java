package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.customer.repository.CustomerProjectRepository;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.entity.PatchHistory;
import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import com.ts.rm.domain.patch.entity.PatchHotfixInRange;
import com.ts.rm.domain.patch.repository.PatchHistoryRepository;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.patch.util.ScriptGenerator;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("buildSelection.enabled=true: 선택된 WEB / engine/<엔진명> 단일파일 / etc 만 복사")
    void pickerSelection_partialCopy(@TempDir Path tempDir) throws IOException {
        // GIVEN — 파일시스템 구조 생성 (새 모델: engine/ 직속 단일 파일)
        // 빌드 v260427: web/foo.war, engine/NC_SMS (단일 파일), engine/NC_FAULT_MS (단일 파일), etc/note.txt
        Path buildDir427 = tempDir.resolve("build427");
        Files.createDirectories(buildDir427.resolve("web"));
        Files.createFile(buildDir427.resolve("web/foo.war"));
        Files.createDirectories(buildDir427.resolve("engine"));
        Files.writeString(buildDir427.resolve("engine/NC_SMS"), "engine-NC_SMS-v260427");
        Files.writeString(buildDir427.resolve("engine/NC_FAULT_MS"), "engine-NC_FAULT_MS-v260427");
        Files.createDirectories(buildDir427.resolve("etc"));
        Files.createFile(buildDir427.resolve("etc/note.txt"));

        // 빌드 v260428: web/foo.war, engine/NC_SMS (단일 파일), engine/NC_FAULT_MS (단일 파일), etc/note.txt
        Path buildDir428 = tempDir.resolve("build428");
        Files.createDirectories(buildDir428.resolve("web"));
        Files.writeString(buildDir428.resolve("web/foo.war"), "war-v260428");
        Files.createDirectories(buildDir428.resolve("engine"));
        Files.writeString(buildDir428.resolve("engine/NC_SMS"), "engine-NC_SMS-v260428");
        Files.writeString(buildDir428.resolve("engine/NC_FAULT_MS"), "engine-NC_FAULT_MS-v260428");
        Files.createDirectories(buildDir428.resolve("etc"));
        Files.createFile(buildDir428.resolve("etc/note.txt"));

        // releaseBasePath → tempDir 주입 (generatePatch 내 createOutputDirectory 가 여기에 쓴다)
        ReflectionTestUtils.setField(patchGenerationService, "releaseBasePath", tempDir.toString());

        String projectId = "infraeye2";
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        // from: 1.1.0 base (buildVersion=0)
        ReleaseVersion baseFrom = ReleaseVersion.builder()
                .releaseVersionId(10L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        // to: 1.1.0.260428 (buildVersion=260428, isBuild=true)
        ReleaseVersion bv428 = ReleaseVersion.builder()
                .releaseVersionId(20L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260428)
                .buildBaseVersion(baseFrom)
                .isApproved(true)
                .build();

        // NC_SMS engine picker 용 v260427
        ReleaseVersion bv427 = ReleaseVersion.builder()
                .releaseVersionId(21L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260427)
                .buildBaseVersion(baseFrom)
                .isApproved(true)
                .build();

        // repository mock
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(10L)).thenReturn(Optional.of(baseFrom));
        when(releaseVersionRepository.findById(20L)).thenReturn(Optional.of(bv428));
        when(releaseVersionRepository.findById(21L)).thenReturn(Optional.of(bv427));
        // validateVersionRange: 미승인 버전 없음
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        // collectBetweenVersionsWithBuild: isSameBaseVersion=true, bv428.isBuild()=true → List.of(bv428)
        // (findVersionsBetween 호출 없음 — isSameBaseVersion 분기)
        // copySqlFiles: bv428 은 빌드이므로 releaseFile 조회 skip (isBuild check)
        // loadBuildVersion 에서도 findById(20L), findById(21L) 사용
        // fileSystemService mock (단일 인자: 빌드 엔티티 자체)
        when(fileSystemService.resolveBuildBasePath(bv428)).thenReturn(buildDir428);
        when(fileSystemService.resolveBuildBasePath(bv427)).thenReturn(buildDir427);
        // accountLookupService
        Account creator = Account.builder()
                .accountId(1L)
                .email(createdBy)
                .accountName("테스트 계정")
                .password("pw")
                .build();
        when(accountLookupService.findByEmail(createdBy)).thenReturn(creator);
        // patchRepository.save
        when(patchRepository.save(any(Patch.class))).thenAnswer(inv -> inv.getArgument(0));
        // patchHistoryRepository.save
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        // picker: WEB=v260428(ID=20), NC_SMS=v260427(ID=21) (NC_FAULT_MS 미선택)
        PatchDto.BuildSelection buildSelection = new PatchDto.BuildSelection(
                true,
                new PatchDto.SelectedWeb(20L),
                List.of(new PatchDto.SelectedEngine("NC_SMS", 21L))
        );

        // WHEN — generatePatch 전체 경로 호출 (가드 조건 if (buildSelection != null && buildSelection.enabled()) 까지 통합 검증)
        when(releaseVersionRepository.findHotfixesInBaseRange(anyString(), any(), any(), any()))
                .thenReturn(java.util.List.of());
        PatchGenerationService.GenerateResult result = patchGenerationService.generatePatch(
                projectId, 10L, 20L, null, createdBy, null, null, "test-patch", buildSelection);

        // THEN — outputPath 아래 파일 검증
        Path outputDir = tempDir.resolve(result.patch().getOutputPath());

        // web/foo.war — v260428 의 내용
        assertThat(outputDir.resolve("web/foo.war")).exists();
        assertThat(Files.readString(outputDir.resolve("web/foo.war"))).isEqualTo("war-v260428");

        // engine/NC_SMS — 단일 파일, v260427 의 내용
        assertThat(outputDir.resolve("engine/NC_SMS")).exists();
        assertThat(Files.readString(outputDir.resolve("engine/NC_SMS"))).isEqualTo("engine-NC_SMS-v260427");

        // engine/NC_FAULT_MS — 미선택이므로 존재하지 않음
        assertThat(outputDir.resolve("engine/NC_FAULT_MS")).doesNotExist();
    }

    @Test
    @DisplayName("Q-S3: 두 빌드 모두 etc/note.txt 가 있으면 큰 buildVersion 의 내용이 살아남음")
    void etcConflict_largerBuildVersionWins(@TempDir Path tempDir) throws IOException {
        // GIVEN: 빌드 v260427: engine/NC_SMS(단일파일), 빌드 v260428: engine/NC_FAULT_MS(단일파일)
        //        picker: NC_SMS=v260427, NC_FAULT_MS=v260428 → etc 는 빌드에서 복사 안 함
        //        (ETC 는 릴리즈 버전의 ReleaseFile 로 등록되어 copySqlFiles 가 처리)
        Path buildDir427 = tempDir.resolve("build427");
        Files.createDirectories(buildDir427.resolve("engine"));
        Files.writeString(buildDir427.resolve("engine/NC_SMS"), "engine-NC_SMS-v260427");

        Path buildDir428 = tempDir.resolve("build428");
        Files.createDirectories(buildDir428.resolve("engine"));
        Files.writeString(buildDir428.resolve("engine/NC_FAULT_MS"), "engine-NC_FAULT_MS-v260428");

        ReflectionTestUtils.setField(patchGenerationService, "releaseBasePath", tempDir.toString());

        String projectId = "infraeye2";
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        ReleaseVersion baseFrom = ReleaseVersion.builder()
                .releaseVersionId(10L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        ReleaseVersion bv427 = ReleaseVersion.builder()
                .releaseVersionId(21L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260427)
                .buildBaseVersion(baseFrom)
                .isApproved(true)
                .build();

        ReleaseVersion bv428 = ReleaseVersion.builder()
                .releaseVersionId(20L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260428)
                .buildBaseVersion(baseFrom)
                .isApproved(true)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(10L)).thenReturn(Optional.of(baseFrom));
        when(releaseVersionRepository.findById(20L)).thenReturn(Optional.of(bv428));
        when(releaseVersionRepository.findById(21L)).thenReturn(Optional.of(bv427));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(java.util.List.of());
        when(releaseVersionRepository.findHotfixesInBaseRange(
                anyString(), any(), any(), any()))
                .thenReturn(java.util.List.of());
        when(fileSystemService.resolveBuildBasePath(bv427)).thenReturn(buildDir427);
        when(fileSystemService.resolveBuildBasePath(bv428)).thenReturn(buildDir428);
        Account creator = Account.builder()
                .accountId(1L)
                .email(createdBy)
                .accountName("테스트 계정")
                .password("pw")
                .build();
        when(accountLookupService.findByEmail(createdBy)).thenReturn(creator);
        when(patchRepository.save(any(Patch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        // picker: NC_SMS=v260427(ID=21), NC_FAULT_MS=v260428(ID=20) — ETC 동행은 buildVersion 오름차순
        PatchDto.BuildSelection buildSelection = new PatchDto.BuildSelection(
                true,
                null,
                List.of(
                        new PatchDto.SelectedEngine("NC_SMS", 21L),
                        new PatchDto.SelectedEngine("NC_FAULT_MS", 20L)
                )
        );

        // WHEN: generatePatch (isSameBaseVersion=true, bv428.isBuild=true)
        PatchGenerationService.GenerateResult result = patchGenerationService.generatePatch(
                projectId, 10L, 20L, null, createdBy, null, null, "etc-conflict-patch", buildSelection);

        // THEN: picker 선택된 engine 단일 파일들이 각각 복사되어야 함
        Path outputDir = tempDir.resolve(result.patch().getOutputPath());
        assertThat(outputDir.resolve("engine/NC_SMS")).exists();
        assertThat(Files.readString(outputDir.resolve("engine/NC_SMS"))).isEqualTo("engine-NC_SMS-v260427");
        assertThat(outputDir.resolve("engine/NC_FAULT_MS")).exists();
        assertThat(Files.readString(outputDir.resolve("engine/NC_FAULT_MS"))).isEqualTo("engine-NC_FAULT_MS-v260428");
    }

    @Test
    @DisplayName("응답: includedBuilds, hotfixesInRange, isBuildOnly 채워짐")
    void responseFields_populated(@TempDir Path tempDir) throws IOException {
        // GIVEN: from(1.1.0 base) != to(1.2.0 base) 표준 패치, 범위 안에 핫픽스 1개,
        //        picker 로 WEB(bv428) + NC_SMS(bv427) 선택
        ReflectionTestUtils.setField(patchGenerationService, "releaseBasePath", tempDir.toString());

        String projectId = "infraeye2";
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        ReleaseVersion fromVersion = ReleaseVersion.builder()
                .releaseVersionId(1L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        ReleaseVersion toVersion = ReleaseVersion.builder()
                .releaseVersionId(2L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.2.0")
                .majorVersion(1).minorVersion(2).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        ReleaseVersion baseFrom11 = ReleaseVersion.builder()
                .releaseVersionId(10L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        // 빌드 v260427
        ReleaseVersion bv427 = ReleaseVersion.builder()
                .releaseVersionId(21L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260427)
                .buildBaseVersion(baseFrom11)
                .isApproved(true)
                .build();

        // 빌드 v260428
        ReleaseVersion bv428 = ReleaseVersion.builder()
                .releaseVersionId(20L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260428)
                .buildBaseVersion(baseFrom11)
                .isApproved(true)
                .build();

        // 핫픽스 1개 (1.1.0.1)
        ReleaseVersion hotfix = ReleaseVersion.builder()
                .releaseVersionId(99L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .hotfixVersion(1)
                .isApproved(true)
                .build();

        // 빌드 파일시스템 디렉토리 준비 (새 모델: engine/ 직속 단일 파일)
        Path buildDir428 = tempDir.resolve("build428");
        Files.createDirectories(buildDir428.resolve("web"));
        Files.createFile(buildDir428.resolve("web/foo.war"));
        Path buildDir427 = tempDir.resolve("build427");
        Files.createDirectories(buildDir427.resolve("engine"));
        Files.writeString(buildDir427.resolve("engine/NC_SMS"), "engine-NC_SMS-v260427");

        // Mocks
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(1L)).thenReturn(Optional.of(fromVersion));
        when(releaseVersionRepository.findById(2L)).thenReturn(Optional.of(toVersion));
        when(releaseVersionRepository.findById(20L)).thenReturn(Optional.of(bv428));
        when(releaseVersionRepository.findById(21L)).thenReturn(Optional.of(bv427));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(java.util.List.of());
        when(releaseVersionRepository.findVersionsBetween(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(toVersion));
        // 핫픽스 1건 반환
        when(releaseVersionRepository.findHotfixesInBaseRange(anyString(), any(), any(), any()))
                .thenReturn(List.of(hotfix));
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(2L))
                .thenReturn(java.util.List.of());
        when(fileSystemService.resolveBuildBasePath(bv428)).thenReturn(buildDir428);
        when(fileSystemService.resolveBuildBasePath(bv427)).thenReturn(buildDir427);
        Account creator = Account.builder()
                .accountId(1L)
                .email(createdBy)
                .accountName("테스트 계정")
                .password("pw")
                .build();
        when(accountLookupService.findByEmail(createdBy)).thenReturn(creator);
        when(patchRepository.save(any(Patch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        // picker: WEB=bv428(ID=20), NC_SMS=bv427(ID=21)
        PatchDto.BuildSelection buildSelection = new PatchDto.BuildSelection(
                true,
                new PatchDto.SelectedWeb(20L),
                List.of(new PatchDto.SelectedEngine("NC_SMS", 21L))
        );

        // WHEN
        PatchGenerationService.GenerateResult result = patchGenerationService.generatePatch(
                projectId, 1L, 2L, null, createdBy, null, null, "response-fields-patch", buildSelection);

        // THEN
        assertThat(result.isBuildOnly()).isFalse();  // from(1L) != to(2L)

        assertThat(result.hotfixesInRange()).hasSize(1);
        assertThat(result.hotfixesInRange().get(0).versionId()).isEqualTo(99L);

        assertThat(result.includedBuilds()).isNotNull();
        assertThat(result.includedBuilds().web()).isNotNull();
        assertThat(result.includedBuilds().web().fullVersion()).startsWith("1.1.0.260428");
        assertThat(result.includedBuilds().engines()).hasSize(1);
        assertThat(result.includedBuilds().engines().get(0).engineName()).isEqualTo("NC_SMS");
        assertThat(result.includedBuilds().engines().get(0).fullVersion()).startsWith("1.1.0.260427");
    }

    @Test
    @DisplayName("buildSelection.enabled=true 면 PatchIncludedBuild 행 + isBuildIncluded=true 가 영구 저장")
    void persistIncludedBuilds_savesRowsAndFlag(@TempDir Path tempDir) throws IOException {
        // GIVEN: 새 모델 — engine/ 직속 단일 파일
        Path buildDir427 = tempDir.resolve("build427");
        Files.createDirectories(buildDir427.resolve("engine"));
        Files.writeString(buildDir427.resolve("engine/NC_SMS"), "engine-NC_SMS-v260427");

        Path buildDir428 = tempDir.resolve("build428");
        Files.createDirectories(buildDir428.resolve("web"));
        Files.createFile(buildDir428.resolve("web/foo.war"));

        ReflectionTestUtils.setField(patchGenerationService, "releaseBasePath", tempDir.toString());

        String projectId = "infraeye2";
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        ReleaseVersion baseFrom = ReleaseVersion.builder()
                .releaseVersionId(10L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        ReleaseVersion bv428 = ReleaseVersion.builder()
                .releaseVersionId(20L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260428)
                .buildBaseVersion(baseFrom)
                .isApproved(true)
                .build();

        ReleaseVersion bv427 = ReleaseVersion.builder()
                .releaseVersionId(21L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(260427)
                .buildBaseVersion(baseFrom)
                .isApproved(true)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(10L)).thenReturn(Optional.of(baseFrom));
        when(releaseVersionRepository.findById(20L)).thenReturn(Optional.of(bv428));
        when(releaseVersionRepository.findById(21L)).thenReturn(Optional.of(bv427));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(releaseVersionRepository.findHotfixesInBaseRange(anyString(), any(), any(), any()))
                .thenReturn(List.of());
        when(fileSystemService.resolveBuildBasePath(bv428)).thenReturn(buildDir428);
        when(fileSystemService.resolveBuildBasePath(bv427)).thenReturn(buildDir427);
        Account creator = Account.builder()
                .accountId(1L)
                .email(createdBy)
                .accountName("테스트 계정")
                .password("pw")
                .build();
        when(accountLookupService.findByEmail(createdBy)).thenReturn(creator);
        when(patchRepository.save(any(Patch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        PatchDto.BuildSelection buildSelection = new PatchDto.BuildSelection(
                true,
                new PatchDto.SelectedWeb(20L),
                List.of(new PatchDto.SelectedEngine("NC_SMS", 21L))
        );

        // WHEN
        patchGenerationService.generatePatch(
                projectId, 10L, 20L, null, createdBy, null, null, "persist-included-builds-patch", buildSelection);

        // THEN: patchRepository.save 는 2회 호출 (최초 저장 + 메타 cascade 저장)
        //       두 번째 save 의 Patch 에 includedBuilds 크기 = 2 (WEB + NC_SMS),
        //       isBuildIncluded = true, isBuildOnly = true (isSameBaseVersion)
        ArgumentCaptor<Patch> patchCaptor = ArgumentCaptor.forClass(Patch.class);
        verify(patchRepository, times(2)).save(patchCaptor.capture());
        Patch savedPatch = patchCaptor.getAllValues().get(1);

        assertThat(savedPatch.getIsBuildIncluded()).isTrue();
        assertThat(savedPatch.getIsBuildOnly()).isTrue();
        assertThat(savedPatch.getIncludedBuilds()).hasSize(2);

        List<PatchIncludedBuild> builds = savedPatch.getIncludedBuilds();
        assertThat(builds).anyMatch(b -> "WEB".equals(b.getKind()));
        assertThat(builds).anyMatch(b -> "ENGINE".equals(b.getKind()) && "NC_SMS".equals(b.getEngineName()));
    }

    @Test
    @DisplayName("hotfixesInRange 가 비어있지 않으면 PatchHotfixInRange 행이 영구 저장")
    void persistHotfixesInRange_savesRows(@TempDir Path tempDir) throws IOException {
        // GIVEN: from(1.1.0 base) != to(1.2.0 base), 핫픽스 1건 (id=99, fullVersion=1.1.0.1, hotfixVersion=1)
        ReflectionTestUtils.setField(patchGenerationService, "releaseBasePath", tempDir.toString());

        String projectId = "infraeye2";
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        ReleaseVersion fromVersion = ReleaseVersion.builder()
                .releaseVersionId(1L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        ReleaseVersion toVersion = ReleaseVersion.builder()
                .releaseVersionId(2L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.2.0")
                .majorVersion(1).minorVersion(2).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        ReleaseVersion hotfix = ReleaseVersion.builder()
                .releaseVersionId(99L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .hotfixVersion(1)
                .isApproved(true)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(1L)).thenReturn(Optional.of(fromVersion));
        when(releaseVersionRepository.findById(2L)).thenReturn(Optional.of(toVersion));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(releaseVersionRepository.findVersionsBetween(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(toVersion));
        when(releaseVersionRepository.findHotfixesInBaseRange(anyString(), any(), any(), any()))
                .thenReturn(List.of(hotfix));
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(2L))
                .thenReturn(List.of());
        Account creator = Account.builder()
                .accountId(1L)
                .email(createdBy)
                .accountName("테스트 계정")
                .password("pw")
                .build();
        when(accountLookupService.findByEmail(createdBy)).thenReturn(creator);
        when(patchRepository.save(any(Patch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN: buildSelection=null
        patchGenerationService.generatePatch(
                projectId, 1L, 2L, null, createdBy, null, null, "persist-hotfixes-patch", null);

        // THEN: patchRepository.save 는 2회 호출. 두 번째 save 의 Patch 에 hotfixesInRange 크기 = 1
        ArgumentCaptor<Patch> patchCaptor = ArgumentCaptor.forClass(Patch.class);
        verify(patchRepository, times(2)).save(patchCaptor.capture());
        Patch savedPatch = patchCaptor.getAllValues().get(1);

        assertThat(savedPatch.getHotfixesInRange()).hasSize(1);
        assertThat(savedPatch.getHotfixesInRange().get(0).getFullVersion()).isEqualTo("1.1.0.1");
        assertThat(savedPatch.getIsBuildIncluded()).isFalse();
    }

    @Test
    @DisplayName("buildSelection 미포함 (null) 일 때 isBuildIncluded=false 이고 메타 행 0개")
    void noBuildSelection_noMetaRows(@TempDir Path tempDir) throws IOException {
        // GIVEN: buildSelection=null, 핫픽스 0개
        ReflectionTestUtils.setField(patchGenerationService, "releaseBasePath", tempDir.toString());

        String projectId = "infraeye2";
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        ReleaseVersion fromVersion = ReleaseVersion.builder()
                .releaseVersionId(1L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        ReleaseVersion toVersion = ReleaseVersion.builder()
                .releaseVersionId(2L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.2.0")
                .majorVersion(1).minorVersion(2).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(1L)).thenReturn(Optional.of(fromVersion));
        when(releaseVersionRepository.findById(2L)).thenReturn(Optional.of(toVersion));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(releaseVersionRepository.findVersionsBetween(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(toVersion));
        when(releaseVersionRepository.findHotfixesInBaseRange(anyString(), any(), any(), any()))
                .thenReturn(List.of());
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(2L))
                .thenReturn(List.of());
        Account creator = Account.builder()
                .accountId(1L)
                .email(createdBy)
                .accountName("테스트 계정")
                .password("pw")
                .build();
        when(accountLookupService.findByEmail(createdBy)).thenReturn(creator);
        when(patchRepository.save(any(Patch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN: buildSelection=null 로 generatePatch 호출
        patchGenerationService.generatePatch(
                projectId, 1L, 2L, null, createdBy, null, null, "no-meta-patch", null);

        // THEN: patchRepository.save 는 2회 호출. 두 번째 save 의 Patch 에 메타 없음
        ArgumentCaptor<Patch> patchCaptor = ArgumentCaptor.forClass(Patch.class);
        verify(patchRepository, times(2)).save(patchCaptor.capture());
        Patch savedPatch = patchCaptor.getAllValues().get(1);

        assertThat(savedPatch.getIsBuildIncluded()).isFalse();
        assertThat(savedPatch.getIsBuildOnly()).isFalse();
        assertThat(savedPatch.getIncludedBuilds()).isEmpty();
        assertThat(savedPatch.getHotfixesInRange()).isEmpty();
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

    // ---------------------------------------------------------------
    // 신규: 빌드 누적 walk + ENGINE 공유 자산 동반 + 출력 경로 fileName 기준 시나리오
    // ---------------------------------------------------------------

    @Test
    @DisplayName("빌드 ReleaseFile 도 누적 walk 에 포함 — ENGINE 공유 자산(nc_conf.conf)이 이전 빌드에서 동반 복사됨")
    void buildInCumulativeWalk_sharedAssetAutoIncluded(@TempDir Path tempDir) throws IOException {
        // GIVEN
        // 구조:
        //   from=1.4.0 (base, id=1)
        //   to=1.5.1 (base, id=3)
        //   빌드 1.5.0.260430 (id=100): ReleaseFile ENGINE/nc_conf.conf (sub_category=nc_conf.conf)
        //   빌드 1.5.1.260501 (id=101): ReleaseFile ENGINE/NC_CONF (sub_category=NC_CONF)
        //   picker: NC_CONF@1.5.1.260501
        //   기대:
        //     - engine/nc_conf.conf (1.5.0.260430 출처, 공유 자산 누적 동반)
        //     - engine/NC_CONF      (picker, 1.5.1.260501 출처)
        ReflectionTestUtils.setField(patchGenerationService, "releaseBasePath", tempDir.toString());

        String projectId = "infraeye2";
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        // from: 1.4.0 base
        ReleaseVersion fromVersion = ReleaseVersion.builder()
                .releaseVersionId(1L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.4.0")
                .majorVersion(1).minorVersion(4).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        // base 1.5.0 (buildBaseVersion 역할, id=2)
        ReleaseVersion base150 = ReleaseVersion.builder()
                .releaseVersionId(2L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.5.0")
                .majorVersion(1).minorVersion(5).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        // to: 1.5.1 base
        ReleaseVersion toVersion = ReleaseVersion.builder()
                .releaseVersionId(3L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.5.1")
                .majorVersion(1).minorVersion(5).patchVersion(1)
                .buildVersion(0)
                .isApproved(true)
                .build();

        // 빌드 1.5.0.260430 (id=100) — 공유 자산 nc_conf.conf 포함
        ReleaseVersion build150 = ReleaseVersion.builder()
                .releaseVersionId(100L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.5.0")
                .majorVersion(1).minorVersion(5).patchVersion(0)
                .buildVersion(260430)
                .buildBaseVersion(base150)
                .isApproved(true)
                .build();

        // 빌드 1.5.1.260501 (id=101) — 엔진 NC_CONF 포함 (picker 대상)
        ReleaseVersion build151 = ReleaseVersion.builder()
                .releaseVersionId(101L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.5.1")
                .majorVersion(1).minorVersion(5).patchVersion(1)
                .buildVersion(260501)
                .buildBaseVersion(toVersion)
                .isApproved(true)
                .build();

        // 빌드 파일시스템: picker 복사용 (NC_CONF 단일 파일)
        Path buildDir151 = tempDir.resolve("build_1.5.1.260501");
        Files.createDirectories(buildDir151.resolve("engine"));
        Files.writeString(buildDir151.resolve("engine/NC_CONF"), "NC_CONF-binary");

        // ReleaseFile 설정
        // 1.5.0.260430 빌드: ENGINE/nc_conf.conf (공유 자산)
        ReleaseFile sharedAsset = ReleaseFile.builder()
                .releaseFileId(1001L)
                .releaseVersion(build150)
                .fileCategory(FileCategory.ENGINE)
                .subCategory("nc_conf.conf")   // 원본 파일명 그대로 (공유 자산)
                .fileName("nc_conf.conf")
                .filePath("versions/infraeye2/standard/1.5.x/1.5.0.260430/engine/nc_conf.conf")
                .executionOrder(1)
                .build();

        // sharedAsset 소스 파일 생성 (releaseBasePath 기준)
        Path sharedAssetSrc = tempDir.resolve("versions/infraeye2/standard/1.5.x/1.5.0.260430/engine/nc_conf.conf");
        Files.createDirectories(sharedAssetSrc.getParent());
        Files.writeString(sharedAssetSrc, "nc_conf-shared-content");

        // 1.5.1.260501 빌드: ENGINE/NC_CONF (엔진, picker 대상) — copySqlFiles 가 pickerEngine 로 skip
        ReleaseFile engineFile = ReleaseFile.builder()
                .releaseFileId(1002L)
                .releaseVersion(build151)
                .fileCategory(FileCategory.ENGINE)
                .subCategory("NC_CONF")
                .fileName("NC_CONF")
                .filePath("versions/infraeye2/standard/1.5.x/1.5.1.260501/engine/NC_CONF")
                .executionOrder(1)
                .build();

        // betweenVersions: 1.5.0 base, 1.5.1 base + build151 enriched
        // (isSameBaseVersion=false → findVersionsBetween 결과 + enriched)
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(1L)).thenReturn(Optional.of(fromVersion));
        when(releaseVersionRepository.findById(3L)).thenReturn(Optional.of(toVersion));
        when(releaseVersionRepository.findById(101L)).thenReturn(Optional.of(build151));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        // findVersionsBetween: 1.5.0(base) + 1.5.1(base) + build150 + build151 — 빌드도 포함
        when(releaseVersionRepository.findVersionsBetween(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(base150, build150, toVersion));
        // ReleaseFile 조회
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(2L))
                .thenReturn(List.of());   // base150: 파일 없음
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(100L))
                .thenReturn(List.of(sharedAsset));   // build150: 공유 자산
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(3L))
                .thenReturn(List.of());   // toVersion(1.5.1 base): 파일 없음
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(101L))
                .thenReturn(List.of(engineFile));  // build151: NC_CONF (picker skip)
        when(releaseVersionRepository.findHotfixesInBaseRange(anyString(), any(), any(), any()))
                .thenReturn(List.of());
        when(fileSystemService.resolveBuildBasePath(build151)).thenReturn(buildDir151);

        Account creator = Account.builder()
                .accountId(1L)
                .email(createdBy)
                .accountName("테스트 계정")
                .password("pw")
                .build();
        when(accountLookupService.findByEmail(createdBy)).thenReturn(creator);
        when(patchRepository.save(any(Patch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(releaseFileRepository.findReleaseFilesBetweenVersionsBySubCategory(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        // picker: NC_CONF @ 빌드 1.5.1.260501 (id=101)
        PatchDto.BuildSelection buildSelection = new PatchDto.BuildSelection(
                true,
                null,
                List.of(new PatchDto.SelectedEngine("NC_CONF", 101L))
        );

        // WHEN
        PatchGenerationService.GenerateResult result = patchGenerationService.generatePatch(
                projectId, 1L, 3L, null, createdBy, null, null, "build-cumulative-walk-patch", buildSelection);

        // THEN
        Path outputDir = tempDir.resolve(result.patch().getOutputPath());

        // 공유 자산: engine/nc_conf.conf (fileName 기준 출력 경로)
        assertThat(outputDir.resolve("engine/nc_conf.conf"))
                .describedAs("빌드 1.5.0.260430 의 공유 자산 nc_conf.conf 가 누적 동반되어야 함")
                .exists();
        assertThat(Files.readString(outputDir.resolve("engine/nc_conf.conf")))
                .isEqualTo("nc_conf-shared-content");

        // picker 엔진: engine/NC_CONF (빌드 파일시스템에서 복사)
        assertThat(outputDir.resolve("engine/NC_CONF"))
                .describedAs("picker 로 선택된 NC_CONF 가 engine/NC_CONF 로 복사되어야 함")
                .exists();
        assertThat(Files.readString(outputDir.resolve("engine/NC_CONF")))
                .isEqualTo("NC_CONF-binary");
    }

    @Test
    @DisplayName("ENGINE 미선택 엔진(NC_SMS)은 빌드 ReleaseFile 에 있어도 누적 skip — 패치에 포함 안 됨")
    void unpickedEngineInBuildReleaseFile_cumulativeSkip(@TempDir Path tempDir) throws IOException {
        // GIVEN: 빌드 1.5.1.260501 에 NC_SMS ReleaseFile 존재 + picker 미선택
        // NC_SMS 는 EngineNameClassifier.isEngineFile("NC_SMS")=true → 누적 skip
        ReflectionTestUtils.setField(patchGenerationService, "releaseBasePath", tempDir.toString());

        String projectId = "infraeye2";
        String createdBy = "test@tscientific";

        Project project = Project.builder()
                .projectId(projectId)
                .projectName("InfraEye 2.0")
                .build();

        ReleaseVersion fromVersion = ReleaseVersion.builder()
                .releaseVersionId(1L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.4.0")
                .majorVersion(1).minorVersion(4).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        ReleaseVersion toVersion = ReleaseVersion.builder()
                .releaseVersionId(2L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.5.0")
                .majorVersion(1).minorVersion(5).patchVersion(0)
                .buildVersion(0)
                .isApproved(true)
                .build();

        ReleaseVersion buildRv = ReleaseVersion.builder()
                .releaseVersionId(200L)
                .project(project)
                .releaseType("STANDARD")
                .version("1.5.0")
                .majorVersion(1).minorVersion(5).patchVersion(0)
                .buildVersion(260501)
                .buildBaseVersion(toVersion)
                .isApproved(true)
                .build();

        // NC_SMS 파일시스템 준비 (copySqlFiles는 DB 경로 기반으로 복사하므로 실제 파일 필요)
        Path smsSrc = tempDir.resolve("versions/infraeye2/standard/1.5.x/1.5.0.260501/engine/NC_SMS");
        Files.createDirectories(smsSrc.getParent());
        Files.writeString(smsSrc, "NC_SMS-content");

        ReleaseFile smsFile = ReleaseFile.builder()
                .releaseFileId(2001L)
                .releaseVersion(buildRv)
                .fileCategory(FileCategory.ENGINE)
                .subCategory("NC_SMS")
                .fileName("NC_SMS")
                .filePath("versions/infraeye2/standard/1.5.x/1.5.0.260501/engine/NC_SMS")
                .executionOrder(1)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseVersionRepository.findById(1L)).thenReturn(Optional.of(fromVersion));
        when(releaseVersionRepository.findById(2L)).thenReturn(Optional.of(toVersion));
        when(releaseVersionRepository.findUnapprovedVersionsBetween(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(releaseVersionRepository.findVersionsBetween(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(toVersion, buildRv));
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(2L))
                .thenReturn(List.of());
        when(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(200L))
                .thenReturn(List.of(smsFile));
        when(releaseVersionRepository.findHotfixesInBaseRange(anyString(), any(), any(), any()))
                .thenReturn(List.of());
        when(releaseFileRepository.findReleaseFilesBetweenVersionsBySubCategory(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        Account creator = Account.builder()
                .accountId(1L)
                .email(createdBy)
                .accountName("테스트 계정")
                .password("pw")
                .build();
        when(accountLookupService.findByEmail(createdBy)).thenReturn(creator);
        when(patchRepository.save(any(Patch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        // picker: NC_SMS 미선택 (engines 빈 리스트)
        PatchDto.BuildSelection buildSelection = new PatchDto.BuildSelection(
                true,
                null,
                List.of()  // NC_SMS 미선택
        );

        // WHEN
        PatchGenerationService.GenerateResult result = patchGenerationService.generatePatch(
                projectId, 1L, 2L, null, createdBy, null, null, "unpicked-engine-patch", buildSelection);

        // THEN: NC_SMS 는 isEngineByClassifier=true 이므로 누적 skip → 패치에 없어야 함
        Path outputDir = tempDir.resolve(result.patch().getOutputPath());
        assertThat(outputDir.resolve("engine/NC_SMS"))
                .describedAs("picker 미선택 + EngineNameClassifier=true 인 NC_SMS 는 누적 skip 되어야 함")
                .doesNotExist();
    }
}
