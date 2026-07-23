package com.ts.rm.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.domain.dashboard.dto.DashboardDto;
import com.ts.rm.domain.dashboard.dto.DashboardDto.LatestInstallVersion;
import com.ts.rm.domain.dashboard.dto.DashboardDto.RecentPatch;
import com.ts.rm.domain.dashboard.dto.DashboardDto.RecentVersion;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/**
 * DashboardService 테스트
 */
@SpringBootTest
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

    @Test
    @DisplayName("대시보드 최근 데이터 조회 - 성공")
    @Sql("/test-data/release-version-tree-test-data.sql")
    void getRecentData_Success() {
        // when
        DashboardDto.Response response = dashboardService.getRecentData("infraeye2", 4, 3);

        // then
        assertThat(response).isNotNull();

        // 최신 설치본 검증 - 테스트 데이터에는 INSTALL이 없으므로 null일 수 있음
        LatestInstallVersion latestInstall = response.latestInstall();
        // latestInstall은 null이거나 INSTALL 카테고리여야 함
        if (latestInstall != null) {
            assertThat(latestInstall.releaseCategory()).isEqualTo("INSTALL");
            assertThat(latestInstall.releaseType()).isEqualTo("STANDARD");
        }

        // 최근 릴리즈 버전 검증 (최대 4개)
        List<RecentVersion> recentVersions = response.recentVersions();
        assertThat(recentVersions).isNotNull();
        assertThat(recentVersions.size()).isLessThanOrEqualTo(4);
        // 테스트 데이터에는 PATCH만 있음
        recentVersions.forEach(version -> {
            assertThat(version.releaseCategory()).isEqualTo("PATCH");
            assertThat(version.releaseType()).isEqualTo("STANDARD");
            assertThat(version.version()).isNotBlank();
            assertThat(version.fileCategories()).isNotNull(); // fileCategories 필드 검증
        });

        // 최근 생성 패치 검증 (최대 3개) - 테스트 데이터에는 patch가 없을 수 있음
        List<RecentPatch> recentPatches = response.recentPatches();
        assertThat(recentPatches).isNotNull();
        assertThat(recentPatches.size()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("대시보드 최근 데이터 조회 - 데이터가 없어도 에러 없이 응답")
    @Sql(statements = {
            "DELETE FROM patch_file",
            "DELETE FROM release_file",
            "DELETE FROM release_version_hierarchy",
            "DELETE FROM release_version",
            "DELETE FROM site"
    })
    void getRecentData_EmptyData() {
        // given - 테스트 데이터 없는 상태

        // when
        DashboardDto.Response response = dashboardService.getRecentData("infraeye2", 4, 3);

        // then
        assertThat(response).isNotNull();
        // latestInstall은 null일 수 있음
        // recentVersions와 recentPatches는 빈 리스트여야 함
        assertThat(response.recentVersions()).isNotNull().isEmpty();
        assertThat(response.recentPatches()).isNotNull().isEmpty();
    }
}
