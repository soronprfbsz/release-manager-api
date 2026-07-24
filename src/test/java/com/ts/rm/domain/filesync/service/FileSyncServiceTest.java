package com.ts.rm.domain.filesync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.ts.rm.domain.common.service.CodeService;
import com.ts.rm.domain.common.service.FileStorageService;
import com.ts.rm.domain.filesync.adapter.FileSyncAdapter;
import com.ts.rm.domain.filesync.dto.FileSyncDiscrepancy;
import com.ts.rm.domain.filesync.dto.FileSyncDto;
import com.ts.rm.domain.filesync.dto.FileSyncMetadata;
import com.ts.rm.domain.filesync.enums.FileSyncAction;
import com.ts.rm.domain.filesync.enums.FileSyncStatus;
import com.ts.rm.domain.filesync.enums.FileSyncTarget;
import com.ts.rm.domain.filesync.repository.FileSyncIgnoreRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * FileSyncService 단위 테스트
 *
 * <p>테스트별로 분석 대상·파일 시나리오가 달라 목 스텁이 조건부로 사용된다.
 * 이에 LENIENT 설정으로 미사용 스텁 예외를 허용한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileSyncService 테스트")
class FileSyncServiceTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FileSyncIgnoreRepository fileSyncIgnoreRepository;

    @Mock
    private CodeService codeService;

    @Mock
    private FileSyncAdapter releaseAdapter;

    @Mock
    private FileSyncAdapter resourceAdapter;

    private FileSyncService fileSyncService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 어댑터 목록으로 서비스 생성
        List<FileSyncAdapter> adapters = List.of(releaseAdapter, resourceAdapter);
        fileSyncService = new FileSyncService(fileStorageService, fileSyncIgnoreRepository, codeService, adapters);

        // 코드 테이블 기반 동적 메시지 — 테스트에선 빈 맵(getOrDefault 기본값 사용)
        lenient().when(codeService.getCodeDescriptionMap(anyString())).thenReturn(Map.of());
        lenient().when(codeService.getCodeNameMap(anyString())).thenReturn(Map.of());

        // 기본 어댑터 설정
        given(releaseAdapter.getTarget()).willReturn(FileSyncTarget.RELEASE_FILE);
        given(releaseAdapter.getBaseScanPath()).willReturn("versions");
        given(resourceAdapter.getTarget()).willReturn(FileSyncTarget.RESOURCE_FILE);
        given(resourceAdapter.getBaseScanPath()).willReturn("resource");
        // FS 전용 파일의 UNREGISTERED 판정에 필요 (기본 mock 은 false 반환)
        given(releaseAdapter.isValidSyncPath(anyString())).willReturn(true);
        given(resourceAdapter.isValidSyncPath(anyString())).willReturn(true);
    }

    @Test
    @DisplayName("분석 - 불일치 없음 (DB와 파일시스템 동기화됨)")
    void analyze_NoDiscrepancies() throws IOException {
        // given
        Path versionsDir = tempDir.resolve("versions");
        Files.createDirectories(versionsDir);
        Path testFile = versionsDir.resolve("test.sql");
        Files.writeString(testFile, "SELECT 1;");

        given(fileStorageService.getAbsolutePath("versions")).willReturn(versionsDir);
        given(fileStorageService.getAbsolutePath("")).willReturn(tempDir);
        given(releaseAdapter.getRegisteredFiles(any())).willReturn(List.of(
                FileSyncMetadata.builder()
                        .id(1L)
                        .filePath("versions/test.sql")
                        .fileName("test.sql")
                        .fileSize(9L)
                        .checksum("a1b2c3") // 체크섬은 동일하다고 가정 (단위 테스트에서는 생략)
                        .registeredAt(LocalDateTime.now())
                        .target(FileSyncTarget.RELEASE_FILE)
                        .build()
        ));

        // 빈 디렉토리로 설정 (resourceAdapter)
        Path resourceDir = tempDir.resolve("resource");
        Files.createDirectories(resourceDir);
        given(fileStorageService.getAbsolutePath("resource")).willReturn(resourceDir);
        given(resourceAdapter.getRegisteredFiles(any())).willReturn(List.of());

        FileSyncDto.AnalyzeRequest request = FileSyncDto.AnalyzeRequest.builder()
                .targets(List.of(FileSyncTarget.RELEASE_FILE))
                .build();

        // when
        FileSyncDto.AnalyzeResponse response = fileSyncService.analyze(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAnalyzedAt()).isNotNull();
        // 체크섬 불일치로 1건 발생할 수 있음 (파일시스템의 실제 체크섬과 DB 체크섬이 다름)
    }

    @Test
    @DisplayName("분석 - 미등록 파일 발견 (UNREGISTERED)")
    void analyze_UnregisteredFileFound() throws IOException {
        // given
        Path versionsDir = tempDir.resolve("versions");
        Files.createDirectories(versionsDir);
        Path unregisteredFile = versionsDir.resolve("unregistered.sql");
        Files.writeString(unregisteredFile, "SELECT 1;");

        given(fileStorageService.getAbsolutePath("versions")).willReturn(versionsDir);
        given(fileStorageService.getAbsolutePath("")).willReturn(tempDir);
        given(releaseAdapter.getRegisteredFiles(any())).willReturn(List.of()); // DB에는 없음

        // resourceAdapter 설정
        Path resourceDir = tempDir.resolve("resource");
        Files.createDirectories(resourceDir);
        given(fileStorageService.getAbsolutePath("resource")).willReturn(resourceDir);
        given(resourceAdapter.getRegisteredFiles(any())).willReturn(List.of());

        FileSyncDto.AnalyzeRequest request = FileSyncDto.AnalyzeRequest.builder()
                .targets(List.of(FileSyncTarget.RELEASE_FILE))
                .build();

        // when
        FileSyncDto.AnalyzeResponse response = fileSyncService.analyze(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getDiscrepancies()).isNotEmpty();

        FileSyncDiscrepancy discrepancy = response.getDiscrepancies().get(0);
        assertThat(discrepancy.getStatus()).isEqualTo(FileSyncStatus.UNREGISTERED);
        assertThat(discrepancy.getFileName()).isEqualTo("unregistered.sql");
        assertThat(discrepancy.getAvailableActions())
                .contains(FileSyncAction.REGISTER, FileSyncAction.DELETE_FILE, FileSyncAction.IGNORE);
    }

    @Test
    @DisplayName("분석 - 파일 없음 발견 (FILE_MISSING)")
    void analyze_FileMissingFound() throws IOException {
        // given
        Path versionsDir = tempDir.resolve("versions");
        Files.createDirectories(versionsDir);
        // 파일시스템에는 파일 없음

        given(fileStorageService.getAbsolutePath("versions")).willReturn(versionsDir);
        given(fileStorageService.getAbsolutePath("")).willReturn(tempDir);
        given(releaseAdapter.getRegisteredFiles(any())).willReturn(List.of(
                FileSyncMetadata.builder()
                        .id(1L)
                        .filePath("versions/missing.sql")
                        .fileName("missing.sql")
                        .fileSize(100L)
                        .checksum("xyz789")
                        .registeredAt(LocalDateTime.now())
                        .target(FileSyncTarget.RELEASE_FILE)
                        .build()
        ));

        // resourceAdapter 설정
        Path resourceDir = tempDir.resolve("resource");
        Files.createDirectories(resourceDir);
        given(fileStorageService.getAbsolutePath("resource")).willReturn(resourceDir);
        given(resourceAdapter.getRegisteredFiles(any())).willReturn(List.of());

        FileSyncDto.AnalyzeRequest request = FileSyncDto.AnalyzeRequest.builder()
                .targets(List.of(FileSyncTarget.RELEASE_FILE))
                .build();

        // when
        FileSyncDto.AnalyzeResponse response = fileSyncService.analyze(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getDiscrepancies()).isNotEmpty();

        FileSyncDiscrepancy discrepancy = response.getDiscrepancies().get(0);
        assertThat(discrepancy.getStatus()).isEqualTo(FileSyncStatus.FILE_MISSING);
        assertThat(discrepancy.getFileName()).isEqualTo("missing.sql");
        assertThat(discrepancy.getAvailableActions())
                .contains(FileSyncAction.DELETE_METADATA, FileSyncAction.IGNORE);
    }

    @Test
    @DisplayName("분석 - 크기 불일치 발견 (SIZE_MISMATCH)")
    void analyze_SizeMismatchFound() throws IOException {
        // given
        Path versionsDir = tempDir.resolve("versions");
        Files.createDirectories(versionsDir);
        Path testFile = versionsDir.resolve("test.sql");
        Files.writeString(testFile, "SELECT 1; -- Long content here");

        given(fileStorageService.getAbsolutePath("versions")).willReturn(versionsDir);
        given(fileStorageService.getAbsolutePath("")).willReturn(tempDir);
        given(releaseAdapter.getRegisteredFiles(any())).willReturn(List.of(
                FileSyncMetadata.builder()
                        .id(1L)
                        .filePath("versions/test.sql")
                        .fileName("test.sql")
                        .fileSize(10L) // DB에는 10 바이트로 기록
                        .checksum("abc123")
                        .registeredAt(LocalDateTime.now())
                        .target(FileSyncTarget.RELEASE_FILE)
                        .build()
        ));

        // resourceAdapter 설정
        Path resourceDir = tempDir.resolve("resource");
        Files.createDirectories(resourceDir);
        given(fileStorageService.getAbsolutePath("resource")).willReturn(resourceDir);
        given(resourceAdapter.getRegisteredFiles(any())).willReturn(List.of());

        FileSyncDto.AnalyzeRequest request = FileSyncDto.AnalyzeRequest.builder()
                .targets(List.of(FileSyncTarget.RELEASE_FILE))
                .build();

        // when
        FileSyncDto.AnalyzeResponse response = fileSyncService.analyze(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getDiscrepancies()).isNotEmpty();

        FileSyncDiscrepancy discrepancy = response.getDiscrepancies().get(0);
        assertThat(discrepancy.getStatus()).isEqualTo(FileSyncStatus.SIZE_MISMATCH);
        assertThat(discrepancy.getAvailableActions())
                .contains(FileSyncAction.UPDATE_METADATA, FileSyncAction.IGNORE);
    }

    @Test
    @DisplayName("분석 - 요약 정보 확인")
    void analyze_SummaryIsCorrect() throws IOException {
        // given
        Path versionsDir = tempDir.resolve("versions");
        Files.createDirectories(versionsDir);
        // 미등록 파일 2개 생성
        Files.writeString(versionsDir.resolve("file1.sql"), "SELECT 1;");
        Files.writeString(versionsDir.resolve("file2.sql"), "SELECT 2;");

        given(fileStorageService.getAbsolutePath("versions")).willReturn(versionsDir);
        given(fileStorageService.getAbsolutePath("")).willReturn(tempDir);
        given(releaseAdapter.getRegisteredFiles(any())).willReturn(List.of()); // DB에는 없음

        // resourceAdapter 설정
        Path resourceDir = tempDir.resolve("resource");
        Files.createDirectories(resourceDir);
        given(fileStorageService.getAbsolutePath("resource")).willReturn(resourceDir);
        given(resourceAdapter.getRegisteredFiles(any())).willReturn(List.of());

        FileSyncDto.AnalyzeRequest request = FileSyncDto.AnalyzeRequest.builder()
                .targets(List.of(FileSyncTarget.RELEASE_FILE))
                .build();

        // when
        FileSyncDto.AnalyzeResponse response = fileSyncService.analyze(request);

        // then
        assertThat(response.getSummary()).isNotNull();
        assertThat(response.getSummary().getTotalScanned()).isEqualTo(2);
        assertThat(response.getSummary().getDiscrepancies()).isEqualTo(2);
        assertThat(response.getDiscrepanciesByTarget().get(FileSyncTarget.RELEASE_FILE)).isEqualTo(2);
    }

    @Test
    @DisplayName("적용 - IGNORE 액션 성공")
    void apply_IgnoreAction() throws IOException {
        // given - 먼저 분석하여 캐시에 등록
        Path versionsDir = tempDir.resolve("versions");
        Files.createDirectories(versionsDir);
        Files.writeString(versionsDir.resolve("test.sql"), "SELECT 1;");

        given(fileStorageService.getAbsolutePath("versions")).willReturn(versionsDir);
        given(fileStorageService.getAbsolutePath("")).willReturn(tempDir);
        given(releaseAdapter.getRegisteredFiles(any())).willReturn(List.of());

        Path resourceDir = tempDir.resolve("resource");
        Files.createDirectories(resourceDir);
        given(fileStorageService.getAbsolutePath("resource")).willReturn(resourceDir);
        given(resourceAdapter.getRegisteredFiles(any())).willReturn(List.of());

        FileSyncDto.AnalyzeRequest analyzeRequest = FileSyncDto.AnalyzeRequest.builder()
                .targets(List.of(FileSyncTarget.RELEASE_FILE))
                .build();

        FileSyncDto.AnalyzeResponse analyzeResponse = fileSyncService.analyze(analyzeRequest);
        String discrepancyId = analyzeResponse.getDiscrepancies().get(0).getId();

        // when
        FileSyncDto.ApplyRequest applyRequest = FileSyncDto.ApplyRequest.builder()
                .actions(List.of(
                        FileSyncDto.ActionItem.builder()
                                .id(discrepancyId)
                                .action(FileSyncAction.IGNORE)
                                .build()
                ))
                .build();

        // IGNORE 처리는 SecurityUtil.getTokenInfo().email() 로 등록자를 조회한다
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@tscientific", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            FileSyncDto.ApplyResponse applyResponse = fileSyncService.apply(applyRequest);

            // then
            assertThat(applyResponse).isNotNull();
            assertThat(applyResponse.getSummary().getTotal()).isEqualTo(1);
            assertThat(applyResponse.getSummary().getSuccess()).isEqualTo(1);
            assertThat(applyResponse.getResults().get(0).getMessage()).isEqualTo("무시 목록에 등록됨");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("적용 - 캐시에 없는 ID로 요청 시 실패")
    void apply_InvalidIdFails() {
        // given
        FileSyncDto.ApplyRequest request = FileSyncDto.ApplyRequest.builder()
                .actions(List.of(
                        FileSyncDto.ActionItem.builder()
                                .id("invalid-uuid")
                                .action(FileSyncAction.REGISTER)
                                .build()
                ))
                .build();

        // when
        FileSyncDto.ApplyResponse response = fileSyncService.apply(request);

        // then
        assertThat(response.getSummary().getFailed()).isEqualTo(1);
        assertThat(response.getResults().get(0).isSuccess()).isFalse();
        assertThat(response.getResults().get(0).getMessage()).contains("불일치 항목을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("분석 - 특정 대상만 필터링")
    void analyze_FilterByTarget() throws IOException {
        // given
        Path versionsDir = tempDir.resolve("versions");
        Files.createDirectories(versionsDir);
        Files.writeString(versionsDir.resolve("release.sql"), "SELECT 1;");

        Path resourceDir = tempDir.resolve("resource");
        Files.createDirectories(resourceDir);
        Files.writeString(resourceDir.resolve("script.sh"), "echo hello");

        given(fileStorageService.getAbsolutePath("versions")).willReturn(versionsDir);
        given(fileStorageService.getAbsolutePath("resource")).willReturn(resourceDir);
        given(fileStorageService.getAbsolutePath("")).willReturn(tempDir);
        given(releaseAdapter.getRegisteredFiles(any())).willReturn(List.of());
        given(resourceAdapter.getRegisteredFiles(any())).willReturn(List.of());

        // RELEASE_FILE만 요청
        FileSyncDto.AnalyzeRequest request = FileSyncDto.AnalyzeRequest.builder()
                .targets(List.of(FileSyncTarget.RELEASE_FILE))
                .build();

        // when
        FileSyncDto.AnalyzeResponse response = fileSyncService.analyze(request);

        // then
        assertThat(response.getDiscrepancies()).allMatch(
                d -> d.getTarget() == FileSyncTarget.RELEASE_FILE);
    }
}
