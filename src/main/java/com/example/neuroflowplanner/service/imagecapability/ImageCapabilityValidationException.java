package com.example.neuroflowplanner.service.imagecapability;

/**
 * Validation error for image generation model capabilities and parameters.
 */
public class ImageCapabilityValidationException extends IllegalArgumentException {

    public ImageCapabilityValidationException(String message) {
        super(message);
    }
}
