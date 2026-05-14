package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.ui.mainview.LegacyMainView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GanttChartDialogIntegrationTest {
    private static final double EPS = 0.51;
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
    void ganttOpensInlineInsideLegacyMainViewWithoutLayoutBreakage() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createMainView(1366, 768);
        List<Task> tasks = sampleTasks();

        runOnFxThread(() -> {
            view.openOrActivateTab("global:inline.gantt", GanttChartDialog.inline(tasks), "Диаграмма Ганта");
            return null;
        });
        settleFx(view);

        StackPane overlayHost = getPrivateField(view, "overlayHost", StackPane.class);
        VBox overlayContainer = getPrivateField(view, "overlayContainer", VBox.class);
        StackPane overlayContentHolder = getPrivateField(view, "overlayContentHolder", StackPane.class);
        Node content = runOnFxThread(() -> overlayContentHolder.getChildren().isEmpty() ? null : overlayContentHolder.getChildren().get(0));

        assertTrue(runOnFxThread(overlayHost::isVisible), "Inline overlay host must stay visible");
        assertNotNull(content, "Active overlay content must be rendered");
        assertTrue(content.getStyleClass().contains("gantt-root"));
        assertNotNull(runOnFxThread(() -> content.lookup(".gantt-header-panel")));
        assertFalse(runOnFxThread(() -> content.lookupAll(".gantt-bar").isEmpty()));
        assertTrue(runOnFxThread(() -> ((Region) content).getWidth() <= overlayContainer.getWidth() + EPS));
        runOnFxThread(() -> {
            BoundsAsserts.assertNodeFitsWithinHost(overlayContainer, overlayHost, "gantt inline overlay");
            return null;
        });
    }

    @Test
    void compactOverlayKeepsHeaderAndScrollPanesOperational() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        GanttChartDialog dialog = createDialog(sampleTasks());
        OverlayHarness harness = mountInOverlay(
                dialog.getContent(),
                1320,
                760,
                "inline-overlay-width-compact",
                "inline-overlay-height-low"
        );
        settleFx(harness.host);

        assertTrue(runOnFxThread(() -> dialog.getContent().getStyleClass().contains("gantt-density-compact")));
        assertNotNull(runOnFxThread(() -> dialog.getContent().lookup(".gantt-header-panel")));
        assertNotNull(runOnFxThread(() -> dialog.getContent().lookup(".gantt-legend-row")));
        assertTrue(runOnFxThread(() -> dialog.getContent().lookupAll(".gantt-scroll-pane").size() >= 3));

        List<ScrollPane> scrollPanes = runOnFxThread(() -> findAllByStyleClass(dialog.getContent(), "gantt-scroll-pane", ScrollPane.class));
        for (ScrollPane scrollPane : scrollPanes) {
            assertTrue(scrollPane.isVisible() && scrollPane.isManaged(), "Compact overlay must keep scroll panes usable");
        }
    }

    @Test
    void veryCompactOverlayCollapsesLegendButPreservesHeaderAndScroll() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        GanttChartDialog dialog = createDialog(sampleTasks());
        OverlayHarness harness = mountInOverlay(
                dialog.getContent(),
                1240,
                660,
                "inline-overlay-width-very-compact",
                "inline-overlay-height-very-low"
        );
        settleFx(harness.host);

        assertTrue(runOnFxThread(() -> dialog.getContent().getStyleClass().contains("gantt-density-very-compact")));
        assertNotNull(runOnFxThread(() -> dialog.getContent().lookup(".gantt-header-panel")));
        assertNull(runOnFxThread(() -> dialog.getContent().lookup(".gantt-legend-row")));
        assertTrue(runOnFxThread(() -> dialog.getContent().lookupAll(".gantt-scroll-pane").size() >= 3));
    }

    @Test
    void longTaskLabelsUseEllipsisAndInstalledTooltip() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        Task task = task(
                "long-label",
                "Очень длинное название задачи для проверки ellipsis и tooltip в inline диаграмме Ганта",
                LocalDate.now().plusDays(7),
                6,
                7.0
        );
        GanttChartDialog dialog = createDialog(List.of(task));
        OverlayHarness harness = mountInOverlay(dialog.getContent(), 1366, 768, "inline-overlay-width-compact");
        settleFx(harness.host);

        Label nameLabel = runOnFxThread(() -> findFirstByStyleClass(dialog.getContent(), "gantt-task-name", Label.class));
        assertNotNull(nameLabel);
        assertFalse(nameLabel.isWrapText());
        assertEquals("...", nameLabel.getEllipsisString());

        Tooltip tooltip = runOnFxThread(() -> findInstalledTooltip(nameLabel));
        assertNotNull(tooltip, "Long labels should expose tooltip with full task context");
        assertTrue(tooltip.getText().contains(task.getTitle()));
        assertTrue(tooltip.getText().contains("Дедлайн:"));
    }

    @Test
    void emptyStateAndOverdueStatesRenderDistinctly() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");

        GanttChartDialog emptyDialog = createDialog(List.of());
        OverlayHarness emptyHarness = mountInOverlay(emptyDialog.getContent(), 1366, 768, "inline-overlay-width-compact");
        settleFx(emptyHarness.host);
        assertNotNull(runOnFxThread(() -> emptyDialog.getContent().lookup(".gantt-empty-state")));
        assertTrue(runOnFxThread(() -> emptyDialog.getContent().lookupAll(".gantt-bar").isEmpty()));

        Task overdue = task("overdue", "Просроченная задача", LocalDate.now().minusDays(2), 4, 8.5);
        GanttChartDialog overdueDialog = createDialog(List.of(overdue));
        OverlayHarness overdueHarness = mountInOverlay(overdueDialog.getContent(), 1366, 768, "inline-overlay-width-compact");
        settleFx(overdueHarness.host);

        Region overdueRow = runOnFxThread(() -> findFirstByStyleClass(overdueDialog.getContent(), "gantt-row-overdue", Region.class));
        Region overdueBar = runOnFxThread(() -> findFirstByStyleClass(overdueDialog.getContent(), "gantt-bar-overdue", Region.class));
        assertNotNull(overdueRow, "Overdue task must mark row state");
        assertNotNull(overdueBar, "Overdue task must mark bar state");
        Tooltip overdueTooltip = runOnFxThread(() -> findInstalledTooltip(overdueBar));
        assertNotNull(overdueTooltip);
        assertTrue(overdueTooltip.getText().contains("просрочено"));
    }

    private static LegacyMainView createMainView(double width, double height) throws Exception {
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, width, height);
            created.applyCss();
            created.layout();
            return created;
        });
        settleFx(view);
        return view;
    }

    private static GanttChartDialog createDialog(List<Task> tasks) throws Exception {
        return runOnFxThread(() -> (GanttChartDialog) GanttChartDialog.inline(tasks));
    }

    private static OverlayHarness mountInOverlay(Node content, double width, double height, String... styleClasses) throws Exception {
        return runOnFxThread(() -> {
            VBox overlayContainer = new VBox();
            overlayContainer.getStyleClass().add("overlay-container");
            overlayContainer.getStyleClass().addAll(styleClasses);
            overlayContainer.setPrefSize(width, height);
            overlayContainer.setMinSize(width, height);
            overlayContainer.setMaxSize(width, height);
            overlayContainer.getChildren().add(content);

            StackPane host = new StackPane(overlayContainer);
            host.setPrefSize(width, height);
            host.setMinSize(width, height);
            host.setMaxSize(width, height);
            new Scene(host, width, height);
            host.applyCss();
            host.layout();
            return new OverlayHarness(host, overlayContainer);
        });
    }

    private static void settleFx(Region root) throws Exception {
        for (int i = 0; i < 3; i++) {
            runOnFxThread(() -> {
                root.applyCss();
                root.layout();
                return null;
            });
        }
    }

    private static Task task(String id, String title, LocalDate deadline, int complexity, double priority) {
        Task task = new Task(id, title, "", deadline, complexity);
        task.setSmartPriority(priority);
        return task;
    }

    private static List<Task> sampleTasks() {
        LocalDate today = LocalDate.now();
        Task parent = task("parent", "Подготовить квартальный релиз", today.plusDays(12), 8, 8.3);
        parent.setStartDate(today.plusDays(4));

        Task subtask = task("subtask", "Подзадача контроля качества", today.plusDays(9), 4, 6.2);
        subtask.setParentId(parent.getId());
        subtask.setStartDate(today.plusDays(6));
        parent.getSubtasks().add(subtask);

        Task todayTask = task("today", "Задача на сегодня", today, 2, 4.9);
        Task lowPriority = task("low", "Низкий приоритет", today.plusDays(4), 3, 2.4);
        lowPriority.setRecurrence("weekly");

        return List.of(parent, todayTask, lowPriority);
    }

    private static Tooltip findInstalledTooltip(Node node) {
        if (node == null) {
            return null;
        }
        for (Object value : node.getProperties().values()) {
            if (value instanceof Tooltip tooltip) {
                return tooltip;
            }
        }
        return null;
    }

    private static <T extends Node> T findFirstByStyleClass(Node root, String styleClass, Class<T> type) {
        List<T> found = findAllByStyleClass(root, styleClass, type);
        return found.isEmpty() ? null : found.get(0);
    }

    private static <T extends Node> List<T> findAllByStyleClass(Node root, String styleClass, Class<T> type) {
        List<T> result = new ArrayList<>();
        collectByStyleClass(root, styleClass, type, result);
        return result;
    }

    private static <T extends Node> void collectByStyleClass(Node node, String styleClass, Class<T> type, Collection<T> target) {
        if (node == null) {
            return;
        }
        if (node.getStyleClass().contains(styleClass) && type.isInstance(node)) {
            target.add(type.cast(node));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectByStyleClass(child, styleClass, type, target);
            }
        }
    }

    private static <T> T getPrivateField(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(target));
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

    private record OverlayHarness(StackPane host, VBox overlayContainer) {
    }

    private static final class BoundsAsserts {
        private BoundsAsserts() {
        }

        private static void assertNodeFitsWithinHost(Region node, Region host, String context) {
            assertNotNull(node, "Node required for bounds assert: " + context);
            assertNotNull(host, "Host required for bounds assert: " + context);
            assertFalse(node.getBoundsInParent().isEmpty(), "Node bounds should be measurable: " + context);
            assertTrue(node.getBoundsInParent().getMaxX() <= host.getWidth() + EPS, "Node maxX exceeds host width: " + context);
            assertTrue(node.getBoundsInParent().getMaxY() <= host.getHeight() + EPS, "Node maxY exceeds host height: " + context);
        }
    }
}
