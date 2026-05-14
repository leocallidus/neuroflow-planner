package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.ai.dto.AiTextModelParameterMetadata;
import com.example.neuroflowplanner.service.context.ChatContextManager;
import com.example.neuroflowplanner.service.context.ChatContextMode;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSnapshot;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatContextBudgetManagerTest extends IsolatedTestDataFixture {

    @Test
    void buildsBudgetSnapshotForConversationAndModel() {
        List<AiDiscoveredModelInfo> previousCatalog = ConfigManager.getExternalApiModelCatalog();
        Integer previousAssistantMaxTokens = ConfigManager.getAssistantTextMaxTokens();
        try {
            ConfigManager.setAssistantTextMaxTokens(4096);
            ConfigManager.setExternalApiModelCatalog(List.of(
                    new AiDiscoveredModelInfo(
                            "openai/gpt-5.4",
                            "chat",
                            true,
                            true,
                            false,
                            true,
                            AiTextModelContextMetadata.fromTokens(1_050_000),
                            new AiTextModelParameterMetadata(32_000, true, true, true, true, 1.0, 1.0, 0.0, 0.0))
            ));

            ChatContextManager manager = new ChatContextManager();
            String conversationId = "context-budget-conversation";

            for (int i = 0; i < 12; i++) {
                manager.appendUserMessage(conversationId, "Сообщение пользователя " + i + " " + "x".repeat(240));
                manager.appendAssistantMessage(conversationId, "Ответ ассистента " + i + " " + "y".repeat(180));
            }

            ChatContextBudgetSnapshot snapshot =
                    manager.buildBudgetSnapshot(conversationId, "openai/gpt-5.4", ChatContextMode.AUTO);

            assertNotNull(snapshot);
            assertEquals(conversationId, snapshot.conversationId());
            assertEquals("openai/gpt-5.4", snapshot.modelId());
            assertEquals(1_050_000, snapshot.contextLimitTokens());
            assertEquals(4096, snapshot.reservedCompletionTokens());
            assertTrue(snapshot.estimatedUsedTokens() > 0);
            assertNotNull(snapshot.effectivePromptBudgetTokens());
            assertNotNull(snapshot.estimatedRemainingTokens());
            assertNotNull(snapshot.usageRatio());
            assertEquals(ChatContextBudgetSeverity.NORMAL, snapshot.severity());
        } finally {
            ConfigManager.setExternalApiModelCatalog(previousCatalog);
            ConfigManager.setAssistantTextMaxTokens(previousAssistantMaxTokens);
        }
    }

    @Test
    void buildsEstimateOnlySnapshotWhenSelectedModelMetadataIsUnknown() {
        List<AiDiscoveredModelInfo> previousCatalog = ConfigManager.getExternalApiModelCatalog();
        Integer previousAssistantMaxTokens = ConfigManager.getAssistantTextMaxTokens();
        try {
            ConfigManager.setAssistantTextMaxTokens(2048);
            ConfigManager.setExternalApiModelCatalog(List.of(
                    new AiDiscoveredModelInfo(
                            "openai/gpt-5.4",
                            "chat",
                            true,
                            true,
                            false,
                            true,
                            AiTextModelContextMetadata.fromTokens(1_050_000),
                            new AiTextModelParameterMetadata(32_000, true, true, true, true, 1.0, 1.0, 0.0, 0.0))
            ));

            ChatContextManager manager = new ChatContextManager();
            String conversationId = "context-budget-unknown-model";

            for (int i = 0; i < 6; i++) {
                manager.appendUserMessage(conversationId, "Запрос " + i + " " + "x".repeat(320));
                manager.appendAssistantMessage(conversationId, "Ответ " + i + " " + "y".repeat(260));
            }

            ChatContextBudgetSnapshot snapshot =
                    manager.buildBudgetSnapshot(conversationId, "custom/unknown-model", ChatContextMode.AUTO);

            assertNotNull(snapshot);
            assertEquals(conversationId, snapshot.conversationId());
            assertEquals("custom/unknown-model", snapshot.modelId());
            assertTrue(snapshot.estimatedUsedTokens() > 0);
            assertEquals(2048, snapshot.reservedCompletionTokens());
            assertNull(snapshot.contextLimitTokens());
            assertNull(snapshot.effectivePromptBudgetTokens());
            assertNull(snapshot.estimatedRemainingTokens());
            assertNull(snapshot.usageRatio());
            assertEquals(ChatContextBudgetSeverity.UNKNOWN, snapshot.severity());
        } finally {
            ConfigManager.setExternalApiModelCatalog(previousCatalog);
            ConfigManager.setAssistantTextMaxTokens(previousAssistantMaxTokens);
        }
    }
}
