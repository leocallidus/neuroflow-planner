package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.navigation.SidebarRailDomain;
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

class TwoTierSidebarPolicyServiceTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
        UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_DISMISSED_HINTS,
        UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN,
        UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED
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
    void resolveRailVisibleSectionsKeepsAllDomainsVisibleAcrossHeightBands() {
        TwoTierSidebarPolicyService service = new TwoTierSidebarPolicyService();
        NavigationRailState state = service.defaultState();

        List<NavigationRailSection> wideTall = service.resolveRailVisibleSections(
            state, UiLayoutBreakpoint.WIDE, NavSurfaceHeightBand.TALL
        );
        List<NavigationRailSection> compactVeryLow = service.resolveRailVisibleSections(
            state, UiLayoutBreakpoint.COMPACT, NavSurfaceHeightBand.VERY_LOW_HEIGHT
        );

        assertEquals(5, wideTall.size());
        assertEquals(5, compactVeryLow.size());
        assertEquals(SidebarRailDomain.WORK, wideTall.get(0).domain());
        assertEquals(SidebarRailDomain.RECENT, wideTall.get(1).domain());
        assertEquals("Рабочие сценарии", wideTall.get(0).railTooltipLabel());
        assertEquals(SidebarRailDomain.SYSTEM, wideTall.get(4).domain());
    }

    @Test
    void resolveActiveContextPanelUsesPersistedDomainAndFallsBackToWork() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "analytics");

        TwoTierSidebarPolicyService service = new TwoTierSidebarPolicyService();
        NavigationRailState loaded = service.loadState();

        assertEquals(SidebarRailDomain.ANALYTICS, loaded.activeRailDomain());
        assertEquals(
            SidebarRailDomain.ANALYTICS,
            service.resolveActiveContextPanel(loaded, UiLayoutBreakpoint.WIDE, NavSurfaceHeightBand.TALL)
        );

        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "unknown-domain");
        NavigationRailState invalid = service.loadState();
        assertEquals(SidebarRailDomain.WORK, invalid.activeRailDomain());
    }

    @Test
    void resolveContextSidebarLayoutAppliesWidthAndHeightCompactionPolicy() {
        TwoTierSidebarPolicyService service = new TwoTierSidebarPolicyService();
        NavigationRailState state = NavigationRailState.empty().withActiveRailDomain(SidebarRailDomain.TOOLS);

        ContextSidebarDisplayPolicy wideTall = service.resolveContextSidebarLayout(
            state, UiLayoutBreakpoint.WIDE, NavSurfaceHeightBand.TALL
        );
        ContextSidebarDisplayPolicy wideVeryLow = service.resolveContextSidebarLayout(
            state, UiLayoutBreakpoint.WIDE, NavSurfaceHeightBand.VERY_LOW_HEIGHT
        );
        ContextSidebarDisplayPolicy wideLow = service.resolveContextSidebarLayout(
            state, UiLayoutBreakpoint.WIDE, NavSurfaceHeightBand.LOW_HEIGHT
        );
        ContextSidebarDisplayPolicy compactLow = service.resolveContextSidebarLayout(
            state, UiLayoutBreakpoint.COMPACT, NavSurfaceHeightBand.LOW_HEIGHT
        );
        ContextSidebarDisplayPolicy compactVeryLow = service.resolveContextSidebarLayout(
            state, UiLayoutBreakpoint.COMPACT, NavSurfaceHeightBand.VERY_LOW_HEIGHT
        );

        assertEquals(LeftPanelSidebarMode.PINNED, wideTall.sidebarMode());
        assertEquals(SidebarRailDomain.TOOLS, wideTall.activeRailDomain());
        assertFalse(wideTall.compactPinnedTopZone());
        assertTrue(wideTall.showInlineHelperHints());
        assertTrue(wideTall.showRecent());
        assertTrue(wideTall.showFavorites());

        assertTrue(wideVeryLow.compactPinnedTopZone());
        assertTrue(wideVeryLow.aggressiveCompaction());
        assertFalse(wideVeryLow.showFavorites());
        assertFalse(wideVeryLow.showRecent());
        assertEquals(3, wideVeryLow.quickActionLimit());
        assertEquals(6, wideVeryLow.maxDomainListRowsBeforeScroll());

        assertTrue(wideLow.compactPinnedTopZone());
        assertFalse(wideLow.aggressiveCompaction());
        assertFalse(wideLow.showFavorites());
        assertFalse(wideLow.showRecent());
        assertEquals(3, wideLow.quickActionLimit());
        assertEquals(8, wideLow.maxDomainListRowsBeforeScroll());

        assertEquals(LeftPanelSidebarMode.OVERLAY, compactLow.sidebarMode());
        assertTrue(compactLow.overlayOnDemand());
        assertFalse(compactLow.collapsed());
        assertFalse(compactLow.aggressiveCompaction());
        assertTrue(compactLow.compactPinnedTopZone());
        assertEquals(3, compactLow.quickActionLimit());
        assertEquals(7, compactLow.maxDomainListRowsBeforeScroll());

        assertEquals(LeftPanelSidebarMode.OVERLAY, compactVeryLow.sidebarMode());
        assertTrue(compactVeryLow.overlayOnDemand());
        assertFalse(compactVeryLow.collapsed());
        assertTrue(compactVeryLow.aggressiveCompaction());
        assertFalse(compactVeryLow.showInlineHelperHints());
    }

    @Test
    void resolveHeightCompactionPlanBuildsCompositePolicy() {
        TwoTierSidebarPolicyService service = new TwoTierSidebarPolicyService();
        NavigationRailState state = NavigationRailState.empty()
            .withActiveRailDomain(SidebarRailDomain.SYSTEM)
            .withContextSidebarCollapsed(true);

        TwoTierSidebarDisplayPolicy policy = service.resolveHeightCompactionPlan(
            state, UiLayoutBreakpoint.NORMAL, NavSurfaceHeightBand.LOW_HEIGHT
        );

        assertEquals(UiLayoutBreakpoint.NORMAL, policy.breakpoint());
        assertTrue(policy.railContains(SidebarRailDomain.SYSTEM));
        assertEquals(SidebarRailDomain.SYSTEM, policy.activeRailDomain());
        assertTrue(policy.heightCompactionApplied());
        assertTrue(policy.contextSidebarPolicy().compactPinnedTopZone());
        assertTrue(policy.contextSidebarPolicy().collapsed());
    }

    @Test
    void loadAndUpdateStatePersistsActiveDomainCollapsedStateAndDismissedHints() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "system");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED, "true");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_DISMISSED_HINTS, "sidebar.rail.novice");

        TwoTierSidebarPolicyService service = new TwoTierSidebarPolicyService();
        NavigationRailState loaded = service.loadState();

        assertEquals(SidebarRailDomain.SYSTEM, loaded.activeRailDomain());
        assertTrue(loaded.contextSidebarCollapsed());
        assertTrue(loaded.isHelperHintDismissed("sidebar.rail.novice"));

        NavigationRailState updated = service.updateActiveRailDomain(loaded, SidebarRailDomain.ANALYTICS);
        updated = service.updateContextSidebarCollapsed(updated, false);
        updated = service.updateDismissedHelperHint(updated, UiNavigationSurfacePolicyService.HELPER_HINT_SIDEBAR_GUIDED, true);

        NavigationRailState reloaded = service.loadState();
        assertEquals(SidebarRailDomain.ANALYTICS, reloaded.activeRailDomain());
        assertFalse(reloaded.contextSidebarCollapsed());
        assertTrue(reloaded.isHelperHintDismissed("sidebar.rail.novice"));
        assertTrue(reloaded.isHelperHintDismissed(UiNavigationSurfacePolicyService.HELPER_HINT_SIDEBAR_GUIDED));
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
