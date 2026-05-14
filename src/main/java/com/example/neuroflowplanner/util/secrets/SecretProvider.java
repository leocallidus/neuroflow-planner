package com.example.neuroflowplanner.util.secrets;

/**
 * Provider abstraction for secret storage backends.
 */
public interface SecretProvider {

    /**
     * Returns provider name for diagnostics.
     */
    String name();

    /**
     * Reads a secret value by logical id.
     */
    String getSecret(String secretId);

    /**
     * Stores a secret value by logical id.
     * Returns true if secret was successfully persisted by this provider.
     */
    default boolean storeSecret(String secretId, String secretValue) {
        return false;
    }

    /**
     * Clears secret value by logical id.
     * Returns true if provider removed stored value.
     */
    default boolean clearSecret(String secretId) {
        return false;
    }
}
