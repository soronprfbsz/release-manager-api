-- ============================================================
-- V17: patch_history_build — 패치 이력별 WEB/ENGINE 빌드 스냅샷
--
-- 목적: patch_history 삭제 시 남은 이력만으로 WEB/ENGINE 버전을 재계산하려면
--       각 이력의 빌드 fullVersion 스냅샷이 필요하다. patch_included_build 는
--       patch_file 삭제 시 ON DELETE CASCADE 로 사라지므로, 패치 완료 시점에
--       이력 쪽으로 복사 보존한다. (배포 이후 완료되는 패치부터 누적)
-- ============================================================

CREATE TABLE IF NOT EXISTS patch_history_build (
    patch_history_build_id BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK',
    history_id             BIGINT      NOT NULL                COMMENT '소속 패치 이력 (FK → patch_history)',
    kind                   VARCHAR(10) NOT NULL                COMMENT 'WEB | ENGINE',
    engine_name            VARCHAR(50) NULL                    COMMENT 'kind=ENGINE 일 때만 채움 (BASE/WEB 은 NULL)',
    full_version           VARCHAR(50) NOT NULL                COMMENT '빌드 fullVersion snapshot (예: 1.1.0.260511-1)',
    created_at             DATETIME    NOT NULL                COMMENT '스냅샷 생성 일시 (= 패치 완료 일시)',
    PRIMARY KEY (patch_history_build_id),
    INDEX idx_phb_history_id (history_id),
    CONSTRAINT fk_phb_history FOREIGN KEY (history_id)
        REFERENCES patch_history (history_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
