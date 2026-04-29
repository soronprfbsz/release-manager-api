-- =========================================================
-- V6: 패치 메타 영구 저장 — 포함된 빌드 / 범위 안 핫픽스
-- =========================================================
-- 배경:
--   기존에는 PatchGenerationService 의 응답으로만 includedBuilds /
--   hotfixesInRange / isBuildOnly 가 일회성으로 내려가, 패치 row 가
--   영구화된 후에는 어떤 빌드 / 핫픽스가 포함됐는지 추적이 불가능했음.
--   목록 배지·상세 시트·후행 추적·감사 요구에 따라 두 메타를 풀 정규화
--   하여 patch_file 옆에 영구 저장한다.
--
-- 변경 내용:
--   1. patch_file 에 캐시 컬럼 2개 추가 (목록 배지용 빠른 조회):
--      - is_build_only      : Build-only 패치 여부 (from == to)
--      - is_build_included  : picker 로 빌드를 끼웠는지 여부
--   2. patch_included_build 테이블 신규 — WEB / ENGINE 빌드 메타 행
--   3. patch_hotfix_in_range 테이블 신규 — 패치 범위 안 핫픽스 메타 행
--
-- 삭제 정책 (spec §3 / Q2):
--   - patch_file 행 삭제 → 메타 행 ON DELETE CASCADE 로 동반 삭제
--   - release_version (빌드 / 핫픽스 원본) 행 삭제 → FK ON DELETE SET NULL
--     로 link 만 끊고 full_version snapshot 은 메타 행에 보존
--
-- 주의:
--   - MariaDB 안전을 위해 ALTER TABLE 을 각 컬럼별로 분리하여 실행합니다.
--   - 기존 patch_file 행은 메타 백필을 하지 않으므로 두 boolean 은
--     DEFAULT FALSE 로 들어갑니다. 화면에서는 배지 미노출이 정상.
-- =========================================================

-- ---------------------------------------------------------
-- 1-1. patch_file 에 is_build_only 컬럼 추가
--      from == to 인 build-only 패치 여부 캐시
-- ---------------------------------------------------------
ALTER TABLE patch_file
    ADD COLUMN is_build_only BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Build-only 패치 여부 (from == to)';

-- ---------------------------------------------------------
-- 1-2. patch_file 에 is_build_included 컬럼 추가
--      picker 로 WEB / ENGINE 빌드를 끼웠는지 여부 캐시
-- ---------------------------------------------------------
ALTER TABLE patch_file
    ADD COLUMN is_build_included BOOLEAN NOT NULL DEFAULT FALSE COMMENT '빌드 picker 사용 여부 (WEB/ENGINE 메타가 있으면 true)';

-- ---------------------------------------------------------
-- 2. patch_included_build 테이블 신규
--    한 패치에 대해 WEB 0~1행 + ENGINE 0~N행이 들어간다.
--    kind 는 'WEB' 또는 'ENGINE' 의 문자열 enum.
-- ---------------------------------------------------------
CREATE TABLE patch_included_build (
    patch_included_build_id BIGINT       AUTO_INCREMENT PRIMARY KEY              COMMENT 'PK',
    patch_id                BIGINT       NOT NULL                                COMMENT '소속 패치 (FK → patch_file)',
    kind                    VARCHAR(10)  NOT NULL                                COMMENT 'WEB | ENGINE',
    engine_name             VARCHAR(50)  NULL                                    COMMENT 'kind=ENGINE 일 때만 채움 (예: NC_SMS, NC_FAULT_MS)',
    build_version_id        BIGINT       NULL                                    COMMENT '원본 빌드 버전 ID (release_version FK, 삭제되면 NULL)',
    full_version            VARCHAR(50)  NOT NULL                                COMMENT '빌드 fullVersion snapshot (예: 1.1.0.260427) — release_version 삭제 후에도 보존',
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pib_patch FOREIGN KEY (patch_id)
        REFERENCES patch_file(patch_id) ON DELETE CASCADE,
    CONSTRAINT fk_pib_build FOREIGN KEY (build_version_id)
        REFERENCES release_version(release_version_id) ON DELETE SET NULL,
    INDEX idx_pib_patch_id (patch_id),
    INDEX idx_pib_engine_name (engine_name)
) COMMENT='패치에 포함된 빌드 메타 (WEB/ENGINE)';

-- ---------------------------------------------------------
-- 3. patch_hotfix_in_range 테이블 신규
--    패치 from~to 범위 안에 존재했던 핫픽스 메타 (별도 적용 안내용).
-- ---------------------------------------------------------
CREATE TABLE patch_hotfix_in_range (
    patch_hotfix_in_range_id BIGINT       AUTO_INCREMENT PRIMARY KEY             COMMENT 'PK',
    patch_id                 BIGINT       NOT NULL                               COMMENT '소속 패치 (FK → patch_file)',
    hotfix_version_id        BIGINT       NULL                                   COMMENT '원본 핫픽스 버전 ID (release_version FK, 삭제되면 NULL)',
    full_version             VARCHAR(50)  NOT NULL                               COMMENT '핫픽스 fullVersion snapshot (예: 1.1.0.1)',
    hotfix_version           INT          NOT NULL                               COMMENT '핫픽스 버전 번호 (정렬용)',
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_phir_patch FOREIGN KEY (patch_id)
        REFERENCES patch_file(patch_id) ON DELETE CASCADE,
    CONSTRAINT fk_phir_hotfix FOREIGN KEY (hotfix_version_id)
        REFERENCES release_version(release_version_id) ON DELETE SET NULL,
    INDEX idx_phir_patch_id (patch_id)
) COMMENT='패치 범위 안 핫픽스 메타 (별도 적용 안내용)';
