package com.example.neuroflowplanner.service.chatio;

import java.util.List;

public class ChatArchiveImportValidationException extends IllegalArgumentException {
    private final List<String> validationErrors;

    public ChatArchiveImportValidationException(String message) {
        this(message, null, List.of(message));
    }

    public ChatArchiveImportValidationException(String message, Throwable cause, List<String> validationErrors) {
        super(message, cause);
        this.validationErrors = validationErrors == null ? List.of(message) : List.copyOf(validationErrors);
    }

    public List<String> validationErrors() {
        return validationErrors;
    }
}
