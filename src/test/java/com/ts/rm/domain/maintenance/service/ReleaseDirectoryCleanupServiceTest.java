package com.ts.rm.domain.maintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.maintenance.dto.MaintenanceResultDto;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.service.ReleaseVersionFileSystemService;
import com.ts.rm.domain.site.entity.Site;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ReleaseDirectoryCleanupService 테스트.
 *
 * <p>NAS(SMB) 핸들 지연으로 best-effort 삭제가 남긴 잔존물(orphan 디렉토리)을
 * DB(release_version)와 대조해 정리하는 reaper 의 안전성 검증:
 * DB 에 살아있는 버전/빌드는 절대 지우지 않고, quiet 기간이 지난 orphan 만 지운다.
 */
@DisplayName("ReleaseDirectoryCleanupService 테스트")
class ReleaseDirectoryCleanupServiceTest {

    private static final int QUIET_HOURS = 24;

    @TempDir
    Path tempDir;

    private ReleaseVersionRepository repository;
    private ReleaseDirectoryCleanupService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReleaseVersionRepository.class);
        ReleaseVersionFileSystemService fileSystemService = new ReleaseVersionFileSystemService();
        ReflectionTestUtils.setField(fileSystemService, "baseReleasePath", tempDir.toString());
        service = new ReleaseDirectoryCleanupService(repository, fileSystemService);
        ReflectionTestUtils.setField(service, "baseReleasePath", tempDir.toString());
    }

    private Project project() {
        return Project.builder().projectId("infraeye2").projectName("InfraEye 2.0").build();
    }

    private ReleaseVersion standardVersion(Long id, String version, int major, int minor, int patch) {
        return ReleaseVersion.builder()
                .releaseVersionId(id).project(project()).releaseType("STANDARD")
                .version(version)
                .majorVersion(major).minorVersion(minor).patchVersion(patch)
                .build();
    }

    /** 커스텀 base 버전 (siteA, 커스텀 1.0.0 / 베이스 1.1.0) */
    private ReleaseVersion customVersion(Site site) {
        return ReleaseVersion.builder()
                .releaseVersionId(5L).project(project()).releaseType("CUSTOM").site(site)
                .version("1.1.0-siteA.1.0.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .customMajorVersion(1).customMinorVersion(0).customPatchVersion(0)
                .build();
    }

    private ReleaseVersion buildVersion(Long id, ReleaseVersion base, int buildVersion, int iteration) {
        return ReleaseVersion.builder()
                .releaseVersionId(id).project(base.getProject()).releaseType(base.getReleaseType())
                .version(base.getVersion())
                .majorVersion(base.getMajorVersion()).minorVersion(base.getMinorVersion())
                .patchVersion(base.getPatchVersion())
                .buildVersion(buildVersion).buildIteration(iteration).buildBaseVersion(base)
                .build();
    }

    /** 디렉토리 생성 + 파일 하나 채우기 */
    private Path createDirWithFile(String relative) throws IOException {
        Path dir = tempDir.resolve(relative);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("dummy.sql"), "x");
        return dir;
    }

    /** quiet 기간(24h)을 확실히 지난 것으로 mtime 조작 */
    private void age(Path dir) throws IOException {
        Files.setLastModifiedTime(dir, FileTime.from(Instant.now().minus(Duration.ofHours(48))));
    }

    @Test
    @DisplayName("DB에 없는 표준 버전 디렉토리는 quiet 기간이 지나면 삭제된다")
    void deletesAgedOrphanStandardVersionDirectory() throws IOException {
        when(repository.findAll()).thenReturn(List.of());
        Path orphan = createDirWithFile("versions/infraeye2/standard/1.1.x/1.1.5");
        age(orphan);

        MaintenanceResultDto.CleanupResult result = service.cleanupOrphanDirectories(QUIET_HOURS);

        assertThat(Files.exists(orphan)).isFalse();
        // 비게 된 major.minor 디렉토리도 정리된다
        assertThat(Files.exists(orphan.getParent())).isFalse();
        assertThat(result.deletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DB에 살아있는 버전의 디렉토리는 오래됐어도 지우지 않는다")
    void keepsLiveVersionDirectory() throws IOException {
        when(repository.findAll()).thenReturn(List.of(standardVersion(61L, "1.1.5", 1, 1, 5)));
        Path live = createDirWithFile("versions/infraeye2/standard/1.1.x/1.1.5");
        age(live);

        MaintenanceResultDto.CleanupResult result = service.cleanupOrphanDirectories(QUIET_HOURS);

        assertThat(Files.exists(live)).isTrue();
        assertThat(result.deletedCount()).isZero();
    }

    @Test
    @DisplayName("quiet 기간이 안 지난 orphan 은 지우지 않는다 (진행 중 업로드 보호)")
    void keepsRecentOrphanDirectory() throws IOException {
        when(repository.findAll()).thenReturn(List.of());
        Path recent = createDirWithFile("versions/infraeye2/standard/1.1.x/1.1.9");
        // mtime = 현재 (age 미적용)

        MaintenanceResultDto.CleanupResult result = service.cleanupOrphanDirectories(QUIET_HOURS);

        assertThat(Files.exists(recent)).isTrue();
        assertThat(result.deletedCount()).isZero();
    }

    @Test
    @DisplayName("살아있는 버전 밑의 DB에 없는 빌드 디렉토리는 삭제된다")
    void deletesOrphanBuildDirectoryUnderLiveVersion() throws IOException {
        when(repository.findAll()).thenReturn(List.of(standardVersion(61L, "1.1.1", 1, 1, 1)));
        Path versionDir = createDirWithFile("versions/infraeye2/standard/1.1.x/1.1.1");
        Path orphanBuild = createDirWithFile("versions/infraeye2/standard/1.1.x/1.1.1/builds/260605-1");
        age(orphanBuild);

        MaintenanceResultDto.CleanupResult result = service.cleanupOrphanDirectories(QUIET_HOURS);

        assertThat(Files.exists(orphanBuild)).isFalse();
        // 비게 된 builds 컨테이너 디렉토리도 정리, 버전 디렉토리는 유지
        assertThat(Files.exists(versionDir.resolve("builds"))).isFalse();
        assertThat(Files.exists(versionDir)).isTrue();
        assertThat(result.deletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DB에 살아있는 빌드 디렉토리는 지우지 않는다")
    void keepsLiveBuildDirectory() throws IOException {
        ReleaseVersion base = standardVersion(61L, "1.1.1", 1, 1, 1);
        when(repository.findAll()).thenReturn(List.of(base, buildVersion(67L, base, 260605, 1)));
        createDirWithFile("versions/infraeye2/standard/1.1.x/1.1.1");
        Path liveBuild = createDirWithFile("versions/infraeye2/standard/1.1.x/1.1.1/builds/260605-1");
        age(liveBuild);

        MaintenanceResultDto.CleanupResult result = service.cleanupOrphanDirectories(QUIET_HOURS);

        assertThat(Files.exists(liveBuild)).isTrue();
        assertThat(result.deletedCount()).isZero();
    }

    @Test
    @DisplayName("커스텀 orphan 은 ZIP 레이아웃(custom mm)과 레거시 레이아웃(base mm) 모두 정리된다")
    void deletesAgedOrphanCustomDirectories_bothLayouts() throws IOException {
        when(repository.findAll()).thenReturn(List.of());
        Path zipLayout = createDirWithFile("versions/infraeye2/custom/siteA/1.0.x/1.1.0-siteA.1.0.0");
        Path legacyLayout = createDirWithFile("versions/infraeye2/custom/siteA/1.1.x/1.1.0-siteA.1.0.0");
        age(zipLayout);
        age(legacyLayout);

        MaintenanceResultDto.CleanupResult result = service.cleanupOrphanDirectories(QUIET_HOURS);

        assertThat(Files.exists(zipLayout)).isFalse();
        assertThat(Files.exists(legacyLayout)).isFalse();
        // 비게 된 사이트 디렉토리까지 정리
        assertThat(Files.exists(tempDir.resolve("versions/infraeye2/custom/siteA"))).isFalse();
        assertThat(result.deletedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("안전 가드: 경로가 안 맞아도 디렉토리 이름이 살아있는 버전과 같으면 지우지 않는다")
    void nameGuard_keepsOrphanPathWhoseNameMatchesLiveVersion() throws IOException {
        // site FK 가 SET NULL 로 끊긴 커스텀 행 → 경로 해석은 custom/unknown/... 이 되지만
        // 실제 파일은 custom/siteA/... 에 있다. 경로만 보면 orphan 으로 오판 → 이름 가드로 보호.
        when(repository.findAll()).thenReturn(List.of(customVersion(null)));
        Path dir = createDirWithFile("versions/infraeye2/custom/siteA/1.0.x/1.1.0-siteA.1.0.0");
        age(dir);

        MaintenanceResultDto.CleanupResult result = service.cleanupOrphanDirectories(QUIET_HOURS);

        assertThat(Files.exists(dir)).isTrue();
        assertThat(result.deletedCount()).isZero();
    }

    @Test
    @DisplayName("살아있는 버전 밑의 DB에 없는 핫픽스 디렉토리는 삭제된다")
    void deletesOrphanHotfixDirectoryUnderLiveVersion() throws IOException {
        when(repository.findAll()).thenReturn(List.of(standardVersion(61L, "1.1.1", 1, 1, 1)));
        Path versionDir = createDirWithFile("versions/infraeye2/standard/1.1.x/1.1.1");
        Path orphanHotfix = createDirWithFile("versions/infraeye2/standard/1.1.x/1.1.1/hotfix/1");
        age(orphanHotfix);

        MaintenanceResultDto.CleanupResult result = service.cleanupOrphanDirectories(QUIET_HOURS);

        assertThat(Files.exists(orphanHotfix)).isFalse();
        assertThat(Files.exists(versionDir)).isTrue();
        assertThat(result.deletedCount()).isEqualTo(1);
    }
}
