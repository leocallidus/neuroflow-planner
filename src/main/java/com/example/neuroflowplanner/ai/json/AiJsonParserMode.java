package com.example.neuroflowplanner.ai.json;

import com.example.neuroflowplanner.util.AiConfigDefaults;

import java.util.Locale;

public enum AiJsonParserMode {
    LEGACY(AiConfigDefaults.JSON_PARSER_MODE_LEGACY),
    DUAL(AiConfigDefaults.JSON_PARSER_MODE_DUAL),
    JACKSON(AiConfigDefaults.JSON_PARSER_MODE_JACKSON);

    private final String configValue;

    AiJsonParserMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static AiJsonParserMode fromConfigValue(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return LEGACY;
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case AiConfigDefaults.JSON_PARSER_MODE_DUAL -> DUAL;
            case AiConfigDefaults.JSON_PARSER_MODE_JACKSON -> JACKSON;
            case AiConfigDefaults.JSON_PARSER_MODE_LEGACY -> LEGACY;
            default -> LEGACY;
        };
    }
}
