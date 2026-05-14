package com.example.neuroflowplanner.service.context;

import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSnapshot;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatContextCompactionService {

    private static final String UNKNOWN_CONVERSATION_ID = "conversation:unknown";

    private final ConcurrentHashMap<String, MutableSummarizationState> states = new ConcurrentHashMap<>();

    public ChatContextSummarizationState getState(String conversationId) {
        MutableSummarizationState state = getMutableState(conversationId);
        synchronized (state) {
            return state.snapshot(normalizeConversationId(conversationId));
        }
    }

    public ChatContextSummarizationState updateBudgetState(String conversationId, ChatContextBudgetSnapshot snapshot) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        MutableSummarizationState state = getMutableState(normalizedConversationId);
        synchronized (state) {
            if (snapshot != null) {
                state.lastBudgetSeverity = snapshot.severity();
                state.lastUsageRatio = snapshot.usageRatio();
            } else {
                state.lastBudgetSeverity = ChatContextBudgetSeverity.UNKNOWN;
                state.lastUsageRatio = null;
            }
            if (state.status != ChatContextSummarizationStatus.SUMMARIZING
                    && state.status != ChatContextSummarizationStatus.SUMMARY_FAILED
                    && state.status != ChatContextSummarizationStatus.SUMMARY_READY) {
                state.status = isNearLimit(snapshot)
                        ? ChatContextSummarizationStatus.NEAR_LIMIT
                        : ChatContextSummarizationStatus.IDLE;
            }
            state.updatedAt = Instant.now();
            return state.snapshot(normalizedConversationId);
        }
    }

    public String tryStartSummarization(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        MutableSummarizationState state = getMutableState(normalizedConversationId);
        synchronized (state) {
            if (state.status == ChatContextSummarizationStatus.SUMMARIZING && state.activeOperationId != null) {
                return null;
            }
            String operationId = "ctxsum-" + java.util.UUID.randomUUID();
            state.status = ChatContextSummarizationStatus.SUMMARIZING;
            state.activeOperationId = operationId;
            state.lastError = null;
            state.updatedAt = Instant.now();
            return operationId;
        }
    }

    public ChatContextSummarizationState markSummaryReady(String conversationId, String operationId) {
        return completeOperation(conversationId, operationId, ChatContextSummarizationStatus.SUMMARY_READY, null);
    }

    public ChatContextSummarizationState markSummaryFailed(String conversationId, String operationId, String errorMessage) {
        return completeOperation(
                conversationId,
                operationId,
                ChatContextSummarizationStatus.SUMMARY_FAILED,
                errorMessage == null ? "" : errorMessage.trim());
    }

    public ChatContextSummarizationState resetState(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        MutableSummarizationState state = getMutableState(normalizedConversationId);
        synchronized (state) {
            state.status = isNearLimit(state.lastBudgetSeverity)
                    ? ChatContextSummarizationStatus.NEAR_LIMIT
                    : ChatContextSummarizationStatus.IDLE;
            state.activeOperationId = null;
            state.lastCompletedOperationId = null;
            state.lastError = null;
            state.updatedAt = Instant.now();
            return state.snapshot(normalizedConversationId);
        }
    }

    public void clearState(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            states.clear();
            return;
        }
        states.remove(normalizeConversationId(conversationId));
    }

    public ChatContextSummarizationState restoreState(
            String conversationId,
            ChatContextSummarizationStatus status,
            String lastError,
            ChatContextBudgetSeverity lastBudgetSeverity,
            Double lastUsageRatio,
            String lastCompletedOperationId,
            Instant updatedAt) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        MutableSummarizationState state = getMutableState(normalizedConversationId);
        synchronized (state) {
            state.status = status == null ? ChatContextSummarizationStatus.IDLE : status;
            state.activeOperationId = null;
            state.lastCompletedOperationId = lastCompletedOperationId == null || lastCompletedOperationId.isBlank()
                    ? null
                    : lastCompletedOperationId.trim();
            state.lastError = lastError == null || lastError.isBlank() ? null : lastError.trim();
            state.lastBudgetSeverity = lastBudgetSeverity == null ? ChatContextBudgetSeverity.UNKNOWN : lastBudgetSeverity;
            state.lastUsageRatio = lastUsageRatio;
            state.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
            return state.snapshot(normalizedConversationId);
        }
    }

    private ChatContextSummarizationState completeOperation(
            String conversationId,
            String operationId,
            ChatContextSummarizationStatus completedStatus,
            String errorMessage) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        MutableSummarizationState state = getMutableState(normalizedConversationId);
        synchronized (state) {
            if (operationId == null
                    || state.activeOperationId == null
                    || !state.activeOperationId.equals(operationId)) {
                return state.snapshot(normalizedConversationId);
            }
            state.status = completedStatus;
            state.lastCompletedOperationId = operationId;
            state.activeOperationId = null;
            state.lastError = errorMessage == null || errorMessage.isBlank() ? null : errorMessage;
            state.updatedAt = Instant.now();
            return state.snapshot(normalizedConversationId);
        }
    }

    private boolean isNearLimit(ChatContextBudgetSnapshot snapshot) {
        return snapshot != null && isNearLimit(snapshot.severity());
    }

    private boolean isNearLimit(ChatContextBudgetSeverity severity) {
        return severity == ChatContextBudgetSeverity.WARNING
                || severity == ChatContextBudgetSeverity.CRITICAL
                || severity == ChatContextBudgetSeverity.OVER_LIMIT;
    }

    private MutableSummarizationState getMutableState(String conversationId) {
        return states.computeIfAbsent(normalizeConversationId(conversationId), key -> new MutableSummarizationState());
    }

    private String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UNKNOWN_CONVERSATION_ID;
        }
        return conversationId.trim();
    }

    private static final class MutableSummarizationState {
        private ChatContextSummarizationStatus status = ChatContextSummarizationStatus.IDLE;
        private String activeOperationId;
        private String lastCompletedOperationId;
        private String lastError;
        private ChatContextBudgetSeverity lastBudgetSeverity = ChatContextBudgetSeverity.UNKNOWN;
        private Double lastUsageRatio;
        private Instant updatedAt = Instant.now();

        private ChatContextSummarizationState snapshot(String conversationId) {
            return new ChatContextSummarizationState(
                    conversationId,
                    status,
                    activeOperationId,
                    lastCompletedOperationId,
                    lastError,
                    lastBudgetSeverity,
                    lastUsageRatio,
                    updatedAt);
        }
    }
}
