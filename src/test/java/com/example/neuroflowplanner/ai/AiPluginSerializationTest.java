package com.example.neuroflowplanner.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AI Plugin Serialization Tests")
class AiPluginSerializationTest {

    @Test
    @DisplayName("Сериализует только web plugin")
    void serializesWebOnly() {
        String payload = renderPlugins(new AiRequestOptions.PluginOptions(
                new AiRequestOptions.WebPluginOptions(true, "native", 4, "Ищи свежее"),
                null,
                null));

        assertTrue(payload.contains("\"plugins\":[{\"id\":\"web\",\"engine\":\"native\",\"max_results\":4,\"search_prompt\":\"Ищи свежее\"}]"));
        assertFalse(payload.contains("file-parser"));
        assertFalse(payload.contains("response-healing"));
    }

    @Test
    @DisplayName("Сериализует только file-parser plugin")
    void serializesFileParserOnly() {
        String payload = renderPlugins(new AiRequestOptions.PluginOptions(
                null,
                new AiRequestOptions.FileParserPluginOptions(true, "pdf-text"),
                null));

        assertTrue(payload.contains("\"plugins\":[{\"id\":\"file-parser\",\"pdf\":{\"engine\":\"pdf-text\"}}]"));
        assertFalse(payload.contains("\"id\":\"web\""));
        assertFalse(payload.contains("response-healing"));
    }

    @Test
    @DisplayName("Сериализует только response-healing plugin")
    void serializesResponseHealingOnly() {
        String payload = renderPlugins(new AiRequestOptions.PluginOptions(
                null,
                null,
                new AiRequestOptions.ResponseHealingPluginOptions(true)));

        assertTrue(payload.contains("\"plugins\":[{\"id\":\"response-healing\"}]"));
        assertFalse(payload.contains("\"id\":\"web\""));
        assertFalse(payload.contains("file-parser"));
    }

    @Test
    @DisplayName("Сериализует несколько plugins одновременно")
    void serializesCombinedPlugins() {
        String payload = renderPlugins(new AiRequestOptions.PluginOptions(
                new AiRequestOptions.WebPluginOptions(true, "exa", 2, ""),
                new AiRequestOptions.FileParserPluginOptions(true, "native"),
                new AiRequestOptions.ResponseHealingPluginOptions(true)));

        assertTrue(payload.contains("\"plugins\":["));
        assertTrue(payload.contains("{\"id\":\"web\",\"engine\":\"exa\",\"max_results\":2}"));
        assertTrue(payload.contains("{\"id\":\"file-parser\",\"pdf\":{\"engine\":\"native\"}}"));
        assertTrue(payload.contains("{\"id\":\"response-healing\"}"));
    }

    private static String renderPlugins(AiRequestOptions.PluginOptions pluginOptions) {
        StubHttpAiClient client = new StubHttpAiClient();
        return client.renderChatPayloadWithPlugins(pluginOptions);
    }

    private static final class StubHttpAiClient extends AbstractHttpAiClient {
        private StubHttpAiClient() {
            this.baseUrl = "https://api.polza.ai/api/v1";
            this.defaultModel = "openai/gpt-4o";
        }

        private String renderChatPayloadWithPlugins(AiRequestOptions.PluginOptions pluginOptions) {
            return buildChatRequestJson(
                    "hello",
                    AiRequestOptions.builder()
                            .model(defaultModel)
                            .pluginOptions(pluginOptions)
                            .build());
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            return CompletableFuture.completedFuture(AiResponse.success("ok", defaultModel));
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection() {
            return CompletableFuture.completedFuture(ConnectionTestResult.success("ok", getMode(), baseUrl));
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey) {
            return testConnection();
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testModel(String model) {
            return testConnection();
        }

        @Override
        public CompletableFuture<List<String>> fetchAvailableModels() {
            return CompletableFuture.completedFuture(List.of(defaultModel));
        }

        @Override
        public boolean supportsImages() {
            return false;
        }

        @Override
        public AiMode getMode() {
            return AiMode.EXTERNAL_OPENAI;
        }

        @Override
        public void reloadConfiguration() {
            // no-op
        }
    }
}
