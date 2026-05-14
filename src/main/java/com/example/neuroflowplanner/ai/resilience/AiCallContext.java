package com.example.neuroflowplanner.ai.resilience;

import java.util.UUID;

public class AiCallContext {
    private final String requestId;
    private final String mode;
    private String model;
    private final String endpoint;
    private final String operation;
    private int attempt;
    private int fallbackIndex;
    private boolean fallbackUsed;

    public AiCallContext(String mode, String model, String endpoint, String operation) {
        this.requestId = UUID.randomUUID().toString();
        this.mode = mode;
        this.model = model;
        this.endpoint = endpoint;
        this.operation = operation;
        this.attempt = 1;
        this.fallbackIndex = 0;
        this.fallbackUsed = false;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getMode() {
        return mode;
    }

    public String getModel() {
        return model;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getOperation() {
        return operation;
    }

    public int getAttempt() {
        return attempt;
    }

    public void incrementAttempt() {
        this.attempt++;
    }

    public int getFallbackIndex() {
        return fallbackIndex;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    /**
     * Updates the context to use a fallback model.
     * Resets the attempt counter since it's a new model execution.
     */
    public void fallbackTo(String nextModel, int newFallbackIndex) {
        this.model = nextModel;
        this.fallbackIndex = newFallbackIndex;
        this.fallbackUsed = true;
        this.attempt = 1; // Reset attempt for the new model
    }
}
