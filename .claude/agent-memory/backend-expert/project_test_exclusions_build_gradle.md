---
name: project-test-exclusions-build-gradle
description: build.gradle test 태스크가 깨진 테스트 클래스 여러 개를 exclude 중 — "210건 통과"는 이 제외분을 뺀 수치
metadata:
  type: project
---

`release-manager-api/build.gradle` 의 `test { java { exclude ... } }` 블록에 시그니처 변경/엔티티 부재 등으로 깨진 테스트 클래스가 다수 exclude 되어 있다. 따라서 `./gradlew test` 의 "통과 건수"는 전체 테스트가 아니라 **제외분을 뺀** 수치다.

**Why:** 기능 코드(비밀번호 관리 등)를 먼저 머지하면서, 그에 맞춰 재설계가 필요한 기존 단위 테스트를 임시로 exclude 해 빌드 그린을 유지해온 운영 방식. 각 exclude 줄 옆 주석에 깨진 이유가 적혀 있다.

**How to apply:**
- "기존 N건 안 깨지게"라는 요청을 받으면, 그 N건에 exclude 된 클래스는 애초에 포함돼 있지 않음을 인지할 것.
- 특정 도메인 테스트를 추가/수정하려는데 `--tests` 로 "No tests found" 가 나오면 build.gradle exclude 를 먼저 의심.
- exclude 를 풀면 그 클래스의 **기존 메서드들**이 현재 서비스 시그니처와 안 맞아 무더기로 깨질 수 있음. 비밀번호 작업 때 `AccountServiceTest` exclude 를 풀자 기존 11개가 실패(getAccounts→findAllWithFilters 통합 쿼리 리팩터, deleteAccount→findByAccountId+delete, adminUpdateAccount role 변경 시 SecurityUtil.getCurrentRole 호출, mapper.toDetailResponse 미사용으로 인한 UnnecessaryStubbing 등)했고 현재 서비스에 맞춰 수정해 해소함.
- 남은 exclude 후보(customer/dashboard/filesync/releaseversion/scenario 등)도 같은 성격의 부채. 손대려면 같은 식으로 현재 시그니처에 맞춰 재설계 필요.

[[api-test-suite-stale-constructors]] 와 연관 (과거 compile 단계 부채는 해소됐으나 runtime 호환 부채는 exclude 로 남아있던 것).
