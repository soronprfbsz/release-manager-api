package com.ts.rm.domain.message.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 메시지 유형
 *
 * <p>발신 주체가 사람이냐 시스템이냐만 구분한다. 저장 구조와 읽음/배지 처리는
 * 유형과 무관하게 동일하다 (ADR-0003).
 */
@Getter
@RequiredArgsConstructor
@Schema(description = "메시지 유형")
public enum MessageType {

    /** 사용자가 직접 보낸 쪽지 */
    @Schema(description = "사용자 쪽지")
    USER("사용자 쪽지"),

    /** 패치 자동삭제 예정 독촉 (시스템 발신) */
    @Schema(description = "패치 처리 독촉")
    PATCH_REMINDER("패치 처리 독촉"),

    /** 미인증 사용자의 비밀번호 재설정 요청 (시스템 발신) */
    @Schema(description = "비밀번호 재설정 요청")
    PASSWORD_RESET_REQUEST("비밀번호 재설정 요청"),

    /** 신규 가입자의 권한·부서 배치 요청 (시스템 발신) */
    @Schema(description = "가입 처리 요청")
    SIGNUP_APPROVAL_REQUEST("가입 처리 요청");

    private final String description;
}
