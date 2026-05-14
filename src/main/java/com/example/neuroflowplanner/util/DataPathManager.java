package com.example.neuroflowplanner.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Manages data directory and file paths for the application.
 * All data files (database, config) are stored in neuroflow_data directory
 * next to the JAR file or in the working directory.
 */
public class DataPathManager {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DataPathManager.class);

    // System property / environment overrides used by tests and CI.
    public static final String PROP_DATA_DIR = "neuroflow.data.dir";
    public static final String PROP_DB_URL = "neuroflow.db.url";
    public static final String PROP_DB_PATH = "neuroflow.db.path";
    public static final String ENV_DATA_DIR = "NEUROFLOW_DATA_DIR";
    public static final String ENV_DB_URL = "NEUROFLOW_DB_URL";
    public static final String ENV_DB_PATH = "NEUROFLOW_DB_PATH";

    private static final String DATA_DIR_NAME = "neuroflow_data";
    private static final String DB_FILE_NAME = "neuroflow.db";
    private static final String CONFIG_FILE_NAME = "config.properties";
    private static final String IMAGES_DIR_NAME = "images";
    private static final String CHAT_UPLOADS_DIR_NAME = "chat_uploads";

    private static volatile Path dataDir;

    static {
        initDataDirectory();
    }

    private static synchronized void initDataDirectory() {
        Path configured = resolveConfiguredDataDir();
        if (configured != null) {
            dataDir = configured;
            ensureDataDirectoryExists();
            return;
        }

        // Get the directory where the JAR is located or working directory
        Path baseDir;
        try {
            // Try to get JAR location
            String jarPath = DataPathManager.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            if (jarFile.isFile()) {
                baseDir = jarFile.getParentFile().toPath();
            } else {
                // Running from IDE or classes directory
                baseDir = Path.of(System.getProperty("user.dir"));
            }
        } catch (Exception e) {
            baseDir = Path.of(System.getProperty("user.dir"));
        }

        dataDir = baseDir.resolve(DATA_DIR_NAME);

        ensureDataDirectoryExists();
    }

    /**
     * Test-only seam: recompute data paths from current runtime overrides.
     * Call this before creating services/singletons that cache paths.
     */
    public static synchronized void reinitializeForTesting() {
        initDataDirectory();
        LOG.info(
            "data.dir.reinitialized.for.testing",
            "dataDir", dataDir,
            "property", PROP_DATA_DIR,
            "environment", ENV_DATA_DIR
        );
    }

    private static void ensureDataDirectoryExists() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOG.error("data.dir.create.failed", e, "dataDir", dataDir);
            // Fallback to current directory
            dataDir = Path.of(DATA_DIR_NAME);
            try {
                Files.createDirectories(dataDir);
            } catch (IOException ex) {
                LOG.error("data.dir.fallback.create.failed", ex, "dataDir", dataDir);
            }
        }
    }

    private static Path resolveConfiguredDataDir() {
        String override = firstNonBlank(System.getProperty(PROP_DATA_DIR), System.getenv(ENV_DATA_DIR));
        if (!hasText(override)) {
            return null;
        }
        try {
            Path resolved = Path.of(override.trim()).toAbsolutePath().normalize();
            LOG.info("data.dir.override.active", "dataDir", resolved, "property", PROP_DATA_DIR, "environment", ENV_DATA_DIR);
            return resolved;
        } catch (Exception ex) {
            LOG.error("data.dir.override.invalid", ex, "value", override, "property", PROP_DATA_DIR, "environment", ENV_DATA_DIR);
            return null;
        }
    }

    private static String resolveConfiguredDbUrl() {
        String override = firstNonBlank(System.getProperty(PROP_DB_URL), System.getenv(ENV_DB_URL));
        if (!hasText(override)) {
            return null;
        }
        String trimmed = override.trim();
        if (trimmed.startsWith("jdbc:")) {
            return trimmed;
        }
        try {
            return "jdbc:sqlite:" + Path.of(trimmed).toAbsolutePath().normalize();
        } catch (Exception ex) {
            LOG.error("db.url.override.invalid", ex, "value", override, "property", PROP_DB_URL, "environment", ENV_DB_URL);
            return null;
        }
    }

    private static Path resolveConfiguredDbPath() {
        String override = firstNonBlank(System.getProperty(PROP_DB_PATH), System.getenv(ENV_DB_PATH));
        if (!hasText(override)) {
            return null;
        }
        try {
            return Path.of(override.trim()).toAbsolutePath().normalize();
        } catch (Exception ex) {
            LOG.error("db.path.override.invalid", ex, "value", override, "property", PROP_DB_PATH, "environment", ENV_DB_PATH);
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (hasText(primary)) {
            return primary;
        }
        if (hasText(fallback)) {
            return fallback;
        }
        return null;
    }

    /**
     * Get the path to the data directory.
     */
    public static Path getDataDirectory() {
        return dataDir;
    }
    
    /**
     * Get the path to the database file.
     */
    public static Path getDatabasePath() {
        Path dbPathOverride = resolveConfiguredDbPath();
        if (dbPathOverride != null) {
            return dbPathOverride;
        }
        return dataDir.resolve(DB_FILE_NAME);
    }

    /**
     * Get the JDBC URL for the database.
     *
     * Priority:
     * 1) `neuroflow.db.url` / `NEUROFLOW_DB_URL`
     * 2) `neuroflow.db.path` / `NEUROFLOW_DB_PATH`
     * 3) `neuroflow.data.dir` / `NEUROFLOW_DATA_DIR` + default DB file name
     * 4) default runtime `neuroflow_data/neuroflow.db`
     */
    public static String getDatabaseUrl() {
        String dbUrlOverride = resolveConfiguredDbUrl();
        if (dbUrlOverride != null) {
            return dbUrlOverride;
        }
        return "jdbc:sqlite:" + getDatabasePath().toAbsolutePath();
    }

    /**
     * Get the path to the config file.
     */
    public static Path getConfigPath() {
        return dataDir.resolve(CONFIG_FILE_NAME);
    }

    /**
     * Get the path to the images directory. Ensures the directory exists.
     */
    public static Path getImagesDirectory() {
        Path imagesDir = dataDir.resolve(IMAGES_DIR_NAME);
        try {
            Files.createDirectories(imagesDir);
        } catch (IOException e) {
            LOG.error("images.dir.create.failed", e, "imagesDir", imagesDir);
        }
        return imagesDir;
    }

    /**
     * Get the path to the chat uploads directory. Ensures the directory exists.
     * Used for storing user-attached images for the assistant.
     */
    public static Path getChatUploadsDirectory() {
        Path uploadsDir = dataDir.resolve(CHAT_UPLOADS_DIR_NAME);
        try {
            Files.createDirectories(uploadsDir);
        } catch (IOException e) {
            LOG.error("chat.uploads.dir.create.failed", e, "uploadsDir", uploadsDir);
        }
        return uploadsDir;
    }
    
    /**
     * Initialize config file with defaults from resources if it doesn't exist.
     */
    public static void initConfigIfNeeded() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            try (InputStream defaultConfig = DataPathManager.class
                    .getResourceAsStream("/config.properties")) {
                if (defaultConfig != null) {
                    Files.copy(defaultConfig, configPath, StandardCopyOption.REPLACE_EXISTING);
                    LOG.info("config.file.created", "configPath", configPath);
                }
            } catch (IOException e) {
                LOG.error("config.file.create.failed", e, "configPath", configPath);
            }
        }
    }
}
