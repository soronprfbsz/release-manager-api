package com.ts.rm.domain.department.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ts.rm.domain.department.entity.DepartmentHierarchy;
import com.ts.rm.domain.department.entity.DepartmentHierarchyId;

/**
 * 부서 계층 구조 Repository
 */
public interface DepartmentHierarchyRepository extends JpaRepository<DepartmentHierarchy, DepartmentHierarchyId>,
        DepartmentHierarchyRepositoryCustom {

    /**
     * 특정 부서의 모든 하위 부서 계층 조회 (자기 자신 포함)
     */
    List<DepartmentHierarchy> findByAncestorDepartmentId(Long ancestorId);

    /**
     * 특정 부서의 직계 자식 부서만 조회 (depth=1)
     */
    List<DepartmentHierarchy> findByAncestorDepartmentIdAndDepth(Long ancestorId, Integer depth);

    /**
     * 특정 부서의 모든 상위 부서 계층 조회 (자기 자신 포함)
     */
    List<DepartmentHierarchy> findByDescendantDepartmentId(Long descendantId);

    /**
     * 특정 부서의 직계 부모 조회 (depth=1)
     */
    Optional<DepartmentHierarchy> findByDescendantDepartmentIdAndDepth(Long descendantId, Integer depth);

    /**
     * 특정 부서가 다른 부서의 조상인지 확인
     */
    boolean existsByAncestorDepartmentIdAndDescendantDepartmentId(Long ancestorId, Long descendantId);

    /**
     * 특정 부서의 직계 자식 수 조회
     */
    long countByAncestorDepartmentIdAndDepth(Long ancestorId, Integer depth);

    /**
     * 서브트리 노드와 서브트리 외부 조상 간의 계층 삭제 (부서 이동 시 기존 관계 제거용)
     * <p>
     * 서브트리 내부 관계(자기 참조 포함)는 유지된다.
     * 벌크 삭제는 영속성 컨텍스트를 우회하므로 flush/clear 를 자동 수행한다 — 삭제된 계층이
     * 컨텍스트에 남으면 같은 복합키로 새 계층을 save(merge) 할 때 UPDATE 로 flush 되어
     * StaleObjectStateException 이 발생한다.
     *
     * @param subtreeIds 이동 대상 부서와 모든 하위 부서 ID
     * @return 삭제된 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DepartmentHierarchy h "
            + "WHERE h.descendant.departmentId IN :subtreeIds "
            + "AND h.ancestor.departmentId NOT IN :subtreeIds")
    int deleteExternalAncestorRelationships(@Param("subtreeIds") List<Long> subtreeIds);
}
