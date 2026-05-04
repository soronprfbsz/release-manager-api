#!/bin/bash

################################################################################
# MariaDB 누적 패치 실행 스크립트
# 생성일: {{GENERATED_DATE}}
# 버전 범위: {{FROM_VERSION}} → {{TO_VERSION}}
################################################################################

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 스크립트 디렉토리
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 로그 디렉토리 및 파일 설정
LOG_DIR="$SCRIPT_DIR/logs/mariadb"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="$LOG_DIR/patch_{{FROM_VERSION}}_to_{{TO_VERSION}}_${TIMESTAMP}.log"

# 로그 디렉토리 생성
mkdir -p "$LOG_DIR"

# 로그 함수 (화면 + 파일 동시 출력)
log_to_file() {
    local message="$1"
    local timestamp=$(date +"%Y-%m-%d %H:%M:%S")
    echo "[$timestamp] $message" >> "$LOG_FILE"
}

log_info() {
    local message="$1"
    echo -e "${GREEN}[INFO]${NC} $message"
    log_to_file "[INFO] $message"
}

log_error() {
    local message="$1"
    echo -e "${RED}[ERROR]${NC} $message"
    log_to_file "[ERROR] $message"
}

log_warning() {
    local message="$1"
    echo -e "${YELLOW}[WARNING]${NC} $message"
    log_to_file "[WARNING] $message"
}

log_step() {
    local message="$1"
    echo -e "${CYAN}[STEP]${NC} $message"
    log_to_file "[STEP] $message"
}

log_success() {
    local message="$1"
    echo -e "${GREEN}[SUCCESS]${NC} $message"
    log_to_file "[SUCCESS] $message"
}

# SQL 실행 실패 시 화면/로그에 강조된 박스로 안내한 뒤 즉시 종료한다.
# 이전에는 'cmd | tee' 의 exit code 가 tee(=0) 로 잡혀서 ERROR 가 나도
# 다음 SQL 이 계속 실행되고 마지막엔 누적 패치 '완료' 로 표시되던 문제 방지.
_abort_on_sql_failure() {
    local label="$1"     # 실패 위치 식별자 (sql 파일명 또는 'SQL 문자열')
    local exit_code="$2"
    local divider="=================================================="
    log_to_file ""
    log_to_file "$divider"
    log_to_file "[ERROR] 패치 중단 — SQL 실행 실패"
    log_to_file "  대상      : $label"
    log_to_file "  exit code : $exit_code"
    log_to_file "  로그 파일 : $LOG_FILE"
    log_to_file "$divider"
    echo "" >&2
    echo -e "${RED}${divider}${NC}" >&2
    echo -e "${RED}[ERROR] 패치 중단 — SQL 실행 실패${NC}" >&2
    echo -e "  ${RED}대상${NC}      : $label" >&2
    echo -e "  ${RED}exit code${NC} : $exit_code" >&2
    echo -e "  ${RED}로그 파일${NC} : $LOG_FILE" >&2
    echo -e "${RED}${divider}${NC}" >&2
    exit "$exit_code"
}

# 기본값
DEFAULT_DOCKER_CONTAINER_NAME="infraeye_2.0"
DEFAULT_DB_USER="infraeye"
DEFAULT_PATCHED_BY="{{DEFAULT_PATCHED_BY}}"

# 버전 메타데이터 배열
declare -a VERSION_METADATA=(
{{VERSION_METADATA}}
)

# --- InfraEye CLI 연동 변수 ---
# InfraEye db patch 로 실행되면 아래 변수들은 InfraEye CLI 가 export 한 값으로 override 됨.
# 직접 실행(./mariadb_patch.sh) 시에는 기본값 사용.
INFRAEYE_VERSION_DIR="${INFRAEYE_VERSION_DIR:-/opt/infraeye/data/version}"
INFRAEYE_BACKUP_DIR="${INFRAEYE_BACKUP_DIR:-/opt/infraeye/data/backup/DB}"
BACKUP_TS="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="${INFRAEYE_BACKUP_DIR}/${BACKUP_TS}-mariadb"

# 스크립트 시작
START_TIME=$(date +"%Y-%m-%d %H:%M:%S")
log_to_file "=========================================="
log_to_file "  MariaDB 누적 패치 실행 스크립트"
log_to_file "=========================================="
log_to_file "실행 시작 시간: $START_TIME"
log_to_file "패치 버전 범위: {{FROM_VERSION}} → {{TO_VERSION}}"
log_to_file "포함된 버전 개수: {{VERSION_COUNT}}"
log_to_file "로그 파일: $LOG_FILE"
log_to_file ""

echo "=========================================="
echo "  MariaDB 누적 패치 실행 스크립트"
echo "=========================================="
echo ""
echo "패치 버전 범위: {{FROM_VERSION}} → {{TO_VERSION}}"
echo "포함된 버전 개수: {{VERSION_COUNT}}"
echo "로그 파일: $LOG_FILE"
echo ""

# 패치 적용 담당자 입력
echo "=========================================="
echo "버전 이력 관리를 위한 정보 입력"
echo "=========================================="
# 기본값이 있으면 표시
if [ -n "$DEFAULT_PATCHED_BY" ]; then
    read -p "패치 적용 담당자 [$DEFAULT_PATCHED_BY]: " APPLIED_BY
    APPLIED_BY=${APPLIED_BY:-$DEFAULT_PATCHED_BY}
else
    read -p "패치 적용 담당자 (예: your_name@tscientific.co.kr): " APPLIED_BY
fi
if [ -z "$APPLIED_BY" ]; then
    log_error "패치 적용 담당자는 필수 입력값입니다."
    exit 1
fi
log_to_file "패치 적용 담당자: $APPLIED_BY"
echo ""

# 실행 방식 선택
echo "=========================================="
echo "MariaDB 접속 방식을 선택하세요:"
echo ""
echo "  1) Docker 컨테이너 방식"
echo "     → Docker 컨테이너 내부의 MariaDB에 접속"
echo "     → 'docker exec' 명령으로 컨테이너 내부에서 실행"
echo ""
echo "  2) 네트워크 직접 연결 방식"
echo "     → 호스트:포트로 MariaDB에 직접 연결"
echo "     → 로컬/원격 서버 모두 지원 (IP 또는 localhost)"
echo "=========================================="
echo ""
read -p "선택 (1 또는 2) [1]: " EXECUTION_MODE

EXECUTION_MODE=$(echo "${EXECUTION_MODE:-1}" | tr -d '[:space:]')

if [ "$EXECUTION_MODE" != "1" ] && [ "$EXECUTION_MODE" != "2" ]; then
    log_error "잘못된 선택입니다. 1 또는 2를 입력하세요."
    exit 1
fi

echo ""

# 실행 방식에 따른 설정
if [ "$EXECUTION_MODE" = "1" ]; then
    log_info "선택된 방식: Docker 컨테이너 방식"
    echo ""

    read -p "Docker 컨테이너 이름 [$DEFAULT_DOCKER_CONTAINER_NAME]: " DOCKER_CONTAINER_NAME
    DOCKER_CONTAINER_NAME=${DOCKER_CONTAINER_NAME:-$DEFAULT_DOCKER_CONTAINER_NAME}

    read -p "MariaDB 사용자명 [$DEFAULT_DB_USER]: " DB_USER
    DB_USER=${DB_USER:-$DEFAULT_DB_USER}

    read -sp "MariaDB 비밀번호: " DB_PASSWORD
    echo ""

    echo ""
    log_info "Docker 컨테이너: $DOCKER_CONTAINER_NAME"
    log_info "사용자: $DB_USER"
    log_info "컨테이너 확인 중..."

    if ! docker ps --format "{{.Names}}" | grep -q "^${DOCKER_CONTAINER_NAME}$"; then
        log_error "Docker 컨테이너 '$DOCKER_CONTAINER_NAME'를 찾을 수 없거나 실행 중이 아닙니다."
        exit 1
    fi

    log_info "컨테이너 확인 완료!"

    log_info "MariaDB 연결 테스트 중..."
    if ! docker exec "$DOCKER_CONTAINER_NAME" mariadb -u"$DB_USER" -p"$DB_PASSWORD" -e "SELECT 1;" > /dev/null 2>&1; then
        log_error "MariaDB 접속에 실패했습니다. 사용자명/비밀번호를 확인해주세요."
        exit 1
    fi

    log_success "MariaDB 접속 성공!"

    # 사전 백업 (docker exec + mysqldump)
    # - CM_DB, NMS_DB: full dump
    # - NMC_DB: 스키마만 (시계열 데이터 제외)
    log_step "사전 백업 시작: $BACKUP_DIR"
    mkdir -p "$BACKUP_DIR"

    _backup_db_docker() {
        local db_name="$1"
        local dump_opts="$2"
        local out_file="$3"
        if docker exec "$DOCKER_CONTAINER_NAME" mariadb-dump \
            -u"$DB_USER" -p"$DB_PASSWORD" $dump_opts "$db_name" > "$out_file" 2>>"$LOG_FILE"; then
            log_success "$db_name 백업 완료: $out_file ($(du -h "$out_file" | cut -f1))"
        else
            log_warning "$db_name 백업 실패 (존재하지 않거나 권한 부족일 수 있음): $out_file"
        fi
    }

    _backup_db_docker "CM_DB"  "--single-transaction --routines --triggers" "$BACKUP_DIR/CM_DB.sql"
    _backup_db_docker "NMS_DB" "--single-transaction --routines --triggers" "$BACKUP_DIR/NMS_DB.sql"
    _backup_db_docker "NMC_DB" "--single-transaction --no-data --routines --triggers" "$BACKUP_DIR/NMC_DB.schema.sql"

    cat > "$BACKUP_DIR/manifest.txt" <<EOF
backup_at: $(date +"%Y-%m-%d %H:%M:%S")
patch_from: {{FROM_VERSION}}
patch_to: {{TO_VERSION}}
db_type: mariadb
execution_mode: docker
container: $DOCKER_CONTAINER_NAME
CM_DB: full
NMS_DB: full
NMC_DB: schema-only (시계열 데이터 제외)
note: NMC_DB 데이터 복구 필요 시 수집 시스템 재수집
EOF
    log_info "백업 manifest: $BACKUP_DIR/manifest.txt"
    echo ""

    execute_sql() {
        local sql_file=$1
        log_step "SQL 파일 실행: $sql_file"
        log_to_file "--- SQL 파일 실행 시작: $sql_file ---"

        docker exec -i "$DOCKER_CONTAINER_NAME" mariadb -u"$DB_USER" -p"$DB_PASSWORD" \
            --show-warnings < "$sql_file" 2>&1 | tee -a "$LOG_FILE"
        local exit_code=${PIPESTATUS[0]}

        if [ "$exit_code" -eq 0 ]; then
            log_to_file "--- SQL 파일 실행 성공: $sql_file ---"
            return 0
        fi
        log_to_file "--- SQL 파일 실행 실패: $sql_file (exit code: $exit_code) ---"
        _abort_on_sql_failure "$sql_file" "$exit_code"
    }

    execute_sql_string() {
        local sql_string=$1
        log_to_file "--- SQL 문자열 실행 시작 ---"
        log_to_file "$sql_string"

        echo "$sql_string" | docker exec -i "$DOCKER_CONTAINER_NAME" mariadb -u"$DB_USER" -p"$DB_PASSWORD" \
            --show-warnings 2>&1 | tee -a "$LOG_FILE"
        local exit_code=${PIPESTATUS[1]}

        if [ "$exit_code" -eq 0 ]; then
            log_to_file "--- SQL 문자열 실행 성공 ---"
            return 0
        fi
        log_to_file "--- SQL 문자열 실행 실패 (exit code: $exit_code) ---"
        _abort_on_sql_failure "SQL 문자열 (VERSION_HISTORY 등)" "$exit_code"
    }

else
    log_info "선택된 방식: 네트워크 직접 연결 방식"
    echo ""

    read -p "MariaDB 호스트 주소 (예: localhost, 192.168.1.100) [localhost]: " DB_HOST
    DB_HOST=${DB_HOST:-localhost}

    read -p "MariaDB 포트 [3306]: " DB_PORT
    DB_PORT=${DB_PORT:-3306}

    read -p "MariaDB 사용자명 [$DEFAULT_DB_USER]: " DB_USER
    DB_USER=${DB_USER:-$DEFAULT_DB_USER}

    read -sp "MariaDB 비밀번호: " DB_PASSWORD
    echo ""

    echo ""
    log_info "MariaDB 호스트: $DB_HOST:$DB_PORT"
    log_info "사용자: $DB_USER"
    log_to_file "접속 정보 - 호스트: $DB_HOST:$DB_PORT, 사용자: $DB_USER"
    log_info "네트워크를 통해 MariaDB 연결 테스트 중..."

    if ! mariadb -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" -e "SELECT 1;" > /dev/null 2>&1; then
        log_error "MariaDB 접속에 실패했습니다. 접속 정보를 확인해주세요."
        log_warning "mariadb 클라이언트가 설치되어 있는지 확인하세요."
        log_warning "방화벽 설정 및 포트가 열려있는지 확인하세요."
        exit 1
    fi

    log_success "MariaDB 접속 성공!"

    # 사전 백업 (호스트 mariadb-dump)
    log_step "사전 백업 시작: $BACKUP_DIR"
    mkdir -p "$BACKUP_DIR"

    _backup_db_network() {
        local db_name="$1"
        local dump_opts="$2"
        local out_file="$3"
        if mariadb-dump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" \
            $dump_opts "$db_name" > "$out_file" 2>>"$LOG_FILE"; then
            log_success "$db_name 백업 완료: $out_file ($(du -h "$out_file" | cut -f1))"
        else
            log_warning "$db_name 백업 실패 (존재하지 않거나 권한 부족일 수 있음): $out_file"
        fi
    }

    _backup_db_network "CM_DB"  "--single-transaction --routines --triggers" "$BACKUP_DIR/CM_DB.sql"
    _backup_db_network "NMS_DB" "--single-transaction --routines --triggers" "$BACKUP_DIR/NMS_DB.sql"
    _backup_db_network "NMC_DB" "--single-transaction --no-data --routines --triggers" "$BACKUP_DIR/NMC_DB.schema.sql"

    cat > "$BACKUP_DIR/manifest.txt" <<EOF
backup_at: $(date +"%Y-%m-%d %H:%M:%S")
patch_from: {{FROM_VERSION}}
patch_to: {{TO_VERSION}}
db_type: mariadb
execution_mode: network
host: $DB_HOST:$DB_PORT
CM_DB: full
NMS_DB: full
NMC_DB: schema-only (시계열 데이터 제외)
note: NMC_DB 데이터 복구 필요 시 수집 시스템 재수집
EOF
    log_info "백업 manifest: $BACKUP_DIR/manifest.txt"
    echo ""

    execute_sql() {
        local sql_file=$1
        log_step "SQL 파일 실행: $sql_file"
        log_to_file "--- SQL 파일 실행 시작: $sql_file ---"

        mariadb -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" \
            --show-warnings < "$sql_file" 2>&1 | tee -a "$LOG_FILE"
        local exit_code=${PIPESTATUS[0]}

        if [ "$exit_code" -eq 0 ]; then
            log_to_file "--- SQL 파일 실행 성공: $sql_file ---"
            return 0
        fi
        log_to_file "--- SQL 파일 실행 실패: $sql_file (exit code: $exit_code) ---"
        _abort_on_sql_failure "$sql_file" "$exit_code"
    }

    execute_sql_string() {
        local sql_string=$1
        log_to_file "--- SQL 문자열 실행 시작 ---"
        log_to_file "$sql_string"

        echo "$sql_string" | mariadb -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" \
            --show-warnings 2>&1 | tee -a "$LOG_FILE"
        local exit_code=${PIPESTATUS[1]}

        if [ "$exit_code" -eq 0 ]; then
            log_to_file "--- SQL 문자열 실행 성공 ---"
            return 0
        fi
        log_to_file "--- SQL 문자열 실행 실패 (exit code: $exit_code) ---"
        _abort_on_sql_failure "SQL 문자열 (VERSION_HISTORY 등)" "$exit_code"
    }
fi

# 에러 핸들러 함수
error_handler() {
    local line_number=$1
    local error_code=$2

    log_error "스크립트 실행 중 에러 발생 (라인: $line_number, 에러 코드: $error_code)"

    END_TIME=$(date +"%Y-%m-%d %H:%M:%S")
    START_TIMESTAMP=$(date -d "$START_TIME" +%s 2>/dev/null || date -j -f "%Y-%m-%d %H:%M:%S" "$START_TIME" +%s 2>/dev/null || echo "0")
    END_TIMESTAMP=$(date +%s)
    DURATION=$((END_TIMESTAMP - START_TIMESTAMP))
    DURATION_MIN=$((DURATION / 60))
    DURATION_SEC=$((DURATION % 60))

    log_to_file "=========================================="
    log_to_file "스크립트 실행 실패"
    log_to_file "=========================================="
    log_to_file "에러 발생 시간: $END_TIME"
    log_to_file "에러 발생 라인: $line_number"
    log_to_file "에러 코드: $error_code"
    log_to_file "실행 시간: ${DURATION_MIN}분 ${DURATION_SEC}초"
    log_to_file "=========================================="

    echo ""
    echo "=========================================="
    log_error "패치 실행 실패!"
    echo "=========================================="
    echo "상세 정보는 로그 파일을 확인하세요: $LOG_FILE"
    echo ""

    exit $error_code
}

# 에러 발생 시 핸들러 실행
trap 'error_handler ${LINENO} $?' ERR

# 에러 발생 시 스크립트 중단
set -e

echo ""
echo "=========================================="
echo "  패치 실행 시작"
echo "=========================================="
echo ""

# SQL 파일 디렉토리로 이동 (존재하는 경우에만)
SQL_DIR="$SCRIPT_DIR/database/mariadb"
if [ -d "$SQL_DIR" ]; then
    cd "$SQL_DIR"
fi

{{SQL_EXECUTION_COMMANDS}}

echo ""
echo "=========================================="
log_success "누적 패치 실행 완료!"
echo "=========================================="
echo ""

# 실행 종료 시간 및 소요 시간 계산
END_TIME=$(date +"%Y-%m-%d %H:%M:%S")
START_TIMESTAMP=$(date -d "$START_TIME" +%s 2>/dev/null || date -j -f "%Y-%m-%d %H:%M:%S" "$START_TIME" +%s 2>/dev/null || echo "0")
END_TIMESTAMP=$(date +%s)
DURATION=$((END_TIMESTAMP - START_TIMESTAMP))
DURATION_MIN=$((DURATION / 60))
DURATION_SEC=$((DURATION % 60))

echo "실행 요약:"
echo "  - 적용된 버전 개수: {{VERSION_COUNT}}"
echo "  - 버전 범위: {{FROM_VERSION}} → {{TO_VERSION}}"
echo "  - 적용 담당자: $APPLIED_BY"
echo "  - 시작 시간: $START_TIME"
echo "  - 종료 시간: $END_TIME"
echo "  - 실행 시간: ${DURATION_MIN}분 ${DURATION_SEC}초"
echo ""
log_info "각 버전 정보가 CM_DB.VERSION_HISTORY 테이블에 기록되었습니다."
log_info "로그 파일: $LOG_FILE"
echo ""

# 버전 레지스트리 업데이트 (InfraEye --version / info version 용)
# mariadb / cratedb 어느 쪽이든 단일 ${INFRAEYE_VERSION_DIR}/version 파일에 덮어쓴다.
# log 줄에는 db 종류(mariadb)를 함께 남겨 어느 패치가 마지막이었는지 추적할 수 있다.
if [ -n "${INFRAEYE_VERSION_DIR:-}" ]; then
    mkdir -p "$INFRAEYE_VERSION_DIR"
    echo "{{TO_VERSION}}" > "$INFRAEYE_VERSION_DIR/version"
    echo "$(date +"%Y-%m-%d %H:%M:%S") version mariadb {{FROM_VERSION}} -> {{TO_VERSION}} success (by $APPLIED_BY)" \
        >> "$INFRAEYE_VERSION_DIR/log"
    log_success "버전 레지스트리 업데이트: $INFRAEYE_VERSION_DIR/version = {{TO_VERSION}} (mariadb)"
fi

# 최종 로그 기록
log_to_file "=========================================="
log_to_file "누적 패치 실행 완료"
log_to_file "=========================================="
log_to_file "실행 종료 시간: $END_TIME"
log_to_file "총 실행 시간: ${DURATION_MIN}분 ${DURATION_SEC}초"
log_to_file "적용된 버전 개수: {{VERSION_COUNT}}"
log_to_file "버전 범위: {{FROM_VERSION}} → {{TO_VERSION}}"
log_to_file "적용 담당자: $APPLIED_BY"
log_to_file "=========================================="
