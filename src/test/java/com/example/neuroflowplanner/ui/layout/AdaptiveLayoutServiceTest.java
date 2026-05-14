package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveLayoutServiceTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_WIDTH,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH
    );

    private final Map<String, String> snapshot = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        snapshotConfig();
    }

    @AfterEach
    void tearDown() {
        restoreConfig();
    }

    @Test
    void loadStateReadsPersistedFlagsAndWidths() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, "compact");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED, "true");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_WIDTH, "310");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH, "275");

        UiLayoutState state = new AdaptiveLayoutService().loadState();

        assertEquals(UiLayoutMode.COMPACT, state.densityMode());
        assertTrue(state.leftPanelCollapsed());
        assertFalse(state.rightPanelCollapsed());
        assertEquals(310.0, state.leftPanelWidth());
        assertEquals(275.0, state.rightPanelWidth());
    }

    @Test
    void applyWindowWidthPolicyAutoCollapsesRightPanelInCompactMode() {
        AdaptiveLayoutService service = new AdaptiveLayoutService();
        UiLayoutState initial = UiLayoutState.defaults().withRightPanelCollapsed(false);

        UiLayoutState updated = service.applyWindowWidthPolicy(initial, 1200.0);

        assertEquals(UiLayoutBreakpoint.COMPACT, updated.breakpoint());
        assertTrue(updated.rightPanelCollapsed());
    }

    @Test
    void saveStatePersistsAndLoadRestoresNormalizedSnapshot() {
        AdaptiveLayoutService service = new AdaptiveLayoutService();
        UiLayoutState persisted = new UiLayoutState(
            UiLayoutBreakpoint.WIDE,
            UiLayoutMode.COMPACT,
            true,
            true,
            312.0,
            448.0
        );

        service.saveState(persisted);
        UiLayoutState loaded = service.loadState();

        assertEquals(UiLayoutMode.COMPACT, loaded.densityMode());
        assertTrue(loaded.leftPanelCollapsed());
        assertTrue(loaded.rightPanelCollapsed());
        assertEquals(312.0, loaded.leftPanelWidth());
        assertEquals(448.0, loaded.rightPanelWidth());
    }

    private void snapshotConfig() {
        snapshot.clear();
        for (String key : CONFIG_KEYS) {
            snapshot.put(key, ConfigManager.getProperty(key));
        }
    }

    private void restoreConfig() {
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            setRuntimeConfig(entry.getKey(), entry.getValue());
        }
    }

    private void setRuntimeConfig(String key, String value) {
        Properties properties = runtimeProperties();
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }

    private Properties runtimeProperties() {
        try {
            return (Properties) PROPERTIES_FIELD.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Unable to access ConfigManager.properties", ex);
        }
    }

    private static Field resolvePropertiesField() {
        try {
            Field field = ConfigManager.class.getDeclaredField("properties");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to access ConfigManager.properties field", ex);
        }
    }
}
