package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.Goal;
import com.example.neuroflowplanner.model.LocalSyncOutboxEntry;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.SyncConfigDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SyncCoordinator Tests")
class SyncCoordinatorTest extends IsolatedTestDataFixture {
    private final ObjectMapper mapper = AiObjectMapperFactory.createMapper(false);
    private HttpServer server;

    @AfterEach
    void tearDownServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("login and bootstrap apply remote changes into local SQLite")
    void loginAndBootstrapApplyRemoteChanges() throws Exception {
        AtomicInteger bootstrapCalls = new AtomicInteger();
        AtomicInteger loginCalls = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/login", exchange -> {
            loginCalls.incrementAndGet();
            JsonNode request = readJson(exchange);
            assertNotNull(request.get("device"));
            writeJson(exchange, 200, """
                {
                  "access_token": "access-login",
                  "refresh_token": "refresh-login",
                  "token_type": "bearer",
                  "expires_in_seconds": 3600,
                  "user": {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "email": "sync@example.com",
                    "display_name": "Sync User",
                    "is_active": true
                  },
                  "device": {
                    "id": "%s",
                    "device_label": "test-device",
                    "platform": "linux",
                    "app_version": "dev",
                    "registered_at": "2026-03-23T00:00:00Z",
                    "last_seen_at": null,
                    "revoked_at": null
                  },
                  "refresh_session_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                }
                """.formatted(request.get("device").get("device_id").asText()));
        });
        server.createContext("/sync/bootstrap", exchange -> {
            bootstrapCalls.incrementAndGet();
            writeJson(exchange, 200, """
                {
                  "user_id": "11111111-1111-1111-1111-111111111111",
                  "device_id": "22222222-2222-2222-2222-222222222222",
                  "latest_change_id": 9,
                  "next_change_id": 9,
                  "has_more": false,
                  "supported_entity_types": ["TASK", "GOAL"],
                  "changes": [
                    {
                      "change_id": 9,
                      "entity_type": "TASK",
                      "entity_id": "33333333-3333-3333-3333-333333333333",
                      "operation": "UPSERT",
                      "committed_at": "2026-03-23T00:00:00Z",
                      "payload": {
                        "id": "33333333-3333-3333-3333-333333333333",
                        "title": "Remote task",
                        "description": "synced from cloud",
                        "deadline_date": "2026-03-25",
                        "complexity": 4,
                        "tags": ["cloud", "stage9"],
                        "recurrence_rule": null
                      }
                    }
                  ]
                }
                """);
        });
        server.createContext("/health/live", exchange -> writeJson(exchange, 200, """
            {"status":"alive","service":"backend","environment":"test","version":"1","request_id":"req"}
            """));
        server.start();

        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_BASE_URL, baseUrl());
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "true");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_REQUEST_TIMEOUT_MS, "5000");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_CONNECT_TIMEOUT_MS, "5000");

        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        try (SyncCoordinator coordinator = new SyncCoordinator(repository, new AuthClient(), new SyncApiClient())) {
            SyncSessionSnapshot session = coordinator.login("sync@example.com", "password").get();
            assertTrue(session.linked());

            SyncRunResult result = coordinator.syncNow(SyncTrigger.MANUAL).get();
            assertEquals("sync_bootstrap_completed", result.status());
            assertEquals(1, result.remoteChangeCount());
            assertEquals(9L, repository.loadAppliedCursor(), "cursor must advance after remote changes are applied");
            assertEquals(9L, repository.loadLastKnownChangeId());
            assertEquals(
                "33333333-3333-3333-3333-333333333333",
                DatabaseManager.getInstance().loadAllTasks().stream()
                    .findFirst()
                    .map(task -> task.getId())
                    .orElse("")
            );
            assertEquals(1, loginCalls.get());
            assertEquals(1, bootstrapCalls.get());
        }
    }

    @Test
    @DisplayName("push sends outbox changes and advances cursor when backend returns no remote delta")
    void pushConsumesOutboxAndAdvancesCursor() throws Exception {
        AtomicInteger pushCalls = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/login", exchange -> {
            JsonNode request = readJson(exchange);
            writeJson(exchange, 200, """
                {
                  "access_token": "access-login",
                  "refresh_token": "refresh-login",
                  "token_type": "bearer",
                  "expires_in_seconds": 3600,
                  "user": {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "email": "sync@example.com",
                    "display_name": "Sync User",
                    "is_active": true
                  },
                  "device": {
                    "id": "%s",
                    "device_label": "test-device",
                    "platform": "linux",
                    "app_version": "dev",
                    "registered_at": "2026-03-23T00:00:00Z",
                    "last_seen_at": null,
                    "revoked_at": null
                  },
                  "refresh_session_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                }
                """.formatted(request.get("device").get("device_id").asText()));
        });
        server.createContext("/sync/push", exchange -> {
            pushCalls.incrementAndGet();
            JsonNode request = readJson(exchange);
            assertEquals(1, request.get("changes").size());
            String clientChangeId = request.get("changes").get(0).get("client_change_id").asText();
            writeJson(exchange, 200, """
                {
                  "accepted": [
                    {
                      "client_change_id": "%s",
                      "entity_type": "TASK",
                      "entity_id": "44444444-4444-4444-4444-444444444444",
                      "operation": "UPSERT",
                      "server_change_id": 11,
                      "idempotent_replay": false
                    }
                  ],
                  "remote_since_change_id": 10,
                  "remote_next_change_id": 11,
                  "latest_change_id": 11,
                  "has_more_remote_changes": false,
                  "remote_changes": []
                }
                """.formatted(clientChangeId));
        });
        server.createContext("/health/live", exchange -> writeJson(exchange, 200, """
            {"status":"alive","service":"backend","environment":"test","version":"1","request_id":"req"}
            """));
        server.start();

        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_BASE_URL, baseUrl());
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "true");

        DatabaseManager databaseManager = DatabaseManager.getInstance();
        SyncStateRepository repository = new SyncStateRepository(databaseManager);
        repository.saveAppliedCursor(10L);

        String entityId = UUID.randomUUID().toString();
        databaseManager.enqueueSyncChange(
                "TASK",
                entityId,
                "UPSERT",
                "{\"id\":\"" + entityId + "\",\"title\":\"Local task\"}");

        try (SyncCoordinator coordinator = new SyncCoordinator(repository, new AuthClient(), new SyncApiClient())) {
            coordinator.login("sync@example.com", "password").get();

            SyncRunResult result = coordinator.syncNow(SyncTrigger.MANUAL).get();
            assertEquals("sync_completed", result.status());
            assertEquals(1, result.acceptedChanges());
            assertEquals(11L, repository.loadAppliedCursor());
            assertEquals(11L, repository.loadLastKnownChangeId());
            assertTrue(databaseManager.loadPendingSyncOutbox(10).isEmpty());
            assertEquals(1, pushCalls.get());
        }
    }

    @Test
    @DisplayName("push sends goal before goal progress delta for the same local goal")
    void pushOrdersGoalBeforeGoalProgressEntry() throws Exception {
        AtomicInteger pushCalls = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/login", exchange -> {
            JsonNode request = readJson(exchange);
            writeJson(exchange, 200, """
                {
                  "access_token": "access-login",
                  "refresh_token": "refresh-login",
                  "token_type": "bearer",
                  "expires_in_seconds": 3600,
                  "user": {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "email": "sync@example.com",
                    "display_name": "Sync User",
                    "is_active": true
                  },
                  "device": {
                    "id": "%s",
                    "device_label": "test-device",
                    "platform": "linux",
                    "app_version": "dev",
                    "registered_at": "2026-03-23T00:00:00Z",
                    "last_seen_at": null,
                    "revoked_at": null
                  },
                  "refresh_session_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                }
                """.formatted(request.get("device").get("device_id").asText()));
        });
        server.createContext("/sync/push", exchange -> {
            pushCalls.incrementAndGet();
            JsonNode request = readJson(exchange);
            assertEquals(2, request.get("changes").size());
            assertEquals("GOAL", request.get("changes").get(0).get("entity_type").asText());
            assertEquals("GOAL_PROGRESS_ENTRY", request.get("changes").get(1).get("entity_type").asText());
            assertEquals("WEEK", request.get("changes").get(0).get("payload").get("period_type_code").asText());
            assertEquals(
                request.get("changes").get(0).get("entity_id").asText(),
                request.get("changes").get(1).get("payload").get("goal_id").asText()
            );

            String goalClientChangeId = request.get("changes").get(0).get("client_change_id").asText();
            String progressClientChangeId = request.get("changes").get(1).get("client_change_id").asText();
            String goalEntityId = request.get("changes").get(0).get("entity_id").asText();
            String progressEntityId = request.get("changes").get(1).get("entity_id").asText();

            writeJson(exchange, 200, """
                {
                  "accepted": [
                    {
                      "client_change_id": "%s",
                      "entity_type": "GOAL",
                      "entity_id": "%s",
                      "operation": "UPSERT",
                      "server_change_id": 21,
                      "idempotent_replay": false
                    },
                    {
                      "client_change_id": "%s",
                      "entity_type": "GOAL_PROGRESS_ENTRY",
                      "entity_id": "%s",
                      "operation": "UPSERT",
                      "server_change_id": 22,
                      "idempotent_replay": false
                    }
                  ],
                  "remote_since_change_id": 10,
                  "remote_next_change_id": 22,
                  "latest_change_id": 22,
                  "has_more_remote_changes": false,
                  "remote_changes": []
                }
                """.formatted(goalClientChangeId, goalEntityId, progressClientChangeId, progressEntityId));
        });
        server.createContext("/health/live", exchange -> writeJson(exchange, 200, """
            {"status":"alive","service":"backend","environment":"test","version":"1","request_id":"req"}
            """));
        server.start();

        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_BASE_URL, baseUrl());
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "true");

        DatabaseManager databaseManager = DatabaseManager.getInstance();
        SyncStateRepository repository = new SyncStateRepository(databaseManager);
        repository.saveAppliedCursor(10L);

        String goalId = UUID.randomUUID().toString();
        Goal goal = new Goal(goalId, "Sync goal", "weekly", 5, 0, "2026-03-23T00:00:00Z", "2026-03-23T00:00:00Z");
        databaseManager.saveGoal(goal);
        goal.setProgress(3);
        goal.setUpdatedAt("2026-03-23T01:00:00Z");
        databaseManager.saveGoal(goal);

        try (SyncCoordinator coordinator = new SyncCoordinator(repository, new AuthClient(), new SyncApiClient())) {
            coordinator.login("sync@example.com", "password").get();

            SyncRunResult result = coordinator.syncNow(SyncTrigger.MANUAL).get();
            assertEquals("sync_completed", result.status());
            assertEquals(2, result.acceptedChanges());
            assertEquals(1, pushCalls.get());
        }
    }

    @Test
    @DisplayName("sync discards legacy chat outbox entries and does not push unsupported entity types")
    void syncDiscardsLegacyChatOutboxEntries() throws Exception {
        AtomicInteger pushCalls = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/login", exchange -> {
            JsonNode request = readJson(exchange);
            writeJson(exchange, 200, """
                {
                  "access_token": "access-login",
                  "refresh_token": "refresh-login",
                  "token_type": "bearer",
                  "expires_in_seconds": 3600,
                  "user": {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "email": "sync@example.com",
                    "display_name": "Sync User",
                    "is_active": true
                  },
                  "device": {
                    "id": "%s",
                    "device_label": "test-device",
                    "platform": "linux",
                    "app_version": "dev",
                    "registered_at": "2026-03-23T00:00:00Z",
                    "last_seen_at": null,
                    "revoked_at": null
                  },
                  "refresh_session_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                }
                """.formatted(request.get("device").get("device_id").asText()));
        });
        server.createContext("/sync/push", exchange -> {
            pushCalls.incrementAndGet();
            writeJson(exchange, 500, """
                {"error":{"message":"legacy chat changes must not be pushed"}}
                """);
        });
        server.createContext("/health/live", exchange -> writeJson(exchange, 200, """
            {"status":"alive","service":"backend","environment":"test","version":"1","request_id":"req"}
            """));
        server.start();

        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_BASE_URL, baseUrl());
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "true");

        DatabaseManager databaseManager = DatabaseManager.getInstance();
        SyncStateRepository repository = new SyncStateRepository(databaseManager);
        repository.saveAppliedCursor(10L);

        String conversationId = databaseManager.createChatConversation("Cloud chat").getId();
        String messageId = UUID.randomUUID().toString();
        databaseManager.enqueueSyncChange("CHAT_CONVERSATION", conversationId, "UPSERT", "{\"id\":\"" + conversationId + "\"}");
        databaseManager.enqueueSyncChange(
            "CHAT_MESSAGE",
            messageId,
            "UPSERT",
            "{\"id\":\"" + messageId + "\",\"conversation_id\":\"" + conversationId + "\"}"
        );

        try (SyncCoordinator coordinator = new SyncCoordinator(repository, new AuthClient(), new SyncApiClient())) {
            coordinator.login("sync@example.com", "password").get();

            SyncRunResult result = coordinator.syncNow(SyncTrigger.MANUAL).get();
            assertEquals("sync_completed", result.status());
            assertEquals(0, result.acceptedChanges());
            assertEquals(0, pushCalls.get());
            assertTrue(databaseManager.loadPendingSyncOutbox(10).isEmpty());
        }
    }

    private JsonNode readJson(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return mapper.readTree(bytes);
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
