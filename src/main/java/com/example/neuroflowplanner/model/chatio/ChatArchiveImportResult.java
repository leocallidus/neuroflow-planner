package com.example.neuroflowplanner.model.chatio;

import java.util.List;

public record ChatArchiveImportResult(
    ChatArchiveImportPreview preview,
    int importedConversationCount,
    int importedMessageCount,
    int skippedConversationCount,
    List<String> warnings
) {
    public ChatArchiveImportResult {
        if (preview == null) {
            throw new IllegalArgumentException("preview is required");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
