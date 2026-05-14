package com.example.neuroflowplanner.service.focusblocks;

public record FocusBlockAiPromptPayload(
        String systemPrompt,
        String userPrompt,
        FocusBlockExplanation fallbackExplanation) {

    public FocusBlockAiPromptPayload {
        systemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
        userPrompt = userPrompt == null ? "" : userPrompt.trim();
        fallbackExplanation = fallbackExplanation == null
                ? FocusBlockExplanation.unavailable()
                : fallbackExplanation;
    }
}
