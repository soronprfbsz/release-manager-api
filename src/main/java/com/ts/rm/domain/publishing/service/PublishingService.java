package com.ts.rm.domain.publishing.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.publishing.dto.PublishingDto;
import com.ts.rm.domain.publishing.dto.PublishingFileDto;
import com.ts.rm.domain.publishing.entity.Publishing;
import com.ts.rm.domain.publishing.entity.PublishingFile;
import com.ts.rm.domain.publishing.mapper.PublishingDtoMapper;
import com.ts.rm.domain.publishing.repository.PublishingFileRepository;
import com.ts.rm.domain.publishing.repository.PublishingRepository;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.file.FileChecksumUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Publishing 서비스
 *
 * <p>퍼블리싱 ZIP 업로드, 다운로드, 삭제 비즈니스 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublishingService {

    private final PublishingRepository publishingRepository;
    private final PublishingFileRepository publishingFileRepository;
    private final CustomerRepository customerRepository;
    private final PublishingDtoMapper publishingDtoMapper;
    private final AccountLookupService accountLookupService;

    @Value("${app.release.base-path:data/release-manager}")
    private String baseReleasePath;

    private static final String PUBLISHING_DIR = "resources/publishing";

    /**
     * 퍼블리싱 생성 (ZIP 업로드)
     *
     * @param zipFile ZIP 파일
     * @param request 생성 요청 정보
     * @return 생성된 퍼블리싱 상세 응답
     */
    @Transactional
    public PublishingDto.DetailResponse createPublishing(MultipartFile zipFile, PublishingDto.CreateRequest request) {
        log.info("퍼블리싱 생성 시작 - 이름: {}, 카테고리: {}", request.publishingName(), request.publishingCategory());

        // 퍼블리싱명 중복 검사
        if (publishingRepository.existsByPublishingName(request.publishingName())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 존재하는 퍼블리싱 이름입니다: " + request.publishingName());
        }

        // ZIP 파일 검증
        validateZipFile(zipFile);

        // 고객사 조회 (커스터마이징인 경우)
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "고객사를 찾을 수 없습니다: " + request.customerId()));
        }

        // sortOrder 자동 채번
        Integer maxSortOrder = publishingRepository.findMaxSortOrderByPublishingCategory(
                request.publishingCategory().toUpperCase());
        Integer sortOrder = maxSortOrder + 1;

        // 생성자 Account 조회
        Account creator = accountLookupService.findByEmail(request.createdByEmail());

        // glyphText / glyphBackgroundColor: 빈 문자열이면 null 처리
        String glyphText = (request.glyphText() == null || request.glyphText().isBlank())
                ? null : request.glyphText();
        String glyphBackgroundColor = (request.glyphBackgroundColor() == null || request.glyphBackgroundColor().isBlank())
                ? null : request.glyphBackgroundColor();

        // Publishing 엔티티 생성
        Publishing publishing = Publishing.builder()
                .publishingName(request.publishingName())
                .description(request.description())
                .publishingCategory(request.publishingCategory().toUpperCase())
                .subCategory(request.subCategory() != null ? request.subCategory().toUpperCase() : null)
                .customer(customer)
                .sortOrder(sortOrder)
                .glyphText(glyphText)
                .glyphBackgroundColor(glyphBackgroundColor)
                .creator(creator)
                .createdByEmail(creator.getEmail())
                .build();

        Publishing savedPublishing = publishingRepository.save(publishing);
        log.info("퍼블리싱 엔티티 저장 완료 - ID: {}", savedPublishing.getPublishingId());

        // ZIP 파일 해제 및 파일 저장
        List<PublishingFile> files = extractZipAndSaveFiles(zipFile, savedPublishing);
        savedPublishing.getFiles().addAll(files);

        log.info("퍼블리싱 생성 완료 - ID: {}, 파일 수: {}", savedPublishing.getPublishingId(), files.size());

        return toDetailResponse(savedPublishing);
    }

    /**
     * 퍼블리싱 수정
     *
     * @param id      퍼블리싱 ID
     * @param request 수정 요청 정보
     * @return 수정된 퍼블리싱 상세 응답
     */
    @Transactional
    public PublishingDto.DetailResponse updatePublishing(Long id, PublishingDto.UpdateRequest request) {
        log.info("퍼블리싱 수정 시작 - ID: {}, 이름: {}", id, request.publishingName());

        Publishing publishing = getPublishingEntity(id);

        // 퍼블리싱명 중복 검사 (자기 자신 제외)
        if (!publishing.getPublishingName().equals(request.publishingName())
                && publishingRepository.existsByPublishingName(request.publishingName())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "이미 존재하는 퍼블리싱 이름입니다: " + request.publishingName());
        }

        // 고객사 조회 (커스터마이징인 경우)
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "고객사를 찾을 수 없습니다: " + request.customerId()));
        }

        // 수정자 Account 조회
        Account updater = accountLookupService.findByEmail(request.updatedByEmail());

        // 퍼블리싱 정보 업데이트
        publishing.setPublishingName(request.publishingName());
        publishing.setDescription(request.description());
        publishing.setPublishingCategory(request.publishingCategory().toUpperCase());
        publishing.setSubCategory(request.subCategory() != null ? request.subCategory().toUpperCase() : null);
        publishing.setCustomer(customer);
        publishing.setUpdater(updater);
        publishing.setUpdatedByEmail(updater.getEmail());
        publishing.updateGlyph(request.glyphText(), request.glyphBackgroundColor());

        log.info("퍼블리싱 수정 완료 - ID: {}", id);

        return toDetailResponse(publishing);
    }

    /**
     * 퍼블리싱 삭제
     *
     * @param id 퍼블리싱 ID
     */
    @Transactional
    public void deletePublishing(Long id) {
        log.info("퍼블리싱 삭제 시작 - ID: {}", id);

        Publishing publishing = getPublishingEntity(id);

        // 파일 삭제
        deletePublishingFiles(publishing);

        // 퍼블리싱 디렉토리 삭제
        Path publishingDir = getPublishingDirectory(publishing);
        deleteDirectoryRecursively(publishingDir);

        // DB 레코드 삭제
        publishingRepository.delete(publishing);

        log.info("퍼블리싱 삭제 완료 - ID: {}", id);
    }

    /**
     * 퍼블리싱 단건 조회
     */
    public PublishingDto.DetailResponse getPublishing(Long id) {
        Publishing publishing = getPublishingEntity(id);
        return toDetailResponse(publishing);
    }

    /**
     * 퍼블리싱 목록 조회 (필터링)
     */
    public List<PublishingDto.SimpleResponse> listPublishings(
            String publishingCategory,
            String subCategory,
            Long customerId,
            String keyword
    ) {
        List<Publishing> publishings = publishingRepository.findAllWithFilters(
                publishingCategory, subCategory, customerId, keyword);
        return publishings.stream()
                .map(this::toSimpleResponse)
                .toList();
    }

    /**
     * 퍼블리싱 순서 변경
     */
    @Transactional
    public void reorderPublishings(PublishingDto.ReorderRequest request) {
        log.info("퍼블리싱 순서 변경 시작 - 카테고리: {}, IDs: {}",
                request.publishingCategory(), request.publishingIds());

        List<Long> publishingIds = request.publishingIds();
        if (publishingIds == null || publishingIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "퍼블리싱 ID 목록은 비어있을 수 없습니다");
        }

        // 모든 퍼블리싱이 존재하고 동일한 카테고리인지 확인
        for (Long publishingId : publishingIds) {
            Publishing publishing = getPublishingEntity(publishingId);
            if (!request.publishingCategory().equalsIgnoreCase(publishing.getPublishingCategory())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "퍼블리싱 " + publishingId + "는 " + request.publishingCategory() + " 카테고리에 속하지 않습니다");
            }
        }

        // sortOrder 업데이트 (1부터 시작)
        int sortOrder = 1;
        for (Long publishingId : publishingIds) {
            Publishing publishing = getPublishingEntity(publishingId);
            publishing.setSortOrder(sortOrder++);
        }

        log.info("퍼블리싱 순서 변경 완료 - 카테고리: {}", request.publishingCategory());
    }

    /**
     * 퍼블리싱 파일 다운로드 (스트리밍)
     */
    public void downloadFile(Long publishingId, Long fileId, OutputStream outputStream) {
        PublishingFile file = publishingFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "파일을 찾을 수 없습니다: " + fileId));

        if (!file.getPublishing().getPublishingId().equals(publishingId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "해당 퍼블리싱에 속한 파일이 아닙니다");
        }

        Path filePath = Paths.get(baseReleasePath, file.getFilePath());
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "파일이 존재하지 않습니다: " + file.getFilePath());
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        } catch (IOException e) {
            log.error("파일 다운로드 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, "파일 다운로드에 실패했습니다");
        }
    }

    /**
     * 퍼블리싱 파일 정보 조회
     */
    public PublishingFileDto.DetailResponse getPublishingFile(Long publishingId, Long fileId) {
        PublishingFile file = publishingFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "파일을 찾을 수 없습니다: " + fileId));

        if (!file.getPublishing().getPublishingId().equals(publishingId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "해당 퍼블리싱에 속한 파일이 아닙니다");
        }

        return publishingDtoMapper.toFileDetailResponse(file);
    }

    /**
     * 파일명 조회
     */
    public String getFileName(Long publishingId, Long fileId) {
        PublishingFile file = publishingFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "파일을 찾을 수 없습니다: " + fileId));

        if (!file.getPublishing().getPublishingId().equals(publishingId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "해당 퍼블리싱에 속한 파일이 아닙니다");
        }

        return file.getFileName();
    }

    /**
     * 파일 크기 조회
     */
    public long getFileSize(Long publishingId, Long fileId) {
        PublishingFile file = publishingFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "파일을 찾을 수 없습니다: " + fileId));

        if (!file.getPublishing().getPublishingId().equals(publishingId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "해당 퍼블리싱에 속한 파일이 아닙니다");
        }

        return file.getFileSize() != null ? file.getFileSize() : 0L;
    }

    /**
     * 퍼블리싱 파일 서빙 (브라우저에서 직접 열기)
     *
     * @param publishingId 퍼블리싱 ID
     * @param filePath     요청 파일 경로 (예: index.html, css/style.css)
     * @return 파일 리소스
     */
    public Resource serveFile(Long publishingId, String filePath) {
        // 퍼블리싱 존재 여부 확인
        Publishing publishing = getPublishingEntity(publishingId);

        // 경로 조작 방지 (Path Traversal 공격 방지)
        if (filePath.contains("..") || filePath.startsWith("/")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "잘못된 파일 경로입니다");
        }

        Path publishingDir = getPublishingDirectory(publishing).normalize();

        // 실제 파일 경로 구성
        Path targetPath = publishingDir.resolve(filePath).normalize();

        // 보안: 퍼블리싱 디렉토리 내부 파일만 접근 허용
        if (!targetPath.startsWith(publishingDir)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "잘못된 파일 경로입니다");
        }

        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "파일을 찾을 수 없습니다: " + filePath);
        }

        try {
            Resource resource = new UrlResource(targetPath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "파일을 읽을 수 없습니다: " + filePath);
            }
        } catch (IOException e) {
            log.error("파일 서빙 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, "파일을 서빙할 수 없습니다");
        }
    }

    /**
     * 퍼블리싱 파일 트리 구조 조회
     *
     * @param publishingId 퍼블리싱 ID
     * @return 파일 트리 구조
     */
    public PublishingDto.FileStructureResponse getFileTree(Long publishingId) {
        Publishing publishing = getPublishingEntity(publishingId);

        Path publishingDir = getPublishingDirectory(publishing);

        if (!Files.exists(publishingDir)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND,
                    "퍼블리싱 디렉토리를 찾을 수 없습니다: " + publishing.getPublishingName());
        }

        try {
            String publishingOutputPath = Paths.get(PUBLISHING_DIR, publishing.getPublishingName()).toString().replace("\\", "/");
            PublishingDto.DirectoryNode root = buildDirectoryNode(publishingDir, publishingDir, publishingOutputPath);

            return new PublishingDto.FileStructureResponse(
                    publishing.getPublishingId(),
                    publishing.getPublishingName(),
                    root
            );
        } catch (IOException e) {
            log.error("퍼블리싱 디렉토리 구조 조회 실패: {}", publishingDir, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "퍼블리싱 디렉토리 구조를 조회할 수 없습니다: " + e.getMessage());
        }
    }

    /**
     * 퍼블리싱 파일 내용 조회
     *
     * @param publishingId 퍼블리싱 ID
     * @param relativePath 파일 상대 경로 (예: css/style.css)
     * @return 파일 내용
     */
    public PublishingDto.FileContentResponse getFileContent(Long publishingId, String relativePath) {
        // 퍼블리싱 존재 확인
        Publishing publishing = getPublishingEntity(publishingId);

        // 퍼블리싱 디렉토리 경로
        Path publishingDir = getPublishingDirectory(publishing);

        if (!Files.exists(publishingDir)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND,
                    "퍼블리싱 디렉토리를 찾을 수 없습니다: " + publishing.getPublishingName());
        }

        // 공통 유틸리티로 경로 검증 및 파일 내용 조회
        Path filePath = FileContentUtil.validateAndResolvePath(publishingDir, relativePath);
        FileContentUtil.FileContentResult result = FileContentUtil.readFileContent(filePath);

        return new PublishingDto.FileContentResponse(
                publishingId,
                relativePath,
                result.fileName(),
                result.size(),
                result.mimeType(),
                result.isBinary(),
                result.content()
        );
    }

    /**
     * 퍼블리싱 전체 다운로드 (ZIP 스트리밍)
     *
     * @param publishingId 퍼블리싱 ID
     * @param outputStream 출력 스트림
     */
    public void downloadPublishing(Long publishingId, OutputStream outputStream) {
        Publishing publishing = getPublishingEntity(publishingId);

        Path publishingDir = getPublishingDirectory(publishing);

        if (!Files.exists(publishingDir)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND,
                    "퍼블리싱 디렉토리를 찾을 수 없습니다: " + publishing.getPublishingName());
        }

        // 스트리밍 시작 전 파일 존재 여부 검증
        validatePublishingDirectoryFiles(publishingDir);

        StreamingZipUtil.compressDirectoryToStream(outputStream, publishingDir);
    }

    /**
     * 퍼블리싱 ZIP 파일명 생성
     *
     * @param publishingId 퍼블리싱 ID
     * @return ZIP 파일명
     */
    public String getDownloadFileName(Long publishingId) {
        Publishing publishing = getPublishingEntity(publishingId);
        return publishing.getPublishingName() + ".zip";
    }

    /**
     * 퍼블리싱 디렉토리의 압축 전 총 크기 계산
     *
     * @param publishingId 퍼블리싱 ID
     * @return 압축 전 총 크기 (바이트)
     */
    public long calculateUncompressedSize(Long publishingId) {
        Publishing publishing = getPublishingEntity(publishingId);

        Path publishingDir = getPublishingDirectory(publishing);

        if (!Files.exists(publishingDir)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND,
                    "퍼블리싱 디렉토리를 찾을 수 없습니다: " + publishing.getPublishingName());
        }

        try {
            return Files.walk(publishingDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            log.warn("파일 크기 계산 실패 - {}: {}", path, e.getMessage());
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.error("디렉토리 크기 계산 실패 - publishingId: {}", publishingId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "디렉토리 크기 계산 실패: " + e.getMessage());
        }
    }

    /**
     * 퍼블리싱 디렉토리 내 파일 존재 여부 검증
     */
    private void validatePublishingDirectoryFiles(Path directory) {
        try {
            long fileCount = Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .count();

            if (fileCount == 0) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND,
                        "퍼블리싱 디렉토리에 압축할 파일이 없습니다: " + directory);
            }

            log.debug("퍼블리싱 파일 검증 완료 - 파일 개수: {}", fileCount);

        } catch (IOException e) {
            log.error("퍼블리싱 디렉토리 검증 실패: {}", directory, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "퍼블리싱 디렉토리 검증 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // ==================== Private Methods ====================

    private Publishing getPublishingEntity(Long id) {
        return publishingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "퍼블리싱을 찾을 수 없습니다: " + id));
    }

    private void validateZipFile(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "ZIP 파일이 없습니다");
        }

        String originalFilename = zipFile.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "ZIP 파일만 업로드 가능합니다");
        }
    }

    private List<PublishingFile> extractZipAndSaveFiles(MultipartFile zipFile, Publishing publishing) {
        List<PublishingFile> files = new ArrayList<>();
        Path publishingDir = getPublishingDirectory(publishing);
        AtomicInteger sortOrder = new AtomicInteger(1);

        try {
            Files.createDirectories(publishingDir);

            ZipExtractUtil.extractWithCallback(zipFile.getInputStream(), publishingDir, (entry, filePath) -> {
                try {
                    // 파일 메타데이터 생성
                    String fileName = filePath.getFileName().toString();
                    String fileType = extractFileType(fileName);
                    String relativePath = PUBLISHING_DIR + "/" + publishing.getPublishingName() + "/" + entry.getName();
                    long fileSize = Files.size(filePath);
                    String checksum = FileChecksumUtil.calculateChecksum(filePath);

                    PublishingFile publishingFile = PublishingFile.builder()
                            .publishing(publishing)
                            .fileType(fileType)
                            .fileName(fileName)
                            .filePath(relativePath)
                            .fileSize(fileSize)
                            .checksum(checksum)
                            .sortOrder(sortOrder.getAndIncrement())
                            .build();

                    files.add(publishingFileRepository.save(publishingFile));
                } catch (IOException e) {
                    log.warn("파일 메타데이터 생성 실패: {}", filePath, e);
                }
            });

            log.info("ZIP 파일 해제 완료 - 퍼블리싱 ID: {}, 파일 수: {}", publishing.getPublishingId(), files.size());
            return files;

        } catch (BusinessException e) {
            log.error("ZIP 파일 해제 실패: {}", e.getMessage(), e);
            // 실패 시 생성된 디렉토리 정리
            deleteDirectoryRecursively(publishingDir);
            throw e;
        } catch (Exception e) {
            log.error("ZIP 파일 해제 실패: {}", e.getMessage(), e);
            deleteDirectoryRecursively(publishingDir);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "ZIP 파일 처리에 실패했습니다: " + e.getMessage());
        }
    }

    private void deletePublishingFiles(Publishing publishing) {
        List<PublishingFile> files = publishing.getFiles();
        for (PublishingFile file : files) {
            Path filePath = Paths.get(baseReleasePath, file.getFilePath());
            try {
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
            } catch (IOException e) {
                log.warn("파일 삭제 실패: {}", filePath);
            }
        }
        publishing.clearFiles();
        publishingFileRepository.deleteAllByPublishing_PublishingId(publishing.getPublishingId());
    }

    private Path getPublishingDirectory(Publishing publishing) {
        return Paths.get(baseReleasePath, PUBLISHING_DIR, publishing.getPublishingName());
    }

    private void deleteDirectoryRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try {
            Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a)) // 역순 (파일 먼저, 디렉토리 나중)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("삭제 실패: {}", path);
                        }
                    });
        } catch (IOException e) {
            log.warn("디렉토리 삭제 실패: {}", directory);
        }
    }

    private String extractFileType(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "UNKNOWN";
        }
        return fileName.substring(lastDotIndex + 1).toUpperCase();
    }

    private PublishingDto.DetailResponse toDetailResponse(Publishing publishing) {
        List<PublishingFileDto.SimpleResponse> files = publishingDtoMapper.toFileSimpleResponseList(publishing.getFiles());

        long totalFileSize = publishing.getFiles().stream()
                .mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0L)
                .sum();

        // HTML 파일 목록 추출
        List<PublishingDto.HtmlFileInfo> htmlFiles = extractHtmlFiles(publishing);

        return new PublishingDto.DetailResponse(
                publishing.getPublishingId(),
                publishing.getPublishingName(),
                publishing.getDescription(),
                publishing.getPublishingCategory(),
                publishing.getSubCategory(),
                publishing.getCustomer() != null ? publishing.getCustomer().getCustomerId() : null,
                publishing.getCustomer() != null ? publishing.getCustomer().getCustomerName() : null,
                publishing.getSortOrder(),
                publishing.getGlyphText(),
                publishing.getGlyphBackgroundColor(),
                publishing.getFiles().size(),
                totalFileSize,
                files,
                htmlFiles,
                publishing.getCreatedByEmail(),
                publishing.getCreatedByName(),
                publishing.getCreator() == null,
                publishing.getUpdatedByEmail(),
                publishing.getUpdatedByName(),
                publishing.getUpdater() == null,
                publishing.getCreatedAt(),
                publishing.getUpdatedAt()
        );
    }

    private PublishingDto.SimpleResponse toSimpleResponse(Publishing publishing) {
        List<PublishingDto.HtmlFileInfo> htmlFiles = extractHtmlFiles(publishing);

        return new PublishingDto.SimpleResponse(
                publishing.getPublishingId(),
                publishing.getPublishingName(),
                publishing.getDescription(),
                publishing.getPublishingCategory(),
                publishing.getSubCategory(),
                publishing.getCustomer() != null ? publishing.getCustomer().getCustomerName() : null,
                publishing.getSortOrder(),
                publishing.getGlyphText(),
                publishing.getGlyphBackgroundColor(),
                publishing.getFiles() != null ? publishing.getFiles().size() : 0,
                htmlFiles,
                publishing.getCreatedAt()
        );
    }

    /**
     * Publishing에서 루트 디렉토리의 HTML 파일 목록 추출
     */
    private List<PublishingDto.HtmlFileInfo> extractHtmlFiles(Publishing publishing) {
        if (publishing.getFiles() == null || publishing.getFiles().isEmpty()) {
            return List.of();
        }

        // filePath에서 "publishing/{publishingName}/" 접두사를 제거하기 위한 패턴
        String prefix = PUBLISHING_DIR + "/" + publishing.getPublishingName() + "/";

        return publishing.getFiles().stream()
                .filter(f -> "HTML".equalsIgnoreCase(f.getFileType()))
                .filter(f -> {
                    // 루트 디렉토리의 HTML 파일만 필터링
                    String servePath = f.getFilePath();
                    if (servePath.startsWith(prefix)) {
                        servePath = servePath.substring(prefix.length());
                    }
                    // 슬래시가 없으면 루트 디렉토리 파일
                    return !servePath.contains("/");
                })
                .sorted((a, b) -> a.getFileName().compareToIgnoreCase(b.getFileName()))
                .map(f -> {
                    String servePath = f.getFilePath();
                    if (servePath.startsWith(prefix)) {
                        servePath = servePath.substring(prefix.length());
                    }
                    return new PublishingDto.HtmlFileInfo(
                            f.getFileName(),
                            "/api/publishing/" + publishing.getPublishingId() + "/serve/" + servePath
                    );
                })
                .distinct()
                .toList();
    }

    /**
     * 디렉토리 구조를 재귀적으로 생성
     *
     * @param directory 조회할 디렉토리
     * @param basePath  기준 경로
     * @return DirectoryNode (재귀 구조)
     */
    private PublishingDto.DirectoryNode buildDirectoryNode(Path directory, Path basePath, String publishingOutputPath) throws IOException {
        String name = directory.getFileName() != null
                ? directory.getFileName().toString()
                : directory.toString();
        String relativePath = basePath.relativize(directory).toString().replace("\\", "/");

        // 빈 경로인 경우 "." 으로 표시
        if (relativePath.isEmpty()) {
            relativePath = ".";
        }

        List<PublishingDto.FileNode> children = new ArrayList<>();

        try (var stream = Files.list(directory)) {
            stream.sorted().forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        // 하위 디렉토리 재귀 조회
                        PublishingDto.DirectoryNode childDir = buildDirectoryNode(path, basePath, publishingOutputPath);

                        // 빈 디렉토리는 제외 (children이 비어있으면 추가하지 않음)
                        if (!childDir.children().isEmpty()) {
                            children.add(childDir);
                        }
                    } else {
                        // 파일 정보 추가
                        children.add(buildFileInfo(path, basePath, publishingOutputPath));
                    }
                } catch (IOException e) {
                    log.warn("파일/디렉토리 정보 조회 실패: {}", path, e);
                }
            });
        }

        return new PublishingDto.DirectoryNode(name, "directory", relativePath, children);
    }

    /**
     * 파일 정보 생성
     */
    private PublishingDto.FileInfo buildFileInfo(Path filePath, Path basePath, String publishingOutputPath) throws IOException {
        String name = filePath.getFileName().toString();
        long size = Files.size(filePath);
        String relativePath = basePath.relativize(filePath).toString().replace("\\", "/");
        String downloadPath = Paths.get(publishingOutputPath, relativePath).toString().replace("\\", "/");

        return new PublishingDto.FileInfo(name, size, "file", relativePath, downloadPath);
    }
}
