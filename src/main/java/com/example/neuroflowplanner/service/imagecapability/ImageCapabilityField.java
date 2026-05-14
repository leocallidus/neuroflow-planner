package com.example.neuroflowplanner.service.imagecapability;

/**
 * Known image generation parameters.
 */
public enum ImageCapabilityField {
    SIZE("size", "Размер"),
    ASPECT_RATIO("aspect_ratio", "Соотношение сторон"),
    RESOLUTION("resolution", "Разрешение"),
    OUTPUT_FORMAT("output_format", "Формат вывода"),
    SEED("seed", "Seed"),
    STEPS("steps", "Шаги"),
    QUALITY("quality", "Качество"),
    STRENGTH("strength", "Сила референса"),
    GUIDANCE_SCALE("guidance_scale", "Следование промпту");

    private final String key;
    private final String displayName;

    ImageCapabilityField(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }
}
