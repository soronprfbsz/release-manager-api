package com.ts.rm.global.file;

import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 빌드 ZIP 파일 루트 디렉토리 검증 유틸리티.
 *
 * <p>빌드 ZIP 파일의 루트 1단계 디렉토리는 {@code web/}, {@code engine/} 만 허용된다.
 * 그 외 디렉토리나 루트 파일이 발견되면 {@link BusinessException} 을 던진다.
 * (운영자 자산은 release version 의 ETC ReleaseFile 로 등록 → patch 의 etc/{version}/* 으로 자연 포함)
 *
 * <p>비교는 <b>대소문자 구분</b>으로 수행한다 (예: {@code Web/} 은 거부). 표준 디렉토리명을
 * 강제하기 위함이다.
 *
 * <p>사용 예시:
 * <pre>{@code
 * byte[] zipBytes = multipartFile.getBytes();
 * BuildZipValidator.validate(zipBytes);  // 통과 시 진행, 실패 시 예외
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BuildZipValidator {

    /**
     * 허용된 루트 디렉토리 (대소문자 구분).
     */
    public static final Set<String> ALLOWED_ROOT_DIRECTORIES = Set.of("web", "engine");

    /**
     * ZIP 바이트 배열을 검증한다.
     *
     * <p>다음 조건을 검사:
     * <ul>
     *   <li>최소 1개 이상의 엔트리가 있어야 함</li>
     *   <li>모든 엔트리의 루트 1단계가 {@code web/}, {@code engine/} 중 하나여야 함</li>
     *   <li>루트(슬래시 없는) 위치의 파일은 거부</li>
     * </ul>
     *
     * @param zipBytes ZIP 파일 바이트 배열
     * @throws BusinessException 검증 실패 시
     */
    public static void validate(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "ZIP 파일이 비어있습니다.");
        }
        validateStream(new ByteArrayInputStream(zipBytes));
    }

    /**
     * Path 기반 ZIP 검증 (대용량 ZIP 메모리 안전 버전).
     *
     * <p>{@link #validate(byte[])} 는 입력 전체가 힙에 올라가야 하므로 수백 MB 이상의 ZIP 에서
     * OOM 위험이 있다. 본 메서드는 파일을 스트리밍하여 검증하므로 ZIP 크기에 무관하게 동작한다.
     *
     * @param zipFile ZIP 파일 경로
     * @throws BusinessException 검증 실패 시
     */
    public static void validate(Path zipFile) {
        if (zipFile == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "ZIP 파일이 비어있습니다.");
        }
        long size;
        try {
            size = Files.size(zipFile);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "ZIP 파일 크기 조회 실패: " + e.getMessage());
        }
        if (size == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "ZIP 파일이 비어있습니다.");
        }

        try (InputStream is = Files.newInputStream(zipFile)) {
            validateStream(is);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "ZIP 파일 읽기 실패: " + e.getMessage());
        }
    }

    /**
     * 임의의 ZIP 입력 스트림에 대해 루트 디렉토리 화이트리스트 검증을 수행하는 공통 핵심.
     */
    private static void validateStream(InputStream inputStream) {
        Set<String> invalidRootEntries = new LinkedHashSet<>();
        boolean hasAnyContent = false;

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    zis.closeEntry();
                    continue;
                }

                hasAnyContent = true;

                // ZIP 내부에서 디렉토리 구분자는 항상 슬래시 (백슬래시 사용 ZIP 도 방어)
                String normalized = name.replace('\\', '/');
                int firstSlash = normalized.indexOf('/');

                if (firstSlash < 0) {
                    // 루트의 파일 (서브디렉토리 없음) → 거부
                    invalidRootEntries.add(normalized);
                } else {
                    String top = normalized.substring(0, firstSlash);
                    if (!ALLOWED_ROOT_DIRECTORIES.contains(top)) {
                        invalidRootEntries.add(top + "/");
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "ZIP 파일 읽기 실패: " + e.getMessage());
        }

        if (!hasAnyContent) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "ZIP 파일이 비어있습니다.");
        }

        if (!invalidRootEntries.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format(
                            "빌드 ZIP 루트는 web/, engine/ 만 허용됩니다. 허용되지 않은 항목: %s",
                            String.join(", ", invalidRootEntries)));
        }
    }
}
