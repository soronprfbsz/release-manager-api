# CLAUDE.md — release-manager-api

> Spring Boot 3.5.6 / Java 17 / MariaDB. **WSL2 (Ubuntu) 셸에서 실행한다.**

## 첫 셋업 (한 번)

```bash
sudo apt install -y openjdk-17-jdk-headless docker.io docker-compose-plugin

# JAVA_HOME 영구 설정 — gradle toolchain 이 Java 17 을 확실히 찾도록
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.zshrc
source ~/.zshrc
```

(bash 사용자는 `~/.bashrc` 로 교체. Java 21 도 함께 있으면 Gradle 의
자동 감지가 17 을 못 찾는 경우가 있어 `JAVA_HOME` 명시가 가장 확실.)

## 명령

```bash
cd docker && docker compose up -d   # 인프라 (MariaDB / Redis) — 한 번만
./gradlew bootRun                   # 백엔드 실행 (포트 8081)
./gradlew test                      # 전체 테스트 (H2 in-memory)
./gradlew clean build -x test       # 빌드 (테스트 제외)
```

`.env` 위치: `release-manager-api/.env` (compose 가 `../.env` 참조).
