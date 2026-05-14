package com.example.neuroflowplanner.ai.media;

import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaInputPolicyTest {

    @Test
    void detectsAllSupportedImageExtensions() {
        assertImage("photo.png", "image/png");
        assertImage("photo.jpg", "image/jpeg");
        assertImage("photo.jpeg", "image/jpeg");
        assertImage("photo.gif", "image/gif");
        assertImage("photo.webp", "image/webp");
    }

    @Test
    void detectsAllSupportedDocumentExtensions() {
        assertDocument("report.pdf", "application/pdf");
        assertDocument("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertDocument("report.txt", "text/plain");
    }

    @Test
    void detectsAllSupportedAudioExtensions() {
        assertAudio("voice.wav", "audio/wav", "wav");
        assertAudio("voice.mp3", "audio/mpeg", "mp3");
        assertAudio("voice.flac", "audio/flac", "flac");
        assertAudio("voice.m4a", "audio/mp4", "m4a");
    }

    @Test
    void rejectsUnsupportedExtensionAndMarksVideoAsBlocked() {
        assertTrue(AiMediaTypeRegistry.detect("archive.zip", null).isEmpty());

        AiMediaTypeDescriptor video = AiMediaTypeRegistry.detect("clip.webm", null).orElseThrow();
        assertEquals(AiMediaInputKind.VIDEO, video.kind());
        assertFalse(video.supportedInput());
    }

    @Test
    void validatesAudioInputForSupportedModel() {
        List<String> previousAudio = ConfigManager.getExternalApiAudioInputModels();
        try {
            ConfigManager.setExternalApiAudioInputModels(List.of("openai/gpt-4o-audio-preview"));

            assertDoesNotThrow(() -> AiModelMediaCapabilityPolicy.validateExternalModelMediaInputs(
                    "openai/gpt-4o-audio-preview",
                    List.of(AiMediaInput.audioData("QUJD", "voice.wav", "audio/wav", "wav"))));
        } finally {
            ConfigManager.setExternalApiAudioInputModels(previousAudio);
        }
    }

    @Test
    void rejectsAudioInputForUnsupportedModel() {
        List<String> previousAudio = ConfigManager.getExternalApiAudioInputModels();
        try {
            ConfigManager.setExternalApiAudioInputModels(List.of("openai/gpt-4o-audio-preview"));

            AiMediaCapabilityValidationException ex = assertThrows(
                    AiMediaCapabilityValidationException.class,
                    () -> AiModelMediaCapabilityPolicy.validateExternalModelMediaInputs(
                            "openai/gpt-4o",
                            List.of(AiMediaInput.audioData("QUJD", "voice.wav", "audio/wav", "wav"))));

            assertEquals("Модель 'openai/gpt-4o' не поддерживает аудио на вход.", ex.getMessage());
        } finally {
            ConfigManager.setExternalApiAudioInputModels(previousAudio);
        }
    }

    @Test
    void rejectsDocumentInputForUnsupportedModel() {
        List<String> previousFile = ConfigManager.getExternalApiFileInputModels();
        try {
            ConfigManager.setExternalApiFileInputModels(List.of("openai/gpt-4o-file"));

            AiMediaCapabilityValidationException ex = assertThrows(
                    AiMediaCapabilityValidationException.class,
                    () -> AiModelMediaCapabilityPolicy.validateExternalModelMediaInputs(
                            "openai/gpt-4o",
                            List.of(AiMediaInput.documentDataUrl(
                                    "data:application/pdf;base64,BBB",
                                    "report.pdf",
                                    "application/pdf"))));

            assertEquals("Модель 'openai/gpt-4o' не поддерживает файлы на вход.", ex.getMessage());
        } finally {
            ConfigManager.setExternalApiFileInputModels(previousFile);
        }
    }

    @Test
    void allowsKnownPolzaDocumentModelEvenWithStaleFileCapabilityCache() {
        List<String> previousFile = ConfigManager.getExternalApiFileInputModels();
        try {
            ConfigManager.setExternalApiFileInputModels(List.of());

            assertDoesNotThrow(() -> AiModelMediaCapabilityPolicy.validateExternalModelMediaInputs(
                    "openai/gpt-5.4",
                    List.of(AiMediaInput.documentDataUrl(
                            "data:application/pdf;base64,BBB",
                            "report.pdf",
                            "application/pdf"))));
            assertDoesNotThrow(() -> AiModelMediaCapabilityPolicy.validateExternalModelMediaInputs(
                    "google/gemini-3-flash-preview",
                    List.of(AiMediaInput.documentDataUrl(
                            "data:application/pdf;base64,BBB",
                            "report.pdf",
                            "application/pdf"))));
        } finally {
            ConfigManager.setExternalApiFileInputModels(previousFile);
        }
    }

    private static void assertImage(String fileName, String mimeType) {
        AiMediaTypeDescriptor descriptor = AiMediaTypeRegistry.detect(fileName, null).orElseThrow();
        assertEquals(AiMediaInputKind.IMAGE, descriptor.kind());
        assertEquals(mimeType, descriptor.mimeType());
        assertEquals(AiMediaTransportType.IMAGE_URL, descriptor.transportType());
        assertTrue(descriptor.supportedInput());
    }

    private static void assertDocument(String fileName, String mimeType) {
        AiMediaTypeDescriptor descriptor = AiMediaTypeRegistry.detect(fileName, null).orElseThrow();
        assertEquals(AiMediaInputKind.DOCUMENT, descriptor.kind());
        assertEquals(mimeType, descriptor.mimeType());
        assertEquals(AiMediaTransportType.FILE, descriptor.transportType());
        assertTrue(descriptor.supportedInput());
    }

    private static void assertAudio(String fileName, String mimeType, String audioFormat) {
        AiMediaTypeDescriptor descriptor = AiMediaTypeRegistry.detect(fileName, null).orElseThrow();
        assertEquals(AiMediaInputKind.AUDIO, descriptor.kind());
        assertEquals(mimeType, descriptor.mimeType());
        assertEquals(AiMediaTransportType.INPUT_AUDIO, descriptor.transportType());
        assertEquals(audioFormat, descriptor.audioFormat());
        assertTrue(descriptor.supportedInput());
    }
}
