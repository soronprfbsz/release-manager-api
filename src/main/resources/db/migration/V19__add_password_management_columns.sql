-- 비밀번호 관리(변경 · 초기화) 지원 컬럼 추가
-- must_change_password: 비밀번호 초기화 시 1, 변경 완료 시 0 (강제 변경 게이트 플래그)
-- last_password_changed_at: 비밀번호 변경/초기화 시각 (감사용)
ALTER TABLE account
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN last_password_changed_at DATETIME NULL;
