-- =========================================================
-- V20: 사이트 관리 메뉴를 루트로 분리
--
-- 변경 전: 운영 관리(operation_management) > 고객사(operation_customers)
-- 변경 후: 사이트 관리(site_management) 가 루트 3번 메뉴 (URL 직결 leaf)
--
-- 루트 순서: 버전 관리(1) / 패치 관리(2) / 사이트 관리(3) / 운영 관리(4) / 업무 지원(5)
-- URL 도 메뉴 계층과 맞춰 operations/customers → sites 로 이동한다.
-- =========================================================

-- ---------------------------------------------------------
-- Step 1: 루트 메뉴 신규 등록
-- ---------------------------------------------------------
INSERT INTO menu (menu_id, menu_name, menu_url, icon, is_icon_visible, description, is_description_visible, is_line_break, menu_order) VALUES
('site_management', '사이트 관리', 'sites', 'building-2', TRUE, '사이트 정보를 관리합니다.', TRUE, FALSE, 3);

-- 자기 자신 관계 (depth=0) — 자식이 없는 leaf 이므로 이 행만 필요
INSERT INTO menu_hierarchy (ancestor, descendant, depth) VALUES
('site_management', 'site_management', 0);

-- 권한 승계 — 구 operation_customers 와 동일 (ADMIN / DEVELOPER / USER / OPERATOR)
INSERT INTO menu_role (menu_id, role) VALUES
('site_management', 'ADMIN'),
('site_management', 'DEVELOPER'),
('site_management', 'USER'),
('site_management', 'OPERATOR');

-- ---------------------------------------------------------
-- Step 2: 구 메뉴 제거
-- menu_hierarchy / menu_role 의 FK 가 ON DELETE CASCADE 라 관련 행도 함께 삭제된다.
-- ---------------------------------------------------------
DELETE FROM menu WHERE menu_id = 'operation_customers';

-- ---------------------------------------------------------
-- Step 3: 루트 메뉴 순서 재배치 (사이트 관리가 3번으로 들어오면서 뒤로 밀림)
-- ---------------------------------------------------------
UPDATE menu SET menu_order = 4 WHERE menu_id = 'operation_management';
UPDATE menu SET menu_order = 5 WHERE menu_id = 'support';

-- ---------------------------------------------------------
-- Step 4: 운영 관리 하위 순서 재번호 (고객사 제거로 생긴 2번 공석 정리)
-- ---------------------------------------------------------
UPDATE menu SET menu_order = 1 WHERE menu_id = 'operation_projects';
UPDATE menu SET menu_order = 2 WHERE menu_id = 'operation_department';
UPDATE menu SET menu_order = 3 WHERE menu_id = 'operation_accounts';
UPDATE menu SET menu_order = 4 WHERE menu_id = 'operation_filesync';
UPDATE menu SET menu_order = 5 WHERE menu_id = 'operation_history';
