package com.example.neuroflowplanner.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured context for error mapping/logging in UI entry adapters and presenters.
 */
public record ErrorContext(Map<String, String> details) {

    public ErrorContext {
        details = details == null || details.isEmpty() ? Map.of() : Map.copyOf(details);
    }

    public static ErrorContext empty() {
        return new ErrorContext(Map.of());
    }

    public static ErrorContext of(String component, String operation, Object... details) {
        Map<String, String> merged = new LinkedHashMap<>(ErrorMapper.details(details));
        if (hasText(component)) {
            merged.put("component", component.trim());
        }
        if (hasText(operation)) {
            merged.put("operation", operation.trim());
        }
        return new ErrorContext(merged);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
