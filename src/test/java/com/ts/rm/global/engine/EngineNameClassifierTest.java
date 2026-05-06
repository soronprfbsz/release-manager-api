package com.ts.rm.global.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * EngineNameClassifier 단위 테스트 (TDD)
 */
@DisplayName("EngineNameClassifier 단위 테스트")
class EngineNameClassifierTest {

    // ---------------------------------------------------------------
    // 화이트리스트 케이스 (SubCategoryValidator ENGINE 목록 ∩ 확장자 없음)
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "화이트리스트 엔진 \"{0}\" → true")
    @ValueSource(strings = {"NC_CONF", "NC_SMS", "OZ_CCTV", "NC_AP", "NC_SNMP"})
    @DisplayName("화이트리스트 엔진명은 isEngineFile=true")
    void whitelistedEngines_returnTrue(String name) {
        assertThat(EngineNameClassifier.isEngineFile(name)).isTrue();
    }

    @Test
    @DisplayName("소문자로 전달된 화이트리스트 엔진명도 대소문자 무시 후 true")
    void lowercaseWhitelisted_returnTrue() {
        assertThat(EngineNameClassifier.isEngineFile("nc_conf")).isTrue();
        assertThat(EngineNameClassifier.isEngineFile("nc_sms")).isTrue();
    }

    // ---------------------------------------------------------------
    // prefix 휴리스틱 (화이트리스트 외, NC_* / OZ_* 대문자 prefix)
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "prefix 휴리스틱 \"{0}\" → true")
    @ValueSource(strings = {"NC_NEWBIE", "NC_CUSTOM_V2", "OZ_NEW_ENGINE"})
    @DisplayName("화이트리스트 외 NC_* / OZ_* 대문자 prefix 는 isEngineFile=true")
    void prefixHeuristic_returnTrue(String name) {
        assertThat(EngineNameClassifier.isEngineFile(name)).isTrue();
    }

    // ---------------------------------------------------------------
    // 확장자 보유 → 공유 자산 (false)
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "확장자 있는 파일 \"{0}\" → false (공유 자산)")
    @ValueSource(strings = {
        "NC_CONF.conf",
        "nc_sms.conf",
        "NMS_COMMON.conf",
        "NC_NEWBIE.sh",
        "OZ_CCTV.properties",
        "nc_conf.conf"
    })
    @DisplayName("확장자가 있는 파일은 공유 자산으로 분류 → isEngineFile=false")
    void filesWithExtension_returnFalse(String name) {
        assertThat(EngineNameClassifier.isEngineFile(name)).isFalse();
    }

    // ---------------------------------------------------------------
    // prefix 외 파일 → false
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "prefix 미해당 \"{0}\" → false")
    @ValueSource(strings = {
        "random_helper",
        "MARIADB",
        "build_script",
        "nc_lowercase"   // 소문자 prefix → false (prefix 비교는 대문자만)
    })
    @DisplayName("NC_*/OZ_* 대문자 prefix 외 + 화이트리스트 미포함 파일은 isEngineFile=false")
    void nonPrefixFiles_returnFalse(String name) {
        // "nc_lowercase" 는 소문자 prefix 이므로 prefix 조건을 통과하지 못함.
        // 단, 화이트리스트 대소문자 무시 검색에서도 히트하지 않으면 false.
        // (nc_lowercase 는 화이트리스트에 없음)
        assertThat(EngineNameClassifier.isEngineFile(name)).isFalse();
    }

    @Test
    @DisplayName("ETC 는 ENGINE 화이트리스트 포함이므로 isEngineFile=true (엔진명으로 처리)")
    void etc_isInWhitelist_returnTrue() {
        // SubCategoryValidator.ALLOWED_SUBCATEGORIES 의 ENGINE set 에 "ETC" 포함
        assertThat(EngineNameClassifier.isEngineFile("ETC")).isTrue();
    }

    // ---------------------------------------------------------------
    // 경계 값
    // ---------------------------------------------------------------

    @Test
    @DisplayName("null 입력은 false")
    void null_returnFalse() {
        assertThat(EngineNameClassifier.isEngineFile(null)).isFalse();
    }

    @Test
    @DisplayName("빈 문자열 입력은 false")
    void blank_returnFalse() {
        assertThat(EngineNameClassifier.isEngineFile("")).isFalse();
        assertThat(EngineNameClassifier.isEngineFile("   ")).isFalse();
    }

    @Test
    @DisplayName("ENGINE_NAMING_RULE 상수가 null 이 아님")
    void engineNamingRule_notNull() {
        assertThat(EngineNameClassifier.ENGINE_NAMING_RULE).isNotNull().isNotBlank();
    }
}
