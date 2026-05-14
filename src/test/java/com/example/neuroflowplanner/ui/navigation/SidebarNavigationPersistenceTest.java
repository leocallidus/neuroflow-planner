package com.example.neuroflowplanner.ui.navigation;

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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarNavigationPersistenceTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_EXPANDED_SECTIONS,
        UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_FAVORITES,
        UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_RECENT
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
    void loadStateReadsPersistedCollectionsWithNormalization() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_EXPANDED_SECTIONS, "history, main,history");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_FAVORITES, "main.system.export, MAIN.SYSTEM.EXPORT");
        setRuntimeConfig(
            UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_RECENT,
            "main.task.panel,main.system.export,main.task.panel"
        );

        SidebarNavState state = new SidebarNavigationService().loadState();

        assertEquals(Set.of("history", "main"), state.expandedSectionIds());
        assertEquals(Set.of("main.system.export"), state.favoriteActionIds());
        assertEquals(List.of("main.task.panel", "main.system.export"), state.recentActionIds());
    }

    @Test
    void loadStateTreatsNoneMarkerAsEmptyCollections() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_EXPANDED_SECTIONS, UxConfigDefaults.UX_COLLECTION_NONE_MARKER);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_FAVORITES, UxConfigDefaults.UX_COLLECTION_NONE_MARKER);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_RECENT, UxConfigDefaults.UX_COLLECTION_NONE_MARKER);

        SidebarNavState state = new SidebarNavigationService().loadState();

        assertTrue(state.expandedSectionIds().isEmpty());
        assertTrue(state.favoriteActionIds().isEmpty());
        assertTrue(state.recentActionIds().isEmpty());
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
