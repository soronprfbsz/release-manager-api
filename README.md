# Release Manager

소프트웨어 릴리즈 버전 및 패치 파일 관리를 위한 Spring Boot 기반 REST API 시스템

## 주요 기능

- 릴리즈 버전 관리 (표준/고객사별 커스텀 버전)
- 핫픽스 버전 관리
- 패치 파일 관리 (MariaDB/CrateDB SQL 스크립트)
- 누적 패치 생성 (버전 범위별)
- 퍼블리싱 파일 관리 (ZIP 업로드/서빙)
- 리소스 파일 관리 (스크립트, 문서 등)
- JWT 기반 인증 시스템
- SSH 터미널 (WebSocket)
- RESTful API + gRPC
- Swagger API 문서화
- GitHub Actions CI/CD (self-hosted runner 자동 배포)

## 기술 스택

| 분류 | 기술 |
|------|------|
| Framework | Spring Boot 3.5.6, Spring Security |
| Language | Java 17 |
| Database | MariaDB 10.11.5, Redis 8.x |
| Query | QueryDSL, JPA, p6spy |
| Migration | Flyway |
| API Docs | SpringDoc OpenAPI 3 |
| RPC | gRPC, Protocol Buffers |
| Build | Gradle 9.x |
| Container | Docker, Docker Compose |
| CI/CD | GitHub Actions (self-hosted runner) |

## 빠른 시작

### 1. 환경 설정

```bash
# 저장소 클론
git clone <repository-url>
cd release-manager

# 환경 변수 설정
cp .env.example .env
# .env 파일 수정 (DB 접속 정보, JWT Secret 등)
```

### 2. Docker로 실행 (권장)

```bash
# Docker Network 생성 (최초 1회)
docker network create network-ts

# 전체 서비스 실행
cd docker
docker compose up -d

# 로그 확인
docker compose logs -f app

# 중지
docker compose down
```

### 3. 로컬 개발 모드

```bash
# DB만 실행
cd docker
docker compose up -d mariadb redis

# Application 로컬 실행
./gradlew bootRun
```

### 4. API 접속

| 서비스 | URL |
|--------|-----|
| Swagger UI | http://localhost:8081/swagger |
| API Docs | http://localhost:8081/api-docs |
| Health Check | http://localhost:8081/actuator/health |
| gRPC | localhost:9090 |

## 프로젝트 구조

### 소스 코드 구조

```
src/main/java/com/ts/rm/
├── domain/                        # 도메인 계층
│   ├── auth/                      # 인증 (로그인, 회원가입, 토큰)
│   ├── account/                   # 계정 관리
│   ├── releaseversion/            # 릴리즈 버전 관리
│   ├── releasefile/               # 릴리즈 파일 관리
│   ├── patch/                     # 패치 생성/조회
│   ├── publishing/                # 퍼블리싱 파일 관리
│   ├── resourcefile/              # 리소스 파일 관리
│   ├── customer/                  # 고객사 관리
│   ├── engineer/                  # 엔지니어 관리
│   ├── project/                   # 프로젝트 관리
│   ├── terminal/                  # SSH 터미널
│   ├── job/                       # DB 백업/복원 작업
│   └── common/                    # 공통 서비스
└── global/                        # 공통 기능
    ├── config/                    # 설정 (Security, Swagger, QueryDSL, Redis)
    ├── security/                  # JWT 인증 필터
    ├── file/                      # 파일 유틸리티
    ├── ssh/                       # SSH 실행기
    └── exception/                 # 전역 예외 처리
```

### 릴리즈 파일 저장소 구조

```
src/main/resources/release-manager/    # 로컬 개발
/data/release-manager/resources/       # Docker 배포

├── resources/
│   ├── publishing/                # 퍼블리싱 파일
│   │   └── {publishingName}/      # ZIP 해제된 파일들
│   │       ├── index.html
│   │       ├── css/
│   │       └── js/
│   └── file/                      # 리소스 파일
│       ├── script/                # 스크립트 파일
│       │   ├── MARIADB/
│       │   └── CRATEDB/
│       └── document/              # 문서 파일
│
├── templates/                     # 패치 스크립트 템플릿
│   ├── MARIADB/
│   │   └── mariadb_patch_template.sh
│   └── CRATEDB/
│       └── cratedb_patch_template.sh
│
└── versions/                      # 릴리즈 버전별 패치 파일
    └── {projectId}/
        ├── standard/              # 표준 릴리즈
        │   └── {major}.{minor}.x/
        │       └── {version}/
        │           ├── mariadb/
        │           ├── cratedb/
        │           └── hotfix/
        │               └── {hotfixVersion}/
        └── custom/                # 커스텀 릴리즈
            └── {customerCode}/
                └── {major}.{minor}.x/
                    └── {version}/
```

## 주요 API

### 인증

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/refresh` | 토큰 갱신 |
| POST | `/api/auth/logout` | 로그아웃 |

### 릴리즈 버전

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/releases/versions/{id}` | 버전 상세 조회 |
| POST | `/api/releases/versions/standard` | 표준 버전 생성 |
| POST | `/api/releases/versions/custom` | 커스텀 버전 생성 |
| POST | `/api/releases/versions/{id}/hotfix` | 핫픽스 생성 |
| DELETE | `/api/releases/versions/{id}` | 버전 삭제 |
| GET | `/api/releases/standard/tree` | 표준 버전 트리 조회 |
| GET | `/api/releases/custom/{customerCode}/tree` | 커스텀 버전 트리 조회 |

### 패치

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/patches` | 누적 패치 생성 |
| POST | `/api/patches/hotfix` | 핫픽스 패치 생성 |
| GET | `/api/patches` | 패치 목록 조회 |
| GET | `/api/patches/{id}` | 패치 상세 조회 |
| GET | `/api/patches/{id}/download` | 패치 다운로드 |

### 퍼블리싱

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/publishing` | 퍼블리싱 목록 조회 |
| POST | `/api/publishing` | 퍼블리싱 생성 (ZIP 업로드) |
| GET | `/api/publishing/{id}` | 퍼블리싱 상세 조회 |
| DELETE | `/api/publishing/{id}` | 퍼블리싱 삭제 |
| GET | `/api/publishing/{id}/serve/{path}` | 파일 서빙 |
| GET | `/api/publishing/{id}/download` | ZIP 다운로드 |
| GET | `/api/publishing/{id}/tree` | 파일 트리 조회 |

### 리소스 파일

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/resource-files` | 리소스 파일 목록 |
| POST | `/api/resource-files` | 리소스 파일 업로드 |
| GET | `/api/resource-files/{id}/download` | 리소스 파일 다운로드 |
| DELETE | `/api/resource-files/{id}` | 리소스 파일 삭제 |

자세한 API 명세는 Swagger UI를 참고하세요.

## 개발 가이드

### 계층 구조

```
Controller → Service → Repository → Database
    ↓           ↓
   DTO        Entity
```

### Repository 패턴 (JPA + QueryDSL)

```java
// Spring Data JPA (기본 CRUD)
public interface ReleaseVersionRepository extends JpaRepository<ReleaseVersion, Long>,
                                                   ReleaseVersionRepositoryCustom {
    Optional<ReleaseVersion> findByVersion(String version);
}

// QueryDSL (복잡한 쿼리)
public interface ReleaseVersionRepositoryCustom {
    List<ReleaseVersion> findStandardVersions();
}

public class ReleaseVersionRepositoryImpl implements ReleaseVersionRepositoryCustom {
    private final JPAQueryFactory queryFactory;
    // QueryDSL 구현
}
```

### 데이터베이스 마이그레이션

Flyway를 사용하여 스키마 변경을 관리합니다.

```sql
-- src/main/resources/db/migration/V8__create_new_table.sql
CREATE TABLE new_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)
);
```

애플리케이션 재시작 시 자동으로 마이그레이션이 적용됩니다.

## 빌드 및 테스트

```bash
# 빌드 (테스트 제외)
./gradlew clean build -x test

# 전체 테스트
./gradlew test

# 특정 도메인 테스트
./gradlew test --tests "com.ts.rm.domain.patch.*"

# QueryDSL Q클래스 재생성
./gradlew clean build
```

## CI/CD 파이프라인

워크플로: `.github/workflows/deploy.yml` — 배포 서버에 설치된 self-hosted runner(label `rm-106`)가
호스트의 docker 로 직접 빌드·배포한다. `tscorp-dev2` 조직 저장소에서만 실행된다(미러 저장소에서는 skip).

### 파이프라인 구조

```
main 브랜치 push (또는 Actions 탭 → Deploy → Run workflow 수동 실행)
  ↓
1. Build jar          → ./gradlew build -x test
  ↓
2. Build image        → docker/Dockerfile.ci 로 ts/release-manager-api:latest 빌드 (label git-sha=커밋)
  ↓
3. Write .env         → workflow env + Secrets 로 .env 생성 (배포 후 삭제)
  ↓
4. Ensure MariaDB / Redis → 실행 중이면 그대로 두고, 없을 때만 기동 + 헬스체크
  ↓
5. Deploy app         → app 컨테이너만 교체 (docker compose up -d --no-deps app)
  ↓
6. Health check       → /actuator/health 확인
```

배포 결과 확인: 저장소 **Actions** 탭, 또는 서버에서
`docker inspect release-manager-api --format '{{index .Config.Labels "git-sha"}}'`.

### 설정값

- **비밀값** — 저장소 **Settings → Secrets and variables → Actions → Repository secrets**

  | Secret | 설명 |
  |--------|------|
  | `MARIADB_ROOT_PASSWORD` | MariaDB root 비밀번호 |
  | `MARIADB_USERNAME` / `MARIADB_PASSWORD` | 애플리케이션 DB 계정 |
  | `REDIS_PASSWORD` | Redis 비밀번호 |
  | `JWT_SECRET` | JWT 시크릿 키 (최소 256비트) |

  값은 등록 후 다시 볼 수 없고 덮어쓰기만 가능하다. 운영 DB·Redis 비밀번호를 바꾸면 Secret 도 같이 바꿔야 다음 배포가 성공한다.
- **비밀이 아닌 값** (서버 IP, 포트, `RELEASE_DIR` 등) — `deploy.yml` 상단 `env:` 에서 관리

## 포트 정보

| 서비스 | 내부 포트 | 외부 포트 (기본값) |
|--------|-----------|-------------------|
| Application | 8081 | 18080 |
| gRPC | 9090 | 9090 |
| MariaDB | 3306 | 13306 |
| Redis | 6379 | 16379 |

## Docker 명령어

```bash
# 시작
cd docker
docker compose up -d

# 로그
docker compose logs -f app

# 재시작
docker compose restart app

# 중지
docker compose down

# DB만 실행 (로컬 개발)
docker compose up -d mariadb redis
```

## 문제 해결

### QueryDSL Q클래스 생성 안됨
```bash
./gradlew clean build
```

### Flyway 마이그레이션 오류
```bash
# 마이그레이션 히스토리 확인
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

# 개발 환경에서만: 마이그레이션 초기화
./gradlew flywayClean flywayMigrate
```

### Docker 컨테이너 문제
```bash
# 컨테이너 재시작
docker compose restart app

# 로그 확인
docker compose logs --tail 100 app

# 컨테이너 상태 확인
docker compose ps
```

### 파일 권한 문제
```bash
# 권한 수정 (Docker 배포 환경)
sudo chown -R 1000:1000 /data/release-manager/resources
```

## 관련 문서

- [배포 가이드](docker/DEPLOYMENT_GUIDE.md)
- [API 문서](http://localhost:8081/swagger)

## 코드 스타일

**Google Java Style Guide** 사용

- IntelliJ IDEA: `Ctrl+Alt+L` (Windows) / `Cmd+Option+L` (Mac)

## 라이선스

MIT License
