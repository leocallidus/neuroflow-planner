package com.example.neuroflowplanner.util.secrets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("LegacyConfigSecretProvider Tests")
class LegacyConfigSecretProviderTest {

    @Test
    @DisplayName("Prefers external key over legacy key")
    void testPrefersExternalKey() {
        Map<String, String> config = new HashMap<>();
        config.put(EnvSecretProvider.SECRET_EXTERNAL_API_KEY, "external_value");
        config.put(EnvSecretProvider.SECRET_LEGACY_API_KEY, "legacy_value");

        LegacyConfigSecretProvider provider = new LegacyConfigSecretProvider(config::get);
        String value = provider.getSecret(EnvSecretProvider.SECRET_EXTERNAL_API_KEY);

        assertEquals("external_value", value);
    }

    @Test
    @DisplayName("Falls back to legacy key when external is missing")
    void testLegacyFallback() {
        Map<String, String> config = new HashMap<>();
        config.put(EnvSecretProvider.SECRET_LEGACY_API_KEY, "legacy_value");

        LegacyConfigSecretProvider provider = new LegacyConfigSecretProvider(config::get);
        String value = provider.getSecret(EnvSecretProvider.SECRET_EXTERNAL_API_KEY);

        assertEquals("legacy_value", value);
    }

    @Test
    @DisplayName("Unknown key returns raw value")
    void testUnknownKey() {
        Map<String, String> config = new HashMap<>();
        config.put("custom.secret", "custom_value");

        LegacyConfigSecretProvider provider = new LegacyConfigSecretProvider(config::get);
        assertEquals("custom_value", provider.getSecret("custom.secret"));
        assertNull(provider.getSecret("missing.secret"));
    }
}
