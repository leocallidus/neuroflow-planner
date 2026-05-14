package com.example.neuroflowplanner.service.chatflow;

import java.time.Instant;

/**
 * Incremental response chunk emitted during assistant answer generation.
 */
public record ChatResponseChunk(
        String requestId,
        String conversationId,
        String model,
        String deltaText,
        String accumulatedText,
        boolean terminal,
        boolean synthetic,
        long elapsedMs,
        Instant timestamp) {

    public ChatResponseChunk {
        requestId = requestId == null ? "" : requestId.trim();
        conversationId = conversationId == null ? "" : conversationId.trim();
        model = model == null ? "" : model.trim();
        deltaText = deltaText == null ? "" : deltaText;
        accumulatedText = accumulatedText == null ? "" : accumulatedText;
        elapsedMs = Math.max(0L, elapsedMs);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static ChatResponseChunk delta(
            String requestId,
            String conversationId,
            String model,
            String deltaText,
            String accumulatedText,
            boolean synthetic,
            long elapsedMs) {
        return new ChatResponseChunk(
                requestId,
                conversationId,
                model,
                deltaText,
                accumulatedText,
                false,
                synthetic,
                elapsedMs,
                Instant.now());
    }

    public static ChatResponseChunk terminal(
            String requestId,
            String conversationId,
            String model,
            String accumulatedText,
            boolean synthetic,
            long elapsedMs) {
        return new ChatResponseChunk(
                requestId,
                conversationId,
                model,
                "",
                accumulatedText,
                true,
                synthetic,
                elapsedMs,
                Instant.now());
    }
}
