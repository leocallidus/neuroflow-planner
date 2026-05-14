package com.example.neuroflowplanner.ai.resilience;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiImageInput;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.ConnectionTestResult;
import com.example.neuroflowplanner.ai.media.AiMediaInput;
import com.example.neuroflowplanner.util.StructuredLogger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A wrapper around AiClient that provides mode-level (provider) fallback.
 * If the primary client fails (after its own internal retries and model fallbacks),
 * this wrapper delegates the request to the next available client in the fallback chain.
 */
public class ModeFallbackAiClientWrapper implements AiClient {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ModeFallbackAiClientWrapper.class);

    private final AiClient primaryClient;
    private final List<Supplier<AiClient>> fallbackClientSuppliers;

    public ModeFallbackAiClientWrapper(AiClient primaryClient, List<Supplier<AiClient>> fallbackClientSuppliers) {
        this.primaryClient = primaryClient;
        this.fallbackClientSuppliers = fallbackClientSuppliers != null ? fallbackClientSuppliers : List.of();
    }

    @Override
    public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
        return executeWithModeFallback(client -> client.sendChatMessage(userText, options), "chat");
    }

    @Override
    public CompletableFuture<AiResponse> sendChatMessageWithImages(String userText, List<AiImageInput> images, AiRequestOptions options) {
        return executeWithModeFallback(client -> {
            if (client.supportsImageInputs()) {
                return client.sendChatMessageWithImages(userText, images, options);
            } else {
                return client.sendChatMessage(userText, options);
            }
        }, "chat_with_images");
    }

    @Override
    public CompletableFuture<AiResponse> sendChatMessageWithMedia(String userText, List<AiMediaInput> mediaInputs, AiRequestOptions options) {
        return executeWithModeFallback(client -> client.sendChatMessageWithMedia(userText, mediaInputs, options), "chat_with_media");
    }

    private CompletableFuture<AiResponse> executeWithModeFallback(java.util.function.Function<AiClient, CompletableFuture<AiResponse>> action, String operation) {
        return action.apply(primaryClient)
                .exceptionallyCompose(primaryErr -> {
                    LOG.warning("ai.request.primary_mode_failed", primaryErr,
                            "primaryMode", primaryClient.getMode().name(),
                            "operation", operation);
                    return tryFallbacks(action, operation, 0, primaryErr);
                });
    }

    private CompletableFuture<AiResponse> tryFallbacks(java.util.function.Function<AiClient, CompletableFuture<AiResponse>> action, String operation, int index, Throwable lastError) {
        if (index >= fallbackClientSuppliers.size()) {
            LOG.error("ai.request.all_modes_failed", lastError, "operation", operation);
            return CompletableFuture.failedFuture(lastError);
        }

        AiClient fallbackClient = fallbackClientSuppliers.get(index).get();
        if (fallbackClient == null || !fallbackClient.isConfigured()) {
            LOG.info("ai.request.skipping_unconfigured_fallback",
                    "operation", operation,
                    "fallbackIndex", index);
            return tryFallbacks(action, operation, index + 1, lastError);
        }

        LOG.info("ai.request.using_mode_fallback",
                "operation", operation,
                "fallbackMode", fallbackClient.getMode().name());

        return action.apply(fallbackClient)
                .exceptionallyCompose(fallbackErr -> {
                    LOG.warning("ai.request.fallback_mode_failed", fallbackErr,
                            "fallbackMode", fallbackClient.getMode().name(),
                            "operation", operation);
                    return tryFallbacks(action, operation, index + 1, fallbackErr);
                });
    }

    // Pass-through methods that just delegate to the primary client
    @Override
    public CompletableFuture<ConnectionTestResult> testConnection() {
        return primaryClient.testConnection();
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey) {
        return primaryClient.testConnection(baseUrl, apiKey);
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testModel(String model) {
        return primaryClient.testModel(model);
    }

    @Override
    public CompletableFuture<List<String>> fetchAvailableModels() {
        return primaryClient.fetchAvailableModels();
    }

    @Override
    public boolean supportsImages() {
        return primaryClient.supportsImages();
    }

    @Override
    public boolean supportsImageInputs() {
        return primaryClient.supportsImageInputs();
    }

    @Override
    public AiMode getMode() {
        return primaryClient.getMode();
    }

    @Override
    public String getDefaultModel() {
        return primaryClient.getDefaultModel();
    }

    @Override
    public boolean isConfigured() {
        return primaryClient.isConfigured();
    }

    @Override
    public String getBaseUrl() {
        return primaryClient.getBaseUrl();
    }

    @Override
    public void reloadConfiguration() {
        primaryClient.reloadConfiguration();
        // Conceptually, fallbacks should be reloaded by their respective factories when instantiated
    }

    @Override
    public void close() {
        primaryClient.close();
    }
}
