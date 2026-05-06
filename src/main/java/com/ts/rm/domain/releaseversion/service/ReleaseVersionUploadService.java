package com.ts.rm.domain.releaseversion.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.common.service.FileStorageService;
import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.patch.util.ScriptGenerator;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releasefile.enums.FileCategory;
import com.ts.rm.global.engine.EngineNameClassifier;
import com.ts.rm.domain.releasefile.repository.ReleaseFileRepository;
import com.ts.rm.domain.releasefile.util.SubCategoryValidator;
import com.ts.rm.domain.releaseversion.dto.ReleaseVersionDto;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.mapper.ReleaseVersionDtoMapper;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.domain.releaseversion.util.VersionParser;
import com.ts.rm.domain.releaseversion.util.VersionParser.VersionInfo;
import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * ReleaseVersion Upload Service
 *
 * <p>릴리즈 버전의 파일 업로드 및 ZIP 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseVersionUploadService {

    private final ReleaseVersionRepository releaseVersionRepository;
    private final ReleaseFileRepository releaseFileRepository;
    private final ProjectRepository projectRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AccountLookupService accountLookupService;
    private final FileStorageService fileStorageService;
    private final ReleaseVersionFileSystemService fileSystemService;
    private final ReleaseVersionTreeService treeService;
    private final ReleaseVersionDtoMapper mapper;
    private final ScriptGenerator mariaDBScriptGenerator;
    private final ScriptGenerator crateDBScriptGenerator;

    @Value("${app.release.base-path:data/release-manager}")
    private String baseReleasePath;

    @Value("${spring.servlet.multipart.max-file-size:1GB}")
    private String maxFileSizeConfig;

    /**
     * 표준 릴리즈 버전 및 파일 일괄 생성
     *
     * @param request      버전 생성 요청
     * @param mariadbFiles MariaDB SQL 파일들 (선택)
     * @param cratedbFiles CrateDB SQL 파일들 (선택)
     * @return 생성된 버전 상세 정보
     */
    @Transactional
    public ReleaseVersionDto.DetailResponse createStandardVersionWithFiles(
            ReleaseVersionDto.CreateRequest request,
            ReleaseVersion savedVersion,
            List<MultipartFile> mariadbFiles,
            List<MultipartFile> cratedbFiles) {

        log.info("Creating standard release version with files: {}", request.version());

        // 0. 파일 검증 먼저 수행 (DB 작업 전, 빠른 실패)
        if (mariadbFiles != null) {
            mariadbFiles.forEach(this::validateFile);
        }
        if (cratedbFiles != null) {
            cratedbFiles.forEach(this::validateFile);
        }

        // 버전 디렉토리 경로 (롤백용)
        String versionDir = null;

        try {
            Long versionId = savedVersion.getReleaseVersionId();

            // 버전 디렉토리 경로 저장 (롤백 시 사용)
            String projectId = savedVersion.getProject() != null ? savedVersion.getProject().getProjectId() : "infraeye2";
            String[] parts = request.version().split("\\.");
            String majorMinor = parts[0] + "." + parts[1] + ".x";
            versionDir = String.format("%s/versions/%s/standard/%s/%s",
                    baseReleasePath, projectId, majorMinor, request.version());

            // 2. MariaDB 파일 업로드
            if (mariadbFiles != null && !mariadbFiles.isEmpty()) {
                log.info("Uploading {} MariaDB files for version {}", mariadbFiles.size(),
                        request.version());
                uploadFiles(savedVersion, mariadbFiles, "mariadb");
            }

            // 3. CrateDB 파일 업로드
            if (cratedbFiles != null && !cratedbFiles.isEmpty()) {
                log.info("Uploading {} CrateDB files for version {}", cratedbFiles.size(),
                        request.version());
                uploadFiles(savedVersion, cratedbFiles, "cratedb");
            }

            log.info("Successfully created version {} with {} MariaDB files and {} CrateDB files",
                    request.version(),
                    mariadbFiles != null ? mariadbFiles.size() : 0,
                    cratedbFiles != null ? cratedbFiles.size() : 0);

            // DetailResponse 반환을 위한 조회
            return mapper.toDetailResponse(savedVersion);

        } catch (Exception e) {
            // 예외 발생 시 파일시스템 롤백
            log.error("Error creating version with files, rolling back filesystem changes", e);
            if (versionDir != null) {
                String projectId = savedVersion.getProject() != null
                        ? savedVersion.getProject().getProjectId()
                        : "infraeye2";
                fileSystemService.rollbackFileSystem(versionDir, projectId, request.version());
            }
            throw e; // DB 트랜잭션 롤백을 위해 예외 재발생
        }
    }

    /**
     * ZIP 파일로 표준 릴리즈 버전 생성
     *
     * @param projectId       프로젝트 ID
     * @param version         버전 (예: 1.1.3)
     * @param comment         패치 노트 내용
     * @param zipFile         패치 파일이 포함된 ZIP 파일
     * @param createdByEmail       생성자 이메일
     * @param isApproved      승인 여부 (true: 승인됨, false: 미승인, null: 미승인)
     * @return 생성된 버전 응답
     */
    @Transactional
    public ReleaseVersionDto.CreateVersionResponse createStandardVersionWithZip(
            String projectId, String version, String comment,
            MultipartFile zipFile, String createdByEmail, Boolean isApproved) {

        log.info("ZIP 파일로 표준 릴리즈 버전 생성 시작 - projectId: {}, version: {}, createdByEmail: {}, isApproved: {}",
                projectId, version, createdByEmail, isApproved);

        // 0. 프로젝트 조회
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND,
                        "프로젝트를 찾을 수 없습니다: " + projectId));

        // 0-1. 미승인 버전 존재 여부 확인 (미승인 버전이 있으면 새 버전 생성 불가)
        validateNoUnapprovedVersionExists(projectId, "STANDARD");

        // 1. 버전 파싱 및 검증
        VersionInfo versionInfo = VersionParser.parse(version);
        validateNewVersion(projectId, "STANDARD", versionInfo);

        // 2. ZIP 파일 검증
        validateZipFile(zipFile);

        Path tempDir = null;
        Path versionPath = null;

        try {
            // 3. 임시 디렉토리에 ZIP 압축 해제
            tempDir = extractZipToTempDirectory(zipFile);

            // 4. ZIP 구조 검증 (패치본만 허용: database/, web/, engine/)
            validateZipStructure(tempDir);

            // 5. 버전 디렉토리 생성
            versionPath = fileSystemService.createVersionDirectory(versionInfo, projectId);

            // 6. 파일 복사 및 DB 저장
            ReleaseVersion savedVersion = copyFilesAndSaveToDb(project, tempDir, versionPath, versionInfo, createdByEmail, comment, isApproved);

            log.info("ZIP 파일로 표준 릴리즈 버전 생성 완료 - projectId: {}, version: {}, ID: {}, isApproved: {}",
                    projectId, version, savedVersion.getReleaseVersionId(), savedVersion.getIsApproved());

            // 7. 응답 생성
            return new ReleaseVersionDto.CreateVersionResponse(
                    savedVersion.getReleaseVersionId(),
                    projectId,
                    version,
                    versionInfo.getMajorVersion(),
                    versionInfo.getMinorVersion(),
                    versionInfo.getPatchVersion(),
                    0,  // hotfixVersion (표준 버전은 0)
                    version,  // fullVersion (표준 버전은 version과 동일)
                    versionInfo.getMajorMinor(),
                    createdByEmail,
                    comment,
                    savedVersion.getCreatedAt(),
                    getCreatedFilesList(savedVersion)
            );

        } catch (BusinessException e) {
            // 생성된 버전 디렉토리 롤백
            if (versionPath != null) {
                fileSystemService.deleteDirectory(versionPath);
            }
            throw e;
        } catch (Exception e) {
            // 생성된 버전 디렉토리 롤백
            if (versionPath != null) {
                fileSystemService.deleteDirectory(versionPath);
            }
            log.error("ZIP 파일로 버전 생성 실패: {}", version, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "버전 생성 중 오류가 발생했습니다: " + e.getMessage());
        } finally {
            // 임시 디렉토리 정리
            if (tempDir != null) {
                fileSystemService.deleteDirectory(tempDir);
            }
        }
    }

    /**
     * ZIP 파일로 커스텀 릴리즈 버전 생성
     *
     * @param request   커스텀 버전 생성 요청
     * @param zipFile   패치 파일이 포함된 ZIP 파일
     * @param createdByEmail 생성자 이메일
     * @return 생성된 커스텀 버전 응답
     */
    @Transactional
    public ReleaseVersionDto.CreateCustomVersionResponse createCustomVersionWithZip(
            ReleaseVersionDto.CreateCustomVersionRequest request, MultipartFile zipFile, String createdByEmail) {

        log.info("ZIP 파일로 커스텀 릴리즈 버전 생성 시작 - projectId: {}, customerId: {}, customBaseVersionId: {}, customVersion: {}, createdByEmail: {}, isApproved: {}",
                request.projectId(), request.customerId(), request.customBaseVersionId(), request.customVersion(),
                createdByEmail, request.isApproved());

        // 0. 프로젝트 조회
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND,
                        "프로젝트를 찾을 수 없습니다: " + request.projectId()));

        // 1. 고객사 조회
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND,
                        "고객사를 찾을 수 없습니다: " + request.customerId()));

        // 2. 해당 고객사의 기존 커스텀 버전 존재 여부 확인 (핫픽스 제외)
        List<ReleaseVersion> existingCustomVersions = releaseVersionRepository
                .findAllByCustomer_CustomerIdOrderByCreatedAtDesc(request.customerId())
                .stream()
                .filter(v -> !v.isHotfix())  // 핫픽스 제외, 기본 커스텀 버전만
                .toList();
        boolean isFirstCustomVersion = existingCustomVersions.isEmpty();

        // 2-1. 미승인 커스텀 버전 존재 여부 확인 (미승인 버전이 있으면 새 버전 생성 불가)
        validateNoUnapprovedCustomVersionExists(request.customerId());

        // 3. 기준 표준 버전 조회 및 검증
        ReleaseVersion customBaseVersion = null;
        if (request.customBaseVersionId() != null) {
            customBaseVersion = releaseVersionRepository.findById(request.customBaseVersionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                            "기준 버전을 찾을 수 없습니다: " + request.customBaseVersionId()));

            if (!"STANDARD".equals(customBaseVersion.getReleaseType())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "커스텀 버전의 기준 버전은 표준(STANDARD) 버전이어야 합니다.");
            }
        } else if (isFirstCustomVersion) {
            // 최초 커스텀 버전 생성 시 customBaseVersionId 필수
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "해당 고객사의 최초 커스텀 버전 생성 시 기준 표준 버전 ID(customBaseVersionId)는 필수입니다.");
        } else {
            // 최초가 아닌 경우: 기존 커스텀 버전의 customBaseVersion을 상속
            customBaseVersion = existingCustomVersions.get(0).getCustomBaseVersion();
            if (customBaseVersion == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "기존 커스텀 버전의 기준 표준 버전 정보를 찾을 수 없습니다.");
            }
            log.info("기존 커스텀 버전에서 기준 표준 버전 상속 - customBaseVersionId: {}, version: {}",
                    customBaseVersion.getReleaseVersionId(), customBaseVersion.getVersion());
        }

        // 4. 커스텀 버전 파싱
        VersionInfo customVersionInfo = VersionParser.parse(request.customVersion());
        String customVersionStr = request.customVersion();
        String customMajorMinor = customVersionInfo.getMajorMinor();
        int customMajorVersion = customVersionInfo.getMajorVersion();
        int customMinorVersion = customVersionInfo.getMinorVersion();
        int customPatchVersion = customVersionInfo.getPatchVersion();

        // 시멘틱 버저닝 형식의 전체 버전 문자열 생성
        // 형식: {베이스버전}-{고객사코드}.{커스텀버전}
        // 예: 1.1.0-companyA.1.0.0
        String fullVersion = customBaseVersion.getVersion() + "-" + customer.getCustomerCode() + "." + customVersionStr;

        // 5. 커스텀 버전 중복 검증 (같은 고객사 내에서)
        validateCustomVersionUnique(request.customerId(), customMajorVersion, customMinorVersion, customPatchVersion);

        // 6. ZIP 파일 검증
        validateZipFile(zipFile);

        Path tempDir = null;
        Path versionPath = null;

        try {
            // 7. 임시 디렉토리에 ZIP 압축 해제
            tempDir = extractZipToTempDirectory(zipFile);

            // 8. ZIP 구조 검증 (패치본만 허용: database/, web/, engine/)
            validateZipStructure(tempDir);

            // 9. 커스텀 버전 디렉토리 생성 (전체 버전 형식 사용)
            versionPath = fileSystemService.createCustomVersionDirectory(
                    request.projectId(), customer.getCustomerCode(), customMajorMinor, fullVersion);

            // 10. 파일 복사 및 DB 저장
            ReleaseVersion savedVersion = copyFilesAndSaveToDbForCustomVersion(
                    project, customer, customBaseVersion, tempDir, versionPath,
                    customMajorVersion, customMinorVersion, customPatchVersion,
                    request.comment(), createdByEmail, request.isApproved());

            log.info("ZIP 파일로 커스텀 릴리즈 버전 생성 완료 - projectId: {}, customerId: {}, version: {}, ID: {}, isApproved: {}",
                    request.projectId(), request.customerId(), fullVersion, savedVersion.getReleaseVersionId(), savedVersion.getIsApproved());

            // 11. 응답 생성
            return new ReleaseVersionDto.CreateCustomVersionResponse(
                    savedVersion.getReleaseVersionId(),
                    request.projectId(),
                    customer.getCustomerCode(),
                    customer.getCustomerName(),
                    customBaseVersion != null ? customBaseVersion.getReleaseVersionId() : null,
                    customBaseVersion != null ? customBaseVersion.getVersion() : null,
                    customMajorVersion,
                    customMinorVersion,
                    customPatchVersion,
                    fullVersion,  // 전체 버전 형식 반환
                    customMajorMinor,
                    createdByEmail,
                    request.comment(),
                    savedVersion.getCreatedAt(),
                    getCreatedFilesList(savedVersion)
            );

        } catch (BusinessException e) {
            // 생성된 버전 디렉토리 롤백
            if (versionPath != null) {
                fileSystemService.deleteDirectory(versionPath);
            }
            throw e;
        } catch (Exception e) {
            // 생성된 버전 디렉토리 롤백
            if (versionPath != null) {
                fileSystemService.deleteDirectory(versionPath);
            }
            log.error("ZIP 파일로 커스텀 버전 생성 실패: {}", fullVersion, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "커스텀 버전 생성 중 오류가 발생했습니다: " + e.getMessage());
        } finally {
            // 임시 디렉토리 정리
            if (tempDir != null) {
                fileSystemService.deleteDirectory(tempDir);
            }
        }
    }

    /**
     * 커스텀 버전 중복 검증 (같은 고객사 내에서 동일 버전이 있는지 확인)
     */
    private void validateCustomVersionUnique(Long customerId, Integer major, Integer minor, Integer patch) {
        boolean exists = releaseVersionRepository.existsByCustomer_CustomerIdAndCustomMajorVersionAndCustomMinorVersionAndCustomPatchVersion(
                customerId, major, minor, patch);
        if (exists) {
            throw new BusinessException(ErrorCode.RELEASE_VERSION_CONFLICT,
                    String.format("이미 존재하는 커스텀 버전입니다: %d.%d.%d", major, minor, patch));
        }
    }

    /**
     * 커스텀 버전용 파일 복사 및 DB 저장
     *
     * @return 저장된 ReleaseVersion 엔티티
     */
    private ReleaseVersion copyFilesAndSaveToDbForCustomVersion(
            Project project, Customer customer, ReleaseVersion customBaseVersion,
            Path tempDir, Path versionPath,
            int customMajorVersion, int customMinorVersion, int customPatchVersion,
            String comment, String createdByEmail,
            Boolean isApproved) throws IOException {

        String customVersionStr = customMajorVersion + "." + customMinorVersion + "." + customPatchVersion;

        // 시멘틱 버저닝 형식의 전체 버전 문자열 생성
        // 형식: {베이스버전}-{고객사코드}.{커스텀버전}
        // 예: 1.1.0-companyA.1.0.0
        String fullVersion = customBaseVersion.getVersion() + "-" + customer.getCustomerCode() + "." + customVersionStr;

        // 생성자(Account) 조회 - 이메일로 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        // isApproved가 null이면 false로 처리
        boolean approved = Boolean.TRUE.equals(isApproved);

        // ReleaseVersion 생성 및 저장 (커스텀 버전 전용 필드 설정)
        // version 필드: 시멘틱 버저닝 형식의 전체 버전 문자열
        // majorVersion/minorVersion/patchVersion: 베이스 버전 숫자 (버전 정렬 및 비교용)
        // customMajorVersion/customMinorVersion/customPatchVersion: 커스텀 버전 숫자
        ReleaseVersion.ReleaseVersionBuilder builder = ReleaseVersion.builder()
                .project(project)
                .releaseType("CUSTOM")
                .customer(customer)
                .customBaseVersion(customBaseVersion)
                .version(fullVersion)  // 시멘틱 버저닝 형식: 1.1.0-companyA.1.0.0
                .majorVersion(customBaseVersion.getMajorVersion())    // 베이스 버전 기준
                .minorVersion(customBaseVersion.getMinorVersion())    // 베이스 버전 기준
                .patchVersion(customBaseVersion.getPatchVersion())    // 베이스 버전 기준
                .customMajorVersion(customMajorVersion)
                .customMinorVersion(customMinorVersion)
                .customPatchVersion(customPatchVersion)
                .creator(creator)
                .createdByEmail(createdByEmail)
                .comment(comment)
                .isApproved(approved);

        // 승인된 경우 승인자와 승인일시 설정
        if (approved) {
            builder.approver(creator)
                   .approvedByEmail(createdByEmail)
                   .approvedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        }

        final ReleaseVersion savedVersion = releaseVersionRepository.save(builder.build());

        log.info("커스텀 ReleaseVersion 저장 완료 - ID: {}, version: {}, customVersion: {}, customBaseVersion: {}",
                savedVersion.getReleaseVersionId(), fullVersion, customVersionStr,
                customBaseVersion.getVersion());

        // 클로저 테이블에 계층 구조 데이터 추가
        treeService.createHierarchyForNewVersion(savedVersion, "CUSTOM");

        // 모든 카테고리 폴더 순회 및 파일 복사
        Files.list(tempDir)
                .filter(Files::isDirectory)
                .forEach(categoryDir -> {
                    String categoryName = categoryDir.getFileName().toString().toUpperCase();

                    try {
                        // FileCategory 검증 및 변환
                        FileCategory fileCategory = FileCategory.fromCode(categoryName);

                        // 타겟 카테고리 디렉토리 생성
                        Path targetCategoryDir = versionPath.resolve(categoryName.toLowerCase());
                        Files.createDirectories(targetCategoryDir);

                        log.info("카테고리 폴더 처리 시작: {} -> {}", categoryName, fileCategory.getDescription());

                        // 카테고리별 파일 복사 (재귀적)
                        processCategoryFiles(categoryDir, targetCategoryDir, savedVersion, fileCategory);

                    } catch (IllegalArgumentException e) {
                        log.warn("알 수 없는 카테고리 폴더 무시: {}", categoryName);
                    } catch (IOException e) {
                        log.error("카테고리 파일 복사 실패: {}", categoryName, e);
                        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                                "파일 복사 중 오류 발생: " + e.getMessage());
                    }
                });

        return savedVersion;
    }

    /**
     * 파일 업로드 처리 (내부용)
     *
     * @param releaseVersion 릴리즈 버전
     * @param files          업로드할 파일 목록
     * @param subCategory    하위 카테고리 (mariadb/cratedb)
     */
    private void uploadFiles(ReleaseVersion releaseVersion, List<MultipartFile> files, String subCategory) {
        Long versionId = releaseVersion.getReleaseVersionId();

        // 기존 파일 중 최대 실행 순서 조회 (전체 기준)
        int maxOrder = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(versionId)
                .stream()
                .mapToInt(ReleaseFile::getExecutionOrder)
                .max()
                .orElse(0);

        int executionOrder = maxOrder + 1;

        for (MultipartFile file : files) {
            try {
                // 파일 검증
                validateFile(file);

                byte[] content = file.getBytes();
                String checksum = calculateChecksum(content);

                // 파일 경로 생성: versions/{projectId}/{type}/{majorMinor}/{version}/{subCategory}/{fileName}
                String projectId = releaseVersion.getProject() != null ? releaseVersion.getProject().getProjectId() : "infraeye2";
                String relativePath = String.format("versions/%s/%s/%s/%s/%s/%s",
                        projectId,
                        releaseVersion.getReleaseType().toLowerCase(),
                        releaseVersion.getMajorMinor(),
                        releaseVersion.getVersion(),
                        subCategory.toLowerCase(),
                        file.getOriginalFilename());

                // 실제 파일 저장
                fileStorageService.saveFile(file, relativePath);

                // DB에 메타데이터 저장
                ReleaseFile releaseFile = ReleaseFile.builder()
                        .releaseVersion(releaseVersion)
                        .fileCategory(com.ts.rm.domain.releasefile.enums.FileCategory.DATABASE)
                        .subCategory(subCategory)
                        .fileName(file.getOriginalFilename())
                        .filePath(relativePath)
                        .fileSize(file.getSize())
                        .checksum(checksum)
                        .executionOrder(executionOrder++)
                        .description("일괄 생성으로 업로드된 파일")
                        .build();

                releaseFileRepository.save(releaseFile);

                log.info("Release file uploaded: {} -> {}", file.getOriginalFilename(), relativePath);

            } catch (IOException e) {
                log.error("Failed to upload release file: {}", file.getOriginalFilename(), e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "파일 업로드 실패: " + e.getMessage());
            }
        }
    }

    /**
     * 파일 검증
     */
    public void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "빈 파일입니다");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".sql")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "SQL 파일만 업로드 가능합니다: " + fileName);
        }

        // 10MB 제한
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "파일 크기는 10MB를 초과할 수 없습니다");
        }
    }

    /**
     * ZIP 파일 검증
     */
    public void validateZipFile(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "ZIP 파일이 비어있습니다");
        }

        // 파일 확장자 확인
        String originalFilename = zipFile.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "ZIP 파일만 업로드 가능합니다");
        }

        // 파일 크기 제한 (1GB)
        long maxSize = 1L * 1024 * 1024 * 1024;
        if (zipFile.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("파일 크기가 너무 큽니다 (최대 1GB): %d bytes", zipFile.getSize()));
        }
    }

    /**
     * ZIP 파일을 임시 디렉토리에 압축 해제
     *
     * <p>한글 파일명 인코딩 문제 해결을 위해 UTF-8 → MS949 순서로 시도합니다.
     */
    public Path extractZipToTempDirectory(MultipartFile zipFile) throws IOException {
        // UTF-8로 먼저 시도
        try {
            return extractZipWithCharset(zipFile, java.nio.charset.StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            // UTF-8 실패 시 MS949(한글 Windows 기본 인코딩)로 재시도
            if (e.getMessage() != null && e.getMessage().contains("malformed input")) {
                log.info("UTF-8 인코딩 실패, MS949로 재시도합니다.");
                return extractZipWithCharset(zipFile, java.nio.charset.Charset.forName("MS949"));
            }
            throw e;
        } catch (IllegalArgumentException e) {
            // ZipInputStream.getNextEntry()가 인코딩 오류 시 IllegalArgumentException을 던짐
            if (e.getMessage() != null && e.getMessage().contains("malformed input")) {
                log.info("UTF-8 인코딩 실패 (IllegalArgumentException), MS949로 재시도합니다.");
                return extractZipWithCharset(zipFile, java.nio.charset.Charset.forName("MS949"));
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "ZIP 파일 압축 해제 실패: " + e.getMessage());
        }
    }

    /**
     * 지정된 문자셋으로 ZIP 파일을 임시 디렉토리에 압축 해제
     *
     * @param zipFile ZIP 파일
     * @param charset 파일명 인코딩
     * @return 압축 해제된 임시 디렉토리 경로
     */
    private Path extractZipWithCharset(MultipartFile zipFile, java.nio.charset.Charset charset) throws IOException {
        Path tempDir = Files.createTempDirectory("release_upload_");

        try (java.util.zip.ZipInputStream zis =
                     new java.util.zip.ZipInputStream(zipFile.getInputStream(), charset)) {

            java.util.zip.ZipEntry entry;
            long totalSize = 0;
            long maxTotalSize = parseFileSize(maxFileSizeConfig); // application.yml 설정값 사용

            while ((entry = zis.getNextEntry()) != null) {
                // 경로 탐색 공격 방지
                Path targetPath = tempDir.resolve(entry.getName()).normalize();
                if (!targetPath.startsWith(tempDir)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                            "유효하지 않은 ZIP 파일 경로입니다: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    // 파일 크기 누적 확인 (ZIP 폭탄 방지)
                    totalSize += entry.getSize();
                    if (totalSize > maxTotalSize) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                                "압축 해제 후 파일 크기가 너무 큽니다 (최대 " + maxFileSizeConfig + ")");
                    }

                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            fileSystemService.deleteDirectory(tempDir);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "ZIP 파일 압축 해제 실패: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // ZipInputStream.getNextEntry()가 인코딩 오류 시 IllegalArgumentException을 던짐
            fileSystemService.deleteDirectory(tempDir);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "ZIP 파일 압축 해제 실패 (인코딩 오류): " + e.getMessage());
        }

        return tempDir;
    }

    /**
     * ZIP 구조 검증 (패치본 카테고리 폴더 확인)
     *
     * <p>허용 폴더: database/, web/, engine/
     */
    public void validateZipStructure(Path tempDir) throws IOException {
        // 패치본 허용 카테고리: DATABASE, WEB, ENGINE
        List<FileCategory> allowedCategories = List.of(FileCategory.DATABASE, FileCategory.WEB, FileCategory.ENGINE);

        // 최상위 디렉토리에서 유효한 카테고리 폴더 확인
        List<String> foundCategories = Files.list(tempDir)
                .filter(Files::isDirectory)
                .map(path -> path.getFileName().toString().toUpperCase())
                .filter(dirName -> {
                    try {
                        FileCategory.fromCode(dirName);
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .toList();

        if (foundCategories.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "ZIP 파일에 유효한 카테고리 폴더가 없습니다. 다음 폴더 중 하나 이상 필요: database/, web/, engine/");
        }

        // 허용되지 않은 카테고리 폴더가 있는지 확인
        for (String foundCategory : foundCategories) {
            FileCategory fileCategory = FileCategory.fromCode(foundCategory);
            if (!allowedCategories.contains(fileCategory)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        String.format("'%s/' 폴더는 허용되지 않습니다. 허용된 폴더: database/, web/, engine/",
                                foundCategory.toLowerCase()));
            }
        }
    }

    /**
     * 파일 복사 및 DB 저장
     *
     * @return 저장된 ReleaseVersion 엔티티
     */
    public ReleaseVersion copyFilesAndSaveToDb(Project project, Path tempDir, Path versionPath,
                                                VersionInfo versionInfo,
                                                String createdByEmail, String comment, Boolean isApproved) throws IOException {
        String version = versionInfo.getMajorVersion() + "." + versionInfo.getMinorVersion() + "." + versionInfo.getPatchVersion();

        // isApproved가 null이면 false로 처리
        boolean approved = Boolean.TRUE.equals(isApproved);

        // 생성자(Account) 조회 - 이메일로 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        // ReleaseVersion 생성 및 저장
        ReleaseVersion.ReleaseVersionBuilder builder = ReleaseVersion.builder()
                .project(project)
                .releaseType("STANDARD")
                .version(version)
                .majorVersion(versionInfo.getMajorVersion())
                .minorVersion(versionInfo.getMinorVersion())
                .patchVersion(versionInfo.getPatchVersion())
                .creator(creator)
                .createdByEmail(createdByEmail)
                .comment(comment)
                .isApproved(approved);

        // 승인된 경우 승인자와 승인일시 설정
        if (approved) {
            builder.approver(creator)
                   .approvedByEmail(createdByEmail)
                   .approvedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        }

        final ReleaseVersion savedVersion = releaseVersionRepository.save(builder.build());

        log.info("ReleaseVersion 저장 완료 - ID: {}, version: {}, isApproved: {}",
                savedVersion.getReleaseVersionId(), version, savedVersion.getIsApproved());

        // 클로저 테이블에 계층 구조 데이터 추가
        treeService.createHierarchyForNewVersion(savedVersion, "STANDARD");

        // 모든 카테고리 폴더 순회 및 파일 복사
        Files.list(tempDir)
                .filter(Files::isDirectory)
                .forEach(categoryDir -> {
                    String categoryName = categoryDir.getFileName().toString().toUpperCase();

                    try {
                        // FileCategory 검증 및 변환
                        FileCategory fileCategory = FileCategory.fromCode(categoryName);

                        // 타겟 카테고리 디렉토리 생성
                        Path targetCategoryDir = versionPath.resolve(categoryName.toLowerCase());
                        Files.createDirectories(targetCategoryDir);

                        log.info("카테고리 폴더 처리 시작: {} -> {}", categoryName, fileCategory.getDescription());

                        // 카테고리별 파일 복사 (재귀적)
                        processCategoryFiles(categoryDir, targetCategoryDir, savedVersion, fileCategory);

                    } catch (IllegalArgumentException e) {
                        log.warn("알 수 없는 카테고리 폴더 무시: {}", categoryName);
                    } catch (IOException e) {
                        log.error("카테고리 파일 복사 실패: {}", categoryName, e);
                        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                                "파일 복사 중 오류 발생: " + e.getMessage());
                    }
                });

        return savedVersion;
    }

    /**
     * 카테고리별 파일 처리 (하위 폴더 재귀 처리)
     *
     * @param categorySourceDir 카테고리 소스 디렉토리 (예: tempDir/database)
     * @param categoryTargetDir 카테고리 타겟 디렉토리 (예: versionPath/database)
     * @param releaseVersion    릴리즈 버전 엔티티
     * @param fileCategory      파일 카테고리 (DATABASE, WEB, ENGINE)
     */
    public void processCategoryFiles(Path categorySourceDir, Path categoryTargetDir,
                                      ReleaseVersion releaseVersion, FileCategory fileCategory) throws IOException {

        // 하위 폴더 순회 (예: database/MARIADB, database/CRATEDB, web/build 등)
        Files.list(categorySourceDir)
                .filter(Files::isDirectory)
                .forEach(subDir -> {
                    String subCategory = subDir.getFileName().toString();

                    // DATABASE / ENGINE: 대소문자 무관 입력을 받아 CODE 테이블 등록값(대문자)으로 자동 정규화.
                    // CODE 테이블에 없는 사용자 정의 값은 입력 그대로 유지 (ReleaseFileUploadService.determineSubCategory 와 동일 정책).
                    if (fileCategory == FileCategory.DATABASE || fileCategory == FileCategory.ENGINE) {
                        String upperSubCategory = subCategory.toUpperCase();
                        if (SubCategoryValidator.isValid(fileCategory, upperSubCategory)) {
                            subCategory = upperSubCategory;
                        } else {
                            log.debug("CODE 테이블에 없는 사용자 정의 하위 카테고리: {}/{}", fileCategory.getCode(), subCategory);
                        }
                    } else {
                        // WEB은 소문자로 변환
                        subCategory = subCategory.toLowerCase();
                    }

                    Path targetSubDir = categoryTargetDir.resolve(subCategory);

                    try {
                        Files.createDirectories(targetSubDir);
                        log.debug("하위 폴더 처리: {}/{}", fileCategory.getCode(), subCategory);

                        // 하위 폴더의 파일 복사
                        copyFilesRecursively(subDir, targetSubDir, releaseVersion, fileCategory, subCategory);

                    } catch (IOException e) {
                        log.error("하위 폴더 파일 복사 실패: {}/{}", fileCategory.getCode(), subCategory, e);
                        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                                "파일 복사 실패: " + e.getMessage());
                    }
                });

        // 카테고리 최상위에 직접 있는 파일도 처리
        Files.list(categorySourceDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        Path targetFile = categoryTargetDir.resolve(file.getFileName());
                        Files.copy(file, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        // ENGINE 카테고리 직속 파일: 엔진/공유 자산 구분하여 sub_category 결정
                        //   - 엔진 (EngineNameClassifier.isEngineFile=true): 대문자 정규화 (NC_CONF)
                        //   - 공유 자산 (확장자 있음 등): 원본 파일명 그대로 (nc_conf.conf)
                        // 다른 카테고리는 sub_category = null 유지
                        String directSub = null;
                        if (fileCategory == FileCategory.ENGINE) {
                            String rawName = file.getFileName().toString();
                            if (EngineNameClassifier.isEngineFile(rawName)) {
                                // 엔진 바이너리: 대문자 정규화
                                directSub = rawName.toUpperCase();
                            } else {
                                // 공유 자산: 원본 파일명 그대로 저장 (누적 skip 분기와 정합)
                                directSub = rawName;
                            }
                        }

                        // ReleaseFile DB 저장
                        saveReleaseFile(file, targetFile, releaseVersion, fileCategory, directSub,
                                categorySourceDir, 1);

                    } catch (IOException e) {
                        log.error("파일 복사 실패: {}", file.getFileName(), e);
                        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                                "파일 복사 실패: " + e.getMessage());
                    }
                });
    }

    /**
     * 재귀적 파일 복사 및 ReleaseFile 저장
     *
     * @param sourceDir      소스 디렉토리
     * @param targetDir      타겟 디렉토리
     * @param releaseVersion 릴리즈 버전
     * @param fileCategory   파일 카테고리
     * @param subCategory    하위 카테고리 (예: mariadb, cratedb, build)
     */
    public void copyFilesRecursively(Path sourceDir, Path targetDir,
                                      ReleaseVersion releaseVersion, FileCategory fileCategory,
                                      String subCategory) throws IOException {

        // 모든 파일을 재귀적으로 탐색하여 복사 (확장자 제한 없음)
        List<Path> files = Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .sorted()
                .toList();

        int executionOrder = 1;
        for (Path file : files) {
            // 상대 경로 계산 (하위 폴더 구조 유지)
            Path relativePath = sourceDir.relativize(file);
            Path targetFile = targetDir.resolve(relativePath);

            // 타겟 디렉토리 생성
            Files.createDirectories(targetFile.getParent());

            // 파일 복사
            Files.copy(file, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // ReleaseFile DB 저장
            saveReleaseFile(file, targetFile, releaseVersion, fileCategory, subCategory,
                    sourceDir, executionOrder++);
        }
    }

    /**
     * ReleaseFile 엔티티 생성 및 저장
     */
    private void saveReleaseFile(Path sourceFile, Path targetFile, ReleaseVersion releaseVersion,
                                  FileCategory fileCategory, String subCategory,
                                  Path sourceBaseDir, int executionOrder) throws IOException {

        // 파일 크기 및 체크섬 계산
        byte[] fileContent = Files.readAllBytes(sourceFile);
        long fileSize = fileContent.length;
        String checksum = calculateChecksum(fileContent);

        // 물리 경로 계산 (baseReleasePath 기준)
        Path basePath = Paths.get(baseReleasePath);
        String physicalPath = basePath.relativize(targetFile).toString().replace("\\", "/");

        // ZIP 내부 상대 경로 계산
        String zipInternalPath = "/" + fileCategory.getCode().toLowerCase() + "/";
        if (subCategory != null) {
            zipInternalPath += subCategory + "/";
        }
        zipInternalPath += sourceBaseDir.relativize(sourceFile).toString().replace("\\", "/");

        // 파일 타입 결정
        String fileType = determineFileType(sourceFile.getFileName().toString());

        // ReleaseFile 저장
        ReleaseFile releaseFile = ReleaseFile.builder()
                .releaseVersion(releaseVersion)
                .fileType(fileType)
                .fileCategory(fileCategory)
                .subCategory(subCategory)
                .fileName(sourceFile.getFileName().toString())
                .filePath(physicalPath)
                .fileSize(fileSize)
                .checksum(checksum)
                .executionOrder(executionOrder)
                .description("ZIP 파일 업로드로 생성된 " + fileCategory.getDescription() + " 파일")
                .build();

        releaseFileRepository.save(releaseFile);

        log.debug("파일 복사 및 저장 완료: {} -> {} (category: {}, size: {}, checksum: {})",
                sourceFile.getFileName(), zipInternalPath, fileCategory.getCode(), fileSize, checksum);
    }

    /**
     * 체크섬 계산 (SHA-256)
     *
     * @param content 파일 바이트 배열
     * @return 64자리 SHA-256 해시값
     */
    public String calculateChecksum(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(content);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 파일 크기 문자열을 바이트 단위로 변환
     *
     * @param sizeStr 크기 문자열 (예: "1GB", "500MB", "1024KB")
     * @return 바이트 단위 크기
     */
    public long parseFileSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            return 1024L * 1024 * 1024; // 기본값 1GB
        }

        sizeStr = sizeStr.trim().toUpperCase();

        long multiplier = 1;
        if (sizeStr.endsWith("GB")) {
            multiplier = 1024L * 1024 * 1024;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        } else if (sizeStr.endsWith("MB")) {
            multiplier = 1024L * 1024;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        } else if (sizeStr.endsWith("KB")) {
            multiplier = 1024L;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        } else if (sizeStr.endsWith("B")) {
            sizeStr = sizeStr.substring(0, sizeStr.length() - 1);
        }

        try {
            return Long.parseLong(sizeStr.trim()) * multiplier;
        } catch (NumberFormatException e) {
            log.warn("파일 크기 파싱 실패: {}, 기본값 1GB 사용", sizeStr);
            return 1024L * 1024 * 1024;
        }
    }

    /**
     * 새 버전 검증 (중복 확인)
     */
    public void validateNewVersion(String projectId, String releaseType, VersionInfo versionInfo) {
        String version = versionInfo.getMajorVersion() + "." + versionInfo.getMinorVersion() + "." + versionInfo.getPatchVersion();

        // 중복 버전 확인 (프로젝트 내에서)
        boolean exists = releaseVersionRepository.existsByProject_ProjectIdAndVersion(projectId, version);
        if (exists) {
            throw new BusinessException(ErrorCode.RELEASE_VERSION_CONFLICT,
                    "이미 존재하는 버전입니다: " + version);
        }
    }

    /**
     * 미승인 표준 버전 존재 여부 검증
     *
     * <p>해당 프로젝트의 표준(STANDARD) 버전 중 미승인 버전이 존재하면 새 버전 생성 불가
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD)
     * @throws BusinessException 미승인 버전이 존재하는 경우
     */
    private void validateNoUnapprovedVersionExists(String projectId, String releaseType) {
        boolean hasUnapproved = releaseVersionRepository.existsByProject_ProjectIdAndReleaseTypeAndIsApproved(
                projectId, releaseType, false);

        if (hasUnapproved) {
            // 미승인 버전 목록 조회 (에러 메시지용)
            List<ReleaseVersion> unapprovedVersions = releaseVersionRepository
                    .findAllByProject_ProjectIdAndReleaseTypeAndIsApproved(projectId, releaseType, false);

            String unapprovedVersionList = unapprovedVersions.stream()
                    .map(ReleaseVersion::getVersion)
                    .reduce((v1, v2) -> v1 + ", " + v2)
                    .orElse("");

            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("미승인 버전이 존재하여 새 버전을 생성할 수 없습니다. (미승인 버전: %s)",
                            unapprovedVersionList));
        }
    }

    /**
     * 미승인 커스텀 버전 존재 여부 검증
     *
     * <p>해당 고객사의 커스텀 버전 중 미승인 버전이 존재하면 새 버전 생성 불가
     *
     * @param customerId 고객사 ID
     * @throws BusinessException 미승인 버전이 존재하는 경우
     */
    private void validateNoUnapprovedCustomVersionExists(Long customerId) {
        boolean hasUnapproved = releaseVersionRepository.existsByCustomer_CustomerIdAndIsApproved(
                customerId, false);

        if (hasUnapproved) {
            // 미승인 버전 목록 조회 (에러 메시지용)
            List<ReleaseVersion> unapprovedVersions = releaseVersionRepository
                    .findAllByCustomer_CustomerIdAndIsApproved(customerId, false);

            String unapprovedVersionList = unapprovedVersions.stream()
                    .map(ReleaseVersion::getVersion)
                    .reduce((v1, v2) -> v1 + ", " + v2)
                    .orElse("");

            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("미승인 커스텀 버전이 존재하여 새 버전을 생성할 수 없습니다. (미승인 버전: %s)",
                            unapprovedVersionList));
        }
    }

    /**
     * 생성된 파일 목록 조회
     *
     * @param releaseVersion 릴리즈 버전 엔티티
     * @return 파일 목록 (상대 경로)
     */
    public List<String> getCreatedFilesList(ReleaseVersion releaseVersion) {
        return releaseFileRepository.findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(
                        releaseVersion.getReleaseVersionId())
                .stream()
                .map(file -> {
                    // DATABASE와 ENGINE은 대문자 유지, 나머지는 소문자 (DB에 이미 대문자로 저장되어 있음)
                    String category = file.getSubCategory() != null ? file.getSubCategory() : "unknown";
                    return category + "/" + file.getFileName();
                })
                .toList();
    }

    /**
     * ZIP 파일로 핫픽스 버전 생성
     *
     * @param hotfixBaseVersionId 핫픽스 원본 버전 ID
     * @param comment             패치 노트 내용
     * @param zipFile             패치 파일이 포함된 ZIP 파일
     * @param createdByEmail      생성자 이메일
     * @param assigneeId          담당자 ID (선택, 패치 스크립트의 기본 담당자로 사용)
     * @param isApproved          승인 여부 (true: 생성과 동시에 승인 처리)
     * @return 생성된 핫픽스 응답
     */
    @Transactional
    public ReleaseVersionDto.CreateHotfixResponse createHotfixWithZip(
            Long hotfixBaseVersionId, String comment, MultipartFile zipFile, String createdByEmail,
            Long assigneeId, boolean isApproved) {

        log.info("ZIP 파일로 핫픽스 버전 생성 시작 - hotfixBaseVersionId: {}, createdByEmail: {}", hotfixBaseVersionId,
                createdByEmail);

        // 1. 원본 버전 조회
        ReleaseVersion baseVersion = releaseVersionRepository.findById(hotfixBaseVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_VERSION_NOT_FOUND,
                        "원본 버전을 찾을 수 없습니다: " + hotfixBaseVersionId));

        // 2. 원본 버전이 핫픽스인지 확인 (핫픽스의 핫픽스는 불가)
        if (baseVersion.isHotfix()) {
            throw new BusinessException(ErrorCode.INVALID_HOTFIX_PARENT,
                    "핫픽스 버전에는 추가 핫픽스를 생성할 수 없습니다. 원본 버전에 핫픽스를 생성하세요.");
        }

        // 3. ZIP 파일 검증
        validateZipFile(zipFile);

        Path tempDir = null;
        Path hotfixPath = null;

        try {
            // 4. 임시 디렉토리에 ZIP 압축 해제
            tempDir = extractZipToTempDirectory(zipFile);

            // 5. ZIP 구조 검증 (패치본만 허용: database/, web/, engine/)
            validateZipStructure(tempDir);

            // 6. 다음 핫픽스 버전 번호 결정
            Integer maxHotfixVersion = releaseVersionRepository.findMaxHotfixVersionByHotfixBaseVersionId(hotfixBaseVersionId);
            int nextHotfixVersion = maxHotfixVersion + 1;

            // 7. 핫픽스 버전 엔티티 생성 및 저장
            ReleaseVersion hotfixVersion = copyFilesAndSaveToDbForHotfix(
                    baseVersion, tempDir, nextHotfixVersion, comment,
                    createdByEmail, isApproved);

            // 8. 핫픽스용 디렉토리 구조 생성
            hotfixPath = createHotfixDirectory(hotfixVersion, baseVersion);

            // 9. 파일 복사 처리
            copyHotfixFiles(tempDir, hotfixPath, hotfixVersion);

            // 10. 핫픽스 패치 스크립트 생성
            generateHotfixPatchScripts(hotfixVersion, hotfixPath, assigneeId);

            log.info("ZIP 파일로 핫픽스 버전 생성 완료 - hotfixBaseVersionId: {}, hotfixVersion: {}, ID: {}",
                    hotfixBaseVersionId, nextHotfixVersion, hotfixVersion.getReleaseVersionId());

            // 11. 응답 생성
            String projectId = baseVersion.getProject() != null
                    ? baseVersion.getProject().getProjectId()
                    : "infraeye2";

            return new ReleaseVersionDto.CreateHotfixResponse(
                    hotfixVersion.getReleaseVersionId(),
                    projectId,
                    hotfixBaseVersionId,
                    baseVersion.getVersion(),
                    hotfixVersion.getMajorVersion(),
                    hotfixVersion.getMinorVersion(),
                    hotfixVersion.getPatchVersion(),
                    hotfixVersion.getHotfixVersion(),
                    hotfixVersion.getFullVersion(),
                    hotfixVersion.getMajorMinor(),
                    createdByEmail,
                    comment,
                    hotfixVersion.getCreatedAt(),
                    getCreatedFilesList(hotfixVersion)
            );

        } catch (BusinessException e) {
            // 생성된 핫픽스 디렉토리 롤백
            if (hotfixPath != null) {
                fileSystemService.deleteDirectory(hotfixPath);
            }
            throw e;
        } catch (Exception e) {
            // 생성된 핫픽스 디렉토리 롤백
            if (hotfixPath != null) {
                fileSystemService.deleteDirectory(hotfixPath);
            }
            log.error("ZIP 파일로 핫픽스 버전 생성 실패: hotfixBaseVersionId={}", hotfixBaseVersionId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "핫픽스 버전 생성 중 오류가 발생했습니다: " + e.getMessage());
        } finally {
            // 임시 디렉토리 정리
            if (tempDir != null) {
                fileSystemService.deleteDirectory(tempDir);
            }
        }
    }

    /**
     * 핫픽스용 파일 복사 및 DB 저장
     *
     * @param hotfixBaseVersion 핫픽스 원본 버전
     * @param tempDir           임시 디렉토리 (ZIP 압축 해제 경로)
     * @param hotfixVersion     핫픽스 버전 번호
     * @param comment           코멘트
     * @param createdByEmail    생성자 이메일
     * @param isApproved        승인 여부
     * @return 저장된 ReleaseVersion 엔티티
     */
    private ReleaseVersion copyFilesAndSaveToDbForHotfix(
            ReleaseVersion hotfixBaseVersion, Path tempDir, int hotfixVersion,
            String comment, String createdByEmail, boolean isApproved) throws IOException {

        // 생성자(Account) 조회 - 이메일로 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        // 핫픽스 버전은 원본 버전의 메타데이터를 상속
        ReleaseVersion.ReleaseVersionBuilder builder = ReleaseVersion.builder()
                .project(hotfixBaseVersion.getProject())
                .releaseType(hotfixBaseVersion.getReleaseType())
                .customer(hotfixBaseVersion.getCustomer())
                .version(hotfixBaseVersion.getVersion())
                .majorVersion(hotfixBaseVersion.getMajorVersion())
                .minorVersion(hotfixBaseVersion.getMinorVersion())
                .patchVersion(hotfixBaseVersion.getPatchVersion())
                .hotfixVersion(hotfixVersion)
                .hotfixBaseVersion(hotfixBaseVersion)
                .creator(creator)
                .createdByEmail(createdByEmail)
                .comment(comment)
                .isApproved(isApproved);

        // 승인된 상태로 생성 시 승인자 정보 설정
        if (isApproved) {
            builder.approver(creator)
                    .approvedByEmail(createdByEmail)
                    .approvedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        }

        ReleaseVersion savedHotfix = releaseVersionRepository.save(builder.build());

        log.info("핫픽스 ReleaseVersion 저장 완료 - ID: {}, fullVersion: {}, hotfixBaseVersion: {}",
                savedHotfix.getReleaseVersionId(), savedHotfix.getFullVersion(),
                hotfixBaseVersion.getVersion());

        // 클로저 테이블에 계층 구조 데이터 추가 (트리 조회에 필요)
        treeService.createHierarchyForNewVersion(savedHotfix, hotfixBaseVersion.getReleaseType());

        return savedHotfix;
    }

    /**
     * 핫픽스 디렉토리 경로 생성
     *
     * @param hotfixVersion     핫픽스 버전 엔티티
     * @param hotfixBaseVersion 핫픽스 원본 버전 엔티티
     * @return 생성된 핫픽스 디렉토리 경로
     */
    private Path createHotfixDirectory(ReleaseVersion hotfixVersion, ReleaseVersion hotfixBaseVersion) throws IOException {
        String projectId = hotfixBaseVersion.getProject() != null
                ? hotfixBaseVersion.getProject().getProjectId()
                : "infraeye2";
        String basePath;

        if ("STANDARD".equals(hotfixBaseVersion.getReleaseType())) {
            basePath = String.format("versions/%s/standard/%s/%s/hotfix/%d",
                    projectId,
                    hotfixBaseVersion.getMajorMinor(),
                    hotfixBaseVersion.getVersion(),
                    hotfixVersion.getHotfixVersion());
        } else {
            // CUSTOM인 경우 고객사 코드 사용
            String customerCode = hotfixBaseVersion.getCustomer() != null
                    ? hotfixBaseVersion.getCustomer().getCustomerCode()
                    : "unknown";
            basePath = String.format("versions/%s/custom/%s/%s/%s/hotfix/%d",
                    projectId,
                    customerCode,
                    hotfixBaseVersion.getMajorMinor(),
                    hotfixBaseVersion.getVersion(),
                    hotfixVersion.getHotfixVersion());
        }

        Path hotfixPath = Paths.get(baseReleasePath, basePath);
        Files.createDirectories(hotfixPath);

        log.info("핫픽스 디렉토리 생성 완료: {}", hotfixPath);

        return hotfixPath;
    }

    /**
     * 핫픽스 파일 복사 처리
     *
     * @param tempDir       임시 디렉토리 (ZIP 압축 해제 경로)
     * @param hotfixPath    핫픽스 디렉토리 경로
     * @param hotfixVersion 핫픽스 버전 엔티티
     */
    private void copyHotfixFiles(Path tempDir, Path hotfixPath, ReleaseVersion hotfixVersion) throws IOException {
        // 모든 카테고리 폴더 순회 및 파일 복사
        Files.list(tempDir)
                .filter(Files::isDirectory)
                .forEach(categoryDir -> {
                    String categoryName = categoryDir.getFileName().toString().toUpperCase();

                    try {
                        // FileCategory 검증 및 변환
                        FileCategory fileCategory = FileCategory.fromCode(categoryName);

                        // 타겟 카테고리 디렉토리 생성
                        Path targetCategoryDir = hotfixPath.resolve(categoryName.toLowerCase());
                        Files.createDirectories(targetCategoryDir);

                        log.info("핫픽스 카테고리 폴더 처리 시작: {} -> {}", categoryName, fileCategory.getDescription());

                        // 카테고리별 파일 복사 (재귀적)
                        processCategoryFiles(categoryDir, targetCategoryDir, hotfixVersion, fileCategory);

                    } catch (IllegalArgumentException e) {
                        log.warn("알 수 없는 카테고리 폴더 무시: {}", categoryName);
                    } catch (IOException e) {
                        log.error("핫픽스 카테고리 파일 복사 실패: {}", categoryName, e);
                        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                                "핫픽스 파일 복사 중 오류 발생: " + e.getMessage());
                    }
                });
    }

    /**
     * 파일명으로부터 파일 타입 결정
     */
    public String determineFileType(String fileName) {
        String lowerCaseFileName = fileName.toLowerCase();

        if (lowerCaseFileName.endsWith(".sql")) {
            return "SQL";
        } else if (lowerCaseFileName.endsWith(".war")) {
            return "WAR";
        } else if (lowerCaseFileName.endsWith(".jar")) {
            return "JAR";
        } else if (lowerCaseFileName.endsWith(".md")) {
            return "MD";
        } else if (lowerCaseFileName.endsWith(".pdf")) {
            return "PDF";
        } else if (lowerCaseFileName.endsWith(".exe")) {
            return "EXE";
        } else if (lowerCaseFileName.endsWith(".sh")) {
            return "SH";
        } else if (lowerCaseFileName.endsWith(".bat")) {
            return "BAT";
        } else if (lowerCaseFileName.endsWith(".txt")) {
            return "TXT";
        } else if (lowerCaseFileName.endsWith(".yml") || lowerCaseFileName.endsWith(".yaml")) {
            return "YAML";
        } else if (lowerCaseFileName.endsWith(".properties")) {
            return "PROPERTIES";
        } else if (lowerCaseFileName.endsWith(".xml")) {
            return "XML";
        } else {
            return "UNDEFINED";
        }
    }

    /**
     * 핫픽스 패치 스크립트 생성
     *
     * <p>핫픽스 생성 시 MariaDB 및 CrateDB 패치 스크립트를 자동 생성합니다.
     * 각 데이터베이스 타입에 해당하는 SQL 파일이 있는 경우에만 스크립트를 생성합니다.
     *
     * @param hotfixVersion 핫픽스 버전 엔티티
     * @param hotfixPath    핫픽스 디렉토리 경로
     * @param assigneeId    담당자 ID (선택, null 가능)
     */
    private void generateHotfixPatchScripts(ReleaseVersion hotfixVersion, Path hotfixPath, Long assigneeId) {
        log.info("핫픽스 패치 스크립트 생성 시작 - fullVersion: {}, assigneeId: {}",
                hotfixVersion.getFullVersion(), assigneeId);

        // 프로젝트 ID 추출
        String projectId = hotfixVersion.getProject() != null
                ? hotfixVersion.getProject().getProjectId()
                : "infraeye2";

        // 담당자 이메일 조회 (패치 담당자 기본값으로 사용 - 패치 파일과 동일하게 이메일 사용)
        String defaultPatchedBy = null;
        if (assigneeId != null) {
            defaultPatchedBy = accountRepository.findByAccountId(assigneeId)
                    .map(Account::getEmail)
                    .orElse(null);
        }

        // 핫픽스 버전의 파일 목록 조회
        List<ReleaseFile> hotfixFiles = releaseFileRepository
                .findAllByReleaseVersion_ReleaseVersionIdOrderByExecutionOrderAsc(
                        hotfixVersion.getReleaseVersionId());

        // MariaDB SQL 파일 필터링
        List<ReleaseFile> mariadbFiles = hotfixFiles.stream()
                .filter(f -> "MARIADB".equalsIgnoreCase(f.getSubCategory()))
                .toList();

        // CrateDB SQL 파일 필터링
        List<ReleaseFile> cratedbFiles = hotfixFiles.stream()
                .filter(f -> "CRATEDB".equalsIgnoreCase(f.getSubCategory()))
                .toList();

        // 스크립트 출력 디렉토리 (baseReleasePath 기준 상대 경로)
        Path basePath = Paths.get(baseReleasePath).toAbsolutePath().normalize();
        Path absoluteHotfixPath = hotfixPath.toAbsolutePath().normalize();
        String outputDirPath = basePath.relativize(absoluteHotfixPath).toString().replace("\\", "/");

        // MariaDB 패치 스크립트 생성 (MariaDB는 VERSION_HISTORY 기록을 위해 항상 생성 - infraeye1/infraeye2만)
        try {
            mariaDBScriptGenerator.generateHotfixScript(
                    projectId,
                    hotfixVersion,
                    mariadbFiles,
                    outputDirPath,
                    defaultPatchedBy);

            // 스크립트 파일을 release_file 테이블에 등록
            saveScriptFileToDb(hotfixVersion, outputDirPath, mariaDBScriptGenerator.getScriptFileName());

            log.info("핫픽스 MariaDB 패치 스크립트 생성 완료 - path: {}", outputDirPath);
        } catch (Exception e) {
            log.error("핫픽스 MariaDB 패치 스크립트 생성 실패", e);
            // 스크립트 생성 실패는 핫픽스 생성 자체를 실패시키지 않음
        }

        // CrateDB 패치 스크립트 생성 (CrateDB 파일이 있는 경우에만)
        if (!cratedbFiles.isEmpty()) {
            try {
                crateDBScriptGenerator.generateHotfixScript(
                        projectId,
                        hotfixVersion,
                        cratedbFiles,
                        outputDirPath,
                        defaultPatchedBy);

                // 스크립트 파일을 release_file 테이블에 등록
                saveScriptFileToDb(hotfixVersion, outputDirPath, crateDBScriptGenerator.getScriptFileName());

                log.info("핫픽스 CrateDB 패치 스크립트 생성 완료 - path: {}", outputDirPath);
            } catch (Exception e) {
                log.error("핫픽스 CrateDB 패치 스크립트 생성 실패", e);
                // 스크립트 생성 실패는 핫픽스 생성 자체를 실패시키지 않음
            }
        }

        log.info("핫픽스 패치 스크립트 생성 완료 - mariadbFiles: {}, cratedbFiles: {}",
                mariadbFiles.size(), cratedbFiles.size());
    }

    /**
     * 스크립트 파일을 release_file 테이블에 등록
     *
     * @param releaseVersion 릴리즈 버전 엔티티
     * @param outputDirPath  출력 디렉토리 상대 경로
     * @param scriptFileName 스크립트 파일명 (예: mariadb_patch.sh)
     */
    private void saveScriptFileToDb(ReleaseVersion releaseVersion, String outputDirPath, String scriptFileName) {
        try {
            // 스크립트 파일 경로
            Path scriptPath = Paths.get(baseReleasePath, outputDirPath, scriptFileName);

            log.info("스크립트 파일 DB 등록 시도 - path: {}", scriptPath);

            if (!Files.exists(scriptPath)) {
                log.warn("스크립트 파일이 존재하지 않아 DB 등록을 건너뜁니다: {}", scriptPath);
                return;
            }

            // 파일 크기 및 체크섬 계산
            byte[] fileContent = Files.readAllBytes(scriptPath);
            long fileSize = fileContent.length;
            String checksum = calculateChecksum(fileContent);

            // 물리 경로 (baseReleasePath 기준 상대 경로)
            String physicalPath = outputDirPath + "/" + scriptFileName;

            // 상대 경로 (ZIP 내부 경로)
            String relativePath = "/" + scriptFileName;

            // ReleaseFile 저장
            // fileCategory를 null로 설정하여 ZIP 다운로드 시 루트에 배치
            ReleaseFile releaseFile = ReleaseFile.builder()
                    .releaseVersion(releaseVersion)
                    .fileType("SH")
                    .fileCategory(null)  // null로 설정하여 ZIP 루트에 배치
                    .subCategory(null)
                    .fileName(scriptFileName)
                    .filePath(physicalPath)
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .executionOrder(0)  // 스크립트는 실행 순서 0
                    .description("핫픽스 패치 실행 스크립트 (자동 생성)")
                    .build();

            releaseFileRepository.save(releaseFile);

            log.info("스크립트 파일 DB 등록 완료: {} (versionId: {}, size: {})",
                    scriptFileName, releaseVersion.getReleaseVersionId(), fileSize);

        } catch (Exception e) {
            log.error("스크립트 파일 DB 등록 실패 - scriptFileName: {}, outputDirPath: {}, error: {}",
                    scriptFileName, outputDirPath, e.getMessage(), e);
            // 스크립트 DB 등록 실패는 핫픽스 생성 자체를 실패시키지 않음
        }
    }
}
