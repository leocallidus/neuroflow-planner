package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.navigation.SidebarRailDomain;

/**
 * Display metadata for a navigation rail domain button/section.
 */
public record NavigationRailSection(
    SidebarRailDomain domain,
    int order,
    String railLabel,
    String railTooltipLabel,
    String contextHeaderLabel,
    String icon
) {
    public NavigationRailSection {
        domain = domain == null ? SidebarRailDomain.WORK : domain;
        order = Math.max(0, order);
        railLabel = normalize(railLabel, domain.label());
        railTooltipLabel = normalize(railTooltipLabel, domain.railTooltipLabel());
        contextHeaderLabel = normalize(contextHeaderLabel, domain.contextHeaderLabel());
        icon = normalize(icon, domain.icon());
    }

    private static String normalize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return raw.trim();
    }
}

