package com.example.neuroflowplanner.util.secrets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("KeychainSecretProvider Tests")
class KeychainSecretProviderTest {

    @Test
    @DisplayName("Reads secret from Linux keychain command")
    void testLinuxLookup() {
        FakeExecutor executor = new FakeExecutor();
        executor.nextResult = new KeychainSecretProvider.CommandResult(0, "linux_secret\n", "");

        KeychainSecretProvider provider = new KeychainSecretProvider("Linux", executor);
        String value = provider.getSecret(EnvSecretProvider.SECRET_EXTERNAL_API_KEY);

        assertEquals("linux_secret", value);
        assertTrue(executor.lastCommand.contains("secret-tool"));
    }

    @Test
    @DisplayName("Reads secret from macOS keychain command")
    void testMacLookup() {
        FakeExecutor executor = new FakeExecutor();
        executor.nextResult = new KeychainSecretProvider.CommandResult(0, "mac_secret\n", "");

        KeychainSecretProvider provider = new KeychainSecretProvider("Mac OS X", executor);
        String value = provider.getSecret(EnvSecretProvider.SECRET_EXTERNAL_API_KEY);

        assertEquals("mac_secret", value);
        assertTrue(executor.lastCommand.contains("security"));
    }

    @Test
    @DisplayName("Stores secret in Linux keychain via stdin")
    void testLinuxStore() {
        FakeExecutor executor = new FakeExecutor();
        executor.nextResult = new KeychainSecretProvider.CommandResult(0, "", "");

        KeychainSecretProvider provider = new KeychainSecretProvider("Linux", executor);
        boolean stored = provider.storeSecret(EnvSecretProvider.SECRET_EXTERNAL_API_KEY, "stored_secret");

        assertTrue(stored);
        assertEquals("stored_secret", executor.lastStdin);
        assertTrue(executor.lastCommand.contains("store"));
    }

    @Test
    @DisplayName("Legacy api.key maps to external secret id")
    void testLegacyKeyMapping() {
        FakeExecutor executor = new FakeExecutor();
        executor.nextResult = new KeychainSecretProvider.CommandResult(0, "legacy_mapped", "");

        KeychainSecretProvider provider = new KeychainSecretProvider("Linux", executor);
        String value = provider.getSecret(EnvSecretProvider.SECRET_LEGACY_API_KEY);

        assertEquals("legacy_mapped", value);
        assertTrue(executor.lastCommand.contains("external.api.key"));
    }

    @Test
    @DisplayName("Unsupported OS returns null and cannot store")
    void testUnsupportedOs() {
        FakeExecutor executor = new FakeExecutor();
        KeychainSecretProvider provider = new KeychainSecretProvider("Windows 11", executor);

        assertNull(provider.getSecret(EnvSecretProvider.SECRET_EXTERNAL_API_KEY));
        assertFalse(provider.storeSecret(EnvSecretProvider.SECRET_EXTERNAL_API_KEY, "x"));
    }

    private static class FakeExecutor implements KeychainSecretProvider.CommandExecutor {
        private KeychainSecretProvider.CommandResult nextResult;
        private List<String> lastCommand = new ArrayList<>();
        private String lastStdin;

        @Override
        public KeychainSecretProvider.CommandResult execute(List<String> command, String stdin, Duration timeout) {
            this.lastCommand = command;
            this.lastStdin = stdin;
            return nextResult == null
                    ? new KeychainSecretProvider.CommandResult(1, "", "simulated error")
                    : nextResult;
        }
    }
}
