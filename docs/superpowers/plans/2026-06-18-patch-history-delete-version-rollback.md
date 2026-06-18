# 패치 이력 삭제 시 고객사 버전 되돌리기 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 패치 이력(`patch_history`)을 삭제하면 해당 고객사+프로젝트의 버전 정보(BASE/WEB/ENGINE/`last_patched_version`)가 남은 이력만 반영한 상태로 즉시 재계산되어, 삭제한 패치는 적용 안 한 것처럼 보이게 한다.

**Architecture:** (1) 패치 완료 시점에 WEB/ENGINE 빌드 스냅샷을 신규 `patch_history_build` 테이블에 영구 저장한다(`patch_included_build`는 완료 시 CASCADE 삭제되므로). (2) 이력 삭제 시 해당 고객사+프로젝트의 `customer_site_version`을 전부 비우고 남은 이력을 완료순으로 **재생(replay)**하여 버전을 재구성한다. 백엔드 단독 변경 — 프론트는 조회 결과를 그대로 표시하므로 자동 반영된다.

**Tech Stack:** Spring Boot 3.5.6 / Java 17 / JPA / MariaDB / Flyway. 테스트는 H2 in-memory + JPA `ddl-auto: create-drop`(Flyway 비활성), 스타일은 Mockito 단위 테스트(`@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`).

## Global Constraints

- 모든 명령은 WSL2 셸에서 `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` 후 실행한다.
- DDL은 Flyway 마이그레이션으로만. 다음 버전 번호는 **V17** (현재 최신 V16).
- Flyway 신규 테이블은 charset/collation 명시: `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` (MariaDB 10.10+ FK errno 150 회피, 기존 `customer_site_version`과 동일).
- 커밋 메시지는 한글 conventional. **Co-Authored-By(Claude) 표기 금지.** main 직접 커밋.
- 표준 패치(고객사 미지정, `customer == null`) 이력 삭제는 재계산 대상이 아니다.
- 재계산은 "완전 재생": 남은 이력을 완료순(`completed_at` ASC, 동률 시 `created_at` ASC)으로 재생. 빌드 스냅샷 없는 옛 이력은 WEB/ENGINE을 기여하지 못하므로 그 컴포넌트는 자연 소멸할 수 있다(BASE/`last_patched_version`은 `to_version`으로 항상 복원).

---

## File Structure

- `src/main/resources/db/migration/V17__add_patch_history_build.sql` — 신규 스냅샷 테이블 DDL (Task 1)
- `src/main/java/com/ts/rm/domain/patch/entity/PatchHistoryBuild.java` — 신규 엔티티 (Task 1)
- `src/main/java/com/ts/rm/domain/patch/repository/PatchHistoryBuildRepository.java` — 신규 리포지토리 (Task 1, Task 3에서 메서드 추가)
- `src/main/java/com/ts/rm/domain/patch/service/PatchHistoryService.java` — `saveFromPatch` 스냅샷 저장(Task 1), `deleteHistory` 재계산(Task 3)
- `src/main/java/com/ts/rm/domain/customer/service/CustomerSiteVersionService.java` — `BuildSnapshot` record + `applyComponentVersions` + `extractBaseVersion`(public) + `clearByCustomerAndProject` (Task 2)
- `src/main/java/com/ts/rm/domain/customer/repository/CustomerSiteVersionRepository.java` — `deleteAllBy...Project_ProjectId` (Task 2)
- `src/main/java/com/ts/rm/domain/patch/service/PatchService.java` — `applyCustomerSiteVersions` 위임 리팩터 + orphan 정리 (Task 2)
- `src/main/java/com/ts/rm/domain/patch/repository/PatchHistoryRepository.java` — 완료순 조회 메서드 (Task 3)
- `src/test/java/com/ts/rm/domain/patch/service/PatchHistoryServiceTest.java` — 신규 테스트 (Task 1, Task 3)
- `src/test/java/com/ts/rm/domain/customer/service/CustomerSiteVersionServiceTest.java` — 신규/확장 테스트 (Task 2)

---

## Task 1: 빌드 스냅샷 영구화 (`patch_history_build` + `saveFromPatch`)

**Files:**
- Create: `src/main/resources/db/migration/V17__add_patch_history_build.sql`
- Create: `src/main/java/com/ts/rm/domain/patch/entity/PatchHistoryBuild.java`
- Create: `src/main/java/com/ts/rm/domain/patch/repository/PatchHistoryBuildRepository.java`
- Modify: `src/main/java/com/ts/rm/domain/patch/service/PatchHistoryService.java`
- Test: `src/test/java/com/ts/rm/domain/patch/service/PatchHistoryServiceTest.java`

**Interfaces:**
- Produces:
  - `PatchHistoryBuild.of(PatchHistory history, String kind, String engineName, String fullVersion, LocalDateTime createdAt) -> PatchHistoryBuild`
  - `PatchHistoryBuildRepository.findAllByHistory_HistoryIdOrderByPatchHistoryBuildIdAsc(Long historyId) -> List<PatchHistoryBuild>`
  - `PatchHistoryService.saveFromPatch(Patch, String, LocalDateTime)` (시그니처 불변; 빌드 스냅샷 저장 부수효과 추가)
- Consumes:
  - 기존 `PatchIncludedBuildRepository.findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(Long) -> List<PatchIncludedBuild>`
  - 기존 `PatchIncludedBuild` getter: `getKind()`, `getEngineName()`, `getFullVersion()`
  - 기존 `Patch.getIsBuildIncluded()`, `Patch.getPatchId()`

- [ ] **Step 1: Flyway 마이그레이션 작성**

`src/main/resources/db/migration/V17__add_patch_history_build.sql`:

```sql
-- ============================================================
-- V17: patch_history_build — 패치 이력별 WEB/ENGINE 빌드 스냅샷
--
-- 목적: patch_history 삭제 시 남은 이력만으로 WEB/ENGINE 버전을 재계산하려면
--       각 이력의 빌드 fullVersion 스냅샷이 필요하다. patch_included_build 는
--       patch_file 삭제 시 ON DELETE CASCADE 로 사라지므로, 패치 완료 시점에
--       이력 쪽으로 복사 보존한다. (배포 이후 완료되는 패치부터 누적)
-- ============================================================

CREATE TABLE IF NOT EXISTS patch_history_build (
    patch_history_build_id BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK',
    history_id             BIGINT      NOT NULL                COMMENT '소속 패치 이력 (FK → patch_history)',
    kind                   VARCHAR(10) NOT NULL                COMMENT 'WEB | ENGINE',
    engine_name            VARCHAR(50) NULL                    COMMENT 'kind=ENGINE 일 때만 채움 (BASE/WEB 은 NULL)',
    full_version           VARCHAR(50) NOT NULL                COMMENT '빌드 fullVersion snapshot (예: 1.1.0.260511-1)',
    created_at             DATETIME    NOT NULL                COMMENT '스냅샷 생성 일시 (= 패치 완료 일시)',
    PRIMARY KEY (patch_history_build_id),
    INDEX idx_phb_history_id (history_id),
    CONSTRAINT fk_phb_history FOREIGN KEY (history_id)
        REFERENCES patch_history (history_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 2: 엔티티 작성**

`src/main/java/com/ts/rm/domain/patch/entity/PatchHistoryBuild.java`:

```java
package com.ts.rm.domain.patch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PatchHistoryBuild Entity
 *
 * <p>패치 이력별 WEB/ENGINE 빌드 스냅샷. patch_included_build 의 미러로,
 * 패치 완료 시점에 복사 보존되어 이력 삭제 후 버전 재계산의 근거가 된다.
 */
@Entity
@Table(name = "patch_history_build")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchHistoryBuild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patch_history_build_id")
    private Long patchHistoryBuildId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "history_id", nullable = false)
    private PatchHistory history;

    /** 'WEB' | 'ENGINE' */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    /** kind='ENGINE' 일 때만 채움. WEB 은 NULL. */
    @Column(name = "engine_name", length = 50)
    private String engineName;

    @Column(name = "full_version", nullable = false, length = 50)
    private String fullVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 빌드 스냅샷 생성 팩토리 (패치 완료 시점 호출).
     */
    public static PatchHistoryBuild of(PatchHistory history, String kind, String engineName,
            String fullVersion, LocalDateTime createdAt) {
        return PatchHistoryBuild.builder()
                .history(history)
                .kind(kind)
                .engineName(engineName)
                .fullVersion(fullVersion)
                .createdAt(createdAt)
                .build();
    }
}
```

- [ ] **Step 3: 리포지토리 작성**

`src/main/java/com/ts/rm/domain/patch/repository/PatchHistoryBuildRepository.java`:

```java
package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.PatchHistoryBuild;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PatchHistoryBuild Repository
 *
 * <p>패치 이력별 빌드 스냅샷 데이터 접근
 */
@Repository
public interface PatchHistoryBuildRepository extends JpaRepository<PatchHistoryBuild, Long> {

    /**
     * 이력별 빌드 스냅샷 목록 조회 (적재 순서).
     */
    List<PatchHistoryBuild> findAllByHistory_HistoryIdOrderByPatchHistoryBuildIdAsc(Long historyId);
}
```

- [ ] **Step 4: 실패하는 테스트 작성**

`src/test/java/com/ts/rm/domain/patch/service/PatchHistoryServiceTest.java` (신규 파일):

```java
package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.entity.PatchHistory;
import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import com.ts.rm.domain.patch.repository.PatchHistoryBuildRepository;
import com.ts.rm.domain.patch.repository.PatchHistoryRepository;
import com.ts.rm.domain.patch.repository.PatchIncludedBuildRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatchHistoryService 테스트")
class PatchHistoryServiceTest {

    @Mock
    private PatchHistoryRepository patchHistoryRepository;
    @Mock
    private PatchIncludedBuildRepository patchIncludedBuildRepository;
    @Mock
    private PatchHistoryBuildRepository patchHistoryBuildRepository;

    @InjectMocks
    private PatchHistoryService patchHistoryService;

    @Test
    @DisplayName("saveFromPatch — 빌드 포함 패치면 patch_included_build 를 patch_history_build 로 복사 저장")
    void saveFromPatch_savesBuildSnapshots() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Patch patch = org.mockito.Mockito.mock(Patch.class);
        when(patch.getIsBuildIncluded()).thenReturn(true);
        when(patch.getPatchId()).thenReturn(10L);

        PatchHistory saved = PatchHistory.builder().historyId(100L).build();
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenReturn(saved);

        PatchIncludedBuild web = PatchIncludedBuild.builder()
                .kind("WEB").engineName(null).fullVersion("1.1.0.260511-1").build();
        PatchIncludedBuild engine = PatchIncludedBuild.builder()
                .kind("ENGINE").engineName("NC_SMS").fullVersion("1.1.0.260511-2").build();
        when(patchIncludedBuildRepository
                .findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(10L))
                .thenReturn(List.of(web, engine));

        // when
        patchHistoryService.saveFromPatch(patch, "ops@ts.com", now);

        // then — 2건 스냅샷 저장
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.ts.rm.domain.patch.entity.PatchHistoryBuild>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(patchHistoryBuildRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue())
                .extracting(com.ts.rm.domain.patch.entity.PatchHistoryBuild::getKind)
                .containsExactly("WEB", "ENGINE");
    }

    @Test
    @DisplayName("saveFromPatch — 빌드 미포함 패치면 스냅샷 저장 안 함")
    void saveFromPatch_noBuild_skipsSnapshots() {
        // given
        Patch patch = org.mockito.Mockito.mock(Patch.class);
        when(patch.getIsBuildIncluded()).thenReturn(false);
        PatchHistory saved = PatchHistory.builder().historyId(101L).build();
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenReturn(saved);

        // when
        patchHistoryService.saveFromPatch(patch, "ops@ts.com", LocalDateTime.now());

        // then
        verify(patchHistoryBuildRepository, never()).saveAll(any());
    }
}
```

- [ ] **Step 5: 테스트 실패 확인**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchHistoryServiceTest"
```
Expected: 컴파일 실패 또는 FAIL — `PatchHistoryService` 가 아직 `patchIncludedBuildRepository`/`patchHistoryBuildRepository` 를 주입받지 않고 스냅샷을 저장하지 않음.

- [ ] **Step 6: `PatchHistoryService.saveFromPatch` 구현**

`PatchHistoryService.java` — 필드 주입 추가(파일 상단 의존성). `@RequiredArgsConstructor` 이므로 `final` 필드만 추가하면 된다:

```java
    private final PatchHistoryRepository patchHistoryRepository;
    private final PatchIncludedBuildRepository patchIncludedBuildRepository;
    private final PatchHistoryBuildRepository patchHistoryBuildRepository;
```

import 추가:
```java
import com.ts.rm.domain.patch.entity.PatchHistoryBuild;
import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import com.ts.rm.domain.patch.repository.PatchHistoryBuildRepository;
import com.ts.rm.domain.patch.repository.PatchIncludedBuildRepository;
import java.util.List;
```

`saveFromPatch` 교체:

```java
    @Transactional
    public PatchHistory saveFromPatch(Patch patch, String completedBy, LocalDateTime completedAt) {
        PatchHistory history = PatchHistory.fromPatch(patch, completedBy, completedAt);
        PatchHistory savedHistory = patchHistoryRepository.save(history);

        // 빌드 포함 패치면 WEB/ENGINE 빌드 스냅샷을 이력 쪽에 복사 보존
        // (patch_included_build 는 패치 완료 시 CASCADE 삭제되므로 재계산 근거가 사라짐)
        if (Boolean.TRUE.equals(patch.getIsBuildIncluded())) {
            List<PatchHistoryBuild> snapshots = patchIncludedBuildRepository
                    .findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(patch.getPatchId())
                    .stream()
                    .map(b -> PatchHistoryBuild.of(savedHistory, b.getKind(), b.getEngineName(),
                            b.getFullVersion(), completedAt))
                    .toList();
            if (!snapshots.isEmpty()) {
                patchHistoryBuildRepository.saveAll(snapshots);
            }
        }

        log.info("패치 이력 저장 완료 - historyId: {}, patchName: {}, completedBy: {}, 빌드스냅샷: {}건",
                savedHistory.getHistoryId(), savedHistory.getPatchName(), completedBy,
                Boolean.TRUE.equals(patch.getIsBuildIncluded()) ? "포함" : 0);
        return savedHistory;
    }
```

- [ ] **Step 7: 테스트 통과 확인**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchHistoryServiceTest"
```
Expected: PASS (2 tests)

- [ ] **Step 8: 커밋**

```bash
git add src/main/resources/db/migration/V17__add_patch_history_build.sql \
        src/main/java/com/ts/rm/domain/patch/entity/PatchHistoryBuild.java \
        src/main/java/com/ts/rm/domain/patch/repository/PatchHistoryBuildRepository.java \
        src/main/java/com/ts/rm/domain/patch/service/PatchHistoryService.java \
        src/test/java/com/ts/rm/domain/patch/service/PatchHistoryServiceTest.java
git commit -m "feat: 패치 완료 시 WEB/ENGINE 빌드 스냅샷을 patch_history_build 에 영구 저장"
```

---

## Task 2: 컴포넌트 버전 적용 공통 헬퍼 추출 (`CustomerSiteVersionService`)

**Files:**
- Modify: `src/main/java/com/ts/rm/domain/customer/service/CustomerSiteVersionService.java`
- Modify: `src/main/java/com/ts/rm/domain/customer/repository/CustomerSiteVersionRepository.java`
- Modify: `src/main/java/com/ts/rm/domain/patch/service/PatchService.java:350-396`
- Test: `src/test/java/com/ts/rm/domain/customer/service/CustomerSiteVersionServiceTest.java`

**Interfaces:**
- Produces:
  - `CustomerSiteVersionService.BuildSnapshot(String kind, String engineName, String fullVersion)` — public record
  - `CustomerSiteVersionService.extractBaseVersion(String version) -> String` (public; major.minor.patch, 미일치 시 원본 반환, null→null)
  - `CustomerSiteVersionService.applyComponentVersions(Long customerId, String projectId, String baseVersion, List<BuildSnapshot> builds, String updatedBy, LocalDateTime updatedAt) -> void`
  - `CustomerSiteVersionService.clearByCustomerAndProject(Long customerId, String projectId) -> void`
  - `CustomerSiteVersionRepository.deleteAllByCustomer_CustomerIdAndProject_ProjectId(Long, String) -> void`
- Consumes:
  - 기존 `CustomerSiteVersionService.upsert(Long, String, String, String, String, String, LocalDateTime)`
  - 기존 `PatchIncludedBuildRepository.findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(Long)`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/ts/rm/domain/customer/service/CustomerSiteVersionServiceTest.java` (신규 파일):

```java
package com.ts.rm.domain.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.customer.entity.CustomerSiteVersion;
import com.ts.rm.domain.customer.repository.CustomerProjectRepository;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.customer.repository.CustomerSiteVersionRepository;
import com.ts.rm.domain.customer.service.CustomerSiteVersionService.BuildSnapshot;
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
@DisplayName("CustomerSiteVersionService 테스트")
class CustomerSiteVersionServiceTest {

    @Mock
    private CustomerSiteVersionRepository siteVersionRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private CustomerProjectRepository customerProjectRepository;
    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @InjectMocks
    private CustomerSiteVersionService service;

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
        // given — 모든 단건 조회를 기존 row 로 만들어 update 경로(customer/project 조회 불필요)
        CustomerSiteVersion existing = CustomerSiteVersion.builder().build();
        when(siteVersionRepository
                .findByCustomer_CustomerIdAndProject_ProjectIdAndComponentAndEngineNameIsNull(
                        any(), anyString(), anyString()))
                .thenReturn(Optional.of(existing));
        when(siteVersionRepository
                .findByCustomer_CustomerIdAndProject_ProjectIdAndComponentAndEngineName(
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
        verify(siteVersionRepository, times(4)).save(any(CustomerSiteVersion.class));
    }

    @Test
    @DisplayName("applyComponentVersions — builds 비면 BASE 만 upsert")
    void applyComponentVersions_emptyBuilds_baseOnly() {
        CustomerSiteVersion existing = CustomerSiteVersion.builder().build();
        when(siteVersionRepository
                .findByCustomer_CustomerIdAndProject_ProjectIdAndComponentAndEngineNameIsNull(
                        any(), anyString(), eq("BASE")))
                .thenReturn(Optional.of(existing));

        service.applyComponentVersions(1L, "PRJ", "1.1.0", List.of(), "ops@ts.com",
                LocalDateTime.now());

        verify(siteVersionRepository, times(1)).save(any(CustomerSiteVersion.class));
    }

    @Test
    @DisplayName("clearByCustomerAndProject — 리포지토리 삭제 위임")
    void clearByCustomerAndProject_delegates() {
        service.clearByCustomerAndProject(1L, "PRJ");
        verify(siteVersionRepository).deleteAllByCustomer_CustomerIdAndProject_ProjectId(1L, "PRJ");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew test --tests "com.ts.rm.domain.customer.service.CustomerSiteVersionServiceTest"
```
Expected: 컴파일 실패 — `BuildSnapshot`/`extractBaseVersion`/`applyComponentVersions`/`clearByCustomerAndProject` 및 repo 메서드 미존재.

- [ ] **Step 3: 리포지토리 삭제 메서드 추가**

`CustomerSiteVersionRepository.java` 에 추가:

```java
    /**
     * 고객사 + 프로젝트의 모든 사이트 버전 삭제 (이력 삭제 후 재계산용).
     *
     * @param customerId 고객사 ID
     * @param projectId  프로젝트 ID
     */
    void deleteAllByCustomer_CustomerIdAndProject_ProjectId(Long customerId, String projectId);
```

- [ ] **Step 4: `CustomerSiteVersionService` 헬퍼 추가**

`CustomerSiteVersionService.java` — 클래스 본문에 record + 메서드 추가. import `java.util.List` 는 이미 존재.

record (클래스 본문 상단, 필드 선언 근처):

```java
    /**
     * 컴포넌트 버전 적용용 빌드 스냅샷 표현.
     * patch_included_build / patch_history_build 양쪽의 공통 입력 형태.
     */
    public record BuildSnapshot(String kind, String engineName, String fullVersion) {}
```

public `extractBaseVersion` (BASE 쓰기용 — 미일치 시 원본 반환):

```java
    /**
     * 버전 문자열에서 BASE(major.minor.patch) 추출. 미일치 시 원본, null→null.
     * <p>사이트 BASE 버전 기록용. (다음 패치 범위 추천용 {@code extractBase} 는 미일치 시 null 로 의미가 다르다.)
     */
    public String extractBaseVersion(String version) {
        if (version == null) {
            return null;
        }
        Matcher m = BASE_VERSION_PATTERN.matcher(version);
        return m.find() ? m.group(1) : version;
    }
```

`applyComponentVersions` + `clearByCustomerAndProject`:

```java
    /**
     * BASE/WEB/ENGINE 컴포넌트 버전을 일괄 upsert.
     *
     * <p>패치 완료(완료 시 patch_included_build) 와 이력 삭제 재계산(patch_history_build)
     * 양쪽에서 재사용되는 공통 로직. BASE 는 항상 갱신, WEB 은 첫 WEB 빌드, ENGINE 은 엔진명별.
     * builds 가 비면 BASE 만 갱신하고 WEB/ENGINE 은 손대지 않는다(이전 값 유지).
     */
    @Transactional
    public void applyComponentVersions(Long customerId, String projectId, String baseVersion,
            List<BuildSnapshot> builds, String updatedBy, LocalDateTime updatedAt) {
        // BASE — 항상 갱신
        upsert(customerId, projectId, "BASE", null, baseVersion, updatedBy, updatedAt);

        if (builds == null || builds.isEmpty()) {
            return;
        }
        // WEB — 첫 WEB 빌드
        builds.stream()
                .filter(b -> "WEB".equals(b.kind()))
                .map(BuildSnapshot::fullVersion)
                .findFirst()
                .ifPresent(v -> upsert(customerId, projectId, "WEB", null, v, updatedBy, updatedAt));
        // ENGINE — 엔진명별
        builds.stream()
                .filter(b -> "ENGINE".equals(b.kind()))
                .filter(b -> b.engineName() != null)
                .forEach(b -> upsert(customerId, projectId, "ENGINE", b.engineName(),
                        b.fullVersion(), updatedBy, updatedAt));
    }

    /**
     * 고객사 + 프로젝트의 사이트 버전 전부 삭제 (재계산 전 초기화용).
     */
    @Transactional
    public void clearByCustomerAndProject(Long customerId, String projectId) {
        siteVersionRepository.deleteAllByCustomer_CustomerIdAndProject_ProjectId(customerId, projectId);
    }
```

- [ ] **Step 5: 새 헬퍼 테스트 통과 확인**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew test --tests "com.ts.rm.domain.customer.service.CustomerSiteVersionServiceTest"
```
Expected: PASS (4 tests)

- [ ] **Step 6: `PatchService.applyCustomerSiteVersions` 위임 리팩터**

`PatchService.java:350-380` 의 `applyCustomerSiteVersions` 를 교체:

```java
    private void applyCustomerSiteVersions(Patch patch, String updatedBy, LocalDateTime now) {
        Long customerId = patch.getCustomer().getCustomerId();
        String projectId = patch.getProject().getProjectId();

        String baseVersion = customerSiteVersionService.extractBaseVersion(patch.getToVersion());

        List<CustomerSiteVersionService.BuildSnapshot> builds = List.of();
        if (Boolean.TRUE.equals(patch.getIsBuildIncluded())) {
            builds = patchIncludedBuildRepository
                    .findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(patch.getPatchId())
                    .stream()
                    .map(b -> new CustomerSiteVersionService.BuildSnapshot(
                            b.getKind(), b.getEngineName(), b.getFullVersion()))
                    .toList();
        }
        customerSiteVersionService.applyComponentVersions(
                customerId, projectId, baseVersion, builds, updatedBy, now);
    }
```

import 추가(없으면): `import com.ts.rm.domain.customer.service.CustomerSiteVersionService;` (이미 필드로 주입 중이므로 대개 존재).

- [ ] **Step 7: PatchService orphan 정리**

리팩터로 `PatchService.extractBaseVersion`(라인 ~390) 과 `BASE_VERSION_PATTERN`(라인 ~70) 이 더 이상 쓰이지 않는지 확인 후 제거. 미사용 import(`java.util.regex.Matcher`, `java.util.regex.Pattern`)도 함께 제거:

```bash
grep -n "extractBaseVersion\|BASE_VERSION_PATTERN\|Matcher\|Pattern" src/main/java/com/ts/rm/domain/patch/service/PatchService.java
```
위 명령 결과에서 `applyCustomerSiteVersions` 외 다른 사용처가 없으면, `extractBaseVersion` 메서드 / `BASE_VERSION_PATTERN` 상수 / `Matcher`·`Pattern` import 를 삭제한다. (다른 사용처가 있으면 남겨둔다.)

- [ ] **Step 8: 회귀 + 전체 빌드 확인**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew compileJava
./gradlew test --tests "com.ts.rm.domain.patch.service.*" --tests "com.ts.rm.domain.customer.service.*"
```
Expected: BUILD SUCCESSFUL, 모든 패치/고객 서비스 테스트 PASS (기존 PatchServiceTest 회귀 포함).

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/ts/rm/domain/customer/service/CustomerSiteVersionService.java \
        src/main/java/com/ts/rm/domain/customer/repository/CustomerSiteVersionRepository.java \
        src/main/java/com/ts/rm/domain/patch/service/PatchService.java \
        src/test/java/com/ts/rm/domain/customer/service/CustomerSiteVersionServiceTest.java
git commit -m "refactor: 사이트 컴포넌트 버전 적용 로직을 CustomerSiteVersionService 공통 헬퍼로 추출"
```

---

## Task 3: 이력 삭제 시 버전 재계산 (`deleteHistory` 재생)

**Files:**
- Modify: `src/main/java/com/ts/rm/domain/patch/service/PatchHistoryService.java`
- Modify: `src/main/java/com/ts/rm/domain/patch/repository/PatchHistoryRepository.java`
- Modify: `src/main/java/com/ts/rm/domain/patch/repository/PatchHistoryBuildRepository.java`
- Test: `src/test/java/com/ts/rm/domain/patch/service/PatchHistoryServiceTest.java`

**Interfaces:**
- Produces:
  - `PatchHistoryRepository.findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByCompletedAtAscCreatedAtAsc(Long, String) -> List<PatchHistory>`
  - `PatchHistoryBuildRepository.deleteAllByHistory_HistoryId(Long) -> void`
  - `PatchHistoryService.deleteHistory(Long)` (시그니처 불변; 재계산 부수효과 추가)
- Consumes (Task 2 산출물):
  - `CustomerSiteVersionService.clearByCustomerAndProject(Long, String)`
  - `CustomerSiteVersionService.extractBaseVersion(String)`
  - `CustomerSiteVersionService.applyComponentVersions(Long, String, String, List<BuildSnapshot>, String, LocalDateTime)`
  - `CustomerSiteVersionService.BuildSnapshot`
  - 기존 `CustomerProjectRepository.findByCustomer_CustomerIdAndProject_ProjectId(Long, String) -> Optional<CustomerProject>`
  - 기존 `CustomerProject.create(Customer, Project)`, `CustomerProject.updateLastPatchInfo(String, LocalDateTime)`
  - 기존 `PatchHistoryBuildRepository.findAllByHistory_HistoryIdOrderByPatchHistoryBuildIdAsc(Long)` (Task 1)

- [ ] **Step 1: 리포지토리 메서드 추가**

`PatchHistoryRepository.java` 에 추가:

```java
    /**
     * 고객사 + 프로젝트의 모든 패치 이력을 완료순으로 조회 (재계산 재생용).
     *
     * @param customerId 고객사 ID
     * @param projectId  프로젝트 ID
     * @return 완료 일시 오름차순(동률 시 생성 일시 오름차순) 이력 목록
     */
    java.util.List<PatchHistory> findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByCompletedAtAscCreatedAtAsc(
            Long customerId, String projectId);
```

`PatchHistoryBuildRepository.java` 에 추가:

```java
    /**
     * 이력 삭제 시 해당 이력의 빌드 스냅샷 제거 (FK CASCADE 와 무관하게 ORM 레벨에서 명시 삭제).
     */
    void deleteAllByHistory_HistoryId(Long historyId);
```

- [ ] **Step 2: 실패하는 테스트 작성**

`PatchHistoryServiceTest.java` 에 의존성 Mock 과 테스트 추가. 클래스 상단 `@Mock` 필드 추가:

```java
    @Mock
    private com.ts.rm.domain.customer.service.CustomerSiteVersionService customerSiteVersionService;
    @Mock
    private com.ts.rm.domain.customer.repository.CustomerProjectRepository customerProjectRepository;
```

테스트 메서드 추가:

```java
    @Test
    @DisplayName("deleteHistory — 고객사 이력 삭제 시 남은 이력을 재생하여 버전 재계산")
    void deleteHistory_recomputesForCustomerPatch() {
        // given — 삭제 대상 이력 (고객사 C=1, 프로젝트 P)
        com.ts.rm.domain.customer.entity.Customer customer =
                org.mockito.Mockito.mock(com.ts.rm.domain.customer.entity.Customer.class);
        when(customer.getCustomerId()).thenReturn(1L);
        com.ts.rm.domain.project.entity.Project project =
                org.mockito.Mockito.mock(com.ts.rm.domain.project.entity.Project.class);
        when(project.getProjectId()).thenReturn("PRJ");

        PatchHistory target = org.mockito.Mockito.mock(PatchHistory.class);
        when(target.getHistoryId()).thenReturn(50L);
        when(target.getCustomer()).thenReturn(customer);
        when(target.getProject()).thenReturn(project);
        when(patchHistoryRepository.findById(50L)).thenReturn(java.util.Optional.of(target));

        // 남은 이력 2건 (완료순)
        PatchHistory h1 = org.mockito.Mockito.mock(PatchHistory.class);
        when(h1.getHistoryId()).thenReturn(40L);
        when(h1.getToVersion()).thenReturn("1.1.0.260511-1");
        when(h1.getCompletedAt()).thenReturn(java.time.LocalDateTime.now().minusDays(2));
        when(h1.getCompletedBy()).thenReturn("ops@ts.com");
        PatchHistory h2 = org.mockito.Mockito.mock(PatchHistory.class);
        when(h2.getHistoryId()).thenReturn(45L);
        when(h2.getToVersion()).thenReturn("1.2.0.260601-1");
        when(h2.getCompletedAt()).thenReturn(java.time.LocalDateTime.now().minusDays(1));
        when(h2.getCompletedBy()).thenReturn("ops@ts.com");
        when(patchHistoryRepository
                .findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByCompletedAtAscCreatedAtAsc(
                        1L, "PRJ"))
                .thenReturn(java.util.List.of(h1, h2));

        when(patchHistoryBuildRepository
                .findAllByHistory_HistoryIdOrderByPatchHistoryBuildIdAsc(any()))
                .thenReturn(java.util.List.of());
        when(customerSiteVersionService.extractBaseVersion("1.1.0.260511-1")).thenReturn("1.1.0");
        when(customerSiteVersionService.extractBaseVersion("1.2.0.260601-1")).thenReturn("1.2.0");

        com.ts.rm.domain.customer.entity.CustomerProject cp =
                org.mockito.Mockito.mock(com.ts.rm.domain.customer.entity.CustomerProject.class);
        when(customerProjectRepository.findByCustomer_CustomerIdAndProject_ProjectId(1L, "PRJ"))
                .thenReturn(java.util.Optional.of(cp));

        // when
        patchHistoryService.deleteHistory(50L);

        // then — 대상 스냅샷 삭제 + 이력 삭제 + 사이트버전 초기화 + 재생 2회 + last_patched 갱신
        verify(patchHistoryBuildRepository).deleteAllByHistory_HistoryId(50L);
        verify(patchHistoryRepository).delete(target);
        verify(customerSiteVersionService).clearByCustomerAndProject(1L, "PRJ");
        verify(customerSiteVersionService, times(2)).applyComponentVersions(
                eq(1L), eq("PRJ"), any(), any(), any(), any());
        verify(cp).updateLastPatchInfo(eq("1.2.0.260601-1"), any());
        verify(customerProjectRepository).save(cp);
    }

    @Test
    @DisplayName("deleteHistory — 남은 이력 없으면 last_patched 초기화(패치 미적용)")
    void deleteHistory_noRemaining_clearsLastPatch() {
        com.ts.rm.domain.customer.entity.Customer customer =
                org.mockito.Mockito.mock(com.ts.rm.domain.customer.entity.Customer.class);
        when(customer.getCustomerId()).thenReturn(1L);
        com.ts.rm.domain.project.entity.Project project =
                org.mockito.Mockito.mock(com.ts.rm.domain.project.entity.Project.class);
        when(project.getProjectId()).thenReturn("PRJ");

        PatchHistory target = org.mockito.Mockito.mock(PatchHistory.class);
        when(target.getHistoryId()).thenReturn(50L);
        when(target.getCustomer()).thenReturn(customer);
        when(target.getProject()).thenReturn(project);
        when(patchHistoryRepository.findById(50L)).thenReturn(java.util.Optional.of(target));
        when(patchHistoryRepository
                .findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByCompletedAtAscCreatedAtAsc(
                        1L, "PRJ"))
                .thenReturn(java.util.List.of());

        com.ts.rm.domain.customer.entity.CustomerProject cp =
                org.mockito.Mockito.mock(com.ts.rm.domain.customer.entity.CustomerProject.class);
        when(customerProjectRepository.findByCustomer_CustomerIdAndProject_ProjectId(1L, "PRJ"))
                .thenReturn(java.util.Optional.of(cp));

        patchHistoryService.deleteHistory(50L);

        verify(customerSiteVersionService).clearByCustomerAndProject(1L, "PRJ");
        verify(cp).updateLastPatchInfo(null, null);
        verify(customerProjectRepository).save(cp);
    }

    @Test
    @DisplayName("deleteHistory — 표준 패치(고객사 미지정)는 재계산하지 않음")
    void deleteHistory_standardPatch_noRecompute() {
        com.ts.rm.domain.project.entity.Project project =
                org.mockito.Mockito.mock(com.ts.rm.domain.project.entity.Project.class);
        PatchHistory target = org.mockito.Mockito.mock(PatchHistory.class);
        when(target.getHistoryId()).thenReturn(60L);
        when(target.getCustomer()).thenReturn(null);
        when(target.getProject()).thenReturn(project);
        when(patchHistoryRepository.findById(60L)).thenReturn(java.util.Optional.of(target));

        patchHistoryService.deleteHistory(60L);

        verify(patchHistoryBuildRepository).deleteAllByHistory_HistoryId(60L);
        verify(patchHistoryRepository).delete(target);
        verify(customerSiteVersionService, never()).clearByCustomerAndProject(any(), any());
    }
```

import 추가(테스트 상단): `import static org.mockito.ArgumentMatchers.eq;`, `import static org.mockito.Mockito.times;`. (`any`, `never`, `verify`, `when` 은 Task 1 에서 이미 추가됨.)

- [ ] **Step 3: 테스트 실패 확인**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchHistoryServiceTest"
```
Expected: 컴파일 실패 또는 FAIL — `deleteHistory` 가 아직 재계산/스냅샷 삭제를 하지 않고, 신규 의존성/리포 메서드가 없음.

- [ ] **Step 4: `PatchHistoryService.deleteHistory` 재계산 구현**

`PatchHistoryService.java` — `final` 필드 추가(`@RequiredArgsConstructor`):

```java
    private final CustomerSiteVersionService customerSiteVersionService;
    private final CustomerProjectRepository customerProjectRepository;
```

import 추가:
```java
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.entity.CustomerProject;
import com.ts.rm.domain.customer.repository.CustomerProjectRepository;
import com.ts.rm.domain.customer.service.CustomerSiteVersionService;
```

`deleteHistory` 교체 + private `recomputeCustomerVersions` 추가:

```java
    @Transactional
    public void deleteHistory(Long historyId) {
        PatchHistory history = patchHistoryRepository.findById(historyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "패치 이력을 찾을 수 없습니다. ID: " + historyId));

        Customer customer = history.getCustomer();
        String projectId = history.getProject().getProjectId();

        // 빌드 스냅샷 먼저 제거 후 이력 삭제 (ORM 레벨에서 명시 삭제)
        patchHistoryBuildRepository.deleteAllByHistory_HistoryId(historyId);
        patchHistoryRepository.delete(history);
        log.info("패치 이력 삭제 완료 - historyId: {}, patchName: {}",
                historyId, history.getPatchName());

        // 고객사 지정 이력이면, 남은 이력 기준으로 버전 정보 재계산
        if (customer != null) {
            recomputeCustomerVersions(customer.getCustomerId(), projectId);
        }
    }

    /**
     * 남은 이력을 완료순으로 재생하여 고객사 버전 정보를 재구성한다.
     *
     * <p>사이트 버전을 모두 비운 뒤 남은 이력을 완료순으로 재생하므로,
     * 삭제한 패치에서만 등장한 엔진 행은 자연 소멸한다. 남은 이력이 없으면
     * last_patched 정보를 비워 "패치 미적용" 상태로 만든다.
     */
    private void recomputeCustomerVersions(Long customerId, String projectId) {
        customerSiteVersionService.clearByCustomerAndProject(customerId, projectId);

        List<PatchHistory> remaining = patchHistoryRepository
                .findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByCompletedAtAscCreatedAtAsc(
                        customerId, projectId);

        if (remaining.isEmpty()) {
            customerProjectRepository
                    .findByCustomer_CustomerIdAndProject_ProjectId(customerId, projectId)
                    .ifPresent(cp -> {
                        cp.updateLastPatchInfo(null, null);
                        customerProjectRepository.save(cp);
                    });
            log.info("패치 이력 재계산 - 남은 이력 없음, 버전 초기화. customerId: {}, projectId: {}",
                    customerId, projectId);
            return;
        }

        for (PatchHistory h : remaining) {
            String baseVersion = customerSiteVersionService.extractBaseVersion(h.getToVersion());
            List<CustomerSiteVersionService.BuildSnapshot> builds = patchHistoryBuildRepository
                    .findAllByHistory_HistoryIdOrderByPatchHistoryBuildIdAsc(h.getHistoryId())
                    .stream()
                    .map(b -> new CustomerSiteVersionService.BuildSnapshot(
                            b.getKind(), b.getEngineName(), b.getFullVersion()))
                    .toList();
            customerSiteVersionService.applyComponentVersions(
                    customerId, projectId, baseVersion, builds, h.getCompletedBy(), h.getCompletedAt());
        }

        PatchHistory last = remaining.get(remaining.size() - 1);
        CustomerProject cp = customerProjectRepository
                .findByCustomer_CustomerIdAndProject_ProjectId(customerId, projectId)
                .orElseGet(() -> CustomerProject.create(last.getCustomer(), last.getProject()));
        cp.updateLastPatchInfo(last.getToVersion(), last.getCompletedAt());
        customerProjectRepository.save(cp);

        log.info("패치 이력 재계산 완료 - customerId: {}, projectId: {}, lastPatchedVersion: {}",
                customerId, projectId, last.getToVersion());
    }
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchHistoryServiceTest"
```
Expected: PASS (Task 1 의 2건 + Task 3 의 3건 = 5 tests)

- [ ] **Step 6: 전체 테스트 + 빌드 확인**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew clean build
```
Expected: BUILD SUCCESSFUL, 전체 테스트 PASS.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/ts/rm/domain/patch/service/PatchHistoryService.java \
        src/main/java/com/ts/rm/domain/patch/repository/PatchHistoryRepository.java \
        src/main/java/com/ts/rm/domain/patch/repository/PatchHistoryBuildRepository.java \
        src/test/java/com/ts/rm/domain/patch/service/PatchHistoryServiceTest.java
git commit -m "feat: 패치 이력 삭제 시 남은 이력 재생으로 고객사 버전 정보 재계산"
```

---

## 검증 (수동, 선택)

배포 후 운영/개발 서버에서:
1. 빌드 포함 패치를 고객사에 완료 → `customer_site_version` 에 BASE/WEB/ENGINE 기록 + `patch_history_build` 에 스냅샷 확인.
2. 또 다른 패치 완료 → 버전 갱신.
3. 최신 패치 이력 삭제 → 헤더 VERSION/빌드 버전이 직전 패치 값으로 롤백되는지 확인.
4. 모든 이력 삭제 → "패치 미적용" 표시 확인.

## 비목표

- 빌드 스냅샷은 변경 배포 이후 완료 패치부터 누적된다(과거 패치는 백필 불가 — 원본 소멸).
- 프론트엔드 변경 없음.
- 표준 패치 이력 삭제는 재계산 없음.

---

## Self-Review

- **Spec coverage:** §3-1 빌드 스냅샷 영구화 → Task 1. §3-2 삭제 시 재계산(replay) → Task 3. §3-3 로직 재사용(공통 헬퍼/extractBaseVersion) → Task 2. §4 완전 재생 과도기 → Task 3 recompute(빈 builds 시 BASE만, 빌드 스냅샷 없는 이력은 WEB/ENGINE 미기여). §2 성공 기준(롤백/미적용/표준 제외) → Task 3 테스트 3종. 누락 없음.
- **Placeholder scan:** 모든 코드 스텝에 실제 코드 포함. "적절히/TODO" 없음.
- **Type consistency:** `BuildSnapshot(kind, engineName, fullVersion)` 정의(Task 2)와 사용(Task 2 PatchService, Task 3 recompute) 일치. `extractBaseVersion`/`applyComponentVersions`/`clearByCustomerAndProject` 시그니처 Task 2 정의와 Task 3 소비 일치. repo 메서드명 Task 정의/소비 일치.
