package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.media.AiMediaInput;

import java.util.Objects;

/**
 * Represents an image input for multimodal chat requests.
 *
 * <p>The provider integration in {@code IMAGES_INTEGRATE.md} expects {@code image_url.url}
 * to be either a regular URL or a {@code data:<mime>;base64,...} data URL.</p>
 */
public record AiImageInput(String dataUrl) {
    public AiImageInput {
        Objects.requireNonNull(dataUrl, "dataUrl cannot be null");
        if (dataUrl.isBlank()) {
            throw new IllegalArgumentException("dataUrl cannot be blank");
        }
    }

    public AiMediaInput toMediaInput() {
        return AiMediaInput.imageDataUrl(dataUrl, null, detectMimeType(dataUrl));
    }

    private static String detectMimeType(String dataUrl) {
        if (dataUrl == null) {
            return "image/*";
        }
        if (dataUrl.startsWith("data:")) {
            int start = "data:".length();
            int end = dataUrl.indexOf(';', start);
            if (end > start) {
                return dataUrl.substring(start, end);
            }
        }
        return "image/*";
    }
}
