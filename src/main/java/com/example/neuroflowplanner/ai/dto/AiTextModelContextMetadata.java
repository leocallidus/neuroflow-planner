package com.example.neuroflowplanner.ai.dto;

import java.util.Locale;

public record AiTextModelContextMetadata(
        Integer contextWindowTokens,
        String contextWindowLabel) {

    public static AiTextModelContextMetadata fromTokens(Integer contextWindowTokens) {
        if (contextWindowTokens == null || contextWindowTokens <= 0) {
            return null;
        }
        return new AiTextModelContextMetadata(contextWindowTokens, formatContextWindowLabel(contextWindowTokens));
    }

    private static String formatContextWindowLabel(int tokens) {
        if (tokens >= 1_000_000) {
            double millions = tokens / 1_000_000.0;
            return trimTrailingZeros(String.format(Locale.US, "%.2fM", millions));
        }
        if (tokens >= 1_000) {
            double thousands = tokens / 1_000.0;
            return trimTrailingZeros(String.format(Locale.US, "%.0fK", thousands));
        }
        return Integer.toString(tokens);
    }

    private static String trimTrailingZeros(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (!value.contains(".")) {
            return value;
        }
        return value.replaceAll("\\.?0+([A-Za-z]+)$", "$1");
    }
}
