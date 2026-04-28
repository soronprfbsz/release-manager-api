package com.ts.rm.domain.releaseversion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuildsInRangeService 테스트")
class BuildsInRangeServiceTest {

    @Mock ReleaseVersionRepository releaseVersionRepository;
    @Mock ReleaseVersionFileSystemService fileSystemService;

    @InjectMocks BuildsInRangeService service;

    @TempDir Path tempDir;

    private Project project(String id) {
        Project p = new Project();
        p.setProjectId(id);
        return p;
    }

    private ReleaseVersion build(Long id, ReleaseVersion base, int buildVersion, LocalDateTime createdAt) {
        ReleaseVersion v = new ReleaseVersion();
        v.setReleaseVersionId(id);
        v.setBuildBaseVersion(base);
        v.setBuildVersion(buildVersion);
        v.setCreatedAt(createdAt);
        v.setProject(base.getProject());
        v.setMajorVersion(base.getMajorVersion());
        v.setMinorVersion(base.getMinorVersion());
        v.setPatchVersion(base.getPatchVersion());
        return v;
    }

    private ReleaseVersion base(Long id) {
        ReleaseVersion v = new ReleaseVersion();
        v.setReleaseVersionId(id);
        v.setProject(project("p"));
        v.setMajorVersion(1);
        v.setMinorVersion(1);
        v.setPatchVersion(0);
        v.setBuildVersion(0);
        v.setHotfixVersion(0);
        return v;
    }

    private void touchFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }

    @Test
    @DisplayName("빌드 0개면 web/engines/hotfixesInRange 모두 빈 배열")
    void empty_returnsAllEmpty() {
        given(releaseVersionRepository.findBuildsInBaseRange("p", 1L, 9L, null)).willReturn(List.of());
        given(releaseVersionRepository.findHotfixesInBaseRange("p", 1L, 9L, null)).willReturn(List.of());

        ReleaseVersionDto.BuildsInRangeResponse resp = service.getBuildsInRange("p", 1L, 9L, null);

        assertThat(resp.web()).isEmpty();
        assertThat(resp.engines()).isEmpty();
        assertThat(resp.hotfixesInRange()).isEmpty();
    }

    @Test
    @DisplayName("web/, engine/{engineName}/, engine 직속 파일이 모두 후보로 분류됨")
    void scansBuildDirectoryAndGroupsCandidates() throws IOException {
        ReleaseVersion base = base(10L);
        ReleaseVersion b1 = build(101L, base, 260427, LocalDateTime.of(2026, 4, 27, 12, 0));
        ReleaseVersion b2 = build(102L, base, 260428, LocalDateTime.of(2026, 4, 28, 12, 0));

        Path d1 = tempDir.resolve("b1"); Path d2 = tempDir.resolve("b2");
        touchFile(d1.resolve("web/index.html"));
        touchFile(d1.resolve("engine/NC_SMS/x.jar"));
        touchFile(d2.resolve("web/index.html"));
        touchFile(d2.resolve("engine/NC_SMS/x.jar"));
        touchFile(d2.resolve("engine/NC_FAULT_MS/y.jar"));
        touchFile(d2.resolve("engine/loose.jar"));

        given(releaseVersionRepository.findBuildsInBaseRange("p", 10L, 10L, null)).willReturn(List.of(b2, b1));
        given(releaseVersionRepository.findHotfixesInBaseRange("p", 10L, 10L, null)).willReturn(List.of());
        given(fileSystemService.resolveBuildBasePath(base, 260427)).willReturn(d1);
        given(fileSystemService.resolveBuildBasePath(base, 260428)).willReturn(d2);

        ReleaseVersionDto.BuildsInRangeResponse resp = service.getBuildsInRange("p", 10L, 10L, null);

        assertThat(resp.web()).extracting(ReleaseVersionDto.BuildCandidate::buildVersionId)
                .containsExactly(102L, 101L);
        assertThat(resp.web().get(0).isLatest()).isTrue();
        assertThat(resp.web().get(1).isLatest()).isFalse();

        assertThat(resp.engines()).extracting(ReleaseVersionDto.EngineGroup::engineName)
                .containsExactlyInAnyOrder("NC_SMS", "NC_FAULT_MS", "UNKNOWN");

        ReleaseVersionDto.EngineGroup smsGroup = resp.engines().stream()
                .filter(g -> g.engineName().equals("NC_SMS")).findFirst().orElseThrow();
        assertThat(smsGroup.candidates()).extracting(ReleaseVersionDto.BuildCandidate::buildVersionId)
                .containsExactly(102L, 101L);

        ReleaseVersionDto.EngineGroup faultGroup = resp.engines().stream()
                .filter(g -> g.engineName().equals("NC_FAULT_MS")).findFirst().orElseThrow();
        assertThat(faultGroup.candidates()).extracting(ReleaseVersionDto.BuildCandidate::buildVersionId)
                .containsExactly(102L);

        ReleaseVersionDto.EngineGroup unknownGroup = resp.engines().stream()
                .filter(g -> g.engineName().equals("UNKNOWN")).findFirst().orElseThrow();
        assertThat(unknownGroup.candidates()).extracting(ReleaseVersionDto.BuildCandidate::buildVersionId)
                .containsExactly(102L);
    }

    @Test
    @DisplayName("engine/{engineName}/ 디렉토리는 있지만 정규 파일이 0개면 후보에서 제외")
    void emptyEngineDirectory_excluded() throws IOException {
        ReleaseVersion base = base(20L);
        ReleaseVersion b1 = build(201L, base, 260427, LocalDateTime.of(2026, 4, 27, 12, 0));
        Path d1 = tempDir.resolve("b1");
        Files.createDirectories(d1.resolve("engine/NC_EMPTY"));
        touchFile(d1.resolve("engine/NC_FILLED/x.jar"));

        given(releaseVersionRepository.findBuildsInBaseRange("p", 20L, 20L, null)).willReturn(List.of(b1));
        given(releaseVersionRepository.findHotfixesInBaseRange("p", 20L, 20L, null)).willReturn(List.of());
        given(fileSystemService.resolveBuildBasePath(base, 260427)).willReturn(d1);

        ReleaseVersionDto.BuildsInRangeResponse resp = service.getBuildsInRange("p", 20L, 20L, null);

        assertThat(resp.engines()).extracting(ReleaseVersionDto.EngineGroup::engineName)
                .containsExactly("NC_FILLED");
    }
}
