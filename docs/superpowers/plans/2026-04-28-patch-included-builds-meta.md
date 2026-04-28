# 패치 메타 영구 저장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 패치 생성 시 응답으로만 일회성 내려가던 `includedBuilds` / `hotfixesInRange` / `isBuildOnly` 를 패치 row 와 함께 영구 저장하고, 목록 화면에 배지·상세 화면에 섹션으로 노출한다.

**Architecture:** `patch_included_build` / `patch_hotfix_in_range` 두 신규 테이블로 풀 정규화하고 `patch_file` 테이블에 `is_build_only` / `is_build_included` boolean 캐시를 추가한다. `PatchGenerationService.generatePatch` 끝부분에서 같은 트랜잭션 안에 메타를 저장하고, `applyBuildSelection` 의 selectedBuilds map 을 응답 매핑과 영구 저장이 공유한다. 빌드 row 가 후일 삭제되어도 fullVersion snapshot 으로 메타가 보존된다.

**Tech Stack:** Spring Boot 3.5.6 / Java 17 / JPA + QueryDSL / MapStruct / JUnit 5 + Mockito (BE) — React 19 + TypeScript / TanStack Query / shadcn-ui (FE).

**연관 문서:** `release-manager-api/docs/superpowers/specs/2026-04-28-patch-included-builds-meta-design.md` (commit `13023aa`).

**WSL 환경 우회 패턴:** WSL 에 Java 21 만 설치. 빌드 검증은 build.gradle 의 `JavaLanguageVersion.of(17)` 을 임시 21 로 바꿨다가 원복. 매 검증 step 의 명령 블록에 그대로 포함.

**적용 범위:** 표준 + 커스텀 패치 모두 (patch_file 테이블 단일).

---

## 파일 구조

### 백엔드 — 변경 / 신규

| 경로 | 책임 |
| --- | --- |
| `src/main/java/com/ts/rm/domain/patch/entity/PatchIncludedBuild.java` | **신규** — patch 에 포함된 빌드 행 (kind/engineName/buildVersionId/fullVersion) |
| `src/main/java/com/ts/rm/domain/patch/entity/PatchHotfixInRange.java` | **신규** — patch 범위 안 핫픽스 메타 행 |
| `src/main/java/com/ts/rm/domain/patch/repository/PatchIncludedBuildRepository.java` | **신규** — JPA repository |
| `src/main/java/com/ts/rm/domain/patch/repository/PatchHotfixInRangeRepository.java` | **신규** — JPA repository |
| `src/main/java/com/ts/rm/domain/patch/entity/Patch.java` | `is_build_only` / `is_build_included` 컬럼 추가 |
| `src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java` | `applyBuildSelection` 반환 타입 변경, 메타 저장 헬퍼 추가, generatePatch 끝부분에서 메타 영구 저장 |
| `src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java` | `DetailResponse` / `ListResponse` 에 새 필드 추가 |
| `src/main/java/com/ts/rm/domain/patch/mapper/PatchDtoMapper.java` | DetailResponse / ListResponse 매핑 갱신 |
| `src/main/java/com/ts/rm/domain/patch/service/PatchService.java` | 목록 조회에서 메타 batch query → summary 매핑 |
| `src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java` | 메타 저장 검증 테스트 추가 |

### 프론트엔드 — 변경

| 경로 | 책임 |
| --- | --- |
| `src/entities/patches/patch/model/types.ts` | `ListResponse` / `DetailResponse` 에 isBuildOnly / isBuildIncluded / includedBuildsSummary / includedBuilds / hotfixesInRange 추가 |
| `src/features/patches/patch-management/ui/PatchTable.tsx` | 행에 빌드 배지 노출 |
| `src/features/patches/patch-management/ui/PatchDetail*.tsx` (또는 동등) | "포함된 빌드" / "범위 안의 핫픽스" 섹션 추가 |

### 운영 자료

| 경로 | 비고 |
| --- | --- |
| `docs/superpowers/specs/2026-04-28-patch-included-builds-meta-design.md` | spec 본문 §8 의 수동 SQL 스크립트 그대로 |

---

## Phase 1 — 백엔드: 데이터 모델

### Task 1: 신규 entity 2개 + repository 2개

**Files:**
- Create: `release-manager-api/src/main/java/com/ts/rm/domain/patch/entity/PatchIncludedBuild.java`
- Create: `release-manager-api/src/main/java/com/ts/rm/domain/patch/entity/PatchHotfixInRange.java`
- Create: `release-manager-api/src/main/java/com/ts/rm/domain/patch/repository/PatchIncludedBuildRepository.java`
- Create: `release-manager-api/src/main/java/com/ts/rm/domain/patch/repository/PatchHotfixInRangeRepository.java`

> 단순 entity / repository — TDD 제외 (CLAUDE.md "TDD 제외 대상: 단순 DTO/Entity").

- [ ] **Step 1: PatchIncludedBuild entity 신규**

```java
package com.ts.rm.domain.patch.entity;

import com.ts.rm.domain.common.entity.BaseEntity;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 패치에 포함된 빌드 메타 행. WEB 1개 또는 ENGINE N개로 구성된다.
 *
 * <p>spec §3.1. 빌드 row 가 후일 삭제되면 build_version_id 는 NULL 로 setting (ON DELETE SET NULL),
 * full_version snapshot 만 보존된다. created_at / updated_at 은 BaseEntity 가 관리.
 */
@Entity
@Table(name = "patch_included_build")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchIncludedBuild extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patch_included_build_id")
    private Long patchIncludedBuildId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patch_id", nullable = false)
    private Patch patch;

    /** 'WEB' | 'ENGINE'. spec §3.1 의미 명세. */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    /** kind='ENGINE' 일 때만 채워진다. WEB 은 NULL. */
    @Column(name = "engine_name", length = 50)
    private String engineName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_version_id")
    private ReleaseVersion buildVersion;

    @Column(name = "full_version", nullable = false, length = 50)
    private String fullVersion;
}
```

- [ ] **Step 2: PatchHotfixInRange entity 신규**

```java
package com.ts.rm.domain.patch.entity;

import com.ts.rm.domain.common.entity.BaseEntity;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 패치 범위 안에 존재한 핫픽스 메타 행. 별도 적용 안내용.
 *
 * <p>spec §3.2. created_at / updated_at 은 BaseEntity 가 관리.
 */
@Entity
@Table(name = "patch_hotfix_in_range")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchHotfixInRange extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patch_hotfix_in_range_id")
    private Long patchHotfixInRangeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patch_id", nullable = false)
    private Patch patch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotfix_version_id")
    private ReleaseVersion hotfixVersion;

    @Column(name = "full_version", nullable = false, length = 50)
    private String fullVersion;

    @Column(name = "hotfix_version", nullable = false)
    private Integer hotfixVersionNumber;
}
```

> 컬럼명 `hotfix_version` 과 entity 필드 `hotfixVersionNumber` 의 매핑 — Java 필드명을 `hotfixVersion` 으로 두면 `releaseversion` 의 `hotfixVersion` 과 헷갈릴 수 있어 `hotfixVersionNumber` 로 명명하되 `@Column(name="hotfix_version")` 으로 매핑. `BaseEntity` extend 로 created_at / updated_at 자동 관리 (CLAUDE.md "All entities extend BaseEntity" 룰).

- [ ] **Step 3: PatchIncludedBuildRepository 신규**

```java
package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchIncludedBuildRepository extends JpaRepository<PatchIncludedBuild, Long> {

    /**
     * patch_id 별 모든 PatchIncludedBuild 행 조회.
     */
    List<PatchIncludedBuild> findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(Long patchId);

    /**
     * 여러 patch_id 의 모든 PatchIncludedBuild 행 batch 조회 (N+1 방지).
     * spec §5.4.
     */
    List<PatchIncludedBuild> findAllByPatch_PatchIdIn(List<Long> patchIds);
}
```

- [ ] **Step 4: PatchHotfixInRangeRepository 신규**

```java
package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.PatchHotfixInRange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchHotfixInRangeRepository extends JpaRepository<PatchHotfixInRange, Long> {

    List<PatchHotfixInRange> findAllByPatch_PatchIdOrderByHotfixVersionNumberAsc(Long patchId);
}
```

- [ ] **Step 5: 컴파일 검증**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: `BUILD SUCCESSFUL`. line 14 = 17.

- [ ] **Step 6: 커밋**

```
git -C release-manager-api add \
    src/main/java/com/ts/rm/domain/patch/entity/PatchIncludedBuild.java \
    src/main/java/com/ts/rm/domain/patch/entity/PatchHotfixInRange.java \
    src/main/java/com/ts/rm/domain/patch/repository/PatchIncludedBuildRepository.java \
    src/main/java/com/ts/rm/domain/patch/repository/PatchHotfixInRangeRepository.java
git -C release-manager-api commit -m "feat: 패치 메타 entity 2개 + repository 2개 신규

PatchIncludedBuild (WEB/ENGINE 빌드 메타) + PatchHotfixInRange (범위 안
핫픽스 메타) 두 entity 와 JpaRepository 를 추가. 빌드/핫픽스 row 삭제
시 build_version_id / hotfix_version_id 는 NULL 로 SET (DB 제약), full_version
snapshot 은 보존되는 모델."
```

---

### Task 2: Patch entity 에 캐시 컬럼 추가

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/entity/Patch.java`

> 단순 entity 변경 — TDD 제외.

- [ ] **Step 1: Patch entity 에 두 boolean 컬럼 + 양방향 관계 추가**

`Patch.java` 의 마지막 필드 직후 (또는 적절한 그룹 위치) 에 추가:

```java
    @Column(name = "is_build_only", nullable = false)
    @Builder.Default
    private Boolean isBuildOnly = false;

    @Column(name = "is_build_included", nullable = false)
    @Builder.Default
    private Boolean isBuildIncluded = false;

    @jakarta.persistence.OneToMany(
            mappedBy = "patch",
            cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true,
            fetch = jakarta.persistence.FetchType.LAZY
    )
    @lombok.Builder.Default
    private java.util.List<PatchIncludedBuild> includedBuilds = new java.util.ArrayList<>();

    @jakarta.persistence.OneToMany(
            mappedBy = "patch",
            cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true,
            fetch = jakarta.persistence.FetchType.LAZY
    )
    @lombok.Builder.Default
    private java.util.List<PatchHotfixInRange> hotfixesInRange = new java.util.ArrayList<>();
```

> @OneToMany 양방향: 헬퍼 메서드 `addIncludedBuild` / `addHotfixInRange` 를 추가하면 양쪽 일관성 유지가 쉬움.

- [ ] **Step 2: 양방향 헬퍼 메서드 추가**

`Patch.java` 안에 추가:

```java
    public void addIncludedBuild(PatchIncludedBuild item) {
        item.setPatch(this);
        this.includedBuilds.add(item);
    }

    public void addHotfixInRange(PatchHotfixInRange item) {
        item.setPatch(this);
        this.hotfixesInRange.add(item);
    }
```

import 정리: `java.util.ArrayList`, `java.util.List`, `jakarta.persistence.OneToMany`, `jakarta.persistence.CascadeType`, `jakarta.persistence.FetchType` 등은 인라인 FQN 대신 import 블록에 정렬해 추가하세요 (위 코드 블록은 가독성을 위해 인라인 FQN 으로 적었음 — 실제 코드는 import 정리 후 단일 식별자로).

- [ ] **Step 3: 컴파일 검증**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: `BUILD SUCCESSFUL`. (이 시점에서 ddl-auto=create-drop 인 H2 가 새 컬럼/테이블을 자동 생성함. 운영 DB 는 spec §8 의 수동 SQL 적용 필요.)

- [ ] **Step 4: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/entity/Patch.java
git -C release-manager-api commit -m "feat: Patch entity 에 is_build_only / is_build_included 캐시 + 양방향 관계 추가

목록 배지용 빠른 조회 캐시 두 boolean 과, 메타 영구 저장을 위해 includedBuilds /
hotfixesInRange 양방향 OneToMany (CascadeType.ALL + orphanRemoval=true) 를 추가.
양방향 일관성 유지를 위해 addIncludedBuild / addHotfixInRange 헬퍼도 함께
추가했다."
```

---

## Phase 2 — 백엔드: 메타 영구 저장

### Task 3: `applyBuildSelection` 반환 타입을 `Map<Long, ReleaseVersion>` 으로 변경

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java`

> 직전 picker 작업의 cleanup 후보 [I-1] (buildIncludedBuilds 의 N+1 중복 조회) 도 동시 해소. TDD 제외 (시그니처 리팩터, 행위 변화 없음).

- [ ] **Step 1: applyBuildSelection 시그니처 변경**

`PatchGenerationService.java` 의 `applyBuildSelection` 정의를 다음과 같이 변경:

```java
    /**
     * picker 입력에 따라 빌드 디렉토리에서 web/, engine/{engineName}/, etc/ 를 outputDir 로 복사.
     *
     * <p>반환 selectedBuilds map 은 buildIncludedBuilds / persistIncludedBuilds 가
     * 동일한 ReleaseVersion 객체를 재사용할 수 있도록 호출자에게 노출한다.
     *
     * @return buildVersionId → ReleaseVersion 매핑 (insertion order 유지).
     */
    private Map<Long, ReleaseVersion> applyBuildSelection(Path outputDir, PatchDto.BuildSelection sel) throws IOException {
        log.info("picker 복사 시작 - WEB: {}, ENGINE: {}",
                sel.web() == null ? "(없음)" : sel.web().buildVersionId(),
                sel.engines() == null ? List.of() : sel.engines());

        Map<Long, ReleaseVersion> selectedBuilds = new LinkedHashMap<>();

        // 기존 a/b/c 단계 그대로 — selectedBuilds 에 캐시.
        if (sel.web() != null) {
            ReleaseVersion bv = loadBuildVersion(sel.web().buildVersionId());
            Path src = fileSystemService.resolveBuildBasePath(bv.getBuildBaseVersion(), bv.getBuildVersion()).resolve("web");
            copyDirectoryReplaceExisting(src, outputDir.resolve("web"));
            selectedBuilds.put(bv.getReleaseVersionId(), bv);
        }
        if (sel.engines() != null) {
            for (PatchDto.SelectedEngine se : sel.engines()) {
                ReleaseVersion bv = loadBuildVersion(se.buildVersionId());
                Path src = fileSystemService.resolveBuildBasePath(bv.getBuildBaseVersion(), bv.getBuildVersion())
                        .resolve("engine").resolve(se.engineName());
                copyDirectoryReplaceExisting(src, outputDir.resolve("engine").resolve(se.engineName()));
                selectedBuilds.putIfAbsent(bv.getReleaseVersionId(), bv);
            }
        }

        List<ReleaseVersion> sortedByBuildVersion = selectedBuilds.values().stream()
                .sorted(Comparator.comparingInt(ReleaseVersion::getBuildVersion))
                .toList();
        log.info("ETC 동행 복사 순서: {}",
                sortedByBuildVersion.stream().map(ReleaseVersion::getFullVersion).toList());
        for (ReleaseVersion bv : sortedByBuildVersion) {
            Path src = fileSystemService.resolveBuildBasePath(bv.getBuildBaseVersion(), bv.getBuildVersion()).resolve("etc");
            if (Files.isDirectory(src)) {
                copyDirectoryReplaceExisting(src, outputDir.resolve("etc"));
            }
        }

        return selectedBuilds;
    }
```

- [ ] **Step 2: 호출자 정정 — generatePatch 안의 호출**

`PatchGenerationService.generatePatch` 안의 picker 단계 호출 (commit `8553c96` 에서 추가됐던 `applyBuildSelection(outputDir, buildSelection)`) 을 다음과 같이 캡처:

```java
            // ---- buildSelection 별도 단계 (spec §5.1 / Q-S2) ----
            Map<Long, ReleaseVersion> selectedBuilds;
            if (buildSelection != null && buildSelection.enabled()) {
                selectedBuilds = applyBuildSelection(outputDir, buildSelection);
            } else {
                selectedBuilds = java.util.Map.of();
            }
```

- [ ] **Step 3: buildIncludedBuilds 가 selectedBuilds 를 받도록 시그니처 변경**

기존 `buildIncludedBuilds(PatchDto.BuildSelection sel)` 을 다음으로 교체:

```java
    /**
     * 응답 보강용 IncludedBuilds 생성. selectedBuilds 캐시 (applyBuildSelection 결과) 를 활용해
     * loadBuildVersion 중복 호출을 피한다.
     */
    private PatchDto.IncludedBuilds buildIncludedBuilds(PatchDto.BuildSelection sel,
                                                       Map<Long, ReleaseVersion> selectedBuilds) {
        if (sel == null || !sel.enabled()) {
            return new PatchDto.IncludedBuilds(null, java.util.List.of());
        }
        PatchDto.IncludedWeb web = null;
        if (sel.web() != null) {
            ReleaseVersion bv = selectedBuilds.get(sel.web().buildVersionId());
            if (bv == null) bv = loadBuildVersion(sel.web().buildVersionId());
            web = new PatchDto.IncludedWeb(bv.getReleaseVersionId(), bv.getFullVersion());
        }
        java.util.List<PatchDto.IncludedEngine> engines = new java.util.ArrayList<>();
        if (sel.engines() != null) {
            for (PatchDto.SelectedEngine se : sel.engines()) {
                ReleaseVersion bv = selectedBuilds.get(se.buildVersionId());
                if (bv == null) bv = loadBuildVersion(se.buildVersionId());
                engines.add(new PatchDto.IncludedEngine(se.engineName(), bv.getReleaseVersionId(), bv.getFullVersion()));
            }
        }
        return new PatchDto.IncludedBuilds(web, engines);
    }
```

- [ ] **Step 4: generatePatch 의 buildIncludedBuilds 호출부 정정**

기존 `PatchDto.IncludedBuilds includedBuilds = buildIncludedBuilds(buildSelection);` 를 다음으로 교체:

```java
            PatchDto.IncludedBuilds includedBuilds = buildIncludedBuilds(buildSelection, selectedBuilds);
```

- [ ] **Step 5: 컴파일 + 회귀 테스트**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchGenerationServiceTest" 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: 모든 테스트 PASS (기존 7건 회귀 없음).

- [ ] **Step 6: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java
git -C release-manager-api commit -m "refactor: applyBuildSelection 이 selectedBuilds 를 반환하도록 시그니처 변경

buildIncludedBuilds 가 동일 ReleaseVersion 캐시를 재사용하도록 selectedBuilds
map 을 함께 받게 만들어 동일 ID 에 대한 loadBuildVersion 중복 조회 (직전
picker 작업의 cleanup 후보 [I-1]) 를 해소. 후속 메타 영구 저장 단계도
같은 캐시를 재사용한다."
```

---

### Task 4: 메타 영구 저장 + Patch 캐시 갱신 (TDD)

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java`
- Modify: `release-manager-api/src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java`

- [ ] **Step 1: 실패할 테스트 추가**

`PatchGenerationServiceTest.java` 에 다음 테스트 추가:

```java
@Test
@DisplayName("buildSelection.enabled=true 면 PatchIncludedBuild 행 + isBuildIncluded=true 가 영구 저장")
void persistIncludedBuilds_savesRowsAndFlag() throws IOException {
    // GIVEN: WEB v260428 + NC_SMS v260427 picker, base 1개 (from!=to), 핫픽스 0개.
    // 기존 pickerSelection_partialCopy 와 동일 fixture 를 활용.
    // patchIncludedBuildRepository.saveAll 또는 patch.addIncludedBuild → patchRepository.save 로 cascade 저장.

    // WHEN: generatePatch(...)

    // THEN:
    //   verify(patchIncludedBuildRepository) 의 save 또는 cascade 결과로 행 2개 (WEB + NC_SMS) 저장.
    //   savedPatch.isBuildIncluded == true.
    //   savedPatch.isBuildOnly == false (from!=to).
}

@Test
@DisplayName("hotfixesInRange 가 비어있지 않으면 PatchHotfixInRange 행이 영구 저장")
void persistHotfixesInRange_savesRows() throws IOException {
    // GIVEN: 핫픽스 1건 (id=99, 1.1.0.1, hotfixVersion=1) 이 from~to 사이에 존재.
    // when(releaseVersionRepository.findHotfixesInBaseRange(...)).thenReturn(List.of(hotfix));

    // WHEN: generatePatch(...)

    // THEN: cascade 저장된 hotfixesInRange 행 1개 + fullVersion="1.1.0.1" 매칭.
}

@Test
@DisplayName("buildSelection 미포함 (null) 일 때 isBuildIncluded=false 이고 메타 행 0개")
void noBuildSelection_noMetaRows() throws IOException {
    // GIVEN: buildSelection=null, 핫픽스 0개.

    // WHEN: generatePatch(..., null)

    // THEN: savedPatch.isBuildIncluded == false, includedBuilds 0개, hotfixesInRange 0개.
}
```

> 정확한 setup 은 기존 `pickerSelection_partialCopy` 테스트의 mock 셋업 (`releaseVersionRepository.findById`, `findVersionsBetweenExcludingHotfixes`, `findHotfixesInBaseRange`, `fileSystemService.resolveBuildBasePath`, `releaseFileRepository.findAll...`, `accountLookupService`, `patchRepository.save`, `patchHistoryRepository.save`, `mariaDBScriptGenerator.generate`, `crateDBScriptGenerator.generate`, `ReflectionTestUtils.setField(patchGenerationService, "releaseBasePath", tempDir.toString())`) 을 그대로 답습. cascade 로 메타 행이 저장되는 경우 patchRepository.save 의 인자 (Patch entity) 의 includedBuilds / hotfixesInRange 컬렉션 size 를 verify (ArgumentCaptor) 로 검증해도 충분.

- [ ] **Step 2: 테스트 실패 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchGenerationServiceTest" 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 3개 신규 테스트 FAIL — 메타 저장 로직 미구현.

- [ ] **Step 3: PatchGenerationService 에 메타 저장 헬퍼 + generatePatch 끝부분 정정**

`PatchGenerationService.java` 에 헬퍼 추가:

```java
    /**
     * picker 로 선택된 빌드들을 PatchIncludedBuild 로 저장. patch 의 양방향 관계를 통해 cascade.
     *
     * <p>kind 명세 (spec §3.1):
     * <ul>
     *   <li>WEB: kind="WEB", engine_name=NULL</li>
     *   <li>ENGINE: kind="ENGINE", engine_name=engineName</li>
     * </ul>
     */
    private void persistIncludedBuilds(Patch patch, PatchDto.BuildSelection sel,
                                       Map<Long, ReleaseVersion> selectedBuilds) {
        if (sel == null || !sel.enabled()) return;
        if (sel.web() != null) {
            ReleaseVersion bv = selectedBuilds.get(sel.web().buildVersionId());
            if (bv == null) bv = loadBuildVersion(sel.web().buildVersionId());
            PatchIncludedBuild row = PatchIncludedBuild.builder()
                    .kind("WEB")
                    .engineName(null)
                    .buildVersion(bv)
                    .fullVersion(bv.getFullVersion())
                    .build();
            patch.addIncludedBuild(row);
        }
        if (sel.engines() != null) {
            for (PatchDto.SelectedEngine se : sel.engines()) {
                ReleaseVersion bv = selectedBuilds.get(se.buildVersionId());
                if (bv == null) bv = loadBuildVersion(se.buildVersionId());
                PatchIncludedBuild row = PatchIncludedBuild.builder()
                        .kind("ENGINE")
                        .engineName(se.engineName())
                        .buildVersion(bv)
                        .fullVersion(bv.getFullVersion())
                        .build();
                patch.addIncludedBuild(row);
            }
        }
    }

    /**
     * 범위 안의 핫픽스를 PatchHotfixInRange 로 저장. patch 의 양방향 관계를 통해 cascade.
     */
    private void persistHotfixesInRange(Patch patch,
                                        java.util.List<ReleaseVersion> hotfixesInRange) {
        for (ReleaseVersion h : hotfixesInRange) {
            PatchHotfixInRange row = PatchHotfixInRange.builder()
                    .hotfixVersion(h)
                    .fullVersion(h.getFullVersion())
                    .hotfixVersionNumber(h.getHotfixVersion())
                    .build();
            patch.addHotfixInRange(row);
        }
    }
```

- [ ] **Step 4: generatePatch 의 끝부분에서 메타 저장 + 캐시 갱신**

`PatchGenerationService.generatePatch` 의 끝부분, 기존 `boolean isBuildOnly = isSameBaseVersion(...)` 직전 또는 직후, GenerateResult 반환 직전에 다음 로직 추가:

```java
            // ---- 메타 영구 저장 (spec §5.1) ----
            // findHotfixesInBaseRange 는 이미 hotfixesInRange 응답 매핑용으로 호출되어 있음 — 같은 결과 재사용.
            java.util.List<ReleaseVersion> hotfixVersions = releaseVersionRepository
                    .findHotfixesInBaseRange(projectId, fromBaseId, toBaseId, customerId);

            patch.setBuildOnly(isBuildOnly);
            patch.setBuildIncluded(buildSelection != null && buildSelection.enabled());
            persistIncludedBuilds(patch, buildSelection, selectedBuilds);
            persistHotfixesInRange(patch, hotfixVersions);
            patch = patchRepository.save(patch);  // cascade 로 메타 행도 저장됨

            // 응답용 hotfixesInRange / includedBuilds 매핑 — 기존 코드 재정렬 가능
            java.util.List<PatchDto.HotfixInRangeInfo> hotfixes = hotfixVersions.stream()
                    .map(h -> new PatchDto.HotfixInRangeInfo(h.getReleaseVersionId(), h.getFullVersion()))
                    .toList();
            PatchDto.IncludedBuilds includedBuilds = buildIncludedBuilds(buildSelection, selectedBuilds);

            return new GenerateResult(patch, isBuildOnly, hotfixes, includedBuilds);
```

> 주의: `setBuildOnly` / `setBuildIncluded` 는 lombok `@Setter` 가 만들어준 메서드 (`setIsBuildOnly` 가 아닌 Boolean prefix 가 제거된 형태일 수 있으니 확인 필요. 만약 lombok 이 `setIsBuildOnly` 로 만든다면 그쪽을 사용). 컴파일 결과로 확인.

기존 코드의 `findHotfixesInBaseRange` 호출이 두 번 (메타 저장용 + 응답 매핑용) 일어나지 않도록 결과를 한 번만 받아 재사용.

- [ ] **Step 5: 테스트 통과 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchGenerationServiceTest" 2>&1 | tail -15
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 모든 PatchGenerationServiceTest PASS (기존 + 신규 3건).

- [ ] **Step 6: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java
git -C release-manager-api commit -m "feat: PatchGenerationService 가 메타 (포함된 빌드 / 핫픽스) 를 영구 저장

generatePatch 의 끝부분에서 buildSelection 의 selectedBuilds map 을 활용해
PatchIncludedBuild / PatchHotfixInRange 행을 patch 양방향 관계로 cascade 저장.
patch.isBuildOnly / isBuildIncluded 캐시 boolean 도 함께 갱신. 모두 같은
@Transactional 안.

findHotfixesInBaseRange 호출은 메타 저장과 응답 매핑이 한 번만 호출하도록
공유."
```

---

## Phase 3 — 백엔드: 응답 DTO

### Task 5: DetailResponse 에 메타 4개 필드 추가

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/mapper/PatchDtoMapper.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchService.java`

> DTO 필드 추가 + 매퍼 정정. 본 단계는 단순 매핑이라 TDD 제외.

- [ ] **Step 1: DetailResponse 에 새 필드 추가**

`PatchDto.java` 의 `DetailResponse` record 정의 끝 (마지막 필드 `LocalDateTime updatedAt` 직후) 에 다음 4개 필드를 추가. 기존 record 시그니처를 다음으로 교체:

```java
    @Schema(description = "패치 상세 응답")
    public record DetailResponse(
            // ...기존 필드 그대로...
            @Schema(description = "수정일시")
            LocalDateTime updatedAt,

            @Schema(description = "Build-only 패치 여부", example = "false")
            boolean isBuildOnly,

            @Schema(description = "빌드 포함 여부", example = "true")
            boolean isBuildIncluded,

            @Schema(description = "포함된 빌드 정보 (빈 객체 가능)")
            IncludedBuilds includedBuilds,

            @Schema(description = "범위 안의 핫픽스 (별도 적용 안내용, 비어있으면 빈 배열)")
            java.util.List<HotfixInRangeInfo> hotfixesInRange
    ) {}
```

> `IncludedBuilds`, `IncludedWeb`, `IncludedEngine`, `HotfixInRangeInfo` 는 이미 PatchDto 에 정의되어 있음 (Phase 2 의 직전 plan).

- [ ] **Step 2: PatchDtoMapper 의 toDetailResponse 정정**

`PatchDtoMapper.java` 의 `toDetailResponse` 가 MapStruct 인터페이스라 자동 매핑되지만, 새 필드 4개를 명시적으로 매핑해야 함. 기존 매퍼 패턴에 맞춰 추가:

```java
    @Mapping(target = "isBuildOnly", source = "buildOnly")
    @Mapping(target = "isBuildIncluded", source = "buildIncluded")
    @Mapping(target = "includedBuilds", source = ".", qualifiedByName = "toIncludedBuilds")
    @Mapping(target = "hotfixesInRange", source = ".", qualifiedByName = "toHotfixesInRange")
    PatchDto.DetailResponse toDetailResponse(Patch patch);

    @Named("toIncludedBuilds")
    default PatchDto.IncludedBuilds toIncludedBuilds(Patch patch) {
        if (!Boolean.TRUE.equals(patch.getIsBuildIncluded())) {
            return new PatchDto.IncludedBuilds(null, java.util.List.of());
        }
        PatchDto.IncludedWeb web = null;
        java.util.List<PatchDto.IncludedEngine> engines = new java.util.ArrayList<>();
        for (com.ts.rm.domain.patch.entity.PatchIncludedBuild row : patch.getIncludedBuilds()) {
            Long bvId = row.getBuildVersion() != null ? row.getBuildVersion().getReleaseVersionId() : null;
            if ("WEB".equals(row.getKind())) {
                web = new PatchDto.IncludedWeb(bvId, row.getFullVersion());
            } else {
                engines.add(new PatchDto.IncludedEngine(row.getEngineName(), bvId, row.getFullVersion()));
            }
        }
        return new PatchDto.IncludedBuilds(web, engines);
    }

    @Named("toHotfixesInRange")
    default java.util.List<PatchDto.HotfixInRangeInfo> toHotfixesInRange(Patch patch) {
        return patch.getHotfixesInRange().stream()
                .map(h -> new PatchDto.HotfixInRangeInfo(
                        h.getHotfixVersion() != null ? h.getHotfixVersion().getReleaseVersionId() : null,
                        h.getFullVersion()))
                .toList();
    }
```

> import 추가: `org.mapstruct.Mapping`, `org.mapstruct.Named`. lombok 의 `getIsBuildIncluded()` / `getIsBuildOnly()` 는 Boolean field 의 generated getter (혹은 `isBuildIncluded()` 가 될 수 있음 — 실제 컴파일 결과로 확인하고 그쪽 사용).

- [ ] **Step 3: PatchService.getPatch 또는 동등 메서드가 DetailResponse 반환하는지 확인**

`PatchService.java` 의 `getPatch(Long patchId)` 가 `Patch` entity 만 반환한다면 그 호출자가 DetailResponse 로 매핑한다. PatchController 가 DetailResponse 로 매핑 호출하는 코드를 확인하고, 메타 매핑이 정상 동작하도록 변경. 보통 변경 없음 (mapper 가 알아서 처리).

- [ ] **Step 4: 컴파일 + 회귀 테스트**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
./gradlew test --tests "com.ts.rm.domain.patch.*" 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: 컴파일 + 모든 patch 도메인 테스트 PASS.

- [ ] **Step 5: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java src/main/java/com/ts/rm/domain/patch/mapper/PatchDtoMapper.java
git -C release-manager-api commit -m "feat: PatchDto.DetailResponse 에 메타 4개 필드 추가

isBuildOnly / isBuildIncluded / includedBuilds / hotfixesInRange 를 응답에
포함. mapper 의 toIncludedBuilds / toHotfixesInRange 헬퍼로 entity → DTO
매핑. build_version_id 가 NULL (빌드 row 삭제됨) 인 경우 IncludedWeb /
IncludedEngine 의 buildVersionId 도 NULL 로 직렬화."
```

---

### Task 6: ListResponse + 목록 batch 조회 (N+1 방지)

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchService.java`

- [ ] **Step 1: ListResponse 에 새 필드 추가**

`PatchDto.java` 의 `ListResponse` 의 마지막 필드 `LocalDateTime updatedAt` 직후 에 다음 추가:

```java
            @Schema(description = "Build-only 패치 여부", example = "false")
            Boolean isBuildOnly,

            @Schema(description = "빌드 포함 여부", example = "true")
            Boolean isBuildIncluded,

            @Schema(description = "포함된 빌드 요약 (예: 'WEB·NC_SMS·NC_FAULT_MS', 빌드 미포함 시 null)", example = "WEB·NC_SMS·NC_FAULT_MS")
            String includedBuildsSummary
```

- [ ] **Step 2: PatchService.listPatchesWithPaging 에서 batch 조회 + summary 매핑**

`PatchService.java` 안의 `listPatchesWithPaging` 을 다음과 같이 정정 (PatchIncludedBuildRepository 주입 필요):

```java
    private final PatchIncludedBuildRepository patchIncludedBuildRepository;
    // ...

    @Transactional(readOnly = true)
    public Page<PatchDto.ListResponse> listPatchesWithPaging(String projectId, String releaseType,
            String customerCode, Pageable pageable) {
        Page<Patch> patches = patchRepository.findAllWithFilters(projectId, releaseType, customerCode, pageable);

        // 페이징 결과의 patch_id 들에 대해 batch 1번으로 PatchIncludedBuild 행 조회
        java.util.List<Long> patchIds = patches.getContent().stream()
                .map(Patch::getPatchId)
                .toList();
        java.util.Map<Long, java.util.List<PatchIncludedBuild>> includedByPatch = patchIds.isEmpty()
                ? java.util.Map.of()
                : patchIncludedBuildRepository.findAllByPatch_PatchIdIn(patchIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(r -> r.getPatch().getPatchId()));

        return PageRowNumberUtil.mapWithRowNumber(patches, (patch, rowNumber) -> {
            PatchDto.ListResponse base = patchDtoMapper.toListResponse(patch);
            String summary = buildIncludedBuildsSummary(
                    includedByPatch.getOrDefault(patch.getPatchId(), java.util.List.of()));
            return new PatchDto.ListResponse(
                    rowNumber,
                    base.patchId(),
                    base.projectId(),
                    base.releaseType(),
                    base.customerCode(),
                    base.customerName(),
                    base.fromVersion(),
                    base.toVersion(),
                    base.patchName(),
                    base.createdByEmail(),
                    base.createdByName(),
                    base.createdByAvatarStyle(),
                    base.createdByAvatarSeed(),
                    base.isDeletedCreator(),
                    base.description(),
                    base.assigneeId(),
                    base.assigneeName(),
                    base.createdAt(),
                    base.updatedAt(),
                    patch.getIsBuildOnly(),
                    patch.getIsBuildIncluded(),
                    summary);
        });
    }

    /**
     * spec §4.3 의 includedBuildsSummary 형식: 'WEB · NC_SMS · NC_FAULT_MS'.
     * 빌드 미포함 시 null.
     */
    private String buildIncludedBuildsSummary(java.util.List<PatchIncludedBuild> rows) {
        if (rows.isEmpty()) return null;
        java.util.List<String> tokens = new java.util.ArrayList<>();
        boolean hasWeb = rows.stream().anyMatch(r -> "WEB".equals(r.getKind()));
        if (hasWeb) tokens.add("WEB");
        rows.stream()
                .filter(r -> "ENGINE".equals(r.getKind()))
                .map(PatchIncludedBuild::getEngineName)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .forEach(tokens::add);
        return String.join(" · ", tokens);
    }
```

> import 정리: `PatchIncludedBuildRepository`, `PatchIncludedBuild` 등.

- [ ] **Step 3: 컴파일 + 빠른 검증**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
./gradlew test --tests "com.ts.rm.domain.patch.*" 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: 컴파일 + 기존 테스트 PASS.

- [ ] **Step 4: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java src/main/java/com/ts/rm/domain/patch/service/PatchService.java
git -C release-manager-api commit -m "feat: PatchDto.ListResponse 에 메타 + summary 추가, 목록 batch 조회로 N+1 방지

isBuildOnly / isBuildIncluded / includedBuildsSummary 를 ListResponse 에 추가.
PatchService.listPatchesWithPaging 이 페이징 결과의 patch_id 들에 대해
findAllByPatch_PatchIdIn batch 1회로 PatchIncludedBuild 행을 모은 뒤
'WEB · NC_SMS · NC_FAULT_MS' 형식의 요약 문자열을 만들어 매핑."
```

---

## Phase 4 — 프론트엔드

### Task 7: 엔티티 타입 추가

**Files:**
- Modify: `release-manager-web/src/entities/patches/patch/model/types.ts`
- Modify: `release-manager-web/src/entities/patches/patch/index.ts` (필요 시 export 추가)

- [ ] **Step 1: 타입 추가**

`types.ts` 의 기존 `PatchListItem` (또는 동등한 ListResponse 타입) 의 마지막 필드 직후에 추가:

```ts
  isBuildOnly?: boolean | null
  isBuildIncluded?: boolean | null
  includedBuildsSummary?: string | null
```

기존 `PatchDetailResponse` (또는 동등) 의 마지막 필드 직후에 추가:

```ts
  isBuildOnly: boolean
  isBuildIncluded: boolean
  includedBuilds: IncludedBuilds  // commit 1a9e3cb 에서 이미 정의
  hotfixesInRange: PatchHotfixInRangeInfo[]  // 동일
```

- [ ] **Step 2: type-check + 단독 커밋 보류**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
```

기대: type 에러 0건 (사용처가 아직 새 필드를 참조하지 않으므로). Task 8 에서 사용 후 통합 commit.

---

### Task 8: PatchTable 행에 빌드 배지 노출

**Files:**
- Modify: `release-manager-web/src/features/patches/patch-management/ui/PatchTable.tsx`

- [ ] **Step 1: 행 컴포넌트 또는 패치명 셀에 배지 추가**

`PatchTable.tsx` 의 patch row 렌더링 위치 (`{row.patchName}` 등) 옆에 다음 배지 그룹을 추가:

```tsx
import { Badge } from '@/shared/ui/badge'
import { Hammer } from 'lucide-react'

// ...row 렌더링...
{patch.isBuildIncluded && patch.includedBuildsSummary && (
  <Badge variant="outline" className="ml-2 max-w-[200px] truncate" title={patch.includedBuildsSummary}>
    <Hammer className="mr-1 h-3 w-3" />
    {patch.includedBuildsSummary}
  </Badge>
)}
{patch.isBuildOnly && (
  <Badge variant="secondary" className="ml-1">Build-only</Badge>
)}
```

> Hammer 아이콘은 lucide-react 에서 빌드/공구 의미 표현. 다른 아이콘 (Wrench, Package 등) 으로 대체 가능. 길이 200px 초과 시 ellipsis + tooltip 으로 전체 표시.

- [ ] **Step 2: type-check + 단독 커밋 보류**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
```

기대: type 에러 0건.

> 단독 커밋 보류 — Task 9 의 Phase 4 통합 commit 에서 한 번에 처리.

---

### Task 9: 패치 상세 화면에 "포함된 빌드" / "범위 안의 핫픽스" 섹션 + 프론트 통합 커밋

**Files:**
- Modify: `release-manager-web/src/features/patches/patch-management/ui/PatchDetail*.tsx` (또는 동등 — 실제 파일명 확인)

> 패치 상세 화면 컴포넌트의 정확한 위치는 implementer 가 first 진입에서 grep 으로 확인 (`grep -rn "DetailResponse\|patchDetail\|PatchDetail" src/features/patches src/widgets`).

- [ ] **Step 1: 두 섹션 신규 마크업**

상세 화면 컴포넌트 안에 다음 섹션 추가:

```tsx
{patch.isBuildOnly && (
  <Badge variant="secondary" className="mb-2">빌드 전용 패치</Badge>
)}

{patch.isBuildIncluded && patch.includedBuilds && (
  <section className="flex flex-col gap-2">
    <h4 className="text-sm font-semibold">포함된 빌드</h4>
    <ul className="flex flex-col gap-1 text-sm">
      {patch.includedBuilds.web && (
        <li className="grid grid-cols-[80px_1fr_auto] gap-2">
          <span className="text-muted-foreground">WEB</span>
          <span>{patch.includedBuilds.web.fullVersion}</span>
          <span className="text-muted-foreground">
            {patch.includedBuilds.web.buildVersionId == null
              ? '(삭제됨)'
              : `#${patch.includedBuilds.web.buildVersionId}`}
          </span>
        </li>
      )}
      {patch.includedBuilds.engines.map((e) => (
        <li key={e.engineName} className="grid grid-cols-[80px_1fr_auto] gap-2">
          <span className="text-muted-foreground">{e.engineName}</span>
          <span>{e.fullVersion}</span>
          <span className="text-muted-foreground">
            {e.buildVersionId == null ? '(삭제됨)' : `#${e.buildVersionId}`}
          </span>
        </li>
      ))}
    </ul>
  </section>
)}

{patch.hotfixesInRange.length > 0 && (
  <section className="flex flex-col gap-2">
    <h4 className="text-sm font-semibold">범위 안의 핫픽스 (별도 적용 필요)</h4>
    <ul className="flex flex-col gap-1 text-sm">
      {patch.hotfixesInRange.map((h) => (
        <li key={`${h.versionId ?? 'deleted'}-${h.fullVersion}`} className="grid grid-cols-[1fr_auto] gap-2">
          <span>{h.fullVersion}</span>
          <span className="text-muted-foreground">
            {h.versionId == null ? '(삭제됨)' : `#${h.versionId}`}
          </span>
        </li>
      ))}
    </ul>
  </section>
)}
```

- [ ] **Step 2: 프론트엔드 통합 커밋 (Task 7~9)**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
```

기대: type 에러 0건.

```
git -C release-manager-web add \
    src/entities/patches/patch/model/types.ts \
    src/entities/patches/patch/index.ts \
    src/features/patches/patch-management/ui/PatchTable.tsx \
    src/features/patches/patch-management/ui/PatchDetail*.tsx
git -C release-manager-web commit -m "feat: 패치 메타 (빌드 / 핫픽스) 를 목록 배지 + 상세 섹션으로 노출

- entities: ListResponse 에 isBuildOnly / isBuildIncluded /
  includedBuildsSummary, DetailResponse 에 includedBuilds /
  hotfixesInRange / isBuildOnly / isBuildIncluded 필드 추가.
- PatchTable: isBuildIncluded 행에 'WEB · NC_SMS · NC_FAULT_MS' 빌드 배지
  (200px ellipsis + tooltip), isBuildOnly 행에 'Build-only' 배지.
- 패치 상세 화면: '포함된 빌드' 섹션 (WEB / ENGINE 구분 + buildVersionId
  표기) + '범위 안의 핫픽스' 섹션. buildVersionId 가 NULL (삭제된 빌드)
  인 경우 '(삭제됨)' 표기."
```

---

## Phase 5 — 통합 검증

### Task 10: 백엔드 풀빌드 + 프론트 type-check + 운영 SQL 안내

**Files:** (검증만, 코드 변경 없음)

- [ ] **Step 1: 백엔드 풀빌드 (compileJava + 본 plan 관련 테스트)**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava compileTestJava 2>&1 | tail -10
./gradlew test --tests "com.ts.rm.domain.patch.*" 2>&1 | tail -15
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: 컴파일 + 모든 patch 도메인 테스트 PASS, line 14 = 17.

- [ ] **Step 2: 프론트 type-check**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
```

기대: 에러 0건.

- [ ] **Step 3: 운영 DB 수동 SQL 안내**

운영자에게 spec `§8` (`docs/superpowers/specs/2026-04-28-patch-included-builds-meta-design.md` 의 §8 운영 DB 수동 SQL) 을 적용하도록 안내. 본 plan 으로 코드 배포 전 SQL 적용이 선행되어야 ALTER TABLE / CREATE TABLE 누락으로 인한 운영 장애를 막는다.

- [ ] **Step 4: 수동 시나리오 검증 (운영 / dev 환경)**

다음 시나리오를 직접 확인:
1. 신규 패치 생성 (빌드 미포함) → 목록에 빌드 배지 미노출, 상세 "포함된 빌드" 섹션 미노출.
2. 신규 패치 생성 (WEB + NC_SMS + NC_FAULT_MS) → 목록 행에 `🔨 WEB · NC_FAULT_MS · NC_SMS` 배지, 상세에 "포함된 빌드" 섹션 정상.
3. Build-only 패치 (`from == to`) 생성 → "Build-only" 배지 + 상세에 "빌드 전용 패치" 표시.
4. 핫픽스 범위 안의 패치 → "범위 안의 핫픽스" 섹션 + 항목 표시.
5. 기존 패치 (메타 미저장) → 모든 배지 미노출 (정상). 상세 섹션도 미노출.
6. 빌드 row 삭제 후 해당 패치 상세 → "(삭제됨)" 표기, fullVersion 은 보존.

- [ ] **Step 5: 검증 종료 (커밋 없음)**

본 task 는 코드 변경 없음. 모든 검증 통과 시 plan 완료.

---

## 부록 — 폐기되는 항목 / 비목표

본 plan 으로 폐기되는 직전 cleanup 후보:
- **picker 작업 cleanup [I-1]** (`buildIncludedBuilds` 의 N+1 중복 조회) — Task 3 의 시그니처 변경으로 동시 해소.

본 plan 의 비목표 (spec §12):
- 빌드 기준 검색·필터.
- 기존 패치 row 백필.
- 메타 export / 감사 리포트.
- Flyway 마이그레이션 도입.
