package com.example.neuroflowplanner.util.secrets;

import java.util.function.Function;

public class LegacyConfigSecretProvider implements SecretProvider {

    private final Function<String, String> propertyReader;

    public LegacyConfigSecretProvider(Function<String, String> propertyReader) {
        this.propertyReader = propertyReader;
    }

    @Override
    public String name() {
        return "legacy-config";
    }

    @Override
    public String getSecret(String secretId) {
        if (secretId == null) {
            return null;
        }

        if (EnvSecretProvider.SECRET_EXTERNAL_API_KEY.equals(secretId)
                || EnvSecretProvider.SECRET_LEGACY_API_KEY.equals(secretId)) {
            return firstNonBlank(
                    propertyReader.apply(EnvSecretProvider.SECRET_EXTERNAL_API_KEY),
                    propertyReader.apply(EnvSecretProvider.SECRET_LEGACY_API_KEY)
            );
        }

        return propertyReader.apply(secretId);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
