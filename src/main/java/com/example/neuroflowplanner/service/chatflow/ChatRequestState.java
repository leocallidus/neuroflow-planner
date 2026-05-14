package com.example.neuroflowplanner.service.chatflow;

/**
 * Lifecycle states for a chat AI request.
 */
public enum ChatRequestState {
    QUEUED,
    SENDING,
    SUMMARIZING,
    WAITING_PROVIDER,
    GENERATING,
    POST_PROCESSING,
    DONE,
    RETRYING,
    FALLBACK_MODEL,
    PARTIAL_DONE,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DONE
            || this == PARTIAL_DONE
            || this == FAILED
            || this == CANCELLED;
    }
}
