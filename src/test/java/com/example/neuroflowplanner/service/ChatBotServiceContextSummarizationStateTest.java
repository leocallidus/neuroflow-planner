package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.ChatContextState;
import com.example.neuroflowplanner.service.context.ChatContextMode;
import com.example.neuroflowplanner.service.context.ChatContextSummaryTemplate;
import com.example.neuroflowplanner.service.context.ChatContextSummarizationState;
import com.example.neuroflowplanner.service.context.ChatContextSummarizationStatus;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatBotServiceContextSummarizationStateTest extends IsolatedTestDataFixture {

    private final DatabaseManager db = DatabaseManager.getInstance();

    @Test
    void exposesPerConversationSummarizationStateFromBudgetSnapshot() {
        List<AiDiscoveredModelInfo> previousCatalog = ConfigManager.getExternalApiModelCatalog();
        Integer previousAssistantMaxTokens = ConfigManager.getAssistantTextMaxTokens();
        try {
            ConfigManager.setAssistantTextMaxTokens(256);
            ConfigManager.setExternalApiModelCatalog(List.of(
                    new AiDiscoveredModelInfo(
                            "openai/gpt-5.4",
                            "chat",
                            true,
                            true,
                            false,
                            true,
                            AiTextModelContextMetadata.fromTokens(600),
                            null)
            ));

            ChatBotService service = new ChatBotService();
            String conversationId = db.createChatConversation("Summarization state service").getId();
            List<AiRequestOptions.ChatHistoryEntry> history = List.of(
                    new AiRequestOptions.ChatHistoryEntry("user", "Запрос " + "x".repeat(620)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ " + "y".repeat(560)),
                    new AiRequestOptions.ChatHistoryEntry("user", "Уточнение " + "z".repeat(620)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Длинный ответ " + "k".repeat(560))
            );

            service.replaceConversationHistory(conversationId, history);

            ChatContextSummarizationState state =
                    service.getContextSummarizationState(conversationId, "openai/gpt-5.4", ChatContextMode.AUTO);

            assertEquals(conversationId, state.conversationId());
            assertEquals(ChatContextSummarizationStatus.NEAR_LIMIT, state.status());
            assertTrue(state.lastBudgetSeverity() == ChatContextBudgetSeverity.WARNING
                    || state.lastBudgetSeverity() == ChatContextBudgetSeverity.CRITICAL
                    || state.lastBudgetSeverity() == ChatContextBudgetSeverity.OVER_LIMIT);
        } finally {
            ConfigManager.setExternalApiModelCatalog(previousCatalog);
            ConfigManager.setAssistantTextMaxTokens(previousAssistantMaxTokens);
        }
    }

    @Test
    void allowsOnlyOneActiveSummarizationOperationPerConversation() {
        List<AiDiscoveredModelInfo> previousCatalog = ConfigManager.getExternalApiModelCatalog();
        Integer previousAssistantMaxTokens = ConfigManager.getAssistantTextMaxTokens();
        try {
            ConfigManager.setAssistantTextMaxTokens(256);
            ConfigManager.setExternalApiModelCatalog(List.of(
                    new AiDiscoveredModelInfo(
                            "openai/gpt-5.4",
                            "chat",
                            true,
                            true,
                            false,
                            true,
                            AiTextModelContextMetadata.fromTokens(600),
                            null)
            ));

            ChatBotService service = new ChatBotService();
            String conversationId = db.createChatConversation("Summarization state lock").getId();
            service.replaceConversationHistory(conversationId, List.of(
                    new AiRequestOptions.ChatHistoryEntry("user", "Запрос " + "x".repeat(620)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ " + "y".repeat(560)),
                    new AiRequestOptions.ChatHistoryEntry("user", "Ещё запрос " + "z".repeat(620)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ещё ответ " + "k".repeat(560))
            ));

            service.getContextSummarizationState(conversationId, "openai/gpt-5.4", ChatContextMode.AUTO);

            String firstOperationId = service.tryStartContextSummarization(conversationId);
            String secondOperationId = service.tryStartContextSummarization(conversationId);

            assertNotNull(firstOperationId);
            assertNull(secondOperationId);

            ChatContextSummarizationState activeState = service.getCachedContextSummarizationState(conversationId);
            assertEquals(ChatContextSummarizationStatus.SUMMARIZING, activeState.status());
            assertEquals(firstOperationId, activeState.activeOperationId());

            ChatContextSummarizationState readyState =
                    service.markContextSummarizationReady(conversationId, firstOperationId);
            assertEquals(ChatContextSummarizationStatus.SUMMARY_READY, readyState.status());
            assertEquals(firstOperationId, readyState.lastCompletedOperationId());
        } finally {
            ConfigManager.setExternalApiModelCatalog(previousCatalog);
            ConfigManager.setAssistantTextMaxTokens(previousAssistantMaxTokens);
        }
    }

    @Test
    void manualSummarizationUsesServicePathAndCompletesForConversation() throws Exception {
        List<AiDiscoveredModelInfo> previousCatalog = ConfigManager.getExternalApiModelCatalog();
        Integer previousAssistantMaxTokens = ConfigManager.getAssistantTextMaxTokens();
        try {
            ConfigManager.setAssistantTextMaxTokens(128);
            ConfigManager.setExternalApiModelCatalog(List.of(
                    new AiDiscoveredModelInfo(
                            "openai/gpt-5.4",
                            "chat",
                            true,
                            true,
                            false,
                            true,
                            AiTextModelContextMetadata.fromTokens(700),
                            null)
            ));

            ChatBotService service = new ChatBotService();
            String conversationId = db.createChatConversation("Manual summarization").getId();
            service.replaceConversationHistory(conversationId, List.of(
                    new AiRequestOptions.ChatHistoryEntry("user", "Сообщение 1 " + "x".repeat(900)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ 1 " + "y".repeat(820)),
                    new AiRequestOptions.ChatHistoryEntry("user", "Сообщение 2 " + "z".repeat(900)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ 2 " + "k".repeat(820)),
                    new AiRequestOptions.ChatHistoryEntry("user", "Сообщение 3 " + "m".repeat(900)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ 3 " + "n".repeat(820)),
                    new AiRequestOptions.ChatHistoryEntry("user", "Сообщение 4 " + "p".repeat(900)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ 4 " + "q".repeat(820))
            ));

            ChatContextSummarizationState state = service
                    .summarizeContext(conversationId, "openai/gpt-5.4", ChatContextMode.AUTO)
                    .get();

            assertNotNull(state);
            assertEquals(conversationId, state.conversationId());
            assertTrue(
                    state.status() == ChatContextSummarizationStatus.IDLE
                            || state.status() == ChatContextSummarizationStatus.NEAR_LIMIT
                            || state.status() == ChatContextSummarizationStatus.SUMMARY_READY,
                    "manual summarize should finish in a non-active state");
            assertTrue(!state.summarizing(), "manual summarize should not stay in active state");
            assertNull(state.lastError());
            assertTrue(state.lastCompletedOperationId() != null || state.status() == ChatContextSummarizationStatus.NEAR_LIMIT);
            ChatContextState persisted = db.loadChatContextState(conversationId);
            assertNotNull(persisted);
            assertNotNull(persisted.getSummary());
            assertTrue(persisted.getSummary().startsWith(ChatContextSummaryTemplate.TITLE));
            for (String section : ChatContextSummaryTemplate.requiredSections()) {
                assertTrue(persisted.getSummary().contains(section), "summary must contain section " + section);
            }
        } finally {
            ConfigManager.setExternalApiModelCatalog(previousCatalog);
            ConfigManager.setAssistantTextMaxTokens(previousAssistantMaxTokens);
        }
    }

    @Test
    void restoresPersistedSummarizationMetadataAfterServiceRestart() throws Exception {
        List<AiDiscoveredModelInfo> previousCatalog = ConfigManager.getExternalApiModelCatalog();
        Integer previousAssistantMaxTokens = ConfigManager.getAssistantTextMaxTokens();
        try {
            ConfigManager.setAssistantTextMaxTokens(128);
            ConfigManager.setExternalApiModelCatalog(List.of(
                    new AiDiscoveredModelInfo(
                            "openai/gpt-5.4",
                            "chat",
                            true,
                            true,
                            false,
                            true,
                            AiTextModelContextMetadata.fromTokens(700),
                            null)
            ));

            ChatBotService firstService = new ChatBotService();
            String conversationId = db.createChatConversation("Persisted summarization metadata").getId();
            firstService.replaceConversationHistory(conversationId, List.of(
                    new AiRequestOptions.ChatHistoryEntry("user", "Сообщение A " + "x".repeat(900)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ A " + "y".repeat(820)),
                    new AiRequestOptions.ChatHistoryEntry("user", "Сообщение B " + "z".repeat(900)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ B " + "k".repeat(820)),
                    new AiRequestOptions.ChatHistoryEntry("user", "Сообщение C " + "m".repeat(900)),
                    new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ C " + "n".repeat(820))
            ));

            firstService.summarizeContext(conversationId, "openai/gpt-5.4", ChatContextMode.AUTO).get();

            ChatBotService restoredService = new ChatBotService();
            ChatContextSummarizationState restoredState =
                    restoredService.getContextSummarizationState(conversationId, "openai/gpt-5.4", ChatContextMode.AUTO);

            assertNotNull(restoredState);
            assertEquals(conversationId, restoredState.conversationId());
            assertTrue(restoredState.lastCompletedOperationId() != null || restoredState.status() == ChatContextSummarizationStatus.NEAR_LIMIT);
            assertTrue(restoredState.lastBudgetSeverity() != null);
            ChatContextState persisted = db.loadChatContextState(conversationId);
            assertNotNull(persisted);
            assertTrue(persisted.getActiveSummaryRevision() != null && persisted.getActiveSummaryRevision() > 0);
            assertTrue(persisted.getLastEstimatedUsageTokens() != null && persisted.getLastEstimatedUsageTokens() > 0);
            assertTrue(persisted.getLastContextWindowTokens() != null && persisted.getLastContextWindowTokens() > 0);
        } finally {
            ConfigManager.setExternalApiModelCatalog(previousCatalog);
            ConfigManager.setAssistantTextMaxTokens(previousAssistantMaxTokens);
        }
    }
}
