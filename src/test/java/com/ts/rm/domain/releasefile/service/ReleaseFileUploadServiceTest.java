package com.ts.rm.domain.releasefile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ts.rm.domain.common.service.FileStorageService;
import com.ts.rm.domain.releasefile.dto.ReleaseFileDto;
import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.mapper.ReleaseFileDtoMapper;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * ReleaseFileUploadService 단위 테스트 (TDD)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseFileUploadService 테스트")
class ReleaseFileUploadServiceTest {

    @Mock
    private ReleaseFileRepository releaseFileRepository;

    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @Mock
    private ReleaseFileDtoMapper mapper;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ReleaseFileUploadService uploadService;

    private ReleaseVersion testVersion;
    private ReleaseFile testReleaseFile;
    private ReleaseFileDto.DetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        testVersion = ReleaseVersion.builder()
                .releaseVersionId(1L)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1)
                .minorVersion(1)
                .patchVersion(0)
                .build();

        testReleaseFile = ReleaseFile.builder()
                .releaseFileId(1L)
                .releaseVersion(testVersion)
                .fileCategory(FileCategory.DATABASE)
                .subCategory("MARIADB")
                .fileName("001_create_users_table.sql")
                .filePath("versions/standard/1.1.x/1.1.0/mariadb/001_create_users_table.sql")
                .fileSize(1024L)
                .checksum("abc123def456")
                .executionOrder(1)
                .description("testuser가 업로드한 파일")
                .build();

        detailResponse = new ReleaseFileDto.DetailResponse(
                1L,
                1L,
                "1.1.0",
                "database",
                "MARIADB",
                "001_create_users_table.sql",
                "versions/standard/1.1.x/1.1.0/mariadb/001_create_users_table.sql",
                1024L,
                "abc123def456",
                1,
                "testuser가 업로드한 파일",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("체크섬 계산 - 성공")
    void calculateChecksum_Success() {
        // given
        String content = "CREATE TABLE users (id INT PRIMARY KEY);";

        // when
        String checksum = uploadService.calculateChecksum(content.getBytes());

        // then
        assertThat(checksum).isNotNull();
        assertThat(checksum).hasSize(64); // SHA-256 해시는 64자리 16진수
    }

    @Test
    @DisplayName("파일 업로드 - 빈 파일 예외")
    void uploadReleaseFiles_EmptyFile() {
        // given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "test.sql",
                "text/plain",
                new byte[0] // 빈 파일
        );

        ReleaseFileDto.UploadRequest request = ReleaseFileDto.UploadRequest.builder()
                .fileCategory("DATABASE")
                .subCategory("MARIADB")
                .uploadedBy("testuser")
                .build();

        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(anyLong()))
                .willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> uploadService.uploadReleaseFiles(1L, List.of(emptyFile), request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("빈 파일입니다");

        then(releaseFileRepository).should(never()).save(any(ReleaseFile.class));
    }

    @Test
    @DisplayName("파일 업로드 - SQL 파일 외 예외")
    void uploadReleaseFiles_NotSqlFile() {
        // given
        MockMultipartFile txtFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "test content".getBytes()
        );

        ReleaseFileDto.UploadRequest request = ReleaseFileDto.UploadRequest.builder()
                .fileCategory("DATABASE")
                .subCategory("MARIADB")
                .uploadedBy("testuser")
                .build();

        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(anyLong()))
                .willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> uploadService.uploadReleaseFiles(1L, List.of(txtFile), request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("SQL 파일만 업로드 가능합니다");

        then(releaseFileRepository).should(never()).save(any(ReleaseFile.class));
    }

    @Test
    @DisplayName("파일 업로드 - 크기 초과 예외")
    void uploadReleaseFiles_FileSizeExceeded() {
        // given
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.sql",
                "text/plain",
                largeContent
        );

        ReleaseFileDto.UploadRequest request = ReleaseFileDto.UploadRequest.builder()
                .fileCategory("DATABASE")
                .subCategory("MARIADB")
                .uploadedBy("testuser")
                .build();

        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(anyLong()))
                .willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> uploadService.uploadReleaseFiles(1L, List.of(largeFile), request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("파일 크기는 10MB를 초과할 수 없습니다");

        then(releaseFileRepository).should(never()).save(any(ReleaseFile.class));
    }

    @Test
    @DisplayName("파일 업로드 - 버전 없음 예외")
    void uploadReleaseFiles_VersionNotFound() {
        // given
        MockMultipartFile sqlFile = new MockMultipartFile(
                "file",
                "test.sql",
                "text/plain",
                "CREATE TABLE test;".getBytes()
        );

        ReleaseFileDto.UploadRequest request = ReleaseFileDto.UploadRequest.builder()
                .fileCategory("DATABASE")
                .subCategory("MARIADB")
                .uploadedBy("testuser")
                .build();

        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> uploadService.uploadReleaseFiles(999L, List.of(sqlFile), request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RELEASE_VERSION_NOT_FOUND);

        then(releaseFileRepository).should(never()).save(any(ReleaseFile.class));
    }

    @Test
    @DisplayName("파일 업로드 - 성공")
    void uploadReleaseFiles_Success() {
        // given
        MockMultipartFile sqlFile = new MockMultipartFile(
                "file",
                "001_create_users_table.sql",
                "text/plain",
                "CREATE TABLE users (id INT PRIMARY KEY);".getBytes()
        );

        ReleaseFileDto.UploadRequest request = ReleaseFileDto.UploadRequest.builder()
                .fileCategory("DATABASE")
                .subCategory("MARIADB")
                .uploadedBy("testuser")
                .build();

        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(anyLong()))
                .willReturn(List.of());
        given(fileStorageService.saveFile(any(MultipartFile.class), anyString()))
                .willReturn("versions/standard/1.1.x/1.1.0/mariadb/001_create_users_table.sql");
        given(releaseFileRepository.save(any(ReleaseFile.class))).willReturn(testReleaseFile);
        given(mapper.toDetailResponse(any(ReleaseFile.class))).willReturn(detailResponse);

        // when
        List<ReleaseFileDto.DetailResponse> result = uploadService.uploadReleaseFiles(1L,
                List.of(sqlFile), request);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileName()).isEqualTo("001_create_users_table.sql");
        assertThat(result.get(0).fileCategory()).isEqualTo("database");

        then(fileStorageService).should(times(1)).saveFile(any(MultipartFile.class), anyString());
        then(releaseFileRepository).should(times(1)).save(any(ReleaseFile.class));
    }
}
