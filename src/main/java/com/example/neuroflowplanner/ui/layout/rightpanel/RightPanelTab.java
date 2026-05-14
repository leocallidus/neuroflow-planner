package com.example.neuroflowplanner.ui.layout.rightpanel;

import java.util.Locale;

/**
 * Compact segmented navigation target for right-panel heavy content.
 */
public enum RightPanelTab {
    DETAILS("details"),
    AI("ai"),
    PATH("path");

    private final String id;

    RightPanelTab(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static RightPanelTab resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return DETAILS;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "details", "description" -> DETAILS;
            case "ai", "insight" -> AI;
            case "path", "critical-path", "critical_path" -> PATH;
            default -> DETAILS;
        };
    }
}
