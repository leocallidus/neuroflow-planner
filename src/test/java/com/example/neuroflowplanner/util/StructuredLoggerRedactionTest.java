package com.example.neuroflowplanner.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StructuredLogger Redaction Tests")
class StructuredLoggerRedactionTest {

    @Test
    @DisplayName("Sanitize value removes secrets before data reaches log backend")
    void sanitizeValueRedactsSensitiveData() throws Exception {
        StructuredLogger logger = StructuredLogger.getLogger(StructuredLoggerRedactionTest.class);
        String apiKey = "sk-live-abcdefgh123456";
        String password = "my-pass";
        String bearer = "BearerToken123";

        String redactedApiKey = invokeSanitizeValue(logger, "apiKey", apiKey);
        String redactedPayload = invokeSanitizeValue(logger, "payload", "{\"password\":\"" + password + "\"}");
        String redactedAuth = invokeSanitizeValue(logger, "note", "Authorization: Bearer " + bearer);

        assertFalse(redactedApiKey.contains(apiKey));
        assertFalse(redactedPayload.contains(password));
        assertFalse(redactedAuth.contains(bearer));
        assertTrue(redactedApiKey.contains("...") || redactedApiKey.contains("***"));
        assertTrue(redactedPayload.contains("***"));
        assertTrue(redactedAuth.contains("***"));
    }

    @Test
    @DisplayName("Sanitize token normalizes spaces and redacts inline secrets")
    void sanitizeTokenNormalizesAndRedacts() throws Exception {
        StructuredLogger logger = StructuredLogger.getLogger(StructuredLoggerRedactionTest.class);
        String redacted = invokeSanitizeToken(logger, "operation failed token=abc123");

        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains(" "));
        assertTrue(redacted.contains("token=***"));
    }

    @Test
    @DisplayName("Sanitize throwable preserves stacktrace and redacts exception messages")
    void sanitizeThrowableRedactsExceptionMessages() throws Exception {
        StructuredLogger logger = StructuredLogger.getLogger(StructuredLoggerRedactionTest.class);
        RuntimeException original = new RuntimeException("token=abc123");
        IllegalStateException cause = new IllegalStateException("password=my-pass");
        original.initCause(cause);

        Throwable sanitized = invokeSanitizeThrowable(logger, original);

        assertFalse(sanitized.toString().contains("abc123"));
        assertFalse(sanitized.toString().contains("my-pass"));
        assertTrue(sanitized.toString().contains("***"));
        assertTrue(sanitized.getStackTrace().length > 0);
        assertTrue(sanitized.getCause() != null);
        assertFalse(sanitized.getCause().toString().contains("my-pass"));
    }

    private static String invokeSanitizeValue(StructuredLogger logger, String key, String value) throws Exception {
        var method = StructuredLogger.class.getDeclaredMethod("sanitizeValue", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(logger, key, value);
    }

    private static String invokeSanitizeToken(StructuredLogger logger, String value) throws Exception {
        var method = StructuredLogger.class.getDeclaredMethod("sanitizeToken", String.class);
        method.setAccessible(true);
        return (String) method.invoke(logger, value);
    }

    private static Throwable invokeSanitizeThrowable(StructuredLogger logger, Throwable value) throws Exception {
        var method = StructuredLogger.class.getDeclaredMethod("sanitizeThrowable", Throwable.class);
        method.setAccessible(true);
        return (Throwable) method.invoke(logger, value);
    }
}
