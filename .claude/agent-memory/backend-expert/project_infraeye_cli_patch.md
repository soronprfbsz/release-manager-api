---
name: InfraEye CLI 패치 조립 패턴
description: fromVersion=1.0.0 패치에만 InfraEye CLI 교체 파일을 루트에 포함시키는 조건부 로직 패턴
type: project
---

레거시 `/usr/bin/InfraEye` (bash CLI)를 Release Manager 1.0.0 패치로 교체하는 기능.

**핵심 결정:**
- `InfraEyeCliScriptGenerator`: `AbstractScriptGenerator` 상속, 파일명 `InfraEye` (확장자 없음), 치환 토큰 없이 템플릿 그대로 복사
- `StreamingZipUtil.resolveUnixMode()`: 서버 파일의 실행 비트 우선 확인 → `.sh` 확장자 보완 폴백 순서로 개선 (확장자 없는 실행파일 지원)
- `PatchGenerationService`: 상수 `INFRAEYE_CLI_BUNDLED_FROM_VERSION = "1.0.0"` 정의, `generatePatchScripts()`에서 fromVersion 비교 후 조건부 호출

**Why:** `InfraEye` 파일은 확장자가 없어 기존 `.sh` 확장자 기반 권한 판단으로는 0755가 부여되지 않음. 서버 파일의 실행 비트를 먼저 확인하도록 변경.

**How to apply:** 향후 특정 버전에만 조건부로 파일을 포함시키는 패턴은 `INFRAEYE_CLI_BUNDLED_FROM_VERSION` 상수 방식을 참고.
