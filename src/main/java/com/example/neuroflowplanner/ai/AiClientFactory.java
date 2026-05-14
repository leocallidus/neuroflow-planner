package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.util.ConfigManager;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory and runtime manager for AI clients.
 * 
 * Responsible for:
 * - Reading the current AI mode from configuration
 * - Creating the appropriate AiClient implementation
 * - Managing hot-reload of configuration without restart
 * - Providing a singleton instance of the active client
 * 
 * Usage:
 * 
 * <pre>
 *     AiClient client = AiClientFactory.getInstance().getActiveClient();
 *     client.sendChatMessage("Hello").thenAccept(response -> ...);
 * </pre>
 */
public class AiClientFactory {

    /**
     * Configuration key for the active AI mode.
     */
    public static final String CONFIG_AI_MODE = "ai.mode";

    /**
     * Singleton instance.
     */
    private static volatile AiClientFactory instance;
    private static final Object INSTANCE_LOCK = new Object();

    /**
     * Lock for thread-safe client access.
     */
    private final ReentrantReadWriteLock clientLock = new ReentrantReadWriteLock();

    /**
     * Current active mode and client.
     */
    private AiMode currentMode;
    private AiClient activeClient;

    /**
     * Cached client instances for each mode (for fast switching).
     */
    private OfflineAiClient offlineClient;
    private LocalOllamaClient localOllamaClient;
    private ExternalOpenAiClient externalOpenAiClient;

    /**
     * Private constructor for singleton.
     */
    private AiClientFactory() {
        initializeFromConfig();
    }

    /**
     * Returns the singleton instance.
     */
    public static AiClientFactory getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new AiClientFactory();
                }
            }
        }
        return instance;
    }

    /**
     * Returns the currently active AI client.
     */
    public AiClient getActiveClient() {
        clientLock.readLock().lock();
        try {
            return activeClient;
        } finally {
            clientLock.readLock().unlock();
        }
    }

    /**
     * Returns the current AI mode.
     */
    public AiMode getCurrentMode() {
        clientLock.readLock().lock();
        try {
            return currentMode;
        } finally {
            clientLock.readLock().unlock();
        }
    }

    /**
     * Returns true if the current mode supports image generation.
     */
    public boolean supportsImages() {
        return getActiveClient().supportsImages();
    }

    /**
     * Returns true if the current mode supports image inputs in chat (multimodal).
     */
    public boolean supportsImageInputs() {
        return getActiveClient().supportsImageInputs();
    }

    /**
     * Returns the ExternalOpenAiClient if available, or null.
     * Needed for image generation operations.
     */
    public ExternalOpenAiClient getExternalClient() {
        clientLock.readLock().lock();
        try {
            return externalOpenAiClient;
        } finally {
            clientLock.readLock().unlock();
        }
    }

    /**
     * Reloads configuration from ConfigManager and switches client if needed.
     * Call this after settings are changed.
     */
    public void reloadFromConfig() {
        clientLock.writeLock().lock();
        try {
            AiMode newMode = readModeFromConfig();

            if (newMode != currentMode) {
                // Mode changed - switch to new client
                switchToMode(newMode);
            } else {
                // Same mode - just reload the client's configuration
                if (activeClient != null) {
                    activeClient.reloadConfiguration();
                }
            }
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    /**
     * Switches to a specific mode.
     * Creates the client if it doesn't exist.
     */
    public void switchToMode(AiMode mode) {
        clientLock.writeLock().lock();
        try {
            currentMode = mode;
            activeClient = getOrCreateClient(mode);

            // Save the mode to config
            ConfigManager.setProperty(CONFIG_AI_MODE, mode.getConfigValue());
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    /**
     * Creates a temporary client for testing without affecting the active client.
     */
    public AiClient createTestClient(AiMode mode, String baseUrl, String apiKey) {
        return switch (mode) {
            case OFFLINE -> new OfflineAiClient();
            case LOCAL_OLLAMA -> new LocalOllamaClient(baseUrl, null);
            case EXTERNAL_OPENAI -> new ExternalOpenAiClient(baseUrl, apiKey, null);
        };
    }

    /**
     * Creates a temporary LocalOllamaClient for testing.
     */
    public LocalOllamaClient createTestOllamaClient(String baseUrl, String model) {
        return new LocalOllamaClient(baseUrl, model);
    }

    /**
     * Creates a temporary ExternalOpenAiClient for testing.
     */
    public ExternalOpenAiClient createTestExternalClient(String baseUrl, String apiKey, String model) {
        return new ExternalOpenAiClient(baseUrl, apiKey, model);
    }

    /**
     * Initializes the factory from current configuration.
     */
    private void initializeFromConfig() {
        currentMode = readModeFromConfig();
        activeClient = getOrCreateClient(currentMode);
    }

    /**
     * Reads the AI mode from configuration.
     */
    private AiMode readModeFromConfig() {
        String modeValue = ConfigManager.getProperty(CONFIG_AI_MODE);
        return AiMode.fromConfigValue(modeValue);
    }

    /**
     * Gets or creates a client for the specified mode.
     */
    private AiClient getOrCreateClient(AiMode mode) {
        AiClient baseClient = getOrCreateBaseClient(mode);
        return wrapWithFallback(baseClient, mode);
    }

    /**
     * Gets or creates a base client without fallback wrapping to prevent infinite
     * recursion.
     */
    private AiClient getOrCreateBaseClient(AiMode mode) {
        return switch (mode) {
            case OFFLINE -> {
                if (offlineClient == null) {
                    offlineClient = new OfflineAiClient();
                }
                yield offlineClient;
            }
            case LOCAL_OLLAMA -> {
                if (localOllamaClient == null) {
                    localOllamaClient = new LocalOllamaClient();
                } else {
                    localOllamaClient.reloadConfiguration();
                }
                yield localOllamaClient;
            }
            case EXTERNAL_OPENAI -> {
                if (externalOpenAiClient == null) {
                    externalOpenAiClient = new ExternalOpenAiClient();
                } else {
                    externalOpenAiClient.reloadConfiguration();
                }
                yield externalOpenAiClient;
            }
        };
    }

    /**
     * Wraps a base client in a ModeFallbackAiClientWrapper with predefined fallback
     * chains.
     */
    private AiClient wrapWithFallback(AiClient primaryClient, AiMode mode) {
        List<Supplier<AiClient>> fallbacks = new ArrayList<>();

        switch (mode) {
            case EXTERNAL_OPENAI:
                fallbacks.add(() -> getOrCreateBaseClient(AiMode.LOCAL_OLLAMA));
                fallbacks.add(() -> getOrCreateBaseClient(AiMode.OFFLINE));
                break;
            case LOCAL_OLLAMA:
                fallbacks.add(() -> getOrCreateBaseClient(AiMode.OFFLINE));
                break;
            case OFFLINE:
            default:
                // No fallbacks for offline
                break;
        }

        return new com.example.neuroflowplanner.ai.resilience.ModeFallbackAiClientWrapper(primaryClient, fallbacks);
    }

    /**
     * Closes all clients and cleans up resources.
     * Call on application shutdown.
     */
    public void shutdown() {
        clientLock.writeLock().lock();
        try {
            if (offlineClient != null) {
                offlineClient.close();
                offlineClient = null;
            }
            if (localOllamaClient != null) {
                localOllamaClient.close();
                localOllamaClient = null;
            }
            if (externalOpenAiClient != null) {
                externalOpenAiClient.close();
                externalOpenAiClient = null;
            }
            activeClient = null;
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    /**
     * Resets the singleton instance (for testing purposes).
     */
    static void resetInstance() {
        synchronized (INSTANCE_LOCK) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }
}
