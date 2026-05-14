package com.example.neuroflowplanner.ai.resilience;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.ConnectionTestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ModeFallbackWrapperTest {

    private AiClient primaryClient;
    private AiClient fallbackClient1;
    private AiClient fallbackClient2;

    @BeforeEach
    void setUp() {
        primaryClient = new StubAiClient(AiMode.EXTERNAL_OPENAI, "Primary Response", false);
        fallbackClient1 = new StubAiClient(AiMode.LOCAL_OLLAMA, "Fallback 1 Response", false);
        fallbackClient2 = new StubAiClient(AiMode.OFFLINE, "Fallback 2 Response", false);
    }

    @Test
    void shouldReturnPrimaryResponseWhenSuccessful() throws Exception {
        ModeFallbackAiClientWrapper wrapper = new ModeFallbackAiClientWrapper(
                primaryClient, List.of(() -> fallbackClient1, () -> fallbackClient2));

        AiResponse resp = wrapper.sendChatMessage("test", new AiRequestOptions("test", null, null, null, null, null, null, false))
                .get();
        assertEquals("Primary Response", resp.content());
    }

    @Test
    void shouldFallbackToFirstConfiguredClientOnFailure() throws Exception {
        ((StubAiClient) primaryClient).setFail(true);
        ModeFallbackAiClientWrapper wrapper = new ModeFallbackAiClientWrapper(
                primaryClient, List.of(() -> fallbackClient1, () -> fallbackClient2));

        AiResponse resp = wrapper.sendChatMessage("test", new AiRequestOptions("test", null, null, null, null, null, null, false))
                .get();
        assertEquals("Fallback 1 Response", resp.content());
    }

    @Test
    void shouldSkipUnconfiguredClientsAndUseNextFallback() throws Exception {
        ((StubAiClient) primaryClient).setFail(true);
        ((StubAiClient) fallbackClient1).setConfigured(false);

        ModeFallbackAiClientWrapper wrapper = new ModeFallbackAiClientWrapper(
                primaryClient, List.of(() -> fallbackClient1, () -> fallbackClient2));

        AiResponse resp = wrapper.sendChatMessage("test", new AiRequestOptions("test", null, null, null, null, null, null, false))
                .get();
        assertEquals("Fallback 2 Response", resp.content());
    }

    @Test
    void shouldFailIfAllClientsFail() {
        ((StubAiClient) primaryClient).setFail(true);
        ((StubAiClient) fallbackClient1).setFail(true);
        ((StubAiClient) fallbackClient2).setFail(true);

        ModeFallbackAiClientWrapper wrapper = new ModeFallbackAiClientWrapper(
                primaryClient, List.of(() -> fallbackClient1, () -> fallbackClient2));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            wrapper.sendChatMessage("test", new AiRequestOptions("test", null, null, null, null, null, null, false)).get();
        });
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals("Stub simulated failure", exception.getCause().getMessage());
    }

    private static class StubAiClient implements AiClient {
        private final AiMode mode;
        private final String responseText;
        private boolean fail;
        private boolean configured = true;

        public StubAiClient(AiMode mode, String responseText, boolean fail) {
            this.mode = mode;
            this.responseText = responseText;
            this.fail = fail;
        }

        public void setFail(boolean fail) {
            this.fail = fail;
        }

        public void setConfigured(boolean configured) {
            this.configured = configured;
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            if (fail)
                return CompletableFuture.failedFuture(new RuntimeException("Stub simulated failure"));
            return CompletableFuture.completedFuture(AiResponse.success(responseText, mode.name()));
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection() {
            return null;
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey) {
            return null;
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testModel(String model) {
            return null;
        }

        @Override
        public CompletableFuture<List<String>> fetchAvailableModels() {
            return null;
        }

        @Override
        public boolean supportsImages() {
            return false;
        }

        @Override
        public AiMode getMode() {
            return mode;
        }

        @Override
        public String getDefaultModel() {
            return "test_model";
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String getBaseUrl() {
            return "http://test";
        }

        @Override
        public void reloadConfiguration() {
        }
    }
}
