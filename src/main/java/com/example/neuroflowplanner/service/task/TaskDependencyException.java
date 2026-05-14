package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domain exception for task dependency validation failures.
 */
public class TaskDependencyException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, String> details;

    public TaskDependencyException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, Map.of());
    }

    public TaskDependencyException(ErrorCode errorCode, String message, Map<String, String> details) {
        this(errorCode, message, null, details);
    }

    public TaskDependencyException(ErrorCode errorCode, String message, Throwable cause, Map<String, String> details) {
        super(message, cause);
        this.errorCode = errorCode == null ? ErrorCode.VALIDATION_FAILED : errorCode;
        this.details = copyDetails(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, String> details() {
        return details;
    }

    private static Map<String, String> copyDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            normalized.put(key, entry.getValue() == null ? "" : entry.getValue());
        }
        return Map.copyOf(normalized);
    }
}
