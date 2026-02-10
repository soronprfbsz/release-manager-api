package com.ts.rm.domain.terminal.controller;

import com.ts.rm.domain.terminal.dto.TerminalDto;
import com.ts.rm.domain.terminal.service.TerminalService;
import com.ts.rm.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 터미널 REST API Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/terminal")
@RequiredArgsConstructor
public class TerminalController implements TerminalControllerDocs {

    private final TerminalService shellOrchestrator;

    /**
     * 터미널 생성
     *
     * @param request        연결 요청 정보
     * @param authentication 인증 정보
     * @return 터미널 정보
     */
    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<TerminalDto.ConnectResponse>> createTerminal(
            @Valid @RequestBody TerminalDto.ConnectRequest request,
            Authentication authentication) {

        log.info("터미널 생성 요청: host={}@{}:{}", request.getUsername(), request.getHost(), request.getPort());

        // 현재 사용자 이메일 추출
        String ownerEmail = authentication.getName();

        TerminalDto.ConnectResponse response = shellOrchestrator.connect(request, ownerEmail);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 터미널 정보 조회
     *
     * @param id 터미널 ID
     * @return 터미널 정보
     */
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TerminalDto.ShellSessionInfo>> getTerminal(
            @PathVariable String id) {

        log.info("터미널 정보 조회: id={}", id);

        TerminalDto.ShellSessionInfo response = shellOrchestrator.getSessionInfo(id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 터미널 삭제
     *
     * @param id 터미널 ID
     * @return 성공 메시지
     */
    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteTerminal(
            @PathVariable String id) {

        log.info("터미널 삭제 요청: id={}", id);

        shellOrchestrator.disconnect(id);

        return ResponseEntity.ok(ApiResponse.success("터미널이 삭제되었습니다"));
    }

    /**
     * 파일 업로드 (클라이언트 → 원격 호스트)
     *
     * @param id     터미널 ID
     * @param file   업로드할 파일
     * @param remotePath 원격 경로 (디렉토리)
     * @return 파일 전송 응답
     */
    @Override
    @PostMapping("/{id}/files")
    public ResponseEntity<ApiResponse<TerminalDto.FileTransferResponse>> uploadFile(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "remotePath", defaultValue = "/release-manager/uploads") String remotePath) {

        log.info("파일 업로드 요청: terminalId={}, file={}, remotePath={}", id, file.getOriginalFilename(), remotePath);

        TerminalDto.FileTransferResponse response = shellOrchestrator.uploadFile(id, file, remotePath);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 패치 파일 배포 (서버 → 원격 호스트)
     *
     * @param id      터미널 ID
     * @param request 패치 배포 요청
     * @return 파일 전송 응답
     */
    @Override
    @PostMapping("/{id}/patches")
    public ResponseEntity<ApiResponse<TerminalDto.FileTransferResponse>> deployPatch(
            @PathVariable String id,
            @Valid @RequestBody TerminalDto.PatchDeploymentRequest request) {

        log.info("패치 파일 배포 요청: terminalId={}, patchId={}, remotePath={}",
                id, request.getPatchId(), request.getRemotePath());

        TerminalDto.FileTransferResponse response = shellOrchestrator.deployPatch(
                id,
                request.getPatchId(),
                request.getRemotePath()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
