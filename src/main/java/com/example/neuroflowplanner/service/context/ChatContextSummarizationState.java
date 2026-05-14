package com.example.neuroflowplanner.service.context;

import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity;

import java.time.Instant;

public record ChatContextSummarizationState(
        String conversationId,
        ChatContextSummarizationStatus status,
        String activeOperationId,
        String lastCompletedOperationId,
        String lastError,
        ChatContextBudgetSeverity lastBudgetSeverity,
        Double lastUsageRatio,
        Instant updatedAt) {

    public ChatContextSummarizationState {
        status = status == null ? ChatContextSummarizationStatus.IDLE : status;
        lastBudgetSeverity = lastBudgetSeverity == null ? ChatContextBudgetSeverity.UNKNOWN : lastBudgetSeverity;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public boolean summarizing() {
        return status == ChatContextSummarizationStatus.SUMMARIZING;
    }
}
