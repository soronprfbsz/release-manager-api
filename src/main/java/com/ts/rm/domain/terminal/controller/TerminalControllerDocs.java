package com.ts.rm.domain.terminal.controller;

import com.ts.rm.domain.terminal.dto.TerminalDto;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * TerminalController Swagger 문서화 인터페이스
 */
@Tag(name = "터미널", description = "터미널 API")
@SwaggerResponse
public interface TerminalControllerDocs {

    @Operation(
            summary = "터미널 생성",
            description = "SSH를 통해 터미널 세션을 생성합니다. "
                    + "생성 후 WebSocket을 통해 명령어를 전송하고 실시간 출력을 받을 수 있습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConnectApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<TerminalDto.ConnectResponse>> createTerminal(
            @Valid @RequestBody TerminalDto.ConnectRequest request,
            Authentication authentication
    );

    @Operation(
            summary = "터미널 정보 조회",
            description = "특정 터미널의 현재 상태 및 정보를 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SessionInfoApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<TerminalDto.ShellSessionInfo>> getTerminal(
            @Parameter(description = "터미널 ID", example = "terminal_2025-12-09T22_00_00_abc123")
            @PathVariable String id
    );

    @Operation(
            summary = "터미널 삭제",
            description = "활성 터미널을 종료하고 삭제합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    example = "{\"status\": \"success\", \"data\": \"터미널이 삭제되었습니다\"}"
                            )
                    )
            )
    )
    ResponseEntity<ApiResponse<String>> deleteTerminal(
            @Parameter(description = "터미널 ID", example = "terminal_2025-12-09T22_00_00_abc123")
            @PathVariable String id
    );

    @Operation(
            summary = "파일 업로드",
            description = "클라이언트에서 원격 SSH 호스트로 파일을 업로드합니다. "
                    + "SFTP 프로토콜을 사용하며, 원격 경로가 존재하지 않으면 자동으로 생성됩니다. "
                    + "기본 업로드 경로는 /release-manager/uploads 입니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FileTransferApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<TerminalDto.FileTransferResponse>> uploadFile(
            @Parameter(description = "터미널 ID", example = "terminal_2025-12-09T22_00_00_abc123")
            @PathVariable String id,

            @Parameter(description = "업로드할 파일")
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "원격 경로 (디렉토리, 기본값: /release-manager/uploads)",
                    example = "/release-manager/uploads")
            @RequestParam(value = "remotePath", defaultValue = "/release-manager/uploads") String remotePath
    );

    @Operation(
            summary = "패치 파일 배포",
            description = "서버에 저장된 패치 파일을 원격 SSH 호스트로 배포합니다. "
                    + "SFTP 프로토콜을 사용하며, 원격 경로가 존재하지 않으면 자동으로 생성됩니다. "
                    + "기본 배포 경로는 /release-manager/patches 입니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FileTransferApiResponse.class)
                    )
            )
    )
    ResponseEntity<ApiResponse<TerminalDto.FileTransferResponse>> deployPatch(
            @Parameter(description = "터미널 ID", example = "terminal_2025-12-09T22_00_00_abc123")
            @PathVariable String id,

            @Valid @RequestBody TerminalDto.PatchDeploymentRequest request
    );

    /**
     * Swagger 스키마용 wrapper 클래스 - 터미널 연결 응답
     */
    @Schema(description = "터미널 연결 API 응답")
    class ConnectApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "터미널 연결 정보")
        public TerminalDto.ConnectResponse data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 터미널 세션 정보 응답
     */
    @Schema(description = "터미널 세션 정보 API 응답")
    class SessionInfoApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "터미널 세션 정보")
        public TerminalDto.ShellSessionInfo data;
    }

    /**
     * Swagger 스키마용 wrapper 클래스 - 파일 전송 응답
     */
    @Schema(description = "파일 전송 API 응답")
    class FileTransferApiResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "파일 전송 결과")
        public TerminalDto.FileTransferResponse data;
    }
}
