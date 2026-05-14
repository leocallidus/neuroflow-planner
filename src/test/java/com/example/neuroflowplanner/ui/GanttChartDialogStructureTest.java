package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GanttChartDialogStructureTest {
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
    void dateRangeNormalizationExtendsToEarliestEstimateAndLatestDeadline() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LocalDate today = LocalDate.now();

        Task latestDeadline = task("latest", "Поздняя задача", today.plusDays(45), 4, 5.0);
        Task earliestEstimate = task("earliest", "Ранняя оценка", today.plusDays(10), 20, 8.0);
        Task archived = task("archived", "Архивная", today.plusDays(90), 3, 2.0);
        archived.setArchived(true);

        GanttChartDialog dialog = createDialog(List.of(latestDeadline, earliestEstimate, archived));
        Object dateRange = invoke(
                dialog,
                "resolveDateRange",
                new Class<?>[]{List.class},
                new Object[]{List.of(latestDeadline, earliestEstimate, archived)}
        );

        assertEquals(today, accessor(dateRange, "today", LocalDate.class));
        assertEquals(today.minusDays(11), accessor(dateRange, "minDate", LocalDate.class));
        assertEquals(today.plusDays(48), accessor(dateRange, "maxDate", LocalDate.class));
        assertEquals(59L, accessor(dateRange, "totalDays", Long.class));
    }

    @Test
    void densityResolutionUsesVeryCompactContractOnTightOverlay() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LocalDate today = LocalDate.now();

        Task task = task("compact", "Компактный режим", today.plusDays(35), 6, 7.5);
        GanttChartDialog dialog = createDialog(List.of(task));
        Object dateRange = invoke(dialog, "resolveDateRange", new Class<?>[]{List.class}, new Object[]{List.of(task)});
        Object adaptiveContext = newNestedInstance(
                "AdaptiveContext",
                1180.0,
                640.0,
                true,
                true,
                true,
                true
        );

        Object density = invoke(
                dialog,
                "resolveDensityConfig",
                new Class<?>[]{adaptiveContext.getClass(), dateRange.getClass(), int.class},
                new Object[]{adaptiveContext, dateRange, 12}
        );

        assertEquals("VERY_COMPACT", ((Enum<?>) accessor(density, "mode", Enum.class)).name());
        assertBetween(accessor(density, "dayWidth", Double.class), 18.0, 24.0);
        assertBetween(accessor(density, "rowHeight", Double.class), 24.0, 28.0);
        assertBetween(accessor(density, "taskColumnWidth", Double.class), 168.0, 232.0);
        assertEquals(false, accessor(density, "showSubtitle", Boolean.class));
        assertEquals(true, accessor(density, "showMetaRow", Boolean.class));
        assertEquals(false, accessor(density, "showLegend", Boolean.class));
        assertEquals(true, accessor(density, "compactMetaRow", Boolean.class));
        assertEquals(true, accessor(density, "compactLegend", Boolean.class));
        assertEquals(true, accessor(density, "reducedTimelineLabels", Boolean.class));
    }

    @Test
    void singleDayBarsStayVisibleOnLongRanges() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LocalDate today = LocalDate.now();

        Task shortTask = task("short", "Короткая задача на длинном горизонте", today.plusDays(180), 1, 5.0);
        shortTask.setStartDate(today.plusDays(180));

        GanttChartDialog dialog = createDialog(List.of(shortTask));
        Object dateRange = invoke(dialog, "resolveDateRange", new Class<?>[]{List.class}, new Object[]{List.of(shortTask)});
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) invoke(dialog, "collectRows", new Class<?>[]{List.class}, new Object[]{List.of(shortTask)});
        Object adaptiveContext = newNestedInstance(
                "AdaptiveContext",
                960.0,
                620.0,
                true,
                true,
                true,
                true
        );
        Object density = invoke(
                dialog,
                "resolveDensityConfig",
                new Class<?>[]{adaptiveContext.getClass(), dateRange.getClass(), int.class},
                new Object[]{adaptiveContext, dateRange, rows.size()}
        );

        Node chartSection = runOnFxThread(() -> (Node) invoke(
                dialog,
                "buildChartSection",
                new Class<?>[]{List.class, dateRange.getClass(), density.getClass()},
                new Object[]{rows, dateRange, density}
        ));
        runOnFxThread(() -> {
            StackPane host = new StackPane(chartSection);
            new Scene(host, 960, 620);
            host.applyCss();
            host.layout();
            return null;
        });

        Pane bar = runOnFxThread(() -> findFirstByStyleClass(chartSection, "gantt-bar", Pane.class));
        double minBarWidth = accessor(density, "minBarWidth", Double.class);

        assertNotNull(bar, "Rendered chart must contain at least one task bar");
        assertTrue(bar.getPrefWidth() >= minBarWidth, "Short bars must stay visually present");
        assertTrue(bar.getLayoutX() >= 0.0, "Bar layout must not overflow to negative X");
        assertTrue(bar.getStyleClass().contains("gantt-bar-short"), "Single-day bar should receive short-range rendering cue");
        assertTrue(bar.getChildren().size() >= 1, "Decorated short bar should retain visual cues");
    }

    private static Task task(String id, String title, LocalDate deadline, int complexity, double priority) {
        Task task = new Task(id, title, "", deadline, complexity);
        task.setSmartPriority(priority);
        return task;
    }

    private static GanttChartDialog createDialog(List<Task> tasks) throws Exception {
        return runOnFxThread(() -> (GanttChartDialog) GanttChartDialog.inline(tasks));
    }

    private static Object newNestedInstance(String simpleName, Object... args) throws Exception {
        for (Class<?> nestedClass : GanttChartDialog.class.getDeclaredClasses()) {
            if (!Objects.equals(nestedClass.getSimpleName(), simpleName)) {
                continue;
            }
            for (Constructor<?> constructor : nestedClass.getDeclaredConstructors()) {
                if (constructor.getParameterCount() != args.length) {
                    continue;
                }
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            }
        }
        throw new IllegalStateException("Nested class not found: " + simpleName);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T accessor(Object target, String methodName, Class<T> type) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (T) method.invoke(target);
    }

    private static void assertBetween(double value, double minInclusive, double maxInclusive) {
        assertTrue(
                value >= minInclusive && value <= maxInclusive,
                "Expected value within [" + minInclusive + ", " + maxInclusive + "] but got " + value
        );
    }

    private static <T extends Node> T findFirstByStyleClass(Node root, String styleClass, Class<T> type) {
        if (root == null) {
            return null;
        }
        if (root.getStyleClass().contains(styleClass) && type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = findFirstByStyleClass(child, styleClass, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T runOnFxThread(FxSupplier<T> supplier) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(supplier.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface FxSupplier<T> {
        T get() throws Exception;
    }
}
