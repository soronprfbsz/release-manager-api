-- ============================================================
-- V9: 사이트별 컴포넌트 현재 버전 추적 테이블 신설
--
-- 목적: 패치 완료(completePatch) 시점마다 사이트의 BASE/WEB/ENGINE
--       컴포넌트별 현재 버전을 upsert 하여 InfraEye `info version` 과
--       동일한 버전 상태를 추적한다.
--
-- 초기 데이터 없음 — V8 에서 patch_file 일괄 삭제, patch_history 에
-- 컴포넌트 정보 없음. 새 워크플로 패치 완료부터 누적 시작.
-- ============================================================

CREATE TABLE customer_site_version (
    site_version_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사이트 버전 추적 ID',
    customer_id     BIGINT       NOT NULL                COMMENT '고객사 ID',
    project_id      VARCHAR(50)  NOT NULL                COMMENT '프로젝트 ID',
    component       VARCHAR(20)  NOT NULL                COMMENT '컴포넌트 구분 (BASE | WEB | ENGINE)',
    current_version VARCHAR(100) NOT NULL                COMMENT '현재 버전 (BASE: 1.1.0 / WEB·ENGINE: fullVersion 1.1.0.260511-1)',
    updated_at      DATETIME     NOT NULL                COMMENT '최종 갱신 일시',
    updated_by      VARCHAR(255) NULL                    COMMENT '최종 갱신자 이메일',
    PRIMARY KEY (site_version_id),
    UNIQUE KEY uk_csv_site_component (customer_id, project_id, component),
    INDEX idx_csv_customer_project (customer_id, project_id),
    CONSTRAINT fk_csv_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    CONSTRAINT fk_csv_project  FOREIGN KEY (project_id)  REFERENCES project  (project_id)
);
