package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.navigation.SidebarRailDomain;

/**
 * Adaptive display policy for the context sidebar (right side of navigation rail).
 */
public record ContextSidebarDisplayPolicy(
    UiLayoutBreakpoint breakpoint,
    NavSurfaceHeightBand heightBand,
    UiLayoutMode densityMode,
    LeftPanelSidebarMode sidebarMode,
    SidebarRailDomain activeRailDomain,
    boolean collapsed,
    boolean overlayOnDemand,
    boolean internalScrollEnabled,
    boolean showPinnedTopZone,
    boolean compactPinnedTopZone,
    boolean aggressiveCompaction,
    boolean showFavorites,
    boolean showRecent,
    boolean showInlineHelperHints,
    int quickActionLimit,
    int maxDomainListRowsBeforeScroll
) {
    public ContextSidebarDisplayPolicy {
        breakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        heightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;
        densityMode = densityMode == null ? UiLayoutMode.COMFORTABLE : densityMode;
        sidebarMode = sidebarMode == null ? LeftPanelSidebarMode.COLLAPSIBLE : sidebarMode;
        activeRailDomain = activeRailDomain == null ? SidebarRailDomain.WORK : activeRailDomain;
        quickActionLimit = Math.max(1, quickActionLimit);
        maxDomainListRowsBeforeScroll = Math.max(4, maxDomainListRowsBeforeScroll);
    }

    public boolean heightCompactionApplied() {
        return heightBand.isLowHeight() || compactPinnedTopZone || aggressiveCompaction;
    }
}

