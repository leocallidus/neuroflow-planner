package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.model.LocalDeviceIdentity;

public final class AuthClient {
    private final SyncTransport transport;

    public AuthClient() {
        this(new SyncTransport());
    }

    public AuthClient(SyncTransport transport) {
        this.transport = transport;
    }

    public SyncPayloads.TokenBundleResponse register(String email, String password, String displayName, LocalDeviceIdentity device) {
        SyncPayloads.RegisterRequest request = new SyncPayloads.RegisterRequest(
                email,
                password,
                blankToNull(displayName),
                toDeviceBinding(device));
        return transport.postJson("/auth/register", request, null, SyncPayloads.TokenBundleResponse.class);
    }

    public SyncPayloads.TokenBundleResponse login(String email, String password, LocalDeviceIdentity device) {
        SyncPayloads.LoginRequest request = new SyncPayloads.LoginRequest(
                email,
                password,
                toDeviceBinding(device));
        return transport.postJson("/auth/login", request, null, SyncPayloads.TokenBundleResponse.class);
    }

    public SyncPayloads.TokenBundleResponse refresh(String refreshToken) {
        return transport.postJson(
                "/auth/refresh",
                new SyncPayloads.RefreshRequest(refreshToken),
                null,
                SyncPayloads.TokenBundleResponse.class);
    }

    public void logout(String refreshToken) {
        transport.postNoContent("/auth/logout", new SyncPayloads.LogoutRequest(refreshToken), null);
    }

    public SyncPayloads.DeviceListResponse listDevices(String accessToken) {
        return transport.getJson("/devices", accessToken, SyncPayloads.DeviceListResponse.class);
    }

    public SyncPayloads.DeviceRevokeResponse revokeDevice(String accessToken, String deviceId) {
        return transport.postJson("/devices/" + deviceId + "/revoke", null, accessToken, SyncPayloads.DeviceRevokeResponse.class);
    }

    private SyncPayloads.DeviceBindingInput toDeviceBinding(LocalDeviceIdentity device) {
        if (device == null) {
            throw new IllegalArgumentException("device identity is required");
        }
        return new SyncPayloads.DeviceBindingInput(
                blankToNull(device.deviceId()),
                requireNonBlank(device.deviceLabel(), "device label"),
                requireNonBlank(device.platform(), "platform"),
                blankToNull(device.appVersion()));
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
