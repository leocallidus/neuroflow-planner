package com.example.neuroflowplanner.sync;

public class SyncHttpException extends RuntimeException {
    private final int statusCode;
    private final String errorCode;
    private final String category;
    private final boolean retryable;
    private final long retryAfterSeconds;
    private final String requestId;

    public SyncHttpException(
            int statusCode,
            String errorCode,
            String category,
            boolean retryable,
            long retryAfterSeconds,
            String message,
            String requestId) {
        super(message == null ? "" : message);
        this.statusCode = statusCode;
        this.errorCode = errorCode == null ? "" : errorCode;
        this.category = category == null ? "" : category;
        this.retryable = retryable;
        this.retryAfterSeconds = Math.max(0L, retryAfterSeconds);
        this.requestId = requestId == null ? "" : requestId;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public String category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String requestId() {
        return requestId;
    }
}
