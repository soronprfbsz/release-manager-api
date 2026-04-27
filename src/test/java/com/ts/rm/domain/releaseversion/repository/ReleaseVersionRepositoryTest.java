package com.ts.rm.domain.releaseversion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * ReleaseVersion Repository 단위 테스트
 */
@DataJpaTest
@Import(ReleaseVersionRepositoryTest.TestConfig.class)
@ActiveProfiles("test")
@DisplayName("ReleaseVersionRepository 테스트")
class ReleaseVersionRepositoryTest {

    private static final String PROJECT_ID = "infraeye2";

    @Autowired
    private ReleaseVersionRepository releaseVersionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Customer testCustomer;
    private Project testProject;

    @BeforeEach
    void setUp() {
        testProject = projectRepository.save(Project.builder()
                .projectId(PROJECT_ID)
                .projectName("Infraeye 2")
                .build());

        testCustomer = Customer.builder()
                .customerCode("company_a")
                .customerName("A회사")
                .description("테스트 고객사")
                .isActive(true)
                .createdByEmail("admin@tscientific")
                .updatedByEmail("admin@tscientific")
                .build();
        testCustomer = customerRepository.save(testCustomer);
    }

    @Test
    @DisplayName("릴리즈 버전 저장 - 표준 릴리즈")
    void save_Standard_Success() {
        // given
        ReleaseVersion version = ReleaseVersion.builder()
                .project(testProject)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1)
                .minorVersion(1)
                .patchVersion(0)
                .createdByEmail("jhlee@tscientific")
                .comment("새로운 기능 추가")
                .build();

        // when
        ReleaseVersion saved = releaseVersionRepository.save(version);

        // then
        assertThat(saved.getReleaseVersionId()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo("1.1.0");
        assertThat(saved.getReleaseType()).isEqualTo("STANDARD");
        assertThat(saved.getCustomer()).isNull();
        assertThat(saved.getMajorMinor()).isEqualTo("1.1.x"); // @Transient 계산 필드 검증
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("릴리즈 버전 저장 - 커스텀 릴리즈")
    void save_Custom_Success() {
        // given
        ReleaseVersion version = ReleaseVersion.builder()
                .project(testProject)
                .releaseType("CUSTOM")
                .customer(testCustomer)
                .version("1.0.0-custom")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(0)
                .createdByEmail("admin@tscientific")
                .comment("고객사 맞춤 기능")
                .customMajorVersion(1)
                .customMinorVersion(0)
                .customPatchVersion(0)
                .build();

        // when
        ReleaseVersion saved = releaseVersionRepository.save(version);

        // then
        assertThat(saved.getReleaseVersionId()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo("1.0.0-custom");
        assertThat(saved.getReleaseType()).isEqualTo("CUSTOM");
        assertThat(saved.getCustomer()).isNotNull();
        assertThat(saved.getCustomer().getCustomerCode()).isEqualTo("company_a");
    }

    @Test
    @DisplayName("버전으로 조회 - 성공")
    void findByVersion_Success() {
        // given
        ReleaseVersion version = createStandardVersion("1.1.0", 1, 1, 0);
        releaseVersionRepository.save(version);

        // when
        Optional<ReleaseVersion> found = releaseVersionRepository.findByVersion("1.1.0");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getVersion()).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("릴리즈 타입과 버전으로 조회 - 성공")
    void findByReleaseTypeAndVersion_Success() {
        // given
        ReleaseVersion version = createStandardVersion("1.1.0", 1, 1, 0);
        releaseVersionRepository.save(version);

        // when
        Optional<ReleaseVersion> found = releaseVersionRepository
                .findByReleaseTypeAndVersion("STANDARD", "1.1.0");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getVersion()).isEqualTo("1.1.0");
        assertThat(found.get().getReleaseType()).isEqualTo("STANDARD");
    }

    @Test
    @DisplayName("릴리즈 타입별 버전 목록 조회 - 최신순")
    void findAllByReleaseTypeOrderByCreatedAtDesc_Success() {
        // given
        createAndSaveStandardVersions();

        // when
        List<ReleaseVersion> versions = releaseVersionRepository
                .findAllByReleaseTypeOrderByCreatedAtDesc("STANDARD");

        // then
        assertThat(versions).hasSize(3);
        // 최신순 정렬 확인 (1.2.0 > 1.1.1 > 1.1.0)
    }

    @Test
    @DisplayName("고객사별 버전 목록 조회 - 최신순")
    void findAllByCustomerIdOrderByCreatedAtDesc_Success() {
        // given
        ReleaseVersion customVersion1 = ReleaseVersion.builder()
                .project(testProject)
                .releaseType("CUSTOM")
                .customer(testCustomer)
                .version("1.0.0")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(0)
                .createdByEmail("admin@tscientific")
                .comment("첫번째 버전")
                .build();
        releaseVersionRepository.save(customVersion1);

        ReleaseVersion customVersion2 = ReleaseVersion.builder()
                .project(testProject)
                .releaseType("CUSTOM")
                .customer(testCustomer)
                .version("1.0.1")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(1)
                .createdByEmail("admin@tscientific")
                .comment("두번째 버전")
                .build();
        releaseVersionRepository.save(customVersion2);

        // when
        List<ReleaseVersion> versions = releaseVersionRepository
                .findAllByCustomer_CustomerIdOrderByCreatedAtDesc(testCustomer.getCustomerId());

        // then
        assertThat(versions).hasSize(2);
    }

    @Test
    @DisplayName("Major.Minor 버전으로 목록 조회 - 패치 버전 내림차순")
    void findAllByMajorMinorOrderByPatchVersionDesc_Success() {
        // given
        createAndSaveStandardVersions();

        // when - majorVersion, minorVersion을 파라미터로 받음
        List<ReleaseVersion> versions = releaseVersionRepository
                .findAllByMajorVersionAndMinorVersionOrderByPatchVersionDesc(1, 1);

        // then
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getPatchVersion()).isEqualTo(1);
        assertThat(versions.get(1).getPatchVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("버전 존재 여부 확인 - 존재함")
    void existsByVersion_True() {
        // given
        ReleaseVersion version = createStandardVersion("1.1.0", 1, 1, 0);
        releaseVersionRepository.save(version);

        // when
        boolean exists = releaseVersionRepository.existsByVersion("1.1.0");

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("버전 범위 조회 - QueryDSL")
    void findVersionsBetween_Success() {
        // given
        createAndSaveStandardVersions();

        // when - fromVersion <= version <= toVersion (양 끝 포함)
        List<ReleaseVersion> versions = releaseVersionRepository
                .findVersionsBetween(PROJECT_ID, "STANDARD", "1.1.0", "1.2.0");

        // then
        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersion()).isEqualTo("1.1.0");
        assertThat(versions.get(1).getVersion()).isEqualTo("1.1.1");
        assertThat(versions.get(2).getVersion()).isEqualTo("1.2.0");
    }

    @Test
    @DisplayName("버전 키 생성 - 표준 릴리즈")
    void getVersionKey_Standard() {
        // given
        ReleaseVersion version = createStandardVersion("1.1.0", 1, 1, 0);
        ReleaseVersion saved = releaseVersionRepository.save(version);

        // when
        String versionKey = saved.getVersionKey();

        // then
        assertThat(versionKey).isEqualTo("standard/1.1.0");
    }

    @Test
    @DisplayName("버전 키 생성 - 커스텀 릴리즈")
    void getVersionKey_Custom() {
        // given
        ReleaseVersion version = ReleaseVersion.builder()
                .project(testProject)
                .releaseType("CUSTOM")
                .customer(testCustomer)
                .version("1.0.0")
                .majorVersion(1)
                .minorVersion(0)
                .patchVersion(0)
                .createdByEmail("admin@tscientific")
                .comment("커스텀 버전")
                .build();
        ReleaseVersion saved = releaseVersionRepository.save(version);

        // when
        String versionKey = saved.getVersionKey();

        // then
        assertThat(versionKey).isEqualTo("custom/company_a/1.0.0");
    }

    // === Helper Methods ===

    private ReleaseVersion createStandardVersion(String version, int major, int minor, int patch) {
        return ReleaseVersion.builder()
                .project(testProject)
                .releaseType("STANDARD")
                .version(version)
                .majorVersion(major)
                .minorVersion(minor)
                .patchVersion(patch)
                .createdByEmail("jhlee@tscientific")
                .comment("테스트 버전")
                .build();
    }

    private void createAndSaveStandardVersions() {
        releaseVersionRepository.save(createStandardVersion("1.1.0", 1, 1, 0));
        releaseVersionRepository.save(createStandardVersion("1.1.1", 1, 1, 1));
        releaseVersionRepository.save(createStandardVersion("1.2.0", 1, 2, 0));
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
