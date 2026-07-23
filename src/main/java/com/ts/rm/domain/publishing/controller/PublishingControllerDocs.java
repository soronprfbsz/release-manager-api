package com.ts.rm.domain.publishing.controller;

import com.ts.rm.domain.publishing.dto.PublishingDto;
import com.ts.rm.domain.publishing.dto.PublishingFileDto;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * PublishingController Swagger 문서화 인터페이스
 */
@Tag(name = "퍼블리싱", description = "퍼블리싱(HTML, CSS, JS 등 웹 화면단 리소스) 관리 API")
@SwaggerResponse
public interface PublishingControllerDocs {

    @Operation(
            summary = "퍼블리싱 생성 (ZIP 업로드)",
            description = "퍼블리싱을 생성하고 ZIP 파일을 업로드합니다.\n\n"
                    + "**카테고리 (publishingCategory)**:\n"
                    + "- `INFRAEYE1`: Infraeye 1 제품\n"
                    + "- `INFRAEYE2`: Infraeye 2 제품\n"
                    + "- `COMMON`: 공통\n"
                    + "- `ETC`: 기타\n\n"
                    + "**서브 카테고리 (subCategory)**:\n"
                    + "- INFRAEYE1: DASHBOARD, REPORT, ETC\n"
                    + "- INFRAEYE2: DASHBOARD, REPORT, MONITORING, ETC\n"
                    + "- COMMON: COMPONENT, LAYOUT, ETC\n\n"
                    + "**ZIP 파일 구조**:\n"
                    + "- ZIP 파일 내부 폴더 구조가 그대로 유지됩니다\n"
                    + "- 예: dashboard/css/style.css",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublishingDetailApiResponse.class)
                    )
            )
    )
    ApiResponse<PublishingDto.DetailResponse> createPublishing(
            @Parameter(hidden = true)
            @RequestHeader("Authorization") String authorizationHeader,

            @Parameter(description = "업로드할 ZIP 파일", required = true)
            @RequestPart("file") MultipartFile zipFile,

            @Parameter(description = "퍼블리싱 명", required = true, example = "대시보드 v1.0")
            @RequestParam String publishingName,

            @Parameter(description = "카테고리 (INFRAEYE1/INFRAEYE2/COMMON/ETC)", required = true, example = "INFRAEYE2")
            @RequestParam String publishingCategory,

            @Parameter(description = "서브 카테고리 (예: DASHBOARD, REPORT)", example = "DASHBOARD")
            @RequestParam(required = false) String subCategory,

            @Parameter(description = "퍼블리싱 설명", example = "Infraeye 2 대시보드 퍼블리싱")
            @RequestParam(required = false) String description,

            @Parameter(description = "사이트 ID (커스터마이징인 경우)", example = "1")
            @RequestParam(required = false) Long siteId,

            @Parameter(description = "글리프 텍스트(배지용)", example = "BETA")
            @RequestParam(required = false) String glyphText,

            @Parameter(description = "글리프 배경색(hex)", example = "#FF8800")
            @RequestParam(required = false) String glyphBackgroundColor
    );

    @Operation(
            summary = "퍼블리싱 상세 조회",
            description = "퍼블리싱 ID로 상세 정보를 조회합니다.\n\n"
                    + "**응답 포함 정보**:\n"
                    + "- 퍼블리싱 메타데이터\n"
                    + "- 포함된 파일 목록",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublishingDetailApiResponse.class)
                    )
            )
    )
    ApiResponse<PublishingDto.DetailResponse> getPublishing(
            @Parameter(description = "퍼블리싱 ID", required = true, example = "1")
            @PathVariable Long id
    );

    @Operation(
            summary = "퍼블리싱 수정",
            description = "퍼블리싱 메타정보를 수정합니다.\n\n"
                    + "**수정 가능한 필드**:\n"
                    + "- `publishingName`: 퍼블리싱명\n"
                    + "- `description`: 설명\n"
                    + "- `publishingCategory`: 카테고리 (INFRAEYE1/INFRAEYE2/COMMON/ETC)\n"
                    + "- `subCategory`: 서브 카테고리\n"
                    + "- `siteId`: 사이트 ID\n\n"
                    + "**주의사항**:\n"
                    + "- 퍼블리싱명은 중복될 수 없습니다\n"
                    + "- 파일은 수정되지 않습니다 (재업로드 필요)",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublishingDetailApiResponse.class)
                    )
            )
    )
    ApiResponse<PublishingDto.DetailResponse> updatePublishing(
            @Parameter(hidden = true)
            @RequestHeader("Authorization") String authorizationHeader,

            @Parameter(description = "퍼블리싱 ID", required = true, example = "1")
            @PathVariable Long id,

            @Parameter(description = "수정 요청 정보", required = true)
            @RequestBody @Valid PublishingDto.UpdateRequest request
    );

    @Operation(
            summary = "퍼블리싱 삭제",
            description = "퍼블리싱과 관련된 모든 파일을 삭제합니다.\n\n"
                    + "**삭제 범위**:\n"
                    + "- DB 레코드 (publishing, publishing_file 테이블)\n"
                    + "- 실제 파일 및 디렉토리\n\n"
                    + "**주의사항**:\n"
                    + "- 삭제된 데이터는 복구할 수 없습니다",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(example = "{\"status\": \"success\", \"data\": null}")
                    )
            )
    )
    ApiResponse<Void> deletePublishing(
            @Parameter(description = "퍼블리싱 ID", required = true, example = "1")
            @PathVariable Long id
    );

    @Operation(
            summary = "퍼블리싱 목록 조회",
            description = "퍼블리싱 목록을 조회합니다.\n\n"
                    + "**필터링 옵션**:\n"
                    + "- `publishingCategory`: 카테고리 필터\n"
                    + "- `subCategory`: 서브 카테고리 필터\n"
                    + "- `siteId`: 사이트 ID 필터 (0이면 표준만)\n"
                    + "- `keyword`: 퍼블리싱명, 설명 통합 검색",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublishingListApiResponse.class)
                    )
            )
    )
    ApiResponse<List<PublishingDto.SimpleResponse>> listPublishings(
            @Parameter(description = "카테고리 필터 (INFRAEYE1/INFRAEYE2/COMMON/ETC)")
            @RequestParam(required = false) String publishingCategory,

            @Parameter(description = "서브 카테고리 필터")
            @RequestParam(required = false) String subCategory,

            @Parameter(description = "사이트 ID 필터 (0이면 표준만)")
            @RequestParam(required = false) Long siteId,

            @Parameter(description = "검색 키워드 (퍼블리싱명, 설명 통합 검색)")
            @RequestParam(required = false) String keyword
    );

    @Operation(
            summary = "퍼블리싱 순서 변경",
            description = "퍼블리싱의 정렬 순서를 변경합니다.\n\n"
                    + "**주의**: 동일한 카테고리 내의 퍼블리싱만 순서 변경이 가능합니다.\n\n"
                    + "**요청 예시**:\n"
                    + "```json\n"
                    + "{\n"
                    + "  \"publishingCategory\": \"INFRAEYE2\",\n"
                    + "  \"publishingIds\": [3, 1, 2, 4]\n"
                    + "}\n"
                    + "```",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(example = "{\"status\": \"success\", \"data\": null}")
                    )
            )
    )
    ApiResponse<Void> reorderPublishings(
            @Parameter(description = "순서 변경 요청", required = true)
            @RequestBody @Valid PublishingDto.ReorderRequest request
    );

    @Operation(
            summary = "퍼블리싱 파일 상세 조회",
            description = "퍼블리싱에 포함된 개별 파일의 상세 정보를 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublishingFileDetailApiResponse.class)
                    )
            )
    )
    ApiResponse<PublishingFileDto.DetailResponse> getPublishingFile(
            @Parameter(description = "퍼블리싱 ID", required = true, example = "1")
            @PathVariable Long id,

            @Parameter(description = "파일 ID", required = true, example = "1")
            @PathVariable Long fileId
    );

    @Operation(
            summary = "퍼블리싱 파일 구조 조회",
            description = "퍼블리싱에 업로드된 파일의 디렉토리/파일 구조를 조회합니다.\n\n"
                    + "**응답 구조**:\n"
                    + "- 재귀적인 디렉토리/파일 구조\n"
                    + "- 각 노드는 `type`으로 구분 (directory/file)\n"
                    + "- 파일은 `size`, `path`, `filePath` 정보 포함",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FileStructureApiResponse.class)
                    )
            )
    )
    ApiResponse<PublishingDto.FileStructureResponse> getFiles(
            @Parameter(description = "퍼블리싱 ID", required = true, example = "1")
            @PathVariable Long id
    );

    @Operation(
            summary = "퍼블리싱 파일 내용 조회",
            description = "퍼블리싱에 업로드된 파일의 텍스트 내용을 조회합니다.\n\n"
                    + "**지원 파일 타입**:\n"
                    + "- HTML, CSS, JS, JSON, XML, TXT, MD, SVG 등 텍스트 파일\n\n"
                    + "**제한 사항**:\n"
                    + "- 최대 파일 크기: 10MB\n"
                    + "- 바이너리 파일 (이미지, 폰트 등)은 조회 불가",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FileContentApiResponse.class)
                    )
            )
    )
    ApiResponse<PublishingDto.FileContentResponse> getFileContent(
            @Parameter(description = "퍼블리싱 ID", required = true, example = "1")
            @PathVariable Long id,

            @Parameter(description = "파일 상대 경로", required = true, example = "css/style.css")
            @RequestParam String path
    );

    @Operation(
            summary = "퍼블리싱 전체 다운로드 (ZIP)",
            description = "퍼블리싱에 업로드된 모든 파일을 ZIP으로 압축하여 다운로드합니다.\n\n"
                    + "**응답 헤더**:\n"
                    + "- `Content-Disposition`: 파일명 (한글 지원)\n"
                    + "- `X-Uncompressed-Size`: 압축 전 총 크기 (바이트) - 진행률 표시용"
    )
    void downloadPublishing(
            @Parameter(description = "퍼블리싱 ID", required = true, example = "1")
            @PathVariable Long id,

            HttpServletResponse response
    ) throws IOException;

    // ========== Swagger 스키마용 wrapper 클래스 ==========

    @Schema(description = "퍼블리싱 상세 API 응답")
    class PublishingDetailApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "퍼블리싱 상세 정보")
        public PublishingDto.DetailResponse data;
    }

    @Schema(description = "퍼블리싱 목록 API 응답")
    class PublishingListApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "퍼블리싱 목록")
        public List<PublishingDto.SimpleResponse> data;
    }

    @Schema(description = "퍼블리싱 파일 상세 API 응답")
    class PublishingFileDetailApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "퍼블리싱 파일 상세 정보")
        public PublishingFileDto.DetailResponse data;
    }

    @Schema(description = "퍼블리싱 파일 구조 API 응답")
    class FileStructureApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "퍼블리싱 파일 구조")
        public PublishingDto.FileStructureResponse data;
    }

    @Schema(description = "퍼블리싱 파일 내용 API 응답")
    class FileContentApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "퍼블리싱 파일 내용")
        public PublishingDto.FileContentResponse data;
    }
}
