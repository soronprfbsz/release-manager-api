package com.ts.rm.domain.site.repository;

import com.ts.rm.domain.site.entity.SiteProject;
import com.ts.rm.domain.site.entity.SiteProjectId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SiteProject Repository
 *
 * <p>사이트-프로젝트 매핑 정보 조회 및 관리
 */
public interface SiteProjectRepository extends JpaRepository<SiteProject, SiteProjectId>,
        SiteProjectRepositoryCustom {

    /**
     * 사이트 ID로 프로젝트 매핑 목록 조회
     *
     * @param siteId 사이트 ID
     * @return SiteProject 목록
     */
    List<SiteProject> findAllBySite_SiteId(Long siteId);

    /**
     * 프로젝트 ID로 사이트 매핑 목록 조회
     *
     * @param projectId 프로젝트 ID
     * @return SiteProject 목록
     */
    List<SiteProject> findAllByProject_ProjectId(String projectId);

    /**
     * 사이트 ID와 프로젝트 ID로 매핑 조회
     *
     * @param siteId 사이트 ID
     * @param projectId  프로젝트 ID
     * @return SiteProject
     */
    Optional<SiteProject> findBySite_SiteIdAndProject_ProjectId(Long siteId, String projectId);

    /**
     * 사이트의 특정 프로젝트 매핑 존재 여부 확인
     *
     * @param siteId 사이트 ID
     * @param projectId  프로젝트 ID
     * @return 존재 여부
     */
    boolean existsBySite_SiteIdAndProject_ProjectId(Long siteId, String projectId);
}
