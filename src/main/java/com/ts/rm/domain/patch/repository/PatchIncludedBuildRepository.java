package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchIncludedBuildRepository extends JpaRepository<PatchIncludedBuild, Long> {

    /**
     * patch_id 별 모든 PatchIncludedBuild 행 조회.
     */
    List<PatchIncludedBuild> findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(Long patchId);

    /**
     * 여러 patch_id 의 모든 PatchIncludedBuild 행 batch 조회 (N+1 방지).
     * spec §5.4.
     */
    List<PatchIncludedBuild> findAllByPatch_PatchIdIn(List<Long> patchIds);
}
