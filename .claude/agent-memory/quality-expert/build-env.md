---
name: 백엔드 빌드 환경 제약
description: WSL에 Java 17과 Java 21 모두 설치됨. JAVA_HOME 명시 시 gradlew 빌드/테스트 정상 동작.
type: project
---

WSL2 환경에 openjdk-17-jdk-headless가 설치되어 있음 (`/usr/lib/jvm/java-17-openjdk-amd64`).
단, `JAVA_HOME`을 명시하지 않으면 Java 21이 기본으로 잡혀 toolchain=17 요구에 실패할 수 있음.

**Why:** 과거에는 Java 17이 미설치였으나 현재는 설치됨. `JAVA_HOME` 명시 필수.

**How to apply:** 빌드/테스트 실행 시 반드시 아래 방식 사용.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew <task>
```

테스트 필터 예시:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew test --tests '*BuildsInRangeServiceTest' --rerun-tasks
```

2026-05-27 확인: BuildsInRangeServiceTest 전체 BUILD SUCCESSFUL (2분 7초).
