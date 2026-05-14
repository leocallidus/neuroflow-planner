package com.example.neuroflowplanner.ui.commandpalette;

import com.example.neuroflowplanner.model.search.GlobalSearchResult;
import com.example.neuroflowplanner.ui.interaction.UiActionRegistry;
import com.example.neuroflowplanner.ui.interaction.UndoRedoManager;
import com.example.neuroflowplanner.ui.interaction.UserActionCommand;
import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteViewMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.NavSurfaceHeightBand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPaletteControllerTest {

    @Test
    void searchAggregatesActionsTasksAndNotesAndExecutesNavigation() {
        UiActionRegistry actionRegistry = new UiActionRegistry(new UndoRedoManager(true, 20));
        AtomicInteger actionExecutions = new AtomicInteger();
        actionRegistry.register(registeredAction(
            "main.deploy.open",
            "Deploy dashboard",
            "tasks",
            "Ctrl/Cmd+N",
            actionExecutions::incrementAndGet,
            () -> true,
            () -> ""
        ));

        AtomicInteger navigationCalls = new AtomicInteger();
        AtomicReference<GlobalSearchResult> opened = new AtomicReference<>();
        CommandPaletteController controller = new CommandPaletteController(
            "main",
            actionRegistry,
            (query, limit) -> List.of(
                GlobalSearchResult.task("task-7", "Deploy API", "Deploy checklist", 160),
                GlobalSearchResult.note("Roadmap", "Deploy milestones", 120)
            ),
            result -> {
                opened.set(result);
                navigationCalls.incrementAndGet();
                return true;
            },
            new CommandPaletteHistory(10)
        );

        List<CommandPaletteItem> found = controller.search("deploy", 10);
        assertTrue(found.stream().anyMatch(item -> item.type() == CommandPaletteItemType.ACTION));
        assertTrue(found.stream().anyMatch(item -> item.type() == CommandPaletteItemType.TASK));
        assertTrue(found.stream().anyMatch(item -> item.type() == CommandPaletteItemType.NOTE));

        CommandPaletteItem taskItem = found.stream()
            .filter(item -> item.type() == CommandPaletteItemType.TASK)
            .findFirst()
            .orElseThrow();
        CommandPaletteController.ExecutionResult executionResult = controller.execute(taskItem, "deploy", 1);

        assertTrue(executionResult.successful());
        assertEquals(1, navigationCalls.get());
        assertEquals("task-7", opened.get().id());
    }

    @Test
    void recentExecutionIsPrioritizedForBlankQuery() {
        UiActionRegistry actionRegistry = new UiActionRegistry(new UndoRedoManager(true, 20));
        actionRegistry.register(registeredAction(
            "main.task.add",
            "Добавить задачу",
            "tasks",
            "",
            () -> {},
            () -> true,
            () -> ""
        ));
        actionRegistry.register(registeredAction(
            "main.notes.open",
            "Открыть заметки",
            "tools",
            "",
            () -> {},
            () -> true,
            () -> ""
        ));

        CommandPaletteController controller = new CommandPaletteController(
            "main",
            actionRegistry,
            (query, limit) -> List.of(),
            result -> false,
            new CommandPaletteHistory(10)
        );

        List<CommandPaletteItem> initial = controller.search("", 10);
        CommandPaletteItem notesAction = initial.stream()
            .filter(item -> "main.notes.open".equals(item.commandId()))
            .findFirst()
            .orElseThrow();
        assertTrue(controller.execute(notesAction, "", 1).successful());

        List<CommandPaletteItem> after = controller.search("", 10);
        assertFalse(after.isEmpty());
        assertEquals("main.notes.open", after.get(0).commandId());
        assertTrue(after.get(0).recent());
    }

    @Test
    void unavailableActionFailsFastWithReason() {
        UiActionRegistry actionRegistry = new UiActionRegistry(new UndoRedoManager(true, 20));
        actionRegistry.register(registeredAction(
            "main.bulk.delete",
            "Удалить выбранные задачи",
            "bulk",
            "",
            () -> {},
            () -> false,
            () -> "Выберите задачи"
        ));

        CommandPaletteController controller = new CommandPaletteController(
            "main",
            actionRegistry,
            (query, limit) -> List.of(),
            result -> false,
            new CommandPaletteHistory(10)
        );

        CommandPaletteItem item = controller.search("удалить", 10).stream()
            .filter(found -> "main.bulk.delete".equals(found.commandId()))
            .findFirst()
            .orElseThrow();
        assertFalse(item.available());
        assertEquals("Выберите задачи", item.unavailableReason());

        CommandPaletteController.ExecutionResult result = controller.execute(item, "удалить", 1);
        assertFalse(result.successful());
        assertTrue(result.message().contains("Выберите задачи"));
    }

    @Test
    void executeActionCommandFromPaletteTriggersRegisteredAction() {
        UiActionRegistry actionRegistry = new UiActionRegistry(new UndoRedoManager(true, 20));
        AtomicInteger executions = new AtomicInteger();
        actionRegistry.register(registeredAction(
            "main.task.add",
            "Добавить задачу",
            "tasks",
            "Ctrl/Cmd+N",
            executions::incrementAndGet,
            () -> true,
            () -> ""
        ));

        CommandPaletteController controller = new CommandPaletteController(
            "main",
            actionRegistry,
            (query, limit) -> List.of(),
            result -> false,
            new CommandPaletteHistory(10)
        );

        CommandPaletteItem actionItem = controller.search("добавить", 10).stream()
            .filter(item -> item.type() == CommandPaletteItemType.ACTION)
            .findFirst()
            .orElseThrow();
        CommandPaletteController.ExecutionResult result = controller.execute(actionItem, "добавить", 1);

        assertTrue(result.successful());
        assertEquals(1, executions.get());
    }

    @Test
    void revealInSidebarUsesSharedActionIdBridge() {
        UiActionRegistry actionRegistry = new UiActionRegistry(new UndoRedoManager(true, 20));
        actionRegistry.register(registeredAction(
            "main.task.bulk.archive",
            "Архивировать выбранные задачи",
            "bulk",
            "",
            () -> {},
            () -> true,
            () -> ""
        ));

        AtomicReference<String> revealedActionId = new AtomicReference<>();
        CommandPaletteController controller = new CommandPaletteController(
            "main",
            actionRegistry,
            (query, limit) -> List.of(),
            result -> false,
            actionId -> {
                revealedActionId.set(actionId);
                return true;
            },
            new CommandPaletteHistory(10)
        );

        CommandPaletteItem actionItem = controller.search("архивировать", 10).stream()
            .filter(item -> item.type() == CommandPaletteItemType.ACTION)
            .findFirst()
            .orElseThrow();

        controller.setSidebarRevealTargetHintResolver(actionId -> "домене \"Система\"");
        CommandPaletteController.ExecutionResult result = controller.revealInSidebar(actionItem);
        assertTrue(result.successful());
        assertEquals("main.task.bulk.archive", revealedActionId.get());
        assertTrue(result.message().contains("домене \"Система\""));
    }

    @Test
    void externalActionExecutionSyncsPaletteHistoryAndUsesSharedShortcutHintResolver() {
        UiActionRegistry actionRegistry = new UiActionRegistry(new UndoRedoManager(true, 20));
        actionRegistry.register(registeredAction(
            "main.system.settings",
            "Настройки",
            "system",
            "CTRL+,",
            () -> {},
            () -> true,
            () -> ""
        ));

        CommandPaletteController controller = new CommandPaletteController(
            "main",
            actionRegistry,
            (query, limit) -> List.of(),
            result -> false,
            null,
            actionId -> "Ctrl/Cmd+,",
            new CommandPaletteHistory(10)
        );

        assertTrue(controller.recordExternalActionExecution("main.system.settings"));

        List<CommandPaletteItem> items = controller.search("", 10);
        CommandPaletteItem settings = items.stream()
            .filter(item -> "main.system.settings".equals(item.commandId()))
            .findFirst()
            .orElseThrow();

        assertTrue(settings.recent());
        assertEquals("Ctrl/Cmd+,", settings.shortcutHint());
    }

    @Test
    void globalSearchProviderFailureDoesNotBreakSearch() {
        UiActionRegistry actionRegistry = new UiActionRegistry(new UndoRedoManager(true, 20));
        actionRegistry.register(registeredAction(
            "main.system.settings",
            "Настройки",
            "system",
            "",
            () -> {},
            () -> true,
            () -> ""
        ));

        CommandPaletteController controller = new CommandPaletteController(
            "main",
            actionRegistry,
            (query, limit) -> {
                throw new IllegalStateException("Search backend unavailable");
            },
            result -> false,
            new CommandPaletteHistory(10)
        );

        List<CommandPaletteItem> items = controller.search("настройки", 10);
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(item -> "main.system.settings".equals(item.commandId())));
    }

    @Test
    void aliasAndPrefixRankingPromotesSettingsForPlainLanguageQuery() {
        UiActionRegistry actionRegistry = new UiActionRegistry(new UndoRedoManager(true, 20));
        actionRegistry.register(registeredAction(
            "main.system.settings",
            "Settings",
            "system",
            "",
            () -> {},
            () -> true,
            () -> ""
        ));
        actionRegistry.register(registeredAction(
            "main.task.listAll",
            "Все задачи",
            "tasks",
            "",
            () -> {},
            () -> true,
            () -> ""
        ));

        CommandPaletteController controller = new CommandPaletteController(
            "main",
            actionRegistry,
            (query, limit) -> List.of(),
            result -> false,
            new CommandPaletteHistory(10)
        );

        List<CommandPaletteItem> found = controller.search("настрой", 10);

        assertFalse(found.isEmpty());
        assertEquals("main.system.settings", found.get(0).commandId());
    }

    @Test
    void guidedViewModelBuildsRecentSuggestedActionsAndEntitiesSections() {
        UiActionRegistry actionRegistry = new UiActionRegistry(new UndoRedoManager(true, 20));
        actionRegistry.register(registeredAction(
            "main.system.settings",
            "Настройки",
            "system",
            "",
            () -> {},
            () -> true,
            () -> ""
        ));
        actionRegistry.register(registeredAction(
            "main.inbox.addTask",
            "Добавить задачу",
            "tasks",
            "Ctrl/Cmd+N",
            () -> {},
            () -> true,
            () -> ""
        ));
        actionRegistry.register(registeredAction(
            "main.ai.autoSchedule",
            "ИИ: авто-планирование",
            "ai",
            "",
            () -> {},
            () -> true,
            () -> ""
        ));

        CommandPaletteHistory history = new CommandPaletteHistory(10);
        CommandPaletteController controller = new CommandPaletteController(
            "mainview",
            actionRegistry,
            (query, limit) -> List.of(
                GlobalSearchResult.task("task-1", "Настроить деплой", "entity", 120),
                GlobalSearchResult.note("Runbook", "Настройки среды", 90)
            ),
            result -> true,
            history
        );
        CommandPaletteItem settings = controller.search("настройки", 10).stream()
            .filter(item -> "main.system.settings".equals(item.commandId()))
            .findFirst()
            .orElseThrow();
        assertTrue(controller.execute(settings, "настройки", 1).successful());

        CommandPaletteDisplayPolicy policy = new CommandPaletteDisplayPolicy(
            UiLayoutBreakpoint.WIDE,
            NavSurfaceHeightBand.TALL,
            UiLayoutMode.COMFORTABLE,
            CommandPaletteViewMode.GUIDED,
            true,
            true,
            true,
            true,
            true,
            false,
            true,
            12,
            4
        );

        CommandPaletteViewModel queryViewModel = controller.buildViewModel("настро", policy);
        CommandPaletteViewModel blankViewModel = controller.buildViewModel("", policy);

        assertTrue(queryViewModel.sections().stream().anyMatch(s -> s.group() == CommandPaletteResultGroup.RECENT));
        assertTrue(queryViewModel.sections().stream().anyMatch(s -> s.group() == CommandPaletteResultGroup.ENTITIES));
        assertTrue(queryViewModel.flatItems().stream().anyMatch(i -> i.type() == CommandPaletteItemType.ACTION));
        assertFalse(queryViewModel.flatItems().isEmpty());
        assertTrue(blankViewModel.sections().stream().anyMatch(s -> s.group() == CommandPaletteResultGroup.SUGGESTED));
    }

    private static UiActionRegistry.RegisteredAction registeredAction(
        String actionId,
        String label,
        String category,
        String shortcut,
        Runnable runnable,
        BooleanSupplier availability,
        Supplier<String> unavailableReason
    ) {
        return new UiActionRegistry.RegisteredAction(
            actionId,
            label,
            category,
            shortcut,
            () -> runOnlyCommand(actionId, label, category, runnable, availability),
            availability,
            unavailableReason,
            false
        );
    }

    private static UserActionCommand runOnlyCommand(
        String actionId,
        String label,
        String category,
        Runnable runnable,
        BooleanSupplier availability
    ) {
        return new UserActionCommand() {
            @Override
            public String actionId() {
                return actionId;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public String category() {
                return category;
            }

            @Override
            public boolean canExecute() {
                return availability.getAsBoolean();
            }

            @Override
            public boolean canUndo() {
                return false;
            }

            @Override
            public void execute() {
                runnable.run();
            }
        };
    }
}
