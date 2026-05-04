#!/bin/bash

################################################################################
# CrateDB 누적 패치 실행 스크립트
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
LOG_DIR="$SCRIPT_DIR/logs/cratedb"
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
# 호출자(execute_sql / execute_sql_string)가 실패 후에도 다음 SQL 로 진행되어
# 결과적으로 false success 가 표시되던 것을 막는다.
#
# CrateDB 백업은 DDL snapshot 만 (시계열 데이터 백업 없음). 실패 시 운영자에게
# DDL 복원 명령 + '시계열 데이터 재수집 필요' 안내를 함께 노출한다.
_abort_on_sql_failure() {
    local label="$1"
    local exit_code="$2"
    local detail="${3:-}"
    local divider="=================================================="
    log_to_file ""
    log_to_file "$divider"
    log_to_file "[ERROR] 패치 중단 — SQL 실행 실패"
    log_to_file "  대상      : $label"
    log_to_file "  exit code : $exit_code"
    [ -n "$detail" ] && log_to_file "  상세      : $detail"
    log_to_file "  로그 파일 : $LOG_FILE"
    log_to_file "$divider"
    echo "" >&2
    echo -e "${RED}${divider}${NC}" >&2
    echo -e "${RED}[ERROR] 패치 중단 — SQL 실행 실패${NC}" >&2
    echo -e "  ${RED}대상${NC}      : $label" >&2
    echo -e "  ${RED}exit code${NC} : $exit_code" >&2
    [ -n "$detail" ] && echo -e "  ${RED}상세${NC}      : $detail" >&2
    echo -e "  ${RED}로그 파일${NC} : $LOG_FILE" >&2
    echo -e "${RED}${divider}${NC}" >&2

    # 수동 복원 안내 (사전 백업이 있을 때만)
    if [ -n "${BACKUP_DIR:-}" ] && [ -d "${BACKUP_DIR}" ]; then
        log_to_file ""
        log_to_file "[수동 복원 안내]"
        log_to_file "  CrateDB DDL 은 자동 롤백되지 않습니다. 필요 시 아래 DDL snapshot 으로 스키마만 복원하세요."
        log_to_file "  사전 백업 : $BACKUP_DIR"
        log_to_file "  포함 파일 : cratedb.ddl.sql (DDL snapshot 만, 데이터 백업 없음)"
        log_to_file "  복원 예시 :"
        if [ "${EXECUTION_MODE:-1}" = "1" ]; then
            log_to_file "    docker exec -i ${DOCKER_CONTAINER_NAME:-<container>} crash --username ${CRATEDB_USER:-<user>} < $BACKUP_DIR/cratedb.ddl.sql"
        else
            log_to_file "    crash --hosts ${CRATEDB_HOST:-<host>}:${CRATEDB_PORT:-4200} --username ${CRATEDB_USER:-<user>} < $BACKUP_DIR/cratedb.ddl.sql"
        fi
        log_to_file "  주의 : CrateDB 는 시계열 DB 라 데이터 dump 가 없습니다. 데이터는 수집 시스템 재수집이 필요합니다."

        echo "" >&2
        echo -e "${YELLOW}[수동 복원 안내]${NC}" >&2
        echo -e "  CrateDB DDL 은 자동 롤백되지 않습니다. 필요 시 아래 DDL snapshot 으로 스키마만 복원하세요." >&2
        echo -e "  ${YELLOW}사전 백업${NC} : $BACKUP_DIR" >&2
        echo -e "  ${YELLOW}포함 파일${NC} : cratedb.ddl.sql (DDL snapshot 만, 데이터 백업 없음)" >&2
        echo -e "  ${YELLOW}복원 예시${NC} :" >&2
        if [ "${EXECUTION_MODE:-1}" = "1" ]; then
            echo -e "    docker exec -i ${DOCKER_CONTAINER_NAME:-<container>} crash --username ${CRATEDB_USER:-<user>} < $BACKUP_DIR/cratedb.ddl.sql" >&2
        else
            echo -e "    crash --hosts ${CRATEDB_HOST:-<host>}:${CRATEDB_PORT:-4200} --username ${CRATEDB_USER:-<user>} < $BACKUP_DIR/cratedb.ddl.sql" >&2
        fi
        echo -e "  ${YELLOW}주의${NC} : CrateDB 는 시계열 DB 라 데이터 dump 가 없습니다. 데이터는 수집 시스템 재수집이 필요합니다." >&2
    fi

    exit "$exit_code"
}

# 기본값
DEFAULT_DOCKER_CONTAINER_NAME="infraeye_2.0"
DEFAULT_CRATEDB_USER="infraeye"
DEFAULT_CRATEDB_PORT="4200"

# 버전 메타데이터 배열
declare -a VERSION_METADATA=(
{{VERSION_METADATA}}
)

# --- InfraEye CLI 연동 변수 ---
# InfraEye db patch 로 실행되면 아래 변수들은 InfraEye CLI 가 export 한 값으로 override 됨.
# 직접 실행(./cratedb_patch.sh) 시에는 기본값 사용.
INFRAEYE_VERSION_DIR="${INFRAEYE_VERSION_DIR:-/opt/infraeye/data/version}"
INFRAEYE_BACKUP_DIR="${INFRAEYE_BACKUP_DIR:-/opt/infraeye/data/backup/DB}"
BACKUP_TS="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="${INFRAEYE_BACKUP_DIR}/${BACKUP_TS}-cratedb"

# 스크립트 시작
START_TIME=$(date +"%Y-%m-%d %H:%M:%S")
log_to_file "=========================================="
log_to_file "  CrateDB 누적 패치 실행 스크립트"
log_to_file "=========================================="
log_to_file "실행 시작 시간: $START_TIME"
log_to_file "패치 버전 범위: {{FROM_VERSION}} → {{TO_VERSION}}"
log_to_file "포함된 버전 개수: {{VERSION_COUNT}}"
log_to_file "로그 파일: $LOG_FILE"
log_to_file ""

echo "=========================================="
echo "  CrateDB 누적 패치 실행 스크립트"
echo "=========================================="
echo ""
echo "패치 버전 범위: {{FROM_VERSION}} → {{TO_VERSION}}"
echo "포함된 버전 개수: {{VERSION_COUNT}}"
echo "로그 파일: $LOG_FILE"
echo ""

# 실행 방식 선택
echo "=========================================="
echo "CrateDB 접속 방식을 선택하세요:"
echo ""
echo "  1) Docker 컨테이너 방식"
echo "     → Docker 컨테이너 내부의 CrateDB에 접속"
echo "     → 'docker exec' 명령으로 컨테이너 내부에서 crash CLI 실행"
echo ""
echo "  2) 네트워크 직접 연결 방식"
echo "     → 호스트:포트로 CrateDB에 직접 연결"
echo "     → 로컬/원격 서버 모두 지원 (IP 또는 localhost)"
echo "     → crash CLI가 호스트 시스템에 설치되어 있어야 함"
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

    read -p "CrateDB 사용자명 [$DEFAULT_CRATEDB_USER]: " CRATEDB_USER
    CRATEDB_USER=${CRATEDB_USER:-$DEFAULT_CRATEDB_USER}

    read -sp "CrateDB 비밀번호: " CRATEDB_PASSWORD
    echo ""

    echo ""
    log_info "Docker 컨테이너: $DOCKER_CONTAINER_NAME"
    log_info "사용자: $CRATEDB_USER"
    log_info "컨테이너 확인 중..."

    if ! docker ps --format "{{.Names}}" | grep -q "^${DOCKER_CONTAINER_NAME}$"; then
        log_error "Docker 컨테이너 '$DOCKER_CONTAINER_NAME'를 찾을 수 없거나 실행 중이 아닙니다."
        exit 1
    fi

    log_info "컨테이너 확인 완료!"

    log_info "CrateDB 연결 테스트 중..."
    TEST_SQL="SELECT 1;"

    if [ -z "$CRATEDB_PASSWORD" ]; then
        # 비밀번호 없음
        TEST_RESULT=$(docker exec "$DOCKER_CONTAINER_NAME" crash --username "$CRATEDB_USER" --command "$TEST_SQL" 2>&1)
    else
        # 비밀번호 있음
        TEST_RESULT=$(docker exec -e CRATEPW="$CRATEDB_PASSWORD" "$DOCKER_CONTAINER_NAME" crash --username "$CRATEDB_USER" --command "$TEST_SQL" 2>&1)
    fi

    log_to_file "연결 테스트 결과:"
    log_to_file "$TEST_RESULT"

    if echo "$TEST_RESULT" | grep -qi "error\|failed\|exception\|unable to connect"; then
        log_error "CrateDB 접속에 실패했습니다."
        log_error "연결 테스트 출력:"
        log_error "$TEST_RESULT"
        log_warning "사용자명/비밀번호를 확인해주세요."
        exit 1
    fi

    log_success "CrateDB 접속 성공!"

    # 사전 백업 (DDL 스냅샷만 - 시계열 데이터 제외)
    log_step "사전 백업 시작 (DDL 스냅샷): $BACKUP_DIR"
    mkdir -p "$BACKUP_DIR"

    _crate_exec_docker() {
        local sql="$1"
        local fmt="${2:-tabular}"
        if [ -z "$CRATEDB_PASSWORD" ]; then
            docker exec "$DOCKER_CONTAINER_NAME" crash --username "$CRATEDB_USER" --format "$fmt" --command "$sql" 2>>"$LOG_FILE"
        else
            docker exec -e CRATEPW="$CRATEDB_PASSWORD" "$DOCKER_CONTAINER_NAME" crash --username "$CRATEDB_USER" --format "$fmt" --command "$sql" 2>>"$LOG_FILE"
        fi
    }

    DDL_FILE="$BACKUP_DIR/cratedb.ddl.sql"
    : > "$DDL_FILE"

    # 사용자 테이블 목록 (시스템 스키마 제외)
    TABLE_LIST=$(_crate_exec_docker \
        "SELECT table_schema || '.' || table_name FROM information_schema.tables WHERE table_schema NOT IN ('information_schema', 'sys', 'pg_catalog')" \
        "csv" | tail -n +2 | tr -d '"' | sed '/^$/d')

    if [ -z "$TABLE_LIST" ]; then
        log_warning "CrateDB 사용자 테이블이 없습니다. DDL 스냅샷 생략."
    else
        echo "-- CrateDB DDL snapshot" >> "$DDL_FILE"
        echo "-- generated_at: $(date +"%Y-%m-%d %H:%M:%S")" >> "$DDL_FILE"
        echo "-- patch: {{FROM_VERSION}} -> {{TO_VERSION}}" >> "$DDL_FILE"
        echo "" >> "$DDL_FILE"

        local ddl_ok=0
        local ddl_fail=0
        while IFS= read -r tbl; do
            [ -z "$tbl" ] && continue
            echo "-- $tbl" >> "$DDL_FILE"
            if _crate_exec_docker "SHOW CREATE TABLE $tbl" "tabular" >> "$DDL_FILE"; then
                echo "" >> "$DDL_FILE"
                ddl_ok=$((ddl_ok + 1))
            else
                echo "-- [ERROR] SHOW CREATE TABLE failed" >> "$DDL_FILE"
                ddl_fail=$((ddl_fail + 1))
            fi
        done <<< "$TABLE_LIST"

        log_success "DDL 스냅샷 완료: $DDL_FILE (성공 $ddl_ok, 실패 $ddl_fail)"
    fi

    cat > "$BACKUP_DIR/manifest.txt" <<EOF
backup_at: $(date +"%Y-%m-%d %H:%M:%S")
patch_from: {{FROM_VERSION}}
patch_to: {{TO_VERSION}}
db_type: cratedb
execution_mode: docker
container: $DOCKER_CONTAINER_NAME
note: DDL 스냅샷만 저장. 시계열 데이터 백업 없음 (재수집 필요).
EOF
    log_info "백업 manifest: $BACKUP_DIR/manifest.txt"
    echo ""

    # SQL 실행 함수 (Docker 방식)
    execute_sql() {
        local sql_file=$1
        log_step "SQL 파일 실행: $sql_file"
        log_to_file "--- SQL 파일 실행 시작: $sql_file ---"

        if [ ! -f "$sql_file" ]; then
            _abort_on_sql_failure "$sql_file" 1 "SQL 파일을 찾을 수 없습니다"
        fi

        local file_size=$(wc -c < "$sql_file")
        if [ "$file_size" -eq 0 ]; then
            log_warning "SQL 파일이 비어있습니다: $sql_file"
            log_to_file "--- SQL 파일이 비어있음: $sql_file ---"
            return 0
        fi

        log_to_file "SQL 파일 크기: $file_size bytes"

        if [ -z "$CRATEDB_PASSWORD" ]; then
            SQL_RESULT=$(docker exec -i "$DOCKER_CONTAINER_NAME" crash --username "$CRATEDB_USER" < "$sql_file" 2>&1)
            EXIT_CODE=$?
        else
            SQL_RESULT=$(docker exec -i -e CRATEPW="$CRATEDB_PASSWORD" "$DOCKER_CONTAINER_NAME" crash --username "$CRATEDB_USER" < "$sql_file" 2>&1)
            EXIT_CODE=$?
        fi

        echo "$SQL_RESULT"
        log_to_file "crash CLI 종료 코드: $EXIT_CODE"
        log_to_file "crash CLI 출력:"
        log_to_file "$SQL_RESULT"

        if [ $EXIT_CODE -ne 0 ]; then
            _abort_on_sql_failure "$sql_file" "$EXIT_CODE" "$SQL_RESULT"
        fi
        if echo "$SQL_RESULT" | grep -qi "error\|exception\|failed"; then
            _abort_on_sql_failure "$sql_file" 1 "$SQL_RESULT"
        fi

        log_success "SQL 파일 실행 성공: $sql_file"
        log_to_file "--- SQL 파일 실행 성공: $sql_file ---"
        return 0
    }

    execute_sql_string() {
        local sql_string=$1
        log_to_file "--- SQL 문자열 실행 시작 ---"
        log_to_file "$sql_string"

        if [ -z "$CRATEDB_PASSWORD" ]; then
            SQL_RESULT=$(echo "$sql_string" | docker exec -i "$DOCKER_CONTAINER_NAME" crash --username "$CRATEDB_USER" 2>&1)
            EXIT_CODE=$?
        else
            SQL_RESULT=$(echo "$sql_string" | docker exec -i -e CRATEPW="$CRATEDB_PASSWORD" "$DOCKER_CONTAINER_NAME" crash --username "$CRATEDB_USER" 2>&1)
            EXIT_CODE=$?
        fi

        echo "$SQL_RESULT"
        log_to_file "crash CLI 종료 코드: $EXIT_CODE"
        log_to_file "crash CLI 출력:"
        log_to_file "$SQL_RESULT"

        if [ $EXIT_CODE -ne 0 ]; then
            _abort_on_sql_failure "SQL 문자열" "$EXIT_CODE" "$SQL_RESULT"
        fi
        if echo "$SQL_RESULT" | grep -qi "error\|exception\|failed"; then
            _abort_on_sql_failure "SQL 문자열" 1 "$SQL_RESULT"
        fi

        log_to_file "--- SQL 문자열 실행 성공 ---"
        return 0
    }

else
    log_info "선택된 방식: 네트워크 직접 연결 방식"
    echo ""

    read -p "CrateDB 호스트 주소 (예: localhost, 192.168.1.100) [localhost]: " CRATEDB_HOST
    CRATEDB_HOST=${CRATEDB_HOST:-localhost}

    read -p "CrateDB 포트 [$DEFAULT_CRATEDB_PORT]: " CRATEDB_PORT
    CRATEDB_PORT=${CRATEDB_PORT:-$DEFAULT_CRATEDB_PORT}

    read -p "CrateDB 사용자명 [$DEFAULT_CRATEDB_USER]: " CRATEDB_USER
    CRATEDB_USER=${CRATEDB_USER:-$DEFAULT_CRATEDB_USER}

    read -sp "CrateDB 비밀번호: " CRATEDB_PASSWORD
    echo ""

    echo ""
    log_info "CrateDB 호스트: $CRATEDB_HOST:$CRATEDB_PORT"
    log_info "사용자: $CRATEDB_USER"
    log_to_file "접속 정보 - 호스트: $CRATEDB_HOST:$CRATEDB_PORT, 사용자: $CRATEDB_USER"

    # crash 명령어 존재 확인
    if ! command -v crash &> /dev/null; then
        log_error "crash CLI를 찾을 수 없습니다."
        log_error "crash를 설치하세요."
        log_error "설치 방법: pip install crash"
        exit 1
    fi

    log_info "crash CLI를 통해 CrateDB 연결 테스트 중..."

    # CrateDB 연결 테스트 (crash CLI 사용)
    TEST_SQL="SELECT 1;"

    if [ -z "$CRATEDB_PASSWORD" ]; then
        # 비밀번호 없음
        TEST_RESULT=$(crash --hosts "$CRATEDB_HOST:$CRATEDB_PORT" --username "$CRATEDB_USER" --command "$TEST_SQL" 2>&1)
    else
        # 비밀번호 있음
        TEST_RESULT=$(CRATEPW="$CRATEDB_PASSWORD" crash --hosts "$CRATEDB_HOST:$CRATEDB_PORT" --username "$CRATEDB_USER" --command "$TEST_SQL" 2>&1)
    fi

    log_to_file "연결 테스트 결과:"
    log_to_file "$TEST_RESULT"

    # 에러 확인
    if echo "$TEST_RESULT" | grep -qi "error\|failed\|exception\|unable to connect"; then
        log_error "CrateDB 접속에 실패했습니다."
        log_error "연결 테스트 출력:"
        log_error "$TEST_RESULT"
        log_warning "호스트 주소, 포트, 사용자명, 비밀번호를 확인해주세요."
        log_warning "CrateDB 서버가 실행 중인지 확인하세요."
        log_warning "방화벽 설정 및 포트가 열려있는지 확인하세요."
        exit 1
    fi

    log_success "CrateDB 접속 성공!"

    # 사전 백업 (DDL 스냅샷만 - 시계열 데이터 제외)
    log_step "사전 백업 시작 (DDL 스냅샷): $BACKUP_DIR"
    mkdir -p "$BACKUP_DIR"

    _crate_exec_network() {
        local sql="$1"
        local fmt="${2:-tabular}"
        if [ -z "$CRATEDB_PASSWORD" ]; then
            crash --hosts "$CRATEDB_HOST:$CRATEDB_PORT" --username "$CRATEDB_USER" --format "$fmt" --command "$sql" 2>>"$LOG_FILE"
        else
            CRATEPW="$CRATEDB_PASSWORD" crash --hosts "$CRATEDB_HOST:$CRATEDB_PORT" --username "$CRATEDB_USER" --format "$fmt" --command "$sql" 2>>"$LOG_FILE"
        fi
    }

    DDL_FILE="$BACKUP_DIR/cratedb.ddl.sql"
    : > "$DDL_FILE"

    TABLE_LIST=$(_crate_exec_network \
        "SELECT table_schema || '.' || table_name FROM information_schema.tables WHERE table_schema NOT IN ('information_schema', 'sys', 'pg_catalog')" \
        "csv" | tail -n +2 | tr -d '"' | sed '/^$/d')

    if [ -z "$TABLE_LIST" ]; then
        log_warning "CrateDB 사용자 테이블이 없습니다. DDL 스냅샷 생략."
    else
        echo "-- CrateDB DDL snapshot" >> "$DDL_FILE"
        echo "-- generated_at: $(date +"%Y-%m-%d %H:%M:%S")" >> "$DDL_FILE"
        echo "-- patch: {{FROM_VERSION}} -> {{TO_VERSION}}" >> "$DDL_FILE"
        echo "" >> "$DDL_FILE"

        local ddl_ok=0
        local ddl_fail=0
        while IFS= read -r tbl; do
            [ -z "$tbl" ] && continue
            echo "-- $tbl" >> "$DDL_FILE"
            if _crate_exec_network "SHOW CREATE TABLE $tbl" "tabular" >> "$DDL_FILE"; then
                echo "" >> "$DDL_FILE"
                ddl_ok=$((ddl_ok + 1))
            else
                echo "-- [ERROR] SHOW CREATE TABLE failed" >> "$DDL_FILE"
                ddl_fail=$((ddl_fail + 1))
            fi
        done <<< "$TABLE_LIST"

        log_success "DDL 스냅샷 완료: $DDL_FILE (성공 $ddl_ok, 실패 $ddl_fail)"
    fi

    cat > "$BACKUP_DIR/manifest.txt" <<EOF
backup_at: $(date +"%Y-%m-%d %H:%M:%S")
patch_from: {{FROM_VERSION}}
patch_to: {{TO_VERSION}}
db_type: cratedb
execution_mode: network
host: $CRATEDB_HOST:$CRATEDB_PORT
note: DDL 스냅샷만 저장. 시계열 데이터 백업 없음 (재수집 필요).
EOF
    log_info "백업 manifest: $BACKUP_DIR/manifest.txt"
    echo ""

    # SQL 실행 함수 (네트워크 방식)
    execute_sql() {
        local sql_file=$1
        log_step "SQL 파일 실행: $sql_file"
        log_to_file "--- SQL 파일 실행 시작: $sql_file ---"

        if [ ! -f "$sql_file" ]; then
            _abort_on_sql_failure "$sql_file" 1 "SQL 파일을 찾을 수 없습니다"
        fi

        local file_size=$(wc -c < "$sql_file")
        if [ "$file_size" -eq 0 ]; then
            log_warning "SQL 파일이 비어있습니다: $sql_file"
            log_to_file "--- SQL 파일이 비어있음: $sql_file ---"
            return 0
        fi

        log_to_file "SQL 파일 크기: $file_size bytes"

        if [ -z "$CRATEDB_PASSWORD" ]; then
            SQL_RESULT=$(crash --hosts "$CRATEDB_HOST:$CRATEDB_PORT" --username "$CRATEDB_USER" < "$sql_file" 2>&1)
            EXIT_CODE=$?
        else
            SQL_RESULT=$(CRATEPW="$CRATEDB_PASSWORD" crash --hosts "$CRATEDB_HOST:$CRATEDB_PORT" --username "$CRATEDB_USER" < "$sql_file" 2>&1)
            EXIT_CODE=$?
        fi

        echo "$SQL_RESULT"
        log_to_file "crash CLI 종료 코드: $EXIT_CODE"
        log_to_file "crash CLI 출력:"
        log_to_file "$SQL_RESULT"

        if [ $EXIT_CODE -ne 0 ]; then
            _abort_on_sql_failure "$sql_file" "$EXIT_CODE" "$SQL_RESULT"
        fi
        if echo "$SQL_RESULT" | grep -qi "error\|exception\|failed"; then
            _abort_on_sql_failure "$sql_file" 1 "$SQL_RESULT"
        fi

        log_success "SQL 파일 실행 성공: $sql_file"
        log_to_file "--- SQL 파일 실행 성공: $sql_file ---"
        return 0
    }

    execute_sql_string() {
        local sql_string=$1
        log_to_file "--- SQL 문자열 실행 시작 ---"
        log_to_file "$sql_string"

        if [ -z "$CRATEDB_PASSWORD" ]; then
            SQL_RESULT=$(echo "$sql_string" | crash --hosts "$CRATEDB_HOST:$CRATEDB_PORT" --username "$CRATEDB_USER" 2>&1)
            EXIT_CODE=$?
        else
            SQL_RESULT=$(echo "$sql_string" | CRATEPW="$CRATEDB_PASSWORD" crash --hosts "$CRATEDB_HOST:$CRATEDB_PORT" --username "$CRATEDB_USER" 2>&1)
            EXIT_CODE=$?
        fi

        echo "$SQL_RESULT"
        log_to_file "crash CLI 종료 코드: $EXIT_CODE"
        log_to_file "crash CLI 출력:"
        log_to_file "$SQL_RESULT"

        if [ $EXIT_CODE -ne 0 ]; then
            _abort_on_sql_failure "SQL 문자열" "$EXIT_CODE" "$SQL_RESULT"
        fi
        if echo "$SQL_RESULT" | grep -qi "error\|exception\|failed"; then
            _abort_on_sql_failure "SQL 문자열" 1 "$SQL_RESULT"
        fi

        log_to_file "--- SQL 문자열 실행 성공 ---"
        return 0
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

# SQL 파일 디렉토리로 이동
SQL_DIR="$SCRIPT_DIR/database/cratedb"
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
echo "  - 시작 시간: $START_TIME"
echo "  - 종료 시간: $END_TIME"
echo "  - 실행 시간: ${DURATION_MIN}분 ${DURATION_SEC}초"
echo ""
log_info "로그 파일: $LOG_FILE"
echo ""

# 버전 레지스트리 업데이트 (InfraEye --version / info version 용)
# mariadb / cratedb 어느 쪽이든 단일 ${INFRAEYE_VERSION_DIR}/version 파일에 덮어쓴다.
# log 줄에는 db 종류(cratedb)를 함께 남겨 어느 패치가 마지막이었는지 추적할 수 있다.
if [ -n "${INFRAEYE_VERSION_DIR:-}" ]; then
    mkdir -p "$INFRAEYE_VERSION_DIR"
    echo "{{TO_VERSION}}" > "$INFRAEYE_VERSION_DIR/version"
    echo "$(date +"%Y-%m-%d %H:%M:%S") version cratedb {{FROM_VERSION}} -> {{TO_VERSION}} success" \
        >> "$INFRAEYE_VERSION_DIR/log"
    log_success "버전 레지스트리 업데이트: $INFRAEYE_VERSION_DIR/version = {{TO_VERSION}} (cratedb)"
fi

# 최종 로그 기록
log_to_file "=========================================="
log_to_file "누적 패치 실행 완료"
log_to_file "=========================================="
log_to_file "실행 종료 시간: $END_TIME"
log_to_file "총 실행 시간: ${DURATION_MIN}분 ${DURATION_SEC}초"
log_to_file "적용된 버전 개수: {{VERSION_COUNT}}"
log_to_file "버전 범위: {{FROM_VERSION}} → {{TO_VERSION}}"
log_to_file "=========================================="
