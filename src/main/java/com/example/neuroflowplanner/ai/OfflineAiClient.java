package com.example.neuroflowplanner.ai;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI client implementation for offline mode.
 * 
 * Returns stub/mock responses when AI features are disabled.
 * This preserves the current "simulation" behavior when no AI is available.
 */
public class OfflineAiClient implements AiClient {

    private static final String OFFLINE_MODEL = "offline";

    /**
     * Default stub responses for common queries.
     */
    private static final String[] STUB_RESPONSES = {
            "Режим офлайн: ИИ-функции отключены. Перейдите в настройки для подключения к ИИ.",
            "Офлайн-режим активен. Для использования ИИ-ассистента настройте подключение в разделе настроек.",
            "ИИ недоступен в офлайн-режиме. Вы можете включить локальный Ollama или внешний API в настройках."
    };

    private int responseIndex = 0;

    @Override
    public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
        // Rotate through stub responses
        String response = STUB_RESPONSES[responseIndex % STUB_RESPONSES.length];
        responseIndex++;
        
        return CompletableFuture.completedFuture(AiResponse.offline(response));
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testConnection() {
        return CompletableFuture.completedFuture(ConnectionTestResult.offlineSuccess());
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey) {
        // Offline mode doesn't use URL/key, always succeeds
        return CompletableFuture.completedFuture(ConnectionTestResult.offlineSuccess());
    }

    @Override
    public CompletableFuture<ConnectionTestResult> testModel(String model) {
        return CompletableFuture.completedFuture(
                ConnectionTestResult.success(
                        "Офлайн режим: модель не требуется",
                        AiMode.OFFLINE,
                        null,
                        OFFLINE_MODEL,
                        "Тестовый ответ в офлайн-режиме",
                        0L
                )
        );
    }

    @Override
    public CompletableFuture<List<String>> fetchAvailableModels() {
        // Offline mode has no models
        return CompletableFuture.completedFuture(Collections.singletonList(OFFLINE_MODEL));
    }

    @Override
    public boolean supportsImages() {
        return false;
    }

    @Override
    public AiMode getMode() {
        return AiMode.OFFLINE;
    }

    @Override
    public String getDefaultModel() {
        return OFFLINE_MODEL;
    }

    @Override
    public boolean isConfigured() {
        // Offline mode is always configured
        return true;
    }

    @Override
    public String getBaseUrl() {
        return null;
    }

    @Override
    public void reloadConfiguration() {
        // Offline mode has no configuration to reload
    }
}
