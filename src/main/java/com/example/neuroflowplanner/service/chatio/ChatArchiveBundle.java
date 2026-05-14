package com.example.neuroflowplanner.service.chatio;

import com.example.neuroflowplanner.model.ChatContextState;
import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;

import java.util.List;

public record ChatArchiveBundle(
    ChatConversation conversation,
    List<ChatMessage> messages,
    ChatContextState contextState
) {
    public ChatArchiveBundle {
        if (conversation == null) {
            throw new IllegalArgumentException("conversation is required");
        }
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
