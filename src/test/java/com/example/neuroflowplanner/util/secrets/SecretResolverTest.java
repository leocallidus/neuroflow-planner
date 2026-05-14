package com.example.neuroflowplanner.util.secrets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SecretResolver Tests")
class SecretResolverTest {

    @Test
    @DisplayName("Resolves secret by provider priority")
    void testResolveByPriority() {
        SecretProvider env = new StubProvider("env", null, false, false);
        SecretProvider keychain = new StubProvider("keychain", "keychain_value", false, false);
        SecretProvider legacy = new StubProvider("legacy", "legacy_value", false, false);

        SecretResolver resolver = new SecretResolver(List.of(env, keychain, legacy));
        String value = resolver.resolve(EnvSecretProvider.SECRET_EXTERNAL_API_KEY);

        assertEquals("keychain_value", value);
    }

    @Test
    @DisplayName("ENV value overrides keychain value")
    void testEnvOverridesKeychain() {
        SecretProvider env = new StubProvider("env", "env_value", false, false);
        SecretProvider keychain = new StubProvider("keychain", "keychain_value", false, false);
        SecretProvider legacy = new StubProvider("legacy", "legacy_value", false, false);

        SecretResolver resolver = new SecretResolver(List.of(env, keychain, legacy));
        String value = resolver.resolve(EnvSecretProvider.SECRET_EXTERNAL_API_KEY);

        assertEquals("env_value", value);
    }

    @Test
    @DisplayName("Store uses first writable provider")
    void testStoreUsesWritableProvider() {
        StubProvider env = new StubProvider("env", null, false, false);
        StubProvider keychain = new StubProvider("keychain", null, true, false);
        StubProvider legacy = new StubProvider("legacy", null, false, false);

        SecretResolver resolver = new SecretResolver(List.of(env, keychain, legacy));
        boolean stored = resolver.store(EnvSecretProvider.SECRET_EXTERNAL_API_KEY, "new_secret");

        assertTrue(stored);
        assertEquals("new_secret", keychain.lastStoredValue);
        assertTrue(env.storeCalled);
    }

    @Test
    @DisplayName("Clear calls all writable providers")
    void testClearCallsWritableProviders() {
        StubProvider env = new StubProvider("env", null, false, false);
        StubProvider keychain = new StubProvider("keychain", null, true, true);
        StubProvider legacy = new StubProvider("legacy", null, false, true);

        SecretResolver resolver = new SecretResolver(List.of(env, keychain, legacy));
        boolean cleared = resolver.clear(EnvSecretProvider.SECRET_EXTERNAL_API_KEY);

        assertTrue(cleared);
        assertTrue(keychain.clearCalled);
        assertTrue(legacy.clearCalled);
    }

    private static class StubProvider implements SecretProvider {
        private final String name;
        private final String value;
        private final boolean writable;
        private final boolean clearable;
        private boolean storeCalled;
        private boolean clearCalled;
        private String lastStoredValue;

        private StubProvider(String name, String value, boolean writable, boolean clearable) {
            this.name = name;
            this.value = value;
            this.writable = writable;
            this.clearable = clearable;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String getSecret(String secretId) {
            return value;
        }

        @Override
        public boolean storeSecret(String secretId, String secretValue) {
            storeCalled = true;
            if (!writable) {
                return false;
            }
            lastStoredValue = secretValue;
            return true;
        }

        @Override
        public boolean clearSecret(String secretId) {
            clearCalled = true;
            return clearable;
        }
    }
}
