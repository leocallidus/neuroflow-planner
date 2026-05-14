package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.media.AiMediaInput;
import com.example.neuroflowplanner.ai.json.AiCoreResponseMapper;
import com.example.neuroflowplanner.ai.json.AiJsonParserMode;
import com.example.neuroflowplanner.ai.json.AiParsingException;
import com.example.neuroflowplanner.util.AiApiUtils;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.SensitiveDataRedactor;
import com.example.neuroflowplanner.util.StructuredLogger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.example.neuroflowplanner.ai.resilience.AiCallContext;

/**
 * AI client implementation for external OpenAI-compatible APIs.
 * 
 * Works with any OpenAI-compatible API (OpenAI, Azure OpenAI, Anthropic via
 * proxy, etc.).
 * Uses separate configuration keys: external.api.baseUrl, external.api.key,
 * external.api.model
 * 
 * Supports image generation (only in this mode).
 */
public class ExternalOpenAiClient extends AbstractHttpAiClient {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ExternalOpenAiClient.class);

    /**
     * Configuration keys for external API mode.
     */
    public static final String CONFIG_BASE_URL = "external.api.baseUrl";
    public static final String CONFIG_API_KEY = "external.api.key";
    public static final String CONFIG_MODEL = "external.api.model";
    public static final String CONFIG_IMAGE_MODEL = "external.image.model";

    /**
     * Default values.
     */
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_IMAGE_MODEL = "dall-e-3";

    private String apiKey;
    private String imageModel;

    /**
     * Creates an ExternalOpenAiClient with configuration from ConfigManager.
     */
    public ExternalOpenAiClient() {
        super();
        reloadConfiguration();
    }

    /**
     * Creates an ExternalOpenAiClient with explicit configuration.
     * Useful for testing connections before saving.
     */
    public ExternalOpenAiClient(String baseUrl, String apiKey, String model) {
        super();
        this.baseUrl = normalizeUrl(baseUrl);
        this.apiKey = apiKey;
        this.defaultModel = model != null ? model : DEFAULT_MODEL;
        this.imageModel = DEFAULT_IMAGE_MODEL;
    }

    @Override
    public void reloadConfiguration() {
        String configUrl = ConfigManager.getProperty(CONFIG_BASE_URL);
        this.baseUrl = normalizeUrl(configUrl != null ? configUrl : DEFAULT_BASE_URL);

        this.apiKey = ConfigManager.getProperty(CONFIG_API_KEY);

        String configModel = ConfigManager.getProperty(CONFIG_MODEL);
        this.defaultModel = configModel != null ? configModel : DEFAULT_MODEL;

        String configImageModel = ConfigManager.getProperty(CONFIG_IMAGE_MODEL);
        this.imageModel = configImageModel != null ? configImageModel : DEFAULT_IMAGE_MODEL;
    }

    @Override
    public AiMode getMode() {
        return AiMode.EXTERNAL_OPENAI;
    }

    @Override
    public boolean supportsImages() {
        return true;
    }

    @Override
    public boolean supportsImageInputs() {
        return true;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean isConfigured() {
        return super.isConfigured() && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Returns the configured image model.
     */
    public String getImageModel() {
        return imageModel;
    }

    /**
     * Returns the API key (for testing purposes, masked).
     */
    public String getMaskedApiKey() {
        return SensitiveDataRedactor.maskSecret(apiKey);
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
                    String newBody = buildChatRequestJson(userText, newOptions);
                    HttpRequest.Builder b = buildRequest(chatUrl)
                            .POST(HttpRequest.BodyPublishers.ofString(newBody));
                    addAuthHeader(b, apiKey);
                    return b;
                },
                HttpResponse.BodyHandlers.ofString(),
                (response, ctx) -> handleHttpResponseWithContext(response, ctx, startTime))
                .exceptionally(e -> AiResponse.fromException(e));
    }

    @Override
    public CompletableFuture<AiResponse> sendChatMessageStreaming(
            String userText,
            AiRequestOptions options,
            Consumer<AiStreamChunk> onChunk) {
        String chatUrl = resolveChatUrl();
        long startTime = System.currentTimeMillis();

        AiCallContext context = new AiCallContext(
                getMode().name(),
                options.model() != null ? options.model() : defaultModel,
                chatUrl,
                "chat_stream");

        return resilienceExecutor.executeWithResilience(
                context,
                httpClient,
                ctx -> {
                    AiRequestOptions streamOptions = options.withModel(ctx.getModel()).withStream(true);
                    String body = buildChatRequestJson(userText, streamOptions);
                    HttpRequest.Builder builder = buildRequest(chatUrl)
                            .POST(HttpRequest.BodyPublishers.ofString(body));
                    addAuthHeader(builder, apiKey);
                    return builder;
                },
                HttpResponse.BodyHandlers.ofLines(),
                (response, ctx) -> handleStreamingHttpResponse(response, ctx, startTime, onChunk))
                .exceptionally(e -> AiResponse.fromException(e));
    }

    @Override
    public CompletableFuture<AiResponse> sendChatMessageWithImages(String userText, List<AiImageInput> images,
            AiRequestOptions options) {
        List<AiMediaInput> mediaInputs = images == null
                ? List.of()
                : images.stream()
                        .filter(Objects::nonNull)
                        .map(AiImageInput::toMediaInput)
                        .toList();
        return sendChatMessageWithMedia(userText, mediaInputs, options);
    }

    @Override
    public CompletableFuture<AiResponse> sendChatMessageWithMedia(String userText, List<AiMediaInput> mediaInputs,
            AiRequestOptions options) {
        String chatUrl = resolveChatUrl();

        long startTime = System.currentTimeMillis();

        AiCallContext context = new AiCallContext(getMode().name(),
                options.model() != null ? options.model() : defaultModel, chatUrl, "chat_with_media");

        return resilienceExecutor.executeWithResilience(
                context,
                httpClient,
                ctx -> {
                    AiRequestOptions newOptions = options.withModel(ctx.getModel()).withMediaInputs(mediaInputs);
                    String newBody = buildChatRequestJson(userText, newOptions);
                    HttpRequest.Builder b = buildRequest(chatUrl)
                            .POST(HttpRequest.BodyPublishers.ofString(newBody));
                    addAuthHeader(b, apiKey);
                    return b;
                },
                HttpResponse.BodyHandlers.ofString(),
                (response, ctx) -> handleHttpResponseWithContext(response, ctx, startTime))
                .exceptionally(e -> AiResponse.fromException(e));
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testConnection() {
        return testConnection(this.baseUrl, this.apiKey);
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testConnection(String testUrl, String testApiKey) {
        String normalizedUrl = normalizeUrl(testUrl);
        long startTime = System.currentTimeMillis();
        String chatOnlyUrl = normalizedUrl + "/models?type=chat";
        String fallbackUrl = normalizedUrl + "/models";

        return requestModelCatalog(chatOnlyUrl, testApiKey)
                .thenCompose(response -> {
                    if (AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                        return CompletableFuture.completedFuture(
                                toSuccessfulConnectionResult(response.body(), normalizedUrl, startTime, false));
                    }
                    if (shouldFallbackToGenericModels(response.statusCode())) {
                        return requestModelCatalog(fallbackUrl, testApiKey)
                                .thenApply(fallbackResponse -> toConnectionResultFromResponse(
                                        fallbackResponse,
                                        normalizedUrl,
                                        startTime,
                                        true));
                    }
                    return CompletableFuture.completedFuture(toFailureResult(response, normalizedUrl));
                })
                .exceptionally(e -> handleTestException(e, normalizedUrl));
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testModel(String model) {
        String chatUrl = resolveChatUrl();
        return sendTestMessage(chatUrl, apiKey, model);
    }

    @Override
    public CompletableFuture<List<String>> fetchAvailableModels() {
        String modelsUrl = baseUrl + "/models";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(modelsUrl))
                .GET()
                .timeout(TEST_TIMEOUT);
        addAuthHeader(builder, apiKey);
        HttpRequest request = builder.build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                        return parseModelsResponse(response.body());
                    }
                    return new ArrayList<String>();
                })
                .exceptionally(e -> new ArrayList<>());
    }

    public CompletableFuture<List<AiDiscoveredModelInfo>> fetchChatModelCatalog() {
        return fetchChatModelCatalog(baseUrl, apiKey);
    }

    /**
     * Fetches available image generation models.
     */
    public CompletableFuture<List<String>> fetchImageModels() {
        return fetchAvailableModels().thenApply(models -> {
            // Filter to only image-related models
            List<String> imageModels = new ArrayList<>();
            for (String model : models) {
                String lowerModel = model.toLowerCase();
                if (lowerModel.contains("dall-e") ||
                        lowerModel.contains("image") ||
                        lowerModel.contains("stable") ||
                        lowerModel.contains("midjourney") ||
                        lowerModel.contains("seedream") ||
                        lowerModel.contains("flux") ||
                        lowerModel.contains("grok-imagine") ||
                        lowerModel.contains("qwen/image")) {
                    imageModels.add(model);
                }
            }
            // If no image models found, return common defaults
            if (imageModels.isEmpty()) {
                imageModels.add("openai/gpt-5.4-image-2");
                imageModels.add("openai/gpt-5-image");
                imageModels.add("openai/gpt-5-image-mini");
                imageModels.add("openai/gpt-image-1.5");
                imageModels.add("google/gemini-3-pro-image-preview");
                imageModels.add("google/gemini-3.1-flash-image-preview");
                imageModels.add("google/gemini-2.5-flash-image");
                imageModels.add("bytedance/seedream-5-lite");
                imageModels.add("bytedance/seedream-4.5");
                imageModels.add("bytedance/seedream-4");
                imageModels.add("bytedance/seedream");
                imageModels.add("qwen/image");
                imageModels.add("x-ai/grok-imagine-image");
                imageModels.add("black-forest-labs/flux.2-pro");
                imageModels.add("black-forest-labs/flux.2-flex");
                imageModels.add("dall-e-3");
            }
            return imageModels;
        });
    }

    private CompletableFuture<List<AiDiscoveredModelInfo>> fetchChatModelCatalog(String normalizedUrl, String key) {
        String chatOnlyUrl = normalizedUrl + "/models?type=chat";
        String fallbackUrl = normalizedUrl + "/models";
        return requestModelCatalog(chatOnlyUrl, key).thenCompose(response -> {
            if (AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                return CompletableFuture.completedFuture(parseChatCapableCatalog(response.body(), false));
            }
            if (shouldFallbackToGenericModels(response.statusCode())) {
                return requestModelCatalog(fallbackUrl, key)
                        .thenApply(fallbackResponse -> {
                            if (!AiApiUtils.isSuccessfulStatus(fallbackResponse.statusCode())) {
                                throw new IllegalStateException(
                                        "Models request failed with status " + fallbackResponse.statusCode());
                            }
                            return parseChatCapableCatalog(fallbackResponse.body(), true);
                        });
            }
            throw new IllegalStateException("Models request failed with status " + response.statusCode());
        });
    }

    private CompletableFuture<HttpResponse<String>> requestModelCatalog(String modelsUrl, String key) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(modelsUrl))
                .GET()
                .timeout(TEST_TIMEOUT);
        addAuthHeader(builder, key);
        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private ConnectionTestResult toConnectionResultFromResponse(
            HttpResponse<String> response,
            String normalizedUrl,
            long startTime,
            boolean filterChatCapable) {
        if (!AiApiUtils.isSuccessfulStatus(response.statusCode())) {
            return toFailureResult(response, normalizedUrl);
        }
        return toSuccessfulConnectionResult(response.body(), normalizedUrl, startTime, filterChatCapable);
    }

    private ConnectionTestResult toSuccessfulConnectionResult(
            String responseBody,
            String normalizedUrl,
            long startTime,
            boolean filterChatCapable) {
        List<AiDiscoveredModelInfo> catalog = parseChatCapableCatalog(responseBody, filterChatCapable);
        long duration = System.currentTimeMillis() - startTime;
        List<String> models = catalog.stream()
                .map(AiDiscoveredModelInfo::id)
                .toList();
        List<String> multimodalModels = catalog.stream()
                .filter(AiDiscoveredModelInfo::multimodal)
                .map(AiDiscoveredModelInfo::id)
                .toList();
        List<String> audioInputModels = catalog.stream()
                .filter(AiDiscoveredModelInfo::supportsAudioInput)
                .map(AiDiscoveredModelInfo::id)
                .toList();
        List<String> fileInputModels = catalog.stream()
                .filter(AiDiscoveredModelInfo::supportsFileInput)
                .map(AiDiscoveredModelInfo::id)
                .toList();
        return ConnectionTestResult.successWithModels(
                "API доступен",
                AiMode.EXTERNAL_OPENAI,
                normalizedUrl,
                models,
                multimodalModels,
                audioInputModels,
                fileInputModels,
                catalog,
                duration);
    }

    private List<AiDiscoveredModelInfo> parseChatCapableCatalog(String responseBody, boolean filterChatCapable) {
        List<AiDiscoveredModelInfo> catalog = AiCoreResponseMapper.extractModelCatalog(responseBody);
        if (!filterChatCapable) {
            return catalog;
        }
        return filterChatCapableModels(catalog);
    }

    private boolean shouldFallbackToGenericModels(int statusCode) {
        return statusCode == 400 || statusCode == 404 || statusCode == 405;
    }

    private ConnectionTestResult toFailureResult(HttpResponse<String> response, String normalizedUrl) {
        int statusCode = response.statusCode();
        String responseBody = response.body();
        if (statusCode == 401) {
            return ConnectionTestResult.failure(
                    "Неверный API ключ",
                    responseBody,
                    statusCode,
                    AiMode.EXTERNAL_OPENAI,
                    normalizedUrl);
        }
        if (statusCode == 403) {
            return ConnectionTestResult.failure(
                    "Доступ запрещен",
                    responseBody,
                    statusCode,
                    AiMode.EXTERNAL_OPENAI,
                    normalizedUrl);
        }
        return ConnectionTestResult.failure(
                "Ошибка API: " + statusCode,
                responseBody,
                statusCode,
                AiMode.EXTERNAL_OPENAI,
                normalizedUrl);
    }

    private List<AiDiscoveredModelInfo> filterChatCapableModels(List<AiDiscoveredModelInfo> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        List<AiDiscoveredModelInfo> filtered = models.stream()
                .filter(model -> model != null && (!"image".equalsIgnoreCase(model.type()) || model.multimodal()))
                .filter(model -> model.type() == null || model.type().isBlank() || "chat".equalsIgnoreCase(model.type()))
                .collect(Collectors.toCollection(ArrayList::new));
        return filtered.isEmpty() ? List.copyOf(models) : List.copyOf(filtered);
    }

    /**
     * Tests an image generation model.
     */
    public CompletableFuture<ConnectionTestResult> testImageModel(String model) {
        String imageUrl = resolveMediaImageUrl();
        String requestBody = buildImageGenerationRequest("A simple test image of a blue circle", model);

        long startTime = System.currentTimeMillis();
        AiCallContext context = new AiCallContext(AiMode.EXTERNAL_OPENAI.name(), model, imageUrl, "test_image_model");

        return resilienceExecutor.executeWithResilience(
                context,
                httpClient,
                ctx -> {
                    String newBody = buildImageGenerationRequest("A simple test image of a blue circle",
                            ctx.getModel());
                    HttpRequest.Builder b = buildRequest(imageUrl)
                            .POST(HttpRequest.BodyPublishers.ofString(newBody))
                            .timeout(java.time.Duration.ofSeconds(60)); // Image generation takes longer
                    addAuthHeader(b, apiKey);
                    return b;
                },
                HttpResponse.BodyHandlers.ofString(),
                (response, ctx) -> {
                    long duration = System.currentTimeMillis() - startTime;

                    if (AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                        String imageUrl2 = extractImageUrl(response.body());
                        String requestId = extractImageRequestId(response.body());
                        return ConnectionTestResult.success(
                                "Модель генерации изображений работает",
                                AiMode.EXTERNAL_OPENAI,
                                baseUrl,
                                model,
                                imageUrl2 != null ? imageUrl2
                                        : requestId != null ? "Запрос принят: " + requestId : "Изображение успешно сгенерировано",
                                duration);
                    } else {
                        return ConnectionTestResult.failure(
                                "Ошибка генерации изображения: " + response.statusCode(),
                                response.body(),
                                response.statusCode(),
                                AiMode.EXTERNAL_OPENAI,
                                baseUrl);
                    }
                })
                .exceptionally(e -> handleTestException(e, baseUrl));
    }

    /**
     * Resolves the chat completions endpoint URL.
     */
    private String resolveChatUrl() {
        return AiApiUtils.resolveChatUrl(baseUrl);
    }

    private AiResponse handleStreamingHttpResponse(
            HttpResponse<Stream<String>> response,
            AiCallContext context,
            long startTime,
            Consumer<AiStreamChunk> onChunk) {
        long duration = System.currentTimeMillis() - startTime;
        int status = response.statusCode();
        String model = context != null ? context.getModel() : defaultModel;
        int attempts = context != null ? context.getAttempt() : 1;

        if (!AiApiUtils.isSuccessfulStatus(status)) {
            return new AiResponse(
                    null,
                    false,
                    "Ошибка API: " + status,
                    status,
                    model,
                    null,
                    null,
                    null,
                    java.time.Instant.now(),
                    duration,
                    status,
                    attempts);
        }

        StringBuilder accumulated = new StringBuilder();
        StringBuilder fallbackRaw = new StringBuilder();
        try (Stream<String> lines = response.body()) {
            lines.forEachOrdered(line -> {
                if (line == null || line.isBlank()) {
                    return;
                }
                String trimmed = line.trim();
                String payload = trimmed.startsWith("data:") ? trimmed.substring(5).trim() : trimmed;
                if (payload.isEmpty()) {
                    return;
                }
                if (isSseDonePayload(payload)) {
                    return;
                }
                String delta = extractStreamDeltaContent(payload);
                if (delta != null && !delta.isBlank()) {
                    accumulated.append(delta);
                    if (onChunk != null) {
                        onChunk.accept(AiStreamChunk.delta(delta, model));
                    }
                    return;
                }
                if (!trimmed.startsWith("data:") || hasStreamFinishReason(payload)) {
                    fallbackRaw.append(payload).append('\n');
                }
            });
        }

        String content = accumulated.toString();
        if (content.isBlank() && fallbackRaw.length() > 0) {
            try {
                content = parseResponseContent(fallbackRaw.toString());
            } catch (AiParsingException ignored) {
                // Keep empty content and convert to controlled error below.
            }
        }

        if (content == null || content.isBlank()) {
            return new AiResponse(
                    null,
                    false,
                    "Не удалось извлечь потоковый ответ",
                    status,
                    model,
                    null,
                    null,
                    null,
                    java.time.Instant.now(),
                    duration,
                    status,
                    attempts);
        }

        if (onChunk != null) {
            onChunk.accept(AiStreamChunk.done(model));
        }

        return AiResponse.success(content, model)
                .withDuration(duration)
                .withAttempts(attempts);
    }

    /**
     * Builds the request body for image generation.
     */
    private String buildImageGenerationRequest(String prompt, String model) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(escapeJson(model)).append("\",");
        json.append("\"input\":{");
        json.append("\"prompt\":\"").append(escapeJson(prompt)).append("\"");
        json.append("},");
        json.append("\"async\":true");
        json.append("}");
        return json.toString();
    }

    private String resolveMediaImageUrl() {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        normalized = stripMediaSuffix(normalized, "/images/generations");
        normalized = stripMediaSuffix(normalized, "/history/generations");
        normalized = stripMediaSuffix(normalized, "/media");
        normalized = stripMediaSuffix(normalized, "/images");
        return normalized + "/media";
    }

    private String stripMediaSuffix(String value, String suffix) {
        if (value == null || suffix == null || suffix.isBlank()) {
            return value == null ? "" : value;
        }
        if (value.endsWith(suffix)) {
            return value.substring(0, value.length() - suffix.length());
        }
        return value;
    }

    /**
     * Extracts the image URL from the generation response.
     */
    private String extractImageUrl(String responseBody) {
        AiJsonParserMode mode = ConfigManager.getAiJsonParserMode();
        return switch (mode) {
            case LEGACY -> extractImageUrlLegacy(responseBody);
            case JACKSON -> extractImageUrlJackson(responseBody);
            case DUAL -> extractImageUrlDual(responseBody);
        };
    }

    private String extractImageUrlJackson(String responseBody) {
        return AiCoreResponseMapper.extractImageUrlFromGeneration(responseBody);
    }

    private String extractImageUrlDual(String responseBody) {
        String legacyUrl = extractImageUrlLegacy(responseBody);
        String jacksonUrl = null;
        AiParsingException jacksonError = null;

        try {
            jacksonUrl = extractImageUrlJackson(responseBody);
        } catch (AiParsingException e) {
            jacksonError = e;
        }

        if (!Objects.equals(legacyUrl, jacksonUrl) || jacksonError != null) {
            LOG.warning("ai.json.dual.mismatch",
                    "operation", "image_url_generation",
                    "provider", getMode().name().toLowerCase(Locale.ROOT),
                    "legacyPresent", legacyUrl != null,
                    "jacksonPresent", jacksonUrl != null,
                    "legacyHash", safeHash(legacyUrl),
                    "jacksonHash", safeHash(jacksonUrl),
                    "jacksonError", jacksonError == null ? "" : jacksonError.getClass().getSimpleName());
        }

        if (legacyUrl != null) {
            return legacyUrl;
        }
        if (jacksonUrl != null) {
            return jacksonUrl;
        }
        if (jacksonError != null) {
            throw jacksonError;
        }
        return null;
    }

    private String extractImageUrlLegacy(String responseBody) {
        try {
            return extractImageUrlJackson(responseBody);
        } catch (AiParsingException e) {
            return null;
        }
    }

    private String extractImageRequestId(String responseBody) {
        try {
            return AiCoreResponseMapper.extractImageRequestIdFromGeneration(responseBody);
        } catch (AiParsingException e) {
            return null;
        }
    }

    private String safeHash(String value) {
        if (value == null) {
            return "null";
        }
        return Integer.toHexString(value.hashCode());
    }
}
