package com.ts.rm.domain.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * CustomerSiteVersion DTO 통합 클래스
 *
 * <p>사이트별 컴포넌트 현재 버전 조회 응답
 */
public final class CustomerSiteVersionDto {

    private CustomerSiteVersionDto() {
    }

    /**
     * 사이트 컴포넌트 버전 단건 응답
     */
    @Schema(description = "사이트 컴포넌트 현재 버전")
    public record SiteVersionResponse(
            @Schema(description = "컴포넌트 구분", example = "BASE",
                    allowableValues = {"BASE", "WEB", "ENGINE"})
            String component,

            @Schema(description = "현재 버전 (BASE: 1.1.0 / WEB·ENGINE: 1.1.0.260511-1)",
                    example = "1.1.0.260511-1")
            String currentVersion,

            @Schema(description = "최종 갱신 일시 (패치 완료 시점)")
            LocalDateTime updatedAt,

            @Schema(description = "최종 갱신자 이메일", example = "admin@tscientific.com")
            String updatedBy
    ) {
    }
}
