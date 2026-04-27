package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PatchGenerationService 의 입력 버전 파싱 로직 단위 테스트.
 *
 * <p>{@link PatchGenerationService#parseInputVersion(String)} 의 동작만 검증.
 * 빌드 인식 패치 생성 전체 흐름은 통합 테스트에서 확인 (사전 부채 정리 후).
 */
@DisplayName("PatchGenerationService.parseInputVersion 테스트")
class PatchGenerationServiceVersionLogicTest {

    @Test
    @DisplayName("표준 일반 버전 파싱 - '1.1.0' → base='1.1.0', build=0")
    void parse_standardPlain() {
        var p = PatchGenerationService.parseInputVersion("1.1.0");
        assertThat(p.baseVersionString()).isEqualTo("1.1.0");
        assertThat(p.buildVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("표준 빌드 버전 파싱 - '1.1.0.260427' → base='1.1.0', build=260427")
    void parse_standardWithBuild() {
        var p = PatchGenerationService.parseInputVersion("1.1.0.260427");
        assertThat(p.baseVersionString()).isEqualTo("1.1.0");
        assertThat(p.buildVersion()).isEqualTo(260427);
    }

    @Test
    @DisplayName("커스텀 일반 버전 파싱 - '1.1.0-companyA.1.0.0' → base 그대로, build=0")
    void parse_customPlain() {
        var p = PatchGenerationService.parseInputVersion("1.1.0-companyA.1.0.0");
        assertThat(p.baseVersionString()).isEqualTo("1.1.0-companyA.1.0.0");
        assertThat(p.buildVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("커스텀 빌드 버전 파싱 - '1.1.0-companyA.1.0.0.260427' → base + build 분리")
    void parse_customWithBuild() {
        var p = PatchGenerationService.parseInputVersion("1.1.0-companyA.1.0.0.260427");
        assertThat(p.baseVersionString()).isEqualTo("1.1.0-companyA.1.0.0");
        assertThat(p.buildVersion()).isEqualTo(260427);
    }

    @Test
    @DisplayName("형식 오류 - 빈 문자열 거부")
    void parse_blank_rejected() {
        assertThatThrownBy(() -> PatchGenerationService.parseInputVersion(""))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_VERSION_FORMAT);
    }

    @Test
    @DisplayName("형식 오류 - null 거부")
    void parse_null_rejected() {
        assertThatThrownBy(() -> PatchGenerationService.parseInputVersion(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_VERSION_FORMAT);
    }

    @Test
    @DisplayName("형식 오류 - 표준 2파트 ('1.1') 거부")
    void parse_standard2Parts_rejected() {
        assertThatThrownBy(() -> PatchGenerationService.parseInputVersion("1.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_VERSION_FORMAT);
    }

    @Test
    @DisplayName("형식 오류 - 표준 5파트 ('1.1.0.1.1') 거부")
    void parse_standard5Parts_rejected() {
        assertThatThrownBy(() -> PatchGenerationService.parseInputVersion("1.1.0.1.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_VERSION_FORMAT);
    }

    @Test
    @DisplayName("형식 오류 - 숫자가 아닌 빌드 ('1.1.0.abc') 거부")
    void parse_nonNumericBuild_rejected() {
        assertThatThrownBy(() -> PatchGenerationService.parseInputVersion("1.1.0.abc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_VERSION_FORMAT);
    }
}
