package com.ts.rm.domain.releaseversion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(ReleaseVersionRepositoryCustomTest.TestConfig.class)
@ActiveProfiles("test")
@DisplayName("ReleaseVersion Custom Repository 테스트")
class ReleaseVersionRepositoryCustomTest {

    private static final String PROJECT_ID = "infraeye2";

    @Autowired
    private ReleaseVersionRepository releaseVersionRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Project testProject;

    @BeforeEach
    void setUp() {
        testProject = projectRepository.save(Project.builder()
                .projectId(PROJECT_ID)
                .projectName("Infraeye 2")
                .build());
    }

    @Test
    @DisplayName("버전 범위 조회 테스트 - From 1.0.0 To 1.1.1")
    void findVersionsBetween() {
        // given
        createAndSaveVersion("1.0.0");
        createAndSaveVersion("1.1.0");
        createAndSaveVersion("1.1.1");
        createAndSaveVersion("1.2.0");

        entityManager.flush();
        entityManager.clear();

        // when - fromVersion <= version <= toVersion (양 끝 포함)
        List<ReleaseVersion> result = releaseVersionRepository
                .findVersionsBetween(PROJECT_ID, "STANDARD", "1.0.0", "1.1.1");

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getVersion()).isEqualTo("1.0.0");
        assertThat(result.get(1).getVersion()).isEqualTo("1.1.0");
        assertThat(result.get(2).getVersion()).isEqualTo("1.1.1");
    }

    @Test
    @DisplayName("버전 범위 조회 테스트 - 중간 버전이 없는 경우 (from 만 포함)")
    void findVersionsBetween_noVersions() {
        // given
        createAndSaveVersion("1.0.0");
        createAndSaveVersion("1.2.0");

        entityManager.flush();
        entityManager.clear();

        // when - fromVersion <= version <= toVersion (양 끝 포함)
        List<ReleaseVersion> result = releaseVersionRepository
                .findVersionsBetween(PROJECT_ID, "STANDARD", "1.0.0", "1.1.0");

        // then - 1.0.0 만 포함 (1.2.0 은 to 초과)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVersion()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("버전 범위 조회 테스트 - 버전 정렬 확인")
    void findVersionsBetween_orderCheck() {
        // given
        createAndSaveVersion("1.0.0");
        createAndSaveVersion("1.2.0");
        createAndSaveVersion("1.1.0");
        createAndSaveVersion("1.1.5");
        createAndSaveVersion("1.1.3");

        entityManager.flush();
        entityManager.clear();

        // when - fromVersion <= version <= toVersion (양 끝 포함)
        List<ReleaseVersion> result = releaseVersionRepository
                .findVersionsBetween(PROJECT_ID, "STANDARD", "1.0.0", "1.2.0");

        // then
        assertThat(result).hasSize(5);
        assertThat(result.get(0).getVersion()).isEqualTo("1.0.0");
        assertThat(result.get(1).getVersion()).isEqualTo("1.1.0");
        assertThat(result.get(2).getVersion()).isEqualTo("1.1.3");
        assertThat(result.get(3).getVersion()).isEqualTo("1.1.5");
        assertThat(result.get(4).getVersion()).isEqualTo("1.2.0");
    }

    @Test
    @DisplayName("범위 빌드 조회 - 마지막에 생성된 빌드가 맨 앞 (base 가 달라 같은 날짜/회차 번호가 겹쳐도)")
    void findBuildsInBaseRange_newestCreatedFirst() {
        // given: 운영 실데이터 형태 — 회차는 base 별로 매겨져 base 간 비교 기준이 되지 못한다
        ReleaseVersion base110 = createAndSaveVersion("1.1.0");
        ReleaseVersion base111 = createAndSaveVersion("1.1.1");
        ReleaseVersion a1 = createAndSaveBuild(base110, 260824, 1, LocalDateTime.of(2026, 8, 24, 9, 0));
        ReleaseVersion a2 = createAndSaveBuild(base110, 260824, 2, LocalDateTime.of(2026, 8, 24, 11, 0));
        ReleaseVersion b1 = createAndSaveBuild(base111, 260824, 1, LocalDateTime.of(2026, 8, 24, 12, 0));

        entityManager.flush();
        entityManager.clear();

        // when
        List<ReleaseVersion> result = releaseVersionRepository.findBuildsInBaseRange(
                PROJECT_ID, base110.getReleaseVersionId(), base111.getReleaseVersionId(), null);

        // then
        assertThat(result).extracting(ReleaseVersion::getReleaseVersionId)
                .containsExactly(b1.getReleaseVersionId(), a2.getReleaseVersionId(), a1.getReleaseVersionId());
    }

    // ========== Helper Methods ==========

    private ReleaseVersion createAndSaveBuild(ReleaseVersion base, int buildVersion, int iteration,
            LocalDateTime createdAt) {
        return releaseVersionRepository.save(ReleaseVersion.builder()
                .project(testProject)
                .version(base.getVersion())
                .releaseType("STANDARD")
                .majorVersion(base.getMajorVersion())
                .minorVersion(base.getMinorVersion())
                .patchVersion(base.getPatchVersion())
                .buildBaseVersion(base)
                .buildVersion(buildVersion)
                .buildIteration(iteration)
                .createdAt(createdAt)
                .createdByEmail("system")
                .build());
    }

    private ReleaseVersion createAndSaveVersion(String version) {
        String[] parts = version.split("\\.");
        ReleaseVersion releaseVersion = ReleaseVersion.builder()
                .project(testProject)
                .version(version)
                .releaseType("STANDARD")
                .majorVersion(Integer.parseInt(parts[0]))
                .minorVersion(Integer.parseInt(parts[1]))
                .patchVersion(Integer.parseInt(parts[2]))
                .createdByEmail("system")
                .comment("테스트 버전")
                .build();
        return releaseVersionRepository.save(releaseVersion);
    }

    /**
     * QueryDSL 테스트용 설정
     */
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.data.jpa.repository.config.EnableJpaAuditing
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public com.querydsl.jpa.impl.JPAQueryFactory jpaQueryFactory(
                jakarta.persistence.EntityManager entityManager) {
            return new com.querydsl.jpa.impl.JPAQueryFactory(entityManager);
        }
    }
}
