package com.example.neuroflowplanner.service.planningquality;

public record PlanningQualityAiPromptPayload(
        String systemPrompt,
        String userPrompt,
        PlanningQualitySummary fallbackSummary) {

    public PlanningQualityAiPromptPayload {
        systemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
        userPrompt = userPrompt == null ? "" : userPrompt.trim();
        fallbackSummary = fallbackSummary == null
                ? PlanningQualitySummary.unavailable()
                : fallbackSummary;
    }
}
