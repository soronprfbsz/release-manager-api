package com.ts.rm.global.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FileChecksumUtil 동작 테스트.
 *
 * <p>버전 생성 OOM 수정(saveReleaseFile 의 readAllBytes 제거)이 이 스트리밍 체크섬을 재사용한다.
 * 저장되는 해시가 기존 방식(SHA-256 → 소문자 %02x 64자리)과 100% 동일해야
 * FileSyncService 의 CHECKSUM_MISMATCH 비교가 깨지지 않으므로, 그 계약을 고정한다.
 */
@DisplayName("FileChecksumUtil 테스트")
class FileChecksumUtilTest {

    /** 기존 calculateChecksum(byte[]) 가 쓰던 것과 동일한 표준 SHA-256 소문자 hex. */
    private static String sha256Hex(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    @DisplayName("파일 스트리밍 체크섬은 동일 내용의 표준 SHA-256(소문자 64-hex)과 일치한다")
    void streamingChecksumMatchesCanonicalSha256(@TempDir Path tempDir) throws Exception {
        byte[] content = "CREATE TABLE users (id INT PRIMARY KEY);\nINSERT INTO users VALUES (1);".getBytes();
        Path file = tempDir.resolve("artifact.bin");
        Files.write(file, content);

        String checksum = FileChecksumUtil.calculateChecksum(file);

        assertThat(checksum).hasSize(64);
        assertThat(checksum).isEqualTo(sha256Hex(content));
    }

    @Test
    @DisplayName("내용이 청크 경계(8192B)를 넘어도 전체를 정확히 해싱한다")
    void hashesAcrossBufferBoundary(@TempDir Path tempDir) throws Exception {
        byte[] content = new byte[8192 * 3 + 17];  // 버퍼 크기 배수 + 잔여
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        Path file = tempDir.resolve("big.bin");
        Files.write(file, content);

        String checksum = FileChecksumUtil.calculateChecksum(file);

        assertThat(checksum).isEqualTo(sha256Hex(content));
    }
}
