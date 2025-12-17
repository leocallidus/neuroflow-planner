package com.example.neuroflowplanner.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConfigManager {
    private static final Properties properties = new Properties();
    private static Path configPath;

    static {
        initConfig();
    }

    private static void initConfig() {
        // Initialize data directory and config path
        DataPathManager.initConfigIfNeeded();
        configPath = DataPathManager.getConfigPath();
        loadProperties();
    }

    private static void loadProperties() {
        // First, load defaults from resources
        try (InputStream defaultProps = ConfigManager.class.getResourceAsStream("/config.properties")) {
            if (defaultProps != null) {
                properties.load(defaultProps);
            }
        } catch (IOException e) {
            System.err.println("Could not load default config from resources.");
        }
        
        // Then override with user config if exists
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                properties.load(in);
            } catch (IOException e) {
                System.err.println("Could not load config from: " + configPath);
            }
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static void setProperty(String key, String value) {
        properties.setProperty(key, value);
        saveProperties();
    }

    private static void saveProperties() {
        try (OutputStream out = Files.newOutputStream(configPath)) {
            properties.store(out, "NeuroFlow Planner Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isDarkTheme() {
        return Boolean.parseBoolean(properties.getProperty("app.theme.dark", "false"));
    }

    public static void setDarkTheme(boolean isDark) {
        setProperty("app.theme.dark", String.valueOf(isDark));
    }
    
    /**
     * Get the path to the data directory for display purposes.
     */
    public static String getDataDirectoryPath() {
        return DataPathManager.getDataDirectory().toAbsolutePath().toString();
    }
}
