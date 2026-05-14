package com.example.neuroflowplanner.ui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiLayoutBreakpointTest {

    @Test
    void fromWidthUsesDefaultBreakpoints() {
        assertEquals(UiLayoutBreakpoint.COMPACT, UiLayoutBreakpoint.fromWidth(1280.0));
        assertEquals(UiLayoutBreakpoint.NORMAL, UiLayoutBreakpoint.fromWidth(1366.0));
        assertEquals(UiLayoutBreakpoint.WIDE, UiLayoutBreakpoint.fromWidth(1600.0));
    }

    @Test
    void fromWidthReturnsNormalForInvalidValues() {
        assertEquals(UiLayoutBreakpoint.NORMAL, UiLayoutBreakpoint.fromWidth(Double.NaN));
        assertEquals(UiLayoutBreakpoint.NORMAL, UiLayoutBreakpoint.fromWidth(Double.POSITIVE_INFINITY));
        assertEquals(UiLayoutBreakpoint.NORMAL, UiLayoutBreakpoint.fromWidth(0.0));
        assertEquals(UiLayoutBreakpoint.NORMAL, UiLayoutBreakpoint.fromWidth(-1.0));
    }

    @Test
    void customThresholdsAreSanitizedAndRemainDeterministic() {
        assertEquals(UiLayoutBreakpoint.NORMAL, UiLayoutBreakpoint.fromWidth(1366.0, 1366, 1366));
        assertEquals(UiLayoutBreakpoint.WIDE, UiLayoutBreakpoint.fromWidth(1367.0, 1366, 1366));
        assertEquals(UiLayoutBreakpoint.WIDE, UiLayoutBreakpoint.fromWidth(900.0, 0, 1));
    }
}
