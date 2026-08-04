package com.ts.rm.domain.releaseversion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.util.VersionParser;
import com.ts.rm.global.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ReleaseVersionFileSystemService.deleteBuildDirectory 동작 테스트.
 *
 * <p>빌드 디렉토리 삭제는 CIFS 부분 실패에 대비해 best-effort 여야 한다 — 일부 항목 삭제가
 * 실패해도 예외를 던지지 않아야 deleteVersion 의 DB 삭제가 진행된다(트랜잭션 롤백 방지).
 */
@DisplayName("ReleaseVersionFileSystemService.deleteBuildDirectory 테스트")
class ReleaseVersionFileSystemServiceTest {

    private ReleaseVersionFileSystemService newService(Path baseReleasePath) {
        ReleaseVersionFileSystemService svc = new ReleaseVersionFileSystemService();
        ReflectionTestUtils.setField(svc, "baseReleasePath", baseReleasePath.toString());
        return svc;
    }

    private ReleaseVersion buildVersion() {
        Project project = Project.builder().projectId("infraeye2").projectName("InfraEye 2.0").build();
        ReleaseVersion base = ReleaseVersion.builder()
                .releaseVersionId(61L).project(project).releaseType("STANDARD")
                .version("1.1.1")
                .majorVersion(1).minorVersion(1).patchVersion(1).buildVersion(0)
                .build();
        return ReleaseVersion.builder()
                .releaseVersionId(67L).project(project).releaseType("STANDARD")
                .version("1.1.1")
                .majorVersion(1).minorVersion(1).patchVersion(1)
                .buildVersion(260605).buildIteration(1).buildBaseVersion(base)
                .build();
    }

    /**
     * 커스텀 base 버전 엔티티. 베이스=1.1.0, 사이트=siteA, 커스텀=1.0.0.
     * <p>version 컬럼에는 ZIP 생성 경로가 저장하는 풀버전 문자열이 들어간다.
     */
    private ReleaseVersion customVersion() {
        Project project = Project.builder().projectId("infraeye2").projectName("InfraEye 2.0").build();
        Site site = Site.builder().siteId(1L).siteCode("siteA").siteName("사이트A").build();
        return ReleaseVersion.builder()
                .releaseVersionId(5L).project(project).releaseType("CUSTOM").site(site)
                .version("1.1.0-siteA.1.0.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)   // 베이스 버전 숫자
                .customMajorVersion(1).customMinorVersion(0).customPatchVersion(0)  // 커스텀 버전 숫자
                .build();
    }

    @Test
    @DisplayName("회귀(#커스텀삭제): 커스텀 ZIP 생성 레이아웃(custom majorMinor)에 만든 디렉토리를 삭제가 제거한다")
    void deletesCustomVersionDirectory_atCustomMajorMinorLayout(@TempDir Path tempDir) throws IOException {
        ReleaseVersionFileSystemService svc = newService(tempDir);
        ReleaseVersion version = customVersion();
        // 운영의 ZIP 생성 경로와 동일하게 custom majorMinor("1.0.x") 아래에 디렉토리 생성
        Path created = svc.createCustomVersionDirectory("infraeye2", "siteA", "1.0.x",
                "1.1.0-siteA.1.0.0");
        Files.writeString(created.resolve("engine.bin"), "x");
        assertThat(Files.exists(created)).isTrue();

        svc.deleteVersionDirectory(version);

        // 삭제가 base majorMinor("1.1.x")만 보면 이 디렉토리는 남는다 → 버그
        assertThat(Files.exists(created)).isFalse();
    }

    @Test
    @DisplayName("커스텀 레거시 레이아웃(base majorMinor)에 만든 디렉토리도 삭제가 제거한다 (dual-path 보장)")
    void deletesCustomVersionDirectory_atLegacyBaseMajorMinorLayout(@TempDir Path tempDir) throws IOException {
        ReleaseVersionFileSystemService svc = newService(tempDir);
        ReleaseVersion version = customVersion();
        // 레거시 비-ZIP 생성 경로(createDirectoryStructure)는 base majorMinor("1.1.x")를 쓴다
        Path created = svc.createCustomVersionDirectory("infraeye2", "siteA", "1.1.x",
                "1.1.0-siteA.1.0.0");
        Files.writeString(created.resolve("engine.bin"), "x");

        svc.deleteVersionDirectory(version);

        assertThat(Files.exists(created)).isFalse();
    }

    @Test
    @DisplayName("커스텀 핫픽스: 생성(createHotfixDirectoryStructure)과 삭제 경로가 일치해 디렉토리가 제거된다")
    void deletesCustomHotfixDirectory_createAndDeletePathsAgree(@TempDir Path tempDir) {
        ReleaseVersionFileSystemService svc = newService(tempDir);
        ReleaseVersion base = customVersion();
        ReleaseVersion hotfix = ReleaseVersion.builder()
                .releaseVersionId(7L).project(base.getProject()).releaseType("CUSTOM").site(base.getSite())
                .version(base.getVersion())
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .customMajorVersion(1).customMinorVersion(0).customPatchVersion(0)
                .hotfixVersion(1).hotfixBaseVersion(base)
                .build();
        // 운영과 동일하게 서비스의 생성 메서드로 디렉토리를 만든다
        svc.createHotfixDirectoryStructure(hotfix, base);
        Path hotfixDir = tempDir.resolve("versions/infraeye2/custom/siteA/1.1.x/1.1.0-siteA.1.0.0/hotfix/1");
        assertThat(Files.exists(hotfixDir)).isTrue();

        svc.deleteHotfixDirectory(hotfix);

        assertThat(Files.exists(hotfixDir)).isFalse();
    }

    @Test
    @DisplayName("정상 빌드 디렉토리는 예외 없이 완전히 삭제된다")
    void deletesNormalBuildDirectory(@TempDir Path tempDir) throws IOException {
        ReleaseVersionFileSystemService svc = newService(tempDir);
        ReleaseVersion build = buildVersion();
        Path dir = svc.resolveBuildBasePath(build);  // 서비스가 실제 삭제할 경로 그대로 사용
        Files.createDirectories(dir.resolve("web/assets"));
        Files.writeString(dir.resolve("web/foo.js"), "x");
        Files.writeString(dir.resolve("web/assets/a.css"), "y");

        svc.deleteBuildDirectory(build);

        assertThat(Files.exists(dir)).isFalse();
    }

    @Test
    @DisplayName("재생성 가드: 표준 버전 생성 시 이전 삭제의 잔존물을 먼저 제거한다 (#잔존물병합)")
    void createVersionDirectory_cleansLeftoverResidue(@TempDir Path tempDir) throws IOException {
        ReleaseVersionFileSystemService svc = newService(tempDir);
        Path target = tempDir.resolve("versions/infraeye2/standard/1.1.x/1.1.7");
        Files.createDirectories(target.resolve("web"));
        Files.writeString(target.resolve("web/stale.js"), "old");

        VersionParser.VersionInfo info = new VersionParser.VersionInfo(1, 1, 7, "1.1.x");
        svc.createVersionDirectory(info, "infraeye2");

        // 잔존 파일이 새 버전과 합쳐지면 패치에 낡은 파일이 유입된다 — 반드시 제거돼야 한다
        assertThat(Files.exists(target.resolve("web/stale.js"))).isFalse();
        assertThat(Files.isDirectory(target)).isTrue();
    }

    @Test
    @DisplayName("재생성 가드: 커스텀 버전 생성 시 이전 삭제의 잔존물을 먼저 제거한다 (#잔존물병합)")
    void createCustomVersionDirectory_cleansLeftoverResidue(@TempDir Path tempDir) throws IOException {
        ReleaseVersionFileSystemService svc = newService(tempDir);
        Path target = tempDir.resolve("versions/infraeye2/custom/siteA/1.0.x/1.1.0-siteA.1.0.0");
        Files.createDirectories(target);
        Files.writeString(target.resolve("stale.sql"), "old");

        svc.createCustomVersionDirectory("infraeye2", "siteA", "1.0.x", "1.1.0-siteA.1.0.0");

        assertThat(Files.exists(target.resolve("stale.sql"))).isFalse();
        assertThat(Files.isDirectory(target)).isTrue();
    }

    @Test
    @DisplayName("재생성 가드: 레거시 생성(createDirectoryStructure)도 잔존물을 먼저 제거한다 (#잔존물병합)")
    void createDirectoryStructure_cleansLeftoverResidue(@TempDir Path tempDir) throws IOException {
        ReleaseVersionFileSystemService svc = newService(tempDir);
        Project project = Project.builder().projectId("infraeye2").projectName("InfraEye 2.0").build();
        ReleaseVersion version = ReleaseVersion.builder()
                .releaseVersionId(61L).project(project).releaseType("STANDARD")
                .version("1.1.1")
                .majorVersion(1).minorVersion(1).patchVersion(1)
                .build();
        Path target = tempDir.resolve("versions/infraeye2/standard/1.1.x/1.1.1");
        Files.createDirectories(target.resolve("web"));
        Files.writeString(target.resolve("web/stale.js"), "old");

        svc.createDirectoryStructure(version, null);

        assertThat(Files.exists(target.resolve("web/stale.js"))).isFalse();
        assertThat(Files.isDirectory(target.resolve("mariadb"))).isTrue();
    }

    @Test
    @DisplayName("재생성 가드: 핫픽스 생성 시 해당 핫픽스 디렉토리 잔존물만 제거, 버전의 다른 내용은 보존")
    void createHotfixDirectoryStructure_cleansOnlyHotfixResidue(@TempDir Path tempDir) throws IOException {
        ReleaseVersionFileSystemService svc = newService(tempDir);
        ReleaseVersion base = customVersion();
        ReleaseVersion hotfix = ReleaseVersion.builder()
                .releaseVersionId(7L).project(base.getProject()).releaseType("CUSTOM").site(base.getSite())
                .version(base.getVersion())
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .customMajorVersion(1).customMinorVersion(0).customPatchVersion(0)
                .hotfixVersion(1).hotfixBaseVersion(base)
                .build();
        Path versionDir = tempDir.resolve("versions/infraeye2/custom/siteA/1.1.x/1.1.0-siteA.1.0.0");
        Path hotfixDir = versionDir.resolve("hotfix/1");
        Files.createDirectories(hotfixDir);
        Files.writeString(hotfixDir.resolve("stale.sql"), "old");
        // 같은 버전의 다른 콘텐츠는 건드리면 안 된다
        Files.createDirectories(versionDir.resolve("database"));
        Files.writeString(versionDir.resolve("database/keep.sql"), "keep");

        svc.createHotfixDirectoryStructure(hotfix, base);

        assertThat(Files.exists(hotfixDir.resolve("stale.sql"))).isFalse();
        assertThat(Files.isDirectory(hotfixDir.resolve("mariadb"))).isTrue();
        assertThat(Files.exists(versionDir.resolve("database/keep.sql"))).isTrue();
    }

    @Test
    @DisplayName("재생성 가드: 빌드 생성 시 동번호 빌드의 잔존 디렉토리를 제거한다 (#잔존물병합)")
    void ensureCleanBuildDirectory_removesLeftoverResidue(@TempDir Path tempDir) throws IOException {
        ReleaseVersionFileSystemService svc = newService(tempDir);
        ReleaseVersion build = buildVersion();
        Path dir = svc.resolveBuildBasePath(build);
        Files.createDirectories(dir.resolve("web"));
        Files.writeString(dir.resolve("web/stale.js"), "old");

        svc.ensureCleanBuildDirectory(build);

        // 잔존물이 남은 채 ZIP 이 풀리면 Files.walk 기반 패치에 낡은 파일이 유입된다
        assertThat(Files.exists(dir)).isFalse();
    }

    @Test
    @DisplayName("재생성 가드: 잔존물이 지워지지 않으면(핸들 점유) 생성을 거부한다 — 잘못된 패치보다 안전")
    void createVersionDirectory_rejectsWhenResidueUndeletable(@TempDir Path tempDir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")),
                "root 는 권한을 무시해 삭제가 항상 성공 — 삭제 불가 모사 불가");
        ReleaseVersionFileSystemService svc = newService(tempDir);
        Path target = tempDir.resolve("versions/infraeye2/standard/1.1.x/1.1.7");
        Path locked = target.resolve("locked");
        Files.createDirectories(locked);
        Files.writeString(locked.resolve("stuck.bin"), "z");
        Files.setPosixFilePermissions(locked, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));

        try {
            VersionParser.VersionInfo info = new VersionParser.VersionInfo(1, 1, 7, "1.1.x");
            assertThatThrownBy(() -> svc.createVersionDirectory(info, "infraeye2"))
                    .isInstanceOf(BusinessException.class);
        } finally {
            Files.setPosixFilePermissions(locked, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        }
    }

    @Test
    @DisplayName("버전 디렉토리 삭제는 best-effort — 일부 항목 실패해도 예외를 던지지 않는다 (SMB 핸들 지연 대응)")
    void bestEffort_versionDirectory_doesNotThrowOnPartialFailure(@TempDir Path tempDir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")),
                "root 는 권한을 무시해 삭제가 항상 성공 — 부분 실패 모사 불가");
        ReleaseVersionFileSystemService svc = newService(tempDir);
        ReleaseVersion version = customVersion();
        Path created = svc.createCustomVersionDirectory("infraeye2", "siteA", "1.0.x",
                "1.1.0-siteA.1.0.0");
        Files.writeString(created.resolve("deletable.sql"), "x");
        Path locked = created.resolve("locked");
        Files.createDirectories(locked);
        Files.writeString(locked.resolve("stuck.bin"), "z");
        Files.setPosixFilePermissions(locked, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));

        try {
            // strict 라면 BusinessException → 트랜잭션 롤백 → DB 행 잔존(반파 상태) — best-effort 여야 한다
            assertThatCode(() -> svc.deleteVersionDirectory(version)).doesNotThrowAnyException();
            assertThat(Files.exists(created.resolve("deletable.sql"))).isFalse();
        } finally {
            Files.setPosixFilePermissions(locked, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        }
    }

    @Test
    @DisplayName("핫픽스 디렉토리 삭제는 best-effort — 일부 항목 실패해도 예외를 던지지 않는다 (SMB 핸들 지연 대응)")
    void bestEffort_hotfixDirectory_doesNotThrowOnPartialFailure(@TempDir Path tempDir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")),
                "root 는 권한을 무시해 삭제가 항상 성공 — 부분 실패 모사 불가");
        ReleaseVersionFileSystemService svc = newService(tempDir);
        ReleaseVersion base = customVersion();
        ReleaseVersion hotfix = ReleaseVersion.builder()
                .releaseVersionId(7L).project(base.getProject()).releaseType("CUSTOM").site(base.getSite())
                .version(base.getVersion())
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .customMajorVersion(1).customMinorVersion(0).customPatchVersion(0)
                .hotfixVersion(1).hotfixBaseVersion(base)
                .build();
        svc.createHotfixDirectoryStructure(hotfix, base);
        Path hotfixDir = tempDir.resolve("versions/infraeye2/custom/siteA/1.1.x/1.1.0-siteA.1.0.0/hotfix/1");
        Path locked = hotfixDir.resolve("locked");
        Files.createDirectories(locked);
        Files.writeString(locked.resolve("stuck.bin"), "z");
        Files.setPosixFilePermissions(locked, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));

        try {
            assertThatCode(() -> svc.deleteHotfixDirectory(hotfix)).doesNotThrowAnyException();
        } finally {
            Files.setPosixFilePermissions(locked, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        }
    }

    @Test
    @DisplayName("회귀: 일부 항목 삭제가 실패해도 best-effort 라 예외를 던지지 않는다 (CIFS 부분 실패 모사)")
    void bestEffort_doesNotThrowOnPartialFailure(@TempDir Path tempDir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")),
                "root 는 권한을 무시해 삭제가 항상 성공 — 부분 실패 모사 불가");
        ReleaseVersionFileSystemService svc = newService(tempDir);
        ReleaseVersion build = buildVersion();
        Path dir = svc.resolveBuildBasePath(build);
        Files.createDirectories(dir.resolve("web"));
        Files.writeString(dir.resolve("web/deletable.js"), "x");  // 삭제 가능
        Path locked = dir.resolve("web/locked");
        Files.createDirectories(locked);
        Files.writeString(locked.resolve("stuck.bin"), "z");
        // locked 디렉토리에서 쓰기 권한 제거 → 내부 파일 삭제 불가 (CIFS 잠금/실패 모사)
        Files.setPosixFilePermissions(locked, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));

        try {
            // strict 였다면 BusinessException 을 던졌을 것 — best-effort 라 예외 없음
            assertThatCode(() -> svc.deleteBuildDirectory(build)).doesNotThrowAnyException();
            // best-effort 는 지울 수 있는 것은 지운다 — 삭제 가능했던 파일은 제거됨
            assertThat(Files.exists(dir.resolve("web/deletable.js"))).isFalse();
            // 잠긴 항목은 남는다 (무해한 orphan)
            assertThat(Files.exists(locked.resolve("stuck.bin"))).isTrue();
        } finally {
            // 권한 복구해야 @TempDir 자동 정리가 가능
            Files.setPosixFilePermissions(locked, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        }
    }
}
