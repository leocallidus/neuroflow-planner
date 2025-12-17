package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

/**
 * Inline help view.
 */
public class HelpDialog implements InlineView {

    private final VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();

    private HelpDialog() {
        root = new VBox(0);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(400, 350);
        root.getStyleClass().add("help-dialog-root");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 10, 25));
        header.getStyleClass().add("help-header-panel");
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("help-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignH.HELP_CIRCLE, 22);
        icon.getStyleClass().add("help-header-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Справка");
        title.getStyleClass().add("help-title");
        Label subtitle = new Label("Руководство и информация");
        subtitle.getStyleClass().add("help-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        root.getChildren().add(header);

        // --- Tabs ---
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("help-tab-pane");
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        tabPane.getTabs().addAll(createAboutTab(), createGuideTab());

        root.getChildren().add(tabPane);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline() {
        return new HelpDialog();
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
        return "Справка";
    }

    private Tab createAboutTab() {
        Tab tab = new Tab("О программе");
        tab.setGraphic(FontIcon.of(MaterialDesignI.INFORMATION_OUTLINE, 16));
        tab.getStyleClass().add("help-tab");

        VBox content = new VBox(20);
        content.getStyleClass().add("help-tab-content");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(30));

        try {
            String logoPath = isDark 
                ? "/com/example/neuroflowplanner/images/logo_mocha.png"
                : "/com/example/neuroflowplanner/images/logo_latte.png";
            ImageView logo = new ImageView(new Image(getClass().getResourceAsStream(logoPath)));
            logo.setFitHeight(100);
            logo.setPreserveRatio(true);
            content.getChildren().add(logo);
        } catch (Exception ignored) {}

        VBox textBox = new VBox(10);
        textBox.setAlignment(Pos.CENTER);
        
        Label appName = new Label("НейроФлоу Планировщик");
        appName.getStyleClass().add("help-app-name");

        Label version = new Label("Версия 1.0");
        version.getStyleClass().add("help-version");

        Label description = new Label(
            "Интеллектуальный планировщик задач, созданный для повышения вашей продуктивности.\n" +
            "Использует алгоритмы ИИ для приоритизации и персональных рекомендаций."
        );
        description.getStyleClass().add("help-description");
        description.setWrapText(true);
        description.setMaxWidth(500);
        description.setAlignment(Pos.CENTER);
        
        textBox.getChildren().addAll(appName, version, description);

        Separator sep = new Separator();
        sep.setMaxWidth(400);
        sep.getStyleClass().add("help-separator");

        VBox infoBox = new VBox(12);
        infoBox.getStyleClass().add("help-info-box");
        infoBox.setMaxWidth(450);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        infoBox.getChildren().addAll(
            createInfoRow(MaterialDesignA.ACCOUNT, "Автор", "leocallidus"),
            createInfoRow(MaterialDesignG.GITHUB, "GitHub", "github.com/leocallidus")
        );

        content.getChildren().addAll(textBox, sep, infoBox);
        
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("help-scroll-pane");
        tab.setContent(scroll);
        return tab;
    }

    private Tab createGuideTab() {
        Tab tab = new Tab("Руководство");
        tab.setGraphic(FontIcon.of(MaterialDesignB.BOOK_OPEN_PAGE_VARIANT, 16));
        tab.getStyleClass().add("help-tab");

        VBox content = new VBox(15);
        content.getStyleClass().add("help-tab-content");
        content.setPadding(new Insets(20));

        content.getChildren().addAll(
            createSection(MaterialDesignR.ROCKET_LAUNCH, "Начало работы", 
                "Создавайте задачи, устанавливайте дедлайны и сложность. ИИ автоматически рассчитает приоритеты."),

            createSection(MaterialDesignP.PLUS_BOX, "Задачи и подзадачи", 
                "• Добавить задачу — создание новой задачи\n" +
                "• Подзадачи — разбивайте большие задачи на части\n" +
                "• Теги — категоризация через запятую\n" +
                "• Повторение — ежедневно, еженедельно, ежемесячно"),

            createSection(MaterialDesignV.VIEW_DASHBOARD, "Визуализация", 
                "• Канбан-доска — перетаскивание задач по статусам\n" +
                "• Диаграмма Ганта — временная шкала проектов\n" +
                "• Календарь — задачи по дням\n" +
                "• Тепловая карта — активность за полгода"),

            createSection(MaterialDesignC.CHART_BAR, "Аналитика", 
                "• Дашборд — сводка по всем задачам\n" +
                "• Статистика — графики выполнения\n" +
                "• Прогноз загруженности — нагрузка на 30 дней\n" +
                "• Временная статистика — анализ затрат времени"),

            createSection(MaterialDesignT.TIMER, "Время", 
                "• Помодоро — 25 мин работы / 5 мин отдых\n" +
                "• Трекер времени — учёт времени на задачи\n" +
                "• Рабочие часы — настройка расписания"),

            createSection(MaterialDesignR.ROBOT, "ИИ-функции", 
                "• Чат-бот — советы и помощь в планировании\n" +
                "• Умные напоминания — уведомления по приоритету\n" +
                "• Прогноз дедлайнов — риски срыва сроков\n" +
                "• Анализ настроения — отслеживание состояния\n" +
                "• Персональные инсайты — рекомендации по продуктивности"),

            createSection(MaterialDesignC.COG, "Настройки", 
                "• Тёмная/светлая тема — переключение в настройках\n" +
                "• Экспорт — PDF, Excel, CSV, Markdown, DOCX\n" +
                "• Архив — завершённые задачи сохраняются")
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("help-scroll-pane");
        tab.setContent(scroll);
        return tab;
    }

    private HBox createInfoRow(Enum<?> iconEnum, String label, String value) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("help-info-row");

        FontIcon icon = FontIcon.of((org.kordamp.ikonli.Ikon) iconEnum, 20);
        icon.getStyleClass().add("help-info-icon");

        Label lbl = new Label(label);
        lbl.getStyleClass().add("help-info-label");
        lbl.setMinWidth(80);

        Label val = new Label(value);
        val.getStyleClass().add("help-info-value");

        row.getChildren().addAll(icon, lbl, val);
        return row;
    }

    private VBox createSection(Enum<?> iconEnum, String title, String text) {
        VBox section = new VBox(8);
        section.getStyleClass().add("help-section");

        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon icon = FontIcon.of((org.kordamp.ikonli.Ikon) iconEnum, 18);
        icon.getStyleClass().add("help-section-icon");
        
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("help-section-title");
        
        titleBox.getChildren().addAll(icon, titleLbl);

        Label textLbl = new Label(text);
        textLbl.getStyleClass().add("help-section-text");
        textLbl.setWrapText(true);

        section.getChildren().addAll(titleBox, textLbl);
        return section;
    }
}
