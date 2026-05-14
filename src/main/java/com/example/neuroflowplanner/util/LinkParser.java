package com.example.neuroflowplanner.util;

import com.example.neuroflowplanner.model.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinkParser {
    public enum LinkType {
        NOTE,
        TASK
    }

    public static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)]]");

    private LinkParser() {
    }

    public static List<LinkTarget> extractLinks(String content) {
        List<LinkTarget> links = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return links;
        }
        Matcher matcher = WIKI_LINK_PATTERN.matcher(content);
        while (matcher.find()) {
            String raw = matcher.group(1);
            LinkTarget target = parse(raw);
            if (target != null) {
                links.add(target);
            }
        }
        return links;
    }

    public static LinkTarget parse(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("task:")) {
            return new LinkTarget(LinkType.TASK, trimmed.substring(5).trim(), trimmed);
        }
        if (lower.startsWith("задача:")) {
            return new LinkTarget(LinkType.TASK, trimmed.substring(7).trim(), trimmed);
        }
        if (lower.startsWith("note:")) {
            return new LinkTarget(LinkType.NOTE, trimmed.substring(5).trim(), trimmed);
        }
        if (lower.startsWith("заметка:")) {
            return new LinkTarget(LinkType.NOTE, trimmed.substring(8).trim(), trimmed);
        }
        return new LinkTarget(LinkType.NOTE, trimmed, trimmed);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matchesNoteTarget(String target, String noteTitle) {
        return normalizeNote(target).equals(normalizeNote(noteTitle));
    }

    public static boolean matchesTaskTarget(String target, Task task) {
        if (task == null) {
            return false;
        }
        String normalized = normalize(target);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalize(task.getId()).equals(normalized)) {
            return true;
        }
        return normalize(task.getTitle()).equals(normalized);
    }

    private static String normalizeNote(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[^a-zA-Z0-9а-яА-Я _-]", "").trim();
        return normalize(sanitized);
    }

    public static final class LinkTarget {
        private final LinkType type;
        private final String target;
        private final String raw;

        public LinkTarget(LinkType type, String target, String raw) {
            this.type = type;
            this.target = target;
            this.raw = raw;
        }

        public LinkType getType() {
            return type;
        }

        public String getTarget() {
            return target;
        }

        public String getRaw() {
            return raw;
        }
    }
}
