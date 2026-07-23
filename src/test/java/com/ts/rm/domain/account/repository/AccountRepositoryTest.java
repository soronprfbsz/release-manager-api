package com.ts.rm.domain.account.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.domain.account.entity.Account;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Account Repository 단위 테스트
 *
 * <p>[@DataJpaTest] - JPA 관련 컴포넌트만 로드 (빠른 테스트) - H2 인메모리 DB 사용 - 트랜잭션 자동 롤백
 * <p>[@Import(TestConfig.class)] - JPA Auditing 활성화
 */
@DataJpaTest
@Import(AccountRepositoryTest.TestConfig.class)
@ActiveProfiles("test")
@DisplayName("AccountRepository 테스트")
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .email("test@example.com")
                .password("password123")
                .accountName("테스트계정")
                .role("USER")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("계정 저장 - 성공")
    void save_Success() {
        // when
        Account saved = accountRepository.save(testAccount);

        // then
        assertThat(saved.getAccountId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이메일로 계정 조회 - 성공")
    void findByEmail_Success() {
        // given
        accountRepository.save(testAccount);

        // when
        Optional<Account> found = accountRepository.findByEmail("test@example.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("이메일로 계정 조회 - 없음")
    void findByEmail_NotFound() {
        // when
        Optional<Account> found = accountRepository.findByEmail("nonexistent@example.com");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("ID로 계정 조회 - 성공")
    void findByAccountId_Success() {
        // given
        Account saved = accountRepository.save(testAccount);

        // when
        Optional<Account> found = accountRepository.findByAccountId(saved.getAccountId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getAccountId()).isEqualTo(saved.getAccountId());
    }

    @Test
    @DisplayName("상태별 계정 조회 - 성공")
    void findAllByStatus_Success() {
        // given
        accountRepository.save(testAccount);

        Account inactiveAccount = Account.builder()
                .email("inactive@example.com")
                .password("password123")
                .accountName("비활성계정")
                .role("USER")
                .status("INACTIVE")
                .build();
        accountRepository.save(inactiveAccount);

        // when
        Page<Account> activeAccountsPage = accountRepository.findAllByStatus("ACTIVE", PageRequest.of(0, 10));

        // then
        assertThat(activeAccountsPage.getContent()).hasSize(1);
        assertThat(activeAccountsPage.getContent().get(0).getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("이메일 존재 여부 확인 - 존재함")
    void existsByEmail_True() {
        // given
        accountRepository.save(testAccount);

        // when
        boolean exists = accountRepository.existsByEmail("test@example.com");

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("이메일 존재 여부 확인 - 존재하지 않음")
    void existsByEmail_False() {
        // when
        boolean exists = accountRepository.existsByEmail("nonexistent@example.com");

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("계정 이름으로 검색 - 성공")
    void findByAccountNameContaining_Success() {
        // given
        accountRepository.save(testAccount);

        Account anotherAccount = Account.builder()
                .email("another@example.com")
                .password("password123")
                .accountName("홍길동")
                .role("USER")
                .status("ACTIVE")
                .build();
        accountRepository.save(anotherAccount);

        // when
        Page<Account> resultsPage = accountRepository.findByAccountNameContaining("테스트", PageRequest.of(0, 10));

        // then
        assertThat(resultsPage.getContent()).hasSize(1);
        assertThat(resultsPage.getContent().get(0).getAccountName()).contains("테스트");
    }

    /**
     * JPA Auditing 및 QueryDSL 설정
     *
     * <p>Account 도메인은 QueryDSL Custom을 사용하지 않지만,
     * <p>다른 Repository들(Site, ReleaseVersion 등)이 아직 Custom Impl을 사용하므로
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
