package com.example.neuroflowplanner.service.chatflow;

/**
 * Progress metadata for a chat request lifecycle event.
 */
public record ChatRequestProgress(
    long elapsedMs,
    int attempt,
    int maxAttempts,
    boolean terminal
) {
    public ChatRequestProgress {
        elapsedMs = Math.max(0L, elapsedMs);
        attempt = Math.max(1, attempt);
        maxAttempts = Math.max(1, maxAttempts);
    }
}
