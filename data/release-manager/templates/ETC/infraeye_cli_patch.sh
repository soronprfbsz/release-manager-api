#!/bin/bash

# =============================================================================
# InfraEye CLI (Release Manager edition)
# =============================================================================
# 레거시 /usr/bin/InfraEye를 대체하는 통합 CLI.
# - 기존 서브커맨드(eng patch, was patch, eng/was/db <args>, into, info) 유지
# - db patch 만 신규 로직으로 교체 (Release Manager가 생성한 호스트 스크립트 실행)
# - --version / info version 추가 (사이트 DB 적용 버전 표시)
#
# 본 파일은 1.0.0 패치 ZIP에 포함되며, install-infraeye.sh 로 /usr/bin/InfraEye 에 설치됩니다.
# =============================================================================

#### host info ####
INSTALL_PATH='/opt'
NMS_CONTAINER_NM='infraeye_2.0'
###################

# --- Release Manager 1.0.0+ 상수 ---
INFRAEYE_CLI_VERSION="1.0.0"
INFRAEYE_DATA_DIR="${INSTALL_PATH}/infraeye/data"
INFRAEYE_VERSION_DIR="${INFRAEYE_DATA_DIR}/version"
INFRAEYE_BACKUP_DIR="${INFRAEYE_DATA_DIR}/backup/DB"
# 레거시 was patch 와 동일한 패턴: {InfraEye설치경로}/patch 아래에서 패치 스크립트를 재귀 탐색
INFRAEYE_PATCH_BASE_DIR="${INSTALL_PATH}/infraeye/patch"

function _show_cli_patch_help()
{
    cat <<EOF
사용법: sudo InfraEye cli patch [OPTIONS]

옵션:
  --install-path=<경로>      INSTALL_PATH 값을 명시적으로 지정
  --nms-container=<이름>     NMS_CONTAINER_NM 값을 명시적으로 지정
  --force                    동일 버전이어도 강제 재설치
  --rollback                 /usr/bin/InfraEye.legacy-bak 을 /usr/bin/InfraEye 로 복원

CLI 소스 파일 탐색 경로:
  ${INFRAEYE_PATCH_BASE_DIR}/ 아래에서 'InfraEye' 파일을 재귀 탐색
  - 1개 발견 시: 해당 파일을 설치 소스로 사용
  - 2개 이상 발견 시: 모호하므로 경고 후 종료
  ※ 어느 경로에서 실행하든 동일 동작 (BASH_SOURCE 가 아닌 탐색 기반)

설정 값 결정 우선순위 (항목별 독립 판단):
  1. CLI 옵션 (--install-path, --nms-container)
  2. 기존 /usr/bin/InfraEye 에서 자동 승계
  3. 기본값 (/opt, infraeye_2.0)

백업 이력 경로: ${INSTALL_PATH}/infraeye/data/backup/CLI/
EOF
}

# CLI 패치 (자기 자신을 /usr/bin/InfraEye 로 설치)
# - 최초 1회: 기존 레거시 파일을 /usr/bin/InfraEye.legacy-bak 로 보존
# - 멱등: 이미 같은 버전이면 skip (--force 로 강제)
# - 롤백: --rollback 옵션
function cli_patch()
{
    # 서브쉘 안에서 strict 모드 실행 (전역 흐름에 영향 없음)
    (
        set -euo pipefail

        local TARGET="/usr/bin/InfraEye"
        local BACKUP_FIXED="/usr/bin/InfraEye.legacy-bak"
        local BACKUP_HISTORY_DIR="${INSTALL_PATH}/infraeye/data/backup/CLI"
        local NEW_VERSION="${INFRAEYE_CLI_VERSION}"

        local rollback=0
        local force=0
        local cli_install_path=""
        local cli_nms_container=""
        for arg in "$@"; do
            case "$arg" in
                --rollback)          rollback=1 ;;
                --force)             force=1 ;;
                --install-path=*)    cli_install_path="${arg#*=}" ;;
                --nms-container=*)   cli_nms_container="${arg#*=}" ;;
                --help|-h)           _show_cli_patch_help; exit 0 ;;
                *) echo "[cli-patch] 알 수 없는 인자: $arg (--help 참조)" >&2; exit 1 ;;
            esac
        done

        [[ $EUID -eq 0 ]] || { echo "[cli-patch] root 권한 필요. 'sudo ./InfraEye cli patch'" >&2; exit 1; }

        # --- 롤백 모드 ---
        if [[ $rollback -eq 1 ]]; then
            [[ -e "$BACKUP_FIXED" ]] || { echo "[cli-patch] 롤백 대상 없음: $BACKUP_FIXED" >&2; exit 1; }
            mv -f "$BACKUP_FIXED" "$TARGET"
            echo "[cli-patch] 롤백 완료: $TARGET ← $BACKUP_FIXED"
            exit 0
        fi

        # --- CLI 소스 파일 재귀 탐색 ---
        # 레거시 was patch / db patch 와 동일한 패턴:
        #   {INFRAEYE_PATCH_DIR | ${INSTALL_PATH}/infraeye/patch} 아래에서 'InfraEye' 파일을 재귀 탐색.
        #   - 0개: 에러 종료
        #   - 1개: 해당 파일을 설치 소스로 사용
        #   - 2개 이상: 모호하므로 경고 후 종료
        # BASH_SOURCE 기반이 아니라 탐색 기반이므로 어느 경로에서 실행하든 동일하게 동작.
        local patch_base="${INFRAEYE_PATCH_DIR:-$INFRAEYE_PATCH_BASE_DIR}"
        if [[ ! -d "$patch_base" ]]; then
            echo "[cli-patch] CLI 패치 베이스 디렉토리가 존재하지 않습니다: $patch_base" >&2
            echo "             패치 파일을 ${INFRAEYE_PATCH_BASE_DIR}/ 아래에 복사해주세요." >&2
            exit 1
        fi

        # ${patch_base}/backup/ 은 이전 패치 보관 영역으로 간주하여 제외 (was/db patch 와 통일)
        local patch_backup_dir="${patch_base}/backup"
        local cli_matches=()
        while IFS= read -r -d '' f; do
            cli_matches+=("$f")
        done < <(find "$patch_base" -path "$patch_backup_dir" -prune -o -type f -name "InfraEye" -print0 2>/dev/null)

        local cli_count=${#cli_matches[@]}

        if [[ "$cli_count" -eq 0 ]]; then
            echo "[cli-patch] CLI 소스 파일(InfraEye)을 찾을 수 없습니다." >&2
            echo "             탐색 경로: ${patch_base}/ (재귀)" >&2
            echo "             패치 파일을 ${INFRAEYE_PATCH_BASE_DIR}/ 아래에 복사해주세요." >&2
            exit 1
        fi

        if [[ "$cli_count" -ge 2 ]]; then
            echo "[cli-patch] CLI 소스 파일(InfraEye)이 2개 이상 발견되어 설치 대상을 결정할 수 없습니다." >&2
            echo "             탐색 경로: ${patch_base}/ (재귀)" >&2
            echo "             발견된 파일 (${cli_count}개):" >&2
            local m
            for m in "${cli_matches[@]}"; do
                echo "               - $m" >&2
            done
            echo "             하나만 남기고 다른 파일은 제거해주세요." >&2
            exit 1
        fi

        local self
        self="$(readlink -f "${cli_matches[0]}")"
        echo "[cli-patch] CLI 소스 파일: $self"

        local target_real=""
        if [[ -e "$TARGET" ]]; then
            target_real="$(readlink -f "$TARGET" 2>/dev/null || echo "")"
        fi

        # --- 발견된 InfraEye 가 이미 설치된 /usr/bin/InfraEye 와 동일 파일이면 차단 ---
        # (예: 사용자가 실수로 INFRAEYE_PATCH_DIR=/usr/bin 지정하는 등)
        if [[ "$self" == "$target_real" ]]; then
            echo "[cli-patch] 발견된 InfraEye 가 이미 설치된 $TARGET 와 동일 파일입니다." >&2
            echo "             패치 디렉토리에 새 InfraEye 파일이 들어있는지 확인하세요." >&2
            exit 1
        fi

        [[ -r "$self" ]] || { echo "[cli-patch] 설치 원본 읽을 수 없음: $self" >&2; exit 1; }

        # --- 실행 중 프로세스 체크 ---
        if pgrep -f "$TARGET" >/dev/null 2>&1; then
            echo "[cli-patch] 경고: $TARGET 실행 중 — 5초 대기 후 진행"
            sleep 5
        fi

        # --- 멱등: 같은 버전이면 skip ---
        if [[ -x "$TARGET" && $force -eq 0 ]]; then
            local current
            current=$("$TARGET" --version 2>/dev/null | awk 'NR==1 {print $3}' || echo "")
            if [[ "$current" == "$NEW_VERSION" ]]; then
                echo "[cli-patch] 이미 $NEW_VERSION 설치됨. skip. (--force 로 강제 재설치)"
                exit 0
            fi
        fi

        # --- 소유권 승계 (기존 → 그대로, 없으면 infraeye:infraeye 기본값) ---
        local OWNER GROUP
        if [[ -e "$TARGET" ]]; then
            OWNER=$(stat -c '%U' "$TARGET")
            GROUP=$(stat -c '%G' "$TARGET")
        else
            OWNER="infraeye"
            GROUP="infraeye"
        fi
        id -u "$OWNER"  >/dev/null 2>&1 || { echo "[cli-patch] 사용자 '$OWNER' 없음 → root 로 fallback"; OWNER="root"; }
        getent group "$GROUP" >/dev/null 2>&1 || { echo "[cli-patch] 그룹 '$GROUP' 없음 → root 로 fallback"; GROUP="root"; }

        # --- 백업 ---
        mkdir -p "$BACKUP_HISTORY_DIR"
        if [[ -e "$TARGET" ]]; then
            if [[ ! -e "$BACKUP_FIXED" ]]; then
                cp -a "$TARGET" "$BACKUP_FIXED"
                echo "[cli-patch] 레거시 고정 백업 생성: $BACKUP_FIXED"
            else
                echo "[cli-patch] 기존 $BACKUP_FIXED 유지 (최초 레거시 보존)"
            fi
            local ts
            ts=$(date +"%Y%m%d-%H%M%S")
            cp -a "$TARGET" "$BACKUP_HISTORY_DIR/InfraEye.$ts.bak"
            echo "[cli-patch] 이력 백업: $BACKUP_HISTORY_DIR/InfraEye.$ts.bak"
        fi

        # --- 설정 값 결정 (우선순위: CLI 옵션 > 기존 파일에서 승계 > 기본값) ---
        local preserved_install_path=""
        local preserved_nms_container=""
        if [[ -e "$TARGET" ]]; then
            local v
            v=$(grep -m1 -E "^INSTALL_PATH=" "$TARGET" 2>/dev/null | cut -d= -f2- | tr -d "'\"")
            [[ -n "$v" ]] && preserved_install_path="$v"
            v=$(grep -m1 -E "^NMS_CONTAINER_NM=" "$TARGET" 2>/dev/null | cut -d= -f2- | tr -d "'\"")
            [[ -n "$v" ]] && preserved_nms_container="$v"
        fi

        local final_install_path="${cli_install_path:-${preserved_install_path:-/opt}}"
        local final_nms_container="${cli_nms_container:-${preserved_nms_container:-infraeye_2.0}}"

        # 각 값의 출처를 로그로 명시
        if [[ -n "$cli_install_path" ]]; then
            echo "[cli-patch] INSTALL_PATH = $final_install_path (CLI 옵션)"
        elif [[ -n "$preserved_install_path" ]]; then
            echo "[cli-patch] INSTALL_PATH = $final_install_path (기존 InfraEye 에서 승계)"
        else
            echo "[cli-patch] 경고: INSTALL_PATH 가 기존 값/옵션 모두 없습니다. 기본값 사용 → $final_install_path"
        fi

        if [[ -n "$cli_nms_container" ]]; then
            echo "[cli-patch] NMS_CONTAINER_NM = $final_nms_container (CLI 옵션)"
        elif [[ -n "$preserved_nms_container" ]]; then
            echo "[cli-patch] NMS_CONTAINER_NM = $final_nms_container (기존 InfraEye 에서 승계)"
        else
            echo "[cli-patch] 경고: NMS_CONTAINER_NM 가 기존 값/옵션 모두 없습니다. 기본값 사용 → $final_nms_container"
        fi

        # --- 신규 파일에 설정 치환 후 임시 파일 생성 ---
        local staged
        staged=$(mktemp)
        # sed 치환 대상 라인 포맷 고정: INSTALL_PATH='...'  /  NMS_CONTAINER_NM='...'
        sed -e "s|^INSTALL_PATH='[^']*'\$|INSTALL_PATH='${final_install_path}'|" \
            -e "s|^NMS_CONTAINER_NM='[^']*'\$|NMS_CONTAINER_NM='${final_nms_container}'|" \
            "$self" > "$staged"

        # 치환이 실제로 일어났는지 검증 (템플릿 포맷이 깨지면 실패)
        if ! grep -q "^INSTALL_PATH='${final_install_path}'\$" "$staged" \
            || ! grep -q "^NMS_CONTAINER_NM='${final_nms_container}'\$" "$staged"; then
            rm -f "$staged"
            echo "[cli-patch] 치환 실패: 템플릿에서 대상 라인을 찾을 수 없습니다." >&2
            exit 1
        fi

        # --- 설치 ---
        install -m 0755 -o "$OWNER" -g "$GROUP" "$staged" "$TARGET"
        rm -f "$staged"
        echo "[cli-patch] 설치 완료: $TARGET ($OWNER:$GROUP, 0755)"

        # --- 검증 ---
        local installed
        installed=$("$TARGET" --version 2>/dev/null | awk 'NR==1 {print $3}' || echo "")
        if [[ "$installed" == "$NEW_VERSION" ]]; then
            echo "[cli-patch] 검증 성공: InfraEye CLI $installed"
        else
            echo "[cli-patch] 검증 실패: 기대=$NEW_VERSION, 실제=$installed" >&2
            exit 1
        fi
    )
}

# 버전 파일 읽기 (없으면 안내 문자열 반환)
function _read_site_version()
{
    local db="$1"
    local f="${INFRAEYE_VERSION_DIR}/${db}"
    if [ -f "$f" ]; then
        cat "$f"
    else
        echo "(not yet applied)"
    fi
}

function _find_patch_metadata_file()
{
    local start_dir="$1"
    local base_dir="${INFRAEYE_PATCH_DIR:-$INFRAEYE_PATCH_BASE_DIR}"
    local current="$start_dir"

    while [ -n "$current" ] && [[ "$current" == "$base_dir"* ]]; do
        if [ -f "$current/.build_version" ]; then
            echo "$current/.build_version"
            return 0
        fi
        if [ "$current" = "$base_dir" ]; then
            break
        fi
        current="$(dirname "$current")"
    done

    local backup_dir="${base_dir}/backup"
    local matches=()
    while IFS= read -r -d '' f; do
        matches+=("$f")
    done < <(find "$base_dir" -path "$backup_dir" -prune -o -type f -name ".build_version" -print0 2>/dev/null)

    if [ "${#matches[@]}" -eq 1 ]; then
        echo "${matches[0]}"
        return 0
    fi

    if [ "${#matches[@]}" -ge 2 ]; then
        echo "[WARN] .build_version 파일이 2개 이상 발견되어 빌드 버전을 결정할 수 없습니다." >&2
    fi

    return 1
}

function _read_patch_to_version()
{
    local start_dir="$1"
    local metadata_file
    metadata_file="$(_find_patch_metadata_file "$start_dir" 2>/dev/null || true)"

    if [ -n "$metadata_file" ]; then
        awk -F= '$1 == "to_version" { print $2; exit }' "$metadata_file"
        return 0
    fi

    return 1
}

function _record_site_version()
{
    local component="$1"
    local version="$2"

    if [ -z "$version" ]; then
        echo "[WARN] 빌드 버전 메타파일(.build_version)을 찾지 못해 빌드 버전을 갱신하지 않습니다."
        return 0
    fi

    mkdir -p "$INFRAEYE_VERSION_DIR"
    echo "$version" > "$INFRAEYE_VERSION_DIR/site"
    echo "$(date +"%Y-%m-%d %H:%M:%S") site ${component} -> ${version} success" >> "$INFRAEYE_VERSION_DIR/log"
    echo "[InfraEye] Site 버전 레지스트리 업데이트: $INFRAEYE_VERSION_DIR/site = $version"
}

# db patch 의 마지막 적용 버전 — mariadb / cratedb 중 더 최근 mtime 의 to_version.
# db patch 는 mariadb 만 / cratedb 만 / 둘 다 적용될 수 있어, 마지막에 갱신된 쪽을 따라간다.
function _read_db_version()
{
    local mdb_file="${INFRAEYE_VERSION_DIR}/mariadb"
    local cdb_file="${INFRAEYE_VERSION_DIR}/cratedb"
    if [ -f "$mdb_file" ] && [ -f "$cdb_file" ]; then
        if [ "$mdb_file" -nt "$cdb_file" ]; then
            cat "$mdb_file"
        else
            cat "$cdb_file"
        fi
    elif [ -f "$mdb_file" ]; then
        cat "$mdb_file"
    elif [ -f "$cdb_file" ]; then
        cat "$cdb_file"
    else
        echo "(not yet applied)"
    fi
}

# --version / info version 공용 출력
# - Version       : db patch (mariadb 또는 cratedb) 시 갱신된 to_version 중 가장 최근
#                   값. mariadb_patch.sh 는 CM_DB.VERSION_HISTORY 와 함께
#                   ${INFRAEYE_VERSION_DIR}/mariadb 를, cratedb_patch.sh 는
#                   ${INFRAEYE_VERSION_DIR}/cratedb 를 mirror 한다.
# - Build Version : was/eng patch 시 .build_version 의 to_version 을
#                   ${INFRAEYE_VERSION_DIR}/site 에 기록한 값.
function _show_version()
{
    local version build
    version=$(_read_db_version)
    build=$(_read_site_version "site")
    cat <<EOF
InfraEye CLI ${INFRAEYE_CLI_VERSION} (Release Manager edition)
  Version: ${version}
  Build Version: ${build}
EOF
}

function _show_help()
{
    cat <<'EOF'
사용법: InfraEye <서브커맨드> [args...]

패치:
  eng patch                  엔진 패치 (대화식)
  was patch                  WAS 패치 (대화식)
  db  patch [--db=...]       DB 패치 (대화식 메뉴 또는 --db=mariadb|cratedb)
  cli patch [--force|--rollback]
                             CLI 자기 자신을 /usr/bin/InfraEye 로 설치/교체
                             (최초 1회: sudo ./InfraEye cli patch)

컨테이너 명령 전달:
  eng|was|db <args>          컨테이너 내 InfraEye_<type> 실행
  into                       컨테이너 쉘 진입

정보:
  info <PATH|CTI_NM|VERSION> 설치 경로 / NMS 컨테이너명 / 사이트 DB 버전 출력
  --version, -v              CLI + MariaDB/CrateDB 적용 버전 출력
  --help, -h                 이 도움말

DB 패치 스크립트 탐색 경로:
  {INSTALL_PATH}/infraeye/patch  (레거시 was patch 와 동일한 패턴, 재귀 탐색)
  - mariadb_patch.sh / cratedb_patch.sh 를 재귀적으로 탐색
  - 동일 이름 파일이 2개 이상 발견되면 모호하므로 경고 후 종료

예:
  sudo ./InfraEye cli patch                         # 최초 1회 CLI 설치
  # 패치 파일을 /opt/infraeye/patch/ 아래(서브디렉토리 포함)에 복사 후 실행
  InfraEye db patch
  InfraEye db patch --db=mariadb
  InfraEye info version
  sudo InfraEye cli patch --rollback                # 레거시로 복원
EOF
}

# =============================================================================
# 엔진 패치 (레거시 그대로 유지)
# =============================================================================
function eng_patch()
{
        echo -e "1. 전체패치( bin 디렉터리 교체 )\n2. 부분패치"
        read -r engPathType

        echo -e "컨테이너 내부의 bin 디렉터리를 백업하시겠습니까? (Y/N)  \nbackup file path : /opt/infraeye/data/backup/ENG"
        read -r backYn


        #백업
        if [ 'Y' == ${backYn^} ];then
                local dirYn=$(docker exec "$container_nm" bash -c "test -d /opt/infraeye/data/backup/ENG && echo 'Y' || echo 'N'")
                if [ 'N' == ${dirYn^} ];then
                        docker exec --user $cmd_user "$container_nm" bash -c "mkdir -p /opt/infraeye/data/backup/ENG"
                fi
                docker exec --user $cmd_user "$container_nm" bash -c "tar -cvf /opt/infraeye/data/backup/ENG/$date-bin.tar.gz -C /opt/infraeye/nms/ bin"

                echo "백업 완료"
        fi

        #find bin dir
        engTest=$(docker exec "$container_nm" bash -c "test -d /docker_dir/patch/eng/bin && echo 'Y' || echo 'N'")
        if [ "$engPathType" == 1 ] && [ "$engTest" != "Y" ]; then
                echo -e "전체 패치를 위해 필요한  bin 디렉터리를 찾을수 없습니다."
                exit 1
        fi


        #패치
        if [ "$engPathType" -eq 1 ]; then
                echo "1. 전체 패치시  컨테이너의 /opt/infraeye/nms/bin 디렉터리가 로컬서버의 /opt/infraeye/patch/eng/bin 디렉터리로 교체됩니다."
                echo "2. 전체 엔진이 재시작 됩니다."

                echo "패치를 진행 하시겠습니까?(Y/N)"
                read -r pathYn
                if [ 'Y' == ${pathYn^} ];then
                        docker exec --user $cmd_user "$container_nm" bash -c "InfraEye_eng stop"
                        docker exec --user $cmd_user "$container_nm" bash -c "rm -rf /opt/infraeye/nms/bin"
                        docker exec --user $cmd_user "$container_nm" bash -c "/usr/bin/cp -rf /docker_dir/patch/eng/bin /opt/infraeye/nms && chmod -R 755 /opt/infraeye/nms/bin && chown -R infraeye:infraeye /opt/infraeye/nms/bin"
                        docker exec --user root:root "$container_nm" bash -c "/opt/infraeye/nms/bin/capabilities.sh add"
                        docker exec --user $cmd_user "$container_nm" bash -c "InfraEye_eng start"
                        _record_site_version "eng" "$(_read_patch_to_version "${INFRAEYE_PATCH_BASE_DIR}" 2>/dev/null || true)"
                fi
        elif [ "$engPathType" -eq 2 ]; then

                echo "1. 부분 패치시 로컬서버의 /opt/infraeye/patch/eng  디렉터리 안에 모든 파일이 컨테이너의 /opt/infraeye/nms/bin/ 으로 복사됩니다. (이름이 동일한 파일은 전부 덮어씌웁니다.)"
                echo "2. 엔진 교체시 해당 엔진을 반드시 재시작해주세요."

                echo "패치를 진행 하시겠습니까?(Y/N)"
                read -r pathYn
                if [ 'Y' == ${pathYn^} ];then
                        #docker exec --user $cmd_user "$container_nm" bash -c "/usr/bin/cp -rf /docker_dir/patch/eng/* /opt/infraeye/nms/bin/ && chmod -R 755 /opt/infraeye/nms/bin && chown -R infraeye:infraeye /opt/infraeye/nms/bin"
                        docker exec --user $cmd_user "$container_nm" bash -c "rsync -av --exclude 'bin' /docker_dir/patch/eng/ /opt/infraeye/nms/bin/ && chmod -R 755 /opt/infraeye/nms/bin && chown -R infraeye:infraeye /opt/infraeye/nms/bin"
                        docker exec --user root:root "$container_nm" bash -c "/opt/infraeye/nms/bin/capabilities.sh add"
                        _record_site_version "eng" "$(_read_patch_to_version "${INFRAEYE_PATCH_BASE_DIR}" 2>/dev/null || true)"
                fi

        else
                echo "1 ~ 2 중에 선택해주세요."
        fi
}

# =============================================================================
# DB 패치 (Release Manager 1.0.0+ 신규 로직으로 교체)
# - 레거시는 컨테이너 내부 db_query_exec.sh를 호출했으나,
#   1.0.0부터는 Release Manager가 생성한 호스트 스크립트 실행으로 변경.
# - 백업/시계열 경고는 각 *_patch.sh 내부에서 수행.
# - 여기서는 디렉토리 탐색 + 사전 안내 + 실행만 담당.
# =============================================================================
function db_patch()
{
    # 레거시 was patch 와 동일한 패턴:
    #   {InfraEye설치경로}/patch 아래에서 mariadb_patch.sh / cratedb_patch.sh 를 재귀 탐색.
    #   - 0개: 에러 종료
    #   - 1개: 해당 파일 실행
    #   - 2개 이상: 모호하므로 경고 후 종료 (사용자가 정리 필요)
    # 우선순위: INFRAEYE_PATCH_DIR 환경변수 > 기본값(${INSTALL_PATH}/infraeye/patch)
    local patch_base="${INFRAEYE_PATCH_DIR:-$INFRAEYE_PATCH_BASE_DIR}"
    local db_choice=""

    # 비대화 모드 인자 파싱
    for arg in "$@"; do
        case "$arg" in
            --db=mariadb|--db=MARIADB) db_choice=1 ;;
            --db=cratedb|--db=CRATEDB) db_choice=2 ;;
        esac
    done

    echo "=========================================="
    echo "  InfraEye DB Patch"
    echo "=========================================="
    echo "[주의] NMC_DB 및 CrateDB는 시계열 데이터로 데이터 백업이 수행되지 않습니다."
    echo "       패치 탐색 경로: ${patch_base}/ (재귀 탐색)"
    echo "       백업 저장 경로: ${INFRAEYE_BACKUP_DIR}/<timestamp>/"
    echo ""

    if [ ! -d "$patch_base" ]; then
        echo "[ERROR] DB 패치 베이스 디렉토리가 존재하지 않습니다: $patch_base"
        echo "        패치 파일을 ${INFRAEYE_PATCH_BASE_DIR}/ 아래에 복사해주세요."
        exit 1
    fi

    if [ -z "$db_choice" ]; then
        echo -e "1. DB 패치\n2. CrateDB 패치"
        read -r db_choice
    fi

    case "$db_choice" in
        1) _find_and_run_db_script "$patch_base" "mariadb_patch.sh" "MariaDB" ;;
        2) _find_and_run_db_script "$patch_base" "cratedb_patch.sh" "CrateDB" ;;
        *) echo "1 ~ 2 중에 선택해주세요."; exit 1 ;;
    esac
}

# {base_dir}/ 아래에서 {script_name} 을 재귀 탐색.
# - 0개: 에러 종료
# - 1개: 해당 스크립트 실행 (exec)
# - 2개 이상: 모호하므로 발견된 목록 출력 후 종료
function _find_and_run_db_script()
{
    local base_dir="$1"
    local script_name="$2"
    local label="$3"

    # ${base_dir}/backup/ 은 이전 패치 보관 영역으로 간주하여 제외 (cli/was patch 와 통일)
    local backup_dir="${base_dir}/backup"
    local matches=()
    while IFS= read -r -d '' f; do
        matches+=("$f")
    done < <(find "$base_dir" -path "$backup_dir" -prune -o -type f -name "$script_name" -print0 2>/dev/null)

    local count=${#matches[@]}

    if [ "$count" -eq 0 ]; then
        echo "[ERROR] ${label} 패치 스크립트(${script_name})를 찾을 수 없습니다."
        echo "        탐색 경로: ${base_dir}/ (재귀)"
        echo "        패치 파일을 ${INFRAEYE_PATCH_BASE_DIR}/ 아래에 복사해주세요."
        exit 1
    fi

    if [ "$count" -ge 2 ]; then
        echo "[ERROR] ${label} 패치 스크립트(${script_name})가 2개 이상 발견되어 실행 대상을 결정할 수 없습니다."
        echo "        탐색 경로: ${base_dir}/ (재귀)"
        echo "        발견된 파일 (${count}개):"
        local f
        for f in "${matches[@]}"; do
            echo "          - $f"
        done
        echo "        하나만 남기고 다른 파일은 제거해주세요."
        exit 1
    fi

    local script="${matches[0]}"
    if [ ! -x "$script" ]; then
        echo "[WARN] 실행 권한이 없어 chmod +x 적용: $script"
        chmod +x "$script"
    fi

    # 버전 레지스트리/백업 디렉토리 준비 (*_patch.sh 가 사용)
    mkdir -p "$INFRAEYE_VERSION_DIR" "$INFRAEYE_BACKUP_DIR"
    export INFRAEYE_VERSION_DIR INFRAEYE_BACKUP_DIR INFRAEYE_DATA_DIR

    echo "[InfraEye] ${label} 패치 실행: $script"
    exec "$script"
}

# =============================================================================
# 웹 패치 (Release Manager 1.0.0+ 신규 로직으로 교체)
# - 레거시는 /docker_dir/patch/was/*.war + /docker_dir/patch/was/webobjects/ 고정 경로 사용.
# - 1.0.0부터는 ${INFRAEYE_PATCH_BASE_DIR}/ 아래에서 .war 파일을 재귀 탐색 (backup/ 제외).
#   .war 가 정확히 1개 발견되면 그 디렉토리를 패치 소스로 사용.
#   .war 와 같은 디렉토리의 .tar.gz 를 webobjects 패치 소스로 사용 (압축 해제 후 적용).
# - 인터랙티브 메뉴/백업/server.xml 갱신/WAS 재시작 등 흐름은 레거시 그대로 유지.
# =============================================================================
function web_patch()
{
        # --- 신규: .war 파일 재귀 탐색 (backup/ 제외) ---
        local host_patch_base="${INFRAEYE_PATCH_BASE_DIR}"
        local host_backup_dir="${INFRAEYE_PATCH_BASE_DIR}/backup"

        if [ ! -d "$host_patch_base" ]; then
                echo "[ERROR] WAS 패치 베이스 디렉토리가 존재하지 않습니다: $host_patch_base"
                echo "        패치 파일을 ${host_patch_base}/ 아래에 복사해주세요."
                exit 1
        fi

        local war_matches=()
        while IFS= read -r -d '' f; do
                war_matches+=("$f")
        done < <(find "$host_patch_base" -path "$host_backup_dir" -prune -o -type f -name "*.war" -print0 2>/dev/null)

        local war_count=${#war_matches[@]}

        if [ "$war_count" -eq 0 ]; then
                echo "[ERROR] WAS 패치 대상 .war 파일을 찾을 수 없습니다."
                echo "        탐색 경로: ${host_patch_base}/ (재귀, backup/ 제외)"
                echo "        패치 파일을 ${host_patch_base}/ 아래에 복사해주세요."
                exit 1
        fi

        if [ "$war_count" -ge 2 ]; then
                echo "[ERROR] .war 파일이 2개 이상 발견되어 패치 대상을 결정할 수 없습니다."
                echo "        탐색 경로: ${host_patch_base}/ (재귀, backup/ 제외)"
                echo "        발견된 파일 (${war_count}개):"
                local m
                for m in "${war_matches[@]}"; do
                        echo "          - $m"
                done
                echo "        하나만 남기고 다른 파일은 제거해주세요."
                exit 1
        fi

        # 1개 발견: 해당 .war 의 디렉토리를 패치 소스로 결정
        local war_file_host="${war_matches[0]}"
        local patch_src_host_dir="$(dirname "$war_file_host")"
        local war_file_name="$(basename "$war_file_host")"
        # 호스트 → 컨테이너 경로 매핑 (/opt/infraeye/patch -> /docker_dir/patch)
        local patch_src_ctn_dir="/docker_dir/patch${patch_src_host_dir#$INFRAEYE_PATCH_BASE_DIR}"

        # 같은 디렉토리에서 .tar.gz 파일 탐색 (webobjects 패치용)
        local tar_files=()
        local f
        for f in "$patch_src_host_dir"/*.tar.gz; do
                [ -f "$f" ] && tar_files+=("$f")
        done
        local tar_count=${#tar_files[@]}
        local tar_file_host=""
        local tar_available="N"
        if [ "$tar_count" -eq 1 ]; then
                tar_file_host="${tar_files[0]}"
                tar_available="Y"
        elif [ "$tar_count" -ge 2 ]; then
                echo "[WARN] ${patch_src_host_dir} 에 .tar.gz 파일이 ${tar_count}개 있어 webobjects 패치 대상을 결정할 수 없습니다."
                echo "       발견된 .tar.gz:"
                for f in "${tar_files[@]}"; do
                        echo "         - $f"
                done
                echo "       webobjects 관련 옵션(1, 3) 은 사용할 수 없습니다."
        fi

        echo "[InfraEye] WAS 패치 소스 디렉토리: $patch_src_host_dir"
        echo "[InfraEye]   - .war 파일: $war_file_name"
        if [ "$tar_available" = "Y" ]; then
                echo "[InfraEye]   - webobjects .tar.gz: $(basename "$tar_file_host")"
        fi

        # ------ 이하 레거시 인터랙티브 흐름 유지 ------
        echo -e "1. 전체패치( war, webobjects )\n2. war 패치 \n3. webobjects 패치"
        read -r wabPathType

        echo -e "컨테이너 내부의 war를 백업하시겠습니까? (Y/N)  \nbackup file path : /opt/infraeye/data/backup/WAS"
        read -r warBackYn

        echo -e "컨테이너 내부의 webobjects 를 백업하시겠습니까? (Y/N)  \nbackup file path : /opt/infraeye/data/backup/WAS"
        read -r webobjectsBackYn


        oldWarFileNm=$(docker exec --user $cmd_user "$container_nm" bash -c "ls /opt/infraeye/nms/webapps |grep war")
        newWarFileNm="$war_file_name"

        #war 백업
        if [ 'Y' == ${warBackYn^} ];then
                local dirYn=$(docker exec "$container_nm" bash -c "test -d /opt/infraeye/data/backup/WAS && echo 'Y' || echo 'N'")
                if [ 'N' == ${dirYn^} ];then
                        docker exec --user $cmd_user "$container_nm" bash -c "mkdir -p /opt/infraeye/data/backup/WAS"
                fi

                docker exec --user $cmd_user "$container_nm" bash -c "tar -cvf /opt/infraeye/data/backup/WAS/$date-$oldWarFileNm.tar.gz -C /opt/infraeye/nms/webapps $oldWarFileNm"

                echo "war 백업 완료"
        fi
        #webobject 백업
        if [ 'Y' == ${webobjectsBackYn^} ];then
                local dirYn=$(docker exec "$container_nm" bash -c "test -d /opt/infraeye/data/backup/WAS && echo 'Y' || echo 'N'")
                if [ 'N' == ${dirYn^} ];then
                        docker exec --user $cmd_user "$container_nm" bash -c "mkdir -p /opt/infraeye/data/backup/WAS"
                fi

                docker exec --user $cmd_user "$container_nm" bash -c "tar -cvf /opt/infraeye/data/backup/WAS/$date-webobjects.tar.gz -C /opt/infraeye/nms webobjects"

                echo "webobject 백업 완료"
        fi



        echo "1. 패치 진행시 패치 관련된 디렉터리가 덮어 씌워집니다.(백업 필수)"
        echo "2. war파일 및 webobjects.tar.gz 는 patch 경로 아래에 꼭 하나만 있어야 합니다."
        echo "3. was가 재시작 됩니다."

        echo "패치를 진행 하시겠습니까?(Y/N)"
        read -r pathYn
        if [ 'Y' != ${pathYn^} ];then
                exit
        fi

        #이전 컨테이너 webapps 의 war 가 2개 이상인 경우
        oldCount=$(echo -e "$oldWarFileNm" | wc -l)
        if [ "$oldCount" -ge 2 ]; then
                echo "기존 컨테이너의 war 파일 개수를 확인해주세요."
                exit 1
        fi

        if [ "$wabPathType" == 1 ] && [ "$tar_available" != "Y" ]; then
                echo -e "전체 패치를 하기 위한 webobjects.tar.gz 파일이 존재하지 않습니다."
                exit 1
        elif [ "$wabPathType" == 3 ] && [ "$tar_available" != "Y" ]; then
                echo -e "webobjects 패치를 진행하기 위한 webobjects.tar.gz 파일이 존재하지 않습니다."
                exit 1
        fi

        # webobjects .tar.gz 압축 해제 (옵션 1, 3 인 경우)
        local extract_dir_host=""
        local extract_dir_ctn=""
        if [ "$wabPathType" == 1 ] || [ "$wabPathType" == 3 ]; then
                if ! command -v tar >/dev/null 2>&1; then
                        echo "tar 명령어가 없어 webobjects .tar.gz 압축 해제를 진행할 수 없습니다."
                        exit 1
                fi
                extract_dir_host="$(mktemp -d "${patch_src_host_dir}/.webobjects_extract.XXXXXX")"
                if ! tar -xzf "$tar_file_host" -C "$extract_dir_host"; then
                        echo "webobjects .tar.gz 압축 해제 실패: $tar_file_host"
                        rm -rf "$extract_dir_host"
                        exit 1
                fi
                # tar.gz 내부 구조 결정: webobjects/ 하위에만 모든 게 있으면 그 디렉토리를, 아니면 추출 dir 자체를 사용
                local extract_source_host="$extract_dir_host"
                if [ -d "$extract_dir_host/webobjects" ]; then
                        extract_source_host="$extract_dir_host/webobjects"
                fi
                extract_dir_ctn="/docker_dir/patch${extract_source_host#$INFRAEYE_PATCH_BASE_DIR}"
        fi

        docker exec --user root:root "$container_nm" bash -c "chmod -R 755 $patch_src_ctn_dir && chown -R infraeye:infraeye $patch_src_ctn_dir"
        docker exec --user $cmd_user "$container_nm" bash -c "InfraEye_was stop"
        #패치
        if [ "$wabPathType" == 1 ]; then
                if [ "$oldWarFileNm" != "$newWarFileNm" ]; then
                        oldWarFileNm=$(echo -e "$oldWarFileNm" | sed 's/.war$//')
                        newWarFileNm=$(echo -e "$newWarFileNm" | sed 's/.war$//')
                        echo "old war 파일과 new war 파일의 이름이 같지 않습니다. /opt/infraeye/was/nms-tomcat/conf/server.xml 설정파일을 변경하시겠습니까?(Y/N)  [ $oldWarFileNm -> $newWarFileNm ]"
                        read -r chgServerXml
                        if [ 'Y' == ${chgServerXml^} ];then
                                docker exec --user $cmd_user "$container_nm" bash -c "sed -i 's/docBase=\"$oldWarFileNm\"/docBase=\"$newWarFileNm\"/' /opt/infraeye/was/nms-tomcat/conf/server.xml"
                        fi
                fi
                docker exec --user $cmd_user "$container_nm" bash -c "rm -rf /opt/infraeye/nms/webapps/*.war /opt/infraeye/nms/webapps/ROOT"
                docker exec --user $cmd_user "$container_nm" bash -c "/usr/bin/cp -rf $patch_src_ctn_dir/$war_file_name /opt/infraeye/nms/webapps/ && chmod -R 755 /opt/infraeye/nms/webapps && chown -R infraeye:infraeye /opt/infraeye/nms/webapps"
                docker exec --user $cmd_user "$container_nm" bash -c "rm -rf /opt/infraeye/nms/webobjects && mkdir -p /opt/infraeye/nms/webobjects && /usr/bin/cp -rf $extract_dir_ctn/. /opt/infraeye/nms/webobjects/ && chmod -R 755 /opt/infraeye/nms/webobjects && chown -R infraeye:infraeye /opt/infraeye/nms/webobjects"

        elif [ "$wabPathType" == 2 ]; then
                if [ "$oldWarFileNm" != "$newWarFileNm" ]; then
                        oldWarFileNm=$(echo -e "$oldWarFileNm" | sed 's/.war$//')
                        newWarFileNm=$(echo -e "$newWarFileNm" | sed 's/.war$//')
                        echo "old war 파일과 new war 파일의 이름이 같지 않습니다. /opt/infraeye/was/nms-tomcat/conf/server.xml 설정파일을 변경하시겠습니까?(Y/N)  [ $oldWarFileNm -> $newWarFileNm ]"
                        read -r chgServerXml
                        if [ 'Y' == ${chgServerXml^} ];then
                                docker exec --user $cmd_user "$container_nm" bash -c "sed -i 's/docBase=\"$oldWarFileNm\"/docBase=\"$newWarFileNm\"/' /opt/infraeye/was/nms-tomcat/conf/server.xml"
                        fi
                fi

                docker exec --user $cmd_user "$container_nm" bash -c "rm -rf /opt/infraeye/nms/webapps/*.war /opt/infraeye/nms/webapps/ROOT"
                docker exec --user $cmd_user "$container_nm" bash -c "/usr/bin/cp -rf $patch_src_ctn_dir/$war_file_name /opt/infraeye/nms/webapps/ && chmod -R 755 /opt/infraeye/nms/webapps && chown -R infraeye:infraeye /opt/infraeye/nms/webapps"

        elif [ "$wabPathType" == 3 ]; then
                docker exec --user $cmd_user "$container_nm" bash -c "rm -rf /opt/infraeye/nms/webobjects && mkdir -p /opt/infraeye/nms/webobjects && /usr/bin/cp -rf $extract_dir_ctn/. /opt/infraeye/nms/webobjects/ && chmod -R 755 /opt/infraeye/nms/webobjects && chown -R infraeye:infraeye /opt/infraeye/nms/webobjects"
        else
                echo "1 ~ 3 중에 선택해주세요."
        fi

        # 임시 추출 디렉토리 정리
        if [ -n "$extract_dir_host" ] && [ -d "$extract_dir_host" ]; then
                rm -rf "$extract_dir_host"
        fi

        docker exec --user $cmd_user "$container_nm" bash -c "InfraEye_was start"
        _record_site_version "was" "$(_read_patch_to_version "$patch_src_host_dir" 2>/dev/null || true)"
}



#컨테이너 선택 (레거시 그대로 유지 - 현재 호출되지 않음)
function select_docker_container()
{

        #컨테이너 name에는 공백이 있을수 있음
        local container_ids=($(docker ps -q))

        local container_names=($(docker ps --format "{{.Names}}"))
        local container_count=${#container_ids[@]}

        # 컨테이너가 한개일때
        if [ "$container_count" == 1 ]; then
                container_nm="${container_names[0]}"
        else
                echo "실행 중인 Docker 컨테이너 목록:"
                docker ps --format "table {{.ID}}\t{{.Names}}" | awk 'BEGIN{printf "%-4s%-15s%-15s\n", "  ", "CONTAINER ID", "NAMES"} NR>1{printf "%-4s%s\n", NR-1, $0}'

                echo "선택할 컨테이너의 번호를 입력하세요:"
                read -r selection

                # 입력 값이 유효한 숫자인지 확인
                if [[ $selection =~ ^[0-9]+$ ]] && [ "$selection" -ge 1 ] && [ "$selection" -le "$container_count" ]; then
                        # 유효한 선택인 경우 컨테이너 이름을 리턴
                        container_nm="${container_names[$((selection - 1))]}"
                else
                        # 유효하지 않은 선택인 경우 스크립트 종료
                        echo "존재하지 않는 컨테이너입니다."
                        exit 1
                fi
        fi
}


# get_info (레거시 + VERSION 분기 추가)
function get_info()
{
        local cmd=$1
        if [ "${cmd^^}" == "PATH" ]; then
                echo "$INSTALL_PATH"
        elif [ "${cmd^^}" == "CTI_NM" ]; then
                echo "$NMS_CONTAINER_NM"
        elif [ "${cmd^^}" == "VERSION" ]; then
                _show_version
        fi

        exit
}


# =============================================================================
# 엔트리 포인트
# =============================================================================

# --version / --help 가로채기 (Release Manager 1.0.0+)
case "${1:-}" in
    --version|-v) _show_version; exit 0 ;;
    --help|-h)    _show_help;    exit 0 ;;
esac

exec_type=$1
cmd=$2
eng_nm=$3
container_nm="$NMS_CONTAINER_NM"
cmd_user="infraeye:infraeye"
date=$(date +"%Y%m%d%H%M%S")


#컨테이너 선택
#select_docker_container

if [ "${cmd^^}" == "PATCH" ]; then

        case "${exec_type^^}" in
                "ENG")
                        eng_patch
                        ;;
                "WAS")
                        web_patch
                        ;;
                "DB")
                        db_patch "${@:3}"
                        ;;
                "CLI")
                        cli_patch "${@:3}"
                        ;;
                *)
                        echo "eng, was, db, cli 중 선택해주세요."
                        ;;
        esac


else

        #$을 전달하게 될경우 정상적으로 처리하지 못함으로 \ 추가
        args=("${@:2}")
        for ((i=0; i<${#args[@]}; i++)); do
                if [[ "${args[$i]}" == -* ]]; then
                        continue
                fi

                args[$i]=$(echo "${args[$i]}" | sed 's/\$/\\$/g')
        done

#       echo "${args[@]}"

        case "${exec_type^^}" in
                "ENG")
                        docker exec --user $cmd_user "$container_nm" bash -c "InfraEye_eng $(echo  ${args[@]})"
                        ;;
                "WAS")
                        docker exec --user $cmd_user "$container_nm" bash -c "InfraEye_was $(echo ${args[@]})"
                        ;;
                "DB")
                        docker exec --user $cmd_user "$container_nm" bash -c "InfraEye_db $(echo ${args[@]})"
                        ;;
                "INTO")
                        docker exec -it --user $cmd_user "$container_nm" bash
                        ;;
                "INFO")
                        get_info $cmd
                        ;;
                *)
                        echo "존재하지 않는 명령어입니다."
                        ;;

        esac
fi
