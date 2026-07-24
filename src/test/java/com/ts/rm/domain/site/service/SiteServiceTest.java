package com.ts.rm.domain.site.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.site.dto.SiteDto;
import com.ts.rm.domain.site.entity.Site;
import com.ts.rm.domain.site.mapper.SiteDtoMapper;
import com.ts.rm.domain.site.repository.SiteProjectRepository;
import com.ts.rm.domain.site.repository.SiteRepository;
import com.ts.rm.domain.project.repository.ProjectRepository;
import com.ts.rm.domain.releaseversion.repository.ReleaseVersionRepository;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Site Service 단위 테스트
 *
 * <p>TDD 방식으로 테스트를 먼저 작성하고 구현
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SiteService 테스트")
class SiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private SiteProjectRepository siteProjectRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SiteDtoMapper mapper;

    @Mock
    private ReleaseVersionRepository releaseVersionRepository;

    @Mock
    private AccountLookupService accountLookupService;

    @InjectMocks
    private SiteService siteService;

    private Site testSite;
    private SiteDto.CreateRequest createRequest;

    @BeforeEach
    void setUp() {
        testSite = Site.builder()
                .siteId(1L)
                .siteCode("company_a")
                .siteName("A회사")
                .description("A회사 설명")
                .isActive(true)
                .createdByEmail("admin@tscientific")
                .updatedByEmail("admin@tscientific")
                .build();

        createRequest = SiteDto.CreateRequest.builder()
                .siteCode("company_a")
                .siteName("A회사")
                .description("A회사 설명")
                .isActive(true)
                .build();

        // 상세/목록 응답 생성 시 커스텀 버전 존재 여부 조회 — 기본 false
        lenient().when(releaseVersionRepository.existsBySite_SiteId(anyLong())).thenReturn(false);
        // 생성/수정자 Account 조회
        Account actor = Account.builder().email("admin@tscientific").accountName("관리자").build();
        lenient().when(accountLookupService.findByEmail(anyString())).thenReturn(actor);
    }

    @Test
    @DisplayName("사이트 생성 - 성공")
    void createSite_Success() {
        // given
        given(siteRepository.existsBySiteCode(anyString())).willReturn(false);
        given(mapper.toEntity(any(SiteDto.CreateRequest.class))).willReturn(testSite);
        given(siteRepository.save(any(Site.class))).willReturn(testSite);

        // when
        SiteDto.DetailResponse result = siteService.createSite(createRequest, "admin@tscientific");

        // then
        assertThat(result).isNotNull();
        assertThat(result.siteCode()).isEqualTo("company_a");
        assertThat(result.siteName()).isEqualTo("A회사");

        then(siteRepository).should(times(1)).existsBySiteCode(anyString());
        then(siteRepository).should(times(1)).save(any(Site.class));
    }

    @Test
    @DisplayName("사이트 생성 - 중복 코드로 실패")
    void createSite_DuplicateCode() {
        // given
        given(siteRepository.existsBySiteCode(anyString())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> siteService.createSite(createRequest, "admin@tscientific"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SITE_CODE_CONFLICT);

        then(siteRepository).should(times(1)).existsBySiteCode(anyString());
        then(siteRepository).should(never()).save(any(Site.class));
    }

    @Test
    @DisplayName("사이트 조회 (ID) - 성공")
    void getSiteById_Success() {
        // given
        given(siteRepository.findById(anyLong())).willReturn(Optional.of(testSite));
        given(siteProjectRepository.findAllBySiteIdWithProject(anyLong())).willReturn(List.of());

        // when
        SiteDto.DetailResponse result = siteService.getSiteById(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.siteId()).isEqualTo(1L);

        then(siteRepository).should(times(1)).findById(1L);
    }

    @Test
    @DisplayName("사이트 조회 (ID) - 존재하지 않음")
    void getSiteById_NotFound() {
        // given
        given(siteRepository.findById(anyLong())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> siteService.getSiteById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SITE_NOT_FOUND);

        then(siteRepository).should(times(1)).findById(999L);
    }

    @Test
    @DisplayName("사이트 코드로 조회 - 성공")
    void getSiteByCode_Success() {
        // given
        given(siteRepository.findBySiteCode(anyString())).willReturn(
                Optional.of(testSite));
        given(siteProjectRepository.findAllBySiteIdWithProject(anyLong())).willReturn(List.of());

        // when
        SiteDto.DetailResponse result = siteService.getSiteByCode("company_a");

        // then
        assertThat(result).isNotNull();
        assertThat(result.siteCode()).isEqualTo("company_a");

        then(siteRepository).should(times(1)).findBySiteCode("company_a");
    }

    @Test
    @DisplayName("활성 사이트 목록 조회 - 성공")
    void getActiveSites_Success() {
        // given
        List<Site> sites = List.of(testSite);

        given(siteRepository.findAllByIsActive(true)).willReturn(sites);
        given(siteProjectRepository.findAllBySiteIdWithProject(anyLong())).willReturn(List.of());

        // when
        List<SiteDto.DetailResponse> result = siteService.getSites(true, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).siteCode()).isEqualTo("company_a");

        then(siteRepository).should(times(1)).findAllByIsActive(true);
    }

    @Test
    @DisplayName("전체 사이트 목록 조회 - 성공")
    void getAllSites_Success() {
        // given
        List<Site> sites = List.of(testSite);

        given(siteRepository.findAll()).willReturn(sites);
        given(siteProjectRepository.findAllBySiteIdWithProject(anyLong())).willReturn(List.of());

        // when
        List<SiteDto.DetailResponse> result = siteService.getSites(null, null);

        // then
        assertThat(result).hasSize(1);

        then(siteRepository).should(times(1)).findAll();
    }

    @Test
    @DisplayName("사이트 수정 - 성공")
    void updateSite_Success() {
        // given
        SiteDto.UpdateRequest updateRequest = SiteDto.UpdateRequest.builder()
                .siteName("새이름")
                .description("새설명")
                .isActive(true)
                .build();

        given(siteRepository.findById(anyLong())).willReturn(Optional.of(testSite));
        given(siteProjectRepository.findAllBySiteIdWithProject(anyLong())).willReturn(List.of());

        // when
        SiteDto.DetailResponse result = siteService.updateSite(1L, updateRequest, "admin@tscientific");

        // then
        assertThat(result).isNotNull();
        // JPA Dirty Checking 사용 - 엔티티 조회만 검증
        then(siteRepository).should(times(1)).findById(1L);
    }

    @Test
    @DisplayName("사이트 삭제 - 성공")
    void deleteSite_Success() {
        // given
        given(siteRepository.findById(anyLong())).willReturn(Optional.of(testSite));

        // when
        siteService.deleteSite(1L);

        // then
        then(siteRepository).should(times(1)).delete(any(Site.class));
    }

    @Test
    @DisplayName("사이트명으로 검색 - 성공")
    void searchSitesByName_Success() {
        // given
        List<Site> sites = List.of(testSite);

        given(siteRepository.findBySiteNameContaining(anyString())).willReturn(sites);
        given(siteProjectRepository.findAllBySiteIdWithProject(anyLong())).willReturn(List.of());

        // when
        List<SiteDto.DetailResponse> result = siteService.getSites(null, "A회사");

        // then
        assertThat(result).hasSize(1);

        then(siteRepository).should(times(1)).findBySiteNameContaining("A회사");
    }
}
