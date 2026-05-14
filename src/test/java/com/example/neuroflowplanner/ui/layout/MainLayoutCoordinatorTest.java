package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteViewMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.LeftPanelDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.LeftPanelSidebarMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.NavSurfaceHeightBand;
import com.example.neuroflowplanner.ui.layout.leftpanel.TwoTierSidebarDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelInspectorTab;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelTabHeightBand;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelTab;
import com.example.neuroflowplanner.ui.navigation.SidebarNavZone;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainLayoutCoordinatorTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_WIDTH,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH,
        UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_COMPACTED_ZONES,
        UxConfigDefaults.CONFIG_UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE,
        UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_DISMISSED_HINTS,
        UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN,
        UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS,
        UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB,
        UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES
    );

    private final Map<String, String> snapshot = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        snapshotConfig();
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMFORTABLE);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_WIDTH, "260");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH, "320");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_COMPACTED_ZONES, UxConfigDefaults.UX_NAV_SURFACES_STATE_COMPACTED_ZONES_DEFAULT);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE, UxConfigDefaults.UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE_DEFAULT);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_DISMISSED_HINTS, UxConfigDefaults.UX_NAV_SURFACES_STATE_DISMISSED_HINTS_DEFAULT);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, UxConfigDefaults.UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN_DEFAULT);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED, String.valueOf(UxConfigDefaults.UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED_DEFAULT));
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS, UxConfigDefaults.UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS_DEFAULT);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB, UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES, UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES_DEFAULT);
    }

    @AfterEach
    void tearDown() {
        restoreConfig();
    }

    @Test
    void compactPolicyCollapsesBothPanelsForSmallWidths() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());

        boolean breakpointChanged = coordinator.applyWindowWidthPolicy(1280.0);
        UiLayoutState state = coordinator.state();

        assertTrue(breakpointChanged);
        assertEquals(UiLayoutBreakpoint.COMPACT, state.breakpoint());
        assertTrue(state.leftPanelCollapsed(), "Compact policy should collapse left rail");
        assertTrue(state.rightPanelCollapsed(), "Compact policy should collapse right drawer");
    }

    @Test
    void rightPanelToggleIsIgnoredInPinnedMode() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());
        coordinator.applyWindowWidthPolicy(1700.0);

        coordinator.toggleRightPanelCollapsed();

        assertEquals(UiLayoutBreakpoint.WIDE, coordinator.state().breakpoint());
        assertFalse(coordinator.state().rightPanelCollapsed(), "Pinned mode must ignore manual right-panel toggle");
    }

    @Test
    void rightPanelToggleWorksInNormalMode() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());
        coordinator.applyWindowWidthPolicy(1400.0);

        coordinator.toggleRightPanelCollapsed();

        assertEquals(UiLayoutBreakpoint.NORMAL, coordinator.state().breakpoint());
        assertTrue(coordinator.state().rightPanelCollapsed(), "Collapsible mode must allow manual right-panel toggle");
    }

    @Test
    void leftNavPolicyUsesWidthAndHeightAndCanAutoCollapseInOverlay() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());

        coordinator.applyWindowWidthPolicy(1729.0);
        assertEquals(LeftPanelSidebarMode.PINNED, coordinator.leftPanelDisplayPolicy().sidebarMode());
        assertEquals(NavSurfaceHeightBand.LOW_HEIGHT, coordinator.navSurfaceHeightBand());

        boolean changed = coordinator.applyWindowHeightPolicy(650.0);

        assertTrue(changed);
        assertEquals(NavSurfaceHeightBand.VERY_LOW_HEIGHT, coordinator.navSurfaceHeightBand());
        assertEquals(LeftPanelSidebarMode.COLLAPSIBLE, coordinator.leftPanelDisplayPolicy().sidebarMode());
        assertFalse(coordinator.leftPanelDisplayPolicy().isZoneVisible(SidebarNavZone.ADVANCED));

        coordinator.applyWindowWidthPolicy(1366.0);
        coordinator.applyWindowHeightPolicy(650.0);
        assertEquals(LeftPanelSidebarMode.OVERLAY, coordinator.leftPanelDisplayPolicy().sidebarMode());
        assertEquals(LeftPanelSidebarMode.OVERLAY, coordinator.contextSidebarDisplayPolicy().sidebarMode());
        assertTrue(coordinator.state().leftPanelCollapsed(), "Overlay sidebar mode should protect workspace by auto-collapse");
    }

    @Test
    void compactOverlayAllowsOnDemandLeftSidebarToStayOpenAcrossHeightRefresh() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());

        coordinator.applyWindowWidthPolicy(1024.0);
        assertEquals(UiLayoutBreakpoint.COMPACT, coordinator.state().breakpoint());
        assertTrue(coordinator.state().leftPanelCollapsed());

        coordinator.setContextSidebarCollapsed(false);
        assertFalse(coordinator.state().leftPanelCollapsed());

        coordinator.applyWindowHeightPolicy(650.0);

        assertEquals(NavSurfaceHeightBand.VERY_LOW_HEIGHT, coordinator.navSurfaceHeightBand());
        assertEquals(LeftPanelSidebarMode.OVERLAY, coordinator.contextSidebarDisplayPolicy().sidebarMode());
        assertFalse(coordinator.state().leftPanelCollapsed());
        assertFalse(coordinator.contextSidebarDisplayPolicy().collapsed());
    }

    @Test
    void twoTierSidebarPolicyStaysSyncedWithAdaptiveShellAndRailSelection() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());

        coordinator.selectNavigationRailDomain(SidebarRailDomain.ANALYTICS);
        coordinator.applyWindowWidthPolicy(1729.0);
        coordinator.applyWindowHeightPolicy(650.0);

        TwoTierSidebarDisplayPolicy policy = coordinator.twoTierSidebarDisplayPolicy();
        LeftPanelDisplayPolicy leftPolicy = coordinator.leftPanelDisplayPolicy();

        assertEquals(SidebarRailDomain.ANALYTICS, policy.activeRailDomain());
        assertEquals(coordinator.navSurfaceHeightBand(), policy.heightBand());
        assertEquals(leftPolicy.sidebarMode(), policy.sidebarMode());
        assertEquals(coordinator.state().leftPanelCollapsed(), policy.contextSidebarPolicy().collapsed());

        coordinator.setContextSidebarCollapsed(false);
        assertFalse(coordinator.state().leftPanelCollapsed(), "Context sidebar collapse state should sync back to shell");
    }

    @Test
    void coordinatorPersistsLeftNavSurfaceUxStateAndPaletteOpenState() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());

        coordinator.setLeftPanelZoneCompacted(SidebarNavZone.ADVANCED, true);
        coordinator.setCommandPaletteViewMode(CommandPaletteViewMode.RECENT);
        coordinator.setNavHelperHintDismissed("palette.empty.state", true);
        coordinator.selectNavigationRailDomain(SidebarRailDomain.SYSTEM);
        coordinator.setContextSidebarCollapsed(true);
        coordinator.setCommandPaletteOverlayOpen(true);
        coordinator.saveState();

        MainLayoutCoordinator reloaded = new MainLayoutCoordinator(new AdaptiveLayoutService());

        assertTrue(reloaded.leftPanelLayoutState().isZoneCompacted(SidebarNavZone.ADVANCED));
        assertEquals(CommandPaletteViewMode.RECENT, reloaded.leftPanelLayoutState().lastPaletteViewMode());
        assertTrue(reloaded.leftPanelLayoutState().isHelperHintDismissed("palette.empty.state"));
        assertEquals(SidebarRailDomain.SYSTEM, reloaded.navigationRailState().activeRailDomain());
        assertTrue(reloaded.navigationRailState().contextSidebarCollapsed());
        assertFalse(reloaded.isCommandPaletteOverlayOpen(), "Overlay open state should be runtime-only and not persisted");
    }

    @Test
    void rightInspectorPolicyStaysSyncedWithLegacyRightPanelTabSelection() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());

        coordinator.selectRightPanelTab(RightPanelTab.AI);

        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.rightPanelInspectorDisplayPolicy().activeTab());

        coordinator.selectRightInspectorTab(RightPanelInspectorTab.DESCRIPTION);

        assertEquals(RightPanelInspectorTab.DESCRIPTION, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(RightPanelTab.DETAILS, coordinator.rightPanelLayoutState().activeTab());
    }

    @Test
    void rightInspectorHeightPolicyRefreshesBandWithoutStaleState() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());
        coordinator.applyWindowWidthPolicy(1729.0);
        coordinator.applyWindowHeightPolicy(900.0);

        boolean changed = coordinator.applyRightPanelInspectorHeightPolicy(640.0);

        assertTrue(changed);
        assertEquals(RightPanelTabHeightBand.VERY_LOW_HEIGHT, coordinator.rightPanelInspectorDisplayPolicy().heightBand());
        assertEquals(UiRightContextMode.PINNED, coordinator.rightPanelInspectorDisplayPolicy().mode());
    }

    @Test
    void rightInspectorActiveTabPersistsAcrossResizeAndModeSwitch() {
        MainLayoutCoordinator coordinator = new MainLayoutCoordinator(new AdaptiveLayoutService());
        coordinator.applyWindowWidthPolicy(1366.0);
        coordinator.selectRightInspectorTab(RightPanelInspectorTab.ANALYTICS);

        assertEquals(UiRightContextMode.COLLAPSIBLE, coordinator.snapshot().rightContextMode());
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());

        coordinator.applyWindowWidthPolicy(1280.0);
        assertEquals(UiRightContextMode.OVERLAY, coordinator.snapshot().rightContextMode());
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.rightPanelInspectorDisplayPolicy().activeTab());

        coordinator.toggleRightPanelCollapsed();
        assertFalse(coordinator.state().rightPanelCollapsed(), "Overlay mode should allow opening right panel");
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());

        coordinator.applyWindowWidthPolicy(1729.0);
        assertEquals(UiRightContextMode.PINNED, coordinator.snapshot().rightContextMode());
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());

        coordinator.applyWindowWidthPolicy(1366.0);
        assertEquals(UiRightContextMode.COLLAPSIBLE, coordinator.snapshot().rightContextMode());
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.rightPanelInspectorDisplayPolicy().activeTab());
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
