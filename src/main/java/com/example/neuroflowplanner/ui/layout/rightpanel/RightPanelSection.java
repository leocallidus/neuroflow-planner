package com.example.neuroflowplanner.ui.layout.rightpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;

import java.util.Locale;

/**
 * Declarative right-panel section metadata used by adaptive display policy.
 */
public record RightPanelSection(
    String id,
    RightPanelSectionPriority priority,
    boolean collapsible,
    boolean defaultExpanded,
    UiLayoutBreakpoint minBreakpoint
) {
    public RightPanelSection {
        id = normalizeId(id);
        priority = priority == null ? RightPanelSectionPriority.SECONDARY : priority;
        minBreakpoint = minBreakpoint == null ? UiLayoutBreakpoint.COMPACT : minBreakpoint;
    }

    public boolean supportedAt(UiLayoutBreakpoint breakpoint) {
        UiLayoutBreakpoint resolved = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        return resolved.ordinal() >= minBreakpoint.ordinal();
    }

    private static String normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "unknown";
        }
        return rawId.trim().toLowerCase(Locale.ROOT);
    }
}
