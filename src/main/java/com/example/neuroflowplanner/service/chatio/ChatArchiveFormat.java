package com.example.neuroflowplanner.service.chatio;

public enum ChatArchiveFormat {
    PDF(".pdf"),
    MARKDOWN(".md"),
    JSON(".json");

    private final String defaultExtension;

    ChatArchiveFormat(String defaultExtension) {
        this.defaultExtension = defaultExtension;
    }

    public String defaultExtension() {
        return defaultExtension;
    }
}
