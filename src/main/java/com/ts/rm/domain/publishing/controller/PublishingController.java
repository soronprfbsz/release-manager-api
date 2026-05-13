package com.ts.rm.domain.publishing.controller;

import com.ts.rm.domain.publishing.dto.PublishingDto;
import com.ts.rm.domain.publishing.dto.PublishingFileDto;
import com.ts.rm.domain.publishing.service.PublishingService;
import com.ts.rm.global.file.HttpFileDownloadUtil;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 퍼블리싱 API Controller
 *
 * <p>퍼블리싱 ZIP 업로드/다운로드/삭제 API
 */
@Slf4j
@RestController
@RequestMapping("/api/publishing")
@RequiredArgsConstructor
public class PublishingController implements PublishingControllerDocs {

    private final PublishingService publishingService;

    /**
     * 퍼블리싱 생성 (ZIP 업로드)
     */
    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PublishingDto.DetailResponse> createPublishing(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestPart("file") MultipartFile zipFile,
            @RequestParam String publishingName,
            @RequestParam String publishingCategory,
            @RequestParam(required = false) String subCategory,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String glyphText,
            @RequestParam(required = false) String glyphBackgroundColor) {

        log.info("퍼블리싱 생성 요청 - 이름: {}, 카테고리: {}", publishingName, publishingCategory);

        String createdBy = SecurityUtil.getTokenInfo().email();

        PublishingDto.CreateRequest request = new PublishingDto.CreateRequest(
                publishingName, description, publishingCategory, subCategory, customerId,
                glyphText, glyphBackgroundColor,
                createdBy
        );

        PublishingDto.DetailResponse response = publishingService.createPublishing(zipFile, request);
        return ApiResponse.success(response);
    }

    /**
     * 퍼블리싱 상세 조회
     */
    @Override
    @GetMapping("/{id}")
    public ApiResponse<PublishingDto.DetailResponse> getPublishing(@PathVariable Long id) {
        log.info("퍼블리싱 조회 요청 - ID: {}", id);
        PublishingDto.DetailResponse response = publishingService.getPublishing(id);
        return ApiResponse.success(response);
    }

    /**
     * 퍼블리싱 수정
     */
    @Override
    @PutMapping("/{id}")
    public ApiResponse<PublishingDto.DetailResponse> updatePublishing(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long id,
            @RequestBody @Valid PublishingDto.UpdateRequest request) {

        log.info("퍼블리싱 수정 요청 - ID: {}, 이름: {}", id, request.publishingName());

        String updatedBy = SecurityUtil.getTokenInfo().email();

        // updatedBy를 포함한 새로운 UpdateRequest 생성
        PublishingDto.UpdateRequest requestWithUpdatedBy = new PublishingDto.UpdateRequest(
                request.publishingName(),
                request.description(),
                request.publishingCategory(),
                request.subCategory(),
                request.customerId(),
                request.glyphText(),
                request.glyphBackgroundColor(),
                updatedBy
        );

        PublishingDto.DetailResponse response = publishingService.updatePublishing(id, requestWithUpdatedBy);
        return ApiResponse.success(response);
    }

    /**
     * 퍼블리싱 삭제
     */
    @Override
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePublishing(@PathVariable Long id) {
        log.info("퍼블리싱 삭제 요청 - ID: {}", id);
        publishingService.deletePublishing(id);
        return ApiResponse.success(null);
    }

    /**
     * 퍼블리싱 목록 조회
     */
    @Override
    @GetMapping
    public ApiResponse<List<PublishingDto.SimpleResponse>> listPublishings(
            @RequestParam(required = false) String publishingCategory,
            @RequestParam(required = false) String subCategory,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String keyword) {

        log.info("퍼블리싱 목록 조회 요청 - 카테고리: {}, 서브카테고리: {}, 고객사ID: {}, 키워드: {}",
                publishingCategory, subCategory, customerId, keyword);

        List<PublishingDto.SimpleResponse> response = publishingService.listPublishings(
                publishingCategory, subCategory, customerId, keyword);
        return ApiResponse.success(response);
    }

    /**
     * 퍼블리싱 순서 변경
     */
    @Override
    @PatchMapping("/reorder")
    public ApiResponse<Void> reorderPublishings(@RequestBody @Valid PublishingDto.ReorderRequest request) {
        log.info("퍼블리싱 순서 변경 요청 - 카테고리: {}, IDs: {}",
                request.publishingCategory(), request.publishingIds());
        publishingService.reorderPublishings(request);
        return ApiResponse.success(null);
    }

    /**
     * 퍼블리싱 파일 상세 조회
     */
    @Override
    @GetMapping("/{id}/files/{fileId}")
    public ApiResponse<PublishingFileDto.DetailResponse> getPublishingFile(
            @PathVariable Long id,
            @PathVariable Long fileId) {

        log.info("퍼블리싱 파일 조회 요청 - 퍼블리싱 ID: {}, 파일 ID: {}", id, fileId);
        PublishingFileDto.DetailResponse response = publishingService.getPublishingFile(id, fileId);
        return ApiResponse.success(response);
    }

    /**
     * 퍼블리싱 파일 서빙 (브라우저에서 직접 열기)
     *
     * <p>index.html 및 관련 리소스(CSS, JS, 이미지 등)를 브라우저에서 직접 볼 수 있도록 서빙합니다.
     * 예: /api/publishing/1/serve/index.html
     *     /api/publishing/1/serve/css/style.css
     *
     * <p>참고: X-Frame-Options는 SecurityConfig의 publishingServeFilterChain에서 비활성화됩니다.
     */
    @GetMapping("/{id}/serve/**")
    public ResponseEntity<Resource> serveFile(
            @PathVariable Long id,
            HttpServletRequest request) {

        // /api/publishing/{id}/serve/ 이후의 경로 추출
        String requestUri = request.getRequestURI();
        String basePath = "/api/publishing/" + id + "/serve/";
        String filePath = requestUri.substring(requestUri.indexOf(basePath) + basePath.length());

        // 경로가 비어있으면 index.html로 기본 설정
        if (filePath.isEmpty() || filePath.equals("/")) {
            filePath = "index.html";
        }

        log.debug("퍼블리싱 파일 서빙 요청 - 퍼블리싱 ID: {}, 파일 경로: {}", id, filePath);

        Resource resource = publishingService.serveFile(id, filePath);

        // Content-Type 결정
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(resource);
    }

    /**
     * 퍼블리싱 파일 구조 조회
     */
    @Override
    @GetMapping("/{id}/files")
    public ApiResponse<PublishingDto.FileStructureResponse> getFiles(@PathVariable Long id) {
        log.info("퍼블리싱 파일 구조 조회 요청 - ID: {}", id);
        PublishingDto.FileStructureResponse response = publishingService.getFileTree(id);
        return ApiResponse.success(response);
    }

    /**
     * 퍼블리싱 파일 내용 조회
     */
    @Override
    @GetMapping("/{id}/files/content")
    public ApiResponse<PublishingDto.FileContentResponse> getFileContent(
            @PathVariable Long id,
            @RequestParam String path) {

        log.info("퍼블리싱 파일 내용 조회 요청 - ID: {}, Path: {}", id, path);
        PublishingDto.FileContentResponse response = publishingService.getFileContent(id, path);
        return ApiResponse.success(response);
    }

    /**
     * 퍼블리싱 전체 다운로드 (ZIP)
     */
    @Override
    @GetMapping("/{id}/download")
    public void downloadPublishing(
            @PathVariable Long id,
            HttpServletResponse response) throws IOException {

        log.info("퍼블리싱 다운로드 요청 - ID: {}", id);

        String fileName = publishingService.getDownloadFileName(id);
        long uncompressedSize = publishingService.calculateUncompressedSize(id);

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                HttpFileDownloadUtil.buildContentDisposition(fileName));

        // 압축 전 크기를 커스텀 헤더로 전달 (프론트엔드 진행률 표시용)
        response.setHeader("X-Uncompressed-Size", String.valueOf(uncompressedSize));

        publishingService.downloadPublishing(id, response.getOutputStream());

        log.info("퍼블리싱 다운로드 완료 - ID: {}, fileName: {}", id, fileName);
    }
}
