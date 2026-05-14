package com.example.neuroflowplanner.service.notes;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.util.StructuredLogger;

import java.util.concurrent.CompletableFuture;

public class DefaultSmartNotesAiService implements SmartNotesAiService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DefaultSmartNotesAiService.class);

    @Override
    public CompletableFuture<String> requestCompletion(String userPrompt, String context) {
        AiClient aiClient = AiClientFactory.getInstance().getActiveClient();

        String systemPrompt = "Ты умный помощник для ведения заметок. Твоя задача - помогать пользователю писать, форматировать и дополнять заметки. Отвечай в формате Markdown.";
        String prompt = "Контекст заметки:\n" + safe(context) + "\n\nЗапрос пользователя: " + safe(userPrompt);

        AiRequestOptions options = AiRequestOptions.builder()
                .model(aiClient.getDefaultModel())
                .systemPrompt(systemPrompt)
                .build();

        return aiClient.sendChatMessage(prompt, options)
                .thenApply(response -> {
                    if (response.success() && response.content() != null && !response.content().isBlank()) {
                        return response.content();
                    }
                    throw new IllegalStateException("AI response content is empty");
                })
                .exceptionally(ex -> {
                    LOG.error("smartnotes.ai.request.exception", ex);
                    throw new RuntimeException("AI request error", ex);
                });
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
