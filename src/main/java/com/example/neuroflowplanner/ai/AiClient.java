package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.media.AiMediaInput;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for AI client implementations.
 * 
 * Each AI mode (Offline, Local Ollama, External OpenAI) has its own
 * implementation
 * of this interface. The interface provides a unified way to interact with
 * different
 * AI backends.
 * 
 * <p>
 * Implementations:
 * </p>
 * <ul>
 * <li>{@code OfflineAiClient} - Returns stub/mock responses when AI is
 * disabled</li>
 * <li>{@code LocalOllamaClient} - Connects to a local Ollama server</li>
 * <li>{@code ExternalOpenAiClient} - Connects to an OpenAI-compatible API</li>
 * </ul>
 */
public interface AiClient {

    /**
     * Sends a chat message to the AI and returns the response asynchronously.
     * 
     * @param userText the user's message text
     * @param options  request options (model, temperature, history, etc.)
     * @return a CompletableFuture that resolves to the AI response
     */
    CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options);

    /**
     * Sends a chat message with one or more image inputs.
     *
     * <p>
     * Default behavior falls back to text-only requests.
     * </p>
     */
    default CompletableFuture<AiResponse> sendChatMessageWithImages(
            String userText,
            List<AiImageInput> images,
            AiRequestOptions options) {
        List<AiMediaInput> mediaInputs = images == null
                ? List.of()
                : images.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(AiImageInput::toMediaInput)
                        .toList();
        return sendChatMessageWithMedia(userText, mediaInputs, options);
    }

    /**
     * Sends a chat message with unified media inputs.
     */
    default CompletableFuture<AiResponse> sendChatMessageWithMedia(
            String userText,
            List<AiMediaInput> mediaInputs,
            AiRequestOptions options) {
        return sendChatMessage(userText, options.withMediaInputs(mediaInputs));
    }

    /**
     * Sends a chat message with default options.
     * Uses the currently configured model and default parameters.
     * 
     * @param userText the user's message text
     * @return a CompletableFuture that resolves to the AI response
     */
    default CompletableFuture<AiResponse> sendChatMessage(String userText) {
        return sendChatMessage(userText, new AiRequestOptions(getDefaultModel(), null, null, null, null, null, null, null, false));
    }

    /**
     * Returns whether this client supports true incremental token streaming.
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * Sends a chat message with incremental chunk callback.
     *
     * <p>
     * Default implementation falls back to non-streaming completion and reports a
     * single terminal chunk.
     * </p>
     */
    default CompletableFuture<AiResponse> sendChatMessageStreaming(
            String userText,
            AiRequestOptions options,
            Consumer<AiStreamChunk> onChunk) {
        return sendChatMessage(userText, options.withStream(false))
                .thenApply(response -> {
                    if (response != null && response.success() && onChunk != null && response.content() != null
                            && !response.content().isBlank()) {
                        onChunk.accept(AiStreamChunk.delta(response.content(), response.model()));
                        onChunk.accept(AiStreamChunk.done(response.model()));
                    }
                    return response;
                });
    }

    /**
     * Tests the connection to the AI service.
     * 
     * <p>
     * For different modes:
     * </p>
     * <ul>
     * <li>OFFLINE: Always returns success</li>
     * <li>LOCAL_OLLAMA: Pings the Ollama API endpoint</li>
     * <li>EXTERNAL_OPENAI: Calls GET /models, then a short test completion</li>
     * </ul>
     * 
     * @return a CompletableFuture that resolves to the test result
     */
    CompletableFuture<ConnectionTestResult> testConnection();

    /**
     * Tests the connection with a specific URL and optional API key.
     * Useful for validating user input before saving.
     * 
     * @param baseUrl the URL to test
     * @param apiKey  the API key (null for modes that don't require it)
     * @return a CompletableFuture that resolves to the test result
     */
    CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey);

    /**
     * Tests a specific model by sending a short test message.
     * 
     * @param model the model name to test
     * @return a CompletableFuture that resolves to the test result with sample
     *         response
     */
    CompletableFuture<ConnectionTestResult> testModel(String model);

    /**
     * Retrieves the list of available models from the AI service.
     * 
     * @return a CompletableFuture that resolves to a list of model names
     */
    CompletableFuture<List<String>> fetchAvailableModels();

    /**
     * Returns whether this client supports image generation.
     * Only EXTERNAL_OPENAI mode supports image generation.
     * 
     * @return true if image generation is supported
     */
    boolean supportsImages();

    /**
     * Returns whether this client supports sending image inputs to chat
     * completions.
     */
    default boolean supportsImageInputs() {
        return false;
    }

    /**
     * Returns the AI mode this client is configured for.
     * 
     * @return the AI mode
     */
    AiMode getMode();

    /**
     * Returns the default model for this client.
     * 
     * @return the default model name, or null if not configured
     */
    String getDefaultModel();

    /**
     * Returns true if the client is properly configured and ready to use.
     * 
     * @return true if the client is configured
     */
    boolean isConfigured();

    /**
     * Returns the base URL this client is configured to use.
     * 
     * @return the base URL, or null for offline mode
     */
    String getBaseUrl();

    /**
     * Reloads the client configuration from the current settings.
     * Called when settings are changed without restarting the application.
     */
    void reloadConfiguration();

    /**
     * Closes any resources held by this client.
     * Called when switching to a different mode or shutting down.
     */
    default void close() {
        // Default implementation does nothing
    }
}
