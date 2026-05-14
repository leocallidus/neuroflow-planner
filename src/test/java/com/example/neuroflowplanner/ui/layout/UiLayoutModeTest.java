package com.example.neuroflowplanner.ui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiLayoutModeTest {

    @Test
    void resolveSupportsCompactAndComfortable() {
        assertEquals(UiLayoutMode.COMPACT, UiLayoutMode.resolve("compact"));
        assertEquals(UiLayoutMode.COMPACT, UiLayoutMode.resolve(" COMPACT "));
        assertEquals(UiLayoutMode.COMFORTABLE, UiLayoutMode.resolve("comfortable"));
    }

    @Test
    void resolveFallsBackToComfortableForUnknownOrBlank() {
        assertEquals(UiLayoutMode.COMFORTABLE, UiLayoutMode.resolve(null));
        assertEquals(UiLayoutMode.COMFORTABLE, UiLayoutMode.resolve(" "));
        assertEquals(UiLayoutMode.COMFORTABLE, UiLayoutMode.resolve("legacy"));
    }

    @Test
    void configValueMatchesExpectedRuntimeValues() {
        assertEquals("comfortable", UiLayoutMode.COMFORTABLE.configValue());
        assertEquals("compact", UiLayoutMode.COMPACT.configValue());
    }
}
