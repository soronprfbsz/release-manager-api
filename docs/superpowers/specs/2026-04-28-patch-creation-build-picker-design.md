# 패치 생성 화면 — 빌드 파일 picker 재설계

작성일: 2026-04-28
대상 영역: `release-manager-web` 패치 생성 폼 + `release-manager-api` 패치 생성 / 빌드 업로드 / 데이터 모델

---

## 1. 배경 및 문제

### 현 상태 결함

1. **데이터 모델 충실도 결함** — `BuildFileService.uploadBuildZip` 가 빌드 ZIP 추출 시 모든 ReleaseFile 의 `sub_category` 를 NULL 로 저장. 그 결과 `PatchGenerationService` 의 ENGINE 분기 로직(line 827~)이 “sub_category 별 마지막 버전”으로 동작하도록 의도되어 있으나 실제로는 모든 엔진 파일이 한 “ETC” 버킷으로 묶여, NC_SMS / NC_FAULT_MS / NC_INV / … 19여 종 엔진을 구분하지 못함.
2. **사용자 통제 부재** — 패치 생성 폼은 `includeAllBuildVersions` 토글 한 개만 있어 “모든 빌드 vs 마지막 빌드” 두 모드 외에는 표현 불가. 운영 시나리오 “WEB 1.1.0.260428 + NC_SMS 1.1.0.260427 + NC_FAULT_MS 미포함” 같은 부분 선택 불가능.
3. **버전 셀렉터에 빌드/핫픽스 노출** — 시작/종료 버전 셀렉터가 base + 핫픽스 + 빌드 4-파트까지 노출하여 “버전 시퀀스” 와 “빌드 변형” 의 두 책임이 한 입력에 혼재.
4. **종료 버전에 빌드 노출 시 의미 모호** — “1.1.0 → 1.1.0.260428” 같은 입력이 build-only 패치를 표현하는데 사용되어 picker 가 없는 상황에서 우회적인 표현이 됨.

### 목표

- 사용자가 패치에 포함할 **WEB 빌드 1개** 와 **엔진별 빌드 1개씩** 을 명시적으로 선택할 수 있다.
- 19종 엔진의 부담을 줄이기 위해 **자동 preselect(엔진별 최신, WEB 최신)** 를 디폴트로 제공하고, 사용자는 변경할 항목만 손댄다.
- 핫픽스는 패치 흐름에서 분리하고, 누적 DB 시퀀스에도 포함하지 않는다(별도 다운로드 artifact).
- Build-only 패치(같은 base 안에서 빌드만 갈아끼우는 패치)를 자연스럽게 지원한다.
- 빌드 ZIP 업로드 시 엔진명을 자동 추출하여 `sub_category` 에 저장한다(데이터 모델 정합성 회복).

---

## 2. 결정 사항 요약

| # | 결정 | 의미 |
| --- | --- | --- |
| Q1 | **하이브리드 picker** | 토글 ON 시 자동 preselect(최신) 상태로 picker 펼쳐지고, 사용자가 변경 가능. |
| Q2 | **`sub_category` 백필 + 신규 업로드 시 자동 저장** | `release_file.sub_category` 가 단일 진실의 원천. 기존 데이터는 1회성 수동 SQL 로 보정. 마이그레이션 파일 추가 없음. |
| Q3 | **토글 OFF = 빌드 전혀 미포함 (기본값)** | DB 패치만 생성. 신규 사용자 디폴트가 변경되는 break — 릴리즈 노트에 명시. |
| Q4 | **인라인 컴팩트 picker + sticky footer** | 같은 sheet 안에서 토글 ON 시 picker 영역 펼침. 1행=1엔진(우측에 dropdown). 일괄 액션 버튼 “모두 최신 / 모두 해제”. footer 의 “취소 / 패치 생성” 은 항상 보이도록 sticky. |
| Q5 | **셀렉터는 base 버전만** | 시작/종료 셀렉터에서 핫픽스 / 빌드 둘 다 제외. 빌드는 picker, 핫픽스는 별도 화면. |
| Q6 | **누적 DB 에서 핫픽스 제외** | `findVersionsBetweenExcludingHotfixes` 만 사용. 응답에 “이 범위 내 핫픽스 N건” 안내 + README 자동 명시. |
| Q7 | **Build-only 패치는 from == to 로 자연 표현** | 별도 모드 없음. 같은 base 두 번 선택 + picker 1개 이상 선택. UI 가 “빌드 전용 패치” 인디케이터 표기. |

---

## 3. 데이터 모델 변경

### 3.1 `release_file.sub_category` 의미 명세

| `file_category` | `sub_category` | 비고 |
| --- | --- | --- |
| WEB | `null` | WEB 은 단일이므로 sub_category 사용 안 함. |
| ENGINE | 엔진명 (예: `NC_SMS`) | ZIP 의 `engine/{engineName}/...` 에서 추출. |
| ENGINE (서브폴더 없음) | `UNKNOWN` | `engine/foo.jar` 처럼 직속 파일이면 `UNKNOWN` 으로 저장(향후 운영자가 수동 보정). |
| ETC | `null` | ETC 는 분류 의미 없음. |
| (DB 카테고리 등 기타) | 현 의미 유지 | DB 마이그레이션 SQL 분류 등 기존 의미 변경 없음. |

### 3.2 신규 빌드 업로드 시 자동 저장

`BuildFileService.uploadBuildZip` 의 ReleaseFile 생성 루프(현 코드 line 245~260)에서:

```java
String topLevel = topLevelOf(info.relativePath());  // "web" | "engine" | "etc"
String engineName = (category == FileCategory.ENGINE)
    ? extractEngineName(info.relativePath())  // "engine/NC_SMS/foo.jar" -> "NC_SMS"
    : null;

ReleaseFile file = ReleaseFile.builder()
    ...
    .fileCategory(category)
    .subCategory(engineName)   // ← 추가
    ...
    .build();
```

`extractEngineName(String relativePath)` 헬퍼:
- 입력: ZIP 내부 상대 경로 (예: `engine/NC_SMS/lib/foo.jar`).
- “/” 분리 후 두 번째 토큰 반환.
- 두 번째 토큰이 없으면(`engine/foo.jar`) `"UNKNOWN"` 반환.

### 3.3 기존 데이터 1회성 수동 SQL

```sql
-- 빌드의 ENGINE 카테고리 파일에 대해 file_path 의 'engine/{engineName}/...' 패턴에서
-- engineName 추출하여 sub_category 에 저장. (file_path 는 baseReleasePath 기준 상대 경로,
-- 'versions/{projectId}/.../builds/{buildVersion}/engine/{engineName}/...' 형태)

-- 사전 백업 (선택, 운영 정책에 따라):
CREATE TABLE release_file_backup_20260428 AS SELECT * FROM release_file;

-- 백필
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

-- 'engine/' 직속 파일(서브폴더 없는 케이스)은 위 SUBSTRING_INDEX 결과가
-- 파일명이 되므로, 그런 행은 별도로 'UNKNOWN' 처리:
UPDATE release_file rf
SET rf.sub_category = 'UNKNOWN'
WHERE rf.file_category = 'ENGINE'
  AND rf.sub_category LIKE '%.%';   -- 확장자가 있으면 파일명으로 잘못 들어간 것
```

> 운영자가 적용 전 SELECT 로 결과를 미리 검증할 것. 마이그레이션 파일 추가하지 않음(사용자 결정).

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

서버 로직:
1. `release_version` 에서 `from <= base <= to` 범위의 빌드(`build_version > 0`) 조회.
2. WEB: 해당 빌드들 중 ReleaseFile 의 `file_category=WEB` 이 존재하는 빌드들을 모음, 빌드 버전 DESC.
3. ENGINE: 동일 빌드들의 `file_category=ENGINE` 행을 `sub_category` 별 그룹화. 각 엔진 그룹 안에서 빌드 버전 DESC.
4. `isLatest`: 각 그룹의 첫 항목.
5. 핫픽스 정보(`hotfixesInRange`): 동 범위의 `hotfix_version > 0` 행을 메타정보만 반환(다운로드 가이드용).

### 4.2 변경 API — 패치 생성

**`POST /api/patches/standard` / `POST /api/patches/custom`**

요청 변경:
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
- `enabled: true` 이지만 `web == null && engines == []` → 검증 실패(”빌드 미포함이면 토글 OFF 가 맞다”).

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

### 4.3 폐기/단순화

- 요청 필드 `includeAllBuildVersions` → 제거. 기존 호출자(레거시)에 대해 backward-compat 유지하지 않음(같은 사내 사용자만 사용하므로).
- `parseInputVersion` 의 4-파트 빌드 처리 분기 → 패치 생성 흐름에서는 더 이상 호출되지 않음. 테스트는 유지(다른 곳에서 쓰일 수 있음).
- `findVersionsBetween` (핫픽스 포함) 호출은 패치 생성에서 제거. `findVersionsBetweenExcludingHotfixes` 만 사용.

---

## 5. 백엔드 비즈니스 로직

### 5.1 패치 생성 시 파일 포함 결정

```
입력: fromVersion(base), toVersion(base), buildSelection
1. findVersionsBetweenExcludingHotfixes(from, to) → DB 스크립트 누적용 baseVersions[]
2. baseVersions 에서 SQL 파일 복사 (현 copySqlFiles 의 DB 카테고리 분기만 유지)
3. buildSelection.enabled == true 이면:
   a. web.buildVersionId 가 있으면 그 빌드의 file_category=WEB 파일들을 outputDir/web/ 에 복사
   b. engines[] 의 각 항목에 대해 그 빌드의 file_category=ENGINE AND sub_category=engineName 파일들을 outputDir/engine/{engineName}/ 에 복사
   c. ETC 카테고리 처리: 위 a/b 에서 실제로 선택된 빌드들의 합집합(`selectedBuildIds = {web.buildVersionId} ∪ engines[*].buildVersionId`)에 대해 그 빌드들의 file_category=ETC 파일들을 outputDir/etc/ 에 복사. 사용자가 선택한 빌드 1개당 ETC 가 함께 따라가는 셈.
4. 핫픽스 정보를 응답 hotfixesInRange 와 README 에 표기.
5. README 의 “포함된 빌드 / 핫픽스(별도 적용 필요)” 섹션 추가.
```

### 5.2 Build-only 분기

`from.id == to.id` 인 경우:
- baseVersions[] = [] (사이가 비어있음).
- DB 스크립트 단계는 모두 skip(`isSameBaseVersion` 분기 그대로 활용).
- buildSelection.enabled == true && (web 또는 engines 중 1개 이상) → 빌드 파일만 포함된 패치 산출.
- 그 외(picker 도 비어있음) → 검증 실패.

### 5.3 검증 룰 (서버측)

```
- enabled == true && web == null && engines.isEmpty() → INVALID_INPUT_VALUE("빌드 미포함이면 토글을 OFF 로 두십시오")
- from.id == to.id && (enabled == false || (web == null && engines.isEmpty()))
    → INVALID_INPUT_VALUE("동일 버전 패치는 최소 1개 이상의 빌드 선택이 필요합니다")
- web.buildVersionId 가 from..to 범위 안의 build_version > 0 행이 아니면 거부
- engines[*].buildVersionId 도 동일 검증 + sub_category 가 engineName 과 일치하는 ReleaseFile 이 실재하는지 확인
```

---

## 6. 프론트엔드 — 폼 구조

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
     └── ENGINE 섹션 (19행)
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

사용자가 손댄 항목은 from/to 가 바뀌어도 가능한 한 유지(buildVersionId 가 새 후보 풀에 없으면 “최신” 으로 fallback).

### 6.4 Build-only 인디케이터

`fromVersionId === toVersionId` 인 순간 폼 어딘가(toggle 라벨 옆 또는 footer 위)에 작은 배지:
> ⓘ 빌드 전용 패치 — DB 스크립트 없이 빌드 파일만 생성됩니다

토글 OFF + from==to 이면 “패치 생성” 비활성 + 안내 텍스트.

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
- 비표준: `engine/foo.jar`(서브폴더 없음) → `sub_category="UNKNOWN"` 으로 저장. picker 의 ENGINE 섹션에 `UNKNOWN` 그룹으로 노출.
- BuildZipValidator 에 “engine 직속 파일” 경고 로깅 추가(거부는 안 함, 운영자 가시성 확보).

---

## 8. 마이그레이션 / 배포 순서

1. **백엔드 코드** 배포(이전 단계 토글 호환은 끊는다):
   - `BuildFileService` `extractEngineName` 추가, `subCategory` 저장.
   - 신규 API `/api/releases/versions/builds-in-range` 구현.
   - `PatchService` 의 요청/응답 DTO 확장(buildSelection, hotfixesInRange, includedBuilds).
   - `PatchGenerationService` 의 빌드 파일 포함 로직 재배선.
2. **데이터 보정 SQL** 운영 DB 에서 수동 실행(섹션 3.3).
3. **프론트엔드 코드** 배포:
   - 셀렉터 base-only 필터링.
   - BuildPickerSection 컴포넌트 신규.
   - `useBuildsInRange` 훅 + 응답 타입.
   - PatchCreateForm 의 폼 데이터 모델 교체.
4. **릴리즈 노트 / 운영 매뉴얼**:
   - 토글 OFF 디폴트 변경 명시.
   - 핫픽스 = 패치와 분리 운영 절차 명시.

---

## 9. 엣지 케이스

| 케이스 | 처리 |
| --- | --- |
| 범위 안에 빌드가 0개 | toggle ON 이어도 WEB/ENGINE 섹션 모두 비어 있음 → toggle 자동 OFF + 안내 텍스트(”이 범위에 빌드가 없습니다”). |
| 범위 안에 빌드는 있지만 WEB 파일이 없음 | WEB 섹션 숨김. |
| 어떤 엔진은 후보가 1개뿐 | dropdown 그대로 노출(”포함 안 함” 옵션 + 그 1개). |
| 빌드 삭제로 picker 의 buildVersionId 가 무효 | from/to 변경 시 fallback(최신)으로 자동 보정. 제출 전 서버 검증으로 차단. |
| 같은 base 두 번 선택 + toggle OFF | 생성 버튼 비활성. 안내 텍스트. |
| 같은 base 두 번 선택 + toggle ON + picker 모두 미포함 | 생성 버튼 비활성. |
| 핫픽스가 from 자체인 경우 | 셀렉터에 핫픽스가 안 보이므로 사용자가 from 으로 직접 선택할 수 없음. (현 동작 변경 — 의도). |
| 같은 빌드에 ETC 파일과 ENGINE 파일이 섞여 있고 ENGINE 만 선택됨 | 그 빌드의 ETC 파일들이 같이 포함됨 (5.1.c). 운영 의도상 ETC 는 빌드와 한 묶음. |
| 어떤 빌드는 ETC 만 있고 WEB/ENGINE 파일이 없음 | picker 의 WEB/ENGINE 섹션에 노출되지 않음 → 그 빌드의 ETC 도 자동 포함되지 않음. ETC-only 빌드를 패치에 넣으려면 별도 운영 절차 필요(현 스펙 범위 외). |

---

## 10. 테스트 전략

### 백엔드
- `BuildFileServiceTest`: 신규 — `extractEngineName` 단위, `engine/UNKNOWN` 케이스, 정상 케이스 sub_category 저장 검증.
- `PatchGenerationServiceTest` (또는 신규): buildSelection 기반 분기:
  - 빌드 미포함 (toggle OFF) → 출력 디렉토리에 web/engine 없음.
  - WEB 1개 + ENGINE 일부 → 출력에 해당 파일들만.
  - Build-only (from == to) → DB 스크립트 0, 빌드 파일만.
  - 핫픽스 범위 안에 있을 때 → DB 시퀀스에 미포함 + 응답의 hotfixesInRange 비어있지 않음.
- `BuildsInRangeApiTest` (신규 통합): `/builds-in-range` 응답 형식 검증.

### 프론트엔드
- `PatchCreateForm.test`: toggle 동작, 자동 preselect, 일괄 액션, build-only 인디케이터, 검증 disabled 상태.
- `BuildPickerSection.test`: 후보 0 처리, dropdown 선택, “포함 안 함”, “모두 최신”/“모두 해제”.
- (선택) Playwright e2e: 1.0.0 → 1.1.0 + 빌드 픽 → 패치 생성 → 결과 ZIP 의 web/engine 디렉토리 검증.

---

## 11. 작업 분할 (구현 순서 제안)

> 자세한 단계는 후속 implementation plan 에서 결정. 본 문서는 큰 청크만 명시.

1. **데이터 모델 정정** — `BuildFileService` `subCategory` 저장 + 단위 테스트. 운영 DB 백필 SQL 운영자 검토용 첨부.
2. **백엔드 신규 API** — `builds-in-range` 엔드포인트 + DTO + 테스트.
3. **백엔드 패치 생성 입출력 변경** — buildSelection 입력, hotfixesInRange/includedBuilds 출력, 검증 룰.
4. **백엔드 누적 로직 단순화** — `findVersionsBetweenExcludingHotfixes` 만 사용. 기존 `includeAllBuildVersions` 분기 제거.
5. **프론트엔드 셀렉터** — `getVersionsFromTree` 가 base 만 노출하도록 필터.
6. **프론트엔드 BuildPickerSection 컴포넌트** — UI + 자동 preselect + 일괄 액션 + dropdown 행.
7. **프론트엔드 PatchCreateForm 통합** — buildSelection 폼 데이터 + 검증 + build-only 인디케이터 + 핫픽스 안내.
8. **e2e 검증 및 릴리즈 노트** 작성.

---

## 12. 비목표 / 후속 과제

- 빌드 picker 에서의 다중 선택(엔진별 N개) — 본 스펙은 1개씩만.
- 엔진 그룹화(NC_*, INFRAEYE_* 등으로 묶어 토글 한 번에 일괄 변경) — 운영 피드백 후 검토.
- 핫픽스 자체 다운로드 화면 변경 — 현 화면 유지.
- 빌드 ZIP 표준 강화(BuildZipValidator 가 `engine/{engineName}/...` 두 단계를 강제) — 별도 결정 필요. 현재는 경고 로깅까지만.
- 파일 카테고리 ETC 의 sub_category 활용 — 본 스펙 범위 외.
