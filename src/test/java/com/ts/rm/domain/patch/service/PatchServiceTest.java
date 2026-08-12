package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.site.repository.SiteRepository;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.message.service.PatchReminderService;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.patch.util.ScriptGenerator;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.service.ReleaseVersionFileSystemService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PatchService 삭제 기능 테스트
 */
@ExtendWith(MockitoExtension.class)
class PatchServiceTest {

    @Mock
    private PatchRepository patchRepository;

    @Mock
    private PatchReminderService patchReminderService;

    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @Mock
    private ReleaseVersionFileSystemService releaseVersionFileSystemService;

    @Mock
    private ReleaseFileRepository releaseFileRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private ScriptGenerator mariaDBScriptGenerator;

    @Mock
    private ScriptGenerator crateDBScriptGenerator;

    @InjectMocks
    private PatchService patchService;

    private String testBasePath;
    private Path testPatchDir;

    @BeforeEach
    void setUp() throws IOException {
        // 테스트용 임시 디렉토리 설정
        testBasePath = Files.createTempDirectory("release-manager-test").toString();
        ReflectionTestUtils.setField(patchService, "releaseBasePath", testBasePath);
    }

    @AfterEach
    void tearDown() throws IOException {
        // 테스트 후 임시 디렉토리 정리
        if (testPatchDir != null && Files.exists(testPatchDir)) {
            deleteDirectoryRecursively(testPatchDir);
        }
        Path basePath = Paths.get(testBasePath);
        if (Files.exists(basePath)) {
            deleteDirectoryRecursively(basePath);
        }
    }

    @Test
    @DisplayName("패치 삭제 - 성공 (DB 레코드 + 실제 파일 모두 삭제)")
    void deletePatch_Success() throws IOException {
        // Given
        Long patchId = 1L;
        String outputPath = "patches/test_patch";

        Patch patch = Patch.builder()
                .patchId(patchId)
                .patchName("test_patch")
                .outputPath(outputPath)
                .releaseType("STANDARD")
                .fromVersion("1.0.0")
                .toVersion("1.1.0")
                .createdByEmail("tester")
                .build();

        // 실제 파일 디렉토리 생성
        testPatchDir = Paths.get(testBasePath, outputPath);
        Files.createDirectories(testPatchDir);
        Files.createDirectories(testPatchDir.resolve("mariadb"));
        Files.createFile(testPatchDir.resolve("README.md"));
        Files.createFile(testPatchDir.resolve("mariadb/test.sql"));

        when(patchRepository.findById(patchId)).thenReturn(Optional.of(patch));

        // When
        patchService.deletePatch(patchId);

        // Then
        verify(patchRepository).findById(patchId);
        verify(patchRepository).delete(patch);

        // 실제 파일이 삭제되었는지 확인
        assertThat(Files.exists(testPatchDir)).isFalse();
    }

    @Test
    @DisplayName("패치 삭제 - 성공 (파일 디렉토리가 존재하지 않는 경우)")
    void deletePatch_SuccessWithoutDirectory() {
        // Given
        Long patchId = 2L;
        String outputPath = "patches/nonexistent_patch";

        Patch patch = Patch.builder()
                .patchId(patchId)
                .patchName("nonexistent_patch")
                .outputPath(outputPath)
                .releaseType("STANDARD")
                .fromVersion("1.0.0")
                .toVersion("1.1.0")
                .createdByEmail("tester")
                .build();

        when(patchRepository.findById(patchId)).thenReturn(Optional.of(patch));

        // When
        patchService.deletePatch(patchId);

        // Then
        verify(patchRepository).findById(patchId);
        verify(patchRepository).delete(patch);
    }

    @Test
    @DisplayName("패치 삭제 - 실패 (패치를 찾을 수 없음)")
    void deletePatch_NotFound() {
        // Given
        Long patchId = 999L;

        when(patchRepository.findById(patchId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> patchService.deletePatch(patchId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("패치를 찾을 수 없습니다");

        verify(patchRepository).findById(patchId);
    }

    @Test
    @DisplayName("패치 삭제 - 중첩된 디렉토리 구조 삭제")
    void deletePatch_NestedDirectories() throws IOException {
        // Given
        Long patchId = 3L;
        String outputPath = "patches/nested_patch";

        Patch patch = Patch.builder()
                .patchId(patchId)
                .patchName("nested_patch")
                .outputPath(outputPath)
                .releaseType("STANDARD")
                .fromVersion("1.0.0")
                .toVersion("1.2.0")
                .createdByEmail("tester")
                .build();

        // 중첩된 디렉토리 구조 생성
        testPatchDir = Paths.get(testBasePath, outputPath);
        Files.createDirectories(testPatchDir.resolve("mariadb/source_files/1.1.0"));
        Files.createDirectories(testPatchDir.resolve("mariadb/source_files/1.2.0"));
        Files.createDirectories(testPatchDir.resolve("cratedb/source_files/1.1.0"));

        Files.createFile(testPatchDir.resolve("README.md"));
        Files.createFile(testPatchDir.resolve("mariadb/mariadb_patch.sh"));
        Files.createFile(testPatchDir.resolve("mariadb/source_files/1.1.0/ddl.sql"));
        Files.createFile(testPatchDir.resolve("mariadb/source_files/1.2.0/dml.sql"));
        Files.createFile(testPatchDir.resolve("cratedb/source_files/1.1.0/ddl.sql"));

        when(patchRepository.findById(patchId)).thenReturn(Optional.of(patch));

        // When
        patchService.deletePatch(patchId);

        // Then
        verify(patchRepository).delete(patch);

        // 모든 중첩된 디렉토리와 파일이 삭제되었는지 확인
        assertThat(Files.exists(testPatchDir)).isFalse();
    }

    @Test
    @DisplayName("오래된 패치 정리 - cutoff 이전 패치의 디렉토리 + row 일괄 삭제")
    void deleteOldPatches_Success() {
        // Given
        int retentionDays = 30;
        Patch p1 = Patch.builder()
                .patchId(10L)
                .patchName("old1")
                .outputPath("patches/old1")
                .releaseType("STANDARD")
                .fromVersion("1.0.0")
                .toVersion("1.1.0")
                .createdByEmail("tester")
                .build();
        Patch p2 = Patch.builder()
                .patchId(11L)
                .patchName("old2")
                .outputPath("patches/old2")
                .releaseType("STANDARD")
                .fromVersion("1.0.0")
                .toVersion("1.2.0")
                .createdByEmail("tester")
                .build();
        when(patchRepository.findByCreatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(p1, p2));

        // When
        long deletedCount = patchService.deleteOldPatches(retentionDays);

        // Then
        assertThat(deletedCount).isEqualTo(2);
        verify(releaseVersionFileSystemService).deleteDirectory(Paths.get(testBasePath, "patches/old1"));
        verify(releaseVersionFileSystemService).deleteDirectory(Paths.get(testBasePath, "patches/old2"));
        verify(patchRepository).deleteAll(List.of(p1, p2));
    }

    @Test
    @DisplayName("오래된 패치 정리 - 대상 없음 (0건 반환, 디렉토리 삭제 미호출)")
    void deleteOldPatches_NoTargets() {
        // Given
        when(patchRepository.findByCreatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        // When
        long deletedCount = patchService.deleteOldPatches(30);

        // Then
        assertThat(deletedCount).isEqualTo(0);
        verify(patchRepository).deleteAll(List.of());
        verifyNoInteractions(releaseVersionFileSystemService);
    }

    /**
     * 테스트 헬퍼: 디렉토리 재귀적 삭제
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {
            stream.sorted((p1, p2) -> -p1.compareTo(p2))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // 테스트 정리 중 오류 무시
                        }
                    });
        }
    }
}
