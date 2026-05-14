package com.example.neuroflowplanner.ai.json;

import java.util.List;

public record AiSchemaValidationResult(boolean valid, List<String> messages) {

    public AiSchemaValidationResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static AiSchemaValidationResult ok() {
        return new AiSchemaValidationResult(true, List.of());
    }

    public static AiSchemaValidationResult invalid(List<String> messages) {
        return new AiSchemaValidationResult(false, messages == null ? List.of("Schema validation failed.") : messages);
    }
}
