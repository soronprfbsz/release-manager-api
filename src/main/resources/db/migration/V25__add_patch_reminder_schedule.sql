-- =========================================================
-- V25: 패치 처리 독촉 스케줄 작업 추가
-- =========================================================
-- 자동 삭제 예정일까지 남은 일수가 마일스톤(D-15/10/5/4/3/2/1)에 해당하는
-- 미처리 패치의 생성자에게 독촉 메시지를 보낸다 (ADR-0005).
--
-- 실행 시각은 patch-cleanup(05:00) 직후인 05:10 — 그날 삭제될 패치는 이미
-- 정리된 뒤라 사라진 패치에 독촉이 나가는 경합이 없다.
-- cron 은 schedule_job.timezone(기본 Asia/Seoul) 기준으로 해석된다.
--
-- api_url 의 {port} 는 스케줄러 실행 시 server.port 값으로 치환됨.
-- 정리가 아니라 발송이므로 http_method 는 POST.
-- =========================================================

INSERT INTO schedule_job (job_name, job_group, description, api_url, http_method, cron_expression, timeout_seconds) VALUES
('patch-reminder', 'MAINTENANCE', '미처리 패치 자동삭제 독촉 발송 (D-15/10/5/4/3/2/1)',
 'http://localhost:{port}/api/maintenance/patch-reminders', 'POST',
 '0 10 5 * * *', 120)
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    api_url = VALUES(api_url),
    http_method = VALUES(http_method),
    cron_expression = VALUES(cron_expression),
    timeout_seconds = VALUES(timeout_seconds);
