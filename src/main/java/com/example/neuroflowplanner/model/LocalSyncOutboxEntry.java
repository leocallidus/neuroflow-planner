package com.example.neuroflowplanner.model;

public record LocalSyncOutboxEntry(
    String id,
    String entityType,
    String entityId,
    String operation,
    String payloadJson,
    String status,
    int attemptCount,
    String errorMessage,
    String lastAttemptAt,
    String createdAt,
    String updatedAt
) {
}
