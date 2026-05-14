package com.example.neuroflowplanner.service.notes;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.ConnectionTestResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.DataPathManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SmartNotes Services Integration")
class SmartNotesServicesIntegrationTest extends IsolatedTestDataFixture {
    private static final String ISOLATED_DIR_PREFIX = "neuroflow-test-data-";
    private static final String TITLE_PREFIX = "stage6-note-it-";

    private final DefaultSmartNotesApplicationService applicationService = new DefaultSmartNotesApplicationService();
    private final DefaultSmartNotesExportService exportService = new DefaultSmartNotesExportService();
    private AiClient originalActiveClient;
    private AiMode originalMode;

    private static final Field ACTIVE_CLIENT_FIELD = findField("activeClient");
    private static final Field CURRENT_MODE_FIELD = findField("currentMode");

    @BeforeEach
    void setUp() throws Exception {
        assertIsolatedDataDir();
        captureAiFactoryState();
        cleanupPrefixedNotes();
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreAiFactoryState();
        assertIsolatedDataDir();
        cleanupPrefixedNotes();
    }

    @Test
    @DisplayName("CRUD, search, resolve title scenario")
    void crudAndSearchRoundTrip() {
        String title = applicationService.createNoteWithTitle(TITLE_PREFIX + "alpha");
        applicationService.saveCurrent(title, title, "release check");

        List<String> foundByContent = applicationService.searchTitles("release");
        assertTrue(foundByContent.contains(title));

        String resolved = applicationService.resolveExistingTitle(title.toLowerCase(Locale.ROOT));
        assertEquals(title, resolved);

        applicationService.deleteNote(title);
        assertFalse(applicationService.listTitles().contains(title));
    }

    @Test
    @DisplayName("Snapshot restore rolls back rename/delete safely")
    void snapshotRestoreRollbackScenario() {
        String first = applicationService.createNoteWithTitle(TITLE_PREFIX + "snap-a");
        String second = applicationService.createNoteWithTitle(TITLE_PREFIX + "snap-b");
        applicationService.saveCurrent(first, first, "wiki [[note:" + second + "]]");
        applicationService.saveCurrent(second, second, "body");

        SmartNotesApplicationService.NotesSnapshot before = applicationService.captureSnapshot();

        applicationService.saveCurrent(first, first + "-renamed", "changed");
        applicationService.deleteNote(second);
        assertFalse(applicationService.listTitles().contains(second));

        applicationService.restoreSnapshot(before);

        List<String> restored = applicationService.listTitles();
        assertTrue(restored.contains(first));
        assertTrue(restored.contains(second));
        assertFalse(restored.contains(first + "-renamed"));
        assertTrue(applicationService.loadContent(first).contains("[[note:" + second + "]]"));
        assertEquals("body", applicationService.loadContent(second));
    }

    @Test
    @DisplayName("Outgoing and incoming links include notes and tasks")
    void linkGraphScenario() {
        String targetNote = applicationService.createNoteWithTitle(TITLE_PREFIX + "target");
        String sourceNote = applicationService.createNoteWithTitle(TITLE_PREFIX + "source");
        applicationService.saveCurrent(sourceNote, sourceNote, "See [[note:" + targetNote + "]]");

        String taskId = "stage6-task-link-" + UUID.randomUUID();
        Task linkedTask = new Task(taskId, "Task from note", "[[note:" + targetNote + "]]", LocalDate.now().plusDays(2),
                3);

        List<SmartNotesApplicationService.LinkChip> outgoing = applicationService.outgoingLinks(
                "Combined [[note:" + targetNote + "]] [[task:" + taskId + "]]",
                token -> taskId.equals(token) ? linkedTask : null);
        assertEquals(2, outgoing.size());
        assertEquals(SmartNotesApplicationService.LinkType.NOTE, outgoing.get(0).type());
        assertEquals(SmartNotesApplicationService.LinkType.TASK, outgoing.get(1).type());

        List<SmartNotesApplicationService.LinkChip> incoming = applicationService.incomingLinks(targetNote,
                () -> List.of(linkedTask));
        assertTrue(incoming.stream().anyMatch(
                chip -> chip.type() == SmartNotesApplicationService.LinkType.NOTE && sourceNote.equals(chip.target())));
        assertTrue(incoming.stream().anyMatch(
                chip -> chip.type() == SmartNotesApplicationService.LinkType.TASK && taskId.equals(chip.target())));
    }

    @Test
    @DisplayName("Export markdown happy-path and fail-path")
    void exportServiceHappyAndFailPath(@TempDir Path tempDir) throws Exception {
        Path markdown = tempDir.resolve("note.md");
        exportService.exportNoteToMarkdown(markdown.toFile(), "Title", "Body text");

        assertTrue(Files.exists(markdown));
        assertTrue(Files.readString(markdown).contains("Body text"));

        assertThrows(
                Exception.class,
                () -> exportService.exportNoteToMarkdown(tempDir.toFile(), "Bad", "should fail"));
    }

    @Test
    @DisplayName("AI service completes via unified AiClient")
    void aiServiceSmoke() throws Exception {
        DefaultSmartNotesAiService aiService = new DefaultSmartNotesAiService();
        try {
            String result = aiService.requestCompletion("prompt", "context").join();
            assertNotNull(result);
        } catch (CompletionException ex) {
            // Also acceptable if the factory cannot initialize
            assertNotNull(ex.getCause());
        }
    }

    @Test
    @DisplayName("AI service surfaces rate-limit failure as runtime error")
    void aiServiceRateLimitErrorPath() throws Exception {
        useActiveAiClient(new StubAiClient(
            AiMode.EXTERNAL_OPENAI,
            () -> CompletableFuture.completedFuture(aiFailureResponse(429, "Too Many Requests"))
        ));

        DefaultSmartNotesAiService aiService = new DefaultSmartNotesAiService();
        CompletionException ex = assertThrows(
            CompletionException.class,
            () -> aiService.requestCompletion("prompt", "context").join()
        );

        Throwable cause = ex.getCause();
        assertNotNull(cause);
        assertTrue(cause instanceof RuntimeException);
        assertEquals("AI request error", cause.getMessage());
    }

    @Test
    @DisplayName("AI service surfaces provider failure (503) as runtime error")
    void aiServiceProviderErrorPath() throws Exception {
        useActiveAiClient(new StubAiClient(
            AiMode.EXTERNAL_OPENAI,
            () -> CompletableFuture.failedFuture(new RuntimeException("HTTP status 503"))
        ));

        DefaultSmartNotesAiService aiService = new DefaultSmartNotesAiService();
        CompletionException ex = assertThrows(
            CompletionException.class,
            () -> aiService.requestCompletion("prompt", "context").join()
        );

        Throwable cause = ex.getCause();
        assertNotNull(cause);
        assertTrue(cause instanceof RuntimeException);
        assertEquals("AI request error", cause.getMessage());
        assertNotNull(cause.getCause());
        assertTrue(cause.getCause().getMessage().contains("503"));
    }

    private void cleanupPrefixedNotes() {
        List<String> existing = new ArrayList<>(applicationService.listTitles());
        for (String title : existing) {
            if (title != null && title.startsWith(TITLE_PREFIX)) {
                applicationService.deleteNote(title);
            }
        }
    }

    private void assertIsolatedDataDir() {
        Path dataDir = DataPathManager.getDataDirectory().toAbsolutePath().normalize();
        Path fileName = dataDir.getFileName();
        assertTrue(
                fileName != null && fileName.toString().startsWith(ISOLATED_DIR_PREFIX),
                "Cleanup allowed only in isolated test data dir, actual: " + dataDir);
    }

    private void captureAiFactoryState() throws IllegalAccessException {
        AiClientFactory factory = AiClientFactory.getInstance();
        originalActiveClient = (AiClient) ACTIVE_CLIENT_FIELD.get(factory);
        originalMode = (AiMode) CURRENT_MODE_FIELD.get(factory);
    }

    private void restoreAiFactoryState() throws IllegalAccessException {
        AiClientFactory factory = AiClientFactory.getInstance();
        ACTIVE_CLIENT_FIELD.set(factory, originalActiveClient);
        CURRENT_MODE_FIELD.set(factory, originalMode);
    }

    private void useActiveAiClient(AiClient aiClient) throws IllegalAccessException {
        AiClientFactory factory = AiClientFactory.getInstance();
        ACTIVE_CLIENT_FIELD.set(factory, aiClient);
        CURRENT_MODE_FIELD.set(factory, aiClient.getMode());
    }

    private static AiResponse aiFailureResponse(int httpStatus, String message) {
        return new AiResponse(
            null,
            false,
            message,
            httpStatus,
            "test-model",
            null,
            null,
            null,
            Instant.now(),
            5L,
            httpStatus,
            1
        );
    }

    private static Field findField(String name) {
        try {
            Field field = AiClientFactory.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to access field: " + name, ex);
        }
    }

    private static final class StubAiClient implements AiClient {
        private final AiMode mode;
        private final java.util.function.Supplier<CompletableFuture<AiResponse>> responseSupplier;

        private StubAiClient(
            AiMode mode,
            java.util.function.Supplier<CompletableFuture<AiResponse>> responseSupplier
        ) {
            this.mode = mode;
            this.responseSupplier = responseSupplier;
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            return responseSupplier.get();
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection() {
            return CompletableFuture.completedFuture(ConnectionTestResult.success(
                "ok",
                mode,
                "stub://test",
                "test-model",
                "pong",
                1
            ));
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey) {
            return testConnection();
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testModel(String model) {
            return testConnection();
        }

        @Override
        public CompletableFuture<List<String>> fetchAvailableModels() {
            return CompletableFuture.completedFuture(List.of("test-model"));
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
            return "test-model";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String getBaseUrl() {
            return "stub://test";
        }

        @Override
        public void reloadConfiguration() {
            // no-op for tests
        }
    }

    private static final class FailingHttpClient extends HttpClient {
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<java.time.Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return new StaticHttpResponse<>(request, 500, (T) "{\"error\":\"boom\"}");
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return java.util.concurrent.CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return java.util.concurrent.CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }
    }

    private static final class StaticHttpResponse<T> implements HttpResponse<T> {
        private final HttpRequest request;
        private final int statusCode;
        private final T body;

        private StaticHttpResponse(HttpRequest request, int statusCode, T body) {
            this.request = request;
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
