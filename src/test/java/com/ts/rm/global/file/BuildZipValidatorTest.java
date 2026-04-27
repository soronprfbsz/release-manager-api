package com.ts.rm.global.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BuildZipValidator 단위 테스트.
 */
@DisplayName("BuildZipValidator 테스트")
class BuildZipValidatorTest {

    /**
     * 주어진 엔트리 이름들로 ZIP 바이트 배열 생성.
     */
    private byte[] makeZip(String... entryNames) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String name : entryNames) {
                ZipEntry e = new ZipEntry(name);
                zos.putNextEntry(e);
                if (!name.endsWith("/")) {
                    zos.write(("dummy:" + name).getBytes());
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("정상: web/, engine/, etc/ 만 포함된 ZIP 은 통과")
    void validate_allowedRootsOnly_passes() throws IOException {
        byte[] zip = makeZip(
                "web/foo.war",
                "engine/NC_SMS/x.jar",
                "etc/config.yml"
        );
        assertThatCode(() -> BuildZipValidator.validate(zip)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정상: web/ 만 있어도 통과 (subset 허용)")
    void validate_onlyWeb_passes() throws IOException {
        byte[] zip = makeZip("web/main.war", "web/static/js/app.js");
        assertThatCode(() -> BuildZipValidator.validate(zip)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("거부: 허용되지 않은 루트 디렉토리 (database/, tools/) 포함")
    void validate_unknownRootDir_rejected() throws IOException {
        byte[] zip = makeZip("web/foo.war", "tools/script.sh", "database/v1.sql");
        assertThatThrownBy(() -> BuildZipValidator.validate(zip))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("tools/")
                .hasMessageContaining("database/");
    }

    @Test
    @DisplayName("거부: 루트에 단독 파일 존재 (서브디렉토리 없음)")
    void validate_rootLevelFile_rejected() throws IOException {
        byte[] zip = makeZip("web/foo.war", "README.md");
        assertThatThrownBy(() -> BuildZipValidator.validate(zip))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("README.md");
    }

    @Test
    @DisplayName("거부: 빈 ZIP")
    void validate_emptyZip_rejected() throws IOException {
        byte[] zip = makeZip();  // no entries
        assertThatThrownBy(() -> BuildZipValidator.validate(zip))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("비어있");
    }

    @Test
    @DisplayName("거부: 대소문자 다른 루트 (Web/ 은 web/ 와 다름)")
    void validate_caseSensitive_rejectsCapitalized() throws IOException {
        byte[] zip = makeZip("Web/foo.war");
        assertThatThrownBy(() -> BuildZipValidator.validate(zip))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Web/");
    }

    @Test
    @DisplayName("ALLOWED_ROOT_DIRECTORIES 가 web, engine, etc 셋만 노출")
    void allowedRootDirectories_isExpectedSet() {
        assertThat(BuildZipValidator.ALLOWED_ROOT_DIRECTORIES)
                .containsExactlyInAnyOrder("web", "engine", "etc");
    }
}
