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

/**
 * ReleaseFile Service 단위 테스트 (TDD)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseFileService 테스트")
class ReleaseFileServiceTest {

    @Mock
    private ReleaseFileRepository releaseFileRepository;

    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @Mock
    private ReleaseFileDtoMapper mapper;

    @InjectMocks
    private ReleaseFileService releaseFileService;

    private ReleaseVersion testVersion;
    private ReleaseFile testReleaseFile;
    private ReleaseFileDto.CreateRequest createRequest;
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
                .subCategory("mariadb")
                .fileName("001_create_users_table.sql")
                .filePath("/release-manager/1.1.0/patch/mariadb/001_create_users_table.sql")
                .fileSize(1024L)
                .checksum("abc123def456")
                .executionOrder(1)
                .description("Create users table")
                .build();

        createRequest = ReleaseFileDto.CreateRequest.builder()
                .releaseVersionId(1L)
                .fileCategory("DATABASE")
                .subCategory("mariadb")
                .fileName("001_create_users_table.sql")
                .filePath("/release-manager/1.1.0/patch/mariadb/001_create_users_table.sql")
                .fileSize(1024L)
                .checksum("abc123def456")
                .executionOrder(1)
                .description("Create users table")
                .build();

        detailResponse = new ReleaseFileDto.DetailResponse(
                1L,
                1L,
                "1.1.0",
                "database",
                "mariadb",
                "001_create_users_table.sql",
                "/release-manager/1.1.0/patch/mariadb/001_create_users_table.sql",
                1024L,
                "abc123def456",
                1,
                "Create users table",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("릴리즈 파일 생성 - 성공")
    void createReleaseFile_Success() {
        // given
        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(releaseFileRepository.save(any(ReleaseFile.class))).willReturn(testReleaseFile);
        given(mapper.toDetailResponse(any(ReleaseFile.class))).willReturn(detailResponse);

        // when
        ReleaseFileDto.DetailResponse result = releaseFileService.createReleaseFile(createRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.fileName()).isEqualTo("001_create_users_table.sql");
        assertThat(result.checksum()).isEqualTo("abc123def456");

        then(releaseFileRepository).should(times(1)).save(any(ReleaseFile.class));
    }

    @Test
    @DisplayName("릴리즈 파일 생성 - ReleaseVersion 없음")
    void createReleaseFile_VersionNotFound() {
        // given
        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> releaseFileService.createReleaseFile(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RELEASE_VERSION_NOT_FOUND);

        then(releaseFileRepository).should(never()).save(any(ReleaseFile.class));
    }

    @Test
    @DisplayName("릴리즈 파일 조회 (ID) - 성공")
    void getReleaseFileById_Success() {
        // given
        given(releaseFileRepository.findById(anyLong())).willReturn(Optional.of(testReleaseFile));
        given(mapper.toDetailResponse(any(ReleaseFile.class))).willReturn(detailResponse);

        // when
        ReleaseFileDto.DetailResponse result = releaseFileService.getReleaseFileById(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.releaseFileId()).isEqualTo(1L);

        then(releaseFileRepository).should(times(1)).findById(1L);
    }

    @Test
    @DisplayName("릴리즈 파일 조회 (ID) - 존재하지 않음")
    void getReleaseFileById_NotFound() {
        // given
        given(releaseFileRepository.findById(anyLong())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> releaseFileService.getReleaseFileById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATCH_FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("버전별 릴리즈 파일 목록 조회 - 성공")
    void getReleaseFilesByVersion_Success() {
        // given
        List<ReleaseFile> releaseFiles = List.of(testReleaseFile);
        List<ReleaseFileDto.SimpleResponse> simpleResponses = List.of(
                new ReleaseFileDto.SimpleResponse(
                        1L, "1.1.0", "database", "mariadb", "001_create_users_table.sql",
                        1024L, "abc123def456", 1, "Create users table"
                )
        );

        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(anyLong()))
                .willReturn(releaseFiles);
        given(mapper.toSimpleResponseList(any())).willReturn(simpleResponses);

        // when
        List<ReleaseFileDto.SimpleResponse> result = releaseFileService.getReleaseFilesByVersion(1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileName()).isEqualTo("001_create_users_table.sql");
    }

    @Test
    @DisplayName("버전+카테고리별 릴리즈 파일 목록 조회 - 성공")
    void getReleaseFilesByVersionAndCategory_Success() {
        // given
        List<ReleaseFile> releaseFiles = List.of(testReleaseFile);
        List<ReleaseFileDto.SimpleResponse> simpleResponses = List.of(
                new ReleaseFileDto.SimpleResponse(
                        1L, "1.1.0", "database", "mariadb", "001_create_users_table.sql",
                        1024L, "abc123def456", 1, "Create users table"
                )
        );

        given(releaseVersionRepository.findById(anyLong())).willReturn(Optional.of(testVersion));
        given(releaseFileRepository.findByReleaseVersion_ReleaseVersionIdAndFileCategoryOrderByExecutionOrderAsc(anyLong(), any(FileCategory.class)))
                .willReturn(releaseFiles);
        given(mapper.toSimpleResponseList(any())).willReturn(simpleResponses);

        // when
        List<ReleaseFileDto.SimpleResponse> result =
                releaseFileService.getReleaseFilesByVersionAndCategory(1L, FileCategory.DATABASE);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileCategory()).isEqualTo("database");
    }

    @Test
    @DisplayName("파일 경로로 릴리즈 파일 조회 - 성공")
    void getReleaseFileByPath_Success() {
        // given
        given(releaseFileRepository.findByFilePath(anyString())).willReturn(
                Optional.of(testReleaseFile));
        given(mapper.toDetailResponse(any(ReleaseFile.class))).willReturn(detailResponse);

        // when
        ReleaseFileDto.DetailResponse result = releaseFileService.getReleaseFileByPath(
                "/release-manager/1.1.0/patch/mariadb/001_create_users_table.sql");

        // then
        assertThat(result).isNotNull();
        assertThat(result.filePath()).isEqualTo(
                "/release-manager/1.1.0/patch/mariadb/001_create_users_table.sql");
    }

    @Test
    @DisplayName("파일 경로로 릴리즈 파일 조회 - 존재하지 않음")
    void getReleaseFileByPath_NotFound() {
        // given
        given(releaseFileRepository.findByFilePath(anyString())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> releaseFileService.getReleaseFileByPath("/invalid/path.sql"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATCH_FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("릴리즈 파일 수정 - 성공")
    void updateReleaseFile_Success() {
        // given
        ReleaseFileDto.UpdateRequest updateRequest = ReleaseFileDto.UpdateRequest.builder()
                .description("Updated description")
                .executionOrder(2)
                .build();

        given(releaseFileRepository.findById(anyLong())).willReturn(Optional.of(testReleaseFile));
        given(mapper.toDetailResponse(any(ReleaseFile.class))).willReturn(detailResponse);

        // when
        ReleaseFileDto.DetailResponse result = releaseFileService.updateReleaseFile(1L, updateRequest);

        // then
        assertThat(result).isNotNull();
        // JPA Dirty Checking 사용 - 엔티티 조회만 검증
        then(releaseFileRepository).should(times(1)).findById(1L);
        then(mapper).should(times(1)).toDetailResponse(any(ReleaseFile.class));
    }

    @Test
    @DisplayName("릴리즈 파일 삭제 - 성공")
    void deleteReleaseFile_Success() {
        // given
        given(releaseFileRepository.findById(anyLong())).willReturn(Optional.of(testReleaseFile));

        // when
        releaseFileService.deleteReleaseFile(1L);

        // then
        then(releaseFileRepository).should(times(1)).delete(any(ReleaseFile.class));
    }


    @Test
    @DisplayName("버전 범위 내 릴리즈 파일 조회 (install 제외) - 성공")
    void getReleaseFilesBetweenVersions_Success() {
        // given
        List<ReleaseFile> releaseFiles = List.of(testReleaseFile);
        List<ReleaseFileDto.SimpleResponse> simpleResponses = List.of(
                new ReleaseFileDto.SimpleResponse(
                        1L, "1.1.0", "database", "mariadb", "001_create_users_table.sql",
                        1024L, "abc123def456", 1, "Create users table"
                )
        );

        given(releaseFileRepository.findReleaseFilesBetweenVersions(anyString(), anyString(), anyString()))
                .willReturn(releaseFiles);
        given(mapper.toSimpleResponseList(any())).willReturn(simpleResponses);

        // when
        List<ReleaseFileDto.SimpleResponse> result =
                releaseFileService.getReleaseFilesBetweenVersions("infraeye2", "1.0.0", "1.1.0");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileCategory()).isEqualTo("database");
    }
}
