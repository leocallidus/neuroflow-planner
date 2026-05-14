package com.example.neuroflowplanner.ai.dto;

public record AiTextModelParameterMetadata(
        Integer maxCompletionTokens,
        boolean supportsTemperature,
        boolean supportsTopP,
        boolean supportsFrequencyPenalty,
        boolean supportsPresencePenalty,
        Double defaultTemperature,
        Double defaultTopP,
        Double defaultFrequencyPenalty,
        Double defaultPresencePenalty) {

    public boolean supportsAnyAdvancedParameter() {
        return supportsTemperature || supportsTopP || supportsFrequencyPenalty || supportsPresencePenalty;
    }
}
