# 엔진 빌드 파일 범위 누적 + NC_AGENT_SERVER 특별 처리 제거 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 패치의 `engine/` 를 from~to 범위 내 모든 빌드에서 상대경로 단위로 최신-승 누적하고, NC_AGENT_SERVER 디렉토리 특별 분기를 제거한다.

**Architecture:** 엔진 복사를 picker-선택-빌드-통째-복사(`applyBuildSelection` 엔진 루프 + `copyBuildSharedAssets`)에서 → `findBuildsInBaseRange`(범위 전체 빌드)를 소스로 한 단일 deep 누적 패스(`accumulateBuildEngineFiles`)로 교체. `applyBuildSelection` 은 WEB 복사 + 메타용 빌드 해석만 담당. picker/`PatchIncludedBuild` 메타는 보존(Approach A).

**Tech Stack:** Spring Boot / Java 17 / JUnit5 + Mockito + AssertJ + `@TempDir`. 빌드 검증은 `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.

**Spec:** `docs/superpowers/specs/2026-05-27-engine-files-range-accumulation-design.md`

**환경 공통:** 모든 gradle 명령 앞에 `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` 선행.

---

## File Structure

- `global/engine/EngineNameClassifier.java` — `DIRECTORY_FORM_ENGINE_NAMES` 제거.
- `domain/releaseversion/service/BuildsInRangeService.java` — `engineNamesInBuild` 정규 파일 only.
- `domain/patch/service/PatchGenerationService.java` — `applyBuildSelection`(web-only+메타), `accumulateBuildEngineFiles`(신규), `copyBuildSharedAssets` 삭제, 호출부 2곳, README 2줄.
- `test/.../BuildsInRangeServiceTest.java` — 디렉토리 무시 회귀 보강.
- `test/.../PatchGenerationServiceTest.java` — accumulate 신규 테스트 + 영향 테스트 갱신/삭제.

---

## Task 1: 분류기 + picker 후보에서 디렉토리형 특별 처리 제거

**Files:**
- Modify: `src/main/java/com/ts/rm/global/engine/EngineNameClassifier.java`
- Modify: `src/main/java/com/ts/rm/domain/releaseversion/service/BuildsInRangeService.java`
- Test: `src/test/java/com/ts/rm/domain/releaseversion/service/BuildsInRangeServiceTest.java`

- [ ] **Step 1: 회귀 테스트 보강 (실패 확인용)** — `BuildsInRangeServiceTest.engineDirectory_ignoredOnlyRegularFilesAreEngines` 에 NC_ prefix 디렉토리도 무시됨을 추가 검증.

기존 테스트(`engine/NC_DIR_SHOULD_BE_IGNORED` 디렉토리 무시) 의 setup 에 한 줄 추가:

```java
// 화이트리스트였던 이름의 디렉토리도 이제 무시됨 (특별 처리 제거 회귀 가드)
Files.createDirectories(d1.resolve("engine/NC_AGENT_SERVER"));
```
assertion 은 그대로 `containsExactly("NC_FILLED")` 유지 (NC_AGENT_SERVER 디렉토리가 후보에 안 들어와야 통과).

- [ ] **Step 2: 테스트 실행 — 현재는 통과(기존 코드도 비화이트리스트 dir 무시), 단 NC_AGENT_SERVER 는 화이트리스트라 후보에 들어가 실패할 것**

Run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew test --tests '*BuildsInRangeServiceTest' -q`
Expected: FAIL — `engines` 에 `NC_AGENT_SERVER` 가 포함되어 `containsExactly("NC_FILLED")` 위반.

- [ ] **Step 3: `EngineNameClassifier` 에서 `DIRECTORY_FORM_ENGINE_NAMES` 제거**

`EngineNameClassifier.java` 에서 아래 필드와 그 javadoc 블록 전체 삭제:
```java
    public static final Set<String> DIRECTORY_FORM_ENGINE_NAMES = Set.of("NC_AGENT_SERVER");
```
클래스 상단 javadoc 의 "특별 케이스(디렉토리형 엔진): {@link #DIRECTORY_FORM_ENGINE_NAMES} ..." 문단(3줄)도 삭제. 미사용이 되는 `import java.util.Set;` 도 제거. `ENGINE_NAMING_RULE` 상수와 `isEngineFile` 은 유지.

- [ ] **Step 4: `BuildsInRangeService.engineNamesInBuild` 를 정규 파일 only 로 변경**

해당 메서드 본문을 아래로 교체 (디렉토리 분기 제거):
```java
    private TreeSet<String> engineNamesInBuild(Path engineDir) {
        TreeSet<String> names = new TreeSet<>();
        if (!Files.isDirectory(engineDir)) return names;
        try (var stream = Files.list(engineDir)) {
            stream.forEach(p -> {
                String name = p.getFileName().toString();
                if (Files.isRegularFile(p) && EngineNameClassifier.isEngineFile(name)) {
                    names.add(name);
                }
            });
        } catch (IOException e) {
            log.warn("engine 디렉토리 list 실패: {}", engineDir, e);
        }
        return names;
    }
```
메서드 javadoc 의 "디렉토리형: {@link ...DIRECTORY_FORM_ENGINE_NAMES} ..." 문단 삭제.

- [ ] **Step 5: 테스트 통과 확인**

Run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew test --tests '*BuildsInRangeServiceTest' -q`
Expected: PASS (모든 케이스). 디렉토리(NC_AGENT_SERVER 포함)는 후보에서 제외.

- [ ] **Step 6: 커밋**

```bash
git -C release-manager-api add src/main/java/com/ts/rm/global/engine/EngineNameClassifier.java src/main/java/com/ts/rm/domain/releaseversion/service/BuildsInRangeService.java src/test/java/com/ts/rm/domain/releaseversion/service/BuildsInRangeServiceTest.java
git -C release-manager-api commit -m "refactor(engine): 디렉토리형 엔진 화이트리스트(NC_AGENT_SERVER) 특별 처리 제거 — picker 후보는 정규 파일만"
```

---

## Task 2: 엔진 복사를 범위 누적으로 교체 (PatchGenerationService)

**Files:**
- Modify: `src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java`
- Test: `src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java`

- [ ] **Step 1: 신규 누적 패스 테스트 작성 (실패)** — `PatchGenerationServiceTest` 에 추가. (헬퍼 `baseVersion`/`buildOf`/`write` 가 없으면 같이 추가.)

```java
    @Test
    @DisplayName("accumulateBuildEngineFiles: 범위 빌드의 engine/ 를 경로별 최신 누적 — 옛 빌드 전용 파일 보존, 겹치면 최신 승")
    void accumulate_perPathLatestWins(@TempDir Path tempDir) throws IOException {
        com.ts.rm.domain.project.entity.Project p = new com.ts.rm.domain.project.entity.Project();
        p.setProjectId("infraeye2");
        ReleaseVersion base = new ReleaseVersion();
        base.setReleaseVersionId(10L); base.setProject(p);
        base.setMajorVersion(1); base.setMinorVersion(1); base.setPatchVersion(1);

        ReleaseVersion older = new ReleaseVersion();
        older.setReleaseVersionId(101L); older.setBuildBaseVersion(base);
        older.setBuildVersion(260514); older.setBuildIteration(1);
        ReleaseVersion newer = new ReleaseVersion();
        newer.setReleaseVersionId(102L); newer.setBuildBaseVersion(base);
        newer.setBuildVersion(260527); newer.setBuildIteration(1);

        Path olderDir = tempDir.resolve("b_older");
        Files.createDirectories(olderDir.resolve("engine/NC_AGENT_SERVER/config"));
        Files.writeString(olderDir.resolve("engine/NC_AGENT_SERVER/NC_AGENT_SERVER"), "binary-v1");
        Files.writeString(olderDir.resolve("engine/NC_AGENT_SERVER/config/config.yml"), "cfg-v1");
        Files.writeString(olderDir.resolve("engine/NC_AGENT_SERVER/agent.zip"), "agent-v1");

        Path newerDir = tempDir.resolve("b_newer");
        Files.createDirectories(newerDir.resolve("engine/NC_AGENT_SERVER"));
        Files.writeString(newerDir.resolve("engine/NC_AGENT_SERVER/NC_AGENT_SERVER"), "binary-v2");

        when(fileSystemService.resolveBuildBasePath(older)).thenReturn(olderDir);
        when(fileSystemService.resolveBuildBasePath(newer)).thenReturn(newerDir);

        Path out = tempDir.resolve("patch");
        // 입력 순서 뒤섞어도 내부 정렬로 최신 승 보장
        patchGenerationService.accumulateBuildEngineFiles(out, java.util.List.of(newer, older));

        Path eng = out.resolve("engine/NC_AGENT_SERVER");
        assertThat(Files.readString(eng.resolve("NC_AGENT_SERVER"))).isEqualTo("binary-v2"); // 겹침 → 최신
        assertThat(Files.readString(eng.resolve("config/config.yml"))).isEqualTo("cfg-v1");  // 옛 빌드 전용 보존
        assertThat(Files.readString(eng.resolve("agent.zip"))).isEqualTo("agent-v1");        // 옛 빌드 전용 보존
    }
```
(`patchGenerationService` 는 기존 `@InjectMocks` 필드명. 다르면 맞춘다. `import static org.mockito.Mockito.when;`, `assertThat`, `@TempDir` 는 기존 파일에 이미 존재.)

- [ ] **Step 2: 테스트 실행 — 컴파일 실패(메서드 없음) 확인**

Run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew test --tests '*PatchGenerationServiceTest.accumulate_perPathLatestWins' -q`
Expected: FAIL — `accumulateBuildEngineFiles` 심볼 없음(컴파일 에러).

- [ ] **Step 3: `accumulateBuildEngineFiles` + `preservePosixPermissions` 추가 (package-private)**

`PatchGenerationService` 에 `import java.util.Comparator;` 추가. 아래 두 메서드를 클래스에 추가:
```java
    /**
     * from~to 범위의 모든 빌드를 정렬해 engine/ 서브트리를 상대경로 단위로 패치 engine/ 에 누적 복사한다.
     * 겹치는 상대경로는 정렬상 나중(최신) 빌드가 REPLACE_EXISTING 으로 덮어쓴다.
     *
     * <p>엔진 바이너리(고유 경로) → 최신 빌드 게만 / 기타 파일 → 경로별 누적. 단일 파일 내용은 병합하지 않는다.
     * 정렬: buildBaseVersion(major,minor,patch) asc → buildVersion asc → buildIteration asc (오래된 것부터).
     *
     * <p>package-private: 단위 테스트(accumulate_perPathLatestWins)에서 직접 호출.
     */
    void accumulateBuildEngineFiles(Path outputDir, List<ReleaseVersion> rangeBuilds) throws IOException {
        if (rangeBuilds == null || rangeBuilds.isEmpty()) return;

        List<ReleaseVersion> ordered = new ArrayList<>(rangeBuilds);
        ordered.sort(Comparator
                .comparingInt((ReleaseVersion b) -> b.getBuildBaseVersion().getMajorVersion())
                .thenComparingInt(b -> b.getBuildBaseVersion().getMinorVersion())
                .thenComparingInt(b -> b.getBuildBaseVersion().getPatchVersion())
                .thenComparingInt(b -> b.getBuildVersion() != null ? b.getBuildVersion() : 0)
                .thenComparingInt(b -> b.getBuildIteration() != null ? b.getBuildIteration() : 0));

        Path outEngineDir = outputDir.resolve("engine");
        int copied = 0;
        for (ReleaseVersion b : ordered) {
            Path buildEngineDir = fileSystemService.resolveBuildBasePath(b).resolve("engine");
            if (!Files.isDirectory(buildEngineDir)) continue;
            try (var stream = Files.walk(buildEngineDir)) {
                for (Path src : stream.toList()) {
                    Path rel = buildEngineDir.relativize(src);
                    Path dst = outEngineDir.resolve(rel.toString());
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                        preservePosixPermissions(src, dst);
                        copied++;
                    }
                }
            }
        }
        log.info("빌드 엔진 파일 범위 누적 복사 완료: {}개 파일 ({}개 빌드)", copied, ordered.size());
    }

    /** source 의 POSIX permission(실행 비트 포함)을 dst 에 복사. 비-POSIX FS 면 실행 비트만 폴백. */
    private void preservePosixPermissions(Path src, Path dst) throws IOException {
        try {
            Files.setPosixFilePermissions(dst, Files.getPosixFilePermissions(src));
        } catch (UnsupportedOperationException ignored) {
            dst.toFile().setExecutable(true, false);
        }
    }
```

- [ ] **Step 4: 신규 테스트 통과 확인**

Run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew test --tests '*PatchGenerationServiceTest.accumulate_perPathLatestWins' -q`
Expected: PASS.

- [ ] **Step 5: `applyBuildSelection` 을 WEB 복사 + 엔진 메타 해석 only 로 축소**

`applyBuildSelection` 본문에서 **엔진 파일 복사 블록(b. ENGINE 부분 복사 — src/dst resolve + copyDirectory/Files.copy + posix perm + 예외)** 을 제거하고, 엔진은 메타용 `loadBuildVersion` 해석만 남긴다:
```java
    private Map<Long, ReleaseVersion> applyBuildSelection(Path outputDir, PatchDto.BuildSelection sel) throws IOException {
        log.info("WEB 빌드 복사 시작 - WEB: {}, ENGINE(메타): {}",
                sel.web() == null ? "(없음)" : sel.web().buildVersionId(),
                sel.engines() == null ? List.of() : sel.engines());

        Map<Long, ReleaseVersion> selectedBuilds = new LinkedHashMap<>();

        // a. WEB 통째 복사 (전체 교체)
        if (sel.web() != null) {
            ReleaseVersion bv = loadBuildVersion(sel.web().buildVersionId());
            Path src = fileSystemService.resolveBuildBasePath(bv).resolve("web");
            copyDirectoryReplaceExisting(src, outputDir.resolve("web"));
            selectedBuilds.put(bv.getReleaseVersionId(), bv);
        }

        // b. ENGINE 복사는 accumulateBuildEngineFiles 가 범위 누적으로 처리.
        //    여기선 메타(persistIncludedBuilds / generateBuildVersionFile / buildIncludedBuilds)용으로 ReleaseVersion 만 해석.
        if (sel.engines() != null) {
            for (PatchDto.SelectedEngine se : sel.engines()) {
                ReleaseVersion bv = loadBuildVersion(se.buildVersionId());
                selectedBuilds.putIfAbsent(bv.getReleaseVersionId(), bv);
            }
        }
        return selectedBuilds;
    }
```

- [ ] **Step 6: `copyBuildSharedAssets` 삭제**

`copyBuildSharedAssets(Path outputDir, List<ReleaseVersion> versions)` 메서드 전체 삭제(javadoc 포함). `copyDirectoryReplaceExisting`, `BuildFileCopyException` 는 WEB 복사가 쓰므로 **유지**.

- [ ] **Step 7: `generatePatch` 호출부 교체** — `applyBuildSelection`(web) 직후 `copyBuildSharedAssets` 호출을 누적 패스로 교체.

기존:
```java
            // ---- 빌드 공유 자산 자동 동반 ----
            progressService.update(5, TOTAL_STEPS, "빌드 공유 자산 동반 중");
            copyBuildSharedAssets(Paths.get(releaseBasePath, outputPath), betweenVersions);
```
교체:
```java
            // ---- ENGINE: 범위 내 전체 빌드의 engine/ 를 경로별 최신 누적 ----
            progressService.update(5, TOTAL_STEPS, "ENGINE 빌드 파일 범위 누적 중");
            if (buildSelection != null && buildSelection.enabled()) {
                // 표준 빌드는 customer=null. 범위 전체 빌드를 소스로 누적.
                List<ReleaseVersion> rangeBuilds = releaseVersionRepository.findBuildsInBaseRange(
                        projectId, fromVersionId, toVersionId, null);
                accumulateBuildEngineFiles(Paths.get(releaseBasePath, outputPath), rangeBuilds);
            }
```
그리고 직전 step 4 라벨 `"WEB / ENGINE 빌드 파일 복사 중"` → `"WEB 빌드 파일 복사 중"` 으로 변경.

- [ ] **Step 8: `generateCustomPatch` 호출부 교체** — 동일 패턴, customer=customerId, 버전ID는 엔티티에서.

기존:
```java
            // ---- 빌드 공유 자산 자동 동반 ----
            progressService.update(5, TOTAL_STEPS, "빌드 공유 자산 동반 중");
            copyBuildSharedAssets(Paths.get(releaseBasePath, outputPath), betweenVersions);
```
교체:
```java
            // ---- ENGINE: 범위 내 전체 빌드의 engine/ 를 경로별 최신 누적 ----
            progressService.update(5, TOTAL_STEPS, "ENGINE 빌드 파일 범위 누적 중");
            if (buildSelection != null && buildSelection.enabled()) {
                List<ReleaseVersion> rangeBuilds = releaseVersionRepository.findBuildsInBaseRange(
                        projectId, fromVersion.getReleaseVersionId(), toVersion.getReleaseVersionId(), customerId);
                accumulateBuildEngineFiles(Paths.get(releaseBasePath, outputPath), rangeBuilds);
            }
```
직전 step 4 라벨도 `"WEB 빌드 파일 복사 중"` 으로 변경.

- [ ] **Step 9: 영향받는 기존 테스트 갱신/삭제** (각 테스트를 읽고 아래대로 조정)

1. **삭제**: `unselectedEngineNotAutoIncluded` — 전제("미선택 엔진은 제외")가 범위-누적 모델과 반대. 메서드 전체 삭제.
2. **`pickerSelection_partialCopy`**: 엔진 복사를 `applyBuildSelection` 결과로 검증하던 부분 제거. WEB/etc 단언은 유지. 엔진을 포함하려면 `findBuildsInBaseRange(projectId, from, to, null)` 스텁을 추가하고 엔진 단언을 누적 결과 기준으로 옮기거나, 엔진 검증은 `accumulate_perPathLatestWins` 로 위임하고 본 테스트에선 제외.
3. **`buildSharedAssetsAutomaticallyIncluded`** / **`multiBuildSharedAssetLatestWins`**: `copyBuildSharedAssets`(betweenVersions) 대신 `findBuildsInBaseRange` 스텁을 추가(`given(releaseVersionRepository.findBuildsInBaseRange(...)).willReturn(빌드들)`)하고, 결과 단언은 그대로(공유자산 누적 / 최신 승) 유지. `enabled=true` buildSelection 필요.
4. 그 외(`buildInCumulativeWalk_sharedAssetAutoIncluded`, `unpickedEngineInBuildReleaseFile_cumulativeSkip`, `null/disabled`, 검증 실패 테스트, `persist*`, `responseFields_populated`, `etcConflict_largerBuildVersionWins`): copySqlFiles/메타/검증 영역이라 원칙적으로 불변. 단 `enabled=true` 인데 `findBuildsInBaseRange` 미스텁이면 누적 패스가 빈 리스트(Mockito 기본)로 no-op → 영향 없음. 실행해서 회귀 확인.

- [ ] **Step 10: patch 도메인 테스트 실행**

Run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew test --tests '*PatchGenerationServiceTest' -q`
Expected: PASS (전체).

- [ ] **Step 11: 커밋**

```bash
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceTest.java
git -C release-manager-api commit -m "feat(patch): 엔진 빌드 파일을 범위 내 전체 빌드에서 경로별 최신 누적 — picker 통째 복사 폐기"
```

---

## Task 3: README 의 NC_AGENT_SERVER 특별 문구 제거

**Files:**
- Modify: `src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java`

- [ ] **Step 1: `generateReadme` 와 `generateCustomReadme` 에서 NC_AGENT_SERVER 줄 제거**

두 메서드 각각에서 아래 append 한 줄 삭제:
```java
            content.append("   - `engine/NC_AGENT_SERVER/` 가 포함된 경우, 그 안의 `patch_nc_agent_server.sh` 가 InfraEye CLI 에 의해 자동 실행됨\n");
```
직전의 "7. `InfraEye eng patch` — 엔진 바이너리 패치 (NC_*, OZ_* 자동 적용 + 재기동)" 줄은 유지.

- [ ] **Step 2: 컴파일 확인**

Run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew compileJava -q`
Expected: EXIT 0.

- [ ] **Step 3: 커밋**

```bash
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java
git -C release-manager-api commit -m "docs(patch): README 에서 NC_AGENT_SERVER 디렉토리 특별 문구 제거 (일반 엔진 패치로 흡수)"
```

---

## Task 4: 전체 검증

- [ ] **Step 1: 전체 컴파일 + 테스트 컴파일**

Run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew compileJava compileTestJava -q`
Expected: EXIT 0.

- [ ] **Step 2: 관련 도메인 테스트**

Run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew test --tests '*PatchGenerationServiceTest' --tests '*BuildsInRangeServiceTest' -q`
Expected: PASS.

- [ ] **Step 3: `DIRECTORY_FORM_ENGINE_NAMES` 잔존 참조 0 확인**

Run: `grep -rn "DIRECTORY_FORM_ENGINE_NAMES\|copyBuildSharedAssets" release-manager-api/src`
Expected: 출력 없음.

- [ ] **Step 4: 수동 서버 검증 (배포 후, 사용자)** — `1.0.0→1.1.2` 표준 패치(고객사 선택)에서 `engine/NC_AGENT_SERVER/` 가 `260514-1`(config/agent) + `260527-1`(최신 바이너리) **합본**으로 생성되는지 확인.

---

## Self-Review 메모

- 스펙 커버리지: §3.1 누적 패스(Task2 S3), §3.2 applyBuildSelection/삭제/호출부(Task2 S5-8), §3.3 분류기/picker(Task1), §3.4 README(Task3). ✓
- 타입 일관성: `accumulateBuildEngineFiles(Path, List<ReleaseVersion>)` 정의(Task2 S3)와 호출(S7,S8)·테스트(S1) 시그니처 동일. `findBuildsInBaseRange(String,Long,Long,Long)` 기존 시그니처 사용. ✓
- 비목표: copySqlFiles, web 통째 교체, 메타 스키마 불변. ✓
- 알려진 한계: dir-form NC_AGENT_SERVER 는 picker/메타·`.build_version` 에 미노출(복사는 정상). 빌드가 단일 파일로 가면 해소.
