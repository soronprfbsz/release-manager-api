# 엔진 빌드 파일 범위 누적 + NC_AGENT_SERVER 특별 처리 제거 설계

- 일자: 2026-05-27
- 범위: `release-manager-api` (백엔드 전용 — 프론트/웹 변경 없음)
- 접근: **Approach A (외과적)** — 복사 로직만 누적으로 교체, picker / `buildSelection` / `PatchIncludedBuild` 메타는 보존.

## 1. 배경 / 문제

빌드의 `engine/` 산출물이 패치에 들어가는 두 경로가 따로 놀고, 둘 다 "최신 빌드 통째 복사"라 증분 빌드 간 파일이 유실된다.

- `applyBuildSelection` (picker 선택 빌드): 디렉토리형 엔진(`NC_AGENT_SERVER`)은 **선택된 단일 빌드의 폴더를 통째 복사**. 예) `1.0.0→1.1.2`에서 최신 빌드(`260527-1`, 2파일)만 선택돼, 이전 빌드(`260514-1`, 10파일)의 config/agent가 누락.
- `copyBuildSharedAssets`: `betweenVersions`(= base 버전들 + to-build) 를 돌며 `isBuild()` 필터 → 표준 패치엔 빌드 row가 거의 없어 사실상 to-build만 처리. 폴더 자산은 "최신 빌드 폴더 통째"라 deep merge 안 됨.
- `NC_AGENT_SERVER`만 `DIRECTORY_FORM_ENGINE_NAMES` 화이트리스트로 특별 분기되어, 화이트리스트 밖 폴더 엔진은 사각지대로 빠진다.

## 2. 요구사항 (확정)

1. `NC_AGENT_SERVER` 전용 특별 분기 제거 — 모든 엔진을 동일 취급.
2. 패치의 `engine/` = **from~to 범위의 모든 빌드를 상대경로(파일) 단위로 deep 누적**. 겹치는 경로는 **최신 빌드 승**. 파일/폴더·이름 무관 동일 규칙.
   - 누적 단위는 **개별 파일(상대경로)** 이며 디렉토리 통째가 아니다. 따라서 **단일 파일 내용을 병합하는 일은 절대 없다** — 같은 경로는 항상 최신 빌드의 파일로 덮어쓸 뿐.
   - **동치 (사용자 멘탈 모델과 일치):** "엔진별 최신 빌드파일만 + 기타파일은 누적" == "경로별 최신 누적".
     - **엔진 빌드파일** (`NC_SMS`, `NC_AGENT_SERVER`, `NC_GATEWAY`, `NC_CONF` 등 엔진명 파일): 각자 고유 경로라 자연히 **최신 빌드 게만** 남음 = "합치지 않고 엔진별 최신". 옛 빌드에만 있는 엔진 파일도 그 경로의 최신으로 포함.
     - **기타파일** (config.yml, agent zip, `*.sh` 등): 경로별로 누적되어 한 빌드에만 있어도 보존. 특정 버전으로 관리되지 않는 증분 자산.
   - 예) `1.0.0→1.1.2`: `NC_AGENT_SERVER` 바이너리 = `260527-1`(최신), `config/config.yml`·`nc_sms_agent/*.zip`(260514-1에만) = 보존. → `engine/foo/{a,b,c}` 식 누적, 겹치는 b는 최신 승.
   - **복사 단계에서 엔진/기타 구분 불필요** — 경로별 최신 누적이 둘 다 동일하게 처리.
3. WEB(`web/`)은 **변경 없음** — picker가 고른 최신 web 빌드 통째 교체.
4. picker / `buildSelection.engines` / `PatchIncludedBuild` 메타 / `.build_version` 은 **보존** (표시·기록용).

## 3. 설계

### 3.1 핵심: 엔진 누적 패스 신설

신규 메서드 `accumulateBuildEngineFiles(Path outputDir, List<ReleaseVersion> buildsAsc)`:

- 입력은 **범위 내 전체 빌드**를 누적 순서(아래)로 정렬한 리스트.
- 각 빌드의 `engine/` 서브트리를 `Files.walk` 로 순회, 상대경로 보존하여 `outputDir/engine/<rel>` 로 복사 (`REPLACE_EXISTING`).
- **엔진/공유자산 구분(`isEngineFile`) 없이 engine/ 아래 전부 복사** — 엔진 바이너리도 이 패스가 처리.
- **실행 비트 보존**: 정규 파일 복사 후 source 의 POSIX permission 을 dst 에 적용(비-POSIX FS 면 `setExecutable` 폴백). 엔진 바이너리·`*.sh` 가 +x 유지되도록 (기존 단일파일 엔진 복사의 perm 보존 로직 계승).

**빌드 소스**: `betweenVersions` 가 아니라
`releaseVersionRepository.findBuildsInBaseRange(projectId, fromVersionId, toVersionId, customerScope)` 결과를 사용.
- 표준 패치(`generatePatch`): `customerScope = null` (표준 빌드는 customer=null).
- 커스텀 패치(`generateCustomPatch`): `customerScope = customerId`.

**누적 순서 (최신 승 보장)**: 빌드를 `buildBaseVersion`의 (major, minor, patch) asc → `buildVersion` asc → `buildIteration` asc 로 정렬해 **오래된 것부터** 복사. `REPLACE_EXISTING` 이므로 마지막(최신)이 살아남는다. (`findBuildsInBaseRange` 는 `buildVersion` DESC 단일 정렬이라 cross-base 순서가 부정확 → 호출부에서 재정렬.)

### 3.2 교체/제거

- `applyBuildSelection`: **WEB 복사만 남김**. 엔진 복사 루프 제거. 반환 `selectedBuilds` 는 web 빌드만 담음.
  - 엔진 메타는 `persistIncludedBuilds`/`buildIncludedBuilds`/`generateBuildVersionFile` 가 이미 `selectedBuilds.get(id) ?? loadBuildVersion(id)` 폴백을 가지므로, web-only map 으로도 정상 동작 (engine 은 loadBuildVersion 폴백).
- `copyBuildSharedAssets`: **삭제** (누적 패스가 대체). `BuildFileCopyException`/`copyDirectoryReplaceExisting` 는 WEB 복사가 계속 쓰므로 유지.
- `generatePatch` / `generateCustomPatch` 의 호출부: `applyBuildSelection`(web) → `accumulateBuildEngineFiles`(engine) 순서로 교체. 진행도 단계 라벨(현재 "WEB / ENGINE 빌드 파일 복사", "빌드 공유 자산 동반")은 의미에 맞게 정리.
- `copySqlFiles` 의 `pickerEngineNames` skip 인자: 빌드 엔진이 누적 패스로 옮겨가도, 누적 패스가 마지막에 `REPLACE_EXISTING` 으로 덮으므로 동작 정합. **이번 범위에선 `copySqlFiles` 시그니처·로직 변경 없음** (버전 ZIP 업로드의 ReleaseFile ENGINE 처리는 별개 소스라 보존). 구현 중 충돌 확인.

### 3.3 분류기 / picker 표시

- `EngineNameClassifier.DIRECTORY_FORM_ENGINE_NAMES` 및 관련 javadoc **제거**. `isEngineFile` 자체는 picker 표시용으로 **유지**.
- `BuildsInRangeService.engineNamesInBuild`: 디렉토리 화이트리스트 분기 **제거** → **top-level 정규 파일 중 `isEngineFile` 통과만** 엔진 후보(기존 테스트 `engineDirectory_ignoredOnlyRegularFilesAreEngines` 와 정합, 사용자의 "엔진=파일" 모델과 일치). 디렉토리는 picker/메타 후보 아님.
  - **전환기 한계**: 빌드가 아직 `engine/NC_AGENT_SERVER/`(디렉토리)로 적재되면 picker/메타엔 노출되지 않는다(= `.build_version` 의 `build_engine_NC_AGENT_SERVER` 라인 생성 안 됨). **복사(누적)에는 그 내용이 포함**되므로 패치 산출물은 정상. 빌드가 `engine/NC_AGENT_SERVER`(단일 파일)로 가면 자연히 picker/메타에 노출. 이 한계는 본 변경의 비목표(복사 정확성이 우선).

### 3.4 README 생성

- `generateReadme` / `generateCustomReadme` 의 `engine/NC_AGENT_SERVER/`·`patch_nc_agent_server.sh` 자동 실행 특별 문구(현 `:513`, `:1529`) **제거 또는 일반화** ("엔진 바이너리 패치" 일반 문구로 흡수).

## 4. 영향 / 비목표

- **프론트엔드 변경 없음** (Approach A). 직전 customerId 수정(표준 빌드 picker null 전달)은 그대로 유효.
- **메타 영구저장(PatchIncludedBuild) 스키마·기능 불변.** 단 폴더 엔진(NC_AGENT_SERVER)이 picker 후보로 들어오면 메타에 1행 기록됨(버전 라벨 = 최신 빌드 fullVersion, 내용은 범위 누적).
- 비목표: 엔진 picker 의 전면 제거(Approach B), web 누적, copySqlFiles 재설계.

## 5. 엣지 / 검증

- 범위 내 빌드 0개 → `engine/` 미생성 (현행 동일).
- 같은 상대경로가 여러 빌드에 → 최신 빌드 내용. 한 빌드에만 있는 파일 → 보존(누적). ← 핵심 회귀 테스트.
- 실행 비트: 누적된 엔진 바이너리 / `*.sh` 가 +x 유지.
- `1.0.0→1.1.2` (260514-1[10] + 260527-1[2]) → `engine/NC_AGENT_SERVER/` 에 **합쳐진 10개 경로** (binary/patch.sh 는 260527-1, config/agent 는 260514-1).
- 기존 테스트: `PatchGenerationServiceTest` 등에서 엔진 복사 가정 사용 시 갱신 필요. `DIRECTORY_FORM_ENGINE_NAMES`/`NC_AGENT_SERVER` 직접 참조 테스트는 없음(grep 확인).
- 빌드 검증: WSL `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` 로 `./gradlew compileJava` + 핵심 테스트.
