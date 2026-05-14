package com.example.neuroflowplanner.service.context;

import com.example.neuroflowplanner.ai.AiRequestOptions;
import java.util.List;
import java.util.Objects;

/**
 * Result of context construction for a single request.
 */
public record ChatContextBuildResult(
        List<AiRequestOptions.ChatHistoryEntry> entries,
        ChatContextMode requestedMode,
        ChatContextMode effectiveMode,
        int estimatedTokens,
        int selectedHistoryMessages,
        int totalHistoryMessages,
        int pinnedFactsCount,
        boolean summaryIncluded,
        boolean overflowProtected,
        boolean degradedToMinimal) {

    public ChatContextBuildResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
        requestedMode = requestedMode == null ? ChatContextMode.AUTO : requestedMode;
        effectiveMode = effectiveMode == null ? requestedMode : effectiveMode;
        estimatedTokens = Math.max(0, estimatedTokens);
        selectedHistoryMessages = Math.max(0, selectedHistoryMessages);
        totalHistoryMessages = Math.max(0, totalHistoryMessages);
        pinnedFactsCount = Math.max(0, pinnedFactsCount);
    }
}
