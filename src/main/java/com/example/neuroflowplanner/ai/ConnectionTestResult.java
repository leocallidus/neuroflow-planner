package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Result of a connection test to an AI service.
 * Used to validate configuration before saving.
 */
public record ConnectionTestResult(
        /**
         * Whether the connection test was successful.
         */
        boolean success,

        /**
         * Human-readable status message.
         */
        String message,

        /**
         * Error details if the test failed.
         * null if successful.
         */
        String errorDetails,

        /**
         * HTTP status code from the test request.
         * null if the request didn't complete.
         */
        Integer httpStatusCode,

        /**
         * Response time in milliseconds.
         * null if the request didn't complete.
         */
        Long responseTimeMs,

        /**
         * The AI mode that was tested.
         */
        AiMode mode,

        /**
         * The base URL that was tested.
         */
        String testedUrl,

        /**
         * The model that was tested (if applicable).
         */
        String testedModel,

        /**
         * Sample response from the test request (if successful).
         * Useful for showing the user that the connection works.
         */
        String sampleResponse,

        /**
         * List of available models discovered during the test.
         * May be empty if models couldn't be retrieved.
         */
        List<String> availableModels,

        /**
         * Discovered chat models that support multimodal input/output.
         */
        List<String> multimodalModels,

        /**
         * Discovered chat models that support audio input.
         */
        List<String> audioInputModels,

        /**
         * Discovered chat models that support file input.
         */
        List<String> fileInputModels,

        /**
         * Full discovered model catalog with extended metadata.
         */
        List<AiDiscoveredModelInfo> modelCatalog,

        /**
         * Time when the test was performed.
         */
        Instant timestamp
) {
    /**
     * Defensive copy for availableModels.
     */
    public ConnectionTestResult {
        if (availableModels != null) {
            availableModels = List.copyOf(availableModels);
        }
        if (multimodalModels != null) {
            multimodalModels = List.copyOf(multimodalModels);
        }
        if (audioInputModels != null) {
            audioInputModels = List.copyOf(audioInputModels);
        }
        if (fileInputModels != null) {
            fileInputModels = List.copyOf(fileInputModels);
        }
        if (modelCatalog != null) {
            modelCatalog = List.copyOf(modelCatalog);
        }
    }

    /**
     * Creates a successful test result.
     */
    public static ConnectionTestResult success(String message, AiMode mode, String url) {
        return new ConnectionTestResult(
                true,
                message,
                null,
                200,
                null,
                mode,
                url,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    /**
     * Creates a successful test result with sample response.
     */
    public static ConnectionTestResult success(String message, AiMode mode, String url, 
                                                String model, String sampleResponse, long responseTimeMs) {
        return new ConnectionTestResult(
                true,
                message,
                null,
                200,
                responseTimeMs,
                mode,
                url,
                model,
                sampleResponse,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    /**
     * Creates a successful test result with available models.
     */
    public static ConnectionTestResult successWithModels(String message, AiMode mode, String url,
                                                          List<String> models, long responseTimeMs) {
        return successWithModels(message, mode, url, models, List.of(), List.of(), List.of(), List.of(), responseTimeMs);
    }

    public static ConnectionTestResult successWithModels(String message, AiMode mode, String url,
                                                          List<String> models, List<String> multimodalModels,
                                                          long responseTimeMs) {
        return successWithModels(message, mode, url, models, multimodalModels, List.of(), List.of(), List.of(), responseTimeMs);
    }

    public static ConnectionTestResult successWithModels(String message, AiMode mode, String url,
                                                          List<String> models, List<String> multimodalModels,
                                                          List<String> audioInputModels, List<String> fileInputModels,
                                                          long responseTimeMs) {
        return successWithModels(
                message,
                mode,
                url,
                models,
                multimodalModels,
                audioInputModels,
                fileInputModels,
                List.of(),
                responseTimeMs);
    }

    public static ConnectionTestResult successWithModels(String message, AiMode mode, String url,
                                                          List<String> models, List<String> multimodalModels,
                                                          List<String> audioInputModels, List<String> fileInputModels,
                                                          List<AiDiscoveredModelInfo> modelCatalog,
                                                          long responseTimeMs) {
        return new ConnectionTestResult(
                true,
                message,
                null,
                200,
                responseTimeMs,
                mode,
                url,
                null,
                null,
                models,
                multimodalModels,
                audioInputModels,
                fileInputModels,
                modelCatalog,
                Instant.now()
        );
    }

    /**
     * Creates a failed test result.
     */
    public static ConnectionTestResult failure(String message, String errorDetails, AiMode mode, String url) {
        return new ConnectionTestResult(
                false,
                message,
                errorDetails,
                null,
                null,
                mode,
                url,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    /**
     * Creates a failed test result with HTTP status code.
     */
    public static ConnectionTestResult failure(String message, String errorDetails, 
                                                int httpStatusCode, AiMode mode, String url) {
        return new ConnectionTestResult(
                false,
                message,
                errorDetails,
                httpStatusCode,
                null,
                mode,
                url,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    /**
     * Creates a test result from an exception.
     */
    public static ConnectionTestResult fromException(Throwable e, AiMode mode, String url) {
        String errorMessage = e.getMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = e.getClass().getSimpleName();
        }
        
        String details = e.getClass().getName() + ": " + errorMessage;
        
        return new ConnectionTestResult(
                false,
                "Ошибка подключения: " + errorMessage,
                details,
                null,
                null,
                mode,
                url,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    /**
     * Creates a test result for offline mode (always successful).
     */
    public static ConnectionTestResult offlineSuccess() {
        return new ConnectionTestResult(
                true,
                "Офлайн режим не требует подключения",
                null,
                null,
                0L,
                AiMode.OFFLINE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    /**
     * Returns true if models were discovered during the test.
     */
    public boolean hasModels() {
        return availableModels != null && !availableModels.isEmpty();
    }

    public boolean hasMultimodalModels() {
        return multimodalModels != null && !multimodalModels.isEmpty();
    }

    public boolean hasAudioInputModels() {
        return audioInputModels != null && !audioInputModels.isEmpty();
    }

    public boolean hasFileInputModels() {
        return fileInputModels != null && !fileInputModels.isEmpty();
    }

    public boolean hasModelCatalog() {
        return modelCatalog != null && !modelCatalog.isEmpty();
    }

    /**
     * Returns the available models as an Optional.
     */
    public Optional<List<String>> getModelsOptional() {
        return Optional.ofNullable(availableModels);
    }

    public Optional<List<String>> getMultimodalModelsOptional() {
        return Optional.ofNullable(multimodalModels);
    }

    public Optional<List<String>> getAudioInputModelsOptional() {
        return Optional.ofNullable(audioInputModels);
    }

    public Optional<List<String>> getFileInputModelsOptional() {
        return Optional.ofNullable(fileInputModels);
    }

    public Optional<List<AiDiscoveredModelInfo>> getModelCatalogOptional() {
        return Optional.ofNullable(modelCatalog);
    }

    /**
     * Returns the sample response as an Optional.
     */
    public Optional<String> getSampleResponseOptional() {
        return Optional.ofNullable(sampleResponse);
    }

    /**
     * Returns a formatted string representation for display.
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(success ? "[OK] " : "[ОШИБКА] ");
        sb.append(message);
        
        if (responseTimeMs != null) {
            sb.append(" (").append(responseTimeMs).append(" мс)");
        }
        
        if (!success && errorDetails != null) {
            sb.append("\nПодробности: ").append(errorDetails);
        }
        
        return sb.toString();
    }
}
