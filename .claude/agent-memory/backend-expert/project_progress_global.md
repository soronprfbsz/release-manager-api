---
name: Progress 공용화 패턴
description: X-Progress-Id 기반 서버 진행도 추적이 global 패키지로 이동. 패치/버전/빌드 세 도메인에서 공용 사용.
type: project
---

2026-05-08 에 progress 추적 공용화 완료.

**Why:** 버전/빌드 업로드에도 패치 생성과 동일한 progress UI 적용 필요. 단일 진실 소스 원칙.

**How to apply:** 새로운 장시간 수행 API 에 progress 추적을 추가할 때 아래 패턴을 따른다.

## 파일 위치

- `global/progress/ServerProgressService.java` — 공용 서비스 (Bean)
- `global/progress/dto/ServerProgressDto.java` — `ProgressResponse` record
- `global/progress/controller/ProgressController.java` — `GET /api/progress/{progressId}`
- `global/progress/controller/ProgressControllerDocs.java` — Swagger 인터페이스

## 컨트롤러 패턴

```java
@RequestHeader(value = "X-Progress-Id", required = false) String progressId
...
progressService.start(progressId);
try {
    // 서비스 호출 시 progressService 전달
    service.doWork(..., progressService);
    return ResponseEntity.ok(...);
} catch (Exception e) {
    progressService.fail(e.getMessage());
    throw e;
} finally {
    progressService.end();
}
```

## 서비스 패턴

서비스 메서드 시그니처 마지막 인자로 `ServerProgressService progress` 추가.
메서드 내부에서 `progress.update(step, totalSteps, message)` 호출.

## 적용된 엔드포인트

- `POST /api/patches/standard/generate` — 8단계 (PatchGenerationService)
- `POST /api/patches/custom/generate` — 8단계 (PatchGenerationService)
- `POST /api/releases/versions/standard` — 5단계 (ReleaseVersionUploadService)
- `POST /api/releases/versions/custom` — 5단계 (ReleaseVersionUploadService)
- `POST /api/releases/versions/{id}/builds` — 4단계 (BuildFileService)

## 삭제된 항목

- `domain/patch/service/PatchProgressService.java` (삭제)
- `domain/patch/dto/PatchDto.PatchProgress` record (삭제)
- `GET /api/patches/progress/{id}` 엔드포인트 (삭제 → `/api/progress/{id}` 로 이전)
