package com.example.neuroflowplanner.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SensitiveDataRedactor Tests")
class SensitiveDataRedactorTest {

    @Test
    @DisplayName("Mask secret keeps first and last 4 characters")
    void testMaskSecret() {
        assertEquals("abcd...wxyz", SensitiveDataRedactor.maskSecret("abcd1234wxyz"));
    }

    @Test
    @DisplayName("Mask short secret returns stars")
    void testMaskShortSecret() {
        assertEquals("***", SensitiveDataRedactor.maskSecret("short"));
        assertEquals("***", SensitiveDataRedactor.maskSecret(""));
        assertEquals("***", SensitiveDataRedactor.maskSecret(null));
    }

    @Test
    @DisplayName("Redacts sensitive fields in plain text")
    void testRedactTextKv() {
        String input = "apiKey=sk-123 token:abc password=my-pass secret=s3cr3t";
        String redacted = SensitiveDataRedactor.redactText(input);

        assertFalse(redacted.contains("sk-123"));
        assertFalse(redacted.contains("abc"));
        assertFalse(redacted.contains("my-pass"));
        assertFalse(redacted.contains("s3cr3t"));
        assertTrue(redacted.contains("apiKey=***"));
        assertTrue(redacted.contains("token:***"));
    }

    @Test
    @DisplayName("Redacts sensitive fields in JSON and Authorization header")
    void testRedactTextJsonAndAuth() {
        String input = "{\"apiKey\":\"sk-123\"} Authorization: Bearer sk-456";
        String redacted = SensitiveDataRedactor.redactText(input);

        assertFalse(redacted.contains("sk-123"));
        assertFalse(redacted.contains("sk-456"));
        assertTrue(redacted.contains("\"apiKey\":\"***\""));
        assertTrue(redacted.contains("Authorization: Bearer ***"));
    }

    @Test
    @DisplayName("Sensitive field names are detected")
    void testSensitiveFieldName() {
        assertTrue(SensitiveDataRedactor.isSensitiveFieldName("apiKey"));
        assertTrue(SensitiveDataRedactor.isSensitiveFieldName("auth.token.value"));
        assertTrue(SensitiveDataRedactor.isSensitiveFieldName("password_hint"));
        assertTrue(SensitiveDataRedactor.isSensitiveFieldName("secretField"));
        assertFalse(SensitiveDataRedactor.isSensitiveFieldName("baseUrl"));
    }
}
