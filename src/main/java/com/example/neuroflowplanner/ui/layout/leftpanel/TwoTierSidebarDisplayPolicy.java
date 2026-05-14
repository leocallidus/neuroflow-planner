package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.navigation.SidebarRailDomain;

import java.util.List;

/**
 * Unified adaptive display policy for a two-tier sidebar (navigation rail + context sidebar).
 */
public record TwoTierSidebarDisplayPolicy(
    UiLayoutBreakpoint breakpoint,
    NavSurfaceHeightBand heightBand,
    UiLayoutMode densityMode,
    LeftPanelSidebarMode sidebarMode,
    List<NavigationRailSection> visibleRailSections,
    SidebarRailDomain activeRailDomain,
    ContextSidebarDisplayPolicy contextSidebarPolicy,
    boolean heightCompactionApplied,
    boolean aggressiveCompaction
) {
    public TwoTierSidebarDisplayPolicy {
        breakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        heightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;
        densityMode = densityMode == null ? UiLayoutMode.COMFORTABLE : densityMode;
        sidebarMode = sidebarMode == null ? LeftPanelSidebarMode.COLLAPSIBLE : sidebarMode;
        visibleRailSections = visibleRailSections == null ? List.of() : List.copyOf(visibleRailSections);
        activeRailDomain = activeRailDomain == null ? SidebarRailDomain.WORK : activeRailDomain;
    }

    public boolean railContains(SidebarRailDomain domain) {
        if (domain == null) {
            return false;
        }
        return visibleRailSections.stream().anyMatch(section -> section.domain() == domain);
    }
}

