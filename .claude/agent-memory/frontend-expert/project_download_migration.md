---
name: 다운로드 브라우저 네이티브 통일 완료
description: 모든 다운로드를 axios blob → triggerBrowserDownload(anchor) 방식으로 일괄 변경 (2026-05-08)
type: project
---

백엔드 SecurityConfig 에서 모든 download endpoint 에 permitAll() 적용 완료 상태.

프론트 모든 다운로드를 `shared/lib/download/triggerBrowserDownload.ts` 의 anchor click 방식으로 통일.

**Why:** XHR blob 방식은 큰 파일 메모리 부담 / 페이지 이동 시 중단 / in-app 토스트 분산 등 단점. 브라우저 다운로드 매니저가 진행률 표시를 담당.

**How to apply:** 새 download endpoint 추가 시 entity api 에서 `triggerBrowserDownload(url)` 만 호출. onProgress 콜백·signal 인자 불필요. 다운로드 토스트 (startTransfer/completeTransfer) 도 불필요.

## 제거된 파일
- `shared/lib/utils/download-helper.ts` — 완전 삭제
- `shared/api/client.ts` 의 `download()` 메서드 — 제거

## 변경 Entity API (7개)
- releaseApi.downloadVersion
- patchApi.download
- publishingApi.download
- projectApi.downloadOnboardingFiles / downloadInstallFiles
- fileApi.downloadCategoryZip
- mariadbApi.downloadBackupFile / downloadLogFile

## 유지된 파일 (업로드 전용)
- `use-file-transfer-progress.ts` — upload 흐름에서 계속 사용 (HotfixCreateForm, FileTransferForm, FileResourceTab 업로드, ProjectListPage 업로드)
- `fileDownloadApi` (shared/api/fileDownloadApi.ts) — 개별 파일 다운로드용, 이미 anchor 방식이었으므로 변경 불필요
