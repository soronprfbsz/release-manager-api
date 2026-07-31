package com.ts.rm.global.exception;

import com.ts.rm.global.response.ApiResponse;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.io.EOFException;
import org.apache.catalina.connector.ClientAbortException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final MessageSource messageSource;

  // 비즈니스 예외 (자동 status 분류: fail/error, 국제화 지원)
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e,
      Locale locale) {
    ErrorCode errorCode = e.getErrorCode();
    String responseStatus = errorCode.getResponseStatus();

    // 커스텀 메시지인지 messageKey인지 판단
    String message;
    if (e.getMessage() != null && e.getMessage().equals(errorCode.getMessageKey())) {
      // messageKey → MessageSource에서 국제화된 메시지 조회
      message = messageSource.getMessage(errorCode.getMessageKey(), null, locale);
    } else {
      // 커스텀 메시지 → 그대로 사용
      message = e.getMessage();
    }

    // ErrorCode의 HttpStatus에 따라 fail(4xx) 또는 error(5xx) 자동 분류
    if ("fail".equals(responseStatus)) {
      log.warn("Business error (client): [{}] {} - {}", errorCode.getCode(), errorCode.name(),
          message, e);
      return ResponseEntity.status(errorCode.getStatus())
          .body(ApiResponse.fail(errorCode.getCode(), message));
    } else {
      log.error("Business error (server): [{}] {} - {}", errorCode.getCode(), errorCode.name(),
          message, e);
      return ResponseEntity.status(errorCode.getStatus())
          .body(ApiResponse.error(errorCode.getCode(), message));
    }
  }

  // Validation 에러 (클라이언트 에러, 국제화 지원)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<ApiResponse.FailDetail>> handleValidationException(
      MethodArgumentNotValidException e, Locale locale) {
    Map<String, String> errors = new HashMap<>();
    e.getBindingResult().getAllErrors().forEach(error -> {
      String fieldName = ((FieldError) error).getField();
      String errorMessage = error.getDefaultMessage();
      errors.put(fieldName, errorMessage);
    });

    String message =
        messageSource.getMessage(ErrorCode.INVALID_INPUT_VALUE.getMessageKey(), null, locale);

    log.error("Validation error: [{}] {} - Fields: {}", ErrorCode.INVALID_INPUT_VALUE.getCode(),
        message, errors, e);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getCode(), message, errors));
  }

  // HTTP 메서드 에러 (클라이언트 에러, 국제화 지원)
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<ApiResponse.FailDetail>> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException e, Locale locale) {
    String message =
        messageSource.getMessage(ErrorCode.METHOD_NOT_ALLOWED.getMessageKey(), null, locale);

    log.error("Method not allowed: [{}] {} - Supported methods: {}",
        ErrorCode.METHOD_NOT_ALLOWED.getCode(), message, e.getSupportedHttpMethods(), e);

    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED.getCode(), message));
  }

  // 매핑되지 않은 API 경로 (클라이언트 에러, 국제화 지원)
  // 핸들러가 없으면 Spring 이 정적 리소스 조회로 넘겨 NoResourceFoundException 을 던진다.
  // 이를 잡지 않으면 handleInternalError 로 떨어져 500 이 되고, 프론트/백엔드 경로 불일치가
  // '서버 오류' 로 위장된다(실제로 /site-versions ↔ /versions 불일치를 8일간 못 찾은 원인).
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<ApiResponse.FailDetail>> handleNoResourceFound(
      NoResourceFoundException e, Locale locale) {
    String message =
        messageSource.getMessage(ErrorCode.ENDPOINT_NOT_FOUND.getMessageKey(), null, locale);

    log.warn("No endpoint found: [{}] {} {}", ErrorCode.ENDPOINT_NOT_FOUND.getCode(),
        e.getHttpMethod(), e.getResourcePath());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.fail(ErrorCode.ENDPOINT_NOT_FOUND.getCode(), message));
  }

  // 인증 실패 (잘못된 인증 정보)
  @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
  public ResponseEntity<ApiResponse<?>> handleBadCredentials(
      org.springframework.security.authentication.BadCredentialsException e, Locale locale) {
    String message =
        messageSource.getMessage(ErrorCode.INVALID_CREDENTIALS.getMessageKey(), null, locale);

    log.warn("Authentication failed: [{}] {} - {}", ErrorCode.INVALID_CREDENTIALS.getCode(),
        message, e.getMessage());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.fail(ErrorCode.INVALID_CREDENTIALS.getCode(), message));
  }

  // 잘못된 인자 (이메일 중복 등)
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException e,
      Locale locale) {
    log.warn("Illegal argument: {}", e.getMessage());

    // 메시지에 '이메일'이 포함되면 이메일 중복 에러로 처리
    if (e.getMessage() != null && e.getMessage().contains("이메일")) {
      String message =
          messageSource.getMessage(ErrorCode.DUPLICATE_EMAIL.getMessageKey(), null, locale);
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ApiResponse.fail(ErrorCode.DUPLICATE_EMAIL.getCode(), message));
    }

    // 일반 잘못된 입력으로 처리
    String message =
        messageSource.getMessage(ErrorCode.INVALID_INPUT_VALUE.getMessageKey(), null, locale);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getCode(), message));
  }

  // 데이터 무결성 위반
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<?>> handleDataIntegrityViolation(
      DataIntegrityViolationException e, Locale locale) {
    String rootCauseMessage = e.getMostSpecificCause().getMessage();
    log.warn("Data integrity violation: {}", rootCauseMessage);

    // FK 제약조건 위반 (삭제 시 참조 데이터 존재)
    if (rootCauseMessage != null && rootCauseMessage.contains("foreign key constraint fails")) {
      String message =
          messageSource.getMessage(ErrorCode.REFERENCED_DATA_EXISTS.getMessageKey(), null, locale);
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ApiResponse.fail(ErrorCode.REFERENCED_DATA_EXISTS.getCode(), message));
    }

    // 그 외 무결성 위반은 일반 서버 오류로 처리
    String message =
        messageSource.getMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessageKey(), null, locale);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), message));
  }

  // Path Variable 또는 Request Parameter 타입 불일치 (클라이언트 에러)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<?>> handleTypeMismatch(
      MethodArgumentTypeMismatchException e, Locale locale) {
    String paramName = e.getName();
    String invalidValue = e.getValue() != null ? e.getValue().toString() : "null";

    log.warn("Type mismatch: parameter '{}' = '{}' (expected type: {})",
        paramName, invalidValue, e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown");

    String message =
        messageSource.getMessage(ErrorCode.INVALID_INPUT_VALUE.getMessageKey(), null, locale);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getCode(),
            message + " (파라미터: " + paramName + ", 값: " + invalidValue + ")"));
  }

  // 필수 Request Parameter 누락 (클라이언트 에러)
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<?>> handleMissingParam(
      MissingServletRequestParameterException e, Locale locale) {
    String paramName = e.getParameterName();
    String paramType = e.getParameterType();

    log.warn("Missing required parameter: '{}' (type: {})", paramName, paramType);

    String message =
        messageSource.getMessage(ErrorCode.INVALID_INPUT_VALUE.getMessageKey(), null, locale);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getCode(),
            message + " (필수 파라미터 누락: " + paramName + ")"));
  }

  // 필수 Request Header 누락 (클라이언트 에러)
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiResponse<?>> handleMissingHeader(
      MissingRequestHeaderException e, Locale locale) {
    String headerName = e.getHeaderName();

    log.warn("Missing required header: '{}'", headerName);

    String message =
        messageSource.getMessage(ErrorCode.INVALID_INPUT_VALUE.getMessageKey(), null, locale);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getCode(),
            message + " (필수 헤더 누락: " + headerName + ")"));
  }

  // Multipart 요청 파싱 에러 (파일 업로드 중 클라이언트 취소 등)
  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ApiResponse<?>> handleMultipartException(MultipartException e,
      Locale locale) {
    // 클라이언트가 업로드를 취소한 경우 (ClientAbortException, EOFException)
    if (isClientAbortException(e)) {
      log.info("클라이언트가 파일 업로드를 취소했습니다");
      // 클라이언트가 이미 연결을 끊었으므로 응답을 보내지 않음
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
    }

    // 그 외 Multipart 에러 (파일 크기 초과 등)
    log.warn("Multipart 요청 처리 실패: {}", e.getMessage());

    String message =
        messageSource.getMessage(ErrorCode.INVALID_INPUT_VALUE.getMessageKey(), null, locale);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getCode(),
            message + " (파일 업로드 실패)"));
  }

  /**
   * 클라이언트 연결 끊김 예외인지 확인
   *
   * <p>브라우저에서 업로드/다운로드 취소 시 발생하는 예외를 감지합니다.
   *
   * @param e 확인할 예외
   * @return 클라이언트 연결 끊김 여부
   */
  private boolean isClientAbortException(Throwable e) {
    Throwable current = e;
    while (current != null) {
      // Tomcat ClientAbortException 확인
      if (current instanceof ClientAbortException) {
        return true;
      }
      // EOFException 확인 (업로드 중 클라이언트 연결 끊김)
      if (current instanceof EOFException) {
        return true;
      }
      // 에러 메시지로 확인
      String message = current.getMessage();
      if (message != null && (message.contains("Connection reset by peer")
              || message.contains("Broken pipe")
              || message.contains("EOFException")
              || message.contains("클라이언트가 연결을 끊었습니다"))) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  // 예상치 못한 서버 에러 (국제화 지원)
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<ApiResponse.ErrorDetail>> handleInternalError(Exception e,
      Locale locale) {
    // 클라이언트가 연결을 끊은 경우 (업로드/다운로드 취소)
    if (isClientAbortException(e)) {
      log.info("클라이언트가 요청을 취소했습니다 (연결 끊김)");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
    }

    log.error("Unexpected error occurred", e);

    String message =
        messageSource.getMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessageKey(), null, locale);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), message));
  }
}
