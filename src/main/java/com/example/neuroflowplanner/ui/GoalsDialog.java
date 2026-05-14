package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.error.AppError;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.Goal;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GoalsDialog implements InlineView {

    private static final String PERIOD_WEEKLY = "weekly";
    private static final String PERIOD_MONTHLY = "monthly";

    private final VBox root;
    private final VBox weeklyList = new VBox(10);
    private final VBox monthlyList = new VBox(10);
    private final TextField titleField = new TextField();
    private final ComboBox<String> periodCombo = new ComboBox<>();
    private final Spinner<Integer> targetSpinner = new Spinner<>(1, 1000, 5);
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;

    private GoalsDialog() {
        root = new VBox(0);
        root.getStyleClass().add("goals-root");

        HBox header = createHeader();
        root.getChildren().add(header);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("goals-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(18);
        content.setPadding(new Insets(20, 25, 25, 25));

        VBox addBox = createAddGoalBox();
        VBox weeklySection = createGoalSection("Цели на неделю", weeklyList);
        VBox monthlySection = createGoalSection("Цели на месяц", monthlyList);

        content.getChildren().addAll(addBox, weeklySection, monthlySection);
        scrollPane.setContent(content);
        root.getChildren().add(scrollPane);

        root.setMinSize(400, 350);
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }

        refreshGoals();
    }

    public static InlineView inline() {
        return new GoalsDialog();
    }

    @Override
    public Node getContent() {
        return root;
    }

    @Override
    public Runnable getOnClose() {
        return null;
    }

    @Override
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    @Override
    public String getTitle() {
        return "Цели";
    }

    private HBox createHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 15, 25));
        header.getStyleClass().add("goals-header-panel");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("goals-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignT.TARGET, 24);
        icon.getStyleClass().add("goals-header-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Цели");
        title.getStyleClass().add("goals-title");
        Label subtitle = new Label("Недельные и месячные цели с прогрессом");
        subtitle.getStyleClass().add("goals-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        return header;
    }

    private VBox createAddGoalBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("goals-add-box");
        box.setPadding(new Insets(15));

        Label title = new Label("Новая цель");
        title.getStyleClass().add("goals-section-title");

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        titleField.setPromptText("Например: Закрыть 5 задач");
        titleField.getStyleClass().add("goals-text-field");
        HBox.setHgrow(titleField, Priority.ALWAYS);

        periodCombo.getItems().addAll("Неделя", "Месяц");
        periodCombo.setValue("Неделя");
        periodCombo.getStyleClass().add("goals-combo");

        targetSpinner.setEditable(true);
        targetSpinner.getStyleClass().add("goals-spinner");
        targetSpinner.setPrefWidth(90);

        Button addBtn = new Button("Добавить");
        addBtn.setGraphic(FontIcon.of(MaterialDesignP.PLUS, 16));
        addBtn.getStyleClass().add("goals-add-btn");
        addBtn.setOnAction(e -> handleAddGoal());

        row.getChildren().addAll(titleField, periodCombo, targetSpinner, addBtn);
        box.getChildren().addAll(title, row);
        return box;
    }

    private VBox createGoalSection(String titleText, VBox list) {
        VBox section = new VBox(12);
        section.getStyleClass().add("goals-section");
        section.setPadding(new Insets(15));

        Label title = new Label(titleText);
        title.getStyleClass().add("goals-section-title");

        section.getChildren().addAll(title, list);
        return section;
    }

    private void refreshGoals() {
        List<Goal> goals = db.loadGoals();
        List<Goal> weekly = goals.stream()
            .filter(goal -> PERIOD_WEEKLY.equalsIgnoreCase(goal.getPeriod()))
            .collect(Collectors.toList());
        List<Goal> monthly = goals.stream()
            .filter(goal -> PERIOD_MONTHLY.equalsIgnoreCase(goal.getPeriod()))
            .collect(Collectors.toList());

        weeklyList.getChildren().clear();
        if (weekly.isEmpty()) {
            Label empty = new Label("Недельных целей пока нет");
            empty.getStyleClass().add("goals-empty");
            weeklyList.getChildren().add(empty);
        } else {
            for (Goal goal : weekly) {
                weeklyList.getChildren().add(buildGoalCard(goal));
            }
        }

        monthlyList.getChildren().clear();
        if (monthly.isEmpty()) {
            Label empty = new Label("Месячных целей пока нет");
            empty.getStyleClass().add("goals-empty");
            monthlyList.getChildren().add(empty);
        } else {
            for (Goal goal : monthly) {
                monthlyList.getChildren().add(buildGoalCard(goal));
            }
        }
    }

    private Node buildGoalCard(Goal goal) {
        VBox card = new VBox(8);
        card.getStyleClass().add("goal-card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(goal.getTitle());
        title.getStyleClass().add("goal-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        Label periodLabel = new Label(periodToLabel(goal.getPeriod()));
        periodLabel.getStyleClass().add("goal-period");

        Label statusLabel = new Label(goal.isCompleted() ? "Выполнено" : "В процессе");
        statusLabel.getStyleClass().add(goal.isCompleted() ? "goal-status-done" : "goal-status");

        header.getChildren().addAll(title, periodLabel, statusLabel);

        ProgressBar progressBar = new ProgressBar();
        progressBar.getStyleClass().add("goal-progress-bar");
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        Label progressText = new Label();
        progressText.getStyleClass().add("goal-progress-text");

        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button minusBtn = new Button();
        minusBtn.setGraphic(FontIcon.of(MaterialDesignM.MINUS, 14));
        minusBtn.getStyleClass().add("goal-action-btn");
        minusBtn.setTooltip(new Tooltip("Уменьшить"));

        Button plusBtn = new Button();
        plusBtn.setGraphic(FontIcon.of(MaterialDesignP.PLUS, 14));
        plusBtn.getStyleClass().add("goal-action-btn");
        plusBtn.setTooltip(new Tooltip("Увеличить"));

        Button resetBtn = new Button();
        resetBtn.setGraphic(FontIcon.of(MaterialDesignR.RELOAD, 14));
        resetBtn.getStyleClass().add("goal-action-btn");
        resetBtn.setTooltip(new Tooltip("Сбросить прогресс"));

        Button deleteBtn = new Button();
        deleteBtn.setGraphic(FontIcon.of(MaterialDesignD.DELETE_OUTLINE, 14));
        deleteBtn.getStyleClass().addAll("goal-action-btn", "goal-action-danger");
        deleteBtn.setTooltip(new Tooltip("Удалить цель"));

        actions.getChildren().addAll(minusBtn, plusBtn, resetBtn, deleteBtn);

        card.getChildren().addAll(header, progressBar, progressText, actions);

        Runnable updateUI = () -> applyGoalState(goal, card, progressBar, progressText, statusLabel, minusBtn, plusBtn, resetBtn);
        updateUI.run();

        minusBtn.setOnAction(e -> {
            int next = Math.max(0, goal.getProgress() - 1);
            updateGoalProgress(goal, next);
            updateUI.run();
        });

        plusBtn.setOnAction(e -> {
            int next = Math.min(goal.getTarget(), goal.getProgress() + 1);
            updateGoalProgress(goal, next);
            updateUI.run();
        });

        resetBtn.setOnAction(e -> {
            updateGoalProgress(goal, 0);
            updateUI.run();
        });

        deleteBtn.setOnAction(e -> {
            if (confirmDelete(goal)) {
                db.deleteGoal(goal.getId());
                refreshGoals();
            }
        });

        return card;
    }

    private void applyGoalState(Goal goal, VBox card, ProgressBar progressBar, Label progressText, Label statusLabel,
                                Button minusBtn, Button plusBtn, Button resetBtn) {
        int target = Math.max(goal.getTarget(), 1);
        int progress = Math.max(0, goal.getProgress());
        double ratio = Math.min(1.0, progress / (double) target);
        progressBar.setProgress(ratio);

        String text = String.format("Прогресс: %d / %d (%.0f%%)", progress, target, ratio * 100);
        progressText.setText(text);

        boolean completed = progress >= target;
        statusLabel.setText(completed ? "Выполнено" : "В процессе");
        statusLabel.getStyleClass().setAll(completed ? "goal-status-done" : "goal-status");

        card.getStyleClass().remove("goal-card-complete");
        if (completed) {
            card.getStyleClass().add("goal-card-complete");
        }

        minusBtn.setDisable(progress == 0);
        resetBtn.setDisable(progress == 0);
        plusBtn.setDisable(progress >= target);
    }

    private void updateGoalProgress(Goal goal, int progress) {
        goal.setProgress(progress);
        goal.setUpdatedAt(LocalDateTime.now().toString());
        db.saveGoal(goal);
    }

    private void handleAddGoal() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Новая цель", "Введите название цели.");
            return;
        }
        int target = targetSpinner.getValue() != null ? targetSpinner.getValue() : 1;
        String period = "Месяц".equalsIgnoreCase(periodCombo.getValue()) ? PERIOD_MONTHLY : PERIOD_WEEKLY;
        String now = LocalDateTime.now().toString();
        Goal goal = new Goal(UUID.randomUUID().toString(), title, period, Math.max(1, target), 0, now, now);
        db.saveGoal(goal);

        titleField.clear();
        targetSpinner.getValueFactory().setValue(5);
        refreshGoals();
    }

    private String periodToLabel(String period) {
        return PERIOD_MONTHLY.equalsIgnoreCase(period) ? "Месяц" : "Неделя";
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        javafx.stage.Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        if (type == Alert.AlertType.ERROR) {
            UiErrorNotifier.showError(
                owner,
                isDark,
                title,
                new AppError(
                    ErrorCode.UNEXPECTED_ERROR,
                    message,
                    "goals.dialog.error",
                    false,
                    Map.of("operation", "goals.showAlert")
                )
            );
            return;
        }
        if (type == Alert.AlertType.WARNING) {
            UiErrorNotifier.showWarning(owner, isDark, title, message);
            return;
        }
        UiErrorNotifier.showInfo(owner, isDark, title, message);
    }

    private boolean confirmDelete(Goal goal) {
        ButtonType okType = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
            "Удалить цель \"" + goal.getTitle() + "\"?",
            okType, cancelType);
        alert.setTitle("Удаление цели");
        alert.setHeaderText(null);
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        return alert.showAndWait().orElse(cancelType) == okType;
    }
}
