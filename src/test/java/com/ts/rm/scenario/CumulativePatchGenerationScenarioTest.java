package com.ts.rm.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ts.rm.config.TestQueryDslConfig;
import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.security.jwt.JwtTokenProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * 누적 패치 생성 시나리오 통합 테스트
 *
 * <p>현재 구조에 맞춰 완전히 재작성됨:
 * <ul>
 *   <li>버전 생성: ZIP 파일 업로드 방식 (multipart/form-data + Authorization)</li>
 *   <li>패치 생성: PatchDto.GenerateRequest (type, fromVersion, toVersion, createdByEmail 등)</li>
 *   <li>파일 구조: database/mariadb/{version}/, database/cratedb/{version}/</li>
 *   <li>스크립트: mariadb_patch.sh, cratedb_patch.sh</li>
 *   <li>README.md 포함</li>
 * </ul>
 *
 * <p>테스트 시나리오:
 * <ol>
 *   <li>버전 1.2.0, 1.3.0, 1.3.1 생성 (ZIP 업로드)</li>
 *   <li>누적 패치 생성 (1.2.0 → 1.3.1)</li>
 *   <li>생성된 파일 구조 및 스크립트 검증</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
@org.springframework.context.annotation.Import(TestQueryDslConfig.class)
@DisplayName("누적 패치 생성 시나리오 테스트")
public class CumulativePatchGenerationScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReleaseVersionRepository releaseVersionRepository;

    @Autowired
    private ReleaseFileRepository releaseFileRepository;

    @Autowired
    private PatchRepository patchRepository;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Value("${app.release.base-path}")
    private String baseReleasePath;

    private static final String CREATED_BY = "test@tscientific";
    private static final String JWT_TOKEN = "Bearer test-token";

    /**
     * 테스트 실행 전: 기존 데이터 정리
     */
    @BeforeEach
    void setUp() {
        // DB 정리는 @Transactional에 의해 자동 롤백됨
        System.out.println("\n[테스트 준비] 트랜잭션 시작 - 테스트 후 자동 롤백됨");

        // JwtTokenProvider Mock 설정
        when(jwtTokenProvider.getEmail(anyString())).thenReturn(CREATED_BY);
    }

    /**
     * 테스트 실행 후: 생성된 파일 디렉토리 정리
     */
    @AfterEach
    void tearDown() throws IOException {
        System.out.println("\n[테스트 정리] 생성된 파일 디렉토리 삭제");

        // 패치 디렉토리 삭제
        Path patchesDir = Paths.get(baseReleasePath, "patches");
        if (Files.exists(patchesDir)) {
            deleteDirectory(patchesDir);
            System.out.println("  ✓ patches/ 디렉토리 삭제");
        }

        // 버전 디렉토리 삭제
        Path versionsDir = Paths.get(baseReleasePath, "versions");
        if (Files.exists(versionsDir)) {
            deleteDirectory(versionsDir);
            System.out.println("  ✓ versions/ 디렉토리 삭제");
        }

        System.out.println("  ✓ DB 변경사항 자동 롤백 (@Transactional)");
        System.out.println("[테스트 정리 완료]\n");
    }

    /**
     * 시나리오 1: 누적 패치 생성 (1.2.0 → 1.3.1)
     */
    @Test
    @DisplayName("시나리오: 누적 패치 생성 (1.2.0 → 1.3.1)")
    void generateCumulativePatch_From120To131() throws Exception {
        System.out.println("\n========================================");
        System.out.println("시나리오: 누적 패치 생성 (1.2.0 → 1.3.1)");
        System.out.println("========================================");

        // Given: 버전 1.2.0, 1.3.0, 1.3.1 생성
        System.out.println("\n[Step 1] 버전 생성");
        createVersion("1.2.0", "Initial version for patch test");
        System.out.println("  ✅ 버전 1.2.0 생성 완료");

        createVersion("1.3.0", "Major feature update");
        System.out.println("  ✅ 버전 1.3.0 생성 완료");

        createVersion("1.3.1", "Bug fix release");
        System.out.println("  ✅ 버전 1.3.1 생성 완료");

        // When: 1.2.0 → 1.3.1 누적 패치 생성
        System.out.println("\n[Step 2] 누적 패치 생성 (1.2.0 → 1.3.1)");
        PatchDto.GenerateRequest patchRequest = PatchDto.GenerateRequest.builder()
                .type("STANDARD")
                .siteId(null)
                .fromVersion("1.2.0")
                .toVersion("1.3.1")
                .createdByEmail(CREATED_BY)
                .description("1.2.0에서 1.3.1로 업그레이드용 누적 패치")
                .engineerId(null) // 엔지니어 미지정
                .patchName(null) // 자동 생성
                .build();

        String patchResponse = mockMvc.perform(post("/api/patches/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.fromVersion").value("1.2.0"))
                .andExpect(jsonPath("$.data.toVersion").value("1.3.1"))
                .andExpect(jsonPath("$.data.releaseType").value("STANDARD"))
                .andExpect(jsonPath("$.data.patchName").exists())
                .andExpect(jsonPath("$.data.outputPath").exists())
                .andReturn().getResponse().getContentAsString();

        // Then: 응답 검증
        PatchDto.DetailResponse response = objectMapper.readTree(patchResponse)
                .get("data")
                .traverse(objectMapper)
                .readValueAs(PatchDto.DetailResponse.class);

        System.out.println("  ✅ 누적 패치 생성 완료");
        System.out.println("    - 패치 ID: " + response.patchId());
        System.out.println("    - 패치 이름: " + response.patchName());
        System.out.println("    - 출력 경로: " + response.outputPath());

        // Then: DB 검증
        System.out.println("\n[검증 1] DB - patch 테이블");
        var savedPatch = patchRepository.findById(response.patchId()).orElseThrow();
        assertThat(savedPatch.getFromVersion()).isEqualTo("1.2.0");
        assertThat(savedPatch.getToVersion()).isEqualTo("1.3.1");
        assertThat(savedPatch.getCreatedBy()).isEqualTo(CREATED_BY);
        System.out.println("  ✅ patch 레코드 저장 확인");

        // Then: 파일 시스템 검증
        System.out.println("\n[검증 2] 파일 시스템 - 디렉토리 구조");
        Path patchDir = Paths.get(baseReleasePath, response.outputPath());
        assertThat(Files.exists(patchDir))
                .as("패치 디렉토리 생성 확인: " + patchDir)
                .isTrue();
        System.out.println("  ✅ 패치 디렉토리 생성: " + patchDir.toAbsolutePath());

        // database 디렉토리 확인
        Path databaseDir = patchDir.resolve("database");
        assertThat(Files.exists(databaseDir))
                .as("database 디렉토리 생성 확인")
                .isTrue();
        System.out.println("  ✅ database/ 디렉토리 생성");

        // MariaDB 디렉토리 및 파일 확인
        Path mariadbDir = databaseDir.resolve("mariadb");
        assertThat(Files.exists(mariadbDir))
                .as("database/mariadb 디렉토리 생성 확인")
                .isTrue();

        // 버전별 SQL 파일 확인
        Path v130MariadbDir = mariadbDir.resolve("1.3.0");
        Path v131MariadbDir = mariadbDir.resolve("1.3.1");

        assertThat(Files.exists(v130MariadbDir))
                .as("database/mariadb/1.3.0 디렉토리 생성 확인")
                .isTrue();
        assertThat(Files.list(v130MariadbDir).count()).isEqualTo(2); // 1.patch_ddl.sql, 2.patch_dml.sql
        System.out.println("  ✅ database/mariadb/1.3.0/ (파일 2개)");

        assertThat(Files.exists(v131MariadbDir))
                .as("database/mariadb/1.3.1 디렉토리 생성 확인")
                .isTrue();
        assertThat(Files.list(v131MariadbDir).count()).isEqualTo(2); // 1.patch_ddl.sql, 2.patch_dml.sql
        System.out.println("  ✅ database/mariadb/1.3.1/ (파일 2개)");

        // CrateDB 디렉토리 확인
        Path cratedbDir = databaseDir.resolve("cratedb");
        assertThat(Files.exists(cratedbDir))
                .as("database/cratedb 디렉토리 생성 확인")
                .isTrue();
        System.out.println("  ✅ database/cratedb/ 디렉토리 생성");

        // Then: 스크립트 파일 검증
        System.out.println("\n[검증 3] 스크립트 파일");

        // mariadb_patch.sh 확인
        Path mariadbScript = patchDir.resolve("mariadb_patch.sh");
        assertThat(Files.exists(mariadbScript))
                .as("mariadb_patch.sh 생성 확인")
                .isTrue();
        System.out.println("  ✅ mariadb_patch.sh 생성");

        String scriptContent = Files.readString(mariadbScript);
        assertThat(scriptContent).contains("#!/bin/bash");
        assertThat(scriptContent).contains("1.2.0");
        assertThat(scriptContent).contains("1.3.1");
        System.out.println("    - Shebang 포함: #!/bin/bash");
        System.out.println("    - 버전 범위 표시 확인");

        // VERSION_HISTORY INSERT 쿼리 포함 확인
        assertThat(scriptContent).contains("CM_DB.VERSION_HISTORY");
        assertThat(scriptContent).contains("APPLIED_BY");
        System.out.println("    - VERSION_HISTORY INSERT 쿼리 포함 확인");

        // cratedb_patch.sh 확인
        Path cratedbScript = patchDir.resolve("cratedb_patch.sh");
        if (Files.exists(cratedbScript)) {
            System.out.println("  ✅ cratedb_patch.sh 생성");
        }

        // Then: README.md 검증
        System.out.println("\n[검증 4] README.md");
        Path readmeFile = patchDir.resolve("README.md");
        assertThat(Files.exists(readmeFile))
                .as("README.md 생성 확인")
                .isTrue();
        System.out.println("  ✅ README.md 생성");

        String readmeContent = Files.readString(readmeFile);
        assertThat(readmeContent).contains("1.2.0");
        assertThat(readmeContent).contains("1.3.1");
        assertThat(readmeContent).contains("1.3.0"); // 중간 버전 포함 확인
        System.out.println("    - 버전 정보 포함 확인");
        System.out.println("    - 중간 버전 (1.3.0) 포함 확인");

        // 최종 요약
        System.out.println("\n========================================");
        System.out.println("✅ 시나리오 완료!");
        System.out.println("========================================");
        System.out.println("버전 범위: 1.2.0 → 1.3.1");
        System.out.println("포함 버전: 1.3.0, 1.3.1");
        System.out.println("생성된 파일:");
        System.out.println("  - mariadb_patch.sh");
        System.out.println("  - cratedb_patch.sh (조건부)");
        System.out.println("  - README.md");
        System.out.println("  - database/mariadb/1.3.0/*.sql (2개)");
        System.out.println("  - database/mariadb/1.3.1/*.sql (2개)");
        System.out.println("  - database/cratedb/1.3.0/*.sql (1개)");
        System.out.println("  - database/cratedb/1.3.1/*.sql (1개)");
        System.out.println("========================================\n");
    }

    /**
     * 시나리오 2: 단일 버전 간 패치 생성 (1.0.0 → 1.0.1)
     */
    @Test
    @DisplayName("시나리오: 단일 버전 간 패치 생성 (1.0.0 → 1.0.1)")
    void generatePatch_SingleVersionJump() throws Exception {
        System.out.println("\n========================================");
        System.out.println("시나리오: 단일 버전 간 패치 생성");
        System.out.println("========================================");

        // Given: 버전 1.0.0, 1.0.1 생성
        System.out.println("\n[Step 1] 버전 생성");
        createVersion("1.0.0", "Initial release");
        System.out.println("  ✅ 버전 1.0.0 생성 완료");

        createVersion("1.0.1", "Hotfix");
        System.out.println("  ✅ 버전 1.0.1 생성 완료");

        // When: 1.0.0 → 1.0.1 패치 생성
        System.out.println("\n[Step 2] 패치 생성 (1.0.0 → 1.0.1)");
        PatchDto.GenerateRequest patchRequest = PatchDto.GenerateRequest.builder()
                .type("STANDARD")
                .fromVersion("1.0.0")
                .toVersion("1.0.1")
                .createdByEmail(CREATED_BY)
                .description("Hotfix patch")
                .build();

        mockMvc.perform(post("/api/patches/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.fromVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.toVersion").value("1.0.1"));

        System.out.println("  ✅ 패치 생성 성공");
        System.out.println("\n========================================\n");
    }

    /**
     * 시나리오 3: 패치 생성 실패 - From 버전 > To 버전
     */
    @Test
    @DisplayName("시나리오: 패치 생성 실패 - From 버전 > To 버전")
    void generatePatch_InvalidVersionOrder() throws Exception {
        System.out.println("\n========================================");
        System.out.println("시나리오: 패치 생성 실패 - 역순 버전");
        System.out.println("========================================");

        // Given: 버전 1.0.0, 1.1.0 생성
        createVersion("1.0.0", "Initial");
        createVersion("1.1.0", "Update");

        // When: 1.1.0 → 1.0.0 패치 생성 (역순)
        PatchDto.GenerateRequest patchRequest = PatchDto.GenerateRequest.builder()
                .type("STANDARD")
                .fromVersion("1.1.0")
                .toVersion("1.0.0")
                .createdByEmail(CREATED_BY)
                .build();

        // Then: 400 Bad Request
        mockMvc.perform(post("/api/patches/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));

        System.out.println("  ✅ 예상대로 실패 (400 Bad Request)");
        System.out.println("========================================\n");
    }

    /**
     * 시나리오 4: 패치 생성 실패 - 존재하지 않는 버전
     */
    @Test
    @DisplayName("시나리오: 패치 생성 실패 - 존재하지 않는 버전")
    void generatePatch_VersionNotFound() throws Exception {
        System.out.println("\n========================================");
        System.out.println("시나리오: 패치 생성 실패 - 존재하지 않는 버전");
        System.out.println("========================================");

        // Given: 버전 1.0.0만 존재
        createVersion("1.0.0", "Only version");

        // When: 1.0.0 → 9.9.9 패치 생성 (9.9.9 존재하지 않음)
        PatchDto.GenerateRequest patchRequest = PatchDto.GenerateRequest.builder()
                .type("STANDARD")
                .fromVersion("1.0.0")
                .toVersion("9.9.9")
                .createdByEmail(CREATED_BY)
                .build();

        // Then: 404 Not Found
        mockMvc.perform(post("/api/patches/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"));

        System.out.println("  ✅ 예상대로 실패 (404 Not Found)");
        System.out.println("========================================\n");
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    /**
     * 표준 릴리즈 버전 생성 헬퍼 메서드
     *
     * @param version 버전 (예: 1.2.0)
     * @param comment 코멘트
     */
    private void createVersion(String version, String comment) throws Exception {
        // 테스트용 ZIP 파일 생성 (database/mariadb, database/cratedb 구조)
        byte[] zipContent = createTestZipFile(version);

        MockMultipartFile patchFiles = new MockMultipartFile(
                "patchFiles",
                "patch_" + version + ".zip",
                "application/zip",
                zipContent
        );

        // @ModelAttribute를 위해 파라미터로 전송 (파일이 아닌 일반 파라미터)
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/releases/standard/versions")
                        .file(patchFiles)
                        .param("version", version)
                        .param("releaseCategory", "PATCH")
                        .param("comment", comment)
                        .header("Authorization", JWT_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    /**
     * 테스트용 ZIP 파일 생성
     *
     * <p>구조:
     * <pre>
     * database/
     *   MARIADB/        ← 대문자 필수
     *     1.patch_ddl.sql
     *     2.patch_dml.sql
     *   CRATEDB/        ← 대문자 필수
     *     1.patch_crate.sql
     * </pre>
     */
    private byte[] createTestZipFile(String version) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        // database/MARIADB/1.patch_ddl.sql (대문자 필수)
        addZipEntry(zos, "database/MARIADB/1.patch_ddl.sql",
                String.format("-- MariaDB DDL for version %s\nCREATE TABLE IF NOT EXISTS test_%s (id INT);",
                        version, version.replace(".", "_")));

        // database/MARIADB/2.patch_dml.sql (대문자 필수)
        addZipEntry(zos, "database/MARIADB/2.patch_dml.sql",
                String.format("-- MariaDB DML for version %s\nINSERT INTO test_%s VALUES (1);",
                        version, version.replace(".", "_")));

        // database/CRATEDB/1.patch_crate.sql (대문자 필수)
        addZipEntry(zos, "database/CRATEDB/1.patch_crate.sql",
                String.format("-- CrateDB for version %s\nCREATE TABLE test_%s (id INT);",
                        version, version.replace(".", "_")));

        zos.close();
        return baos.toByteArray();
    }

    /**
     * ZIP 엔트리 추가 헬퍼
     */
    private void addZipEntry(ZipOutputStream zos, String entryName, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /**
     * 디렉토리 재귀 삭제
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
