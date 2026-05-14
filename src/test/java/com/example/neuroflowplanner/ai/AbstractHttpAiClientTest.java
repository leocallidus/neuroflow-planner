package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.media.AiMediaCapabilityValidationException;
import com.example.neuroflowplanner.ai.media.AiMediaInput;
import com.example.neuroflowplanner.ai.media.AiMediaInputKind;
import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AbstractHttpAiClient Tests")
class AbstractHttpAiClientTest {

    @Test
    @DisplayName("Добавляет reasoning_effort в chat JSON для поддерживаемых моделей")
    void includesReasoningEffortForSupportedModel() {
        StubHttpAiClient client = new StubHttpAiClient();

        String payload = client.renderChatPayload("openai/gpt-5.4", "high");

        assertTrue(payload.contains("\"reasoning_effort\":\"high\""));
    }

    @Test
    @DisplayName("Пропускает reasoning_effort для неподдерживаемых моделей")
    void omitsReasoningEffortForUnsupportedModel() {
        StubHttpAiClient client = new StubHttpAiClient();

        String payload = client.renderChatPayload("claude-haiku-4.5", "high");

        assertFalse(payload.contains("\"reasoning_effort\""));
    }

    @Test
    @DisplayName("Добавляет cache_control для Claude system prompt при включенном prompt caching")
    void includesPromptCachingForClaudeSystemPrompt() {
        StubHttpAiClient client = new StubHttpAiClient();
        String previous = ConfigManager.getProperty(AiConfigDefaults.CONFIG_AI_PROMPT_CACHING_ENABLED);
        try {
            ConfigManager.setAiPromptCachingEnabled(true);
            String payload = client.renderChatPayloadWithSystem(
                    "anthropic/claude-sonnet-4-5-20250929",
                    "Длинная системная инструкция");

            assertTrue(payload.contains("\"cache_control\":{\"type\":\"ephemeral\"}"));
            assertTrue(payload.contains("\"role\":\"system\",\"content\":[{\"type\":\"text\",\"text\":\"Длинная системная инструкция\""));
        } finally {
            ConfigManager.setProperty(AiConfigDefaults.CONFIG_AI_PROMPT_CACHING_ENABLED, previous);
        }
    }

    @Test
    @DisplayName("Не добавляет cache_control для Claude system prompt при выключенном prompt caching")
    void omitsPromptCachingForClaudeWhenDisabled() {
        StubHttpAiClient client = new StubHttpAiClient();
        String previous = ConfigManager.getProperty(AiConfigDefaults.CONFIG_AI_PROMPT_CACHING_ENABLED);
        try {
            ConfigManager.setAiPromptCachingEnabled(false);
            String payload = client.renderChatPayloadWithSystem(
                    "anthropic/claude-sonnet-4-5-20250929",
                    "Длинная системная инструкция");

            assertFalse(payload.contains("\"cache_control\""));
            assertTrue(payload.contains("\"role\":\"system\",\"content\":\"Длинная системная инструкция\""));
        } finally {
            ConfigManager.setProperty(AiConfigDefaults.CONFIG_AI_PROMPT_CACHING_ENABLED, previous);
        }
    }

    @Test
    @DisplayName("Добавляет reasoning object для Polza reasoning-моделей")
    void includesStructuredReasoningForPolzaModels() {
        StubHttpAiClient client = new StubHttpAiClient("https://api.polza.ai/api/v1");

        String payload = client.renderChatPayloadWithReasoning(
                "openai/o4-mini",
                new AiRequestOptions.ReasoningOptions("xhigh", 1200, "detailed", true, true));

        assertTrue(payload.contains("\"reasoning\":{"));
        assertTrue(payload.contains("\"effort\":\"xhigh\""));
        assertTrue(payload.contains("\"max_tokens\":1200"));
        assertTrue(payload.contains("\"summary\":\"detailed\""));
        assertTrue(payload.contains("\"enabled\":true"));
        assertTrue(payload.contains("\"exclude\":true"));
        assertFalse(payload.contains("\"reasoning_effort\""));
    }

    @Test
    @DisplayName("Добавляет plugins array для Polza chat completions")
    void includesPluginsArrayForPolzaChatCompletions() {
        StubHttpAiClient client = new StubHttpAiClient("https://api.polza.ai/api/v1");

        String payload = client.renderChatPayloadWithPlugins(
                "openai/gpt-4o",
                new AiRequestOptions.PluginOptions(
                        new AiRequestOptions.WebPluginOptions(true, "exa", 3, "Найди свежие данные"),
                        new AiRequestOptions.FileParserPluginOptions(true, "mistral-ocr"),
                        new AiRequestOptions.ResponseHealingPluginOptions(true)));

        assertTrue(payload.contains("\"plugins\":["));
        assertTrue(payload.contains("{\"id\":\"web\",\"engine\":\"exa\",\"max_results\":3,\"search_prompt\":\"Найди свежие данные\"}"));
        assertTrue(payload.contains("{\"id\":\"file-parser\",\"pdf\":{\"engine\":\"mistral-ocr\"}}"));
        assertTrue(payload.contains("{\"id\":\"response-healing\"}"));
    }

    @Test
    @DisplayName("Сериализует расширенные text-параметры модели")
    void includesExtendedTextParametersInChatPayload() {
        StubHttpAiClient client = new StubHttpAiClient("https://api.polza.ai/api/v1");

        String payload = client.renderChatPayloadWithTextParameters(
                "openai/gpt-5.4",
                4096,
                0.7,
                0.9,
                0.4,
                -0.2);

        assertTrue(payload.contains("\"max_tokens\":4096"));
        assertTrue(payload.contains("\"temperature\":0.7"));
        assertTrue(payload.contains("\"top_p\":0.9"));
        assertTrue(payload.contains("\"frequency_penalty\":0.4"));
        assertTrue(payload.contains("\"presence_penalty\":-0.2"));
    }

    @Test
    @DisplayName("Не отправляет выключенные plugins и опускает engine auto")
    void omitsDisabledPluginsAndAutoEngine() {
        StubHttpAiClient client = new StubHttpAiClient("https://api.polza.ai/api/v1");

        String payload = client.renderChatPayloadWithPlugins(
                "openai/gpt-4o",
                new AiRequestOptions.PluginOptions(
                        new AiRequestOptions.WebPluginOptions(true, "auto", 5, "  "),
                        new AiRequestOptions.FileParserPluginOptions(false, "native"),
                        new AiRequestOptions.ResponseHealingPluginOptions(false)));

        assertTrue(payload.contains("\"plugins\":[{\"id\":\"web\",\"max_results\":5}]"));
        assertFalse(payload.contains("\"engine\":\"auto\""));
        assertFalse(payload.contains("file-parser"));
        assertFalse(payload.contains("response-healing"));
    }

    @Test
    @DisplayName("Строит единый media payload для image, file и audio")
    void buildsUnifiedMediaPayload() {
        StubHttpAiClient client = new StubHttpAiClient();
        List<String> previousMultimodal = ConfigManager.getExternalApiMultimodalModels();
        try {
            ConfigManager.setExternalApiMultimodalModels(List.of("openai/gpt-4o"));
            withExternalMediaCapabilities(
                    List.of("openai/gpt-4o"),
                    List.of("openai/gpt-4o"),
                    () -> {
                        String imageAndFilePayload = client.renderChatPayloadWithMedia(
                                "Проанализируй вложения",
                                List.of(
                                        AiMediaInput.imageDataUrl("data:image/png;base64,AAA", "image.png", "image/png"),
                                        AiMediaInput.documentDataUrl("data:application/pdf;base64,BBB", "report.pdf", "application/pdf")
                                ));

                        assertTrue(imageAndFilePayload.contains("\"type\":\"image_url\""));
                        assertTrue(imageAndFilePayload.contains("\"type\":\"file\""));
                        assertTrue(imageAndFilePayload.contains("\"filename\":\"report.pdf\""));

                        String audioPayload = client.renderChatPayloadWithMedia(
                                "Послушай вложение",
                                List.of(AiMediaInput.audioData("CCC", "voice.wav", "audio/wav", "wav")));

                        assertTrue(audioPayload.contains("\"type\":\"input_audio\""));
                        assertTrue(audioPayload.contains("\"format\":\"wav\""));
                    });
        } finally {
            ConfigManager.setExternalApiMultimodalModels(previousMultimodal);
        }
    }

    @Test
    @DisplayName("Нормализует RAW_BYTES в Polza media payload format")
    void normalizesRawBytesMediaPayload() {
        StubHttpAiClient client = new StubHttpAiClient();
        List<String> previousMultimodal = ConfigManager.getExternalApiMultimodalModels();
        try {
            ConfigManager.setExternalApiMultimodalModels(List.of("openai/gpt-4o"));
            withExternalMediaCapabilities(
                    List.of("openai/gpt-4o"),
                    List.of("openai/gpt-4o"),
                    () -> {
                        String imageAndFilePayload = client.renderChatPayloadWithMedia(
                                "",
                                List.of(
                                        AiMediaInput.rawBytes(AiMediaInputKind.IMAGE, new byte[] {1, 2, 3}, "image.png", "image/png", null),
                                        AiMediaInput.rawBytes(AiMediaInputKind.DOCUMENT, new byte[] {4, 5, 6}, "report.pdf", "application/pdf", null)
                                ));

                        assertTrue(imageAndFilePayload.contains("\"text\":\"\""));
                        assertTrue(imageAndFilePayload.contains("\"url\":\"data:image/png;base64,AQID\""));
                        assertTrue(imageAndFilePayload.contains("\"file_data\":\"data:application/pdf;base64,BAUG\""));

                        String audioPayload = client.renderChatPayloadWithMedia(
                                "",
                                List.of(AiMediaInput.rawBytes(AiMediaInputKind.AUDIO, new byte[] {7, 8, 9}, "voice.wav", "audio/wav", "wav")));

                        assertTrue(audioPayload.contains("\"text\":\"\""));
                        assertTrue(audioPayload.contains("\"data\":\"BwgJ\""));
                        assertFalse(audioPayload.contains("\"data\":\"data:audio"));
                    });
        } finally {
            ConfigManager.setExternalApiMultimodalModels(previousMultimodal);
        }
    }

    @Test
    @DisplayName("Стрипует data URL prefix для input_audio")
    void stripsAudioDataUrlPrefix() {
        StubHttpAiClient client = new StubHttpAiClient();
        withExternalMediaCapabilities(
                List.of("openai/gpt-4o"),
                List.of(),
                () -> {
                    String payload = client.renderChatPayloadWithMedia(
                            "Послушай",
                            List.of(AiMediaInput.audioData("data:audio/wav;base64,QUJD", "voice.wav", "audio/wav", "wav")));

                    assertTrue(payload.contains("\"input_audio\":{\"data\":\"QUJD\",\"format\":\"wav\"}"));
                });
    }

    @Test
    @DisplayName("Блокирует audio input для модели без audio capability")
    void blocksAudioInputForUnsupportedModel() {
        StubHttpAiClient client = new StubHttpAiClient();
        List<String> previous = ConfigManager.getExternalApiAudioInputModels();
        try {
            ConfigManager.setExternalApiAudioInputModels(List.of("openai/gpt-4o-audio-preview"));

            AiMediaCapabilityValidationException ex = assertThrows(
                    AiMediaCapabilityValidationException.class,
                    () -> client.renderChatPayloadWithModelAndMedia(
                            "openai/gpt-4o",
                            "Послушай",
                            List.of(AiMediaInput.audioData("QUJD", "voice.wav", "audio/wav", "wav"))));

            assertEquals("Модель 'openai/gpt-4o' не поддерживает аудио на вход.", ex.getMessage());
        } finally {
            ConfigManager.setExternalApiAudioInputModels(previous);
        }
    }

    @Test
    @DisplayName("Блокирует document input для модели без file capability")
    void blocksDocumentInputForUnsupportedModel() {
        StubHttpAiClient client = new StubHttpAiClient();
        List<String> previous = ConfigManager.getExternalApiFileInputModels();
        try {
            ConfigManager.setExternalApiFileInputModels(List.of("openai/gpt-4o-file"));

            AiMediaCapabilityValidationException ex = assertThrows(
                    AiMediaCapabilityValidationException.class,
                    () -> client.renderChatPayloadWithModelAndMedia(
                            "openai/gpt-4o",
                            "Прочитай",
                            List.of(AiMediaInput.documentDataUrl(
                                    "data:application/pdf;base64,BBB",
                                    "report.pdf",
                                    "application/pdf"))));

            assertEquals("Модель 'openai/gpt-4o' не поддерживает файлы на вход.", ex.getMessage());
        } finally {
            ConfigManager.setExternalApiFileInputModels(previous);
        }
    }

    @Test
    @DisplayName("Блокирует image input для немультимодальной модели")
    void blocksImageInputForUnsupportedModel() {
        StubHttpAiClient client = new StubHttpAiClient();
        List<String> previous = ConfigManager.getExternalApiMultimodalModels();
        try {
            ConfigManager.setExternalApiMultimodalModels(List.of("openai/gpt-4o-vision"));

            AiMediaCapabilityValidationException ex = assertThrows(
                    AiMediaCapabilityValidationException.class,
                    () -> client.renderChatPayloadWithModelAndMedia(
                            "openai/gpt-4o",
                            "Посмотри",
                            List.of(AiMediaInput.imageDataUrl("data:image/png;base64,AAA", "image.png", "image/png"))));

            assertEquals("Модель 'openai/gpt-4o' не поддерживает изображения на вход.", ex.getMessage());
        } finally {
            ConfigManager.setExternalApiMultimodalModels(previous);
        }
    }

    @Test
    @DisplayName("Блокирует смешанный audio и image payload")
    void blocksMixedAudioAndImagePayload() {
        StubHttpAiClient client = new StubHttpAiClient();
        withExternalMediaCapabilities(
                List.of("openai/gpt-4o"),
                List.of("openai/gpt-4o"),
                () -> {
                    List<String> previousMultimodal = ConfigManager.getExternalApiMultimodalModels();
                    try {
                        ConfigManager.setExternalApiMultimodalModels(List.of("openai/gpt-4o"));
                        AiMediaCapabilityValidationException ex = assertThrows(
                                AiMediaCapabilityValidationException.class,
                                () -> client.renderChatPayloadWithModelAndMedia(
                                        "openai/gpt-4o",
                                        "Смешанный запрос",
                                        List.of(
                                                AiMediaInput.audioData("QUJD", "voice.wav", "audio/wav", "wav"),
                                                AiMediaInput.imageDataUrl("data:image/png;base64,AAA", "image.png", "image/png"))));

                        assertEquals("Аудио пока можно отправлять только отдельно, без изображений и документов.", ex.getMessage());
                    } finally {
                        ConfigManager.setExternalApiMultimodalModels(previousMultimodal);
                    }
                });
    }

    @Test
    @DisplayName("Блокирует video input")
    void blocksVideoInput() {
        StubHttpAiClient client = new StubHttpAiClient();

        AiMediaCapabilityValidationException ex = assertThrows(
                AiMediaCapabilityValidationException.class,
                () -> client.renderChatPayloadWithModelAndMedia(
                        "openai/gpt-4o",
                        "Видео",
                        List.of(AiMediaInput.rawBytes(
                                AiMediaInputKind.VIDEO,
                                new byte[] {1, 2, 3},
                                "clip.mp4",
                                "video/mp4",
                                null))));

        assertEquals("Видео на вход пока не поддерживается.", ex.getMessage());
    }

    @Test
    @DisplayName("Блокирует некорректный web max results")
    void blocksInvalidWebMaxResults() {
        StubHttpAiClient client = new StubHttpAiClient("https://api.polza.ai/api/v1");

        AiPluginValidationException ex = assertThrows(
                AiPluginValidationException.class,
                () -> client.renderChatPayloadWithPlugins(
                        "openai/gpt-4o",
                        new AiRequestOptions.PluginOptions(
                                new AiRequestOptions.WebPluginOptions(true, "exa", 25, null),
                                null,
                                null)));

        assertEquals("Плагин web: max results должен быть в диапазоне 1..20.", ex.getMessage());
    }

    @Test
    @DisplayName("Блокирует некорректный file-parser pdf engine")
    void blocksInvalidFileParserPdfEngine() {
        StubHttpAiClient client = new StubHttpAiClient("https://api.polza.ai/api/v1");

        AiPluginValidationException ex = assertThrows(
                AiPluginValidationException.class,
                () -> client.renderChatPayloadWithPlugins(
                        "openai/gpt-4o",
                        new AiRequestOptions.PluginOptions(
                                null,
                                new AiRequestOptions.FileParserPluginOptions(true, "broken-engine"),
                                null)));

        assertEquals(
                "Плагин file-parser: недопустимый PDF engine. Разрешены только pdf-text, mistral-ocr или native.",
                ex.getMessage());
    }

    @Test
    @DisplayName("AiRequestOptions делает defensive copy для media inputs")
    void requestOptionsDefensiveCopyMediaInputs() {
        List<AiMediaInput> source = new java.util.ArrayList<>();
        source.add(AiMediaInput.imageDataUrl("data:image/png;base64,AAA", "image.png", "image/png"));

        AiRequestOptions options = AiRequestOptions.builder()
                .model("openai/gpt-4o")
                .mediaInputs(source)
                .build();

        source.clear();

        assertEquals(1, options.mediaInputs().size());
    }

    private static final class StubHttpAiClient extends AbstractHttpAiClient {
        private StubHttpAiClient() {
            this("http://stub.test");
        }

        private StubHttpAiClient(String baseUrl) {
            this.baseUrl = baseUrl;
            this.defaultModel = "stub-model";
        }

        private String renderChatPayload(String model, String reasoningEffort) {
            return buildChatRequestJson(
                "hello",
                AiRequestOptions.builder()
                    .model(model)
                    .reasoningEffort(reasoningEffort)
                    .build()
            );
        }

        private String renderChatPayloadWithSystem(String model, String systemPrompt) {
            return buildChatRequestJson(
                    "hello",
                    AiRequestOptions.builder()
                            .model(model)
                            .systemPrompt(systemPrompt)
                            .build());
        }

        private String renderChatPayloadWithReasoning(String model, AiRequestOptions.ReasoningOptions reasoning) {
            return buildChatRequestJson(
                    "hello",
                    AiRequestOptions.builder()
                            .model(model)
                            .reasoning(reasoning)
                            .reasoningEffort("high")
                            .build());
        }

        private String renderChatPayloadWithMedia(String userText, List<AiMediaInput> mediaInputs) {
            return renderChatPayloadWithModelAndMedia("openai/gpt-4o", userText, mediaInputs);
        }

        private String renderChatPayloadWithModelAndMedia(String model, String userText, List<AiMediaInput> mediaInputs) {
            return buildChatRequestJson(
                    userText,
                    AiRequestOptions.builder()
                            .model(model)
                            .mediaInputs(mediaInputs)
                            .build());
        }

        private String renderChatPayloadWithPlugins(String model, AiRequestOptions.PluginOptions pluginOptions) {
            return buildChatRequestJson(
                    "hello",
                    AiRequestOptions.builder()
                            .model(model)
                            .pluginOptions(pluginOptions)
                            .build());
        }

        private String renderChatPayloadWithTextParameters(
                String model,
                Integer maxTokens,
                Double temperature,
                Double topP,
                Double frequencyPenalty,
                Double presencePenalty) {
            return buildChatRequestJson(
                    "hello",
                    AiRequestOptions.builder()
                            .model(model)
                            .maxTokens(maxTokens)
                            .temperature(temperature)
                            .topP(topP)
                            .frequencyPenalty(frequencyPenalty)
                            .presencePenalty(presencePenalty)
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
            // No-op for tests.
        }
    }

    private static void withExternalMediaCapabilities(
            List<String> audioModels,
            List<String> fileModels,
            Runnable action) {
        List<String> previousAudio = ConfigManager.getExternalApiAudioInputModels();
        List<String> previousFile = ConfigManager.getExternalApiFileInputModels();
        try {
            ConfigManager.setExternalApiAudioInputModels(audioModels);
            ConfigManager.setExternalApiFileInputModels(fileModels);
            action.run();
        } finally {
            ConfigManager.setExternalApiAudioInputModels(previousAudio);
            ConfigManager.setExternalApiFileInputModels(previousFile);
        }
    }
}
