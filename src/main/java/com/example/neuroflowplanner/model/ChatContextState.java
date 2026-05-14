package com.example.neuroflowplanner.model;

import java.util.List;

public class ChatContextState {
    private final String conversationId;
    private final String preferredMode;
    private final String summary;
    private final int summaryCoveredMessages;
    private final List<String> pinnedFacts;
    private final Integer lastContextWindowTokens;
    private final Integer lastEstimatedUsageTokens;
    private final Integer lastReservedCompletionTokens;
    private final String lastSummarizeAt;
    private final String lastSummarizeStatus;
    private final Integer activeSummaryRevision;
    private final String lastBudgetSeverity;
    private final Double lastUsageRatio;
    private final String updatedAt;

    public ChatContextState(
        String conversationId,
        String preferredMode,
        String summary,
        int summaryCoveredMessages,
        List<String> pinnedFacts,
        String updatedAt
    ) {
        this(
            conversationId,
            preferredMode,
            summary,
            summaryCoveredMessages,
            pinnedFacts,
            null,
            null,
            null,
            "",
            "",
            0,
            "",
            null,
            updatedAt
        );
    }

    public ChatContextState(
        String conversationId,
        String preferredMode,
        String summary,
        int summaryCoveredMessages,
        List<String> pinnedFacts,
        Integer lastContextWindowTokens,
        Integer lastEstimatedUsageTokens,
        Integer lastReservedCompletionTokens,
        String lastSummarizeAt,
        String lastSummarizeStatus,
        Integer activeSummaryRevision,
        String lastBudgetSeverity,
        Double lastUsageRatio,
        String updatedAt
    ) {
        this.conversationId = conversationId;
        this.preferredMode = preferredMode;
        this.summary = summary;
        this.summaryCoveredMessages = summaryCoveredMessages;
        this.pinnedFacts = pinnedFacts == null ? List.of() : List.copyOf(pinnedFacts);
        this.lastContextWindowTokens = lastContextWindowTokens;
        this.lastEstimatedUsageTokens = lastEstimatedUsageTokens;
        this.lastReservedCompletionTokens = lastReservedCompletionTokens;
        this.lastSummarizeAt = lastSummarizeAt;
        this.lastSummarizeStatus = lastSummarizeStatus;
        this.activeSummaryRevision = activeSummaryRevision;
        this.lastBudgetSeverity = lastBudgetSeverity;
        this.lastUsageRatio = lastUsageRatio;
        this.updatedAt = updatedAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getPreferredMode() {
        return preferredMode;
    }

    public String getSummary() {
        return summary;
    }

    public int getSummaryCoveredMessages() {
        return summaryCoveredMessages;
    }

    public List<String> getPinnedFacts() {
        return pinnedFacts;
    }

    public Integer getLastContextWindowTokens() {
        return lastContextWindowTokens;
    }

    public Integer getLastEstimatedUsageTokens() {
        return lastEstimatedUsageTokens;
    }

    public Integer getLastReservedCompletionTokens() {
        return lastReservedCompletionTokens;
    }

    public String getLastSummarizeAt() {
        return lastSummarizeAt;
    }

    public String getLastSummarizeStatus() {
        return lastSummarizeStatus;
    }

    public Integer getActiveSummaryRevision() {
        return activeSummaryRevision;
    }

    public String getLastBudgetSeverity() {
        return lastBudgetSeverity;
    }

    public Double getLastUsageRatio() {
        return lastUsageRatio;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
