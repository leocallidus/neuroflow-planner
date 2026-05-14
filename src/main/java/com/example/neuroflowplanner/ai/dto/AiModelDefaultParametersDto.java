package com.example.neuroflowplanner.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiModelDefaultParametersDto(
        Double temperature,
        @JsonProperty("top_p") Double topP,
        @JsonProperty("frequency_penalty") Double frequencyPenalty,
        @JsonProperty("presence_penalty") Double presencePenalty) {
}
