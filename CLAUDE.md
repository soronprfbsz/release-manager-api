# CLAUDE.md — release-manager-api

> Spring Boot 3.5.6 / Java 17 / MariaDB

## 명령

```bash
./gradlew bootRun                # 로컬 실행 (포트 8081)
./gradlew test                   # 전체 테스트 (H2 in-memory)
./gradlew clean build -x test    # 빌드 (테스트 제외)
```

## 인프라 (MariaDB / Redis / app)

```bash
cd docker && docker compose up -d
```

`.env` 위치: `release-manager-api/.env` (compose 가 `../.env` 참조).
