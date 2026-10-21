package com.ts.rm.domain.releaseversion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionHierarchyRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseVersionTreeService 테스트")
class ReleaseVersionTreeServiceTest {

    @Mock ReleaseVersionRepository releaseVersionRepository;
    @Mock ReleaseVersionHierarchyRepository hierarchyRepository;
    @Mock ReleaseFileRepository releaseFileRepository;
    @Mock ReleaseVersionFileSystemService fileSystemService;

    @InjectMocks ReleaseVersionTreeService service;

    private ReleaseVersion base(Long id, String version) {
        String[] parts = version.split("\\.");
        return ReleaseVersion.builder()
                .releaseVersionId(id)
                .releaseType("STANDARD")
                .version(version)
                .majorVersion(Integer.parseInt(parts[0]))
                .minorVersion(Integer.parseInt(parts[1]))
                .patchVersion(Integer.parseInt(parts[2]))
                .build();
    }

    private ReleaseVersion build(Long id, ReleaseVersion base, int buildVersion, int iteration,
            LocalDateTime createdAt) {
        return ReleaseVersion.builder()
                .releaseVersionId(id)
                .releaseType("STANDARD")
                .version(base.getVersion())
                .majorVersion(base.getMajorVersion())
                .minorVersion(base.getMinorVersion())
                .patchVersion(base.getPatchVersion())
                .buildBaseVersion(base)
                .buildVersion(buildVersion)
                .buildIteration(iteration)
                .createdAt(createdAt)
                .build();
    }

    @Test
    @DisplayName("빌드 노드는 마지막에 생성된 빌드가 맨 위 — 같은 날짜의 회차(-1/-2)도 생성 순서를 따른다")
    void builds_newestCreatedFirst() {
        ReleaseVersion base = base(10L, "1.1.29");
        ReleaseVersion b261020_1 = build(197L, base, 261020, 1, LocalDateTime.of(2026, 10, 20, 1, 22));
        ReleaseVersion b261021_1 = build(198L, base, 261021, 1, LocalDateTime.of(2026, 10, 21, 1, 39));
        ReleaseVersion b261021_2 = build(199L, base, 261021, 2, LocalDateTime.of(2026, 10, 21, 5, 46));

        // DB 가 돌려주는 순서와 무관해야 한다 (스크린샷 재현: -1 이 -2 위에 오던 입력)
        List<ReleaseVersionDto.MajorMinorNode> groups = service.buildMajorMinorGroupsFromDb(
                new java.util.ArrayList<>(List.of(base, b261021_1, b261021_2, b261020_1)));

        List<String> buildOrder = groups.get(0).versions().get(0).builds().stream()
                .map(ReleaseVersionDto.BuildNode::fullVersion)
                .toList();
        assertThat(buildOrder).containsExactly(
                "1.1.29.261021-2", "1.1.29.261021-1", "1.1.29.261020-1");
    }
}
