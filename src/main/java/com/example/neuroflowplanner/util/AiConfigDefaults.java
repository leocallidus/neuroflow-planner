package com.example.neuroflowplanner.util;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public final class AiConfigDefaults {
    public static final String CONFIG_AI_JSON_PARSER_MODE = "ai.json.parser.mode";
    public static final String CONFIG_AI_JSON_SCHEMA_VALIDATION_ENABLED = "ai.json.schema.validation.enabled";
    public static final String CONFIG_AI_JSON_FAIL_ON_UNKNOWN_PROVIDER_PROPERTIES =
            "ai.json.parser.failOnUnknownProviderProperties";
    public static final String CONFIG_AI_JSON_FAIL_ON_UNKNOWN_UI_PROPERTIES =
            "ai.json.parser.failOnUnknownUiProperties";
    public static final String CONFIG_EXTERNAL_API_CUSTOM_MODELS = "external.api.customModels";
    public static final String CONFIG_EXTERNAL_API_DISCOVERED_MODELS = "external.api.discoveredModels";
    public static final String CONFIG_EXTERNAL_API_MULTIMODAL_MODELS = "external.api.multimodalModels";
    public static final String CONFIG_EXTERNAL_API_AUDIO_INPUT_MODELS = "external.api.audioInputModels";
    public static final String CONFIG_EXTERNAL_API_FILE_INPUT_MODELS = "external.api.fileInputModels";
    public static final String CONFIG_EXTERNAL_API_MODEL_CATALOG = "external.api.modelCatalog";
    public static final String CONFIG_EXTERNAL_IMAGE_CUSTOM_MODELS = "external.image.customModels";
    public static final String CONFIG_EXTERNAL_IMAGE_DISCOVERED_MODELS = "external.image.discoveredModels";
    public static final String CONFIG_ASSISTANT_DETAIL = "ai.assistant.detail";
    public static final String CONFIG_ASSISTANT_TONE = "ai.assistant.tone";
    public static final String CONFIG_ASSISTANT_REASONING_EFFORT = "ai.assistant.reasoningEffort";
    public static final String CONFIG_ASSISTANT_REASONING_MAX_TOKENS = "ai.assistant.reasoning.maxTokens";
    public static final String CONFIG_ASSISTANT_REASONING_SUMMARY = "ai.assistant.reasoning.summary";
    public static final String CONFIG_ASSISTANT_REASONING_EXCLUDE = "ai.assistant.reasoning.exclude";
    public static final String CONFIG_ASSISTANT_TEXT_MAX_TOKENS = "ai.assistant.text.maxTokens";
    public static final String CONFIG_ASSISTANT_TEXT_TEMPERATURE = "ai.assistant.text.temperature";
    public static final String CONFIG_ASSISTANT_TEXT_TOP_P = "ai.assistant.text.topP";
    public static final String CONFIG_ASSISTANT_TEXT_FREQUENCY_PENALTY = "ai.assistant.text.frequencyPenalty";
    public static final String CONFIG_ASSISTANT_TEXT_PRESENCE_PENALTY = "ai.assistant.text.presencePenalty";
    public static final String CONFIG_AI_PROMPT_CACHING_ENABLED = "ai.promptCaching.enabled";
    public static final String CONFIG_PLUGIN_WEB_ENABLED = "ai.plugin.web.enabled";
    public static final String CONFIG_PLUGIN_WEB_ENGINE = "ai.plugin.web.engine";
    public static final String CONFIG_PLUGIN_WEB_MAX_RESULTS = "ai.plugin.web.maxResults";
    public static final String CONFIG_PLUGIN_WEB_SEARCH_PROMPT = "ai.plugin.web.searchPrompt";
    public static final String CONFIG_PLUGIN_FILE_PARSER_ENABLED = "ai.plugin.file-parser.enabled";
    public static final String CONFIG_PLUGIN_FILE_PARSER_PDF_ENGINE = "ai.plugin.file-parser.pdf.engine";
    public static final String CONFIG_PLUGIN_RESPONSE_HEALING_ENABLED = "ai.plugin.response-healing.enabled";
    public static final String CONFIG_CHAT_CONTEXT_PANEL_EXPANDED = "ai.chat.contextPanel.expanded";
    public static final String CONFIG_DAILY_REVIEW_PERSISTED = "daily.review.persisted";
    public static final String CONFIG_FOCUS_BLOCKS_PERSISTED = "focus.blocks.persisted";
    public static final String CONFIG_PLANNING_QUALITY_PERSISTED = "planning.quality.persisted";

    public static final String JSON_PARSER_MODE_LEGACY = "legacy";
    public static final String JSON_PARSER_MODE_DUAL = "dual";
    public static final String JSON_PARSER_MODE_JACKSON = "jackson";
    public static final String JSON_PARSER_MODE_DEFAULT = JSON_PARSER_MODE_LEGACY;

    public static final boolean JSON_SCHEMA_VALIDATION_ENABLED = true;
    public static final boolean JSON_FAIL_ON_UNKNOWN_PROVIDER_PROPERTIES = false;
    public static final boolean JSON_FAIL_ON_UNKNOWN_UI_PROPERTIES = true;

    public static final String DEFAULT_API_URL = "https://api.artemox.com/v1";
    public static final String DEFAULT_MODEL = "deepseek-chat";
    public static final String ASSISTANT_DETAIL_BRIEF = "brief";
    public static final String ASSISTANT_DETAIL_DETAILED = "detailed";
    public static final String ASSISTANT_TONE_FORMAL = "formal";
    public static final String ASSISTANT_TONE_FRIENDLY = "friendly";
    public static final String ASSISTANT_REASONING_LOW = "low";
    public static final String ASSISTANT_REASONING_MEDIUM = "medium";
    public static final String ASSISTANT_REASONING_HIGH = "high";
    public static final String ASSISTANT_REASONING_NONE = "none";
    public static final String ASSISTANT_REASONING_MINIMAL = "minimal";
    public static final String ASSISTANT_REASONING_XHIGH = "xhigh";
    public static final String ASSISTANT_REASONING_SUMMARY_AUTO = "auto";
    public static final String ASSISTANT_REASONING_SUMMARY_CONCISE = "concise";
    public static final String ASSISTANT_REASONING_SUMMARY_DETAILED = "detailed";
    public static final String PLUGIN_WEB_ENGINE_AUTO = "auto";
    public static final String PLUGIN_WEB_ENGINE_NATIVE = "native";
    public static final String PLUGIN_WEB_ENGINE_EXA = "exa";
    public static final String PLUGIN_FILE_PARSER_PDF_ENGINE_TEXT = "pdf-text";
    public static final String PLUGIN_FILE_PARSER_PDF_ENGINE_MISTRAL_OCR = "mistral-ocr";
    public static final String PLUGIN_FILE_PARSER_PDF_ENGINE_NATIVE = "native";
    public static final String DEFAULT_ASSISTANT_DETAIL = ASSISTANT_DETAIL_BRIEF;
    public static final String DEFAULT_ASSISTANT_TONE = ASSISTANT_TONE_FRIENDLY;
    public static final String DEFAULT_ASSISTANT_REASONING_EFFORT = ASSISTANT_REASONING_MEDIUM;
    public static final String DEFAULT_ASSISTANT_REASONING_SUMMARY = ASSISTANT_REASONING_SUMMARY_AUTO;
    public static final boolean DEFAULT_ASSISTANT_REASONING_EXCLUDE = true;
    public static final Integer DEFAULT_ASSISTANT_TEXT_MAX_TOKENS = null;
    public static final Double DEFAULT_ASSISTANT_TEXT_TEMPERATURE = null;
    public static final Double DEFAULT_ASSISTANT_TEXT_TOP_P = null;
    public static final Double DEFAULT_ASSISTANT_TEXT_FREQUENCY_PENALTY = null;
    public static final Double DEFAULT_ASSISTANT_TEXT_PRESENCE_PENALTY = null;
    public static final boolean AI_PROMPT_CACHING_ENABLED = true;
    public static final boolean DEFAULT_PLUGIN_WEB_ENABLED = false;
    public static final String DEFAULT_PLUGIN_WEB_ENGINE = PLUGIN_WEB_ENGINE_AUTO;
    public static final int DEFAULT_PLUGIN_WEB_MAX_RESULTS = 5;
    public static final String DEFAULT_PLUGIN_WEB_SEARCH_PROMPT = "";
    public static final boolean DEFAULT_PLUGIN_FILE_PARSER_ENABLED = false;
    public static final String DEFAULT_PLUGIN_FILE_PARSER_PDF_ENGINE = PLUGIN_FILE_PARSER_PDF_ENGINE_TEXT;
    public static final boolean DEFAULT_PLUGIN_RESPONSE_HEALING_ENABLED = false;
    public static final boolean DEFAULT_CHAT_CONTEXT_PANEL_EXPANDED = false;

    // --- AI Resilience Desktop Defaults ---
    public static final long REQUEST_CONNECT_TIMEOUT_MS = 10000;
    public static final long REQUEST_READ_TIMEOUT_MS = 60000;
    public static final long REQUEST_TOTAL_BUDGET_MS = 180000;
    public static final long REQUEST_HEARTBEAT_INTERVAL_MS = 2500;
    public static final int RETRY_MAX_ATTEMPTS = 3;
    public static final long RETRY_BASE_DELAY_MS = 2000;
    public static final long RETRY_MAX_DELAY_MS = 10000;
    public static final double RETRY_JITTER_RATIO = 0.2;
    public static final int CONCURRENT_MAX_IN_FLIGHT = 5;
    public static final long CONCURRENT_ACQUIRE_TIMEOUT_MS = 5000;
    public static final boolean FALLBACK_MODE_ENABLED = false;
    public static final String FALLBACK_MODELS = "deepseek-chat,gpt-4o-mini,llama-3-8b";
    public static final boolean CONTINUATION_ENABLED = true;
    public static final int CONTINUATION_MAX_STEPS = 1;
    public static final int CONTINUATION_MIN_PARTIAL_CHARS = 160;
    public static final int CONTINUATION_PROMPT_MAX_CHARS = 2200;

    // --- Image Generation Anti-timeout Defaults ---
    public static final long IMAGE_REQUEST_TOTAL_BUDGET_MS = 300000;
    public static final long IMAGE_REQUEST_HEARTBEAT_INTERVAL_MS = 2500;
    public static final int IMAGE_SUBMIT_MAX_ATTEMPTS = 2;
    public static final int IMAGE_POLL_MAX_ATTEMPTS = 3;
    public static final int IMAGE_DOWNLOAD_MAX_ATTEMPTS = 3;
    public static final long IMAGE_RETRY_BASE_DELAY_MS = 1250;
    public static final long IMAGE_RETRY_MAX_DELAY_MS = 8000;
    public static final long IMAGE_POLL_INITIAL_DELAY_MS = 900;
    public static final long IMAGE_POLL_MAX_DELAY_MS = 6000;
    public static final double IMAGE_POLL_JITTER_RATIO = 0.2;
    public static final boolean IMAGE_FALLBACK_MODE_ENABLED = true;
    public static final String IMAGE_FALLBACK_MODELS = String.join(",",
            ImageGenConfigDefaults.MODEL_NANO_BANANA,
            ImageGenConfigDefaults.MODEL_GEMINI_3_PRO_IMAGE_PREVIEW,
            ImageGenConfigDefaults.MODEL_GEMINI_3_1_FLASH_IMAGE_PREVIEW,
            ImageGenConfigDefaults.MODEL_SEEDREAM_V4_5,
            ImageGenConfigDefaults.MODEL_SEEDREAM_5_LITE,
            ImageGenConfigDefaults.MODEL_Z_IMAGE,
            ImageGenConfigDefaults.MODEL_GROK_IMAGINE_IMAGE,
            ImageGenConfigDefaults.MODEL_FLUX_2_PRO,
            ImageGenConfigDefaults.MODEL_FLUX_2_FLEX,
            ImageGenConfigDefaults.MODEL_GPT_5_IMAGE);

    public static final List<String> MODEL_OPTIONS = List.of(
            "deepseek-chat",
            "kimi-k2.5",
            "glm-4.7-flash",
            "glm-4.7",
            "deepseek-v3.2",
            "grok-4-fast",
            "deepseek-chat-v3.1",
            "gpt-5-mini",
            "gpt-oss-120b",
            "mistral-large-2512",
            "mistral-7b-instruct-v0.3",
            "qwen3-235b-a22b-thinking-2507",
            "llama-3.1-70b-instruct",
            "claude-haiku-4.5",
            "gemini-3-flash-preview");
    public static final List<String> ASSISTANT_REASONING_OPTIONS = List.of(
            ASSISTANT_REASONING_NONE,
            ASSISTANT_REASONING_MINIMAL,
            ASSISTANT_REASONING_LOW,
            ASSISTANT_REASONING_MEDIUM,
            ASSISTANT_REASONING_HIGH,
            ASSISTANT_REASONING_XHIGH);
    public static final List<String> ASSISTANT_REASONING_SUMMARY_OPTIONS = List.of(
            ASSISTANT_REASONING_SUMMARY_AUTO,
            ASSISTANT_REASONING_SUMMARY_CONCISE,
            ASSISTANT_REASONING_SUMMARY_DETAILED);
    public static final List<String> PLUGIN_WEB_ENGINE_OPTIONS = List.of(
            PLUGIN_WEB_ENGINE_AUTO,
            PLUGIN_WEB_ENGINE_NATIVE,
            PLUGIN_WEB_ENGINE_EXA);
    public static final List<String> PLUGIN_FILE_PARSER_PDF_ENGINE_OPTIONS = List.of(
            PLUGIN_FILE_PARSER_PDF_ENGINE_TEXT,
            PLUGIN_FILE_PARSER_PDF_ENGINE_MISTRAL_OCR,
            PLUGIN_FILE_PARSER_PDF_ENGINE_NATIVE);

    public static boolean isSupportedModel(String model) {
        if (model == null) {
            return false;
        }
        String normalized = model.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return MODEL_OPTIONS.contains(normalized);
    }

    public static String normalizeModel(String model) {
        if (isSupportedModel(model)) {
            return model.trim();
        }
        return DEFAULT_MODEL;
    }

    public static String normalizeExternalModelId(String modelId) {
        if (modelId == null) {
            return "";
        }
        return modelId.trim();
    }

    public static String normalizeAssistantReasoningEffort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_ASSISTANT_REASONING_EFFORT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case ASSISTANT_REASONING_NONE,
                    ASSISTANT_REASONING_MINIMAL,
                    ASSISTANT_REASONING_LOW,
                    ASSISTANT_REASONING_MEDIUM,
                    ASSISTANT_REASONING_HIGH,
                    ASSISTANT_REASONING_XHIGH -> normalized;
            default -> DEFAULT_ASSISTANT_REASONING_EFFORT;
        };
    }

    public static String normalizeAssistantReasoningSummary(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_ASSISTANT_REASONING_SUMMARY;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case ASSISTANT_REASONING_SUMMARY_AUTO,
                    ASSISTANT_REASONING_SUMMARY_CONCISE,
                    ASSISTANT_REASONING_SUMMARY_DETAILED -> normalized;
            default -> DEFAULT_ASSISTANT_REASONING_SUMMARY;
        };
    }

    public static String normalizePluginWebEngine(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PLUGIN_WEB_ENGINE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case PLUGIN_WEB_ENGINE_AUTO,
                    PLUGIN_WEB_ENGINE_NATIVE,
                    PLUGIN_WEB_ENGINE_EXA -> normalized;
            default -> DEFAULT_PLUGIN_WEB_ENGINE;
        };
    }

    public static int normalizePluginWebMaxResults(Integer value) {
        if (value == null) {
            return DEFAULT_PLUGIN_WEB_MAX_RESULTS;
        }
        return Math.max(1, Math.min(20, value));
    }

    public static String normalizePluginWebSearchPrompt(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PLUGIN_WEB_SEARCH_PROMPT;
        }
        return value.trim();
    }

    public static String normalizePluginFileParserPdfEngine(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PLUGIN_FILE_PARSER_PDF_ENGINE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case PLUGIN_FILE_PARSER_PDF_ENGINE_TEXT,
                    PLUGIN_FILE_PARSER_PDF_ENGINE_MISTRAL_OCR,
                    PLUGIN_FILE_PARSER_PDF_ENGINE_NATIVE -> normalized;
            default -> DEFAULT_PLUGIN_FILE_PARSER_PDF_ENGINE;
        };
    }

    public static String toLegacyReasoningEffort(String value) {
        String normalized = normalizeAssistantReasoningEffort(value);
        return switch (normalized) {
            case ASSISTANT_REASONING_NONE -> null;
            case ASSISTANT_REASONING_MINIMAL -> ASSISTANT_REASONING_LOW;
            case ASSISTANT_REASONING_XHIGH -> ASSISTANT_REASONING_HIGH;
            default -> normalized;
        };
    }

    public static Integer normalizeAssistantTextMaxTokens(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return Math.min(value, 200_000);
    }

    public static Double normalizeAssistantTextTemperature(Double value) {
        return normalizeDoubleInRange(value, 0.0, 2.0);
    }

    public static Double normalizeAssistantTextTopP(Double value) {
        return normalizeDoubleInRange(value, 0.0, 1.0);
    }

    public static Double normalizeAssistantTextFrequencyPenalty(Double value) {
        return normalizeDoubleInRange(value, -2.0, 2.0);
    }

    public static Double normalizeAssistantTextPresencePenalty(Double value) {
        return normalizeDoubleInRange(value, -2.0, 2.0);
    }

    private static Double normalizeDoubleInRange(Double value, double min, double max) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static boolean supportsReasoningEffort(String modelId) {
        String normalized = normalizeExternalModelId(modelId).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        String family = normalized;
        int slashIndex = family.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < family.length() - 1) {
            family = family.substring(slashIndex + 1);
        }
        return family.startsWith("gpt-5")
                || family.startsWith("o1")
                || family.startsWith("o3")
                || family.startsWith("o4")
                || family.startsWith("deepseek-r1")
                || family.startsWith("qwq")
                || family.contains("thinking")
                || family.contains("reason");
    }

    public static boolean supportsManualPromptCaching(String modelId) {
        String normalized = normalizeExternalModelId(modelId).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        String family = normalized;
        int slashIndex = family.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < family.length() - 1) {
            family = family.substring(slashIndex + 1);
        }
        return family.startsWith("claude") || normalized.contains("/claude");
    }

    public static boolean supportsStructuredReasoning(String modelId) {
        String normalized = normalizeExternalModelId(modelId).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        String family = normalized;
        int slashIndex = family.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < family.length() - 1) {
            family = family.substring(slashIndex + 1);
        }
        return family.startsWith("o1")
                || family.startsWith("o3")
                || family.startsWith("o4")
                || family.startsWith("claude")
                || family.startsWith("deepseek-r1")
                || family.startsWith("grok")
                || family.startsWith("t-pro")
                || family.contains("gemini") && family.contains("thinking")
                || normalized.contains("/claude")
                || normalized.contains("/deepseek-r1")
                || normalized.contains("/grok")
                || normalized.contains("/gemini") && normalized.contains("thinking");
    }

    public static List<String> mergeExternalModelOptions(
            List<String> discoveredModels,
            List<String> customModels,
            String activeModel) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addNonBlank(merged, activeModel);
        addAllNonBlank(merged, customModels);
        addAllNonBlank(merged, discoveredModels);
        addAllNonBlank(merged, MODEL_OPTIONS);
        return List.copyOf(new ArrayList<>(merged));
    }

    public static List<String> mergeImageModelOptions(
            List<String> discoveredModels,
            List<String> customModels,
            String activeModel) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addNonBlank(merged, activeModel);
        addAllNonBlank(merged, customModels);
        addAllNonBlank(merged, discoveredModels);
        addAllNonBlank(merged, ImageGenConfigDefaults.IMAGE_MODEL_OPTIONS);
        return List.copyOf(new ArrayList<>(merged));
    }

    private static void addAllNonBlank(LinkedHashSet<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addNonBlank(target, value);
        }
    }

    private static void addNonBlank(LinkedHashSet<String> target, String value) {
        String normalized = normalizeExternalModelId(value);
        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }

    private AiConfigDefaults() {
    }
}
