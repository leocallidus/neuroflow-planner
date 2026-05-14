package com.example.neuroflowplanner.model.chatio;

import com.example.neuroflowplanner.model.ChatContextState;
import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;

import java.util.List;

public record ChatArchiveImportConversationPlan(
    ChatArchiveImportAction action,
    String sourceConversationId,
    String resolvedConversationId,
    String title,
    int messageCount,
    boolean titleCollision,
    int messageIdCollisionCount,
    List<String> warnings,
    ChatConversation conversationToPersist,
    List<ChatMessage> messagesToPersist,
    ChatContextState contextStateToPersist
) {
    public ChatArchiveImportConversationPlan {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (sourceConversationId == null || sourceConversationId.isBlank()) {
            throw new IllegalArgumentException("sourceConversationId is required");
        }
        if (resolvedConversationId == null || resolvedConversationId.isBlank()) {
            throw new IllegalArgumentException("resolvedConversationId is required");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        messagesToPersist = messagesToPersist == null ? List.of() : List.copyOf(messagesToPersist);
        if (messageCount < 0 || messageIdCollisionCount < 0) {
            throw new IllegalArgumentException("message counters must be >= 0");
        }
    }

    public boolean importable() {
        return action != ChatArchiveImportAction.SKIP;
    }
}
