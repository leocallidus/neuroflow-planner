package com.example.neuroflowplanner.service.context.budget;

public record ChatContextBudgetSnapshot(
        String conversationId,
        String modelId,
        int estimatedUsedTokens,
        Integer contextLimitTokens,
        int reservedCompletionTokens,
        Integer effectivePromptBudgetTokens,
        Integer estimatedRemainingTokens,
        Double usageRatio,
        ChatContextBudgetSeverity severity) {

    public ChatContextBudgetSnapshot {
        estimatedUsedTokens = Math.max(0, estimatedUsedTokens);
        reservedCompletionTokens = Math.max(0, reservedCompletionTokens);
        severity = severity == null ? ChatContextBudgetSeverity.UNKNOWN : severity;
    }

    public boolean hasKnownContextLimit() {
        return contextLimitTokens != null && contextLimitTokens > 0;
    }
}
