package com.ts.rm.global.engine;

import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.util.SubCategoryValidator;
import java.util.Set;

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
 *
 * <p>특별 케이스(디렉토리형 엔진): {@link #DIRECTORY_FORM_ENGINE_NAMES} 화이트리스트의
 * 이름을 가진 디렉토리(예: {@code engine/NC_AGENT_SERVER/})는 파일이 아닌 트리 단위로
 * 한 엔진을 구성한다. 그 외 디렉토리는 기존처럼 엔진 후보로 인식하지 않는다.
 */
public final class EngineNameClassifier {

    /**
     * picker UI 에 표시할 엔진 식별 규칙 안내 문자열.
     */
    public static final String ENGINE_NAMING_RULE =
            "엔진 후보는 SubCategoryValidator 화이트리스트 ∪ NC_*/OZ_* prefix (확장자 있는 파일은 공유 자산으로 분류)";

    /**
     * 디렉토리 단위로 한 엔진을 구성하는 특별 케이스 엔진명 화이트리스트.
     *
     * <p>여기 등록된 이름의 디렉토리만 {@code engine/} 직속 후보로 인식한다.
     * 신규 디렉토리형 엔진을 추가할 때만 본 Set 에 한 항목 추가한다.
     */
    public static final Set<String> DIRECTORY_FORM_ENGINE_NAMES = Set.of("NC_AGENT_SERVER");

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
