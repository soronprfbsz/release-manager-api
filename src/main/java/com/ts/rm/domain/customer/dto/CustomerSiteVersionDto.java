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
     * 패치 생성 폼 자동 채우기용 다음 패치 범위 응답
     *
     * <p>suggestedFrom* / suggestedTo* 는 적합한 버전이 없으면 null 반환:
     * <ul>
     *   <li>suggestedFrom* null: 사이트가 이미 최신 버전이거나 등록된 버전이 없음</li>
     *   <li>suggestedTo* null: 프로젝트에 승인된 표준 버전이 하나도 없음</li>
     * </ul>
     */
    @Schema(description = "다음 패치 범위 자동 제안 응답")
    public record NextPatchRangeResponse(
            @Schema(description = "사이트 현재 패치 base 버전 (customer_project.last_patched_version 기반). "
                    + "아직 패치 적용 이력 없으면 null", example = "1.1.0", nullable = true)
            String currentVersion,

            @Schema(description = "제안 from 버전 (현재 버전 직후 base). "
                    + "사이트가 최신이거나 버전 없으면 null", example = "1.2.0", nullable = true)
            String suggestedFromVersion,

            @Schema(description = "제안 from 버전 ID", example = "5", nullable = true)
            Long suggestedFromVersionId,

            @Schema(description = "제안 to 버전 (프로젝트 최신 승인 표준 base 버전). "
                    + "버전이 하나도 없으면 null", example = "1.3.0", nullable = true)
            String suggestedToVersion,

            @Schema(description = "제안 to 버전 ID", example = "12", nullable = true)
            Long suggestedToVersionId
    ) {
    }

    /**
     * 사이트 컴포넌트 버전 단건 응답
     */
    @Schema(description = "사이트 컴포넌트 현재 버전")
    public record SiteVersionResponse(
            @Schema(description = "컴포넌트 구분", example = "BASE",
                    allowableValues = {"BASE", "WEB", "ENGINE"})
            String component,

            @Schema(description = "엔진명 (component=ENGINE 일 때만; BASE/WEB 은 null)",
                    example = "NC_AGENT_SERVER", nullable = true)
            String engineName,

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
