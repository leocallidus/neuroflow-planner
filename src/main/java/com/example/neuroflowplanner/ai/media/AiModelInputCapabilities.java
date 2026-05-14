package com.example.neuroflowplanner.ai.media;

import java.util.List;
import java.util.Locale;

/**
 * Normalized input capability flags derived from provider model metadata.
 */
public record AiModelInputCapabilities(
        boolean supportsImageInput,
        boolean supportsAudioInput,
        boolean supportsFileInput) {

    public static final AiModelInputCapabilities NONE = new AiModelInputCapabilities(false, false, false);

    public static AiModelInputCapabilities fromInputModalities(List<String> inputModalities) {
        if (inputModalities == null || inputModalities.isEmpty()) {
            return NONE;
        }
        boolean image = false;
        boolean audio = false;
        boolean file = false;
        for (String modality : inputModalities) {
            String normalized = normalize(modality);
            if (normalized == null) {
                continue;
            }
            image |= containsImage(normalized);
            audio |= containsAudio(normalized);
            file |= containsFile(normalized);
        }
        return new AiModelInputCapabilities(image, audio, file);
    }

    public boolean supportsAnyMediaInput() {
        return supportsImageInput || supportsAudioInput || supportsFileInput;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsImage(String value) {
        return value.contains("image") || value.contains("vision");
    }

    private static boolean containsAudio(String value) {
        return value.contains("audio") || value.contains("speech");
    }

    private static boolean containsFile(String value) {
        return value.contains("file")
                || value.contains("document")
                || value.contains("pdf")
                || value.contains("docx")
                || value.contains("txt");
    }
}
