# 패치 이력 삭제 시 고객사 버전 정보 되돌리기 — 설계

- 작성일: 2026-06-18
- 대상: `release-manager-api` (백엔드 단독. 프론트 변경 없음)

## 1. 배경 / 문제

고객사 버전 정보는 **패치 이력에서 계산되는 값이 아니라, 패치 완료 시점에 별도로 저장된 값**이다.

- `customer_project.last_patched_version` — 마지막 패치의 `to_version` (헤더 VERSION 도출 기준 / 다음 패치 범위 추천 기준)
- `customer_site_version.current_version` — BASE / WEB / ENGINE 별 현재 버전 (헤더·빌드 상세 표시)
- `patch_history` — append-only 이력. 삭제 엔드포인트(`DELETE /api/patch-histories/{id}`) 존재

현재 `PatchHistoryService.deleteHistory()`는 `patch_history` 행만 삭제하고 위 저장값을 건드리지 않는다. 그래서 **이력은 지웠는데 버전 정보는 지운 패치가 반영된 채로 남는다.** 고객사 입장에서 "제거한 패치는 적용 안 한 것처럼" 보이지 않는다.

### 결정적 제약

`patch_history`는 독립 스냅샷이라 `from_version`/`to_version`만 갖는다. WEB/ENGINE 빌드의 상세 버전(예: `1.1.0.260511-1`)은 `patch_included_build`에만 있고, 이 테이블은 패치 완료 시 `patch_file`이 삭제될 때 **`ON DELETE CASCADE`로 함께 삭제**된다.

→ **이미 완료된 패치의 WEB/ENGINE 빌드 버전은 DB 어디에도 남아있지 않다.** BASE와 `last_patched_version`은 `to_version`에서 항상 복원되지만, WEB/ENGINE을 되돌리려면 **완료 시점에 빌드 스냅샷을 이력 쪽에 영구 저장하는 스키마 변경**이 선행되어야 한다. 이 변경은 **배포 이후 완료되는 패치부터만** 동작한다 (과거 이력은 데이터 없음).

## 2. 목표 / 성공 기준

- 이력 삭제 시, 해당 고객사+프로젝트의 버전 정보(BASE / WEB / ENGINE / `last_patched_version`)가 **남은 이력만 반영한 상태**로 즉시 재계산된다.
- 삭제한 패치에서만 추가됐던 엔진 행은 재계산 후 사라진다.
- 남은 이력이 없으면 "패치 미적용" 상태(= `last_patched_version` NULL, site_version 행 없음)가 된다.
- 표준 패치(고객사 미지정, `customer_id` NULL) 이력 삭제는 재계산 대상이 아니다(되돌릴 고객사 버전이 없음).

검증:
- 통합 테스트 — 패치 2건 완료 → 최신 이력 삭제 → 버전이 직전 이력 값으로 롤백되는지.
- 마지막 1건 삭제 → "패치 미적용" 상태가 되는지.
- 특정 엔진을 처음 추가한 패치 삭제 → 해당 엔진 행이 사라지는지.

## 3. 설계: "재계산 = 남은 이력 재생(replay)"

### 3-1. 빌드 스냅샷 영구화 (신규)

신규 테이블 `patch_history_build` — `patch_included_build`의 미러. 패치 완료 시점에 함께 저장하여, 이력 단위로 WEB/ENGINE fullVersion을 보존한다.

```
patch_history_build
  patch_history_build_id  BIGINT PK AUTO_INCREMENT
  history_id              BIGINT NOT NULL  (FK → patch_history.history_id, ON DELETE CASCADE)
  kind                    VARCHAR(10) NOT NULL   -- 'WEB' | 'ENGINE'
  engine_name             VARCHAR(50) NULL       -- ENGINE 일 때만
  full_version            VARCHAR(50) NOT NULL
  created_at              DATETIME NOT NULL
```

- 빌드는 패치당 1:N(WEB 1 + 엔진 N)이므로 컬럼이 아닌 자식 테이블로 둔다.
- `history_id` FK는 `ON DELETE CASCADE` — 이력 삭제 시 스냅샷도 함께 삭제.
- Flyway 마이그레이션 1건. **charset/collation 명시**(`DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci`) — MariaDB 10.10+ FK errno 150 회피 (프로젝트 관례).

엔티티 `PatchHistoryBuild` + `PatchHistory`에 `@OneToMany`(또는 별도 Repository) 추가. `PatchHistoryService.saveFromPatch()`(완료 처리 경로)에서 해당 패치의 `patch_included_build` 목록을 복사해 저장.

### 3-2. 삭제 시 즉시 재계산 (`deleteHistory` 확장)

`PatchHistoryService.deleteHistory(historyId)` 흐름 (단일 `@Transactional`):

1. 이력 조회. `customer`/`project` 캡처.
2. `patch_history` 행 삭제 (FK CASCADE로 `patch_history_build`도 삭제).
3. `customer`가 NULL이면(표준 패치) 종료.
4. **재계산(replay):**
   - `customer_site_version`에서 (customer, project) 행 전부 삭제.
   - 남은 `patch_history`를 (customer, project)로 조회, **`completed_at` ASC**(동률 시 `created_at` ASC) 정렬.
   - 각 이력을 순서대로 재생하며 `CustomerSiteVersionService.upsert` 호출:
     - **BASE**: `extractBaseVersion(toVersion)` → upsert (BASE, engineName=null)
     - **WEB**: 해당 이력의 `patch_history_build` 중 `kind='WEB'` → upsert (WEB, engineName=null)
     - **ENGINE**: `kind='ENGINE'` 각 행 → upsert (ENGINE, engineName)
     - upsert 의 `updated_at`/`updated_by` 는 그 이력의 `completed_at`/`completed_by` 사용.
   - 마지막(최신) 이력의 `to_version` / `completed_at` 으로 `customer_project.updateLastPatchInfo()` 갱신.
   - 남은 이력이 없으면: site_version 행은 이미 비워졌고, `customer_project` 의 `last_patched_version`/`last_patched_at` 을 NULL 로 (신규 메서드 `clearLastPatchInfo()` 또는 `updateLastPatchInfo(null, null)`).

재생 방식이라 "지운 패치만 빠진" 상태가 자연스럽게 만들어지고, 그 패치에서만 등장한 엔진 행도 재생에서 제외되어 소멸한다. 이는 `completePatch()`의 `applyCustomerSiteVersions` 로직과 동일한 upsert를 재사용하는 것이므로 동작 일관성이 보장된다.

### 3-3. 로직 재사용

- `extractBaseVersion()` / `BASE_VERSION_PATTERN` 은 현재 `PatchService` private. 재계산에서도 동일 규칙이 필요하므로 **공용 유틸 또는 `CustomerSiteVersionService` 메서드로 추출**하여 양쪽에서 재사용한다(중복 정의 금지).
- 재생 단위 로직(BASE/WEB/ENGINE upsert)은 `applyCustomerSiteVersions`와 사실상 같다. 소스만 `patch_included_build`(완료 시) vs `patch_history_build`(재생 시)로 다르다. 공통 헬퍼(`applyComponentVersions(customerId, projectId, baseVersion, builds, updatedBy, updatedAt)`)로 묶어 양쪽에서 호출하는 것을 권장.

## 4. 과도기 동작 (완전 재생 채택)

- 빌드 스냅샷이 없는 옛 이력은 재생 시 WEB/ENGINE 을 기여하지 못한다.
- 따라서 **스냅샷 없는 과거 패치가 (재생 순서상) 최신이면 그 시점 WEB/ENGINE 행은 재계산 후 사라진다** (= 해당 컴포넌트 "미적용"처럼 표시). BASE / `last_patched_version` 은 `to_version` 으로 정상 복원되므로 헤더 VERSION 은 유지된다.
- 시간이 지나 스냅샷 보유 패치로 교체되면 자연 정상화된다. 별도 백필은 불가능(원본 데이터 소멸).

## 5. 영향 범위

- **백엔드만 변경.** 프론트는 `customer_site_version` / `patch_history` 조회 결과를 그대로 표시하므로, 재계산 결과가 자동 반영된다. `CustomerDetailPanel`은 WEB/ENGINE 부재를 조건부 렌더링으로 이미 안전 처리.
- 변경 파일(예상):
  - Flyway: `V__create_patch_history_build.sql` (신규)
  - 엔티티: `PatchHistoryBuild`(신규), `PatchHistory`(연관 추가)
  - 리포지토리: `PatchHistoryBuildRepository`(신규), `PatchHistoryRepository`(고객사+프로젝트 조회·정렬 메서드)
  - 서비스: `PatchHistoryService.saveFromPatch()`(스냅샷 저장), `deleteHistory()`(재계산), `CustomerSiteVersionService`(공통 헬퍼/삭제 메서드), `PatchService`(헬퍼 추출)

## 6. 비목표 / 주의

- 패치 "완료"의 빌드 캡처는 변경 배포 이후부터만 스냅샷이 쌓인다.
- 표준(고객사 미지정) 이력 삭제는 재계산 없음.
- DDL 은 Flyway 마이그레이션으로만(프로젝트 관례). 1회성 데이터 보정 외 수동 SQL 금지.
