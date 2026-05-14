package com.example.neuroflowplanner.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiModelDescriptorDto(
        String id,
        String name,
        String type,
        AiModelArchitectureDto architecture,
        @JsonProperty("top_provider") AiModelTopProviderDto topProvider,
        @JsonProperty("input_modalities") List<String> inputModalities,
        @JsonProperty("output_modalities") List<String> outputModalities) {
}
