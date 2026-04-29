# 패치 메타 (포함된 빌드 / 범위 안의 핫픽스) 영구 저장 설계

작성일: 2026-04-28
대상 영역: `release-manager-api` patch 도메인 + `release-manager-web` 패치 관리 UI
연관 작업: 본 spec 은 commit `b5308c1` (패치 생성 응답에 `includedBuilds` / `hotfixesInRange` / `isBuildOnly` 추가) 의 후속이다. 응답으로만 일회성 내려가던 빌드 메타정보를 패치 row 와 함께 영구 저장하여 목록·상세 화면에서 후행 추적할 수 있도록 한다.

---

## 1. 배경 및 문제

### 현 상태

- 패치 생성 시 `PatchGenerationService.generatePatch` 가 `GenerateResult` 를 반환하고 Controller 가 `GenerateResponse` 로 매핑해 응답 (`includedBuilds`, `hotfixesInRange`, `isBuildOnly`).
- 그러나 **패치 row 자체에는 빌드 메타가 저장되지 않는다**. 즉 패치 목록 / 상세 화면에서는 어떤 빌드가 포함됐는지 확인 불가.
- 운영 시나리오 결함:
  1. 패치 목록에서 "이 패치에 NC_SMS 가 포함됐나?" 한눈 파악 불가.
  2. 수개월 뒤 패치 ZIP 없이 메타만으로 빌드 구성 추적 불가.
  3. 회계/감사 추적 불가 — 어떤 시점에 어떤 buildSelection 으로 패치가 만들어졌는지 기록 부재.

### 목표

- 패치 row 와 함께 빌드 메타를 영구 저장해 위 3가지 시나리오를 지원한다.
- 빌드 버전 자체가 후일 삭제되어도 메타 (fullVersion snapshot) 는 보존되어야 한다.
- 본 spec 의 범위는 **저장 + 목록 배지 + 상세 섹션** 까지. 검색/필터는 본 spec 비목표 (별도 후속).

---

## 2. 결정 사항 요약

| # | 결정 | 의미 |
| --- | --- | --- |
| Q1 | **별도 테이블 풀 정규화** | `patch_included_build`, `patch_hotfix_in_range` 두 테이블 신설. patch 테이블에 `is_build_only`, `is_build_included` boolean 캐시 추가. 향후 검색·필터·집계에 강함. |
| Q2 | **빌드 row 삭제 시 SET NULL** | 메타에 저장된 `build_version_id` / `hotfix_version_id` 는 원본 row 삭제 시 NULL 로. `full_version` snapshot 은 보존. |
| Q3 | **DDL 은 Flyway 마이그레이션** (2026-04-29 정정) | `V6__add_patch_included_builds_and_hotfix_meta.sql` 로 추가, 부팅 시 자동 적용. 신규 환경 구축 보장. 초안의 「수동 SQL」 결정은 사용자 결정으로 뒤집힘. |
| Q4 | **기존 패치 row 의 메타는 비워둠** | 백필 안 함. 신규 패치 생성부터 메타가 채워진다. 기존 row 의 목록 배지는 "—" 또는 미노출. |
| Q5 | **검색/필터는 본 spec 비목표** | 목록의 keyword/customer/releaseType 필터는 그대로. 빌드 기준 필터는 별도 후속 사이클. |
| Q6 | **응답 통합** | `GenerateResponse` (생성 응답) 와 `DetailResponse` (상세 조회) 와 `ListResponse` (목록) 가 일관된 필드명을 쓴다 (`isBuildOnly`, `isBuildIncluded`, `includedBuilds`, `hotfixesInRange`). 목록은 요약 문자열 (`includedBuildsSummary`) 도 추가 제공. |

---

## 3. 데이터 모델

### 3.1 신규 테이블 — `patch_included_build`

```sql
CREATE TABLE patch_included_build (
    patch_included_build_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patch_id                BIGINT       NOT NULL,
    kind                    VARCHAR(10)  NOT NULL,    -- 'WEB' | 'ENGINE'
    engine_name             VARCHAR(50)  NULL,        -- kind='ENGINE' 일 때만
    build_version_id        BIGINT       NULL,
    full_version            VARCHAR(50)  NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pib_patch FOREIGN KEY (patch_id)
        REFERENCES patch_file(patch_id) ON DELETE CASCADE,
    CONSTRAINT fk_pib_build FOREIGN KEY (build_version_id)
        REFERENCES release_version(release_version_id) ON DELETE SET NULL,
    INDEX idx_pib_patch_id (patch_id),
    INDEX idx_pib_engine_name (engine_name)
);
```

**의미 명세**

| `kind` | `engine_name` | `build_version_id` |
| --- | --- | --- |
| `WEB` | NULL | 빌드 row id (삭제 시 NULL) |
| `ENGINE` | 엔진명 (예: `NC_SMS`, `UNKNOWN`) | 빌드 row id (삭제 시 NULL) |

`full_version` 은 빌드 row 삭제와 무관하게 보존되는 snapshot.

### 3.2 신규 테이블 — `patch_hotfix_in_range`

```sql
CREATE TABLE patch_hotfix_in_range (
    patch_hotfix_in_range_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patch_id                  BIGINT       NOT NULL,
    hotfix_version_id         BIGINT       NULL,
    full_version              VARCHAR(50)  NOT NULL,
    hotfix_version            INT          NOT NULL,
    created_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_phir_patch FOREIGN KEY (patch_id)
        REFERENCES patch_file(patch_id) ON DELETE CASCADE,
    CONSTRAINT fk_phir_hotfix FOREIGN KEY (hotfix_version_id)
        REFERENCES release_version(release_version_id) ON DELETE SET NULL,
    INDEX idx_phir_patch_id (patch_id)
);
```

### 3.3 기존 테이블 ALTER — `patch`

```sql
ALTER TABLE patch_file
  ADD COLUMN is_build_only     BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN is_build_included BOOLEAN NOT NULL DEFAULT FALSE;
```

목록 배지용 빠른 조회 캐시. 영구 저장된 메타 (위 두 신규 테이블) 와 일관성을 같은 트랜잭션에서 보장.

### 3.4 기존 패치 row 의 메타

- 신규 컬럼 default = `FALSE` 이므로 기존 row 는 "빌드 미포함 (또는 정보 없음)" 으로 표시된다.
- `patch_included_build` / `patch_hotfix_in_range` 도 행 0개 시작.
- **백필 안 함** (결정 Q4). 신규 패치 생성부터 메타가 채워진다.

---

## 4. 백엔드 API

### 4.1 변경 없음 — 패치 생성

`POST /api/patches/standard` 의 요청·응답 (`GenerateResponse`) 은 그대로. 다만 내부 동작에서 **메타가 영구 저장됨**.

### 4.2 변경 — 패치 상세 조회

`GET /api/patches/{patchId}` 의 `PatchDto.DetailResponse` 에 다음 필드 추가:

```java
@Schema(description = "Build-only 패치 여부", example = "false")
boolean isBuildOnly,

@Schema(description = "빌드 포함 여부", example = "true")
boolean isBuildIncluded,

@Schema(description = "포함된 빌드 정보 (빈 객체 가능)")
PatchDto.IncludedBuilds includedBuilds,

@Schema(description = "범위 안의 핫픽스 (별도 적용 안내용, 비어있으면 빈 배열)")
List<PatchDto.HotfixInRangeInfo> hotfixesInRange
```

`IncludedBuilds`, `IncludedWeb`, `IncludedEngine`, `HotfixInRangeInfo` 는 이미 `PatchDto` 에 정의됨. 재사용.

### 4.3 변경 — 패치 목록 조회

`GET /api/patches` (페이징) 의 `PatchDto.ListResponse` 에 다음 필드 추가:

```java
@Schema(description = "Build-only 패치 여부", example = "false")
Boolean isBuildOnly,

@Schema(description = "빌드 포함 여부", example = "true")
Boolean isBuildIncluded,

@Schema(description = "포함된 빌드 요약 (예: 'WEB·NC_SMS·NC_FAULT_MS', 빌드 미포함 시 null)", example = "WEB·NC_SMS·NC_FAULT_MS")
String includedBuildsSummary
```

`includedBuildsSummary` 는 다음 형식으로 생성:
- `is_build_included=false` → `null`
- 그 외 → `WEB · {engineName1} · {engineName2} · ...` (WEB 이 있으면 첫 번째에, 그 다음 엔진명 사전순). 길이 제한 없음 (UI 에서 ellipsis 처리).

**N+1 방지**: 목록 쿼리에서 `patch_included_build` 를 한 번의 left join + group_concat 으로 가져온다 (또는 별도 batch query 1번).

### 4.4 폐기 / 단순화

- 본 spec 으로 폐기되는 항목 없음.

---

## 5. 백엔드 비즈니스 로직

### 5.1 패치 생성 시 영구 저장

`PatchGenerationService.generatePatch` 의 끝부분 (현 commit `b5308c1` 에서 `GenerateResult` 를 반환하기 직전) 에서:

```
1. patch = patchRepository.save(patch)  // 기존
2. buildSelection 이 있고 enabled=true 이면:
   a. patch.isBuildIncluded = true
   b. selectedBuilds map 을 순회하며 PatchIncludedBuild 행 생성·save
      (kind, engine_name, build_version_id, full_version)
3. patch.isBuildOnly = (현 isSameBaseVersion 결과)
4. hotfixes 비어있지 않으면 PatchHotfixInRange 행들 save
5. patchRepository.save(patch)  // is_build_only, is_build_included 갱신
6. return new GenerateResult(...)  // 기존 응답
```

모두 같은 `@Transactional` 안. 실패 시 patch + 메타 모두 롤백.

### 5.2 헬퍼

`PatchGenerationService.buildIncludedBuilds(BuildSelection)` 는 이미 응답 매핑용으로 존재. 본 spec 에서는 동일 데이터 (selectedBuilds map) 를 영구 저장에도 활용. 헬퍼 1~2개 추가:
- `persistIncludedBuilds(Patch, BuildSelection, Map<Long, ReleaseVersion> selectedBuilds)`
- `persistHotfixesInRange(Patch, List<HotfixInRangeInfo>)`

`applyBuildSelection` 이 selectedBuilds 를 이미 만들고 있으므로 **반환 타입을 `Map<Long, ReleaseVersion>`** 으로 변경하면 buildIncludedBuilds + persistIncludedBuilds 가 동일 캐시를 재사용 (이전 cleanup 후보 [I-1] 도 동시 해소).

### 5.3 entity / repository

- 신규 entity: `PatchIncludedBuild`, `PatchHotfixInRange`
- 신규 repository: `PatchIncludedBuildRepository`, `PatchHotfixInRangeRepository`
- `Patch` entity 에 `@OneToMany(mappedBy = "patch", cascade = CascadeType.ALL, orphanRemoval = true)` 양방향 관계 추가 (선택). 단방향 (`PatchIncludedBuild.patch_id` 만) 으로도 충분.

### 5.4 목록 조회 N+1 방지

`PatchRepositoryCustom.findAllWithFilters` 가 페이징 결과의 patch_id 들을 모은 뒤 `PatchIncludedBuildRepository.findAllByPatchIdIn(patchIds)` 한 번 호출 → 메모리에서 group by + summary 생성. 또는 QueryDSL 의 join + group_concat 사용 (DB 종속). 단순한 batch query 방식 권장.

---

## 6. 프론트엔드

### 6.1 엔티티 타입 추가

`entities/patches/patch/model/types.ts` 에 다음 추가:

```ts
export interface PatchListItem {
  // ...기존 필드...
  isBuildOnly?: boolean
  isBuildIncluded?: boolean
  includedBuildsSummary?: string | null
}

export interface PatchDetailResponse {
  // ...기존 필드...
  isBuildOnly: boolean
  isBuildIncluded: boolean
  includedBuilds: IncludedBuilds
  hotfixesInRange: PatchHotfixInRangeInfo[]
}
```

`IncludedBuilds`, `IncludedWeb`, `IncludedEngine`, `PatchHotfixInRangeInfo` 는 이미 정의됨 (commit `1a9e3cb`).

### 6.2 목록 화면 — 행 배지

패치 관리 표 (`PatchTable.tsx`) 의 패치명 셀 또는 별도 컬럼에 작은 배지:

- `isBuildIncluded=true && includedBuildsSummary` → `🏗 WEB·NC_SMS·NC_FAULT_MS` (badge variant outline, 글자수 길면 ellipsis + tooltip).
- `isBuildOnly=true` → 추가 배지 `Build-only` (badge variant secondary 또는 색상).
- `isBuildIncluded=false` → 배지 미노출.

### 6.3 상세 화면 — 새 섹션

패치 상세 화면 (현재 PatchDetailPanel 또는 동등) 에 두 섹션:

```
┌─ 포함된 빌드 ─────────────────────────────┐
│ WEB    1.1.0.260428  (build #42)          │
│ ENGINE NC_SMS        1.1.0.260427  (#41)  │
│ ENGINE NC_FAULT_MS   1.1.0.260428  (#42)  │
└───────────────────────────────────────────┘

┌─ 범위 안의 핫픽스 (별도 적용 필요) ──────┐
│ 1.0.0.1  (hotfix #33)                     │
│ 1.0.0.2  (hotfix #34)                     │
└───────────────────────────────────────────┘
```

빌드/핫픽스 정보 0개면 해당 섹션 숨김. `isBuildOnly=true` 면 상단에 "빌드 전용 패치" 안내 배지.

### 6.4 빌드 row 가 삭제된 경우

`buildVersionId` 가 NULL 이면 `(build #—)` 또는 `(삭제됨)` 같은 placeholder 표시. `fullVersion` 은 항상 표시.

---

## 7. 마이그레이션 / 배포 순서

1. **백엔드 코드 배포** — Flyway 가 부팅 시 `V6__add_patch_included_builds_and_hotfix_meta.sql` 을 자동 적용 (FLYWAY_ENABLED=true). entity + repository + 저장 로직 + DetailResponse / ListResponse 확장도 동시.
2. **프론트엔드 코드 배포** — 타입 추가 + 목록 배지 + 상세 섹션.
3. **운영 검증** — 신규 패치 생성 1건으로 메타 저장 + 목록 배지 + 상세 섹션 동작 확인. 기존 패치 row 들이 "빌드 미포함" 으로 표시되는지 확인.

> **2026-04-29 정정**: 초안은 사용자 룰 「마이그레이션 파일 추가 지양」 에 따라 수동 SQL 로 갔으나, 신규 환경 구축 / 회귀 보장을 위해 사용자 결정으로 Flyway 마이그레이션을 도입하는 쪽으로 변경. DDL 은 마이그레이션으로 관리하는 표준 원칙을 따른다.

---

## 8. 적용 SQL (Flyway 마이그레이션으로 자동 적용)

> **2026-04-29 정정**: 본 SQL 은 `release-manager-api/src/main/resources/db/migration/V6__add_patch_included_builds_and_hotfix_meta.sql` 로 커밋되어 Flyway 가 부팅 시 자동 적용한다 (V5 의 `add_build_version` 스타일 따름 — ALTER 분리, COMMENT 명시). 신규 환경 구축 시에도 동일 스키마가 자동 동기화. 운영자는 별도 수동 적용 불필요.
>
> 아래는 마이그레이션 파일에 들어가는 DDL 을 spec 안에서도 가독성 차원에서 한 번 더 보여준다 (사전 백업 줄은 마이그레이션엔 포함되지 않음 — 환경 의존이라 운영 정책에 따라 별도 수행).

```sql
-- 사전 백업 (선택, 운영 정책에 따라 — 마이그레이션에 포함되지 않음):
CREATE TABLE patch_backup_20260428 AS SELECT * FROM patch_file;

-- 1) patch 테이블에 캐시 컬럼 추가
ALTER TABLE patch_file
  ADD COLUMN is_build_only     BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN is_build_included BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) patch_included_build 신규
CREATE TABLE patch_included_build (
    patch_included_build_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patch_id                BIGINT       NOT NULL,
    kind                    VARCHAR(10)  NOT NULL,
    engine_name             VARCHAR(50)  NULL,
    build_version_id        BIGINT       NULL,
    full_version            VARCHAR(50)  NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pib_patch FOREIGN KEY (patch_id)
        REFERENCES patch_file(patch_id) ON DELETE CASCADE,
    CONSTRAINT fk_pib_build FOREIGN KEY (build_version_id)
        REFERENCES release_version(release_version_id) ON DELETE SET NULL,
    INDEX idx_pib_patch_id (patch_id),
    INDEX idx_pib_engine_name (engine_name)
);

-- 3) patch_hotfix_in_range 신규
CREATE TABLE patch_hotfix_in_range (
    patch_hotfix_in_range_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patch_id                  BIGINT       NOT NULL,
    hotfix_version_id         BIGINT       NULL,
    full_version              VARCHAR(50)  NOT NULL,
    hotfix_version            INT          NOT NULL,
    created_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_phir_patch FOREIGN KEY (patch_id)
        REFERENCES patch_file(patch_id) ON DELETE CASCADE,
    CONSTRAINT fk_phir_hotfix FOREIGN KEY (hotfix_version_id)
        REFERENCES release_version(release_version_id) ON DELETE SET NULL,
    INDEX idx_phir_patch_id (patch_id)
);
```

> 위 DDL 은 V6 마이그레이션으로 자동 적용되므로 운영 DB 에 직접 실행하지 말 것 (Flyway schema_history 와 충돌).

---

## 9. 엣지 케이스

| 케이스 | 처리 |
| --- | --- |
| `buildSelection.enabled=false` 또는 null | `is_build_included=false`, `patch_included_build` 행 0개 |
| `buildSelection.enabled=true` + WEB 만 선택 | WEB 행 1개. `includedBuildsSummary = "WEB"` |
| `buildSelection.enabled=true` + ENGINE 만 N개 선택 | ENGINE 행 N개. `includedBuildsSummary = "NC_SMS·NC_FAULT_MS·..."` (사전순) |
| 빌드 row 삭제 후 메타만 남음 | `build_version_id=NULL`, `full_version` snapshot 유지. UI 에서 `(삭제됨)` 표시 |
| `is_build_only=true` (from==to + picker 1개 이상) | 목록에 Build-only 배지 |
| 핫픽스 0개 | `patch_hotfix_in_range` 행 0개. 상세 화면의 "범위 안의 핫픽스" 섹션 숨김 |
| 기존 패치 (메타 미저장) | `is_build_included=false`, `is_build_only=false`. 목록 배지 미노출 |
| 패치 row 삭제 | CASCADE 로 메타 행도 삭제 |

---

## 10. 테스트 전략

### 백엔드
- `PatchGenerationServiceTest` 확장:
  - **메타 저장 검증**: `buildSelection` 있을 때 `patch_included_build` / `patch_hotfix_in_range` 행이 정확한 수로 저장되는지 (mock 또는 H2 통합).
  - **isBuildOnly / isBuildIncluded 갱신 검증**: 다양한 buildSelection 조합에서 patch 의 boolean 캐시가 올바른지.
  - **빌드 row 삭제 시 SET NULL 동작**은 본 spec 의 비목표 (DB 제약 검증은 별도).
- `PatchRepositoryCustom.findAllWithFilters` 확장 — 페이징 + summary 매핑 검증.
- 통합: 기존 `PatchControllerTest` (또는 동등) 확장 — DetailResponse / ListResponse 의 새 필드 직렬화 검증.

### 프론트엔드
- 목록 배지 컴포넌트 — `isBuildIncluded=true/false`, summary 짧음/김에 따라 노출.
- 상세 섹션 — `includedBuilds` 비어있음 / WEB 만 / ENGINE 만 / 둘 다 / 핫픽스 1개+ 시나리오.
- (선택) Playwright e2e — 패치 생성 → 목록에서 배지 확인 → 상세 진입해서 섹션 확인.

---

## 11. 작업 분할 (구현 순서 제안)

1. **운영 DB 수동 SQL 작성·검토 (§8)** — 운영자 한 번에 적용 가능한 형태.
2. **백엔드 entity + repository** — `PatchIncludedBuild`, `PatchHotfixInRange`, 두 repository, Patch 의 boolean 컬럼.
3. **백엔드 영구 저장 로직** — `PatchGenerationService` 의 generatePatch 끝부분에서 메타 저장. `applyBuildSelection` 의 반환 타입을 selectedBuilds map 으로 변경하여 buildIncludedBuilds 와 공유. TDD.
4. **백엔드 응답 DTO 확장** — DetailResponse / ListResponse 에 새 필드 + 매핑. ListResponse 는 batch query 1번으로 N+1 방지.
5. **프론트엔드 타입 추가** — entities/patches.
6. **프론트엔드 목록 배지** — PatchTable 또는 동등.
7. **프론트엔드 상세 섹션** — 패치 상세 화면.
8. **검증** — 백엔드 빌드 + 프론트 type-check + 수동 시나리오.

---

## 12. 비목표 / 후속 과제

- **검색·필터** — 빌드 기준 ("NC_SMS 가 포함된 패치만") 은 본 spec 비목표 (Q5). 향후 별도 사이클.
- **기존 패치 백필** — 비목표 (Q4). 정확도 한계.
- **메타 export / 감사 리포트** — 본 spec 비목표.
- ~~Flyway 마이그레이션 도입~~ — 2026-04-29 사용자 결정으로 도입 결정. V6 마이그레이션 추가됨. (비목표에서 제외)
