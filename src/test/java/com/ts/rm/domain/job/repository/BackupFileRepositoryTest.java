package com.ts.rm.domain.job.repository;

import com.ts.rm.config.AbstractTestBase;
import com.ts.rm.config.TestQueryDslConfig;
import com.ts.rm.domain.job.entity.BackupFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BackupFileRepository 테스트
 */
@Import(TestQueryDslConfig.class)
class BackupFileRepositoryTest extends AbstractTestBase {

    @Autowired
    private BackupFileRepository backupFileRepository;

    @Test
    @DisplayName("백업 파일 저장 성공")
    void saveBackupFile() {
        // given
        BackupFile backupFile = BackupFile.builder()
                .fileCategory("MARIADB")
                .fileType("SQL")
                .fileName("test_backup.sql")
                .filePath("job/MARIADB/backup_files/test_backup.sql")
                .fileSize(1024L)
                .checksum("abc123")
                .description("테스트 백업 파일")
                .createdByEmail("test@test.com")
                .build();

        // when
        BackupFile savedFile = backupFileRepository.save(backupFile);

        // then
        assertThat(savedFile.getBackupFileId()).isNotNull();
        assertThat(savedFile.getFileName()).isEqualTo("test_backup.sql");
        assertThat(savedFile.getFilePath()).isEqualTo("job/MARIADB/backup_files/test_backup.sql");
    }

    @Test
    @DisplayName("파일 경로 중복 시 예외 발생")
    void duplicateFilePathShouldThrowException() {
        // given - 첫 번째 백업 파일 저장
        BackupFile backupFile1 = BackupFile.builder()
                .fileCategory("MARIADB")
                .fileType("SQL")
                .fileName("duplicate_test.sql")
                .filePath("job/MARIADB/backup_files/duplicate_test.sql")
                .fileSize(1024L)
                .checksum("abc123")
                .createdByEmail("test@test.com")
                .build();
        backupFileRepository.save(backupFile1);

        // when - 동일한 파일 경로로 두 번째 백업 파일 저장 시도
        BackupFile backupFile2 = BackupFile.builder()
                .fileCategory("MARIADB")
                .fileType("SQL")
                .fileName("duplicate_test.sql")
                .filePath("job/MARIADB/backup_files/duplicate_test.sql")  // 동일한 경로
                .fileSize(2048L)
                .checksum("def456")
                .createdByEmail("test@test.com")
                .build();

        // then - 중복 키 예외 발생
        assertThatThrownBy(() -> {
            backupFileRepository.save(backupFile2);
            backupFileRepository.flush();  // 즉시 DB에 반영하여 제약조건 체크
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("파일 경로 존재 여부 확인")
    void existsByFilePath() {
        // given
        String filePath = "job/MARIADB/backup_files/exists_test.sql";
        BackupFile backupFile = BackupFile.builder()
                .fileCategory("MARIADB")
                .fileType("SQL")
                .fileName("exists_test.sql")
                .filePath(filePath)
                .fileSize(1024L)
                .checksum("abc123")
                .createdByEmail("test@test.com")
                .build();
        backupFileRepository.save(backupFile);

        // when
        boolean exists = backupFileRepository.existsByFilePath(filePath);
        boolean notExists = backupFileRepository.existsByFilePath("job/MARIADB/backup_files/not_exists.sql");

        // then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("파일명으로 조회")
    void findByFileName() {
        // given
        BackupFile backupFile = BackupFile.builder()
                .fileCategory("MARIADB")
                .fileType("SQL")
                .fileName("find_test.sql")
                .filePath("job/MARIADB/backup_files/find_test.sql")
                .fileSize(1024L)
                .checksum("abc123")
                .createdByEmail("test@test.com")
                .build();
        backupFileRepository.save(backupFile);

        // when
        var foundFile = backupFileRepository.findByFileName("find_test.sql");

        // then
        assertThat(foundFile).isPresent();
        assertThat(foundFile.get().getFileName()).isEqualTo("find_test.sql");
    }
}
