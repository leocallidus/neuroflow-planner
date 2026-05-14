package com.example.neuroflowplanner.ai.media;

import java.util.Objects;

/**
 * Source-of-truth mapping for supported local media inputs.
 */
public record AiMediaTypeDescriptor(
        AiMediaInputKind kind,
        String extension,
        String mimeType,
        AiMediaTransportType transportType,
        String audioFormat,
        boolean supportedInput) {

    public AiMediaTypeDescriptor {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(mimeType, "mimeType");
        if (supportedInput) {
            Objects.requireNonNull(transportType, "transportType");
        }
    }

    public boolean isAudio() {
        return kind == AiMediaInputKind.AUDIO;
    }

    public boolean isDocument() {
        return kind == AiMediaInputKind.DOCUMENT;
    }

    public boolean isImage() {
        return kind == AiMediaInputKind.IMAGE;
    }

    public boolean isVideo() {
        return kind == AiMediaInputKind.VIDEO;
    }
}
