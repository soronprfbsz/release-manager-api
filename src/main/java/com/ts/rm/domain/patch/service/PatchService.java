package com.ts.rm.domain.patch.service;

import com.ts.rm.domain.customer.entity.Customer;
import com.ts.rm.domain.customer.repository.CustomerRepository;
import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.mapper.PatchDtoMapper;
import com.ts.rm.domain.patch.repository.PatchRepository;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.pagination.PageRowNumberUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 패치 서비스 (오케스트레이터)
 *
 * <p>패치 CRUD 및 다른 서비스들을 조율하는 역할을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatchService {

    private final PatchRepository patchRepository;
    private final PatchDtoMapper patchDtoMapper;
    private final PatchGenerationService patchGenerationService;
    private final PatchDownloadService patchDownloadService;
    private final ReleaseVersionRepository releaseVersionRepository;
    private final CustomerRepository customerRepository;

    @Value("${app.release.base-path:data/release-manager}")
    private String releaseBasePath;

    /**
     * 패치 생성 (버전 문자열 기반) - 위임
     */
    @Transactional
    public Patch generatePatchByVersion(String projectId, String releaseType, Long customerId,
            String fromVersion, String toVersion, String createdByEmail, String description,
            Long engineerId, String patchName, PatchDto.BuildSelection buildSelection) {
        boolean sameBase = fromVersion.equals(toVersion);
        validateBuildSelection(buildSelection, sameBase);
        return patchGenerationService.generatePatchByVersion(
                projectId, releaseType, customerId, fromVersion, toVersion,
                createdByEmail, description, engineerId, patchName, buildSelection);
    }

    /**
     * 패치 생성 (버전 ID 기반) - 위임
     */
    @Transactional
    public Patch generatePatch(String projectId, Long fromVersionId, Long toVersionId, Long customerId,
            String createdByEmail, String description, Long engineerId, String patchName,
            PatchDto.BuildSelection buildSelection) {
        boolean sameBase = fromVersionId.equals(toVersionId);
        validateBuildSelection(buildSelection, sameBase);
        return patchGenerationService.generatePatch(
                projectId, fromVersionId, toVersionId, customerId,
                createdByEmail, description, engineerId, patchName, buildSelection);
    }

    /**
     * buildSelection 의 spec §4.3 검증 룰을 검사한다.
     *
     * @param selection  요청에서 받은 buildSelection (null 가능)
     * @param sameBase   from.id == to.id 여부 (Build-only 케이스)
     * @throws BusinessException INVALID_INPUT_VALUE 룰에 위배되면
     */
    public static void validateBuildSelection(PatchDto.BuildSelection selection, boolean sameBase) {
        boolean enabled = selection != null && selection.enabled();
        boolean pickerEmpty = selection == null
                || (selection.web() == null && (selection.engines() == null || selection.engines().isEmpty()));

        if (enabled && pickerEmpty) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "빌드 미포함이면 토글을 OFF 로 두십시오");
        }
        if (sameBase && (!enabled || pickerEmpty)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "동일 버전 패치는 최소 1개 이상의 빌드 선택이 필요합니다");
        }
    }

    /**
     * 패치 조회
     */
    @Transactional(readOnly = true)
    public Patch getPatch(Long patchId) {
        return patchRepository.findById(patchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "패치를 찾을 수 없습니다: " + patchId));
    }

    /**
     * 패치 목록 페이징 조회
     *
     * @param projectId    프로젝트 ID (null이면 전체)
     * @param releaseType  릴리즈 타입 (STANDARD/CUSTOM, null이면 전체)
     * @param customerCode 고객사 코드 (null이면 전체)
     * @param pageable     페이징 정보
     * @return 패치 목록 페이지 (rowNumber 포함)
     */
    @Transactional(readOnly = true)
    public Page<PatchDto.ListResponse> listPatchesWithPaging(String projectId, String releaseType,
            String customerCode, Pageable pageable) {
        Page<Patch> patches = patchRepository.findAllWithFilters(projectId, releaseType, customerCode, pageable);

        // rowNumber 계산 (공통 유틸리티 사용)
        return PageRowNumberUtil.mapWithRowNumber(patches, (patch, rowNumber) -> {
            PatchDto.ListResponse response = patchDtoMapper.toListResponse(patch);
            return new PatchDto.ListResponse(
                    rowNumber,
                    response.patchId(),
                    response.projectId(),
                    response.releaseType(),
                    response.customerCode(),
                    response.customerName(),
                    response.fromVersion(),
                    response.toVersion(),
                    response.patchName(),
                    response.createdByEmail(),
                    response.createdByName(),
                    response.createdByAvatarStyle(),
                    response.createdByAvatarSeed(),
                    response.isDeletedCreator(),
                    response.description(),
                    response.assigneeId(),
                    response.assigneeName(),
                    response.createdAt(),
                    response.updatedAt()
            );
        });
    }

    /**
     * 패치를 스트리밍 방식으로 ZIP 압축하여 출력 스트림에 작성 - 위임
     */
    @Transactional(readOnly = true)
    public void streamPatchAsZip(Long patchId, OutputStream outputStream) {
        Patch patch = getPatch(patchId);
        patchDownloadService.streamPatchAsZip(patch, outputStream);
    }

    /**
     * 패치 ZIP 파일명 생성 - 위임
     */
    public String getZipFileName(Long patchId) {
        Patch patch = getPatch(patchId);
        return patchDownloadService.getZipFileName(patch);
    }

    /**
     * 패치 디렉토리의 압축 전 총 크기 계산 - 위임
     */
    public long calculateUncompressedSize(Long patchId) {
        Patch patch = getPatch(patchId);
        return patchDownloadService.calculateUncompressedSize(patch);
    }

    /**
     * 패치 ZIP 파일 내부 구조 조회 - 위임
     */
    @Transactional(readOnly = true)
    public PatchDto.DirectoryNode getZipFileStructure(Long patchId) {
        Patch patch = getPatch(patchId);
        return patchDownloadService.getZipFileStructure(patch);
    }

    /**
     * 패치 파일 내용 조회 - 위임
     */
    @Transactional(readOnly = true)
    public PatchDto.FileContentResponse getFileContent(Long patchId, String relativePath) {
        Patch patch = getPatch(patchId);
        return patchDownloadService.getFileContent(patch, relativePath);
    }

    /**
     * 패치 삭제 (DB 레코드 + 실제 파일)
     *
     * @param patchId 패치 ID
     */
    @Transactional
    public void deletePatch(Long patchId) {
        // 1. 패치 조회
        Patch patch = getPatch(patchId);

        // 2. 실제 파일 디렉토리 삭제
        Path patchDir = Paths.get(releaseBasePath, patch.getOutputPath());

        if (Files.exists(patchDir)) {
            try {
                deleteDirectoryRecursively(patchDir);
                log.info("패치 디렉토리 삭제 완료: {}", patchDir.toAbsolutePath());
            } catch (IOException e) {
                log.error("패치 디렉토리 삭제 실패: {}", patchDir, e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "패치 파일 삭제 중 오류가 발생했습니다: " + e.getMessage());
            }
        } else {
            log.warn("패치 디렉토리가 존재하지 않습니다: {}", patchDir);
        }

        // 3. DB 레코드 삭제
        patchRepository.delete(patch);

        log.info("패치 삭제 완료 - ID: {}, Name: {}", patchId, patch.getPatchName());
    }

    /**
     * 디렉토리 재귀적 삭제
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {
            stream.sorted((p1, p2) -> -p1.compareTo(p2)) // 역순 정렬 (하위 항목부터 삭제)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.debug("파일/디렉토리 삭제: {}", path);
                        } catch (IOException e) {
                            log.warn("파일/디렉토리 삭제 실패: {}", path, e);
                        }
                    });
        }
    }

    // ========================================
    // 커스텀 패치용 메서드
    // ========================================

    /**
     * 커스텀 버전 보유 고객사 목록 조회
     *
     * @param projectId 프로젝트 ID
     * @return 커스텀 버전이 있는 고객사 목록
     */
    @Transactional(readOnly = true)
    public List<PatchDto.CustomerWithCustomVersions> getCustomersWithCustomVersions(String projectId) {
        log.info("커스텀 버전 보유 고객사 목록 조회 - projectId: {}", projectId);

        List<Long> customerIds = releaseVersionRepository.findCustomerIdsWithCustomVersions(projectId);

        return customerIds.stream()
                .map(customerId -> customerRepository.findById(customerId)
                        .map(customer -> new PatchDto.CustomerWithCustomVersions(
                                customer.getCustomerId(),
                                customer.getCustomerCode(),
                                customer.getCustomerName()
                        ))
                        .orElse(null))
                .filter(dto -> dto != null)
                .toList();
    }

    /**
     * 고객사별 커스텀 버전 목록 조회 (셀렉트박스용)
     *
     * <p>베이스 버전(표준본)을 첫 번째로, 이후 커스텀 버전들을 반환합니다.
     * 프론트엔드에서 From 버전 선택 시 베이스 버전부터 선택 가능합니다.
     *
     * @param projectId  프로젝트 ID
     * @param customerId 고객사 ID
     * @return 버전 목록 (베이스 버전 + 커스텀 버전들)
     */
    @Transactional(readOnly = true)
    public List<PatchDto.CustomVersionSelectOption> getCustomVersionsByCustomer(String projectId, Long customerId) {
        log.info("고객사별 커스텀 버전 목록 조회 - projectId: {}, customerId: {}", projectId, customerId);

        // 고객사 존재 확인
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND,
                        "고객사를 찾을 수 없습니다: " + customerId));

        List<ReleaseVersion> customVersions = releaseVersionRepository.findAllByCustomer_CustomerIdOrderByCreatedAtDesc(customerId);

        List<PatchDto.CustomVersionSelectOption> result = new java.util.ArrayList<>();

        // 베이스 버전 추가 (첫 번째 커스텀 버전의 customBaseVersion에서 가져옴)
        if (!customVersions.isEmpty()) {
            ReleaseVersion firstCustomVersion = customVersions.get(customVersions.size() - 1); // 가장 오래된 버전
            ReleaseVersion customBaseVersion = firstCustomVersion.getCustomBaseVersion();

            if (customBaseVersion != null) {
                result.add(new PatchDto.CustomVersionSelectOption(
                        customBaseVersion.getReleaseVersionId(),
                        customBaseVersion.getVersion(),
                        true, // 표준 버전은 항상 승인됨
                        true  // 베이스 버전
                ));
            }
        }

        // 커스텀 버전들 추가 (최신순)
        // 핫픽스/빌드는 version 필드가 base 와 동일하므로 fullVersion 으로 식별 가능하게 노출
        // (예: 1.1.0-companyA.1.0.0, 1.1.0-companyA.1.0.0.1, 1.1.0-companyA.1.0.0.260427)
        customVersions.forEach(v -> result.add(new PatchDto.CustomVersionSelectOption(
                v.getReleaseVersionId(),
                v.getFullVersion(),
                v.getIsApproved(),
                false // 커스텀 버전
        )));

        return result;
    }

    /**
     * 커스텀 패치 생성 (버전 문자열 기반) - 위임
     */
    @Transactional
    public Patch generateCustomPatchByVersion(String projectId, Long customerId,
            String fromVersion, String toVersion, String createdByEmail, String description,
            Long engineerId, String patchName) {
        return patchGenerationService.generateCustomPatchByVersion(
                projectId, customerId, fromVersion, toVersion,
                createdByEmail, description, engineerId, patchName);
    }

    /**
     * 패치 일괄 삭제 (DB 레코드 + 실제 파일)
     *
     * @param patchIds 삭제할 패치 ID 목록
     * @return 삭제 결과
     */
    @Transactional
    public PatchDto.BatchDeleteResponse batchDeletePatches(List<Long> patchIds) {
        log.info("패치 일괄 삭제 요청 - patchIds: {}", patchIds);

        if (patchIds == null || patchIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "패치 ID 목록은 비어있을 수 없습니다");
        }

        // 1. 패치 일괄 조회 및 검증
        List<Patch> patches = patchRepository.findAllById(patchIds);

        if (patches.size() != patchIds.size()) {
            log.warn("일부 패치를 찾을 수 없음 - 요청: {}, 조회: {}",
                    patchIds.size(), patches.size());
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "일부 패치를 찾을 수 없습니다");
        }

        // 2. 각 패치의 실제 파일 디렉토리 삭제
        for (Patch patch : patches) {
            Path patchDir = Paths.get(releaseBasePath, patch.getOutputPath());

            if (Files.exists(patchDir)) {
                try {
                    deleteDirectoryRecursively(patchDir);
                    log.info("패치 디렉토리 삭제 완료: {}", patchDir.toAbsolutePath());
                } catch (IOException e) {
                    log.error("패치 디렉토리 삭제 실패: {}", patchDir, e);
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                            "패치 파일 삭제 중 오류가 발생했습니다: " + e.getMessage());
                }
            } else {
                log.warn("패치 디렉토리가 존재하지 않습니다: {}", patchDir);
            }
        }

        // 3. DB 레코드 일괄 삭제
        patchRepository.deleteAll(patches);

        String message = String.format("%d개 패치가 삭제되었습니다.", patches.size());
        log.info("패치 일괄 삭제 완료 - {}", message);

        return new PatchDto.BatchDeleteResponse(patches.size(), message);
    }
}
