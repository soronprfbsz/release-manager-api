package com.ts.rm.domain.site.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.domain.site.entity.Site;
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
 * Site Repository 단위 테스트
 */
@DataJpaTest
@Import(SiteRepositoryTest.TestConfig.class)
@ActiveProfiles("test")
@DisplayName("SiteRepository 테스트")
class SiteRepositoryTest {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Site testSite;

    @BeforeEach
    void setUp() {
        testSite = Site.builder()
                .siteCode("company_a")
                .siteName("A회사")
                .description("A회사 설명")
                .isActive(true)
                .createdByEmail("admin@tscientific")
                .updatedByEmail("admin@tscientific")
                .build();
    }

    @Test
    @DisplayName("사이트 저장 - 성공")
    void save_Success() {
        // when
        Site saved = siteRepository.save(testSite);

        // then
        assertThat(saved.getSiteId()).isNotNull();
        assertThat(saved.getSiteCode()).isEqualTo("company_a");
        assertThat(saved.getSiteName()).isEqualTo("A회사");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("사이트 코드로 조회 - 성공")
    void findBySiteCode_Success() {
        // given
        siteRepository.save(testSite);

        // when
        Optional<Site> found = siteRepository.findBySiteCode("company_a");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getSiteCode()).isEqualTo("company_a");
    }

    @Test
    @DisplayName("사이트 코드로 조회 - 없음")
    void findBySiteCode_NotFound() {
        // when
        Optional<Site> found = siteRepository.findBySiteCode("nonexistent");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("활성화된 사이트 목록 조회 - 성공")
    void findAllByIsActive_Success() {
        // given
        siteRepository.save(testSite);

        Site inactiveSite = Site.builder()
                .siteCode("company_b")
                .siteName("B회사")
                .description("B회사 설명")
                .isActive(false)
                .createdByEmail("admin@tscientific")
                .updatedByEmail("admin@tscientific")
                .build();
        siteRepository.save(inactiveSite);

        // when
        List<Site> activeSites = siteRepository.findAllByIsActive(true);

        // then
        assertThat(activeSites).hasSize(1);
        assertThat(activeSites.get(0).getSiteCode()).isEqualTo("company_a");
    }

    @Test
    @DisplayName("사이트 코드 존재 여부 확인 - 존재함")
    void existsBySiteCode_True() {
        // given
        siteRepository.save(testSite);

        // when
        boolean exists = siteRepository.existsBySiteCode("company_a");

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("사이트 코드 존재 여부 확인 - 존재하지 않음")
    void existsBySiteCode_False() {
        // when
        boolean exists = siteRepository.existsBySiteCode("nonexistent");

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("사이트명으로 검색 - 성공")
    void findBySiteNameContaining_Success() {
        // given
        siteRepository.save(testSite);

        Site anotherSite = Site.builder()
                .siteCode("company_b")
                .siteName("홍길동회사")
                .description("설명")
                .isActive(true)
                .createdByEmail("admin@tscientific")
                .updatedByEmail("admin@tscientific")
                .build();
        siteRepository.save(anotherSite);

        // when
        List<Site> results = siteRepository.findBySiteNameContaining("A회사");

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSiteName()).contains("A회사");
    }

    /**
     * JPA Auditing 및 QueryDSL 설정
     *
     * <p>Site 도메인은 QueryDSL Custom을 사용하지 않지만,
     * <p>다른 Repository들(ReleaseVersion, ReleaseFile 등)이 아직 Custom Impl을 사용하므로
     * <p>JPAQueryFactory 빈이 필요함
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
