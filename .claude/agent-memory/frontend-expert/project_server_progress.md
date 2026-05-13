---
name: 공용 서버 진행도 모듈 구현
description: 패치/버전/빌드 생성 공통 progress polling 및 이탈 차단 모듈 공용화 완료
type: project
---

패치 도메인 전용이었던 progress polling/UI/navigation block 을 shared 레이어로 공용화 완료.

**Why:** 버전/빌드 생성 엔드포인트에도 X-Progress-Id 헤더가 추가되어 동일 패턴 적용 필요.

**How to apply:**
- 서버 진행도 필요 시 `useServerProgress` (`@/shared/api`) 사용
- UUID 생성은 `generateProgressId` (`@/shared/lib/progress/generateProgressId`)
- 진행 뷰는 `ServerProgressView` (`@/shared/ui/server-progress-view`), steps props 는 `readonly string[]`
- 페이지 이탈 차단은 `useNavigationBlock` (`@/shared/lib/hooks/use-navigation-block`)
- 공용 progress API 경로: `GET /api/progress/{progressId}` (패치 전용 `/patches/progress/` 는 삭제됨)
- `apiClient.upload` 의 headers 가 외부 전달 headers 를 병합하도록 수정됨 (Content-Type 은 항상 multipart/form-data 로 마지막에 고정)
