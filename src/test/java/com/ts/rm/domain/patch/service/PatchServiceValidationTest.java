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
        assertThatThrownBy(() -> PatchService.validateBuildSelection(selection, /*sameBase*/ false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("from == to 인데 toggle OFF 면 INVALID_INPUT_VALUE")
    void sameBaseAndDisabled_throws() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(false, null, List.of());
        assertThatThrownBy(() -> PatchService.validateBuildSelection(selection, /*sameBase*/ true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("from == to + toggle ON + picker 비어있으면 INVALID_INPUT_VALUE")
    void sameBaseEnabledButEmpty_throws() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(true, null, List.of());
        assertThatThrownBy(() -> PatchService.validateBuildSelection(selection, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("정상: from != to + toggle OFF (DB only)")
    void disabledRangePatch_passes() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(false, null, List.of());
        PatchService.validateBuildSelection(selection, false);  // no exception
    }

    @Test
    @DisplayName("정상: null buildSelection + range patch")
    void nullSelectionRangePatch_passes() {
        PatchService.validateBuildSelection(null, false);
    }

    @Test
    @DisplayName("정상: from == to + toggle ON + picker 1개 이상")
    void sameBaseWithPicker_passes() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(
                true, new PatchDto.SelectedWeb(42L), List.of());
        PatchService.validateBuildSelection(selection, true);
    }

    @Test
    @DisplayName("정상: from != to + toggle ON + engines 만 1개 (web 없음)")
    void enginesOnlyRangePatch_passes() {
        PatchDto.BuildSelection selection = new PatchDto.BuildSelection(
                true,
                null,
                java.util.List.of(new PatchDto.SelectedEngine("NC_SMS", 42L)));
        PatchService.validateBuildSelection(selection, false);  // no exception
    }
}
