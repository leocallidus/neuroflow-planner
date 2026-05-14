package com.example.neuroflowplanner.service.context.budget;

import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.ai.dto.AiTextModelParameterMetadata;
import com.example.neuroflowplanner.service.context.ChatContextBuildResult;
import com.example.neuroflowplanner.util.AiConfigDefaults;

public final class ChatContextBudgetEstimator {

    private static final int DEFAULT_COMPLETION_RESERVE_TOKENS = 4096;
    private static final double WARNING_THRESHOLD = 0.70;
    private static final double CRITICAL_THRESHOLD = 0.85;

    public ChatContextBudgetSnapshot estimate(
            String conversationId,
            String modelId,
            ChatContextBuildResult context,
            AiTextModelContextMetadata contextMetadata,
            AiTextModelParameterMetadata parameterMetadata,
            Integer configuredMaxTokens) {
        int estimatedUsedTokens = context == null ? 0 : Math.max(0, context.estimatedTokens());
        Integer contextLimitTokens = contextMetadata == null ? null : contextMetadata.contextWindowTokens();
        int reservedCompletionTokens = resolveReservedCompletionTokens(contextLimitTokens, parameterMetadata, configuredMaxTokens);

        Integer effectivePromptBudgetTokens = null;
        Integer estimatedRemainingTokens = null;
        Double usageRatio = null;
        ChatContextBudgetSeverity severity = ChatContextBudgetSeverity.UNKNOWN;

        if (contextLimitTokens != null && contextLimitTokens > 0) {
            effectivePromptBudgetTokens = Math.max(1, contextLimitTokens - reservedCompletionTokens);
            estimatedRemainingTokens = Math.max(0, effectivePromptBudgetTokens - estimatedUsedTokens);
            usageRatio = Math.max(0.0, estimatedUsedTokens / (double) effectivePromptBudgetTokens);
            severity = resolveSeverity(usageRatio);
        }

        return new ChatContextBudgetSnapshot(
                normalizeConversationId(conversationId),
                AiConfigDefaults.normalizeExternalModelId(modelId),
                estimatedUsedTokens,
                contextLimitTokens,
                reservedCompletionTokens,
                effectivePromptBudgetTokens,
                estimatedRemainingTokens,
                usageRatio,
                severity);
    }

    private String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "conversation:unknown";
        }
        return conversationId.trim();
    }

    private int resolveReservedCompletionTokens(
            Integer contextLimitTokens,
            AiTextModelParameterMetadata parameterMetadata,
            Integer configuredMaxTokens) {
        int reserve = configuredMaxTokens != null && configuredMaxTokens > 0
                ? configuredMaxTokens
                : parameterMetadata != null && parameterMetadata.maxCompletionTokens() != null && parameterMetadata.maxCompletionTokens() > 0
                ? parameterMetadata.maxCompletionTokens()
                : DEFAULT_COMPLETION_RESERVE_TOKENS;
        if (contextLimitTokens == null || contextLimitTokens <= 0) {
            return reserve;
        }
        return Math.min(reserve, Math.max(256, contextLimitTokens - 1));
    }

    private ChatContextBudgetSeverity resolveSeverity(Double usageRatio) {
        if (usageRatio == null) {
            return ChatContextBudgetSeverity.UNKNOWN;
        }
        if (usageRatio >= 1.0) {
            return ChatContextBudgetSeverity.OVER_LIMIT;
        }
        if (usageRatio >= CRITICAL_THRESHOLD) {
            return ChatContextBudgetSeverity.CRITICAL;
        }
        if (usageRatio >= WARNING_THRESHOLD) {
            return ChatContextBudgetSeverity.WARNING;
        }
        return ChatContextBudgetSeverity.NORMAL;
    }
}
