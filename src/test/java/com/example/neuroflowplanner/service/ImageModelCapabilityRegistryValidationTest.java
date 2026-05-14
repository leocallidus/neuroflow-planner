package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.service.imagecapability.ImageCapabilityValidationException;
import com.example.neuroflowplanner.service.imagecapability.ImageConfigResolution;
import com.example.neuroflowplanner.service.imagecapability.ImageModelCapability;
import com.example.neuroflowplanner.service.imagecapability.ImageModelCapabilityRegistry;
import com.example.neuroflowplanner.service.imagecapability.ImageValidatedOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageModelCapabilityRegistryValidationTest {

    private final ImageModelCapabilityRegistry registry = ImageModelCapabilityRegistry.getInstance();

    @Test
    void nanoBananaAcceptsAspectRatioAndNormalizesOfficialModelId() {
        ImageValidatedOptions options = registry.validateOptions(
            ImageModelCapabilityRegistry.MODEL_NANO_BANANA,
            "",
            "",
            "",
            "",
            "",
            "",
            ""
        );

        assertEquals(ImageModelCapabilityRegistry.MODEL_NANO_BANANA, options.model());
        assertEquals("", options.size());
        assertEquals("1:1", options.aspectRatio());
        assertEquals("", options.resolution());
        assertEquals("png", options.outputFormat());
    }

    @Test
    void geminiPreviewRequiresValidAspectRatioAndResolution() {
        ImageValidatedOptions options = registry.validateOptions(
            ImageModelCapabilityRegistry.MODEL_GEMINI_3_PRO_IMAGE_PREVIEW,
            "",
            "16:9",
            "2K",
            "",
            "jpeg",
            "",
            ""
        );

        assertEquals("16:9", options.aspectRatio());
        assertEquals("2K", options.resolution());
        assertEquals("", options.size());
        assertEquals("jpeg", options.outputFormat());
    }

    @Test
    void legacyAliasesResolveToCanonicalPolzaIds() {
        assertEquals(
            ImageModelCapabilityRegistry.MODEL_NANO_BANANA,
            registry.resolveModelOrDefault("nano-banana")
        );
        assertEquals(
            ImageModelCapabilityRegistry.MODEL_SEEDREAM_3,
            registry.resolveModelOrDefault("seedream-3.0")
        );
        assertEquals(
            ImageModelCapabilityRegistry.MODEL_SEEDREAM_4,
            registry.resolveModelOrDefault("seedream-v4")
        );
        assertEquals(
            ImageModelCapabilityRegistry.MODEL_FLUX_2_PRO,
            registry.resolveModelOrDefault("flux.2-pro")
        );
        assertEquals(
            ImageModelCapabilityRegistry.MODEL_QWEN_IMAGE,
            registry.resolveModelOrDefault("z-image")
        );
        assertEquals(
            ImageModelCapabilityRegistry.MODEL_GPT_5_4_IMAGE_2,
            registry.resolveModelOrDefault("gpt54-image2")
        );
        assertEquals(
            ImageModelCapabilityRegistry.MODEL_GPT_5_IMAGE,
            registry.resolveModelOrDefault("gpt-5-image")
        );
    }

    @Test
    void latestPolzaImageModelsAreSupportedWithDocumentedFields() {
        ImageValidatedOptions gpt54 = registry.validateOptions(
            ImageModelCapabilityRegistry.MODEL_GPT_5_4_IMAGE_2,
            "",
            "21:9",
            "",
            "",
            "",
            "",
            ""
        );
        assertEquals("21:9", gpt54.aspectRatio());

        ImageValidatedOptions seedream4 = registry.validateOptions(
            ImageModelCapabilityRegistry.MODEL_SEEDREAM_4,
            "",
            "3:2",
            "4K",
            "",
            "",
            "",
            ""
        );
        assertEquals("3:2", seedream4.aspectRatio());
        assertEquals("4K", seedream4.resolution());

        ImageValidatedOptions seedream3 = registry.validateOptions(
            ImageModelCapabilityRegistry.MODEL_SEEDREAM_3,
            "",
            "9:16",
            "",
            "",
            "",
            "",
            "2.5"
        );
        assertEquals("9:16", seedream3.aspectRatio());
        assertEquals("2.5", seedream3.guidanceScale());
    }

    @Test
    void rejectsUnsupportedFieldValuesForCapabilityMatrix() {
        ImageCapabilityValidationException ex = assertThrows(
            ImageCapabilityValidationException.class,
            () -> registry.validateOptions(
                ImageModelCapabilityRegistry.MODEL_GEMINI_3_PRO_IMAGE_PREVIEW,
                "",
                "11:11",
                "2K",
                "",
                "",
                "",
                ""
            )
        );

        assertTrue(ex.getMessage().contains("aspect_ratio"));
    }

    @Test
    void resolveConfiguredOptionsPreservesUnknownModelAndDisablesExtraFields() {
        ImageConfigResolution resolution = registry.resolveConfiguredOptions(
            "unknown-model",
            "invalid-size",
            "invalid-ratio",
            "invalid-resolution",
            "invalid-quality",
            "invalid-format",
            "invalid-strength",
            "invalid-guidance"
        );

        assertTrue(resolution.hasIssues());
        assertEquals("unknown-model", resolution.options().model());
        assertEquals("", resolution.options().size());
        assertEquals("", resolution.options().aspectRatio());
        assertEquals("", resolution.options().resolution());
        assertEquals("", resolution.options().quality());
        assertFalse(resolution.summary().isBlank());
    }

    @Test
    void validateOptionsAllowsUnknownModelWithoutCapabilityFields() {
        ImageValidatedOptions options = registry.validateOptions(
            "custom-provider/sdxl-ultra",
            "4k",
            "16:9",
            "2K",
            "high",
            "png",
            "0.8",
            "2.5"
        );

        assertEquals("custom-provider/sdxl-ultra", options.model());
        assertEquals("", options.size());
        assertEquals("", options.aspectRatio());
        assertEquals("", options.resolution());
    }

    @Test
    void validateOptionsSilentlyClearsUnsupportedFieldsForKnownModel() {
        ImageValidatedOptions options = registry.validateOptions(
            ImageModelCapabilityRegistry.MODEL_GPT_5_IMAGE,
            "",
            "16:9",
            "2K",
            "high",
            "png",
            "0.8",
            "2.5"
        );

        assertEquals(ImageModelCapabilityRegistry.MODEL_GPT_5_IMAGE, options.model());
        assertEquals("", options.aspectRatio());
        assertEquals("", options.resolution());
        assertEquals("", options.quality());
        assertEquals("", options.outputFormat());
        assertEquals("", options.strength());
        assertEquals("", options.guidanceScale());
    }

    @Test
    void transportFieldNamesStayStableForCurrentModels() {
        ImageModelCapability nano = registry.requireCapability(ImageModelCapabilityRegistry.MODEL_NANO_BANANA);
        ImageModelCapability gemini = registry.requireCapability(ImageModelCapabilityRegistry.MODEL_GEMINI_3_PRO_IMAGE_PREVIEW);
        ImageModelCapability seedream4 = registry.requireCapability(ImageModelCapabilityRegistry.MODEL_SEEDREAM_4);
        ImageModelCapability seedream = registry.requireCapability(ImageModelCapabilityRegistry.MODEL_SEEDREAM_V4_5);
        ImageModelCapability flux = registry.requireCapability(ImageModelCapabilityRegistry.MODEL_FLUX_2_PRO);

        assertEquals("aspect_ratio", nano.transportFieldName(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.ASPECT_RATIO));
        assertEquals("aspect_ratio", gemini.transportFieldName(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.ASPECT_RATIO));
        assertEquals("image_resolution", seedream4.transportFieldName(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.RESOLUTION));
        assertEquals("aspect_ratio", seedream.transportFieldName(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.ASPECT_RATIO));
        assertEquals("image_resolution", gemini.transportFieldName(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.RESOLUTION));
        assertEquals("image_resolution", flux.transportFieldName(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.RESOLUTION));
    }
}
