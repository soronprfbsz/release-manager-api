# 패치 생성 빌드 picker 재설계 구현 계획 (v2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 빌드 디렉토리를 진실의 원천으로 두는 새 전제 (commit `00ee8f8`) 위에서, 패치 생성 폼에 WEB 1개·엔진별 1개씩 선택 가능한 인라인 컴팩트 picker 와 builds-in-range API · `buildSelection` 입출력 · `PatchGenerationService` 재배선을 도입한다.

**Architecture:** `builds-in-range` API 는 빌드 디렉토리를 walk 하여 후보를 집계하고, `PatchGenerationService` 는 versions[] 루프에서 빌드를 skip 한 뒤 별도 단계에서 picker 입력만으로 `web/{engineName}/etc` 부분 복사를 수행한다. ETC 동행은 `selectedBuildIds` 오름차순 + REPLACE_EXISTING 으로 처리한다.

**Tech Stack:** Spring Boot 3.5.6 / Java 17 / JPA + QueryDSL / MapStruct / JUnit 5 + Mockito (BE) — React 19 + TypeScript / Vite / TanStack Query / shadcn-ui / Playwright (FE).

**연관 문서:** `release-manager-api/docs/superpowers/specs/2026-04-28-patch-creation-build-picker-design.md` (commit `7d18caf`).

**적용 범위:** 표준 패치 흐름 (`POST /api/patches/standard`, `PatchCreateForm`, `PatchGenerateFormCard`) 만. 커스텀 패치 (`POST /api/patches/custom`, `CustomPatchCreateForm`, `CustomPatchGenerateFormCard`) 는 현재 빌드 포함 분기 자체가 없어 변경하지 않음 (필요 시 후속 과제).

**WSL 환경 우회 패턴:** WSL 의 Java 는 21만 설치. 빌드 검증은 build.gradle 의 `JavaLanguageVersion.of(17)` 을 임시 21 로 바꿨다가 원복하는 패턴 사용. 매 검증 step 의 명령 블록에 그대로 포함되어 있다.

---

## 파일 구조

### 백엔드 — 변경 / 신규

| 경로 | 책임 |
| --- | --- |
| `src/main/java/com/ts/rm/domain/releaseversion/dto/ReleaseVersionDto.java` | `BuildCandidate`, `EngineGroup`, `HotfixInRangeInfo`, `BuildsInRangeResponse` 추가 |
| `src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustom.java` (+Impl) | `findBuildsInBaseRange`, `findHotfixesInBaseRange` 신규 |
| `src/main/java/com/ts/rm/domain/releaseversion/service/BuildsInRangeService.java` | **신규** — 디렉토리 walk 기반 후보 집계 |
| `src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionController.java` (+Docs) | `GET /api/releases/versions/builds-in-range` |
| `src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java` | `BuildSelection`, `SelectedEngine`, `IncludedBuilds`, `HotfixInRangeInfo`, `GenerateRequest` 변경, `GenerateResponse` 신설 |
| `src/main/java/com/ts/rm/domain/patch/controller/PatchController.java` (+Docs) | 새 입력으로 호출 + 새 응답 |
| `src/main/java/com/ts/rm/domain/patch/service/PatchService.java` | `generatePatchByVersion` / `generatePatch` 시그니처 변경 + 검증 룰 |
| `src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java` | 자동 통째 복사 분기 폐기, picker 입력 별도 단계, ETC 동행 오름차순, 응답 채움 |

### 프론트엔드 — 변경 / 신규

| 경로 | 책임 |
| --- | --- |
| `src/entities/releases/release/model/types.ts` | `BuildCandidate`, `EngineGroup`, `HotfixInRangeInfo`, `BuildsInRangeResponse` 추가 |
| `src/entities/releases/release/api/releaseApi.ts` | `getBuildsInRange` 호출 |
| `src/entities/releases/release/queries/releaseQueries.ts` | `useBuildsInRange` 훅 |
| `src/entities/patches/patch/model/types.ts` | `BuildSelection`, `SelectedEngine`, `GenerateRequest` 확장, response 필드 추가 |
| `src/features/patches/patch-management/model/types.ts` | `PatchCreateFormData` 에 `buildSelection` 추가 |
| `src/features/patches/patch-management/ui/BuildPickerSection.tsx` | **신규** — 컴팩트 picker (WEB radio + ENGINE 행) |
| `src/features/patches/patch-management/ui/PatchCreateForm.tsx` | 폼에 picker 통합 + 검증 + build-only 인디케이터 |
| `src/features/patches/patch-management/ui/PatchGenerateFormCard.tsx` | 동일 패턴 적용 |
| `src/pages/patches/PatchesPage.tsx` | `getVersionsFromTree` base 만 노출, 핫픽스 안내 toast |

---

## Phase 1 — 백엔드: `builds-in-range` API

### Task 1: ReleaseVersionDto 응답 record 추가

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/dto/ReleaseVersionDto.java`

> 단순 record 추가 — TDD 제외 (CLAUDE.md "TDD 제외 대상: 단순 DTO/Entity").

- [ ] **Step 1: ReleaseVersionDto 클래스 마지막 닫는 중괄호 위에 다음 record 들 추가**

```java
    // ========================================
    // builds-in-range Response DTOs
    // ========================================

    @Schema(description = "빌드 후보 1건")
    public record BuildCandidate(
            @Schema(description = "빌드 버전 ID", example = "42")
            Long buildVersionId,

            @Schema(description = "전체 버전 문자열", example = "1.1.0.260428")
            String fullVersion,

            @Schema(description = "생성 시각")
            LocalDateTime createdAt,

            @Schema(description = "후보 그룹 안에서 가장 최신인지 여부", example = "true")
            boolean isLatest
    ) {}

    @Schema(description = "엔진 후보 그룹")
    public record EngineGroup(
            @Schema(description = "엔진명 (engine/{engineName} 디렉토리명, 직속 파일이면 UNKNOWN)", example = "NC_SMS")
            String engineName,

            @Schema(description = "이 엔진을 가진 빌드 후보들")
            List<BuildCandidate> candidates
    ) {}

    @Schema(description = "범위 안의 핫픽스 메타정보")
    public record HotfixInRangeInfo(
            @Schema(description = "핫픽스 버전 ID", example = "33")
            Long versionId,

            @Schema(description = "전체 버전 문자열", example = "1.0.0.1")
            String fullVersion,

            @Schema(description = "핫픽스 버전 (4번째 자리)", example = "1")
            Integer hotfixVersion
    ) {}

    @Schema(description = "패치 범위 안의 빌드 후보 응답")
    public record BuildsInRangeResponse(
            @Schema(description = "WEB 후보 (없으면 빈 배열)")
            List<BuildCandidate> web,

            @Schema(description = "엔진별 후보 그룹 (엔진명 기준 정렬)")
            List<EngineGroup> engines,

            @Schema(description = "범위 안의 핫픽스 메타정보 (없으면 빈 배열)")
            List<HotfixInRangeInfo> hotfixesInRange
    ) {}
```

- [ ] **Step 2: 컴파일 검증**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: `BUILD SUCCESSFUL`. line 14 = `JavaLanguageVersion.of(17)`.

- [ ] **Step 3: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/dto/ReleaseVersionDto.java
git -C release-manager-api commit -m "feat: builds-in-range 응답용 DTO record 추가

BuildCandidate, EngineGroup, HotfixInRangeInfo, BuildsInRangeResponse 4개
record 를 ReleaseVersionDto 에 추가. 후속 task 에서 Repository / Service /
Controller 가 사용한다."
```

---

### Task 2: ReleaseVersionRepository — 빌드/핫픽스 범위 조회 + 단위 테스트

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustom.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryImpl.java`
- Test: 통합 테스트는 Phase 5 e2e 에서 다룸 (이 단계는 컴파일 + 컨트랙트 명세까지)

- [ ] **Step 1: Custom 인터페이스에 메서드 시그니처 추가**

`ReleaseVersionRepositoryCustom.java` 의 `findVersionsBetweenExcludingHotfixes` 시그니처 직후에 추가:

```java
    /**
     * 두 base 버전 사이의 빌드 행 (build_version > 0) 조회.
     *
     * <p>표준 패치는 customerId 가 null. 커스텀 패치는 해당 고객사의 빌드만 반환한다.
     * 결과는 build_version DESC 정렬.
     *
     * @param projectId    프로젝트 ID
     * @param fromBaseId   시작 base 버전 ID (포함)
     * @param toBaseId     종료 base 버전 ID (포함)
     * @param customerId   고객사 ID (null = 표준)
     * @return 빌드 ReleaseVersion 목록 (build_version DESC)
     */
    List<ReleaseVersion> findBuildsInBaseRange(String projectId, Long fromBaseId, Long toBaseId, Long customerId);

    /**
     * 두 base 버전 사이의 핫픽스 행 (hotfix_version > 0) 조회.
     *
     * @param projectId    프로젝트 ID
     * @param fromBaseId   시작 base 버전 ID (포함)
     * @param toBaseId     종료 base 버전 ID (포함)
     * @param customerId   고객사 ID (null = 표준)
     * @return 핫픽스 ReleaseVersion 목록 (hotfix_version ASC)
     */
    List<ReleaseVersion> findHotfixesInBaseRange(String projectId, Long fromBaseId, Long toBaseId, Long customerId);
```

- [ ] **Step 2: 컴파일 실패 확인**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -15
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 컴파일 에러 (`ReleaseVersionRepositoryImpl` 가 새 메서드를 구현하지 않음).

- [ ] **Step 3: Impl 에 QueryDSL 구현 추가**

`ReleaseVersionRepositoryImpl.java` 의 `findVersionsBetweenExcludingHotfixes` 메서드 직후에 추가 (`QReleaseVersion` 별칭은 기존 패턴 그대로 사용):

```java
    @Override
    public List<ReleaseVersion> findBuildsInBaseRange(String projectId, Long fromBaseId, Long toBaseId, Long customerId) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        BooleanExpression projectMatch = rv.project.projectId.eq(projectId);
        BooleanExpression isBuild = rv.buildVersion.gt(0);
        BooleanExpression baseRange = rv.buildBaseVersion.releaseVersionId.goe(fromBaseId)
                .and(rv.buildBaseVersion.releaseVersionId.loe(toBaseId));
        BooleanExpression customerMatch = (customerId == null)
                ? rv.customer.isNull()
                : rv.customer.customerId.eq(customerId);

        return queryFactory
                .selectFrom(rv)
                .where(projectMatch, isBuild, baseRange, customerMatch)
                .orderBy(rv.buildVersion.desc())
                .fetch();
    }

    @Override
    public List<ReleaseVersion> findHotfixesInBaseRange(String projectId, Long fromBaseId, Long toBaseId, Long customerId) {
        QReleaseVersion rv = QReleaseVersion.releaseVersion;

        BooleanExpression projectMatch = rv.project.projectId.eq(projectId);
        BooleanExpression isHotfix = rv.hotfixVersion.gt(0);
        BooleanExpression baseRange = rv.hotfixBaseVersion.releaseVersionId.goe(fromBaseId)
                .and(rv.hotfixBaseVersion.releaseVersionId.loe(toBaseId));
        BooleanExpression customerMatch = (customerId == null)
                ? rv.customer.isNull()
                : rv.customer.customerId.eq(customerId);

        return queryFactory
                .selectFrom(rv)
                .where(projectMatch, isHotfix, baseRange, customerMatch)
                .orderBy(rv.hotfixVersion.asc())
                .fetch();
    }
```

> Q 클래스 import 가 필요하면 `com.querydsl.core.types.dsl.BooleanExpression`, `com.ts.rm.domain.releaseversion.entity.QReleaseVersion` 을 추가한다 (Impl 의 다른 메서드도 같은 패턴).

- [ ] **Step 4: 컴파일 통과 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: `BUILD SUCCESSFUL`.

- [ ] **Step 5: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustom.java src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryImpl.java
git -C release-manager-api commit -m "feat: ReleaseVersionRepository 에 빌드/핫픽스 범위 조회 추가

findBuildsInBaseRange (build_version > 0, build_version DESC) 와
findHotfixesInBaseRange (hotfix_version > 0, hotfix_version ASC) 두 메서드를
QueryDSL 로 구현. customerId null 이면 표준, 값이 있으면 해당 고객사 필터.
후속 BuildsInRangeService 가 호출한다."
```

---

### Task 3: BuildsInRangeService — 디렉토리 walk 기반 후보 집계

**Files:**
- Create: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/service/BuildsInRangeService.java`
- Create: `release-manager-api/src/test/java/com/ts/rm/domain/releaseversion/service/BuildsInRangeServiceTest.java`

- [ ] **Step 1: 실패할 테스트 추가**

`BuildsInRangeServiceTest.java` 신규 생성:

```java
package com.ts.rm.domain.releaseversion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuildsInRangeService 테스트")
class BuildsInRangeServiceTest {

    @Mock ReleaseVersionRepository releaseVersionRepository;
    @Mock ReleaseVersionFileSystemService fileSystemService;

    @InjectMocks BuildsInRangeService service;

    @TempDir Path tempDir;

    private Project project(String id) {
        Project p = new Project();
        p.setProjectId(id);
        return p;
    }

    private ReleaseVersion build(Long id, ReleaseVersion base, int buildVersion, LocalDateTime createdAt) {
        ReleaseVersion v = new ReleaseVersion();
        v.setReleaseVersionId(id);
        v.setBuildBaseVersion(base);
        v.setBuildVersion(buildVersion);
        v.setCreatedAt(createdAt);
        v.setProject(base.getProject());
        v.setMajorVersion(base.getMajorVersion());
        v.setMinorVersion(base.getMinorVersion());
        v.setPatchVersion(base.getPatchVersion());
        return v;
    }

    private ReleaseVersion base(Long id) {
        ReleaseVersion v = new ReleaseVersion();
        v.setReleaseVersionId(id);
        v.setProject(project("p"));
        v.setMajorVersion(1);
        v.setMinorVersion(1);
        v.setPatchVersion(0);
        v.setBuildVersion(0);
        v.setHotfixVersion(0);
        return v;
    }

    private void touchFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }

    @Test
    @DisplayName("빌드 0개면 web/engines/hotfixesInRange 모두 빈 배열")
    void empty_returnsAllEmpty() {
        given(releaseVersionRepository.findBuildsInBaseRange("p", 1L, 9L, null)).willReturn(List.of());
        given(releaseVersionRepository.findHotfixesInBaseRange("p", 1L, 9L, null)).willReturn(List.of());

        ReleaseVersionDto.BuildsInRangeResponse resp = service.getBuildsInRange("p", 1L, 9L, null);

        assertThat(resp.web()).isEmpty();
        assertThat(resp.engines()).isEmpty();
        assertThat(resp.hotfixesInRange()).isEmpty();
    }

    @Test
    @DisplayName("web/, engine/{engineName}/, engine 직속 파일이 모두 후보로 분류됨")
    void scansBuildDirectoryAndGroupsCandidates() throws IOException {
        ReleaseVersion base = base(10L);
        ReleaseVersion b1 = build(101L, base, 260427, LocalDateTime.of(2026, 4, 27, 12, 0));
        ReleaseVersion b2 = build(102L, base, 260428, LocalDateTime.of(2026, 4, 28, 12, 0));

        Path d1 = tempDir.resolve("b1"); Path d2 = tempDir.resolve("b2");
        touchFile(d1.resolve("web/index.html"));
        touchFile(d1.resolve("engine/NC_SMS/x.jar"));
        touchFile(d2.resolve("web/index.html"));
        touchFile(d2.resolve("engine/NC_SMS/x.jar"));
        touchFile(d2.resolve("engine/NC_FAULT_MS/y.jar"));
        touchFile(d2.resolve("engine/loose.jar"));

        given(releaseVersionRepository.findBuildsInBaseRange("p", 10L, 10L, null)).willReturn(List.of(b2, b1));
        given(releaseVersionRepository.findHotfixesInBaseRange("p", 10L, 10L, null)).willReturn(List.of());
        given(fileSystemService.resolveBuildBasePath(base, 260427)).willReturn(d1);
        given(fileSystemService.resolveBuildBasePath(base, 260428)).willReturn(d2);

        ReleaseVersionDto.BuildsInRangeResponse resp = service.getBuildsInRange("p", 10L, 10L, null);

        // WEB: b2 가 최신 (build_version DESC 그대로)
        assertThat(resp.web()).extracting(ReleaseVersionDto.BuildCandidate::buildVersionId)
                .containsExactly(102L, 101L);
        assertThat(resp.web().get(0).isLatest()).isTrue();
        assertThat(resp.web().get(1).isLatest()).isFalse();

        // ENGINE 그룹: NC_SMS, NC_FAULT_MS, UNKNOWN (engine 직속 파일)
        assertThat(resp.engines()).extracting(ReleaseVersionDto.EngineGroup::engineName)
                .containsExactlyInAnyOrder("NC_SMS", "NC_FAULT_MS", "UNKNOWN");

        // NC_SMS 는 b1, b2 모두 보유
        ReleaseVersionDto.EngineGroup smsGroup = resp.engines().stream()
                .filter(g -> g.engineName().equals("NC_SMS")).findFirst().orElseThrow();
        assertThat(smsGroup.candidates()).extracting(ReleaseVersionDto.BuildCandidate::buildVersionId)
                .containsExactly(102L, 101L);

        // NC_FAULT_MS 는 b2 만
        ReleaseVersionDto.EngineGroup faultGroup = resp.engines().stream()
                .filter(g -> g.engineName().equals("NC_FAULT_MS")).findFirst().orElseThrow();
        assertThat(faultGroup.candidates()).extracting(ReleaseVersionDto.BuildCandidate::buildVersionId)
                .containsExactly(102L);

        // UNKNOWN 도 b2 만
        ReleaseVersionDto.EngineGroup unknownGroup = resp.engines().stream()
                .filter(g -> g.engineName().equals("UNKNOWN")).findFirst().orElseThrow();
        assertThat(unknownGroup.candidates()).extracting(ReleaseVersionDto.BuildCandidate::buildVersionId)
                .containsExactly(102L);
    }

    @Test
    @DisplayName("engine/{engineName}/ 디렉토리는 있지만 정규 파일이 0개면 후보에서 제외")
    void emptyEngineDirectory_excluded() throws IOException {
        ReleaseVersion base = base(20L);
        ReleaseVersion b1 = build(201L, base, 260427, LocalDateTime.of(2026, 4, 27, 12, 0));
        Path d1 = tempDir.resolve("b1");
        Files.createDirectories(d1.resolve("engine/NC_EMPTY"));
        touchFile(d1.resolve("engine/NC_FILLED/x.jar"));

        given(releaseVersionRepository.findBuildsInBaseRange("p", 20L, 20L, null)).willReturn(List.of(b1));
        given(releaseVersionRepository.findHotfixesInBaseRange("p", 20L, 20L, null)).willReturn(List.of());
        given(fileSystemService.resolveBuildBasePath(base, 260427)).willReturn(d1);

        ReleaseVersionDto.BuildsInRangeResponse resp = service.getBuildsInRange("p", 20L, 20L, null);

        assertThat(resp.engines()).extracting(ReleaseVersionDto.EngineGroup::engineName)
                .containsExactly("NC_FILLED");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인 (BuildsInRangeService 가 아직 없음)**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.releaseversion.service.BuildsInRangeServiceTest" 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 컴파일 에러 (`BuildsInRangeService` 가 없음).

- [ ] **Step 3: BuildsInRangeService 구현**

`BuildsInRangeService.java` 신규 생성:

```java
package com.ts.rm.domain.releaseversion.service;

import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 패치 범위 안의 빌드 후보를 빌드 디렉토리에서 직접 walk 하여 집계하는 서비스.
 *
 * <p>빌드 산출물의 진실의 원천은 빌드 디렉토리이며 (commit 00ee8f8), 본 서비스는
 * ReleaseFile 인덱스를 거치지 않고 디렉토리 안의 web/, engine/{engineName}/, engine 직속
 * 파일 존재 여부로 후보를 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildsInRangeService {

    private static final String UNKNOWN_ENGINE = "UNKNOWN";

    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseVersionFileSystemService fileSystemService;

    public ReleaseVersionDto.BuildsInRangeResponse getBuildsInRange(
            String projectId, Long fromBaseId, Long toBaseId, Long customerId) {

        List<ReleaseVersion> builds = releaseVersionRepository
                .findBuildsInBaseRange(projectId, fromBaseId, toBaseId, customerId);

        List<ReleaseVersionDto.BuildCandidate> webCandidates = new ArrayList<>();
        // engineName -> List<BuildCandidate>. LinkedHashMap 으로 첫 등장 순서를 유지하지 않고,
        // 마지막에 engineName 사전순으로 다시 정렬한다.
        Map<String, List<ReleaseVersionDto.BuildCandidate>> engineMap = new LinkedHashMap<>();

        for (ReleaseVersion bv : builds) {
            Path buildBasePath = fileSystemService.resolveBuildBasePath(
                    bv.getBuildBaseVersion(), bv.getBuildVersion());
            if (!Files.isDirectory(buildBasePath)) {
                continue;
            }

            ReleaseVersionDto.BuildCandidate candidate = new ReleaseVersionDto.BuildCandidate(
                    bv.getReleaseVersionId(),
                    bv.getFullVersion(),
                    bv.getCreatedAt(),
                    false  // isLatest 는 정렬 후 첫 항목에만 true
            );

            if (hasFiles(buildBasePath.resolve("web"))) {
                webCandidates.add(candidate);
            }

            for (String engineName : engineNamesInBuild(buildBasePath.resolve("engine"))) {
                engineMap.computeIfAbsent(engineName, k -> new ArrayList<>()).add(candidate);
            }
        }

        List<ReleaseVersionDto.BuildCandidate> webSorted = markLatestFirst(webCandidates);

        List<String> sortedEngineNames = new ArrayList<>(engineMap.keySet());
        sortedEngineNames.sort(Comparator.naturalOrder());
        List<ReleaseVersionDto.EngineGroup> engineGroups = new ArrayList<>();
        for (String name : sortedEngineNames) {
            engineGroups.add(new ReleaseVersionDto.EngineGroup(name, markLatestFirst(engineMap.get(name))));
        }

        List<ReleaseVersionDto.HotfixInRangeInfo> hotfixes = releaseVersionRepository
                .findHotfixesInBaseRange(projectId, fromBaseId, toBaseId, customerId).stream()
                .map(h -> new ReleaseVersionDto.HotfixInRangeInfo(
                        h.getReleaseVersionId(), h.getFullVersion(), h.getHotfixVersion()))
                .toList();

        return new ReleaseVersionDto.BuildsInRangeResponse(webSorted, engineGroups, hotfixes);
    }

    /**
     * 디렉토리 안에 정규 파일이 1개 이상 있는지 검사 (any subdirectory depth).
     */
    private boolean hasFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.walk(dir)) {
            return stream.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            log.warn("디렉토리 walk 실패 (false 로 처리): {}", dir, e);
            return false;
        }
    }

    /**
     * engine/ 디렉토리 안의 1단계 하위 디렉토리명 (정규 파일 1개 이상인 것만) 과,
     * engine/ 직속 정규 파일이 있으면 UNKNOWN 을 함께 반환.
     */
    private TreeSet<String> engineNamesInBuild(Path engineDir) {
        TreeSet<String> names = new TreeSet<>();
        if (!Files.isDirectory(engineDir)) {
            return names;
        }
        try (var stream = Files.list(engineDir)) {
            stream.forEach(child -> {
                if (Files.isDirectory(child) && hasFiles(child)) {
                    names.add(child.getFileName().toString());
                }
            });
        } catch (IOException e) {
            log.warn("engine 디렉토리 list 실패: {}", engineDir, e);
        }
        try (var stream = Files.list(engineDir)) {
            boolean directFile = stream.anyMatch(Files::isRegularFile);
            if (directFile) {
                names.add(UNKNOWN_ENGINE);
            }
        } catch (IOException e) {
            log.warn("engine 디렉토리 list 실패 (직속 파일): {}", engineDir, e);
        }
        return names;
    }

    /**
     * 입력 후보 리스트의 첫 항목에 isLatest=true 를 부여한 새 리스트를 반환.
     * 입력은 build_version DESC 정렬되어 있다고 가정한다 (Repository 가 보장).
     */
    private List<ReleaseVersionDto.BuildCandidate> markLatestFirst(List<ReleaseVersionDto.BuildCandidate> input) {
        if (input.isEmpty()) {
            return List.of();
        }
        List<ReleaseVersionDto.BuildCandidate> out = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            ReleaseVersionDto.BuildCandidate c = input.get(i);
            out.add(new ReleaseVersionDto.BuildCandidate(
                    c.buildVersionId(), c.fullVersion(), c.createdAt(), i == 0));
        }
        return out;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.releaseversion.service.BuildsInRangeServiceTest" 2>&1 | tail -15
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: 모든 테스트 PASS, line 14 = `JavaLanguageVersion.of(17)`.

- [ ] **Step 5: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/service/BuildsInRangeService.java src/test/java/com/ts/rm/domain/releaseversion/service/BuildsInRangeServiceTest.java
git -C release-manager-api commit -m "feat: BuildsInRangeService 신설 (디렉토리 walk 기반 후보 집계)

빌드 디렉토리의 web/, engine/{engineName}/, engine 직속 파일 존재 여부로
WEB / ENGINE 후보를 만들고 hotfixesInRange 를 분리 반환. 별도 인덱스 없이
디렉토리만 보고 후보를 만들어 빌드 산출물의 진실의 원천 = 디렉토리 원칙을
구현 측에서도 일관 적용."
```

---

### Task 4: ReleaseVersionController — `GET /api/releases/versions/builds-in-range`

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionController.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionControllerDocs.java`

> Controller 자체는 단순 위임이라 별도 Controller 단위 테스트는 작성하지 않음 (Service 단에서 검증). 통합 e2e 는 Phase 5.

- [ ] **Step 1: ControllerDocs 에 메서드 시그니처 추가**

`ReleaseVersionControllerDocs.java` 의 적절한 위치 (다른 GET 엔드포인트 docs 와 같은 그룹) 에 추가:

```java
    @Operation(
            summary = "빌드 후보 조회 (range)",
            description = "패치 범위 (fromVersionId..toVersionId) 안의 빌드 디렉토리를 walk 하여 "
                    + "WEB / ENGINE 후보와 hotfixesInRange 메타정보를 반환합니다. "
                    + "별도 인덱스 없이 빌드 디렉토리의 web/, engine/{engineName}/, engine 직속 파일 "
                    + "존재 여부로 후보를 만듭니다."
    )
    @GetMapping("/builds-in-range")
    ResponseEntity<ApiResponse<ReleaseVersionDto.BuildsInRangeResponse>> getBuildsInRange(
            @Parameter(description = "프로젝트 ID", required = true) @RequestParam String projectId,
            @Parameter(description = "시작 base 버전 ID (포함)", required = true) @RequestParam Long fromVersionId,
            @Parameter(description = "종료 base 버전 ID (포함)", required = true) @RequestParam Long toVersionId,
            @Parameter(description = "고객사 ID (커스텀인 경우)") @RequestParam(required = false) Long customerId
    );
```

> import 추가: `org.springframework.web.bind.annotation.GetMapping`, `org.springframework.web.bind.annotation.RequestParam` (이미 다른 docs 시그니처에서 사용 중이면 생략).

- [ ] **Step 2: Controller 에 의존성 + 핸들러 추가**

`ReleaseVersionController.java` 클래스 필드에 `BuildsInRangeService` 주입을 추가하고 핸들러를 추가:

```java
    private final BuildsInRangeService buildsInRangeService;

    @Override
    @GetMapping("/builds-in-range")
    public ResponseEntity<ApiResponse<ReleaseVersionDto.BuildsInRangeResponse>> getBuildsInRange(
            @RequestParam String projectId,
            @RequestParam Long fromVersionId,
            @RequestParam Long toVersionId,
            @RequestParam(required = false) Long customerId) {
        log.info("GET /api/releases/versions/builds-in-range - projectId: {}, range: {}..{}, customerId: {}",
                projectId, fromVersionId, toVersionId, customerId);
        ReleaseVersionDto.BuildsInRangeResponse response =
                buildsInRangeService.getBuildsInRange(projectId, fromVersionId, toVersionId, customerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
```

> `@RequiredArgsConstructor` 가 이미 클래스에 붙어 있다면 final 필드 추가만으로 의존성 주입이 됨 (기존 패턴).

- [ ] **Step 3: 컴파일 + 빠른 회귀 검증**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
./gradlew test --tests "com.ts.rm.domain.releaseversion.service.BuildsInRangeServiceTest" 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: 컴파일 + 기존 테스트 PASS.

- [ ] **Step 4: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionController.java src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionControllerDocs.java
git -C release-manager-api commit -m "feat: GET /api/releases/versions/builds-in-range 엔드포인트 추가

BuildsInRangeService 를 위임 호출하고 ApiResponse 로 래핑.
Swagger 문서화는 ReleaseVersionControllerDocs 에 작성."
```

---

## Phase 2 — 백엔드: 패치 생성 입출력

### Task 5: PatchDto 변경 — `BuildSelection`, `IncludedBuilds`, `GenerateRequest` 재배선

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java`

> 단순 record 변경 — TDD 제외.

- [ ] **Step 1: GenerateRequest 변경 + 신규 record 추가**

`PatchDto.java` 의 기존 `GenerateRequest` 를 다음으로 교체:

```java
    @Builder
    @Schema(description = "패치 생성 요청")
    public record GenerateRequest(
            @Schema(description = "프로젝트 ID", example = "infraeye2")
            @NotBlank(message = "프로젝트 ID는 필수입니다")
            @Size(max = 50, message = "프로젝트 ID는 50자 이하여야 합니다")
            String projectId,

            @Schema(description = "릴리즈 타입", example = "standard")
            @NotBlank(message = "릴리즈 타입은 필수입니다")
            String type,

            @Schema(description = "고객사 ID (커스텀인 경우)", example = "1")
            Long customerId,

            @Schema(description = "시작 버전", example = "1.0.0")
            @NotBlank(message = "시작 버전은 필수입니다")
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전 형식이 올바르지 않습니다 (예: 1.0.0)")
            String fromVersion,

            @Schema(description = "종료 버전", example = "1.1.1")
            @NotBlank(message = "종료 버전은 필수입니다")
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전 형식이 올바르지 않습니다 (예: 1.1.1)")
            String toVersion,

            @Schema(description = "생성자 이메일", example = "admin@tscientific")
            @NotBlank(message = "생성자 이메일은 필수입니다")
            @Size(max = 100, message = "생성자 이메일은 100자 이하여야 합니다")
            String createdByEmail,

            @Schema(description = "설명", example = "1.0.0에서 1.1.1로 업그레이드용 누적 패치")
            String description,

            @Schema(description = "패치 담당자 ID", example = "1")
            Long assigneeId,

            @Schema(description = "패치 이름 (미입력 시 자동 생성)", example = "20251125_1.0.0_1.1.1")
            @Size(max = 100, message = "패치 이름은 100자 이하여야 합니다")
            String patchName,

            @Schema(description = "빌드 파일 선택 (null 또는 enabled=false 면 빌드 미포함)")
            BuildSelection buildSelection
    ) {}

    @Builder
    @Schema(description = "패치에 포함할 빌드 파일 선택")
    public record BuildSelection(
            @Schema(description = "토글 ON 여부 (false 면 빌드 미포함)", example = "true")
            boolean enabled,

            @Schema(description = "WEB 선택 (null 이면 WEB 미포함)")
            SelectedWeb web,

            @Schema(description = "엔진별 선택 (미포함 엔진은 배열에서 제외)")
            java.util.List<SelectedEngine> engines
    ) {}

    @Schema(description = "WEB 빌드 선택")
    public record SelectedWeb(
            @Schema(description = "선택한 빌드 버전 ID", example = "42")
            @jakarta.validation.constraints.NotNull(message = "WEB buildVersionId 는 필수입니다")
            Long buildVersionId
    ) {}

    @Schema(description = "엔진 빌드 선택")
    public record SelectedEngine(
            @Schema(description = "엔진명", example = "NC_SMS")
            @NotBlank(message = "engineName 은 필수입니다")
            String engineName,

            @Schema(description = "선택한 빌드 버전 ID", example = "42")
            @jakarta.validation.constraints.NotNull(message = "engine buildVersionId 는 필수입니다")
            Long buildVersionId
    ) {}
```

> 기존 `Boolean includeAllBuildVersions` 필드와 `shouldIncludeAllBuildVersions()` 헬퍼는 삭제. 호출자는 후속 task 에서 정정한다.

- [ ] **Step 2: `IncludedBuilds`, `HotfixInRangeInfo`, `GenerateResponse` 추가**

`PatchDto.java` 의 Response DTOs 섹션 (`BatchDeleteResponse` 위 또는 아래) 에 추가:

```java
    @Schema(description = "패치에 실제 포함된 빌드 정보 (응답)")
    public record IncludedBuilds(
            @Schema(description = "WEB 포함 정보 (없으면 null)")
            IncludedWeb web,

            @Schema(description = "엔진별 포함 정보")
            java.util.List<IncludedEngine> engines
    ) {}

    @Schema(description = "패치에 포함된 WEB 빌드 정보")
    public record IncludedWeb(
            @Schema(description = "빌드 버전 ID", example = "42")
            Long buildVersionId,

            @Schema(description = "전체 버전 문자열", example = "1.1.0.260428")
            String fullVersion
    ) {}

    @Schema(description = "패치에 포함된 엔진 빌드 정보")
    public record IncludedEngine(
            @Schema(description = "엔진명", example = "NC_SMS")
            String engineName,

            @Schema(description = "빌드 버전 ID", example = "42")
            Long buildVersionId,

            @Schema(description = "전체 버전 문자열", example = "1.1.0.260428")
            String fullVersion
    ) {}

    @Schema(description = "범위 안의 핫픽스 메타정보 (응답)")
    public record HotfixInRangeInfo(
            @Schema(description = "핫픽스 버전 ID", example = "33")
            Long versionId,

            @Schema(description = "전체 버전 문자열", example = "1.0.0.1")
            String fullVersion
    ) {}

    @Schema(description = "패치 생성 응답")
    public record GenerateResponse(
            @Schema(description = "생성된 패치 ID", example = "1")
            Long patchId,

            @Schema(description = "패치 이름")
            String patchName,

            @Schema(description = "출력 경로")
            String outputPath,

            @Schema(description = "Build-only 패치 여부 (from == to)", example = "false")
            boolean isBuildOnly,

            @Schema(description = "범위 안의 핫픽스 (별도 적용 안내용, 비어있으면 빈 배열)")
            java.util.List<HotfixInRangeInfo> hotfixesInRange,

            @Schema(description = "패치에 실제 포함된 빌드 정보")
            IncludedBuilds includedBuilds
    ) {}
```

- [ ] **Step 3: 컴파일 실패 확인 (호출자에서 `includeAllBuildVersions` 사용)**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: PatchService / PatchController / PatchGenerationService 등에서 `includeAllBuildVersions` / `shouldIncludeAllBuildVersions` / 기존 GenerateRequest 사용처가 컴파일 실패. **이 컴파일 실패는 다음 task 들에서 해소된다.**

- [ ] **Step 4: 일단 커밋 (브로큰 빌드 지양 — 다음 task 에서 즉시 해소되므로 본 step 의 commit 은 보류)**

> 본 task 의 변경은 호출자 정정 없이 컴파일이 깨지므로 단독 커밋하지 않는다. **Step 3 의 검증 결과만 확인하고 다음 Task 6·7 의 정정 변경분을 함께 묶어 단일 커밋으로 처리한다.** Task 6 의 step 4 / Task 7 의 step 4 가 정정 후 빌드 통과를 확인한 직후, 본 task 의 변경분도 동일 add 묶음에 포함시켜 한 번에 커밋한다 (Task 7 step 4 의 git add 명령 참조).

---

### Task 6: PatchService — `generatePatchByVersion` / `generatePatch` 시그니처 변경 + 검증 룰

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchService.java`
- Test: `release-manager-api/src/test/java/com/ts/rm/domain/patch/service/PatchServiceValidationTest.java` (신규)

- [ ] **Step 1: 검증 룰 단위 테스트 작성**

`PatchServiceValidationTest.java` 신규 생성 (검증만 단독 검증, generatePatch 전체 흐름은 PatchGenerationServiceTest 가 담당):

```java
package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PatchService.validateBuildSelection 테스트")
class PatchServiceValidationTest {

    @Test
    @DisplayName("toggle ON 인데 web/engines 모두 비어있으면 INVALID_INPUT_VALUE")
    void enabledButEmpty_throws() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(true, null, List.of());
        assertThatThrownBy(() -> PatchService.validateBuildSelection(selection, /*sameBase*/ false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("from == to 인데 toggle OFF 면 INVALID_INPUT_VALUE")
    void sameBaseAndDisabled_throws() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(false, null, List.of());
        assertThatThrownBy(() -> PatchService.validateBuildSelection(selection, /*sameBase*/ true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("from == to + toggle ON + picker 비어있으면 INVALID_INPUT_VALUE")
    void sameBaseEnabledButEmpty_throws() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(true, null, List.of());
        assertThatThrownBy(() -> PatchService.validateBuildSelection(selection, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("정상: from != to + toggle OFF (DB only)")
    void disabledRangePatch_passes() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(false, null, List.of());
        PatchService.validateBuildSelection(selection, false);  // no exception
    }

    @Test
    @DisplayName("정상: null buildSelection + range patch")
    void nullSelectionRangePatch_passes() {
        PatchService.validateBuildSelection(null, false);
    }

    @Test
    @DisplayName("정상: from == to + toggle ON + picker 1개 이상")
    void sameBaseWithPicker_passes() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(
                true, new PatchDto.SelectedWeb(42L), List.of());
        PatchService.validateBuildSelection(selection, true);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchServiceValidationTest" 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 컴파일 에러 (`PatchService.validateBuildSelection` 미존재).

- [ ] **Step 3: PatchService 시그니처 변경 + validateBuildSelection 추가**

`PatchService.java` 에서:

(a) 기존 `generatePatchByVersion(...)` 의 마지막 인자를 변경:

```java
    @Transactional
    public Patch generatePatchByVersion(String projectId, String releaseType, Long customerId,
            String fromVersion, String toVersion, String createdByEmail, String description,
            Long engineerId, String patchName, PatchDto.BuildSelection buildSelection) {
        return patchGenerationService.generatePatchByVersion(
                projectId, releaseType, customerId, fromVersion, toVersion,
                createdByEmail, description, engineerId, patchName, buildSelection);
    }
```

(b) `generatePatch(...)` 의 마지막 인자도 동일하게 변경:

```java
    @Transactional
    public Patch generatePatch(String projectId, Long fromVersionId, Long toVersionId, Long customerId,
            String createdByEmail, String description, Long engineerId, String patchName,
            PatchDto.BuildSelection buildSelection) {
        return patchGenerationService.generatePatch(
                projectId, fromVersionId, toVersionId, customerId,
                createdByEmail, description, engineerId, patchName, buildSelection);
    }
```

(c) 클래스 안에 정적 검증 메서드 추가 (Javadoc 의 `@param ` 중복 사용 안 함):

```java
    /**
     * buildSelection 의 spec §4.3 검증 룰을 검사한다.
     *
     * @param selection  요청에서 받은 buildSelection (null 가능)
     * @param sameBase   from.id == to.id 여부 (Build-only 케이스)
     * @throws BusinessException INVALID_INPUT_VALUE 룰에 위배되면
     */
    public static void validateBuildSelection(PatchDto.BuildSelection selection, boolean sameBase) {
        boolean enabled = selection != null && selection.enabled();
        boolean pickerEmpty = selection == null
                || selection.web() == null && (selection.engines() == null || selection.engines().isEmpty());

        if (enabled && pickerEmpty) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "빌드 미포함이면 토글을 OFF 로 두십시오");
        }
        if (sameBase && (!enabled || pickerEmpty)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "동일 버전 패치는 최소 1개 이상의 빌드 선택이 필요합니다");
        }
    }
```

> import 추가: `com.ts.rm.domain.patch.dto.PatchDto` (이미 있음).

- [ ] **Step 4: 검증 테스트 통과 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchServiceValidationTest" 2>&1 | tail -15
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 6/6 PASS.

> 다른 호출자 (`PatchController`, `PatchGenerationService`) 의 컴파일 에러는 Task 7·8 에서 해소된다. 본 step 도 단독 커밋하지 않고 Task 7 의 step 4 commit 명령에 포함된다.

---

### Task 7: PatchController — 새 입력 / 새 응답으로 재배선

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/controller/PatchController.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/controller/PatchControllerDocs.java` (Swagger)

> Controller 단위 테스트는 별도 작성하지 않음 (Service 단의 검증 + 통합 e2e 가 책임).

- [ ] **Step 1: PatchController 의 표준 패치 생성 핸들러 정정**

`PatchController.java` 에서 표준 패치 생성 핸들러의 `patchService.generatePatchByVersion(...)` 호출부를 새 시그니처에 맞게 정정. 마지막 인자 `request.shouldIncludeAllBuildVersions()` → `request.buildSelection()` 으로 교체:

```java
        Patch patch = patchService.generatePatchByVersion(
                request.projectId(),
                request.type(),
                request.customerId(),
                request.fromVersion(),
                request.toVersion(),
                request.createdByEmail(),
                request.description(),
                request.assigneeId(),
                request.patchName(),
                request.buildSelection()
        );
```

- [ ] **Step 2: (응답 DTO 매핑은 본 task 에서 다루지 않음)**

`PatchGenerationService` 의 반환 타입은 Task 8 까지 `Patch` 그대로이고, Task 10 에서 `GenerateResult` record 로 확장된다. 따라서 본 task 의 Controller 는 **Step 1 의 인자만 정정** 하고, 응답은 기존 `Patch` 를 ApiResponse 로 래핑하는 현 코드를 그대로 둔다. `PatchDto.GenerateResponse` 매핑은 Task 10 의 step 4 에서 일괄 적용된다.

- [ ] **Step 3: PatchControllerDocs 정정 (Swagger 설명 갱신)**

`PatchControllerDocs.java` 에서 표준 패치 생성 메서드의 `@Operation.description` 안의 "WEB/ENGINE 모든 버전 포함" / `includeAllBuildVersions` 관련 문구를 다음과 같이 교체:

```java
    @Operation(
            summary = "패치 생성",
            description = "from..to 사이의 누적 DB 패치를 생성하고, buildSelection.enabled=true 인 경우 "
                    + "선택된 WEB/엔진 빌드와 함께 ETC 동행을 처리한다. 핫픽스는 누적 DB 시퀀스에 미포함."
    )
```

- [ ] **Step 4: Phase 2 통합 빌드 + 커밋**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava compileTestJava 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: **이 시점에는 PatchGenerationService 가 여전히 옛 시그니처라 컴파일 에러가 남는다.** 따라서 Task 6·7 의 변경분만 단독 커밋하지 않고 **Task 8 의 step 4 까지 한 번에 묶어서 커밋한다** (Task 8 의 step 5 명령 참조). 본 step 은 **컴파일 에러 메시지가 PatchGenerationService 에 한정되는지** 만 확인한다.

---

## Phase 3 — 백엔드: `PatchGenerationService` 재배선

### Task 8: 자동 통째 복사 분기 폐기 + versions[] 루프에서 빌드 skip

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java`
- Modify: `release-manager-api/src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java` (있으면 확장, 없으면 신규 — 이 plan 에서는 **신규** 가정. 본 task 가 첫 등장.)

- [ ] **Step 1: PatchGenerationService 의 시그니처 변경**

`PatchGenerationService.java` 의 두 진입점 시그니처를 `boolean includeAllBuildVersions` → `PatchDto.BuildSelection buildSelection` 으로 교체:

```java
    public Patch generatePatchByVersion(
            String projectId, String releaseType, Long customerId,
            String fromVersion, String toVersion, String createdByEmail, String description,
            Long engineerId, String patchName, PatchDto.BuildSelection buildSelection) {
        // 기존 본문에서 includeAllBuildVersions 사용처를 buildSelection 으로 통일.
        // ...
    }

    public Patch generatePatch(
            String projectId, Long fromVersionId, Long toVersionId, Long customerId,
            String createdByEmail, String description, Long engineerId, String patchName,
            PatchDto.BuildSelection buildSelection) {
        // 동일.
    }
```

> 두 진입점이 내부 헬퍼 (`copyReleaseFilesToOutput` 등) 를 호출할 때 인자 전달도 함께 정정.

- [ ] **Step 2: versions[] 루프 안의 빌드 자동 통째 복사 분기 제거**

`copyReleaseFilesToOutput` (또는 versions[] 루프가 있는 메서드) 의 다음 블록 (commit `00ee8f8` 에서 추가됐음) 을 **제거**:

```java
//   if (v.isBuild()) {
//       if (lastVersionIdForWeb == null && buildRootHasFiles(v, "web")) { ... }
//       if (lastVersionIdForEngineAll == null && buildRootHasFiles(v, "engine")) { ... }
//   }
```

및

```java
//   if (version.isBuild()) {
//       int copiedCount = copyBuildFilesFromFileSystem(version, outputDir);
//       ...
//       continue;
//   }
```

대신 단순히 **빌드 버전이면 versions[] 루프에서 skip**:

```java
            for (ReleaseVersion version : versions) {
                if (version.isBuild()) {
                    continue;  // 빌드는 picker 입력 단계에서 별도 처리 (§5.3 Q-S2)
                }
                // 이하 base 버전 처리 (DB 카테고리 / WEB / ENGINE / ETC 의 ReleaseFile 행 기반 복사) 그대로
                ...
            }
```

또한 lastVersionIdForEngineAll 변수와 그에 의존하던 분기 (`if (lastVersionIdForEngineAll != null && ...)`) 를 제거하고 base 만의 ENGINE sub_category 분기 (`lastVersionIdByEngineSubCategory`) 만 남긴다.

- [ ] **Step 3: 테스트 추가 / 확장**

`PatchGenerationServiceTest.java` 에 (없으면 신규 생성하고) 다음 테스트 추가:

```java
@Test
@DisplayName("buildSelection==null 이면 versions[] 루프에서 빌드 skip, outputDir 에 web/engine 없음")
void nullBuildSelection_buildSkippedFromVersionsLoop() throws IOException {
    // GIVEN: from~to 사이에 base 1개 + 빌드 1개. base 의 ReleaseFile 은 DB SQL 1개.
    // (테스트 setup 은 기존 PatchGenerationServiceTest 패턴을 참고; 모킹은 ReleaseFile,
    //  ReleaseVersionRepository, ReleaseVersionFileSystemService 를 그대로 쓴다.)

    // WHEN: generatePatch(..., null /* buildSelection */) 호출

    // THEN: outputDir 에 mariadb/ 또는 cratedb/ SQL 만 있고 web/, engine/, etc/ 디렉토리 없음.
    //       releaseVersionRepository 는 base 의 ReleaseFile 만 조회하고 빌드의 ReleaseFile 조회는 호출되지 않음
    //       (verify(releaseFileRepository, never()).findAllBy...(buildVersionId)).
}

@Test
@DisplayName("buildSelection.enabled=false 면 빌드 미포함")
void disabledBuildSelection_buildSkipped() throws IOException {
    PatchDto.BuildSelection sel = new PatchDto.BuildSelection(false, null, java.util.List.of());
    // 위와 동일한 검증.
}
```

> 정확한 mock 셋업은 기존 PatchGenerationServiceTest 의 setup 헬퍼를 활용; 신규 파일이면 `@ExtendWith(MockitoExtension.class) + @TempDir` 패턴으로 작성.

- [ ] **Step 4: 빌드 + 테스트 + 커밋 (Phase 2 + Task 8 단일 커밋)**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava compileTestJava 2>&1 | tail -10
./gradlew test --tests "com.ts.rm.domain.patch.service.*" --tests "com.ts.rm.domain.releaseversion.service.*" 2>&1 | tail -15
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: 컴파일 + 모든 테스트 PASS.

```
git -C release-manager-api add \
    src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java \
    src/main/java/com/ts/rm/domain/patch/service/PatchService.java \
    src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java \
    src/main/java/com/ts/rm/domain/patch/controller/PatchController.java \
    src/main/java/com/ts/rm/domain/patch/controller/PatchControllerDocs.java \
    src/test/java/com/ts/rm/domain/patch/service/PatchServiceValidationTest.java \
    src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java
git -C release-manager-api commit -m "feat: 패치 생성 입출력을 buildSelection 으로 재배선

- PatchDto: includeAllBuildVersions 제거, BuildSelection / SelectedWeb /
  SelectedEngine / IncludedBuilds / GenerateResponse 추가.
- PatchService: generatePatchByVersion / generatePatch 시그니처를
  PatchDto.BuildSelection 으로 교체, validateBuildSelection 정적 검증 추가.
- PatchController + Docs: 새 인자 사용으로 정정.
- PatchGenerationService: versions[] 루프에서 빌드 skip. 자동 통째 복사
  분기 (commit 00ee8f8 에서 임시로 들어갔던) 폐기. 빌드 처리는 후속 task
  에서 picker 입력 별도 단계로 추가될 예정."
```

---

### Task 9: PatchGenerationService — picker 입력 별도 단계 (web/{engineName}/etc 부분 복사)

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java`
- Modify: `release-manager-api/src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java`

- [ ] **Step 1: 테스트 추가**

`PatchGenerationServiceTest.java` 에 추가:

```java
@Test
@DisplayName("buildSelection.enabled=true: 선택된 WEB / engine/{engineName} / etc 만 복사")
void pickerSelection_partialCopy() throws IOException {
    // GIVEN: 빌드 v260427 의 web/foo.war, engine/NC_SMS/x.jar, engine/NC_FAULT_MS/y.jar, etc/note.txt
    //        빌드 v260428 의 web/foo.war, engine/NC_SMS/x.jar, engine/NC_FAULT_MS/y.jar, etc/note.txt
    //        picker: WEB=v260428, NC_SMS=v260427 (NC_FAULT_MS 미선택)

    // WHEN: generatePatch(..., new BuildSelection(true, new SelectedWeb(buildId(v260428)),
    //          List.of(new SelectedEngine("NC_SMS", buildId(v260427)))))

    // THEN: outputDir 에:
    //   web/foo.war       (v260428)
    //   engine/NC_SMS/x.jar (v260427)
    //   etc/note.txt      (Q-S3 로 v260428 의 내용 — Task 10 에서 검증, 본 task 에서는 존재 여부만)
    // outputDir 에 engine/NC_FAULT_MS 디렉토리 없음 (미선택).
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchGenerationServiceTest.pickerSelection_partialCopy" 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: FAIL — picker 분기가 아직 없음.

- [ ] **Step 3: PatchGenerationService 에 picker 단계 추가**

versions[] 루프 직후, 패치 디렉토리 정리 직전에 다음 단계 추가:

```java
            // ---- buildSelection 별도 단계 (spec §5.1 / Q-S2) ----
            if (buildSelection != null && buildSelection.enabled()) {
                applyBuildSelection(outputDir, buildSelection);
            }
```

그리고 같은 클래스에 헬퍼 메서드 추가:

```java
    /**
     * picker 입력에 따라 빌드 디렉토리에서 web/, engine/{engineName}/, etc/ 를 outputDir 로 복사.
     *
     * <p>spec §5.1 / Q-S3: ETC 는 selectedBuildIds 의 합집합을 buildVersion 오름차순으로 순차 복사하여
     * 같은 경로 충돌 시 큰 buildVersion 의 내용이 살아남게 한다.
     */
    private void applyBuildSelection(Path outputDir, PatchDto.BuildSelection sel) throws IOException {
        java.util.Set<Long> selectedBuildIds = new java.util.LinkedHashSet<>();

        // a. WEB 부분 복사
        if (sel.web() != null) {
            ReleaseVersion bv = loadBuildVersion(sel.web().buildVersionId());
            Path src = fileSystemService.resolveBuildBasePath(bv.getBuildBaseVersion(), bv.getBuildVersion()).resolve("web");
            copyDirectoryReplaceExisting(src, outputDir.resolve("web"));
            selectedBuildIds.add(bv.getReleaseVersionId());
        }

        // b. ENGINE 부분 복사
        if (sel.engines() != null) {
            for (PatchDto.SelectedEngine se : sel.engines()) {
                ReleaseVersion bv = loadBuildVersion(se.buildVersionId());
                Path src = fileSystemService.resolveBuildBasePath(bv.getBuildBaseVersion(), bv.getBuildVersion())
                        .resolve("engine").resolve(se.engineName());
                copyDirectoryReplaceExisting(src, outputDir.resolve("engine").resolve(se.engineName()));
                selectedBuildIds.add(bv.getReleaseVersionId());
            }
        }

        // c. ETC 동행: buildVersion 오름차순 순차 복사 (REPLACE_EXISTING)
        java.util.List<ReleaseVersion> sortedByBuildVersion = selectedBuildIds.stream()
                .map(this::loadBuildVersion)
                .sorted(java.util.Comparator.comparingInt(ReleaseVersion::getBuildVersion))
                .toList();
        for (ReleaseVersion bv : sortedByBuildVersion) {
            Path src = fileSystemService.resolveBuildBasePath(bv.getBuildBaseVersion(), bv.getBuildVersion()).resolve("etc");
            if (Files.isDirectory(src)) {
                copyDirectoryReplaceExisting(src, outputDir.resolve("etc"));
            }
        }
    }

    private ReleaseVersion loadBuildVersion(Long buildVersionId) {
        return releaseVersionRepository.findById(buildVersionId)
                .filter(ReleaseVersion::isBuild)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        "선택한 빌드 버전을 찾을 수 없습니다: " + buildVersionId));
    }

    /**
     * source 디렉토리 전체를 target 으로 복사 (REPLACE_EXISTING).
     * source 가 존재하지 않으면 no-op.
     */
    private void copyDirectoryReplaceExisting(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        try (var stream = Files.walk(source)) {
            stream.forEach(p -> {
                try {
                    Path rel = source.relativize(p);
                    Path dst = target.resolve(rel.toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new BuildFileCopyException(p, e);
                }
            });
        } catch (BuildFileCopyException e) {
            throw new IOException("빌드 파일 복사 실패: " + e.sourcePath, e);
        }
    }
```

> 기존 `copyBuildFilesFromFileSystem` 헬퍼는 호출처가 사라졌으므로 제거하거나, 위 `copyDirectoryReplaceExisting` 으로 일원화한다. `BuildFileCopyException` 내부 클래스는 그대로 보존 (위 헬퍼가 사용).

- [ ] **Step 4: 테스트 통과 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchGenerationServiceTest" 2>&1 | tail -15
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 모든 PatchGenerationServiceTest PASS.

- [ ] **Step 5: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java
git -C release-manager-api commit -m "feat: PatchGenerationService 에 picker 입력 별도 복사 단계 추가

versions[] 루프 직후 buildSelection.enabled=true 인 경우 applyBuildSelection
을 호출하여 web/, engine/{engineName}/, etc/ 를 빌드 디렉토리에서 직접
부분 복사. ETC 는 selectedBuildIds 오름차순 순차 복사로 큰 buildVersion 이
같은 경로 충돌 시 살아남게 한다."
```

---

### Task 10: ETC 충돌 검증 + 응답 (`includedBuilds`, `hotfixesInRange`, `isBuildOnly`) 채우기

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchService.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/controller/PatchController.java`
- Modify: `release-manager-api/src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java`

- [ ] **Step 1: 테스트 추가**

```java
@Test
@DisplayName("Q-S3: 두 빌드 모두 etc/note.txt 가 있으면 큰 buildVersion 의 내용이 살아남음")
void etcConflict_largerBuildVersionWins() throws IOException {
    // GIVEN: 빌드 v260427/etc/note.txt = "old", 빌드 v260428/etc/note.txt = "new"
    //        picker: NC_SMS=v260427, NC_FAULT_MS=v260428 (selectedBuildIds = {v260427, v260428})

    // WHEN: generatePatch(..., buildSelection)

    // THEN: outputDir/etc/note.txt 의 내용이 "new" 임 (v260428 의 내용).
}

@Test
@DisplayName("응답: includedBuilds, hotfixesInRange, isBuildOnly 채워짐")
void responseFields_populated() throws IOException {
    // GIVEN: from!=to 인 표준 패치, 범위 안에 핫픽스 1개, picker 로 WEB + 엔진 1개 선택

    // WHEN: generatePatch(...)

    // THEN: 반환된 PatchGenerationService 결과에서:
    //   includedBuilds.web().fullVersion()   == "1.1.0.260428"
    //   includedBuilds.engines() 의 size == 1 + engineName, fullVersion 매칭
    //   hotfixesInRange.size() == 1 + 핫픽스 메타정보 매칭
    //   isBuildOnly == false (from != to)
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchGenerationServiceTest" 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 두 신규 테스트 FAIL — `includedBuilds` 등 응답 필드 미채움.

- [ ] **Step 3: PatchGenerationService 가 풍부한 결과 record 를 반환하도록 변경**

내부 record 추가 (`Patch` + 응답 보강 필드):

```java
    public record GenerateResult(
            Patch patch,
            boolean isBuildOnly,
            java.util.List<PatchDto.HotfixInRangeInfo> hotfixesInRange,
            PatchDto.IncludedBuilds includedBuilds
    ) {}
```

`generatePatch(...)` 와 `generatePatchByVersion(...)` 의 반환 타입을 `Patch` → `GenerateResult` 로 변경하고, 끝부분에서 보강 필드를 채워 반환:

```java
        boolean isBuildOnly = (fromBaseId.equals(toBaseId));
        java.util.List<PatchDto.HotfixInRangeInfo> hotfixes = releaseVersionRepository
                .findHotfixesInBaseRange(projectId, fromBaseId, toBaseId, customerId).stream()
                .map(h -> new PatchDto.HotfixInRangeInfo(h.getReleaseVersionId(), h.getFullVersion()))
                .toList();
        PatchDto.IncludedBuilds includedBuilds = buildIncludedBuilds(buildSelection);
        return new GenerateResult(patch, isBuildOnly, hotfixes, includedBuilds);
```

`buildIncludedBuilds` 헬퍼 추가:

```java
    private PatchDto.IncludedBuilds buildIncludedBuilds(PatchDto.BuildSelection sel) {
        if (sel == null || !sel.enabled()) {
            return new PatchDto.IncludedBuilds(null, java.util.List.of());
        }
        PatchDto.IncludedWeb web = null;
        if (sel.web() != null) {
            ReleaseVersion bv = loadBuildVersion(sel.web().buildVersionId());
            web = new PatchDto.IncludedWeb(bv.getReleaseVersionId(), bv.getFullVersion());
        }
        java.util.List<PatchDto.IncludedEngine> engines = new java.util.ArrayList<>();
        if (sel.engines() != null) {
            for (PatchDto.SelectedEngine se : sel.engines()) {
                ReleaseVersion bv = loadBuildVersion(se.buildVersionId());
                engines.add(new PatchDto.IncludedEngine(se.engineName(), bv.getReleaseVersionId(), bv.getFullVersion()));
            }
        }
        return new PatchDto.IncludedBuilds(web, engines);
    }
```

- [ ] **Step 4: PatchService / PatchController 에서 결과 매핑 정정**

`PatchService.generatePatchByVersion` / `generatePatch` 의 반환 타입을 `Patch` → `PatchGenerationService.GenerateResult` 로 교체. `PatchController` 의 표준 패치 생성 핸들러를 다음과 같이 정정:

```java
        PatchGenerationService.GenerateResult result = patchService.generatePatchByVersion(
                request.projectId(), request.type(), request.customerId(),
                request.fromVersion(), request.toVersion(),
                request.createdByEmail(), request.description(),
                request.assigneeId(), request.patchName(),
                request.buildSelection());

        PatchDto.GenerateResponse body = new PatchDto.GenerateResponse(
                result.patch().getPatchId(),
                result.patch().getPatchName(),
                result.patch().getOutputPath(),
                result.isBuildOnly(),
                result.hotfixesInRange(),
                result.includedBuilds());

        return ResponseEntity.ok(ApiResponse.success(body));
```

> `PatchControllerDocs` 의 응답 타입도 `PatchDto.GenerateResponse` 로 정정.

- [ ] **Step 5: 빌드 + 테스트 + 커밋**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.*" --tests "com.ts.rm.domain.releaseversion.*" 2>&1 | tail -15
./gradlew clean build -x test 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: 모든 patch / releaseversion 테스트 PASS + `BUILD SUCCESSFUL` + line 14 = `JavaLanguageVersion.of(17)`.

```
git -C release-manager-api add \
    src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java \
    src/main/java/com/ts/rm/domain/patch/service/PatchService.java \
    src/main/java/com/ts/rm/domain/patch/controller/PatchController.java \
    src/main/java/com/ts/rm/domain/patch/controller/PatchControllerDocs.java \
    src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java
git -C release-manager-api commit -m "feat: 패치 생성 응답에 includedBuilds / hotfixesInRange / isBuildOnly 추가

PatchGenerationService 가 GenerateResult record 를 반환하도록 변경하여 응답
보강 정보 (포함된 WEB/엔진 빌드, 범위 안의 핫픽스, build-only 여부) 를 함께
전달. PatchController 가 이를 PatchDto.GenerateResponse 로 매핑하여
ApiResponse 로 래핑."
```

---

## Phase 4 — 프론트엔드

### Task 11: 엔티티 타입 추가 (releases + patches)

**Files:**
- Modify: `release-manager-web/src/entities/releases/release/model/types.ts`
- Modify: `release-manager-web/src/entities/patches/patch/model/types.ts`

- [ ] **Step 1: releases 엔티티 타입 추가**

`release-manager-web/src/entities/releases/release/model/types.ts` 끝에 추가:

```ts
// ---- builds-in-range API ----

export interface BuildCandidate {
  buildVersionId: number
  fullVersion: string
  createdAt: string
  isLatest: boolean
}

export interface EngineGroup {
  engineName: string
  candidates: BuildCandidate[]
}

export interface HotfixInRangeInfo {
  versionId: number
  fullVersion: string
  hotfixVersion: number
}

export interface BuildsInRangeResponse {
  web: BuildCandidate[]
  engines: EngineGroup[]
  hotfixesInRange: HotfixInRangeInfo[]
}
```

- [ ] **Step 2: patches 엔티티 타입 변경**

`release-manager-web/src/entities/patches/patch/model/types.ts` 의 `GenerateRequest` 에서 `includeAllBuildVersions` 를 제거하고 `buildSelection` 를 추가, 그리고 응답 타입 추가:

```ts
export interface SelectedWeb {
  buildVersionId: number
}

export interface SelectedEngine {
  engineName: string
  buildVersionId: number
}

export interface BuildSelection {
  enabled: boolean
  web: SelectedWeb | null
  engines: SelectedEngine[]
}

export interface GenerateRequest {
  projectId: string
  type: string
  customerId?: number | null
  fromVersion: string
  toVersion: string
  createdByEmail: string
  description?: string
  assigneeId?: number
  patchName?: string
  buildSelection?: BuildSelection | null
}

export interface IncludedWeb {
  buildVersionId: number
  fullVersion: string
}

export interface IncludedEngine {
  engineName: string
  buildVersionId: number
  fullVersion: string
}

export interface IncludedBuilds {
  web: IncludedWeb | null
  engines: IncludedEngine[]
}

export interface PatchHotfixInRangeInfo {
  versionId: number
  fullVersion: string
}

export interface GenerateResponse {
  patchId: number
  patchName: string
  outputPath: string
  isBuildOnly: boolean
  hotfixesInRange: PatchHotfixInRangeInfo[]
  includedBuilds: IncludedBuilds
}
```

- [ ] **Step 3: type-check**

```
cd release-manager-web
npm run type-check 2>&1 | tail -25
```

기대: `includeAllBuildVersions` 사용처 (PatchesPage / PatchCreateForm / PatchGenerateFormCard) 에서 type 에러. **이 에러는 Task 13~16 에서 해소된다. 본 task 단독 커밋은 보류.**

---

### Task 12: `useBuildsInRange` 훅 + `releaseApi.getBuildsInRange` 추가

**Files:**
- Modify: `release-manager-web/src/entities/releases/release/api/releaseApi.ts`
- Modify: `release-manager-web/src/entities/releases/release/queries/releaseQueries.ts`

- [ ] **Step 1: api 함수 추가**

`releaseApi.ts` 에 추가 (기존 호출 패턴, 즉 `axios.get` 래퍼를 그대로 사용):

```ts
import type { BuildsInRangeResponse } from "../model/types"

export const releaseApi = {
  // ...기존 메서드...

  getBuildsInRange: async (
    projectId: string,
    fromVersionId: number,
    toVersionId: number,
    customerId?: number | null,
  ): Promise<BuildsInRangeResponse> => {
    const params: Record<string, string | number> = {
      projectId,
      fromVersionId,
      toVersionId,
    }
    if (customerId != null) params.customerId = customerId
    const response = await axiosInstance.get("/api/releases/versions/builds-in-range", { params })
    return response.data.data
  },
}
```

> 기존 `releaseApi` 의 다른 메서드 시그니처를 정확히 모른다면 파일을 열어 동일한 기존 패턴 (`response.data.data` 추출 등) 을 그대로 따른다.

- [ ] **Step 2: 훅 추가**

`releaseQueries.ts` 에 추가:

```ts
import { useQuery } from "@tanstack/react-query"
import { releaseApi } from "../api/releaseApi"

export const useBuildsInRange = (
  projectId: string | null,
  fromVersionId: number | null,
  toVersionId: number | null,
  customerId?: number | null,
) => {
  return useQuery({
    queryKey: ["builds-in-range", projectId, fromVersionId, toVersionId, customerId ?? null],
    queryFn: () => releaseApi.getBuildsInRange(projectId!, fromVersionId!, toVersionId!, customerId),
    enabled: !!projectId && !!fromVersionId && !!toVersionId,
  })
}
```

- [ ] **Step 3: type-check**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
```

기대: 본 task 의 변경분만으로는 새 에러가 추가되지 않음. (Task 11 의 잔존 에러는 그대로.)

> 본 task 도 단독 커밋은 보류. Task 13 ~ 16 까지 한 번에 묶어 커밋.

---

### Task 13: 셀렉터 base-only 필터 — `PatchesPage.getVersionsFromTree`

**Files:**
- Modify: `release-manager-web/src/pages/patches/PatchesPage.tsx`

- [ ] **Step 1: `getVersionsFromTree` 가 base 만 반환하도록 정정**

`PatchesPage.tsx` 의 `getVersionsFromTree` 함수에서 빌드 (`buildVersion > 0`) / 핫픽스 (`hotfixVersion > 0`) 행을 제외:

```ts
function getVersionsFromTree(treeData: ReleaseVersionTreeNode[] | undefined): VersionOption[] {
  if (!treeData) return []
  // base 만 노출 (빌드/핫픽스 제외)
  return flatten(treeData)
    .filter((node) => (node.buildVersion ?? 0) === 0 && (node.hotfixVersion ?? 0) === 0)
    .map(toVersionOption)
}
```

> 정확한 트리 구조 (필드명) 는 기존 코드 패턴에 맞게 유지. 이 step 의 핵심은 buildVersion=0 + hotfixVersion=0 행만 통과시키는 것.

- [ ] **Step 2: `formData.includeAllBuildVersions` 사용처 제거 + `buildSelection` 초기값 추가**

`PatchesPage.tsx` 에서 폼 초기값 / dispatch 부분의 `includeAllBuildVersions: false` / `includeAllBuildVersions: standardFormData.includeAllBuildVersions || undefined` 등을 제거하고 `buildSelection: null` 로 대체:

```ts
const initialFormData: PatchCreateFormData = {
  // ...기존 필드...
  buildSelection: null,
}

// generate request 빌드 시:
const request: GenerateRequest = {
  // ...기존 필드...
  buildSelection: standardFormData.buildSelection ?? null,
}
```

- [ ] **Step 3: 핫픽스 안내 toast 준비 — 응답 hotfixesInRange 처리 분기 추가**

generate 성공 콜백에서 응답의 `hotfixesInRange` 가 비어있지 않으면 toast 표시:

```ts
onSuccess: (data) => {
  // ...기존 처리...
  if (data.hotfixesInRange.length > 0) {
    toast.warning(
      `이 범위에 핫픽스 ${data.hotfixesInRange.length}건이 있습니다. ` +
      `핫픽스는 버전 관리 화면에서 별도로 다운로드/적용해 주세요. (` +
      `${data.hotfixesInRange.map((h) => h.fullVersion).join(", ")})`,
    )
  }
},
```

> 기존 toast 라이브러리 (sonner / react-hot-toast 등) 는 프로젝트 패턴에 맞춰 사용.

- [ ] **Step 4: type-check + 단독 커밋 보류**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
```

기대: 본 task 의 변경분으로 셀렉터 / dispatch 측 에러는 해소되지만 PatchCreateForm / PatchGenerateFormCard 에서 여전히 `formData.includeAllBuildVersions` 참조가 남아 type 에러. Task 14~16 까지 묶어 한 번에 커밋.

---

### Task 14: BuildPickerSection 컴포넌트 신규

**Files:**
- Create: `release-manager-web/src/features/patches/patch-management/ui/BuildPickerSection.tsx`

- [ ] **Step 1: 컴포넌트 신규 작성**

```tsx
import * as React from "react"
import { RadioGroup, RadioGroupItem } from "@/shared/ui/radio-group"  // shadcn-ui 경로 (프로젝트 alias 에 맞게 조정)
import { Label } from "@/shared/ui/label"
import { Button } from "@/shared/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/shared/ui/dropdown-menu"
import type {
  BuildsInRangeResponse,
  BuildCandidate,
  EngineGroup,
} from "@/entities/releases/release/model/types"
import type { BuildSelection, SelectedEngine } from "@/entities/patches/patch/model/types"

interface BuildPickerSectionProps {
  data: BuildsInRangeResponse
  value: BuildSelection
  onChange: (next: BuildSelection) => void
  disabled?: boolean
}

const NONE = "__NONE__"

export function BuildPickerSection({ data, value, onChange, disabled }: BuildPickerSectionProps) {
  const setWeb = (buildVersionId: number | null) => {
    onChange({ ...value, web: buildVersionId == null ? null : { buildVersionId } })
  }
  const setEngine = (engineName: string, buildVersionId: number | null) => {
    const others = value.engines.filter((e) => e.engineName !== engineName)
    onChange({
      ...value,
      engines: buildVersionId == null ? others : [...others, { engineName, buildVersionId }],
    })
  }
  const selectAllLatest = () => {
    const web: BuildSelection["web"] =
      data.web.length > 0 ? { buildVersionId: data.web[0].buildVersionId } : null
    const engines: SelectedEngine[] = data.engines.map((g) => ({
      engineName: g.engineName,
      buildVersionId: g.candidates[0].buildVersionId,
    }))
    onChange({ ...value, web, engines })
  }
  const clearAll = () => onChange({ ...value, web: null, engines: [] })

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <span className="text-sm text-muted-foreground">
          {data.web.length === 0 && data.engines.length === 0
            ? "이 범위에 빌드가 없습니다"
            : "모두 최신 자동 선택됨"}
        </span>
        <div className="flex gap-2">
          <Button type="button" size="sm" variant="outline" onClick={selectAllLatest} disabled={disabled}>
            모두 최신
          </Button>
          <Button type="button" size="sm" variant="outline" onClick={clearAll} disabled={disabled}>
            모두 해제
          </Button>
        </div>
      </div>

      {data.web.length > 0 && (
        <section>
          <h4 className="mb-2 text-sm font-semibold">WEB</h4>
          <RadioGroup
            value={value.web ? String(value.web.buildVersionId) : NONE}
            onValueChange={(v) => setWeb(v === NONE ? null : Number(v))}
            disabled={disabled}
          >
            {data.web.map((c) => (
              <div key={c.buildVersionId} className="flex items-center gap-2">
                <RadioGroupItem id={`web-${c.buildVersionId}`} value={String(c.buildVersionId)} />
                <Label htmlFor={`web-${c.buildVersionId}`} className="cursor-pointer">
                  {c.fullVersion} {c.isLatest && <span className="ml-1 text-xs">✦최신</span>}
                </Label>
              </div>
            ))}
            <div className="flex items-center gap-2">
              <RadioGroupItem id="web-none" value={NONE} />
              <Label htmlFor="web-none" className="cursor-pointer">
                포함 안 함
              </Label>
            </div>
          </RadioGroup>
        </section>
      )}

      {data.engines.length > 0 && (
        <section>
          <h4 className="mb-2 text-sm font-semibold">ENGINE</h4>
          <ul className="flex flex-col gap-2">
            {data.engines.map((g) => (
              <EngineRow
                key={g.engineName}
                group={g}
                selectedBuildId={
                  value.engines.find((e) => e.engineName === g.engineName)?.buildVersionId ?? null
                }
                onChange={(bv) => setEngine(g.engineName, bv)}
                disabled={disabled}
              />
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

interface EngineRowProps {
  group: EngineGroup
  selectedBuildId: number | null
  onChange: (buildVersionId: number | null) => void
  disabled?: boolean
}

function EngineRow({ group, selectedBuildId, onChange, disabled }: EngineRowProps) {
  const selected: BuildCandidate | undefined =
    selectedBuildId == null
      ? undefined
      : group.candidates.find((c) => c.buildVersionId === selectedBuildId)

  return (
    <li className="grid grid-cols-[160px_1fr] items-center gap-2">
      <span className="text-sm">{group.engineName}</span>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button type="button" variant="outline" size="sm" disabled={disabled}>
            {selected ? (
              <>
                {selected.fullVersion}
                {selected.isLatest && <span className="ml-1 text-xs">✦최신</span>}
              </>
            ) : (
              "포함 안 함"
            )}
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {group.candidates.map((c) => (
            <DropdownMenuItem key={c.buildVersionId} onSelect={() => onChange(c.buildVersionId)}>
              {c.fullVersion}
              {c.isLatest && <span className="ml-1 text-xs">✦최신</span>}
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => onChange(null)}>포함 안 함</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </li>
  )
}
```

> shadcn-ui 컴포넌트 import 경로 (`@/shared/ui/...`) 는 프로젝트의 기존 alias 에 맞게 조정. 기존 컴포넌트들이 사용하는 패턴을 그대로 따른다 (`PatchCreateForm.tsx` 등의 import 를 참고).

- [ ] **Step 2: type-check**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
```

기대: 본 컴포넌트 자체는 type 통과. 호출자 (PatchCreateForm / PatchGenerateFormCard) 의 잔존 에러는 그대로.

> 단독 커밋 보류 — Task 16 의 step 4 에서 한 번에 커밋.

---

### Task 15: PatchCreateForm picker 통합

**Files:**
- Modify: `release-manager-web/src/features/patches/patch-management/ui/PatchCreateForm.tsx`
- Modify: `release-manager-web/src/features/patches/patch-management/model/types.ts`

- [ ] **Step 1: PatchCreateFormData 타입에 buildSelection 추가**

`features/patches/patch-management/model/types.ts` 의 `PatchCreateFormData` 에서 `includeAllBuildVersions` 를 제거하고 `buildSelection` 추가:

```ts
import type { BuildSelection } from "@/entities/patches/patch/model/types"

export interface PatchCreateFormData {
  // ...기존 필드 (projectId, fromVersionId, toVersionId, customerId, ... )...
  buildSelection: BuildSelection | null
}
```

- [ ] **Step 2: PatchCreateForm 폼 변경**

`PatchCreateForm.tsx` 의 기존 `includeAllBuildVersions` Checkbox / Switch 블록 (line 200~220 근처) 을 토글 + BuildPickerSection 으로 대체:

```tsx
import { BuildPickerSection } from "./BuildPickerSection"
import { useBuildsInRange } from "@/entities/releases/release/queries/releaseQueries"
import { Switch } from "@/shared/ui/switch"

// ...컴포넌트 내부...

const buildsQuery = useBuildsInRange(
  formData.projectId ?? null,
  formData.fromVersionId ?? null,
  formData.toVersionId ?? null,
  formData.customerId ?? null,
)

const toggleEnabled = formData.buildSelection?.enabled ?? false

const setEnabled = (next: boolean) => {
  if (!next) {
    onFormDataChange({ ...formData, buildSelection: { enabled: false, web: null, engines: [] } })
    return
  }
  // 토글 ON → 자동 preselect (모두 최신)
  const data = buildsQuery.data
  const web = data && data.web.length > 0 ? { buildVersionId: data.web[0].buildVersionId } : null
  const engines = (data?.engines ?? []).map((g) => ({
    engineName: g.engineName,
    buildVersionId: g.candidates[0].buildVersionId,
  }))
  onFormDataChange({ ...formData, buildSelection: { enabled: true, web, engines } })
}

// JSX:
<div className="flex flex-col gap-2">
  <div className="flex items-center justify-between">
    <Label htmlFor="buildToggle" className="cursor-pointer font-medium">
      빌드 파일 포함
    </Label>
    <Switch id="buildToggle" checked={toggleEnabled} onCheckedChange={setEnabled} />
  </div>
  {toggleEnabled && buildsQuery.data && (
    <BuildPickerSection
      data={buildsQuery.data}
      value={formData.buildSelection ?? { enabled: true, web: null, engines: [] }}
      onChange={(next) => onFormDataChange({ ...formData, buildSelection: next })}
    />
  )}

  {formData.fromVersionId === formData.toVersionId && (
    <p className="text-xs text-muted-foreground">
      ⓘ 빌드 전용 패치 — DB 스크립트 없이 빌드 파일만 생성됩니다
    </p>
  )}
</div>
```

- [ ] **Step 3: 클라이언트 검증 룰 추가**

`PatchCreateForm.tsx` 의 "패치 생성" 버튼 disabled 조건에 다음을 추가:

```ts
const sameBase =
  formData.fromVersionId != null && formData.fromVersionId === formData.toVersionId
const sel = formData.buildSelection
const pickerEmpty = !sel || sel.web == null && (sel.engines == null || sel.engines.length === 0)
const enabled = sel?.enabled ?? false

const submitDisabled =
  // 기존 룰 (fromId > toId, fromId/toId 미선택 등) ...
  (enabled && pickerEmpty) || (sameBase && (!enabled || pickerEmpty))
```

- [ ] **Step 4: type-check**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
```

기대: 본 task 의 변경분으로 PatchCreateForm 의 `includeAllBuildVersions` 잔존 참조가 해소됨. PatchGenerateFormCard 의 잔존 에러만 남음.

> 단독 커밋 보류 — Task 16 의 step 4 에서 한 번에 커밋.

---

### Task 16: PatchGenerateFormCard picker 통합 + 프론트엔드 통합 커밋

**Files:**
- Modify: `release-manager-web/src/features/patches/patch-management/ui/PatchGenerateFormCard.tsx`

- [ ] **Step 1: 동일 폼 블록 적용**

`PatchGenerateFormCard.tsx` 의 `includeAllBuildVersions` 사용처 (line 200~220 근처) 를 다음 블록으로 교체. (Task 15 와 동일한 코드. 두 컴포넌트의 `PatchCreateFormData` 가 같으므로 폼 블록도 동일.)

```tsx
import { BuildPickerSection } from "./BuildPickerSection"
import { useBuildsInRange } from "@/entities/releases/release/queries/releaseQueries"
import { Switch } from "@/shared/ui/switch"

// ...컴포넌트 내부...

const buildsQuery = useBuildsInRange(
  formData.projectId ?? null,
  formData.fromVersionId ?? null,
  formData.toVersionId ?? null,
  formData.customerId ?? null,
)

const toggleEnabled = formData.buildSelection?.enabled ?? false

const setEnabled = (next: boolean) => {
  if (!next) {
    onFormDataChange({ ...formData, buildSelection: { enabled: false, web: null, engines: [] } })
    return
  }
  const data = buildsQuery.data
  const web = data && data.web.length > 0 ? { buildVersionId: data.web[0].buildVersionId } : null
  const engines = (data?.engines ?? []).map((g) => ({
    engineName: g.engineName,
    buildVersionId: g.candidates[0].buildVersionId,
  }))
  onFormDataChange({ ...formData, buildSelection: { enabled: true, web, engines } })
}

// JSX:
<div className="flex flex-col gap-2">
  <div className="flex items-center justify-between">
    <Label htmlFor="buildToggleCard" className="cursor-pointer font-medium">
      빌드 파일 포함
    </Label>
    <Switch id="buildToggleCard" checked={toggleEnabled} onCheckedChange={setEnabled} />
  </div>
  {toggleEnabled && buildsQuery.data && (
    <BuildPickerSection
      data={buildsQuery.data}
      value={formData.buildSelection ?? { enabled: true, web: null, engines: [] }}
      onChange={(next) => onFormDataChange({ ...formData, buildSelection: next })}
    />
  )}
  {formData.fromVersionId === formData.toVersionId && (
    <p className="text-xs text-muted-foreground">
      ⓘ 빌드 전용 패치 — DB 스크립트 없이 빌드 파일만 생성됩니다
    </p>
  )}
</div>
```

검증 룰 (Task 15 step 3 의 disabled 조건) 도 동일하게 적용:

```ts
const sameBase =
  formData.fromVersionId != null && formData.fromVersionId === formData.toVersionId
const sel = formData.buildSelection
const pickerEmpty = !sel || sel.web == null && (sel.engines == null || sel.engines.length === 0)
const enabled = sel?.enabled ?? false

const submitDisabled =
  // 기존 룰 ...
  (enabled && pickerEmpty) || (sameBase && (!enabled || pickerEmpty))
```

> 두 컴포넌트의 차이는 wrapper (Sheet vs Card) 와 footer 위치뿐이므로 폼 블록 자체는 동일하게 둔다. 만약 Task 15 작업 중에 자동 preselect 로직을 별도 헬퍼 (`computeAutoPreselect(data): BuildSelection` 등) 로 추출했다면 본 task 에서도 같은 헬퍼를 재사용한다.

- [ ] **Step 2: type-check + 빌드**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
npm run build 2>&1 | tail -10
```

기대: type 에러 0개 + production 빌드 성공.

- [ ] **Step 3: 프론트엔드 통합 커밋 (Task 11 ~ Task 16)**

```
git -C release-manager-web add \
    src/entities/releases/release/model/types.ts \
    src/entities/releases/release/api/releaseApi.ts \
    src/entities/releases/release/queries/releaseQueries.ts \
    src/entities/patches/patch/model/types.ts \
    src/features/patches/patch-management/model/types.ts \
    src/features/patches/patch-management/ui/BuildPickerSection.tsx \
    src/features/patches/patch-management/ui/PatchCreateForm.tsx \
    src/features/patches/patch-management/ui/PatchGenerateFormCard.tsx \
    src/pages/patches/PatchesPage.tsx
git -C release-manager-web commit -m "feat: 패치 생성 폼에 BuildPickerSection 통합

- entities: BuildsInRangeResponse / BuildCandidate / EngineGroup 추가,
  BuildSelection / SelectedEngine / GenerateResponse 추가.
- useBuildsInRange 훅 + releaseApi.getBuildsInRange.
- PatchesPage: getVersionsFromTree 가 base (build_version=0 && hotfix_version=0)
  만 노출. 응답 hotfixesInRange 가 있으면 toast 안내.
- BuildPickerSection 신규 컴포넌트 (WEB radio + ENGINE 행 + 일괄 액션).
- PatchCreateForm / PatchGenerateFormCard: 토글 + picker + 검증 룰
  + build-only 인디케이터 통합. includeAllBuildVersions 폼 필드 제거."
```

---

## Phase 5 — 통합 검증

### Task 17: 백엔드/프론트엔드 통합 빌드 + 수동 시나리오 검증

**Files:** (검증만, 코드 변경 없음)

- [ ] **Step 1: 백엔드 풀빌드 + 전체 테스트**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew clean build 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle | head -1
```

기대: `BUILD SUCCESSFUL` + 모든 테스트 PASS + line 14 = `JavaLanguageVersion.of(17)`.

- [ ] **Step 2: 프론트엔드 풀빌드**

```
cd release-manager-web
npm run type-check && npm run build 2>&1 | tail -15
```

기대: type-check 통과 + production build 성공.

- [ ] **Step 3: 수동 시나리오 검증 (운영 / dev 환경)**

다음을 운영자/사용자 확인 명세로 README 또는 릴리즈 노트에 적고 직접 시나리오 검증:

1. **DB only 패치**: `from=1.0.0, to=1.1.0`, 토글 OFF → 결과 ZIP 에 `web/` `engine/` `etc/` 없음, `mariadb/` 또는 `cratedb/` 만.
2. **부분 빌드 포함 패치**: 위 범위에서 토글 ON, NC_SMS 만 picker 로 1개 선택, 나머지 "포함 안 함" → 결과 ZIP 에 `engine/NC_SMS/...` 만, `engine/NC_FAULT_MS/` 없음.
3. **Build-only 패치**: `from=1.1.0, to=1.1.0`, 토글 ON, WEB 1개 선택 → 결과 ZIP 에 SQL 0개 + `web/` 산출.
4. **응답 hotfixesInRange**: 핫픽스가 있는 범위로 패치 생성 → 응답에 `hotfixesInRange` 비어있지 않음 + UI toast 노출 확인.
5. **검증 룰**: 토글 ON + picker 모두 미포함 → 서버가 `INVALID_INPUT_VALUE` 로 거부 (UI 의 "패치 생성" 버튼은 그 전에 disabled).

- [ ] **Step 4: (선택) Playwright e2e**

```
cd release-manager-web
# 테스트가 이미 정의되어 있다면 실행:
# npm run e2e -- --grep "build picker"
```

> e2e 시나리오 신규 작성은 본 plan 의 비목표. 운영 검증을 통해 동작 확인이 우선.

- [ ] **Step 5: 검증 종료 (커밋 없음)**

본 task 는 코드 변경 없이 검증만 수행. 모든 검증 통과 시 plan 완료.

---

## 부록 A — 폐기되는 항목 명시

직전 spec/plan (2026-04-28 초안) 의 다음 단계는 본 plan 에서 **명시적으로 폐기**된다 (commit `7d18caf` 의 spec 재설계 반영):

- `BuildFileService.extractEngineName` 헬퍼 추가 — **폐기** (빌드는 ReleaseFile 행을 만들지 않음).
- 빌드 ZIP 업로드 시 `subCategory` 자동 저장 — **폐기**.
- 데이터 보정 SQL (release_file 의 sub_category 백필) — **폐기**.
- `PatchGenerationService` 의 자동 통째 복사 분기 (commit `00ee8f8` 에서 임시 도입) — Task 8 에서 제거.

본 plan 의 17 task 만으로 spec §11 의 모든 청크를 커버한다.

## 부록 B — 본 plan 의 비목표 (테스트 누락)

spec §10 에 명시된 다음 항목은 **본 plan 의 17 task 에 포함되지 않는다** (별도 후속 사이클로 분리):

- 프론트엔드 단위 테스트: `PatchCreateForm.test`, `BuildPickerSection.test`. 본 plan 의 백엔드 단위 테스트와 Task 17 의 수동 시나리오 검증으로 핵심 동작은 보장되며, 컴포넌트 단위 테스트는 후속 task 에서 별도 커버.
- Playwright e2e 신규 시나리오: 본 plan 은 Task 17 의 수동 검증으로 갈음. 자동화 e2e 추가가 필요하면 별도 plan.

이 두 항목이 본 plan 완료 후 곧바로 필요하다면, 17 task 완료 직후 새 brainstorming → plan 사이클로 분리 진행을 권장.
