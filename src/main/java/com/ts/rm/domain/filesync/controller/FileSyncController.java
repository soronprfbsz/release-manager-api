package com.ts.rm.domain.filesync.controller;

import com.ts.rm.domain.filesync.service.FileSyncService;
import com.ts.rm.domain.filesync.dto.FileSyncDto;
import com.ts.rm.domain.filesync.enums.FileSyncTarget;
import com.ts.rm.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 파일 동기화 API 컨트롤러
 *
 * <p>파일시스템과 DB 메타데이터 간의 동기화를 관리합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/file-sync")
@RequiredArgsConstructor
public class FileSyncController implements FileSyncControllerDocs {

    private final FileSyncService fileSyncService;

    /**
     * 파일 동기화 분석
     *
     * <p>파일시스템과 DB 메타데이터를 비교하여 불일치 항목을 반환합니다.
     *
     * @param request 분석 요청 (대상, 경로 등)
     * @return 분석 결과 (불일치 목록)
     */
    @Override
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<FileSyncDto.AnalyzeResponse>> analyze(
            @RequestBody(required = false) FileSyncDto.AnalyzeRequest request) {

        if (request == null) {
            request = FileSyncDto.AnalyzeRequest.builder().build();
        }

        log.info("파일 동기화 분석 요청 - targets: {}, basePath: {}",
                request.getTargets(), request.getBasePath());

        FileSyncDto.AnalyzeResponse response = fileSyncService.analyze(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 동기화 액션 적용
     *
     * <p>분석 결과에서 선택한 액션들을 일괄 적용합니다.
     *
     * @param request 적용 요청 (액션 목록)
     * @return 적용 결과
     */
    @Override
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<FileSyncDto.ApplyResponse>> apply(
            @Valid @RequestBody FileSyncDto.ApplyRequest request) {

        log.info("파일 동기화 적용 요청 - {}건", request.getActions().size());

        FileSyncDto.ApplyResponse response = fileSyncService.apply(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 무시된 파일 목록 조회
     *
     * @param targetType 대상 유형 필터 (선택)
     * @return 무시된 파일 목록
     */
    @Override
    @GetMapping("/ignores")
    public ResponseEntity<ApiResponse<List<FileSyncDto.IgnoredFile>>> getIgnoredFiles(
            @RequestParam(required = false) FileSyncTarget targetType) {

        log.info("무시된 파일 목록 조회 요청 - targetType: {}", targetType);

        List<FileSyncDto.IgnoredFile> ignoredFiles = fileSyncService.getIgnoredFiles(targetType);

        return ResponseEntity.ok(ApiResponse.success(ignoredFiles));
    }

    /**
     * 무시 목록에서 제거
     *
     * @param ignoreId 무시 항목 ID
     * @return 성공 응답
     */
    @Override
    @DeleteMapping("/ignores/{ignoreId}")
    public ResponseEntity<ApiResponse<Void>> removeFromIgnoreList(
            @PathVariable Long ignoreId) {

        log.info("무시 목록 제거 요청 - ignoreId: {}", ignoreId);

        fileSyncService.removeFromIgnoreList(ignoreId);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ========================================
    // 파일 등록 API (유형별 분리)
    // ========================================

    /**
     * 리소스 파일 등록
     *
     * <p>분석 결과에서 UNREGISTERED 상태인 리소스 파일들을 등록합니다.
     *
     * @param request 등록 요청
     * @return 등록 결과
     */
    @Override
    @PostMapping("/resources/register")
    public ResponseEntity<ApiResponse<FileSyncDto.RegisterResponse>> registerResourceFiles(
            @Valid @RequestBody FileSyncDto.ResourceFileRegisterRequest request) {

        log.info("리소스 파일 등록 요청 - {}건", request.getItems().size());

        FileSyncDto.RegisterResponse response = fileSyncService.registerResourceFiles(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 백업 파일 등록
     *
     * <p>분석 결과에서 UNREGISTERED 상태인 백업 파일들을 등록합니다.
     *
     * @param request 등록 요청
     * @return 등록 결과
     */
    @Override
    @PostMapping("/backups/register")
    public ResponseEntity<ApiResponse<FileSyncDto.RegisterResponse>> registerBackupFiles(
            @Valid @RequestBody FileSyncDto.BackupFileRegisterRequest request) {

        log.info("백업 파일 등록 요청 - {}건", request.getItems().size());

        FileSyncDto.RegisterResponse response = fileSyncService.registerBackupFiles(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 패치 파일 등록
     *
     * <p>분석 결과에서 UNREGISTERED 상태인 패치 폴더들을 등록합니다.
     *
     * @param request 등록 요청
     * @return 등록 결과
     */
    @Override
    @PostMapping("/patches/register")
    public ResponseEntity<ApiResponse<FileSyncDto.RegisterResponse>> registerPatchFiles(
            @Valid @RequestBody FileSyncDto.PatchFileRegisterRequest request) {

        log.info("패치 파일 등록 요청 - {}건", request.getItems().size());

        FileSyncDto.RegisterResponse response = fileSyncService.registerPatchFiles(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 릴리즈 파일 등록
     *
     * <p>분석 결과에서 UNREGISTERED 상태인 릴리즈 파일들을 등록합니다.
     *
     * @param request 등록 요청
     * @return 등록 결과
     */
    @Override
    @PostMapping("/releases/register")
    public ResponseEntity<ApiResponse<FileSyncDto.RegisterResponse>> registerReleaseFiles(
            @Valid @RequestBody FileSyncDto.ReleaseFileRegisterRequest request) {

        log.info("릴리즈 파일 등록 요청 - {}건", request.getItems().size());

        FileSyncDto.RegisterResponse response = fileSyncService.registerReleaseFiles(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
