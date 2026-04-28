package com.ts.rm.domain.patch.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ts.rm.domain.patch.mapper.PatchDtoMapper;
import com.ts.rm.domain.patch.service.PatchService;
import com.ts.rm.global.config.MessageConfig;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import com.ts.rm.global.exception.GlobalExceptionHandler;
import com.ts.rm.global.filter.JwtAuthenticationFilter;
import com.ts.rm.global.logging.service.ApiLogService;
import com.ts.rm.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PatchController 삭제 API 테스트
 */
@WebMvcTest(controllers = PatchController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, MessageConfig.class})
class PatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatchService patchService;

    @MockitoBean
    private PatchDtoMapper patchDtoMapper;

    // Security 관련 MockBean 추가
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private ApiLogService apiLogService;

    @Test
    @DisplayName("DELETE /api/patches/{id} - 성공")
    void deletePatch_Success() throws Exception {
        // Given
        Long patchId = 1L;
        doNothing().when(patchService).deletePatch(patchId);

        // When & Then
        mockMvc.perform(delete("/api/patches/{id}", patchId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(patchService).deletePatch(patchId);
    }

    @Test
    @DisplayName("DELETE /api/patches/{id} - 실패 (패치를 찾을 수 없음)")
    void deletePatch_NotFound() throws Exception {
        // Given
        Long patchId = 999L;

        doThrow(new BusinessException(ErrorCode.DATA_NOT_FOUND,
                "패치를 찾을 수 없습니다: " + patchId))
                .when(patchService).deletePatch(patchId);

        // When & Then
        mockMvc.perform(delete("/api/patches/{id}", patchId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"));

        verify(patchService).deletePatch(patchId);
    }

    @Test
    @DisplayName("DELETE /api/patches/{id} - 실패 (파일 삭제 중 오류)")
    void deletePatch_FileDeleteError() throws Exception {
        // Given
        Long patchId = 1L;

        doThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                "패치 파일 삭제 중 오류가 발생했습니다"))
                .when(patchService).deletePatch(patchId);

        // When & Then
        mockMvc.perform(delete("/api/patches/{id}", patchId))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"));

        verify(patchService).deletePatch(patchId);
    }
}
