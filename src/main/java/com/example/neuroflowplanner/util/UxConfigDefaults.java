package com.example.neuroflowplanner.util;

public final class UxConfigDefaults {
    public static final String CONFIG_UX_UNDO_MAX_HISTORY = "ux.undo.maxHistory";
    public static final String CONFIG_UX_LAYOUT_DENSITY_MODE = "ux.layout.density.mode";
    public static final String CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED = "ux.layout.state.leftPanelCollapsed";
    public static final String CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED = "ux.layout.state.rightPanelCollapsed";
    public static final String CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_WIDTH = "ux.layout.state.leftPanelWidth";
    public static final String CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH = "ux.layout.state.rightPanelWidth";
    public static final String CONFIG_UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS = "ux.rightpanel.state.expandedSections";
    public static final String CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB = "ux.rightpanel.inspector.state.activeTab";
    public static final String CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES = "ux.rightpanel.inspector.state.expandedSubstates";
    public static final String CONFIG_UX_INLINE_OVERLAY_STATE_ACTIVE_TAB = "ux.inlineoverlay.state.activeTab";
    public static final String CONFIG_UX_INLINE_OVERLAY_STATE_TAB_ORDER = "ux.inlineoverlay.state.tabOrder";
    public static final String CONFIG_UX_SIDEBAR_MAX_QUICK_ITEMS = "ux.sidebar.max.quick.items";
    public static final String CONFIG_UX_SIDEBAR_MAX_FAVORITES = "ux.sidebar.max.favorites";
    public static final String CONFIG_UX_SIDEBAR_MAX_RECENT = "ux.sidebar.max.recent";
    public static final String CONFIG_UX_SIDEBAR_STATE_EXPANDED_SECTIONS = "ux.sidebar.state.expandedSections";
    public static final String CONFIG_UX_SIDEBAR_STATE_FAVORITES = "ux.sidebar.state.favorites";
    public static final String CONFIG_UX_SIDEBAR_STATE_RECENT = "ux.sidebar.state.recent";
    public static final String CONFIG_UX_NAV_SURFACES_STATE_COMPACTED_ZONES = "ux.navsurfaces.state.compactedZones";
    public static final String CONFIG_UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE = "ux.commandPalette.state.lastViewMode";
    public static final String CONFIG_UX_NAV_SURFACES_STATE_DISMISSED_HINTS = "ux.navsurfaces.state.dismissedHints";
    public static final String CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN = "ux.twoTierSidebar.state.activeRailDomain";
    public static final String CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED = "ux.twoTierSidebar.state.contextCollapsed";

    public static final int UX_UNDO_MAX_HISTORY_DEFAULT = 100;
    public static final int UX_UNDO_MAX_HISTORY_MIN = 10;
    public static final int UX_UNDO_MAX_HISTORY_MAX = 1000;

    public static final String UX_LAYOUT_DENSITY_MODE_COMFORTABLE = "comfortable";
    public static final String UX_LAYOUT_DENSITY_MODE_COMPACT = "compact";
    public static final String UX_LAYOUT_DENSITY_MODE_DEFAULT = UX_LAYOUT_DENSITY_MODE_COMFORTABLE;
    public static final boolean UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED_DEFAULT = false;
    public static final boolean UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED_DEFAULT = false;
    public static final double UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_DEFAULT = 260.0;
    public static final double UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_MIN = 56.0;
    public static final double UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_MAX = 420.0;
    public static final double UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_DEFAULT = 320.0;
    public static final double UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MIN = 240.0;
    public static final double UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX = 560.0;
    public static final String UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS_DEFAULT = "details,description";
    public static final String UX_RIGHT_PANEL_STATE_ACTIVE_TAB_DEFAULT = "details";
    public static final String UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT = "properties";
    public static final String UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES_DEFAULT = "__none__";
    public static final String UX_INLINE_OVERLAY_STATE_ACTIVE_TAB_DEFAULT = "__none__";
    public static final String UX_INLINE_OVERLAY_STATE_TAB_ORDER_DEFAULT = "__none__";
    public static final int UX_LAYOUT_BREAKPOINT_NORMAL_MIN_WIDTH = 1366;
    public static final int UX_LAYOUT_BREAKPOINT_WIDE_MIN_WIDTH = 1600;
    public static final int UX_SIDEBAR_MAX_QUICK_ITEMS_DEFAULT = 8;
    public static final int UX_SIDEBAR_MAX_QUICK_ITEMS_MIN = 3;
    public static final int UX_SIDEBAR_MAX_QUICK_ITEMS_MAX = 20;
    public static final int UX_SIDEBAR_MAX_FAVORITES_DEFAULT = 10;
    public static final int UX_SIDEBAR_MAX_FAVORITES_MIN = 3;
    public static final int UX_SIDEBAR_MAX_FAVORITES_MAX = 30;
    public static final int UX_SIDEBAR_MAX_RECENT_DEFAULT = 12;
    public static final int UX_SIDEBAR_MAX_RECENT_MIN = 3;
    public static final int UX_SIDEBAR_MAX_RECENT_MAX = 50;
    public static final String UX_SIDEBAR_STATE_EXPANDED_SECTIONS_DEFAULT = "history,main,tools";
    public static final String UX_SIDEBAR_STATE_FAVORITES_DEFAULT = "__none__";
    public static final String UX_SIDEBAR_STATE_RECENT_DEFAULT = "__none__";
    public static final String UX_NAV_SURFACES_STATE_COMPACTED_ZONES_DEFAULT = "__none__";
    public static final String UX_COMMAND_PALETTE_VIEW_MODE_GUIDED = "guided";
    public static final String UX_COMMAND_PALETTE_VIEW_MODE_RECENT = "recent";
    public static final String UX_COMMAND_PALETTE_VIEW_MODE_SEARCH = "search";
    public static final String UX_COMMAND_PALETTE_VIEW_MODE_CONTEXT = "context";
    public static final String UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE_DEFAULT = UX_COMMAND_PALETTE_VIEW_MODE_GUIDED;
    public static final String UX_NAV_SURFACES_STATE_DISMISSED_HINTS_DEFAULT = "__none__";
    public static final String UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN_DEFAULT = "work";
    public static final boolean UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED_DEFAULT = false;
    public static final String UX_COLLECTION_NONE_MARKER = "__none__";

    private UxConfigDefaults() {
    }
}
