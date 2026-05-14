package com.example.neuroflowplanner.model;

public record LocalAccountLink(
    String userId,
    String email,
    String displayName,
    String status,
    String linkedAt,
    String lastAuthenticatedAt
) {
}
