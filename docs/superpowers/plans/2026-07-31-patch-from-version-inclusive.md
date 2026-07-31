# 패치 생성 From 버전 Inclusive 전환 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 패치 생성 폼의 From 자동 제안을 "사이트 현재 버전의 직후" 에서 "사이트 현재 버전 자신" 으로 바꿔, 이미 적용된 버전에 사후 추가된 빌드/파일이 다음 패치에 실리게 한다.

**Architecture:** 패치 산출물 수집 코드(`findBuildsInBaseRange`, `copySqlFiles`, `findReleaseFilesBetweenVersions`)는 이미 전부 `from` inclusive 다. 따라서 **수집 로직은 건드리지 않고**, 제안값을 산출하는 `SiteVersionService.getNextPatchRange()` 의 비교 연산자 하나와, `from == to` 를 막고 있던 프론트 조건 두 곳만 고친다.

**Tech Stack:** Spring Boot 3.5.6 / Java 17 / JUnit5 + Mockito + AssertJ / QueryDSL / React 19 + TypeScript

**설계 문서:** `release-manager-api/docs/superpowers/specs/2026-07-31-patch-from-version-inclusive-design.md`

## Global Constraints

- 커밋 메시지는 **한글 conventional** 형식. `Co-Authored-By: Claude` 표기 **금지**.
- `main` 에 직접 커밋한다 (브랜치 생성하지 않음).
- 백엔드 명령은 **WSL2 셸**에서 실행. `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.
- 백엔드 작업 디렉토리: `/mnt/c/Soronprfbs/project/release-manager/release-manager-api`
- 프론트 작업 디렉토리: `/mnt/c/Soronprfbs/project/release-manager/release-manager-web`
- **DB 스키마 변경 없음.** Flyway 마이그레이션을 추가하지 않는다.
- 기존 코드 스타일을 따른다. 인접 코드를 "개선" 하지 않는다.

## File Structure

| 파일 | 역할 | 변경 |
|------|------|------|
| `src/main/java/com/ts/rm/domain/site/service/SiteVersionService.java` | 다음 패치 범위 제안 산출 | L231 비교 연산자 + L189 JavaDoc |
| `src/test/java/com/ts/rm/domain/site/service/SiteVersionServiceTest.java` | 위 서비스 단위 테스트 | 테스트 4건 + 헬퍼 1개 추가 |
| `src/test/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustomTest.java` | 범위 조회 리포지토리 테스트 | 테스트 2건 + 헬퍼 1개 추가 (프로덕션 변경 없음 — 전제 고정용) |
| `src/features/patches/patch-management/ui/PatchCreateForm.tsx` | 패치 생성 폼 | L156 조건 + L240 안내 문구 분기 |

---

### Task 1: From 제안을 현재 버전 포함으로 변경

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/site/service/SiteVersionService.java:189`, `:231`
- Test: `release-manager-api/src/test/java/com/ts/rm/domain/site/service/SiteVersionServiceTest.java`

**Interfaces:**
- Consumes: `SiteVersionService.getNextPatchRange(Long siteId, String projectId)` → `SiteVersionDto.NextPatchRangeResponse`
- Produces: `NextPatchRangeResponse` 의 `suggestedFromVersion` / `suggestedFromVersionId` 가 사이트 현재 버전과 같아질 수 있다. 사이트가 최신이면 `suggestedFromVersion.equals(suggestedToVersion)` 이 되고 **더 이상 `null` 이 아니다.** Task 3 의 프론트 분기가 이 계약에 의존한다.

`NextPatchRangeResponse` 는 record 이며 생성자 순서는
`(currentVersion, suggestedFromVersion, suggestedFromVersionId, suggestedToVersion, suggestedToVersionId)` 이다.

- [ ] **Step 1: 실패하는 테스트 4건을 작성한다**

`SiteVersionServiceTest.java` 의 마지막 테스트(`clearBySiteAndProject_delegates`) 뒤, 닫는 `}` 앞에 아래를 추가한다.

```java
    // ========== getNextPatchRange ==========

    @Test
    @DisplayName("getNextPatchRange — 사이트 1.1.13 / 최신 1.1.15 → from 은 현재 버전 자신(1.1.13)")
    void getNextPatchRange_fromIsCurrentBase() {
        givenSiteAndVersions("1.1.13", "1.1.11", "1.1.13", "1.1.14", "1.1.15");

        SiteVersionDto.NextPatchRangeResponse res = service.getNextPatchRange(1L, "PRJ");

        assertThat(res.currentVersion()).isEqualTo("1.1.13");
        assertThat(res.suggestedFromVersion()).isEqualTo("1.1.13");
        assertThat(res.suggestedToVersion()).isEqualTo("1.1.15");
    }

    @Test
    @DisplayName("getNextPatchRange — 사이트가 이미 최신이면 from == to (기존에는 from 이 null)")
    void getNextPatchRange_alreadyLatest_fromEqualsTo() {
        givenSiteAndVersions("1.1.15", "1.1.13", "1.1.14", "1.1.15");

        SiteVersionDto.NextPatchRangeResponse res = service.getNextPatchRange(1L, "PRJ");

        assertThat(res.suggestedFromVersion()).isEqualTo("1.1.15");
        assertThat(res.suggestedToVersion()).isEqualTo("1.1.15");
        assertThat(res.suggestedFromVersionId()).isEqualTo(res.suggestedToVersionId());
    }

    @Test
    @DisplayName("getNextPatchRange — 패치 이력 없으면 가장 오래된 버전 (기존 동작 유지)")
    void getNextPatchRange_noHistory_oldestVersion() {
        givenSiteAndVersions(null, "1.1.13", "1.1.14", "1.1.15");

        SiteVersionDto.NextPatchRangeResponse res = service.getNextPatchRange(1L, "PRJ");

        assertThat(res.currentVersion()).isNull();
        assertThat(res.suggestedFromVersion()).isEqualTo("1.1.13");
    }

    @Test
    @DisplayName("getNextPatchRange — 현재 버전이 승인 목록에 없으면 직후 버전으로 degrade")
    void getNextPatchRange_currentBaseMissing_fallsBackToNext() {
        givenSiteAndVersions("1.1.12", "1.1.11", "1.1.13", "1.1.15");

        SiteVersionDto.NextPatchRangeResponse res = service.getNextPatchRange(1L, "PRJ");

        assertThat(res.suggestedFromVersion()).isEqualTo("1.1.13");
    }

    /**
     * getNextPatchRange 용 공통 stub.
     *
     * @param lastPatchedVersion 사이트의 last_patched_version (null 이면 패치 이력 없음)
     * @param versions           프로젝트의 승인된 표준 base 버전들 (오름차순으로 주지 않아도 서비스가 정렬)
     */
    private void givenSiteAndVersions(String lastPatchedVersion, String... versions) {
        when(siteProjectRepository.findBySite_SiteIdAndProject_ProjectId(1L, "PRJ"))
                .thenReturn(Optional.of(SiteProject.builder()
                        .lastPatchedVersion(lastPatchedVersion)
                        .build()));

        List<ReleaseVersion> rows = new ArrayList<>();
        long id = 1L;
        for (String v : versions) {
            String[] p = v.split("\\.");
            rows.add(ReleaseVersion.builder()
                    .releaseVersionId(id++)
                    .version(v)
                    .majorVersion(Integer.parseInt(p[0]))
                    .minorVersion(Integer.parseInt(p[1]))
                    .patchVersion(Integer.parseInt(p[2]))
                    .build());
        }
        when(releaseVersionRepository
                .findAllByProject_ProjectIdAndReleaseTypeAndIsApproved("PRJ", "STANDARD", true))
                .thenReturn(rows);
    }
```

같은 파일 상단 import 에 아래를 추가한다 (기존 import 블록의 알파벳 순서를 지킬 것).

```java
import com.ts.rm.domain.site.dto.SiteVersionDto;
import com.ts.rm.domain.site.entity.SiteProject;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import java.util.ArrayList;
```

`ReleaseVersion` 의 `buildVersion` / `hotfixVersion` 은 `@Builder.Default` 로 `0` 이라
`isBuild()` / `isHotfix()` 가 모두 `false` 이므로 서비스의 필터를 그대로 통과한다.

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

```bash
cd /mnt/c/Soronprfbs/project/release-manager/release-manager-api
./gradlew test --tests '*SiteVersionServiceTest' -i
```

Expected: `getNextPatchRange_fromIsCurrentBase` 는 `expected "1.1.13" but was "1.1.14"` 로 FAIL,
`getNextPatchRange_alreadyLatest_fromEqualsTo` 는 `expected "1.1.15" but was null` 로 FAIL.
나머지 2건은 PASS (기존 동작이라 회귀 방지용).

**중요:** 4건 중 2건이 실패해야 한다. 4건 모두 통과하면 stub 이 서비스를 타지 않은 것이므로 멈추고 원인을 확인할 것.

- [ ] **Step 3: 비교 연산자를 바꾼다**

`SiteVersionService.java:231` — `> 0` 을 `>= 0` 으로.

```java
            fromCandidate = baseVersions.stream()
                    .filter(v -> compareBase(v.getVersion(), currentBase) >= 0)
                    .findFirst()
                    .orElse(null); // 이미 최신 → null
```

`.orElse(null)` 뒤 주석은 더 이상 사실이 아니므로 아래로 교체한다.

```java
                    .orElse(null); // currentBase 가 승인 목록에 없고 그보다 큰 버전도 없음
```

- [ ] **Step 4: JavaDoc 을 실제 동작에 맞게 갱신한다**

`SiteVersionService.java:189` 의 한 줄을 교체한다.

기존:
```java
     *   <li>suggestedFrom: currentVersion 직후 버전. 없으면(최신 상태) null</li>
```

변경:
```java
     *   <li>suggestedFrom: currentVersion 자신(inclusive). 사이트가 이미 최신이면 suggestedTo 와 같아진다.
     *       이미 적용된 버전에 사후 추가된 빌드/파일을 다음 패치가 회수하도록 하한을 포함시킨다.</li>
```

- [ ] **Step 5: 테스트를 실행해 4건 모두 통과를 확인한다**

```bash
cd /mnt/c/Soronprfbs/project/release-manager/release-manager-api
./gradlew test --tests '*SiteVersionServiceTest'
```

Expected: 기존 3건 + 신규 4건 = 7건 PASS.

- [ ] **Step 6: 전체 테스트로 회귀를 확인한다**

```bash
cd /mnt/c/Soronprfbs/project/release-manager/release-manager-api
./gradlew test
```

Expected: BUILD SUCCESSFUL. 실패가 나오면 그 테스트가 "from 은 현재 버전 다음" 을 전제로
했던 것인지 확인하고, 전제가 바뀐 것이면 테스트를 새 계약에 맞게 고친다.

- [ ] **Step 7: 커밋**

```bash
cd /mnt/c/Soronprfbs/project/release-manager/release-manager-api
git add src/main/java/com/ts/rm/domain/site/service/SiteVersionService.java \
        src/test/java/com/ts/rm/domain/site/service/SiteVersionServiceTest.java
git commit -m "fix: 패치 From 제안을 사이트 현재 버전 포함으로 변경

사이트가 1.1.13 이면 From 을 1.1.14 로 제안해, 1.1.13 적용 이후 해당
버전에 추가된 엔진 빌드/파일이 어떤 패치에도 실리지 않는 문제가 있었다.
From 하한을 현재 버전 자신으로 내려 다음 패치가 회수하도록 한다.

사이트가 이미 최신인 경우 기존에는 From 이 null 이라 패치 생성 자체가
불가능했으나, 이제 From == To 패치로 빌드만 회수할 수 있다."
```

---

### Task 2: 빌드 범위 조회의 from inclusive 동작을 테스트로 고정

**Files:**
- Test: `release-manager-api/src/test/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustomTest.java`

**Interfaces:**
- Consumes: `ReleaseVersionRepository.findBuildsInBaseRange(String projectId, Long fromBaseId, Long toBaseId, Long siteId)` → `List<ReleaseVersion>`
- Produces: 없음 (테스트 전용 태스크)

**이 태스크에 프로덕션 코드 변경은 없다.** Task 1 의 전제 — "빌드는 `buildBaseVersion` PK 가
`[fromBaseId, toBaseId]` 안에 들면 포함된다" — 를 테스트로 못 박아, 나중에 이 쿼리가 바뀌면
Task 1 이 조용히 무력화되는 일을 막는다.

- [ ] **Step 1: 테스트 2건을 작성한다**

`ReleaseVersionRepositoryCustomTest.java` 의 `// ========== Helper Methods ==========` 주석 **바로 위**에 추가한다.

```java
    @Test
    @DisplayName("findBuildsInBaseRange — from 이 빌드의 base 와 같으면 그 빌드가 포함된다 (inclusive)")
    void findBuildsInBaseRange_includesBuildOnFromBase() {
        ReleaseVersion base13 = createAndSaveVersion("1.1.13");
        ReleaseVersion base14 = createAndSaveVersion("1.1.14");
        ReleaseVersion build13 = createAndSaveBuild("1.1.13", 260730, base13);
        entityManager.flush();
        entityManager.clear();

        List<ReleaseVersion> result = releaseVersionRepository.findBuildsInBaseRange(
                PROJECT_ID, base13.getReleaseVersionId(), base14.getReleaseVersionId(), null);

        assertThat(result).extracting(ReleaseVersion::getReleaseVersionId)
                .containsExactly(build13.getReleaseVersionId());
    }

    @Test
    @DisplayName("findBuildsInBaseRange — from 이 빌드의 base 보다 크면 그 빌드는 빠진다 (버그 재현 조건)")
    void findBuildsInBaseRange_excludesBuildBelowFromBase() {
        ReleaseVersion base13 = createAndSaveVersion("1.1.13");
        ReleaseVersion base14 = createAndSaveVersion("1.1.14");
        createAndSaveBuild("1.1.13", 260730, base13);
        entityManager.flush();
        entityManager.clear();

        List<ReleaseVersion> result = releaseVersionRepository.findBuildsInBaseRange(
                PROJECT_ID, base14.getReleaseVersionId(), base14.getReleaseVersionId(), null);

        assertThat(result).isEmpty();
    }
```

그리고 `createAndSaveVersion` 헬퍼 **바로 아래**에 빌드 생성 헬퍼를 추가한다.

```java
    /**
     * 빌드 버전 저장. buildVersion &gt; 0 이어야 findBuildsInBaseRange 가 인식한다.
     * 표준 빌드이므로 site 는 null 로 둔다.
     */
    private ReleaseVersion createAndSaveBuild(String baseVersion, int buildVersion,
            ReleaseVersion buildBase) {
        String[] parts = baseVersion.split("\\.");
        return releaseVersionRepository.save(ReleaseVersion.builder()
                .project(testProject)
                .version(baseVersion)
                .releaseType("STANDARD")
                .majorVersion(Integer.parseInt(parts[0]))
                .minorVersion(Integer.parseInt(parts[1]))
                .patchVersion(Integer.parseInt(parts[2]))
                .buildVersion(buildVersion)
                .buildIteration(1)
                .buildBaseVersion(buildBase)
                .createdByEmail("system")
                .comment("테스트 빌드")
                .build());
    }
```

- [ ] **Step 2: 테스트를 실행해 2건 모두 통과를 확인한다**

```bash
cd /mnt/c/Soronprfbs/project/release-manager/release-manager-api
./gradlew test --tests '*ReleaseVersionRepositoryCustomTest'
```

Expected: 기존 테스트 + 신규 2건 전부 PASS.

이 태스크는 현재 동작을 기술하는 것이므로 **처음부터 통과하는 것이 정상**이다.
실패한다면 Task 1 의 전제가 틀린 것이므로 진행을 멈추고 보고할 것.

- [ ] **Step 3: 커밋**

```bash
cd /mnt/c/Soronprfbs/project/release-manager/release-manager-api
git add src/test/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustomTest.java
git commit -m "test: 빌드 범위 조회의 from inclusive 동작 고정

findBuildsInBaseRange 가 buildBaseVersion PK 를 [from, to] inclusive 로
비교한다는 전제를 테스트로 못 박는다. From 제안을 현재 버전 포함으로
바꾼 변경이 이 동작에 의존한다."
```

---

### Task 3: 프론트에서 From == To 를 정상 상태로 허용

**Files:**
- Modify: `release-manager-web/src/features/patches/patch-management/ui/PatchCreateForm.tsx:155-156`, `:240-244`

**Interfaces:**
- Consumes: Task 1 이 바꾼 `NextPatchRangeResponse` — `suggestedFromVersion` 이 더 이상 `null` 이 되지 않고, 사이트가 최신이면 `suggestedToVersion` 과 같은 값이 온다.
- Produces: 없음 (최종 소비자)

- [ ] **Step 1: From 을 To 와 같게 골라도 To 가 지워지지 않게 한다**

`PatchCreateForm.tsx` 의 `handleFromVersionChange` 안, 아래 두 줄을 찾는다.

```ts
      const toVersionCleared =
        prev.toVersion && compareVersions(value, prev.toVersion) >= 0 ? '' : prev.toVersion
```

`>= 0` 을 `> 0` 으로 바꾼다.

```ts
      // From == To 는 유효한 범위다 (해당 버전에 사후 추가된 빌드/파일 회수용).
      // From 이 To 를 넘어설 때만 To 를 비운다.
      const toVersionCleared =
        prev.toVersion && compareVersions(value, prev.toVersion) > 0 ? '' : prev.toVersion
```

- [ ] **Step 2: "최신 상태" 판정 기준을 바꾼다**

`rangeHint` 안의 아래 분기를 찾는다.

```ts
    if (nextRange.suggestedFromVersion === null) {
      // 사이트 버전이 등록된 최신 버전과 동일 — 추가로 적용할 신규 버전이 없는 상태.
      // 부정적 경고가 아닌 중립 안내로 표시한다.
      return { type: 'info' as const, text: `최신 상태 (${nextRange.currentVersion})` }
    }
```

`suggestedFromVersion` 은 더 이상 `null` 이 되지 않으므로, From 과 To 가 같은지로 판정하도록 교체한다.

```ts
    if (
      nextRange.suggestedFromVersion !== null &&
      nextRange.suggestedFromVersion === nextRange.suggestedToVersion
    ) {
      // 사이트 버전이 등록된 최신 버전과 동일 — 신규 버전은 없지만, 해당 버전에
      // 사후 추가된 빌드/파일을 회수하는 From == To 패치는 만들 수 있다.
      return {
        type: 'info' as const,
        text: `최신 상태 (${nextRange.currentVersion}) — 추가된 빌드/파일만 회수합니다`,
      }
    }
```

`!== null` 검사를 남기는 이유: 등록된 승인 버전이 하나도 없으면 서비스가 From/To 를 모두
`null` 로 반환하므로(`SiteVersionService` 의 `baseVersions.isEmpty()` 분기), `null === null` 이
참이 되어 "최신 상태" 로 잘못 표시되는 것을 막는다.

- [ ] **Step 3: 타입 체크와 린트를 통과시킨다**

```bash
cd /mnt/c/Soronprfbs/project/release-manager/release-manager-web
npm run type-check && npm run lint
```

Expected: 둘 다 에러 없이 종료.

- [ ] **Step 4: 실제 화면에서 확인한다**

백엔드(`./gradlew bootRun`, 8081)와 프론트(`npm run dev`)를 띄우고 패치 생성 폼에서 확인한다.

1. 최신이 아닌 사이트 선택 → From 이 **사이트 현재 버전과 같게** 채워진다.
2. 이미 최신인 사이트 선택 → From/To 가 **둘 다 현재 버전**으로 채워지고, 안내에
   "최신 상태 (x.y.z) — 추가된 빌드/파일만 회수합니다" 가 뜨며, 생성 버튼이 **활성**이다.
3. From 드롭다운에서 To 와 같은 버전을 직접 고른다 → **To 가 비워지지 않는다.**

- [ ] **Step 5: 커밋**

```bash
cd /mnt/c/Soronprfbs/project/release-manager/release-manager-web
git add src/features/patches/patch-management/ui/PatchCreateForm.tsx
git commit -m "fix: 패치 생성 폼에서 From == To 범위 허용

From 제안이 사이트 현재 버전 자신으로 바뀌면서 사이트가 최신일 때
From == To 가 정상 상태가 됐다. From 선택 시 To 를 비우던 조건과
'최신 상태' 판정 기준을 이에 맞게 수정한다."
```

---

## 완료 후 확인

- [ ] `./gradlew test` BUILD SUCCESSFUL
- [ ] `npm run type-check` / `npm run lint` 통과
- [ ] 커밋 3건 (api 2건 + web 1건)
- [ ] push 는 사용자 요청 시에만 수행 (요청 시 각 repo 의 **모든 remote** 에 push — `github` 는 PUBLIC 이므로 커밋에 시크릿이 없는지 먼저 확인)
