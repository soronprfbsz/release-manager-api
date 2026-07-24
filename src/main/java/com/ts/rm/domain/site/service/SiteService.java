package com.ts.rm.domain.site.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.site.dto.SiteDto;
import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.site.entity.SiteProject;
import com.ts.rm.domain.site.mapper.SiteDtoMapper;
import com.ts.rm.domain.site.repository.SiteProjectRepository;
import com.ts.rm.domain.site.repository.SiteRepository;
import com.ts.rm.domain.site.repository.SiteVersionRepository;
import com.ts.rm.domain.patch.repository.PatchHistoryRepository;
import com.ts.rm.domain.project.entity.Project;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.pagination.PageRowNumberUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Site Service
 *
 * <p>사이트 관리 비즈니스 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteService {

    private final SiteRepository siteRepository;
    private final SiteProjectRepository siteProjectRepository;
    private final SiteVersionRepository siteVersionRepository;
    private final PatchHistoryRepository patchHistoryRepository;
    private final ProjectRepository projectRepository;
    private final ReleaseVersionRepository releaseVersionRepository;
    private final SiteDtoMapper mapper;
    private final AccountLookupService accountLookupService;

    /**
     * 사이트 생성
     *
     * @param request   사이트 생성 요청
     * @param createdByEmail 생성자 이메일
     * @return 생성된 사이트 상세 정보
     */
    @Transactional
    public SiteDto.DetailResponse createSite(SiteDto.CreateRequest request, String createdByEmail) {
        log.info("Creating site with code: {}", request.siteCode());

        // 중복 검증
        if (siteRepository.existsBySiteCode(request.siteCode())) {
            throw new BusinessException(ErrorCode.SITE_CODE_CONFLICT);
        }

        // 생성자 Account 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        Site site = mapper.toEntity(request);
        // 글리프 정보 정규화 (빈 문자열이면 null 로 저장)
        site.updateGlyph(request.glyphText(), request.glyphBackgroundColor());
        site.setCreator(creator);
        site.setCreatedByEmail(creator.getEmail());
        site.setUpdater(creator);
        site.setUpdatedByEmail(creator.getEmail());

        Site savedSite = siteRepository.save(site);

        // 프로젝트 연결 처리
        SiteDto.ProjectInfo projectInfo = null;
        if (request.projectId() != null && !request.projectId().isBlank()) {
            projectInfo = saveSiteProject(savedSite, request.projectId());
        }

        log.info("Site created successfully with id: {}", savedSite.getSiteId());
        return toDetailResponseWithProject(savedSite, projectInfo);
    }

    /**
     * 사이트 조회 (ID)
     *
     * @param siteId 사이트 ID
     * @return 사이트 상세 정보
     */
    public SiteDto.DetailResponse getSiteById(Long siteId) {
        Site site = findSiteById(siteId);
        SiteDto.ProjectInfo projectInfo = getProjectInfoBySiteId(siteId);
        return toDetailResponseWithProject(site, projectInfo);
    }

    /**
     * 사이트 조회 (코드)
     *
     * @param siteCode 사이트 코드
     * @return 사이트 상세 정보
     */
    public SiteDto.DetailResponse getSiteByCode(String siteCode) {
        Site site = siteRepository.findBySiteCode(siteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_NOT_FOUND));
        SiteDto.ProjectInfo projectInfo = getProjectInfoBySiteId(site.getSiteId());
        return toDetailResponseWithProject(site, projectInfo);
    }

    /**
     * 사이트 목록 조회 (필터링 및 검색) - 비페이징
     *
     * @param isActive 활성화 여부 필터 (true: 활성화만, false: 비활성화만, null: 전체)
     * @param keyword  사이트명 검색 키워드
     * @return 사이트 목록
     */
    public List<SiteDto.DetailResponse> getSites(Boolean isActive, String keyword) {
        List<Site> sites;

        // 키워드 검색이 있는 경우
        if (keyword != null && !keyword.trim().isEmpty()) {
            sites = siteRepository.findBySiteNameContaining(keyword.trim());
        }
        // 활성화 여부 필터링
        else if (isActive != null) {
            sites = siteRepository.findAllByIsActive(isActive);
        }
        // 전체 조회
        else {
            sites = siteRepository.findAll();
        }

        return sites.stream()
                .map(site -> {
                    SiteDto.ProjectInfo projectInfo = getProjectInfoBySiteId(site.getSiteId());
                    return toDetailResponseWithProject(site, projectInfo);
                })
                .toList();
    }

    /**
     * 사이트 목록 페이징 조회 (필터링 및 검색)
     *
     * @param projectId 프로젝트 ID (null이면 전체)
     * @param isActive  활성화 여부 필터 (true: 활성화만, false: 비활성화만, null: 전체)
     * @param keyword   사이트명 검색 키워드
     * @param pageable  페이징 정보 (sort에 "project.projectName", "lastPatchedVersion", "lastPatchedAt", "hasCustomVersion" 사용 가능)
     * @return 사이트 페이지
     */
    public Page<SiteDto.ListResponse> getSitesWithPaging(String projectId, Boolean isActive, String keyword, Pageable pageable) {
        // QueryDSL Custom 메서드 사용 (프로젝트 정보 JOIN 정렬 지원)
        Page<Site> sites = siteRepository.findAllWithProjectInfo(projectId, isActive, keyword, pageable);

        // rowNumber 계산 (공통 유틸리티 사용)
        return PageRowNumberUtil.mapWithRowNumber(sites, (site, rowNumber) -> {
            SiteDto.ProjectInfo projectInfo = getProjectInfoBySiteId(site.getSiteId());
            boolean hasCustomVersion = releaseVersionRepository.existsBySite_SiteId(site.getSiteId());
            return new SiteDto.ListResponse(
                    rowNumber,
                    site.getSiteId(),
                    site.getSiteCode(),
                    site.getSiteName(),
                    site.getSiteCategory(),
                    site.getDescription(),
                    site.getIsActive(),
                    hasCustomVersion,
                    projectInfo,
                    site.getCreatedAt(),
                    site.getGlyphText(),
                    site.getGlyphBackgroundColor()
            );
        });
    }

    /**
     * 사이트 정보 수정
     *
     * <p>프로젝트 정보는 수정 불가 (기존 프로젝트 정보 유지)
     *
     * @param siteId    사이트 ID
     * @param request       수정 요청
     * @param updatedBy     수정자 이메일
     * @return 수정된 사이트 상세 정보
     */
    @Transactional
    public SiteDto.DetailResponse updateSite(Long siteId,
            SiteDto.UpdateRequest request, String updatedBy) {
        log.info("Updating site with siteId: {}", siteId);

        // 엔티티 조회
        Site site = findSiteById(siteId);

        // 수정자 Account 조회
        Account updater = accountLookupService.findByEmail(updatedBy);

        // Setter를 통한 수정 (JPA Dirty Checking)
        if (request.siteName() != null) {
            site.setSiteName(request.siteName());
        }
        if (request.siteCategory() != null) {
            site.setSiteCategory(request.siteCategory());
        }
        if (request.description() != null) {
            site.setDescription(request.description());
        }
        if (request.isActive() != null) {
            site.setIsActive(request.isActive());
        }
        // 글리프 정보 수정 (null=미변경, ""=제거)
        site.updateGlyph(request.glyphText(), request.glyphBackgroundColor());
        // updater는 항상 설정
        site.setUpdater(updater);
        site.setUpdatedByEmail(updater.getEmail());

        // 기존 프로젝트 정보 조회 (프로젝트는 수정 불가)
        SiteDto.ProjectInfo projectInfo = getProjectInfoBySiteId(siteId);

        // 트랜잭션 커밋 시 자동으로 UPDATE 쿼리 실행 (Dirty Checking)
        log.info("Site updated successfully with siteId: {}", siteId);
        return toDetailResponseWithProject(site, projectInfo);
    }

    /**
     * 사이트 삭제
     *
     * @param siteId 사이트 ID
     */
    @Transactional
    public void deleteSite(Long siteId) {
        log.info("Deleting site with siteId: {}", siteId);

        // 사이트 존재 검증
        Site site = findSiteById(siteId);
        siteRepository.delete(site);

        log.info("Site deleted successfully with siteId: {}", siteId);
    }

    /**
     * 사이트 패치 상태 초기화 (ADMIN 전용)
     *
     * <p>customer_site_version, customer_project(last_patched_*), patch_history 를 초기화한다.
     * patch_file 은 건드리지 않는다.
     *
     * @param siteId  초기화 대상 사이트 ID
     * @param requestorEmail 요청자 이메일 (로그용)
     * @return 각 테이블별 삭제/초기화 건수
     */
    @Transactional
    public SiteDto.ResetPatchStateResponse resetPatchState(Long siteId, String requestorEmail) {
        log.info("사이트 패치 상태 초기화 요청 - siteId: {}, 요청자: {}", siteId, requestorEmail);

        // 사이트 존재 검증
        findSiteById(siteId);

        // 1) customer_site_version 삭제
        long siteVersionCount = siteVersionRepository.countBySite_SiteId(siteId);
        siteVersionRepository.deleteAllBySite_SiteId(siteId);
        log.info("customer_site_version 삭제 완료 - siteId: {}, 건수: {}", siteId, siteVersionCount);

        // 2) customer_project last_patched_* 초기화
        List<SiteProject> siteProjects = siteProjectRepository.findAllBySite_SiteId(siteId);
        siteProjects.forEach(cp -> cp.updateLastPatchInfo(null, null));
        log.info("customer_project 초기화 완료 - siteId: {}, 건수: {}", siteId, siteProjects.size());

        // 3) patch_history 삭제 (건수 먼저 조회 후 삭제)
        long patchHistoryCount = patchHistoryRepository.countBySite_SiteId(siteId);
        patchHistoryRepository.deleteAllBySite_SiteId(siteId);
        log.info("patch_history 삭제 완료 - siteId: {}, 건수: {}, 요청자: {}",
                siteId, patchHistoryCount, requestorEmail);

        return new SiteDto.ResetPatchStateResponse(
                siteVersionCount,
                siteProjects.size(),
                patchHistoryCount
        );
    }

    // === Private Helper Methods ===

    /**
     * 사이트 조회 (존재하지 않으면 예외 발생)
     */
    private Site findSiteById(Long siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_NOT_FOUND));
    }

    /**
     * 사이트 ID로 프로젝트 정보 조회 (단일 프로젝트)
     */
    private SiteDto.ProjectInfo getProjectInfoBySiteId(Long siteId) {
        List<SiteProject> siteProjects = siteProjectRepository.findAllBySiteIdWithProject(siteId);
        if (siteProjects.isEmpty()) {
            return null;
        }
        // 첫 번째 프로젝트만 반환 (현재 사이트당 하나의 프로젝트만 사용)
        SiteProject cp = siteProjects.get(0);
        return new SiteDto.ProjectInfo(
                cp.getProject().getProjectId(),
                cp.getProject().getProjectName(),
                cp.getLastPatchedVersion(),
                cp.getLastPatchedAt()
        );
    }

    /**
     * 사이트 생성 시 프로젝트 연결 저장
     */
    private SiteDto.ProjectInfo saveSiteProject(Site site, String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND,
                        "프로젝트를 찾을 수 없습니다: " + projectId));

        SiteProject siteProject = SiteProject.create(site, project);
        siteProjectRepository.save(siteProject);

        return new SiteDto.ProjectInfo(
                project.getProjectId(),
                project.getProjectName(),
                null,
                null
        );
    }

    /**
     * Site 엔티티와 프로젝트 정보로 DetailResponse 생성
     */
    private SiteDto.DetailResponse toDetailResponseWithProject(Site site,
            SiteDto.ProjectInfo projectInfo) {
        boolean hasCustomVersion = releaseVersionRepository.existsBySite_SiteId(site.getSiteId());
        return new SiteDto.DetailResponse(
                site.getSiteId(),
                site.getSiteCode(),
                site.getSiteName(),
                site.getSiteCategory(),
                site.getDescription(),
                site.getIsActive(),
                hasCustomVersion,
                projectInfo,
                site.getCreatedAt(),
                site.getCreatedByEmail(),
                site.getCreator() != null ? site.getCreator().getAvatarStyle() : null,
                site.getCreator() != null ? site.getCreator().getAvatarSeed() : null,
                site.getCreator() == null,
                site.getUpdatedAt(),
                site.getUpdatedByEmail(),
                site.getUpdater() != null ? site.getUpdater().getAvatarStyle() : null,
                site.getUpdater() != null ? site.getUpdater().getAvatarSeed() : null,
                site.getUpdater() == null,
                site.getGlyphText(),
                site.getGlyphBackgroundColor()
        );
    }
}
