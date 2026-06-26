package com.ts.rm.global.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 임시 비밀번호 생성 유틸리티
 *
 * <p>비밀번호 초기화 시 시스템이 생성하는 1회성 랜덤 비밀번호를 만든다.
 *
 * <p>생성 규칙 (PRD-001 §9):
 * <ul>
 *   <li>{@link SecureRandom} 사용 (암호학적 난수)</li>
 *   <li>길이 12자</li>
 *   <li>혼동 문자 제외: {@code 0 O o 1 l I}</li>
 *   <li>영문 대문자 / 소문자 / 숫자 / 안전 특수문자 각 최소 1개 보장</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PasswordGenerator {

    /** 임시 비밀번호 길이 */
    private static final int LENGTH = 12;

    // 혼동 문자(0 O o 1 l I) 제외 문자 집합
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";   // I, O 제외
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";   // l, o 제외
    private static final String DIGIT = "23456789";                   // 0, 1 제외
    private static final String SPECIAL = "@#$%*?";                   // 안전 특수문자

    private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 임시 비밀번호 생성 (평문)
     *
     * <p>반환된 평문은 절대 저장하지 말고, BCrypt 해시 후 응답으로만 1회 노출한다.
     * 로그에도 남기지 않는다.
     *
     * @return 12자 임시 비밀번호 (각 문자 종류 최소 1개 포함)
     */
    public static String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);

        // 각 문자 종류 최소 1개 보장
        chars.add(randomChar(UPPER));
        chars.add(randomChar(LOWER));
        chars.add(randomChar(DIGIT));
        chars.add(randomChar(SPECIAL));

        // 나머지 자리는 전체 집합에서 채움
        for (int i = chars.size(); i < LENGTH; i++) {
            chars.add(randomChar(ALL));
        }

        // 종류 보장 문자가 앞쪽에 몰리지 않도록 셔플 (Fisher-Yates)
        for (int i = chars.size() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            Character tmp = chars.get(i);
            chars.set(i, chars.get(j));
            chars.set(j, tmp);
        }

        StringBuilder sb = new StringBuilder(LENGTH);
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static char randomChar(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
