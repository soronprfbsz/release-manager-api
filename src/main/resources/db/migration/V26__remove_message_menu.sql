-- =========================================================
-- 메시지 메뉴 제거 (V24 에서 등록한 sharing_messages)
-- =========================================================
-- 메시지 진입점을 사이드바의 '업무 지원 > 공유 > 메시지' 에서 좌측 하단
-- 프로필 드롭다운으로 옮긴다. 개인용 기능이라 업무 메뉴 트리에 있을 이유가
-- 없고, 탑바 알림 벨 / 티커와 진입 경로가 중복되어 있었다.
--
-- 라우트(/support/sharing/messages)와 화면은 그대로 유지된다. 접근 권한은
-- 프론트 ROUTE_PERMISSIONS 가 별도로 관리하므로 메뉴 삭제와 무관하다.
-- 다만 페이지 제목/설명/브레드크럼은 이 메뉴 row 에서 나오던 값이라,
-- 화면 쪽에서 PageLayout 에 직접 넘기도록 함께 변경했다.
--
-- menu_hierarchy / menu_role 은 menu(menu_id) 에 ON DELETE CASCADE 로 묶여
-- 있어 menu 삭제만으로 함께 정리되지만, 의도를 남기기 위해 명시적으로 지운다.
-- DELETE 는 본래 멱등이므로 재실행해도 안전하다.

DELETE FROM menu_role WHERE menu_id = 'sharing_messages';
DELETE FROM menu_hierarchy WHERE ancestor = 'sharing_messages' OR descendant = 'sharing_messages';
DELETE FROM menu WHERE menu_id = 'sharing_messages';
