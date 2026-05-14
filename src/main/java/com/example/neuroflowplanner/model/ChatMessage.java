package com.example.neuroflowplanner.model;

public class ChatMessage {
    private final String id;
    private final String conversationId;
    private final String role;
    private final String content;
    private final int seq;
    private final String createdAt;

    public ChatMessage(String id, String conversationId, String role, String content, int seq, String createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.seq = seq;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public int getSeq() {
        return seq;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
