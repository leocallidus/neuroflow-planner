package com.example.neuroflowplanner.service.context;

import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatContextCompactionServiceTest {

    @Test
    void enforcesSingleSummarizeOperationPerConversation() {
        ChatContextCompactionService service = new ChatContextCompactionService();
        String conversationId = "conv-state-lock";

        ChatContextSummarizationState nearLimitState = service.updateBudgetState(
                conversationId,
                new ChatContextBudgetSnapshot(
                        conversationId,
                        "openai/gpt-5.4",
                        840,
                        1_000,
                        100,
                        900,
                        60,
                        0.93,
                        ChatContextBudgetSeverity.CRITICAL));

        assertEquals(ChatContextSummarizationStatus.NEAR_LIMIT, nearLimitState.status());

        String firstOperationId = service.tryStartSummarization(conversationId);
        assertNotNull(firstOperationId);
        assertEquals(ChatContextSummarizationStatus.SUMMARIZING, service.getState(conversationId).status());

        String secondOperationId = service.tryStartSummarization(conversationId);
        assertNull(secondOperationId);
        assertEquals(firstOperationId, service.getState(conversationId).activeOperationId());
    }

    @Test
    void transitionsToReadyAndFailedStatesForMatchingOperation() {
        ChatContextCompactionService service = new ChatContextCompactionService();
        String conversationId = "conv-state-terminal";

        service.updateBudgetState(
                conversationId,
                new ChatContextBudgetSnapshot(
                        conversationId,
                        "openai/gpt-5.4",
                        760,
                        1_000,
                        100,
                        900,
                        140,
                        0.84,
                        ChatContextBudgetSeverity.WARNING));

        String readyOperationId = service.tryStartSummarization(conversationId);
        ChatContextSummarizationState readyState = service.markSummaryReady(conversationId, readyOperationId);

        assertEquals(ChatContextSummarizationStatus.SUMMARY_READY, readyState.status());
        assertEquals(readyOperationId, readyState.lastCompletedOperationId());
        assertNull(readyState.activeOperationId());

        String failedOperationId = service.tryStartSummarization(conversationId);
        ChatContextSummarizationState failedState =
                service.markSummaryFailed(conversationId, failedOperationId, "provider timeout");

        assertEquals(ChatContextSummarizationStatus.SUMMARY_FAILED, failedState.status());
        assertEquals("provider timeout", failedState.lastError());
        assertEquals(failedOperationId, failedState.lastCompletedOperationId());
        assertNull(failedState.activeOperationId());
    }

    @Test
    void switchesBetweenIdleAndNearLimitWhenBudgetCrossesThresholds() {
        ChatContextCompactionService service = new ChatContextCompactionService();
        String conversationId = "conv-thresholds";

        ChatContextSummarizationState idleState = service.updateBudgetState(
                conversationId,
                new ChatContextBudgetSnapshot(
                        conversationId,
                        "openai/gpt-5.4",
                        420,
                        2_000,
                        256,
                        1_744,
                        1_324,
                        0.24,
                        ChatContextBudgetSeverity.NORMAL));

        assertEquals(ChatContextSummarizationStatus.IDLE, idleState.status());
        assertEquals(ChatContextBudgetSeverity.NORMAL, idleState.lastBudgetSeverity());

        ChatContextSummarizationState warningState = service.updateBudgetState(
                conversationId,
                new ChatContextBudgetSnapshot(
                        conversationId,
                        "openai/gpt-5.4",
                        1_420,
                        2_000,
                        256,
                        1_744,
                        324,
                        0.81,
                        ChatContextBudgetSeverity.WARNING));

        assertEquals(ChatContextSummarizationStatus.NEAR_LIMIT, warningState.status());
        assertEquals(ChatContextBudgetSeverity.WARNING, warningState.lastBudgetSeverity());

        ChatContextSummarizationState recoveredState = service.updateBudgetState(
                conversationId,
                new ChatContextBudgetSnapshot(
                        conversationId,
                        "openai/gpt-5.4",
                        510,
                        2_000,
                        256,
                        1_744,
                        1_234,
                        0.29,
                        ChatContextBudgetSeverity.NORMAL));

        assertEquals(ChatContextSummarizationStatus.IDLE, recoveredState.status());
        assertEquals(ChatContextBudgetSeverity.NORMAL, recoveredState.lastBudgetSeverity());
    }
}
