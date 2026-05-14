package com.example.neuroflowplanner.ai.json;

import java.util.List;
import java.util.StringJoiner;

public final class AiSchemaValidationException extends AiParsingException {
    private final String schemaPath;
    private final List<String> validationMessages;

    public AiSchemaValidationException(String schemaPath, List<String> validationMessages) {
        super("Schema validation failed for " + schemaPath + ": " + toSummary(validationMessages));
        this.schemaPath = schemaPath;
        this.validationMessages = validationMessages == null ? List.of() : List.copyOf(validationMessages);
    }

    public String schemaPath() {
        return schemaPath;
    }

    public List<String> validationMessages() {
        return validationMessages;
    }

    private static String toSummary(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return "unknown schema violation";
        }
        StringJoiner joiner = new StringJoiner("; ");
        int limit = Math.min(messages.size(), 3);
        for (int i = 0; i < limit; i++) {
            joiner.add(messages.get(i));
        }
        if (messages.size() > limit) {
            joiner.add("... (" + (messages.size() - limit) + " more)");
        }
        return joiner.toString();
    }
}
