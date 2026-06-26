# PRD-001 · 비밀번호 관리 (변경 · 초기화)

| | |
|---|---|
| **상태** | 승인 (구현 대기) |
| **작성일** | 2026-06-25 |
| **범위** | release-manager-api + release-manager-web |
| **관련 문서** | [CONTEXT.md](../../CONTEXT.md) · [ADR-0001](../adr/0001-temp-password-display-and-relay.md) · [ADR-0002](../adr/0002-forced-change-on-reset.md) |

## 1. 개요

두 가지 비밀번호 흐름을 제공한다. 용어는 [CONTEXT.md](../../CONTEXT.md)를 따른다 — **변경(Change)** 과 **초기화(Reset)** 는 주체·전제·전달 방식이 다른 별개 개념이며 혼용하지 않는다.

- **비밀번호 변경** — 본인이 "내 정보"에서 **현재 비밀번호를 입력해** 새 값으로 바꾼다.
- **비밀번호 초기화** — `ADMIN`/`OPERATOR`가 "계정 관리"에서 **다른 Account**의 비밀번호를 시스템 생성 **임시 비밀번호**로 덮어쓰고, 화면에 1회 표시된 값을 대상 User에게 대역으로 전달한다. 대상자는 다음 로그인 시 **강제로 변경**한다.

## 2. 현황 (구현 전 기준)

- 자가 변경은 `ProfileEditForm` + `PATCH /api/accounts/me`(`MyAccountUpdateRequest.password`)로 **이미 동작하나 현재 비밀번호 검증이 없다.** → 보안 강화 대상.
- 비밀번호 초기화·임시비번 생성·강제변경·전용 권한은 **전무.**
- 해싱: `BCryptPasswordEncoder`(Bean `passwordEncoder`). 비번 컬럼 `account.password VARCHAR(100)`.
- 인가: `@PreAuthorize` 없음 — 서비스에서 `SecurityUtil`로 수동 검사.
- 이메일 발송 인프라 없음.

## 3. 범위

### In scope
1. 자가 비밀번호 **변경** 보안 강화 (현재 비번 검증, 전용 엔드포인트).
2. 관리자 비밀번호 **초기화** (랜덤 임시비번 발급 + 화면 1회 표시/복사).
3. 임시비번 **최초 로그인 강제 변경** 게이트.
4. 신규 엔드포인트의 **서버 측 권한 강제**.

### Out of scope (후속/별건)
- 이메일/SMS 자동 발송.
- 기존 `PUT/DELETE /api/accounts/{id}`의 미강제 인가 갭 보강 (§11 리스크 참조).
- 비밀번호 만료(주기적 강제 변경), 비밀번호 재사용 이력 정책, 유출 비번 차단(HIBP).
- 변경 시 타 세션/Refresh Token 일괄 무효화 (권고이나 v1 제외, §10).

## 4. 사용자 시나리오

1. **본인 변경**: 로그인 사용자가 "내 정보" → "비밀번호 변경" 섹션에서 현재/새/새 확인 입력 → 저장 → 성공 토스트.
2. **관리자 초기화**: OPERATOR가 계정관리 목록에서 대상 행 ⋯ → "비밀번호 초기화" → 확인 다이얼로그 → 실행 → 임시비번이 모달에 1회 표시(복사 버튼 + 안내 문구) → 대상자에게 전달.
3. **강제 변경**: 대상자가 임시비번으로 로그인 → 강제변경 게이트 표시(앱 차단) → 현재(=임시)/새/새 확인 입력 → 완료 후 정상 진입.

## 5. 기능 요구사항

### 5.1 비밀번호 변경 (자가)
- "내 정보"(`ProfileEditForm`) 내 **독립 "비밀번호 변경" 섹션** + 자체 제출 버튼. 프로필 저장과 분리.
- 입력: 현재 비밀번호, 새 비밀번호, 새 비밀번호 확인.
- 서버는 현재 비밀번호를 `passwordEncoder.matches()`로 검증 후에만 변경.
- 성공 시 `must_change_password=false`, `last_password_changed_at=now` 갱신.
- `ProfileEditForm`/`MyAccountUpdateRequest`/`PATCH /me`에서 **비번 처리 제거** (검증 없는 경로 폐쇄).

### 5.2 비밀번호 초기화 (관리자)
- 진입: 계정관리 `AccountTable` 행 액션 메뉴에 "비밀번호 초기화"(권한·대상 조건 충족 시 노출).
- 확인 다이얼로그 → 실행 → 시스템이 임시비번 생성·해시 저장.
- 부수효과: `must_change_password=true`, `login_attempt_count=0`, `locked_until=null`, `last_password_changed_at=now`.
- 응답으로 **평문 임시비번 1회 반환** → 모달에 표시 + 복사 버튼 + 전달용 안내 문구. 평문은 어디에도 저장하지 않음.

### 5.3 강제 변경 게이트
- 로그인 응답 및 `GET /me`에 `mustChangePassword` 포함.
- `true`면 프론트는 **전역 차단 게이트**(강제변경 화면) 표시 — 변경 완료 전까지 다른 화면 접근 불가. 로그아웃만 허용.
- 게이트는 §5.1과 **동일한 변경 엔드포인트**를 사용(현재 비번=임시비번). 성공 시 플래그 해제 후 정상 진입.

## 6. API 설계

### 6.1 자가 변경 — `POST /api/accounts/me/password`
```
Request  { currentPassword: string, newPassword: string }
200      { } (또는 갱신된 MyAccount)
400 INVALID_CURRENT_PASSWORD   현재 비밀번호 불일치
400 PASSWORD_POLICY_VIOLATION  정책 위반(§8)
400 PASSWORD_SAME_AS_CURRENT   새 비번이 현재와 동일
```
- 인증 필수. 대상은 `SecurityUtil.getCurrentAccountId()` 본인 고정.

### 6.2 관리자 초기화 — `POST /api/accounts/{id}/reset-password`
```
Request  {} (본문 없음)
200      { temporaryPassword: string }   ← 1회성, 재조회 불가
403 FORBIDDEN          권한 없음(§7) — OPERATOR가 ADMIN 대상 / 비권한자
400 CANNOT_RESET_SELF  본인 계정(=현재 로그인) 대상
404 ACCOUNT_NOT_FOUND
```
- 서버에서 호출자 역할·대상 역할·본인여부 검증(§7).

### 6.3 로그인/내정보 응답 확장
- `POST /api/auth/signin`, `GET /api/accounts/me` 응답에 `mustChangePassword: boolean` 추가.

## 7. 권한 매트릭스 (서버 강제)

| 호출자 \ 대상 | 본인 | USER/DEVELOPER | OPERATOR | ADMIN |
|---|---|---|---|---|
| **ADMIN** | ✕(변경 사용) | ✓ | ✓ | ✓ |
| **OPERATOR** | ✕(변경 사용) | ✓ | ✓ | **✕** |
| USER/DEVELOPER | — | ✕ | ✕ | ✕ |

- 자가 **변경**: 모든 인증 사용자가 본인에 한해 가능.
- 프론트는 버튼 노출도 동일 규칙으로 제어하되, **서버 검증이 최종 권위**.
- `permissions.ts`에 `account.resetPassword = ['ADMIN','OPERATOR']` 추가, `usePermission.canResetAccountPassword`. OPERATOR→ADMIN·본인 제외는 대상 행 단위로 추가 판정.

## 8. 비밀번호 정책 (NIST SP 800-63B 정렬)

사용자가 직접 정하는 비밀번호(자가 변경·강제 변경)에 적용:
- 최소 **8자**, 최대 **64자** (BCrypt 72바이트 절단 회피).
- **조합 규칙 강제 없음** (대/소/숫자/특수 의무화하지 않음).
- 새 비밀번호 = 현재 비밀번호 **금지**.
- 프론트(Zod) + 백엔드 **양쪽 검증**(프론트 검증은 우회 가능하므로 서버가 최종).

## 9. 임시 비밀번호 생성 규칙

- `java.security.SecureRandom` 사용.
- 길이 **12자**. 사람이 받아쓰는 값이므로 **혼동 문자 제외**(`0 O o 1 l I`).
- 영문 대문자·소문자·숫자·안전 특수문자(`@#$%*?` 등) 각 **최소 1개** 보장.
- 생성 즉시 BCrypt 해시 저장, 평문은 응답으로만 1회 노출.
- 신규 유틸: `PasswordGenerator`(api `global/util` 등).

## 10. 데이터 모델 / 마이그레이션

신규 Flyway 마이그레이션 **`V19__add_password_management_columns.sql`** (V18이 최신):
```sql
ALTER TABLE account
  ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN last_password_changed_at DATETIME NULL;
```
- `must_change_password`: 초기화 시 1, 변경 완료 시 0. 기존 행은 0(영향 없음).
- `last_password_changed_at`: 감사용. 변경/초기화 시 갱신.
- DDL은 Flyway로만 (운영 스타일 준수). 신규 컬럼만이라 charset/collation 이슈 없음.

## 11. 보안 고려사항 / 알려진 리스크

- **임시비번 화면 노출**: 운영자가 평문을 보게 됨 → 그래서 §5.3 강제 변경으로 위험 창을 닫는다(ADR-0002). 권한 상승 방지를 위해 OPERATOR→ADMIN 초기화 차단(§7).
- **평문 미저장**: 임시비번은 응답 1회만. 로그(api_log 등)에 본문이 남지 않도록 주의(응답 바디 마스킹 확인).
- **무차별 대입**: 기존 `login_attempt_count`/`locked_until` 잠금 정책 유지. 초기화 시 잠금 해제.
- **[후속 과제]** `PUT/DELETE /api/accounts/{id}`가 Swagger상 "ADMIN 전용"이나 코드 미강제. 본 PRD 범위 밖이나 별도 정리 필요(계정수정 권한 정책: ADMIN 전용 vs ADMIN+OPERATOR 확정 선행).
- **[권고, v1 제외]** 비밀번호 변경 시 타 세션/Refresh Token 무효화.

## 12. UI/UX

- **내 정보 — 비밀번호 변경 섹션**: 현재/새/새 확인 + 표시토글(기존 Eye/EyeOff 패턴 재사용), 자체 "비밀번호 변경" 버튼, 성공 토스트.
- **초기화 확인 다이얼로그**: `AlertDialog` 패턴. 대상 계정명/이메일 명시, 되돌릴 수 없음 경고.
- **임시비번 결과 모달**: 큰 모노스페이스 표기 + 복사 버튼(`copyToClipboard`) + 전달용 안내 문구. 닫으면 재확인 불가 경고.
- **강제 변경 게이트**: 로그인 직후 전체 차단 화면. 다른 라우트 접근 차단, 로그아웃만 허용.
- 모든 확인/제출은 진행 중 버튼 로딩·중복 제출 방지(기존 패턴).

## 13. 구현 작업 분해

### 백엔드 (release-manager-api)
1. `V19` 마이그레이션 + `Account` 엔티티 필드(`mustChangePassword`, `lastPasswordChangedAt`).
2. `PasswordGenerator` 유틸(SecureRandom, §9).
3. 비번 정책 검증 유틸/공통 로직(§8).
4. `POST /api/accounts/me/password` (현재 비번 검증 → 변경 → 플래그 해제).
5. `POST /api/accounts/{id}/reset-password` (권한·대상 검증 → 임시비번 발급 → 부수효과 → 평문 1회 반환).
6. `PATCH /me`/`MyAccountUpdateRequest`에서 password 제거.
7. signin/`GET /me` 응답에 `mustChangePassword` 추가.
8. 에러코드(`ErrorCode`) 추가: INVALID_CURRENT_PASSWORD, PASSWORD_SAME_AS_CURRENT, CANNOT_RESET_SELF 등.

### 프론트 (release-manager-web)
1. `accountApi`: `changeMyPassword`, `resetPassword` + TanStack mutation 훅.
2. `MyAccountUpdateRequest`에서 password 제거, 비번 변경 타입 추가.
3. `ProfileEditForm`: 비번 필드 제거 → 독립 "비밀번호 변경" 섹션 신설(Zod 검증, §8).
4. `AccountTable` 행 액션 "비밀번호 초기화" + 확인 다이얼로그 + 결과(임시비번) 모달.
5. `permissions.ts`/`usePermission`: `account.resetPassword`, `canResetAccountPassword` + 대상 행 조건.
6. 로그인/세션에 `mustChangePassword` 반영 → 강제변경 게이트 컴포넌트 + 라우팅 가드.

## 14. 검증 / 완료 기준

- 자가 변경: 현재 비번 틀리면 거부, 맞으면 변경 후 새 비번으로 로그인 성공.
- 초기화: OPERATOR가 USER 초기화 → 임시비번 1회 표시 → 그 값으로 로그인 → 강제변경 게이트 → 변경 후 정상.
- 권한: OPERATOR가 ADMIN 초기화 시 403, 본인 초기화 시 거부(버튼 비노출 + 서버 거부 동시 확인).
- 정책: 7자/65자/현재와 동일 → 거부(프론트·백엔드 모두).
- `npm run type-check`·`./gradlew test` 통과.

## 15. 의사결정 로그 (그릴 결과)

| # | 결정 |
|---|---|
| Q1 | 자가 변경 = 보안 강화 + 전용 플로우 |
| Q2 | 내 정보 폼 내 **독립 섹션**(저장 분리) |
| Q3 | 정책 = NIST 정렬(8~64, 조합 비강제, 동일 금지) |
| Q4 | 초기화 비번 = **최초 로그인 강제 변경** (ADR-0002) |
| Q5 | OPERATOR→ADMIN 차단 + 본인 제외 |
| Q6 | 전달 = **화면 1회 표시 + 복사**(이메일 없음, ADR-0001) + 초기화 시 잠금 해제 |
| Q7 | 신규 비번 엔드포인트만 서버 강제(기존 갭은 후속) |
| Q8 | 문서 = `docs/prd/`, ADR = `docs/adr/` |
