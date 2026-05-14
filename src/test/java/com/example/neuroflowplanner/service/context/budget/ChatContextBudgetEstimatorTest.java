package com.example.neuroflowplanner.service.context.budget;

import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.ai.dto.AiTextModelParameterMetadata;
import com.example.neuroflowplanner.service.context.ChatContextBuildResult;
import com.example.neuroflowplanner.service.context.ChatContextMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatContextBudgetEstimatorTest {

    @Test
    void calculatesBudgetSnapshotWithKnownContextWindow() {
        ChatContextBudgetEstimator estimator = new ChatContextBudgetEstimator();
        ChatContextBuildResult context = new ChatContextBuildResult(
                List.of(),
                ChatContextMode.AUTO,
                ChatContextMode.AUTO,
                412_000,
                12,
                48,
                2,
                true,
                false,
                false);

        ChatContextBudgetSnapshot snapshot = estimator.estimate(
                "conv-1",
                "openai/gpt-5.4",
                context,
                AiTextModelContextMetadata.fromTokens(1_050_000),
                new AiTextModelParameterMetadata(32_000, true, true, true, true, 1.0, 1.0, 0.0, 0.0),
                null);

        assertEquals("conv-1", snapshot.conversationId());
        assertEquals("openai/gpt-5.4", snapshot.modelId());
        assertEquals(412_000, snapshot.estimatedUsedTokens());
        assertEquals(1_050_000, snapshot.contextLimitTokens());
        assertEquals(32_000, snapshot.reservedCompletionTokens());
        assertEquals(1_018_000, snapshot.effectivePromptBudgetTokens());
        assertEquals(606_000, snapshot.estimatedRemainingTokens());
        assertTrue(snapshot.usageRatio() > 0.40 && snapshot.usageRatio() < 0.41);
        assertEquals(ChatContextBudgetSeverity.NORMAL, snapshot.severity());
    }

    @Test
    void marksSnapshotAsCriticalWhenUsageApproachesLimit() {
        ChatContextBudgetEstimator estimator = new ChatContextBudgetEstimator();
        ChatContextBuildResult context = new ChatContextBuildResult(
                List.of(),
                ChatContextMode.AUTO,
                ChatContextMode.AUTO,
                870,
                4,
                30,
                0,
                true,
                false,
                false);

        ChatContextBudgetSnapshot snapshot = estimator.estimate(
                "conv-2",
                "openai/gpt-4o",
                context,
                AiTextModelContextMetadata.fromTokens(2_000),
                new AiTextModelParameterMetadata(1_000, true, true, false, false, 1.0, 1.0, null, null),
                null);

        assertEquals(1_000, snapshot.reservedCompletionTokens());
        assertEquals(1_000, snapshot.effectivePromptBudgetTokens());
        assertEquals(0.87, snapshot.usageRatio(), 0.0001);
        assertEquals(ChatContextBudgetSeverity.CRITICAL, snapshot.severity());
    }

    @Test
    void returnsEstimateOnlySnapshotWhenModelMetadataIsUnknown() {
        ChatContextBudgetEstimator estimator = new ChatContextBudgetEstimator();
        ChatContextBuildResult context = new ChatContextBuildResult(
                List.of(),
                ChatContextMode.AUTO,
                ChatContextMode.AUTO,
                512,
                3,
                6,
                0,
                false,
                false,
                false);

        ChatContextBudgetSnapshot snapshot = estimator.estimate(
                "conv-unknown",
                "custom/model-without-catalog-metadata",
                context,
                null,
                null,
                null);

        assertEquals("conv-unknown", snapshot.conversationId());
        assertEquals("custom/model-without-catalog-metadata", snapshot.modelId());
        assertEquals(512, snapshot.estimatedUsedTokens());
        assertEquals(4096, snapshot.reservedCompletionTokens());
        assertNull(snapshot.contextLimitTokens());
        assertNull(snapshot.effectivePromptBudgetTokens());
        assertNull(snapshot.estimatedRemainingTokens());
        assertNull(snapshot.usageRatio());
        assertEquals(ChatContextBudgetSeverity.UNKNOWN, snapshot.severity());
    }
}
