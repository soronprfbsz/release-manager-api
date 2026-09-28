# Release Manager 배포 가이드

## 목차

1. [사전 준비사항](#사전-준비사항)
2. [환경 설정](#환경-설정)
3. [배포 방법](#배포-방법)
4. [디렉토리 구조](#디렉토리-구조)
5. [Health Check](#health-check)
6. [트러블슈팅](#트러블슈팅)
7. [백업 및 복구](#백업-및-복구)

---

## 사전 준비사항

### Docker Network 생성

```bash
# network-ts 네트워크가 없으면 생성
docker network create network-ts
```

### 필수 디렉토리 생성

```bash
# 데이터 디렉토리 생성
sudo mkdir -p /{RELEASE_DIR}/release-manager/{mariadb/lib,mariadb/log,redis/data,resources}

# 권한 설정
sudo chown -R 1000:1000 /{RELEASE_DIR}/release-manager
```

---

## 환경 설정

### .env 파일 설정

프로젝트 루트에 `.env` 파일 생성 (`.env.example` 참고):

```bash
# 복사 후 수정
cp .env.example .env
vi .env
```

### 주요 환경 변수

| 변수 | 설명 | 로컬 개발 | Docker 배포 |
|------|------|-----------|-------------|
| `RELEASE_BASE_PATH` | 릴리즈 파일 저장 경로 | `src/main/resources/release-manager` | `/app/resources` |
| `RELEASE_DIR` | Docker 볼륨 마운트 경로 | - | `/data/release-manager/resources` |
| `MARIADB_HOST` | MariaDB 호스트 | `localhost` | `mariadb` |
| `REDIS_HOST` | Redis 호스트 | `localhost` | `redis` |

### GitHub Actions Secrets

CI/CD 파이프라인용 비밀값은 GitHub 저장소 **Settings → Secrets and variables → Actions → Repository secrets** 에 등록한다
(`MARIADB_ROOT_PASSWORD`, `MARIADB_USERNAME`, `MARIADB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`).

- 값은 등록 후 조회 불가 — 변경은 같은 이름으로 덮어쓴다 (`gh secret set <이름> -R tscorp-dev2/release-manager-api`)
- 비밀이 아닌 값(서버 IP·포트·경로)은 `.github/workflows/deploy.yml` 의 `env:` 에서 관리

---

## 배포 방법

### CI/CD 자동 배포 (권장)

`main` 브랜치에 push 하면 `.github/workflows/deploy.yml` 이 배포 서버의 self-hosted runner 에서 자동 실행됩니다
(Actions 탭 → Deploy → Run workflow 로 수동 실행도 가능):

1. **build**: Gradle 빌드
2. **docker-build**: Docker 이미지 빌드
3. **deploy**: 배포 서버(runner 호스트)에서 app 컨테이너 교체 + 헬스체크

### 수동 배포

```bash
# 1. 최신 코드 pull
cd /path/to/release-manager
git pull origin main

# 2. 빌드
./gradlew clean build -x test

# 3. Docker 이미지 빌드 및 배포
cd docker
docker compose build
docker compose down
docker compose up -d

# 4. 로그 확인
docker compose logs -f app
```

### 로컬 개발 환경

```bash
# 데이터베이스만 Docker로 실행
cd docker
docker compose up -d mariadb redis

# 애플리케이션은 IDE에서 실행
./gradlew bootRun
```

---

## 디렉토리 구조

### 호스트 디렉토리 (Docker 배포 시)

```
/data/release-manager/
├── mariadb/
│   ├── lib/                    # MariaDB 데이터 파일
│   └── log/                    # MariaDB 로그 (slow query 등)
├── redis/
│   └── data/                   # Redis AOF 데이터
└── resources/                  # 릴리즈 파일 저장소 (/app/resources로 마운트)
    ├── resources/
    │   ├── publishing/         # 퍼블리싱 파일 (ZIP 업로드)
    │   │   └── {publishingName}/
    │   └── file/               # 리소스 파일
    │       ├── script/         # 스크립트 파일
    │       │   ├── MARIADB/
    │       │   └── CRATEDB/
    │       └── document/       # 문서 파일
    ├── templates/              # 패치 스크립트 템플릿
    │   ├── MARIADB/
    │   │   └── mariadb_patch_template.sh
    │   └── CRATEDB/
    │       └── cratedb_patch_template.sh
    └── versions/               # 릴리즈 버전별 패치 파일
        └── {projectId}/
            ├── standard/       # 표준 릴리즈
            │   └── {major}.{minor}.x/
            │       └── {version}/
            │           ├── mariadb/
            │           ├── cratedb/
            │           └── hotfix/
            └── custom/         # 커스텀 릴리즈
                └── {customerCode}/
```

### 로컬 개발 디렉토리

```
src/main/resources/release-manager/
├── resources/
│   ├── publishing/
│   └── file/
├── templates/
└── versions/
```

---

## Health Check

### 컨테이너 상태 확인

```bash
# 모든 서비스 상태
docker compose ps

# 개별 서비스 로그
docker compose logs -f app
docker compose logs -f mariadb
docker compose logs -f redis
```

### API Health Check

```bash
# Actuator Health Endpoint
curl http://localhost:18080/actuator/health

# Swagger UI 접근
open http://localhost:18080/swagger
```

### 정상 응답 예시

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## 트러블슈팅

### 컨테이너 시작 실패

```bash
# 상세 로그 확인
docker compose logs app

# 컨테이너 상태 확인
docker inspect release-manager-api

# 볼륨 마운트 확인
docker inspect release-manager-api | grep -A 10 "Mounts"
```

### OutOfMemoryError: Java heap space

**원인**: 대용량 파일 처리 시 JVM heap 메모리 부족

**해결**:
```bash
# docker-compose.yml에서 JAVA_OPTS 수정
JAVA_OPTS: "-Xms1024m -Xmx2048m -XX:+UseG1GC"

# 컨테이너 재시작
docker compose down && docker compose up -d
```

### 데이터베이스 연결 실패

```bash
# MariaDB 상태 확인
docker compose ps mariadb
docker compose logs mariadb

# 직접 연결 테스트
docker exec -it release-manager-mariadb mysql -u root -p

# 환경 변수 확인
docker exec release-manager-api env | grep MARIADB
```

### Redis 연결 실패

```bash
# Redis 상태 확인
docker compose ps redis
docker compose logs redis

# 직접 연결 테스트
docker exec -it release-manager-redis redis-cli -a ${REDIS_PASSWORD} ping
```

### 파일 권한 문제

```bash
# 권한 확인
ls -la /data/release-manager/

# 권한 수정 (컨테이너 내부 사용자: UID 1000)
sudo chown -R 1000:1000 /data/release-manager/resources
```

### 디스크 용량 부족

```bash
# 디스크 사용량 확인
df -h /data

# Docker 정리
docker system prune -a --volumes
```

---

## 백업 및 복구

### 백업

```bash
# 전체 백업 스크립트
BACKUP_DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backup/release-manager/${BACKUP_DATE}"
mkdir -p ${BACKUP_DIR}

# 1. 릴리즈 파일 백업
tar -czf ${BACKUP_DIR}/resources.tar.gz -C /data/release-manager resources

# 2. 데이터베이스 백업
docker exec release-manager-mariadb mysqldump \
  -u root -p${MARIADB_ROOT_PASSWORD} \
  --single-transaction \
  release_manager > ${BACKUP_DIR}/database.sql

# 3. Redis 백업 (AOF)
cp /data/release-manager/redis/data/appendonly.aof ${BACKUP_DIR}/
```

### 복구

```bash
BACKUP_DIR="/backup/release-manager/20250107_120000"

# 1. 서비스 중지
cd /path/to/release-manager/docker
docker compose down

# 2. 릴리즈 파일 복구
tar -xzf ${BACKUP_DIR}/resources.tar.gz -C /data/release-manager

# 3. 데이터베이스 복구
docker compose up -d mariadb
sleep 30  # MariaDB 시작 대기
docker exec -i release-manager-mariadb mysql \
  -u root -p${MARIADB_ROOT_PASSWORD} \
  release_manager < ${BACKUP_DIR}/database.sql

# 4. Redis 복구
cp ${BACKUP_DIR}/appendonly.aof /data/release-manager/redis/data/

# 5. 전체 서비스 시작
docker compose up -d
```

---

## 보안 고려사항

1. **파일 권한**: 755 (디렉토리), 644 (파일)
2. **컨테이너 사용자**: spring 사용자로 실행 (non-root)
3. **네트워크**: 내부 Docker 네트워크(network-ts) 사용
4. **환경변수**: `.env` 파일 Git 제외, GitHub Actions Secrets로 관리
5. **포트 노출**: 필요한 포트만 외부 노출

---

## 포트 정보

| 서비스 | 내부 포트 | 외부 포트 (기본값) |
|--------|-----------|-------------------|
| Application | 8081 | 18080 |
| gRPC | 9090 | 9090 |
| MariaDB | 3306 | 13306 |
| Redis | 6379 | 16379 |

---

## 문의

문제 발생 시:
1. 로그 확인: `docker compose logs -f app`
2. 상태 확인: `docker compose ps`
3. 이슈 등록: GitHub Issues
