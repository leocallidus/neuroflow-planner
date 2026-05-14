package com.example.neuroflowplanner.model.chatio;

import com.example.neuroflowplanner.service.chatio.ChatArchiveFormat;

import java.util.List;

public record ChatArchiveImportPreview(
    ChatArchiveFormat format,
    ChatArchiveImportOptions options,
    int sourceCount,
    int acceptedCount,
    int newConversationCount,
    int conflictingConversationCount,
    int titleCollisionCount,
    int messageIdCollisionCount,
    int messageCount,
    int skippedConversationCount,
    List<String> warnings,
    List<ChatArchiveImportConversationPlan> conversationPlans
) {
    public ChatArchiveImportPreview {
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }
        if (options == null) {
            options = ChatArchiveImportOptions.defaults();
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        conversationPlans = conversationPlans == null ? List.of() : List.copyOf(conversationPlans);
    }

    public int importableConversationCount() {
        return acceptedCount;
    }

    public boolean hasChanges() {
        return acceptedCount > 0;
    }
}
