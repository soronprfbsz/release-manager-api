package com.ts.rm.domain.account.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.account.entity.QAccount;
import com.ts.rm.domain.account.enums.AccountRole;
import com.ts.rm.domain.account.enums.AccountStatus;
import com.ts.rm.domain.common.entity.QCode;
import com.ts.rm.domain.department.entity.QDepartment;
import com.ts.rm.global.querydsl.QuerydslPaginationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Account Repository Custom Implementation
 *
 * <p>QueryDSL을 사용한 복잡한 계정 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepositoryCustom {

    private static final String POSITION_CODE_TYPE = "POSITION";

    private final JPAQueryFactory queryFactory;

    private static final QAccount account = QAccount.account;
    private static final QCode positionCode = new QCode("positionCode");
    private static final QDepartment department = QDepartment.department;

    @Override
    public Page<Account> findAllWithFilters(String status, List<Long> departmentIds, Long primaryDepartmentId,
                                            String departmentType, boolean unassigned, String keyword, Pageable pageable) {
        // 1. 기본 쿼리 생성 (직급/부서 정렬을 위해 code, department 테이블 left join)
        JPAQuery<Account> contentQuery = queryFactory
                .selectFrom(account)
                .leftJoin(account.department, department)
                .leftJoin(positionCode)
                    .on(positionCode.codeTypeId.eq(POSITION_CODE_TYPE)
                            .and(positionCode.codeId.eq(account.position)))
                .where(
                        statusCondition(status),
                        departmentCondition(departmentIds, unassigned),
                        departmentTypeCondition(departmentType),
                        keywordCondition(keyword)
                );

        // 2. Count 쿼리 생성
        JPAQuery<Long> countQuery = queryFactory
                .select(account.count())
                .from(account)
                .where(
                        statusCondition(status),
                        departmentCondition(departmentIds, unassigned),
                        departmentTypeCondition(departmentType),
                        keywordCondition(keyword)
                );

        // 3. 정렬 필드 매핑 정의
        Map<String, com.querydsl.core.types.Expression<?>> sortMapping = Map.of(
                "accountId", account.accountId,
                "email", account.email,
                "accountName", account.accountName,
                "phone", account.phone,
                "position", positionCode.sortOrder,  // 직급 순서(code.sort_order) 기준 정렬
                "departmentName", department.departmentName,  // 부서명 기준 정렬
                "role", account.role,
                "status", account.status,
                "lastLoginAt", account.lastLoginAt,
                "createdAt", account.createdAt
        );

        // 4. 우선 정렬 조건 생성 (primaryDepartmentId가 있으면 해당 부서 계정이 먼저 정렬됨)
        List<OrderSpecifier<?>> additionalOrders = new ArrayList<>();
        if (primaryDepartmentId != null) {
            // 1차 정렬: 요청한 부서 우선
            NumberExpression<Integer> departmentPriority = new CaseBuilder()
                    .when(account.department.departmentId.eq(primaryDepartmentId)).then(0)
                    .otherwise(1);
            additionalOrders.add(departmentPriority.asc());

            // 2차 정렬: 직급(position)의 code.sort_order 순서대로 정렬
            additionalOrders.add(positionCode.sortOrder.asc().nullsLast());
        }

        // 5. 공통 유틸리티로 페이징/정렬 적용
        return QuerydslPaginationUtil.applyPagination(
                contentQuery,
                countQuery,
                pageable,
                sortMapping,
                additionalOrders,
                account.createdAt.desc()
        );
    }

    @Override
    public List<Account> findActiveAdminContacts() {
        // ADMIN 우선(0), OPERATOR 후순(1) → 이름 오름차순
        NumberExpression<Integer> rolePriority = new CaseBuilder()
                .when(account.role.eq(AccountRole.ADMIN.getCodeId())).then(0)
                .otherwise(1);

        return queryFactory
                .selectFrom(account)
                .leftJoin(account.department, department).fetchJoin()
                .where(
                        account.role.in(AccountRole.ADMIN.getCodeId(), AccountRole.OPERATOR.getCodeId()),
                        account.status.eq(AccountStatus.ACTIVE.name())
                )
                .orderBy(rolePriority.asc(), account.accountName.asc())
                .fetch();
    }

    /**
     * 상태 조건
     */
    private BooleanExpression statusCondition(String status) {
        return (status != null && !status.trim().isEmpty()) ? account.status.eq(status) : null;
    }

    /**
     * 부서 조건 (단일 부서, 여러 부서 IN 조건, 또는 미배치)
     *
     * @param departmentIds 부서 ID 목록 (null이면 전체)
     * @param unassigned 미배치 계정만 조회 (true: department가 null인 계정만)
     */
    private BooleanExpression departmentCondition(List<Long> departmentIds, boolean unassigned) {
        // 미배치 계정 조회
        if (unassigned) {
            return account.department.isNull();
        }

        // 부서 ID 목록이 없으면 전체 조회
        if (departmentIds == null || departmentIds.isEmpty()) {
            return null;
        }

        // 단일 부서
        if (departmentIds.size() == 1) {
            return account.department.departmentId.eq(departmentIds.get(0));
        }

        // 여러 부서
        return account.department.departmentId.in(departmentIds);
    }

    /**
     * 키워드 검색 조건 (계정명, 이메일 통합 검색)
     */
    private BooleanExpression keywordCondition(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String trimmedKeyword = keyword.trim();
        return account.accountName.containsIgnoreCase(trimmedKeyword)
                .or(account.email.containsIgnoreCase(trimmedKeyword));
    }

    /**
     * 부서 유형 조건 (DEVELOPMENT: 개발, ENGINEER: 엔지니어)
     */
    private BooleanExpression departmentTypeCondition(String departmentType) {
        if (departmentType == null || departmentType.trim().isEmpty()) {
            return null;
        }
        return account.department.departmentType.eq(departmentType);
    }
}
