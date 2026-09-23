package com.ts.rm.domain.department.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.department.dto.DepartmentDto;
import com.ts.rm.domain.department.entity.Department;
import com.ts.rm.domain.department.entity.DepartmentHierarchy;
import com.ts.rm.domain.department.mapper.DepartmentDtoMapper;
import com.ts.rm.domain.department.repository.DepartmentHierarchyRepository;
import com.ts.rm.domain.department.repository.DepartmentRepository;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private static final Long ROOT_DEPARTMENT_ID = 1L;

    private final DepartmentRepository departmentRepository;
    private final DepartmentHierarchyRepository hierarchyRepository;
    private final AccountRepository accountRepository;
    private final DepartmentDtoMapper mapper;

    /**
     * 전체 부서 목록 조회 (flat)
     */
    public List<DepartmentDto.Response> getAllDepartments() {
        List<Department> departments = departmentRepository.findAllByOrderByDepartmentNameAsc();
        return mapper.toResponseList(departments);
    }

    /**
     * 부서 상세 조회
     */
    public DepartmentDto.DetailResponse getDepartmentById(Long departmentId) {
        Department department = findDepartmentById(departmentId);

        // 부모 정보 조회
        Optional<DepartmentHierarchy> parentHierarchy =
                hierarchyRepository.findByDescendantDepartmentIdAndDepth(departmentId, 1);

        Long parentId = parentHierarchy.map(h -> h.getAncestor().getDepartmentId()).orElse(null);
        String parentName = parentHierarchy.map(h -> h.getAncestor().getDepartmentName()).orElse(null);

        // 깊이 계산 (루트로부터의 거리)
        List<DepartmentHierarchy> ancestors = hierarchyRepository.findByDescendantDepartmentId(departmentId);
        int depth = ancestors.stream()
                .mapToInt(DepartmentHierarchy::getDepth)
                .max()
                .orElse(0);

        // 직계 자식 수
        long childCount = hierarchyRepository.countByAncestorDepartmentIdAndDepth(departmentId, 1);

        // 소속 계정 수
        long accountCount = accountRepository.countByDepartmentDepartmentId(departmentId);

        return new DepartmentDto.DetailResponse(
                department.getDepartmentId(),
                department.getDepartmentName(),
                department.getDepartmentType(),
                department.getDescription(),
                department.getSortOrder(),
                parentId,
                parentName,
                depth,
                childCount,
                accountCount
        );
    }

    /**
     * 부서 트리 구조 조회
     */
    public List<DepartmentDto.TreeResponse> getDepartmentTree() {
        // 모든 부서와 계층 정보 조회
        List<Department> allDepartments = departmentRepository.findAll();
        List<DepartmentHierarchy> allHierarchies = hierarchyRepository.findAll();

        // 부서별 계정 수 조회
        Map<Long, Long> accountCounts = new HashMap<>();
        for (Department dept : allDepartments) {
            accountCounts.put(dept.getDepartmentId(),
                    accountRepository.countByDepartmentDepartmentId(dept.getDepartmentId()));
        }

        // 부서 ID → 부서 맵
        Map<Long, Department> deptMap = new HashMap<>();
        for (Department dept : allDepartments) {
            deptMap.put(dept.getDepartmentId(), dept);
        }

        // 부모 → 자식 관계 맵 (depth=1만)
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (DepartmentHierarchy h : allHierarchies) {
            if (h.getDepth() == 1) {
                childrenMap
                        .computeIfAbsent(h.getAncestor().getDepartmentId(), k -> new ArrayList<>())
                        .add(h.getDescendant().getDepartmentId());
            }
        }

        // 루트 부서 찾기 (depth=1인 조상이 없는 부서)
        List<Long> rootIds = new ArrayList<>();
        for (Department dept : allDepartments) {
            boolean hasParent = allHierarchies.stream()
                    .anyMatch(h -> h.getDescendant().getDepartmentId().equals(dept.getDepartmentId())
                            && h.getDepth() == 1);
            if (!hasParent) {
                rootIds.add(dept.getDepartmentId());
            }
        }

        // 트리 구조 생성
        List<DepartmentDto.TreeResponse> result = new ArrayList<>();
        for (Long rootId : rootIds) {
            result.add(buildTreeNode(rootId, 0, deptMap, childrenMap, accountCounts));
        }

        return result;
    }

    private DepartmentDto.TreeResponse buildTreeNode(Long deptId, int depth,
            Map<Long, Department> deptMap,
            Map<Long, List<Long>> childrenMap,
            Map<Long, Long> accountCounts) {

        Department dept = deptMap.get(deptId);
        List<Long> childIds = childrenMap.getOrDefault(deptId, new ArrayList<>());

        // sortOrder로 정렬하여 children 생성
        List<DepartmentDto.TreeResponse> children = childIds.stream()
                .map(childId -> buildTreeNode(childId, depth + 1, deptMap, childrenMap, accountCounts))
                .sorted((a, b) -> {
                    int orderA = a.sortOrder() != null ? a.sortOrder() : 0;
                    int orderB = b.sortOrder() != null ? b.sortOrder() : 0;
                    return Integer.compare(orderA, orderB);
                })
                .toList();

        return new DepartmentDto.TreeResponse(
                dept.getDepartmentId(),
                dept.getDepartmentName(),
                dept.getDepartmentType(),
                dept.getDescription(),
                dept.getSortOrder(),
                depth,
                accountCounts.getOrDefault(deptId, 0L),
                children
        );
    }

    /**
     * 직계 하위 부서 목록 조회
     */
    public List<DepartmentDto.Response> getChildDepartments(Long departmentId) {
        findDepartmentById(departmentId); // 존재 확인

        List<DepartmentHierarchy> children =
                hierarchyRepository.findByAncestorDepartmentIdAndDepth(departmentId, 1);

        return children.stream()
                .map(h -> mapper.toResponse(h.getDescendant()))
                .toList();
    }

    /**
     * 모든 하위 부서 목록 조회 (자기 자신 제외)
     */
    public List<DepartmentDto.Response> getDescendantDepartments(Long departmentId) {
        findDepartmentById(departmentId); // 존재 확인

        List<DepartmentHierarchy> descendants =
                hierarchyRepository.findByAncestorDepartmentId(departmentId);

        return descendants.stream()
                .filter(h -> h.getDepth() > 0) // 자기 자신 제외
                .map(h -> mapper.toResponse(h.getDescendant()))
                .toList();
    }

    /**
     * 부서 생성
     */
    @Transactional
    public DepartmentDto.Response createDepartment(DepartmentDto.CreateRequest request) {
        // 부서명 중복 체크
        if (departmentRepository.existsByDepartmentName(request.departmentName())) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NAME_CONFLICT);
        }

        // 상위 부서 결정 (null이면 루트 부서 하위)
        Long parentId = request.parentDepartmentId() != null
                ? request.parentDepartmentId()
                : ROOT_DEPARTMENT_ID;

        Department parentDepartment = findDepartmentById(parentId);

        // 새 부서 생성 (sortOrder는 나중에 설정)
        Department newDepartment = Department.builder()
                .departmentName(request.departmentName())
                .departmentType(request.departmentType())
                .description(request.description())
                .sortOrder(0)
                .build();

        Department saved = departmentRepository.save(newDepartment);

        // 계층 구조 생성
        createHierarchyForNewDepartment(saved, parentDepartment);

        // sortOrder 설정: 지정된 경우 형제 재정렬, 미지정 시 맨 뒤에 추가
        if (request.sortOrder() != null) {
            reorderSiblings(parentId, saved.getDepartmentId(), request.sortOrder());
        } else {
            int nextOrder = getNextSortOrder(parentId);
            saved.update(null, null, null, nextOrder);
        }

        log.info("부서 생성 완료: {} (상위: {}, sortOrder: {})",
                saved.getDepartmentName(), parentDepartment.getDepartmentName(), saved.getSortOrder());

        return mapper.toResponse(saved);
    }

    private void createHierarchyForNewDepartment(Department newDept, Department parent) {
        // 1. 자기 참조 (depth=0)
        hierarchyRepository.save(DepartmentHierarchy.createSelfReference(newDept));

        // 2. 부모의 모든 조상과 연결
        List<DepartmentHierarchy> parentAncestors =
                hierarchyRepository.findByDescendantDepartmentId(parent.getDepartmentId());

        for (DepartmentHierarchy ancestorHierarchy : parentAncestors) {
            DepartmentHierarchy newHierarchy = DepartmentHierarchy.createWithDepth(
                    ancestorHierarchy.getAncestor(),
                    newDept,
                    ancestorHierarchy.getDepth() + 1
            );
            hierarchyRepository.save(newHierarchy);
        }
    }

    /**
     * 부서 수정
     */
    @Transactional
    public DepartmentDto.Response updateDepartment(Long departmentId, DepartmentDto.UpdateRequest request) {
        Department department = findDepartmentById(departmentId);

        // 부서명 중복 체크 (자기 자신 제외)
        if (request.departmentName() != null &&
            departmentRepository.existsByDepartmentNameAndDepartmentIdNot(request.departmentName(), departmentId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NAME_CONFLICT);
        }

        // sortOrder 변경 시 형제 부서들 재정렬
        if (request.sortOrder() != null) {
            // 부모 부서 ID 조회
            Optional<DepartmentHierarchy> parentHierarchy =
                    hierarchyRepository.findByDescendantDepartmentIdAndDepth(departmentId, 1);
            Long parentId = parentHierarchy
                    .map(h -> h.getAncestor().getDepartmentId())
                    .orElse(ROOT_DEPARTMENT_ID);

            reorderSiblings(parentId, departmentId, request.sortOrder());
        }

        // sortOrder 외 필드 업데이트 (sortOrder는 reorderSiblings에서 처리됨)
        department.update(request.departmentName(), request.departmentType(), request.description(), null);

        log.info("부서 수정 완료: {}", department.getDepartmentName());

        return mapper.toResponse(department);
    }

    /**
     * 부서 삭제
     * - 하위 부서가 있으면 삭제 불가
     * - 소속 계정이 있으면 삭제 불가
     * - 루트 부서는 삭제 불가
     */
    @Transactional
    public void deleteDepartment(Long departmentId) {
        Department department = findDepartmentById(departmentId);

        // 루트 부서 삭제 방지
        if (departmentId.equals(ROOT_DEPARTMENT_ID)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_ROOT_CANNOT_DELETE);
        }

        // 하위 부서 존재 확인
        long childCount = hierarchyRepository.countByAncestorDepartmentIdAndDepth(departmentId, 1);
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_CHILDREN);
        }

        // 소속 계정 존재 확인
        long accountCount = accountRepository.countByDepartmentDepartmentId(departmentId);
        if (accountCount > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_ACCOUNTS);
        }

        // 계층 데이터 삭제 (CASCADE로 자동 삭제되지만 명시적으로)
        hierarchyRepository.deleteByDepartmentId(departmentId);

        // 부서 삭제
        departmentRepository.delete(department);

        log.info("부서 삭제 완료: {}", department.getDepartmentName());
    }

    /**
     * 부서 이동
     * - 자기 자신의 하위로 이동 불가
     * - 새 부모가 null이면 루트 부서 하위로 이동
     * - sortOrder 변경도 처리
     * - 동일/다른 depth 모두 sort_order를 1부터 재정렬
     */
    @Transactional
    public DepartmentDto.Response moveDepartment(Long departmentId, DepartmentDto.MoveRequest request) {
        Department department = findDepartmentById(departmentId);

        // 루트 부서는 이동 불가
        if (departmentId.equals(ROOT_DEPARTMENT_ID)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_ROOT_CANNOT_DELETE);
        }

        // 새 부모 결정
        Long newParentId = request.newParentId() != null ? request.newParentId() : ROOT_DEPARTMENT_ID;
        Department newParent = findDepartmentById(newParentId);

        // 자기 자신의 하위로 이동 방지
        if (hierarchyRepository.existsByAncestorDepartmentIdAndDescendantDepartmentId(departmentId, newParentId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_CANNOT_MOVE_TO_DESCENDANT);
        }

        // 현재 부모 ID 조회
        Optional<DepartmentHierarchy> currentParentHierarchy =
                hierarchyRepository.findByDescendantDepartmentIdAndDepth(departmentId, 1);
        Long currentParentId = currentParentHierarchy
                .map(h -> h.getAncestor().getDepartmentId())
                .orElse(null);

        // 부모가 변경되는 경우에만 계층 구조 업데이트
        boolean parentChanged = !newParentId.equals(currentParentId);
        if (parentChanged) {
            // 이동 대상 서브트리(자기 포함)와 서브트리 내 상대 깊이 — 관계 삭제 전에 조회해야 한다
            Map<Long, Integer> relativeDepths = new HashMap<>();
            for (DepartmentHierarchy h : hierarchyRepository.findByAncestorDepartmentId(departmentId)) {
                relativeDepths.put(h.getDescendant().getDepartmentId(), h.getDepth());
            }
            List<Long> subtreeIds = new ArrayList<>(relativeDepths.keySet());

            // 서브트리 외부 조상과의 관계만 삭제 (서브트리 내부 관계는 유지).
            // 벌크 삭제 후 영속성 컨텍스트가 비워지므로(clearAutomatically) 이후 사용할 엔티티는 재조회한다
            hierarchyRepository.deleteExternalAncestorRelationships(subtreeIds);
            department = findDepartmentById(departmentId);

            // 새 부모의 조상들과 연결
            List<DepartmentHierarchy> newParentAncestors =
                    hierarchyRepository.findByDescendantDepartmentId(newParentId);

            for (Long descId : subtreeIds) {
                Department descendant = findDepartmentById(descId);
                int relativeDepth = relativeDepths.get(descId);

                for (DepartmentHierarchy ancestorHierarchy : newParentAncestors) {
                    DepartmentHierarchy newHierarchy = DepartmentHierarchy.createWithDepth(
                            ancestorHierarchy.getAncestor(),
                            descendant,
                            ancestorHierarchy.getDepth() + 1 + relativeDepth
                    );
                    hierarchyRepository.save(newHierarchy);
                }
            }

            log.info("부서 이동 완료: {} → {}", department.getDepartmentName(), newParent.getDepartmentName());

            // 원래 부모의 형제들 sort_order 정규화 (1부터 순차적으로)
            if (currentParentId != null) {
                normalizeSiblingSortOrder(currentParentId, null);
            }
        }

        // sortOrder 재정렬 (부모 변경 여부와 무관하게 항상 처리)
        if (request.sortOrder() != null) {
            reorderSiblings(newParentId, departmentId, request.sortOrder());
            log.info("부서 정렬 순서 변경: {} → {}", department.getDepartmentName(), request.sortOrder());
        } else if (parentChanged) {
            // 부모가 변경되었는데 sortOrder가 없으면 맨 뒤에 추가 후 정규화
            int nextOrder = getNextSortOrder(newParentId);
            department.update(null, null, null, nextOrder);
            normalizeSiblingSortOrder(newParentId, null);
        }

        return mapper.toResponse(department);
    }

    private Department findDepartmentById(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    /**
     * 형제 부서들의 sortOrder 재정렬
     * - 타겟 부서를 지정된 위치에 삽입
     * - 모든 형제를 1부터 순차적으로 재정렬
     *
     * @param parentId 부모 부서 ID
     * @param targetDeptId 이동할 부서 ID
     * @param targetPosition 삽입할 위치 (1부터 시작)
     */
    private void reorderSiblings(Long parentId, Long targetDeptId, int targetPosition) {
        // 같은 부모의 직계 자식들(형제들) 조회
        List<DepartmentHierarchy> siblings = hierarchyRepository.findByAncestorDepartmentIdAndDepth(parentId, 1);

        // 타겟 부서 제외한 형제들을 sortOrder 순으로 정렬
        List<Department> orderedSiblings = siblings.stream()
                .map(DepartmentHierarchy::getDescendant)
                .filter(d -> !d.getDepartmentId().equals(targetDeptId))
                .sorted((a, b) -> {
                    int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                    int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                    return Integer.compare(orderA, orderB);
                })
                .collect(java.util.stream.Collectors.toList());

        Department targetDept = findDepartmentById(targetDeptId);

        // 타겟 위치에 삽입 (1-indexed → 0-indexed 변환, 범위 체크)
        int insertIndex = Math.max(0, Math.min(targetPosition - 1, orderedSiblings.size()));
        orderedSiblings.add(insertIndex, targetDept);

        // 모두 1부터 순서대로 재정렬
        for (int i = 0; i < orderedSiblings.size(); i++) {
            orderedSiblings.get(i).update(null, null, null, i + 1);
        }

        log.debug("부서 정렬 순서 재정렬 완료 - parentId: {}, targetDeptId: {}, targetPosition: {}, 총 형제 수: {}",
                parentId, targetDeptId, targetPosition, orderedSiblings.size());
    }

    /**
     * 형제 부서들의 sortOrder 정규화 (1부터 순차적으로)
     * - 특정 부서 제외 가능
     *
     * @param parentId 부모 부서 ID
     * @param excludeDeptId 제외할 부서 ID (null이면 모두 포함)
     */
    private void normalizeSiblingSortOrder(Long parentId, Long excludeDeptId) {
        List<DepartmentHierarchy> siblings = hierarchyRepository.findByAncestorDepartmentIdAndDepth(parentId, 1);

        List<Department> orderedSiblings = siblings.stream()
                .map(DepartmentHierarchy::getDescendant)
                .filter(d -> excludeDeptId == null || !d.getDepartmentId().equals(excludeDeptId))
                .sorted((a, b) -> {
                    int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                    int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                    return Integer.compare(orderA, orderB);
                })
                .collect(java.util.stream.Collectors.toList());

        // 1부터 순서대로 재정렬
        for (int i = 0; i < orderedSiblings.size(); i++) {
            orderedSiblings.get(i).update(null, null, null, i + 1);
        }

        log.debug("형제 부서 정렬 순서 정규화 완료 - parentId: {}, 총 형제 수: {}", parentId, orderedSiblings.size());
    }

    /**
     * 새 부서 생성 시 다음 sortOrder 반환
     * - 같은 부모의 형제들 중 가장 큰 sortOrder + 1
     * - 형제가 없으면 1 반환
     *
     * @param parentId 부모 부서 ID
     * @return 다음 sortOrder
     */
    private int getNextSortOrder(Long parentId) {
        List<DepartmentHierarchy> siblings = hierarchyRepository.findByAncestorDepartmentIdAndDepth(parentId, 1);

        return siblings.stream()
                .map(h -> h.getDescendant().getSortOrder())
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }
}
