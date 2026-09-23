package com.ts.rm.domain.department.repository;

import com.ts.rm.domain.department.entity.Department;
import java.util.List;

/**
 * DepartmentHierarchy Repository Custom Interface
 *
 * <p>QueryDSL을 사용한 부서 계층 구조 쿼리 인터페이스
 */
public interface DepartmentHierarchyRepositoryCustom {

    /**
     * 특정 부서 관련 모든 계층 데이터 삭제 (조상 또는 후손으로 참조되는 경우)
     *
     * @param departmentId 부서 ID
     * @return 삭제된 행 수
     */
    long deleteByDepartmentId(Long departmentId);

    /**
     * 특정 부서의 모든 하위 부서 ID 목록 조회 (자기 자신 제외)
     *
     * @param ancestorId 조상 부서 ID
     * @return 하위 부서 ID 목록
     */
    List<Long> findDescendantIds(Long ancestorId);

    /**
     * 루트 부서 조회 (다른 부서의 자식이 아닌 부서)
     *
     * @return 루트 부서 목록
     */
    List<Department> findRootDepartments();
}
