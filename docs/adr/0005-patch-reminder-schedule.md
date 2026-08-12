# 패치 독촉은 삭제예정일 기준 마일스톤 7회로 보내고, 삭제예정일은 cleanup cron 에서 역산한다

`patch-cleanup` 스케줄이 보관기간(기본 30일)이 지난 `patch_file` 을 지운다. 그전에 생성자에게 처리를 독촉하되, 방치 패치 1건당 15일간 매일 보내면 배지가 15까지 치솟아 사람 쪽지가 묻힌다.

**결정**: 삭제예정일 기준 **D-15 / D-10 / D-5 / D-4 / D-3 / D-2 / D-1** 7회만 발송한다. 발송 시각은 cleanup(KST 05:00) 직후인 **KST 05:10**. 삭제예정일은 `created_at` + 보관기간이 아니라 **그 시점 이후 처음 도래하는 cleanup 실행 시각**으로 계산한다.

## Considered Options
- **매일 발송 + 누적**: 요구사항 문구 그대로지만 배지가 방치 패치 수 × 15 까지 늘어난다.
- **매일 발송 + 이전 독촉 삭제**: 배지는 패치당 1로 유지되나 발송 이력이 사라지고 삭제 로직이 붙는다.
- **마일스톤 7회**(채택): 최대 7건, 삭제 로직 불필요. 마감이 다가올수록 간격이 촘촘해져 독촉 강도도 자연스럽다.

## Consequences
- 앱 컨테이너는 `TZ=UTC` 라 `created_at` 은 UTC 지만, 스케줄러 `CronTrigger` 는 `ZoneId.of(job.getTimezone())` = `Asia/Seoul` 로 돈다. 두 기준을 섞으면 삭제예정일이 하루 어긋난다. 계산은 `created_at(UTC) + retentionDays → KST 변환 → 05:00 이전이면 당일 05:00, 이후면 익일 05:00` 로 고정하고, D-N 은 KST 날짜 차이로 판정한다.
- 보관기간은 `patch.cleanup.retention-days` 를 cleanup 과 공유한다. 별도 파라미터를 두면 두 값이 어긋나 문구가 거짓말이 된다.
- 스케줄러 화면에 "지금 실행"(`POST /api/schedules/jobs/{jobId}/execute`)이 있어 같은 날 중복 발송이 가능하다. `message.dedup_key`(`PATCH_REMINDER:{patchId}:{yyyyMMdd}`) UNIQUE 로 DB 레벨에서 막는다. 사용자 쪽지는 NULL 이며 MariaDB 는 NULL 중복을 허용한다.
- 패치가 처리(완료/삭제/자동삭제)되면 같은 `ref_id` 의 미읽음 독촉을 자동 숨김처리한다. 훅 지점은 `completePatch` / `deletePatch` / `batchDeletePatches` / `deleteOldPatches` 4곳이다.
- 발신 계정은 `message.system-sender-email` 설정값으로 조회한다. 계정을 못 찾으면 ERROR 로그 후 발송만 건너뛰고 스케줄 자체는 성공 처리한다.
