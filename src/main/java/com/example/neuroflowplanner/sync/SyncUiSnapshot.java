package com.example.neuroflowplanner.sync;

public record SyncUiSnapshot(
        SyncUiStatus status,
        boolean authenticated,
        boolean syncEnabled,
        boolean strategyRequired,
        String accountEmail,
        String displayName,
        String baseUrl,
        AccountLinkStrategy selectedStrategy,
        LocalSyncProfileSummary localSummary,
        int remotePreviewChangeCount,
        String statusMessage,
        String detailMessage,
        String rolloutMessage,
        String diagnosticsMessage,
        String lastSyncAt,
        String lastErrorSummary) {

    public static SyncUiSnapshot initial(String baseUrl) {
        return new SyncUiSnapshot(
                SyncUiStatus.SIGNED_OUT,
                false,
                false,
                false,
                "",
                "",
                safe(baseUrl),
                null,
                new LocalSyncProfileSummary(0, 0, 0, 0, 0, 0, 0),
                0,
                "Не подключено",
                "Войдите в аккаунт, чтобы включить облачную синхронизацию.",
                "",
                "",
                "",
                "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
