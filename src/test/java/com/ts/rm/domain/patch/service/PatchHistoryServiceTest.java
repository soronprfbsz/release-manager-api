package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ts.rm.domain.patch.entity.Patch;
import com.ts.rm.domain.patch.entity.PatchHistory;
import com.ts.rm.domain.patch.entity.PatchIncludedBuild;
import com.ts.rm.domain.patch.repository.PatchHistoryBuildRepository;
import com.ts.rm.domain.patch.repository.PatchHistoryRepository;
import com.ts.rm.domain.patch.repository.PatchIncludedBuildRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatchHistoryService 테스트")
class PatchHistoryServiceTest {

    @Mock
    private PatchHistoryRepository patchHistoryRepository;
    @Mock
    private PatchIncludedBuildRepository patchIncludedBuildRepository;
    @Mock
    private PatchHistoryBuildRepository patchHistoryBuildRepository;

    @InjectMocks
    private PatchHistoryService patchHistoryService;

    @Test
    @DisplayName("saveFromPatch — 빌드 포함 패치면 patch_included_build 를 patch_history_build 로 복사 저장")
    void saveFromPatch_savesBuildSnapshots() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Patch patch = org.mockito.Mockito.mock(Patch.class);
        when(patch.getIsBuildIncluded()).thenReturn(true);
        when(patch.getPatchId()).thenReturn(10L);

        PatchHistory saved = PatchHistory.builder().historyId(100L).build();
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenReturn(saved);

        PatchIncludedBuild web = PatchIncludedBuild.builder()
                .kind("WEB").engineName(null).fullVersion("1.1.0.260511-1").build();
        PatchIncludedBuild engine = PatchIncludedBuild.builder()
                .kind("ENGINE").engineName("NC_SMS").fullVersion("1.1.0.260511-2").build();
        when(patchIncludedBuildRepository
                .findAllByPatch_PatchIdOrderByPatchIncludedBuildIdAsc(10L))
                .thenReturn(List.of(web, engine));

        // when
        patchHistoryService.saveFromPatch(patch, "ops@ts.com", now);

        // then — 2건 스냅샷 저장
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.ts.rm.domain.patch.entity.PatchHistoryBuild>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(patchHistoryBuildRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue())
                .extracting(com.ts.rm.domain.patch.entity.PatchHistoryBuild::getKind)
                .containsExactly("WEB", "ENGINE");
    }

    @Test
    @DisplayName("saveFromPatch — 빌드 미포함 패치면 스냅샷 저장 안 함")
    void saveFromPatch_noBuild_skipsSnapshots() {
        // given
        Patch patch = org.mockito.Mockito.mock(Patch.class);
        when(patch.getIsBuildIncluded()).thenReturn(false);
        PatchHistory saved = PatchHistory.builder().historyId(101L).build();
        when(patchHistoryRepository.save(any(PatchHistory.class))).thenReturn(saved);

        // when
        patchHistoryService.saveFromPatch(patch, "ops@ts.com", LocalDateTime.now());

        // then
        verify(patchHistoryBuildRepository, never()).saveAll(any());
    }
}
