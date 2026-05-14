package com.example.neuroflowplanner.service.imageflow;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Event emitted by the image generation lifecycle publisher.
 */
public record ImageRequestEvent(
    String jobId,
    String requestId,
    String conversationId,
    ImageRequestState state,
    String model,
    String message,
    ImageRequestProgress progress,
    Instant timestamp,
    Map<String, String> metadata
) {
    public ImageRequestEvent {
        jobId = sanitizeId(jobId);
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
