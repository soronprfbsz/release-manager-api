package com.ts.rm.global.file;

import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ZIP 파일 압축 해제 유틸리티
 *
 * <p>한글 파일명 인코딩 문제를 해결하기 위해 UTF-8 → MS949 fallback 전략 적용
 *
 * <ul>
 *   <li>UTF-8: macOS/Linux에서 생성된 ZIP 파일
 *   <li>MS949: Windows 탐색기에서 생성된 한글 파일명 ZIP 파일
 * </ul>
 *
 * <p>사용 예시:
 * <pre>{@code
 * // 기본 압축 해제
 * List<ExtractedFileInfo> files = ZipExtractUtil.extract(zipFile.getInputStream(), targetDir);
 *
 * // 커스텀 처리가 필요한 경우
 * ZipExtractUtil.extractWithCallback(zipFile.getInputStream(), targetDir, (entry, filePath) -> {
 *     // 파일별 추가 처리
 * });
 * }</pre>
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ZipExtractUtil {

    private static final Charset MS949 = Charset.forName("MS949");

    /**
     * ZIP 파일을 지정된 디렉토리에 압축 해제 (Path 기반 스트리밍).
     *
     * <p>대용량 ZIP(수백 MB ~ GB)의 경우 {@link #extract(InputStream, Path)} 는 charset 재시도용으로
     * 입력을 byte 배열에 통째로 버퍼링하여 OOM 위험이 있다. 본 메서드는 Path 로부터 두 번
     * 다시 열어 UTF-8 → MS949 재시도하므로 힙 사용량이 ZIP 크기에 의존하지 않는다.
     *
     * @param zipFile   ZIP 파일 경로
     * @param targetDir 압축 해제 대상 디렉토리
     * @return 압축 해제된 파일 정보 목록
     */
    public static List<ExtractedFileInfo> extract(Path zipFile, Path targetDir) {
        List<ExtractedFileInfo> result = new ArrayList<>();
        extractWithCallback(zipFile, targetDir, (entry, filePath) -> {
            try {
                long fileSize = Files.size(filePath);
                result.add(new ExtractedFileInfo(
                        filePath.getFileName().toString(),
                        entry.getName(),
                        filePath,
                        fileSize
                ));
            } catch (IOException e) {
                log.warn("파일 크기 조회 실패: {}", filePath, e);
                result.add(new ExtractedFileInfo(
                        filePath.getFileName().toString(),
                        entry.getName(),
                        filePath,
                        0L
                ));
            }
        });
        return result;
    }

    /**
     * Path 기반 스트리밍 압축 해제 + 콜백.
     *
     * @param zipFile      ZIP 파일 경로
     * @param targetDir    대상 디렉토리
     * @param fileCallback 각 파일 압축 해제 후 호출되는 콜백
     */
    public static void extractWithCallback(
            Path zipFile, Path targetDir, BiConsumer<ZipEntry, Path> fileCallback) {

        // 1차: UTF-8
        try (InputStream is = Files.newInputStream(zipFile)) {
            extractStreamWithCharset(is, targetDir, StandardCharsets.UTF_8, fileCallback);
            return;
        } catch (BusinessException e) {
            if (!isMalformedInputError(e)) throw e;
            log.info("UTF-8 인코딩 실패, MS949로 재시도합니다.");
        } catch (IllegalArgumentException e) {
            if (!isMalformedInputError(e)) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                        "ZIP 파일 압축 해제 실패: " + e.getMessage());
            }
            log.info("UTF-8 인코딩 실패 (IllegalArgumentException), MS949로 재시도합니다.");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                    "ZIP 파일 읽기 실패: " + e.getMessage());
        }

        // 2차: MS949 (스트림 새로 열기 — byte[] 버퍼 없이 재시도)
        try (InputStream is = Files.newInputStream(zipFile)) {
            extractStreamWithCharset(is, targetDir, MS949, fileCallback);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                    "ZIP 파일 압축 해제 실패 (인코딩 오류): " + e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                    "ZIP 파일 읽기 실패: " + e.getMessage());
        }
    }

    /**
     * ZIP 파일을 지정된 디렉토리에 압축 해제
     *
     * @param inputStream ZIP 파일 입력 스트림
     * @param targetDir   압축 해제할 대상 디렉토리
     * @return 압축 해제된 파일 정보 목록
     * @throws BusinessException 압축 해제 실패 시
     */
    public static List<ExtractedFileInfo> extract(InputStream inputStream, Path targetDir) {
        List<ExtractedFileInfo> result = new ArrayList<>();
        extractWithCallback(inputStream, targetDir, (entry, filePath) -> {
            try {
                long fileSize = Files.size(filePath);
                result.add(new ExtractedFileInfo(
                        filePath.getFileName().toString(),
                        entry.getName(),
                        filePath,
                        fileSize
                ));
            } catch (IOException e) {
                log.warn("파일 크기 조회 실패: {}", filePath, e);
                result.add(new ExtractedFileInfo(
                        filePath.getFileName().toString(),
                        entry.getName(),
                        filePath,
                        0L
                ));
            }
        });
        return result;
    }

    /**
     * ZIP 파일을 압축 해제하면서 각 파일에 대해 콜백 실행
     *
     * @param inputStream  ZIP 파일 입력 스트림
     * @param targetDir    압축 해제할 대상 디렉토리
     * @param fileCallback 각 파일 압축 해제 후 실행할 콜백 (ZipEntry, Path)
     * @throws BusinessException 압축 해제 실패 시
     */
    public static void extractWithCallback(
            InputStream inputStream, Path targetDir, BiConsumer<ZipEntry, Path> fileCallback) {

        // 입력 스트림을 재사용하기 위해 바이트 배열로 복사
        byte[] zipBytes;
        try {
            zipBytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                    "ZIP 파일 읽기 실패: " + e.getMessage());
        }

        // UTF-8로 먼저 시도
        try {
            extractWithCharset(zipBytes, targetDir, StandardCharsets.UTF_8, fileCallback);
            return;
        } catch (BusinessException e) {
            if (isMalformedInputError(e)) {
                log.info("UTF-8 인코딩 실패, MS949로 재시도합니다.");
            } else {
                throw e;
            }
        } catch (IllegalArgumentException e) {
            if (isMalformedInputError(e)) {
                log.info("UTF-8 인코딩 실패 (IllegalArgumentException), MS949로 재시도합니다.");
            } else {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                        "ZIP 파일 압축 해제 실패: " + e.getMessage());
            }
        }

        // MS949로 재시도
        try {
            extractWithCharset(zipBytes, targetDir, MS949, fileCallback);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                    "ZIP 파일 압축 해제 실패 (인코딩 오류): " + e.getMessage());
        }
    }

    /**
     * 지정된 문자셋으로 ZIP 파일 압축 해제 (byte[] 입력)
     */
    private static void extractWithCharset(
            byte[] zipBytes, Path targetDir, Charset charset, BiConsumer<ZipEntry, Path> fileCallback) {
        extractStreamWithCharset(
                new java.io.ByteArrayInputStream(zipBytes), targetDir, charset, fileCallback);
    }

    /**
     * 지정된 문자셋으로 ZIP 입력 스트림을 디렉토리에 압축 해제 (공통 핵심 루프).
     *
     * <p>byte[] / Path / 임의의 InputStream 모두 본 헬퍼를 호출하여 동일한 Zip Slip 방어,
     * 디렉토리 처리, 콜백 invocation 동작을 공유한다.
     */
    private static void extractStreamWithCharset(
            InputStream inputStream, Path targetDir, Charset charset,
            BiConsumer<ZipEntry, Path> fileCallback) {

        try (ZipInputStream zis = new ZipInputStream(inputStream, charset)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // 경로 탐색 공격 방지 (Zip Slip)
                Path targetPath = targetDir.resolve(entryName).normalize();
                if (!targetPath.startsWith(targetDir)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                            "유효하지 않은 ZIP 파일 경로입니다: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);

                    if (fileCallback != null) {
                        fileCallback.accept(entry, targetPath);
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                    "ZIP 파일 압축 해제 실패: " + e.getMessage());
        }
    }

    /**
     * 인코딩 오류인지 확인
     */
    private static boolean isMalformedInputError(Exception e) {
        String message = e.getMessage();
        return message != null && message.contains("malformed input");
    }

    /**
     * 압축 해제된 파일 정보
     *
     * @param fileName     파일명
     * @param relativePath ZIP 내부 상대 경로
     * @param absolutePath 압축 해제된 절대 경로
     * @param fileSize     파일 크기 (bytes)
     */
    public record ExtractedFileInfo(
            String fileName,
            String relativePath,
            Path absolutePath,
            long fileSize
    ) {
    }
}
