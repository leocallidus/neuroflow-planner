package com.example.neuroflowplanner.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiModelsResponseDto(List<AiModelDescriptorDto> data, List<AiModelDescriptorDto> models) {
}
