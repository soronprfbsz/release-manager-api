package com.ts.rm.domain.releasefile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * ReleaseFile Custom Repository 버전 범위 조회 테스트.
 *
 * <p>회귀 방지 목적: 과거 {@code version} VARCHAR 컬럼에 대한 사전식(lexicographic) 비교를
 * 사용해 {@code 1.1.5 → 1.1.12} 처럼 patch 번호가 한 자리→두 자리로 넘어가는 범위에서
 * 결과가 0건이 되던 버그가 있었다. 이제 major/minor/patch 정수 비교로 수정되었으므로
 * 두 자리 버전을 포함한 범위도 정상 조회되어야 한다.
 */
@DataJpaTest
@Import(ReleaseFileRepositoryCustomTest.TestConfig.class)
@ActiveProfiles("test")
@DisplayName("ReleaseFile Custom Repository 버전 범위 조회 테스트")
class ReleaseFileRepositoryCustomTest {

    private static final String PROJECT_ID = "infraeye2";

    @Autowired
    private ReleaseFileRepository releaseFileRepository;

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

        // 실제 260708 패치 상황 재현: 1.1.5 ~ 1.1.12
        // MARIADB SQL 파일은 1.1.5, 1.1.6, 1.1.7, 1.1.8, 1.1.12 에만 존재
        createVersionWithFiles("1.1.5", FileCategory.DATABASE, "MARIADB", "dump_ip.sql");
        createVersionWithFiles("1.1.6", FileCategory.DATABASE, "MARIADB", "patch_agg.sql");
        createVersionWithFiles("1.1.7", FileCategory.DATABASE, "MARIADB", "patch_agent.sql");
        createVersionWithFiles("1.1.8", FileCategory.DATABASE, "MARIADB", "icmp_policy.sql");
        createVersionWithFiles("1.1.9", null, null, null);   // SQL 없음 (engine/etc 만)
        createVersionWithFiles("1.1.10", null, null, null);  // SQL 없음
        createVersionWithFiles("1.1.11", null, null, null);  // SQL 없음
        createVersionWithFiles("1.1.12", FileCategory.DATABASE, "MARIADB", "sms_agent.sql");

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("sub_category 범위 조회 - 1.1.5→1.1.12 두 자리 교차 범위의 MARIADB 파일이 모두 조회된다")
    void findBySubCategory_acrossDoubleDigitRange() {
        // when
        List<ReleaseFile> result = releaseFileRepository
                .findReleaseFilesBetweenVersionsBySubCategory(PROJECT_ID, "1.1.5", "1.1.12", "MARIADB");

        // then - 1.1.5, 1.1.6, 1.1.7, 1.1.8, 1.1.12 의 파일 5개
        assertThat(result).hasSize(5);
        assertThat(result)
                .extracting(f -> f.getReleaseVersion().getVersion())
                // 정수 정렬: 1.1.12 는 1.1.8 보다 뒤여야 한다 (사전식이면 1.1.12 가 앞으로 온다)
                .containsExactly("1.1.5", "1.1.6", "1.1.7", "1.1.8", "1.1.12");
    }

    @Test
    @DisplayName("전체 파일 범위 조회 - 1.1.5→1.1.12 범위에 1.1.12 파일이 포함된다")
    void findBetweenVersions_acrossDoubleDigitRange() {
        // when
        List<ReleaseFile> result = releaseFileRepository
                .findReleaseFilesBetweenVersions(PROJECT_ID, "1.1.5", "1.1.12");

        // then - MARIADB(5) + WEB(각 버전 1개씩 8) = 13
        assertThat(result).extracting(f -> f.getFileName()).contains("sms_agent.sql");
        assertThat(result)
                .extracting(f -> f.getReleaseVersion().getVersion())
                .contains("1.1.12"); // 두 자리 버전이 누락되지 않아야 한다
    }

    @Test
    @DisplayName("빌드 아티팩트 범위 조회 - 1.1.5→1.1.12 범위의 WEB/ENGINE 파일이 모두 조회된다")
    void findBuildArtifacts_acrossDoubleDigitRange() {
        // when
        List<ReleaseFile> result = releaseFileRepository
                .findBuildArtifactsBetweenVersions(PROJECT_ID, "1.1.5", "1.1.12");

        // then - 각 버전마다 WEB 파일 1개씩 = 8개
        assertThat(result).hasSize(8);
        assertThat(result)
                .extracting(f -> f.getReleaseVersion().getVersion())
                .contains("1.1.9", "1.1.10", "1.1.11", "1.1.12");
    }

    // ========== Helper Methods ==========

    /**
     * 버전 1개 생성 + (선택) DATABASE SQL 파일 1개 + WEB 빌드 파일 1개.
     * <p>모든 버전에 WEB 파일을 붙여 빌드 아티팩트 조회도 검증한다.
     */
    private void createVersionWithFiles(String version, FileCategory sqlCategory,
            String subCategory, String sqlFileName) {
        String[] parts = version.split("\\.");
        ReleaseVersion rv = releaseVersionRepository.save(ReleaseVersion.builder()
                .project(testProject)
                .version(version)
                .releaseType("STANDARD")
                .majorVersion(Integer.parseInt(parts[0]))
                .minorVersion(Integer.parseInt(parts[1]))
                .patchVersion(Integer.parseInt(parts[2]))
                .createdByEmail("system")
                .comment("테스트 버전 " + version)
                .build());

        if (sqlCategory != null) {
            releaseFileRepository.save(ReleaseFile.builder()
                    .releaseVersion(rv)
                    .fileType("sql")
                    .fileCategory(sqlCategory)
                    .subCategory(subCategory)
                    .fileName(sqlFileName)
                    .filePath("database/mariadb/" + version + "/" + sqlFileName)
                    .executionOrder(1)
                    .build());
        }

        // WEB 빌드 파일 (모든 버전)
        releaseFileRepository.save(ReleaseFile.builder()
                .releaseVersion(rv)
                .fileType("war")
                .fileCategory(FileCategory.WEB)
                .subCategory(null)
                .fileName("app-" + version + ".war")
                .filePath("web/" + version + "/app-" + version + ".war")
                .executionOrder(1)
                .build());
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
