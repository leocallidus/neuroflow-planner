package com.example.neuroflowplanner.model;

public record LocalDeviceIdentity(
    String deviceId,
    String deviceLabel,
    String platform,
    String appVersion,
    String createdAt,
    String updatedAt
) {
}
