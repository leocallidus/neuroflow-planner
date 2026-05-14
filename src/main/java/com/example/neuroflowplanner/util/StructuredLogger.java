package com.example.neuroflowplanner.util;

import com.example.neuroflowplanner.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;

public final class StructuredLogger {

    private final Logger logger;
    private final String component;

    private StructuredLogger(Class<?> type) {
        this.logger = LoggerFactory.getLogger(type);
        this.component = type == null ? "unknown" : type.getSimpleName();
    }

    public static StructuredLogger getLogger(Class<?> type) {
        return new StructuredLogger(type);
    }

    public void info(String event, Object... keyValues) {
        log(LogLevel.INFO, event, null, null, keyValues);
    }

    public void info(String event, ErrorCode errorCode, Object... keyValues) {
        log(LogLevel.INFO, event, errorCode, null, keyValues);
    }

    public void warning(String event, Object... keyValues) {
        log(LogLevel.WARNING, event, null, null, keyValues);
    }

    public void warning(String event, ErrorCode errorCode, Object... keyValues) {
        log(LogLevel.WARNING, event, errorCode, null, keyValues);
    }

    public void error(String event, Throwable throwable, Object... keyValues) {
        log(LogLevel.ERROR, event, null, throwable, keyValues);
    }

    public void error(String event, ErrorCode errorCode, Throwable throwable, Object... keyValues) {
        log(LogLevel.ERROR, event, errorCode, throwable, keyValues);
    }

    private void log(LogLevel level, String event, ErrorCode errorCode, Throwable throwable, Object... keyValues) {
        String sanitizedEvent = sanitizeToken(event);
        List<KeyValue> pairs = normalizeKeyValues(keyValues);

        if (errorCode != null) {
            if (!containsKey(pairs, "errorCode")) {
                pairs.add(new KeyValue("errorCode", errorCode.name()));
            }
            if (!containsKey(pairs, "errorFamily")) {
                pairs.add(new KeyValue("errorFamily", errorCode.familyTag()));
            }
        }

        if (throwable != null) {
            if (!containsKey(pairs, "errorType")) {
                pairs.add(new KeyValue("errorType", throwable.getClass().getSimpleName()));
            }
            if (!containsKey(pairs, "errorMessage")) {
                String rawMessage = throwable.getMessage();
                pairs.add(new KeyValue("errorMessage", sanitizeValue("errorMessage", rawMessage == null ? "" : rawMessage)));
            }
        }

        String componentValue = firstValue(pairs, "component");
        if (componentValue.isBlank()) {
            componentValue = sanitizeToken(component);
        }
        String errorCodeValue = errorCode != null ? errorCode.name() : firstValue(pairs, "errorCode");
        String requestIdValue = firstValue(pairs, "requestId");
        String userActionIdValue = firstValue(pairs, "userActionId");
        String durationMsValue = firstValue(pairs, "durationMs");

        Map<String, String> previous = new HashMap<>();
        putMdc("event", sanitizedEvent, previous);
        putMdc("component", componentValue, previous);
        putMdc("errorCode", errorCodeValue, previous);
        putMdc("requestId", requestIdValue, previous);
        putMdc("userActionId", userActionIdValue, previous);
        putMdc("durationMs", durationMsValue, previous);

        try {
            LoggingEventBuilder builder = switch (level) {
                case INFO -> logger.atInfo();
                case WARNING -> logger.atWarn();
                case ERROR -> logger.atError();
            };
            if (throwable != null) {
                builder.setCause(sanitizeThrowable(throwable));
            }
            for (KeyValue pair : pairs) {
                builder.addKeyValue(pair.key(), pair.value());
            }
            builder.log(sanitizedEvent);
        } finally {
            restoreMdc("durationMs", previous);
            restoreMdc("userActionId", previous);
            restoreMdc("requestId", previous);
            restoreMdc("errorCode", previous);
            restoreMdc("component", previous);
            restoreMdc("event", previous);
        }
    }

    private List<KeyValue> normalizeKeyValues(Object... keyValues) {
        List<KeyValue> pairs = new ArrayList<>();
        if (keyValues != null && keyValues.length > 0) {
            for (int i = 0; i < keyValues.length; i += 2) {
                Object rawKey = keyValues[i];
                String key = sanitizeKey(rawKey);
                Object rawValue = (i + 1) < keyValues.length ? keyValues[i + 1] : "";
                String value = rawValue == null ? "" : String.valueOf(rawValue);
                pairs.add(new KeyValue(key, sanitizeValue(key, value)));
            }
        }
        return pairs;
    }

    private String sanitizeToken(String value) {
        String raw = value == null ? "" : value;
        return SensitiveDataRedactor.redactText(raw).replace(' ', '_');
    }

    private String sanitizeKey(Object key) {
        String raw = key == null ? "field" : String.valueOf(key).trim();
        if (raw.isEmpty()) {
            return "field";
        }
        return raw.replace(' ', '_');
    }

    private String sanitizeValue(String key, String value) {
        String redacted = SensitiveDataRedactor.redactFieldValue(key, value);
        if (redacted == null) {
            return "";
        }
        return redacted.replace(' ', '_');
    }

    private boolean containsKey(List<KeyValue> pairs, String key) {
        for (KeyValue pair : pairs) {
            if (pair.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private String firstValue(List<KeyValue> pairs, String key) {
        for (KeyValue pair : pairs) {
            if (pair.key().equals(key)) {
                return pair.value();
            }
        }
        return "";
    }

    private void putMdc(String key, String value, Map<String, String> previous) {
        previous.put(key, MDC.get(key));
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    private void restoreMdc(String key, Map<String, String> previous) {
        String old = previous.get(key);
        if (old == null || old.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, old);
        }
    }

    private Throwable sanitizeThrowable(Throwable throwable) {
        return sanitizeThrowable(throwable, new IdentityHashMap<>());
    }

    private Throwable sanitizeThrowable(Throwable throwable, IdentityHashMap<Throwable, Boolean> visited) {
        if (throwable == null) {
            return null;
        }
        if (visited.containsKey(throwable)) {
            return new SanitizedThrowable(throwable.getClass().getSimpleName(), "cyclic-cause");
        }
        visited.put(throwable, Boolean.TRUE);

        String type = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        String redactedMessage = message == null ? "" : SensitiveDataRedactor.redactText(message);
        SanitizedThrowable sanitized = new SanitizedThrowable(type, redactedMessage);
        sanitized.setStackTrace(throwable.getStackTrace());

        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            sanitized.initCause(sanitizeThrowable(cause, visited));
        }
        for (Throwable suppressed : throwable.getSuppressed()) {
            sanitized.addSuppressed(sanitizeThrowable(suppressed, visited));
        }
        return sanitized;
    }

    private static final class SanitizedThrowable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private SanitizedThrowable(String originalType, String redactedMessage) {
            super(buildMessage(originalType, redactedMessage));
        }

        private static String buildMessage(String originalType, String redactedMessage) {
            if (redactedMessage == null || redactedMessage.isBlank()) {
                return originalType;
            }
            return originalType + ": " + redactedMessage;
        }
    }

    private record KeyValue(String key, String value) {
    }

    private enum LogLevel {
        INFO,
        WARNING,
        ERROR
    }
}
