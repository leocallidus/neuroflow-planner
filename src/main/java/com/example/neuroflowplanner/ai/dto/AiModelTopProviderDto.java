package com.example.neuroflowplanner.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiModelTopProviderDto(
        @JsonProperty("context_length") Integer contextLength,
        @JsonProperty("max_completion_tokens") Integer maxCompletionTokens,
        @JsonProperty("supported_parameters") List<String> supportedParameters,
        @JsonProperty("default_parameters") AiModelDefaultParametersDto defaultParameters) {
}
