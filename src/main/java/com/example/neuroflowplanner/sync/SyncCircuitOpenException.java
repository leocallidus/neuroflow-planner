package com.example.neuroflowplanner.sync;

public class SyncCircuitOpenException extends RuntimeException {
    private final long retryAfterMillis;

    public SyncCircuitOpenException(String message, long retryAfterMillis) {
        super(message == null ? "" : message);
        this.retryAfterMillis = Math.max(0L, retryAfterMillis);
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
