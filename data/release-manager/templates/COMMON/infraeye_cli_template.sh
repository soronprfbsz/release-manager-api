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

function _show_cli_patch_help()
{
    cat <<EOF
사용법: sudo ./InfraEye cli patch [OPTIONS]

옵션:
  --install-path=<경로>      INSTALL_PATH 값을 명시적으로 지정
  --nms-container=<이름>     NMS_CONTAINER_NM 값을 명시적으로 지정
  --force                    동일 버전이어도 강제 재설치
  --rollback                 /usr/bin/InfraEye.legacy-bak 을 /usr/bin/InfraEye 로 복원

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

        # --- 자기 자신 경로 ---
        local self
        self="$(readlink -f "${BASH_SOURCE[0]}")"
        local target_real=""
        if [[ -e "$TARGET" ]]; then
            target_real="$(readlink -f "$TARGET" 2>/dev/null || echo "")"
        fi

        # --- self == target 방어 (이미 설치된 CLI에서 cli patch 재호출) ---
        if [[ "$self" == "$target_real" && $force -eq 0 ]]; then
            echo "[cli-patch] 이미 설치된 CLI에서 cli patch를 호출했습니다. 의미 없음. (--force 로 강제 가능)"
            exit 0
        fi

        [[ -r "$self" ]] || { echo "[cli-patch] 설치 원본 없음: $self" >&2; exit 1; }

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

# --version / info version 공용 출력
function _show_version()
{
    local mdb cdb
    mdb=$(_read_site_version "mariadb")
    cdb=$(_read_site_version "cratedb")
    cat <<EOF
InfraEye CLI ${INFRAEYE_CLI_VERSION} (Release Manager edition)
  MariaDB: ${mdb}
  CrateDB: ${cdb}
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
  1) $INFRAEYE_PATCH_DIR 환경변수
  2) 현재 작업 디렉토리 ($PWD)

예:
  sudo ./InfraEye cli patch                         # 최초 1회 CLI 설치
  cd /path/to/patch-1.0.0 && InfraEye db patch
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
    local patch_dir="${INFRAEYE_PATCH_DIR:-$PWD}"
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
    echo "[주의] NMC_DB 및 CrateDB는 시계열 데이터로 전체 데이터 백업이 수행되지 않습니다."
    echo "       복구 필요 시 수집 시스템을 통한 재수집이 필요합니다."
    echo "       백업 저장 경로: ${INFRAEYE_BACKUP_DIR}/<timestamp>/"
    echo ""

    if [ -z "$db_choice" ]; then
        echo -e "1. DB 패치\n2. CrateDB 패치"
        read -r db_choice
    fi

    case "$db_choice" in
        1) _run_db_script "$patch_dir/mariadb_patch.sh" "MariaDB" ;;
        2) _run_db_script "$patch_dir/cratedb_patch.sh" "CrateDB" ;;
        *) echo "1 ~ 2 중에 선택해주세요."; exit 1 ;;
    esac
}

function _run_db_script()
{
    local script="$1"
    local label="$2"

    if [ ! -f "$script" ]; then
        echo "[ERROR] ${label} 패치 스크립트를 찾을 수 없습니다: $script"
        echo "  - 패치 디렉토리에서 'InfraEye db patch'를 실행하거나"
        echo "  - INFRAEYE_PATCH_DIR 환경변수로 경로를 지정하세요."
        exit 1
    fi
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
# 웹 패치 (레거시 그대로 유지)
# =============================================================================
function web_patch()
{
        echo -e "1. 전체패치( war, webobjects )\n2. war 패치 \n3. webobjects 패치"
        read -r wabPathType

        echo -e "컨테이너 내부의 war를 백업하시겠습니까? (Y/N)  \nbackup file path : /opt/infraeye/data/backup/WAS"
        read -r warBackYn

        echo -e "컨테이너 내부의 webobjects 를 백업하시겠습니까? (Y/N)  \nbackup file path : /opt/infraeye/data/backup/WAS"
        read -r webobjectsBackYn


        oldWarFileNm=$(docker exec --user $cmd_user "$container_nm" bash -c "ls /opt/infraeye/nms/webapps |grep war")
        newWarFileNm=$(docker exec --user $cmd_user "$container_nm" bash -c "ls /docker_dir/patch/was/ |grep war")

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
        echo "2. war파일 및 webobjects 디렉토리는 patch 경로에 꼭 하나만 있어야 합니다."
        echo "3. was가 재시작 됩니다."

        echo "패치를 진행 하시겠습니까?(Y/N)"
        read -r pathYn
        if [ 'Y' != ${pathYn^} ];then
                exit
        fi

        #패치파일 확인
        wasTest=$(docker exec "$container_nm" bash -c "test -e /docker_dir/patch/was/*.war && echo 'Y' || echo 'N'")
        webobTest=$(docker exec "$container_nm" bash -c "test -d /docker_dir/patch/was//webobjects && echo 'Y' || echo 'N'")

        #war 파일이 2개 이상인 경우
        oldCount=$(echo -e "$oldWarFileNm" | wc -l)
        newCount=$(echo -e "$newWarFileNm" | wc -l)
        if [ "$oldCount" -ge 2 ] || [ "$newCount" -ge 2 ]; then
            echo "war 파일의 개수를 확인해주세요."
            exit 1
        fi



        if [ "$wabPathType" == 1 ] && [ "$wasTest" != "Y" ] && [ "$webobTest" != "Y" ]; then
                echo -e "전체 패치를 하기 위한 war파일이나 webobject디렉터리가 존재하지 않습니다."
                exit 1
        elif [ "$wabPathType" == 2 ] && [ "$wasTest" != "Y" ]; then
                echo $wabPathType
                echo $wasTest
                echo -e "war 패치를 진행하기 위한 war파일이 존재하지 않습니다."
                exit 1
        elif [ "$wabPathType" == 3 ] && [ "$webobTest" != "Y" ]; then
                echo -e "webobject 패치를 진행하기 위한 webobject디렉터리가 존재하지 않습니다."
                exit 1
        fi

        docker exec --user root:root "$container_nm" bash -c "chmod -R 755 /docker_dir/patch/was && chown -R infraeye:infraeye /docker_dir/patch/was"
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
                docker exec --user $cmd_user "$container_nm" bash -c "/usr/bin/cp -rf /docker_dir/patch/was/*.war /opt/infraeye/nms/webapps/ && chmod -R 755 /opt/infraeye/nms/webapps && chown -R infraeye:infraeye /opt/infraeye/nms/webapps"
                docker exec --user $cmd_user "$container_nm" bash -c "/usr/bin/cp -rf /docker_dir/patch/was/webobjects /opt/infraeye/nms/ && chmod -R 755 /opt/infraeye/nms/webobjects && chown -R infraeye:infraeye /opt/infraeye/nms/webobjects"

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
                docker exec --user $cmd_user "$container_nm" bash -c "/usr/bin/cp -rf /docker_dir/patch/was/*.war /opt/infraeye/nms/webapps/ && chmod -R 755 /opt/infraeye/nms/webapps && chown -R infraeye:infraeye /opt/infraeye/nms/webapps"

        elif [ "$wabPathType" == 3 ]; then
                docker exec --user $cmd_user "$container_nm" bash -c "/usr/bin/cp -rf /docker_dir/patch/was/webobjects /opt/infraeye/nms/ && chmod -R 755 /opt/infraeye/nms/webobjects && chown -R infraeye:infraeye /opt/infraeye/nms/webobjects"
        else
                echo "1 ~ 3 중에 선택해주세요."
        fi

        docker exec --user $cmd_user "$container_nm" bash -c "InfraEye_was start"
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
