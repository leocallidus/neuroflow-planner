package com.example.neuroflowplanner.error;

import com.example.neuroflowplanner.util.SensitiveDataRedactor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified error object used by UI notification layer.
 */
public record AppError(
    ErrorCode code,
    String userMessage,
    String technicalMessage,
    boolean retryable,
    Map<String, String> details
) {
    private static final String DEFAULT_USER_MESSAGE = "Произошла непредвиденная ошибка.";

    public AppError {
        code = code == null ? ErrorCode.UNEXPECTED_ERROR : code;
        userMessage = normalizeUserMessage(userMessage);
        technicalMessage = normalizeTechnicalMessage(technicalMessage);
        details = sanitizeDetails(details);
    }

    private static String normalizeUserMessage(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_USER_MESSAGE;
        }
        return value.trim();
    }

    private static String normalizeTechnicalMessage(String value) {
        String raw = value == null ? "" : value.trim();
        return SensitiveDataRedactor.redactText(raw);
    }

    private static Map<String, String> sanitizeDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            String key = entry.getKey() == null ? "detail" : entry.getKey().trim();
            if (key.isEmpty()) {
                key = "detail";
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            sanitized.put(key, SensitiveDataRedactor.redactFieldValue(key, value));
        }
        return Map.copyOf(sanitized);
    }
}
