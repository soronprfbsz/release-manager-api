package com.ts.rm.domain.site.repository;

import com.ts.rm.domain.site.entity.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Site Repository Custom
 *
 * <p>QueryDSL을 사용한 복잡한 사이트 쿼리
 */
public interface SiteRepositoryCustom {

    /**
     * 사이트 목록 페이징 조회 (프로젝트 정보 포함 정렬 지원)
     *
     * <p>SiteProject 테이블과 LEFT JOIN하여 lastPatchedVersion, lastPatchedAt 정렬 지원
     * <p>hasCustomVersion 서브쿼리 정렬 지원 (커스텀 버전 존재 여부)
     *
     * @param projectId 프로젝트 ID (null이면 전체)
     * @param isActive  활성화 여부 필터 (true: 활성화만, false: 비활성화만, null: 전체)
     * @param keyword   사이트명 검색 키워드
     * @param pageable  페이징 정보 (sort에 "project.projectName", "lastPatchedVersion", "lastPatchedAt", "hasCustomVersion" 사용 가능)
     * @return 사이트 페이지
     */
    Page<Site> findAllWithProjectInfo(String projectId, Boolean isActive, String keyword, Pageable pageable);
}
