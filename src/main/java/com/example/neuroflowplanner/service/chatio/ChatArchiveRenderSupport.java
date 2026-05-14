package com.example.neuroflowplanner.service.chatio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class ChatArchiveRenderSupport {
    static final String IMAGE_MESSAGE_PREFIX = "NEUROFLOW_IMAGE:";
    static final String MODEL_MESSAGE_PREFIX = "NEUROFLOW_MODEL:";

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    String normalizeMessageText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        if (normalized.startsWith(IMAGE_MESSAGE_PREFIX)) {
            String path = normalized.substring(IMAGE_MESSAGE_PREFIX.length()).trim();
            if (path.isEmpty()) {
                return "Изображение сохранено (путь не указан).";
            }
            return "Изображение сохранено: `" + path + "`";
        }
        return normalized;
    }

    ModelTaggedText parseModelTaggedText(String text) {
        if (text == null || text.isBlank() || !text.startsWith(MODEL_MESSAGE_PREFIX)) {
            return new ModelTaggedText(null, text == null ? "" : text);
        }
        String withoutPrefix = text.substring(MODEL_MESSAGE_PREFIX.length());
        int nl = withoutPrefix.indexOf('\n');
        if (nl < 0) {
            String model = withoutPrefix.trim();
            return new ModelTaggedText(model.isBlank() ? null : model, "");
        }
        String model = withoutPrefix.substring(0, nl).trim();
        String content = withoutPrefix.substring(nl + 1);
        return new ModelTaggedText(model.isBlank() ? null : model, content);
    }

    String formatDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw).format(formatter);
        } catch (Exception ignored) {
            return raw;
        }
    }

    record ModelTaggedText(String model, String content) {
    }
}
