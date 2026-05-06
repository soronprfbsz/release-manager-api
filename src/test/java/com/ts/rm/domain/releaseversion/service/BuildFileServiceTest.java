package com.ts.rm.domain.releaseversion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BuildFileService 단위 테스트.
 *
 * <p>ZipExtractUtil 같은 정적 유틸리티는 그대로 호출하고, 파일시스템은 {@link TempDir} 로 격리.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BuildFileService 테스트")
class BuildFileServiceTest {

    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @Mock
    private ReleaseFileRepository releaseFileRepository;

    @Mock
    private ReleaseVersionFileSystemService fileSystemService;

    @Mock
    private ReleaseVersionService releaseVersionService;

    @InjectMocks
    private BuildFileService buildFileService;

    @TempDir
    Path tempDir;

    private Path makeZip(String... entries) throws IOException {
        Path zipPath = Files.createTempFile(tempDir, "test-zip-", ".zip");
        try (OutputStream out = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            for (String name : entries) {
                ZipEntry e = new ZipEntry(name);
                zos.putNextEntry(e);
                if (!name.endsWith("/")) {
                    zos.write(("payload:" + name).getBytes());
                }
                zos.closeEntry();
            }
        }
        return zipPath;
    }

    private ReleaseVersion buildBase() {
        return ReleaseVersion.builder()
                .releaseVersionId(10L)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .hotfixVersion(0).buildVersion(0)
                .build();
    }

    private ReleaseVersion build(Long id, ReleaseVersion base, int buildVersion) {
        return ReleaseVersion.builder()
                .releaseVersionId(id)
                .releaseType(base.getReleaseType())
                .version(base.getVersion())
                .majorVersion(base.getMajorVersion())
                .minorVersion(base.getMinorVersion())
                .patchVersion(base.getPatchVersion())
                .hotfixVersion(0)
                .buildVersion(buildVersion)
                .buildBaseVersion(base)
                .build();
    }

    @Test
    @DisplayName("정상: web/engine 파일이 추출되고 ReleaseFile 행은 생성하지 않음")
    void uploadBuildZip_valid_extractsWithoutPersistingReleaseFiles() throws IOException {
        ReleaseVersion base = buildBase();
        ReleaseVersion buildVer = build(99L, base, 260427);

        Path buildDir = tempDir.resolve("versions/p/standard/1.1.x/1.1.0/builds/260427");
        // resolveBuildBasePath 가 buildDir 을 반환하도록 mock
        given(releaseVersionRepository.findById(99L)).willReturn(Optional.of(buildVer));
        given(fileSystemService.resolveBuildBasePath(buildVer)).willReturn(buildDir);

        // 새 구조: engine/<엔진명> 단일 파일
        Path zip = makeZip("web/foo.war", "engine/NC_SMS");

        BuildFileService.UploadResult result = buildFileService.uploadBuildZip(99L, zip, "u@x");

        assertThat(result.buildVersionId()).isEqualTo(99L);
        assertThat(result.uploadedFileCount()).isEqualTo(2);

        // 파일이 실제로 추출되었는지 확인
        assertThat(buildDir.resolve("web/foo.war")).exists();
        assertThat(buildDir.resolve("engine/NC_SMS")).exists();

        verify(releaseFileRepository, never()).save(any(ReleaseFile.class));
    }

    @Test
    @DisplayName("거부: 빌드가 아닌 버전 (build_version=0) 업로드 시 INVALID_INPUT_VALUE")
    void uploadBuildZip_notABuild_rejected() throws IOException {
        ReleaseVersion notBuild = ReleaseVersion.builder()
                .releaseVersionId(50L)
                .releaseType("STANDARD")
                .version("1.1.0")
                .majorVersion(1).minorVersion(1).patchVersion(0)
                .hotfixVersion(0).buildVersion(0)  // 빌드 아님
                .build();

        given(releaseVersionRepository.findById(50L)).willReturn(Optional.of(notBuild));

        Path zip = makeZip("web/foo.war");

        assertThatThrownBy(() -> buildFileService.uploadBuildZip(50L, zip, "u@x"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("거부: ZIP 루트에 허용되지 않은 디렉토리 포함 시 INVALID_INPUT_VALUE")
    void uploadBuildZip_invalidZipRoot_rejected() throws IOException {
        ReleaseVersion base = buildBase();
        ReleaseVersion buildVer = build(99L, base, 260427);
        given(releaseVersionRepository.findById(99L)).willReturn(Optional.of(buildVer));

        Path zip = makeZip("database/v1.sql");

        assertThatThrownBy(() -> buildFileService.uploadBuildZip(99L, zip, "u@x"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("database/");
    }

    @Test
    @DisplayName("거부: buildVersionId 가 존재하지 않으면 RELEASE_VERSION_NOT_FOUND")
    void uploadBuildZip_buildNotFound_rejected() throws IOException {
        given(releaseVersionRepository.findById(404L)).willReturn(Optional.empty());

        Path zip = makeZip("web/foo.war");
        assertThatThrownBy(() -> buildFileService.uploadBuildZip(404L, zip, "u@x"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RELEASE_VERSION_NOT_FOUND);
    }

    // ========================================
    // createBuildWithZip 오케스트레이터 테스트
    // ========================================

    @Test
    @DisplayName("createBuildWithZip - ZIP 미동봉 시 빌드만 생성, uploadedFileCount=0")
    void createBuildWithZip_noZip_buildOnly() {
        com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto.CreateBuildRequest req =
                com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto.CreateBuildRequest.builder()
                        .comment("빌드 행만").buildVersion(260427).build();

        com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto.CreateBuildResponse innerResp =
                new com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto.CreateBuildResponse(
                        99L, "1.1.0", 260427, "1.1.0.260427", 0);
        given(releaseVersionService.createBuild(10L, req, "u@x")).willReturn(innerResp);

        var result = buildFileService.createBuildWithZip(10L, req, null, "u@x");

        assertThat(result.buildVersionId()).isEqualTo(99L);
        assertThat(result.fullVersion()).isEqualTo("1.1.0.260427");
        assertThat(result.uploadedFileCount()).isEqualTo(0);
        // ZIP 처리 경로가 호출되지 않아야 함
        org.mockito.Mockito.verifyNoInteractions(fileSystemService);
        verify(releaseFileRepository, never()).save(any(ReleaseFile.class));
    }

    @Test
    @DisplayName("createBuildWithZip - ZIP 동봉 시 빌드 생성 + 파일 업로드, uploadedFileCount=N")
    void createBuildWithZip_withZip_filesUploaded() throws IOException {
        ReleaseVersion base = buildBase();
        ReleaseVersion buildVer = build(99L, base, 260427);

        var req = com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto.CreateBuildRequest.builder()
                .comment("ZIP 동봉").buildVersion(260427).build();

        var innerResp =
                new com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto.CreateBuildResponse(
                        99L, "1.1.0", 260427, "1.1.0.260427", 0);
        given(releaseVersionService.createBuild(10L, req, "u@x")).willReturn(innerResp);

        // uploadBuildZip 흐름을 위한 mock
        Path buildDir = tempDir.resolve("versions/p/standard/1.1.x/1.1.0/builds/260427");
        given(releaseVersionRepository.findById(99L)).willReturn(Optional.of(buildVer));
        given(fileSystemService.resolveBuildBasePath(buildVer)).willReturn(buildDir);

        Path zip = makeZip("web/foo.war", "engine/x.jar");

        var result = buildFileService.createBuildWithZip(10L, req, zip, "u@x");

        assertThat(result.buildVersionId()).isEqualTo(99L);
        assertThat(result.uploadedFileCount()).isEqualTo(2);
        assertThat(buildDir.resolve("web/foo.war")).exists();
        assertThat(buildDir.resolve("engine/x.jar")).exists();
        verify(releaseFileRepository, never()).save(any(ReleaseFile.class));
    }
}
