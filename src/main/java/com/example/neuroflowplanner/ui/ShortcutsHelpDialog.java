package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.ui.interaction.ShortcutRegistry;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight shortcuts reference dialog used by main and notes contexts.
 */
public final class ShortcutsHelpDialog {
    private ShortcutsHelpDialog() {
    }

    public static void show(Window owner, String title, List<ShortcutHelpEntry> entries) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title == null || title.isBlank() ? "Горячие клавиши" : title.trim());
        dialog.initModality(Modality.NONE);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(ButtonType.CLOSE);
        pane.setHeaderText("Подсказка по горячим клавишам");
        pane.setGraphic(null);
        pane.setContent(buildContent(entries));
        pane.setPrefSize(560, 380);
        pane.getStyleClass().add("shortcuts-help-dialog-pane");
        applyStyles(pane);

        dialog.show();
    }

    private static VBox buildContent(List<ShortcutHelpEntry> entries) {
        List<ShortcutHelpEntry> safeEntries = entries == null ? List.of() : entries;

        GridPane table = new GridPane();
        table.setHgap(14);
        table.setVgap(8);
        table.setPadding(new Insets(6, 6, 6, 6));

        Label shortcutHeader = new Label("Клавиши");
        shortcutHeader.getStyleClass().add("shortcuts-help-header");
        Label actionHeader = new Label("Действие");
        actionHeader.getStyleClass().add("shortcuts-help-header");

        table.add(shortcutHeader, 0, 0);
        table.add(actionHeader, 1, 0);

        int row = 1;
        for (ShortcutHelpEntry entry : safeEntries) {
            Label shortcut = new Label(entry.shortcut());
            shortcut.getStyleClass().add("shortcuts-help-shortcut");
            Label action = new Label(entry.action());
            action.getStyleClass().add("shortcuts-help-action");
            action.setWrapText(true);

            table.add(shortcut, 0, row);
            table.add(action, 1, row);
            row++;
        }

        table.getColumnConstraints().addAll(
            createColumn(180, false),
            createColumn(340, true)
        );

        ScrollPane scroll = new ScrollPane(table);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPadding(new Insets(0));
        scroll.getStyleClass().add("shortcuts-help-scroll");

        Label hint = new Label("Конфликты шорткатов проверяются при старте приложения.");
        hint.getStyleClass().add("shortcuts-help-hint");

        VBox root = new VBox(10, scroll, hint);
        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(8));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return root;
    }

    private static ColumnConstraints createColumn(double prefWidth, boolean hgrow) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setPrefWidth(prefWidth);
        constraints.setFillWidth(true);
        if (hgrow) {
            constraints.setHgrow(Priority.ALWAYS);
        }
        return constraints;
    }

    private static void applyStyles(DialogPane pane) {
        URL appCss = ShortcutsHelpDialog.class.getResource("/styles/app.css");
        if (appCss != null) {
            pane.getStylesheets().add(appCss.toExternalForm());
        }
        if (ConfigManager.isDarkTheme()) {
            URL darkCss = ShortcutsHelpDialog.class.getResource("/styles/dark-theme.css");
            if (darkCss != null) {
                pane.getStylesheets().add(darkCss.toExternalForm());
            }
        }
    }

    public static List<ShortcutHelpEntry> defaultMainEntries() {
        List<ShortcutHelpEntry> entries = new ArrayList<>();
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+K", "Открыть командную палитру"));
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+F", "Фокус глобального поиска"));
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+Shift+L", "Показать/скрыть inline-вкладки"));
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+Z", "Undo: отменить действие"));
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+Shift+Z", "Redo: повторить действие"));
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+N", "Создать новую задачу"));
        appendFamiliarAliases(entries);
        return List.copyOf(entries);
    }

    public static List<ShortcutHelpEntry> defaultNotesEntries() {
        List<ShortcutHelpEntry> entries = new ArrayList<>();
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+K", "Открыть командную палитру"));
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+F", "Фокус глобального поиска"));
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+Z", "Undo: отменить действие с заметкой"));
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+Shift+Z", "Redo: повторить действие с заметкой"));
        entries.add(new ShortcutHelpEntry("Ctrl/Cmd+N", "Создать новую заметку"));
        appendFamiliarAliases(entries);
        return List.copyOf(entries);
    }

    private static void appendFamiliarAliases(List<ShortcutHelpEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<ShortcutRegistry.ShortcutAlias> aliases = ShortcutRegistry.activeShortcutAliases();
        if (aliases.isEmpty()) {
            return;
        }
        for (ShortcutRegistry.ShortcutAlias alias : aliases) {
            if (alias == null) {
                continue;
            }
            entries.add(new ShortcutHelpEntry(
                alias.displayShortcut(),
                "Obsidian-паттерн: " + alias.actionLabel()
            ));
        }
    }

    public record ShortcutHelpEntry(String shortcut, String action) {
        public ShortcutHelpEntry {
            shortcut = normalize(shortcut, "-");
            action = normalize(action, "—");
        }

        private static String normalize(String value, String fallback) {
            if (value == null) {
                return fallback;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? fallback : trimmed;
        }
    }
}
