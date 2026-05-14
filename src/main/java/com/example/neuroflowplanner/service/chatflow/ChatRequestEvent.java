package com.example.neuroflowplanner.service.chatflow;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Event emitted by chat lifecycle publisher.
 */
public record ChatRequestEvent(
    String requestId,
    String conversationId,
    ChatRequestState state,
    String model,
    String message,
    ChatRequestProgress progress,
    Instant timestamp,
    Map<String, String> metadata
) {
    public ChatRequestEvent {
        requestId = sanitizeId(requestId);
        conversationId = sanitizeId(conversationId);
        state = Objects.requireNonNull(state, "state");
        model = model == null ? "" : model.trim();
        message = message == null ? "" : message.trim();
        progress = Objects.requireNonNull(progress, "progress");
        timestamp = timestamp == null ? Instant.now() : timestamp;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String sanitizeId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
