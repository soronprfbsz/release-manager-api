package com.ts.rm.domain.site.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.site.entity.SiteVersion;
import com.ts.rm.domain.site.repository.SiteProjectRepository;
import com.ts.rm.domain.site.repository.SiteRepository;
import com.ts.rm.domain.site.repository.SiteVersionRepository;
import com.ts.rm.domain.site.service.SiteVersionService.BuildSnapshot;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SiteVersionService 테스트")
class SiteVersionServiceTest {

    @Mock
    private SiteVersionRepository siteVersionRepository;
    @Mock
    private SiteRepository siteRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private SiteProjectRepository siteProjectRepository;
    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @InjectMocks
    private SiteVersionService service;

    @Test
    @DisplayName("extractBaseVersion — fullVersion 에서 major.minor.patch 추출, 미일치 시 원본")
    void extractBaseVersion_extractsBase() {
        assertThat(service.extractBaseVersion("1.1.0.260511-1")).isEqualTo("1.1.0");
        assertThat(service.extractBaseVersion("2.3.4")).isEqualTo("2.3.4");
        assertThat(service.extractBaseVersion("abc")).isEqualTo("abc");
        assertThat(service.extractBaseVersion(null)).isNull();
    }

    @Test
    @DisplayName("applyComponentVersions — BASE 1 + WEB 1 + ENGINE 2 → upsert 4회")
    void applyComponentVersions_fansOutToUpsert() {
        // given — 모든 단건 조회를 기존 row 로 만들어 update 경로(site/project 조회 불필요)
        SiteVersion existing = SiteVersion.builder().build();
        when(siteVersionRepository
                .findBySite_SiteIdAndProject_ProjectIdAndComponentAndEngineNameIsNull(
                        any(), anyString(), anyString()))
                .thenReturn(Optional.of(existing));
        when(siteVersionRepository
                .findBySite_SiteIdAndProject_ProjectIdAndComponentAndEngineName(
                        any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existing));

        List<BuildSnapshot> builds = List.of(
                new BuildSnapshot("WEB", null, "1.1.0.260511-1"),
                new BuildSnapshot("ENGINE", "NC_SMS", "1.1.0.260511-2"),
                new BuildSnapshot("ENGINE", "NC_GATEWAY", "1.1.0.260511-3"));

        // when
        service.applyComponentVersions(1L, "PRJ", "1.1.0", builds, "ops@ts.com",
                LocalDateTime.now());

        // then — BASE/WEB/ENGINE×2 = 4 upsert → save 4회
        verify(siteVersionRepository, times(4)).save(any(SiteVersion.class));
    }

    @Test
    @DisplayName("applyComponentVersions — builds 비면 BASE 만 upsert")
    void applyComponentVersions_emptyBuilds_baseOnly() {
        SiteVersion existing = SiteVersion.builder().build();
        when(siteVersionRepository
                .findBySite_SiteIdAndProject_ProjectIdAndComponentAndEngineNameIsNull(
                        any(), anyString(), eq("BASE")))
                .thenReturn(Optional.of(existing));

        service.applyComponentVersions(1L, "PRJ", "1.1.0", List.of(), "ops@ts.com",
                LocalDateTime.now());

        verify(siteVersionRepository, times(1)).save(any(SiteVersion.class));
    }

    @Test
    @DisplayName("clearBySiteAndProject — 리포지토리 삭제 위임")
    void clearBySiteAndProject_delegates() {
        service.clearBySiteAndProject(1L, "PRJ");
        verify(siteVersionRepository).deleteAllBySite_SiteIdAndProject_ProjectId(1L, "PRJ");
    }
}
