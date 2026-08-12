-- =========================================================
-- V24: 사용자 메시지(쪽지 / 시스템 알림) 도입
-- =========================================================
-- 발신 1건(message) + 수신자별 1건(message_recipient) 구조.
-- 시스템 발신 알림(패치 독촉 등)도 message_type 으로만 구분되는 일반
-- 메시지로 저장한다 (ADR-0003).
--
-- 주의 1: DB 기본 collation 은 utf8mb4_general_ci 지만 기존 테이블(account 등)
--         은 모두 utf8mb4_unicode_ci 다. 신규 테이블에 collation 을 명시하지
--         않으면 account 참조 FK 가 errno 150 으로 실패한다.
-- 주의 2: 계정은 hard delete 되므로 account 참조는 NULL 허용 + ON DELETE SET
--         NULL 로 두고 이름/이메일을 스냅샷으로 남긴다. patch_file.created_by /
--         created_by_email 과 같은 방식 — 계정을 지워도 주고받은 기록은 남는다.
-- =========================================================

-- ---------------------------------------------------------
-- 메시지 (발신 단위)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS message (
    message_id        BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '메시지 ID',

    sender_account_id BIGINT       COMMENT '발신자 계정 ID (계정 삭제 시 NULL)',
    sender_email      VARCHAR(100) NOT NULL COMMENT '발신자 이메일 (스냅샷)',
    sender_name       VARCHAR(50)  NOT NULL COMMENT '발신자 이름 (스냅샷)',

    message_type      VARCHAR(30)  NOT NULL DEFAULT 'USER' COMMENT '메시지 유형 (USER, PATCH_REMINDER)',
    title             VARCHAR(200) NOT NULL COMMENT '제목',
    content           TEXT         NOT NULL COMMENT '본문 (평문)',

    -- 참조 대상 스냅샷 — 원본이 삭제된 뒤에도 딥링크가 동작해야 하므로 값으로 보관
    ref_type          VARCHAR(30)  COMMENT '참조 대상 유형 (PATCH)',
    ref_id            BIGINT       COMMENT '참조 대상 ID (patch_id)',
    ref_project_id    VARCHAR(50)  COMMENT '참조 대상 프로젝트 ID (딥링크용)',
    ref_release_type  VARCHAR(20)  COMMENT '참조 대상 릴리즈 타입 (딥링크 탭 선택용)',

    -- 시스템 발송 멱등 키. 사용자 쪽지는 NULL (MariaDB 는 NULL 중복 허용)
    dedup_key         VARCHAR(100) COMMENT '중복 발송 방지 키 (PATCH_REMINDER:{patchId}:{yyyyMMdd})',

    sender_deleted_at DATETIME     COMMENT '발신함 숨김 일시',

    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    UNIQUE INDEX uk_msg_dedup_key (dedup_key),
    INDEX idx_msg_sender (sender_account_id, created_at DESC),
    INDEX idx_msg_ref (ref_type, ref_id),

    CONSTRAINT fk_message_sender FOREIGN KEY (sender_account_id)
        REFERENCES account(account_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='메시지 (발신 단위)';

-- ---------------------------------------------------------
-- 메시지 수신자 (수신 단위)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_recipient (
    recipient_id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '수신 ID',

    message_id           BIGINT NOT NULL COMMENT '메시지 ID',
    recipient_account_id BIGINT       COMMENT '수신자 계정 ID (계정 삭제 시 NULL)',
    recipient_email      VARCHAR(100) NOT NULL COMMENT '수신자 이메일 (스냅샷)',
    recipient_name       VARCHAR(50)  NOT NULL COMMENT '수신자 이름 (스냅샷)',

    read_at              DATETIME COMMENT '읽은 일시 (NULL = 안읽음)',
    deleted_at           DATETIME COMMENT '수신함 숨김 일시',

    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    -- 같은 메시지에 같은 수신자가 중복 등록되지 않도록. 계정 삭제로 NULL 이 되면
    -- MariaDB 의 NULL 중복 허용 규칙에 따라 제약에서 자연히 빠진다.
    UNIQUE INDEX uk_mr_message_account (message_id, recipient_account_id),
    -- 탑바 배지 집계 전용 인덱스 (recipient + 안읽음 + 미숨김)
    INDEX idx_mr_unread (recipient_account_id, read_at, deleted_at),

    CONSTRAINT fk_msg_recipient_message FOREIGN KEY (message_id)
        REFERENCES message(message_id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_recipient_account FOREIGN KEY (recipient_account_id)
        REFERENCES account(account_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='메시지 수신자';

-- =========================================================
-- 메뉴 등록: 업무 지원(support) > 공유(sharing) > 메시지
-- =========================================================
-- 수동 선반영 환경 대비로 시드는 멱등 처리한다.

INSERT INTO menu (menu_id, menu_name, menu_url, icon, is_icon_visible, description, is_description_visible, is_line_break, menu_order) VALUES
('sharing_messages', '메시지', 'support/sharing/messages', 'mail', TRUE, '사용자간 메시지를 주고받습니다.', TRUE, FALSE, 3)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), menu_url = VALUES(menu_url), icon = VALUES(icon);

INSERT INTO menu_hierarchy (ancestor, descendant, depth) VALUES
('sharing_messages', 'sharing_messages', 0),
('sharing', 'sharing_messages', 1),
('support', 'sharing_messages', 2)
ON DUPLICATE KEY UPDATE depth = VALUES(depth);

-- 개인용 기능이므로 전 role 에 개방
INSERT INTO menu_role (menu_id, role) VALUES
('sharing_messages', 'ADMIN'),
('sharing_messages', 'OPERATOR'),
('sharing_messages', 'DEVELOPER'),
('sharing_messages', 'USER')
ON DUPLICATE KEY UPDATE role = VALUES(role);
