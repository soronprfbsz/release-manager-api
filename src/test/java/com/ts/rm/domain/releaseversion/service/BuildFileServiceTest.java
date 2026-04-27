package com.ts.rm.domain.releaseversion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * BuildFileService 단위 테스트.
 *
 * <p>ZipExtractUtil/FileChecksumUtil 같은 정적 유틸리티는 그대로 호출하고,
 * 파일시스템은 {@link TempDir} 로 격리.
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

    @BeforeEach
    void setUp() {
        // baseReleasePath 는 @Value 로 주입되므로 ReflectionTestUtils 로 직접 설정
        ReflectionTestUtils.setField(buildFileService, "baseReleasePath", tempDir.toString());
    }

    private byte[] makeZip(String... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String name : entries) {
                ZipEntry e = new ZipEntry(name);
                zos.putNextEntry(e);
                if (!name.endsWith("/")) {
                    zos.write(("payload:" + name).getBytes());
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
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
    @DisplayName("정상: web/engine/etc 파일이 추출되고 ReleaseFile 행이 카테고리별로 생성됨")
    void uploadBuildZip_valid_extractsAndPersists() throws IOException {
        ReleaseVersion base = buildBase();
        ReleaseVersion buildVer = build(99L, base, 260427);

        Path buildDir = tempDir.resolve("versions/p/standard/1.1.x/1.1.0/builds/260427");
        // resolveBuildBasePath 가 buildDir 을 반환하도록 mock
        given(releaseVersionRepository.findById(99L)).willReturn(Optional.of(buildVer));
        given(fileSystemService.resolveBuildBasePath(base, 260427)).willReturn(buildDir);
        given(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(99L))
                .willReturn(Collections.emptyList());
        given(releaseFileRepository.save(any(ReleaseFile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        byte[] zip = makeZip("web/foo.war", "engine/NC_SMS/x.jar", "etc/config.yml");

        BuildFileService.UploadResult result = buildFileService.uploadBuildZip(99L, zip, "u@x");

        assertThat(result.buildVersionId()).isEqualTo(99L);
        assertThat(result.createdFiles()).hasSize(3);

        // 파일이 실제로 추출되었는지 확인
        assertThat(buildDir.resolve("web/foo.war")).exists();
        assertThat(buildDir.resolve("engine/NC_SMS/x.jar")).exists();
        assertThat(buildDir.resolve("etc/config.yml")).exists();

        // 카테고리 매핑 확인
        var categories = result.createdFiles().stream()
                .map(ReleaseFile::getFileCategory)
                .sorted()
                .toList();
        assertThat(categories)
                .containsExactlyInAnyOrder(FileCategory.WEB, FileCategory.ENGINE, FileCategory.ETC);

        // executionOrder 가 1 부터 증가
        var orders = result.createdFiles().stream()
                .map(ReleaseFile::getExecutionOrder)
                .sorted()
                .toList();
        assertThat(orders).containsExactly(1, 2, 3);

        // fileType 추론 확인 (확장자 대문자)
        var types = result.createdFiles().stream().map(ReleaseFile::getFileType).toList();
        assertThat(types).contains("WAR", "JAR", "YML");
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

        byte[] zip = makeZip("web/foo.war");

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

        byte[] zip = makeZip("database/v1.sql");

        assertThatThrownBy(() -> buildFileService.uploadBuildZip(99L, zip, "u@x"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("database/");
    }

    @Test
    @DisplayName("거부: buildVersionId 가 존재하지 않으면 RELEASE_VERSION_NOT_FOUND")
    void uploadBuildZip_buildNotFound_rejected() throws IOException {
        given(releaseVersionRepository.findById(404L)).willReturn(Optional.empty());

        byte[] zip = makeZip("web/foo.war");
        assertThatThrownBy(() -> buildFileService.uploadBuildZip(404L, zip, "u@x"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RELEASE_VERSION_NOT_FOUND);
    }

    @Test
    @DisplayName("정상: 기존 ReleaseFile 이 있으면 executionOrder 가 maxOrder+1 부터 시작")
    void uploadBuildZip_existingFiles_continuesOrder() throws IOException {
        ReleaseVersion base = buildBase();
        ReleaseVersion buildVer = build(99L, base, 260427);
        Path buildDir = tempDir.resolve("versions/p/standard/1.1.x/1.1.0/builds/260427");

        given(releaseVersionRepository.findById(99L)).willReturn(Optional.of(buildVer));
        given(fileSystemService.resolveBuildBasePath(base, 260427)).willReturn(buildDir);
        // 기존에 executionOrder=5 인 행이 있다고 mock
        ReleaseFile existing = ReleaseFile.builder()
                .releaseFileId(1L)
                .executionOrder(5)
                .build();
        var existingList = new ArrayList<ReleaseFile>();
        existingList.add(existing);
        given(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(99L))
                .willReturn(existingList);
        given(releaseFileRepository.save(any(ReleaseFile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        byte[] zip = makeZip("web/foo.war");
        BuildFileService.UploadResult result = buildFileService.uploadBuildZip(99L, zip, "u@x");

        // 시작 order 가 5+1 = 6
        assertThat(result.createdFiles().get(0).getExecutionOrder()).isEqualTo(6);
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
        org.mockito.Mockito.verify(releaseFileRepository, org.mockito.Mockito.never())
                .save(any(ReleaseFile.class));
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
        given(fileSystemService.resolveBuildBasePath(base, 260427)).willReturn(buildDir);
        given(releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(99L))
                .willReturn(Collections.emptyList());
        given(releaseFileRepository.save(any(ReleaseFile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        byte[] zip = makeZip("web/foo.war", "engine/x.jar");

        var result = buildFileService.createBuildWithZip(10L, req, zip, "u@x");

        assertThat(result.buildVersionId()).isEqualTo(99L);
        assertThat(result.uploadedFileCount()).isEqualTo(2);
        assertThat(buildDir.resolve("web/foo.war")).exists();
        assertThat(buildDir.resolve("engine/x.jar")).exists();
    }
}
