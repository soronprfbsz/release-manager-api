-- =========================================================
-- V5: release_version 테이블에 빌드 버전 컬럼 추가
-- =========================================================
-- 배경:
--   동일 DB 스키마를 유지한 채 WEB/ENGINE 빌드만 자주 갱신하는
--   운영 요구가 생겨, 별도의 빌드 버전을 관리할 필요가 생겼습니다.
--
-- 변경 내용:
--   1. build_version 컬럼 추가 (0=일반, 1+=빌드, 예: 260427)
--   2. build_base_version_id 컬럼 추가 (빌드의 원본 버전 FK)
--   3. UNIQUE KEY 변경: 기존 uk_project_type_customer_version 에 build_version 추가
--   4. 인덱스 추가: idx_build_version, idx_build_base_version_id
--   5. FK 추가: fk_release_version_build_base
--
-- 표기 예시:
--   일반:          1.1.0
--   핫픽스:        1.1.0.1
--   빌드:          1.1.0.260427
--   커스텀+빌드:   1.1.0-customerA.1.0.0.260427
--
-- 주의:
--   - MariaDB 안전을 위해 ALTER TABLE을 각각 분리하여 실행합니다.
--   - hotfix_version > 0 AND build_version > 0 동시 허용은
--     서비스 레이어에서 검증합니다 (스키마에서는 제약 없음).
-- =========================================================

-- ---------------------------------------------------------
-- 1. build_version 컬럼 추가
--    0 = 일반 버전 (빌드 아님)
--    1 이상 = 빌드 버전, 숫자 그대로 저장 (예: 260427)
-- ---------------------------------------------------------
ALTER TABLE release_version
    ADD COLUMN build_version INT NOT NULL DEFAULT 0 COMMENT '빌드 버전 (0=일반, 1+=빌드, 예: 260427)' AFTER hotfix_base_version_id;

-- ---------------------------------------------------------
-- 2. build_base_version_id 컬럼 추가
--    build_version > 0 인 경우 원본 기준 버전 ID를 참조합니다.
--    기준 버전이 삭제되면 NULL로 설정(ON DELETE SET NULL).
-- ---------------------------------------------------------
ALTER TABLE release_version
    ADD COLUMN build_base_version_id BIGINT NULL COMMENT '빌드 원본 버전 ID (build_version > 0 일 때 필수, FK → release_version)' AFTER build_version;

-- ---------------------------------------------------------
-- 3. 기존 UNIQUE KEY 제거
--    build_version 이 추가됨에 따라 유니크 조합이 변경됩니다.
--    (project_id, release_type, customer_id, version, hotfix_version)
--    → (project_id, release_type, customer_id, version, hotfix_version, build_version)
-- ---------------------------------------------------------
ALTER TABLE release_version
    DROP INDEX uk_project_type_customer_version;

-- ---------------------------------------------------------
-- 4. build_version 을 포함한 새 UNIQUE KEY 추가
--    같은 base 버전에 동일 build_version 이 존재하면 안 됩니다.
--    (재시도 시 +1 로직은 서비스 레이어에서 처리)
-- ---------------------------------------------------------
ALTER TABLE release_version
    ADD UNIQUE KEY uk_project_type_customer_version (project_id, release_type, customer_id, version, hotfix_version, build_version);

-- ---------------------------------------------------------
-- 5. build_version 인덱스 추가
--    빌드 버전 목록 조회 시 성능을 위한 인덱스
-- ---------------------------------------------------------
ALTER TABLE release_version
    ADD INDEX idx_build_version (build_version);

-- ---------------------------------------------------------
-- 6. build_base_version_id 인덱스 추가
--    특정 기준 버전의 빌드 목록 조회 시 성능을 위한 인덱스
-- ---------------------------------------------------------
ALTER TABLE release_version
    ADD INDEX idx_build_base_version_id (build_base_version_id);

-- ---------------------------------------------------------
-- 7. build_base_version_id FK 추가
--    ON DELETE SET NULL: 원본 버전 삭제 시 빌드의 FK를 NULL 처리
-- ---------------------------------------------------------
ALTER TABLE release_version
    ADD CONSTRAINT fk_release_version_build_base
        FOREIGN KEY (build_base_version_id)
            REFERENCES release_version (release_version_id)
            ON DELETE SET NULL;
