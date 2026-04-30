-- =========================================================
-- V7: release_version 에 build_iteration 컬럼 추가
-- =========================================================
-- 배경:
--   기존엔 동일 base 에 동일 build_version (yyMMdd) 이 충돌하면
--   build_version 자체를 +1 (260430 → 260431) 로 회피했다. 그러나
--   날짜가 의미를 잃어 표기가 혼란스러우므로, 같은 날짜 안의 회차는
--   '-N' 접미로 구분한다 (예: 1.0.0.260430-1, 1.0.0.260430-2).
--
-- 변경 내용:
--   1. build_iteration 컬럼 추가 (0=일반 버전, 1+=빌드 회차)
--   2. 기존 빌드 행은 build_iteration=1 로 채움
--   3. UNIQUE KEY 갱신: (..., build_version) → (..., build_version, build_iteration)
--
-- 표기 예시:
--   일반:           1.1.0
--   핫픽스:         1.1.0.1
--   빌드:           1.1.0.260430-1
--   같은 날 추가:   1.1.0.260430-2
--   커스텀+빌드:    1.1.0-customerA.1.0.0.260430-1
-- =========================================================

-- 1. 컬럼 추가 (NOT NULL DEFAULT 0)
ALTER TABLE release_version
    ADD COLUMN build_iteration INT NOT NULL DEFAULT 0
        COMMENT '빌드 회차 (0=일반/핫픽스, 1+=빌드 같은 날 N번째)' AFTER build_version;

-- 2. 기존 빌드 행은 회차=1 로 보정
UPDATE release_version SET build_iteration = 1 WHERE build_version > 0;

-- 3. UNIQUE KEY 갱신
ALTER TABLE release_version
    DROP INDEX uk_project_type_customer_version;

ALTER TABLE release_version
    ADD UNIQUE KEY uk_project_type_customer_version
        (project_id, release_type, customer_id, version, hotfix_version, build_version, build_iteration);

-- 4. 회차 조회용 인덱스 (max(iteration) 조회 시 활용)
ALTER TABLE release_version
    ADD INDEX idx_build_version_iteration (build_version, build_iteration);
