package com.ts.rm.domain.patch.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ts.rm.domain.patch.dto.PatchDto;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PatchService.validateBuildSelection 테스트")
class PatchServiceValidationTest {

    @Test
    @DisplayName("toggle ON 인데 web/engines 모두 비어있으면 INVALID_INPUT_VALUE")
    void enabledButEmpty_throws() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(true, null, List.of());
        assertThatThrownBy(() -> PatchService.validateBuildSelection(selection))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("정상: toggle OFF (DB only) — from==to 라도 빌드 없이 허용 (빌드 전용 강제 폐지)")
    void disabledSelection_passes() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(false, null, List.of());
        PatchService.validateBuildSelection(selection);  // no exception
    }

    @Test
    @DisplayName("정상: null buildSelection")
    void nullSelection_passes() {
        PatchService.validateBuildSelection(null);  // no exception
    }

    @Test
    @DisplayName("정상: toggle ON + web 빌드 1개")
    void webOnly_passes() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(
                true, new PatchDto.SelectedWeb(42L), List.of());
        PatchService.validateBuildSelection(selection);  // no exception
    }

    @Test
    @DisplayName("정상: toggle ON + engines 만 1개 (web 없음)")
    void enginesOnly_passes() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(
                true,
                null,
                List.of(new PatchDto.SelectedEngine("NC_SMS", 42L)));
        PatchService.validateBuildSelection(selection);  // no exception
    }
}
