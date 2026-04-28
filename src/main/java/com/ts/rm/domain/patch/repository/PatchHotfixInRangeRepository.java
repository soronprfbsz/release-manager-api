package com.ts.rm.domain.patch.repository;

import com.ts.rm.domain.patch.entity.PatchHotfixInRange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchHotfixInRangeRepository extends JpaRepository<PatchHotfixInRange, Long> {

    List<PatchHotfixInRange> findAllByPatch_PatchIdOrderByHotfixVersionNumberAsc(Long patchId);
}
