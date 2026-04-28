# 패치 생성 화면 — 빌드 파일 picker 재설계

작성일: 2026-04-28
재설계: 2026-04-28 (빌드 = 디렉토리 진실 원천 전제 반영)
대상 영역: `release-manager-web` 패치 생성 폼 + `release-manager-api` 패치 생성 / `builds-in-range` API / `PatchGenerationService`

---

## 1. 배경 및 문제

### 현 상태 결함

1. **사용자 통제 부재** — 패치 생성 폼은 `includeAllBuildVersions` 토글 한 개만 있어 "모든 빌드 vs 마지막 빌드" 두 모드 외에는 표현 불가. 운영 시나리오 "WEB 1.1.0.260428 + NC_SMS 1.1.0.260427 + NC_FAULT_MS 미포함" 같은 부분 선택 불가능.
2. **버전 셀렉터에 빌드/핫픽스 노출** — 시작/종료 버전 셀렉터가 base + 핫픽스 + 빌드 4-파트까지 노출하여 "버전 시퀀스" 와 "빌드 변형" 의 두 책임이 한 입력에 혼재.
3. **종료 버전에 빌드 노출 시 의미 모호** — "1.1.0 → 1.1.0.260428" 같은 입력이 build-only 패치를 표현하는데 사용되어 picker 가 없는 상황에서 우회적인 표현이 됨.
4. **빌드와 base 버전의 데이터 모델 혼재** — 직전 리팩터 이전에는 빌드 ZIP 추출 시에도 ReleaseFile 행을 생성했고, 그 결과 빌드 산출물의 "진실의 원천" 이 디렉토리와 인덱스 양쪽에 분산되어 일관성 부담이 있었음. 직전 리팩터 (commit `00ee8f8`) 에서 빌드는 ReleaseFile 인덱스에서 분리되어 디렉토리만 진실의 원천이 되도록 정정됨.

### 목표

- 사용자가 패치에 포함할 **WEB 빌드 1개** 와 **엔진별 빌드 1개씩** 을 명시적으로 선택할 수 있다.
- 19종 엔진의 부담을 줄이기 위해 **자동 preselect(엔진별 최신, WEB 최신)** 를 디폴트로 제공하고, 사용자는 변경할 항목만 손댄다.
- 핫픽스는 패치 흐름에서 분리하고, 누적 DB 시퀀스에도 포함하지 않는다(별도 다운로드 artifact).
- Build-only 패치(같은 base 안에서 빌드만 갈아끼우는 패치)를 자연스럽게 지원한다.
- 빌드 산출물의 진실의 원천은 **빌드 디렉토리** (`versions/.../builds/{buildVersion}/`) 이며, picker 후보·검증·복사 모두 디렉토리에서 직접 읽는다. 빌드 산출물에 대한 별도 인덱스(테이블·컬럼) 는 도입하지 않는다.

---

## 2. 결정 사항 요약

| # | 결정 | 의미 |
| --- | --- | --- |
| Q1 | **하이브리드 picker** | 토글 ON 시 자동 preselect(최신) 상태로 picker 펼쳐지고, 사용자가 변경 가능. |
| Q2 | **빌드 산출물의 진실의 원천 = 디렉토리** | 빌드 ZIP 업로드 시 ReleaseFile 행을 만들지 않는다 (직전 리팩터 `00ee8f8`). picker 후보·검증·복사 모두 빌드 디렉토리에서 직접 읽는다. 신규 테이블/컬럼/마이그레이션 없음. |
| Q3 | **토글 OFF = 빌드 전혀 미포함 (기본값)** | DB 패치만 생성. 신규 사용자 디폴트가 변경되는 break — 릴리즈 노트에 명시. |
| Q4 | **인라인 컴팩트 picker + sticky footer** | 같은 sheet 안에서 토글 ON 시 picker 영역 펼침. 1행=1엔진(우측에 dropdown). 일괄 액션 버튼 "모두 최신 / 모두 해제". footer 의 "취소 / 패치 생성" 은 항상 보이도록 sticky. |
| Q5 | **셀렉터는 base 버전만** | 시작/종료 셀렉터에서 핫픽스 / 빌드 둘 다 제외. 빌드는 picker, 핫픽스는 별도 화면. |
| Q6 | **누적 DB 에서 핫픽스 제외** | `findVersionsBetweenExcludingHotfixes` 만 사용. 응답에 "이 범위 내 핫픽스 N건" 안내 + README 자동 명시. |
| Q7 | **Build-only 패치는 from == to 로 자연 표현** | 별도 모드 없음. 같은 base 두 번 선택 + picker 1개 이상 선택. UI 가 "빌드 전용 패치" 인디케이터 표기. |
| Q-S1 | **`builds-in-range` API 의 데이터 소스 = 빌드 디렉토리 walk** | 매 호출 시 범위 안의 각 빌드 디렉토리를 walk 하여 web/ 와 engine/{engineName}/ 의 정규 파일 존재 여부로 후보 집계. 별도 캐시·메타 없음 (필요 시 후속 과제). |
| Q-S2 | **PatchGeneration 의 빌드 처리 = picker 입력만이 진실** | 직전 리팩터의 "빌드 버전이 끼면 자동 통째 복사" 분기는 폐기. `versions[]` 루프에서 빌드 버전은 skip 하고, picker 결과를 별도 단계에서 부분 복사. backward-compat 안 챙김. |
| Q-S3 | **ETC 동행 충돌 = 빌드 버전 오름차순 + REPLACE_EXISTING** | picker 로 선택된 빌드들의 합집합의 etc/ 를 outputDir/etc/ 에 오름차순 순차 복사. 같은 경로 충돌 시 큰 buildVersion 의 내용이 살아남음. |

> **표기**: Q1~Q7 은 UX·운영 시나리오 결정. Q-S* (S = sub) 는 직전 리팩터 (commit `00ee8f8`) 이후 백엔드 데이터 흐름 결정.

---

## 3. 데이터 모델

### 3.1 변경 없음

- 신규 테이블 / 컬럼 / 인덱스 → **없음**.
- Flyway 마이그레이션 파일 추가 → **없음**.
- `release_file.sub_category` 의 빌드 ENGINE 행 자동 저장 → **폐기** (애초에 빌드 ZIP 업로드는 ReleaseFile 행을 만들지 않음).
- 1회성 백필 SQL → **폐기** (백필 대상이 없음).
- 다른 카테고리 (DB 카테고리 등) 의 `release_file.sub_category` 의미는 기존 그대로.

### 3.2 빌드 디렉토리가 진실의 원천

- 빌드 산출물 위치: `versions/{projectId}/{standard|custom/...}/.../builds/{buildVersion}/`.
  - `release-manager-api/CLAUDE.md` 의 "File System Structure" 섹션 참고.
- 1단계 디렉토리 분류 (`web/`, `engine/`, `etc/`) 는 ZIP 업로드 시 `BuildZipValidator` 가 강제.
- 엔진명 = `engine/` 의 1단계 하위 **디렉토리** 명. `engine/foo.jar` 같은 직속 파일은 `UNKNOWN` 그룹으로 노출 (구조 정책 상세는 §7).

---

## 4. 백엔드 API

### 4.1 신규 API — 빌드 후보 조회

**`GET /api/releases/versions/builds-in-range`**

쿼리:
- `projectId: string`
- `fromVersionId: long`
- `toVersionId: long`
- `customerId: long?` (커스텀 패치인 경우)

응답:
```jsonc
{
  "web": [
    { "buildVersionId": 42, "fullVersion": "1.1.0.260428", "createdAt": "...", "isLatest": true },
    { "buildVersionId": 41, "fullVersion": "1.1.0.260427", "createdAt": "..." }
  ],
  "engines": [
    {
      "engineName": "NC_SMS",
      "candidates": [
        { "buildVersionId": 42, "fullVersion": "1.1.0.260428", "createdAt": "...", "isLatest": true },
        { "buildVersionId": 41, "fullVersion": "1.1.0.260427", "createdAt": "..." }
      ]
    },
    { "engineName": "NC_FAULT_MS", "candidates": [...] }
  ],
  "hotfixesInRange": [
    { "versionId": 33, "fullVersion": "1.0.0.1", "hotfixVersion": 1 }
  ]
}
```

서버 로직 (디렉토리 walk):
1. `release_version` 에서 `from <= base <= to` 범위의 빌드(`build_version > 0`) 행 조회.
2. 각 빌드 행에 대해 `ReleaseVersionFileSystemService.resolveBuildBasePath(base, buildVersion)` 으로 디렉토리 위치를 잡고 다음을 검사:
   - **WEB 후보**: `<buildDir>/web/` 가 존재하고 그 아래에 정규 파일이 1개 이상이면 해당 빌드를 WEB 후보 배열에 추가.
   - **ENGINE 후보**: `<buildDir>/engine/` 의 1단계 하위 **디렉토리** 들을 enumerate. 각 디렉토리 안에 정규 파일이 1개 이상이면 `(engineName=디렉토리명, buildVersionId)` 페어로 추가. `engine/` 직속 파일이 1개 이상이면 `engineName=UNKNOWN` 그룹에 추가.
3. WEB 후보와 각 ENGINE 그룹 내부를 `buildVersion DESC` 로 정렬, 첫 항목에 `isLatest=true`.
4. `hotfixesInRange`: `release_version` 에서 같은 범위의 `hotfix_version > 0` 행을 메타정보만 반환 (디렉토리 walk 와 무관, 기존 로직 그대로).

**효율 메모**:
- 빌드 1개 당 `Files.list(buildDir/web)` + `Files.list(buildDir/engine)` 의 짧은 호출. 정규 파일 존재 여부는 `anyMatch` 로 short-circuit.
- 캐시·메타 없음. 패치 생성 폼에서 from/to 변경 시 매번 재계산 (UI 측 자동 preselect 와 일관).
- 응답 시간이 운영에서 부담이 되면 후속 과제로 캐시·메타화 검토 (§12).

### 4.2 변경 API — 패치 생성

**`POST /api/patches/standard` / `POST /api/patches/custom`**

요청:
```jsonc
{
  "fromVersion": "1.1.0",
  "toVersion": "1.2.0",
  "customerId": null,
  "assigneeId": 3,
  "patchName": "...",
  "description": "...",
  "buildSelection": {
    "enabled": true,
    "web": { "buildVersionId": 42 },          // null = WEB 미포함
    "engines": [                              // 미포함 엔진은 배열에서 제외
      { "engineName": "NC_SMS", "buildVersionId": 42 },
      { "engineName": "NC_FAULT_MS", "buildVersionId": 41 }
    ]
  }
}
```

- `buildSelection: null` 또는 `enabled: false` → 빌드 미포함(DB only).
- `enabled: true` 이지만 `web == null && engines == []` → 검증 실패("빌드 미포함이면 토글 OFF 가 맞다").

응답에 추가 필드:
```jsonc
{
  ...,
  "isBuildOnly": false,
  "hotfixesInRange": [
    { "versionId": 33, "fullVersion": "1.0.0.1" }
  ],
  "includedBuilds": {
    "web": { "buildVersionId": 42, "fullVersion": "1.1.0.260428" },
    "engines": [
      { "engineName": "NC_SMS", "buildVersionId": 42, "fullVersion": "1.1.0.260428" },
      { "engineName": "NC_FAULT_MS", "buildVersionId": 41, "fullVersion": "1.1.0.260427" }
    ]
  }
}
```

### 4.3 검증 룰 (서버측)

| 조건 | 메시지 |
| --- | --- |
| `enabled==true && web==null && engines.isEmpty()` | "빌드 미포함이면 토글을 OFF 로 두십시오" |
| `from.id == to.id && (enabled==false || (web==null && engines.isEmpty()))` | "동일 버전 패치는 최소 1개 이상의 빌드 선택이 필요합니다" |
| `web.buildVersionId` 가 from..to 범위 내 build (`build_version > 0`) 가 아님, 또는 그 빌드 디렉토리의 `web/` 에 정규 파일이 1개 이상 존재하지 않음 | "선택한 WEB 빌드가 유효하지 않습니다" |
| `engines[*].buildVersionId` 가 범위 내 build 가 아니거나, 그 빌드 디렉토리의 `engine/{engineName}/` 가 존재하지 않거나 정규 파일이 1개 이상 존재하지 않음 | "선택한 엔진 빌드가 유효하지 않습니다" |

### 4.4 폐기 / 단순화

- 요청 필드 `includeAllBuildVersions` → 제거. 사내 단일 사용자라 backward-compat 미유지.
- `parseInputVersion` 의 4-파트 빌드 처리 분기 → 패치 생성 흐름에서는 더 이상 호출되지 않음. 다른 곳 호출 가능성을 위해 메서드 자체와 테스트는 유지.
- `findVersionsBetween` (핫픽스 포함) → 패치 생성에서 미사용. `findVersionsBetweenExcludingHotfixes` 만 사용.
- `BuildFileService.uploadBuildZip` 의 `ReleaseFile` 생성 / `extractEngineName` / `subCategory` 저장 추가 → 모두 폐기 (직전 리팩터 `00ee8f8` 에서 처리 완료).

---

## 5. 백엔드 비즈니스 로직 — `PatchGenerationService` 재배선

### 5.1 패치 생성 흐름

입력: `fromVersion(base)`, `toVersion(base)`, `buildSelection`

```
1. baseVersions = findVersionsBetweenExcludingHotfixes(from, to)
   → 핫픽스·빌드를 모두 제외한 base 시퀀스만.

2. baseVersions 의 각 base 에 대해 ReleaseFile 인덱스 기반으로 SQL 파일 복사
   (현 copySqlFiles 의 DB 카테고리 분기 그대로).
   versions[] 루프 안에서는 빌드 버전은 진입 자체 skip.

3. buildSelection.enabled == true 이면 별도 단계로:
   a. web.buildVersionId 가 있으면:
      <buildDir>/web/ 전체 → outputDir/web/ 로 복사
   b. engines[] 의 각 (engineName, buildVersionId) 페어에 대해:
      <buildDir>/engine/{engineName}/ 전체 → outputDir/engine/{engineName}/ 로 복사
   c. ETC 동행:
      selectedBuildIds = {web.buildVersionId} ∪ engines[*].buildVersionId
      → buildVersion 오름차순 정렬 후 순차 복사
      → 각 빌드의 <buildDir>/etc/ 를 outputDir/etc/ 에 REPLACE_EXISTING 으로 덮어씀
      → 결과: 가장 큰 buildVersion 의 etc/ 가 살아남음 (Q-S3).

4. 응답에 isBuildOnly / hotfixesInRange / includedBuilds 채움.
   README 에 "포함된 빌드 / 핫픽스(별도 적용 필요)" 섹션 추가.
```

### 5.2 ETC 동행 충돌 처리 — 구체 예시

picker 선택:
- WEB = v260428, NC_SMS = v260427, NC_FAULT_MS = v260428.

빌드 디렉토리 상태:
```
builds/260427/etc/note.txt
builds/260427/etc/release-notes-old.txt
builds/260428/etc/note.txt
builds/260428/etc/release-notes-new.txt
```

`selectedBuildIds = {v260427, v260428}`. 오름차순 순차 복사 (REPLACE_EXISTING) 결과 `outputDir/etc/`:

| 파일 | 출처 | 비고 |
| --- | --- | --- |
| `note.txt` | v260428 | v260427 내용을 v260428 가 덮어씀 |
| `release-notes-old.txt` | v260427 | 충돌 없음, 그대로 살아남음 |
| `release-notes-new.txt` | v260428 | 충돌 없음, 그대로 살아남음 |

picker 에서 어느 항목으로도 선택받지 못한 빌드의 `etc/` 는 복사되지 않는다. 예: v260427 이 picker 의 어느 항목에도 선택되지 않았다면 v260427 의 `etc/` 는 patch ZIP 에 포함되지 않음.

### 5.3 직전 리팩터에서 폐기되는 분기

직전 리팩터 (commit `00ee8f8`) 에서 `PatchGenerationService` 에 추가된 다음 분기들은 본 spec 으로 **폐기** 된다 (Q-S2):

- `versions[]` 루프 안의 `if (v.isBuild()) { ... copyBuildFilesFromFileSystem(...) ... continue; }` 자동 통째 복사 분기.
- WEB / ENGINE 마지막 버전 결정 시 빌드 버전을 만나면 `lastVersionIdForWeb` / `lastVersionIdForEngineAll` 로 잠정 확정하는 분기.

base 버전에 대해서만 동작하던 ENGINE `sub_category` 분기 (`lastVersionIdByEngineSubCategory`) 는 빌드 버전이 ReleaseFile 행을 만들지 않으므로 base 만의 분기로 자연 정상화된다.

### 5.4 보존 / 재활용되는 헬퍼

- `copyBuildFilesFromFileSystem(buildVersion, outputDir)` — 통째 복사 시그니처. 본 spec 에서는 직접 호출하지 않으나, 디렉토리 → 디렉토리 단위 복사 유틸로 분리해 web/engine/{engineName}/etc 부분 복사에 재활용 (정확한 분리는 plan 단계).
- `buildRootHasFiles(buildVersion, rootName)` — `builds-in-range` API 의 후보 집계와 검증 룰에서 재사용.
- `BuildFileCopyException` 내부 클래스 — 보존.

### 5.5 Build-only (`from.id == to.id`)

- `baseVersions = []` → DB 스크립트 단계 전체 skip (`isSameBaseVersion` 분기 그대로 활용).
- 빌드 파일 단계만 진행.
- picker 가 비어있으면 §4.3 검증 룰에서 차단.

---

## 6. 프론트엔드 — 폼 구조

서버 내부 구현이 바뀌었지만 `builds-in-range` 의 응답 형태와 `POST /api/patches/*` 의 요청·응답 페이로드는 동일하므로 프론트엔드 타입·훅·컴포넌트 트리는 직전 spec (재설계 이전) 과 동일.

### 6.1 컴포넌트 트리

```
PatchCreateForm (sheet)
├── 버전 범위 (시작/종료 — base 만 노출)
├── 고객사 / 담당자
├── 패치명
├── 설명
├── 빌드 파일 포함 [Toggle, 기본 OFF]
└── (Toggle ON 시) BuildPickerSection
     ├── 헤더: "모두 최신 자동 선택됨" + 일괄 액션 ["모두 최신", "모두 해제"]
     ├── WEB 섹션
     │    └── RadioGroup: [build1 ✦최신, build2, ..., 포함 안 함]
     └── ENGINE 섹션 (N행)
          └── 각 행: [엔진명] | [선택된 빌드 ✦최신 ▼ DropdownMenu]
                                            └── [build1 ✦최신, build2, ..., 포함 안 함]
└── footer (sticky): [취소] [패치 생성]
```

### 6.2 컴팩트 행 마크업 (예시)

```
WEB
┌────────────────────────────────────────────┐
│ ◉ 1.1.0.260428  ✦최신  (2026-04-28)        │
│ ◯ 1.1.0.260427         (2026-04-27)        │
│ ◯ 포함 안 함                                │
└────────────────────────────────────────────┘

ENGINE   [모두 최신] [모두 해제]
┌─────────────────┬──────────────────────────┐
│ NC_SMS          │ 1.1.0.260428  ✦최신  ▼  │
│ NC_FAULT_MS     │ 1.1.0.260427  ✦최신  ▼  │
│ NC_INV          │ 포함 안 함  ▼            │
│ ...             │ ...                       │
└─────────────────┴──────────────────────────┘
```

각 ENGINE 행의 dropdown 클릭 시:
```
1.1.0.260428  ✦최신
1.1.0.260427
1.0.5.260315
─────────────
포함 안 함
```

### 6.3 자동 preselect 룰

토글 OFF → ON 으로 처음 켜질 때 또는 from/to 가 변경될 때:
- WEB: WEB 후보 중 첫 항목(`isLatest=true`).
- ENGINE: 각 엔진의 첫 항목(`isLatest=true`).
- 후보가 0인 엔진은 행 자체를 표시하지 않음(WEB 도 후보 0개면 WEB 섹션 숨김).

사용자가 손댄 항목은 from/to 가 바뀌어도 가능한 한 유지(buildVersionId 가 새 후보 풀에 없으면 "최신" 으로 fallback).

### 6.4 Build-only 인디케이터

`fromVersionId === toVersionId` 인 순간 폼 어딘가(toggle 라벨 옆 또는 footer 위)에 작은 배지:
> ⓘ 빌드 전용 패치 — DB 스크립트 없이 빌드 파일만 생성됩니다

토글 OFF + from==to 이면 "패치 생성" 비활성 + 안내 텍스트.

### 6.5 핫픽스 안내

응답의 `hotfixesInRange` 가 비어있지 않으면 패치 생성 결과 화면(또는 toast)에 표기:
> ⚠ 이 범위에 핫픽스 N건이 있습니다. 핫픽스는 버전 관리 화면에서 별도로 다운로드/적용해 주세요. (1.0.0.1, 1.0.0.2, …)

### 6.6 폼 검증 (클라이언트)

- toggle ON && (web == null && engines.empty()) → 생성 버튼 disabled.
- from.id === to.id && toggle OFF → 생성 버튼 disabled.
- from.id > to.id → 생성 버튼 disabled (기존 룰).

---

## 7. 빌드 ZIP 구조 가정 및 예외

- 표준: `web/...`, `engine/{engineName}/...`, `etc/...`.
- 비표준: `engine/foo.jar` (서브폴더 없음) → `builds-in-range` 응답에서 `UNKNOWN` 그룹으로 노출. (직전 spec 의 "ReleaseFile.sub_category=UNKNOWN" 저장 정책은 폐기. 디렉토리 walk 결과의 그룹화로 대체.)
- `BuildZipValidator` 에 "engine 직속 파일" 경고 로깅 추가(거부는 안 함, 운영자 가시성 확보).

---

## 8. 마이그레이션 / 배포 순서

1. **백엔드 코드** 배포:
   - `BuildsInRangeService` 구현 (디렉토리 walk 기반).
   - `PatchService` / `PatchDto` 의 `buildSelection` 입력 + `hotfixesInRange` / `includedBuilds` 출력.
   - `PatchGenerationService` 재배선 — 자동 통째 복사 폐기, picker 입력 별도 단계, ETC 동행 오름차순.
2. **프론트엔드 코드** 배포:
   - 셀렉터 base-only 필터링.
   - `BuildPickerSection` 컴포넌트 신규.
   - `useBuildsInRange` 훅 + 응답 타입.
   - `PatchCreateForm` 의 폼 데이터 모델 교체.
3. **릴리즈 노트 / 운영 매뉴얼**:
   - 토글 OFF 디폴트 변경 명시.
   - 핫픽스 = 패치와 분리 운영 절차 명시.

> **Flyway 마이그레이션 추가 없음**. 직전 spec 의 §3.3 데이터 보정 SQL 단계는 폐기 (백필 대상 자체가 없음).

---

## 9. 엣지 케이스

| 케이스 | 처리 |
| --- | --- |
| 범위 안에 빌드가 0개 | toggle ON 이어도 WEB/ENGINE 섹션 모두 비어 있음 → toggle 자동 OFF + 안내 텍스트("이 범위에 빌드가 없습니다"). |
| 범위 안에 빌드는 있지만 `web/` 에 정규 파일이 0개 | WEB 섹션 숨김. |
| 어떤 엔진은 후보가 1개뿐 | dropdown 그대로 노출("포함 안 함" 옵션 + 그 1개). |
| 빌드 삭제로 picker 의 buildVersionId 가 무효 | from/to 변경 시 fallback(최신)으로 자동 보정. 제출 전 서버 검증으로 차단. |
| 같은 base 두 번 선택 + toggle OFF | 생성 버튼 비활성. 안내 텍스트. |
| 같은 base 두 번 선택 + toggle ON + picker 모두 미포함 | 생성 버튼 비활성. |
| 핫픽스가 from 자체인 경우 | 셀렉터에 핫픽스가 안 보이므로 사용자가 from 으로 직접 선택할 수 없음. (현 동작 변경 — 의도). |
| 같은 빌드에 ETC 파일과 ENGINE 파일이 섞여 있고 ENGINE 만 선택됨 | 그 빌드의 ETC 파일들이 같이 포함됨 (§5.1.c). 운영 의도상 ETC 는 picker 로 선택된 빌드와 한 묶음. |
| 어떤 빌드는 ETC 만 있고 WEB/ENGINE 파일이 없음 | picker 의 WEB/ENGINE 섹션에 노출되지 않음 → 그 빌드의 ETC 도 자동 포함되지 않음. ETC-only 빌드를 패치에 넣으려면 별도 운영 절차 필요(현 spec 범위 외). |

---

## 10. 테스트 전략

### 백엔드
- `BuildsInRangeServiceTest` (신규):
  - 빌드 0개 / 1개 / N개 케이스에서 응답 형태.
  - `engine/{engineName}/` 가 존재하지만 정규 파일이 0개인 빌드는 후보에서 제외.
  - `engine/` 직속 파일이 있으면 `UNKNOWN` 그룹에 노출.
  - WEB 후보·ENGINE 그룹 모두 buildVersion DESC 정렬, 첫 항목에 `isLatest=true`.
  - `hotfixesInRange` 가 분리되어 반환.
- `PatchGenerationServiceTest` (확장) — `buildSelection` 기반 분기:
  - toggle OFF → outputDir 에 web/engine/etc 어느 것도 없음.
  - WEB 1개 + ENGINE 일부 → 출력에 선택된 부분만, 미선택 엔진 디렉토리 없음.
  - Build-only (`from == to`) → DB 스크립트 0, 빌드 부분 복사만.
  - 핫픽스 범위 안에 있을 때 → DB 시퀀스 미포함 + 응답 `hotfixesInRange` 비어있지 않음.
  - **ETC 충돌**: 두 빌드 모두 `etc/note.txt` 가 있을 때 최종 outputDir 의 `etc/note.txt` 가 큰 buildVersion 의 내용임을 검증.
- 직전 리팩터의 자동 통째 복사 분기는 본 spec 으로 폐기되므로 그 분기에 대한 테스트는 작성하지 않음.

### 프론트엔드
- `PatchCreateForm.test`: toggle 동작, 자동 preselect, 일괄 액션, build-only 인디케이터, 검증 disabled 상태.
- `BuildPickerSection.test`: 후보 0 처리, dropdown 선택, "포함 안 함", "모두 최신"/"모두 해제".
- (선택) Playwright e2e: 1.0.0 → 1.1.0 + 빌드 픽 → 패치 생성 → 결과 ZIP 의 web/engine 디렉토리 검증.

---

## 11. 작업 분할 (구현 순서 제안)

> 자세한 단계는 후속 implementation plan 에서 결정. 본 문서는 큰 청크만 명시.

1. **백엔드 신규 API** — `builds-in-range` 엔드포인트 + 디렉토리 walk 후보 집계 + 단위 테스트.
2. **백엔드 패치 생성 입출력 변경** — `buildSelection` 입력, `hotfixesInRange` / `includedBuilds` 출력, 검증 룰.
3. **백엔드 PatchGeneration 재배선** — 자동 통째 복사 분기 폐기, picker 입력 별도 단계, ETC 동행 오름차순. `findVersionsBetweenExcludingHotfixes` 만 사용.
4. **프론트엔드 셀렉터** — `getVersionsFromTree` 가 base 만 노출하도록 필터.
5. **프론트엔드 BuildPickerSection 컴포넌트** — UI + 자동 preselect + 일괄 액션 + dropdown 행.
6. **프론트엔드 PatchCreateForm 통합** — `buildSelection` 폼 데이터 + 검증 + build-only 인디케이터 + 핫픽스 안내.
7. **e2e 검증 및 릴리즈 노트** 작성.

---

## 12. 비목표 / 후속 과제

- 빌드 picker 에서의 다중 선택(엔진별 N개) — 본 spec 은 1개씩만.
- 엔진 그룹화(NC_*, INFRAEYE_* 등으로 묶어 토글 한 번에 일괄 변경) — 운영 피드백 후 검토.
- 핫픽스 자체 다운로드 화면 변경 — 현 화면 유지.
- 빌드 ZIP 표준 강화(`BuildZipValidator` 가 `engine/{engineName}/...` 두 단계를 강제) — 별도 결정 필요. 현재는 경고 로깅까지만.
- **빌드 디렉토리 walk 의 캐싱 / 메타화** — 본 spec 은 매 호출 walk. 응답 시간이 운영 부담이 되면 후속에서 빌드 업로드 시점에 메타 (어떤 엔진들이 들어있는지) 를 별도 저장하는 방식 검토.
