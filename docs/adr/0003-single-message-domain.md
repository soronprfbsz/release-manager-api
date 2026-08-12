# 시스템 알림과 사용자 쪽지를 단일 message 도메인으로 다룬다

패치 자동삭제 독촉(시스템 발신)과 사용자간 쪽지는 발신 주체만 다를 뿐, 수신함에 쌓이고 안읽음 개수가 탑바 배지로 집계된다는 점이 같다. 독촉의 발신자도 실재 계정(`admin@tscientific.co.kr`)이라 "시스템 전용" 저장소를 따로 둘 근거가 없다.

**결정**: `message`(발신 1건) + `message_recipient`(수신자별 1건) 두 테이블로 통합하고, 시스템 독촉은 `message_type='PATCH_REMINDER'` 인 일반 메시지로 저장한다. 배지는 `message_recipient` 한 곳만 집계한다.

## Considered Options
- **message / notification 분리**: 알림 종류가 크게 늘고 UX 를 달리 가져갈 계획이라면 유리하다. 지금은 조회·읽음처리·배지집계 로직이 2벌이 되고 배지가 두 쿼리의 합이 된다.
- **수신자별 row 복제(단일 테이블)**: 조인이 없어 단순하지만, 복수 수신자 발송 시 본문이 N벌 복제되고 발신함에서 같은 메시지가 N행으로 늘어진다.
- **message + message_recipient**(채택): 본문 1벌, 읽음 상태는 수신자별. 발신함에서 "3명 중 2명 읽음" 집계가 자연스럽다.

## Consequences
- 배지 = `SELECT count(*) FROM message_recipient WHERE recipient_account_id=? AND read_at IS NULL AND deleted_at IS NULL` 단일 쿼리.
- 삭제는 양쪽 모두 숨김처리 — 수신자는 `message_recipient.deleted_at`, 발신자는 `message.sender_deleted_at`. 수신자가 지워도 발신함에는 남는다.
- 시스템 독촉에 참조 대상을 실어야 하므로 `ref_type` / `ref_id` / `ref_project_id` / `ref_release_type` 를 message 에 둔다. 딥링크는 이 스냅샷만으로 동작해야 한다 — 패치가 삭제된 뒤에도 메시지는 남기 때문.
- 답장·첨부·리치텍스트는 넣지 않는다. 필요해지면 `parent_message_id` 추가로 확장 가능한 형태는 유지한다.
