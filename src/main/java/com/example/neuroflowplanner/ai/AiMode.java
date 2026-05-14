package com.example.neuroflowplanner.ai;

/**
 * Enum representing the three AI operation modes.
 * 
 * <ul>
 *   <li>{@link #OFFLINE} - AI features disabled, uses stub/mock responses</li>
 *   <li>{@link #LOCAL_OLLAMA} - Local Ollama server for AI requests</li>
 *   <li>{@link #EXTERNAL_OPENAI} - External OpenAI-compatible API</li>
 * </ul>
 */
public enum AiMode {

    /**
     * Offline mode - AI features are disabled.
     * Uses stub/mock responses when AI is not available.
     */
    OFFLINE("offline", "Офлайн (без ИИ)"),

    /**
     * Local Ollama mode - uses a local Ollama server.
     * User provides IP/URL of the local server.
     */
    LOCAL_OLLAMA("local", "Локальный (Ollama)"),

    /**
     * External OpenAI-compatible API mode.
     * User provides URL and API key.
     * Supports image generation.
     */
    EXTERNAL_OPENAI("external", "Внешний API (OpenAI-совместимый)");

    private final String configValue;
    private final String displayName;

    AiMode(String configValue, String displayName) {
        this.configValue = configValue;
        this.displayName = displayName;
    }

    /**
     * Returns the value used in configuration files.
     * @return config value (e.g., "offline", "local", "external")
     */
    public String getConfigValue() {
        return configValue;
    }

    /**
     * Returns the human-readable display name for UI.
     * @return display name in Russian
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if this mode supports image generation.
     * Only EXTERNAL_OPENAI mode supports image generation.
     * @return true if image generation is supported
     */
    public boolean supportsImageGeneration() {
        return this == EXTERNAL_OPENAI;
    }

    /**
     * Returns true if this mode requires a network connection.
     * @return true if network is required
     */
    public boolean requiresNetwork() {
        return this != OFFLINE;
    }

    /**
     * Returns true if this mode requires an API key.
     * @return true if API key is required
     */
    public boolean requiresApiKey() {
        return this == EXTERNAL_OPENAI;
    }

    /**
     * Parses a config value string to an AiMode.
     * @param configValue the value from config (e.g., "offline", "local", "external")
     * @return the corresponding AiMode, defaults to OFFLINE if not found
     */
    public static AiMode fromConfigValue(String configValue) {
        if (configValue == null || configValue.isBlank()) {
            return OFFLINE;
        }
        String normalized = configValue.trim().toLowerCase();
        for (AiMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        return OFFLINE;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
