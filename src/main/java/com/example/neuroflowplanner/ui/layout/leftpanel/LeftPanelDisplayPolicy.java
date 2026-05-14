package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.navigation.SidebarNavZone;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Unified adaptive display policy for sidebar and command palette surfaces.
 */
public record LeftPanelDisplayPolicy(
    UiLayoutBreakpoint breakpoint,
    NavSurfaceHeightBand heightBand,
    UiLayoutMode densityMode,
    LeftPanelSidebarMode sidebarMode,
    List<SidebarNavZone> visibleZones,
    Set<SidebarNavZone> compactedZones,
    boolean heightCompactionApplied,
    boolean aggressiveCompaction,
    boolean pinnedQuickZone,
    boolean internalScrollEnabled,
    boolean showInlineNoviceGuidance,
    boolean palettePromotesAdvancedActions,
    int quickActionLimit,
    CommandPaletteDisplayPolicy palettePolicy
) {
    public LeftPanelDisplayPolicy {
        breakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        heightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;
        densityMode = densityMode == null ? UiLayoutMode.COMFORTABLE : densityMode;
        sidebarMode = sidebarMode == null ? LeftPanelSidebarMode.COLLAPSIBLE : sidebarMode;
        visibleZones = visibleZones == null ? List.of() : List.copyOf(visibleZones);
        compactedZones = compactedZones == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(compactedZones));
        quickActionLimit = Math.max(1, quickActionLimit);
    }

    public boolean isZoneVisible(SidebarNavZone zone) {
        return zone != null && visibleZones.contains(zone);
    }

    public boolean isZoneCompacted(SidebarNavZone zone) {
        return zone != null && compactedZones.contains(zone);
    }
}
