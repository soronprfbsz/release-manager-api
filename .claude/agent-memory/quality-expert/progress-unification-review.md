---
name: 진행도 공용화 검증 이력 (2026-05-08)
description: ServerProgress 공용화 작업 후 quality 검증 결과. complete() 비대칭 이슈 핵심.
type: project
---

검증일: 2026-05-08
작업: global/progress 공용화 (ServerProgressService, ProgressController, ServerProgressView 등)

핵심 발견:
- PatchGenerationService: complete(TOTAL_STEPS) 정상 호출
- ReleaseVersionUploadService: complete() 미호출 — 마지막 update(5,5,...) 후 바로 return
- BuildFileService: complete() 미호출 — 마지막 update(TOTAL_STEPS,...) 후 바로 return
- 프론트 ServerProgressView completed 상태는 progress.completed===true 에만 의존

**Why:** completed=true 없이 end()만 호출되면 10초 후 맵에서 삭제됨. 프론트가 마지막 polling 타이밍에 null 응답 받을 수 있음. completed 체크리스트 전환 안 됨.

**How to apply:** ReleaseVersionUploadService, BuildFileService 수정 시 각 메서드 마지막 return 직전에 progress.complete(TOTAL_STEPS) 추가 필요.
