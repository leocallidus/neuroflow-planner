package com.example.neuroflowplanner.util;

import com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField;
import com.example.neuroflowplanner.service.imagecapability.ImageConfigResolution;
import com.example.neuroflowplanner.service.imagecapability.ImageModelCapability;
import com.example.neuroflowplanner.service.imagecapability.ImageModelCapabilityRegistry;
import com.example.neuroflowplanner.service.imagecapability.ImageValidatedOptions;

import java.util.List;

public final class ImageGenConfigDefaults {

    public static final String CONFIG_KEY_IMAGE_MODEL = "ai.image.model";
    public static final String CONFIG_KEY_IMAGE_SIZE = "ai.image.size";
    public static final String CONFIG_KEY_IMAGE_ASPECT_RATIO = "ai.image.aspect_ratio";
    public static final String CONFIG_KEY_IMAGE_RESOLUTION = "ai.image.resolution";
    public static final String CONFIG_KEY_IMAGE_QUALITY = "ai.image.quality";
    public static final String CONFIG_KEY_IMAGE_OUTPUT_FORMAT = "ai.image.output_format";
    public static final String CONFIG_KEY_IMAGE_STRENGTH = "ai.image.strength";
    public static final String CONFIG_KEY_IMAGE_GUIDANCE_SCALE = "ai.image.guidance_scale";

    public static final String MODEL_NANO_BANANA = ImageModelCapabilityRegistry.MODEL_NANO_BANANA;
    public static final String MODEL_GEMINI_3_PRO_IMAGE_PREVIEW = ImageModelCapabilityRegistry.MODEL_GEMINI_3_PRO_IMAGE_PREVIEW;
    public static final String MODEL_GEMINI_3_1_FLASH_IMAGE_PREVIEW = ImageModelCapabilityRegistry.MODEL_GEMINI_3_1_FLASH_IMAGE_PREVIEW;
    public static final String MODEL_SEEDREAM_3 = ImageModelCapabilityRegistry.MODEL_SEEDREAM_3;
    public static final String MODEL_SEEDREAM_4 = ImageModelCapabilityRegistry.MODEL_SEEDREAM_4;
    public static final String MODEL_SEEDREAM_V4_5 = ImageModelCapabilityRegistry.MODEL_SEEDREAM_V4_5;
    public static final String MODEL_SEEDREAM_5_LITE = ImageModelCapabilityRegistry.MODEL_SEEDREAM_5_LITE;
    public static final String MODEL_QWEN_IMAGE = ImageModelCapabilityRegistry.MODEL_QWEN_IMAGE;
    public static final String MODEL_Z_IMAGE = ImageModelCapabilityRegistry.MODEL_Z_IMAGE;
    public static final String MODEL_GROK_IMAGINE_IMAGE = ImageModelCapabilityRegistry.MODEL_GROK_IMAGINE_IMAGE;
    public static final String MODEL_FLUX_2_PRO = ImageModelCapabilityRegistry.MODEL_FLUX_2_PRO;
    public static final String MODEL_FLUX_2_FLEX = ImageModelCapabilityRegistry.MODEL_FLUX_2_FLEX;
    public static final String MODEL_GPT_5_4_IMAGE_2 = ImageModelCapabilityRegistry.MODEL_GPT_5_4_IMAGE_2;
    public static final String MODEL_GPT_5_IMAGE = ImageModelCapabilityRegistry.MODEL_GPT_5_IMAGE;
    public static final String MODEL_GPT_5_IMAGE_MINI = ImageModelCapabilityRegistry.MODEL_GPT_5_IMAGE_MINI;
    public static final String MODEL_GPT_IMAGE_1_5 = ImageModelCapabilityRegistry.MODEL_GPT_IMAGE_1_5;

    public static final List<String> IMAGE_MODEL_OPTIONS = ImageModelCapabilityRegistry.getInstance().supportedModels();
    public static final String DEFAULT_IMAGE_MODEL = ImageModelCapabilityRegistry.getInstance().defaultModel();
    public static final String DEFAULT_RESOLUTION = ImageModelCapabilityRegistry.getInstance().defaultResolution();

    private static final ImageModelCapabilityRegistry REGISTRY = ImageModelCapabilityRegistry.getInstance();

    private ImageGenConfigDefaults() {
    }

    public static boolean isSupportedImageModel(String model) {
        return REGISTRY.isSupportedModel(model);
    }

    public static String normalizeImageModel(String model) {
        return REGISTRY.resolveModelOrDefault(model);
    }

    public static boolean supportsResolution(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supports(ImageCapabilityField.RESOLUTION);
    }

    public static boolean supportsQualityField(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supports(ImageCapabilityField.QUALITY);
    }

    public static boolean supportsOutputFormatField(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supports(ImageCapabilityField.OUTPUT_FORMAT);
    }

    public static boolean supportsStrengthField(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supports(ImageCapabilityField.STRENGTH);
    }

    public static boolean supportsGuidanceScaleField(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supports(ImageCapabilityField.GUIDANCE_SCALE);
    }

    public static boolean supportsAspectRatioField(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supports(ImageCapabilityField.ASPECT_RATIO);
    }

    public static boolean supportsSizeField(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supports(ImageCapabilityField.SIZE);
    }

    public static List<String> getSizeOptionsForModel(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supportedValues(ImageCapabilityField.SIZE);
    }

    public static List<String> getAspectRatioOptionsForModel(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supportedValues(ImageCapabilityField.ASPECT_RATIO);
    }

    public static List<String> getResolutionOptions() {
        return REGISTRY.resolutionOptions();
    }

    public static List<String> getQualityOptionsForModel(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supportedValues(ImageCapabilityField.QUALITY);
    }

    public static List<String> getOutputFormatOptionsForModel(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).supportedValues(ImageCapabilityField.OUTPUT_FORMAT);
    }

    public static String defaultSizeForModel(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).defaultValue(ImageCapabilityField.SIZE);
    }

    public static String defaultAspectRatioForModel(String model) {
        return REGISTRY.requireCapability(normalizeImageModel(model)).defaultValue(ImageCapabilityField.ASPECT_RATIO);
    }

    public static String normalizeSizeForModel(String model, String size) {
        ImageModelCapability capability = REGISTRY.requireCapability(normalizeImageModel(model));
        return capability.supports(ImageCapabilityField.SIZE)
            ? capability.normalizeValue(ImageCapabilityField.SIZE, size)
            : "";
    }

    public static String normalizeAspectRatioForModel(String model, String aspectRatio) {
        ImageModelCapability capability = REGISTRY.requireCapability(normalizeImageModel(model));
        return capability.supports(ImageCapabilityField.ASPECT_RATIO)
            ? capability.normalizeValue(ImageCapabilityField.ASPECT_RATIO, aspectRatio)
            : "";
    }

    public static String normalizeResolution(String resolution) {
        return resolution == null ? "" : resolution.trim();
    }

    public static String normalizeQualityForModel(String model, String quality) {
        ImageModelCapability capability = REGISTRY.requireCapability(normalizeImageModel(model));
        return capability.supports(ImageCapabilityField.QUALITY)
            ? capability.normalizeValue(ImageCapabilityField.QUALITY, quality)
            : "";
    }

    public static String normalizeOutputFormatForModel(String model, String outputFormat) {
        ImageModelCapability capability = REGISTRY.requireCapability(normalizeImageModel(model));
        return capability.supports(ImageCapabilityField.OUTPUT_FORMAT)
            ? capability.normalizeValue(ImageCapabilityField.OUTPUT_FORMAT, outputFormat)
            : "";
    }

    public static String normalizeStrengthForModel(String model, String strength) {
        ImageModelCapability capability = REGISTRY.requireCapability(normalizeImageModel(model));
        return capability.supports(ImageCapabilityField.STRENGTH)
            ? capability.normalizeValue(ImageCapabilityField.STRENGTH, strength)
            : "";
    }

    public static String normalizeGuidanceScaleForModel(String model, String guidanceScale) {
        ImageModelCapability capability = REGISTRY.requireCapability(normalizeImageModel(model));
        return capability.supports(ImageCapabilityField.GUIDANCE_SCALE)
            ? capability.normalizeValue(ImageCapabilityField.GUIDANCE_SCALE, guidanceScale)
            : "";
    }

    public static ImageValidatedOptions validateImageOptions(
        String model,
        String size,
        String aspectRatio,
        String resolution,
        String quality,
        String outputFormat,
        String strength,
        String guidanceScale
    ) {
        return REGISTRY.validateOptions(model, size, aspectRatio, resolution, quality, outputFormat, strength, guidanceScale);
    }

    public static ImageConfigResolution resolveConfiguredOptions(
        String model,
        String size,
        String aspectRatio,
        String resolution,
        String quality,
        String outputFormat,
        String strength,
        String guidanceScale
    ) {
        return REGISTRY.resolveConfiguredOptions(model, size, aspectRatio, resolution, quality, outputFormat, strength, guidanceScale);
    }

    public static ImageModelCapability getCapability(String model) {
        return REGISTRY.resolveCapability(normalizeImageModel(model));
    }
}
