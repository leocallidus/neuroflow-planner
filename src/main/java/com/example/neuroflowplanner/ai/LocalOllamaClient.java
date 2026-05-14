package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.json.AiCoreResponseMapper;
import com.example.neuroflowplanner.ai.json.AiJsonParserMode;
import com.example.neuroflowplanner.ai.json.AiParsingException;
import com.example.neuroflowplanner.util.AiApiUtils;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.example.neuroflowplanner.ai.resilience.AiCallContext;

/**
 * AI client implementation for local Ollama server.
 * 
 * Connects to a locally running Ollama instance.
 * Uses separate configuration keys: local.ollama.baseUrl, local.ollama.model
 */
public class LocalOllamaClient extends AbstractHttpAiClient {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(LocalOllamaClient.class);

    /**
     * Configuration keys for local Ollama mode.
     */
    public static final String CONFIG_BASE_URL = "local.ollama.baseUrl";
    public static final String CONFIG_MODEL = "local.ollama.model";

    /**
     * Default Ollama URL (localhost).
     */
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "llama3";

    /**
     * Creates a LocalOllamaClient with configuration from ConfigManager.
     */
    public LocalOllamaClient() {
        super();
        reloadConfiguration();
    }

    /**
     * Creates a LocalOllamaClient with explicit configuration.
     * Useful for testing connections before saving.
     */
    public LocalOllamaClient(String baseUrl, String model) {
        super();
        this.baseUrl = normalizeUrl(baseUrl);
        this.defaultModel = model != null ? model : DEFAULT_MODEL;
    }

    @Override
    public void reloadConfiguration() {
        String configUrl = ConfigManager.getProperty(CONFIG_BASE_URL);
        this.baseUrl = normalizeUrl(configUrl != null ? configUrl : DEFAULT_BASE_URL);

        String configModel = ConfigManager.getProperty(CONFIG_MODEL);
        this.defaultModel = configModel != null ? configModel : DEFAULT_MODEL;
    }

    @Override
    public AiMode getMode() {
        return AiMode.LOCAL_OLLAMA;
    }

    @Override
    public boolean supportsImages() {
        return false;
    }

    @Override
    public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
        String chatUrl = resolveChatUrl();

        long startTime = System.currentTimeMillis();
        AiCallContext context = new AiCallContext(getMode().name(),
                options.model() != null ? options.model() : defaultModel, chatUrl, "chat");

        return resilienceExecutor.executeWithResilience(
                context,
                httpClient,
                ctx -> {
                    AiRequestOptions newOptions = options.withModel(ctx.getModel());
                    String newBody = buildOllamaChatRequest(userText, newOptions);
                    return buildRequest(chatUrl).POST(HttpRequest.BodyPublishers.ofString(newBody));
                },
                HttpResponse.BodyHandlers.ofString(),
                (response, ctx) -> handleHttpResponseWithContext(response, ctx, startTime))
                .exceptionally(e -> AiResponse.fromException(e));
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testConnection() {
        return testConnection(this.baseUrl, null);
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testConnection(String testUrl, String apiKey) {
        String normalizedUrl = normalizeUrl(testUrl);
        String tagsUrl = normalizedUrl + "/api/tags";

        long startTime = System.currentTimeMillis();
        AiCallContext context = new AiCallContext(AiMode.LOCAL_OLLAMA.name(), "tags", normalizedUrl, "test_connection");

        return resilienceExecutor.executeWithResilience(
                context,
                httpClient,
                ctx -> HttpRequest.newBuilder().uri(URI.create(tagsUrl)).GET().timeout(TEST_TIMEOUT),
                HttpResponse.BodyHandlers.ofString(),
                (response, ctx) -> {
                    long duration = System.currentTimeMillis() - startTime;

                    if (AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                        List<String> models = parseOllamaModelsResponse(response.body());
                        return ConnectionTestResult.successWithModels(
                                "Ollama сервер доступен",
                                AiMode.LOCAL_OLLAMA,
                                normalizedUrl,
                                models,
                                duration);
                    } else {
                        return ConnectionTestResult.failure(
                                "Ошибка подключения к Ollama",
                                response.body(),
                                response.statusCode(),
                                AiMode.LOCAL_OLLAMA,
                                normalizedUrl);
                    }
                })
                .exceptionally(e -> handleTestException(e, normalizedUrl));
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testModel(String model) {
        String chatUrl = resolveChatUrl();
        return sendTestMessage(chatUrl, null, model);
    }

    @Override
    public CompletableFuture<List<String>> fetchAvailableModels() {
        String tagsUrl = baseUrl + "/api/tags";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tagsUrl))
                .GET()
                .timeout(TEST_TIMEOUT)
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                        return parseOllamaModelsResponse(response.body());
                    }
                    return new ArrayList<String>();
                })
                .exceptionally(e -> new ArrayList<>());
    }

    /**
     * Resolves the chat endpoint URL.
     * Ollama uses /api/chat for chat completions.
     */
    private String resolveChatUrl() {
        return baseUrl + "/api/chat";
    }

    /**
     * Builds the request body for Ollama chat API.
     * Ollama format is slightly different from OpenAI.
     */
    private String buildOllamaChatRequest(String userText, AiRequestOptions options) {
        String model = options.model() != null ? options.model() : defaultModel;
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(escapeJson(model)).append("\",");
        json.append("\"messages\":[");

        // Add system prompt if provided
        if (options.systemPrompt() != null && !options.systemPrompt().isBlank()) {
            json.append("{\"role\":\"system\",\"content\":\"")
                    .append(escapeJson(options.systemPrompt()))
                    .append("\"},");
        }

        // Add conversation history if provided
        if (options.conversationHistory() != null) {
            for (AiRequestOptions.ChatHistoryEntry entry : options.conversationHistory()) {
                json.append("{\"role\":\"").append(escapeJson(entry.role()))
                        .append("\",\"content\":\"").append(escapeJson(entry.content()))
                        .append("\"},");
            }
        }

        // Add current user message
        json.append("{\"role\":\"user\",\"content\":\"")
                .append(escapeJson(userText))
                .append("\"}");

        json.append("],");
        json.append("\"stream\":").append(options.stream());

        // Add Ollama-specific options
        json.append(",\"options\":{");
        boolean hasOptions = false;
        if (options.temperature() != null) {
            json.append("\"temperature\":").append(options.temperature());
            hasOptions = true;
        }
        if (options.maxTokens() != null) {
            if (hasOptions)
                json.append(",");
            json.append("\"num_predict\":").append(options.maxTokens());
        }
        json.append("}");

        json.append("}");
        return json.toString();
    }

    /**
     * Parses the Ollama /api/tags response to extract model names.
     */
    private List<String> parseOllamaModelsResponse(String responseBody) {
        AiJsonParserMode mode = ConfigManager.getAiJsonParserMode();
        return switch (mode) {
            case LEGACY -> parseOllamaModelsResponseLegacy(responseBody);
            case JACKSON -> parseOllamaModelsResponseJackson(responseBody);
            case DUAL -> parseOllamaModelsResponseDual(responseBody);
        };
    }

    private List<String> parseOllamaModelsResponseJackson(String responseBody) {
        List<String> rawModels = AiCoreResponseMapper.extractModelNames(responseBody);
        return normalizeOllamaModelNames(rawModels);
    }

    private List<String> parseOllamaModelsResponseDual(String responseBody) {
        List<String> legacyModels = parseOllamaModelsResponseLegacy(responseBody);
        List<String> jacksonModels = null;
        AiParsingException jacksonError = null;

        try {
            jacksonModels = parseOllamaModelsResponseJackson(responseBody);
        } catch (AiParsingException e) {
            jacksonError = e;
        }

        if (!Objects.equals(legacyModels, jacksonModels) || jacksonError != null) {
            LOG.warning("ai.json.dual.mismatch",
                    "operation", "ollama_models_list",
                    "provider", getMode().name().toLowerCase(Locale.ROOT),
                    "legacyCount", legacyModels == null ? 0 : legacyModels.size(),
                    "jacksonCount", jacksonModels == null ? 0 : jacksonModels.size(),
                    "legacyHash", safeHash(legacyModels),
                    "jacksonHash", safeHash(jacksonModels),
                    "jacksonError", jacksonError == null ? "" : jacksonError.getClass().getSimpleName());
        }

        if (legacyModels != null && !legacyModels.isEmpty()) {
            return legacyModels;
        }
        if (jacksonModels != null) {
            return jacksonModels;
        }
        if (jacksonError != null) {
            throw jacksonError;
        }
        return new ArrayList<>();
    }

    private List<String> parseOllamaModelsResponseLegacy(String responseBody) {
        try {
            return parseOllamaModelsResponseJackson(responseBody);
        } catch (AiParsingException e) {
            return new ArrayList<>();
        }
    }

    private List<String> normalizeOllamaModelNames(List<String> rawModels) {
        List<String> models = new ArrayList<>();
        if (rawModels == null) {
            return models;
        }
        for (String rawModel : rawModels) {
            if (rawModel == null || rawModel.isBlank()) {
                continue;
            }
            String normalized = rawModel.trim();
            if (normalized.endsWith(":latest")) {
                normalized = normalized.substring(0, normalized.length() - 7);
            }
            if (!models.contains(normalized)) {
                models.add(normalized);
            }
        }
        return models;
    }

    private String safeHash(List<String> values) {
        if (values == null) {
            return "null";
        }
        return Integer.toHexString(values.hashCode());
    }
}
