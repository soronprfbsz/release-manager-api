package com.ts.rm.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * PasswordGenerator 단위 테스트 (PRD-001 §9)
 *
 * <p>임시 비밀번호 생성 규칙 검증:
 * <ul>
 *   <li>길이 12자</li>
 *   <li>영대/영소/숫자/안전 특수문자 각 최소 1개</li>
 *   <li>혼동 문자(0 O o 1 l I) 미포함</li>
 *   <li>호출마다 다른 값(난수성)</li>
 * </ul>
 */
@DisplayName("PasswordGenerator 테스트")
class PasswordGeneratorTest {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGIT = "23456789";
    private static final String SPECIAL = "@#$%*?";
    private static final String CONFUSING = "0Oo1lI";

    @RepeatedTest(50)
    @DisplayName("생성된 비밀번호 길이는 항상 12자")
    void generate_LengthIs12() {
        String password = PasswordGenerator.generate();

        assertThat(password).hasSize(12);
    }

    @RepeatedTest(50)
    @DisplayName("영대/영소/숫자/안전특수 각 최소 1개 포함")
    void generate_ContainsEachCharacterType() {
        String password = PasswordGenerator.generate();

        assertThat(containsAny(password, UPPER)).as("영문 대문자 포함").isTrue();
        assertThat(containsAny(password, LOWER)).as("영문 소문자 포함").isTrue();
        assertThat(containsAny(password, DIGIT)).as("숫자 포함").isTrue();
        assertThat(containsAny(password, SPECIAL)).as("안전 특수문자 포함").isTrue();
    }

    @RepeatedTest(50)
    @DisplayName("혼동 문자(0 O o 1 l I) 미포함")
    void generate_ExcludesConfusingCharacters() {
        String password = PasswordGenerator.generate();

        for (char c : CONFUSING.toCharArray()) {
            assertThat(password).as("혼동 문자 '%c' 미포함", c).doesNotContain(String.valueOf(c));
        }
    }

    @RepeatedTest(50)
    @DisplayName("허용된 문자 집합 밖의 문자는 없음")
    void generate_OnlyUsesAllowedCharacters() {
        String allowed = UPPER + LOWER + DIGIT + SPECIAL;
        String password = PasswordGenerator.generate();

        for (char c : password.toCharArray()) {
            assertThat(allowed.indexOf(c)).as("허용된 문자 '%c'", c).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("연속 생성 시 매번 다른 값(난수성) - 중복 없음")
    void generate_ProducesUniqueValues() {
        int count = 1000;
        Set<String> generated = new HashSet<>();

        for (int i = 0; i < count; i++) {
            generated.add(PasswordGenerator.generate());
        }

        assertThat(generated).hasSize(count);
    }

    private boolean containsAny(String value, String charset) {
        for (char c : value.toCharArray()) {
            if (charset.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }
}
