package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.util.UxConfigDefaults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiLayoutStateTest {

    @Test
    void defaultsUseConfiguredStateDefaults() {
        UiLayoutState state = UiLayoutState.defaults();

        assertEquals(UiLayoutBreakpoint.NORMAL, state.breakpoint());
        assertEquals(UiLayoutMode.COMFORTABLE, state.densityMode());
        assertFalse(state.leftPanelCollapsed());
        assertFalse(state.rightPanelCollapsed());
        assertEquals(UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_DEFAULT, state.leftPanelWidth());
        assertEquals(UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_DEFAULT, state.rightPanelWidth());
    }

    @Test
    void constructorNormalizesNullsAndClampsWidths() {
        UiLayoutState state = new UiLayoutState(
            null,
            null,
            true,
            false,
            Double.NaN,
            9999.0
        );

        assertEquals(UiLayoutBreakpoint.NORMAL, state.breakpoint());
        assertEquals(UiLayoutMode.COMFORTABLE, state.densityMode());
        assertTrue(state.leftPanelCollapsed());
        assertFalse(state.rightPanelCollapsed());
        assertEquals(UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_DEFAULT, state.leftPanelWidth());
        assertEquals(UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX, state.rightPanelWidth());
    }

    @Test
    void withPanelWidthsClampsToMinMaxRange() {
        UiLayoutState updated = UiLayoutState.defaults().withPanelWidths(-100.0, 10000.0);

        assertEquals(UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_MIN, updated.leftPanelWidth());
        assertEquals(UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX, updated.rightPanelWidth());
    }
}
