package com.example.neuroflowplanner.sync;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public final class SyncPayloads {

    private SyncPayloads() {
    }

    public enum SyncEntityCode {
        TASK,
        TASK_DEPENDENCY,
        TIME_SESSION,
        TASK_TEMPLATE,
        GOAL,
        GOAL_PROGRESS_ENTRY,
        MOOD_ENTRY
    }

    public enum SyncOperationCode {
        UPSERT,
        DELETE,
        RESTORE
    }

    public record DeviceBindingInput(
            String device_id,
            String device_label,
            String platform,
            String app_version) {
    }

    public record RegisterRequest(
            String email,
            String password,
            String display_name,
            DeviceBindingInput device) {
    }

    public record LoginRequest(
            String email,
            String password,
            DeviceBindingInput device) {
    }

    public record RefreshRequest(String refresh_token) {
    }

    public record LogoutRequest(String refresh_token) {
    }

    public record AuthenticatedUserResponse(
            String id,
            String email,
            String display_name,
            boolean is_active) {
    }

    public record DeviceSessionResponse(
            String id,
            String device_label,
            String platform,
            String app_version,
            String registered_at,
            String last_seen_at,
            String revoked_at) {
    }

    public record TokenBundleResponse(
            String access_token,
            String refresh_token,
            String token_type,
            int expires_in_seconds,
            AuthenticatedUserResponse user,
            DeviceSessionResponse device,
            String refresh_session_id) {
    }

    public record DeviceListItemResponse(
            String id,
            String device_label,
            String platform,
            String app_version,
            String registered_at,
            String last_seen_at,
            String revoked_at,
            int active_refresh_session_count,
            boolean is_current_device) {
    }

    public record DeviceListResponse(List<DeviceListItemResponse> devices) {
    }

    public record DeviceRevokeResponse(
            String id,
            String revoked_at,
            int revoked_refresh_session_count) {
    }

    public record SyncBootstrapRequest(int limit) {
    }

    public record SyncPullRequest(long since_change_id, int limit) {
    }

    public record ClientSyncChange(
            String client_change_id,
            SyncEntityCode entity_type,
            SyncOperationCode operation,
            String entity_id,
            JsonNode payload) {
    }

    public record SyncPushRequest(
            long since_change_id,
            int pull_limit,
            List<ClientSyncChange> changes) {
    }

    public record ServerSyncChange(
            long change_id,
            SyncEntityCode entity_type,
            String entity_id,
            SyncOperationCode operation,
            String committed_at,
            JsonNode payload) {
    }

    public record PushAcceptedChange(
            String client_change_id,
            SyncEntityCode entity_type,
            String entity_id,
            SyncOperationCode operation,
            long server_change_id,
            boolean idempotent_replay) {
    }

    public record SyncPullResponse(
            long since_change_id,
            long next_change_id,
            long latest_change_id,
            boolean has_more,
            List<ServerSyncChange> changes) {
    }

    public record SyncBootstrapResponse(
            String user_id,
            String device_id,
            long latest_change_id,
            long next_change_id,
            boolean has_more,
            List<SyncEntityCode> supported_entity_types,
            List<ServerSyncChange> changes) {
    }

    public record SyncPushResponse(
            List<PushAcceptedChange> accepted,
            long remote_since_change_id,
            long remote_next_change_id,
            long latest_change_id,
            boolean has_more_remote_changes,
            List<ServerSyncChange> remote_changes) {
    }

    public record ApiErrorEnvelope(ApiErrorBody error) {
    }

    public record ApiErrorBody(
            Integer status,
            String code,
            String message,
            JsonNode details,
            String category,
            Boolean retryable,
            String request_id) {
    }
}
