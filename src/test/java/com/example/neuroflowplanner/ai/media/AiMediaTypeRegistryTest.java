package com.example.neuroflowplanner.ai.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMediaTypeRegistryTest {

    @Test
    void detectsSupportedImageByExtension() {
        AiMediaTypeDescriptor descriptor = AiMediaTypeRegistry.fromFileName("photo.WEBP").orElseThrow();

        assertEquals(AiMediaInputKind.IMAGE, descriptor.kind());
        assertEquals("image/webp", descriptor.mimeType());
        assertEquals(AiMediaTransportType.IMAGE_URL, descriptor.transportType());
        assertTrue(descriptor.supportedInput());
    }

    @Test
    void detectsSupportedDocumentByMimeType() {
        AiMediaTypeDescriptor descriptor =
                AiMediaTypeRegistry.fromMimeType("application/pdf; charset=binary").orElseThrow();

        assertEquals(AiMediaInputKind.DOCUMENT, descriptor.kind());
        assertEquals(AiMediaTransportType.FILE, descriptor.transportType());
        assertTrue(descriptor.supportedInput());
    }

    @Test
    void detectsAudioAndNormalizesFormat() {
        AiMediaTypeDescriptor descriptor = AiMediaTypeRegistry.detect("voice_note.m4a", null).orElseThrow();

        assertEquals(AiMediaInputKind.AUDIO, descriptor.kind());
        assertEquals(AiMediaTransportType.INPUT_AUDIO, descriptor.transportType());
        assertEquals("m4a", descriptor.audioFormat());
        assertTrue(descriptor.supportedInput());
    }

    @Test
    void marksVideoAsBlockedInput() {
        AiMediaTypeDescriptor descriptor = AiMediaTypeRegistry.detect("clip.mp4", null).orElseThrow();

        assertEquals(AiMediaInputKind.VIDEO, descriptor.kind());
        assertFalse(descriptor.supportedInput());
        assertTrue(descriptor.isVideo());
    }
}
