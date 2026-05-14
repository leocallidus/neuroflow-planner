package com.example.neuroflowplanner.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiChatMessageDto(String role, String content) {
}
