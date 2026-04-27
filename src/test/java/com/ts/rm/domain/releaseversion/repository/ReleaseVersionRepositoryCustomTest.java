package com.ts.rm.domain.releaseversion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
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

    // ========== Helper Methods ==========

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
