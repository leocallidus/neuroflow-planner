package com.example.neuroflowplanner.service.imageflow;

/**
 * Lifecycle states for an image generation request.
 */
public enum ImageRequestState {
    QUEUED,
    SENDING,
    PROVIDER_ACCEPTED,
    POLLING,
    DOWNLOADING,
    SAVING,
    DONE,
    RETRYING,
    FALLBACK_MODEL,
    RESUMING,
    PAUSED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DONE
            || this == FAILED
            || this == CANCELLED;
    }
}
