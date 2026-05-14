package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;

/**
 * Handles migration of legacy configuration keys to the new AI mode structure.
 * 
 * This class is responsible for:
 * - Detecting legacy api.* keys
 * - Migrating them to external.api.* keys
 * - Setting default ai.mode=offline for new installations
 */
public class AiConfigMigration {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(AiConfigMigration.class);

    /**
     * Legacy configuration keys.
     */
    private static final String LEGACY_API_URL = "api.url";
    private static final String LEGACY_API_KEY = "api.key";
    private static final String LEGACY_API_MODEL = "api.model";
    private static final String LEGACY_IMAGE_MODEL = "ai.image.model";
    private static final String LEGACY_IMAGE_SIZE = "ai.image.size";
    private static final String LEGACY_IMAGE_ASPECT_RATIO = "ai.image.aspect_ratio";
    private static final String LEGACY_IMAGE_RESOLUTION = "ai.image.resolution";

    /**
     * New configuration keys.
     */
    private static final String AI_MODE = "ai.mode";
    private static final String EXTERNAL_API_URL = "external.api.baseUrl";
    private static final String EXTERNAL_API_KEY = "external.api.key";
    private static final String EXTERNAL_API_MODEL = "external.api.model";
    private static final String EXTERNAL_IMAGE_MODEL = "external.image.model";
    private static final String EXTERNAL_IMAGE_SIZE = "external.image.size";
    private static final String EXTERNAL_IMAGE_ASPECT_RATIO = "external.image.aspect_ratio";
    private static final String EXTERNAL_IMAGE_RESOLUTION = "external.image.resolution";

    /**
     * Migration flag to prevent re-migration.
     */
    private static final String MIGRATION_DONE_FLAG = "ai.config.migrated.v2";

    /**
     * Performs migration if needed.
     * Should be called during application startup.
     */
    public static void migrateIfNeeded() {
        // Check if migration was already done
        String migrated = ConfigManager.getProperty(MIGRATION_DONE_FLAG);
        if ("true".equals(migrated)) {
            return;
        }

        // Check if we have legacy keys to migrate
        String legacyUrl = ConfigManager.getProperty(LEGACY_API_URL);
        String legacyKey = ConfigManager.getProperty(LEGACY_API_KEY);
        String legacyModel = ConfigManager.getProperty(LEGACY_API_MODEL);

        boolean hasLegacyConfig = (legacyUrl != null && !legacyUrl.isBlank()) ||
                                  (legacyKey != null && !legacyKey.isBlank()) ||
                                  (legacyModel != null && !legacyModel.isBlank());

        if (hasLegacyConfig) {
            performMigration(legacyUrl, legacyKey, legacyModel);
        } else {
            // New installation - set default mode to offline
            ensureDefaultMode();
        }

        // Mark migration as done
        ConfigManager.setProperty(MIGRATION_DONE_FLAG, "true");
    }

    /**
     * Performs the actual migration of legacy keys.
     */
    private static void performMigration(String legacyUrl, String legacyKey, String legacyModel) {
        // Migrate API settings to external.*
        if (legacyUrl != null && !legacyUrl.isBlank()) {
            String existingExtUrl = ConfigManager.getProperty(EXTERNAL_API_URL);
            if (existingExtUrl == null || existingExtUrl.isBlank()) {
                ConfigManager.setProperty(EXTERNAL_API_URL, legacyUrl);
            }
        }

        if (legacyKey != null && !legacyKey.isBlank()) {
            String existingExtKey = ConfigManager.getProperty(EXTERNAL_API_KEY);
            if (existingExtKey == null || existingExtKey.isBlank()) {
                ConfigManager.setProperty(EXTERNAL_API_KEY, legacyKey);
            }
        }

        if (legacyModel != null && !legacyModel.isBlank()) {
            String existingExtModel = ConfigManager.getProperty(EXTERNAL_API_MODEL);
            if (existingExtModel == null || existingExtModel.isBlank()) {
                ConfigManager.setProperty(EXTERNAL_API_MODEL, legacyModel);
            }
        }

        // Migrate image settings
        migrateProperty(LEGACY_IMAGE_MODEL, EXTERNAL_IMAGE_MODEL);
        migrateProperty(LEGACY_IMAGE_SIZE, EXTERNAL_IMAGE_SIZE);
        migrateProperty(LEGACY_IMAGE_ASPECT_RATIO, EXTERNAL_IMAGE_ASPECT_RATIO);
        migrateProperty(LEGACY_IMAGE_RESOLUTION, EXTERNAL_IMAGE_RESOLUTION);

        // Set default mode to offline (as per requirements)
        ensureDefaultMode();

        LOG.info("ai.config.migration.completed");
    }

    /**
     * Migrates a single property if the new one doesn't exist.
     */
    private static void migrateProperty(String legacyKey, String newKey) {
        String legacyValue = ConfigManager.getProperty(legacyKey);
        if (legacyValue != null && !legacyValue.isBlank()) {
            String existingValue = ConfigManager.getProperty(newKey);
            if (existingValue == null || existingValue.isBlank()) {
                ConfigManager.setProperty(newKey, legacyValue);
            }
        }
    }

    /**
     * Ensures the default mode is set to offline.
     */
    private static void ensureDefaultMode() {
        String currentMode = ConfigManager.getProperty(AI_MODE);
        if (currentMode == null || currentMode.isBlank()) {
            ConfigManager.setProperty(AI_MODE, AiMode.OFFLINE.getConfigValue());
        }
    }

    /**
     * Returns true if the application was using legacy configuration.
     * Useful for showing migration notifications to the user.
     */
    public static boolean hadLegacyConfig() {
        String legacyKey = ConfigManager.getProperty(LEGACY_API_KEY);
        return legacyKey != null && !legacyKey.isBlank();
    }

    /**
     * Returns true if migration was performed in this session.
     * Useful for showing migration notifications.
     */
    public static boolean wasMigrated() {
        return "true".equals(ConfigManager.getProperty(MIGRATION_DONE_FLAG));
    }
}
