package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.ImageGenConfigDefaults;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Styled dialog for choosing and managing custom model identifiers.
 */
public final class ModelManagementDialog {

    public record Result(String selectedModel, List<String> customModels) {
        public Result {
            customModels = customModels == null ? List.of() : List.copyOf(customModels);
        }
    }

    private final boolean isDark = ConfigManager.isDarkTheme();
    private final String title;
    private final String subtitle;
    private final String noteText;
    private final String defaultBadgeText;
    private final List<String> baseModels;
    private final List<String> providerModels;
    private final List<String> providerMultimodalModels;
    private final List<String> providerAudioInputModels;
    private final List<String> providerFileInputModels;
    private final String currentModel;
    private final ObservableList<String> customModels;
    private final ObservableList<String> visibleModels = FXCollections.observableArrayList();

    private Dialog<Result> dialog;
    private ListView<String> modelListView;
    private TextField searchField;
    private TextField customModelField;
    private Label statusLabel;

    public ModelManagementDialog(
            String title,
            String subtitle,
            String noteText,
            String defaultBadgeText,
            List<String> baseModels,
            List<String> providerModels,
            List<String> providerMultimodalModels,
            List<String> providerAudioInputModels,
            List<String> providerFileInputModels,
            List<String> customModels,
            String currentModel) {
        this.title = Objects.requireNonNullElse(title, "Модели");
        this.subtitle = Objects.requireNonNullElse(subtitle, "");
        this.noteText = Objects.requireNonNullElse(noteText, "");
        this.defaultBadgeText = Objects.requireNonNullElse(defaultBadgeText, "База");
        this.baseModels = baseModels == null ? List.of() : List.copyOf(baseModels);
        this.providerModels = providerModels == null ? List.of() : List.copyOf(providerModels);
        this.providerMultimodalModels = providerMultimodalModels == null ? List.of() : List.copyOf(providerMultimodalModels);
        this.providerAudioInputModels = providerAudioInputModels == null ? List.of() : List.copyOf(providerAudioInputModels);
        this.providerFileInputModels = providerFileInputModels == null ? List.of() : List.copyOf(providerFileInputModels);
        this.currentModel = AiConfigDefaults.normalizeExternalModelId(currentModel);
        this.customModels = FXCollections.observableArrayList(customModels == null ? List.of() : customModels);
        dedupeCustomModels();
    }

    public Result showAndWait() {
        createDialog();
        refreshVisibleModels(currentModel);
        dialog.showAndWait();
        return dialog.getResult();
    }

    public static Result showExternalApi(
            List<String> providerModels,
            List<String> providerMultimodalModels,
            List<String> providerAudioInputModels,
            List<String> providerFileInputModels,
            List<String> customModels,
            String currentModel) {
        return new ModelManagementDialog(
                "Каталог моделей API",
                "Добавьте ID модели вручную или выберите вариант из найденных и сохранённых.",
                "Текущая модель в основном экране хранится как обычный ID. Этот диалог помогает собрать и выбрать удобный список.",
                "База",
                AiConfigDefaults.MODEL_OPTIONS,
                providerModels,
                providerMultimodalModels,
                providerAudioInputModels,
                providerFileInputModels,
                customModels,
                currentModel
        ).showAndWait();
    }

    public static Result showImageModels(
            List<String> providerModels,
            List<String> customModels,
            String currentModel) {
        return new ModelManagementDialog(
                "Каталог image-моделей",
                "Добавьте свой ID image-модели, выберите найденную через API или базовую.",
                "Для неизвестных image-моделей дополнительные параметры автоматически отключаются, но сам ID можно сохранить и использовать.",
                "Image preset",
                ImageGenConfigDefaults.IMAGE_MODEL_OPTIONS,
                providerModels,
                List.of(),
                List.of(),
                List.of(),
                customModels,
                currentModel
        ).showAndWait();
    }

    private void createDialog() {
        dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().add("model-management-dialog");
        dialogPane.setPrefWidth(560);

        VBox content = new VBox(16);
        content.setPadding(new Insets(22));

        VBox header = new VBox(8);
        header.getStyleClass().add("model-management-header");

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("model-management-icon");
        iconPane.getChildren().add(FontIcon.of(MaterialDesignC.CUBE_OUTLINE, 20));

        VBox titleBox = new VBox(3);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("model-management-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("model-management-subtitle");
        subtitleLabel.setWrapText(true);
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        titleRow.getChildren().addAll(iconPane, titleBox);
        header.getChildren().add(titleRow);

        Label listLabel = new Label("Доступные ID моделей");
        listLabel.getStyleClass().add("model-management-section-label");

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("model-search-box");

        searchField = new TextField();
        searchField.setPromptText("Поиск по ID модели");
        searchField.getStyleClass().addAll("model-add-field", "model-search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, oldValue, newValue) ->
                refreshVisibleModels(modelListView == null ? currentModel : modelListView.getSelectionModel().getSelectedItem()));

        Button clearSearchButton = new Button("Сбросить");
        clearSearchButton.getStyleClass().add("model-search-clear-btn");
        clearSearchButton.setGraphic(FontIcon.of(MaterialDesignC.CLOSE, 14));
        clearSearchButton.setOnAction(e -> searchField.clear());

        searchBox.getChildren().addAll(searchField, clearSearchButton);

        modelListView = new ListView<>(visibleModels);
        modelListView.getStyleClass().add("model-list-view");
        modelListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        modelListView.setPrefHeight(250);
        modelListView.setCellFactory(list -> new ModelCell());
        modelListView.setPlaceholder(createEmptySearchState());

        Label addLabel = new Label("Добавить свой ID");
        addLabel.getStyleClass().add("model-management-section-label");

        HBox addBox = new HBox(10);
        addBox.setAlignment(Pos.CENTER_LEFT);

        customModelField = new TextField();
        customModelField.setPromptText("Например: openai/gpt-5.4");
        customModelField.getStyleClass().add("model-add-field");
        HBox.setHgrow(customModelField, Priority.ALWAYS);
        customModelField.setOnAction(e -> addCustomModel());

        Button addButton = new Button("Добавить");
        addButton.getStyleClass().add("model-add-btn");
        addButton.setGraphic(FontIcon.of(MaterialDesignP.PLUS, 14));
        addButton.setOnAction(e -> addCustomModel());

        Button removeButton = new Button("Удалить своё");
        removeButton.getStyleClass().add("model-delete-btn");
        removeButton.setGraphic(FontIcon.of(MaterialDesignT.TRASH_CAN_OUTLINE, 14));
        removeButton.disableProperty().bind(modelListView.getSelectionModel().selectedItemProperty().isNull());
        removeButton.setOnAction(e -> removeSelectedCustomModel());

        addBox.getChildren().addAll(customModelField, addButton, removeButton);

        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.getStyleClass().add("model-status-box");
        FontIcon statusIcon = FontIcon.of(MaterialDesignI.INFORMATION_OUTLINE, 14);
        statusIcon.getStyleClass().add("model-status-icon-neutral");
        statusLabel = new Label("Список объединяет ваши модели, найденные через API и базовые варианты.");
        statusLabel.getStyleClass().add("model-status-text");
        statusLabel.setWrapText(true);
        statusBox.getChildren().addAll(statusIcon, statusLabel);

        HBox noteBox = new HBox(8);
        noteBox.setAlignment(Pos.CENTER_LEFT);
        noteBox.getStyleClass().add("model-management-note-box");
        FontIcon noteIcon = FontIcon.of(MaterialDesignI.INFORMATION_OUTLINE, 16);
        noteIcon.getStyleClass().add("model-management-note-icon");
        Label noteLabel = new Label(noteText);
        noteLabel.getStyleClass().add("model-management-note-text");
        noteLabel.setWrapText(true);
        noteBox.getChildren().addAll(noteIcon, noteLabel);

        content.getChildren().addAll(
                header,
                listLabel,
                searchBox,
                modelListView,
                addLabel,
                addBox,
                statusBox,
                noteBox
        );

        dialogPane.setContent(content);

        ButtonType applyButtonType = new ButtonType("Выбрать", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().setAll(applyButtonType, cancelButtonType);

        Button applyButton = (Button) dialogPane.lookupButton(applyButtonType);
        applyButton.getStyleClass().add("model-apply-btn");
        applyButton.disableProperty().bind(modelListView.getSelectionModel().selectedItemProperty().isNull());

        Button cancelButton = (Button) dialogPane.lookupButton(cancelButtonType);
        cancelButton.getStyleClass().add("model-cancel-btn");

        dialog.setResultConverter(buttonType -> {
            if (buttonType == applyButtonType) {
                String selectedModel = modelListView.getSelectionModel().getSelectedItem();
                return new Result(
                        AiConfigDefaults.normalizeExternalModelId(selectedModel),
                        List.copyOf(customModels));
            }
            return null;
        });
    }

    private void addCustomModel() {
        String modelId = AiConfigDefaults.normalizeExternalModelId(customModelField.getText());
        if (modelId.isBlank()) {
            updateStatus("Введите ID модели.");
            return;
        }
        if (!customModels.contains(modelId)) {
            customModels.add(0, modelId);
        }
        customModelField.clear();
        refreshVisibleModels(modelId);
        updateStatus("Модель добавлена в пользовательский список.");
    }

    private void removeSelectedCustomModel() {
        String selected = modelListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBlank()) {
            updateStatus("Сначала выберите модель.");
            return;
        }
        if (!customModels.remove(selected)) {
            updateStatus("Можно удалять только свои модели.");
            return;
        }
        String fallbackSelection = currentModel.equals(selected) ? "" : currentModel;
        refreshVisibleModels(fallbackSelection);
        updateStatus("Пользовательская модель удалена.");
    }

    private void refreshVisibleModels(String preferredSelection) {
        visibleModels.setAll(filterModelOptions(mergeModelOptions()));
        String normalizedSelection = AiConfigDefaults.normalizeExternalModelId(preferredSelection);
        if (!normalizedSelection.isBlank() && visibleModels.contains(normalizedSelection)) {
            modelListView.getSelectionModel().select(normalizedSelection);
        } else if (!currentModel.isBlank() && visibleModels.contains(currentModel)) {
            modelListView.getSelectionModel().select(currentModel);
        } else if (!visibleModels.isEmpty()) {
            modelListView.getSelectionModel().selectFirst();
        }
    }

    private List<String> filterModelOptions(List<String> sourceModels) {
        if (sourceModels == null || sourceModels.isEmpty()) {
            return List.of();
        }
        String query = searchField == null ? "" : searchField.getText();
        if (query == null || query.isBlank()) {
            return sourceModels;
        }
        String normalizedQuery = query.trim().toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String model : sourceModels) {
            if (model != null && model.toLowerCase().contains(normalizedQuery)) {
                filtered.add(model);
            }
        }
        return List.copyOf(filtered);
    }

    private Label createEmptySearchState() {
        Label placeholder = new Label("Ничего не найдено. Попробуйте другой ID или добавьте модель вручную.");
        placeholder.getStyleClass().add("model-list-empty-state");
        placeholder.setWrapText(true);
        return placeholder;
    }

    private List<String> mergeModelOptions() {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        addNonBlank(merged, currentModel);
        addAllNonBlank(merged, customModels);
        addAllNonBlank(merged, providerModels);
        addAllNonBlank(merged, baseModels);
        return List.copyOf(new ArrayList<>(merged));
    }

    private void dedupeCustomModels() {
        List<String> normalized = new ArrayList<>();
        for (String model : customModels) {
            String value = AiConfigDefaults.normalizeExternalModelId(model);
            if (!value.isBlank() && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        customModels.setAll(normalized);
    }

    private void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    private void addAllNonBlank(java.util.LinkedHashSet<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addNonBlank(target, value);
        }
    }

    private void addNonBlank(java.util.LinkedHashSet<String> target, String value) {
        String normalized = AiConfigDefaults.normalizeExternalModelId(value);
        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }

    private final class ModelCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || item.isBlank()) {
                setText(null);
                setGraphic(null);
                return;
            }

            HBox root = new HBox(10);
            root.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(item);
            nameLabel.getStyleClass().add("model-name");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            List<Label> badges = new ArrayList<>();
            if (item.equals(currentModel)) {
                badges.add(createBadge("Активная", "model-badge-active"));
            }
            if (customModels.contains(item)) {
                badges.add(createBadge("Своя", "model-badge-custom"));
            } else if (providerModels.contains(item)) {
                badges.add(createBadge("API", "model-badge-provider"));
            } else {
                badges.add(createBadge(defaultBadgeText, "model-badge-default"));
            }
            if (providerMultimodalModels.contains(item)) {
                badges.add(createBadge("Мультимодальная", "model-badge-multimodal"));
            }
            if (providerAudioInputModels.contains(item)) {
                badges.add(createBadge("Аудио", "model-badge-audio-input"));
            }
            if (providerFileInputModels.contains(item)) {
                badges.add(createBadge("Файлы", "model-badge-file-input"));
            }

            root.getChildren().add(nameLabel);
            root.getChildren().add(spacer);
            root.getChildren().addAll(badges);
            setGraphic(root);
        }

        private Label createBadge(String text, String styleClass) {
            Label label = new Label(text);
            label.getStyleClass().addAll("model-source-badge", styleClass);
            label.setTooltip(new Tooltip(text));
            return label;
        }
    }
}
