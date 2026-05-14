package com.example.neuroflowplanner.ui.navigation;

/**
 * Reusable mapping of an action to a navigation rail domain and context sidebar placement.
 */
public record SidebarRailActionMapping(
    String actionId,
    SidebarRailDomain railDomain,
    SidebarContextPlacement contextPlacement
) {
    public SidebarRailActionMapping {
        actionId = actionId == null ? "" : actionId.trim();
        railDomain = railDomain == null ? SidebarRailDomain.WORK : railDomain;
        contextPlacement = contextPlacement == null ? SidebarContextPlacement.DOMAIN_LIST : contextPlacement;
    }

    public boolean pinnedTopZone() {
        return contextPlacement == SidebarContextPlacement.PINNED_TOP_ZONE;
    }
}

