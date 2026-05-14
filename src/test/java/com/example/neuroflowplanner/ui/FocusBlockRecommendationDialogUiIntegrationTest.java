package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.service.focusblocks.FocusBlockRecommendationResult;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRecommendationService;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRecommendationSnapshot;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusBlockRecommendationDialogUiIntegrationTest extends IsolatedTestDataFixture {

    private static boolean fxRuntimeReady;

    @BeforeAll
    static void initFxRuntime() {
        try {
            CompletableFuture<Void> started = new CompletableFuture<>();
            Platform.startup(() -> started.complete(null));
            started.get(5, TimeUnit.SECONDS);
            fxRuntimeReady = true;
        } catch (IllegalStateException alreadyStarted) {
            fxRuntimeReady = true;
        } catch (Throwable ignored) {
            fxRuntimeReady = false;
        }
    }

    @Test
    void showsLoadingStateWhileRecommendationsAreStillFetching() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        StubFocusBlockRecommendationService service = new StubFocusBlockRecommendationService();
        service.nextResult = new CompletableFuture<>();

        FocusBlockRecommendationDialog dialog = runOnFxThread(() -> FocusBlockRecommendationDialog.testingInstance(service));

        VBox loadingState = getField(dialog, "loadingState", VBox.class);
        VBox content = getField(dialog, "content", VBox.class);
        VBox errorState = getField(dialog, "errorState", VBox.class);
        VBox emptyState = getField(dialog, "emptyState", VBox.class);

        assertTrue(loadingState.isVisible());
        assertFalse(content.isVisible());
        assertFalse(errorState.isVisible());
        assertFalse(emptyState.isVisible());
    }

    @Test
    void showsErrorStateWhenServiceFails() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        StubFocusBlockRecommendationService service = new StubFocusBlockRecommendationService();
        CompletableFuture<FocusBlockRecommendationResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("AI unavailable"));
        service.nextResult = failed;

        FocusBlockRecommendationDialog dialog = runOnFxThread(() -> FocusBlockRecommendationDialog.testingInstance(service));
        runOnFxThread(() -> null);

        VBox loadingState = getField(dialog, "loadingState", VBox.class);
        VBox errorState = getField(dialog, "errorState", VBox.class);
        VBox emptyState = getField(dialog, "emptyState", VBox.class);

        assertFalse(loadingState.isVisible());
        assertTrue(errorState.isVisible());
        assertFalse(emptyState.isVisible());
    }

    @Test
    void showsEmptyStateWhenSnapshotHasNoFocusSignals() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        StubFocusBlockRecommendationService service = new StubFocusBlockRecommendationService();
        service.nextResult = CompletableFuture.completedFuture(new FocusBlockRecommendationResult(
                new FocusBlockRecommendationSnapshot(
                        LocalDate.of(2026, 3, 11),
                        Instant.parse("2026-03-11T08:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true
                ),
                Instant.parse("2026-03-11T08:00:00Z"),
                "",
                false,
                false
        ));

        FocusBlockRecommendationDialog dialog = runOnFxThread(() -> FocusBlockRecommendationDialog.testingInstance(service));
        runOnFxThread(() -> null);

        VBox loadingState = getField(dialog, "loadingState", VBox.class);
        VBox errorState = getField(dialog, "errorState", VBox.class);
        VBox emptyState = getField(dialog, "emptyState", VBox.class);

        assertFalse(loadingState.isVisible());
        assertFalse(errorState.isVisible());
        assertTrue(emptyState.isVisible());
    }

    private static final class StubFocusBlockRecommendationService extends FocusBlockRecommendationService {
        private CompletableFuture<FocusBlockRecommendationResult> nextResult =
                CompletableFuture.completedFuture(new FocusBlockRecommendationResult(null, null, "", false, false));

        @Override
        public CompletableFuture<FocusBlockRecommendationResult> getRecommendations(LocalDate reviewDate, boolean forceRefresh) {
            return nextResult;
        }
    }

    private static <T> T getField(Object target, String fieldName, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot access field: " + fieldName, ex);
        }
    }

    private static <T> T runOnFxThread(ThrowingSupplier<T> supplier) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
