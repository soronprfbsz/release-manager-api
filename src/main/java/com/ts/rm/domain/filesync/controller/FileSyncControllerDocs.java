package com.ts.rm.domain.filesync.controller;

import com.ts.rm.domain.filesync.dto.FileSyncDto;
import com.ts.rm.domain.filesync.enums.FileSyncTarget;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * FileSyncController Swagger 문서화 인터페이스
 */
@Tag(name = "파일 동기화", description = "파일시스템과 DB 메타데이터 동기화 API")
@SwaggerResponse
public interface FileSyncControllerDocs {

    @Operation(
            summary = "파일 동기화 분석",
            description = "파일시스템과 DB 메타데이터를 비교하여 불일치 항목을 분석합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AnalyzeApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<FileSyncDto.AnalyzeResponse>> analyze(
            @RequestBody(required = false) FileSyncDto.AnalyzeRequest request
    );

    @Operation(
            summary = "동기화 액션 적용",
            description = "분석된 불일치 항목에 대해 선택한 액션을 적용합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApplyApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<FileSyncDto.ApplyResponse>> apply(
            @Valid @RequestBody FileSyncDto.ApplyRequest request
    );

    @Operation(
            summary = "무시된 파일 목록 조회",
            description = "파일 동기화 분석에서 제외된 무시 목록을 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IgnoredFileListApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<List<FileSyncDto.IgnoredFile>>> getIgnoredFiles(
            @Parameter(description = "대상 유형 필터 (RELEASE_FILE, RESOURCE_FILE, BACKUP_FILE)")
            @RequestParam(required = false) FileSyncTarget targetType
    );

    @Operation(
            summary = "무시 목록에서 제거",
            description = "무시 목록에서 항목을 제거합니다. 제거된 항목은 다음 분석 시 다시 나타납니다.",
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
    ResponseEntity<ApiResponse<Void>> removeFromIgnoreList(
            @Parameter(description = "무시 항목 ID")
            @PathVariable Long ignoreId
    );

    @Operation(
            summary = "리소스 파일 등록",
            description = "분석 결과에서 UNREGISTERED 상태인 리소스 파일들을 DB에 등록합니다. "
                    + "리소스명, 대분류, 소분류, 설명 등의 메타데이터를 함께 입력할 수 있습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<FileSyncDto.RegisterResponse>> registerResourceFiles(
            @Valid @RequestBody FileSyncDto.ResourceFileRegisterRequest request
    );

    @Operation(
            summary = "백업 파일 등록",
            description = "분석 결과에서 UNREGISTERED 상태인 백업 파일들을 DB에 등록합니다. "
                    + "파일 카테고리, 설명 등의 메타데이터를 함께 입력할 수 있습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<FileSyncDto.RegisterResponse>> registerBackupFiles(
            @Valid @RequestBody FileSyncDto.BackupFileRegisterRequest request
    );

    @Operation(
            summary = "패치 파일 등록",
            description = "분석 결과에서 UNREGISTERED 상태인 패치 폴더들을 DB에 등록합니다. "
                    + "담당 엔지니어, 고객사 코드, 설명 등의 메타데이터를 함께 입력할 수 있습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<FileSyncDto.RegisterResponse>> registerPatchFiles(
            @Valid @RequestBody FileSyncDto.PatchFileRegisterRequest request
    );

    @Operation(
            summary = "릴리즈 파일 등록",
            description = "분석 결과에서 UNREGISTERED 상태인 릴리즈 파일들을 DB에 등록합니다. "
                    + "릴리즈 버전 ID, 파일 카테고리, 하위 카테고리, 실행 순서, 설명 등의 메타데이터를 함께 입력할 수 있습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<FileSyncDto.RegisterResponse>> registerReleaseFiles(
            @Valid @RequestBody FileSyncDto.ReleaseFileRegisterRequest request
    );

    /**
     * Swagger 스키마용 wrapper 클래스 - 분석 응답
     */
    @Schema(description = "파일 동기화 분석 API 응답")
    class AnalyzeApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "분석 결과")
        public FileSyncDto.AnalyzeResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 적용 응답
     */
    @Schema(description = "동기화 액션 적용 API 응답")
    class ApplyApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "적용 결과")
        public FileSyncDto.ApplyResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 무시 목록 응답
     */
    @Schema(description = "무시된 파일 목록 API 응답")
    class IgnoredFileListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "무시된 파일 목록")
        public List<FileSyncDto.IgnoredFile> data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 등록 응답
     */
    @Schema(description = "파일 등록 API 응답")
    class RegisterApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "등록 결과")
        public FileSyncDto.RegisterResponse data;
    }
}
