package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.Locale;

/**
 * UI density presets for adaptive layout.
 */
public enum UiLayoutMode {
    COMFORTABLE(UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMFORTABLE),
    COMPACT(UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMPACT);

    private final String configValue;

    UiLayoutMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static UiLayoutMode resolve(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return COMFORTABLE;
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        if (UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMPACT.equals(normalized)) {
            return COMPACT;
        }
        return COMFORTABLE;
    }
}
