package com.example.neuroflowplanner.model;

public record TaskBulkOperationResult(
    String operation,
    int processedCount,
    int updatedCount,
    int failedCount,
    int batchCount,
    long durationMs
) {
    public TaskBulkOperationResult {
        if (operation == null || operation.isBlank()) {
            operation = "unknown";
        }
        if (processedCount < 0) {
            throw new IllegalArgumentException("processedCount must be >= 0");
        }
        if (updatedCount < 0) {
            throw new IllegalArgumentException("updatedCount must be >= 0");
        }
        if (failedCount < 0) {
            throw new IllegalArgumentException("failedCount must be >= 0");
        }
        if (batchCount < 0) {
            throw new IllegalArgumentException("batchCount must be >= 0");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0");
        }
    }

    public boolean isSuccessful() {
        return failedCount == 0;
    }
}
