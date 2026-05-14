package com.example.neuroflowplanner.service.imagecapability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for image generation models and their supported parameters.
 */
public final class ImageModelCapabilityRegistry {

    public static final String MODEL_NANO_BANANA = "google/gemini-2.5-flash-image";
    public static final String MODEL_GEMINI_3_PRO_IMAGE_PREVIEW = "google/gemini-3-pro-image-preview";
    public static final String MODEL_GEMINI_3_1_FLASH_IMAGE_PREVIEW = "google/gemini-3.1-flash-image-preview";
    public static final String MODEL_SEEDREAM_3 = "bytedance/seedream";
    public static final String MODEL_SEEDREAM_4 = "bytedance/seedream-4";
    public static final String MODEL_SEEDREAM_V4_5 = "bytedance/seedream-4.5";
    public static final String MODEL_SEEDREAM_5_LITE = "bytedance/seedream-5-lite";
    public static final String MODEL_QWEN_IMAGE = "qwen/image";
    public static final String MODEL_Z_IMAGE = MODEL_QWEN_IMAGE;
    public static final String MODEL_GROK_IMAGINE_IMAGE = "x-ai/grok-imagine-image";
    public static final String MODEL_FLUX_2_PRO = "black-forest-labs/flux.2-pro";
    public static final String MODEL_FLUX_2_FLEX = "black-forest-labs/flux.2-flex";
    public static final String MODEL_GPT_5_4_IMAGE_2 = "openai/gpt-5.4-image-2";
    public static final String MODEL_GPT_5_IMAGE = "openai/gpt-5-image";
    public static final String MODEL_GPT_5_IMAGE_MINI = "openai/gpt-5-image-mini";
    public static final String MODEL_GPT_IMAGE_1_5 = "openai/gpt-image-1.5";

    private static final List<String> NANO_ASPECT_RATIO_OPTIONS = List.of("1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9", "21:9");
    private static final List<String> NANO_PRO_ASPECT_RATIO_OPTIONS = List.of("auto", "1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9", "21:9");
    private static final List<String> SEEDREAM_3_ASPECT_RATIO_OPTIONS = List.of("1:1", "4:3", "3:4", "16:9", "9:16");
    private static final List<String> SEEDREAM_ASPECT_RATIO_OPTIONS = List.of("1:1", "2:3", "3:2", "3:4", "4:3", "9:16", "16:9", "21:9");
    private static final List<String> FLUX_ASPECT_RATIO_OPTIONS = List.of("1:1", "2:3", "3:2", "3:4", "4:3", "9:16", "16:9");
    private static final List<String> GROK_ASPECT_RATIO_OPTIONS = List.of("1:1", "3:2", "2:3", "4:3", "3:4", "16:9", "9:16");
    private static final List<String> GPT_5_4_IMAGE_ASPECT_RATIO_OPTIONS = List.of("auto", "1:1", "5:4", "9:16", "21:9", "16:9", "4:3", "3:2", "4:5", "3:4", "2:3");
    private static final List<String> GPT_IMAGE_ASPECT_RATIO_OPTIONS = List.of("1:1", "2:3", "3:2");
    private static final List<String> RESOLUTION_OPTIONS = List.of("1K", "2K", "4K");
    private static final List<String> FLUX_RESOLUTION_OPTIONS = List.of("1K", "2K");
    private static final List<String> NANO_OUTPUT_FORMAT_OPTIONS = List.of("png", "jpeg");
    private static final List<String> QUALITY_BASIC_HIGH_OPTIONS = List.of("basic", "high");
    private static final List<String> QUALITY_MEDIUM_HIGH_OPTIONS = List.of("medium", "high");

    private static final String DEFAULT_MODEL = MODEL_NANO_BANANA;
    private static final String DEFAULT_GEMINI_ASPECT_RATIO = "1:1";
    private static final String DEFAULT_FLUX_ASPECT_RATIO = "1:1";
    private static final String DEFAULT_GROK_ASPECT_RATIO = "1:1";
    private static final String DEFAULT_RESOLUTION = "1K";
    private static final String DEFAULT_OUTPUT_FORMAT = "png";
    private static final String DEFAULT_QUALITY_BASIC = "basic";
    private static final String DEFAULT_QUALITY_MEDIUM = "medium";

    private static final ImageModelCapabilityRegistry INSTANCE = new ImageModelCapabilityRegistry();

    private final Map<String, ImageModelCapability> capabilities;
    private final Map<String, String> canonicalModelIds;
    private final List<String> supportedModels;

    private ImageModelCapabilityRegistry() {
        LinkedHashMap<String, ImageModelCapability> registry = new LinkedHashMap<>();
        LinkedHashMap<String, String> canonicalIds = new LinkedHashMap<>();

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_NANO_BANANA)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", NANO_ASPECT_RATIO_OPTIONS, DEFAULT_GEMINI_ASPECT_RATIO)
            .support(ImageCapabilityField.OUTPUT_FORMAT, "output_format", NANO_OUTPUT_FORMAT_OPTIONS, DEFAULT_OUTPUT_FORMAT)
            .documentedParameters("prompt", "aspect_ratio", "images", "output_format", "n")
            .build(), "nano-banana");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_GEMINI_3_PRO_IMAGE_PREVIEW)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", NANO_PRO_ASPECT_RATIO_OPTIONS, DEFAULT_GEMINI_ASPECT_RATIO)
            .support(ImageCapabilityField.RESOLUTION, "image_resolution", RESOLUTION_OPTIONS, DEFAULT_RESOLUTION)
            .support(ImageCapabilityField.OUTPUT_FORMAT, "output_format", NANO_OUTPUT_FORMAT_OPTIONS, DEFAULT_OUTPUT_FORMAT)
            .documentedParameters("prompt", "aspect_ratio", "image_resolution", "images", "output_format")
            .build(), "gemini-3-pro-image-preview", "nano-banana-pro");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_GEMINI_3_1_FLASH_IMAGE_PREVIEW)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", NANO_ASPECT_RATIO_OPTIONS, DEFAULT_GEMINI_ASPECT_RATIO)
            .support(ImageCapabilityField.RESOLUTION, "image_resolution", RESOLUTION_OPTIONS, DEFAULT_RESOLUTION)
            .support(ImageCapabilityField.OUTPUT_FORMAT, "output_format", NANO_OUTPUT_FORMAT_OPTIONS, DEFAULT_OUTPUT_FORMAT)
            .documentedParameters("prompt", "aspect_ratio", "image_resolution", "images", "output_format")
            .build(), "gemini-3.1-flash-image-preview", "nano-banana-2");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_SEEDREAM_3)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", SEEDREAM_3_ASPECT_RATIO_OPTIONS, DEFAULT_GEMINI_ASPECT_RATIO)
            .support(ImageCapabilityField.GUIDANCE_SCALE, "guidance_scale", List.of(), "2.5")
            .fixedPayloadField("enable_safety_checker", "true")
            .documentedParameters("prompt", "aspect_ratio", "seed", "guidance_scale", "enable_safety_checker")
            .build(), "seedream", "seedream-3", "seedream-3.0", "bytedance/seedream-3");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_SEEDREAM_4)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", SEEDREAM_ASPECT_RATIO_OPTIONS, DEFAULT_GEMINI_ASPECT_RATIO)
            .support(ImageCapabilityField.RESOLUTION, "image_resolution", RESOLUTION_OPTIONS, DEFAULT_RESOLUTION)
            .documentedParameters("prompt", "aspect_ratio", "image_resolution", "seed", "images")
            .build(), "seedream-4", "seedream-v4", "bytedance/seedream-v4");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_SEEDREAM_V4_5)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", SEEDREAM_ASPECT_RATIO_OPTIONS, DEFAULT_GEMINI_ASPECT_RATIO)
            .support(ImageCapabilityField.QUALITY, "quality", QUALITY_BASIC_HIGH_OPTIONS, DEFAULT_QUALITY_BASIC)
            .documentedParameters("prompt", "aspect_ratio", "quality", "images")
            .build(), "seedream-v4.5");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_SEEDREAM_5_LITE)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", SEEDREAM_ASPECT_RATIO_OPTIONS, DEFAULT_GEMINI_ASPECT_RATIO)
            .support(ImageCapabilityField.QUALITY, "quality", QUALITY_BASIC_HIGH_OPTIONS, DEFAULT_QUALITY_BASIC)
            .documentedParameters("prompt", "aspect_ratio", "quality", "images")
            .build(), "seedream-5-lite");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_QWEN_IMAGE)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", List.of(), DEFAULT_GEMINI_ASPECT_RATIO)
            .support(ImageCapabilityField.OUTPUT_FORMAT, "output_format", List.of(), DEFAULT_OUTPUT_FORMAT)
            .support(ImageCapabilityField.STRENGTH, "strength", List.of(), "")
            .support(ImageCapabilityField.GUIDANCE_SCALE, "guidance_scale", List.of(), "")
            .documentedParameters("prompt", "aspect_ratio", "images", "output_format", "strength", "guidance_scale")
            .build(), "qwen-image", "z-image");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_GROK_IMAGINE_IMAGE)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", GROK_ASPECT_RATIO_OPTIONS, DEFAULT_GROK_ASPECT_RATIO)
            .documentedParameters("prompt", "aspect_ratio", "images")
            .build(), "grok-imagine-image", "grok-image");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_FLUX_2_PRO)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", FLUX_ASPECT_RATIO_OPTIONS, DEFAULT_FLUX_ASPECT_RATIO)
            .support(ImageCapabilityField.RESOLUTION, "image_resolution", FLUX_RESOLUTION_OPTIONS, DEFAULT_RESOLUTION)
            .documentedParameters("prompt", "aspect_ratio", "image_resolution", "images")
            .build(), "flux.2-pro");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_FLUX_2_FLEX)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", FLUX_ASPECT_RATIO_OPTIONS, DEFAULT_FLUX_ASPECT_RATIO)
            .support(ImageCapabilityField.RESOLUTION, "image_resolution", FLUX_RESOLUTION_OPTIONS, DEFAULT_RESOLUTION)
            .documentedParameters("prompt", "aspect_ratio", "image_resolution", "images")
            .build(), "flux.2-flex");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_GPT_5_4_IMAGE_2)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", GPT_5_4_IMAGE_ASPECT_RATIO_OPTIONS, "auto")
            .documentedParameters("prompt", "aspect_ratio", "n", "images")
            .build(), "gpt-5.4-image-2", "gpt54-image2");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_GPT_5_IMAGE)
            .documentedParameters("prompt", "images")
            .build(), "gpt-5-image");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_GPT_5_IMAGE_MINI)
            .documentedParameters("prompt", "images")
            .build(), "gpt-5-image-mini");

        register(registry, canonicalIds, ImageModelCapability.builder(MODEL_GPT_IMAGE_1_5)
            .support(ImageCapabilityField.ASPECT_RATIO, "aspect_ratio", GPT_IMAGE_ASPECT_RATIO_OPTIONS, "1:1")
            .support(ImageCapabilityField.QUALITY, "quality", QUALITY_MEDIUM_HIGH_OPTIONS, DEFAULT_QUALITY_MEDIUM)
            .documentedParameters("prompt", "aspect_ratio", "images", "quality")
            .build(), "gpt-image-1.5");

        this.capabilities = Map.copyOf(registry);
        this.canonicalModelIds = Map.copyOf(canonicalIds);
        this.supportedModels = List.copyOf(registry.keySet());
    }

    public static ImageModelCapabilityRegistry getInstance() {
        return INSTANCE;
    }

    public List<String> supportedModels() {
        return supportedModels;
    }

    public String defaultModel() {
        return DEFAULT_MODEL;
    }

    public String defaultResolution() {
        return DEFAULT_RESOLUTION;
    }

    public List<String> resolutionOptions() {
        return RESOLUTION_OPTIONS;
    }

    public boolean isSupportedModel(String model) {
        return resolveCanonicalModel(model) != null;
    }

    public String resolveModelOrDefault(String model) {
        String canonical = resolveCanonicalModel(model);
        if (canonical != null) {
            return canonical;
        }
        String sanitized = sanitize(model);
        return sanitized.isEmpty() ? DEFAULT_MODEL : sanitized;
    }

    public ImageModelCapability requireCapability(String model) {
        String canonical = resolveCanonicalModel(model);
        ImageModelCapability capability = canonical == null ? null : capabilities.get(canonical);
        if (capability == null) {
            throw new ImageCapabilityValidationException(
                "Модель генерации изображения '" + sanitize(model) + "' не поддерживается."
            );
        }
        return capability;
    }

    public ImageModelCapability resolveCapability(String model) {
        String canonical = resolveCanonicalModel(model);
        ImageModelCapability capability = canonical == null ? null : capabilities.get(canonical);
        if (capability != null) {
            return capability;
        }
        String sanitized = sanitize(model);
        if (sanitized.isEmpty()) {
            return capabilities.get(DEFAULT_MODEL);
        }
        return ImageModelCapability.builder(sanitized)
            .documentedParameters("prompt")
            .build();
    }

    public ImageValidatedOptions validateOptions(
        String model,
        String size,
        String aspectRatio,
        String resolution,
        String quality,
        String outputFormat,
        String strength,
        String guidanceScale
    ) {
        String normalizedModel = sanitize(model);
        if (normalizedModel.isEmpty()) {
            throw new ImageCapabilityValidationException("Выберите модель генерации изображения.");
        }
        if (!isSupportedModel(normalizedModel)) {
            return new ImageValidatedOptions(normalizedModel, "", "", "", "", "", "", "");
        }
        ImageModelCapability capability = resolveCapability(normalizedModel);
        return new ImageValidatedOptions(
            capability.model(),
            normalizeSupportedField(capability, ImageCapabilityField.SIZE, size),
            normalizeSupportedField(capability, ImageCapabilityField.ASPECT_RATIO, aspectRatio),
            normalizeSupportedField(capability, ImageCapabilityField.RESOLUTION, resolution),
            normalizeSupportedField(capability, ImageCapabilityField.QUALITY, quality),
            normalizeSupportedField(capability, ImageCapabilityField.OUTPUT_FORMAT, outputFormat),
            normalizeSupportedField(capability, ImageCapabilityField.STRENGTH, strength),
            normalizeSupportedField(capability, ImageCapabilityField.GUIDANCE_SCALE, guidanceScale)
        );
    }

    public ImageConfigResolution resolveConfiguredOptions(
        String model,
        String size,
        String aspectRatio,
        String resolution,
        String quality,
        String outputFormat,
        String strength,
        String guidanceScale
    ) {
        List<String> issues = new ArrayList<>();
        String sanitizedModel = sanitize(model);
        ImageModelCapability capability = resolveCapability(sanitizedModel);
        if (!capabilities.containsKey(normalizeModelKey(sanitizedModel)) && !sanitizedModel.isBlank()) {
            issues.add("Неизвестная модель '" + sanitizedModel + "'. Дополнительные параметры будут отключены.");
        }
        String normalizedSize = resolveField(capability, ImageCapabilityField.SIZE, size, issues);
        String normalizedAspectRatio = resolveField(capability, ImageCapabilityField.ASPECT_RATIO, aspectRatio, issues);
        String normalizedResolution = resolveField(capability, ImageCapabilityField.RESOLUTION, resolution, issues);
        String normalizedQuality = resolveField(capability, ImageCapabilityField.QUALITY, quality, issues);
        String normalizedOutputFormat = resolveField(capability, ImageCapabilityField.OUTPUT_FORMAT, outputFormat, issues);
        String normalizedStrength = resolveField(capability, ImageCapabilityField.STRENGTH, strength, issues);
        String normalizedGuidanceScale = resolveField(capability, ImageCapabilityField.GUIDANCE_SCALE, guidanceScale, issues);

        return new ImageConfigResolution(
            new ImageValidatedOptions(
                capability.model(),
                normalizedSize,
                normalizedAspectRatio,
                normalizedResolution,
                normalizedQuality,
                normalizedOutputFormat,
                normalizedStrength,
                normalizedGuidanceScale
            ),
            issues
        );
    }

    private String resolveField(
        ImageModelCapability capability,
        ImageCapabilityField field,
        String rawValue,
        List<String> issues
    ) {
        String normalized = sanitize(rawValue);
        if (!capability.supports(field)) {
            if (!normalized.isEmpty()) {
                issues.add("Параметр '" + field.key() + "' не поддерживается моделью '" + capability.model() + "' и будет очищен.");
            }
            return "";
        }
        if (normalized.isEmpty()) {
            return capability.defaultValue(field);
        }
        try {
            return capability.normalizeValue(field, normalized);
        } catch (ImageCapabilityValidationException ex) {
            issues.add(
                "Недопустимое значение '" + normalized + "' для '" + field.key() + "'. Использовано значение по умолчанию."
            );
            return capability.defaultValue(field);
        }
    }

    private String normalizeSupportedField(
        ImageModelCapability capability,
        ImageCapabilityField field,
        String rawValue
    ) {
        if (!capability.supports(field)) {
            return "";
        }
        return capability.normalizeValue(field, rawValue);
    }

    private void register(
        Map<String, ImageModelCapability> registry,
        Map<String, String> canonicalIds,
        ImageModelCapability capability,
        String... aliases
    ) {
        registry.put(capability.model(), capability);
        canonicalIds.put(normalizeModelKey(capability.model()), capability.model());
        if (aliases != null) {
            for (String alias : aliases) {
                String normalizedAlias = normalizeModelKey(alias);
                if (!normalizedAlias.isBlank()) {
                    canonicalIds.put(normalizedAlias, capability.model());
                }
            }
        }
    }

    private String resolveCanonicalModel(String model) {
        String normalized = normalizeModelKey(model);
        if (normalized.isEmpty()) {
            return null;
        }
        return canonicalModelIds.get(normalized);
    }

    private String normalizeModelKey(String model) {
        String normalized = sanitize(model);
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
