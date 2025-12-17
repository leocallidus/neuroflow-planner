package com.example.neuroflowplanner;

import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.ui.MainView;
import com.example.neuroflowplanner.ui.SettingsDialog;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class NeuroFlowApp extends Application {

    static {
        // Отключение системного DPI-масштабирования
        System.setProperty("prism.allowHiDPIScaling", "false");
        System.setProperty("glass.win.uiScale", "100%");
        System.setProperty("glass.gtk.uiScale", "100%");
    }

    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView();
        Scene scene = new Scene(mainView, 1678, 748);
        
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        
        // Load saved theme
        boolean isDark = ConfigManager.isDarkTheme();
        if (isDark) {
            scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        
        // Set application icon
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/com/example/neuroflowplanner/images/favicon.png")));
        
        // Register scene for theme switching
        SettingsDialog.setMainScene(scene);
        SettingsDialog.setDarkThemeState(isDark);
        
        // Handle window close request - check for unsaved changes
        stage.setOnCloseRequest(event -> {
            if (!mainView.canCloseApplication()) {
                event.consume(); // Prevent closing
            }
        });
        
        stage.setTitle("НейроФлоу Планировщик — ИИ-Планировщик задач");
        stage.setScene(scene);
        stage.setMinWidth(950);
        stage.setMinHeight(650);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
