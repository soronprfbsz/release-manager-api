package com.ts.rm.domain.customer.repository;

import com.ts.rm.domain.customer.entity.CustomerSiteVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CustomerSiteVersion Repository
 *
 * <p>사이트별 컴포넌트 버전 조회 및 저장
 */
public interface CustomerSiteVersionRepository extends JpaRepository<CustomerSiteVersion, Long> {

    /**
     * 고객사 + 프로젝트 + 컴포넌트로 단건 조회 (upsert 용).
     *
     * @param customerId 고객사 ID
     * @param projectId  프로젝트 ID
     * @param component  컴포넌트 (BASE/WEB/ENGINE)
     * @return 존재하면 Optional 에 포함
     */
    Optional<CustomerSiteVersion> findByCustomer_CustomerIdAndProject_ProjectIdAndComponent(
            Long customerId, String projectId, String component);

    /**
     * 고객사 + 프로젝트의 모든 컴포넌트 버전 목록 조회 (UI 표시용).
     *
     * @param customerId 고객사 ID
     * @param projectId  프로젝트 ID
     * @return 컴포넌트 버전 목록 (최대 3건: BASE/WEB/ENGINE)
     */
    List<CustomerSiteVersion> findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByComponent(
            Long customerId, String projectId);

    /**
     * 고객사의 사이트 버전 건수 조회 (초기화 전 카운트용).
     *
     * @param customerId 고객사 ID
     * @return 건수
     */
    long countByCustomer_CustomerId(Long customerId);

    /**
     * 고객사의 모든 사이트 버전 삭제 (고객사 초기화용).
     *
     * @param customerId 고객사 ID
     */
    void deleteAllByCustomer_CustomerId(Long customerId);
}
