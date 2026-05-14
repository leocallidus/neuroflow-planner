package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UxRolloutConfigMigrationTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final Method NORMALIZE_METHOD = resolveNormalizeMethod();
    private static final List<String> LEGACY_KEYS = List.of(
        "ux.undo.enabled",
        "ux.search.global.enabled",
        "ux.commandPalette.enabled",
        "ux.shortcuts.enabled",
        "ux.layout.adaptive.enabled",
        "ux.layout.obsidianInspired.enabled",
        "ux.layout.compact.autoCollapseRightPanel",
        "ux.sidebar.v2.enabled",
        "ux.sidebar.filter.enabled",
        "ux.sidebar.favorites.enabled",
        "ux.sidebar.recent.enabled"
    );
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
    void migrationRemovesLegacyRolloutKeysAndKeepsPreferenceValues() throws Exception {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMPACT);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED, "true");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_WIDTH, "300");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH, "340");
        for (String key : LEGACY_KEYS) {
            setRuntimeConfig(key, "true");
        }

        NORMALIZE_METHOD.invoke(null);

        for (String key : LEGACY_KEYS) {
            assertFalse(runtimeProperties().containsKey(key), "Legacy rollout key must be removed: " + key);
        }
        assertEquals(UiLayoutMode.COMPACT, UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode()));
        assertEquals(300.0, ConfigManager.getUxLayoutStateLeftPanelWidth());
        assertEquals(340.0, ConfigManager.getUxLayoutStateRightPanelWidth());
    }

    private void snapshotConfig() {
        snapshot.clear();
        for (String key : CONFIG_KEYS) {
            snapshot.put(key, ConfigManager.getProperty(key));
        }
        for (String key : LEGACY_KEYS) {
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

    private static Method resolveNormalizeMethod() {
        try {
            Method method = ConfigManager.class.getDeclaredMethod("normalizeLegacyUxRolloutProperties");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to access ConfigManager.normalizeLegacyUxRolloutProperties", ex);
        }
    }
}

