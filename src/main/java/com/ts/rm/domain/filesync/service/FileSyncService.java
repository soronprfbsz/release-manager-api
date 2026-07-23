package com.ts.rm.domain.filesync.service;

import com.ts.rm.domain.common.service.CodeService;
import com.ts.rm.domain.common.service.FileStorageService;
import com.ts.rm.domain.filesync.entity.FileSyncIgnore;
import com.ts.rm.domain.filesync.repository.FileSyncIgnoreRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.file.FileChecksumUtil;
import com.ts.rm.domain.filesync.adapter.FileSyncAdapter;
import com.ts.rm.domain.filesync.dto.FileSyncDiscrepancy;
import com.ts.rm.domain.filesync.dto.FileSyncDto;
import com.ts.rm.domain.filesync.dto.FileSyncMetadata;
import com.ts.rm.domain.filesync.enums.FileSyncAction;
import com.ts.rm.domain.filesync.enums.FileSyncStatus;
import com.ts.rm.domain.filesync.enums.FileSyncTarget;
import com.ts.rm.global.security.SecurityUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파일 동기화 서비스
 *
 * <p>파일시스템과 DB 메타데이터 간의 불일치를 분석하고 동기화합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSyncService {

    private final FileStorageService fileStorageService;
    private final FileSyncIgnoreRepository fileSyncIgnoreRepository;
    private final CodeService codeService;
    private final List<FileSyncAdapter> adapters;

    /** 분석 결과 캐시 (apply 시 참조용) */
    private final Map<String, FileSyncDiscrepancy> discrepancyCache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String CODE_TYPE_FILE_SYNC_STATUS = "FILE_SYNC_STATUS";
    private static final String CODE_TYPE_FILE_SYNC_TARGET = "FILE_SYNC_TARGET";

    /**
     * 파일 동기화 분석
     *
     * @param request 분석 요청
     * @return 분석 결과
     */
    @Transactional(readOnly = true)
    public FileSyncDto.AnalyzeResponse analyze(FileSyncDto.AnalyzeRequest request) {
        log.info("파일 동기화 분석 시작 - targets: {}, basePath: {}",
                request.getTargets(), request.getBasePath());

        // 캐시 초기화
        discrepancyCache.clear();

        // 코드 테이블에서 동적 메시지 조회
        Map<String, String> statusDescriptions = codeService.getCodeDescriptionMap(CODE_TYPE_FILE_SYNC_STATUS);
        Map<String, String> targetNames = codeService.getCodeNameMap(CODE_TYPE_FILE_SYNC_TARGET);

        List<FileSyncAdapter> targetAdapters = getTargetAdapters(request.getTargets());
        List<FileSyncDiscrepancy> allDiscrepancies = new ArrayList<>();
        Map<FileSyncTarget, Integer> discrepanciesByTarget = new EnumMap<>(FileSyncTarget.class);

        // 무시 목록 조회 (분석 대상 유형들에 대해)
        List<FileSyncTarget> targetTypes = targetAdapters.stream()
                .map(FileSyncAdapter::getTarget)
                .toList();
        Set<String> ignoredPaths = fileSyncIgnoreRepository.findIgnoredPathsByTargetTypes(targetTypes);
        log.debug("무시 목록 조회 완료 - {}건", ignoredPaths.size());

        int totalScanned = 0;
        int synced = 0;
        int ignoredCount = 0;

        for (FileSyncAdapter adapter : targetAdapters) {
            log.debug("어댑터 분석 시작: {}", adapter.getTarget());

            // 1. DB에서 등록된 파일 목록 조회
            List<FileSyncMetadata> dbFiles = adapter.getRegisteredFiles(request.getBasePath());
            Map<String, FileSyncMetadata> dbFileMap = new HashMap<>();
            for (FileSyncMetadata meta : dbFiles) {
                dbFileMap.put(meta.getFilePath(), meta);
            }

            // 2. 파일시스템 스캔
            List<FileSyncMetadata> fsFiles = scanFileSystem(adapter, request.getBasePath());
            Map<String, FileSyncMetadata> fsFileMap = new HashMap<>();
            for (FileSyncMetadata meta : fsFiles) {
                fsFileMap.put(meta.getFilePath(), meta);
            }

            totalScanned += fsFiles.size();

            // 3. 비교 및 불일치 추출
            List<FileSyncDiscrepancy> discrepancies = compareFiles(
                    adapter, dbFileMap, fsFileMap, statusDescriptions, targetNames);

            // 4. 무시 목록에 있는 항목 필터링
            int beforeFilter = discrepancies.size();
            discrepancies = discrepancies.stream()
                    .filter(d -> !ignoredPaths.contains(d.getFilePath()))
                    .toList();
            ignoredCount += (beforeFilter - discrepancies.size());

            synced += (dbFiles.size() - discrepancies.stream()
                    .filter(d -> d.getStatus() != FileSyncStatus.UNREGISTERED)
                    .count());

            allDiscrepancies.addAll(discrepancies);
            discrepanciesByTarget.put(adapter.getTarget(), discrepancies.size());

            // 캐시에 저장
            for (FileSyncDiscrepancy disc : discrepancies) {
                discrepancyCache.put(disc.getId(), disc);
            }

            log.debug("어댑터 분석 완료: {} - 불일치 {}건", adapter.getTarget(), discrepancies.size());
        }

        log.info("파일 동기화 분석 완료 - 총 {}건 스캔, 동기화 {}건, 불일치 {}건, 무시됨 {}건",
                totalScanned, synced, allDiscrepancies.size(), ignoredCount);

        return FileSyncDto.AnalyzeResponse.builder()
                .analyzedAt(LocalDateTime.now())
                .summary(FileSyncDto.Summary.builder()
                        .totalScanned(totalScanned)
                        .synced(synced)
                        .discrepancies(allDiscrepancies.size())
                        .build())
                .discrepanciesByTarget(discrepanciesByTarget)
                .discrepancies(allDiscrepancies)
                .build();
    }

    /**
     * 동기화 액션 적용
     *
     * @param request 적용 요청
     * @return 적용 결과
     */
    @Transactional
    public FileSyncDto.ApplyResponse apply(FileSyncDto.ApplyRequest request) {
        log.info("파일 동기화 적용 시작 - {}건", request.getActions().size());

        List<FileSyncDto.ActionResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (FileSyncDto.ActionItem actionItem : request.getActions()) {
            FileSyncDto.ActionResult result = processAction(actionItem);
            results.add(result);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        log.info("파일 동기화 적용 완료 - 성공 {}건, 실패 {}건", successCount, failedCount);

        return FileSyncDto.ApplyResponse.builder()
                .appliedAt(LocalDateTime.now())
                .results(results)
                .summary(FileSyncDto.ApplySummary.builder()
                        .total(request.getActions().size())
                        .success(successCount)
                        .failed(failedCount)
                        .build())
                .build();
    }

    /**
     * 대상 어댑터 목록 반환
     */
    private List<FileSyncAdapter> getTargetAdapters(List<FileSyncTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return adapters;
        }
        return adapters.stream()
                .filter(a -> targets.contains(a.getTarget()))
                .toList();
    }

    /**
     * 파일시스템 스캔
     */
    private List<FileSyncMetadata> scanFileSystem(FileSyncAdapter adapter, String subPath) {
        String scanPath = adapter.getBaseScanPath();
        if (subPath != null && !subPath.isEmpty()) {
            scanPath = subPath;
        }

        Path basePath = fileStorageService.getAbsolutePath(scanPath);
        if (!Files.exists(basePath)) {
            log.warn("스캔 경로가 존재하지 않습니다: {}", basePath);
            return List.of();
        }

        List<String> allowedExtensions = adapter.getAllowedExtensions();
        List<String> excludedDirs = adapter.getExcludedDirectories();

        List<FileSyncMetadata> files = new ArrayList<>();

        // 폴더 기반 어댑터인 경우 (예: PATCH_FILE)
        if (adapter.isFolderBased()) {
            int depth = adapter.getFolderScanDepth();
            try (Stream<Path> paths = Files.walk(basePath, depth)) {
                paths.filter(Files::isDirectory)
                        .filter(p -> !p.equals(basePath)) // 기준 경로 제외
                        .filter(p -> isAllowedFolder(p, excludedDirs))
                        .forEach(path -> {
                            try {
                                FileSyncMetadata metadata = createMetadataFromFolder(path, adapter);
                                files.add(metadata);
                            } catch (Exception e) {
                                log.warn("폴더 메타데이터 생성 실패: {}", path, e);
                            }
                        });
            } catch (IOException e) {
                log.error("폴더 스캔 실패: {}", basePath, e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "폴더 스캔에 실패했습니다: " + e.getMessage());
            }
        } else {
            // 파일 기반 어댑터 (기본)
            try (Stream<Path> paths = Files.walk(basePath)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> isAllowedFile(p, allowedExtensions, excludedDirs))
                        .forEach(path -> {
                            try {
                                FileSyncMetadata metadata = createMetadataFromPath(path, adapter);
                                files.add(metadata);
                            } catch (Exception e) {
                                log.warn("파일 메타데이터 생성 실패: {}", path, e);
                            }
                        });
            } catch (IOException e) {
                log.error("파일시스템 스캔 실패: {}", basePath, e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "파일시스템 스캔에 실패했습니다: " + e.getMessage());
            }
        }

        return files;
    }

    /**
     * 허용된 폴더인지 확인
     */
    private boolean isAllowedFolder(Path path, List<String> excludedDirs) {
        if (excludedDirs != null) {
            String folderName = path.getFileName().toString();
            return !excludedDirs.contains(folderName);
        }
        return true;
    }

    /**
     * 폴더에서 FileSyncMetadata 생성 (폴더 기반 어댑터용)
     */
    private FileSyncMetadata createMetadataFromFolder(Path path, FileSyncAdapter adapter) throws IOException {
        Path basePath = fileStorageService.getAbsolutePath("");
        String relativePath = basePath.relativize(path).toString().replace("\\", "/");

        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        LocalDateTime lastModified = LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

        // 폴더는 파일 크기와 체크섬이 없음
        return FileSyncMetadata.builder()
                .filePath(relativePath)
                .fileName(path.getFileName().toString())
                .fileSize(null)  // 폴더는 크기 없음
                .checksum(null)  // 폴더는 체크섬 없음
                .lastModified(lastModified)
                .target(adapter.getTarget())
                .build();
    }

    /**
     * 허용된 파일인지 확인
     */
    private boolean isAllowedFile(Path path, List<String> allowedExtensions, List<String> excludedDirs) {
        // 제외 디렉토리 확인
        if (excludedDirs != null) {
            for (Path part : path) {
                if (excludedDirs.contains(part.toString())) {
                    return false;
                }
            }
        }

        // 확장자 확인
        if (allowedExtensions != null && !allowedExtensions.isEmpty()) {
            String fileName = path.getFileName().toString().toLowerCase();
            return allowedExtensions.stream()
                    .anyMatch(ext -> fileName.endsWith(ext.toLowerCase()));
        }

        return true;
    }

    /**
     * Path에서 FileSyncMetadata 생성
     */
    private FileSyncMetadata createMetadataFromPath(Path path, FileSyncAdapter adapter) throws IOException {
        Path basePath = fileStorageService.getAbsolutePath("");
        String relativePath = basePath.relativize(path).toString().replace("\\", "/");

        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        LocalDateTime lastModified = LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

        // 체크섬 계산 (성능 고려: 크기가 작을 때만 미리 계산)
        String checksum = null;
        if (attrs.size() < 10 * 1024 * 1024) { // 10MB 미만
            checksum = FileChecksumUtil.calculateChecksum(path);
        }

        return FileSyncMetadata.builder()
                .filePath(relativePath)
                .fileName(path.getFileName().toString())
                .fileSize(attrs.size())
                .checksum(checksum)
                .lastModified(lastModified)
                .target(adapter.getTarget())
                .build();
    }

    /**
     * DB와 파일시스템 비교
     */
    private List<FileSyncDiscrepancy> compareFiles(
            FileSyncAdapter adapter,
            Map<String, FileSyncMetadata> dbFileMap,
            Map<String, FileSyncMetadata> fsFileMap,
            Map<String, String> statusDescriptions,
            Map<String, String> targetNames) {

        FileSyncTarget target = adapter.getTarget();
        String targetName = targetNames.getOrDefault(target.name(), target.name());
        List<FileSyncDiscrepancy> discrepancies = new ArrayList<>();

        // 1. DB에만 있는 파일 (FILE_MISSING)
        for (Map.Entry<String, FileSyncMetadata> entry : dbFileMap.entrySet()) {
            String path = entry.getKey();
            if (!fsFileMap.containsKey(path)) {
                FileSyncMetadata dbMeta = entry.getValue();
                discrepancies.add(createDiscrepancy(
                        target, targetName, path, dbMeta.getFileName(),
                        FileSyncStatus.FILE_MISSING,
                        statusDescriptions.getOrDefault(FileSyncStatus.FILE_MISSING.name(),
                                FileSyncStatus.FILE_MISSING.getDescription()),
                        null, dbMeta,
                        List.of(FileSyncAction.DELETE_METADATA, FileSyncAction.IGNORE)));
            }
        }

        // 2. 파일시스템에만 있는 파일 (UNREGISTERED)
        //    단, 어댑터에서 유효하지 않은 경로는 무시 (예: ReleaseFile의 경우 버전이 없는 경로)
        for (Map.Entry<String, FileSyncMetadata> entry : fsFileMap.entrySet()) {
            String path = entry.getKey();
            if (!dbFileMap.containsKey(path)) {
                // 유효한 동기화 경로인지 확인 (유효하지 않으면 미등록으로 간주하지 않음)
                if (!adapter.isValidSyncPath(path)) {
                    log.debug("유효하지 않은 동기화 경로 - 무시됨: {}", path);
                    continue;
                }
                FileSyncMetadata fsMeta = entry.getValue();
                discrepancies.add(createDiscrepancy(
                        target, targetName, path, fsMeta.getFileName(),
                        FileSyncStatus.UNREGISTERED,
                        statusDescriptions.getOrDefault(FileSyncStatus.UNREGISTERED.name(),
                                FileSyncStatus.UNREGISTERED.getDescription()),
                        fsMeta, null,
                        List.of(FileSyncAction.REGISTER, FileSyncAction.DELETE_FILE, FileSyncAction.IGNORE)));
            }
        }

        // 3. 둘 다 있지만 불일치 (SIZE_MISMATCH, CHECKSUM_MISMATCH)
        for (Map.Entry<String, FileSyncMetadata> entry : dbFileMap.entrySet()) {
            String path = entry.getKey();
            FileSyncMetadata dbMeta = entry.getValue();
            FileSyncMetadata fsMeta = fsFileMap.get(path);

            if (fsMeta != null) {
                // 크기 비교
                if (!Objects.equals(dbMeta.getFileSize(), fsMeta.getFileSize())) {
                    discrepancies.add(createDiscrepancy(
                            target, targetName, path, dbMeta.getFileName(),
                            FileSyncStatus.SIZE_MISMATCH,
                            statusDescriptions.getOrDefault(FileSyncStatus.SIZE_MISMATCH.name(),
                                    FileSyncStatus.SIZE_MISMATCH.getDescription()),
                            fsMeta, dbMeta,
                            List.of(FileSyncAction.UPDATE_METADATA, FileSyncAction.IGNORE)));
                }
                // 체크섬 비교 (둘 다 있을 때만)
                else if (dbMeta.getChecksum() != null && fsMeta.getChecksum() != null
                        && !dbMeta.getChecksum().equals(fsMeta.getChecksum())) {
                    discrepancies.add(createDiscrepancy(
                            target, targetName, path, dbMeta.getFileName(),
                            FileSyncStatus.CHECKSUM_MISMATCH,
                            statusDescriptions.getOrDefault(FileSyncStatus.CHECKSUM_MISMATCH.name(),
                                    FileSyncStatus.CHECKSUM_MISMATCH.getDescription()),
                            fsMeta, dbMeta,
                            List.of(FileSyncAction.UPDATE_METADATA, FileSyncAction.IGNORE)));
                }
            }
        }

        return discrepancies;
    }

    /**
     * FileSyncDiscrepancy 생성
     */
    private FileSyncDiscrepancy createDiscrepancy(
            FileSyncTarget target,
            String targetName,
            String filePath,
            String fileName,
            FileSyncStatus status,
            String message,
            FileSyncMetadata fsMeta,
            FileSyncMetadata dbMeta,
            List<FileSyncAction> availableActions) {

        FileSyncDiscrepancy.FileInfo fileInfo = null;
        if (fsMeta != null) {
            fileInfo = FileSyncDiscrepancy.FileInfo.builder()
                    .size(fsMeta.getFileSize())
                    .checksum(fsMeta.getChecksum())
                    .lastModified(fsMeta.getLastModified() != null
                            ? fsMeta.getLastModified().format(DATE_FORMATTER) : null)
                    .build();
        }

        FileSyncDiscrepancy.DbInfo dbInfo = null;
        if (dbMeta != null) {
            dbInfo = FileSyncDiscrepancy.DbInfo.builder()
                    .id(dbMeta.getId())
                    .size(dbMeta.getFileSize())
                    .checksum(dbMeta.getChecksum())
                    .registeredAt(dbMeta.getRegisteredAt() != null
                            ? dbMeta.getRegisteredAt().format(DATE_FORMATTER) : null)
                    .build();
        }

        return FileSyncDiscrepancy.builder()
                .id(UUID.randomUUID().toString())
                .target(target)
                .targetName(targetName)
                .filePath(filePath)
                .fileName(fileName)
                .status(status)
                .message(message)
                .fileInfo(fileInfo)
                .dbInfo(dbInfo)
                .availableActions(availableActions)
                .build();
    }

    /**
     * 개별 액션 처리
     */
    private FileSyncDto.ActionResult processAction(FileSyncDto.ActionItem actionItem) {
        String id = actionItem.getId();
        FileSyncAction action = actionItem.getAction();

        // 캐시에서 discrepancy 조회
        FileSyncDiscrepancy discrepancy = discrepancyCache.get(id);
        if (discrepancy == null) {
            return FileSyncDto.ActionResult.builder()
                    .id(id)
                    .action(action)
                    .success(false)
                    .message("불일치 항목을 찾을 수 없습니다. 다시 분석해주세요.")
                    .build();
        }

        try {
            FileSyncAdapter adapter = getAdapterByTarget(discrepancy.getTarget());
            String resultMessage;

            switch (action) {
                case REGISTER -> {
                    // REGISTER 액션은 유형별 분리 API 사용 안내
                    String apiEndpoint = switch (discrepancy.getTarget()) {
                        case RESOURCE_FILE -> "/api/file-sync/resources/register";
                        case BACKUP_FILE -> "/api/file-sync/backups/register";
                        case PATCH_FILE -> "/api/file-sync/patches/register";
                        case RELEASE_FILE -> "/api/file-sync/releases/register";
                    };
                    return FileSyncDto.ActionResult.builder()
                            .id(id)
                            .filePath(discrepancy.getFilePath())
                            .action(action)
                            .success(false)
                            .message("파일 등록은 전용 API를 사용해주세요: " + apiEndpoint)
                            .build();
                }
                case UPDATE_METADATA -> {
                    // 파일시스템 정보로 DB 갱신
                    Path filePath = fileStorageService.getAbsolutePath(discrepancy.getFilePath());

                    // 폴더 기반 어댑터는 크기/체크섬 계산하지 않음
                    Long fileSize = null;
                    String checksum = null;
                    if (!adapter.isFolderBased()) {
                        checksum = FileChecksumUtil.calculateChecksum(filePath);
                        fileSize = Files.size(filePath);
                    }

                    FileSyncMetadata newMetadata = FileSyncMetadata.builder()
                            .filePath(discrepancy.getFilePath())
                            .fileName(discrepancy.getFileName())
                            .fileSize(fileSize)
                            .checksum(checksum)
                            .build();

                    Long dbId = discrepancy.getDbInfo().getId();
                    adapter.updateMetadata(dbId, newMetadata);
                    if (adapter.isFolderBased()) {
                        resultMessage = "폴더 메타데이터 갱신됨";
                    } else {
                        resultMessage = String.format("메타데이터 갱신됨 (size: %d → %d)",
                                discrepancy.getDbInfo().getSize(), fileSize);
                    }
                }
                case DELETE_METADATA -> {
                    Long dbId = discrepancy.getDbInfo().getId();
                    adapter.deleteMetadata(dbId);
                    resultMessage = "메타데이터 삭제됨";
                }
                case DELETE_FILE -> {
                    // 폴더 기반 어댑터는 디렉토리 삭제, 아니면 파일 삭제
                    if (adapter.isFolderBased()) {
                        fileStorageService.deleteDirectory(discrepancy.getFilePath());
                        resultMessage = "폴더 삭제됨";
                    } else {
                        fileStorageService.deleteFile(discrepancy.getFilePath());
                        resultMessage = "파일 삭제됨";
                    }
                }
                case IGNORE -> {
                    // 무시 목록에 등록 (중복 체크)
                    if (!fileSyncIgnoreRepository.existsByFilePathAndTargetType(
                            discrepancy.getFilePath(), discrepancy.getTarget())) {
                        String currentUserEmail = SecurityUtil.getTokenInfo().email();
                        FileSyncIgnore ignore = FileSyncIgnore.builder()
                                .filePath(discrepancy.getFilePath())
                                .targetType(discrepancy.getTarget())
                                .status(discrepancy.getStatus())
                                .ignoredBy(currentUserEmail)
                                .build();
                        fileSyncIgnoreRepository.save(ignore);
                        resultMessage = "무시 목록에 등록됨";
                    } else {
                        resultMessage = "이미 무시 목록에 등록된 항목";
                    }
                }
                default -> {
                    return FileSyncDto.ActionResult.builder()
                            .id(id)
                            .filePath(discrepancy.getFilePath())
                            .action(action)
                            .success(false)
                            .message("지원하지 않는 액션입니다")
                            .build();
                }
            }

            // 캐시에서 제거
            discrepancyCache.remove(id);

            return FileSyncDto.ActionResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .action(action)
                    .success(true)
                    .message(resultMessage)
                    .build();

        } catch (Exception e) {
            log.error("액션 처리 실패: {} - {}", id, action, e);
            return FileSyncDto.ActionResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .action(action)
                    .success(false)
                    .message("처리 실패: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 대상 유형에 맞는 어댑터 반환
     */
    private FileSyncAdapter getAdapterByTarget(FileSyncTarget target) {
        return adapters.stream()
                .filter(a -> a.getTarget() == target)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "어댑터를 찾을 수 없습니다: " + target));
    }

    // ========================================
    // 무시 목록 관리
    // ========================================

    /**
     * 무시된 파일 목록 조회
     *
     * @param targetType 대상 유형 (null이면 전체)
     * @return 무시된 파일 목록
     */
    @Transactional(readOnly = true)
    public List<FileSyncDto.IgnoredFile> getIgnoredFiles(FileSyncTarget targetType) {
        List<FileSyncIgnore> ignoreList;

        if (targetType != null) {
            ignoreList = fileSyncIgnoreRepository.findByTargetTypeOrderByCreatedAtDesc(targetType);
        } else {
            ignoreList = fileSyncIgnoreRepository.findAllByOrderByCreatedAtDesc();
        }

        return ignoreList.stream()
                .map(this::toIgnoredFileDto)
                .toList();
    }

    /**
     * 무시 목록에서 제거
     *
     * @param ignoreId 무시 항목 ID
     */
    @Transactional
    public void removeFromIgnoreList(Long ignoreId) {
        FileSyncIgnore ignore = fileSyncIgnoreRepository.findById(ignoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "무시 항목을 찾을 수 없습니다: " + ignoreId));

        fileSyncIgnoreRepository.delete(ignore);
        log.info("무시 목록에서 제거됨 - id: {}, path: {}", ignoreId, ignore.getFilePath());
    }

    /**
     * FileSyncIgnore 엔티티를 DTO로 변환
     */
    private FileSyncDto.IgnoredFile toIgnoredFileDto(FileSyncIgnore entity) {
        return FileSyncDto.IgnoredFile.builder()
                .ignoreId(entity.getIgnoreId())
                .filePath(entity.getFilePath())
                .targetType(entity.getTargetType())
                .status(entity.getStatus())
                .ignoredBy(entity.getIgnoredBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    // ========================================
    // 파일 등록 (유형별 분리 API)
    // ========================================

    /**
     * 리소스 파일 등록
     *
     * @param request 등록 요청
     * @return 등록 결과
     */
    @Transactional
    public FileSyncDto.RegisterResponse registerResourceFiles(FileSyncDto.ResourceFileRegisterRequest request) {
        log.info("리소스 파일 등록 시작 - {}건", request.getItems().size());

        List<FileSyncDto.RegisterResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        FileSyncAdapter adapter = getAdapterByTarget(FileSyncTarget.RESOURCE_FILE);

        for (FileSyncDto.ResourceFileRegisterItem item : request.getItems()) {
            FileSyncDto.RegisterResult result = processResourceFileRegister(item, adapter);
            results.add(result);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        log.info("리소스 파일 등록 완료 - 성공 {}건, 실패 {}건", successCount, failedCount);

        return FileSyncDto.RegisterResponse.builder()
                .registeredAt(LocalDateTime.now())
                .results(results)
                .summary(FileSyncDto.ApplySummary.builder()
                        .total(request.getItems().size())
                        .success(successCount)
                        .failed(failedCount)
                        .build())
                .build();
    }

    /**
     * 백업 파일 등록
     *
     * @param request 등록 요청
     * @return 등록 결과
     */
    @Transactional
    public FileSyncDto.RegisterResponse registerBackupFiles(FileSyncDto.BackupFileRegisterRequest request) {
        log.info("백업 파일 등록 시작 - {}건", request.getItems().size());

        List<FileSyncDto.RegisterResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        FileSyncAdapter adapter = getAdapterByTarget(FileSyncTarget.BACKUP_FILE);

        for (FileSyncDto.BackupFileRegisterItem item : request.getItems()) {
            FileSyncDto.RegisterResult result = processBackupFileRegister(item, adapter);
            results.add(result);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        log.info("백업 파일 등록 완료 - 성공 {}건, 실패 {}건", successCount, failedCount);

        return FileSyncDto.RegisterResponse.builder()
                .registeredAt(LocalDateTime.now())
                .results(results)
                .summary(FileSyncDto.ApplySummary.builder()
                        .total(request.getItems().size())
                        .success(successCount)
                        .failed(failedCount)
                        .build())
                .build();
    }

    /**
     * 패치 파일 등록
     *
     * @param request 등록 요청
     * @return 등록 결과
     */
    @Transactional
    public FileSyncDto.RegisterResponse registerPatchFiles(FileSyncDto.PatchFileRegisterRequest request) {
        log.info("패치 파일 등록 시작 - {}건", request.getItems().size());

        List<FileSyncDto.RegisterResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        FileSyncAdapter adapter = getAdapterByTarget(FileSyncTarget.PATCH_FILE);

        for (FileSyncDto.PatchFileRegisterItem item : request.getItems()) {
            FileSyncDto.RegisterResult result = processPatchFileRegister(item, adapter);
            results.add(result);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        log.info("패치 파일 등록 완료 - 성공 {}건, 실패 {}건", successCount, failedCount);

        return FileSyncDto.RegisterResponse.builder()
                .registeredAt(LocalDateTime.now())
                .results(results)
                .summary(FileSyncDto.ApplySummary.builder()
                        .total(request.getItems().size())
                        .success(successCount)
                        .failed(failedCount)
                        .build())
                .build();
    }

    /**
     * 릴리즈 파일 등록
     *
     * @param request 등록 요청
     * @return 등록 결과
     */
    @Transactional
    public FileSyncDto.RegisterResponse registerReleaseFiles(FileSyncDto.ReleaseFileRegisterRequest request) {
        log.info("릴리즈 파일 등록 시작 - {}건", request.getItems().size());

        List<FileSyncDto.RegisterResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        FileSyncAdapter adapter = getAdapterByTarget(FileSyncTarget.RELEASE_FILE);

        for (FileSyncDto.ReleaseFileRegisterItem item : request.getItems()) {
            FileSyncDto.RegisterResult result = processReleaseFileRegister(item, adapter);
            results.add(result);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        log.info("릴리즈 파일 등록 완료 - 성공 {}건, 실패 {}건", successCount, failedCount);

        return FileSyncDto.RegisterResponse.builder()
                .registeredAt(LocalDateTime.now())
                .results(results)
                .summary(FileSyncDto.ApplySummary.builder()
                        .total(request.getItems().size())
                        .success(successCount)
                        .failed(failedCount)
                        .build())
                .build();
    }

    // ----------------------------------------
    // 유형별 등록 처리 메서드
    // ----------------------------------------

    private FileSyncDto.RegisterResult processResourceFileRegister(
            FileSyncDto.ResourceFileRegisterItem item, FileSyncAdapter adapter) {

        String id = item.getId();
        FileSyncDiscrepancy discrepancy = discrepancyCache.get(id);

        if (discrepancy == null) {
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .success(false)
                    .message("불일치 항목을 찾을 수 없습니다. 다시 분석해주세요.")
                    .build();
        }

        if (discrepancy.getTarget() != FileSyncTarget.RESOURCE_FILE) {
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(false)
                    .message("리소스 파일이 아닙니다. 대상 유형: " + discrepancy.getTarget())
                    .build();
        }

        try {
            Path filePath = fileStorageService.getAbsolutePath(discrepancy.getFilePath());
            String checksum = FileChecksumUtil.calculateChecksum(filePath);
            Long fileSize = Files.size(filePath);

            FileSyncMetadata metadata = FileSyncMetadata.builder()
                    .filePath(discrepancy.getFilePath())
                    .fileName(discrepancy.getFileName())
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .target(FileSyncTarget.RESOURCE_FILE)
                    .build();

            // 추가 메타데이터 구성
            Map<String, Object> additionalData = new HashMap<>();
            if (item.getResourceFileName() != null) {
                additionalData.put("resourceFileName", item.getResourceFileName());
            }
            if (item.getFileCategory() != null) {
                additionalData.put("fileCategory", item.getFileCategory());
            }
            if (item.getSubCategory() != null) {
                additionalData.put("subCategory", item.getSubCategory());
            }
            if (item.getDescription() != null) {
                additionalData.put("description", item.getDescription());
            }
            additionalData.put("createdByEmail", SecurityUtil.getTokenInfo().email());

            Long newId = adapter.registerFile(metadata, additionalData);

            // 캐시에서 제거
            discrepancyCache.remove(id);

            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(true)
                    .message("리소스 파일 등록 완료")
                    .registeredId(newId)
                    .build();

        } catch (Exception e) {
            log.error("리소스 파일 등록 실패: {}", id, e);
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(false)
                    .message("등록 실패: " + e.getMessage())
                    .build();
        }
    }

    private FileSyncDto.RegisterResult processBackupFileRegister(
            FileSyncDto.BackupFileRegisterItem item, FileSyncAdapter adapter) {

        String id = item.getId();
        FileSyncDiscrepancy discrepancy = discrepancyCache.get(id);

        if (discrepancy == null) {
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .success(false)
                    .message("불일치 항목을 찾을 수 없습니다. 다시 분석해주세요.")
                    .build();
        }

        if (discrepancy.getTarget() != FileSyncTarget.BACKUP_FILE) {
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(false)
                    .message("백업 파일이 아닙니다. 대상 유형: " + discrepancy.getTarget())
                    .build();
        }

        try {
            Path filePath = fileStorageService.getAbsolutePath(discrepancy.getFilePath());
            String checksum = FileChecksumUtil.calculateChecksum(filePath);
            Long fileSize = Files.size(filePath);

            FileSyncMetadata metadata = FileSyncMetadata.builder()
                    .filePath(discrepancy.getFilePath())
                    .fileName(discrepancy.getFileName())
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .target(FileSyncTarget.BACKUP_FILE)
                    .build();

            // 추가 메타데이터 구성
            Map<String, Object> additionalData = new HashMap<>();
            if (item.getFileCategory() != null) {
                additionalData.put("fileCategory", item.getFileCategory());
            }
            if (item.getDescription() != null) {
                additionalData.put("description", item.getDescription());
            }
            additionalData.put("createdByEmail", SecurityUtil.getTokenInfo().email());

            Long newId = adapter.registerFile(metadata, additionalData);

            // 캐시에서 제거
            discrepancyCache.remove(id);

            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(true)
                    .message("백업 파일 등록 완료")
                    .registeredId(newId)
                    .build();

        } catch (Exception e) {
            log.error("백업 파일 등록 실패: {}", id, e);
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(false)
                    .message("등록 실패: " + e.getMessage())
                    .build();
        }
    }

    private FileSyncDto.RegisterResult processPatchFileRegister(
            FileSyncDto.PatchFileRegisterItem item, FileSyncAdapter adapter) {

        String id = item.getId();
        FileSyncDiscrepancy discrepancy = discrepancyCache.get(id);

        if (discrepancy == null) {
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .success(false)
                    .message("불일치 항목을 찾을 수 없습니다. 다시 분석해주세요.")
                    .build();
        }

        if (discrepancy.getTarget() != FileSyncTarget.PATCH_FILE) {
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(false)
                    .message("패치 파일이 아닙니다. 대상 유형: " + discrepancy.getTarget())
                    .build();
        }

        try {
            // 패치는 폴더 기반이므로 크기/체크섬 없음
            FileSyncMetadata metadata = FileSyncMetadata.builder()
                    .filePath(discrepancy.getFilePath())
                    .fileName(discrepancy.getFileName())
                    .fileSize(null)
                    .checksum(null)
                    .target(FileSyncTarget.PATCH_FILE)
                    .build();

            // 추가 메타데이터 구성
            Map<String, Object> additionalData = new HashMap<>();
            if (item.getEngineerId() != null) {
                additionalData.put("engineerId", item.getEngineerId());
            }
            if (item.getSiteCode() != null) {
                additionalData.put("siteCode", item.getSiteCode());
            }
            if (item.getDescription() != null) {
                additionalData.put("description", item.getDescription());
            }
            additionalData.put("createdByEmail", SecurityUtil.getTokenInfo().email());

            Long newId = adapter.registerFile(metadata, additionalData);

            // 캐시에서 제거
            discrepancyCache.remove(id);

            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(true)
                    .message("패치 폴더 등록 완료")
                    .registeredId(newId)
                    .build();

        } catch (Exception e) {
            log.error("패치 파일 등록 실패: {}", id, e);
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(false)
                    .message("등록 실패: " + e.getMessage())
                    .build();
        }
    }

    private FileSyncDto.RegisterResult processReleaseFileRegister(
            FileSyncDto.ReleaseFileRegisterItem item, FileSyncAdapter adapter) {

        String id = item.getId();
        FileSyncDiscrepancy discrepancy = discrepancyCache.get(id);

        if (discrepancy == null) {
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .success(false)
                    .message("불일치 항목을 찾을 수 없습니다. 다시 분석해주세요.")
                    .build();
        }

        if (discrepancy.getTarget() != FileSyncTarget.RELEASE_FILE) {
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(false)
                    .message("릴리즈 파일이 아닙니다. 대상 유형: " + discrepancy.getTarget())
                    .build();
        }

        try {
            Path filePath = fileStorageService.getAbsolutePath(discrepancy.getFilePath());
            String checksum = FileChecksumUtil.calculateChecksum(filePath);
            Long fileSize = Files.size(filePath);

            FileSyncMetadata metadata = FileSyncMetadata.builder()
                    .filePath(discrepancy.getFilePath())
                    .fileName(discrepancy.getFileName())
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .target(FileSyncTarget.RELEASE_FILE)
                    .build();

            // 추가 메타데이터 구성
            Map<String, Object> additionalData = new HashMap<>();
            if (item.getReleaseVersionId() != null) {
                additionalData.put("releaseVersionId", item.getReleaseVersionId());
            }
            if (item.getFileCategory() != null) {
                additionalData.put("fileCategory", item.getFileCategory());
            }
            if (item.getSubCategory() != null) {
                additionalData.put("subCategory", item.getSubCategory());
            }
            if (item.getExecutionOrder() != null) {
                additionalData.put("executionOrder", item.getExecutionOrder());
            }
            if (item.getDescription() != null) {
                additionalData.put("description", item.getDescription());
            }

            Long newId = adapter.registerFile(metadata, additionalData);

            // 캐시에서 제거
            discrepancyCache.remove(id);

            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(true)
                    .message("릴리즈 파일 등록 완료")
                    .registeredId(newId)
                    .build();

        } catch (Exception e) {
            log.error("릴리즈 파일 등록 실패: {}", id, e);
            return FileSyncDto.RegisterResult.builder()
                    .id(id)
                    .filePath(discrepancy.getFilePath())
                    .success(false)
                    .message("등록 실패: " + e.getMessage())
                    .build();
        }
    }
}
