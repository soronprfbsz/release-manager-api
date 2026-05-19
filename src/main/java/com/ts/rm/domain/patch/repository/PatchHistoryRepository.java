package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.PatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PatchHistory Repository
 *
 * <p>패치 이력 데이터 접근
 */
@Repository
public interface PatchHistoryRepository extends JpaRepository<PatchHistory, Long>,
        PatchHistoryRepositoryCustom {

    /**
     * 고객사의 패치 이력 건수 조회 (고객사 초기화용).
     *
     * @param customerId 고객사 ID
     * @return 패치 이력 건수
     */
    long countByCustomer_CustomerId(Long customerId);

    /**
     * 고객사의 모든 패치 이력 삭제 (고객사 초기화용).
     *
     * <p>파생 delete 쿼리는 {@code void} 반환만 안정적으로 동작하므로,
     * 삭제 건수는 {@link #countByCustomer_CustomerId} 로 별도 조회한다.
     *
     * @param customerId 고객사 ID
     */
    void deleteAllByCustomer_CustomerId(Long customerId);

}
