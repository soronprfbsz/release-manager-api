package com.ts.rm.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.config.AbstractTestBase;
import com.ts.rm.config.TestQueryDslConfig;
import com.ts.rm.domain.dashboard.dto.DashboardDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/**
 * DashboardService 테스트
 *
 * <p>대시보드는 단일 getRecentData 에서 표준/빌드/패치 3개 조회로 분리되었다.
 */
@Import(TestQueryDslConfig.class)
@Transactional
class DashboardServiceTest extends AbstractTestBase {

    @Autowired
    private DashboardService dashboardService;

    @Test
    @DisplayName("최근 표준 버전 조회 - 성공 (limit 이내, STANDARD)")
    @Sql("/test-data/release-version-tree-test-data.sql")
    void getRecentStandardVersions_Success() {
        // when
        DashboardDto.RecentStandardResponse response =
                dashboardService.getRecentStandardVersions("test-project", 4);

        // then
        assertThat(response).isNotNull();
        assertThat(response.versions()).isNotNull();
        assertThat(response.versions().size()).isLessThanOrEqualTo(4);
        response.versions().forEach(version -> {
            assertThat(version.releaseType()).isEqualTo("STANDARD");
            assertThat(version.version()).isNotBlank();
            assertThat(version.fileCategories()).isNotNull();
        });
    }

    @Test
    @DisplayName("최근 패치 조회 - 성공 (limit 이내)")
    @Sql("/test-data/release-version-tree-test-data.sql")
    void getRecentPatches_Success() {
        // when
        DashboardDto.RecentPatchResponse response =
                dashboardService.getRecentPatches("test-project", 3);

        // then
        assertThat(response).isNotNull();
        assertThat(response.patches()).isNotNull();
        assertThat(response.patches().size()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("데이터가 없어도 에러 없이 빈 응답")
    void getRecent_EmptyData() {
        // @Sql 로 데이터를 적재하지 않으므로 H2(create-drop) 는 빈 상태다.
        // when
        DashboardDto.RecentStandardResponse standard =
                dashboardService.getRecentStandardVersions("test-project", 4);
        DashboardDto.RecentBuildResponse build =
                dashboardService.getRecentBuildVersions("test-project", 4);
        DashboardDto.RecentPatchResponse patches =
                dashboardService.getRecentPatches("test-project", 3);

        // then
        assertThat(standard).isNotNull();
        assertThat(standard.versions()).isNotNull().isEmpty();
        assertThat(build).isNotNull();
        assertThat(build.versions()).isNotNull().isEmpty();
        assertThat(patches).isNotNull();
        assertThat(patches.patches()).isNotNull().isEmpty();
    }
}
