package com.ts.rm.domain.patch.controller;

import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.mapper.PatchDtoMapper;
import com.ts.rm.domain.patch.service.PatchGenerationService;
import com.ts.rm.domain.patch.service.PatchService;
import com.ts.rm.global.file.HttpFileDownloadUtil;
import com.ts.rm.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 패치 관리 API Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/patches")
@RequiredArgsConstructor
public class PatchController implements PatchControllerDocs {

    private final PatchService patchService;
    private final PatchDtoMapper patchDtoMapper;

    /**
     * 표준 패치 생성 (누적 패치 생성)
     */
    @Override
    @PostMapping("/standard/generate")
    public ApiResponse<PatchDto.GenerateResponse> generatePatch(
            @Valid @RequestBody PatchDto.GenerateRequest request) {

        log.info("패치 생성 요청 - Project: {}, From: {}, To: {}, Type: {}, PatchName: {}, BuildSelection: {}",
                request.projectId(), request.fromVersion(), request.toVersion(), request.type(),
                request.patchName(), request.buildSelection());

        PatchGenerationService.GenerateResult result = patchService.generatePatchByVersion(
                request.projectId(),
                request.type(),
                request.customerId(),
                request.fromVersion(),
                request.toVersion(),
                request.createdByEmail(),
                request.description(),
                request.assigneeId(),
                request.patchName(),
                request.buildSelection()
        );

        PatchDto.GenerateResponse body = new PatchDto.GenerateResponse(
                result.patch().getPatchId(),
                result.patch().getPatchName(),
                result.patch().getOutputPath(),
                result.isBuildOnly(),
                result.hotfixesInRange(),
                result.includedBuilds()
        );

        return ApiResponse.success(body);
    }

    /**
     * 패치 상세 조회
     */
    @Override
    @GetMapping("/{id}")
    public ApiResponse<PatchDto.DetailResponse> getPatch(@PathVariable Long id) {

        log.info("패치 조회 요청 - ID: {}", id);

        Patch patch = patchService.getPatch(id);
        PatchDto.DetailResponse response = patchDtoMapper.toDetailResponse(patch);

        return ApiResponse.success(response);
    }

    /**
     * 패치 목록 조회 (페이징)
     */
    @Override
    @GetMapping
    public ApiResponse<Page<PatchDto.ListResponse>> listPatches(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String releaseType,
            @RequestParam(required = false) String customerCode,
            @ParameterObject Pageable pageable) {

        log.info("패치 목록 조회 요청 - projectId: {}, releaseType: {}, customerCode: {}, page: {}, size: {}",
                projectId, releaseType, customerCode, pageable.getPageNumber(), pageable.getPageSize());

        // API 정렬 필드를 엔티티 경로로 매핑
        Pageable mappedPageable = com.ts.rm.global.querydsl.SortFieldMapper.mapPatchSortFields(pageable);

        Page<PatchDto.ListResponse> response = patchService.listPatchesWithPaging(projectId, releaseType, customerCode, mappedPageable);

        return ApiResponse.success(response);
    }

    /**
     * 패치 파일 구조 조회
     */
    @Override
    @GetMapping("/{id}/files")
    public ApiResponse<PatchDto.FileStructureResponse> getPatchFileStructure(@PathVariable Long id) {

        log.info("패치 파일 구조 조회 요청 - ID: {}", id);

        Patch patch = patchService.getPatch(id);
        PatchDto.DirectoryNode root = patchService.getZipFileStructure(id);

        PatchDto.FileStructureResponse response = new PatchDto.FileStructureResponse(
                patch.getPatchId(),
                patch.getPatchName(),
                root
        );

        return ApiResponse.success(response);
    }

    /**
     * 패치 다운로드 (ZIP)
     *
     * <p>응답 헤더:
     * <ul>
     *   <li><b>X-Uncompressed-Size</b>: 압축 전 총 파일 크기 (바이트) - 진행률 표시용</li>
     * </ul>
     */
    @Override
    @GetMapping("/{id}/download")
    public void downloadPatch(
            @PathVariable Long id,
            HttpServletResponse response) throws IOException {

        log.info("패치 다운로드 요청 - ID: {}", id);

        String fileName = patchService.getZipFileName(id);
        long uncompressedSize = patchService.calculateUncompressedSize(id);

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                HttpFileDownloadUtil.buildContentDisposition(fileName));

        // 압축 전 크기를 커스텀 헤더로 전달 (프론트엔드 진행률 표시용)
        response.setHeader("X-Uncompressed-Size", String.valueOf(uncompressedSize));

        patchService.streamPatchAsZip(id, response.getOutputStream());

        log.info("패치 다운로드 완료 - ID: {}, fileName: {}, uncompressedSize: {} bytes",
                id, fileName, uncompressedSize);
    }

    /**
     * 패치 파일 내용 조회
     */
    @Override
    @GetMapping("/{id}/content")
    public ApiResponse<PatchDto.FileContentResponse> getFileContent(
            @PathVariable Long id,
            @RequestParam String path) {

        log.info("패치 파일 내용 조회 요청 - ID: {}, Path: {}", id, path);

        PatchDto.FileContentResponse response = patchService.getFileContent(id, path);

        return ApiResponse.success(response);
    }

    /**
     * 패치 삭제
     */
    @Override
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePatch(@PathVariable Long id) {

        log.info("패치 삭제 요청 - ID: {}", id);

        patchService.deletePatch(id);

        return ApiResponse.success(null);
    }

    /**
     * 패치 일괄 삭제
     */
    @Override
    @DeleteMapping
    public ApiResponse<PatchDto.BatchDeleteResponse> batchDeletePatches(
            @RequestParam List<Long> ids) {

        log.info("패치 일괄 삭제 요청 - ids: {}", ids);

        PatchDto.BatchDeleteResponse response = patchService.batchDeletePatches(ids);

        log.info("패치 일괄 삭제 완료 - {}", response.message());
        return ApiResponse.success(response);
    }

    // ========================================
    // 커스텀 패치 API
    // ========================================

    /**
     * 커스텀 버전 보유 고객사 목록 조회
     */
    @Override
    @GetMapping("/custom/customers")
    public ApiResponse<List<PatchDto.CustomerWithCustomVersions>> getCustomersWithCustomVersions(
            @RequestParam String projectId) {

        log.info("커스텀 버전 보유 고객사 목록 조회 요청 - projectId: {}", projectId);

        List<PatchDto.CustomerWithCustomVersions> customers = patchService.getCustomersWithCustomVersions(projectId);

        return ApiResponse.success(customers);
    }

    /**
     * 고객사별 커스텀 버전 목록 조회
     */
    @Override
    @GetMapping("/custom/customers/{customerId}/versions")
    public ApiResponse<List<PatchDto.CustomVersionSelectOption>> getCustomVersionsByCustomer(
            @RequestParam String projectId,
            @PathVariable Long customerId) {

        log.info("고객사별 커스텀 버전 목록 조회 요청 - projectId: {}, customerId: {}", projectId, customerId);

        List<PatchDto.CustomVersionSelectOption> versions = patchService.getCustomVersionsByCustomer(projectId, customerId);

        return ApiResponse.success(versions);
    }

    /**
     * 커스텀 패치 생성
     */
    @Override
    @PostMapping("/custom/generate")
    public ApiResponse<PatchDto.DetailResponse> generateCustomPatch(
            @Valid @RequestBody PatchDto.GenerateCustomPatchRequest request) {

        log.info("커스텀 패치 생성 요청 - Project: {}, Customer: {}, From: {}, To: {}",
                request.projectId(), request.customerId(), request.fromVersion(), request.toVersion());

        Patch patch = patchService.generateCustomPatchByVersion(
                request.projectId(),
                request.customerId(),
                request.fromVersion(),
                request.toVersion(),
                request.createdByEmail(),
                request.description(),
                request.assigneeId(),
                request.patchName()
        );

        PatchDto.DetailResponse response = patchDtoMapper.toDetailResponse(patch);

        return ApiResponse.success(response);
    }
}
