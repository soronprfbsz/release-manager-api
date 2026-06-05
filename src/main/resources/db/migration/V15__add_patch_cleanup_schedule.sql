-- =========================================================
-- V15: 패치 파일 자동 정리 스케줄 작업 추가
-- =========================================================
-- 생성 후 retention 기간이 지난 패치 파일(디렉토리 + patch_file row)을
-- 주기적으로 정리하는 maintenance 스케줄 잡을 등록한다.
--   실제 정리 로직: MaintenanceController#cleanupPatches → PatchService#deleteOldPatches
--
-- api_url 의 {port} 는 스케줄러 실행 시 server.port 값으로 치환됨.
-- V3 의 api-log-cleanup INSERT 구문을 본떠 작성. is_enabled 미지정 →
-- schedule_job 의 DEFAULT TRUE 로 즉시 활성화된다.
-- =========================================================

-- 오래된 패치 파일 정리 (매일 새벽 5시)
INSERT INTO schedule_job (job_name, job_group, description, api_url, http_method, cron_expression, timeout_seconds) VALUES
('patch-cleanup', 'MAINTENANCE', '30일 이상 지난 패치 파일 정리',
 'http://localhost:{port}/api/maintenance/patches', 'DELETE',
 '0 0 5 * * *', 300);
