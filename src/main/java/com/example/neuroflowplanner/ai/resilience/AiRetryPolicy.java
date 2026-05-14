package com.example.neuroflowplanner.ai.resilience;

public class AiRetryPolicy {
    private final int maxAttempts;
    private final AiRetryDelayStrategy delayStrategy;

    public AiRetryPolicy(int maxAttempts, AiRetryDelayStrategy delayStrategy) {
        this.maxAttempts = maxAttempts;
        this.delayStrategy = delayStrategy;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public AiRetryDelayStrategy getDelayStrategy() {
        return delayStrategy;
    }
}
