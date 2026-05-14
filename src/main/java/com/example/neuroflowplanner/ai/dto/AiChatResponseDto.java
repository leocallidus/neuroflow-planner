package com.example.neuroflowplanner.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiChatResponseDto(
        List<AiChatChoiceDto> choices,
        AiChatMessageDto message,
        String content,
        String response) {
}
