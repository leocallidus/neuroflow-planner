package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.media.AiMediaInput;
import com.example.neuroflowplanner.ai.media.AiModelMediaCapabilityPolicy;
import com.example.neuroflowplanner.ai.json.AiCoreResponseMapper;
import com.example.neuroflowplanner.ai.json.AiJsonParserMode;
import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.ai.json.AiParsingException;
import com.example.neuroflowplanner.util.AiApiUtils;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.fasterxml.jackson.databind.JsonNode;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.example.neuroflowplanner.ai.resilience.AiCallContext;
import com.example.neuroflowplanner.ai.resilience.AiResilienceExecutor;
import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.ConfigManager;

/**
 * Abstract base class for HTTP-based AI clients.
 * Provides common functionality for LocalOllamaClient and ExternalOpenAiClient.
 */
public abstract class AbstractHttpAiClient implements AiClient {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(AbstractHttpAiClient.class);

    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    protected static final Duration TEST_TIMEOUT = Duration.ofSeconds(15);

    protected HttpClient httpClient;
    protected String baseUrl;
    protected String defaultModel;
    protected AiResilienceExecutor resilienceExecutor;

    /**
     * Creates a new HTTP client instance.
     * Uses a trust-all SSL context for local development servers.
     */
    protected AbstractHttpAiClient() {
        this.httpClient = createHttpClient();
        this.resilienceExecutor = new AiResilienceExecutor(ConfigManager.getAiResiliencePolicy());
    }

    /**
     * Creates an HttpClient that trusts all certificates.
     * This is needed for local Ollama servers with self-signed certs.
     */
    protected HttpClient createHttpClient() {
        Duration connectTimeout = resolveConnectTimeout();
        try {
            TrustManager[] trustAll = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String type) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String type) {
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(connectTimeout)
                    .build();
        } catch (Exception e) {
            return HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
        }
    }

    private Duration resolveConnectTimeout() {
        try {
            Duration configured = ConfigManager.getAiResiliencePolicy().getConnectTimeout();
            if (configured != null && !configured.isNegative() && !configured.isZero()) {
                return configured;
            }
        } catch (RuntimeException ignored) {
            // Fallback to safe default timeout if configuration is unavailable.
        }
        return DEFAULT_TIMEOUT;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    @Override
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    /**
     * Builds the JSON request body for a chat completion.
     */
    protected String buildChatRequestJson(String userText, AiRequestOptions options) {
        String model = options.model() != null ? options.model() : defaultModel;
        validateRequestGuardrails(model, options);
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(escapeJson(model)).append("\",");
        json.append("\"messages\":[");

        // Add system prompt if provided
        if (options.systemPrompt() != null && !options.systemPrompt().isBlank()) {
            appendTextMessage(json, "system", options.systemPrompt(), model);
            json.append(",");
        }

        // Add conversation history if provided
        if (options.conversationHistory() != null) {
            for (AiRequestOptions.ChatHistoryEntry entry : options.conversationHistory()) {
                appendTextMessage(json, entry.role(), entry.content(), model);
                json.append(",");
            }
        }

        // Add current user message
        appendUserMessage(json, userText, options.mediaInputs(), model);

        json.append("],");
        json.append("\"stream\":").append(options.stream());

        // Add optional parameters
        if (options.temperature() != null) {
            json.append(",\"temperature\":").append(options.temperature());
        }
        if (options.maxTokens() != null) {
            json.append(",\"max_tokens\":").append(options.maxTokens());
        }
        if (options.topP() != null) {
            json.append(",\"top_p\":").append(options.topP());
        }
        if (options.frequencyPenalty() != null) {
            json.append(",\"frequency_penalty\":").append(options.frequencyPenalty());
        }
        if (options.presencePenalty() != null) {
            json.append(",\"presence_penalty\":").append(options.presencePenalty());
        }
        appendPlugins(json, options);
        boolean structuredReasoning = appendStructuredReasoning(json, model, options);
        if (!structuredReasoning) {
            appendReasoningEffort(json, model, options);
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Builds the JSON request body for a multimodal chat completion (text + image
     * inputs).
     *
     * <p>
     * Based on {@code IMAGES_INTEGRATE.md}: the last user message uses a content
     * array
     * with {@code type: text} followed by one or more {@code type: image_url}
     * entries.
     * </p>
     */
    protected String buildChatRequestJsonWithImages(String userText, List<AiImageInput> images,
            AiRequestOptions options) {
        if (images == null || images.isEmpty()) {
            return buildChatRequestJson(userText, options);
        }
        List<AiMediaInput> mediaInputs = images.stream()
                .filter(Objects::nonNull)
                .map(AiImageInput::toMediaInput)
                .toList();
        return buildChatRequestJsonWithMedia(userText, mediaInputs, options);
    }

    protected String buildChatRequestJsonWithMedia(String userText, List<AiMediaInput> mediaInputs,
            AiRequestOptions options) {
        if (mediaInputs == null || mediaInputs.isEmpty()) {
            return buildChatRequestJson(userText, options);
        }

        String model = options.model() != null ? options.model() : defaultModel;
        validateRequestGuardrails(model, options.withMediaInputs(mediaInputs));
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(escapeJson(model)).append("\",");
        json.append("\"messages\":[");

        if (options.systemPrompt() != null && !options.systemPrompt().isBlank()) {
            appendTextMessage(json, "system", options.systemPrompt(), model);
            json.append(",");
        }

        if (options.conversationHistory() != null) {
            for (AiRequestOptions.ChatHistoryEntry entry : options.conversationHistory()) {
                appendTextMessage(json, entry.role(), entry.content(), model);
                json.append(",");
            }
        }

        appendUserMessage(json, userText, mediaInputs, model);

        json.append("],");
        json.append("\"stream\":").append(options.stream());

        if (options.temperature() != null) {
            json.append(",\"temperature\":").append(options.temperature());
        }
        if (options.maxTokens() != null) {
            json.append(",\"max_tokens\":").append(options.maxTokens());
        }
        if (options.topP() != null) {
            json.append(",\"top_p\":").append(options.topP());
        }
        if (options.frequencyPenalty() != null) {
            json.append(",\"frequency_penalty\":").append(options.frequencyPenalty());
        }
        if (options.presencePenalty() != null) {
            json.append(",\"presence_penalty\":").append(options.presencePenalty());
        }
        appendPlugins(json, options);
        boolean structuredReasoning = appendStructuredReasoning(json, model, options);
        if (!structuredReasoning) {
            appendReasoningEffort(json, model, options);
        }

        json.append("}");
        return json.toString();
    }

    private void validateRequestGuardrails(String model, AiRequestOptions options) {
        if (options == null) {
            return;
        }
        validateMediaInputCapabilities(model, options.mediaInputs());
        validatePluginOptions(options.pluginOptions());
    }

    private void validateMediaInputCapabilities(String model, List<AiMediaInput> mediaInputs) {
        if (mediaInputs == null || mediaInputs.isEmpty()) {
            return;
        }
        AiModelMediaCapabilityPolicy.validateExternalModelMediaInputs(model, mediaInputs);
    }

    private void validatePluginOptions(AiRequestOptions.PluginOptions pluginOptions) {
        if (!supportsPluginPayload()) {
            return;
        }
        AiPluginValidationPolicy.validate(pluginOptions);
    }

    /**
     * Builds an HTTP request with common headers.
     */
    protected HttpRequest.Builder buildRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(DEFAULT_TIMEOUT);
    }

    /**
     * Adds authorization header if API key is provided.
     */
    protected void addAuthHeader(HttpRequest.Builder builder, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
    }

    /**
     * Parses the response content from a chat completion response.
     */
    protected String parseResponseContent(String responseBody) {
        AiJsonParserMode mode = ConfigManager.getAiJsonParserMode();
        return switch (mode) {
            case LEGACY -> parseResponseContentLegacy(responseBody);
            case JACKSON -> parseResponseContentJackson(responseBody);
            case DUAL -> parseResponseContentDual(responseBody);
        };
    }

    /**
     * Parses the list of models from a /models endpoint response.
     * Normalizes model names and removes duplicates.
     */
    protected List<String> parseModelsResponse(String responseBody) {
        AiJsonParserMode mode = ConfigManager.getAiJsonParserMode();
        return switch (mode) {
            case LEGACY -> parseModelsResponseLegacy(responseBody);
            case JACKSON -> parseModelsResponseJackson(responseBody);
            case DUAL -> parseModelsResponseDual(responseBody);
        };
    }

    protected String parseResponseContentJackson(String responseBody) {
        return AiCoreResponseMapper.extractChatContent(responseBody);
    }

    protected String parseResponseContentLegacy(String responseBody) {
        try {
            return parseResponseContentJackson(responseBody);
        } catch (AiParsingException e) {
            return null;
        }
    }

    protected List<String> parseModelsResponseJackson(String responseBody) {
        List<String> rawModels = AiCoreResponseMapper.extractModelNames(responseBody);
        return normalizeAndDedupeModels(rawModels);
    }

    protected List<String> parseModelsResponseLegacy(String responseBody) {
        try {
            return parseModelsResponseJackson(responseBody);
        } catch (AiParsingException e) {
            return new ArrayList<>();
        }
    }

    private String parseResponseContentDual(String responseBody) {
        String legacyContent = parseResponseContentLegacy(responseBody);
        String jacksonContent = null;
        AiParsingException jacksonError = null;

        try {
            jacksonContent = parseResponseContentJackson(responseBody);
        } catch (AiParsingException e) {
            jacksonError = e;
        }

        if (!Objects.equals(legacyContent, jacksonContent) || jacksonError != null) {
            LOG.warning("ai.json.dual.mismatch",
                    "operation", "chat_content",
                    "provider", getMode().name().toLowerCase(Locale.ROOT),
                    "legacyPresent", legacyContent != null,
                    "jacksonPresent", jacksonContent != null,
                    "legacyLength", safeLength(legacyContent),
                    "jacksonLength", safeLength(jacksonContent),
                    "legacyHash", safeHash(legacyContent),
                    "jacksonHash", safeHash(jacksonContent),
                    "jacksonError", jacksonError == null ? "" : jacksonError.getClass().getSimpleName());
        }

        if (legacyContent != null) {
            return legacyContent;
        }
        if (jacksonContent != null) {
            return jacksonContent;
        }
        if (jacksonError != null) {
            throw jacksonError;
        }
        throw new AiParsingException("AI response does not contain assistant content.");
    }

    private List<String> parseModelsResponseDual(String responseBody) {
        List<String> legacyModels = parseModelsResponseLegacy(responseBody);
        List<String> jacksonModels = null;
        AiParsingException jacksonError = null;

        try {
            jacksonModels = parseModelsResponseJackson(responseBody);
        } catch (AiParsingException e) {
            jacksonError = e;
        }

        if (!Objects.equals(legacyModels, jacksonModels) || jacksonError != null) {
            LOG.warning("ai.json.dual.mismatch",
                    "operation", "models_list",
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

    private List<String> normalizeAndDedupeModels(List<String> rawModels) {
        List<String> models = new ArrayList<>();
        Set<String> normalizedSet = new HashSet<>();

        if (rawModels == null) {
            return models;
        }

        for (String modelName : rawModels) {
            if (modelName == null || modelName.isBlank()) {
                continue;
            }
            String normalized = normalizeModelName(modelName);
            if (normalized == null || normalized.isBlank()) {
                continue;
            }
            String key = normalized.toLowerCase(Locale.ROOT);
            if (normalizedSet.add(key)) {
                models.add(normalized);
            }
        }

        return models;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String safeHash(String value) {
        if (value == null) {
            return "null";
        }
        return Integer.toHexString(value.hashCode());
    }

    private String safeHash(List<String> values) {
        if (values == null) {
            return "null";
        }
        return Integer.toHexString(values.hashCode());
    }

    /**
     * Normalizes a model name to a consistent format.
     * Converts "Provider: Model-Name" to "provider/model-name" format.
     * Keeps "provider/model" format as-is.
     */
    protected String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return modelName;
        }

        String trimmed = modelName.trim();

        // Check if already in provider/model format
        if (trimmed.contains("/") && !trimmed.contains(":")) {
            return trimmed;
        }

        // Convert "Provider: Model-Name" to "provider/model-name"
        if (trimmed.contains(":")) {
            int colonIndex = trimmed.indexOf(":");
            String provider = trimmed.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
            String model = trimmed.substring(colonIndex + 1).trim();

            // Normalize provider name
            provider = provider.replace(" ", "-");

            // Normalize model name: convert to lowercase, replace spaces with dashes
            String normalizedModel = model.toLowerCase()
                    .replace(" ", "-")
                    .replaceAll("-+", "-"); // Remove duplicate dashes

            return provider + "/" + normalizedModel;
        }

        // Return as-is if no special format detected
        return trimmed;
    }

    /**
     * Escapes a string for JSON.
     */
    protected String escapeJson(String text) {
        if (text == null)
            return "";
        return AiApiUtils.escapeJson(text);
    }

    /**
     * Normalizes a base URL (removes trailing slash, etc.).
     */
    protected String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    protected void appendReasoningEffort(StringBuilder json, String model, AiRequestOptions options) {
        if (json == null || options == null) {
            return;
        }
        String effort = options.reasoningEffort();
        if (effort == null || effort.isBlank()) {
            return;
        }
        if (!AiConfigDefaults.supportsReasoningEffort(model)) {
            return;
        }
        json.append(",\"reasoning_effort\":\"")
                .append(escapeJson(AiConfigDefaults.normalizeAssistantReasoningEffort(effort)))
                .append("\"");
    }

    protected void appendPlugins(StringBuilder json, AiRequestOptions options) {
        if (json == null || options == null || options.pluginOptions() == null || !supportsPluginPayload()) {
            return;
        }

        List<String> serializedPlugins = new ArrayList<>();
        appendWebPlugin(serializedPlugins, options.pluginOptions().web());
        appendFileParserPlugin(serializedPlugins, options.pluginOptions().fileParser());
        appendResponseHealingPlugin(serializedPlugins, options.pluginOptions().responseHealing());
        if (serializedPlugins.isEmpty()) {
            return;
        }

        json.append(",\"plugins\":[");
        for (int i = 0; i < serializedPlugins.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(serializedPlugins.get(i));
        }
        json.append("]");
    }

    private void appendWebPlugin(List<String> serializedPlugins, AiRequestOptions.WebPluginOptions web) {
        if (serializedPlugins == null || web == null || !web.enabled()) {
            return;
        }
        StringBuilder plugin = new StringBuilder();
        plugin.append("{\"id\":\"web\"");
        String normalizedEngine = AiConfigDefaults.normalizePluginWebEngine(web.engine());
        if (!"auto".equals(normalizedEngine)) {
            plugin.append(",\"engine\":\"").append(escapeJson(normalizedEngine)).append("\"");
        }
        Integer normalizedMaxResults = AiConfigDefaults.normalizePluginWebMaxResults(web.maxResults());
        if (normalizedMaxResults != null && normalizedMaxResults > 0) {
            plugin.append(",\"max_results\":").append(normalizedMaxResults);
        }
        String normalizedSearchPrompt = AiConfigDefaults.normalizePluginWebSearchPrompt(web.searchPrompt());
        if (!normalizedSearchPrompt.isBlank()) {
            plugin.append(",\"search_prompt\":\"").append(escapeJson(normalizedSearchPrompt)).append("\"");
        }
        plugin.append("}");
        serializedPlugins.add(plugin.toString());
    }

    private void appendFileParserPlugin(List<String> serializedPlugins, AiRequestOptions.FileParserPluginOptions fileParser) {
        if (serializedPlugins == null || fileParser == null || !fileParser.enabled()) {
            return;
        }
        String normalizedPdfEngine = AiConfigDefaults.normalizePluginFileParserPdfEngine(fileParser.pdfEngine());
        serializedPlugins.add("{\"id\":\"file-parser\",\"pdf\":{\"engine\":\""
                + escapeJson(normalizedPdfEngine)
                + "\"}}");
    }

    private void appendResponseHealingPlugin(
            List<String> serializedPlugins,
            AiRequestOptions.ResponseHealingPluginOptions responseHealing) {
        if (serializedPlugins == null || responseHealing == null || !responseHealing.enabled()) {
            return;
        }
        serializedPlugins.add("{\"id\":\"response-healing\"}");
    }

    protected boolean supportsPluginPayload() {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        return getMode() == AiMode.EXTERNAL_OPENAI && normalizedBaseUrl.contains("polza.ai");
    }

    protected boolean appendStructuredReasoning(StringBuilder json, String model, AiRequestOptions options) {
        if (json == null || options == null || options.reasoning() == null) {
            return false;
        }
        if (!supportsStructuredReasoningPayload(model)) {
            return false;
        }
        AiRequestOptions.ReasoningOptions normalized = normalizeReasoningOptions(options.reasoning());
        if (normalized == null) {
            return false;
        }

        json.append(",\"reasoning\":{");
        boolean appended = false;

        if (normalized.effort() != null && !normalized.effort().isBlank()) {
            json.append("\"effort\":\"").append(escapeJson(normalized.effort())).append("\"");
            appended = true;
        }
        if (normalized.maxTokens() != null && normalized.maxTokens() > 0) {
            if (appended) {
                json.append(",");
            }
            json.append("\"max_tokens\":").append(normalized.maxTokens());
            appended = true;
        }
        if (normalized.summary() != null && !normalized.summary().isBlank()) {
            if (appended) {
                json.append(",");
            }
            json.append("\"summary\":\"").append(escapeJson(normalized.summary())).append("\"");
            appended = true;
        }
        if (normalized.enabled() != null) {
            if (appended) {
                json.append(",");
            }
            json.append("\"enabled\":").append(normalized.enabled());
            appended = true;
        }
        if (normalized.exclude() != null) {
            if (appended) {
                json.append(",");
            }
            json.append("\"exclude\":").append(normalized.exclude());
        }
        json.append("}");
        return true;
    }

    protected void appendTextMessage(StringBuilder json, String role, String content, String model) {
        json.append("{\"role\":\"")
                .append(escapeJson(role))
                .append("\",\"content\":");
        if (shouldUsePromptCaching(role, content, model)) {
            json.append("[{\"type\":\"text\",\"text\":\"")
                    .append(escapeJson(content))
                    .append("\",\"cache_control\":{\"type\":\"ephemeral\"}}]");
        } else {
            json.append("\"").append(escapeJson(content)).append("\"");
        }
        json.append("}");
    }

    protected void appendUserMessage(StringBuilder json, String userText, List<AiMediaInput> mediaInputs, String model) {
        if (mediaInputs == null || mediaInputs.isEmpty()) {
            appendTextMessage(json, "user", userText, model);
            return;
        }

        json.append("{\"role\":\"user\",\"content\":[");
        String safeText = userText != null ? userText : "";
        json.append("{\"type\":\"text\",\"text\":\"").append(escapeJson(safeText)).append("\"}");
        for (AiMediaInput mediaInput : mediaInputs) {
            appendMediaContentPart(json, mediaInput);
        }
        json.append("]}");
    }

    protected void appendMediaContentPart(StringBuilder json, AiMediaInput mediaInput) {
        if (json == null || mediaInput == null) {
            return;
        }
        switch (mediaInput.kind()) {
            case IMAGE -> {
                String payload = resolveImagePayload(mediaInput);
                if (payload == null || payload.isBlank()) {
                    return;
                }
                json.append(",{\"type\":\"image_url\",\"image_url\":{\"url\":\"")
                        .append(escapeJson(payload))
                        .append("\"}}");
            }
            case DOCUMENT -> {
                String payload = resolveDocumentPayload(mediaInput);
                if (payload == null || payload.isBlank()) {
                    return;
                }
                String fileName = mediaInput.originalFilename() == null || mediaInput.originalFilename().isBlank()
                        ? "attachment"
                        : mediaInput.originalFilename();
                json.append(",{\"type\":\"file\",\"file\":{\"filename\":\"")
                        .append(escapeJson(fileName))
                        .append("\",\"file_data\":\"")
                        .append(escapeJson(payload))
                        .append("\"}}");
            }
            case AUDIO -> {
                String payload = resolveAudioPayload(mediaInput);
                if (payload == null || payload.isBlank()) {
                    return;
                }
                String audioFormat = mediaInput.audioFormat() == null ? "" : mediaInput.audioFormat();
                json.append(",{\"type\":\"input_audio\",\"input_audio\":{\"data\":\"")
                        .append(escapeJson(payload))
                        .append("\",\"format\":\"")
                        .append(escapeJson(audioFormat))
                        .append("\"}}");
            }
            case VIDEO -> {
                // Intentionally omitted: input video is not supported.
            }
        }
    }

    protected String resolveImagePayload(AiMediaInput mediaInput) {
        if (mediaInput == null) {
            return null;
        }
        return switch (mediaInput.source()) {
            case URL -> mediaInput.normalizedPayloadData();
            case BASE64_DATA_URL -> ensureDataUrl(mediaInput.normalizedPayloadData(), mediaInput.mimeType());
            case RAW_BYTES -> buildDataUrl(mediaInput.mimeType(), mediaInput.rawBytes());
        };
    }

    protected String resolveDocumentPayload(AiMediaInput mediaInput) {
        if (mediaInput == null) {
            return null;
        }
        return switch (mediaInput.source()) {
            case URL -> throw new IllegalArgumentException("Document inputs must use base64/data URL payloads");
            case BASE64_DATA_URL -> ensureDataUrl(mediaInput.normalizedPayloadData(), mediaInput.mimeType());
            case RAW_BYTES -> buildDataUrl(mediaInput.mimeType(), mediaInput.rawBytes());
        };
    }

    protected String resolveAudioPayload(AiMediaInput mediaInput) {
        if (mediaInput == null) {
            return null;
        }
        return switch (mediaInput.source()) {
            case URL -> throw new IllegalArgumentException("Audio inputs must use base64 payloads");
            case BASE64_DATA_URL -> ensureBareBase64(mediaInput.normalizedPayloadData());
            case RAW_BYTES -> encodeBase64(mediaInput.rawBytes());
        };
    }

    protected String ensureDataUrl(String payload, String mimeType) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        if (isDataUrl(payload)) {
            return payload;
        }
        return "data:" + mimeType + ";base64," + payload.trim();
    }

    protected String ensureBareBase64(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String trimmed = payload.trim();
        if (!isDataUrl(trimmed)) {
            return trimmed;
        }
        int commaIndex = trimmed.indexOf(',');
        if (commaIndex < 0 || commaIndex == trimmed.length() - 1) {
            return null;
        }
        return trimmed.substring(commaIndex + 1);
    }

    protected String buildDataUrl(String mimeType, byte[] rawBytes) {
        String base64 = encodeBase64(rawBytes);
        if (base64 == null) {
            return null;
        }
        return "data:" + mimeType + ";base64," + base64;
    }

    protected String encodeBase64(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            return null;
        }
        return Base64.getEncoder().encodeToString(rawBytes);
    }

    protected boolean isDataUrl(String payload) {
        return payload != null && payload.regionMatches(true, 0, "data:", 0, "data:".length());
    }

    protected boolean shouldUsePromptCaching(String role, String content, String model) {
        return role != null
                && "system".equalsIgnoreCase(role)
                && content != null
                && !content.isBlank()
                && ConfigManager.isAiPromptCachingEnabled()
                && AiConfigDefaults.supportsManualPromptCaching(model);
    }

    protected boolean supportsStructuredReasoningPayload(String model) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        return normalizedBaseUrl.contains("polza.ai")
                && AiConfigDefaults.supportsStructuredReasoning(model);
    }

    protected AiRequestOptions.ReasoningOptions normalizeReasoningOptions(AiRequestOptions.ReasoningOptions reasoning) {
        if (reasoning == null) {
            return null;
        }
        String effort = AiConfigDefaults.normalizeAssistantReasoningEffort(reasoning.effort());
        Integer maxTokens = reasoning.maxTokens();
        if (maxTokens != null && maxTokens <= 0) {
            maxTokens = null;
        }
        if (maxTokens != null) {
            maxTokens = Math.min(maxTokens, 200_000);
        }
        String summary = AiConfigDefaults.normalizeAssistantReasoningSummary(reasoning.summary());
        Boolean enabled = reasoning.enabled();
        if (AiConfigDefaults.ASSISTANT_REASONING_NONE.equals(effort)) {
            enabled = Boolean.FALSE;
        } else if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        return new AiRequestOptions.ReasoningOptions(
                effort,
                maxTokens,
                summary,
                enabled,
                reasoning.exclude());
    }

    /**
     * Creates an AiResponse from an HTTP response.
     */
    protected AiResponse handleHttpResponse(HttpResponse<String> response, long startTime) {
        return handleHttpResponseWithContext(response, null, startTime);
    }

    protected AiResponse handleHttpResponseWithContext(HttpResponse<String> response, AiCallContext context,
            long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        int status = response.statusCode();
        int attempts = context != null ? context.getAttempt() : 1;

        if (AiApiUtils.isSuccessfulStatus(status)) {
            try {
                String content = parseResponseContent(response.body());
                if (content != null) {
                    return AiResponse.success(content, defaultModel)
                            .withDuration(duration)
                            .withAttempts(attempts);
                }
                return new AiResponse(null, false, "Не удалось извлечь ответ из: " + response.body(),
                        status, defaultModel, null, null, null, java.time.Instant.now(), duration, status, attempts);
            } catch (AiParsingException parsingException) {
                return new AiResponse(
                        null,
                        false,
                        "Некорректный формат ответа AI: " + parsingException.getMessage(),
                        status,
                        defaultModel,
                        null,
                        null,
                        null,
                        java.time.Instant.now(),
                        duration,
                        status,
                        attempts);
            }
        } else {
            return new AiResponse(null, false, "Ошибка API: " + status,
                    status, defaultModel, null, null, null, java.time.Instant.now(), duration, status, attempts);
        }
    }

    /**
     * Creates a ConnectionTestResult from an exception.
     */
    protected ConnectionTestResult handleTestException(Throwable e, String url) {
        return ConnectionTestResult.fromException(e, getMode(), url);
    }

    /**
     * Returns true for terminal SSE payload.
     */
    protected boolean isSseDonePayload(String payload) {
        if (payload == null) {
            return false;
        }
        return "[DONE]".equalsIgnoreCase(payload.trim());
    }

    /**
     * Extracts incremental {@code delta.content} text from streaming chunk payload.
     */
    protected String extractStreamDeltaContent(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = AiObjectMapperFactory.providerResponseMapper().readTree(payload.trim());
        } catch (Exception parseError) {
            return null;
        }

        String content = null;
        JsonNode choices = root.path("choices");
        if (choices.isArray()) {
            for (JsonNode choice : choices) {
                content = firstTextValue(choice.path("delta").get("content"));
                if (content == null || content.isBlank()) {
                    content = firstTextValue(choice.path("message").get("content"));
                }
                if (content != null && !content.isBlank()) {
                    break;
                }
            }
        }
        if (content == null || content.isBlank()) {
            content = firstTextValue(root.path("delta").get("content"));
        }
        if (content == null || content.isBlank()) {
            content = firstTextValue(root.get("content"));
        }
        if (content == null || content.isBlank()) {
            return null;
        }

        String sanitized = AiApiUtils.sanitizeAssistantText(content);
        return sanitized == null || sanitized.isBlank() ? null : sanitized;
    }

    private String firstTextValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (child == null || child.isNull()) {
                    continue;
                }
                if (child.isTextual()) {
                    String text = child.asText();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                    continue;
                }
                String text = firstTextValue(child.get("text"));
                if (text != null && !text.isBlank()) {
                    return text;
                }
                text = firstTextValue(child.get("content"));
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * Detects terminal finish reason in streaming chunk payload.
     */
    protected boolean hasStreamFinishReason(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        String normalized = payload.replace(" ", "").replace("\n", "");
        if (!normalized.contains("\"finish_reason\"")) {
            return false;
        }
        return !normalized.contains("\"finish_reason\":null");
    }

    /**
     * Sends a test message to verify the connection and model.
     */
    protected CompletableFuture<ConnectionTestResult> sendTestMessage(String url, String apiKey, String model) {
        String testPrompt = "Ответь одним словом: OK";
        AiRequestOptions options = new AiRequestOptions(model, null, null, null, null, null, null, false);

        long startTime = System.currentTimeMillis();
        AiCallContext context = new AiCallContext(getMode().name(), model, url, "test_model");

        return resilienceExecutor.executeWithResilience(
                context,
                httpClient,
                ctx -> {
                    AiRequestOptions newOptions = options.withModel(ctx.getModel());
                    String newBody = buildChatRequestJson(testPrompt, newOptions);
                    HttpRequest.Builder b = buildRequest(url)
                            .POST(HttpRequest.BodyPublishers.ofString(newBody))
                            .timeout(TEST_TIMEOUT);
                    addAuthHeader(b, apiKey);
                    return b;
                },
                HttpResponse.BodyHandlers.ofString(),
                (response, ctx) -> {
                    long duration = System.currentTimeMillis() - startTime;

                    if (AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                        String content;
                        try {
                            content = parseResponseContent(response.body());
                        } catch (AiParsingException parsingException) {
                            return ConnectionTestResult.failure(
                                    "Некорректный формат ответа AI",
                                    parsingException.getMessage(),
                                    response.statusCode(),
                                    getMode(),
                                    url);
                        }
                        return ConnectionTestResult.success(
                                "Подключение успешно",
                                getMode(),
                                url,
                                model,
                                content,
                                duration);
                    } else {
                        return ConnectionTestResult.failure(
                                "Ошибка API: " + response.statusCode(),
                                response.body(),
                                response.statusCode(),
                                getMode(),
                                url);
                    }
                })
                .exceptionally(e -> handleTestException(e, url));
    }
}
