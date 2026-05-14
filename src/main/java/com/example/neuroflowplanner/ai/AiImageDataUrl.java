package com.example.neuroflowplanner.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Utilities for encoding local image files into data URLs for chat image inputs.
 */
public final class AiImageDataUrl {
    private AiImageDataUrl() {}

    public static AiImageInput encodeToDataUrl(Path imagePath) throws IOException {
        if (imagePath == null) {
            throw new IllegalArgumentException("imagePath cannot be null");
        }

        String mimeType = detectSupportedMimeType(imagePath);
        if (mimeType == null) {
            throw new IllegalArgumentException("Unsupported image type. Allowed: png, jpeg/jpg, webp.");
        }

        byte[] bytes = Files.readAllBytes(imagePath);
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String dataUrl = "data:" + mimeType + ";base64," + base64;
        return new AiImageInput(dataUrl);
    }

    private static String detectSupportedMimeType(Path imagePath) throws IOException {
        String mime = Files.probeContentType(imagePath);
        if (mime != null) {
            mime = mime.toLowerCase();
            if ("image/png".equals(mime) || "image/jpeg".equals(mime) || "image/webp".equals(mime)) {
                return mime;
            }
        }

        String name = imagePath.getFileName() != null ? imagePath.getFileName().toString().toLowerCase() : "";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        return null;
    }
}

