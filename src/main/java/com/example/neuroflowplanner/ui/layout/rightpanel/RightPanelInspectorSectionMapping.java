package com.example.neuroflowplanner.ui.layout.rightpanel;

import java.util.Locale;

/**
 * IA mapping entry from legacy right-panel section id to a canonical inspector tab and tab-local priority.
 */
public record RightPanelInspectorSectionMapping(
    String sectionId,
    RightPanelInspectorTab inspectorTab,
    RightPanelSectionPriority contentPriority,
    int order
) {
    public RightPanelInspectorSectionMapping {
        sectionId = normalizeId(sectionId);
        inspectorTab = inspectorTab == null ? RightPanelInspectorTab.PROPERTIES : inspectorTab;
        contentPriority = contentPriority == null ? RightPanelSectionPriority.SECONDARY : contentPriority;
        order = Math.max(0, order);
    }

    private static String normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "unknown";
        }
        return rawId.trim().toLowerCase(Locale.ROOT);
    }
}
