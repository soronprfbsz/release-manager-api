package com.ts.rm.domain.department.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ts.rm.config.AbstractTestBase;
import com.ts.rm.config.TestQueryDslConfig;
import com.ts.rm.domain.department.dto.DepartmentDto;
import com.ts.rm.domain.department.entity.Department;
import com.ts.rm.domain.department.entity.DepartmentHierarchy;
import com.ts.rm.domain.department.repository.DepartmentHierarchyRepository;
import com.ts.rm.domain.department.repository.DepartmentRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestQueryDslConfig.class)
@Transactional
@DisplayName("DepartmentService 부서 이동 테스트")
class DepartmentServiceMoveTest extends AbstractTestBase {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DepartmentHierarchyRepository hierarchyRepository;

    @Autowired
    private EntityManager entityManager;

    private Department root;
    private Department division;
    private Department group;
    private Department team;
    private Department otherDivision;

    /**
     * root
     * ├── division (본부)
     * │   ├── group (그룹)
     * │   └── team (팀)
     * └── otherDivision
     */
    @BeforeEach
    void setUp() {
        root = saveDepartment("루트", null, 1);
        division = saveDepartment("본부", root, 1);
        otherDivision = saveDepartment("다른본부", root, 2);
        group = saveDepartment("그룹", division, 1);
        team = saveDepartment("팀", division, 2);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("같은 본부 내 형제 그룹 하위로 이동 - 공통 조상과의 관계 depth 가 갱신된다")
    void moveDepartment_intoSiblingUnderSameAncestor() {
        departmentService.moveDepartment(team.getDepartmentId(),
                new DepartmentDto.MoveRequest(group.getDepartmentId(), null));
        entityManager.flush();
        entityManager.clear();

        assertThat(ancestorDepths(team)).containsExactlyInAnyOrderEntriesOf(Map.of(
                team.getDepartmentId(), 0,
                group.getDepartmentId(), 1,
                division.getDepartmentId(), 2,
                root.getDepartmentId(), 3));
        assertThat(departmentRepository.findById(team.getDepartmentId()).orElseThrow().getSortOrder())
                .isEqualTo(1);
        // 원래 부모(본부)의 남은 형제 sort_order 정규화
        assertThat(departmentRepository.findById(group.getDepartmentId()).orElseThrow().getSortOrder())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("sortOrder 지정 이동 - 같은 본부 내 형제 그룹 하위로 이동해도 성공한다")
    void moveDepartment_withSortOrder_intoSiblingUnderSameAncestor() {
        departmentService.moveDepartment(team.getDepartmentId(),
                new DepartmentDto.MoveRequest(group.getDepartmentId(), 1));
        entityManager.flush();
        entityManager.clear();

        assertThat(ancestorDepths(team)).containsEntry(group.getDepartmentId(), 1)
                .containsEntry(division.getDepartmentId(), 2);
    }

    @Test
    @DisplayName("하위 부서가 있는 부서 이동 - 서브트리 내부 관계는 유지되고 외부 조상만 교체된다")
    void moveDepartment_withSubtree_keepsInternalRelationships() {
        Department subTeam = saveDepartment("하위팀", group, 1);
        entityManager.flush();
        entityManager.clear();

        departmentService.moveDepartment(group.getDepartmentId(),
                new DepartmentDto.MoveRequest(otherDivision.getDepartmentId(), null));
        entityManager.flush();
        entityManager.clear();

        assertThat(ancestorDepths(group)).containsExactlyInAnyOrderEntriesOf(Map.of(
                group.getDepartmentId(), 0,
                otherDivision.getDepartmentId(), 1,
                root.getDepartmentId(), 2));
        assertThat(ancestorDepths(subTeam)).containsExactlyInAnyOrderEntriesOf(Map.of(
                subTeam.getDepartmentId(), 0,
                group.getDepartmentId(), 1,
                otherDivision.getDepartmentId(), 2,
                root.getDepartmentId(), 3));
    }

    private Map<Long, Integer> ancestorDepths(Department department) {
        List<DepartmentHierarchy> ancestors =
                hierarchyRepository.findByDescendantDepartmentId(department.getDepartmentId());
        return ancestors.stream().collect(Collectors.toMap(
                h -> h.getAncestor().getDepartmentId(), DepartmentHierarchy::getDepth));
    }

    private Department saveDepartment(String name, Department parent, int sortOrder) {
        Department department = departmentRepository.save(Department.builder()
                .departmentName(name)
                .sortOrder(sortOrder)
                .build());
        hierarchyRepository.save(DepartmentHierarchy.createSelfReference(department));
        if (parent != null) {
            for (DepartmentHierarchy ancestor : hierarchyRepository.findByDescendantDepartmentId(
                    parent.getDepartmentId())) {
                hierarchyRepository.save(DepartmentHierarchy.createWithDepth(
                        ancestor.getAncestor(), department, ancestor.getDepth() + 1));
            }
        }
        return department;
    }
}
