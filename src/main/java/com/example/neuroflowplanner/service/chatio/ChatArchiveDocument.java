package com.example.neuroflowplanner.service.chatio;

import java.util.List;
import java.util.Map;

public record ChatArchiveDocument(
    String schemaType,
    int schemaVersion,
    String exportedAt,
    Map<String, String> source,
    List<ChatArchiveBundle> conversations
) {
    public ChatArchiveDocument {
        if (schemaType == null || schemaType.isBlank()) {
            schemaType = "chat-archive";
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
        source = source == null ? Map.of() : Map.copyOf(source);
        conversations = conversations == null ? List.of() : List.copyOf(conversations);
    }
}
