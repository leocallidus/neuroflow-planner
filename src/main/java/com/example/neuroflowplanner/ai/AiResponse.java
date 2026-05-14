package com.example.neuroflowplanner.ai;

import java.time.Instant;
import java.util.Optional;

/**
 * Response from an AI request.
 * Contains the generated content and metadata about the response.
 */
public record AiResponse(
        /**
         * The generated text content.
         * May be null if the request failed.
         */
        String content,

        /**
         * Whether the request was successful.
         */
        boolean success,

        /**
         * Error message if the request failed.
         * null if successful.
         */
        String errorMessage,

        /**
         * Error code (e.g., HTTP status code) if the request failed.
         * null if successful.
         */
        Integer errorCode,

        /**
         * The model that was used for generation.
         */
        String model,

        /**
         * Number of tokens used in the prompt.
         * May be null if not available.
         */
        Integer promptTokens,

        /**
         * Number of tokens generated in the response.
         * May be null if not available.
         */
        Integer completionTokens,

        /**
         * Total tokens used (prompt + completion).
         * May be null if not available.
         */
        Integer totalTokens,

        /**
         * Time when the response was received.
         */
        Instant timestamp,

        /**
         * Duration of the request in milliseconds.
         * May be null if not measured.
         */
        Long durationMs,

        /**
         * HTTP status code.
         * May be null if not an HTTP error or not available.
         */
        Integer httpStatus,

        /**
         * Number of attempts performed (for resilience execution).
         */
        Integer attempts) {
    /**
     * Creates a successful response with content.
     */
    public static AiResponse success(String content) {
        return new AiResponse(
                content,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null,
                200,
                1);
    }

    /**
     * Creates a successful response with content and model info.
     */
    public static AiResponse success(String content, String model) {
        return new AiResponse(
                content,
                true,
                null,
                null,
                model,
                null,
                null,
                null,
                Instant.now(),
                null,
                200,
                1);
    }

    /**
     * Creates a successful response with full token information.
     */
    public static AiResponse success(String content, String model,
            Integer promptTokens, Integer completionTokens) {
        Integer total = (promptTokens != null && completionTokens != null)
                ? promptTokens + completionTokens
                : null;
        return new AiResponse(
                content,
                true,
                null,
                null,
                model,
                promptTokens,
                completionTokens,
                total,
                Instant.now(),
                null,
                200,
                1);
    }

    /**
     * Creates a failed response with an error message.
     */
    public static AiResponse error(String errorMessage) {
        return new AiResponse(
                null,
                false,
                errorMessage,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null,
                null,
                1);
    }

    /**
     * Creates a failed response with an error message and code.
     */
    public static AiResponse error(String errorMessage, int errorCode) {
        return new AiResponse(
                null,
                false,
                errorMessage,
                errorCode,
                null,
                null,
                null,
                null,
                Instant.now(),
                null,
                null,
                1);
    }

    /**
     * Creates a failed response from an exception.
     */
    public static AiResponse fromException(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return error(message);
    }

    /**
     * Creates an offline/stub response.
     */
    public static AiResponse offline(String stubContent) {
        return new AiResponse(
                stubContent,
                true,
                null,
                null,
                "offline",
                null,
                null,
                null,
                Instant.now(),
                0L,
                200,
                1);
    }

    /**
     * Returns the content as an Optional.
     */
    public Optional<String> getContentOptional() {
        return Optional.ofNullable(content);
    }

    /**
     * Returns the content or a default value if null/empty.
     */
    public String getContentOrDefault(String defaultValue) {
        return (content != null && !content.isBlank()) ? content : defaultValue;
    }

    /**
     * Returns a copy of this response with duration set.
     */
    public AiResponse withDuration(long durationMs) {
        return new AiResponse(
                content,
                success,
                errorMessage,
                errorCode,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                timestamp,
                durationMs,
                httpStatus,
                attempts);
    }

    /**
     * Returns a copy of this response with updated attempts.
     */
    public AiResponse withAttempts(int attempts) {
        return new AiResponse(
                content,
                success,
                errorMessage,
                errorCode,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                timestamp,
                durationMs,
                httpStatus,
                attempts);
    }
}
