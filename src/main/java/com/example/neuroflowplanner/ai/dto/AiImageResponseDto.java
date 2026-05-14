package com.example.neuroflowplanner.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiImageResponseDto(
        String id,
        String requestId,
        String status,
        String state,
        String resultUrl,
        String imageUrl,
        String outputUrl,
        String url,
        JsonNode data) {
}
