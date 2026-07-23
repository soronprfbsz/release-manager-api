package com.ts.rm.domain.site.repository;

import com.ts.rm.domain.site.entity.SiteVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SiteVersion Repository
 *
 * <p>사이트별 컴포넌트 버전 조회 및 저장
 */
public interface SiteVersionRepository extends JpaRepository<SiteVersion, Long> {

    /**
     * 사이트 + 프로젝트 + 컴포넌트 (+ engineName) 로 단건 조회 (upsert 용).
     *
     * <p>BASE/WEB 은 engineName=null 로 호출. ENGINE 은 엔진명을 전달.
     * Spring Data JPA 의 ...IsNull/...Equals 분기는 메서드 별도 정의로 표현한다.
     *
     * @param siteId 사이트 ID
     * @param projectId  프로젝트 ID
     * @param component  컴포넌트 (BASE/WEB/ENGINE)
     * @param engineName 엔진명 (ENGINE 일 때만)
     * @return 존재하면 Optional 에 포함
     */
    Optional<SiteVersion> findBySite_SiteIdAndProject_ProjectIdAndComponentAndEngineName(
            Long siteId, String projectId, String component, String engineName);

    /**
     * engineName = NULL 단건 조회 (BASE/WEB 용).
     */
    Optional<SiteVersion> findBySite_SiteIdAndProject_ProjectIdAndComponentAndEngineNameIsNull(
            Long siteId, String projectId, String component);

    /**
     * 사이트 + 프로젝트의 모든 컴포넌트 버전 목록 조회 (UI 표시용).
     *
     * @param siteId 사이트 ID
     * @param projectId  프로젝트 ID
     * @return 컴포넌트 버전 목록 (최대 3건: BASE/WEB/ENGINE)
     */
    List<SiteVersion> findAllBySite_SiteIdAndProject_ProjectIdOrderByComponent(
            Long siteId, String projectId);

    /**
     * 사이트의 사이트 버전 건수 조회 (초기화 전 카운트용).
     *
     * @param siteId 사이트 ID
     * @return 건수
     */
    long countBySite_SiteId(Long siteId);

    /**
     * 사이트의 모든 사이트 버전 삭제 (사이트 초기화용).
     *
     * @param siteId 사이트 ID
     */
    void deleteAllBySite_SiteId(Long siteId);

    /**
     * 사이트 + 프로젝트의 모든 사이트 버전 삭제 (이력 삭제 후 재계산용).
     *
     * @param siteId 사이트 ID
     * @param projectId  프로젝트 ID
     */
    void deleteAllBySite_SiteIdAndProject_ProjectId(Long siteId, String projectId);
}
