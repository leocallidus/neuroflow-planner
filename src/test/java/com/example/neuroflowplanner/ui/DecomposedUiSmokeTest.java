package com.example.neuroflowplanner.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Decomposed UI Smoke Tests")
class DecomposedUiSmokeTest {
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
        } catch (Throwable throwable) {
            fxRuntimeReady = false;
        }
    }

    @Test
    @DisplayName("MainView adapter bootstraps and exposes root content")
    void mainViewAdapterBootstrap() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");

        MainView mainView = runOnFxThread(MainView::new);

        assertNotNull(mainView);
        assertNotNull(mainView.getCenter());
        assertTrue(mainView.canCloseApplication());
    }

    @Test
    @DisplayName("SmartNotesDialog inline adapter bootstraps existing entry-point")
    void smartNotesInlineBootstrap() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");

        InlineView smartNotes = runOnFxThread(SmartNotesDialog::inline);

        assertNotNull(smartNotes);
        assertNotNull(smartNotes.getContent());
        assertNotNull(smartNotes.getTitle());
    }

    private static <T> T runOnFxThread(Supplier<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result.get(20, TimeUnit.SECONDS);
    }
}
