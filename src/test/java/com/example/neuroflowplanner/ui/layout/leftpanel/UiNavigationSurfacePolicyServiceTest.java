package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.navigation.SidebarNavZone;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiNavigationSurfacePolicyServiceTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
        UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_COMPACTED_ZONES,
        UxConfigDefaults.CONFIG_UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE,
        UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_DISMISSED_HINTS
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
    void resolveSidebarModeUsesWidthAndHeightContract() {
        UiNavigationSurfacePolicyService service = new UiNavigationSurfacePolicyService();

        assertEquals(
            LeftPanelSidebarMode.OVERLAY,
            service.resolveSidebarMode(UiLayoutBreakpoint.COMPACT, NavSurfaceHeightBand.TALL)
        );
        assertEquals(
            LeftPanelSidebarMode.COLLAPSIBLE,
            service.resolveSidebarMode(UiLayoutBreakpoint.NORMAL, NavSurfaceHeightBand.LOW_HEIGHT)
        );
        assertEquals(
            LeftPanelSidebarMode.OVERLAY,
            service.resolveSidebarMode(UiLayoutBreakpoint.NORMAL, NavSurfaceHeightBand.VERY_LOW_HEIGHT)
        );
        assertEquals(
            LeftPanelSidebarMode.PINNED,
            service.resolveSidebarMode(UiLayoutBreakpoint.WIDE, NavSurfaceHeightBand.TALL)
        );
        assertEquals(
            LeftPanelSidebarMode.COLLAPSIBLE,
            service.resolveSidebarMode(UiLayoutBreakpoint.WIDE, NavSurfaceHeightBand.VERY_LOW_HEIGHT)
        );
    }

    @Test
    void resolveHeightBandKeepsThresholdBoundariesStable() {
        UiNavigationSurfacePolicyService service = new UiNavigationSurfacePolicyService();

        assertEquals(NavSurfaceHeightBand.LOW_HEIGHT, service.resolveHeightBand(Double.NaN));
        assertEquals(NavSurfaceHeightBand.VERY_LOW_HEIGHT, service.resolveHeightBand(699.0));
        assertEquals(NavSurfaceHeightBand.LOW_HEIGHT, service.resolveHeightBand(700.0));
        assertEquals(NavSurfaceHeightBand.LOW_HEIGHT, service.resolveHeightBand(849.0));
        assertEquals(NavSurfaceHeightBand.TALL, service.resolveHeightBand(850.0));
    }

    @Test
    void resolveSidebarVisibleZonesAppliesHeightAwareDegradation() {
        UiNavigationSurfacePolicyService service = new UiNavigationSurfacePolicyService();
        LeftPanelLayoutState state = service.defaultState();

        assertEquals(
            List.of(SidebarNavZone.QUICK, SidebarNavZone.CORE, SidebarNavZone.ADVANCED),
            service.resolveSidebarVisibleZones(state, UiLayoutBreakpoint.WIDE, NavSurfaceHeightBand.TALL)
        );
        assertEquals(
            List.of(SidebarNavZone.QUICK, SidebarNavZone.CORE),
            service.resolveSidebarVisibleZones(state, UiLayoutBreakpoint.WIDE, NavSurfaceHeightBand.VERY_LOW_HEIGHT)
        );
        assertEquals(
            List.of(SidebarNavZone.QUICK, SidebarNavZone.CORE),
            service.resolveSidebarVisibleZones(state, UiLayoutBreakpoint.NORMAL, NavSurfaceHeightBand.LOW_HEIGHT)
        );
        assertEquals(
            List.of(SidebarNavZone.QUICK, SidebarNavZone.CORE),
            service.resolveSidebarVisibleZones(state, UiLayoutBreakpoint.COMPACT, NavSurfaceHeightBand.TALL)
        );
    }

    @Test
    void resolvePaletteLayoutUsesHeightAwareCompactionAndHints() {
        UiNavigationSurfacePolicyService service = new UiNavigationSurfacePolicyService();
        LeftPanelLayoutState state = LeftPanelLayoutState.empty()
            .withLastPaletteViewMode(CommandPaletteViewMode.RECENT)
            .withHelperHintDismissed(UiNavigationSurfacePolicyService.HELPER_HINT_PALETTE_EMPTY_STATE, true);

        CommandPaletteDisplayPolicy wideTall = service.resolvePaletteLayout(
            state,
            UiLayoutBreakpoint.WIDE,
            NavSurfaceHeightBand.TALL
        );
        CommandPaletteDisplayPolicy compactLow = service.resolvePaletteLayout(
            state,
            UiLayoutBreakpoint.COMPACT,
            NavSurfaceHeightBand.VERY_LOW_HEIGHT
        );

        assertEquals(CommandPaletteViewMode.RECENT, wideTall.preferredViewMode());
        assertFalse(wideTall.compactRows());
        assertTrue(wideTall.showDescriptions());
        assertFalse(wideTall.showGuidedEmptyState());
        assertTrue(wideTall.maxResults() >= 16);

        assertTrue(compactLow.compactRows());
        assertFalse(compactLow.showDescriptions());
        assertTrue(compactLow.maxResults() <= 10);
        assertEquals(0, compactLow.exampleQueryCount());
    }

    @Test
    void resolveHeightCompactionPlanCombinesAutomaticAndPersistedCompaction() {
        UiNavigationSurfacePolicyService service = new UiNavigationSurfacePolicyService();
        LeftPanelLayoutState state = LeftPanelLayoutState.empty()
            .withCompactedZone(SidebarNavZone.CORE, true)
            .withHelperHintDismissed(UiNavigationSurfacePolicyService.HELPER_HINT_SIDEBAR_GUIDED, true);

        LeftPanelDisplayPolicy policy = service.resolveHeightCompactionPlan(
            state,
            UiLayoutBreakpoint.WIDE,
            NavSurfaceHeightBand.LOW_HEIGHT
        );

        assertEquals(LeftPanelSidebarMode.PINNED, policy.sidebarMode());
        assertTrue(policy.heightCompactionApplied());
        assertTrue(policy.isZoneVisible(SidebarNavZone.ADVANCED));
        assertTrue(policy.isZoneCompacted(SidebarNavZone.CORE));
        assertTrue(policy.isZoneCompacted(SidebarNavZone.ADVANCED));
        assertFalse(policy.showInlineNoviceGuidance());
        assertTrue(policy.palettePolicy().showRecentSection());
    }

    @Test
    void resolveHeightCompactionPlanKeepsCompactLowHeightReadable() {
        UiNavigationSurfacePolicyService service = new UiNavigationSurfacePolicyService();
        LeftPanelDisplayPolicy policy = service.resolveHeightCompactionPlan(
            LeftPanelLayoutState.empty(),
            UiLayoutBreakpoint.COMPACT,
            NavSurfaceHeightBand.LOW_HEIGHT
        );

        assertEquals(LeftPanelSidebarMode.OVERLAY, policy.sidebarMode());
        assertTrue(policy.heightCompactionApplied());
        assertFalse(policy.aggressiveCompaction());
        assertTrue(policy.isZoneCompacted(SidebarNavZone.CORE));
        assertEquals(4, policy.quickActionLimit());
    }

    @Test
    void loadAndUpdateStatePersistsCompactedZonesPaletteModeAndDismissedHints() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMPACT);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_COMPACTED_ZONES, "core,advanced");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE, "recent");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_DISMISSED_HINTS, "palette.empty.state");

        UiNavigationSurfacePolicyService service = new UiNavigationSurfacePolicyService();
        LeftPanelLayoutState loaded = service.loadState();

        assertTrue(loaded.isZoneCompacted(SidebarNavZone.CORE));
        assertTrue(loaded.isZoneCompacted(SidebarNavZone.ADVANCED));
        assertEquals(CommandPaletteViewMode.RECENT, loaded.lastPaletteViewMode());
        assertTrue(loaded.isHelperHintDismissed("palette.empty.state"));

        LeftPanelLayoutState updated = service.updateCompactedZone(loaded, SidebarNavZone.CORE, false);
        updated = service.updateLastPaletteViewMode(updated, CommandPaletteViewMode.CONTEXT);
        updated = service.updateDismissedHelperHint(updated, "sidebar.guided", true);

        LeftPanelLayoutState reloaded = service.loadState();
        assertFalse(reloaded.isZoneCompacted(SidebarNavZone.CORE));
        assertTrue(reloaded.isZoneCompacted(SidebarNavZone.ADVANCED));
        assertEquals(CommandPaletteViewMode.CONTEXT, reloaded.lastPaletteViewMode());
        assertTrue(reloaded.isHelperHintDismissed("palette.empty.state"));
        assertTrue(reloaded.isHelperHintDismissed("sidebar.guided"));
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
