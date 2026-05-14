package com.example.neuroflowplanner.ai.json;

import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSchemaRegistryTest {

    private static final List<String> CONFIG_KEYS = List.of(
            "ai.json.parser.mode",
            "ai.json.schema.validation.enabled");
    private static final Field PROPERTIES_FIELD = findPropertiesField();

    private final Map<String, String> configSnapshot = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        snapshotConfig();
        AiSchemaRegistry.clearCache();
    }

    @AfterEach
    void tearDown() {
        restoreConfig();
        AiSchemaRegistry.clearCache();
    }

    @Test
    void validatesHappyPayloadWhenEnabledAndJacksonMode() {
        setRuntimeConfig("ai.json.parser.mode", "jackson");
        setRuntimeConfig("ai.json.schema.validation.enabled", "true");

        String payload = readFixture("ai/payloads/openai/chat/happy_message_content.json");
        AiSchemaValidationResult result = AiSchemaRegistry.validateJson("ai/schema/chat-response.schema.json", payload);

        assertTrue(result.valid());
        assertTrue(result.messages().isEmpty());
    }

    @Test
    void rejectsSchemaMismatchWhenEnabledAndJacksonMode() {
        setRuntimeConfig("ai.json.parser.mode", "jackson");
        setRuntimeConfig("ai.json.schema.validation.enabled", "true");

        String payload = readFixture("ai/payloads/openai/chat/malformed_missing_content.json");
        AiSchemaValidationResult result = AiSchemaRegistry.validateJson("ai/schema/chat-response.schema.json", payload);

        assertFalse(result.valid());
        assertFalse(result.messages().isEmpty());
    }

    @Test
    void rejectsMalformedJsonWhenEnabledAndJacksonMode() {
        setRuntimeConfig("ai.json.parser.mode", "jackson");
        setRuntimeConfig("ai.json.schema.validation.enabled", "true");

        String payload = readFixture("ai/payloads/openai/chat/malformed_truncated.json");
        AiSchemaValidationResult result = AiSchemaRegistry.validateJson("ai/schema/chat-response.schema.json", payload);

        assertFalse(result.valid());
        assertFalse(result.messages().isEmpty());
    }

    @Test
    void skipsValidationInLegacyMode() {
        setRuntimeConfig("ai.json.parser.mode", "legacy");
        setRuntimeConfig("ai.json.schema.validation.enabled", "true");

        String payload = readFixture("ai/payloads/openai/chat/malformed_truncated.json");
        AiSchemaValidationResult result = AiSchemaRegistry.validateJson("ai/schema/chat-response.schema.json", payload);

        assertTrue(result.valid());
    }

    @Test
    void skipsValidationWhenFeatureDisabled() {
        setRuntimeConfig("ai.json.parser.mode", "jackson");
        setRuntimeConfig("ai.json.schema.validation.enabled", "false");

        String payload = readFixture("ai/payloads/openai/chat/malformed_truncated.json");
        AiSchemaValidationResult result = AiSchemaRegistry.validateJson("ai/schema/chat-response.schema.json", payload);

        assertTrue(result.valid());
    }

    private void snapshotConfig() {
        configSnapshot.clear();
        for (String key : CONFIG_KEYS) {
            configSnapshot.put(key, ConfigManager.getProperty(key));
        }
    }

    private void restoreConfig() {
        for (Map.Entry<String, String> entry : configSnapshot.entrySet()) {
            setRuntimeConfig(entry.getKey(), entry.getValue());
        }
    }

    private void setRuntimeConfig(String key, String value) {
        Properties properties = runtimeProperties();
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }

    private Properties runtimeProperties() {
        try {
            return (Properties) PROPERTIES_FIELD.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Unable to read ConfigManager.properties", ex);
        }
    }

    private static Field findPropertiesField() {
        try {
            Field field = ConfigManager.class.getDeclaredField("properties");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to access ConfigManager.properties", ex);
        }
    }

    private String readFixture(String classpathLocation) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found: " + classpathLocation);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read fixture: " + classpathLocation, e);
        }
    }
}
