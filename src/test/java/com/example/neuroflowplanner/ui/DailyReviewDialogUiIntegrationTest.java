package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.service.dailyreview.DailyReviewResult;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewService;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewSnapshot;
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

class DailyReviewDialogUiIntegrationTest extends IsolatedTestDataFixture {

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
    void showsLoadingStateWhileReviewIsStillFetching() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        StubDailyReviewService service = new StubDailyReviewService();
        service.nextResult = new CompletableFuture<>();

        DailyReviewDialog dialog = runOnFxThread(() -> DailyReviewDialog.testingInstance(service, null, null));

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
        StubDailyReviewService service = new StubDailyReviewService();
        CompletableFuture<DailyReviewResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("AI unavailable"));
        service.nextResult = failed;

        DailyReviewDialog dialog = runOnFxThread(() -> DailyReviewDialog.testingInstance(service, null, null));
        runOnFxThread(() -> null);

        VBox loadingState = getField(dialog, "loadingState", VBox.class);
        VBox errorState = getField(dialog, "errorState", VBox.class);
        VBox emptyState = getField(dialog, "emptyState", VBox.class);

        assertFalse(loadingState.isVisible());
        assertTrue(errorState.isVisible());
        assertFalse(emptyState.isVisible());
    }

    @Test
    void showsEmptyStateWhenSnapshotHasNoDailySignals() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        StubDailyReviewService service = new StubDailyReviewService();
        service.nextResult = CompletableFuture.completedFuture(new DailyReviewResult(
                new DailyReviewSnapshot(
                        LocalDate.of(2026, 3, 10),
                        Instant.parse("2026-03-10T08:00:00Z"),
                        0,
                        0,
                        0,
                        0,
                        0,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                Instant.parse("2026-03-10T08:00:00Z"),
                "",
                false,
                false
        ));

        DailyReviewDialog dialog = runOnFxThread(() -> DailyReviewDialog.testingInstance(service, null, null));
        runOnFxThread(() -> null);

        VBox loadingState = getField(dialog, "loadingState", VBox.class);
        VBox errorState = getField(dialog, "errorState", VBox.class);
        VBox emptyState = getField(dialog, "emptyState", VBox.class);

        assertFalse(loadingState.isVisible());
        assertFalse(errorState.isVisible());
        assertTrue(emptyState.isVisible());
    }

    private static final class StubDailyReviewService extends DailyReviewService {
        private CompletableFuture<DailyReviewResult> nextResult = CompletableFuture.completedFuture(new DailyReviewResult(null, null, "", false, false));

        @Override
        public CompletableFuture<DailyReviewResult> getReview(LocalDate reviewDate, boolean forceRefresh) {
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
