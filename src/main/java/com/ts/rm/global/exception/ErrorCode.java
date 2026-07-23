package com.ts.rm.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 관리 - FAIL: 클라이언트 에러 (4xx) - 잘못된 요청, 인증/인가 실패 등 - ERROR: 서버 에러 (5xx) - 서버 내부
 * 처리 오류 - 메시지는 messages_{locale}.properties에서 국제화 지원
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // ========================================
  // FAIL - 클라이언트 에러 (4xx)
  // ========================================

  // Common - 공통
  INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "error.common.invalid_input"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C003", "error.common.method_not_allowed"),
  DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "error.common.data_not_found"),
  DATA_CONFLICT(HttpStatus.CONFLICT, "C005", "error.common.data_conflict"),
  REFERENCED_DATA_EXISTS(HttpStatus.CONFLICT, "C006", "error.common.referenced_data_exists"),

  // Account - 계정
  ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "A001", "error.account.not_found"),
  ACCOUNT_EMAIL_CONFLICT(HttpStatus.CONFLICT, "A002", "error.account.email_conflict"),
  LAST_ADMIN_CANNOT_DELETE(HttpStatus.BAD_REQUEST, "A003", "error.account.last_admin_cannot_delete"),
  INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "A004", "error.account.invalid_current_password"),
  PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "A005", "error.account.password_same_as_current"),
  PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "A006", "error.account.password_policy_violation"),
  CANNOT_RESET_SELF(HttpStatus.BAD_REQUEST, "A007", "error.account.cannot_reset_self"),

  // Auth - 인증
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AU001", "error.auth.invalid_credentials"),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AU002", "error.auth.duplicate_email"),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AU003", "error.auth.invalid_refresh_token"),
  EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AU004", "error.auth.expired_refresh_token"),
  REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "AU005", "error.auth.refresh_token_not_found"),
  INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AU006", "error.auth.invalid_access_token"),
  EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AU007", "error.auth.expired_access_token"),
  ACCESS_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AU008", "error.auth.access_token_not_found"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "AU009", "error.auth.forbidden"),
  ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "AU010", "error.auth.account_suspended"),
  ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "AU011", "error.auth.account_inactive"),
  ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "AU012", "error.auth.account_locked"),

  // Site - 사이트
  SITE_NOT_FOUND(HttpStatus.NOT_FOUND, "ST001", "error.site.not_found"),
  SITE_CODE_CONFLICT(HttpStatus.CONFLICT, "ST002", "error.site.code_conflict"),
  SITE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "ST003", "error.site.id_required"),

  // Engineer - 엔지니어
  ENGINEER_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "error.engineer.not_found"),
  ENGINEER_EMAIL_CONFLICT(HttpStatus.CONFLICT, "E002", "error.engineer.email_conflict"),

  // Department - 부서
  DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "error.department.not_found"),
  DEPARTMENT_NAME_CONFLICT(HttpStatus.CONFLICT, "D002", "error.department.name_conflict"),
  DEPARTMENT_HAS_CHILDREN(HttpStatus.CONFLICT, "D003", "error.department.has_children"),
  DEPARTMENT_HAS_ACCOUNTS(HttpStatus.CONFLICT, "D004", "error.department.has_accounts"),
  DEPARTMENT_CANNOT_MOVE_TO_DESCENDANT(HttpStatus.BAD_REQUEST, "D005", "error.department.cannot_move_to_descendant"),
  DEPARTMENT_ROOT_CANNOT_DELETE(HttpStatus.BAD_REQUEST, "D006", "error.department.root_cannot_delete"),

  // Project - 프로젝트
  PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "error.project.not_found"),
  PROJECT_ID_CONFLICT(HttpStatus.CONFLICT, "P002", "error.project.id_conflict"),
  PROJECT_ID_REQUIRED(HttpStatus.BAD_REQUEST, "P003", "error.project.id_required"),

  // Release - 릴리즈
  RELEASE_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "error.release.type_not_found"),
  RELEASE_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "R002", "error.release.version_not_found"),
  RELEASE_VERSION_CONFLICT(HttpStatus.CONFLICT, "R003", "error.release.version_conflict"),
  INVALID_VERSION_FORMAT(HttpStatus.BAD_REQUEST, "R004", "error.release.invalid_version_format"),
  DATABASE_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "R005", "error.release.database_type_not_found"),
  PATCH_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "R006", "error.release.patch_file_not_found"),
  INVALID_HOTFIX_PARENT(HttpStatus.BAD_REQUEST, "R007", "error.release.invalid_hotfix_parent"),

  // Patch - 누적 패치
  PATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "PA001", "error.patch.not_found"),
  INVALID_PATCH_FOLDER_NAME(HttpStatus.BAD_REQUEST, "PA002", "error.patch.invalid_folder_name"),

  // Resource - 리소스 파일
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RF001", "error.resource.not_found"),
  DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "RF002", "error.resource.duplicate"),
  FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "RF003", "error.resource.file_not_found"),
  FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "RF004", "error.resource.upload_failed"),
  FILE_DOWNLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "RF005", "error.resource.download_failed"),
  FILE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "RF006", "error.resource.delete_failed"),

  // SSH - SSH 연결 및 실행
  SSH_CONNECTION_FAILED(HttpStatus.BAD_GATEWAY, "SSH001", "error.ssh.connection_failed"),
  SSH_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "SSH002", "error.ssh.authentication_failed"),
  SSH_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SSH003", "error.ssh.execution_failed"),
  SSH_CHANNEL_OPEN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SSH004", "error.ssh.channel_open_failed"),
  SSH_IO_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SSH005", "error.ssh.io_error"),
  SSH_CHANNEL_NOT_CONNECTED(HttpStatus.BAD_REQUEST, "SSH006", "error.ssh.channel_not_connected"),

  // Terminal - 웹 터미널
  TERMINAL_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "error.terminal.session_not_found"),
  TERMINAL_NOT_CONNECTED(HttpStatus.BAD_REQUEST, "T002", "error.terminal.not_connected"),
  TERMINAL_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "T003", "error.terminal.execution_failed"),

  // ========================================
  // ERROR - 서버 에러 (5xx)
  // ========================================

  // Common - 공통
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002",
      "error.common.internal_server");

  private final HttpStatus status;
  private final String code;
  private final String messageKey; // 국제화 메시지 키

  /**
   * HTTP 상태 코드를 기반으로 응답 status 타입 반환
   *
   * @return "fail" (4xx 클라이언트 에러) 또는 "error" (5xx 서버 에러)
   */
  public String getResponseStatus() {
    if (status.is4xxClientError()) {
      return "fail";
    } else if (status.is5xxServerError()) {
      return "error";
    }
    return "error"; // 기본값
  }
}
