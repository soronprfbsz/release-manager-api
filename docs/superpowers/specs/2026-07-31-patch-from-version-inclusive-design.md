# 패치 생성 From 버전을 사이트 현재 버전 포함으로 변경 — 설계

- 작성일: 2026-07-31
- 대상: `release-manager-api` (1곳) + `release-manager-web` (2곳)

## 1. 배경 / 문제

패치 생성 폼은 사이트를 고르면 `GET /api/site-versions/next-patch-range` 결과로 From/To 를 자동 채운다.
현재 `SiteVersionService.getNextPatchRange()` 는 From 을 **사이트 현재 버전의 "직후" 버전**으로 제안한다.

```java
// SiteVersionService.java:231
fromCandidate = baseVersions.stream()
        .filter(v -> compareBase(v.getVersion(), currentBase) > 0)   // 직후 버전
        .findFirst()
        .orElse(null);
```

즉 사이트가 1.1.13 이면 From 은 1.1.14 가 된다. 그래서 **사이트가 1.1.13 을 적용한 뒤에 1.1.13 에 산출물이 추가되면 그 산출물은 어떤 패치에도 실리지 않는다.**

누락되는 경로는 두 가지이고, 둘 다 범위 하한이 `from` 을 포함(inclusive)한다.

| 경로 | 수집 코드 | 범위 조건 |
|------|-----------|-----------|
| 빌드 버전 (`1.1.13.260730` 류) | `ReleaseVersionRepositoryImpl.findBuildsInBaseRange` (`:349`) | `buildBaseVersion.releaseVersionId BETWEEN fromVersionId AND toVersionId` |
| 1.1.13 릴리즈에 직접 추가된 ENGINE/WEB/ETC 파일 | `PatchGenerationService.copySqlFiles` (`:1012`) | `betweenVersions` 각 버전의 전체 `ReleaseFile` |

From 이 1.1.14 면 1.1.13 은 두 범위 모두에서 빠진다.

### 겹침 1버전으로 충분한 이유

"사이트가 1.1.10 → 1.1.13 을 한 번에 받으면 1.1.11 의 사후 추가분도 누락되지 않나" 라는 우려가 있으나, 그렇지 않다.
사후 추가가 문제 되는 대상은 **이미 적용이 끝난 최고 버전(= `currentBase`) 하나뿐**이다. 그보다 낮은 미적용 버전은 어차피 다음 패치 범위에 통째로 포함된다. 따라서 `currentBase` 만 겹치면 논리적 빈틈이 없다.

예외는 `currentBase` 보다 **낮은** 버전에 사후 추가하는 경우인데, 정상 운영 흐름이 아니므로 대상에서 제외한다.

### 부수 효과로 함께 막히는 구멍

현재는 사이트가 최신(`currentBase` == 최신)이면 `suggestedFrom` 이 `null` 이고, 프론트는 "최신 상태" 안내만 띄운다(`PatchCreateForm.tsx:240`).
→ **최신 버전에 새 엔진 빌드가 올라와도 배포할 수단이 아예 없다.** From 을 `currentBase` 로 바꾸면 `from == to` 패치가 되고, 백엔드는 이미 이를 허용한다(`PatchGenerationService.java:800`, `:860`). 실무에서는 이쪽이 더 자주 발생하는 형태다.

## 2. 목표 / 성공 기준

- 사이트가 1.1.13, 최신이 1.1.15 → From 자동 제안이 **1.1.13**.
- 사이트가 1.1.13, 최신도 1.1.13 → From/To 모두 **1.1.13** (기존: From `null` + "최신 상태" 로 생성 불가).
- 1.1.13 에 사후 추가된 빌드/파일이 다음 패치에 포함된다.
- 패치 이력 없는 신규 사이트 → 가장 오래된 버전 (기존 동작 유지).
- `currentBase` 가 승인 버전 목록에 없으면 → 직후 버전 (기존 동작으로 degrade).

## 3. 설계

범위 하한을 `(currentBase, 최신]` → `[currentBase, 최신]` 으로 내린다. 수집 로직은 전부 이미 `from` inclusive 이므로, **제안값 산출과 그것을 받는 UI 조건만 고친다.**

### 3-1. 백엔드 (1곳)

`SiteVersionService.java:231` — 비교 연산자만 변경.

```java
.filter(v -> compareBase(v.getVersion(), currentBase) >= 0)   // > 0 → >= 0
```

- `currentBase` 가 승인 목록에 없으면 필터가 자연스럽게 직후 버전을 고르므로 **별도 fallback 코드가 필요 없다.**
- 사이트가 최신이면 `suggestedFrom == suggestedTo == currentBase` 가 되어 `from == to` 패치가 제안된다.
- L189 JavaDoc (`suggestedFrom: currentVersion 직후 버전`) 을 실제 동작에 맞게 갱신한다.

### 3-2. 프론트 (2곳)

**(a) `PatchCreateForm.tsx:156` — `from == to` 선택이 To 를 지우는 문제**

```ts
// 현재: from 을 to 와 같게 고르면 to 가 빈 문자열로 초기화된다
const toVersionCleared =
  prev.toVersion && compareVersions(value, prev.toVersion) >= 0 ? '' : prev.toVersion
```

`>= 0` → `> 0`. `from == to` 를 정상 상태로 만들려면 필수다.

**(b) `PatchCreateForm.tsx:240` — "최신 상태" 판정 기준**

`suggestedFromVersion === null` 분기는 이제 성립하지 않는다. 판정을 `suggestedFromVersion === suggestedToVersion` 으로 바꾸고, 문구를 "생성 불가" 가 아니라 "회수 패치" 로 읽히게 조정한다.

```
최신 상태 (1.1.13) — 추가된 빌드/파일만 회수합니다
```

### 3-3. 변경하지 않는 것 (확인 완료)

| 대상 | 확인 결과 |
|------|-----------|
| `PatchGenerationService.validateVersionRange` (`:800`) | `from == to` 이미 허용, `from > to` 만 거부 |
| `collectBetweenVersionsWithBuild` (`:860`) | `from == to` 도 inclusive 수집하도록 이미 수정됨 |
| `findBuildsInBaseRange` / `findReleaseFilesBetweenVersions` | 이미 `from` inclusive |
| `PatchGenerateFormCard.tsx:88` `filteredToVersions` | `compareVersions(v, from) >= 0` — From 자신을 To 후보에 포함 |
| `PatchGenerateFormCard.tsx:117` `submitDisabled` | From/To 존재 여부만 검사 |
| `validation.ts:31` | `compareVersions(from, to) > 0` 만 거부 — `from == to` 통과 |
| `PatchHistoryService` replay | `toVersion` + `patch_history_build` 스냅샷 기준. `from` 미사용 |
| `updateSiteProjectPatchInfo` (`:1484`) | `toVersion` 기준 |
| `MariaDBScriptGenerator` VERSION_HISTORY INSERT (`:396`) | `ON DUPLICATE KEY UPDATE` — 재실행 안전 |

## 4. 수용하는 부작용

1. **`currentBase` 의 DB SQL 이 매 패치마다 재실행된다.** `generatePatchScripts` 가 `sqlVersions.get(0)` = From 부터 SQL 을 수집하므로(`:1337`), 1.1.13 SQL 이 다시 패치에 실린다. `mariadb_patch_template.sh` 는 SQL 실패 시 **즉시 전체 중단**(`:69-88`, `set -e`) 이므로, 비멱등 스크립트 하나가 후속 버전 적용까지 막을 수 있다.
   → **버전별 SQL 을 멱등하게 작성하는 정책으로 감당한다는 결정.** 전환 직후 첫 패치들은 이미 등록된 과거 스크립트를 재실행하게 되므로, 위험 패턴(`IF NOT EXISTS` 없는 DDL, 무방비 `INSERT`, `UPDATE x = x + 1`)에 유의한다.
2. `currentBase` 의 파일이 패치에 다시 포함되어 패치 용량·적용 시간이 늘어난다.
3. 패치 표기가 `1.1.14 → 1.1.15` 에서 `1.1.13 → 1.1.15` 로 바뀐다. 운영자가 "1.1.13 은 이미 적용했는데" 로 읽을 여지가 있으나, README 문구 변경은 **이번 범위에서 제외**한다.

## 5. 검증

`SiteVersionService` 테스트 — 4케이스:

| 케이스 | 기대 `suggestedFrom` |
|--------|----------------------|
| currentBase=1.1.13, 최신=1.1.15 | 1.1.13 |
| currentBase=1.1.13, 최신=1.1.13 (이미 최신) | 1.1.13 (기존 `null`) |
| 패치 이력 없음 | 가장 오래된 버전 (기존 유지) |
| currentBase 가 승인 목록에 없음 | 직후 버전 (degrade 확인) |

통합 테스트 — 1.1.13 에 사후 빌드가 존재하는 상태에서 From=1.1.13 패치 생성 → 해당 빌드가 포함되는지.

빌드 검증 — `./gradlew test`, `npm run type-check`.

## 6. 배포

**파괴적 변경이 아니다.** 자동 채움은 From/To 를 한 번에 `set` 하므로 `handleFromVersionChange` 를 타지 않는다. 백엔드만 먼저 배포해도 오동작 없이 안내 문구만 어색해진다. 순서 제약 없이 함께 배포한다.
