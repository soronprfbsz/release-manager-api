package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private com.ts.rm.domain.customer.service.CustomerSiteVersionService customerSiteVersionService;
    @Mock
    private com.ts.rm.domain.customer.repository.CustomerProjectRepository customerProjectRepository;

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

    @Test
    @DisplayName("deleteHistory — 고객사 이력 삭제 시 남은 이력을 재생하여 버전 재계산")
    void deleteHistory_recomputesForCustomerPatch() {
        // given — 삭제 대상 이력 (고객사 C=1, 프로젝트 P)
        com.ts.rm.domain.customer.entity.Customer customer =
                org.mockito.Mockito.mock(com.ts.rm.domain.customer.entity.Customer.class);
        when(customer.getCustomerId()).thenReturn(1L);
        com.ts.rm.domain.project.entity.Project project =
                org.mockito.Mockito.mock(com.ts.rm.domain.project.entity.Project.class);
        when(project.getProjectId()).thenReturn("PRJ");

        PatchHistory target = org.mockito.Mockito.mock(PatchHistory.class);
        when(target.getCustomer()).thenReturn(customer);
        when(target.getProject()).thenReturn(project);
        when(patchHistoryRepository.findById(50L)).thenReturn(java.util.Optional.of(target));

        // 남은 이력 2건 (완료순)
        PatchHistory h1 = org.mockito.Mockito.mock(PatchHistory.class);
        when(h1.getHistoryId()).thenReturn(40L);
        when(h1.getToVersion()).thenReturn("1.1.0.260511-1");
        when(h1.getCompletedAt()).thenReturn(java.time.LocalDateTime.now().minusDays(2));
        when(h1.getCompletedBy()).thenReturn("ops@ts.com");
        PatchHistory h2 = org.mockito.Mockito.mock(PatchHistory.class);
        when(h2.getHistoryId()).thenReturn(45L);
        when(h2.getToVersion()).thenReturn("1.2.0.260601-1");
        when(h2.getCompletedAt()).thenReturn(java.time.LocalDateTime.now().minusDays(1));
        when(h2.getCompletedBy()).thenReturn("ops@ts.com");
        when(patchHistoryRepository
                .findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByCompletedAtAscCreatedAtAsc(
                        1L, "PRJ"))
                .thenReturn(java.util.List.of(h1, h2));

        when(patchHistoryBuildRepository
                .findAllByHistory_HistoryIdOrderByPatchHistoryBuildIdAsc(any()))
                .thenReturn(java.util.List.of());
        when(customerSiteVersionService.extractBaseVersion("1.1.0.260511-1")).thenReturn("1.1.0");
        when(customerSiteVersionService.extractBaseVersion("1.2.0.260601-1")).thenReturn("1.2.0");

        com.ts.rm.domain.customer.entity.CustomerProject cp =
                org.mockito.Mockito.mock(com.ts.rm.domain.customer.entity.CustomerProject.class);
        when(customerProjectRepository.findByCustomer_CustomerIdAndProject_ProjectId(1L, "PRJ"))
                .thenReturn(java.util.Optional.of(cp));

        // when
        patchHistoryService.deleteHistory(50L);

        // then — 대상 스냅샷 삭제 + 이력 삭제 + 사이트버전 초기화 + 재생 2회 + last_patched 갱신
        verify(patchHistoryBuildRepository).deleteAllByHistory_HistoryId(50L);
        verify(patchHistoryRepository).delete(target);
        verify(customerSiteVersionService).clearByCustomerAndProject(1L, "PRJ");
        verify(customerSiteVersionService, times(2)).applyComponentVersions(
                eq(1L), eq("PRJ"), any(), any(), any(), any());
        verify(cp).updateLastPatchInfo(eq("1.2.0.260601-1"), any());
        verify(customerProjectRepository).save(cp);
    }

    @Test
    @DisplayName("deleteHistory — 남은 이력 없으면 last_patched 초기화(패치 미적용)")
    void deleteHistory_noRemaining_clearsLastPatch() {
        com.ts.rm.domain.customer.entity.Customer customer =
                org.mockito.Mockito.mock(com.ts.rm.domain.customer.entity.Customer.class);
        when(customer.getCustomerId()).thenReturn(1L);
        com.ts.rm.domain.project.entity.Project project =
                org.mockito.Mockito.mock(com.ts.rm.domain.project.entity.Project.class);
        when(project.getProjectId()).thenReturn("PRJ");

        PatchHistory target = org.mockito.Mockito.mock(PatchHistory.class);
        when(target.getCustomer()).thenReturn(customer);
        when(target.getProject()).thenReturn(project);
        when(patchHistoryRepository.findById(50L)).thenReturn(java.util.Optional.of(target));
        when(patchHistoryRepository
                .findAllByCustomer_CustomerIdAndProject_ProjectIdOrderByCompletedAtAscCreatedAtAsc(
                        1L, "PRJ"))
                .thenReturn(java.util.List.of());

        com.ts.rm.domain.customer.entity.CustomerProject cp =
                org.mockito.Mockito.mock(com.ts.rm.domain.customer.entity.CustomerProject.class);
        when(customerProjectRepository.findByCustomer_CustomerIdAndProject_ProjectId(1L, "PRJ"))
                .thenReturn(java.util.Optional.of(cp));

        patchHistoryService.deleteHistory(50L);

        verify(customerSiteVersionService).clearByCustomerAndProject(1L, "PRJ");
        verify(cp).updateLastPatchInfo(null, null);
        verify(customerProjectRepository).save(cp);
    }

    @Test
    @DisplayName("deleteHistory — 표준 패치(고객사 미지정)는 재계산하지 않음")
    void deleteHistory_standardPatch_noRecompute() {
        PatchHistory target = org.mockito.Mockito.mock(PatchHistory.class);
        when(target.getCustomer()).thenReturn(null);
        when(patchHistoryRepository.findById(60L)).thenReturn(java.util.Optional.of(target));

        patchHistoryService.deleteHistory(60L);

        verify(patchHistoryBuildRepository).deleteAllByHistory_HistoryId(60L);
        verify(patchHistoryRepository).delete(target);
        verify(customerSiteVersionService, never()).clearByCustomerAndProject(any(), any());
    }
}
