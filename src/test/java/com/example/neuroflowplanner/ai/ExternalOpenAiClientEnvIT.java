package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ExternalOpenAiClient ENV Integration")
class ExternalOpenAiClientEnvIT {

    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static int serverPort;
    private static String expectedEnvKey;
    private static String originalBaseUrl;
    private static volatile String receivedAuthorizationHeader;

    @BeforeAll
    static void setUp() throws IOException {
        String externalEnv = System.getenv(ConfigManager.ENV_EXTERNAL_API_KEY);
        String legacyEnv = System.getenv(ConfigManager.ENV_LEGACY_API_KEY);
        expectedEnvKey = (externalEnv != null && !externalEnv.isBlank()) ? externalEnv : legacyEnv;

        Assumptions.assumeTrue(
                expectedEnvKey != null && !expectedEnvKey.isBlank(),
                "Integration test requires NEUROFLOW_EXTERNAL_API_KEY or NEUROFLOW_API_KEY"
        );

        originalBaseUrl = ConfigManager.getProperty(ExternalOpenAiClient.CONFIG_BASE_URL);
        serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        serverPort = serverSocket.getLocalPort();
        serverThread = new Thread(ExternalOpenAiClientEnvIT::handleSingleRequest, "env-it-http-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        if (originalBaseUrl == null) {
            ConfigManager.setProperty(ExternalOpenAiClient.CONFIG_BASE_URL, "");
        } else {
            ConfigManager.setProperty(ExternalOpenAiClient.CONFIG_BASE_URL, originalBaseUrl);
        }
    }

    @Test
    @DisplayName("Connection test succeeds with key only in ENV")
    void testConnectionUsesEnvKeyWithoutConfigSecret() {
        String baseUrl = "http://127.0.0.1:" + serverPort + "/v1";
        ConfigManager.setProperty(ExternalOpenAiClient.CONFIG_BASE_URL, baseUrl);

        // Explicitly clear file-based secret values.
        ConfigManager.setProperty(ConfigManager.CONFIG_EXTERNAL_API_KEY, "");
        ConfigManager.setProperty(ConfigManager.CONFIG_API_KEY, "");

        ExternalOpenAiClient client = new ExternalOpenAiClient();
        ConnectionTestResult result = client.testConnection().join();

        assertTrue(result.success(), "Ожидалось успешное подключение с ключом из ENV");
        assertEquals("Bearer " + expectedEnvKey, receivedAuthorizationHeader);
    }

    private static void handleSingleRequest() {
        try (Socket socket = serverSocket.accept()) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            OutputStream out = socket.getOutputStream();

            String requestLine = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.regionMatches(true, 0, "Authorization:", 0, "Authorization:".length())) {
                    receivedAuthorizationHeader = line.substring("Authorization:".length()).trim();
                }
            }

            boolean validPath = requestLine != null && requestLine.contains(" /v1/models ");
            boolean validAuth = ("Bearer " + expectedEnvKey).equals(receivedAuthorizationHeader);
            int statusCode = (validPath && validAuth) ? 200 : 401;
            String responseBody = statusCode == 200
                    ? "{\"data\":[{\"id\":\"gpt-4o-mini\"}]}"
                    : "{\"error\":\"unauthorized\"}";

            byte[] bodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            String statusText = statusCode == 200 ? "OK" : "Unauthorized";
            String headers = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + bodyBytes.length + "\r\n"
                    + "Connection: close\r\n\r\n";

            out.write(headers.getBytes(StandardCharsets.UTF_8));
            out.write(bodyBytes);
            out.flush();
        } catch (IOException ignored) {
            // Socket may be closed during teardown.
        }
    }
}
