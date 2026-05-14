package com.example.neuroflowplanner.util.secrets;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("KeychainSecretProvider System Integration")
class KeychainSecretProviderSystemIT {

    @Test
    @DisplayName("Round-trip secret in OS keychain when backend is available")
    void testRoundTripIfBackendAvailable() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Assumptions.assumeTrue(
                osName.contains("linux") || osName.contains("mac"),
                "Keychain provider supports Linux/macOS backends only"
        );

        KeychainSecretProvider provider = new KeychainSecretProvider();
        String testSecretId = "external.api.key.system-test." + UUID.randomUUID();
        String secretValue = "nf_stage3_" + UUID.randomUUID();

        boolean stored = provider.storeSecret(testSecretId, secretValue);
        Assumptions.assumeTrue(
                stored,
                "OS keychain backend is unavailable in current session (e.g. no unlocked keyring)"
        );

        try {
            assertEquals(secretValue, provider.getSecret(testSecretId));
        } finally {
            provider.clearSecret(testSecretId);
        }
    }
}
