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
     * 고객사의 모든 패치 이력 삭제 (고객사 초기화용).
     *
     * @param customerId 고객사 ID
     * @return 삭제된 건수
     */
    long deleteAllByCustomer_CustomerId(Long customerId);

}
