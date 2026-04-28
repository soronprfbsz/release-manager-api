package com.ts.rm.domain.patch.controller;

import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * PatchController Swagger 문서화 인터페이스
 */
@Tag(name = "패치", description = "패치 생성 및 조회 API")
@SwaggerResponse
public interface PatchControllerDocs {

    @Operation(
            summary = "패치 생성",
            description = "from..to 사이의 누적 DB 패치를 생성하고, buildSelection.enabled=true 인 경우 "
                    + "선택된 WEB/엔진 빌드와 함께 ETC 동행을 처리한다. 핫픽스는 누적 DB 시퀀스에 미포함.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PatchDetailApiResponse.class)
                    )
            )
    )
    ApiResponse<PatchDto.DetailResponse> generatePatch(
            @Valid @RequestBody PatchDto.GenerateRequest request
    );

    @Operation(
            summary = "패치 상세 조회",
            description = "패치 ID로 상세 정보를 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PatchDetailApiResponse.class)
                    )
            )
    )
    ApiResponse<PatchDto.DetailResponse> getPatch(
            @PathVariable Long id
    );

    @Operation(
            summary = "패치 목록 조회",
            description = "패치 목록을 페이징하여 조회합니다. projectId, releaseType, customerId로 필터링 가능. page, size, sort 파라미터 사용 가능\n\n"
                    + "정렬 가능 필드:\n"
                    + "- patchName: 패치명\n"
                    + "- customerName: 고객사명\n"
                    + "- engineerName: 담당 엔지니어명\n"
                    + "- createdByEmail: 생성자\n"
                    + "- createdAt: 생성일시",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PatchListApiResponse.class)
                    )
            )
    )
    ApiResponse<Page<PatchDto.ListResponse>> listPatches(
            @Parameter(description = "프로젝트 ID (예: infraeye2)")
            @RequestParam(required = false) String projectId,

            @Parameter(description = "릴리즈 타입 (STANDARD/CUSTOM)")
            @RequestParam(required = false) String releaseType,

            @Parameter(description = "고객사 코드 (특정 고객사의 패치만 조회)")
            @RequestParam(required = false) String customerCode,

            @ParameterObject Pageable pageable
    );

    @Operation(
            summary = "패치 파일 구조 조회",
            description = "패치 ZIP 파일 내부 구조를 재귀적으로 조회합니다.\n\n"
                    + "응답에는 다음이 포함됩니다:\n"
                    + "- root: 루트 디렉토리 (재귀 구조)\n"
                    + "  - mariadb: MariaDB 패치 파일 및 디렉토리\n"
                    + "  - cratedb: CrateDB 패치 파일 및 디렉토리\n"
                    + "  - README.md: 패치 설명 파일\n"
                    + "  - 실행 스크립트 파일들\n\n"
                    + "각 노드는 FileNode 인터페이스를 구현하며 FileInfo(파일) 또는 DirectoryNode(디렉토리)입니다.\n"
                    + "DirectoryNode는 children을 통해 재귀적으로 하위 파일/디렉토리를 포함합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FileStructureApiResponse.class),
                            examples = @ExampleObject(
                                    name = "패치 파일 구조 조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": {
                                                "patchId": 1,
                                                "patchName": "infraeye2_1.1.0_to_1.2.0_standard_patch",
                                                "root": {
                                                  "name": "infraeye2_1.1.0_to_1.2.0_standard_patch",
                                                  "type": "directory",
                                                  "path": "",
                                                  "children": [
                                                    {
                                                      "name": "README.md",
                                                      "type": "file",
                                                      "path": "README.md",
                                                      "size": 1234
                                                    },
                                                    {
                                                      "name": "database",
                                                      "type": "directory",
                                                      "path": "database",
                                                      "children": [
                                                        {
                                                          "name": "mariadb",
                                                          "type": "directory",
                                                          "path": "database/mariadb",
                                                          "children": [
                                                            {
                                                              "name": "source_files",
                                                              "type": "directory",
                                                              "path": "database/mariadb/source_files",
                                                              "children": [
                                                                {
                                                                  "name": "1.1.1",
                                                                  "type": "directory",
                                                                  "path": "database/mariadb/source_files/1.1.1",
                                                                  "children": [
                                                                    {
                                                                      "name": "1.patch_mariadb_ddl.sql",
                                                                      "type": "file",
                                                                      "path": "database/mariadb/source_files/1.1.1/1.patch_mariadb_ddl.sql",
                                                                      "size": 5678
                                                                    }
                                                                  ]
                                                                }
                                                              ]
                                                            },
                                                            {
                                                              "name": "mariadb_cumulative_patch.sh",
                                                              "type": "file",
                                                              "path": "database/mariadb/mariadb_cumulative_patch.sh",
                                                              "size": 2345
                                                            }
                                                          ]
                                                        },
                                                        {
                                                          "name": "cratedb",
                                                          "type": "directory",
                                                          "path": "database/cratedb",
                                                          "children": [
                                                            {
                                                              "name": "cratedb_cumulative_patch.sh",
                                                              "type": "file",
                                                              "path": "database/cratedb/cratedb_cumulative_patch.sh",
                                                              "size": 3456
                                                            }
                                                          ]
                                                        }
                                                      ]
                                                    }
                                                  ]
                                                }
                                              }
                                            }
                                            """
                            )
                    )
            )
    )
    ApiResponse<PatchDto.FileStructureResponse> getPatchFileStructure(
            @PathVariable Long id
    );

    @Operation(
            summary = "패치 다운로드",
            description = "패치 파일을 ZIP 형식으로 다운로드합니다.\n\n"
                    + "ZIP 파일에는 다음이 포함됩니다:\n"
                    + "```\n"
                    + "patch/\n"
                    + "├── mariadb_patch.sh      # MariaDB 패치 실행 스크립트\n"
                    + "├── cratedb_patch.sh      # CrateDB 패치 실행 스크립트 (파일 있을 때만)\n"
                    + "├── database/\n"
                    + "│   ├── mariadb/          # MariaDB SQL 파일\n"
                    + "│   └── cratedb/          # CrateDB SQL 파일\n"
                    + "├── etc/                  # 릴리즈 파일로 등록된 기타 파일 (예: InfraEye CLI)\n"
                    + "└── README.md             # 패치 설명 파일\n"
                    + "```\n\n"
                    + "**응답 헤더**:\n"
                    + "- `X-Uncompressed-Size`: 압축 전 총 파일 크기 (바이트) - 진행률 표시용"
    )
    void downloadPatch(
            @PathVariable Long id,
            HttpServletResponse response
    ) throws IOException;

    @Operation(
            summary = "패치 파일 내용 조회",
            description = "패치 디렉토리 내의 특정 파일 내용을 조회합니다.\n\n"
                    + "**사용 예시**:\n"
                    + "- `GET /api/patches/1/content?path=mariadb/source_files/1.1.1/1.patch_mariadb_ddl.sql`\n"
                    + "- `GET /api/patches/1/content?path=README.md`\n\n"
                    + "**제약사항**:\n"
                    + "- 파일 크기: 최대 10MB\n"
                    + "- 파일 형식: 텍스트 파일만 지원 (SQL, MD, SH 등)\n"
                    + "- 보안: 경로 탐색 공격 방지 (../ 등 차단)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FileContentApiResponse.class)
                    )
            )
    )
    ApiResponse<PatchDto.FileContentResponse> getFileContent(
            @PathVariable Long id,

            @Parameter(description = "파일 상대 경로", example = "mariadb/source_files/1.1.0/1.patch_mariadb_ddl.sql", required = true)
            @RequestParam String path
    );

    @Operation(
            summary = "패치 삭제",
            description = "패치를 삭제합니다.\n\n"
                    + "**삭제 범위**:\n"
                    + "- DB 레코드 (patch_file 테이블)\n"
                    + "- 실제 파일 디렉토리 (patches/{patchName}/ 전체)\n\n"
                    + "**주의사항**:\n"
                    + "- 삭제된 데이터는 복구할 수 없습니다.\n"
                    + "- 디렉토리 삭제 실패 시에도 DB 레코드는 삭제됩니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    example = "{\"status\": \"success\", \"data\": null}"
                            )
                    )
            )
    )
    ApiResponse<Void> deletePatch(
            @PathVariable Long id
    );

    @Operation(
            summary = "패치 일괄 삭제",
            description = """
                    여러 패치를 일괄 삭제합니다.

                    **삭제 범위**:
                    - DB 레코드 (patch 테이블)
                    - 실제 파일 디렉토리 (patches/{patchName}/ 전체)

                    **주의사항**:
                    - 삭제된 데이터는 복구할 수 없습니다.
                    - 일부 패치를 찾을 수 없으면 전체 요청이 실패합니다.

                    **사용 예시**:
                    - `DELETE /api/patches?ids=1,2,3`
                    """,
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "삭제 성공",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = BatchDeleteApiResponse.class)
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "일부 패치를 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400",
                            description = "패치 ID 목록이 비어있음"
                    )
            }
    )
    ApiResponse<PatchDto.BatchDeleteResponse> batchDeletePatches(
            @Parameter(description = "삭제할 패치 ID 목록 (쉼표로 구분)", example = "1,2,3", required = true)
            @RequestParam List<Long> ids
    );

    // ========================================
    // 커스텀 패치 API
    // ========================================

    @Operation(
            summary = "커스텀 버전 보유 고객사 목록 조회",
            description = "프로젝트 내에서 커스텀 버전이 존재하는 고객사 목록을 조회합니다.\n\n"
                    + "커스텀 패치 생성 시 고객사 선택 드롭다운에 사용됩니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerListApiResponse.class)
                    )
            )
    )
    ApiResponse<List<PatchDto.CustomerWithCustomVersions>> getCustomersWithCustomVersions(
            @Parameter(description = "프로젝트 ID", example = "infraeye2", required = true)
            @RequestParam String projectId
    );

    @Operation(
            summary = "고객사별 커스텀 버전 목록 조회",
            description = "특정 고객사의 베이스 버전과 커스텀 버전 목록을 조회합니다.\n\n"
                    + "**응답 구조**:\n"
                    + "- 첫 번째 항목: 베이스 버전 (표준본, isBaseVersion=true)\n"
                    + "- 이후 항목들: 커스텀 버전들 (최신순, isBaseVersion=false)\n\n"
                    + "**사용 예시**:\n"
                    + "- From 버전: 베이스 버전(1.1.0) 선택 → To 버전: 커스텀 버전(1.1.0-C2) 선택\n"
                    + "- From 버전: 커스텀 버전(1.1.0-C1) 선택 → To 버전: 커스텀 버전(1.1.0-C3) 선택",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomVersionListApiResponse.class)
                    )
            )
    )
    ApiResponse<List<PatchDto.CustomVersionSelectOption>> getCustomVersionsByCustomer(
            @Parameter(description = "프로젝트 ID", example = "infraeye2", required = true)
            @RequestParam String projectId,

            @Parameter(description = "고객사 ID", example = "1", required = true)
            @PathVariable Long customerId
    );

    @Operation(
            summary = "커스텀 패치 생성",
            description = "고객사의 커스텀 버전 범위에 대한 누적 패치를 생성합니다.\n\n"
                    + "**프로세스**:\n"
                    + "1. From ~ To 커스텀 버전 사이의 파일을 수집\n"
                    + "2. 누적 패치 디렉토리 생성 (patches/{projectId}/custom/{customerCode}/{patchName})\n"
                    + "3. 패치 스크립트 및 README 생성\n"
                    + "4. DB에 패치 정보 저장",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PatchDetailApiResponse.class)
                    )
            )
    )
    ApiResponse<PatchDto.DetailResponse> generateCustomPatch(
            @Valid @RequestBody PatchDto.GenerateCustomPatchRequest request
    );

    /**
     * Swagger 스키마용 wrapper 클래스 - 패치 상세 응답
     */
    @Schema(description = "패치 상세 API 응답")
    class PatchDetailApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "패치 상세 정보")
        public PatchDto.DetailResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 패치 목록 응답
     */
    @Schema(description = "패치 목록 API 응답")
    class PatchListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "페이징된 패치 목록")
        public PageData data;

        @Schema(description = "페이지 데이터")
        static class PageData {
            @Schema(description = "패치 목록")
            public List<PatchDto.ListResponse> content;

            @Schema(description = "전체 페이지 수", example = "1")
            public int totalPages;

            @Schema(description = "전체 요소 수", example = "10")
            public long totalElements;

            @Schema(description = "페이지 크기", example = "10")
            public int size;

            @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
            public int number;
        }
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 파일 구조 응답
     */
    @Schema(description = "파일 구조 API 응답")
    class FileStructureApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "파일 구조")
        public PatchDto.FileStructureResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 파일 내용 응답
     */
    @Schema(description = "파일 내용 API 응답")
    class FileContentApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "파일 내용")
        public PatchDto.FileContentResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 커스텀 버전 보유 고객사 목록 응답
     */
    @Schema(description = "커스텀 버전 보유 고객사 목록 API 응답")
    class CustomerListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "고객사 목록")
        public List<PatchDto.CustomerWithCustomVersions> data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 커스텀 버전 목록 응답
     */
    @Schema(description = "커스텀 버전 목록 API 응답")
    class CustomVersionListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "커스텀 버전 목록")
        public List<PatchDto.CustomVersionSelectOption> data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 일괄 삭제 응답
     */
    @Schema(description = "패치 일괄 삭제 API 응답")
    class BatchDeleteApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "일괄 삭제 결과")
        public PatchDto.BatchDeleteResponse data;
    }
}
