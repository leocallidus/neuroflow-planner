package com.example.neuroflowplanner.sync;

import java.util.List;

public record SyncRunResult(
        SyncTrigger trigger,
        String status,
        int attemptedChanges,
        int acceptedChanges,
        int remoteChangeCount,
        long appliedCursor,
        long latestKnownChangeId,
        boolean hasMoreRemoteChanges,
        int roundsPerformed,
        List<SyncPayloads.ServerSyncChange> remoteChanges) {

    public static SyncRunResult skipped(SyncTrigger trigger, String status) {
        return new SyncRunResult(trigger, status, 0, 0, 0, 0L, 0L, false, 0, List.of());
    }
}
