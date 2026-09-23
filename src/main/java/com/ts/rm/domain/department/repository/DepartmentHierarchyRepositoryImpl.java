package com.ts.rm.domain.department.repository;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.department.entity.Department;
import com.ts.rm.domain.department.entity.QDepartment;
import com.ts.rm.domain.department.entity.QDepartmentHierarchy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * DepartmentHierarchy Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 부서 계층 구조 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class DepartmentHierarchyRepositoryImpl implements DepartmentHierarchyRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QDepartmentHierarchy departmentHierarchy = QDepartmentHierarchy.departmentHierarchy;
    private static final QDepartmentHierarchy subDepartmentHierarchy = new QDepartmentHierarchy("subDepartmentHierarchy");
    private static final QDepartment department = QDepartment.department;

    @Override
    public long deleteByDepartmentId(Long departmentId) {
        return queryFactory
                .delete(departmentHierarchy)
                .where(
                        departmentHierarchy.ancestor.departmentId.eq(departmentId)
                                .or(departmentHierarchy.descendant.departmentId.eq(departmentId))
                )
                .execute();
    }

    @Override
    public List<Long> findDescendantIds(Long ancestorId) {
        return queryFactory
                .select(departmentHierarchy.descendant.departmentId)
                .from(departmentHierarchy)
                .where(
                        departmentHierarchy.ancestor.departmentId.eq(ancestorId)
                                .and(departmentHierarchy.depth.gt(0))
                )
                .fetch();
    }

    @Override
    public List<Department> findRootDepartments() {
        // 루트 부서: 자기 참조(depth=0)만 있고, depth=1인 관계에서 descendant로 등장하지 않는 부서
        return queryFactory
                .select(departmentHierarchy.descendant)
                .from(departmentHierarchy)
                .where(
                        departmentHierarchy.ancestor.eq(departmentHierarchy.descendant)
                                .and(departmentHierarchy.descendant.departmentId.notIn(
                                        JPAExpressions
                                                .select(subDepartmentHierarchy.descendant.departmentId)
                                                .from(subDepartmentHierarchy)
                                                .where(subDepartmentHierarchy.depth.eq(1))
                                ))
                )
                .fetch();
    }
}
