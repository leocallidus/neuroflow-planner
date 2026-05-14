package com.example.neuroflowplanner.util.secrets;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class EnvSecretProvider implements SecretProvider {

    public static final String SECRET_EXTERNAL_API_KEY = "external.api.key";
    public static final String SECRET_LEGACY_API_KEY = "api.key";
    public static final String SECRET_CLOUD_SYNC_REFRESH_TOKEN = "cloud.sync.refresh.token";

    public static final String ENV_EXTERNAL_API_KEY = "NEUROFLOW_EXTERNAL_API_KEY";
    public static final String ENV_LEGACY_API_KEY = "NEUROFLOW_API_KEY";
    public static final String ENV_CLOUD_SYNC_REFRESH_TOKEN = "NEUROFLOW_CLOUD_SYNC_REFRESH_TOKEN";

    private static final Map<String, List<String>> ENV_NAME_BY_SECRET = Map.of(
            SECRET_EXTERNAL_API_KEY, List.of(ENV_EXTERNAL_API_KEY, ENV_LEGACY_API_KEY),
            SECRET_LEGACY_API_KEY, List.of(ENV_EXTERNAL_API_KEY, ENV_LEGACY_API_KEY),
            SECRET_CLOUD_SYNC_REFRESH_TOKEN, List.of(ENV_CLOUD_SYNC_REFRESH_TOKEN)
    );

    private final Function<String, String> envReader;

    public EnvSecretProvider() {
        this(System::getenv);
    }

    EnvSecretProvider(Function<String, String> envReader) {
        this.envReader = envReader;
    }

    @Override
    public String name() {
        return "env";
    }

    @Override
    public String getSecret(String secretId) {
        List<String> envNames = ENV_NAME_BY_SECRET.get(secretId);
        if (envNames == null || envNames.isEmpty()) {
            return null;
        }

        for (String envName : envNames) {
            String envValue = envReader.apply(envName);
            if (envValue != null && !envValue.isBlank()) {
                return envValue;
            }
        }
        return null;
    }
}
