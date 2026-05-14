package com.example.neuroflowplanner.sync;

import java.util.Locale;

public enum AccountLinkStrategy {
    UPLOAD_LOCAL("upload_local", "Загрузить локальные данные в облако"),
    REPLACE_LOCAL("replace_local", "Заменить локальные данные облачными"),
    MERGE("merge", "Объединить локальные и облачные данные");

    private final String persistedValue;
    private final String displayLabel;

    AccountLinkStrategy(String persistedValue, String displayLabel) {
        this.persistedValue = persistedValue;
        this.displayLabel = displayLabel;
    }

    public String persistedValue() {
        return persistedValue;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public static AccountLinkStrategy fromPersistedValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (AccountLinkStrategy strategy : values()) {
            if (strategy.persistedValue.equals(normalized)) {
                return strategy;
            }
        }
        return null;
    }
}
