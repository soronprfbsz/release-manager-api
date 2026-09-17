---
name: patch-history-delete-rollback-review
description: 2026-06-18 패치 이력 삭제 시 버전 재계산(replay) 기능 머지 전 통합 리뷰 결과 — clear→replay flush ordering 의존성이 핵심 미묘점
metadata:
  type: project
---

기능: patch_history 삭제 시 patch_history_build(신규 스냅샷 테이블) 기반으로 customer_site_version + last_patched_version 을 남은 이력 완료순 재생(replay)으로 재계산. 백엔드 단독, 3커밋(8fb61ea..1e19dd1).

**리뷰 결론: READY (Critical 0). compileJava 통과, 신규 테스트 2종(5 케이스) PASS.**

핵심 통합 검증 결과:
- **clear→replay flush ordering 의존성**: `recomputeCustomerVersions` 가 `clearByCustomerAndProject`(파생 delete, @Modifying 없음 → select-then-em.remove) 후 `applyComponentVersions`(upsert 의 findBy JPQL 이 auto-flush 트리거)를 호출. 현재는 첫 findBy 의 auto-flush 가 DELETE 를 먼저 밀어내 정합성 OK. 단 향후 `deleteAllBy...` 를 `@Modifying @Query` 벌크 delete 로 바꾸면 영속성 컨텍스트 불일치 위험. **암묵적·미묘하므로 주석 가치 있음(Important).**
- **completePatch(누적) vs replay(clear후 전체재생) 비대칭**: 빌드 미포함 패치는 completePatch 에서 WEB/ENGINE "이전값 유지", replay 에서는 스냅샷 0건이라 미기여. 그래도 "삭제한 1건만 뺀 상태"와 종단 일치함(설계 §4 완전재생 의도대로 빌드 도입 패치 삭제 시 WEB/ENGINE 소멸이 올바름).
- **트랜잭션**: deleteHistory @Transactional 안에서 별도 빈 CustomerSiteVersionService 메서드(@Transactional REQUIRED) 호출 → 같은 트랜잭션 합류. private recompute 도 동일 트랜잭션. 원자적.
- **FK/Flyway V17**: history_id BIGINT FK, patch_history(utf8mb4_unicode_ci)와 collation 일치, ON DELETE CASCADE + ORM deleteAllByHistory_HistoryId 중복 안전. V17 번호 충돌 없음(최신 V16).
- **completePatch 순서**: saveFromPatch(스냅샷 복사) → ... → patchRepository.delete(patch)(patch_included_build CASCADE 삭제) 순서라 복사 시점에 원본 살아있음. OK.

알려진 Minor(saveFromPatch 로그 `{}건` 삼항 "포함"/0 비대칭) triage: **머지 차단 아님**. 가독성만. 후속 정리 권장.
