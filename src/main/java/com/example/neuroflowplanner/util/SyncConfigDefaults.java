package com.example.neuroflowplanner.util;

public final class SyncConfigDefaults {
    public static final String CONFIG_CLOUD_SYNC_BETA_ENABLED = "cloud.sync.beta.enabled";
    public static final String CONFIG_CLOUD_SYNC_BETA_ALLOWED_EMAILS = "cloud.sync.beta.allowedEmails";
    public static final String CONFIG_CLOUD_SYNC_ENABLED = "cloud.sync.enabled";
    public static final String CONFIG_CLOUD_SYNC_BASE_URL = "cloud.sync.baseUrl";
    public static final String CONFIG_CLOUD_SYNC_STARTUP_ENABLED = "cloud.sync.startup.enabled";
    public static final String CONFIG_CLOUD_SYNC_RECONNECT_ENABLED = "cloud.sync.reconnect.enabled";
    public static final String CONFIG_CLOUD_SYNC_CONNECT_TIMEOUT_MS = "cloud.sync.connectTimeoutMs";
    public static final String CONFIG_CLOUD_SYNC_REQUEST_TIMEOUT_MS = "cloud.sync.requestTimeoutMs";
    public static final String CONFIG_CLOUD_SYNC_PULL_LIMIT = "cloud.sync.pull.limit";
    public static final String CONFIG_CLOUD_SYNC_OUTBOX_BATCH_SIZE = "cloud.sync.outbox.batchSize";
    public static final String CONFIG_CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS = "cloud.sync.periodic.intervalSeconds";
    public static final String CONFIG_CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS = "cloud.sync.healthcheck.intervalSeconds";
    public static final String CONFIG_CLOUD_SYNC_RETRY_MAX_ATTEMPTS = "cloud.sync.retry.maxAttempts";
    public static final String CONFIG_CLOUD_SYNC_RETRY_BASE_DELAY_MS = "cloud.sync.retry.baseDelayMs";
    public static final String CONFIG_CLOUD_SYNC_RETRY_MAX_DELAY_MS = "cloud.sync.retry.maxDelayMs";
    public static final String CONFIG_CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD =
            "cloud.sync.circuitBreaker.failureThreshold";
    public static final String CONFIG_CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS =
            "cloud.sync.circuitBreaker.cooldownMs";

    public static final boolean CLOUD_SYNC_BETA_ENABLED_DEFAULT = true;
    public static final String CLOUD_SYNC_BETA_ALLOWED_EMAILS_DEFAULT = "";
    public static final boolean CLOUD_SYNC_ENABLED_DEFAULT = false;
    public static final String CLOUD_SYNC_BASE_URL_DEFAULT = "";
    public static final boolean CLOUD_SYNC_STARTUP_ENABLED_DEFAULT = true;
    public static final boolean CLOUD_SYNC_RECONNECT_ENABLED_DEFAULT = true;
    public static final long CLOUD_SYNC_CONNECT_TIMEOUT_MS_DEFAULT = 5_000L;
    public static final long CLOUD_SYNC_REQUEST_TIMEOUT_MS_DEFAULT = 15_000L;
    public static final int CLOUD_SYNC_PULL_LIMIT_DEFAULT = 200;
    public static final int CLOUD_SYNC_PULL_LIMIT_MIN = 1;
    public static final int CLOUD_SYNC_PULL_LIMIT_MAX = 2_000;
    public static final int CLOUD_SYNC_OUTBOX_BATCH_SIZE_DEFAULT = 100;
    public static final int CLOUD_SYNC_OUTBOX_BATCH_SIZE_MIN = 1;
    public static final int CLOUD_SYNC_OUTBOX_BATCH_SIZE_MAX = 1_000;
    public static final int CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS_DEFAULT = 300;
    public static final int CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS_MIN = 15;
    public static final int CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS_MAX = 86_400;
    public static final int CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS_DEFAULT = 30;
    public static final int CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS_MIN = 5;
    public static final int CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS_MAX = 3_600;
    public static final int CLOUD_SYNC_RETRY_MAX_ATTEMPTS_DEFAULT = 3;
    public static final int CLOUD_SYNC_RETRY_MAX_ATTEMPTS_MIN = 1;
    public static final int CLOUD_SYNC_RETRY_MAX_ATTEMPTS_MAX = 10;
    public static final long CLOUD_SYNC_RETRY_BASE_DELAY_MS_DEFAULT = 500L;
    public static final long CLOUD_SYNC_RETRY_BASE_DELAY_MS_MIN = 100L;
    public static final long CLOUD_SYNC_RETRY_BASE_DELAY_MS_MAX = 30_000L;
    public static final long CLOUD_SYNC_RETRY_MAX_DELAY_MS_DEFAULT = 5_000L;
    public static final long CLOUD_SYNC_RETRY_MAX_DELAY_MS_MIN = 500L;
    public static final long CLOUD_SYNC_RETRY_MAX_DELAY_MS_MAX = 60_000L;
    public static final int CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD_DEFAULT = 3;
    public static final int CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD_MIN = 1;
    public static final int CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD_MAX = 20;
    public static final long CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS_DEFAULT = 30_000L;
    public static final long CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS_MIN = 1_000L;
    public static final long CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS_MAX = 300_000L;

    private SyncConfigDefaults() {
    }
}
