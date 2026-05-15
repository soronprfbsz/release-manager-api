package com.ts.rm.domain.analytics.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringTemplate;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.CustomerPatchCount;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.MonthlyCustomerPatchRaw;
import com.ts.rm.domain.analytics.dto.AnalyticsDto.VersionCustomerRaw;
import com.ts.rm.domain.customer.entity.QCustomer;
import com.ts.rm.domain.patch.entity.QPatchHistory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 패치 분석 Repository 구현체
 *
 * <p>QueryDSL을 사용한 패치 분석 집계 쿼리 구현
 * <p>patch_history 테이블 사용 (patch_file은 용량 문제로 삭제될 수 있으므로)
 */
@Repository
@RequiredArgsConstructor
public class PatchAnalyticsRepositoryImpl implements PatchAnalyticsRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 프로젝트별 기간 내 고객사별 패치 건수 Top-N 조회
     *
     * <p>CUSTOM 타입 패치만 집계 (STANDARD는 고객사가 없음)
     *
     * @param projectId 프로젝트 ID
     * @param startDate 시작일시
     * @param topN      상위 N개
     * @return 고객사별 패치 건수 목록 (내림차순)
     */
    @Override
    public List<CustomerPatchCount> findTopCustomersByPatchCount(String projectId,
            LocalDateTime startDate, int topN) {
        QPatchHistory patchHistory = QPatchHistory.patchHistory;
        QCustomer customer = QCustomer.customer;

        return queryFactory
                .select(Projections.constructor(CustomerPatchCount.class,
                        customer.customerId,
                        customer.customerCode,
                        customer.customerName,
                        patchHistory.count()))
                .from(patchHistory)
                .join(patchHistory.customer, customer)
                .where(
                        patchHistory.project.projectId.eq(projectId),
                        patchHistory.createdAt.goe(startDate),
                        patchHistory.customer.isNotNull()
                )
                .groupBy(
                        customer.customerId,
                        customer.customerCode,
                        customer.customerName
                )
                .orderBy(patchHistory.count().desc(), customer.customerName.asc())
                .limit(topN)
                .fetch();
    }

    /**
     * 프로젝트별 기간 내 월별+고객별 패치 건수 조회
     *
     * <p>CUSTOM 타입 패치만 집계 (고객사별 통계이므로)
     *
     * @param projectId 프로젝트 ID
     * @param startDate 시작일시
     * @return 월별+고객별 패치 건수 목록 (연월 오름차순, 고객명 오름차순)
     */
    @Override
    public List<MonthlyCustomerPatchRaw> findMonthlyCustomerPatchCounts(String projectId,
            LocalDateTime startDate) {
        QPatchHistory patchHistory = QPatchHistory.patchHistory;
        QCustomer customer = QCustomer.customer;

        // DATE_FORMAT(created_at, '%Y-%m') 형식으로 월별 그룹화
        StringTemplate yearMonthTemplate = Expressions.stringTemplate(
                "DATE_FORMAT({0}, '%Y-%m')",
                patchHistory.createdAt
        );

        return queryFactory
                .select(Projections.constructor(MonthlyCustomerPatchRaw.class,
                        yearMonthTemplate,
                        customer.customerName,
                        patchHistory.count()))
                .from(patchHistory)
                .join(patchHistory.customer, customer)
                .where(
                        patchHistory.project.projectId.eq(projectId),
                        patchHistory.createdAt.goe(startDate),
                        patchHistory.customer.isNotNull()
                )
                .groupBy(yearMonthTemplate, customer.customerName)
                .orderBy(yearMonthTemplate.asc(), customer.customerName.asc())
                .fetch();
    }

    /**
     * 프로젝트별 각 고객사의 최신 완료 patch_history.to_version 조회.
     *
     * <p>서브쿼리로 각 customer 의 MAX(completed_at) 인 row 만 선택.
     */
    @Override
    public List<VersionCustomerRaw> findLatestVersionByCustomer(String projectId) {
        QPatchHistory ph = QPatchHistory.patchHistory;
        QPatchHistory ph2 = new QPatchHistory("ph2");
        QCustomer customer = QCustomer.customer;

        return queryFactory
                .select(Projections.constructor(VersionCustomerRaw.class,
                        ph.toVersion,
                        customer.customerId,
                        customer.customerCode,
                        customer.customerName))
                .from(ph)
                .join(ph.customer, customer)
                .where(
                        ph.project.projectId.eq(projectId),
                        ph.customer.isNotNull(),
                        ph.completedAt.eq(
                                JPAExpressions
                                        .select(ph2.completedAt.max())
                                        .from(ph2)
                                        .where(ph2.customer.eq(ph.customer)
                                                .and(ph2.project.projectId.eq(projectId)))
                        )
                )
                .orderBy(customer.customerName.asc())
                .fetch();
    }
}
