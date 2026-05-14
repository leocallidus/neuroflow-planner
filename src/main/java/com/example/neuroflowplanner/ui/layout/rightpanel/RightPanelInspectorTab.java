package com.example.neuroflowplanner.ui.layout.rightpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;

import java.util.List;
import java.util.Locale;

/**
 * Canonical inspector tabs for the future right-panel tabbed inspector UX.
 */
public enum RightPanelInspectorTab {
    PROPERTIES("properties", "Свойства", "Свойства"),
    DESCRIPTION("description", "Описание", "Описание"),
    ANALYTICS("analytics", "ИИ-Анализ & График", "ИИ+График");

    private static final List<RightPanelInspectorTab> BASELINE_ORDER = List.of(
        PROPERTIES,
        DESCRIPTION,
        ANALYTICS
    );

    private final String id;
    private final String label;
    private final String compactLabel;

    RightPanelInspectorTab(String id, String label, String compactLabel) {
        this.id = id;
        this.label = label;
        this.compactLabel = compactLabel;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String compactLabel() {
        return compactLabel;
    }

    public String resolveLabel(UiLayoutBreakpoint breakpoint) {
        UiLayoutBreakpoint safe = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        return safe == UiLayoutBreakpoint.COMPACT ? compactLabel : label;
    }

    public static List<RightPanelInspectorTab> baselineOrder() {
        return BASELINE_ORDER;
    }

    public static RightPanelInspectorTab resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return PROPERTIES;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "properties", "property", "details" -> PROPERTIES;
            case "description", "desc" -> DESCRIPTION;
            case "analytics", "analysis", "ai", "path", "ai+path", "ai-path" -> ANALYTICS;
            default -> PROPERTIES;
        };
    }
}
