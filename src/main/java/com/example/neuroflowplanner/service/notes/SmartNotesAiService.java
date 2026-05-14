package com.example.neuroflowplanner.service.notes;

import java.util.concurrent.CompletableFuture;

public interface SmartNotesAiService {
    CompletableFuture<String> requestCompletion(String userPrompt, String context);
}
