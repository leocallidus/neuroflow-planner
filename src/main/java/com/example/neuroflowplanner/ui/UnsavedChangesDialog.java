package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

/**
 * Styled dialog for confirming unsaved changes.
 */
public class UnsavedChangesDialog {

    /**
     * Shows a confirmation dialog for unsaved changes.
     * @return true if user wants to discard changes, false to stay
     */
    public static boolean showAndWait() {
        boolean isDark = ConfigManager.isDarkTheme();
        
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Несохранённые изменения");
        dialog.setHeaderText(null);
        
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(UnsavedChangesDialog.class.getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            pane.getStylesheets().add(UnsavedChangesDialog.class.getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("unsaved-dialog");
        pane.setPrefWidth(420);
        pane.setMinHeight(250);
        
        VBox content = new VBox(18);
        content.setPadding(new Insets(25));
        content.setAlignment(Pos.CENTER);
        
        // Warning icon
        StackPane iconPane = new StackPane();
        iconPane.setMinSize(60, 60);
        iconPane.setMaxSize(60, 60);
        iconPane.setStyle("-fx-background-color: " + (isDark ? "rgba(249,226,175,0.15)" : "rgba(223,142,29,0.1)") + "; -fx-background-radius: 50%;");
        FontIcon icon = FontIcon.of(MaterialDesignA.ALERT_OUTLINE, 32);
        icon.setIconColor(javafx.scene.paint.Color.web(isDark ? "#f9e2af" : "#df8e1d"));
        iconPane.getChildren().add(icon);
        
        // Title
        Label titleLabel = new Label("Есть несохранённые изменения");
        titleLabel.getStyleClass().add("unsaved-title");
        
        // Message
        Label msgLabel = new Label("Вы уверены, что хотите закрыть?\nВсе введённые данные будут потеряны.");
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(350);
        msgLabel.setAlignment(Pos.CENTER);
        msgLabel.setStyle("-fx-text-alignment: center;");
        msgLabel.getStyleClass().add("unsaved-message");
        
        content.getChildren().addAll(iconPane, titleLabel, msgLabel);
        pane.setContent(content);
        
        // Buttons
        ButtonType discardBtn = new ButtonType("Закрыть", ButtonBar.ButtonData.OK_DONE);
        ButtonType stayBtn = new ButtonType("Остаться", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(discardBtn, stayBtn);
        
        // Style buttons
        Button discard = (Button) pane.lookupButton(discardBtn);
        discard.getStyleClass().add("unsaved-discard-btn");
        discard.setMinWidth(100);
        
        Button stay = (Button) pane.lookupButton(stayBtn);
        stay.getStyleClass().add("unsaved-stay-btn");
        stay.setMinWidth(100);
        
        return dialog.showAndWait().orElse(stayBtn) == discardBtn;
    }
}
