package com.ts.rm.global.file;

import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 파일 내용 조회 공통 유틸리티
 *
 * <p>파일 경로 검증, MIME 타입 판단, 텍스트/바이너리 분기 처리를 제공합니다.
 *
 * <p>사용 예시:
 * <pre>{@code
 * Path filePath = FileContentUtil.validateAndResolvePath(baseDir, "css/style.css");
 * FileContentResult result = FileContentUtil.readFileContent(filePath, 10 * 1024 * 1024);
 *
 * return new FileContentResponse(
 *     id,
 *     relativePath,
 *     result.fileName(),
 *     result.size(),
 *     result.mimeType(),
 *     result.isBinary(),
 *     result.content()
 * );
 * }</pre>
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileContentUtil {

    /** 기본 최대 파일 크기 (10MB) */
    public static final long DEFAULT_MAX_SIZE_BYTES = 10 * 1024 * 1024;

    /** 확장자 → MIME 타입 매핑 (Files.probeContentType 실패 시 fallback) */
    private static final Map<String, String> EXTENSION_MIME_MAP = Map.ofEntries(
            // 텍스트
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("log", "text/plain"),
            // 웹
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "text/javascript"),
            Map.entry("ts", "text/typescript"),
            Map.entry("jsx", "text/javascript"),
            Map.entry("tsx", "text/typescript"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("yaml", "application/yaml"),
            Map.entry("yml", "application/yaml"),
            // 프로그래밍 언어
            Map.entry("java", "text/x-java-source"),
            Map.entry("py", "text/x-python"),
            Map.entry("c", "text/x-c"),
            Map.entry("cpp", "text/x-c++"),
            Map.entry("h", "text/x-c"),
            Map.entry("hpp", "text/x-c++"),
            Map.entry("go", "text/x-go"),
            Map.entry("rs", "text/x-rust"),
            Map.entry("rb", "text/x-ruby"),
            Map.entry("php", "text/x-php"),
            Map.entry("kt", "text/x-kotlin"),
            Map.entry("swift", "text/x-swift"),
            Map.entry("scala", "text/x-scala"),
            // 스크립트/데이터
            Map.entry("sh", "text/x-shellscript"),
            Map.entry("bash", "text/x-shellscript"),
            Map.entry("bat", "text/x-batch"),
            Map.entry("cmd", "text/x-batch"),
            Map.entry("ps1", "text/x-powershell"),
            Map.entry("sql", "text/x-sql"),
            Map.entry("properties", "text/x-java-properties"),
            Map.entry("conf", "text/plain"),
            Map.entry("ini", "text/plain"),
            Map.entry("toml", "text/x-toml"),
            // 이미지
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            // 압축/아카이브
            Map.entry("zip", "application/zip"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("gz", "application/gzip"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("rar", "application/vnd.rar"),
            // 문서
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            // 실행 파일
            Map.entry("exe", "application/x-msdownload"),
            Map.entry("dll", "application/x-msdownload"),
            Map.entry("jar", "application/java-archive"),
            Map.entry("war", "application/java-archive"),
            // 설정 파일
            Map.entry("env", "text/plain"),
            Map.entry("gitignore", "text/plain"),
            Map.entry("dockerfile", "text/plain"),
            Map.entry("makefile", "text/plain")
    );

    /**
     * 파일 내용 조회 결과
     *
     * @param fileName 파일명
     * @param size     파일 크기 (bytes)
     * @param mimeType MIME 타입
     * @param isBinary 바이너리 파일 여부 (true면 content는 Base64 인코딩됨)
     * @param content  파일 내용 (텍스트 또는 Base64)
     */
    public record FileContentResult(
            String fileName,
            long size,
            String mimeType,
            boolean isBinary,
            String content
    ) {
    }

    /**
     * 파일 내용 조회 (기본 최대 크기: 10MB)
     *
     * @param filePath 파일 경로
     * @return 파일 내용 결과
     * @throws BusinessException 파일이 존재하지 않거나 읽기 실패 시
     */
    public static FileContentResult readFileContent(Path filePath) {
        return readFileContent(filePath, DEFAULT_MAX_SIZE_BYTES);
    }

    /**
     * 파일 내용 조회
     *
     * <p>텍스트 파일은 UTF-8 문자열로, 바이너리 파일은 Base64 인코딩하여 반환합니다.
     *
     * @param filePath     파일 경로
     * @param maxSizeBytes 최대 허용 파일 크기 (bytes)
     * @return 파일 내용 결과
     * @throws BusinessException 파일이 존재하지 않거나, 크기 초과 또는 읽기 실패 시
     */
    public static FileContentResult readFileContent(Path filePath, long maxSizeBytes) {
        // 파일 존재 확인
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND,
                    "파일을 찾을 수 없습니다: " + filePath.getFileName());
        }

        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "디렉토리는 조회할 수 없습니다: " + filePath.getFileName());
        }

        try {
            // 파일 크기 확인
            long fileSize = Files.size(filePath);
            if (fileSize > maxSizeBytes) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        String.format("파일 크기가 너무 큽니다 (최대 %dMB): %d bytes",
                                maxSizeBytes / (1024 * 1024), fileSize));
            }

            String fileName = filePath.getFileName().toString();

            // MIME 타입 확인 — Files.probeContentType 은 OS/JVM 환경 의존적이라
            // 예를 들어 .sh 가 환경에 따라 null 또는 application/x-shellscript 로 반환되어
            // 텍스트 파일이 binary 로 잘못 분류되는 문제가 있었다. getMimeType 헬퍼는
            // probeContentType 실패 시 EXTENSION_MIME_MAP fallback 으로 안정성을 보장한다.
            String mimeType = getMimeType(filePath);

            // 텍스트 파일 여부 판단
            boolean isBinary = !isTextMimeType(mimeType);

            String content;
            if (isBinary) {
                // 바이너리 파일: Base64 인코딩
                byte[] bytes = Files.readAllBytes(filePath);
                content = Base64.getEncoder().encodeToString(bytes);
            } else {
                // 텍스트 파일: UTF-8로 읽기
                content = Files.readString(filePath);
            }

            return new FileContentResult(fileName, fileSize, mimeType, isBinary, content);

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("파일 읽기 실패: {}", filePath, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    /**
     * 경로 검증 및 해석 (경로 탐색 공격 방지)
     *
     * <p>상대 경로가 기준 디렉토리 외부를 가리키는 경우 예외를 발생시킵니다.
     *
     * @param baseDir      기준 디렉토리
     * @param relativePath 상대 경로
     * @return 해석된 절대 경로
     * @throws BusinessException 경로가 비어있거나 기준 디렉토리 외부를 가리키는 경우
     */
    public static Path validateAndResolvePath(Path baseDir, String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "파일 경로가 비어있습니다");
        }

        try {
            // 상대 경로를 절대 경로로 변환
            Path resolvedPath = baseDir.resolve(relativePath.trim()).normalize();

            // 경로 탐색 공격 방지: 해석된 경로가 기준 디렉토리 내부에 있는지 확인
            if (!resolvedPath.startsWith(baseDir)) {
                log.warn("경로 탐색 공격 시도 감지: baseDir={}, relativePath={}, resolved={}",
                        baseDir, relativePath, resolvedPath);
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "유효하지 않은 파일 경로입니다");
            }

            return resolvedPath;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("경로 해석 실패: baseDir={}, relativePath={}", baseDir, relativePath, e);
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "유효하지 않은 파일 경로입니다: " + e.getMessage());
        }
    }

    /**
     * MIME 타입이 텍스트 파일인지 확인
     *
     * @param mimeType MIME 타입
     * @return 텍스트 파일 여부
     */
    public static boolean isTextMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }

        // 텍스트 계열 MIME 타입
        if (mimeType.startsWith("text/")) {
            return true;
        }

        // 텍스트로 취급할 application/* 타입
        return mimeType.equals("application/json") ||
               mimeType.equals("application/xml") ||
               mimeType.equals("application/javascript") ||
               mimeType.equals("application/x-javascript") ||
               mimeType.equals("application/ecmascript") ||
               mimeType.equals("application/xhtml+xml") ||
               mimeType.equals("application/x-sh") ||
               mimeType.equals("application/x-shellscript") ||
               mimeType.equals("application/x-csh") ||
               mimeType.equals("application/x-httpd-php") ||
               mimeType.equals("application/sql") ||
               mimeType.equals("application/graphql") ||
               mimeType.equals("application/ld+json") ||
               mimeType.equals("application/manifest+json") ||
               mimeType.equals("application/x-yaml") ||
               mimeType.equals("application/yaml") ||
               mimeType.endsWith("+xml") ||
               mimeType.endsWith("+json");
    }

    /**
     * 파일의 MIME 타입 조회
     *
     * <p>Files.probeContentType()을 우선 시도하고, 실패 시 확장자 기반으로 판단합니다.
     *
     * <p>사용 예시:
     * <pre>{@code
     * String mimeType = FileContentUtil.getMimeType(Paths.get("/path/to/file.sql"));
     * // 결과: "text/x-sql"
     * }</pre>
     *
     * @param path 파일 경로
     * @return MIME 타입 (판단 불가 시 "application/octet-stream")
     */
    public static String getMimeType(Path path) {
        if (path == null) {
            return "application/octet-stream";
        }

        // 1. Files.probeContentType() 시도
        try {
            String mimeType = Files.probeContentType(path);
            if (mimeType != null) {
                return mimeType;
            }
        } catch (IOException e) {
            log.debug("MIME 타입 probing 실패: {}", path, e);
        }

        // 2. 확장자 기반 판단
        String fileName = path.getFileName().toString().toLowerCase();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex + 1);
            String mimeType = EXTENSION_MIME_MAP.get(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }

        // 3. 특수 파일명 처리 (확장자 없는 파일)
        if (fileName.equals("dockerfile") || fileName.equals("makefile") ||
                fileName.equals(".gitignore") || fileName.equals(".env")) {
            return "text/plain";
        }

        return "application/octet-stream";
    }
}
