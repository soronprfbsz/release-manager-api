---
name: 백엔드 빌드 환경 제약
description: WSL 환경에 Java 17 미설치, gradlew 직접 빌드 불가. 소스 정적 분석으로 대체.
type: project
---

WSL2 환경에 Java 21 (openjdk-21)만 설치됨.
build.gradle toolchain = JavaLanguageVersion.of(17) 요구로 gradlew clean build 실패.

**Why:** Java 17 미설치. gradle.properties 경로 지정으로도 우회 불가 — toolchain 매처가 버전 17 일치를 강제함.

**How to apply:** 빌드 검증이 필요할 때는 사용자가 직접 Windows/IntelliJ 환경에서 수행 요청. quality-expert는 소스 정적 분석 + 프론트 type-check로 대체.
