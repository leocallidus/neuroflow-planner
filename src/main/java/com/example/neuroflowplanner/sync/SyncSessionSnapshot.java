package com.example.neuroflowplanner.sync;

public record SyncSessionSnapshot(
        String userId,
        String email,
        String displayName,
        String deviceId,
        String refreshSessionId,
        boolean linked) {
}
