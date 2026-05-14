package com.example.neuroflowplanner.model.chatio;

public record ChatArchiveImportOptions(ChatArchiveImportConflictPolicy conflictPolicy) {
    public ChatArchiveImportOptions {
        if (conflictPolicy == null) {
            conflictPolicy = ChatArchiveImportConflictPolicy.KEEP_BOTH;
        }
    }

    public static ChatArchiveImportOptions defaults() {
        return new ChatArchiveImportOptions(ChatArchiveImportConflictPolicy.KEEP_BOTH);
    }
}
