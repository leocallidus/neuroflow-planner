package com.example.neuroflowplanner.sync;

public record SyncHealthEvent(
        String occurredAt,
        String category,
        String title,
        String detail,
        boolean failure,
        boolean deferred) {
}
