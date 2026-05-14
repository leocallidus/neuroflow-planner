package com.example.neuroflowplanner.ai.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiModelInputCapabilitiesTest {

    @Test
    void derivesImageAudioAndFileFlagsFromInputModalities() {
        AiModelInputCapabilities capabilities = AiModelInputCapabilities.fromInputModalities(
                List.of("text", "image", "audio", "application/pdf"));

        assertTrue(capabilities.supportsImageInput());
        assertTrue(capabilities.supportsAudioInput());
        assertTrue(capabilities.supportsFileInput());
    }

    @Test
    void returnsNoneForMissingModalities() {
        AiModelInputCapabilities capabilities = AiModelInputCapabilities.fromInputModalities(List.of("text"));

        assertFalse(capabilities.supportsImageInput());
        assertFalse(capabilities.supportsAudioInput());
        assertFalse(capabilities.supportsFileInput());
    }
}
