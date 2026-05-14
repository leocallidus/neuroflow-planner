package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.service.context.ChatContextBuildResult;
import com.example.neuroflowplanner.service.context.ChatContextManager;
import com.example.neuroflowplanner.service.context.ChatContextMode;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.ConfigManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatContextManagerPolicyTest extends IsolatedTestDataFixture {

    private static final List<String> OVERRIDDEN_KEYS = List.of(
        "ai.chat.context.recentWindowMessages",
        "ai.chat.context.summaryTriggerMessages",
        "ai.chat.context.maxBudgetTokens",
        "ai.chat.context.minimalBudgetTokens"
    );

    private final Map<String, String> previousConfigValues = new HashMap<>();
    private static final java.lang.reflect.Field CONFIG_PROPERTIES_FIELD = findConfigField();

    @BeforeEach
    void captureConfig() {
        for (String key : OVERRIDDEN_KEYS) {
            previousConfigValues.put(key, ConfigManager.getProperty(key));
        }
    }

    @AfterEach
    void restoreConfig() {
        for (Map.Entry<String, String> entry : previousConfigValues.entrySet()) {
            restoreInMemoryConfig(entry.getKey(), entry.getValue());
        }
    }

    @Test
    void includesSummaryAfterThresholdAndUsesConfiguredRecentWindow() {
        ConfigManager.setProperty("ai.chat.context.recentWindowMessages", "6");
        ConfigManager.setProperty("ai.chat.context.summaryTriggerMessages", "10");
        ConfigManager.setProperty("ai.chat.context.maxBudgetTokens", "12000");
        ConfigManager.setProperty("ai.chat.context.minimalBudgetTokens", "2500");

        ChatContextManager manager = new ChatContextManager();
        String conversationId = "context-summary-test";

        for (int i = 1; i <= 18; i++) {
            String message = "Сообщение " + i + " " + "x".repeat(80);
            if (i % 2 == 0) {
                manager.appendAssistantMessage(conversationId, message);
            } else {
                manager.appendUserMessage(conversationId, message);
            }
        }

        ChatContextBuildResult result = manager.buildContext(conversationId, ChatContextMode.AUTO);

        assertEquals(ChatContextMode.AUTO, result.requestedMode());
        assertEquals(ChatContextMode.AUTO, result.effectiveMode());
        assertTrue(result.summaryIncluded(), "Long history must include auto-summary");
        assertFalse(result.degradedToMinimal(), "Summary scenario should not degrade with normal budget");
        assertTrue(result.selectedHistoryMessages() <= 6, "Recent window must cap selected messages");
        assertEquals(18, result.totalHistoryMessages());

        List<AiRequestOptions.ChatHistoryEntry> summaryEntries = result.entries().stream()
            .filter(entry -> "system".equalsIgnoreCase(entry.role()))
            .filter(entry -> entry.content() != null && entry.content().startsWith("Сводка предыдущего контекста"))
            .toList();
        assertFalse(summaryEntries.isEmpty(), "Context must contain generated summary as system entry");
    }

    @Test
    void degradesToMinimalWhenContextBudgetIsExceeded() {
        ConfigManager.setProperty("ai.chat.context.recentWindowMessages", "12");
        ConfigManager.setProperty("ai.chat.context.summaryTriggerMessages", "30");
        ConfigManager.setProperty("ai.chat.context.maxBudgetTokens", "1500");
        ConfigManager.setProperty("ai.chat.context.minimalBudgetTokens", "500");

        ChatContextManager manager = new ChatContextManager();
        String conversationId = "context-budget-test";
        String largeChunk = "L".repeat(980);

        for (int i = 0; i < 9; i++) {
            manager.appendUserMessage(conversationId, "Большой фрагмент " + i + " " + largeChunk);
        }

        ChatContextBuildResult result = manager.buildContext(conversationId, ChatContextMode.AUTO);

        assertEquals(ChatContextMode.AUTO, result.requestedMode());
        assertEquals(ChatContextMode.MINIMAL, result.effectiveMode());
        assertTrue(result.overflowProtected(), "Budget overflow should trigger overflow protection");
        assertTrue(result.degradedToMinimal(), "Budget overflow should degrade AUTO to MINIMAL");
        assertTrue(result.selectedHistoryMessages() <= 2, "Minimal mode should keep only a tiny tail");
        assertTrue(result.estimatedTokens() <= 500, "Strict minimal context should fit minimal token budget");
    }

    @Test
    void keepsConversationContextsIsolated() {
        ChatContextManager manager = new ChatContextManager();
        manager.appendUserMessage("conv-A", "Только для A");
        manager.appendAssistantMessage("conv-A", "Ответ A");
        manager.appendUserMessage("conv-B", "Только для B");
        manager.appendAssistantMessage("conv-B", "Ответ B");

        ChatContextBuildResult contextA = manager.buildContext("conv-A", ChatContextMode.FULL);
        ChatContextBuildResult contextB = manager.buildContext("conv-B", ChatContextMode.FULL);

        String joinedA = joinEntries(contextA.entries());
        String joinedB = joinEntries(contextB.entries());

        assertTrue(joinedA.contains("Только для A"));
        assertTrue(joinedA.contains("Ответ A"));
        assertFalse(joinedA.contains("Только для B"));

        assertTrue(joinedB.contains("Только для B"));
        assertTrue(joinedB.contains("Ответ B"));
        assertFalse(joinedB.contains("Только для A"));
    }

    private static String joinEntries(List<AiRequestOptions.ChatHistoryEntry> entries) {
        return entries.stream()
            .map(AiRequestOptions.ChatHistoryEntry::content)
            .collect(Collectors.joining("\n"));
    }

    private static java.lang.reflect.Field findConfigField() {
        try {
            java.lang.reflect.Field field = ConfigManager.class.getDeclaredField("properties");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access ConfigManager.properties", e);
        }
    }

    private static void restoreInMemoryConfig(String key, String value) {
        try {
            Properties properties = (Properties) CONFIG_PROPERTIES_FIELD.get(null);
            if (value == null) {
                properties.remove(key);
            } else {
                properties.setProperty(key, value);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to restore config for key: " + key, e);
        }
    }
}
