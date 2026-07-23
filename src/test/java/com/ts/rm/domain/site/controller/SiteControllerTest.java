package com.ts.rm.domain.site.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ts.rm.domain.account.repository.AccountRepository;
import com.ts.rm.domain.site.dto.SiteDto;
import com.ts.rm.domain.site.service.SiteService;
import com.ts.rm.global.config.MessageConfig;
import com.ts.rm.global.exception.GlobalExceptionHandler;
import com.ts.rm.global.security.jwt.JwtTokenProvider;
import com.ts.rm.domain.common.service.CustomUserDetailsService;
import com.ts.rm.global.filter.JwtAuthenticationFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Site Controller 통합 테스트
 */
@WebMvcTest(controllers = SiteController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, MessageConfig.class})
@ActiveProfiles("test")
@DisplayName("SiteController 테스트")
class SiteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SiteService siteService;

    // Security 관련 MockBean 추가
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private SiteDto.DetailResponse detailResponse;
    private SiteDto.ListResponse listResponse;
    private SiteDto.SimpleResponse simpleResponse;

    private MockedStatic<SecurityContextHolder> securityContextHolderMock;

    @BeforeEach
    void setUp() {
        // SecurityContextHolder 모킹 설정
        SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        UserDetails userDetails = User.builder()
                .username("admin@tscientific")
                .password("password")
                .roles("USER")
                .build();

        given(authentication.getPrincipal()).willReturn(userDetails);
        given(authentication.isAuthenticated()).willReturn(true);
        given(securityContext.getAuthentication()).willReturn(authentication);

        securityContextHolderMock = mockStatic(SecurityContextHolder.class);
        securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        LocalDateTime now = LocalDateTime.now();

        detailResponse = new SiteDto.DetailResponse(
                1L,
                "company_a",
                "A회사",
                "A회사 설명",
                true,
                null,
                now,
                "admin@tscientific",
                now,
                "admin@tscientific"
        );

        listResponse = new SiteDto.ListResponse(
                1L,
                1L,
                "company_a",
                "A회사",
                "A회사 설명",
                true,
                null,
                now
        );

        simpleResponse = new SiteDto.SimpleResponse(
                1L,
                "company_a",
                "A회사",
                true
        );
    }

    @AfterEach
    void tearDown() {
        if (securityContextHolderMock != null) {
            securityContextHolderMock.close();
        }
    }

    @Test
    @DisplayName("사이트 생성 - 성공")
    void createSite_Success() throws Exception {
        // given
        SiteDto.CreateRequest request = SiteDto.CreateRequest.builder()
                .siteCode("company_a")
                .siteName("A회사")
                .description("A회사 설명")
                .isActive(true)
                .build();

        given(siteService.createSite(any(), eq("admin@tscientific"))).willReturn(detailResponse);

        // when & then
        mockMvc.perform(post("/api/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.siteCode").value("company_a"))
                .andExpect(jsonPath("$.data.siteName").value("A회사"));
    }

    @Test
    @DisplayName("사이트 조회 (ID) - 성공")
    void getSiteById_Success() throws Exception {
        // given
        given(siteService.getSiteById(1L)).willReturn(detailResponse);

        // when & then
        mockMvc.perform(get("/api/sites/{id}", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.siteId").value(1))
                .andExpect(jsonPath("$.data.siteCode").value("company_a"));
    }

    @Test
    @DisplayName("활성 사이트 목록 조회 - 성공")
    void getActiveSites_Success() throws Exception {
        // given
        Page<SiteDto.ListResponse> page = new PageImpl<>(List.of(listResponse));
        given(siteService.getSitesWithPaging(eq(true), isNull(), any(Pageable.class)))
                .willReturn(page);

        // when & then
        mockMvc.perform(get("/api/sites").param("isActive", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].siteCode").value("company_a"));
    }

    @Test
    @DisplayName("전체 사이트 목록 조회 - 성공")
    void getAllSites_Success() throws Exception {
        // given
        Page<SiteDto.ListResponse> page = new PageImpl<>(List.of(listResponse));
        given(siteService.getSitesWithPaging(isNull(), isNull(), any(Pageable.class)))
                .willReturn(page);

        // when & then
        mockMvc.perform(get("/api/sites"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].siteId").value(1));
    }

    @Test
    @DisplayName("사이트 정보 수정 - 성공")
    void updateSite_Success() throws Exception {
        // given
        SiteDto.UpdateRequest request = SiteDto.UpdateRequest.builder()
                .siteName("수정된회사")
                .description("수정된설명")
                .build();

        SiteDto.DetailResponse updatedResponse = new SiteDto.DetailResponse(
                1L,
                "company_a",
                "수정된회사",
                "수정된설명",
                true,
                null,
                detailResponse.createdAt(),
                "admin@tscientific",
                LocalDateTime.now(),
                "admin@tscientific"
        );

        given(siteService.updateSite(eq(1L), any(), eq("admin@tscientific"))).willReturn(updatedResponse);

        // when & then
        mockMvc.perform(put("/api/sites/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.siteName").value("수정된회사"));
    }

    @Test
    @DisplayName("사이트 삭제 - 성공")
    void deleteSite_Success() throws Exception {
        // given
        willDoNothing().given(siteService).deleteSite(1L);

        // when & then
        mockMvc.perform(delete("/api/sites/{id}", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("사이트 활성화 - 성공")
    void activateSite_Success() throws Exception {
        // given
        willDoNothing().given(siteService).updateSiteStatus(eq(1L), eq(true));

        // when & then
        mockMvc.perform(patch("/api/sites/{id}/status", 1L)
                        .param("isActive", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("사이트 비활성화 - 성공")
    void deactivateSite_Success() throws Exception {
        // given
        willDoNothing().given(siteService).updateSiteStatus(eq(1L), eq(false));

        // when & then
        mockMvc.perform(patch("/api/sites/{id}/status", 1L)
                        .param("isActive", "false"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("사이트 생성 - Validation 실패 (siteCode 누락)")
    void createSite_ValidationFail_MissingSiteCode() throws Exception {
        // given
        SiteDto.CreateRequest request = SiteDto.CreateRequest.builder()
                .siteName("A회사")
                .description("설명")
                .isActive(true)
                .build();

        // when & then
        mockMvc.perform(post("/api/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사이트 생성 - Validation 실패 (siteName 누락)")
    void createSite_ValidationFail_MissingSiteName() throws Exception {
        // given
        SiteDto.CreateRequest request = SiteDto.CreateRequest.builder()
                .siteCode("company_a")
                .description("설명")
                .isActive(true)
                .build();

        // when & then
        mockMvc.perform(post("/api/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
