package com.example.neuroflowplanner.util;

public final class AiApiUtils {

    private AiApiUtils() {
    }

    public static boolean isSuccessfulStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    public static String resolveChatUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.contains("/api/chat") || normalized.contains("/api/generate") || normalized.contains("/chat/completions")) {
            return normalized;
        }
        String trimmed = trimTrailingSlash(normalized);
        return trimmed + "/chat/completions";
    }

    public static String resolveModelsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        String trimmed = trimTrailingSlash(normalized)
            .replace("/api/chat", "")
            .replace("/api/generate", "")
            .replace("/chat/completions", "");
        trimmed = trimTrailingSlash(trimmed);
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/models";
        }
        return trimmed;
    }

    /**
     * JavaFX/WebView setups often don't have emoji-capable fonts, causing emoji to render as '?'.
     * To keep UI readable, strip common emoji code points from assistant output.
     */
    public static String sanitizeAssistantText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (isEmojiLikeCodePoint(cp)) {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    private static boolean isEmojiLikeCodePoint(int codePoint) {
        // Variation selector-16 (emoji presentation) and ZWJ used to compose emoji sequences.
        if (codePoint == 0xFE0F || codePoint == 0x200D) {
            return true;
        }
        // Combining enclosing keycap for sequences like "1️⃣"
        if (codePoint == 0x20E3) {
            return true;
        }
        // Skin tone modifiers
        if (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF) {
            return true;
        }
        // Main emoji blocks (covers most modern emoji)
        if (codePoint >= 0x1F000 && codePoint <= 0x1FAFF) {
            return true;
        }
        // Misc symbols / dingbats that are frequently rendered as emoji
        if (codePoint >= 0x2600 && codePoint <= 0x27BF) {
            return true;
        }
        // Misc technical (contains some emoji-like glyphs, e.g. hourglass)
        return codePoint >= 0x2300 && codePoint <= 0x23FF;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return AiConfigDefaults.DEFAULT_API_URL;
        }
        return baseUrl.trim();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - hex.length(); k++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
