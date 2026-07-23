package com.ts.rm.domain.project.controller;

import com.ts.rm.domain.project.dto.ProjectDto;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * ProjectController Swagger 문서화 인터페이스
 */
@Tag(name = "프로젝트", description = "프로젝트 관리 API")
@SwaggerResponse
public interface ProjectControllerDocs {

    @Operation(
            summary = "프로젝트 생성",
            description = "새로운 프로젝트를 생성합니다",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "생성됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProjectDetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ProjectDto.DetailResponse>> createProject(
            @Valid @RequestBody ProjectDto.CreateRequest request
    );

    @Operation(
            summary = "프로젝트 조회",
            description = "ID로 프로젝트 정보를 조회합니다",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProjectDetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ProjectDto.DetailResponse>> getProjectById(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable String id
    );

    @Operation(
            summary = "프로젝트 목록 조회",
            description = "전체 프로젝트 목록을 조회합니다. isEnabled 파라미터로 활성/비활성 필터링 가능",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProjectListApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<List<ProjectDto.DetailResponse>>> getAllProjects(
            @Parameter(description = "활성 여부 필터 (null이면 전체 조회)")
            Boolean isEnabled
    );

    @Operation(
            summary = "프로젝트 수정",
            description = "프로젝트 정보를 수정합니다 (프로젝트 ID는 변경 불가)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProjectDetailApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ProjectDto.DetailResponse>> updateProject(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable String id,

            @Valid @RequestBody ProjectDto.UpdateRequest request
    );

    @Operation(
            summary = "프로젝트 삭제",
            description = "프로젝트를 삭제합니다. 연관된 릴리즈 버전/패치가 있으면 삭제할 수 없습니다",
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
    ResponseEntity<ApiResponse<Void>> deleteProject(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable String id
    );

    @Operation(
            summary = "온보딩 파일 트리 조회",
            description = """
                    프로젝트별 온보딩 파일 목록을 트리 구조로 조회합니다 (파일시스템 기반).

                    **용도**: 기존 레거시 사이트들의 DB 상태를 버전관리 가능한 동일한 상태로 만들기 위한 온보딩 파일 조회

                    **API 경로**: `/api/projects/{id}/onboardings/files`

                    **파일 저장 경로**: `{baseReleasePath}/onboardings/{projectId}/`
                    """,
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OnboardingFilesApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ProjectDto.OnboardingFilesResponse>> getOnboardingFiles(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable String id
    );

    @Operation(
            summary = "온보딩 디렉토리 생성",
            description = """
                    온보딩 파일용 디렉토리를 생성합니다.

                    **경로 예시**: `/mariadb`, `/mariadb/scripts`, `/cratedb`

                    이미 존재하는 경로인 경우 409 Conflict 오류가 발생합니다.
                    """,
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "생성됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OnboardingDirectoryApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ProjectDto.OnboardingDirectoryResponse>> createOnboardingDirectory(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable String id,

            @Parameter(description = "생성할 디렉토리 경로 (예: /mariadb/scripts)", required = true)
            String path
    );

    @Operation(
            summary = "온보딩 파일 업로드",
            description = """
                    온보딩 파일을 업로드합니다.

                    **지원 형식**:
                    - 단일 파일: 지정된 경로에 저장
                    - ZIP 파일: extractZip 옵션에 따라 압축 해제 또는 원본 저장

                    **파일 저장 경로**: `{baseReleasePath}/onboardings/{projectId}/{targetPath}/`
                    """,
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "생성됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OnboardingUploadApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ProjectDto.OnboardingUploadResponse>> uploadOnboardingFile(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable String id,

            @Parameter(description = "업로드할 파일 (ZIP 또는 단일 파일)", required = true)
            MultipartFile file,

            @Parameter(description = "대상 경로 (예: /mariadb)", required = false)
            String targetPath,

            @Parameter(description = "파일 설명", required = false)
            String description,

            @Parameter(description = "ZIP 파일 압축 해제 여부 (true: 압축 해제, false: 원본 유지, 기본값: true)", required = false)
            Boolean extractZip
    );

    @Operation(
            summary = "온보딩 전체 파일 ZIP 다운로드",
            description = "프로젝트의 모든 온보딩 파일을 ZIP으로 다운로드합니다",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "ZIP 파일 다운로드",
                    content = @Content(mediaType = "application/zip")
            )
    )
    void downloadAllOnboardingFiles(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable String id,

            HttpServletResponse response
    ) throws IOException;

    @Operation(
            summary = "온보딩 파일 삭제",
            description = """
                    온보딩 파일을 삭제합니다.

                    **파일 경로 예시**: `/mariadb/init.sql`, `/cratedb`

                    디렉토리 경로를 지정하면 해당 디렉토리와 하위 파일이 모두 삭제됩니다.
                    """,
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OnboardingDeleteApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<ProjectDto.OnboardingDeleteResponse>> deleteOnboardingFile(
            @Parameter(description = "프로젝트 ID", required = true)
            @PathVariable String id,

            @Parameter(description = "삭제할 파일 경로 (예: /mariadb/init.sql)", required = true)
            String filePath
    );

    /**
     * Swagger 스키마용 wrapper 클래스 - 프로젝트 상세 응답
     */
    @Schema(description = "프로젝트 상세 API 응답", example = """
            {
              "status": "success",
              "data": {
                "projectId": "infraeye1",
                "projectName": "Infraeye 1",
                "description": "Infraeye 1.0 - 레거시 NMS 솔루션",
                "createdAt": "2025-01-15T10:30:00",
                "createdBy": "SYSTEM"
              }
            }
            """)
    class ProjectDetailApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "프로젝트 상세 정보")
        public ProjectDto.DetailResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 프로젝트 목록 응답
     */
    @Schema(description = "프로젝트 목록 API 응답", example = """
            {
              "status": "success",
              "data": [
                {
                  "projectId": "infraeye1",
                  "projectName": "Infraeye 1",
                  "description": "Infraeye 1.0 - 레거시 NMS 솔루션",
                  "createdAt": "2025-01-15T10:30:00",
                  "createdBy": "SYSTEM"
                },
                {
                  "projectId": "infraeye2",
                  "projectName": "Infraeye 2",
                  "description": "Infraeye 2.0 - 신규 NMS 솔루션",
                  "createdAt": "2025-01-16T09:00:00",
                  "createdBy": "SYSTEM"
                }
              ]
            }
            """)
    class ProjectListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "프로젝트 목록")
        public List<ProjectDto.DetailResponse> data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 온보딩 파일 트리 응답
     */
    @Schema(description = "온보딩 파일 트리 API 응답", example = """
            {
              "status": "success",
              "data": {
                "projectId": "infraeye2",
                "projectName": "Infraeye 2",
                "hasFiles": true,
                "totalFileCount": 3,
                "totalSize": 102400,
                "files": {
                  "name": "root",
                  "path": "/",
                  "filePath": "onboardings/infraeye2",
                  "type": "directory",
                  "children": [
                    {
                      "name": "mariadb",
                      "path": "/mariadb",
                      "filePath": "onboardings/infraeye2/mariadb",
                      "type": "directory",
                      "children": [
                        {
                          "name": "init_schema.sql",
                          "path": "/mariadb/init_schema.sql",
                          "filePath": "onboardings/infraeye2/mariadb/init_schema.sql",
                          "type": "file",
                          "size": 51200
                        }
                      ]
                    }
                  ]
                }
              }
            }
            """)
    class OnboardingFilesApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "온보딩 파일 트리 정보")
        public ProjectDto.OnboardingFilesResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 온보딩 디렉토리 생성 응답
     */
    @Schema(description = "온보딩 디렉토리 생성 API 응답", example = """
            {
              "status": "success",
              "data": {
                "projectId": "infraeye2",
                "createdPath": "/mariadb/scripts",
                "message": "디렉토리가 생성되었습니다."
              }
            }
            """)
    class OnboardingDirectoryApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "디렉토리 생성 결과 정보")
        public ProjectDto.OnboardingDirectoryResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 온보딩 파일 업로드 응답
     */
    @Schema(description = "온보딩 파일 업로드 API 응답", example = """
            {
              "status": "success",
              "data": {
                "projectId": "infraeye2",
                "uploadedFileCount": 3,
                "uploadedFiles": [
                  {
                    "fileName": "init_schema.sql",
                    "path": "/mariadb/init_schema.sql",
                    "size": 51200
                  }
                ],
                "message": "3개 파일이 업로드되었습니다."
              }
            }
            """)
    class OnboardingUploadApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "업로드 결과 정보")
        public ProjectDto.OnboardingUploadResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 온보딩 파일 삭제 응답
     */
    @Schema(description = "온보딩 파일 삭제 API 응답", example = """
            {
              "status": "success",
              "data": {
                "projectId": "infraeye2",
                "deletedPath": "/mariadb/init_schema.sql",
                "message": "파일이 삭제되었습니다."
              }
            }
            """)
    class OnboardingDeleteApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "삭제 결과 정보")
        public ProjectDto.OnboardingDeleteResponse data;
    }
}
