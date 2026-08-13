package com.ts.rm.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 비밀번호 재설정 요청 DTO (미인증 공개 API)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "비밀번호 재설정 요청")
public class PasswordResetRequest {

    @Schema(description = "요청자 이메일", example = "user@example.com")
    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 50, message = "이메일은 최대 50자까지 입력 가능합니다.")
    private String email;

    @Schema(description = "담당자에게 남길 메모", example = "내선 1234로 연락 주세요")
    @Size(max = 500, message = "메모는 최대 500자까지 입력 가능합니다.")
    private String memo;

    @Schema(description = "요청을 받을 담당자 계정 ID 목록", example = "[1, 2]")
    @NotEmpty(message = "요청할 담당자를 1명 이상 선택해주세요.")
    @Size(max = 20, message = "담당자는 최대 20명까지 선택 가능합니다.")
    private List<@NotNull Long> recipientAccountIds;
}
