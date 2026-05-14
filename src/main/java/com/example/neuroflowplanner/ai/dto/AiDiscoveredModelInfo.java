package com.example.neuroflowplanner.ai.dto;

public record AiDiscoveredModelInfo(
        String id,
        String type,
        boolean multimodal,
        boolean supportsImageInput,
        boolean supportsAudioInput,
        boolean supportsFileInput,
        AiTextModelContextMetadata textContextMetadata,
        AiTextModelParameterMetadata textParameterMetadata) {

    public AiDiscoveredModelInfo(
            String id,
            String type,
            boolean multimodal,
            boolean supportsImageInput,
            boolean supportsAudioInput,
            boolean supportsFileInput) {
        this(id, type, multimodal, supportsImageInput, supportsAudioInput, supportsFileInput, null, null);
    }

    public AiDiscoveredModelInfo(
            String id,
            String type,
            boolean multimodal,
            boolean supportsImageInput,
            boolean supportsAudioInput,
            boolean supportsFileInput,
            AiTextModelParameterMetadata textParameterMetadata) {
        this(id, type, multimodal, supportsImageInput, supportsAudioInput, supportsFileInput, null, textParameterMetadata);
    }
}
