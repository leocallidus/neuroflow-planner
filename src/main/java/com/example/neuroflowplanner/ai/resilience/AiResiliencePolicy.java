package com.example.neuroflowplanner.ai.resilience;

import java.time.Duration;
import java.util.List;

public class AiResiliencePolicy {
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration requestBudget;
    private final AiRetryPolicy retryPolicy;
    private final AiConcurrencyLimiter concurrencyLimiter;
    private final List<String> fallbackModels;
    private final boolean fallbackModeEnabled;

    public AiResiliencePolicy(Duration connectTimeout,
            Duration readTimeout,
            AiRetryPolicy retryPolicy,
            AiConcurrencyLimiter concurrencyLimiter,
            List<String> fallbackModels,
            boolean fallbackModeEnabled) {
        this(connectTimeout, readTimeout, Duration.ofMillis(Long.MAX_VALUE), retryPolicy, concurrencyLimiter, fallbackModels,
                fallbackModeEnabled);
    }

    public AiResiliencePolicy(Duration connectTimeout,
            Duration readTimeout,
            Duration requestBudget,
            AiRetryPolicy retryPolicy,
            AiConcurrencyLimiter concurrencyLimiter,
            List<String> fallbackModels,
            boolean fallbackModeEnabled) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.requestBudget = requestBudget;
        this.retryPolicy = retryPolicy;
        this.concurrencyLimiter = concurrencyLimiter;
        this.fallbackModels = fallbackModels;
        this.fallbackModeEnabled = fallbackModeEnabled;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public Duration getRequestBudget() {
        return requestBudget;
    }

    public AiRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public AiConcurrencyLimiter getConcurrencyLimiter() {
        return concurrencyLimiter;
    }

    public List<String> getFallbackModels() {
        return fallbackModels;
    }

    public boolean isFallbackModeEnabled() {
        return fallbackModeEnabled;
    }
}
