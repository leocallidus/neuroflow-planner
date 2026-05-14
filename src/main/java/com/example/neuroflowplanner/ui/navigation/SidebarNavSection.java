package com.example.neuroflowplanner.ui.navigation;

import java.util.Locale;

/**
 * Sidebar section metadata used by navigation model and rendering adapters.
 */
public record SidebarNavSection(
    String id,
    String label,
    int order,
    SidebarNavZone zone,
    boolean collapsible,
    boolean defaultExpanded
) {
    public SidebarNavSection {
        id = normalizeId(id);
        label = normalizeLabel(label, id);
        order = Math.max(0, order);
        zone = zone == null ? SidebarNavZone.CORE : zone;
    }

    private static String normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "unknown";
        }
        return rawId.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeLabel(String rawLabel, String fallback) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return fallback;
        }
        return rawLabel.trim();
    }
}
