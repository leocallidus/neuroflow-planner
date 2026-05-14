package com.example.neuroflowplanner.util;

import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewPersistenceRecord;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockCandidate;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockExplanation;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockPersistenceRecord;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockReason;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRecommendation;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRisk;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRiskLevel;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockSummarySource;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockType;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityPersistenceRecord;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityRecommendation;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityRisk;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityRiskSeverity;
import com.example.neuroflowplanner.service.planningquality.PlanningQualitySummary;
import com.example.neuroflowplanner.service.planningquality.PlanningQualitySummarySource;
import com.example.neuroflowplanner.service.planningquality.RescheduleRateMetric;
import com.example.neuroflowplanner.service.planningquality.RhythmStabilityBand;
import com.example.neuroflowplanner.service.planningquality.RhythmStabilityMetric;
import com.example.neuroflowplanner.service.planningquality.TimeEstimateAccuracyMetric;
import com.example.neuroflowplanner.sync.SyncSecretIds;
import com.example.neuroflowplanner.util.secrets.EnvSecretProvider;
import com.example.neuroflowplanner.util.secrets.KeychainSecretProvider;
import com.example.neuroflowplanner.util.secrets.LegacyConfigSecretProvider;
import com.example.neuroflowplanner.util.secrets.SecretProvider;
import com.example.neuroflowplanner.util.secrets.SecretResolver;
import com.example.neuroflowplanner.ai.json.AiJsonParserMode;
import com.example.neuroflowplanner.ai.json.AiJsonParserModeResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.neuroflowplanner.service.task.TaskDependencyMode;
import com.example.neuroflowplanner.service.task.TaskDependencyModeResolver;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewFocusRecommendation;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewFreeWindow;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewOverdueItem;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.Arrays;
import java.util.Locale;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.neuroflowplanner.ai.resilience.AiConcurrencyLimiter;
import com.example.neuroflowplanner.ai.resilience.AiResiliencePolicy;
import com.example.neuroflowplanner.ai.resilience.AiRetryDelayStrategy;
import com.example.neuroflowplanner.ai.resilience.AiRetryPolicy;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewSummary;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewSummarySource;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewUpcomingItem;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewWindowSuitability;

public class ConfigManager {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ConfigManager.class);
    private static final ObjectMapper CONFIG_MAPPER = AiObjectMapperFactory.createMapper(false);
    private static final String CONFIG_APP_THEME_DARK = "app.theme.dark";

    public static final String CONFIG_API_KEY = "api.key";
    public static final String CONFIG_EXTERNAL_API_KEY = "external.api.key";
    public static final String CONFIG_CLOUD_SYNC_REFRESH_TOKEN = SyncSecretIds.CONFIG_CLOUD_SYNC_REFRESH_TOKEN;

    public static final String ENV_EXTERNAL_API_KEY = EnvSecretProvider.ENV_EXTERNAL_API_KEY;
    public static final String ENV_LEGACY_API_KEY = EnvSecretProvider.ENV_LEGACY_API_KEY;
    public static final String ENV_CLOUD_SYNC_REFRESH_TOKEN = EnvSecretProvider.ENV_CLOUD_SYNC_REFRESH_TOKEN;
    public static final String PROP_DISABLE_OS_SECRET_PROVIDERS = "neuroflow.test.disableOsSecretProviders";

    private static final Set<String> SECRET_KEYS = Set.of(
            CONFIG_API_KEY,
            CONFIG_EXTERNAL_API_KEY,
            CONFIG_CLOUD_SYNC_REFRESH_TOKEN);
    private static final Set<String> LEGACY_UX_ROLLOUT_KEYS = Set.of(
            "ux.undo.enabled",
            "ux.search.global.enabled",
            "ux.commandPalette.enabled",
            "ux.shortcuts.enabled",
            "ux.layout.adaptive.enabled",
            "ux.layout.obsidianInspired.enabled",
            "ux.layout.compact.autoCollapseRightPanel",
            "ux.sidebar.v2.enabled",
            "ux.sidebar.filter.enabled",
            "ux.sidebar.favorites.enabled",
            "ux.sidebar.recent.enabled");

    private static final Properties properties = new Properties();
    private static final BooleanProperty darkThemeProperty = new SimpleBooleanProperty(false);
    private static final LongProperty configRevisionProperty = new SimpleLongProperty(0L);
    private static Path configPath;
    private static SecretResolver secretResolver;

    static {
        initConfig();
    }

    public static synchronized void resetForTesting() {
        properties.clear();
        DataPathManager.reinitializeForTesting();
        initConfig();
    }

    private static void initConfig() {
        // Initialize data directory and config path
        DataPathManager.initConfigIfNeeded();
        configPath = DataPathManager.getConfigPath();
        loadProperties();
        initSecretResolver();
    }

    private static void loadProperties() {
        // First, load defaults from resources
        try (InputStream defaultProps = ConfigManager.class.getResourceAsStream("/config.properties")) {
            if (defaultProps != null) {
                properties.load(defaultProps);
            }
        } catch (IOException e) {
            LOG.warning("config.default.load.failed", "resource", "/config.properties");
        }

        // Then override with user config if exists
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                properties.load(in);
            } catch (IOException e) {
                LOG.error("config.user.load.failed", e, "configPath", configPath);
            }
        }
        normalizeLegacyUxRolloutProperties();
        darkThemeProperty.set(Boolean.parseBoolean(properties.getProperty(CONFIG_APP_THEME_DARK, "false")));
    }

    public static String getProperty(String key) {
        if (key == null) {
            return null;
        }

        if (isSecretKey(key)) {
            return resolveSecret(key);
        }

        return properties.getProperty(key);
    }

    public static void setProperty(String key, String value) {
        if (key == null) {
            return;
        }

        if (isSecretKey(key)) {
            setSecretProperty(key, value);
            return;
        }

        properties.setProperty(key, value == null ? "" : value);
        if (CONFIG_APP_THEME_DARK.equals(key)) {
            darkThemeProperty.set(Boolean.parseBoolean(value == null ? "false" : value));
        }
        saveProperties();
        notifyConfigChanged();
    }

    private static void saveProperties() {
        Properties persisted = new Properties();
        persisted.putAll(properties);

        // Never persist API secrets in config file.
        for (String secretKey : SECRET_KEYS) {
            persisted.setProperty(secretKey, "");
        }

        try (OutputStream out = Files.newOutputStream(configPath)) {
            persisted.store(out, "NeuroFlow Planner Configuration");
        } catch (IOException e) {
            LOG.error("config.save.failed", e, "configPath", configPath);
        }
    }

    public static boolean isDarkTheme() {
        return darkThemeProperty.get();
    }

    public static void setDarkTheme(boolean isDark) {
        setProperty(CONFIG_APP_THEME_DARK, String.valueOf(isDark));
    }

    public static ReadOnlyBooleanProperty darkThemeProperty() {
        return darkThemeProperty;
    }

    public static ReadOnlyLongProperty configRevisionProperty() {
        return configRevisionProperty;
    }

    public static boolean isChatContextPanelExpanded() {
        return getBooleanProperty(
                AiConfigDefaults.CONFIG_CHAT_CONTEXT_PANEL_EXPANDED,
                AiConfigDefaults.DEFAULT_CHAT_CONTEXT_PANEL_EXPANDED);
    }

    public static void setChatContextPanelExpanded(boolean expanded) {
        setProperty(AiConfigDefaults.CONFIG_CHAT_CONTEXT_PANEL_EXPANDED, String.valueOf(expanded));
    }

    /**
     * Get the path to the data directory for display purposes.
     */
    public static String getDataDirectoryPath() {
        return DataPathManager.getDataDirectory().toAbsolutePath().toString();
    }

    public static String getImagesDirectoryPath() {
        return DataPathManager.getImagesDirectory().toAbsolutePath().toString();
    }

    public static String getChatUploadsDirectoryPath() {
        return DataPathManager.getChatUploadsDirectory().toAbsolutePath().toString();
    }

    public static AiJsonParserMode getAiJsonParserMode() {
        String rawMode = properties.getProperty(
                AiConfigDefaults.CONFIG_AI_JSON_PARSER_MODE,
                AiConfigDefaults.JSON_PARSER_MODE_DEFAULT);
        return AiJsonParserModeResolver.resolve(rawMode);
    }

    public static boolean isAiJsonSchemaValidationEnabled() {
        return getBooleanProperty(
                AiConfigDefaults.CONFIG_AI_JSON_SCHEMA_VALIDATION_ENABLED,
                AiConfigDefaults.JSON_SCHEMA_VALIDATION_ENABLED);
    }

    public static boolean isAiJsonProviderFailOnUnknownProperties() {
        return getBooleanProperty(
                AiConfigDefaults.CONFIG_AI_JSON_FAIL_ON_UNKNOWN_PROVIDER_PROPERTIES,
                AiConfigDefaults.JSON_FAIL_ON_UNKNOWN_PROVIDER_PROPERTIES);
    }

    public static boolean isAiJsonUiFailOnUnknownProperties() {
        return getBooleanProperty(
                AiConfigDefaults.CONFIG_AI_JSON_FAIL_ON_UNKNOWN_UI_PROPERTIES,
                AiConfigDefaults.JSON_FAIL_ON_UNKNOWN_UI_PROPERTIES);
    }

    public static TaskDependencyMode getTaskDependencyMode() {
        String rawMode = properties.getProperty(
                TaskDependencyConfigDefaults.CONFIG_TASK_DEPENDENCIES_MODE,
                TaskDependencyConfigDefaults.TASK_DEPENDENCIES_MODE_DEFAULT);
        return TaskDependencyModeResolver.resolve(rawMode);
    }

    public static String getDbBulkWritesMode() {
        String rawMode = properties.getProperty(
                DbWriteConfigDefaults.CONFIG_DB_BULK_WRITES_MODE,
                DbWriteConfigDefaults.DB_BULK_WRITES_MODE_DEFAULT);
        if (rawMode == null) {
            return DbWriteConfigDefaults.DB_BULK_WRITES_MODE_DEFAULT;
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case DbWriteConfigDefaults.MODE_LEGACY,
                    DbWriteConfigDefaults.MODE_TRANSACTIONAL,
                    DbWriteConfigDefaults.MODE_BATCHED -> normalized;
            default -> DbWriteConfigDefaults.DB_BULK_WRITES_MODE_DEFAULT;
        };
    }

    public static int getDbBulkBatchSize() {
        int configured = getIntProperty(
                DbWriteConfigDefaults.CONFIG_DB_BULK_BATCH_SIZE,
                DbWriteConfigDefaults.DB_BULK_BATCH_SIZE_DEFAULT);
        if (configured < DbWriteConfigDefaults.DB_BULK_BATCH_SIZE_MIN) {
            return DbWriteConfigDefaults.DB_BULK_BATCH_SIZE_MIN;
        }
        if (configured > DbWriteConfigDefaults.DB_BULK_BATCH_SIZE_MAX) {
            return DbWriteConfigDefaults.DB_BULK_BATCH_SIZE_MAX;
        }
        return configured;
    }

    public static boolean isCloudSyncEnabled() {
        return getBooleanProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED,
                SyncConfigDefaults.CLOUD_SYNC_ENABLED_DEFAULT);
    }

    public static String getCloudSyncBaseUrl() {
        String value = properties.getProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_BASE_URL,
                SyncConfigDefaults.CLOUD_SYNC_BASE_URL_DEFAULT);
        if (value == null) {
            return null;
        }
        String trimmed = CloudSyncUrlSupport.normalizeBaseUrl(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean isCloudSyncStartupEnabled() {
        return getBooleanProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_STARTUP_ENABLED,
                SyncConfigDefaults.CLOUD_SYNC_STARTUP_ENABLED_DEFAULT);
    }

    public static boolean isCloudSyncReconnectEnabled() {
        return getBooleanProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_RECONNECT_ENABLED,
                SyncConfigDefaults.CLOUD_SYNC_RECONNECT_ENABLED_DEFAULT);
    }

    public static Duration getCloudSyncConnectTimeout() {
        long millis = getLongProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_CONNECT_TIMEOUT_MS,
                SyncConfigDefaults.CLOUD_SYNC_CONNECT_TIMEOUT_MS_DEFAULT);
        millis = Math.max(100L, millis);
        return Duration.ofMillis(millis);
    }

    public static boolean isCloudSyncBetaEnabled() {
        return getBooleanProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_BETA_ENABLED,
                SyncConfigDefaults.CLOUD_SYNC_BETA_ENABLED_DEFAULT);
    }

    public static List<String> getCloudSyncBetaAllowedEmails() {
        String raw = properties.getProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_BETA_ALLOWED_EMAILS,
                SyncConfigDefaults.CLOUD_SYNC_BETA_ALLOWED_EMAILS_DEFAULT);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (String token : raw.split("\\s*,\\s*")) {
            String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                emails.add(normalized);
            }
        }
        return List.copyOf(emails);
    }

    public static Duration getCloudSyncRequestTimeout() {
        long millis = getLongProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_REQUEST_TIMEOUT_MS,
                SyncConfigDefaults.CLOUD_SYNC_REQUEST_TIMEOUT_MS_DEFAULT);
        millis = Math.max(500L, millis);
        return Duration.ofMillis(millis);
    }

    public static int getCloudSyncPullLimit() {
        int configured = getIntProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_PULL_LIMIT,
                SyncConfigDefaults.CLOUD_SYNC_PULL_LIMIT_DEFAULT);
        if (configured < SyncConfigDefaults.CLOUD_SYNC_PULL_LIMIT_MIN) {
            return SyncConfigDefaults.CLOUD_SYNC_PULL_LIMIT_MIN;
        }
        if (configured > SyncConfigDefaults.CLOUD_SYNC_PULL_LIMIT_MAX) {
            return SyncConfigDefaults.CLOUD_SYNC_PULL_LIMIT_MAX;
        }
        return configured;
    }

    public static int getCloudSyncOutboxBatchSize() {
        int configured = getIntProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_OUTBOX_BATCH_SIZE,
                SyncConfigDefaults.CLOUD_SYNC_OUTBOX_BATCH_SIZE_DEFAULT);
        if (configured < SyncConfigDefaults.CLOUD_SYNC_OUTBOX_BATCH_SIZE_MIN) {
            return SyncConfigDefaults.CLOUD_SYNC_OUTBOX_BATCH_SIZE_MIN;
        }
        if (configured > SyncConfigDefaults.CLOUD_SYNC_OUTBOX_BATCH_SIZE_MAX) {
            return SyncConfigDefaults.CLOUD_SYNC_OUTBOX_BATCH_SIZE_MAX;
        }
        return configured;
    }

    public static Duration getCloudSyncPeriodicInterval() {
        int seconds = getIntProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS,
                SyncConfigDefaults.CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS_DEFAULT);
        if (seconds < SyncConfigDefaults.CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS_MIN) {
            seconds = SyncConfigDefaults.CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS_MIN;
        }
        if (seconds > SyncConfigDefaults.CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS_MAX) {
            seconds = SyncConfigDefaults.CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS_MAX;
        }
        return Duration.ofSeconds(seconds);
    }

    public static Duration getCloudSyncHealthcheckInterval() {
        int seconds = getIntProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS,
                SyncConfigDefaults.CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS_DEFAULT);
        if (seconds < SyncConfigDefaults.CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS_MIN) {
            seconds = SyncConfigDefaults.CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS_MIN;
        }
        if (seconds > SyncConfigDefaults.CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS_MAX) {
            seconds = SyncConfigDefaults.CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS_MAX;
        }
        return Duration.ofSeconds(seconds);
    }

    public static int getCloudSyncRetryMaxAttempts() {
        int configured = getIntProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_RETRY_MAX_ATTEMPTS,
                SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_ATTEMPTS_DEFAULT);
        if (configured < SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_ATTEMPTS_MIN) {
            return SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_ATTEMPTS_MIN;
        }
        if (configured > SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_ATTEMPTS_MAX) {
            return SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_ATTEMPTS_MAX;
        }
        return configured;
    }

    public static Duration getCloudSyncRetryBaseDelay() {
        long millis = getLongProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_RETRY_BASE_DELAY_MS,
                SyncConfigDefaults.CLOUD_SYNC_RETRY_BASE_DELAY_MS_DEFAULT);
        if (millis < SyncConfigDefaults.CLOUD_SYNC_RETRY_BASE_DELAY_MS_MIN) {
            millis = SyncConfigDefaults.CLOUD_SYNC_RETRY_BASE_DELAY_MS_MIN;
        }
        if (millis > SyncConfigDefaults.CLOUD_SYNC_RETRY_BASE_DELAY_MS_MAX) {
            millis = SyncConfigDefaults.CLOUD_SYNC_RETRY_BASE_DELAY_MS_MAX;
        }
        return Duration.ofMillis(millis);
    }

    public static Duration getCloudSyncRetryMaxDelay() {
        long millis = getLongProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_RETRY_MAX_DELAY_MS,
                SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_DELAY_MS_DEFAULT);
        if (millis < SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_DELAY_MS_MIN) {
            millis = SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_DELAY_MS_MIN;
        }
        if (millis > SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_DELAY_MS_MAX) {
            millis = SyncConfigDefaults.CLOUD_SYNC_RETRY_MAX_DELAY_MS_MAX;
        }
        return Duration.ofMillis(millis);
    }

    public static int getCloudSyncCircuitBreakerFailureThreshold() {
        int configured = getIntProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD,
                SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD_DEFAULT);
        if (configured < SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD_MIN) {
            return SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD_MIN;
        }
        if (configured > SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD_MAX) {
            return SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD_MAX;
        }
        return configured;
    }

    public static Duration getCloudSyncCircuitBreakerCooldown() {
        long millis = getLongProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS,
                SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS_DEFAULT);
        if (millis < SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS_MIN) {
            millis = SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS_MIN;
        }
        if (millis > SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS_MAX) {
            millis = SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS_MAX;
        }
        return Duration.ofMillis(millis);
    }

    public static boolean isUxUndoEnabled() {
        return true;
    }

    public static int getUxUndoMaxHistory() {
        int configured = getIntProperty(
                UxConfigDefaults.CONFIG_UX_UNDO_MAX_HISTORY,
                UxConfigDefaults.UX_UNDO_MAX_HISTORY_DEFAULT);
        if (configured < UxConfigDefaults.UX_UNDO_MAX_HISTORY_MIN) {
            return UxConfigDefaults.UX_UNDO_MAX_HISTORY_MIN;
        }
        if (configured > UxConfigDefaults.UX_UNDO_MAX_HISTORY_MAX) {
            return UxConfigDefaults.UX_UNDO_MAX_HISTORY_MAX;
        }
        return configured;
    }

    public static boolean isUxGlobalSearchEnabled() {
        return true;
    }

    public static boolean isUxCommandPaletteEnabled() {
        return true;
    }

    public static boolean isUxShortcutsEnabled() {
        return true;
    }

    public static boolean isUxAdaptiveLayoutEnabled() {
        return true;
    }

    public static boolean isUxObsidianInspiredLayoutEnabled() {
        return true;
    }

    public static String getUxLayoutDensityMode() {
        String raw = properties.getProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
                UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_DEFAULT);
        return normalizeUxLayoutDensityMode(raw);
    }

    public static void setUxLayoutDensityMode(String mode) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
                normalizeUxLayoutDensityMode(mode));
    }

    public static boolean isUxLayoutCompactAutoCollapseRightPanelEnabled() {
        return true;
    }

    public static boolean isUxLayoutStateLeftPanelCollapsed() {
        return getBooleanProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED,
                UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED_DEFAULT);
    }

    public static void setUxLayoutStateLeftPanelCollapsed(boolean collapsed) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED,
                Boolean.toString(collapsed));
    }

    public static boolean isUxLayoutStateRightPanelCollapsed() {
        return getBooleanProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED,
                UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED_DEFAULT);
    }

    public static void setUxLayoutStateRightPanelCollapsed(boolean collapsed) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED,
                Boolean.toString(collapsed));
    }

    public static double getUxLayoutStateLeftPanelWidth() {
        double configured = getDoubleProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_WIDTH,
                UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_DEFAULT);
        return clampDouble(
                configured,
                UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_MIN,
                UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_MAX);
    }

    public static void setUxLayoutStateLeftPanelWidth(double width) {
        double normalized = clampDouble(
                width,
                UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_MIN,
                UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_MAX);
        setProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_WIDTH,
                formatDoublePropertyValue(normalized));
    }

    public static double getUxLayoutStateRightPanelWidth() {
        double configured = getDoubleProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH,
                UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_DEFAULT);
        return clampDouble(
                configured,
                UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MIN,
                UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX);
    }

    public static void setUxLayoutStateRightPanelWidth(double width) {
        double normalized = clampDouble(
                width,
                UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MIN,
                UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX);
        setProperty(
                UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH,
                formatDoublePropertyValue(normalized));
    }

    public static Set<String> getUxRightPanelExpandedSectionIds() {
        return parseStringSetProperty(
                UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS,
                UxConfigDefaults.UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS_DEFAULT);
    }

    public static void setUxRightPanelExpandedSectionIds(Set<String> sectionIds) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS,
                encodeStringCollection(sectionIds));
    }

    public static String getUxRightPanelInspectorActiveTab() {
        String raw = properties.getProperty(
                UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB,
                UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT);
        if (raw == null || raw.isBlank()) {
            return UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "properties", "description", "analytics" -> normalized;
            default -> UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT;
        };
    }

    public static void setUxRightPanelInspectorActiveTab(String tabId) {
        String normalized = tabId == null || tabId.isBlank()
                ? UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT
                : tabId.trim().toLowerCase(Locale.ROOT);
        if (!"properties".equals(normalized)
                && !"description".equals(normalized)
                && !"analytics".equals(normalized)) {
            normalized = UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT;
        }
        setProperty(
                UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB,
                normalized);
    }

    public static Set<String> getUxRightPanelInspectorExpandedSubstateIds() {
        return parseStringSetProperty(
                UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES,
                UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES_DEFAULT);
    }

    public static void setUxRightPanelInspectorExpandedSubstateIds(Set<String> substateIds) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES,
                encodeStringCollection(substateIds));
    }

    public static String getUxInlineOverlayActiveTabId() {
        String raw = properties.getProperty(
                UxConfigDefaults.CONFIG_UX_INLINE_OVERLAY_STATE_ACTIVE_TAB,
                UxConfigDefaults.UX_INLINE_OVERLAY_STATE_ACTIVE_TAB_DEFAULT);
        String normalized = normalizeCollectionValue(raw);
        if (normalized == null
                || UxConfigDefaults.UX_COLLECTION_NONE_MARKER.equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    public static void setUxInlineOverlayActiveTabId(String tabId) {
        String normalized = normalizeCollectionValue(tabId);
        setProperty(
                UxConfigDefaults.CONFIG_UX_INLINE_OVERLAY_STATE_ACTIVE_TAB,
                normalized == null
                        ? UxConfigDefaults.UX_INLINE_OVERLAY_STATE_ACTIVE_TAB_DEFAULT
                        : normalized);
    }

    public static List<String> getUxInlineOverlayTabOrder() {
        return parseStringListProperty(
                UxConfigDefaults.CONFIG_UX_INLINE_OVERLAY_STATE_TAB_ORDER,
                UxConfigDefaults.UX_INLINE_OVERLAY_STATE_TAB_ORDER_DEFAULT);
    }

    public static void setUxInlineOverlayTabOrder(List<String> tabOrder) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_INLINE_OVERLAY_STATE_TAB_ORDER,
                encodeStringCollection(tabOrder));
    }

    public static boolean isUxSidebarV2Enabled() {
        return true;
    }

    public static boolean isUxSidebarFilterEnabled() {
        return true;
    }

    public static boolean isUxSidebarFavoritesEnabled() {
        return true;
    }

    public static boolean isUxSidebarRecentEnabled() {
        return true;
    }

    public static int getUxSidebarMaxQuickItems() {
        int configured = getIntProperty(
                UxConfigDefaults.CONFIG_UX_SIDEBAR_MAX_QUICK_ITEMS,
                UxConfigDefaults.UX_SIDEBAR_MAX_QUICK_ITEMS_DEFAULT);
        if (configured < UxConfigDefaults.UX_SIDEBAR_MAX_QUICK_ITEMS_MIN) {
            return UxConfigDefaults.UX_SIDEBAR_MAX_QUICK_ITEMS_MIN;
        }
        if (configured > UxConfigDefaults.UX_SIDEBAR_MAX_QUICK_ITEMS_MAX) {
            return UxConfigDefaults.UX_SIDEBAR_MAX_QUICK_ITEMS_MAX;
        }
        return configured;
    }

    public static int getUxSidebarMaxFavorites() {
        int configured = getIntProperty(
                UxConfigDefaults.CONFIG_UX_SIDEBAR_MAX_FAVORITES,
                UxConfigDefaults.UX_SIDEBAR_MAX_FAVORITES_DEFAULT);
        if (configured < UxConfigDefaults.UX_SIDEBAR_MAX_FAVORITES_MIN) {
            return UxConfigDefaults.UX_SIDEBAR_MAX_FAVORITES_MIN;
        }
        if (configured > UxConfigDefaults.UX_SIDEBAR_MAX_FAVORITES_MAX) {
            return UxConfigDefaults.UX_SIDEBAR_MAX_FAVORITES_MAX;
        }
        return configured;
    }

    public static int getUxSidebarMaxRecent() {
        int configured = getIntProperty(
                UxConfigDefaults.CONFIG_UX_SIDEBAR_MAX_RECENT,
                UxConfigDefaults.UX_SIDEBAR_MAX_RECENT_DEFAULT);
        if (configured < UxConfigDefaults.UX_SIDEBAR_MAX_RECENT_MIN) {
            return UxConfigDefaults.UX_SIDEBAR_MAX_RECENT_MIN;
        }
        if (configured > UxConfigDefaults.UX_SIDEBAR_MAX_RECENT_MAX) {
            return UxConfigDefaults.UX_SIDEBAR_MAX_RECENT_MAX;
        }
        return configured;
    }

    public static Set<String> getUxSidebarExpandedSectionIds() {
        return parseStringSetProperty(
                UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_EXPANDED_SECTIONS,
                UxConfigDefaults.UX_SIDEBAR_STATE_EXPANDED_SECTIONS_DEFAULT);
    }

    public static void setUxSidebarExpandedSectionIds(Set<String> sectionIds) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_EXPANDED_SECTIONS,
                encodeStringCollection(sectionIds));
    }

    public static Set<String> getUxSidebarFavoriteActionIds() {
        return parseStringSetProperty(
                UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_FAVORITES,
                UxConfigDefaults.UX_SIDEBAR_STATE_FAVORITES_DEFAULT);
    }

    public static void setUxSidebarFavoriteActionIds(Set<String> actionIds) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_FAVORITES,
                encodeStringCollection(actionIds));
    }

    public static List<String> getUxSidebarRecentActionIds() {
        return parseStringListProperty(
                UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_RECENT,
                UxConfigDefaults.UX_SIDEBAR_STATE_RECENT_DEFAULT);
    }

    public static void setUxSidebarRecentActionIds(List<String> actionIds) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_RECENT,
                encodeStringCollection(actionIds));
    }

    public static Set<String> getUxNavSurfaceCompactedZones() {
        return parseStringSetProperty(
                UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_COMPACTED_ZONES,
                UxConfigDefaults.UX_NAV_SURFACES_STATE_COMPACTED_ZONES_DEFAULT);
    }

    public static void setUxNavSurfaceCompactedZones(Set<String> zoneIds) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_COMPACTED_ZONES,
                encodeStringCollection(zoneIds));
    }

    public static String getUxCommandPaletteLastViewMode() {
        String rawMode = properties.getProperty(
                UxConfigDefaults.CONFIG_UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE,
                UxConfigDefaults.UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE_DEFAULT);
        if (rawMode == null || rawMode.isBlank()) {
            return UxConfigDefaults.UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE_DEFAULT;
        }
        return rawMode.trim().toLowerCase(Locale.ROOT);
    }

    public static void setUxCommandPaletteLastViewMode(String mode) {
        String normalized = mode == null || mode.isBlank()
                ? UxConfigDefaults.UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE_DEFAULT
                : mode.trim().toLowerCase(Locale.ROOT);
        setProperty(
                UxConfigDefaults.CONFIG_UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE,
                normalized);
    }

    public static Set<String> getUxNavSurfaceDismissedHelperHintIds() {
        return parseStringSetProperty(
                UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_DISMISSED_HINTS,
                UxConfigDefaults.UX_NAV_SURFACES_STATE_DISMISSED_HINTS_DEFAULT);
    }

    public static void setUxNavSurfaceDismissedHelperHintIds(Set<String> hintIds) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_NAV_SURFACES_STATE_DISMISSED_HINTS,
                encodeStringCollection(hintIds));
    }

    public static String getUxTwoTierSidebarActiveRailDomain() {
        String raw = properties.getProperty(
                UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN,
                UxConfigDefaults.UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN_DEFAULT);
        if (raw == null || raw.isBlank()) {
            return UxConfigDefaults.UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN_DEFAULT;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static void setUxTwoTierSidebarActiveRailDomain(String domainId) {
        String normalized = domainId == null || domainId.isBlank()
                ? UxConfigDefaults.UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN_DEFAULT
                : domainId.trim().toLowerCase(Locale.ROOT);
        setProperty(
                UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN,
                normalized);
    }

    public static boolean isUxTwoTierSidebarContextCollapsed() {
        return getBooleanProperty(
                UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED,
                UxConfigDefaults.UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED_DEFAULT);
    }

    public static void setUxTwoTierSidebarContextCollapsed(boolean collapsed) {
        setProperty(
                UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED,
                String.valueOf(collapsed));
    }

    public static AiResiliencePolicy getAiResiliencePolicy() {
        long connectTimeoutMs = getLongProperty("ai.request.connectTimeoutMs",
                AiConfigDefaults.REQUEST_CONNECT_TIMEOUT_MS);
        long readTimeoutMs = getLongProperty("ai.request.readTimeoutMs", AiConfigDefaults.REQUEST_READ_TIMEOUT_MS);
        long requestBudgetMs = getLongProperty("ai.request.totalBudgetMs", AiConfigDefaults.REQUEST_TOTAL_BUDGET_MS);

        int maxAttempts = getIntProperty("ai.retry.maxAttempts", AiConfigDefaults.RETRY_MAX_ATTEMPTS);
        long baseDelayMs = getLongProperty("ai.retry.baseDelayMs", AiConfigDefaults.RETRY_BASE_DELAY_MS);
        long maxDelayMs = getLongProperty("ai.retry.maxDelayMs", AiConfigDefaults.RETRY_MAX_DELAY_MS);
        double jitterRatio = getDoubleProperty("ai.retry.jitterRatio", AiConfigDefaults.RETRY_JITTER_RATIO);

        int maxInFlight = getIntProperty("ai.concurrent.maxInFlight", AiConfigDefaults.CONCURRENT_MAX_IN_FLIGHT);
        long acquireTimeoutMs = getLongProperty("ai.concurrent.acquireTimeoutMs",
                AiConfigDefaults.CONCURRENT_ACQUIRE_TIMEOUT_MS);

        boolean fallbackModeEnabled = Boolean.parseBoolean(properties.getProperty("ai.fallback.mode.enabled",
                String.valueOf(AiConfigDefaults.FALLBACK_MODE_ENABLED)));
        String fallbackModelsStr = properties.getProperty("ai.fallback.models", AiConfigDefaults.FALLBACK_MODELS);
        List<String> fallbackModels = Arrays.asList(fallbackModelsStr.split("\\s*,\\s*"));

        AiRetryDelayStrategy delayStrategy = new AiRetryDelayStrategy(Duration.ofMillis(baseDelayMs),
                Duration.ofMillis(maxDelayMs), jitterRatio);
        AiRetryPolicy retryPolicy = new AiRetryPolicy(maxAttempts, delayStrategy);
        AiConcurrencyLimiter limiter = new AiConcurrencyLimiter(maxInFlight, Duration.ofMillis(acquireTimeoutMs));

        return new AiResiliencePolicy(
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs),
                Duration.ofMillis(Math.max(1000L, requestBudgetMs)),
                retryPolicy,
                limiter,
                fallbackModels,
                fallbackModeEnabled);
    }

    public static long getImageRequestBudgetMs() {
        long value = getLongProperty("ai.image.request.totalBudgetMs", AiConfigDefaults.IMAGE_REQUEST_TOTAL_BUDGET_MS);
        return Math.max(10_000L, value);
    }

    public static long getImageRequestHeartbeatIntervalMs() {
        long value = getLongProperty(
                "ai.image.request.heartbeatIntervalMs",
                AiConfigDefaults.IMAGE_REQUEST_HEARTBEAT_INTERVAL_MS);
        return Math.max(500L, value);
    }

    public static int getImageSubmitMaxAttempts() {
        int value = getIntProperty("ai.image.retry.submit.maxAttempts", AiConfigDefaults.IMAGE_SUBMIT_MAX_ATTEMPTS);
        return Math.max(1, Math.min(5, value));
    }

    public static int getImagePollMaxAttempts() {
        int value = getIntProperty("ai.image.retry.poll.maxAttempts", AiConfigDefaults.IMAGE_POLL_MAX_ATTEMPTS);
        return Math.max(1, Math.min(6, value));
    }

    public static int getImageDownloadMaxAttempts() {
        int value = getIntProperty(
                "ai.image.retry.download.maxAttempts",
                AiConfigDefaults.IMAGE_DOWNLOAD_MAX_ATTEMPTS);
        return Math.max(1, Math.min(6, value));
    }

    public static long getImageRetryBaseDelayMs() {
        long value = getLongProperty("ai.image.retry.baseDelayMs", AiConfigDefaults.IMAGE_RETRY_BASE_DELAY_MS);
        return Math.max(100L, value);
    }

    public static long getImageRetryMaxDelayMs() {
        long value = getLongProperty("ai.image.retry.maxDelayMs", AiConfigDefaults.IMAGE_RETRY_MAX_DELAY_MS);
        return Math.max(getImageRetryBaseDelayMs(), value);
    }

    public static long getImagePollInitialDelayMs() {
        long value = getLongProperty(
                "ai.image.poll.initialDelayMs",
                AiConfigDefaults.IMAGE_POLL_INITIAL_DELAY_MS);
        return Math.max(250L, value);
    }

    public static long getImagePollMaxDelayMs() {
        long value = getLongProperty("ai.image.poll.maxDelayMs", AiConfigDefaults.IMAGE_POLL_MAX_DELAY_MS);
        return Math.max(getImagePollInitialDelayMs(), value);
    }

    public static double getImagePollJitterRatio() {
        double value = getDoubleProperty("ai.image.poll.jitterRatio", AiConfigDefaults.IMAGE_POLL_JITTER_RATIO);
        return clampDouble(value, 0.0, 0.8);
    }

    public static boolean isImageFallbackModeEnabled() {
        return getBooleanProperty("ai.image.fallback.enabled", AiConfigDefaults.IMAGE_FALLBACK_MODE_ENABLED);
    }

    public static List<String> getImageFallbackModels() {
        String raw = properties.getProperty("ai.image.fallback.models", AiConfigDefaults.IMAGE_FALLBACK_MODELS);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> models = new ArrayList<>();
        for (String token : raw.split("\\s*,\\s*")) {
            String normalized = ImageGenConfigDefaults.normalizeImageModel(token);
            if (!ImageGenConfigDefaults.isSupportedImageModel(token)) {
                continue;
            }
            if (!models.contains(normalized)) {
                models.add(normalized);
            }
        }
        return List.copyOf(models);
    }

    public static List<String> getExternalApiCustomModels() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_EXTERNAL_API_CUSTOM_MODELS, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (String token : raw.split("\\s*,\\s*")) {
            String normalized = AiConfigDefaults.normalizeExternalModelId(token);
            if (!normalized.isBlank()) {
                models.add(normalized);
            }
        }
        return List.copyOf(new ArrayList<>(models));
    }

    public static void setExternalApiCustomModels(List<String> models) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (models != null) {
            for (String model : models) {
                String value = AiConfigDefaults.normalizeExternalModelId(model);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        setProperty(
                AiConfigDefaults.CONFIG_EXTERNAL_API_CUSTOM_MODELS,
                normalized.isEmpty() ? "" : String.join(",", normalized));
    }

    public static List<String> getExternalApiDiscoveredModels() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_EXTERNAL_API_DISCOVERED_MODELS, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (String token : raw.split("\\s*,\\s*")) {
            String normalized = AiConfigDefaults.normalizeExternalModelId(token);
            if (!normalized.isBlank()) {
                models.add(normalized);
            }
        }
        return List.copyOf(new ArrayList<>(models));
    }

    public static void setExternalApiDiscoveredModels(List<String> models) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (models != null) {
            for (String model : models) {
                String value = AiConfigDefaults.normalizeExternalModelId(model);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        setProperty(
                AiConfigDefaults.CONFIG_EXTERNAL_API_DISCOVERED_MODELS,
                normalized.isEmpty() ? "" : String.join(",", normalized));
    }

    public static List<String> getExternalApiMultimodalModels() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_EXTERNAL_API_MULTIMODAL_MODELS, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (String token : raw.split("\\s*,\\s*")) {
            String normalized = AiConfigDefaults.normalizeExternalModelId(token);
            if (!normalized.isBlank()) {
                models.add(normalized);
            }
        }
        return List.copyOf(new ArrayList<>(models));
    }

    public static void setExternalApiMultimodalModels(List<String> models) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (models != null) {
            for (String model : models) {
                String value = AiConfigDefaults.normalizeExternalModelId(model);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        setProperty(
                AiConfigDefaults.CONFIG_EXTERNAL_API_MULTIMODAL_MODELS,
                normalized.isEmpty() ? "" : String.join(",", normalized));
    }

    public static List<String> getExternalApiAudioInputModels() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_EXTERNAL_API_AUDIO_INPUT_MODELS, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (String token : raw.split("\\s*,\\s*")) {
            String normalized = AiConfigDefaults.normalizeExternalModelId(token);
            if (!normalized.isBlank()) {
                models.add(normalized);
            }
        }
        return List.copyOf(new ArrayList<>(models));
    }

    public static void setExternalApiAudioInputModels(List<String> models) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (models != null) {
            for (String model : models) {
                String value = AiConfigDefaults.normalizeExternalModelId(model);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        setProperty(
                AiConfigDefaults.CONFIG_EXTERNAL_API_AUDIO_INPUT_MODELS,
                normalized.isEmpty() ? "" : String.join(",", normalized));
    }

    public static List<String> getExternalApiFileInputModels() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_EXTERNAL_API_FILE_INPUT_MODELS, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (String token : raw.split("\\s*,\\s*")) {
            String normalized = AiConfigDefaults.normalizeExternalModelId(token);
            if (!normalized.isBlank()) {
                models.add(normalized);
            }
        }
        return List.copyOf(new ArrayList<>(models));
    }

    public static void setExternalApiFileInputModels(List<String> models) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (models != null) {
            for (String model : models) {
                String value = AiConfigDefaults.normalizeExternalModelId(model);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        setProperty(
                AiConfigDefaults.CONFIG_EXTERNAL_API_FILE_INPUT_MODELS,
                normalized.isEmpty() ? "" : String.join(",", normalized));
    }

    public static List<AiDiscoveredModelInfo> getExternalApiModelCatalog() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_EXTERNAL_API_MODEL_CATALOG, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<AiDiscoveredModelInfo> parsed = CONFIG_MAPPER.readValue(
                    raw,
                    new TypeReference<List<AiDiscoveredModelInfo>>() {
                    });
            LinkedHashSet<String> seenIds = new LinkedHashSet<>();
            List<AiDiscoveredModelInfo> normalized = new ArrayList<>();
            for (AiDiscoveredModelInfo info : parsed) {
                if (info == null) {
                    continue;
                }
                String normalizedId = AiConfigDefaults.normalizeExternalModelId(info.id());
                if (normalizedId.isBlank() || !seenIds.add(normalizedId)) {
                    continue;
                }
                normalized.add(new AiDiscoveredModelInfo(
                        normalizedId,
                        info.type(),
                        info.multimodal(),
                        info.supportsImageInput(),
                        info.supportsAudioInput(),
                        info.supportsFileInput(),
                        info.textContextMetadata(),
                        info.textParameterMetadata()));
            }
            return List.copyOf(normalized);
        } catch (Exception e) {
            LOG.warning("config.external.model.catalog.parse.failed",
                    "key", AiConfigDefaults.CONFIG_EXTERNAL_API_MODEL_CATALOG);
            return List.of();
        }
    }

    public static void setExternalApiModelCatalog(List<AiDiscoveredModelInfo> catalog) {
        List<AiDiscoveredModelInfo> normalized = new ArrayList<>();
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        if (catalog != null) {
            for (AiDiscoveredModelInfo info : catalog) {
                if (info == null) {
                    continue;
                }
                String normalizedId = AiConfigDefaults.normalizeExternalModelId(info.id());
                if (normalizedId.isBlank() || !seenIds.add(normalizedId)) {
                    continue;
                }
                normalized.add(new AiDiscoveredModelInfo(
                        normalizedId,
                        info.type(),
                        info.multimodal(),
                        info.supportsImageInput(),
                        info.supportsAudioInput(),
                        info.supportsFileInput(),
                        info.textContextMetadata(),
                        info.textParameterMetadata()));
            }
        }
        if (normalized.isEmpty()) {
            setProperty(AiConfigDefaults.CONFIG_EXTERNAL_API_MODEL_CATALOG, "");
            return;
        }
        try {
            setProperty(
                    AiConfigDefaults.CONFIG_EXTERNAL_API_MODEL_CATALOG,
                    CONFIG_MAPPER.writeValueAsString(normalized));
        } catch (Exception e) {
            LOG.warning("config.external.model.catalog.serialize.failed",
                    "key", AiConfigDefaults.CONFIG_EXTERNAL_API_MODEL_CATALOG);
        }
    }

    public static DailyReviewPersistenceRecord getPersistedDailyReview() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_DAILY_REVIEW_PERSISTED, "");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = CONFIG_MAPPER.readTree(raw);
            if (root == null || !root.isObject()) {
                return null;
            }
            return new DailyReviewPersistenceRecord(
                    readLocalDate(root.get("reviewDate"), LocalDate.now()),
                    readInstant(root.get("generatedAt"), Instant.now()),
                    readText(root.get("modelId")),
                    root.path("aiUsed").asBoolean(false),
                    readText(root.get("snapshotFingerprint")),
                    root.path("activeTaskCount").asInt(0),
                    root.path("overdueTaskCount").asInt(0),
                    root.path("tasksDueTodayCount").asInt(0),
                    root.path("upcomingTaskCount").asInt(0),
                    root.path("trackedMinutesToday").asLong(0L),
                    root.path("approximateFreeWindows").asBoolean(false),
                    readDailyReviewSummary(root.path("summary")),
                    readFocusRecommendation(root.path("focusRecommendation")),
                    readOverdueItems(root.path("overdueItems")),
                    readUpcomingItems(root.path("upcomingItems")),
                    readFreeWindows(root.path("freeWindows"))
            );
        } catch (Exception e) {
            LOG.warning("config.daily.review.parse.failed",
                    "key", AiConfigDefaults.CONFIG_DAILY_REVIEW_PERSISTED);
            return null;
        }
    }

    public static void setPersistedDailyReview(DailyReviewPersistenceRecord persistedReview) {
        if (persistedReview == null) {
            setProperty(AiConfigDefaults.CONFIG_DAILY_REVIEW_PERSISTED, "");
            return;
        }
        try {
            ObjectNode root = CONFIG_MAPPER.createObjectNode();
            root.put("reviewDate", persistedReview.reviewDate().toString());
            root.put("generatedAt", persistedReview.generatedAt().toString());
            root.put("modelId", persistedReview.modelId());
            root.put("aiUsed", persistedReview.aiUsed());
            root.put("snapshotFingerprint", persistedReview.snapshotFingerprint());
            root.put("activeTaskCount", persistedReview.activeTaskCount());
            root.put("overdueTaskCount", persistedReview.overdueTaskCount());
            root.put("tasksDueTodayCount", persistedReview.tasksDueTodayCount());
            root.put("upcomingTaskCount", persistedReview.upcomingTaskCount());
            root.put("trackedMinutesToday", persistedReview.trackedMinutesToday());
            root.put("approximateFreeWindows", persistedReview.approximateFreeWindows());
            root.set("summary", writeDailyReviewSummary(persistedReview.summary()));
            root.set("focusRecommendation", writeFocusRecommendation(persistedReview.focusRecommendation()));
            root.set("overdueItems", writeOverdueItems(persistedReview.overdueItems()));
            root.set("upcomingItems", writeUpcomingItems(persistedReview.upcomingItems()));
            root.set("freeWindows", writeFreeWindows(persistedReview.freeWindows()));
            setProperty(
                    AiConfigDefaults.CONFIG_DAILY_REVIEW_PERSISTED,
                    CONFIG_MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            LOG.warning("config.daily.review.serialize.failed",
                    "key", AiConfigDefaults.CONFIG_DAILY_REVIEW_PERSISTED);
        }
    }

    public static FocusBlockPersistenceRecord getPersistedFocusBlockRecommendations() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_FOCUS_BLOCKS_PERSISTED, "");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = CONFIG_MAPPER.readTree(raw);
            if (root == null || !root.isObject()) {
                return null;
            }
            return new FocusBlockPersistenceRecord(
                    readLocalDate(root.get("reviewDate"), LocalDate.now()),
                    readInstant(root.get("generatedAt"), Instant.now()),
                    readText(root.get("modelId")),
                    root.path("aiUsed").asBoolean(false),
                    readText(root.get("snapshotFingerprint")),
                    root.path("limitedHistory").asBoolean(true),
                    root.path("profileConfidence").asDouble(0.0),
                    root.path("switchDensityScore").asDouble(0.0),
                    root.path("averageFocusMinutes").asLong(0L),
                    root.path("stableFocusMinutes").asLong(0L),
                    root.path("totalTrackedMinutes").asLong(0L),
                    root.path("totalSessions").asInt(0),
                    readFocusBlockExplanation(root.path("explanation")),
                    readFocusBlockCandidates(root.path("candidateWindows")),
                    readFocusBlockRecommendations(root.path("focusWindows")),
                    readFocusBlockRecommendations(root.path("shortWindows")),
                    readFocusBlockRecommendation(root.path("nextRecommendedBlock")),
                    readFocusBlockRisks(root.path("risks"))
            );
        } catch (Exception e) {
            LOG.warning("config.focus.blocks.parse.failed",
                    "key", AiConfigDefaults.CONFIG_FOCUS_BLOCKS_PERSISTED);
            return null;
        }
    }

    public static void setPersistedFocusBlockRecommendations(FocusBlockPersistenceRecord persistedRecord) {
        if (persistedRecord == null) {
            setProperty(AiConfigDefaults.CONFIG_FOCUS_BLOCKS_PERSISTED, "");
            return;
        }
        try {
            ObjectNode root = CONFIG_MAPPER.createObjectNode();
            root.put("reviewDate", persistedRecord.reviewDate().toString());
            root.put("generatedAt", persistedRecord.generatedAt().toString());
            root.put("modelId", persistedRecord.modelId());
            root.put("aiUsed", persistedRecord.aiUsed());
            root.put("snapshotFingerprint", persistedRecord.snapshotFingerprint());
            root.put("limitedHistory", persistedRecord.limitedHistory());
            root.put("profileConfidence", persistedRecord.profileConfidence());
            root.put("switchDensityScore", persistedRecord.switchDensityScore());
            root.put("averageFocusMinutes", persistedRecord.averageFocusMinutes());
            root.put("stableFocusMinutes", persistedRecord.stableFocusMinutes());
            root.put("totalTrackedMinutes", persistedRecord.totalTrackedMinutes());
            root.put("totalSessions", persistedRecord.totalSessions());
            root.set("explanation", writeFocusBlockExplanation(persistedRecord.explanation()));
            root.set("candidateWindows", writeFocusBlockCandidates(persistedRecord.candidateWindows()));
            root.set("focusWindows", writeFocusBlockRecommendations(persistedRecord.focusWindows()));
            root.set("shortWindows", writeFocusBlockRecommendations(persistedRecord.shortWindows()));
            root.set("nextRecommendedBlock", writeFocusBlockRecommendation(persistedRecord.nextRecommendedBlock()));
            root.set("risks", writeFocusBlockRisks(persistedRecord.risks()));
            setProperty(
                    AiConfigDefaults.CONFIG_FOCUS_BLOCKS_PERSISTED,
                    CONFIG_MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            LOG.warning("config.focus.blocks.serialize.failed",
                    "key", AiConfigDefaults.CONFIG_FOCUS_BLOCKS_PERSISTED);
        }
    }

    public static PlanningQualityPersistenceRecord getPersistedPlanningQuality() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_PLANNING_QUALITY_PERSISTED, "");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = CONFIG_MAPPER.readTree(raw);
            if (root == null || !root.isObject()) {
                return null;
            }
            return new PlanningQualityPersistenceRecord(
                    readLocalDate(root.get("periodStart"), LocalDate.now().minusDays(13)),
                    readLocalDate(root.get("periodEnd"), LocalDate.now()),
                    readInstant(root.get("generatedAt"), Instant.now()),
                    readText(root.get("modelId")),
                    root.path("aiUsed").asBoolean(false),
                    readText(root.get("snapshotFingerprint")),
                    root.path("activeTaskCount").asInt(0),
                    root.path("completedTaskCount").asInt(0),
                    root.path("estimatedTaskCount").asInt(0),
                    root.path("scheduledTaskCount").asInt(0),
                    root.path("trackedTaskCount").asInt(0),
                    root.path("trackedSessionCount").asInt(0),
                    root.path("limitedData").asBoolean(true),
                    readPlanningQualitySummary(root.path("summary")),
                    readTimeEstimateAccuracyMetric(root.path("accuracyMetric")),
                    readRescheduleRateMetric(root.path("rescheduleMetric")),
                    readRhythmStabilityMetric(root.path("rhythmMetric")),
                    readPlanningQualityRisks(root.path("risks")),
                    readPlanningQualityRecommendations(root.path("recommendations"))
            );
        } catch (Exception e) {
            LOG.warning("config.planning.quality.parse.failed",
                    "key", AiConfigDefaults.CONFIG_PLANNING_QUALITY_PERSISTED);
            return null;
        }
    }

    public static void setPersistedPlanningQuality(PlanningQualityPersistenceRecord persistedRecord) {
        if (persistedRecord == null) {
            setProperty(AiConfigDefaults.CONFIG_PLANNING_QUALITY_PERSISTED, "");
            return;
        }
        try {
            ObjectNode root = CONFIG_MAPPER.createObjectNode();
            root.put("periodStart", persistedRecord.periodStart().toString());
            root.put("periodEnd", persistedRecord.periodEnd().toString());
            root.put("generatedAt", persistedRecord.generatedAt().toString());
            root.put("modelId", persistedRecord.modelId());
            root.put("aiUsed", persistedRecord.aiUsed());
            root.put("snapshotFingerprint", persistedRecord.snapshotFingerprint());
            root.put("activeTaskCount", persistedRecord.activeTaskCount());
            root.put("completedTaskCount", persistedRecord.completedTaskCount());
            root.put("estimatedTaskCount", persistedRecord.estimatedTaskCount());
            root.put("scheduledTaskCount", persistedRecord.scheduledTaskCount());
            root.put("trackedTaskCount", persistedRecord.trackedTaskCount());
            root.put("trackedSessionCount", persistedRecord.trackedSessionCount());
            root.put("limitedData", persistedRecord.limitedData());
            root.set("summary", writePlanningQualitySummary(persistedRecord.summary()));
            root.set("accuracyMetric", writeTimeEstimateAccuracyMetric(persistedRecord.accuracyMetric()));
            root.set("rescheduleMetric", writeRescheduleRateMetric(persistedRecord.rescheduleMetric()));
            root.set("rhythmMetric", writeRhythmStabilityMetric(persistedRecord.rhythmMetric()));
            root.set("risks", writePlanningQualityRisks(persistedRecord.risks()));
            root.set("recommendations", writePlanningQualityRecommendations(persistedRecord.recommendations()));
            setProperty(
                    AiConfigDefaults.CONFIG_PLANNING_QUALITY_PERSISTED,
                    CONFIG_MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            LOG.warning("config.planning.quality.serialize.failed",
                    "key", AiConfigDefaults.CONFIG_PLANNING_QUALITY_PERSISTED);
        }
    }

    private static DailyReviewSummary readDailyReviewSummary(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new DailyReviewSummary(DailyReviewSummarySource.UNAVAILABLE, "", List.of(), "", "", "");
        }
        List<String> bullets = new ArrayList<>();
        JsonNode bulletsNode = node.get("bullets");
        if (bulletsNode != null && bulletsNode.isArray()) {
            for (JsonNode bulletNode : bulletsNode) {
                String bullet = readText(bulletNode);
                if (!bullet.isBlank()) {
                    bullets.add(bullet);
                }
            }
        }
        return new DailyReviewSummary(
                parseDailyReviewSummarySource(readText(node.get("source"))),
                readText(node.get("headline")),
                bullets,
                readText(node.get("riskNote")),
                readText(node.get("nextStep")),
                readText(node.get("unavailableReason"))
        );
    }

    private static ObjectNode writeDailyReviewSummary(DailyReviewSummary summary) {
        DailyReviewSummary safe = summary == null
                ? new DailyReviewSummary(DailyReviewSummarySource.UNAVAILABLE, "", List.of(), "", "", "")
                : summary;
        ObjectNode node = CONFIG_MAPPER.createObjectNode();
        node.put("source", safe.source().name());
        node.put("headline", safe.headline());
        ArrayNode bullets = CONFIG_MAPPER.createArrayNode();
        for (String bullet : safe.bullets()) {
            bullets.add(bullet);
        }
        node.set("bullets", bullets);
        node.put("riskNote", safe.riskNote());
        node.put("nextStep", safe.nextStep());
        node.put("unavailableReason", safe.unavailableReason());
        return node;
    }

    private static DailyReviewFocusRecommendation readFocusRecommendation(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new DailyReviewFocusRecommendation("", "", "", DailyReviewSummarySource.UNAVAILABLE);
        }
        return new DailyReviewFocusRecommendation(
                readText(node.get("title")),
                readText(node.get("rationale")),
                readText(node.get("suggestedNextStep")),
                parseDailyReviewSummarySource(readText(node.get("source")))
        );
    }

    private static ObjectNode writeFocusRecommendation(DailyReviewFocusRecommendation focus) {
        DailyReviewFocusRecommendation safe = focus == null
                ? new DailyReviewFocusRecommendation("", "", "", DailyReviewSummarySource.UNAVAILABLE)
                : focus;
        ObjectNode node = CONFIG_MAPPER.createObjectNode();
        node.put("title", safe.title());
        node.put("rationale", safe.rationale());
        node.put("suggestedNextStep", safe.suggestedNextStep());
        node.put("source", safe.source().name());
        return node;
    }

    private static List<DailyReviewOverdueItem> readOverdueItems(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DailyReviewOverdueItem> items = new ArrayList<>();
        for (JsonNode itemNode : node) {
            items.add(new DailyReviewOverdueItem(
                    readText(itemNode.get("taskId")),
                    readText(itemNode.get("title")),
                    readLocalDate(itemNode.get("deadlineDate"), null),
                    readLocalDateTime(itemNode.get("deadlineDateTime"), null),
                    itemNode.path("overdueDays").asInt(0),
                    itemNode.path("complexity").asInt(0),
                    itemNode.path("smartPriority").asDouble(0.0),
                    readStringList(itemNode.get("tags"))
            ));
        }
        return List.copyOf(items);
    }

    private static ArrayNode writeOverdueItems(List<DailyReviewOverdueItem> items) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (items == null) {
            return array;
        }
        for (DailyReviewOverdueItem item : items) {
            ObjectNode node = CONFIG_MAPPER.createObjectNode();
            node.put("taskId", item.taskId());
            node.put("title", item.title());
            if (item.deadlineDate() != null) {
                node.put("deadlineDate", item.deadlineDate().toString());
            }
            if (item.deadlineDateTime() != null) {
                node.put("deadlineDateTime", item.deadlineDateTime().toString());
            }
            node.put("overdueDays", item.overdueDays());
            node.put("complexity", item.complexity());
            node.put("smartPriority", item.smartPriority());
            node.set("tags", writeStringList(item.tags()));
            array.add(node);
        }
        return array;
    }

    private static List<DailyReviewUpcomingItem> readUpcomingItems(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DailyReviewUpcomingItem> items = new ArrayList<>();
        for (JsonNode itemNode : node) {
            items.add(new DailyReviewUpcomingItem(
                    readText(itemNode.get("taskId")),
                    readText(itemNode.get("title")),
                    readLocalDate(itemNode.get("deadlineDate"), null),
                    readLocalDateTime(itemNode.get("deadlineDateTime"), null),
                    itemNode.path("daysUntilDue").asInt(0),
                    itemNode.path("dueToday").asBoolean(false),
                    itemNode.path("urgent").asBoolean(false),
                    itemNode.path("complexity").asInt(0),
                    itemNode.path("smartPriority").asDouble(0.0),
                    readStringList(itemNode.get("tags"))
            ));
        }
        return List.copyOf(items);
    }

    private static ArrayNode writeUpcomingItems(List<DailyReviewUpcomingItem> items) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (items == null) {
            return array;
        }
        for (DailyReviewUpcomingItem item : items) {
            ObjectNode node = CONFIG_MAPPER.createObjectNode();
            node.put("taskId", item.taskId());
            node.put("title", item.title());
            if (item.deadlineDate() != null) {
                node.put("deadlineDate", item.deadlineDate().toString());
            }
            if (item.deadlineDateTime() != null) {
                node.put("deadlineDateTime", item.deadlineDateTime().toString());
            }
            node.put("daysUntilDue", item.daysUntilDue());
            node.put("dueToday", item.dueToday());
            node.put("urgent", item.urgent());
            node.put("complexity", item.complexity());
            node.put("smartPriority", item.smartPriority());
            node.set("tags", writeStringList(item.tags()));
            array.add(node);
        }
        return array;
    }

    private static List<DailyReviewFreeWindow> readFreeWindows(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DailyReviewFreeWindow> windows = new ArrayList<>();
        for (JsonNode itemNode : node) {
            windows.add(new DailyReviewFreeWindow(
                    readLocalDateTime(itemNode.get("start"), null),
                    readLocalDateTime(itemNode.get("end"), null),
                    itemNode.path("durationMinutes").asInt(0),
                    parseWindowSuitability(readText(itemNode.get("suitability"))),
                    itemNode.path("approximate").asBoolean(false),
                    readText(itemNode.get("label"))
            ));
        }
        return List.copyOf(windows);
    }

    private static ArrayNode writeFreeWindows(List<DailyReviewFreeWindow> windows) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (windows == null) {
            return array;
        }
        for (DailyReviewFreeWindow window : windows) {
            ObjectNode node = CONFIG_MAPPER.createObjectNode();
            if (window.start() != null) {
                node.put("start", window.start().toString());
            }
            if (window.end() != null) {
                node.put("end", window.end().toString());
            }
            node.put("durationMinutes", window.durationMinutes());
            node.put("suitability", window.suitability().name());
            node.put("approximate", window.approximate());
            node.put("label", window.label());
            array.add(node);
        }
        return array;
    }

    private static ArrayNode writeStringList(List<String> values) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null) {
                array.add(value);
            }
        }
        return array;
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = readText(item);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static FocusBlockExplanation readFocusBlockExplanation(JsonNode node) {
        if (node == null || !node.isObject()) {
            return FocusBlockExplanation.unavailable();
        }
        return new FocusBlockExplanation(
                parseFocusBlockSummarySource(readText(node.get("source"))),
                readText(node.get("headline")),
                readText(node.get("summary")),
                readText(node.get("nextAction")),
                readText(node.get("limitations"))
        );
    }

    private static ObjectNode writeFocusBlockExplanation(FocusBlockExplanation explanation) {
        FocusBlockExplanation safe = explanation == null ? FocusBlockExplanation.unavailable() : explanation;
        ObjectNode node = CONFIG_MAPPER.createObjectNode();
        node.put("source", safe.source().name());
        node.put("headline", safe.headline());
        node.put("summary", safe.summary());
        node.put("nextAction", safe.nextAction());
        node.put("limitations", safe.limitations());
        return node;
    }

    private static PlanningQualitySummary readPlanningQualitySummary(JsonNode node) {
        if (node == null || !node.isObject()) {
            return PlanningQualitySummary.unavailable();
        }
        return new PlanningQualitySummary(
                parsePlanningQualitySummarySource(readText(node.get("source"))),
                readText(node.get("headline")),
                readText(node.get("summary")),
                readText(node.get("nextAction")),
                readText(node.get("limitations"))
        );
    }

    private static ObjectNode writePlanningQualitySummary(PlanningQualitySummary summary) {
        PlanningQualitySummary safe = summary == null ? PlanningQualitySummary.unavailable() : summary;
        ObjectNode node = CONFIG_MAPPER.createObjectNode();
        node.put("source", safe.source().name());
        node.put("headline", safe.headline());
        node.put("summary", safe.summary());
        node.put("nextAction", safe.nextAction());
        node.put("limitations", safe.limitations());
        return node;
    }

    private static TimeEstimateAccuracyMetric readTimeEstimateAccuracyMetric(JsonNode node) {
        if (node == null || !node.isObject()) {
            return TimeEstimateAccuracyMetric.unavailable();
        }
        return new TimeEstimateAccuracyMetric(
                node.path("estimatedTaskCount").asInt(0),
                node.path("comparableTaskCount").asInt(0),
                node.path("averageErrorRatio").asDouble(0.0),
                node.path("hitRate").asDouble(0.0),
                node.path("underestimationBias").asDouble(0.0),
                node.path("overestimationBias").asDouble(0.0),
                node.path("approximate").asBoolean(true)
        );
    }

    private static ObjectNode writeTimeEstimateAccuracyMetric(TimeEstimateAccuracyMetric metric) {
        TimeEstimateAccuracyMetric safe = metric == null ? TimeEstimateAccuracyMetric.unavailable() : metric;
        ObjectNode node = CONFIG_MAPPER.createObjectNode();
        node.put("estimatedTaskCount", safe.estimatedTaskCount());
        node.put("comparableTaskCount", safe.comparableTaskCount());
        node.put("averageErrorRatio", safe.averageErrorRatio());
        node.put("hitRate", safe.hitRate());
        node.put("underestimationBias", safe.underestimationBias());
        node.put("overestimationBias", safe.overestimationBias());
        node.put("approximate", safe.approximate());
        return node;
    }

    private static RescheduleRateMetric readRescheduleRateMetric(JsonNode node) {
        if (node == null || !node.isObject()) {
            return RescheduleRateMetric.unavailable();
        }
        return new RescheduleRateMetric(
                node.path("analyzedTaskCount").asInt(0),
                node.path("rescheduledTaskCount").asInt(0),
                node.path("untouchedTaskCount").asInt(0),
                node.path("multipleRescheduleCount").asInt(0),
                node.path("lateRescheduleCount").asInt(0),
                node.path("rescheduleRate").asDouble(0.0),
                node.path("approximate").asBoolean(true)
        );
    }

    private static ObjectNode writeRescheduleRateMetric(RescheduleRateMetric metric) {
        RescheduleRateMetric safe = metric == null ? RescheduleRateMetric.unavailable() : metric;
        ObjectNode node = CONFIG_MAPPER.createObjectNode();
        node.put("analyzedTaskCount", safe.analyzedTaskCount());
        node.put("rescheduledTaskCount", safe.rescheduledTaskCount());
        node.put("untouchedTaskCount", safe.untouchedTaskCount());
        node.put("multipleRescheduleCount", safe.multipleRescheduleCount());
        node.put("lateRescheduleCount", safe.lateRescheduleCount());
        node.put("rescheduleRate", safe.rescheduleRate());
        node.put("approximate", safe.approximate());
        return node;
    }

    private static RhythmStabilityMetric readRhythmStabilityMetric(JsonNode node) {
        if (node == null || !node.isObject()) {
            return RhythmStabilityMetric.unavailable();
        }
        return new RhythmStabilityMetric(
                parseRhythmStabilityBand(readText(node.get("band"))),
                node.path("score").asDouble(0.0),
                node.path("analyzedDayCount").asInt(0),
                node.path("productiveDayCount").asInt(0),
                node.path("startTimeVariabilityMinutes").asInt(0),
                node.path("focusMinutesVariability").asDouble(0.0),
                node.path("approximate").asBoolean(true)
        );
    }

    private static ObjectNode writeRhythmStabilityMetric(RhythmStabilityMetric metric) {
        RhythmStabilityMetric safe = metric == null ? RhythmStabilityMetric.unavailable() : metric;
        ObjectNode node = CONFIG_MAPPER.createObjectNode();
        node.put("band", safe.band().name());
        node.put("score", safe.score());
        node.put("analyzedDayCount", safe.analyzedDayCount());
        node.put("productiveDayCount", safe.productiveDayCount());
        node.put("startTimeVariabilityMinutes", safe.startTimeVariabilityMinutes());
        node.put("focusMinutesVariability", safe.focusMinutesVariability());
        node.put("approximate", safe.approximate());
        return node;
    }

    private static List<PlanningQualityRisk> readPlanningQualityRisks(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PlanningQualityRisk> items = new ArrayList<>();
        for (JsonNode itemNode : node) {
            items.add(new PlanningQualityRisk(
                    parsePlanningQualityRiskSeverity(readText(itemNode.get("severity"))),
                    readText(itemNode.get("title")),
                    readText(itemNode.get("detail"))
            ));
        }
        return List.copyOf(items);
    }

    private static ArrayNode writePlanningQualityRisks(List<PlanningQualityRisk> items) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (items == null) {
            return array;
        }
        for (PlanningQualityRisk item : items) {
            ObjectNode node = CONFIG_MAPPER.createObjectNode();
            node.put("severity", item.severity().name());
            node.put("title", item.title());
            node.put("detail", item.detail());
            array.add(node);
        }
        return array;
    }

    private static List<PlanningQualityRecommendation> readPlanningQualityRecommendations(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PlanningQualityRecommendation> items = new ArrayList<>();
        for (JsonNode itemNode : node) {
            items.add(new PlanningQualityRecommendation(
                    readText(itemNode.get("title")),
                    readText(itemNode.get("detail")),
                    readText(itemNode.get("action")),
                    parsePlanningQualitySummarySource(readText(itemNode.get("source")))
            ));
        }
        return List.copyOf(items);
    }

    private static ArrayNode writePlanningQualityRecommendations(List<PlanningQualityRecommendation> items) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (items == null) {
            return array;
        }
        for (PlanningQualityRecommendation item : items) {
            ObjectNode node = CONFIG_MAPPER.createObjectNode();
            node.put("title", item.title());
            node.put("detail", item.detail());
            node.put("action", item.action());
            node.put("source", item.source().name());
            array.add(node);
        }
        return array;
    }

    private static List<FocusBlockCandidate> readFocusBlockCandidates(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<FocusBlockCandidate> items = new ArrayList<>();
        for (JsonNode itemNode : node) {
            items.add(new FocusBlockCandidate(
                    readText(itemNode.get("label")),
                    readLocalDateTime(itemNode.get("startAt"), null),
                    readLocalDateTime(itemNode.get("endAt"), null),
                    itemNode.path("durationMinutes").asLong(0L),
                    parseFocusBlockType(readText(itemNode.get("type"))),
                    itemNode.path("suitabilityScore").asDouble(0.0),
                    itemNode.path("confidence").asDouble(0.0),
                    itemNode.path("approximate").asBoolean(false),
                    readFocusBlockReasons(itemNode.get("reasons"))
            ));
        }
        return List.copyOf(items);
    }

    private static ArrayNode writeFocusBlockCandidates(List<FocusBlockCandidate> items) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (items == null) {
            return array;
        }
        for (FocusBlockCandidate item : items) {
            ObjectNode node = CONFIG_MAPPER.createObjectNode();
            node.put("label", item.label());
            if (item.startAt() != null) {
                node.put("startAt", item.startAt().toString());
            }
            if (item.endAt() != null) {
                node.put("endAt", item.endAt().toString());
            }
            node.put("durationMinutes", item.durationMinutes());
            node.put("type", item.type().name());
            node.put("suitabilityScore", item.suitabilityScore());
            node.put("confidence", item.confidence());
            node.put("approximate", item.approximate());
            node.set("reasons", writeFocusBlockReasons(item.reasons()));
            array.add(node);
        }
        return array;
    }

    private static FocusBlockRecommendation readFocusBlockRecommendation(JsonNode node) {
        if (node == null || !node.isObject()) {
            return FocusBlockRecommendation.unavailable();
        }
        return new FocusBlockRecommendation(
                readText(node.get("title")),
                readText(node.get("rationale")),
                readText(node.get("nextStep")),
                readLocalDateTime(node.get("startAt"), null),
                readLocalDateTime(node.get("endAt"), null),
                node.path("durationMinutes").asLong(0L),
                parseFocusBlockType(readText(node.get("type"))),
                node.path("suitabilityScore").asDouble(0.0),
                node.path("confidence").asDouble(0.0),
                node.path("primary").asBoolean(true),
                readFocusBlockReasons(node.get("reasons"))
        );
    }

    private static List<FocusBlockRecommendation> readFocusBlockRecommendations(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<FocusBlockRecommendation> items = new ArrayList<>();
        for (JsonNode itemNode : node) {
            items.add(readFocusBlockRecommendation(itemNode));
        }
        return List.copyOf(items);
    }

    private static ObjectNode writeFocusBlockRecommendation(FocusBlockRecommendation item) {
        FocusBlockRecommendation safe = item == null ? FocusBlockRecommendation.unavailable() : item;
        ObjectNode node = CONFIG_MAPPER.createObjectNode();
        node.put("title", safe.title());
        node.put("rationale", safe.rationale());
        node.put("nextStep", safe.nextStep());
        if (safe.startAt() != null) {
            node.put("startAt", safe.startAt().toString());
        }
        if (safe.endAt() != null) {
            node.put("endAt", safe.endAt().toString());
        }
        node.put("durationMinutes", safe.durationMinutes());
        node.put("type", safe.type().name());
        node.put("suitabilityScore", safe.suitabilityScore());
        node.put("confidence", safe.confidence());
        node.put("primary", safe.primary());
        node.set("reasons", writeFocusBlockReasons(safe.reasons()));
        return node;
    }

    private static ArrayNode writeFocusBlockRecommendations(List<FocusBlockRecommendation> items) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (items == null) {
            return array;
        }
        for (FocusBlockRecommendation item : items) {
            array.add(writeFocusBlockRecommendation(item));
        }
        return array;
    }

    private static List<FocusBlockReason> readFocusBlockReasons(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<FocusBlockReason> reasons = new ArrayList<>();
        for (JsonNode itemNode : node) {
            reasons.add(new FocusBlockReason(
                    readText(itemNode.get("title")),
                    readText(itemNode.get("detail"))
            ));
        }
        return List.copyOf(reasons);
    }

    private static ArrayNode writeFocusBlockReasons(List<FocusBlockReason> reasons) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (reasons == null) {
            return array;
        }
        for (FocusBlockReason reason : reasons) {
            ObjectNode node = CONFIG_MAPPER.createObjectNode();
            node.put("title", reason.title());
            node.put("detail", reason.detail());
            array.add(node);
        }
        return array;
    }

    private static List<FocusBlockRisk> readFocusBlockRisks(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<FocusBlockRisk> items = new ArrayList<>();
        for (JsonNode itemNode : node) {
            items.add(new FocusBlockRisk(
                    parseFocusBlockRiskLevel(readText(itemNode.get("level"))),
                    readText(itemNode.get("title")),
                    readText(itemNode.get("detail"))
            ));
        }
        return List.copyOf(items);
    }

    private static ArrayNode writeFocusBlockRisks(List<FocusBlockRisk> items) {
        ArrayNode array = CONFIG_MAPPER.createArrayNode();
        if (items == null) {
            return array;
        }
        for (FocusBlockRisk item : items) {
            ObjectNode node = CONFIG_MAPPER.createObjectNode();
            node.put("level", item.level().name());
            node.put("title", item.title());
            node.put("detail", item.detail());
            array.add(node);
        }
        return array;
    }

    private static DailyReviewSummarySource parseDailyReviewSummarySource(String raw) {
        if (raw == null || raw.isBlank()) {
            return DailyReviewSummarySource.UNAVAILABLE;
        }
        try {
            return DailyReviewSummarySource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DailyReviewSummarySource.UNAVAILABLE;
        }
    }

    private static FocusBlockSummarySource parseFocusBlockSummarySource(String raw) {
        if (raw == null || raw.isBlank()) {
            return FocusBlockSummarySource.UNAVAILABLE;
        }
        try {
            return FocusBlockSummarySource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FocusBlockSummarySource.UNAVAILABLE;
        }
    }

    private static PlanningQualitySummarySource parsePlanningQualitySummarySource(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlanningQualitySummarySource.UNAVAILABLE;
        }
        try {
            return PlanningQualitySummarySource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PlanningQualitySummarySource.UNAVAILABLE;
        }
    }

    private static PlanningQualityRiskSeverity parsePlanningQualityRiskSeverity(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlanningQualityRiskSeverity.INFO;
        }
        try {
            return PlanningQualityRiskSeverity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PlanningQualityRiskSeverity.INFO;
        }
    }

    private static RhythmStabilityBand parseRhythmStabilityBand(String raw) {
        if (raw == null || raw.isBlank()) {
            return RhythmStabilityBand.UNAVAILABLE;
        }
        try {
            return RhythmStabilityBand.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RhythmStabilityBand.UNAVAILABLE;
        }
    }

    private static FocusBlockType parseFocusBlockType(String raw) {
        if (raw == null || raw.isBlank()) {
            return FocusBlockType.LIGHT_FOCUS;
        }
        try {
            return FocusBlockType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FocusBlockType.LIGHT_FOCUS;
        }
    }

    private static FocusBlockRiskLevel parseFocusBlockRiskLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return FocusBlockRiskLevel.INFO;
        }
        try {
            return FocusBlockRiskLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FocusBlockRiskLevel.INFO;
        }
    }

    private static DailyReviewWindowSuitability parseWindowSuitability(String raw) {
        if (raw == null || raw.isBlank()) {
            return DailyReviewWindowSuitability.UNKNOWN;
        }
        try {
            return DailyReviewWindowSuitability.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DailyReviewWindowSuitability.UNKNOWN;
        }
    }

    private static String readText(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private static Instant readInstant(JsonNode node, Instant fallback) {
        String raw = readText(node);
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static LocalDate readLocalDate(JsonNode node, LocalDate fallback) {
        String raw = readText(node);
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static LocalDateTime readLocalDateTime(JsonNode node, LocalDateTime fallback) {
        String raw = readText(node);
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static List<String> getExternalImageCustomModels() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_EXTERNAL_IMAGE_CUSTOM_MODELS, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (String token : raw.split("\\s*,\\s*")) {
            String normalized = ImageGenConfigDefaults.normalizeImageModel(token);
            if (!normalized.isBlank()) {
                models.add(normalized);
            }
        }
        return List.copyOf(new ArrayList<>(models));
    }

    public static void setExternalImageCustomModels(List<String> models) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (models != null) {
            for (String model : models) {
                String value = ImageGenConfigDefaults.normalizeImageModel(model);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        setProperty(
                AiConfigDefaults.CONFIG_EXTERNAL_IMAGE_CUSTOM_MODELS,
                normalized.isEmpty() ? "" : String.join(",", normalized));
    }

    public static List<String> getExternalImageDiscoveredModels() {
        String raw = properties.getProperty(AiConfigDefaults.CONFIG_EXTERNAL_IMAGE_DISCOVERED_MODELS, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (String token : raw.split("\\s*,\\s*")) {
            String normalized = ImageGenConfigDefaults.normalizeImageModel(token);
            if (!normalized.isBlank()) {
                models.add(normalized);
            }
        }
        return List.copyOf(new ArrayList<>(models));
    }

    public static void setExternalImageDiscoveredModels(List<String> models) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (models != null) {
            for (String model : models) {
                String value = ImageGenConfigDefaults.normalizeImageModel(model);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        setProperty(
                AiConfigDefaults.CONFIG_EXTERNAL_IMAGE_DISCOVERED_MODELS,
                normalized.isEmpty() ? "" : String.join(",", normalized));
    }

    public static String getAssistantReasoningEffort() {
        return AiConfigDefaults.normalizeAssistantReasoningEffort(
                properties.getProperty(
                        AiConfigDefaults.CONFIG_ASSISTANT_REASONING_EFFORT,
                        AiConfigDefaults.DEFAULT_ASSISTANT_REASONING_EFFORT));
    }

    public static void setAssistantReasoningEffort(String value) {
        setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_REASONING_EFFORT,
                AiConfigDefaults.normalizeAssistantReasoningEffort(value));
    }

    public static Integer getAssistantReasoningMaxTokens() {
        int value = getIntProperty(AiConfigDefaults.CONFIG_ASSISTANT_REASONING_MAX_TOKENS, -1);
        if (value <= 0) {
            return null;
        }
        return Math.min(value, 200_000);
    }

    public static void setAssistantReasoningMaxTokens(Integer value) {
        if (value == null || value <= 0) {
            setProperty(AiConfigDefaults.CONFIG_ASSISTANT_REASONING_MAX_TOKENS, "");
            return;
        }
        setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_REASONING_MAX_TOKENS,
                String.valueOf(Math.min(value, 200_000)));
    }

    public static String getAssistantReasoningSummary() {
        return AiConfigDefaults.normalizeAssistantReasoningSummary(
                properties.getProperty(
                        AiConfigDefaults.CONFIG_ASSISTANT_REASONING_SUMMARY,
                        AiConfigDefaults.DEFAULT_ASSISTANT_REASONING_SUMMARY));
    }

    public static void setAssistantReasoningSummary(String value) {
        setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_REASONING_SUMMARY,
                AiConfigDefaults.normalizeAssistantReasoningSummary(value));
    }

    public static boolean isAssistantReasoningExcluded() {
        return getBooleanProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_REASONING_EXCLUDE,
                AiConfigDefaults.DEFAULT_ASSISTANT_REASONING_EXCLUDE);
    }

    public static void setAssistantReasoningExcluded(boolean excluded) {
        setProperty(AiConfigDefaults.CONFIG_ASSISTANT_REASONING_EXCLUDE, String.valueOf(excluded));
    }

    public static Integer getAssistantTextMaxTokens() {
        return AiConfigDefaults.normalizeAssistantTextMaxTokens(
                getOptionalIntProperty(AiConfigDefaults.CONFIG_ASSISTANT_TEXT_MAX_TOKENS));
    }

    public static void setAssistantTextMaxTokens(Integer value) {
        Integer normalized = AiConfigDefaults.normalizeAssistantTextMaxTokens(value);
        setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_TEXT_MAX_TOKENS,
                normalized == null ? "" : String.valueOf(normalized));
    }

    public static Double getAssistantTextTemperature() {
        return AiConfigDefaults.normalizeAssistantTextTemperature(
                getOptionalDoubleProperty(AiConfigDefaults.CONFIG_ASSISTANT_TEXT_TEMPERATURE));
    }

    public static void setAssistantTextTemperature(Double value) {
        Double normalized = AiConfigDefaults.normalizeAssistantTextTemperature(value);
        setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_TEXT_TEMPERATURE,
                normalized == null ? "" : String.valueOf(normalized));
    }

    public static Double getAssistantTextTopP() {
        return AiConfigDefaults.normalizeAssistantTextTopP(
                getOptionalDoubleProperty(AiConfigDefaults.CONFIG_ASSISTANT_TEXT_TOP_P));
    }

    public static void setAssistantTextTopP(Double value) {
        Double normalized = AiConfigDefaults.normalizeAssistantTextTopP(value);
        setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_TEXT_TOP_P,
                normalized == null ? "" : String.valueOf(normalized));
    }

    public static Double getAssistantTextFrequencyPenalty() {
        return AiConfigDefaults.normalizeAssistantTextFrequencyPenalty(
                getOptionalDoubleProperty(AiConfigDefaults.CONFIG_ASSISTANT_TEXT_FREQUENCY_PENALTY));
    }

    public static void setAssistantTextFrequencyPenalty(Double value) {
        Double normalized = AiConfigDefaults.normalizeAssistantTextFrequencyPenalty(value);
        setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_TEXT_FREQUENCY_PENALTY,
                normalized == null ? "" : String.valueOf(normalized));
    }

    public static Double getAssistantTextPresencePenalty() {
        return AiConfigDefaults.normalizeAssistantTextPresencePenalty(
                getOptionalDoubleProperty(AiConfigDefaults.CONFIG_ASSISTANT_TEXT_PRESENCE_PENALTY));
    }

    public static void setAssistantTextPresencePenalty(Double value) {
        Double normalized = AiConfigDefaults.normalizeAssistantTextPresencePenalty(value);
        setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_TEXT_PRESENCE_PENALTY,
                normalized == null ? "" : String.valueOf(normalized));
    }

    public static boolean isAiPromptCachingEnabled() {
        return getBooleanProperty(
                AiConfigDefaults.CONFIG_AI_PROMPT_CACHING_ENABLED,
                AiConfigDefaults.AI_PROMPT_CACHING_ENABLED);
    }

    public static void setAiPromptCachingEnabled(boolean enabled) {
        setProperty(AiConfigDefaults.CONFIG_AI_PROMPT_CACHING_ENABLED, String.valueOf(enabled));
    }

    public static boolean isAiPluginWebEnabled() {
        return getBooleanProperty(
                AiConfigDefaults.CONFIG_PLUGIN_WEB_ENABLED,
                AiConfigDefaults.DEFAULT_PLUGIN_WEB_ENABLED);
    }

    public static void setAiPluginWebEnabled(boolean enabled) {
        setProperty(AiConfigDefaults.CONFIG_PLUGIN_WEB_ENABLED, String.valueOf(enabled));
    }

    public static String getAiPluginWebEngine() {
        return AiConfigDefaults.normalizePluginWebEngine(
                properties.getProperty(
                        AiConfigDefaults.CONFIG_PLUGIN_WEB_ENGINE,
                        AiConfigDefaults.DEFAULT_PLUGIN_WEB_ENGINE));
    }

    public static void setAiPluginWebEngine(String engine) {
        setProperty(
                AiConfigDefaults.CONFIG_PLUGIN_WEB_ENGINE,
                AiConfigDefaults.normalizePluginWebEngine(engine));
    }

    public static int getAiPluginWebMaxResults() {
        return AiConfigDefaults.normalizePluginWebMaxResults(
                getIntProperty(
                        AiConfigDefaults.CONFIG_PLUGIN_WEB_MAX_RESULTS,
                        AiConfigDefaults.DEFAULT_PLUGIN_WEB_MAX_RESULTS));
    }

    public static void setAiPluginWebMaxResults(Integer maxResults) {
        setProperty(
                AiConfigDefaults.CONFIG_PLUGIN_WEB_MAX_RESULTS,
                String.valueOf(AiConfigDefaults.normalizePluginWebMaxResults(maxResults)));
    }

    public static String getAiPluginWebSearchPrompt() {
        return AiConfigDefaults.normalizePluginWebSearchPrompt(
                properties.getProperty(
                        AiConfigDefaults.CONFIG_PLUGIN_WEB_SEARCH_PROMPT,
                        AiConfigDefaults.DEFAULT_PLUGIN_WEB_SEARCH_PROMPT));
    }

    public static void setAiPluginWebSearchPrompt(String prompt) {
        setProperty(
                AiConfigDefaults.CONFIG_PLUGIN_WEB_SEARCH_PROMPT,
                AiConfigDefaults.normalizePluginWebSearchPrompt(prompt));
    }

    public static boolean isAiPluginFileParserEnabled() {
        return getBooleanProperty(
                AiConfigDefaults.CONFIG_PLUGIN_FILE_PARSER_ENABLED,
                AiConfigDefaults.DEFAULT_PLUGIN_FILE_PARSER_ENABLED);
    }

    public static void setAiPluginFileParserEnabled(boolean enabled) {
        setProperty(AiConfigDefaults.CONFIG_PLUGIN_FILE_PARSER_ENABLED, String.valueOf(enabled));
    }

    public static String getAiPluginFileParserPdfEngine() {
        return AiConfigDefaults.normalizePluginFileParserPdfEngine(
                properties.getProperty(
                        AiConfigDefaults.CONFIG_PLUGIN_FILE_PARSER_PDF_ENGINE,
                        AiConfigDefaults.DEFAULT_PLUGIN_FILE_PARSER_PDF_ENGINE));
    }

    public static void setAiPluginFileParserPdfEngine(String engine) {
        setProperty(
                AiConfigDefaults.CONFIG_PLUGIN_FILE_PARSER_PDF_ENGINE,
                AiConfigDefaults.normalizePluginFileParserPdfEngine(engine));
    }

    public static boolean isAiPluginResponseHealingEnabled() {
        return getBooleanProperty(
                AiConfigDefaults.CONFIG_PLUGIN_RESPONSE_HEALING_ENABLED,
                AiConfigDefaults.DEFAULT_PLUGIN_RESPONSE_HEALING_ENABLED);
    }

    public static void setAiPluginResponseHealingEnabled(boolean enabled) {
        setProperty(AiConfigDefaults.CONFIG_PLUGIN_RESPONSE_HEALING_ENABLED, String.valueOf(enabled));
    }

    public static long getAiRequestHeartbeatIntervalMs() {
        long value = getLongProperty("ai.request.heartbeatIntervalMs", AiConfigDefaults.REQUEST_HEARTBEAT_INTERVAL_MS);
        return Math.max(500L, value);
    }

    public static boolean isAiContinuationEnabled() {
        return getBooleanProperty("ai.request.continuation.enabled", AiConfigDefaults.CONTINUATION_ENABLED);
    }

    public static int getAiContinuationMaxSteps() {
        int value = getIntProperty("ai.request.continuation.maxSteps", AiConfigDefaults.CONTINUATION_MAX_STEPS);
        return Math.max(0, Math.min(3, value));
    }

    public static int getAiContinuationMinPartialChars() {
        int value = getIntProperty("ai.request.continuation.minPartialChars",
                AiConfigDefaults.CONTINUATION_MIN_PARTIAL_CHARS);
        return Math.max(20, Math.min(2000, value));
    }

    private static long getLongProperty(String key, long defaultValue) {
        String val = properties.getProperty(key);
        if (val == null)
            return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int getIntProperty(String key, int defaultValue) {
        String val = properties.getProperty(key);
        if (val == null)
            return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Integer getOptionalIntProperty(String key) {
        String val = properties.getProperty(key);
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double getDoubleProperty(String key, double defaultValue) {
        String val = properties.getProperty(key);
        if (val == null)
            return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Double getOptionalDoubleProperty(String key) {
        String val = properties.getProperty(key);
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean getBooleanProperty(String key, boolean defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) {
            return defaultValue;
        }
        String normalized = val.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        return defaultValue;
    }

    private static String normalizeUxLayoutDensityMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_DEFAULT;
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMFORTABLE,
                    UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMPACT -> normalized;
            default -> UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_DEFAULT;
        };
    }

    private static double clampDouble(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static String formatDoublePropertyValue(double value) {
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    private static Set<String> parseStringSetProperty(String key, String defaultValue) {
        String raw = properties.getProperty(key, defaultValue);
        if (raw == null) {
            return Set.of();
        }
        String normalizedRaw = raw.trim();
        if (normalizedRaw.isEmpty() || UxConfigDefaults.UX_COLLECTION_NONE_MARKER.equalsIgnoreCase(normalizedRaw)) {
            return Set.of();
        }
        String[] parts = normalizedRaw.split("\\s*,\\s*");
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String part : parts) {
            String value = normalizeCollectionValue(part);
            if (value != null) {
                values.add(value);
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private static List<String> parseStringListProperty(String key, String defaultValue) {
        String raw = properties.getProperty(key, defaultValue);
        if (raw == null) {
            return List.of();
        }
        String normalizedRaw = raw.trim();
        if (normalizedRaw.isEmpty() || UxConfigDefaults.UX_COLLECTION_NONE_MARKER.equalsIgnoreCase(normalizedRaw)) {
            return List.of();
        }
        String[] parts = normalizedRaw.split("\\s*,\\s*");
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String part : parts) {
            String value = normalizeCollectionValue(part);
            if (value != null) {
                deduped.add(value);
            }
        }
        return List.copyOf(new ArrayList<>(deduped));
    }

    private static String encodeStringCollection(Iterable<String> values) {
        if (values == null) {
            return UxConfigDefaults.UX_COLLECTION_NONE_MARKER;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String safe = normalizeCollectionValue(value);
            if (safe != null) {
                normalized.add(safe);
            }
        }
        if (normalized.isEmpty()) {
            return UxConfigDefaults.UX_COLLECTION_NONE_MARKER;
        }
        return String.join(",", normalized);
    }

    private static String normalizeCollectionValue(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private static void normalizeLegacyUxRolloutProperties() {
        List<String> removedKeys = new ArrayList<>();
        for (String key : LEGACY_UX_ROLLOUT_KEYS) {
            if (properties.remove(key) != null) {
                removedKeys.add(key);
            }
        }
        if (removedKeys.isEmpty()) {
            return;
        }
        LOG.info(
                "config.ux.rollout.keys.normalized",
                "removedCount", removedKeys.size(),
                "removedKeys", String.join(",", removedKeys));
        saveProperties();
    }

    private static boolean isSecretKey(String key) {
        return SECRET_KEYS.contains(key);
    }

    private static void initSecretResolver() {
        SecretProvider envProvider = new EnvSecretProvider();
        SecretProvider legacyConfigProvider = new LegacyConfigSecretProvider(properties::getProperty);
        if (Boolean.getBoolean(PROP_DISABLE_OS_SECRET_PROVIDERS)) {
            secretResolver = new SecretResolver(List.of(envProvider, legacyConfigProvider));
            return;
        }
        SecretProvider keychainProvider = new KeychainSecretProvider();
        secretResolver = new SecretResolver(List.of(envProvider, keychainProvider, legacyConfigProvider));
    }

    private static String resolveSecret(String key) {
        if (secretResolver == null) {
            return null;
        }
        return secretResolver.resolve(normalizeSecretKey(key));
    }

    private static void setSecretProperty(String key, String value) {
        String normalizedValue = value == null ? "" : value.trim();
        String canonicalKey = normalizeSecretKey(key);

        properties.setProperty(canonicalKey, normalizedValue);
        if (!canonicalKey.equals(key)) {
            properties.setProperty(key, "");
        }
        if (CONFIG_EXTERNAL_API_KEY.equals(canonicalKey)) {
            properties.setProperty(CONFIG_API_KEY, "");
            properties.setProperty(CONFIG_EXTERNAL_API_KEY, normalizedValue);
        }

        if (normalizedValue.isBlank()) {
            if (secretResolver != null) {
                secretResolver.clear(canonicalKey);
            }
        } else if (secretResolver != null) {
            secretResolver.store(canonicalKey, normalizedValue);
        }

        saveProperties();
        notifyConfigChanged();
    }

    private static void notifyConfigChanged() {
        configRevisionProperty.set(configRevisionProperty.get() + 1L);
    }

    private static String normalizeSecretKey(String key) {
        if (CONFIG_API_KEY.equals(key)) {
            return CONFIG_EXTERNAL_API_KEY;
        }
        return key;
    }
}
