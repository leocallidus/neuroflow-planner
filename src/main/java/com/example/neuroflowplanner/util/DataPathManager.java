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
    
    private static final String DATA_DIR_NAME = "neuroflow_data";
    private static final String DB_FILE_NAME = "neuroflow.db";
    private static final String CONFIG_FILE_NAME = "config.properties";
    
    private static Path dataDir;
    
    static {
        initDataDirectory();
    }
    
    private static void initDataDirectory() {
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
        
        // Create data directory if it doesn't exist
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            System.err.println("Failed to create data directory: " + e.getMessage());
            // Fallback to current directory
            dataDir = Path.of(DATA_DIR_NAME);
            try {
                Files.createDirectories(dataDir);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
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
        return dataDir.resolve(DB_FILE_NAME);
    }
    
    /**
     * Get the JDBC URL for the database.
     */
    public static String getDatabaseUrl() {
        return "jdbc:sqlite:" + getDatabasePath().toAbsolutePath();
    }
    
    /**
     * Get the path to the config file.
     */
    public static Path getConfigPath() {
        return dataDir.resolve(CONFIG_FILE_NAME);
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
                    System.out.println("Created config file: " + configPath);
                }
            } catch (IOException e) {
                System.err.println("Failed to create default config: " + e.getMessage());
            }
        }
    }
}
