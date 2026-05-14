package com.example.neuroflowplanner.ai.json;

import com.example.neuroflowplanner.util.ConfigManager;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class AiObjectMapperFactory {
    private static volatile ObjectMapper providerResponseMapper;
    private static volatile ObjectMapper strictUiMapper;

    private AiObjectMapperFactory() {
    }

    public static ObjectMapper providerResponseMapper() {
        ensureInitialized();
        return providerResponseMapper;
    }

    public static ObjectMapper strictUiMapper() {
        ensureInitialized();
        return strictUiMapper;
    }

    public static synchronized void reloadFromConfig() {
        providerResponseMapper = createProviderResponseMapper(ConfigManager.isAiJsonProviderFailOnUnknownProperties());
        strictUiMapper = createMapper(ConfigManager.isAiJsonUiFailOnUnknownProperties());
    }

    public static ObjectMapper createMapper(boolean failOnUnknownProperties) {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties);
        mapper.findAndRegisterModules();
        return mapper;
    }

    private static ObjectMapper createProviderResponseMapper(boolean failOnUnknownProperties) {
        ObjectMapper mapper = createMapper(failOnUnknownProperties);
        // Provider DTO contracts have many optional fields; schema validation handles
        // required-path checks, while mapper should stay forward-compatible.
        mapper.disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        return mapper;
    }

    private static void ensureInitialized() {
        if (providerResponseMapper == null || strictUiMapper == null) {
            synchronized (AiObjectMapperFactory.class) {
                if (providerResponseMapper == null || strictUiMapper == null) {
                    reloadFromConfig();
                }
            }
        }
    }
}
