-- ============================================================
-- V8: 패치 완료 워크플로 도입
--
-- 핵심 변경:
--   1) 기존 patch_file 데이터 일괄 삭제 (완전 리셋 — 사용자 결정)
--   2) patch_history 에 완료 메타 컬럼(completed_at, completed_by) 추가
--
-- 주의: completed_at NOT NULL 이므로 기존 row 가 있으면 아래 3단계로 처리:
--   (1) NULL 허용 컬럼으로 추가
--   (2) 기존 row created_at → completed_at 으로 채움
--   (3) NOT NULL 로 변경
-- ============================================================

-- 1) 기존 patch_file 데이터 일괄 삭제 (새 워크플로 시작)
DELETE FROM patch_file;

-- 2-a) completed_at 을 NULL 허용으로 먼저 추가
ALTER TABLE patch_history
    ADD COLUMN completed_at DATETIME NULL,
    ADD COLUMN completed_by VARCHAR(255) NULL;

-- 2-b) 기존 row 의 completed_at 을 created_at 으로 초기화 (운영 데이터 무손실)
UPDATE patch_history
SET completed_at = created_at
WHERE completed_at IS NULL;

-- 2-c) completed_at 을 NOT NULL 로 변경
ALTER TABLE patch_history
    MODIFY COLUMN completed_at DATETIME NOT NULL;

-- 3) 완료 일시 기준 인덱스
CREATE INDEX idx_ph_completed_at ON patch_history (completed_at);
