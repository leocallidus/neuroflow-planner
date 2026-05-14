package com.example.neuroflowplanner.util.secrets;

import java.util.ArrayList;
import java.util.List;

public class SecretResolver {

    private final List<SecretProvider> providers;

    public SecretResolver(List<SecretProvider> providers) {
        this.providers = new ArrayList<>(providers);
    }

    /**
     * Resolves secret from providers by order.
     */
    public String resolve(String secretId) {
        for (SecretProvider provider : providers) {
            String value = provider.getSecret(secretId);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Tries to persist secret in the first writable provider.
     */
    public boolean store(String secretId, String secretValue) {
        if (secretValue == null || secretValue.isBlank()) {
            return false;
        }
        for (SecretProvider provider : providers) {
            if (provider.storeSecret(secretId, secretValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clears secret from all writable providers.
     */
    public boolean clear(String secretId) {
        boolean cleared = false;
        for (SecretProvider provider : providers) {
            if (provider.clearSecret(secretId)) {
                cleared = true;
            }
        }
        return cleared;
    }
}
