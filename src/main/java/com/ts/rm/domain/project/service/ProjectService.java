package com.ts.rm.domain.project.service;

import com.ts.rm.domain.project.dto.ProjectDto;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.mapper.ProjectDtoMapper;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.file.FileContentUtil;
import com.ts.rm.global.file.StreamingZipUtil;
import com.ts.rm.global.file.ZipExtractUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project Service
 *
 * <p>프로젝트 관리 비즈니스 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectDtoMapper mapper;

    @Value("${app.release.base-path:data/release-manager}")
    private String baseReleasePath;

    /**
     * 프로젝트 생성
     *
     * @param request 프로젝트 생성 요청
     * @return 생성된 프로젝트 상세 정보
     */
    @Transactional
    public ProjectDto.DetailResponse createProject(ProjectDto.CreateRequest request) {
        log.info("Creating project with id: {}", request.projectId());

        // 중복 검증
        if (projectRepository.existsByProjectId(request.projectId())) {
            throw new BusinessException(ErrorCode.PROJECT_ID_CONFLICT);
        }

        Project project = mapper.toEntity(request);

        // isEnabled 기본값 처리 (null이면 true)
        project.setIsEnabled(request.isEnabled() != null ? request.isEnabled() : true);

        // 글리프 필드 처리
        if (request.glyphText() != null) {
            project.setGlyphText(request.glyphText().isBlank() ? null : request.glyphText());
        }
        if (request.glyphBackgroundColor() != null) {
            project.setGlyphBackgroundColor(request.glyphBackgroundColor().isBlank() ? null : request.glyphBackgroundColor());
        }

        Project savedProject = projectRepository.save(project);

        log.info("Project created successfully with id: {}", savedProject.getProjectId());
        return mapper.toDetailResponse(savedProject);
    }

    /**
     * 프로젝트 조회 (ID)
     *
     * @param projectId 프로젝트 ID
     * @return 프로젝트 상세 정보
     */
    public ProjectDto.DetailResponse getProjectById(String projectId) {
        Project project = findProjectById(projectId);
        return mapper.toDetailResponse(project);
    }

    /**
     * 프로젝트 목록 조회
     *
     * @param isEnabled 활성 여부 필터 (null이면 전체 조회)
     * @return 프로젝트 목록
     */
    public List<ProjectDto.DetailResponse> getAllProjects(Boolean isEnabled) {
        List<Project> projects;
        if (isEnabled != null) {
            projects = projectRepository.findAllByIsEnabledOrderByProjectNameAsc(isEnabled);
        } else {
            projects = projectRepository.findAllByOrderByProjectNameAsc();
        }
        return mapper.toDetailResponseList(projects);
    }

    /**
     * 프로젝트 정보 수정
     *
     * @param projectId 프로젝트 ID
     * @param request   수정 요청
     * @return 수정된 프로젝트 상세 정보
     */
    @Transactional
    public ProjectDto.DetailResponse updateProject(String projectId,
            ProjectDto.UpdateRequest request) {
        log.info("Updating project with id: {}", projectId);

        // 엔티티 조회
        Project project = findProjectById(projectId);

        // Setter를 통한 수정 (JPA Dirty Checking)
        if (request.projectName() != null) {
            project.setProjectName(request.projectName());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.isEnabled() != null) {
            project.setIsEnabled(request.isEnabled());
        }

        // 글리프 필드 수정 (null=미변경, ""=제거)
        project.updateGlyph(request.glyphText(), request.glyphBackgroundColor());

        // 트랜잭션 커밋 시 자동으로 UPDATE 쿼리 실행 (Dirty Checking)
        log.info("Project updated successfully with id: {}", projectId);
        return mapper.toDetailResponse(project);
    }

    /**
     * 프로젝트 삭제
     *
     * @param projectId 프로젝트 ID
     */
    @Transactional
    public void deleteProject(String projectId) {
        log.info("Deleting project with id: {}", projectId);

        // 프로젝트 존재 검증
        Project project = findProjectById(projectId);
        projectRepository.delete(project);

        log.info("Project deleted successfully with id: {}", projectId);
    }

    /**
     * 프로젝트 존재 여부 확인
     *
     * @param projectId 프로젝트 ID
     * @return 존재 여부
     */
    public boolean existsById(String projectId) {
        return projectRepository.existsByProjectId(projectId);
    }

    // === Onboarding File Methods (파일시스템 기반) ===

    /**
     * 프로젝트별 온보딩 파일 트리 조회 (파일시스템 기반)
     *
     * @param projectId 프로젝트 ID
     * @return 온보딩 파일 트리 응답
     */
    public ProjectDto.OnboardingFilesResponse getOnboardingFiles(String projectId) {
        log.info("온보딩 파일 조회 요청 - projectId: {}", projectId);

        // 프로젝트 조회 및 검증
        Project project = findProjectById(projectId);

        // 온보딩 파일 경로: {baseReleasePath}/onboardings/{projectId}
        Path onboardingPath = Paths.get(baseReleasePath, "onboardings", projectId);

        // 디렉토리가 존재하지 않는 경우 빈 응답 반환
        if (!Files.exists(onboardingPath) || !Files.isDirectory(onboardingPath)) {
            log.info("온보딩 디렉토리 미존재 - path: {}", onboardingPath);
            return new ProjectDto.OnboardingFilesResponse(
                    projectId,
                    project.getProjectName(),
                    false,
                    0,
                    0L,
                    ProjectDto.OnboardingFileNode.directory("root", "/", "onboardings/" + projectId, List.of())
            );
        }

        // 파일 트리 빌드
        FileTreeResult result = buildOnboardingFileTree(onboardingPath, projectId);

        log.info("온보딩 파일 조회 완료 - projectId: {}, fileCount: {}, totalSize: {}",
                projectId, result.fileCount, result.totalSize);

        return new ProjectDto.OnboardingFilesResponse(
                projectId,
                project.getProjectName(),
                result.fileCount > 0,
                result.fileCount,
                result.totalSize,
                result.rootNode
        );
    }

    /**
     * 온보딩 파일 트리 빌드
     */
    private FileTreeResult buildOnboardingFileTree(Path rootPath, String projectId) {
        int[] fileCount = {0};
        long[] totalSize = {0L};

        String onboardingRelativeBase = "onboardings/" + projectId;
        ProjectDto.OnboardingFileNode rootNode = buildDirectoryNode(rootPath, rootPath, onboardingRelativeBase, fileCount, totalSize);

        return new FileTreeResult(rootNode, fileCount[0], totalSize[0]);
    }

    /**
     * 디렉토리 노드 재귀 빌드
     */
    private ProjectDto.OnboardingFileNode buildDirectoryNode(Path currentPath, Path rootPath,
            String onboardingRelativeBase, int[] fileCount, long[] totalSize) {
        List<ProjectDto.OnboardingFileNode> children = new ArrayList<>();

        try (Stream<Path> stream = Files.list(currentPath)) {
            List<Path> sortedPaths = stream
                    .sorted(Comparator
                            .comparing((Path p) -> Files.isDirectory(p) ? 0 : 1)  // 디렉토리 우선
                            .thenComparing(p -> p.getFileName().toString().toLowerCase()))  // 이름순
                    .toList();

            for (Path path : sortedPaths) {
                String relativePath = "/" + rootPath.relativize(path).toString().replace("\\", "/");
                String name = path.getFileName().toString();

                // filePath 생성 (onboardings/{projectId}/ 포함)
                String filePathStr = onboardingRelativeBase + "/" + rootPath.relativize(path).toString().replace("\\", "/");

                if (Files.isDirectory(path)) {
                    // 디렉토리인 경우 재귀 호출
                    ProjectDto.OnboardingFileNode dirNode = buildDirectoryNode(path, rootPath, onboardingRelativeBase, fileCount, totalSize);
                    children.add(dirNode);
                } else {
                    // 파일인 경우
                    try {
                        long size = Files.size(path);
                        LocalDateTime modifiedAt = getLastModifiedTime(path);
                        String mimeType = FileContentUtil.getMimeType(path);
                        fileCount[0]++;
                        totalSize[0] += size;
                        children.add(ProjectDto.OnboardingFileNode.file(name, relativePath, filePathStr, size, modifiedAt, mimeType));
                    } catch (IOException e) {
                        log.warn("파일 정보 조회 실패: {}", path, e);
                        children.add(ProjectDto.OnboardingFileNode.file(name, relativePath, filePathStr, 0L, null, null));
                    }
                }
            }
        } catch (IOException e) {
            log.error("디렉토리 읽기 실패: {}", currentPath, e);
        }

        String relativePath = currentPath.equals(rootPath)
                ? "/"
                : "/" + rootPath.relativize(currentPath).toString().replace("\\", "/");
        String name = currentPath.equals(rootPath)
                ? "root"
                : currentPath.getFileName().toString();

        // filePath 생성 (onboardings/{projectId}/ 포함)
        String filePathStr = currentPath.equals(rootPath)
                ? onboardingRelativeBase
                : onboardingRelativeBase + "/" + rootPath.relativize(currentPath).toString().replace("\\", "/");

        return ProjectDto.OnboardingFileNode.directory(name, relativePath, filePathStr, children);
    }

    /**
     * 파일의 수정 날짜 조회
     */
    private LocalDateTime getLastModifiedTime(Path path) throws IOException {
        FileTime fileTime = Files.getLastModifiedTime(path);
        return LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 파일 트리 빌드 결과
     */
    private record FileTreeResult(
            ProjectDto.OnboardingFileNode rootNode,
            int fileCount,
            long totalSize
    ) {
    }

    // === Onboarding File Upload/Download/Delete Methods (파일시스템 기반) ===

    /**
     * 온보딩 디렉토리 생성
     *
     * @param projectId 프로젝트 ID
     * @param path      생성할 디렉토리 경로 (예: /mariadb/scripts)
     * @return 생성 결과 응답
     */
    public ProjectDto.OnboardingDirectoryResponse createOnboardingDirectory(String projectId, String path) {
        log.info("온보딩 디렉토리 생성 요청 - projectId: {}, path: {}", projectId, path);

        // 프로젝트 조회 및 검증
        findProjectById(projectId);

        // 경로 정규화
        String normalizedPath = path.replaceAll("^/+|/+$", "");
        if (normalizedPath.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "디렉토리 경로가 비어있습니다");
        }

        Path fullPath = Paths.get(baseReleasePath, "onboardings", projectId, normalizedPath);

        try {
            if (Files.exists(fullPath)) {
                throw new BusinessException(ErrorCode.DATA_CONFLICT, "이미 존재하는 경로입니다: " + path);
            }
            Files.createDirectories(fullPath);
            log.info("온보딩 디렉토리 생성 완료 - path: {}", fullPath);

        } catch (IOException e) {
            log.error("온보딩 디렉토리 생성 실패 - projectId: {}, error: {}", projectId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "디렉토리 생성에 실패했습니다: " + e.getMessage());
        }

        return new ProjectDto.OnboardingDirectoryResponse(
                projectId,
                "/" + normalizedPath,
                "디렉토리가 생성되었습니다."
        );
    }

    /**
     * 온보딩 파일 업로드 (ZIP 또는 단일 파일) - 파일시스템만 사용
     *
     * @param projectId   프로젝트 ID
     * @param file        업로드할 파일
     * @param targetPath  대상 경로 (null이면 루트에 저장)
     * @param description 파일 설명 (사용하지 않음, API 호환성 유지)
     * @param extractZip  ZIP 파일 압축 해제 여부 (true: 압축 해제, false: 원본 유지)
     * @return 업로드 결과 응답
     */
    public ProjectDto.OnboardingUploadResponse uploadOnboardingFile(
            String projectId, MultipartFile file, String targetPath, String description, boolean extractZip) {

        log.info("온보딩 파일 업로드 요청 - projectId: {}, fileName: {}, targetPath: {}, extractZip: {}",
                projectId, file.getOriginalFilename(), targetPath, extractZip);

        // 프로젝트 조회 및 검증
        findProjectById(projectId);

        // 온보딩 기본 경로
        Path onboardingBasePath = Paths.get(baseReleasePath, "onboardings", projectId);

        // 대상 경로 설정
        Path uploadTargetPath = onboardingBasePath;
        String targetSubPath = "";
        if (targetPath != null && !targetPath.isBlank()) {
            // 경로 정규화 (앞뒤 슬래시 제거)
            String normalizedPath = targetPath.replaceAll("^/+|/+$", "");
            if (!normalizedPath.isEmpty()) {
                uploadTargetPath = onboardingBasePath.resolve(normalizedPath);
                targetSubPath = normalizedPath;
            }
        }

        List<ProjectDto.UploadedFileInfo> uploadedFiles = new ArrayList<>();

        try {
            // 디렉토리 생성
            Files.createDirectories(uploadTargetPath);

            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일명이 없습니다");
            }

            // ZIP 파일이고 extractZip이 true인 경우 압축 해제
            if (extractZip && originalFileName.toLowerCase().endsWith(".zip")) {
                uploadedFiles = extractZipFile(file, uploadTargetPath, targetSubPath);
            } else {
                // 단일 파일 저장 (ZIP 파일도 extractZip=false이면 원본 그대로 저장)
                Path targetFilePath = uploadTargetPath.resolve(originalFileName);
                Files.copy(file.getInputStream(), targetFilePath, StandardCopyOption.REPLACE_EXISTING);

                long fileSize = Files.size(targetFilePath);
                String relativePath = "/" + targetSubPath + (targetSubPath.isEmpty() ? "" : "/") + originalFileName;
                uploadedFiles.add(new ProjectDto.UploadedFileInfo(
                        originalFileName,
                        relativePath,
                        fileSize
                ));
            }

            log.info("온보딩 파일 업로드 완료 - projectId: {}, uploadedCount: {}", projectId, uploadedFiles.size());

            return new ProjectDto.OnboardingUploadResponse(
                    projectId,
                    uploadedFiles.size(),
                    uploadedFiles,
                    uploadedFiles.size() + "개 파일이 업로드되었습니다."
            );

        } catch (IOException e) {
            log.error("온보딩 파일 업로드 실패 - projectId: {}, error: {}", projectId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "파일 업로드에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * ZIP 파일 압축 해제 (파일시스템만 사용)
     */
    private List<ProjectDto.UploadedFileInfo> extractZipFile(
            MultipartFile zipFile, Path targetDir, String targetSubPath) throws IOException {

        List<ProjectDto.UploadedFileInfo> uploadedFiles = new ArrayList<>();

        ZipExtractUtil.extractWithCallback(zipFile.getInputStream(), targetDir, (entry, filePath) -> {
            try {
                String fileRelativePath = targetSubPath.isEmpty()
                        ? entry.getName()
                        : targetSubPath + "/" + entry.getName();

                long fileSize = Files.size(filePath);
                uploadedFiles.add(new ProjectDto.UploadedFileInfo(
                        filePath.getFileName().toString(),
                        "/" + fileRelativePath,
                        fileSize
                ));
            } catch (IOException e) {
                log.warn("파일 크기 조회 실패: {}", filePath, e);
            }
        });

        return uploadedFiles;
    }

    /**
     * 온보딩 단일 파일 다운로드
     *
     * @param projectId    프로젝트 ID
     * @param filePath     파일 경로 (예: /mariadb/init.sql)
     * @param outputStream 출력 스트림
     */
    public void downloadOnboardingFile(String projectId, String filePath, OutputStream outputStream) {
        log.info("온보딩 파일 다운로드 요청 - projectId: {}, filePath: {}", projectId, filePath);

        // 프로젝트 조회 및 검증
        findProjectById(projectId);

        // 경로 정규화
        String normalizedPath = filePath.replaceAll("^/+", "");
        Path fullPath = Paths.get(baseReleasePath, "onboardings", projectId, normalizedPath);

        if (!Files.exists(fullPath) || Files.isDirectory(fullPath)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "파일을 찾을 수 없습니다: " + filePath);
        }

        try (InputStream is = Files.newInputStream(fullPath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            log.info("온보딩 파일 다운로드 완료 - projectId: {}, filePath: {}", projectId, filePath);
        } catch (IOException e) {
            log.error("온보딩 파일 다운로드 실패 - projectId: {}, error: {}", projectId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, "파일 다운로드에 실패했습니다");
        }
    }

    /**
     * 온보딩 파일 크기 조회
     */
    public long getOnboardingFileSize(String projectId, String filePath) {
        String normalizedPath = filePath.replaceAll("^/+", "");
        Path fullPath = Paths.get(baseReleasePath, "onboardings", projectId, normalizedPath);

        if (!Files.exists(fullPath) || Files.isDirectory(fullPath)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "파일을 찾을 수 없습니다: " + filePath);
        }

        try {
            return Files.size(fullPath);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 온보딩 파일명 조회
     */
    public String getOnboardingFileName(String projectId, String filePath) {
        String normalizedPath = filePath.replaceAll("^/+", "");
        return Paths.get(normalizedPath).getFileName().toString();
    }

    /**
     * 온보딩 전체 파일 ZIP 다운로드
     *
     * @param projectId    프로젝트 ID
     * @param outputStream 출력 스트림
     */
    public void downloadAllOnboardingFiles(String projectId, OutputStream outputStream) {
        log.info("온보딩 전체 파일 다운로드 요청 - projectId: {}", projectId);

        // 프로젝트 조회 및 검증
        findProjectById(projectId);

        Path onboardingPath = Paths.get(baseReleasePath, "onboardings", projectId);

        if (!Files.exists(onboardingPath) || !Files.isDirectory(onboardingPath)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "온보딩 파일이 없습니다");
        }

        try {
            // 파일 목록 수집
            List<StreamingZipUtil.ZipFileEntry> fileEntries = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(onboardingPath)) {
                stream.filter(Files::isRegularFile)
                        .forEach(path -> {
                            String relativePath = onboardingPath.relativize(path).toString().replace("\\", "/");
                            fileEntries.add(new StreamingZipUtil.ZipFileEntry(path, relativePath));
                        });
            }

            if (fileEntries.isEmpty()) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "다운로드할 파일이 없습니다");
            }

            // ZIP 스트리밍
            StreamingZipUtil.compressFilesToStream(outputStream, fileEntries);
            log.info("온보딩 전체 파일 다운로드 완료 - projectId: {}, fileCount: {}", projectId, fileEntries.size());

        } catch (IOException e) {
            log.error("온보딩 전체 파일 다운로드 실패 - projectId: {}, error: {}", projectId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, "파일 다운로드에 실패했습니다");
        }
    }

    /**
     * 온보딩 전체 파일 크기 조회 (압축 전)
     */
    public long getOnboardingTotalSize(String projectId) {
        Path onboardingPath = Paths.get(baseReleasePath, "onboardings", projectId);

        if (!Files.exists(onboardingPath)) {
            return 0L;
        }

        try (Stream<Path> stream = Files.walk(onboardingPath)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 온보딩 파일 삭제 (파일 경로 기반) - 파일시스템에서만 삭제
     *
     * @param projectId 프로젝트 ID
     * @param filePath  파일 경로 (예: /mariadb/init.sql)
     * @return 삭제 결과 응답
     */
    public ProjectDto.OnboardingDeleteResponse deleteOnboardingFile(String projectId, String filePath) {
        log.info("온보딩 파일 삭제 요청 - projectId: {}, filePath: {}", projectId, filePath);

        // 프로젝트 조회 및 검증
        findProjectById(projectId);

        // 경로 정규화
        String normalizedPath = filePath.replaceAll("^/+", "");
        Path fullPath = Paths.get(baseReleasePath, "onboardings", projectId, normalizedPath);

        // 파일시스템에서 삭제
        if (Files.exists(fullPath)) {
            try {
                if (Files.isDirectory(fullPath)) {
                    // 디렉토리인 경우 하위 파일까지 전체 삭제
                    try (Stream<Path> stream = Files.walk(fullPath)) {
                        stream.sorted(Comparator.reverseOrder())
                                .forEach(path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException e) {
                                        log.warn("파일 삭제 실패: {}", path, e);
                                    }
                                });
                    }
                } else {
                    // 단일 파일 삭제
                    Files.delete(fullPath);
                }
                log.info("온보딩 파일 삭제 완료 - path: {}", fullPath);

            } catch (IOException e) {
                log.error("온보딩 파일 삭제 실패 - projectId: {}, error: {}", projectId, e.getMessage(), e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 삭제에 실패했습니다");
            }
        } else {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "파일을 찾을 수 없습니다: " + filePath);
        }

        return new ProjectDto.OnboardingDeleteResponse(
                projectId,
                filePath,
                "파일이 삭제되었습니다."
        );
    }

    // === Private Helper Methods ===

    /**
     * 프로젝트 조회 (존재하지 않으면 예외 발생)
     */
    private Project findProjectById(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
