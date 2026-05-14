package com.example.neuroflowplanner.ai.resilience;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class AiRetryDelayStrategy {
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final double jitterRatio;

    public AiRetryDelayStrategy(Duration baseDelay, Duration maxDelay, double jitterRatio) {
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.jitterRatio = jitterRatio;
    }

    public Duration calculateDelay(int attempt) {
        // Exponential backoff: baseDelay * 2^(attempt - 1)
        long delayMs = (long) (baseDelay.toMillis() * Math.pow(2, attempt - 1));

        // Cap at maxDelay
        if (delayMs > maxDelay.toMillis()) {
            delayMs = maxDelay.toMillis();
        }

        // Apply jitter
        if (jitterRatio > 0) {
            long jitterMax = (long) (delayMs * jitterRatio);
            long jitter = ThreadLocalRandom.current().nextLong(-jitterMax, jitterMax + 1);
            delayMs += jitter;
        }

        // Must not be negative
        if (delayMs < 0) {
            delayMs = 0;
        }

        return Duration.ofMillis(delayMs);
    }
}
