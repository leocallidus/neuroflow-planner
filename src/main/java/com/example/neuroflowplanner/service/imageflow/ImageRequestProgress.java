package com.example.neuroflowplanner.service.imageflow;

/**
 * Progress metadata for an image generation lifecycle event.
 */
public record ImageRequestProgress(
    long elapsedMs,
    int attempt,
    int maxAttempts,
    boolean terminal
) {
    public ImageRequestProgress {
        elapsedMs = Math.max(0L, elapsedMs);
        attempt = Math.max(1, attempt);
        maxAttempts = Math.max(1, maxAttempts);
    }
}
