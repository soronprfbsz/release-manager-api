package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.Patch;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Patch Repository Custom Interface
 *
 * <p>QueryDSL을 사용한 복잡한 쿼리 구현을 위한 인터페이스
 */
public interface PatchRepositoryCustom {

    /**
     * 프로젝트별 릴리즈 타입별 최근 N개 패치 조회
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param limit       조회 개수
     * @return 최근 패치 목록
     */
    List<Patch> findRecentByProjectIdAndReleaseType(String projectId, String releaseType, int limit);

    /**
     * 패치 목록 조회 (다중 필터링)
     *
     * @param projectId    프로젝트 ID (null이면 전체)
     * @param releaseType  릴리즈 타입 (STANDARD/CUSTOM, null이면 전체)
     * @param siteCode 사이트 코드 (null이면 전체)
     * @param pageable     페이징 정보
     * @return 패치 목록 페이지
     */
    Page<Patch> findAllWithFilters(String projectId, String releaseType, String siteCode, Pageable pageable);

    /**
     * 주어진 패치명 목록 중 실제 존재하는 패치명 조회
     *
     * <p>patch_history와 patch_file 비교 시 사용
     * <p>존재하지 않는 패치명은 파일이 삭제된 것으로 간주
     *
     * @param patchNames 확인할 패치명 목록
     * @return 실제 존재하는 패치명 Set
     */
    Set<String> findExistingPatchNames(Set<String> patchNames);
}
