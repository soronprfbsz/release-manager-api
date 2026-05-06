package com.ts.rm.global.engine;

import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.util.SubCategoryValidator;

/**
 * 엔진 파일명 식별 유틸리티.
 *
 * <p>engine/ 디렉토리 안의 파일이 "엔진 바이너리" 인지 "공유 자산" 인지 판단한다.
 *
 * <p>판단 기준:
 * <ol>
 *   <li>확장자({@code .} 포함 문자열) 가 있으면 → 공유 자산 ({@code false})</li>
 *   <li>확장자 없고 {@link SubCategoryValidator}의 ENGINE 화이트리스트에 있으면 → 엔진 ({@code true})</li>
 *   <li>확장자 없고 대문자 {@code NC_} / {@code OZ_} prefix 이면 → 엔진 ({@code true})</li>
 *   <li>그 외 → 공유 자산 ({@code false})</li>
 * </ol>
 */
public final class EngineNameClassifier {

    /**
     * picker UI 에 표시할 엔진 식별 규칙 안내 문자열.
     */
    public static final String ENGINE_NAMING_RULE =
            "엔진 후보는 SubCategoryValidator 화이트리스트 ∪ NC_*/OZ_* prefix (확장자 있는 파일은 공유 자산으로 분류)";

    private EngineNameClassifier() {}

    /**
     * 주어진 파일명이 "엔진 바이너리" 인지 판단한다.
     *
     * @param fileName engine/ 디렉토리 직속 파일명
     * @return 엔진 바이너리이면 {@code true}, 공유 자산이면 {@code false}
     */
    public static boolean isEngineFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        // 확장자 보유 시 공유 자산
        if (fileName.contains(".")) {
            return false;
        }
        // 화이트리스트 (대소문자 무시)
        if (SubCategoryValidator.isValid(FileCategory.ENGINE, fileName.toUpperCase())) {
            return true;
        }
        // prefix 휴리스틱 — 대문자 NC_* / OZ_* 만 허용 (소문자 prefix 는 공유 자산)
        return fileName.startsWith("NC_") || fileName.startsWith("OZ_");
    }
}
