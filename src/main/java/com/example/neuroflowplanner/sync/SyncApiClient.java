package com.example.neuroflowplanner.sync;

import java.util.List;

public final class SyncApiClient {
    private final SyncTransport transport;

    public SyncApiClient() {
        this(new SyncTransport());
    }

    public SyncApiClient(SyncTransport transport) {
        this.transport = transport;
    }

    public SyncPayloads.SyncBootstrapResponse bootstrap(String accessToken, int limit) {
        return transport.postJson(
                "/sync/bootstrap",
                new SyncPayloads.SyncBootstrapRequest(limit),
                accessToken,
                SyncPayloads.SyncBootstrapResponse.class);
    }

    public SyncPayloads.SyncPullResponse pull(String accessToken, long sinceChangeId, int limit) {
        return transport.postJson(
                "/sync/pull",
                new SyncPayloads.SyncPullRequest(sinceChangeId, limit),
                accessToken,
                SyncPayloads.SyncPullResponse.class);
    }

    public SyncPayloads.SyncPushResponse push(
            String accessToken,
            long sinceChangeId,
            int pullLimit,
            List<SyncPayloads.ClientSyncChange> changes) {
        return transport.postJson(
                "/sync/push",
                new SyncPayloads.SyncPushRequest(sinceChangeId, pullLimit, changes),
                accessToken,
                SyncPayloads.SyncPushResponse.class);
    }

    public boolean checkLiveness() {
        return transport.isReachable();
    }
}
