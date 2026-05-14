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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPayloadRegressionCorpusTest {
    private static final List<String> CONFIG_KEYS = List.of(
            "ai.json.parser.mode",
            "ai.json.schema.validation.enabled",
            "ai.json.parser.failOnUnknownProviderProperties");
    private static final Field PROPERTIES_FIELD = findPropertiesField();

    private final Map<String, String> configSnapshot = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        snapshotConfig();
        setRuntimeConfig("ai.json.parser.mode", "jackson");
        setRuntimeConfig("ai.json.schema.validation.enabled", "true");
        setRuntimeConfig("ai.json.parser.failOnUnknownProviderProperties", "false");
        AiObjectMapperFactory.reloadFromConfig();
        AiSchemaRegistry.clearCache();
    }

    @AfterEach
    void tearDown() {
        restoreConfig();
        AiObjectMapperFactory.reloadFromConfig();
        AiSchemaRegistry.clearCache();
    }


    @Test
    void chatCorpusFixtures() {
        assertEquals("Plan created successfully.",
                AiCoreResponseMapper.extractChatContent(readFixture("ai/payloads/openai/chat/happy_message_content.json")));
        assertEquals("Legacy completion text fallback.",
                AiCoreResponseMapper.extractChatContent(readFixture("ai/payloads/openai/chat/edge_text_fallback.json")));
        assertEquals("Provider-specific top-level response fallback.",
                AiCoreResponseMapper.extractChatContent(readFixture("ai/payloads/openai/chat/edge_response_fallback.json")));

        assertEquals("Ollama says hello.",
                AiCoreResponseMapper.extractChatContent(readFixture("ai/payloads/ollama/chat/happy_message_content.json")));
        assertEquals("OpenAI-compatible fallback from gateway.",
                AiCoreResponseMapper.extractChatContent(readFixture("ai/payloads/ollama/chat/edge_openai_compatible_fallback.json")));

        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper
                .extractChatContent(readFixture("ai/payloads/openai/chat/malformed_missing_content.json")));
        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper
                .extractChatContent(readFixture("ai/payloads/openai/chat/malformed_truncated.json")));
        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper
                .extractChatContent(readFixture("ai/payloads/ollama/chat/malformed_message_type_mismatch.json")));
    }

    @Test
    void modelsCorpusFixtures() {
        List<String> openAiIds = AiCoreResponseMapper.extractModelNames(
                readFixture("ai/payloads/openai/models/happy_data_ids.json"));
        assertEquals(List.of("gpt-4o-mini", "gpt-4.1-mini"), openAiIds);

        List<String> openAiNames = AiCoreResponseMapper.extractModelNames(
                readFixture("ai/payloads/openai/models/edge_data_names.json"));
        assertEquals(List.of("provider/model-gamma", "Provider: Model Alpha", "provider/model-beta"), openAiNames);

        List<String> ollamaNames = AiCoreResponseMapper.extractModelNames(
                readFixture("ai/payloads/ollama/tags/happy_models_names.json"));
        assertEquals(List.of("llama3:latest", "mistral:latest"), ollamaNames);

        List<String> ollamaEdgeNames = AiCoreResponseMapper.extractModelNames(
                readFixture("ai/payloads/ollama/tags/edge_duplicate_latest_suffix.json"));
        assertEquals(List.of("llama3:latest", "llama3", "deepseek-r1:latest"), ollamaEdgeNames);

        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper
                .extractModelNames(readFixture("ai/payloads/openai/models/malformed_data_not_array.json")));
        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper
                .extractModelNames(readFixture("ai/payloads/ollama/tags/malformed_models_not_array.json")));
    }

    @Test
    void imageGenerationCorpusFixtures() {
        String happyGeneration = readFixture("ai/payloads/openai/images-generations/happy_request_id.json");
        assertEquals("req_1234567890", AiCoreResponseMapper.extractImageRequestIdFromGeneration(happyGeneration));
        assertNull(AiCoreResponseMapper.extractImageUrlFromGeneration(happyGeneration));

        String mediaGeneration = """
                {"id":"aig_media_123","object":"media.generation","status":"pending","model":"openai/gpt-5-image"}
                """;
        assertEquals("aig_media_123", AiCoreResponseMapper.extractImageRequestIdFromGeneration(mediaGeneration));

        String edgeGeneration = readFixture("ai/payloads/openai/images-generations/edge_url_in_data_array.json");
        assertEquals("https://cdn.example.test/generated/image-1.png",
                AiCoreResponseMapper.extractImageUrlFromGeneration(edgeGeneration));
        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper.extractImageRequestIdFromGeneration(edgeGeneration));

        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper.extractImageRequestIdFromGeneration(
                readFixture("ai/payloads/openai/images-generations/malformed_missing_request_id.json")));
    }

    @Test
    void imagePollingCorpusFixtures() {
        assertEquals("https://cdn.example.test/images/result-ready.png",
                AiCoreResponseMapper.extractImageUrlFromPolling(
                        readFixture("ai/payloads/image-polling/happy_result_url_ready.json")));
        assertEquals("https://cdn.example.test/images/output-final.webp",
                AiCoreResponseMapper.extractImageUrlFromPolling(
                        readFixture("ai/payloads/image-polling/edge_state_output_url.json")));
        assertEquals("https://cdn.example.test/images/direct-link.jpg",
                AiCoreResponseMapper.extractImageUrlFromPolling(
                        readFixture("ai/payloads/image-polling/edge_url_only.json")));

        String failedPolling = readFixture("ai/payloads/image-polling/terminal_failed_status.json");
        assertEquals("failed", AiCoreResponseMapper.extractImageStatusOrStateFromPolling(failedPolling));
        assertNull(AiCoreResponseMapper.extractImageUrlFromPolling(failedPolling));

        String mediaPolling = """
                {"id":"aig_media_123","status":"completed","data":{"url":"https://cdn.example.test/images/media-object.png"}}
                """;
        assertEquals("https://cdn.example.test/images/media-object.png",
                AiCoreResponseMapper.extractImageUrlFromPolling(mediaPolling));

        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper.extractImageUrlFromPolling(
                readFixture("ai/payloads/image-polling/malformed_invalid_json.json")));
    }

    @Test
    void imageHistoryCorpusFixtures() {
        String nestedHistory = """
                {
                  "id":"hist_123",
                  "status":"completed",
                  "attempts":[
                    {
                      "response":{
                        "result":{
                          "url":"https://cdn.example.test/images/history-nested.png"
                        }
                      }
                    }
                  ]
                }
                """;
        assertEquals("completed", AiCoreResponseMapper.extractImageStatusFromHistory(nestedHistory));
        assertEquals("https://cdn.example.test/images/history-nested.png",
                AiCoreResponseMapper.extractImageUrlFromHistory(nestedHistory));

        String noUrlHistory = """
                {"id":"hist_124","status":"processing","attempts":[]}
                """;
        assertEquals("processing", AiCoreResponseMapper.extractImageStatusFromHistory(noUrlHistory));
        assertNull(AiCoreResponseMapper.extractImageUrlFromHistory(noUrlHistory));
    }

    @Test
    void uiCorpusFixtures() {
        assertNotNull(AiCoreResponseMapper.parseUiTaskAutofillResponse(
                readFixture("ai/payloads/ui/autofill/happy_strict_contract.json")));
        assertNotNull(AiCoreResponseMapper.parseUiTaskAutofillResponse(
                readFixture("ai/payloads/ui/autofill/edge_complexity_string.json")));
        assertNotNull(AiCoreResponseMapper.parseUiTaskAutofillResponse(
                readFixture("ai/payloads/ui/autofill/edge_markdown_fenced_json.txt")));

        AiParsingException missingFields = assertThrows(AiParsingException.class, () ->
                AiCoreResponseMapper.parseUiTaskAutofillResponse(
                        readFixture("ai/payloads/ui/autofill/malformed_missing_required_fields.json")));
        assertTrue(missingFields instanceof AiSchemaValidationException);
        assertFalse(((AiSchemaValidationException) missingFields).validationMessages().isEmpty());

        AiParsingException outOfRange = assertThrows(AiParsingException.class, () ->
                AiCoreResponseMapper.parseUiTaskAutofillResponse(
                        readFixture("ai/payloads/ui/autofill/malformed_complexity_out_of_range.json")));
        assertTrue(outOfRange instanceof AiSchemaValidationException);
        assertFalse(((AiSchemaValidationException) outOfRange).validationMessages().isEmpty());
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
}
