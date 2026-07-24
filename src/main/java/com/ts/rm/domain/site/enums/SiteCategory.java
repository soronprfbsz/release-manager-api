package com.ts.rm.domain.site.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사이트 구분 Enum - 좌측 트리 그룹핑 기준
 */
@Schema(description = "사이트 구분")
public enum SiteCategory {
    @Schema(description = "고객사 (실제 납품·운영)")
    CUSTOMER,

    @Schema(description = "내부 테스트 (demo·사내 운영서버)")
    INTERNAL_TEST
}
