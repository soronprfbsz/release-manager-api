package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.Patch;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Patch Repository
 *
 * <p>누적 패치 조회 및 관리를 위한 Repository
 * <p>Spring Data JPA 메서드 네이밍으로 CRUD 처리
 * <p>복잡한 쿼리(LIMIT 포함 등)는 PatchRepositoryCustom에서 QueryDSL로 처리
 * <p>업데이트는 JPA Dirty Checking 사용 (Service에서 엔티티 조회 후 setter 호출)
 */
public interface PatchRepository extends JpaRepository<Patch, Long>, PatchRepositoryCustom {

    /**
     * 패치 페이징 조회 (최신순)
     *
     * @param pageable 페이징 정보
     * @return 패치 페이지
     */
    Page<Patch> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 릴리즈 타입별 패치 페이징 조회 (최신순)
     *
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param pageable    페이징 정보
     * @return 패치 페이지
     */
    Page<Patch> findAllByReleaseTypeOrderByCreatedAtDesc(String releaseType, Pageable pageable);

    /**
     * 프로젝트별 패치 페이징 조회 (최신순)
     *
     * @param projectId 프로젝트 ID
     * @param pageable  페이징 정보
     * @return 패치 페이지
     */
    Page<Patch> findAllByProject_ProjectIdOrderByCreatedAtDesc(String projectId, Pageable pageable);

    /**
     * 프로젝트 및 릴리즈 타입별 패치 페이징 조회 (최신순)
     *
     * @param projectId   프로젝트 ID
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param pageable    페이징 정보
     * @return 패치 페이지
     */
    Page<Patch> findAllByProject_ProjectIdAndReleaseTypeOrderByCreatedAtDesc(
            String projectId, String releaseType, Pageable pageable);

    /**
     * 릴리즈 타입별 패치 조회 (최신순) - 비페이징
     *
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @return 패치 목록
     */
    List<Patch> findAllByReleaseTypeOrderByCreatedAtDesc(String releaseType);

    /**
     * 고객사별 패치 조회 (최신순)
     *
     * @param customerId 고객사 ID
     * @return 패치 목록
     */
    List<Patch> findAllByCustomer_CustomerIdOrderByCreatedAtDesc(Long customerId);

    /**
     * 버전 범위로 패치 조회
     *
     * @param releaseType 릴리즈 타입 (STANDARD/CUSTOM)
     * @param fromVersion 시작 버전
     * @param toVersion   종료 버전
     * @return 패치 목록
     */
    List<Patch> findByReleaseTypeAndFromVersionAndToVersion(String releaseType,
            String fromVersion, String toVersion);

    /**
     * 패치명 중복 여부 — 자동 패치명 생성 시 suffix increment 판단에 사용
     */
    boolean existsByPatchName(String patchName);
}
