-- DEVELOPER 역할에 파일 동기화 메뉴 접근 권한 추가
-- 멱등 처리: 이미 권한 행이 있는 환경(수동 선반영된 dev 등)에서도 안전하게 재실행 가능.
INSERT INTO menu_role (menu_id, role)
VALUES ('operation_filesync', 'DEVELOPER')
ON DUPLICATE KEY UPDATE role = VALUES(role);
