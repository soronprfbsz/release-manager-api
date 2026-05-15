# CLAUDE.md — release-manager-api

> Spring Boot 3.5.6 / Java 17 / MariaDB. **WSL2 (Ubuntu) 셸에서 실행한다.**

## 첫 셋업 (한 번)

```bash
sudo apt install -y openjdk-17-jdk     # Java 17 toolchain (gradle 자동 감지)
sudo apt install -y docker.io docker-compose-plugin   # 인프라용
```

## 명령

```bash
cd docker && docker compose up -d   # 인프라 (MariaDB / Redis) — 한 번만
./gradlew bootRun                   # 백엔드 실행 (포트 8081)
./gradlew test                      # 전체 테스트 (H2 in-memory)
./gradlew clean build -x test       # 빌드 (테스트 제외)
```

`.env` 위치: `release-manager-api/.env` (compose 가 `../.env` 참조).
