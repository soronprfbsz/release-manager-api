-- ============================================================
-- V23: 미승인 버전 포함 패치 식별 플래그
--
-- 배경: ADMIN / DEVELOPER 는 승인되지 않은 버전으로도 패치를 생성할 수 있다
--       (내부 테스트 → 승인 워크플로). 이렇게 만들어진 패치가 정상 패치와
--       구분되지 않으면 고객사로 오배포될 수 있으므로 생성 시점 스냅샷으로
--       기록한다.
--
-- patch_history 에도 같은 컬럼을 두는 이유: 패치 완료 시 patch row 는 삭제되고
-- 이력만 남으므로, 컬럼이 없으면 '미검증 버전을 적용했다'는 사실이 소실된다.
-- (patch_history_build 를 별도로 보존하는 것과 같은 취지)
--
-- 기존 row 는 모두 승인된 버전으로만 생성되었으므로 default false 가 정확하다.
-- ============================================================

ALTER TABLE patch
    ADD COLUMN contains_unapproved BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '미승인 버전 포함 여부 (생성 시점 스냅샷)';

ALTER TABLE patch_history
    ADD COLUMN contains_unapproved BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '미승인 버전 포함 여부 (패치 생성 시점 스냅샷 복사)';
