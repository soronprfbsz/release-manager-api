-- =========================================================
-- V22: orphan 릴리즈 디렉토리 정리 스케줄 작업 추가
-- =========================================================
-- NAS(SMB)에서는 다른 클라이언트의 열린 핸들 때문에 버전/빌드 삭제가
-- best-effort 로 동작하고 잔존 디렉토리(orphan)가 남는다 (#SMB핸들).
-- DB(release_version)와 대조해 orphan 버전/빌드/핫픽스 디렉토리를
-- 매일 정리하는 maintenance 스케줄 잡을 등록한다.
--   실제 정리 로직: MaintenanceController#cleanupOrphanDirectories
--                 → ReleaseDirectoryCleanupService#cleanupOrphanDirectories
--
-- api_url 의 {port} 는 스케줄러 실행 시 server.port 값으로 치환됨.
-- V15 의 patch-cleanup INSERT 구문을 본떠 작성. is_enabled 미지정 →
-- schedule_job 의 DEFAULT TRUE 로 즉시 활성화된다.
-- NAS 트리 순회가 느릴 수 있어 timeout 은 600초.
-- =========================================================

-- orphan 릴리즈 디렉토리 정리 (매일 새벽 5시 20분 — patch-cleanup(05:00) 이후)
INSERT INTO schedule_job (job_name, job_group, description, api_url, http_method, cron_expression, timeout_seconds) VALUES
('orphan-directory-cleanup', 'MAINTENANCE', 'DB에 없는 orphan 버전/빌드/핫픽스 디렉토리 정리 (24시간 이상 변경 없는 것만)',
 'http://localhost:{port}/api/maintenance/orphan-directories?quietHours=24', 'DELETE',
 '0 20 5 * * *', 600);
