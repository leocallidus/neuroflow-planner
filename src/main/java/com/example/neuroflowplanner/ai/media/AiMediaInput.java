package com.example.neuroflowplanner.ai.media;

import java.util.Arrays;
import java.util.Objects;

/**
 * Unified media input contract for multimodal chat requests.
 */
public record AiMediaInput(
        AiMediaInputKind kind,
        AiMediaInputSource source,
        String originalFilename,
        String mimeType,
        String normalizedPayloadData,
        byte[] rawBytes,
        String audioFormat) {

    public AiMediaInput {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        mimeType = requireNonBlank(mimeType, "mimeType");
        normalizedPayloadData = normalizeNullable(normalizedPayloadData);
        originalFilename = normalizeNullable(originalFilename);
        audioFormat = normalizeNullable(audioFormat);

        if (source == AiMediaInputSource.RAW_BYTES) {
            if (rawBytes == null || rawBytes.length == 0) {
                throw new IllegalArgumentException("rawBytes cannot be empty for RAW_BYTES media source");
            }
        } else if (normalizedPayloadData == null) {
            throw new IllegalArgumentException("normalizedPayloadData cannot be blank for non-binary media source");
        }

        if (kind == AiMediaInputKind.AUDIO && audioFormat == null) {
            throw new IllegalArgumentException("audioFormat is required for audio inputs");
        }

        rawBytes = rawBytes == null ? null : Arrays.copyOf(rawBytes, rawBytes.length);
    }

    @Override
    public byte[] rawBytes() {
        return rawBytes == null ? null : Arrays.copyOf(rawBytes, rawBytes.length);
    }

    public static AiMediaInput imageUrl(String url, String mimeType) {
        return new AiMediaInput(AiMediaInputKind.IMAGE, AiMediaInputSource.URL, null, mimeType, url, null, null);
    }

    public static AiMediaInput imageDataUrl(String dataUrl, String originalFilename, String mimeType) {
        return new AiMediaInput(
                AiMediaInputKind.IMAGE,
                AiMediaInputSource.BASE64_DATA_URL,
                originalFilename,
                mimeType,
                dataUrl,
                null,
                null);
    }

    public static AiMediaInput documentDataUrl(String dataUrl, String originalFilename, String mimeType) {
        return new AiMediaInput(
                AiMediaInputKind.DOCUMENT,
                AiMediaInputSource.BASE64_DATA_URL,
                originalFilename,
                mimeType,
                dataUrl,
                null,
                null);
    }

    public static AiMediaInput audioData(String base64Data, String originalFilename, String mimeType, String audioFormat) {
        return new AiMediaInput(
                AiMediaInputKind.AUDIO,
                AiMediaInputSource.BASE64_DATA_URL,
                originalFilename,
                mimeType,
                base64Data,
                null,
                audioFormat);
    }

    public static AiMediaInput rawBytes(
            AiMediaInputKind kind,
            byte[] rawBytes,
            String originalFilename,
            String mimeType,
            String audioFormat) {
        return new AiMediaInput(kind, AiMediaInputSource.RAW_BYTES, originalFilename, mimeType, null, rawBytes, audioFormat);
    }

    private static String requireNonBlank(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
