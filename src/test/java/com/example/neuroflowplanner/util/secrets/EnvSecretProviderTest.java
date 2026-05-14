package com.example.neuroflowplanner.util.secrets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("EnvSecretProvider Tests")
class EnvSecretProviderTest {

    @Test
    @DisplayName("Reads external key from primary env variable")
    void testPrimaryEnvName() {
        EnvSecretProvider provider = new EnvSecretProvider(name -> Map.of(
                EnvSecretProvider.ENV_EXTERNAL_API_KEY, "primary_value",
                EnvSecretProvider.ENV_LEGACY_API_KEY, "legacy_value"
        ).get(name));

        assertEquals("primary_value", provider.getSecret(EnvSecretProvider.SECRET_EXTERNAL_API_KEY));
    }

    @Test
    @DisplayName("Uses legacy env name when primary is missing")
    void testLegacyEnvFallback() {
        EnvSecretProvider provider = new EnvSecretProvider(name -> Map.of(
                EnvSecretProvider.ENV_LEGACY_API_KEY, "legacy_value"
        ).get(name));

        assertEquals("legacy_value", provider.getSecret(EnvSecretProvider.SECRET_EXTERNAL_API_KEY));
    }

    @Test
    @DisplayName("Returns null for unknown secret id")
    void testUnknownSecretId() {
        EnvSecretProvider provider = new EnvSecretProvider(name -> "ignored");
        assertNull(provider.getSecret("unknown.secret"));
    }
}
