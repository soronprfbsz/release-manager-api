package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.PatchHistoryBuild;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PatchHistoryBuild Repository
 *
 * <p>패치 이력별 빌드 스냅샷 데이터 접근
 */
@Repository
public interface PatchHistoryBuildRepository extends JpaRepository<PatchHistoryBuild, Long> {

    /**
     * 이력별 빌드 스냅샷 목록 조회 (적재 순서).
     */
    List<PatchHistoryBuild> findAllByHistory_HistoryIdOrderByPatchHistoryBuildIdAsc(Long historyId);

    /**
     * 이력 삭제 시 해당 이력의 빌드 스냅샷 제거 (FK CASCADE 와 무관하게 ORM 레벨에서 명시 삭제).
     */
    void deleteAllByHistory_HistoryId(Long historyId);
}
