package com.ts.rm.domain.patch.util;

import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;

/**
 * 스크립트 생성 추상 클래스
 *
 * <p>공통 로직 (템플릿 로드, 메타데이터 생성, 이스케이프 처리 등)을 제공합니다.
 */
@Slf4j
public abstract class AbstractScriptGenerator implements ScriptGenerator {

    @Value("${app.release.base-path:data/release-manager}")
    protected String baseReleasePath;

    protected static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss");

    /**
     * 템플릿 파일 경로 반환 (구현체에서 정의)
     *
     * @return 템플릿 classpath 상대 경로 (예: templates/MARIADB/mariadb_patch_template.sh)
     */
    protected abstract String getTemplatePath();

    /**
     * 템플릿 로드 (classpath resource).
     *
     * <p>jar 안의 {@code src/main/resources/templates/...} 에서 읽으므로 backend 재빌드만으로
     * 템플릿 변경이 반영된다 (NAS / 외부 마운트 디렉토리 수동 갱신 불필요).
     */
    protected String loadTemplate() {
        String resourcePath = getTemplatePath();
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "템플릿 파일을 찾을 수 없습니다: classpath:" + resourcePath);
        }
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("템플릿 로드 실패: classpath:{}", resourcePath, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "템플릿 로드 실패: " + e.getMessage());
        }
    }

    /**
     * 스크립트 파일 저장
     *
     * @param script        스크립트 내용
     * @param outputDirPath 출력 디렉토리 상대 경로 (baseReleasePath 기준)
     */
    protected void saveScript(String script, String outputDirPath) {
        try {
            // CRLF를 LF로 변환 (Linux 환경에서 실행 가능하도록)
            script = script.replace("\r\n", "\n").replace("\r", "\n");

            // 스크립트 파일 저장 (baseReleasePath + 상대 경로)
            Path scriptPath = Paths.get(baseReleasePath, outputDirPath, getScriptFileName());
            Files.createDirectories(scriptPath.getParent());
            Files.writeString(scriptPath, script);

            // 실행 권한 부여 (Linux/Mac에서만 작동)
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                scriptPath.toFile().setExecutable(true);
            }

            log.info("{} 패치 스크립트 생성 완료: {}", getDatabaseType(), scriptPath);

        } catch (IOException e) {
            log.error("{} 패치 스크립트 생성 실패", getDatabaseType(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "스크립트 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 데이터베이스 타입 반환 (로그용)
     *
     * @return 데이터베이스 타입 (예: MariaDB, CrateDB)
     */
    protected abstract String getDatabaseType();

    /**
     * 버전 메타데이터 배열 생성
     *
     * <pre>
     * "1.1.0:2025-11-05:jhlee:초기 버전"
     * "1.1.1:2025-11-10:jhlee:버그 수정"
     * </pre>
     */
    protected String buildVersionMetadata(List<ReleaseVersion> versions) {
        return versions.stream()
                .map(v -> String.format("    \"%s:%s:%s:%s\"",
                        v.getVersion(),
                        v.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        v.getCreatedByName(),
                        v.getComment() != null ? v.getComment().replace("\"", "\\\"") : ""))
                .collect(Collectors.joining("\n"));
    }

    /**
     * SQL 문자열에서 특수문자 이스케이프
     *
     * @param value 원본 문자열
     * @return 이스케이프된 문자열
     */
    protected String escapeForSql(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * 현재 날짜/시간 문자열 생성
     *
     * @return 포맷된 날짜/시간 (yyyy-MM-dd HH:mm:ss)
     */
    protected String getCurrentDateTime() {
        return LocalDateTime.now().format(DATE_FORMATTER);
    }
}
