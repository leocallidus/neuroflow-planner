package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.util.ConfigManager;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chatbot avatar icon (theme-aware).
 */
public final class ChatBotAvatar {

    private static final String LIGHT_PNG = "/com/example/neuroflowplanner/images/chatbot_latte.png";
    private static final String DARK_PNG = "/com/example/neuroflowplanner/images/chatbot_mocha.png";
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private ChatBotAvatar() {}

    public static Node create(double sizePx) {
        ImageView lightIcon = createFixed(false, sizePx);
        ImageView darkIcon = createFixed(true, sizePx);

        StackPane wrapper = new StackPane(lightIcon, darkIcon);
        wrapper.setMinSize(sizePx, sizePx);
        wrapper.setPrefSize(sizePx, sizePx);
        wrapper.setMaxSize(sizePx, sizePx);
        wrapper.setPickOnBounds(false);

        lightIcon.visibleProperty().bind(ConfigManager.darkThemeProperty().not());
        lightIcon.managedProperty().bind(ConfigManager.darkThemeProperty().not());
        darkIcon.visibleProperty().bind(ConfigManager.darkThemeProperty());
        darkIcon.managedProperty().bind(ConfigManager.darkThemeProperty());
        return wrapper;
    }

    public static Node create(boolean isDark, double sizePx) {
        return createFixed(isDark, sizePx);
    }

    private static ImageView createFixed(boolean isDark, double sizePx) {
        String resourcePath = isDark ? DARK_PNG : LIGHT_PNG;
        Image image = CACHE.computeIfAbsent(resourcePath, path ->
            new Image(ChatBotAvatar.class.getResource(path).toExternalForm(), true)
        );
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(sizePx);
        imageView.setFitHeight(sizePx);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setManaged(false);
        imageView.setMouseTransparent(true);
        return imageView;
    }
}
