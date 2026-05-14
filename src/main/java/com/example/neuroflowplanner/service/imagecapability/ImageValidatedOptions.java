package com.example.neuroflowplanner.service.imagecapability;

/**
 * Strictly validated image generation options.
 */
public record ImageValidatedOptions(
    String model,
    String size,
    String aspectRatio,
    String resolution,
    String quality,
    String outputFormat,
    String strength,
    String guidanceScale
) {
}
