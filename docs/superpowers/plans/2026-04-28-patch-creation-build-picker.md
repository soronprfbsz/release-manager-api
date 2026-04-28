# 패치 생성 빌드 picker 재설계 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 패치 생성 폼에 WEB 1개·엔진별 1개씩 선택 가능한 인라인 컴팩트 picker 를 도입하고, 데이터 모델·API·UI 를 그에 맞게 재배선한다.

**Architecture:** 빌드 ZIP 업로드 시 ENGINE 의 `sub_category` 를 자동 저장하여 진실의 원천을 회복한 뒤, 신규 `builds-in-range` API 로 picker 후보를 제공한다. 패치 생성 입력은 `buildSelection` 으로 명시화되고, 누적 DB 시퀀스에서 핫픽스를 분리한다. 프론트엔드는 인라인 picker + 자동 preselect + 일괄 액션을 제공한다.

**Tech Stack:** Spring Boot 3.5.6 / Java 17 / JPA + QueryDSL / MapStruct / JUnit 5 + Mockito (BE) — React 19 + TypeScript / Vite / TanStack Query / shadcn-ui / Playwright (FE).

**연관 문서:** `docs/superpowers/specs/2026-04-28-patch-creation-build-picker-design.md`

---

## 파일 구조

### 백엔드 — 변경

| 경로 | 책임 |
| --- | --- |
| `src/main/java/com/ts/rm/domain/releaseversion/service/BuildFileService.java` | `extractEngineName` 헬퍼 + ZIP 추출 시 `subCategory` 저장 |
| `src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustom.java` (+Impl) | `findBuildsInBaseRange` 쿼리 |
| `src/main/java/com/ts/rm/domain/releaseversion/dto/ReleaseVersionDto.java` | `BuildsInRangeResponse`, `EngineGroup`, `BuildCandidate` |
| `src/main/java/com/ts/rm/domain/releaseversion/service/ReleaseVersionService.java` | `getBuildsInRange` 메서드 |
| `src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionController.java` (+Docs) | `GET /api/releases/versions/builds-in-range` |
| `src/main/java/com/ts/rm/domain/releasefile/repository/ReleaseFileRepositoryCustom.java` (+Impl) | 빌드별 카테고리 + sub_category 집계 쿼리 |
| `src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java` | `GenerateRequest` 필드 변경, `BuildSelection`/`SelectedEngine` types, response 필드 추가 |
| `src/main/java/com/ts/rm/domain/patch/controller/PatchController.java` (+Docs) | 새 입력으로 호출 시그니처 재배선 |
| `src/main/java/com/ts/rm/domain/patch/service/PatchService.java` | `generatePatchByVersion` 시그니처 변경 (buildSelection 인자) |
| `src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java` | 빌드 파일 포함 분기 재설계, 핫픽스 제외, ETC 동행, 응답 필드 채움 |

### 프론트엔드 — 변경 / 신규

| 경로 | 책임 |
| --- | --- |
| `src/entities/releases/release/model/types.ts` | `BuildCandidate`, `EngineGroup`, `BuildsInRangeResponse` 추가 |
| `src/entities/releases/release/api/releaseApi.ts` | `getBuildsInRange` 호출 |
| `src/entities/releases/release/queries/releaseQueries.ts` | `useBuildsInRange` 훅 |
| `src/entities/patches/patch/model/types.ts` | `BuildSelection`, `SelectedEngine`, `GenerateRequest` 확장, response 필드 추가 |
| `src/features/patches/patch-management/model/types.ts` | `PatchCreateFormData` 에 `buildSelection` 추가 |
| `src/features/patches/patch-management/ui/BuildPickerSection.tsx` | **신규** — 컴팩트 picker (WEB radio + ENGINE 행) |
| `src/features/patches/patch-management/ui/PatchCreateForm.tsx` | 폼에 picker 통합 + 검증 + build-only 인디케이터 |
| `src/features/patches/patch-management/ui/PatchGenerateFormCard.tsx` | 동일 패턴 적용(또는 PatchCreateForm 으로 일원화) |
| `src/pages/patches/PatchesPage.tsx` | `getVersionsFromTree` base 만 노출, 핫픽스 안내 toast |

### 운영 자료

| 경로 | 비고 |
| --- | --- |
| `docs/superpowers/plans/2026-04-28-patch-creation-build-picker.md` | (이 문서) |
| (운영 DB) | 수동 SQL 백필 — 본 plan Task 21 의 SQL 그대로 |

---

## Phase 1 — 데이터 모델 정정 (sub_category)

### Task 1: BuildFileService — `extractEngineName` 헬퍼 + 단위 테스트

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/service/BuildFileService.java`
- Modify: `release-manager-api/src/test/java/com/ts/rm/domain/releaseversion/service/BuildFileServiceTest.java`

- [ ] **Step 1: 실패할 테스트 추가**

`BuildFileServiceTest.java` 의 `class BuildFileServiceTest { ... }` 안에 추가:

```java
@Test
@DisplayName("extractEngineName: engine/{engineName}/... 에서 두 번째 토큰을 반환")
void extractEngineName_normalCase() {
    assertThat(BuildFileService.extractEngineName("engine/NC_SMS/lib/foo.jar"))
            .isEqualTo("NC_SMS");
}

@Test
@DisplayName("extractEngineName: engine 직속 파일이면 UNKNOWN")
void extractEngineName_directFile_returnsUnknown() {
    assertThat(BuildFileService.extractEngineName("engine/foo.jar"))
            .isEqualTo("UNKNOWN");
}

@Test
@DisplayName("extractEngineName: engine 단독 prefix 라면 UNKNOWN")
void extractEngineName_engineOnly_returnsUnknown() {
    assertThat(BuildFileService.extractEngineName("engine/"))
            .isEqualTo("UNKNOWN");
}

@Test
@DisplayName("extractEngineName: 백슬래시 ZIP 도 처리")
void extractEngineName_backslash_handled() {
    assertThat(BuildFileService.extractEngineName("engine\\NC_INV\\bin\\app.exe"))
            .isEqualTo("NC_INV");
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.releaseversion.service.BuildFileServiceTest" 2>&1 | tail -30
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 컴파일 에러(`extractEngineName` 가 BuildFileService 에 없음).

- [ ] **Step 3: BuildFileService 에 헬퍼 추가**

`BuildFileService.java` 의 `topLevelOf(String zipRelativePath)` 메서드 직전에 추가:

```java
/**
 * ZIP 내부 상대 경로에서 엔진명을 추출한다.
 *
 * <p>표준 경로는 {@code engine/{engineName}/...} 형식이며 두 번째 토큰을 엔진명으로 본다.
 * 두 번째 토큰이 없거나(예: {@code engine/foo.jar}, {@code engine/}) 그 토큰이 비어있으면
 * {@code "UNKNOWN"} 을 반환하여 운영자가 사후 보정할 수 있도록 한다.
 *
 * @param zipRelativePath ZIP 내부 상대 경로 (예: {@code engine/NC_SMS/lib/foo.jar})
 * @return 엔진명 또는 {@code "UNKNOWN"}
 */
static String extractEngineName(String zipRelativePath) {
    if (zipRelativePath == null) return "UNKNOWN";
    String normalized = zipRelativePath.replace('\\', '/');
    int firstSlash = normalized.indexOf('/');
    if (firstSlash < 0) return "UNKNOWN";
    String afterFirst = normalized.substring(firstSlash + 1);
    int secondSlash = afterFirst.indexOf('/');
    String token = secondSlash < 0 ? afterFirst : afterFirst.substring(0, secondSlash);
    return token.isBlank() ? "UNKNOWN" : token;
}
```

- [ ] **Step 4: 테스트 통과 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.releaseversion.service.BuildFileServiceTest" 2>&1 | tail -20
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: `BUILD SUCCESSFUL`, line 14 = `languageVersion = JavaLanguageVersion.of(17)`.

- [ ] **Step 5: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/service/BuildFileService.java src/test/java/com/ts/rm/domain/releaseversion/service/BuildFileServiceTest.java
git -C release-manager-api commit -m "feat: BuildFileService 에 extractEngineName 헬퍼 추가

ZIP 의 engine/{engineName}/... 패턴에서 엔진명을 안전하게 추출. 두 번째
토큰이 없거나 비어있으면 'UNKNOWN' 반환. 후속 sub_category 자동 저장에서
사용된다."
```

---

### Task 2: BuildFileService — 빌드 ZIP 추출 시 `subCategory` 저장

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/service/BuildFileService.java` (line 245~260 부근)
- Modify: `release-manager-api/src/test/java/com/ts/rm/domain/releaseversion/service/BuildFileServiceTest.java`

- [ ] **Step 1: 실패할 테스트 추가**

`BuildFileServiceTest.java` 의 `uploadBuildZip_valid_extractsAndPersists` 다음에 추가:

```java
@Test
@DisplayName("정상: ENGINE 파일은 sub_category 에 engineName 이 저장됨")
void uploadBuildZip_engineFile_persistsSubCategory() throws IOException {
    ReleaseVersion base = buildBase();
    ReleaseVersion buildVer = build(99L, base, 260427);
    Path buildDir = tempDir.resolve("versions/p/standard/1.1.x/1.1.0/builds/260427");

    given(releaseVersionRepository.findById(99L)).willReturn(Optional.of(buildVer));
    given(fileSystemService.resolveBuildBasePath(base, 260427)).willReturn(buildDir);
    given(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(99L))
            .willReturn(Collections.emptyList());
    given(releaseFileRepository.save(any(ReleaseFile.class)))
            .willAnswer(inv -> inv.getArgument(0));

    Path zip = makeZip(
            "web/foo.war",
            "engine/NC_SMS/lib/x.jar",
            "engine/NC_FAULT_MS/cfg/y.cfg",
            "engine/loose.jar",
            "etc/note.txt"
    );

    BuildFileService.UploadResult result = buildFileService.uploadBuildZip(99L, zip, "u@x");

    var bySubCategory = result.createdFiles().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                    rf -> String.valueOf(rf.getSubCategory()),
                    java.util.stream.Collectors.mapping(ReleaseFile::getFileCategory, java.util.stream.Collectors.toList())
            ));

    // WEB 은 sub_category null
    assertThat(bySubCategory.get("null"))
            .containsExactlyInAnyOrder(FileCategory.WEB, FileCategory.ETC);

    // ENGINE 은 엔진명별로 분리됨
    assertThat(bySubCategory.get("NC_SMS")).containsExactly(FileCategory.ENGINE);
    assertThat(bySubCategory.get("NC_FAULT_MS")).containsExactly(FileCategory.ENGINE);
    // engine 직속 파일은 UNKNOWN
    assertThat(bySubCategory.get("UNKNOWN")).containsExactly(FileCategory.ENGINE);
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.releaseversion.service.BuildFileServiceTest.uploadBuildZip_engineFile_persistsSubCategory" 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: FAIL — sub_category 가 모두 null (현재 코드).

- [ ] **Step 3: BuildFileService.uploadBuildZip 의 ReleaseFile 생성 부분 수정**

`BuildFileService.java` 의 `uploadBuildZip` 메서드 안 ReleaseFile.builder() 호출(현 line 245~260 부근) 을 다음과 같이 변경:

```java
            String topLevel = topLevelOf(info.relativePath());
            FileCategory category = mapTopLevelToCategory(topLevel);
            if (category == null) {
                // BuildZipValidator 가 이미 막아준 케이스라 도달 불가하지만 안전망
                log.warn("알 수 없는 루트 디렉토리: {} (skip)", topLevel);
                continue;
            }

            // ENGINE 만 sub_category 저장 (WEB/ETC 는 null)
            String subCategory = (category == FileCategory.ENGINE)
                    ? extractEngineName(info.relativePath())
                    : null;

            String relativeFromBase = relativizeFromBase(baseRoot, info.absolutePath());
            String checksum;
            try {
                checksum = FileChecksumUtil.calculateChecksum(info.absolutePath());
            } catch (IOException e) {
                log.warn("체크섬 계산 실패 (계속 진행): {}", info.absolutePath(), e);
                checksum = null;
            }

            ReleaseFile file = ReleaseFile.builder()
                    .releaseVersion(build)
                    .fileType(determineFileType(info.fileName()))
                    .fileCategory(category)
                    .subCategory(subCategory)
                    .fileName(info.fileName())
                    .filePath(relativeFromBase)
                    .fileSize(info.fileSize())
                    .checksum(checksum)
                    .executionOrder(order++)
                    .description(uploadedByEmail != null
                            ? uploadedByEmail + " 가 빌드 ZIP 으로 업로드"
                            : "빌드 ZIP 업로드")
                    .build();
```

- [ ] **Step 4: 테스트 통과 + 회귀 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.releaseversion.service.BuildFileServiceTest" 2>&1 | tail -15
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: 모든 BuildFileServiceTest 통과, line 14 = `JavaLanguageVersion.of(17)`.

- [ ] **Step 5: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/service/BuildFileService.java src/test/java/com/ts/rm/domain/releaseversion/service/BuildFileServiceTest.java
git -C release-manager-api commit -m "feat: 빌드 ZIP 업로드 시 ENGINE 의 sub_category 자동 저장

engine/{engineName}/... 의 두 번째 토큰을 ReleaseFile.sub_category 에
저장하여 NC_SMS/NC_FAULT_MS 등 엔진별 식별이 가능해짐. PatchGeneration
의 sub_category 분기 로직이 의도대로 동작하기 위한 데이터 모델 정정."
```

---

## Phase 2 — `builds-in-range` API

### Task 3: ReleaseVersionRepository 에 빌드 범위 조회 + 단위 테스트

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustom.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryImpl.java`
- Test: 통합 테스트는 Phase 6 e2e 에서 다룸 (이 단계는 컴파일까지)

- [ ] **Step 1: Custom 인터페이스에 메서드 시그니처 추가**

`ReleaseVersionRepositoryCustom.java` 에 추가:

```java
/**
 * 표준/커스텀 base 버전 범위 안에 있는 빌드(build_version > 0) 를 조회.
 *
 * <p>경계 포함(from <= base <= to). 핫픽스 행은 제외.
 * customerCode 가 null 이면 STANDARD, 그렇지 않으면 해당 고객사 CUSTOM 범위.
 *
 * @param projectId       프로젝트 ID
 * @param releaseType     "STANDARD" 또는 "CUSTOM"
 * @param customerCode    CUSTOM 의 경우 고객사 코드, STANDARD 면 null
 * @param fromVersion     base from (예: "1.0.0")
 * @param toVersion       base to   (예: "1.1.0")
 * @return 해당 범위의 빌드 버전 목록 (build_version DESC, 같으면 patch DESC)
 */
List<ReleaseVersion> findBuildsInBaseRange(
        String projectId, String releaseType, String customerCode,
        String fromVersion, String toVersion);
```

상단 import 에 `import java.util.List;` 가 없으면 추가.

- [ ] **Step 2: Impl 구현**

`ReleaseVersionRepositoryImpl.java` 에 메서드 구현 추가:

```java
@Override
public List<ReleaseVersion> findBuildsInBaseRange(
        String projectId, String releaseType, String customerCode,
        String fromVersion, String toVersion) {
    QReleaseVersion rv = QReleaseVersion.releaseVersion;
    com.querydsl.core.types.dsl.BooleanExpression cond = rv.project.projectId.eq(projectId)
            .and(rv.releaseType.eq(releaseType))
            .and(rv.buildVersion.gt(0))
            .and(rv.hotfixVersion.eq(0))
            .and(rv.version.goe(fromVersion))
            .and(rv.version.loe(toVersion));
    if (customerCode != null) {
        cond = cond.and(rv.customer.customerCode.eq(customerCode));
    } else {
        cond = cond.and(rv.customer.isNull());
    }
    return queryFactory
            .selectFrom(rv)
            .leftJoin(rv.creator).fetchJoin()
            .where(cond)
            .orderBy(rv.buildVersion.desc(), rv.patchVersion.desc())
            .fetch();
}
```

> 주의: `rv.version.goe/loe` 는 문자열 비교라 `1.10.0 < 1.2.0` 같은 사전식 함정이 있다. 본 메서드는 같은 major.minor 베이스 안의 빌드 범위 정렬에는 적합하지만, 본 plan Phase 2/3 에서는 호출 측이 `from <= base <= to` 의 base 시퀀스를 별도로 산출(예: PatchGenerationService 의 기존 `findVersionsBetweenExcludingHotfixes`) 한 후 그 결과의 첫/끝 base 버전 문자열을 인자로 넘긴다. 시멘틱 검증은 Task 5 의 서비스에서 수행.

- [ ] **Step 3: 컴파일 확인**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustom.java src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryImpl.java
git -C release-manager-api commit -m "feat: ReleaseVersionRepository 에 base 범위 빌드 조회 추가

findBuildsInBaseRange — STANDARD/CUSTOM, 핫픽스 제외, build_version DESC.
builds-in-range API 와 패치 생성 시 빌드 후보 산출에 사용."
```

---

### Task 4: ReleaseFileRepository 에 빌드별 카테고리/엔진 집계

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releasefile/repository/ReleaseFileRepositoryCustom.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releasefile/repository/ReleaseFileRepositoryImpl.java`

본 메서드는 “버전 ID 들을 받아 각 버전이 어떤 카테고리/엔진을 갖는지” 알려주는 집계용. picker 가 “해당 빌드에 web 파일 있나? engine NC_SMS 있나?” 를 알아야 후보 노출 여부를 결정.

- [ ] **Step 1: Custom 인터페이스 시그니처 추가**

`ReleaseFileRepositoryCustom.java` 에 추가:

```java
/**
 * 빌드 버전 ID 목록에 대해 각 버전이 보유한 (file_category, sub_category) 쌍을 반환.
 *
 * <p>picker 의 후보 노출 여부 결정에 사용.
 * 결과는 (versionId, category, subCategory) 튜플의 리스트.
 *
 * @param versionIds 빌드 버전 ID 목록
 * @return 각 버전의 카테고리/sub_category 쌍 (중복 제거)
 */
List<BuildCategoryRow> findBuildCategoryRows(List<Long> versionIds);

/**
 * 빌드별 카테고리 row.
 */
record BuildCategoryRow(
        Long versionId,
        com.ts.rm.domain.releasefile.enums.FileCategory category,
        String subCategory  // ENGINE 만 의미. 그 외 null.
) {}
```

- [ ] **Step 2: Impl 구현**

`ReleaseFileRepositoryImpl.java` 에 추가 (기존 메서드들 옆):

```java
@Override
public List<BuildCategoryRow> findBuildCategoryRows(List<Long> versionIds) {
    if (versionIds == null || versionIds.isEmpty()) return java.util.List.of();
    QReleaseFile rf = QReleaseFile.releaseFile;
    return queryFactory
            .select(com.querydsl.core.types.Projections.constructor(BuildCategoryRow.class,
                    rf.releaseVersion.releaseVersionId, rf.fileCategory, rf.subCategory))
            .from(rf)
            .where(rf.releaseVersion.releaseVersionId.in(versionIds))
            .groupBy(rf.releaseVersion.releaseVersionId, rf.fileCategory, rf.subCategory)
            .fetch();
}
```

- [ ] **Step 3: 컴파일 확인**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releasefile/repository/ReleaseFileRepositoryCustom.java src/main/java/com/ts/rm/domain/releasefile/repository/ReleaseFileRepositoryImpl.java
git -C release-manager-api commit -m "feat: ReleaseFileRepository 에 빌드별 (category, sub_category) 집계 추가

findBuildCategoryRows — picker 후보 노출 여부 결정용. 각 빌드가 어떤
카테고리/엔진 파일을 보유했는지 GROUP BY 로 산출."
```

---

### Task 5: ReleaseVersionDto — `BuildsInRangeResponse` 타입

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/dto/ReleaseVersionDto.java`

- [ ] **Step 1: 타입 추가**

`ReleaseVersionDto.java` 의 마지막 inner class/record 다음에 추가:

```java
@Schema(description = "빌드 후보 항목")
public record BuildCandidate(
        @Schema(description = "빌드 ReleaseVersion ID")
        Long buildVersionId,
        @Schema(description = "전체 버전 (예: 1.1.0.260428)")
        String fullVersion,
        @Schema(description = "빌드 버전 정수 (예: 260428)")
        int buildVersion,
        @Schema(description = "생성일시 ISO-8601")
        String createdAt,
        @Schema(description = "그룹 내 최신 여부")
        boolean isLatest
) {}

@Schema(description = "엔진 그룹 (engineName, 후보 목록)")
public record EngineGroup(
        @Schema(description = "엔진명 (예: NC_SMS, UNKNOWN 가능)")
        String engineName,
        @Schema(description = "후보 목록 (build_version DESC)")
        java.util.List<BuildCandidate> candidates
) {}

@Schema(description = "범위 내 핫픽스 정보 (다운로드 안내용)")
public record HotfixInRange(
        @Schema(description = "핫픽스 버전 ID") Long versionId,
        @Schema(description = "전체 버전 (예: 1.0.0.1)") String fullVersion,
        @Schema(description = "핫픽스 번호") int hotfixVersion
) {}

@Schema(description = "버전 범위 안의 빌드 후보 응답")
public record BuildsInRangeResponse(
        @Schema(description = "WEB 빌드 후보 (build_version DESC)")
        java.util.List<BuildCandidate> web,
        @Schema(description = "엔진별 빌드 후보 그룹")
        java.util.List<EngineGroup> engines,
        @Schema(description = "범위 안의 핫픽스 (별도 다운로드 안내)")
        java.util.List<HotfixInRange> hotfixesInRange
) {}
```

- [ ] **Step 2: 컴파일 확인**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: `BUILD SUCCESSFUL`.

- [ ] **Step 3: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/dto/ReleaseVersionDto.java
git -C release-manager-api commit -m "feat: ReleaseVersionDto 에 BuildsInRangeResponse 타입 추가

builds-in-range API 응답 모델. WEB 후보, ENGINE 그룹(engineName +
candidates), 핫픽스 안내."
```

---

### Task 6: ReleaseVersionService — `getBuildsInRange` 메서드 + 테스트

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustom.java` (`findHotfixesInBaseRange` 추가)
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryImpl.java` (구현)
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/service/ReleaseVersionService.java`
- Test: `release-manager-api/src/test/java/com/ts/rm/domain/releaseversion/service/ReleaseVersionBuildServiceTest.java` (기존 파일 사용)

- [ ] **Step 1: 핫픽스 범위 조회 Custom 메서드 추가** (테스트 mock 의 의존)

`ReleaseVersionRepositoryCustom.java` 에 추가:

```java
/**
 * base 버전 범위 안의 핫픽스(hotfix_version > 0) 행 조회.
 */
List<ReleaseVersion> findHotfixesInBaseRange(
        String projectId, String releaseType, String customerCode,
        String fromVersion, String toVersion);
```

`ReleaseVersionRepositoryImpl.java` 에 구현:

```java
@Override
public List<ReleaseVersion> findHotfixesInBaseRange(
        String projectId, String releaseType, String customerCode,
        String fromVersion, String toVersion) {
    QReleaseVersion rv = QReleaseVersion.releaseVersion;
    com.querydsl.core.types.dsl.BooleanExpression cond = rv.project.projectId.eq(projectId)
            .and(rv.releaseType.eq(releaseType))
            .and(rv.hotfixVersion.gt(0))
            .and(rv.version.goe(fromVersion))
            .and(rv.version.loe(toVersion));
    if (customerCode != null) {
        cond = cond.and(rv.customer.customerCode.eq(customerCode));
    } else {
        cond = cond.and(rv.customer.isNull());
    }
    return queryFactory
            .selectFrom(rv)
            .where(cond)
            .orderBy(rv.version.asc(), rv.hotfixVersion.asc())
            .fetch();
}
```

- [ ] **Step 2: 실패할 서비스 테스트 추가**

`ReleaseVersionBuildServiceTest.java` 에 추가 (필요시 새 메서드 안에서 mock 설정):

```java
@Test
@DisplayName("getBuildsInRange: WEB/ENGINE 그룹 + 최신 플래그 + 핫픽스 정보")
void getBuildsInRange_groupsByEngine_andMarksLatest() {
    String projectId = "p";
    String fromVersion = "1.0.0";
    String toVersion  = "1.1.0";

    ReleaseVersion baseFrom = ReleaseVersion.builder()
            .releaseVersionId(1L).version("1.0.0").buildVersion(0).hotfixVersion(0)
            .releaseType("STANDARD").build();
    ReleaseVersion baseTo = ReleaseVersion.builder()
            .releaseVersionId(2L).version("1.1.0").buildVersion(0).hotfixVersion(0)
            .releaseType("STANDARD").build();
    ReleaseVersion build1 = ReleaseVersion.builder()
            .releaseVersionId(10L).version("1.1.0").buildVersion(260427).hotfixVersion(0)
            .releaseType("STANDARD").buildBaseVersion(baseTo)
            .createdAt(java.time.LocalDateTime.now()).build();
    ReleaseVersion build2 = ReleaseVersion.builder()
            .releaseVersionId(11L).version("1.1.0").buildVersion(260428).hotfixVersion(0)
            .releaseType("STANDARD").buildBaseVersion(baseTo)
            .createdAt(java.time.LocalDateTime.now()).build();
    ReleaseVersion hotfix = ReleaseVersion.builder()
            .releaseVersionId(20L).version("1.0.0").hotfixVersion(1).buildVersion(0)
            .releaseType("STANDARD").build();

    given(releaseVersionRepository.findBuildsInBaseRange(
            projectId, "STANDARD", null, fromVersion, toVersion))
            .willReturn(java.util.List.of(build2, build1));  // DESC 순
    given(releaseVersionRepository.findHotfixesInBaseRange(
            projectId, "STANDARD", null, fromVersion, toVersion))
            .willReturn(java.util.List.of(hotfix));
    given(releaseFileRepository.findBuildCategoryRows(java.util.List.of(11L, 10L)))
            .willReturn(java.util.List.of(
                    new com.ts.rm.domain.releasefile.repository.ReleaseFileRepositoryCustom.BuildCategoryRow(
                            11L, com.ts.rm.domain.releasefile.enums.FileCategory.WEB, null),
                    new com.ts.rm.domain.releasefile.repository.ReleaseFileRepositoryCustom.BuildCategoryRow(
                            11L, com.ts.rm.domain.releasefile.enums.FileCategory.ENGINE, "NC_SMS"),
                    new com.ts.rm.domain.releasefile.repository.ReleaseFileRepositoryCustom.BuildCategoryRow(
                            10L, com.ts.rm.domain.releasefile.enums.FileCategory.WEB, null),
                    new com.ts.rm.domain.releasefile.repository.ReleaseFileRepositoryCustom.BuildCategoryRow(
                            10L, com.ts.rm.domain.releasefile.enums.FileCategory.ENGINE, "NC_FAULT_MS")
            ));

    var resp = releaseVersionService.getBuildsInRange(
            projectId, "STANDARD", null, fromVersion, toVersion);

    // WEB 후보: 두 빌드 모두 web 보유. 첫 항목(11L) 이 isLatest.
    assertThat(resp.web()).extracting("buildVersionId").containsExactly(11L, 10L);
    assertThat(resp.web().get(0).isLatest()).isTrue();
    assertThat(resp.web().get(1).isLatest()).isFalse();

    // ENGINE 그룹: NC_SMS = [11L], NC_FAULT_MS = [10L]
    var bySrc = resp.engines().stream()
            .collect(java.util.stream.Collectors.toMap(
                    com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto.EngineGroup::engineName,
                    g -> g));
    assertThat(bySrc).containsKeys("NC_SMS", "NC_FAULT_MS");
    assertThat(bySrc.get("NC_SMS").candidates()).extracting("buildVersionId").containsExactly(11L);
    assertThat(bySrc.get("NC_SMS").candidates().get(0).isLatest()).isTrue();
    assertThat(bySrc.get("NC_FAULT_MS").candidates()).extracting("buildVersionId").containsExactly(10L);

    // 핫픽스
    assertThat(resp.hotfixesInRange()).extracting("versionId").containsExactly(20L);
}
```

- [ ] **Step 3: 테스트 실패 확인**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.releaseversion.service.ReleaseVersionBuildServiceTest.getBuildsInRange_groupsByEngine_andMarksLatest" 2>&1 | tail -20
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 컴파일 에러 (`getBuildsInRange` 메서드 없음).

- [ ] **Step 4: ReleaseVersionService 에 메서드 구현**

`ReleaseVersionService.java` 의 마지막 메서드 다음에 추가:

```java
/**
 * 패치 생성 picker 용 — base 버전 범위 안의 빌드 후보 + 핫픽스 안내.
 */
public ReleaseVersionDto.BuildsInRangeResponse getBuildsInRange(
        String projectId, String releaseType, String customerCode,
        String fromVersion, String toVersion) {

    List<ReleaseVersion> builds = releaseVersionRepository.findBuildsInBaseRange(
            projectId, releaseType, customerCode, fromVersion, toVersion);
    List<ReleaseVersion> hotfixes = releaseVersionRepository.findHotfixesInBaseRange(
            projectId, releaseType, customerCode, fromVersion, toVersion);

    if (builds.isEmpty()) {
        return new ReleaseVersionDto.BuildsInRangeResponse(
                List.of(),
                List.of(),
                hotfixes.stream()
                        .map(h -> new ReleaseVersionDto.HotfixInRange(
                                h.getReleaseVersionId(), h.getFullVersion(), h.getHotfixVersion()))
                        .toList()
        );
    }

    java.util.List<Long> buildIds = builds.stream()
            .map(ReleaseVersion::getReleaseVersionId)
            .toList();
    var rows = releaseFileRepository.findBuildCategoryRows(buildIds);

    java.util.Set<Long> webBuildIds = rows.stream()
            .filter(r -> r.category() == com.ts.rm.domain.releasefile.enums.FileCategory.WEB)
            .map(r -> r.versionId())
            .collect(java.util.stream.Collectors.toSet());

    // engineName -> ordered list of buildVersionId (build_version DESC 보존)
    java.util.Map<String, java.util.List<Long>> engineToBuildIds = new java.util.LinkedHashMap<>();
    for (ReleaseVersion b : builds) {
        for (var r : rows) {
            if (!r.versionId().equals(b.getReleaseVersionId())) continue;
            if (r.category() != com.ts.rm.domain.releasefile.enums.FileCategory.ENGINE) continue;
            String engineName = r.subCategory() == null ? "UNKNOWN" : r.subCategory();
            engineToBuildIds.computeIfAbsent(engineName, k -> new java.util.ArrayList<>())
                    .add(b.getReleaseVersionId());
        }
    }

    // builds 의 순서가 DESC 이므로 buildId -> ReleaseVersion 매핑
    java.util.Map<Long, ReleaseVersion> byId = builds.stream()
            .collect(java.util.stream.Collectors.toMap(
                    ReleaseVersion::getReleaseVersionId, b -> b));

    java.util.List<ReleaseVersionDto.BuildCandidate> webCandidates = new java.util.ArrayList<>();
    boolean firstWeb = true;
    for (ReleaseVersion b : builds) {
        if (!webBuildIds.contains(b.getReleaseVersionId())) continue;
        webCandidates.add(toBuildCandidate(b, firstWeb));
        firstWeb = false;
    }

    java.util.List<ReleaseVersionDto.EngineGroup> engineGroups = new java.util.ArrayList<>();
    // 엔진명 알파벳 정렬(UNKNOWN 은 가장 뒤)
    java.util.List<String> engineNames = new java.util.ArrayList<>(engineToBuildIds.keySet());
    engineNames.sort((a, b) -> {
        if ("UNKNOWN".equals(a) && !"UNKNOWN".equals(b)) return 1;
        if ("UNKNOWN".equals(b) && !"UNKNOWN".equals(a)) return -1;
        return a.compareTo(b);
    });
    for (String engineName : engineNames) {
        java.util.List<Long> ids = engineToBuildIds.get(engineName);
        java.util.List<ReleaseVersionDto.BuildCandidate> cands = new java.util.ArrayList<>();
        boolean first = true;
        for (Long id : ids) {
            cands.add(toBuildCandidate(byId.get(id), first));
            first = false;
        }
        engineGroups.add(new ReleaseVersionDto.EngineGroup(engineName, cands));
    }

    java.util.List<ReleaseVersionDto.HotfixInRange> hotfixInfo = hotfixes.stream()
            .map(h -> new ReleaseVersionDto.HotfixInRange(
                    h.getReleaseVersionId(), h.getFullVersion(), h.getHotfixVersion()))
            .toList();

    return new ReleaseVersionDto.BuildsInRangeResponse(webCandidates, engineGroups, hotfixInfo);
}

private ReleaseVersionDto.BuildCandidate toBuildCandidate(ReleaseVersion b, boolean isLatest) {
    return new ReleaseVersionDto.BuildCandidate(
            b.getReleaseVersionId(),
            b.getFullVersion(),
            b.getBuildVersion(),
            b.getCreatedAt() != null ? b.getCreatedAt().toString() : null,
            isLatest
    );
}
```

- [ ] **Step 5: 테스트 통과 확인**

```
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.releaseversion.service.ReleaseVersionBuildServiceTest" 2>&1 | tail -15
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: `BUILD SUCCESSFUL` + `getBuildsInRange_groupsByEngine_andMarksLatest` 통과.

- [ ] **Step 6: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryCustom.java src/main/java/com/ts/rm/domain/releaseversion/repository/ReleaseVersionRepositoryImpl.java src/main/java/com/ts/rm/domain/releaseversion/service/ReleaseVersionService.java src/test/java/com/ts/rm/domain/releaseversion/service/ReleaseVersionBuildServiceTest.java
git -C release-manager-api commit -m "feat: getBuildsInRange — picker 후보 + 핫픽스 안내 응답 산출

ReleaseVersionService.getBuildsInRange. WEB 그룹/ENGINE 그룹별 후보를
build_version DESC, 첫 항목에 isLatest=true. 'UNKNOWN' 엔진은 정렬상
가장 뒤. 동시에 같은 범위의 핫픽스 메타정보를 hotfixesInRange 로 반환."
```

---

### Task 7: ReleaseVersionController — `GET builds-in-range` 엔드포인트

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionController.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionControllerDocs.java`

- [ ] **Step 1: ControllerDocs 에 시그니처 추가**

`ReleaseVersionControllerDocs.java` 에 추가 (다른 메서드들 옆):

```java
@Operation(summary = "범위 내 빌드 후보 조회",
        description = "패치 생성 picker 용. base from..to 범위의 빌드 후보를 WEB/ENGINE 그룹으로 반환. "
                + "같은 범위의 핫픽스 정보(별도 다운로드 안내용)도 함께 반환.")
ResponseEntity<ApiResponse<ReleaseVersionDto.BuildsInRangeResponse>> getBuildsInRange(
        @Parameter(description = "프로젝트 ID") String projectId,
        @Parameter(description = "릴리즈 타입 (STANDARD/CUSTOM)") String releaseType,
        @Parameter(description = "고객사 코드 (CUSTOM 시 필수)") String customerCode,
        @Parameter(description = "From base 버전 (예: 1.0.0)") String fromVersion,
        @Parameter(description = "To base 버전 (예: 1.1.0)") String toVersion
);
```

- [ ] **Step 2: Controller 구현 추가**

`ReleaseVersionController.java` 의 다른 GET 매핑들 옆에 추가:

```java
@Override
@GetMapping("/versions/builds-in-range")
public ResponseEntity<ApiResponse<ReleaseVersionDto.BuildsInRangeResponse>> getBuildsInRange(
        @org.springframework.web.bind.annotation.RequestParam String projectId,
        @org.springframework.web.bind.annotation.RequestParam String releaseType,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String customerCode,
        @org.springframework.web.bind.annotation.RequestParam String fromVersion,
        @org.springframework.web.bind.annotation.RequestParam String toVersion) {
    log.info("빌드 범위 조회 - projectId: {}, type: {}, customer: {}, {} -> {}",
            projectId, releaseType, customerCode, fromVersion, toVersion);
    var response = releaseVersionService.getBuildsInRange(
            projectId, releaseType.toUpperCase(), customerCode, fromVersion, toVersion);
    return ResponseEntity.ok(ApiResponse.success(response));
}
```

- [ ] **Step 3: 컴파일 + 테스트**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionController.java src/main/java/com/ts/rm/domain/releaseversion/controller/ReleaseVersionControllerDocs.java
git -C release-manager-api commit -m "feat: GET /api/releases/versions/builds-in-range 추가

패치 생성 picker 의 빌드 후보 + 핫픽스 안내를 반환. 쿼리 파라미터는
projectId/releaseType/customerCode/fromVersion/toVersion."
```

---

## Phase 3 — 패치 생성 입출력 / 비즈니스 로직 재배선

### Task 8: PatchDto — `GenerateRequest` 에 `BuildSelection`, response 필드 추가

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java`

- [ ] **Step 1: 입력/응답 타입 추가 + GenerateRequest 변경**

`PatchDto.java` 의 `GenerateRequest` 를 다음과 같이 변경:

```java
@Builder
@Schema(description = "패치 생성 요청")
public record GenerateRequest(
        @Schema(description = "프로젝트 ID", example = "infraeye2")
        @NotBlank @Size(max = 50)
        String projectId,

        @Schema(description = "릴리즈 타입", example = "standard")
        @NotBlank
        String type,

        @Schema(description = "고객사 ID (커스텀인 경우)", example = "1")
        Long customerId,

        @Schema(description = "시작 base 버전", example = "1.0.0")
        @NotBlank
        @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전 형식이 올바르지 않습니다 (예: 1.0.0)")
        String fromVersion,

        @Schema(description = "종료 base 버전", example = "1.1.0")
        @NotBlank
        @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전 형식이 올바르지 않습니다 (예: 1.0.0)")
        String toVersion,

        @Schema(description = "생성자 이메일")
        @NotBlank @Size(max = 100)
        String createdByEmail,

        @Schema(description = "설명")
        String description,

        @Schema(description = "패치 담당자 ID")
        Long assigneeId,

        @Schema(description = "패치 이름 (미입력 시 자동)")
        @Size(max = 100)
        String patchName,

        @Schema(description = "빌드 파일 선택. null/disabled=DB only 패치.")
        BuildSelection buildSelection
) {}

@Builder
@Schema(description = "빌드 파일 선택")
public record BuildSelection(
        @Schema(description = "선택 활성 여부 (false 면 DB only)")
        boolean enabled,
        @Schema(description = "WEB 빌드 (null = 포함 안 함)")
        WebSelection web,
        @Schema(description = "엔진별 선택 (포함 안 함 엔진은 배열에서 제외)")
        java.util.List<EngineSelection> engines
) {}

public record WebSelection(
        @NotNull Long buildVersionId
) {}

public record EngineSelection(
        @NotBlank @Size(max = 100) String engineName,
        @NotNull Long buildVersionId
) {}

@Schema(description = "패치 결과의 빌드 동행 정보")
public record IncludedBuildsInfo(
        WebSelection web,
        java.util.List<EngineSelection> engines
) {}

@Schema(description = "범위 내 핫픽스 (별도 다운로드 안내용)")
public record HotfixInRange(
        Long versionId,
        String fullVersion,
        int hotfixVersion
) {}
```

또한 `DetailResponse` 에 다음 필드를 추가 (말미에 `// 신규` 주석으로):

```java
@Schema(description = "Build-only 여부")
boolean isBuildOnly,
@Schema(description = "범위 내 핫픽스 (별도 다운로드 필요)")
java.util.List<HotfixInRange> hotfixesInRange,
@Schema(description = "포함된 빌드 요약")
IncludedBuildsInfo includedBuilds
```

> 주의: `DetailResponse` 의 기존 필드 순서는 유지하면서 위 3개를 끝에 append. `PatchDtoMapper` 의 매핑이 영향 받으면 후속 task 에서 수정.

- [ ] **Step 2: import 보강 + 컴파일**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

`@NotNull` import (`jakarta.validation.constraints.NotNull`) 가 없으면 추가. 컴파일 에러가 나면 메시지에 따라 import 보강. 기대: `BUILD SUCCESSFUL`.

- [ ] **Step 3: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/dto/PatchDto.java
git -C release-manager-api commit -m "feat: PatchDto.GenerateRequest 에 buildSelection 도입, response 확장

includeAllBuildVersions 제거, BuildSelection(enabled/web/engines) 으로
대체. DetailResponse 에 isBuildOnly, hotfixesInRange, includedBuilds
추가. PatchGenerationService 의 새 분기에 활용."
```

---

### Task 9: PatchService 시그니처 변경 + Controller 재배선

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchService.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/controller/PatchController.java`
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/controller/PatchControllerDocs.java`

- [ ] **Step 1: PatchService.generatePatchByVersion 시그니처 변경**

`PatchService.java` 의 `generatePatchByVersion(String, String, Long, String, String, String, String, Long, String, boolean)` 호출과 시그니처를 다음으로 교체 (마지막 `boolean includeAllBuildVersions` 제거하고 `BuildSelection buildSelection` 추가):

```java
public Patch generatePatchByVersion(
        String projectId,
        String type,
        Long customerId,
        String fromVersion,
        String toVersion,
        String createdByEmail,
        String description,
        Long assigneeId,
        String patchName,
        com.ts.rm.domain.patch.dto.PatchDto.BuildSelection buildSelection) {
    // 기존 메서드 본문 유지하되, 내부에서 PatchGenerationService 호출 인자를
    // includeAllBuildVersions -> buildSelection 으로 교체.
    // ...
}
```

본문 안에서 `PatchGenerationService.generatePatch(...)` (또는 동일 역할 메서드) 호출 인자를 `buildSelection` 으로 교체.

> 본 task 는 Service 안 PatchGenerationService 호출 라인에 `buildSelection` 까지만 전달. PatchGenerationService 내부 분기는 Task 10 에서 변경.

- [ ] **Step 2: PatchController + Docs 호출부 변경**

`PatchController.java` 의 `generatePatch` 메서드 안:

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

`log.info` 의 `IncludeAllBuildVersions` 토큰을 `BuildSelection` 으로 교체:

```java
log.info("패치 생성 요청 - Project: {}, From: {}, To: {}, Type: {}, PatchName: {}, BuildSelectionEnabled: {}",
        request.projectId(), request.fromVersion(), request.toVersion(), request.type(),
        request.patchName(),
        request.buildSelection() != null && request.buildSelection().enabled());
```

`PatchControllerDocs.java` 의 시그니처 변경 사항이 있다면 동기화.

- [ ] **Step 3: 컴파일**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/service/PatchService.java src/main/java/com/ts/rm/domain/patch/controller/PatchController.java src/main/java/com/ts/rm/domain/patch/controller/PatchControllerDocs.java
git -C release-manager-api commit -m "refactor: PatchService.generatePatchByVersion 의 boolean 인자를 BuildSelection 으로 교체

includeAllBuildVersions 제거, buildSelection 파라미터로 통일. 컨트롤러
로깅도 토글 enabled 만 출력하도록 수정. PatchGenerationService 내부
분기는 다음 task 에서 정리."
```

---

### Task 10: PatchGenerationService — 새 buildSelection 분기 + 핫픽스 제외 + ETC 동행 + 응답 채움

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java`
- Test: `release-manager-api/src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceVersionLogicTest.java` (excluded 아닌 파일 — 사용 가능)

본 task 는 코드 분량이 가장 크다. 단계를 둘로 쪼갠다 (10a, 10b).

#### Task 10a — 빌드 파일 포함 분기 / 핫픽스 제외

- [ ] **Step 1: 분리 가능한 헬퍼로 추출 + 단위 테스트**

`PatchGenerationService` 의 빌드 분기 로직은 service 가 파일시스템 + 다수 repo 의존이라 통째로 mock 단위 테스트하기 어렵다. 본 task 의 단위 테스트는 **buildSelection 해석 로직**을 service 안의 작은 순수 메서드로 분리해 그 부분만 검증한다.

`PatchGenerationService.java` 에 신규 package-private static 메서드 추가:

```java
/**
 * buildSelection 으로부터 (1) 활성 여부, (2) 선택된 빌드 ID 합집합,
 * (3) 엔진별 매핑을 산출. service 본문 분기에서 사용.
 */
static SelectionPlan resolveSelectionPlan(
        com.ts.rm.domain.patch.dto.PatchDto.BuildSelection sel) {
    if (sel == null || !sel.enabled()) return SelectionPlan.disabled();
    java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
    Long webId = null;
    if (sel.web() != null) {
        webId = sel.web().buildVersionId();
        ids.add(webId);
    }
    java.util.Map<String, Long> enginesByName = new java.util.LinkedHashMap<>();
    if (sel.engines() != null) {
        for (var e : sel.engines()) {
            enginesByName.put(e.engineName(), e.buildVersionId());
            ids.add(e.buildVersionId());
        }
    }
    return new SelectionPlan(true, webId, enginesByName, ids);
}

record SelectionPlan(
        boolean enabled,
        Long webBuildVersionId,
        java.util.Map<String, Long> enginesByName,
        java.util.Set<Long> selectedBuildIds
) {
    static SelectionPlan disabled() {
        return new SelectionPlan(false, null, java.util.Map.of(), java.util.Set.of());
    }
}
```

`PatchGenerationServiceVersionLogicTest.java` (excluded 아닌 파일) 에 추가:

```java
@Test
@DisplayName("resolveSelectionPlan: null/disabled 입력은 disabled SelectionPlan")
void selectionPlan_nullOrDisabled() {
    var plan1 = PatchGenerationService.resolveSelectionPlan(null);
    assertThat(plan1.enabled()).isFalse();
    assertThat(plan1.selectedBuildIds()).isEmpty();

    var plan2 = PatchGenerationService.resolveSelectionPlan(
            new PatchDto.BuildSelection(false, null, java.util.List.of()));
    assertThat(plan2.enabled()).isFalse();
    assertThat(plan2.selectedBuildIds()).isEmpty();
}

@Test
@DisplayName("resolveSelectionPlan: web 만 선택 시 selectedBuildIds = {webBuildId}")
void selectionPlan_webOnly() {
    var sel = new PatchDto.BuildSelection(
            true,
            new PatchDto.WebSelection(42L),
            java.util.List.of()
    );
    var plan = PatchGenerationService.resolveSelectionPlan(sel);
    assertThat(plan.enabled()).isTrue();
    assertThat(plan.webBuildVersionId()).isEqualTo(42L);
    assertThat(plan.enginesByName()).isEmpty();
    assertThat(plan.selectedBuildIds()).containsExactly(42L);
}

@Test
@DisplayName("resolveSelectionPlan: web + engines 합집합")
void selectionPlan_webAndEngines() {
    var sel = new PatchDto.BuildSelection(
            true,
            new PatchDto.WebSelection(42L),
            java.util.List.of(
                    new PatchDto.EngineSelection("NC_SMS", 42L),
                    new PatchDto.EngineSelection("NC_FAULT_MS", 41L)
            )
    );
    var plan = PatchGenerationService.resolveSelectionPlan(sel);
    assertThat(plan.enabled()).isTrue();
    assertThat(plan.enginesByName())
            .containsEntry("NC_SMS", 42L)
            .containsEntry("NC_FAULT_MS", 41L);
    assertThat(plan.selectedBuildIds()).containsExactlyInAnyOrder(42L, 41L);  // 합집합
}

@Test
@DisplayName("resolveSelectionPlan: 모두 미포함이지만 enabled=true 면 selectedBuildIds 빈 집합")
void selectionPlan_enabledButEmpty() {
    var sel = new PatchDto.BuildSelection(true, null, java.util.List.of());
    var plan = PatchGenerationService.resolveSelectionPlan(sel);
    assertThat(plan.enabled()).isTrue();
    assertThat(plan.selectedBuildIds()).isEmpty();
}
```

> 빌드 파일 복사·DB 시퀀스·핫픽스 제외의 end-to-end 검증은 단위 테스트가 아니라 Phase 6 Task 18 의 수동 시나리오로 다룬다. 단위 테스트의 역할은 SelectionPlan 변환 로직 보증까지.

- [ ] **Step 2: 테스트 실패 확인**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchGenerationServiceVersionLogicTest" 2>&1 | tail -25
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
```

기대: 컴파일 에러(`resolveSelectionPlan` / `SelectionPlan` 미존재).

- [ ] **Step 3: PatchGenerationService 재배선**

> 본 task 는 PatchGenerationService 의 빌드 분기를 통째로 갈아끼운다. 현재 코드(line 827~951 부근)의 `copySqlFiles` 와 그 안의 `if (!includeAllBuildVersions ...)` 블록을 보고 다음 변경을 진행한다.

**1. 시그니처 정리.** `generatePatchInternal(...)` (또는 동급 private 메서드) 의 마지막 인자 `boolean includeAllBuildVersions` 를 `PatchDto.BuildSelection buildSelection` 으로 교체. 호출자(`generatePatch`, `generateCustomPatch`) 도 동일 패턴으로 따라 변경.

**2. 누적 base 시퀀스.** 본 흐름에서 `findVersionsBetween(projectId, releaseType, fromVersion, toVersion)` 호출을 제거하고 `findVersionsBetweenExcludingHotfixes(projectId, releaseType, fromVersion, toVersion)` 만 사용한다(이미 Repository 에 존재).

**3. 빌드 파일 복사 메서드 신규.** `copySqlFiles` 안의 빌드 분기(WEB 마지막 1개 / ENGINE sub_category 별 마지막 1개) 를 모두 제거하고, 다음 신규 메서드로 교체:

```java
/**
 * buildSelection 에 따라 outputDir 아래에 web/engine/etc 파일을 복사한다.
 * - WEB:   web 의 buildVersionId 의 file_category=WEB 파일을 outputDir/web/ 으로
 * - ENGINE: engines[*] 각 항목의 (buildVersionId, sub_category=engineName) 매칭
 *           file_category=ENGINE 파일을 outputDir/engine/{engineName}/ 으로
 * - ETC:   선택된 빌드 ID 의 합집합에 대해 file_category=ETC 파일을 outputDir/etc/ 으로
 */
private void copySelectedBuildFiles(SelectionPlan plan, java.nio.file.Path outputDir)
        throws java.io.IOException {
    if (!plan.enabled() || plan.selectedBuildIds().isEmpty()) return;

    // 1. WEB
    if (plan.webBuildVersionId() != null) {
        java.util.List<ReleaseFile> webFiles = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdAndFileCategory(
                        plan.webBuildVersionId(), FileCategory.WEB);
        copyReleaseFiles(webFiles, outputDir);  // 기존 헬퍼 사용
    }

    // 2. ENGINE
    for (var entry : plan.enginesByName().entrySet()) {
        String engineName = entry.getKey();
        Long buildId = entry.getValue();
        java.util.List<ReleaseFile> engineFiles = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdAndFileCategoryAndSubCategory(
                        buildId, FileCategory.ENGINE, engineName);
        copyReleaseFiles(engineFiles, outputDir);
    }

    // 3. ETC 동행 — 선택된 빌드 ID 합집합 각각의 ETC
    for (Long buildId : plan.selectedBuildIds()) {
        java.util.List<ReleaseFile> etcFiles = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdAndFileCategory(
                        buildId, FileCategory.ETC);
        copyReleaseFiles(etcFiles, outputDir);
    }
}
```

> 위 finder 메서드(`findAllByReleaseVersion_ReleaseVersionIdAndFileCategory`, `…AndSubCategory`) 가 ReleaseFileRepository 에 없으면 같이 추가한다(spring data JPA 명명 규약으로 자동 생성). `copyReleaseFiles(List<ReleaseFile>, Path outputDir)` 는 기존 `copyBuildFilesFromFileSystem` 의 단일 빌드 처리 부분을 재사용 가능한 헬퍼로 분리해 사용.

**4. build-only 분기.** `betweenVersions` 가 비어있고 `from.id == to.id` 인 경우 기존 `isSameBaseVersion` 가드(line 1008~1012) 를 그대로 활용하여 DB 스크립트 단계는 skip. 이후 `copySelectedBuildFiles(plan, outputDir)` 만 수행.

**5. 호출 진입점.** `copySqlFiles(...)` 의 시그니처에서 `boolean includeAllBuildVersions` 제거. 빌드 분기 코드 블록 제거. `copySqlFiles` 종료 직후 (또는 generatePatchInternal 의 적절한 위치) `copySelectedBuildFiles(resolveSelectionPlan(buildSelection), outputDir)` 호출.

**6. 응답 enrich (서비스 레벨).** `generatePatchInternal` 이 `Patch` 저장 후 `PatchDto.DetailResponse` 를 직접 조립하던 부분(또는 호출자) 에서 다음 3 필드를 채운다:

```java
boolean isBuildOnly = (fromVersionEntity.getReleaseVersionId()
        .equals(toVersionEntity.getReleaseVersionId()));

java.util.List<ReleaseVersion> hotfixesInRange = releaseVersionRepository
        .findHotfixesInBaseRange(projectId, releaseType, customerCode,
                fromVersion, toVersion);
java.util.List<PatchDto.HotfixInRange> hotfixInfo = hotfixesInRange.stream()
        .map(h -> new PatchDto.HotfixInRange(
                h.getReleaseVersionId(), h.getFullVersion(), h.getHotfixVersion()))
        .toList();

PatchDto.IncludedBuildsInfo included = plan.enabled()
        ? new PatchDto.IncludedBuildsInfo(
                plan.webBuildVersionId() != null
                        ? new PatchDto.WebSelection(plan.webBuildVersionId())
                        : null,
                plan.enginesByName().entrySet().stream()
                        .map(e -> new PatchDto.EngineSelection(e.getKey(), e.getValue()))
                        .toList())
        : null;

// PatchDtoMapper 결과(record copy) + 새 3 필드 = 새 DetailResponse 인스턴스 생성.
// (PatchDto.DetailResponse 가 record 라서 wither 가 없으므로 모든 필드를 다시 넘긴다.)
```

> Mapper 의 자동 매핑 대상에서 새 3 필드는 제외(`@Mapping(target = "isBuildOnly", ignore = true)` 등) — 후처리에서 채움.

**7. 키워드 정리.** 모든 main 코드 / 활성 테스트에서 `includeAllBuildVersions` 키워드 검색 후 제거. exclude 된 깨진 테스트 파일은 손대지 않음(사전 부채로 별도 정리).

- [ ] **Step 4: PatchDtoMapper 보강**

`PatchDtoMapper.java` 의 `toDetailResponse` 가 새 3 필드(`isBuildOnly`, `hotfixesInRange`, `includedBuilds`)를 채우도록 수정. 빈 값 기본은 `false`, `List.of()`, `null`.

엔티티 `Patch` 가 기존에 이 필드들을 갖지 않는다면, 응답 enrich 는 service 레벨에서 후처리(매퍼 결과를 받아 새 record 로 재구성)하는 편이 cleaner. 본 plan 은 후자 권장.

- [ ] **Step 5: 컴파일 + 테스트**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew test --tests "com.ts.rm.domain.patch.service.PatchGenerationServiceVersionLogicTest" --tests "com.ts.rm.domain.releaseversion.service.BuildFileServiceTest" --tests "com.ts.rm.domain.releaseversion.service.ReleaseVersionBuildServiceTest" 2>&1 | tail -30
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: `BUILD SUCCESSFUL`. 핫픽스 누락은 정상(스펙 결정).

- [ ] **Step 6: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java src/main/java/com/ts/rm/domain/patch/mapper/PatchDtoMapper.java src/test/java/com/ts/rm/domain/patch/service/PatchGenerationServiceVersionLogicTest.java
git -C release-manager-api commit -m "feat: PatchGenerationService 를 buildSelection 기반으로 재배선

- 누적 DB 시퀀스에서 findVersionsBetweenExcludingHotfixes 만 사용
  (핫픽스는 별도 다운로드 artifact 로 분리).
- 빌드 파일 복사를 copySelectedBuildFiles 로 분리: WEB 1개 + 엔진별 1개
  + 그 빌드들의 ETC 동행.
- from==to + buildSelection 1개 이상 → build-only 패치(DB 스크립트 0).
- 응답 enrich: isBuildOnly / hotfixesInRange / includedBuilds.
- 기존 includeAllBuildVersions 분기 및 호출 제거."
```

---

#### Task 10b — README / SiteVersionFile 안내 갱신

**Files:**
- Modify: `release-manager-api/src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java`

- [ ] **Step 1: README 생성 부분에 핫픽스 안내 + 포함된 빌드 요약 추가**

`generateReadme` / `generateCustomReadme` 안에 다음 섹션 추가 (문자열 빌더에 append):

```
## 포함된 빌드 (Build files)

WEB: 1.1.0.260428
ENGINE:
  - NC_SMS:        1.1.0.260428
  - NC_FAULT_MS:   1.1.0.260427
  - NC_INV:        포함 안 함

## 별도 적용 필요 — 핫픽스 (Hotfixes)

이 패치 범위(1.0.0 ~ 1.1.0) 안에 포함되지 않은 핫픽스가 있습니다.
필요한 경우 버전 관리 화면에서 직접 다운로드/적용해 주세요.

  - 1.0.0.1
  - 1.0.0.2
```

(섹션은 hotfixesInRange 가 비어있지 않을 때만 출력. 포함된 빌드는 buildSelection.enabled 일 때만.)

- [ ] **Step 2: 컴파일**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew compileJava 2>&1 | tail -10
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

- [ ] **Step 3: 커밋**

```
git -C release-manager-api add src/main/java/com/ts/rm/domain/patch/service/PatchGenerationService.java
git -C release-manager-api commit -m "feat: 패치 README 에 포함된 빌드/별도 적용 핫픽스 섹션 추가

운영자가 패치 ZIP 만 받아도 'WEB/엔진별 어떤 빌드가 들어갔는지' 와
'이 범위에 핫픽스 N건이 있고 별도 적용해야 한다' 를 즉시 알 수 있도록
README 에 명시."
```

---

## Phase 4 — 프론트엔드 타입 / API / 쿼리

### Task 11: FE — 빌드/패치 타입 추가

**Files:**
- Modify: `release-manager-web/src/entities/releases/release/model/types.ts`
- Modify: `release-manager-web/src/entities/patches/patch/model/types.ts`

- [ ] **Step 1: releases types 에 BuildCandidate / EngineGroup / BuildsInRangeResponse 추가**

`release/model/types.ts` 의 끝에 추가:

```ts
/** 패치 picker 용 빌드 후보 */
export interface BuildCandidate {
  buildVersionId: number
  fullVersion: string
  buildVersion: number
  createdAt: string | null
  isLatest: boolean
}

/** 엔진별 후보 그룹 */
export interface EngineGroup {
  engineName: string
  candidates: BuildCandidate[]
}

/** 범위 내 핫픽스 안내 */
export interface HotfixInRange {
  versionId: number
  fullVersion: string
  hotfixVersion: number
}

/** 패치 picker 후보 응답 (백엔드 BuildsInRangeResponse) */
export interface BuildsInRangeResponse {
  web: BuildCandidate[]
  engines: EngineGroup[]
  hotfixesInRange: HotfixInRange[]
}
```

- [ ] **Step 2: patch types 에 BuildSelection 추가 + GenerateRequest 변경**

`patches/patch/model/types.ts` 변경:

```ts
/** WEB 빌드 선택 (null = 포함 안 함) */
export interface WebSelection {
  buildVersionId: number
}

/** 엔진별 빌드 선택 */
export interface EngineSelection {
  engineName: string
  buildVersionId: number
}

/** 빌드 파일 선택 (toggle ON 시) */
export interface BuildSelection {
  enabled: boolean
  web: WebSelection | null
  engines: EngineSelection[]
}

export interface CumulativePatchGenerateRequest {
  projectId: string
  type: 'standard' | 'custom'
  customerId?: number
  fromVersion: string
  toVersion: string
  createdByEmail: string
  assigneeId?: number
  description?: string
  patchName?: string
  /** 토글 OFF 또는 enabled=false 면 DB only */
  buildSelection?: BuildSelection
}
```

기존 `includeAllBuildVersions` 필드 제거.

또한 `CumulativePatchDetail` 에 추가:

```ts
  isBuildOnly?: boolean
  hotfixesInRange?: HotfixInRange[]
  includedBuilds?: {
    web: WebSelection | null
    engines: EngineSelection[]
  }
```

`HotfixInRange` 는 releases types 에서 import:

```ts
import type { HotfixInRange } from '@/entities/releases/release/model/types'
```

- [ ] **Step 3: type-check**

```
cd release-manager-web && npm run type-check 2>&1 | tail -20
```

기대: PASS.

- [ ] **Step 4: 커밋**

```
git -C release-manager-web add src/entities/releases/release/model/types.ts src/entities/patches/patch/model/types.ts
git -C release-manager-web commit -m "feat: 패치 picker 용 BuildSelection / BuildsInRangeResponse 타입 추가

릴리즈 도메인에 BuildCandidate/EngineGroup/HotfixInRange,
패치 도메인에 WebSelection/EngineSelection/BuildSelection.
CumulativePatchGenerateRequest 의 includeAllBuildVersions 는
buildSelection 으로 교체."
```

---

### Task 12: FE — `getBuildsInRange` API + 훅

**Files:**
- Modify: `release-manager-web/src/entities/releases/release/api/releaseApi.ts`
- Modify: `release-manager-web/src/entities/releases/release/queries/releaseQueries.ts`

- [ ] **Step 1: releaseApi.ts 에 메서드 추가**

```ts
import type { BuildsInRangeResponse } from '../model/types'

// 기존 ENDPOINTS 객체 안에 추가:
const ENDPOINTS = {
  ...,
  buildsInRange: '/api/releases/versions/builds-in-range',
} as const

// 기존 export 객체 안에 추가:
getBuildsInRange: async (params: {
  projectId: string
  releaseType: 'STANDARD' | 'CUSTOM'
  customerCode?: string | null
  fromVersion: string
  toVersion: string
}): Promise<BuildsInRangeResponse> => {
  const qs = new URLSearchParams()
  qs.append('projectId', params.projectId)
  qs.append('releaseType', params.releaseType)
  if (params.customerCode) qs.append('customerCode', params.customerCode)
  qs.append('fromVersion', params.fromVersion)
  qs.append('toVersion', params.toVersion)
  return await apiClient.get<BuildsInRangeResponse>(
    `${ENDPOINTS.buildsInRange}?${qs.toString()}`
  )
},
```

- [ ] **Step 2: releaseQueries.ts 에 useBuildsInRange 추가**

```ts
import type { BuildsInRangeResponse } from '../model/types'

// releaseKeys 확장
export const releaseKeys = {
  ...,
  buildsInRange: (params: {
    projectId: string
    releaseType: 'STANDARD' | 'CUSTOM'
    customerCode?: string | null
    fromVersion: string
    toVersion: string
  }) => [...releaseKeys.all, 'buildsInRange', params] as const,
}

export const useBuildsInRange = (
  params: {
    projectId: string
    releaseType: 'STANDARD' | 'CUSTOM'
    customerCode?: string | null
    fromVersion: string
    toVersion: string
  } | null,
  enabled = true,
) =>
  useQuery({
    queryKey: params ? releaseKeys.buildsInRange(params) : ['releases', 'buildsInRange', 'noop'],
    queryFn: () => releaseApi.getBuildsInRange(params!),
    enabled: enabled && !!params && !!params.fromVersion && !!params.toVersion,
  })
```

- [ ] **Step 3: type-check**

```
cd release-manager-web && npm run type-check 2>&1 | tail -10
```

기대: PASS.

- [ ] **Step 4: 커밋**

```
git -C release-manager-web add src/entities/releases/release/api/releaseApi.ts src/entities/releases/release/queries/releaseQueries.ts
git -C release-manager-web commit -m "feat: useBuildsInRange 훅 + getBuildsInRange API 추가

picker 가 현재 from/to 범위의 WEB/ENGINE 빌드 후보와 핫픽스 안내를
조회하는 진입점."
```

---

## Phase 5 — 프론트엔드 UI

### Task 13: FE — 패치 셀렉터 base-only 필터링

**Files:**
- Modify: `release-manager-web/src/pages/patches/PatchesPage.tsx` (`getVersionsFromTree` 헬퍼)

현재 `getVersionsFromTree` 는 빌드/핫픽스 fullVersion 까지 노출. 본 task 에서 base 만 노출하도록 필터.

- [ ] **Step 1: 헬퍼 변경**

`PatchesPage.tsx` line 98 근방의 `getVersionsFromTree` 본문에서 빌드/핫픽스 행을 추가하던 분기를 제거. 결과 배열은 base 버전(`hotfixVersion === 0 && (buildVersion ?? 0) === 0`) 만 포함.

```ts
// 파일 트리에서 base 만 추출
function getVersionsFromTree(tree: ReleaseTreeResponse | undefined): VersionSelectOption[] {
  if (!tree) return []
  const out: VersionSelectOption[] = []
  for (const group of tree.majorMinorGroups) {
    for (const v of group.versions) {
      // 빌드/핫픽스는 picker 와 별도 화면에서 다룬다 → 셀렉터에는 base 만
      out.push({ versionId: v.versionId, version: v.version, isApproved: v.isApproved })
    }
  }
  // 정렬: major.minor.patch 4-part 보정 제거. base 만이라 3-part 정렬로 충분.
  return out.sort((a, b) => compareBaseVersion(b.version, a.version))
}
```

`compareBaseVersion` 헬퍼는 기존 `compareVersionStrings` 가 있으면 그것을 사용. 없으면 inline:

```ts
function compareBaseVersion(a: string, b: string): number {
  const pa = a.split('.').map(Number)
  const pb = b.split('.').map(Number)
  for (let i = 0; i < 3; i++) {
    if ((pa[i] ?? 0) !== (pb[i] ?? 0)) return (pa[i] ?? 0) - (pb[i] ?? 0)
  }
  return 0
}
```

- [ ] **Step 2: type-check**

```
cd release-manager-web && npm run type-check 2>&1 | tail -10
```

- [ ] **Step 3: 커밋**

```
git -C release-manager-web add src/pages/patches/PatchesPage.tsx
git -C release-manager-web commit -m "refactor: 패치 셀렉터에서 핫픽스/빌드 제외, base 버전만 노출

빌드는 picker, 핫픽스는 별도 다운로드. 셀렉터의 책임은 base 시퀀스 입력."
```

---

### Task 14: FE — `BuildPickerSection` 컴포넌트 신규

**Files:**
- Create: `release-manager-web/src/features/patches/patch-management/ui/BuildPickerSection.tsx`
- Create: `release-manager-web/src/features/patches/patch-management/ui/BuildPickerEngineRow.tsx`

- [ ] **Step 1: BuildPickerSection.tsx 작성**

```tsx
import { useEffect, useMemo } from 'react'
import { Hammer } from 'lucide-react'

import type {
  BuildsInRangeResponse,
  BuildCandidate,
} from '@/entities/releases/release/model/types'
import type { BuildSelection } from '@/entities/patches/patch/model/types'

import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { RadioGroup, RadioGroupItem } from '@/shared/ui/radio-group'
import { Label } from '@/shared/ui/label'

import { BuildPickerEngineRow } from './BuildPickerEngineRow'

interface Props {
  data: BuildsInRangeResponse
  value: BuildSelection
  onChange: (next: BuildSelection) => void
}

const NONE = '__NONE__'  // "포함 안 함" sentinel

export function BuildPickerSection({ data, value, onChange }: Props) {
  // value 가 enabled=false 거나 비어있을 때 자동 preselect
  useEffect(() => {
    if (!value.enabled) return
    const filledWeb =
      value.web ?? (data.web[0] ? { buildVersionId: data.web[0].buildVersionId } : null)
    const filledEngines = data.engines.map((g) => {
      const existing = value.engines.find((e) => e.engineName === g.engineName)
      if (existing) return existing
      const latest = g.candidates[0]
      return latest
        ? { engineName: g.engineName, buildVersionId: latest.buildVersionId }
        : null
    }).filter(Boolean) as BuildSelection['engines']

    // 외부에서 받은 값이 이미 채워져 있으면 변경 안 함
    if (
      filledWeb?.buildVersionId === value.web?.buildVersionId &&
      filledEngines.length === value.engines.length &&
      filledEngines.every((e, i) =>
        e.engineName === value.engines[i]?.engineName &&
        e.buildVersionId === value.engines[i]?.buildVersionId)
    ) return
    onChange({ ...value, web: filledWeb, engines: filledEngines })
  }, [data, value, onChange])

  const handleWebChange = (raw: string) => {
    if (raw === NONE) onChange({ ...value, web: null })
    else onChange({ ...value, web: { buildVersionId: Number(raw) } })
  }

  const handleEngineChange = (engineName: string, buildVersionId: number | null) => {
    const others = value.engines.filter((e) => e.engineName !== engineName)
    if (buildVersionId == null) onChange({ ...value, engines: others })
    else onChange({ ...value, engines: [...others, { engineName, buildVersionId }] })
  }

  const selectAllLatest = () => {
    onChange({
      enabled: true,
      web: data.web[0] ? { buildVersionId: data.web[0].buildVersionId } : null,
      engines: data.engines
        .map((g) => g.candidates[0]
          ? { engineName: g.engineName, buildVersionId: g.candidates[0].buildVersionId }
          : null)
        .filter(Boolean) as BuildSelection['engines'],
    })
  }

  const clearAll = () => onChange({ enabled: true, web: null, engines: [] })

  const webValue = value.web ? String(value.web.buildVersionId) : NONE

  if (data.web.length === 0 && data.engines.length === 0) {
    return (
      <div className="text-xs text-muted-foreground italic px-2 py-3">
        이 범위에 빌드 후보가 없습니다.
      </div>
    )
  }

  return (
    <div className="rounded-md border bg-card/50 p-3 space-y-4">
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>모든 빌드 자동 선택됨(최신). 변경할 항목만 클릭하세요.</span>
        <div className="flex gap-2">
          <Button type="button" size="sm" variant="ghost" onClick={selectAllLatest}>
            모두 최신
          </Button>
          <Button type="button" size="sm" variant="ghost" onClick={clearAll}>
            모두 해제
          </Button>
        </div>
      </div>

      {data.web.length > 0 && (
        <section>
          <div className="flex items-center gap-2 mb-2">
            <Badge variant="web" className="text-[10px]">WEB</Badge>
          </div>
          <RadioGroup value={webValue} onValueChange={handleWebChange} className="space-y-1">
            {data.web.map((c) => <WebOption key={c.buildVersionId} c={c} />)}
            <div className="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-accent">
              <RadioGroupItem id="web-none" value={NONE} />
              <Label htmlFor="web-none" className="text-sm cursor-pointer">포함 안 함</Label>
            </div>
          </RadioGroup>
        </section>
      )}

      {data.engines.length > 0 && (
        <section>
          <div className="flex items-center gap-2 mb-2">
            <Badge variant="engine" className="text-[10px]">ENGINE</Badge>
            <span className="text-xs text-muted-foreground">({data.engines.length}개)</span>
          </div>
          <div className="rounded border divide-y">
            {data.engines.map((group) => (
              <BuildPickerEngineRow
                key={group.engineName}
                group={group}
                selectedBuildId={
                  value.engines.find((e) => e.engineName === group.engineName)?.buildVersionId ?? null
                }
                onChange={(buildId) => handleEngineChange(group.engineName, buildId)}
              />
            ))}
          </div>
        </section>
      )}
    </div>
  )
}

function WebOption({ c }: { c: BuildCandidate }) {
  return (
    <div className="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-accent">
      <RadioGroupItem id={`web-${c.buildVersionId}`} value={String(c.buildVersionId)} />
      <Label htmlFor={`web-${c.buildVersionId}`} className="text-sm cursor-pointer flex-1 flex items-center gap-2">
        <Hammer className="h-3.5 w-3.5 text-blue-500" />
        {c.fullVersion}
        {c.isLatest && <span className="text-[10px] text-amber-500">✦최신</span>}
      </Label>
    </div>
  )
}
```

- [ ] **Step 2: BuildPickerEngineRow.tsx 작성**

```tsx
import { Check, ChevronsUpDown, Hammer } from 'lucide-react'

import type { EngineGroup } from '@/entities/releases/release/model/types'

import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'

interface Props {
  group: EngineGroup
  selectedBuildId: number | null  // null = 포함 안 함
  onChange: (buildVersionId: number | null) => void
}

export function BuildPickerEngineRow({ group, selectedBuildId, onChange }: Props) {
  const selected = selectedBuildId
    ? group.candidates.find((c) => c.buildVersionId === selectedBuildId)
    : null

  return (
    <div className="flex items-center justify-between gap-2 px-2 py-1.5 hover:bg-accent">
      <div className="flex items-center gap-2 text-sm font-medium min-w-0">
        <Hammer className="h-3.5 w-3.5 text-blue-500 shrink-0" />
        <span className="truncate">{group.engineName}</span>
      </div>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button type="button" variant="ghost" size="sm" className="text-xs h-7 px-2 gap-1">
            {selected ? (
              <>
                {selected.fullVersion}
                {selected.isLatest && <span className="text-[10px] text-amber-500">✦최신</span>}
              </>
            ) : (
              <span className="text-muted-foreground">포함 안 함</span>
            )}
            <ChevronsUpDown className="h-3 w-3 opacity-60" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="min-w-[16rem]">
          {group.candidates.map((c) => (
            <DropdownMenuItem
              key={c.buildVersionId}
              onSelect={() => onChange(c.buildVersionId)}
              className="text-xs"
            >
              <span className="flex-1">{c.fullVersion}</span>
              {c.isLatest && <span className="text-[10px] text-amber-500 mr-2">✦최신</span>}
              {selectedBuildId === c.buildVersionId && <Check className="h-3 w-3" />}
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onSelect={() => onChange(null)}
            className="text-xs text-muted-foreground"
          >
            <span className="flex-1">포함 안 함</span>
            {selectedBuildId === null && <Check className="h-3 w-3" />}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}
```

- [ ] **Step 3: type-check**

```
cd release-manager-web && npm run type-check 2>&1 | tail -15
```

기대: PASS. shadcn 의 `RadioGroup`/`DropdownMenu`/`Badge` `web|engine` variant 가 이미 있는지 확인 필요(없으면 임시로 `default` 사용).

- [ ] **Step 4: 커밋**

```
git -C release-manager-web add src/features/patches/patch-management/ui/BuildPickerSection.tsx src/features/patches/patch-management/ui/BuildPickerEngineRow.tsx
git -C release-manager-web commit -m "feat: BuildPickerSection 컴포넌트 신규

WEB radio + 엔진별 컴팩트 행(드롭다운). 자동 preselect(최신),
'모두 최신'/'모두 해제' 일괄 액션. 각 항목에 '포함 안 함' 옵션."
```

---

### Task 15: FE — PatchCreateForm 통합 + 검증 + build-only 인디케이터

**Files:**
- Modify: `release-manager-web/src/features/patches/patch-management/ui/PatchCreateForm.tsx`
- Modify: `release-manager-web/src/features/patches/patch-management/model/types.ts`
- Modify: `release-manager-web/src/features/patches/patch-management/model/validation.ts` (있다면)

- [ ] **Step 1: PatchCreateFormData 에 buildSelection 추가**

`features/patches/patch-management/model/types.ts`:

```ts
import type { BuildSelection } from '@/entities/patches/patch/model/types'

export interface PatchCreateFormData {
  fromVersion: string
  toVersion: string
  customerCode?: string  // 기존 유지
  assigneeId?: number | string
  patchName?: string
  description?: string
  buildSelection: BuildSelection
}

export const INITIAL_BUILD_SELECTION: BuildSelection = {
  enabled: false,  // 기본 OFF
  web: null,
  engines: [],
}
```

기존 `INITIAL_FORM_DATA`/`INITIAL_STANDARD_FORM` 등에 `buildSelection: INITIAL_BUILD_SELECTION` 추가. `includeAllBuildVersions` 키 제거.

- [ ] **Step 2: PatchCreateForm.tsx 의 토글 영역 교체**

기존 토글 영역(line 207~218 부근, “모든 버전의 빌드 파일 포함” 라벨/Switch)을 다음으로 교체:

```tsx
{/* 빌드 파일 포함 토글 */}
<div className="rounded-lg border bg-card p-4 space-y-3">
  <div className="flex items-center justify-between">
    <div className="space-y-0.5">
      <Label htmlFor="buildSelectionEnabled" className="cursor-pointer font-medium">
        빌드 파일 포함
      </Label>
      <p className="text-xs text-muted-foreground">
        토글을 켜면 WEB/엔진별 빌드를 직접 선택할 수 있습니다 (자동 preselect=최신).
      </p>
    </div>
    <Switch
      id="buildSelectionEnabled"
      checked={formData.buildSelection.enabled}
      onCheckedChange={(checked) => onFormDataChange({
        ...formData,
        buildSelection: { ...formData.buildSelection, enabled: checked },
      })}
    />
  </div>

  {formData.buildSelection.enabled && buildsInRange.data && (
    <BuildPickerSection
      data={buildsInRange.data}
      value={formData.buildSelection}
      onChange={(next) => onFormDataChange({ ...formData, buildSelection: next })}
    />
  )}

  {/* Build-only 인디케이터 */}
  {formData.fromVersion && formData.fromVersion === formData.toVersion && (
    <div className="text-xs text-blue-600 dark:text-blue-400 flex items-center gap-1">
      ⓘ 빌드 전용 패치 — DB 스크립트 없이 빌드 파일만 생성됩니다
    </div>
  )}

  {/* 핫픽스 안내 */}
  {(buildsInRange.data?.hotfixesInRange?.length ?? 0) > 0 && (
    <div className="text-xs text-amber-600 dark:text-amber-400">
      ⚠ 이 범위에 핫픽스 {buildsInRange.data!.hotfixesInRange.length}건 있음.
      핫픽스는 버전 관리 화면에서 별도로 다운로드/적용해 주세요.
      ({buildsInRange.data!.hotfixesInRange.map((h) => h.fullVersion).join(', ')})
    </div>
  )}
</div>
```

상단에 hooks 추가:

```tsx
import { useBuildsInRange } from '@/entities/releases/release'
import { BuildPickerSection } from './BuildPickerSection'

// 컴포넌트 본문 안:
const buildsInRange = useBuildsInRange(
  formData.buildSelection.enabled && formData.fromVersion && formData.toVersion
    ? {
        projectId,
        releaseType: 'STANDARD',
        customerCode: null,
        fromVersion: formData.fromVersion,
        toVersion: formData.toVersion,
      }
    : null,
)
```

(커스텀 패치 폼인 `CustomPatchCreateForm` 도 동일 패턴으로 후속 적용. 본 task 는 standard 만 처리.)

- [ ] **Step 3: 검증 룰 (생성 버튼 비활성)**

`PatchCreateForm` 의 “패치 생성” 버튼 `disabled` 조건에 다음을 OR 로 추가:

```tsx
const sel = formData.buildSelection
const isBuildOnly = formData.fromVersion === formData.toVersion
const buildSelectionEmpty = sel.enabled && !sel.web && sel.engines.length === 0

const submitDisabled =
  !formData.fromVersion ||
  !formData.toVersion ||
  // build-only 면 picker 가 ON 이고 1개 이상 선택 필수
  (isBuildOnly && (!sel.enabled || buildSelectionEmpty)) ||
  // 일반 패치인데 picker 가 ON 인데 모두 미포함이면 토글 OFF 가 맞다
  buildSelectionEmpty ||
  // 기타 기존 검증
  ...existingChecks
```

비활성 시 안내 텍스트:

```tsx
{isBuildOnly && (!sel.enabled || buildSelectionEmpty) && (
  <p className="text-xs text-destructive">동일 버전 패치는 빌드를 1개 이상 선택해야 합니다.</p>
)}
{buildSelectionEmpty && !isBuildOnly && (
  <p className="text-xs text-destructive">빌드 미포함이면 토글을 OFF 로 두십시오.</p>
)}
```

- [ ] **Step 4: PatchesPage 에서 폼 → API 호출 매핑 변경**

`PatchesPage.tsx` 의 `handleStandardSubmit` (또는 동등 함수) 안에서 `includeAllBuildVersions` 대신 `buildSelection` 전달:

```ts
mutation.mutate({
  ...,
  buildSelection: standardFormData.buildSelection.enabled
    ? standardFormData.buildSelection
    : { enabled: false, web: null, engines: [] },
})
```

- [ ] **Step 5: type-check + dev server 수동 확인**

```
cd release-manager-web && npm run type-check 2>&1 | tail -15
npm run dev
# 브라우저에서 패치 생성 모달 열어 토글 ON 시 picker 동작, build-only 인디케이터 검증
```

- [ ] **Step 6: 커밋**

```
git -C release-manager-web add src/features/patches/patch-management/ui/PatchCreateForm.tsx src/features/patches/patch-management/model/types.ts src/features/patches/patch-management/model/validation.ts src/pages/patches/PatchesPage.tsx
git -C release-manager-web commit -m "feat: PatchCreateForm 에 BuildPickerSection 통합 + 검증 + 안내

토글 OFF 기본값(DB only). ON 시 자동 preselect 된 picker 펼쳐짐.
build-only 패치(from==to + picker 1개 이상) 인디케이터 표기.
범위 안 핫픽스 N건 안내. 빈 picker / 동일버전+OFF 케이스 검증."
```

---

### Task 16: FE — Custom 패치 폼 동일 패턴 적용 (선택, 커스텀 사용 빈도에 따라)

**Files:**
- Modify: `release-manager-web/src/features/patches/patch-management/ui/PatchGenerateFormCard.tsx`

> standard 와 동일 패턴. `releaseType: 'CUSTOM'` + `customerCode` 인자만 다름. 본 task 는 사용자 운영 빈도가 확인된 후 진행. **MVP 에서는 skip 가능**(우선순위 낮음).

- [ ] **Step 1~5**: Task 15 Step 2~6 동일 패턴, releaseType/customerCode 만 변경.

- [ ] **Step 6: 커밋**

```
git -C release-manager-web add src/features/patches/patch-management/ui/PatchGenerateFormCard.tsx
git -C release-manager-web commit -m "feat: 커스텀 패치 폼에도 빌드 picker 적용"
```

---

## Phase 6 — 운영 / 검증

### Task 17: 수동 SQL 백필 — 운영 DB 에 적용

**Files:** 없음(운영 DB 직접 실행).

- [ ] **Step 1: 적용 전 검증 SELECT 실행**

```sql
SELECT release_file_id,
       file_category,
       sub_category,
       file_path
FROM release_file
WHERE file_category = 'ENGINE'
  AND sub_category IS NULL
  AND file_path LIKE '%/engine/%'
LIMIT 30;
```

기대: 빌드 ZIP 추출본 행이 보이고 file_path 가 `versions/.../builds/.../engine/{engineName}/...` 패턴.

- [ ] **Step 2: 백업(선택, 운영 정책에 따라)**

```sql
CREATE TABLE release_file_backup_20260428 AS SELECT * FROM release_file;
```

- [ ] **Step 3: 백필**

```sql
UPDATE release_file rf
SET rf.sub_category = TRIM(BOTH '/' FROM
    SUBSTRING_INDEX(
        SUBSTRING_INDEX(rf.file_path, '/engine/', -1),
        '/',
        1
    )
)
WHERE rf.file_category = 'ENGINE'
  AND rf.sub_category IS NULL
  AND rf.file_path LIKE '%/engine/%';
```

- [ ] **Step 4: UNKNOWN 보정**

```sql
UPDATE release_file rf
SET rf.sub_category = 'UNKNOWN'
WHERE rf.file_category = 'ENGINE'
  AND rf.sub_category LIKE '%.%';
```

- [ ] **Step 5: 검증 SELECT**

```sql
SELECT sub_category, COUNT(*)
FROM release_file
WHERE file_category = 'ENGINE'
GROUP BY sub_category
ORDER BY 1;
```

기대: NC_SMS, NC_FAULT_MS, … 등 운영 엔진명들 + 소수의 UNKNOWN.

- [ ] **Step 6: 운영자 보고**

운영 채널에 백필 완료 + 그룹별 행수 공유.

> 본 task 는 코드 commit 없음. 실행 결과는 운영 로그에만.

---

### Task 18: 빌드 + e2e 검증

**Files:** 없음(실행만).

- [ ] **Step 1: 백엔드 풀 빌드 + 테스트**

```
cd release-manager-api
sed -i 's/languageVersion = JavaLanguageVersion.of(17)/languageVersion = JavaLanguageVersion.of(21)/' build.gradle
./gradlew clean build 2>&1 | tail -30
sed -i 's/languageVersion = JavaLanguageVersion.of(21)/languageVersion = JavaLanguageVersion.of(17)/' build.gradle
grep -n "languageVersion" build.gradle
```

기대: `BUILD SUCCESSFUL`.

- [ ] **Step 2: 프론트 type-check + lint**

```
cd release-manager-web
npm run type-check 2>&1 | tail -10
npm run lint 2>&1 | tail -10
```

기대: 둘 다 PASS.

- [ ] **Step 3: 수동 시나리오 검증 (브라우저)**

`npm run dev` + 백엔드 가동 후:
1. 표준 패치 생성 → 토글 OFF → 패치 생성 → DB only 확인.
2. 토글 ON → picker 자동 preselect 확인 → 패치 생성 → web/engine 디렉토리 확인.
3. 토글 ON → 일부 엔진 “포함 안 함” → 결과 ZIP 에 해당 엔진 폴더 부재 확인.
4. from==to + 토글 ON + WEB 1개 → build-only 패치 → DB 스크립트 0 확인.
5. from==to + 토글 OFF → “패치 생성” 비활성 + 안내 텍스트.
6. 핫픽스가 범위 안에 있을 때 → 폼/응답 안내 표시.

- [ ] **Step 4: 릴리즈 노트 추가**

`release-manager-api/BUILD_VERSION.md` 또는 별도 `CHANGELOG.md` 에 항목 추가:

```
## 2026-04-28 — 패치 생성 빌드 picker 도입

[BREAKING] '빌드 파일 포함' 토글 기본값이 OFF (이전: 모든 버전 포함=true 의 반대).
  토글 OFF 면 빌드 파일이 포함되지 않습니다(DB 패치만).
  기존 워크플로 유지하려면 패치 생성 시 토글을 ON 으로 켜시면 됩니다(자동 preselect=최신).

[변경] 누적 패치에서 핫픽스 DB 스크립트가 자동 포함되지 않습니다. 핫픽스는 버전 관리
  화면에서 별도로 다운로드/적용해 주세요.

[추가] WEB 1개 + 엔진별 1개씩 직접 선택 가능한 picker. '포함 안 함' 옵션 포함.

[추가] Build-only 패치 (같은 base 안에서 빌드만 갈아끼우기) 지원.
```

- [ ] **Step 5: 커밋**

```
git -C release-manager-api add BUILD_VERSION.md
git -C release-manager-api commit -m "docs: 패치 생성 빌드 picker 릴리즈 노트 추가

토글 OFF 기본값 변경, 핫픽스 분리, picker, build-only 패치 지원."
```

---

## 자체 점검 결과

본 plan 의 spec 커버리지:
- 결정 Q1 (하이브리드) → Task 14, 15
- 결정 Q2 (sub_category 백필) → Task 1, 2, 17
- 결정 Q3 (토글 OFF=미포함) → Task 15 (INITIAL_BUILD_SELECTION enabled=false), Task 18 릴리즈 노트
- 결정 Q4 (인라인 컴팩트) → Task 14, 15
- 결정 Q5 (셀렉터 base only) → Task 13
- 결정 Q6 (핫픽스 분리) → Task 10a (findVersionsBetweenExcludingHotfixes 만 사용), Task 10b (README), Task 15 (안내)
- 결정 Q7 (build-only via from==to) → Task 10a (분기 로직), Task 15 (인디케이터)

데이터 모델 / API / 비즈니스 로직 / UI / 운영 절차 모두 task 가 존재함.
