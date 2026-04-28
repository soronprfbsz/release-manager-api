package com.ts.rm.domain.releaseversion.controller;

import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * ReleaseVersionController Swagger 문서화 인터페이스
 */
@Tag(name = "릴리즈 버전", description = "릴리즈 버전 관리 API")
@SwaggerResponse
public interface ReleaseVersionControllerDocs {

    @Operation(
            summary = "표준 릴리즈 버전 생성",
            description = "ZIP 파일로 표준 릴리즈 버전을 생성합니다.\n\n"
                    + "**ZIP 파일 구조 규칙**:\n"
                    + "```\n"
                    + "patch_1.1.3.zip\n"
                    + "├── database/              ← 데이터베이스 관련 파일\n"
                    + "│   ├── mariadb/\n"
                    + "│   │   ├── 1.patch_ddl.sql\n"
                    + "│   │   └── 2.patch_dml.sql\n"
                    + "│   └── cratedb/\n"
                    + "│       └── 1.patch_crate.sql\n"
                    + "├── web/                   ← 웹 애플리케이션 빌드 산출물\n"
                    + "│   └── build/\n"
                    + "│       ├── frontend.war\n"
                    + "│       └── admin.war\n"
                    + "├── engine/                ← 엔진 빌드 산출물\n"
                    + "│   ├── build/\n"
                    + "│   │   └── engine.jar\n"
                    + "│   └── scripts/\n"
                    + "│       └── startup.sh\n"
                    + "└── install/               ← 설치본 파일 (패치 생성 시 제외됨)\n"
                    + "    ├── installer.exe\n"
                    + "    └── setup_guide.md\n"
                    + "```\n\n"
                    + "**카테고리 폴더**:\n"
                    + "- `database/` - 데이터베이스 SQL 스크립트\n"
                    + "- `web/` - 웹 애플리케이션 빌드 산출물 (WAR, JAR)\n"
                    + "- `engine/` - 엔진 빌드 산출물 및 실행 스크립트\n"
                    + "- `install/` - 설치본 파일 (※ 패치 생성 시 제외)\n"
                    + "- 최소 1개 이상의 카테고리 폴더 필수\n\n"
                    + "**제약사항**:\n"
                    + "- ZIP 파일 크기: application.yml의 max-file-size 설정값 (기본 1GB)\n"
                    + "- 압축 해제 후 크기: application.yml의 max-file-size 설정값 (기본 1GB)\n"
                    + "- 허용 확장자: 모든 확장자 허용 (제한 없음)\n"
                    + "- Authorization 헤더에 JWT 토큰 필수 (Bearer {token})",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateVersionApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.CreateVersionResponse>> createStandardVersion(
            @Parameter(description = "버전 정보 (version, comment)", required = true)
            @Valid @ModelAttribute ReleaseVersionDto.CreateStandardVersionRequest request,

            @Parameter(description = "패치 파일 ZIP", required = true)
            @RequestPart("patchFiles") MultipartFile patchFiles,

            @Parameter(description = "JWT 토큰 (Bearer {token})", required = true)
            @RequestHeader("Authorization") String authorization
    );

    @Operation(
            summary = "커스텀 릴리즈 버전 생성",
            description = "ZIP 파일로 커스텀 릴리즈 버전을 생성합니다.\n\n"
                    + "**커스텀 버전 특징**:\n"
                    + "- 특정 표준 버전(customBaseVersionId)을 기준으로 파생된 버전\n"
                    + "- 특정 고객사(customerId)를 위한 맞춤 릴리즈\n"
                    + "- 커스텀 버전 번호(customVersion)는 고객사별로 독립적으로 관리\n"
                    + "- **커스텀 버전은 PATCH 카테고리만 지원** (INSTALL 불가)\n\n"
                    + "**customBaseVersionId 필수 조건**:\n"
                    + "- 해당 고객사의 **최초 커스텀 버전 생성 시 필수**\n"
                    + "- 이후 버전 생성 시에는 선택 (생략 시 null로 저장)\n\n"
                    + "**ZIP 파일 구조 규칙**: database/, web/, engine/ 폴더만 허용\n"
                    + "```\n"
                    + "custom_patch.zip\n"
                    + "├── database/\n"
                    + "│   └── mariadb/\n"
                    + "│       └── 1.custom_patch.sql\n"
                    + "├── web/\n"
                    + "│   └── build/\n"
                    + "│       └── custom_frontend.war\n"
                    + "└── engine/\n"
                    + "    └── build/\n"
                    + "        └── custom_engine.jar\n"
                    + "```\n\n"
                    + "**저장 경로**: `versions/{projectId}/custom/{customerCode}/{customMajorMinor}/{customVersion}/`",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateCustomVersionApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.CreateCustomVersionResponse>> createCustomVersion(
            @Parameter(description = "커스텀 버전 정보 (projectId, customerId, customBaseVersionId, customVersion, comment)", required = true)
            @Valid @ModelAttribute ReleaseVersionDto.CreateCustomVersionRequest request,

            @Parameter(description = "패치 파일 ZIP", required = true)
            @RequestPart("patchFiles") MultipartFile patchFiles,

            @Parameter(description = "JWT 토큰 (Bearer {token})", required = true)
            @RequestHeader("Authorization") String authorization
    );

    @Operation(
            summary = "릴리즈 버전 조회 (ID)",
            description = "ID로 릴리즈 버전 정보를 조회합니다",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.DetailResponse>> getVersionById(
            @Parameter(description = "버전 ID", required = true)
            @PathVariable Long id
    );

    @Operation(
            summary = "표준 릴리즈 버전 트리 조회",
            description = "프로젝트별 표준 릴리즈 버전들을 계층 구조로 조회합니다 (프론트엔드 트리 렌더링용)\n\n"
                    + "**응답 구조** (3단계 중첩):\n"
                    + "1. majorMinorGroups: 메이저.마이너 그룹 목록 (예: 1.1.x, 1.2.x)\n"
                    + "2. versions: 각 그룹 내의 버전 목록 (예: 1.1.0, 1.1.1, 1.1.2)\n"
                    + "3. fileCategories: 각 버전의 파일 카테고리 목록 (예: DATABASE, WEB, ENGINE)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TreeApiResponse.class),
                            examples = @ExampleObject(
                                    name = "표준 릴리즈 버전 트리 조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": {
                                                "releaseType": "STANDARD",
                                                "customerCode": null,
                                                "majorMinorGroups": [
                                                  {
                                                    "majorMinor": "1.1.x",
                                                    "versions": [
                                                      {
                                                        "versionId": 1,
                                                        "version": "1.1.0",
                                                        "createdAt": "2025-11-20",
                                                        "createdBy": "jhlee@tscientific",
                                                        "comment": "Initial release",
                                                        "fileCategories": ["DATABASE", "WEB", "ENGINE"]
                                                      },
                                                      {
                                                        "versionId": 2,
                                                        "version": "1.1.1",
                                                        "createdAt": "2025-11-25",
                                                        "createdBy": "jhlee@tscientific",
                                                        "comment": "Bug fixes",
                                                        "fileCategories": ["DATABASE", "WEB"]
                                                      }
                                                    ]
                                                  },
                                                  {
                                                    "majorMinor": "1.2.x",
                                                    "versions": [
                                                      {
                                                        "versionId": 3,
                                                        "version": "1.2.0",
                                                        "createdAt": "2025-12-01",
                                                        "createdBy": "jhlee@tscientific",
                                                        "comment": "New features",
                                                        "fileCategories": ["DATABASE", "WEB", "ENGINE"]
                                                      }
                                                    ]
                                                  }
                                                ]
                                              }
                                            }
                                            """
                            )
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.TreeResponse>> getStandardReleaseTree(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId
    );

    @Operation(
            summary = "전체 커스텀 릴리즈 버전 트리 조회",
            description = "프로젝트별 모든 고객사의 커스텀 릴리즈 버전들을 계층 구조로 조회합니다.\n\n"
                    + "**응답 구조** (4단계 중첩):\n"
                    + "1. customers: 고객사 목록\n"
                    + "2. majorMinorGroups: 각 고객사의 커스텀 메이저.마이너 그룹 목록 (예: 1.0.x, 1.1.x)\n"
                    + "3. versions: 각 그룹 내의 커스텀 버전 목록 (예: 1.0.0, 1.0.1)\n"
                    + "4. 각 고객사에는 기준 표준본 정보(customBaseVersionId, customBaseVersion) 포함 (고객사별로 하나의 기준 표준본)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomTreeApiResponse.class),
                            examples = @ExampleObject(
                                    name = "전체 커스텀 릴리즈 버전 트리 조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": {
                                                "releaseType": "CUSTOM",
                                                "customers": [
                                                  {
                                                    "customerId": 1,
                                                    "customerCode": "companyA",
                                                    "customerName": "A회사",
                                                    "customBaseVersionId": 5,
                                                    "customBaseVersion": "1.1.0",
                                                    "majorMinorGroups": [
                                                      {
                                                        "majorMinor": "1.0.x",
                                                        "versions": [
                                                          {
                                                            "versionId": 101,
                                                            "version": "1.0.0",
                                                            "createdAt": "2025-12-01",
                                                            "createdBy": "jhlee@tscientific",
                                                            "comment": "A사 커스텀 패치",
                                                            "isApproved": true,
                                                            "approvedBy": "admin@tscientific.co.kr",
                                                            "approvedAt": "2025-12-02",
                                                            "fileCategories": ["DATABASE", "WEB"]
                                                          },
                                                          {
                                                            "versionId": 102,
                                                            "version": "1.0.1",
                                                            "createdAt": "2025-12-10",
                                                            "createdBy": "jhlee@tscientific",
                                                            "comment": "A사 버그 수정",
                                                            "isApproved": false,
                                                            "approvedBy": null,
                                                            "approvedAt": null,
                                                            "fileCategories": ["DATABASE"]
                                                          }
                                                        ]
                                                      }
                                                    ]
                                                  },
                                                  {
                                                    "customerId": 2,
                                                    "customerCode": "companyB",
                                                    "customerName": "B회사",
                                                    "customBaseVersionId": 8,
                                                    "customBaseVersion": "1.2.0",
                                                    "majorMinorGroups": [
                                                      {
                                                        "majorMinor": "1.4.x",
                                                        "versions": [
                                                          {
                                                            "versionId": 201,
                                                            "version": "1.4.1",
                                                            "createdAt": "2025-12-05",
                                                            "createdBy": "jhlee@tscientific",
                                                            "comment": "B사 커스텀 기능",
                                                            "isApproved": true,
                                                            "approvedBy": "admin@tscientific.co.kr",
                                                            "approvedAt": "2025-12-06",
                                                            "fileCategories": ["DATABASE", "WEB", "ENGINE"]
                                                          },
                                                          {
                                                            "versionId": 202,
                                                            "version": "1.4.2",
                                                            "createdAt": "2025-12-15",
                                                            "createdBy": "jhlee@tscientific",
                                                            "comment": "B사 추가 수정",
                                                            "isApproved": false,
                                                            "approvedBy": null,
                                                            "approvedAt": null,
                                                            "fileCategories": ["DATABASE"]
                                                          }
                                                        ]
                                                      }
                                                    ]
                                                  }
                                                ]
                                              }
                                            }
                                            """
                            )
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.CustomTreeResponse>> getAllCustomReleaseTree(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId
    );

    @Operation(
            summary = "특정 고객사 커스텀 릴리즈 버전 트리 조회",
            description = "프로젝트별 특정 고객사의 커스텀 릴리즈 버전들을 계층 구조로 조회합니다 (프론트엔드 트리 렌더링용)\n\n"
                    + "**응답 구조** (3단계 중첩):\n"
                    + "1. majorMinorGroups: 메이저.마이너 그룹 목록 (예: 1.1.x, 1.2.x)\n"
                    + "2. versions: 각 그룹 내의 버전 목록 (예: 1.1.0, 1.1.1, 1.1.2)\n"
                    + "3. fileCategories: 각 버전의 파일 카테고리 목록 (예: DATABASE, WEB, ENGINE)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TreeApiResponse.class),
                            examples = @ExampleObject(
                                    name = "커스텀 릴리즈 버전 트리 조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": {
                                                "releaseType": "CUSTOM",
                                                "customerCode": "company_a",
                                                "majorMinorGroups": [
                                                  {
                                                    "majorMinor": "1.1.x",
                                                    "versions": [
                                                      {
                                                        "versionId": 101,
                                                        "version": "1.1.0",
                                                        "createdAt": "2025-11-22",
                                                        "createdBy": "jhlee@tscientific",
                                                        "comment": "Company A custom release",
                                                        "fileCategories": ["DATABASE", "WEB"]
                                                      }
                                                    ]
                                                  },
                                                  {
                                                    "majorMinor": "1.2.x",
                                                    "versions": [
                                                      {
                                                        "versionId": 102,
                                                        "version": "1.2.0",
                                                        "createdAt": "2025-12-03",
                                                        "createdBy": "jhlee@tscientific",
                                                        "comment": "Custom features for Company A",
                                                        "fileCategories": ["DATABASE", "WEB", "ENGINE"]
                                                      }
                                                    ]
                                                  }
                                                ]
                                              }
                                            }
                                            """
                            )
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.TreeResponse>> getCustomReleaseTree(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId,

            @Parameter(description = "고객사 코드", required = true, example = "company_a")
            @PathVariable("customer-code") String customerCode
    );

    @Operation(
            summary = "릴리즈 버전 삭제",
            description = "릴리즈 버전을 완전히 삭제합니다.\n\n"
                    + "**삭제되는 항목**:\n"
                    + "- 데이터베이스: release_version, release_file, release_version_hierarchy\n"
                    + "- 파일 시스템: versions/{type}/{majorMinor}/{version}/ 디렉토리",
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
    ResponseEntity<ApiResponse<Void>> deleteVersion(
            @Parameter(description = "버전 ID", required = true)
            @PathVariable Long id
    );

    @Operation(
            summary = "릴리즈 버전 파일 트리 조회",
            description = "릴리즈 버전의 파일 구조를 트리 형태로 조회합니다.\n\n"
                    + "**응답 구조**:\n"
                    + "- 디렉토리와 파일을 계층 구조로 반환\n"
                    + "- 각 파일 노드에는 releaseFileId 포함 (다운로드 시 사용)\n"
                    + "- relativePath를 기반으로 트리 구조 생성",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FileTreeApiResponse.class),
                            examples = @ExampleObject(
                                    name = "파일 트리 조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": {
                                                "versionId": 1,
                                                "version": "1.1.0",
                                                "root": {
                                                  "name": "1.1.0",
                                                  "type": "directory",
                                                  "path": "",
                                                  "releaseFileId": null,
                                                  "size": null,
                                                  "children": [
                                                    {
                                                      "name": "database",
                                                      "type": "directory",
                                                      "path": "database",
                                                      "releaseFileId": null,
                                                      "size": null,
                                                      "children": [
                                                        {
                                                          "name": "mariadb",
                                                          "type": "directory",
                                                          "path": "database/mariadb",
                                                          "releaseFileId": null,
                                                          "size": null,
                                                          "children": [
                                                            {
                                                              "name": "1.patch_ddl.sql",
                                                              "type": "file",
                                                              "path": "database/mariadb/1.patch_ddl.sql",
                                                              "releaseFileId": 101,
                                                              "size": 5678,
                                                              "children": null
                                                            },
                                                            {
                                                              "name": "2.patch_dml.sql",
                                                              "type": "file",
                                                              "path": "database/mariadb/2.patch_dml.sql",
                                                              "releaseFileId": 102,
                                                              "size": 3456,
                                                              "children": null
                                                            }
                                                          ]
                                                        }
                                                      ]
                                                    },
                                                    {
                                                      "name": "web",
                                                      "type": "directory",
                                                      "path": "web",
                                                      "releaseFileId": null,
                                                      "size": null,
                                                      "children": [
                                                        {
                                                          "name": "build",
                                                          "type": "directory",
                                                          "path": "web/build",
                                                          "releaseFileId": null,
                                                          "size": null,
                                                          "children": [
                                                            {
                                                              "name": "frontend.war",
                                                              "type": "file",
                                                              "path": "web/build/frontend.war",
                                                              "releaseFileId": 201,
                                                              "size": 25678912,
                                                              "children": null
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
    ResponseEntity<ApiResponse<ReleaseVersionDto.FileTreeResponse>> getVersionFileTree(
            @Parameter(description = "버전 ID", required = true)
            @PathVariable Long id
    );

    @Operation(
            summary = "표준본 버전 목록 조회 (셀렉트박스용)",
            description = "프로젝트별 표준본 버전 목록을 조회합니다.\n\n"
                    + "**용도**: 커스텀 버전 생성 시 기준 표준본 선택 셀렉트박스 구성\n\n"
                    + "**정렬**: 최신순 (createdAt DESC)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = VersionSelectListApiResponse.class),
                            examples = @ExampleObject(
                                    name = "표준본 버전 목록 조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": [
                                                {
                                                  "versionId": 5,
                                                  "version": "1.2.0",
                                                  "isApproved": true
                                                },
                                                {
                                                  "versionId": 3,
                                                  "version": "1.1.1",
                                                  "isApproved": true
                                                },
                                                {
                                                  "versionId": 1,
                                                  "version": "1.1.0",
                                                  "isApproved": false
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
    )
    ResponseEntity<ApiResponse<java.util.List<ReleaseVersionDto.VersionSelectOption>>> getStandardVersionsForSelect(
            @Parameter(description = "프로젝트 ID", required = true, example = "infraeye2")
            @PathVariable String projectId
    );

    @Operation(
            summary = "릴리즈 버전 코멘트 수정",
            description = "릴리즈 버전의 코멘트(패치 노트)를 수정합니다.\n\n"
                    + "**권한**: ADMIN, USER\n\n"
                    + "**처리 내용**:\n"
                    + "- comment 필드를 수정",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.DetailResponse>> updateComment(
            @Parameter(description = "버전 ID", required = true)
            @PathVariable Long id,

            @Parameter(description = "수정할 코멘트 정보", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody ReleaseVersionDto.UpdateRequest request
    );

    @Operation(
            summary = "릴리즈 버전 승인",
            description = "릴리즈 버전을 승인합니다.\n\n"
                    + "**권한**: ADMIN, USER\n\n"
                    + "**처리 내용**:\n"
                    + "- is_approved 필드를 true로 변경",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.DetailResponse>> approveVersion(
            @Parameter(description = "버전 ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "JWT 인증 토큰", required = true, example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authorization
    );

    // ========================================
    // Hotfix API Documentation
    // ========================================

    @Operation(
            summary = "핫픽스 생성",
            description = "특정 버전에 대한 핫픽스를 생성합니다.\n\n"
                    + "**핫픽스 특징**:\n"
                    + "- 기존 버전(예: 1.3.2)에 대한 긴급 버그 수정 패치\n"
                    + "- 4자리 버전 형식 사용 (예: 1.3.2.1, 1.3.2.2)\n"
                    + "- 핫픽스 버전은 자동으로 증가 (1 → 2 → 3)\n"
                    + "- **메인 라인 패치 생성 시 제외됨** (별도 관리)\n\n"
                    + "**제약사항**:\n"
                    + "- 핫픽스의 핫픽스는 생성 불가 (1.3.2.1에 대한 핫픽스 불가)\n"
                    + "- ZIP 파일 구조 규칙은 표준 버전과 동일\n\n"
                    + "**저장 경로**: `versions/{projectId}/standard/{majorMinor}/{version}/hotfix/{hotfixVersion}/`",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateHotfixApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.CreateHotfixResponse>> createHotfix(
            @Parameter(description = "원본 버전 ID (핫픽스 대상)", required = true)
            @PathVariable Long id,

            @Parameter(description = "핫픽스 정보 (comment, engineerId)", required = true)
            @Valid @ModelAttribute ReleaseVersionDto.CreateHotfixRequest request,

            @Parameter(description = "패치 파일 ZIP", required = true)
            @RequestPart("patchFiles") MultipartFile patchFiles,

            @Parameter(description = "JWT 토큰 (Bearer {token})", required = true)
            @RequestHeader("Authorization") String authorization
    );

    @Operation(
            summary = "핫픽스 목록 조회",
            description = "특정 버전의 핫픽스 목록을 조회합니다.\n\n"
                    + "**응답 구조**:\n"
                    + "- hotfixBaseVersionId: 핫픽스 원본 버전 ID\n"
                    + "- hotfixBaseVersion: 핫픽스 원본 버전 번호 (예: 1.3.2)\n"
                    + "- hotfixes: 핫픽스 목록 (버전 순 정렬)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = HotfixListApiResponse.class),
                            examples = @ExampleObject(
                                    name = "핫픽스 목록 조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": {
                                                "hotfixBaseVersionId": 15,
                                                "hotfixBaseVersion": "1.3.2",
                                                "hotfixes": [
                                                  {
                                                    "releaseVersionId": 25,
                                                    "hotfixVersion": 1,
                                                    "fullVersion": "1.3.2.1",
                                                    "createdAt": "2025-12-15",
                                                    "createdBy": "jhlee@tscientific",
                                                    "comment": "특정 버그 수정",
                                                    "isApproved": true,
                                                    "fileCategories": ["DATABASE"]
                                                  },
                                                  {
                                                    "releaseVersionId": 26,
                                                    "hotfixVersion": 2,
                                                    "fullVersion": "1.3.2.2",
                                                    "createdAt": "2025-12-20",
                                                    "createdBy": "jhlee@tscientific",
                                                    "comment": "추가 버그 수정",
                                                    "isApproved": false,
                                                    "fileCategories": ["DATABASE", "WEB"]
                                                  }
                                                ]
                                              }
                                            }
                                            """
                            )
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.HotfixListResponse>> getHotfixesByVersionId(
            @Parameter(description = "원본 버전 ID", required = true)
            @PathVariable Long id
    );

    /**
     * Swagger 스키마용 wrapper 클래스 - 표준 버전 생성 응답
     */
    @Schema(description = "표준 버전 생성 API 응답")
    class CreateVersionApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "생성된 버전 정보")
        public ReleaseVersionDto.CreateVersionResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 커스텀 버전 생성 응답
     */
    @Schema(description = "커스텀 버전 생성 API 응답")
    class CreateCustomVersionApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "생성된 커스텀 버전 정보")
        public ReleaseVersionDto.CreateCustomVersionResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 버전 상세 응답
     */
    @Schema(description = "버전 상세 API 응답")
    class DetailApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "버전 상세 정보")
        public ReleaseVersionDto.DetailResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 트리 응답
     */
    @Schema(description = "버전 트리 API 응답")
    class TreeApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "버전 트리")
        public ReleaseVersionDto.TreeResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 파일 트리 응답
     */
    @Schema(description = "파일 트리 API 응답")
    class FileTreeApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "파일 트리")
        public ReleaseVersionDto.FileTreeResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 전체 커스텀 트리 응답
     */
    @Schema(description = "전체 커스텀 트리 API 응답")
    class CustomTreeApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "커스텀 버전 트리 (고객사별 그룹화)")
        public ReleaseVersionDto.CustomTreeResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 표준본 버전 셀렉트박스 목록 응답
     */
    @Schema(description = "표준본 버전 셀렉트박스 목록 API 응답")
    class VersionSelectListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "표준본 버전 목록")
        public java.util.List<ReleaseVersionDto.VersionSelectOption> data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 핫픽스 생성 응답
     */
    @Schema(description = "핫픽스 생성 API 응답")
    class CreateHotfixApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "생성된 핫픽스 정보")
        public ReleaseVersionDto.CreateHotfixResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 핫픽스 목록 응답
     */
    @Schema(description = "핫픽스 목록 API 응답")
    class HotfixListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "핫픽스 목록")
        public ReleaseVersionDto.HotfixListResponse data;
    }

    // ========================================
    // Build (빌드 버전) API Documentation
    // ========================================

    @Operation(
            summary = "빌드 버전 생성 (선택적 ZIP 동봉)",
            description = "특정 버전에 대한 빌드(WEB/ENGINE 산출물 패치)를 생성합니다.\n\n"
                    + "**빌드 특징**:\n"
                    + "- 같은 base 버전 위에 build_version 으로 구분 (예: 1.1.0.260427)\n"
                    + "- 빌드는 즉시 활성 (is_approved=true)\n"
                    + "- buildVersion 미입력 시 서버가 오늘 yyMMdd 자동 채움\n"
                    + "- 같은 base 에 동일 buildVersion 있으면 +1 후 자동 재시도\n\n"
                    + "**제약사항**:\n"
                    + "- 핫픽스 위에는 빌드 생성 불가\n"
                    + "- 빌드 위에는 빌드 생성 불가\n"
                    + "- ZIP 루트는 web/, engine/, etc/ 만 허용 (대소문자 구분)\n\n"
                    + "**저장 경로**: `versions/{projectId}/{type}/{majorMinor}/{version}/builds/{buildVersion}/`",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateBuildApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.CreateBuildResponse>> createBuild(
            @Parameter(description = "빌드 원본 버전 ID", required = true)
            @PathVariable Long id,
            @Valid @ModelAttribute ReleaseVersionDto.CreateBuildRequest request,
            @Parameter(description = "빌드 ZIP 파일 (web/engine/etc 루트만 허용). 미동봉 시 빌드 행만 생성")
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Parameter(description = "JWT 인증 토큰", required = true, example = "Bearer eyJhbGciOiJIUzI1NiIs...")
            @RequestHeader("Authorization") String authorization
    );

    @Operation(
            summary = "특정 버전의 빌드 목록 조회",
            description = "지정한 base 버전의 모든 빌드를 build_version 내림차순으로 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BuildListApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.BuildListResponse>> getBuildsByVersionId(
            @Parameter(description = "원본 버전 ID", required = true)
            @PathVariable Long id
    );

    @Operation(
            summary = "빌드 버전 삭제",
            description = "빌드 행과 빌드 디렉토리(builds/{buildVersion}/) 를 함께 삭제합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공"
            )
    )
    ResponseEntity<ApiResponse<Void>> deleteBuild(
            @Parameter(description = "빌드 ReleaseVersion ID", required = true)
            @PathVariable Long id
    );

    @Operation(
            summary = "빌드 ZIP 재업로드 (교체)",
            description = "기존 빌드 디렉토리 산출물을 삭제하고 새 ZIP 으로 교체합니다.\n\n"
                    + "**제약사항**:\n"
                    + "- 빌드 버전(build_version > 0) 에만 사용 가능\n"
                    + "- ZIP 루트는 web/, engine/, etc/ 만 허용 (대소문자 구분)\n"
                    + "- 동일 트랜잭션이므로 신규 업로드 실패 시 삭제도 롤백됨",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공"
            )
    )
    ResponseEntity<ApiResponse<ReleaseVersionDto.UploadBuildZipResponse>> replaceBuildZip(
            @Parameter(description = "빌드 ReleaseVersion ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "새 빌드 ZIP 파일", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "JWT 인증 토큰", required = true, example = "Bearer eyJhbGciOiJIUzI1NiIs...")
            @RequestHeader("Authorization") String authorization
    );

    /**
     * Swagger 스키마용 wrapper 클래스 - 빌드 생성 응답
     */
    @Schema(description = "빌드 생성 API 응답")
    class CreateBuildApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "빌드 생성 결과")
        public ReleaseVersionDto.CreateBuildResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 빌드 목록 응답
     */
    @Schema(description = "빌드 목록 API 응답")
    class BuildListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "빌드 목록")
        public ReleaseVersionDto.BuildListResponse data;
    }
}
