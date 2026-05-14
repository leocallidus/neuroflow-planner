package com.example.neuroflowplanner.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SensitiveDataRedactor {

    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "apikey",
            "api.key",
            "token",
            "password",
            "secret"
    );

    private static final Pattern JSON_SENSITIVE_FIELD = Pattern.compile(
            "(?i)(\"(?:apiKey|token|password|secret|api\\.key)\"\\s*:\\s*\")([^\"]*)(\")"
    );
    private static final Pattern KV_SENSITIVE_FIELD = Pattern.compile(
            "(?i)\\b(apiKey|api\\.key|token|password|secret)\\b(\\s*[:=]\\s*)([^\\s,;]+)"
    );
    private static final Pattern AUTH_BEARER = Pattern.compile(
            "(?i)(authorization\\s*:\\s*bearer\\s+)([^\\s]+)"
    );

    private SensitiveDataRedactor() {
    }

    public static String maskSecret(String value) {
        if (value == null || value.isBlank() || value.length() < 8) {
            return "***";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    public static boolean isSensitiveFieldName(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        for (String marker : SENSITIVE_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    public static String redactFieldValue(String fieldName, String fieldValue) {
        if (isSensitiveFieldName(fieldName)) {
            return maskSecret(fieldValue);
        }
        return redactText(fieldValue);
    }

    public static String redactText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String redacted = JSON_SENSITIVE_FIELD.matcher(text).replaceAll("$1***$3");
        redacted = KV_SENSITIVE_FIELD.matcher(redacted).replaceAll("$1$2***");
        redacted = AUTH_BEARER.matcher(redacted).replaceAll("$1***");
        return redacted;
    }
}
