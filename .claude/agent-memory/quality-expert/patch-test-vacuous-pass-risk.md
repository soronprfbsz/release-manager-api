---
name: 패치 테스트 LENIENT mock 의 vacuous-pass 위험
description: PatchGenerationServiceTest 는 LENIENT mock — findBuildsInBaseRange stub 누락 시 빈 리스트 반환으로 누적 테스트가 허위 통과할 수 있음.
metadata:
  type: feedback
---

`PatchGenerationServiceTest` 는 `@MockitoSettings(strictness = Strictness.LENIENT)` 이다. 누적/동반 관련 테스트를 검증할 때, 통과(green)만 보고 "정상"이라 판단하면 안 된다. **stub 누락으로 인한 허위 통과(vacuous pass)** 여부를 반드시 별도 확인할 것.

**Why:** be646e6 (엔진 빌드 범위 누적) 리뷰 시, `unpickedEngineInBuildReleaseFile_cumulativeSkip` 가 통과 중이었으나 그 이유가 `releaseVersionRepository.findBuildsInBaseRange(...)` mock 을 stub 하지 않아 기본값 빈 List 가 반환되어 `accumulateBuildEngineFiles` 가 no-op 이 됐기 때문이었다. DisplayName 이 주장하는 "NC_SMS 가 엔진이라 누적 skip" 동작은 실제로 검증되지 않았고, stub 을 채우면 NC_SMS 가 누적 복사되어 `doesNotExist()` assert 가 실패한다 (격리 실험으로 확인). 즉 새 누적 동작에는 EngineNameClassifier 기반 디스크-walk skip 자체가 없다.

**How to apply:** `accumulateBuildEngineFiles` 가 관여하는 테스트는 `findBuildsInBaseRange` stub 이 그 테스트 블록 안에 명시돼 있는지 grep 으로 확인. 누락된 테스트가 "doesNotExist / 미포함" 류 assert 를 한다면 vacuous pass 의심. 의심되면 임시로 stub 을 주입해 실패하는지(=실제 동작 검증인지) 격리 실험으로 증명하고, 원복할 것. [[build-env]] 의 JAVA_HOME 방식으로 단일 테스트 `--tests '*PatchGenerationServiceTest.<method>' --rerun-tasks` 실행 가능.
