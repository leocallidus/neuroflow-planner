package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.PersonalInsightsService;
import com.example.neuroflowplanner.service.PersonalInsightsService.*;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.util.List;

public class PersonalInsightsDialog implements InlineView {

    private final VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private final PersonalInsightsService service = new PersonalInsightsService();

    private PersonalInsightsDialog(List<Task> tasks) {
        root = new VBox(0);
        root.getStyleClass().add("insights-root");

        HBox header = createHeader();
        root.getChildren().add(header);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("insights-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 25, 25, 25));

        Stats stats = service.calculateStats(tasks);
        HBox statsCards = createStatsCards(stats);
        content.getChildren().add(statsCards);

        List<Insight> insights = service.generateInsights(tasks);
        VBox insightsSection = createInsightsSection(insights);
        content.getChildren().add(insightsSection);

        scrollPane.setContent(content);
        root.getChildren().add(scrollPane);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(400, 350);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    private HBox createHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 15, 25));
        header.getStyleClass().add("insights-header-panel");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("insights-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignL.LIGHTBULB_ON, 24);
        icon.getStyleClass().add("insights-header-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Персональные инсайты");
        title.getStyleClass().add("insights-title");
        Label subtitle = new Label("Аналитика вашей личной эффективности");
        subtitle.getStyleClass().add("insights-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        return header;
    }

    private HBox createStatsCards(Stats stats) {
        HBox cards = new HBox(12);
        cards.setAlignment(Pos.CENTER);

        cards.getChildren().addAll(
            createMetricCard("Выполнено", String.format("%.0f%%", stats.completionRate()), MaterialDesignC.CHECK_CIRCLE, "insights-card-done"),
            createMetricCard("Всего задач", String.valueOf(stats.totalTasks()), MaterialDesignF.FORMAT_LIST_BULLETED, "insights-card-total"),
            createMetricCard("Время", String.format("%dч %dм", stats.totalMinutes() / 60, stats.totalMinutes() % 60), MaterialDesignC.CLOCK_OUTLINE, "insights-card-time"),
            createMetricCard("Сложность", String.format("%.1f", stats.avgComplexity()), MaterialDesignC.CHART_BAR, "insights-card-complexity")
        );

        return cards;
    }

    private VBox createMetricCard(String label, String value, Enum<?> iconEnum, String styleClass) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12));
        card.setPrefWidth(150);
        card.getStyleClass().addAll("insights-metric-card", styleClass);

        FontIcon icon = FontIcon.of((org.kordamp.ikonli.Ikon) iconEnum, 22);
        icon.getStyleClass().add("insights-card-icon");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("insights-card-value");

        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("insights-card-label");

        card.getChildren().addAll(icon, valueLabel, nameLabel);
        return card;
    }

    private VBox createInsightsSection(List<Insight> insights) {
        VBox section = new VBox(12);
        section.getStyleClass().add("insights-section");
        section.setPadding(new Insets(15));

        Label sectionTitle = new Label("Ваши инсайты");
        sectionTitle.getStyleClass().add("insights-section-title");
        section.getChildren().add(sectionTitle);

        if (insights.isEmpty()) {
            Label empty = new Label("Добавьте больше задач для получения инсайтов");
            empty.getStyleClass().add("insights-empty");
            section.getChildren().add(empty);
        } else {
            for (Insight insight : insights) {
                HBox card = createInsightCard(insight);
                section.getChildren().add(card);
            }
        }

        return section;
    }

    private HBox createInsightCard(Insight insight) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 15, 12, 15));
        
        String typeClass = switch (insight.type()) {
            case POSITIVE -> "insight-positive";
            case WARNING -> "insight-warning";
            case INFO -> "insight-info";
            case TIP -> "insight-tip";
        };
        card.getStyleClass().addAll("insight-card", typeClass);

        Label emoji = new Label(insight.icon());
        emoji.setStyle("-fx-font-size: 24px;");
        emoji.setMinWidth(36);

        VBox textBox = new VBox(2);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label titleLabel = new Label(insight.title());
        titleLabel.getStyleClass().add("insight-title");

        Label descLabel = new Label(insight.description());
        descLabel.getStyleClass().add("insight-description");
        descLabel.setWrapText(true);

        textBox.getChildren().addAll(titleLabel, descLabel);
        card.getChildren().addAll(emoji, textBox);

        return card;
    }

    public static InlineView inline(List<Task> tasks) {
        return new PersonalInsightsDialog(tasks);
    }

    @Override
    public Node getContent() { return root; }

    @Override
    public Runnable getOnClose() { return null; }

    @Override
    public void setCloseAction(Runnable closeAction) { this.closeAction = closeAction; }

    @Override
    public String getTitle() { return "Персональные инсайты"; }
}
