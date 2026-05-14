package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.Locale;

/**
 * Preferred command palette presentation memory (last-used mode).
 */
public enum CommandPaletteViewMode {
    GUIDED(UxConfigDefaults.UX_COMMAND_PALETTE_VIEW_MODE_GUIDED),
    RECENT(UxConfigDefaults.UX_COMMAND_PALETTE_VIEW_MODE_RECENT),
    SEARCH(UxConfigDefaults.UX_COMMAND_PALETTE_VIEW_MODE_SEARCH),
    CONTEXT(UxConfigDefaults.UX_COMMAND_PALETTE_VIEW_MODE_CONTEXT);

    private final String configValue;

    CommandPaletteViewMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static CommandPaletteViewMode resolve(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return GUIDED;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (CommandPaletteViewMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        return GUIDED;
    }
}
