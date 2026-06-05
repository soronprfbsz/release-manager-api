-- ============================================================
-- V14: customer_site_version 에 engine_name 컬럼 추가
--
-- 목적: ENGINE 컴포넌트를 엔진별 행으로 추적해야 함.
--       기존엔 (customer, project, ENGINE) 단일 row 에 모든 엔진 빌드의
--       fullVersion 사전식 최댓값 1개만 들어가 있어 엔진별 빌드 버전 차이를
--       표시할 수 없었다.
--
-- 변경:
--   1) engine_name VARCHAR(50) NULL 컬럼 추가 (BASE/WEB 은 NULL, ENGINE 행은
--      각 엔진명을 적재). NULL 허용 = MariaDB unique key 에서 NULL 은 distinct
--      취급되어 기존 BASE/WEB/ENGINE 단일 행과 호환된다.
--   2) UNIQUE KEY 를 (customer_id, project_id, component, engine_name) 으로 확장.
--      BASE/WEB 는 engine_name=NULL 1행만 가능, ENGINE 은 engine_name 별 N행 가능.
--   3) 기존 (ENGINE, NULL) 행은 그대로 보존 — 다음 패치 완료 시점에 엔진별
--      행이 추가되며 점진적 누적. 운영자가 별도 백필 필요하면 후속 1회성 SQL.
-- ============================================================

ALTER TABLE customer_site_version
    ADD COLUMN engine_name VARCHAR(50) NULL COMMENT '엔진명 (component=ENGINE 일 때 채움, BASE/WEB 은 NULL)' AFTER component;

ALTER TABLE customer_site_version
    DROP INDEX uk_csv_site_component,
    ADD UNIQUE KEY uk_csv_site_component_engine (customer_id, project_id, component, engine_name);

ALTER TABLE customer_site_version
    ADD INDEX idx_csv_engine_name (engine_name);
