package com.example.neuroflowplanner.service.imagecapability;

import java.util.List;

/**
 * Resolved image configuration with optional compatibility issues.
 */
public record ImageConfigResolution(
    ImageValidatedOptions options,
    List<String> issues
) {
    public ImageConfigResolution {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public String summary() {
        return String.join(" ", issues);
    }
}
