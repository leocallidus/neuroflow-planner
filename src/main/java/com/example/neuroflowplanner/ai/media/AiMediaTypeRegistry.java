package com.example.neuroflowplanner.ai.media;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Supported file extension and MIME mapping for Polza media input.
 */
public final class AiMediaTypeRegistry {
    private static final Map<String, AiMediaTypeDescriptor> TYPES_BY_EXTENSION = buildTypes();
    private static final Map<String, AiMediaTypeDescriptor> TYPES_BY_MIME = buildMimeIndex(TYPES_BY_EXTENSION);

    private AiMediaTypeRegistry() {
    }

    public static Optional<AiMediaTypeDescriptor> detect(String fileName, String mimeType) {
        Optional<AiMediaTypeDescriptor> byExtension = fromFileName(fileName);
        if (byExtension.isPresent()) {
            return byExtension;
        }
        return fromMimeType(mimeType);
    }

    public static Optional<AiMediaTypeDescriptor> fromFileName(String fileName) {
        String extension = normalizeExtension(fileName);
        if (extension == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TYPES_BY_EXTENSION.get(extension));
    }

    public static Optional<AiMediaTypeDescriptor> fromMimeType(String mimeType) {
        String normalizedMime = normalizeMimeType(mimeType);
        if (normalizedMime == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TYPES_BY_MIME.get(normalizedMime));
    }

    public static String normalizeExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot < 0 || lastDot == normalized.length() - 1) {
            return normalized.startsWith(".") ? normalized.substring(1) : null;
        }
        return normalized.substring(lastDot + 1);
    }

    public static String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(';');
        return separator >= 0 ? normalized.substring(0, separator).trim() : normalized;
    }

    private static Map<String, AiMediaTypeDescriptor> buildTypes() {
        Map<String, AiMediaTypeDescriptor> types = new LinkedHashMap<>();

        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.IMAGE, "png", "image/png", AiMediaTransportType.IMAGE_URL, null, true));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.IMAGE, "jpg", "image/jpeg", AiMediaTransportType.IMAGE_URL, null, true));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.IMAGE, "jpeg", "image/jpeg", AiMediaTransportType.IMAGE_URL, null, true));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.IMAGE, "gif", "image/gif", AiMediaTransportType.IMAGE_URL, null, true));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.IMAGE, "webp", "image/webp", AiMediaTransportType.IMAGE_URL, null, true));

        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.DOCUMENT, "pdf", "application/pdf", AiMediaTransportType.FILE, null, true));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.DOCUMENT,
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                AiMediaTransportType.FILE,
                null,
                true));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.DOCUMENT, "txt", "text/plain", AiMediaTransportType.FILE, null, true));

        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.AUDIO, "wav", "audio/wav", AiMediaTransportType.INPUT_AUDIO, "wav", true));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.AUDIO, "mp3", "audio/mpeg", AiMediaTransportType.INPUT_AUDIO, "mp3", true));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.AUDIO, "flac", "audio/flac", AiMediaTransportType.INPUT_AUDIO, "flac", true));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.AUDIO, "m4a", "audio/mp4", AiMediaTransportType.INPUT_AUDIO, "m4a", true));

        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.VIDEO, "mp4", "video/mp4", null, null, false));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.VIDEO, "mov", "video/quicktime", null, null, false));
        register(types, new AiMediaTypeDescriptor(
                AiMediaInputKind.VIDEO, "webm", "video/webm", null, null, false));
        return Map.copyOf(types);
    }

    private static void register(Map<String, AiMediaTypeDescriptor> types, AiMediaTypeDescriptor descriptor) {
        types.put(descriptor.extension(), descriptor);
    }

    private static Map<String, AiMediaTypeDescriptor> buildMimeIndex(Map<String, AiMediaTypeDescriptor> typesByExtension) {
        Map<String, AiMediaTypeDescriptor> mimeIndex = new LinkedHashMap<>();
        for (AiMediaTypeDescriptor descriptor : typesByExtension.values()) {
            mimeIndex.put(normalizeMimeType(descriptor.mimeType()), descriptor);
        }
        return Map.copyOf(mimeIndex);
    }
}
