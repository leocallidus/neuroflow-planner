package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.service.AISchedulingEngine;
import com.example.neuroflowplanner.service.AutoPrioritizationService;
import com.example.neuroflowplanner.service.TimePredictionService;
import com.example.neuroflowplanner.service.SmartRecommendationsService;
import com.example.neuroflowplanner.service.ProductivityAnalysisService;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.application.Platform;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import javafx.stage.FileChooser;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.io.font.PdfEncodings;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

public class MainView extends BorderPane {

    private final ObservableList<Task> tasks = FXCollections.observableArrayList();
    private final TreeTableView<Task> taskTable = new TreeTableView<>();
    private final TreeItem<Task> rootItem = new TreeItem<>();
    private WebView aiInsightWebView;
    private String currentInsightText = "";
    private final Label detailTitle = new Label("Выберите задачу");
    private WebView descriptionWebView;
    private final Label detailDeadline = new Label("-");
    private final Label detailComplexity = new Label("-");
    private final Label detailPriority = new Label("-");
    private final Label detailTags = new Label("-");
    private final Label detailRecurrence = new Label("-");
    private final Label detailDependsOn = new Label("-");
    private final Label detailStartDate = new Label("-");
    private final AISchedulingEngine aiEngine = new AISchedulingEngine();
    private final AutoPrioritizationService autoPrioritizer = new AutoPrioritizationService();
    private final TimePredictionService timePrediction = new TimePredictionService();
    private final SmartRecommendationsService recommendations = new SmartRecommendationsService();
    private final ProductivityAnalysisService productivityAnalysis = new ProductivityAnalysisService();
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final StackPane overlayHost = new StackPane();
    private final StackPane overlayContentHolder = new StackPane();
    private final StackPane overlayScrim = new StackPane();
    private final VBox overlayContainer = new VBox();
    private final Label overlayTitle = new Label();
    private Runnable overlayOnClose;
    private InlineView currentInlineView;
    private Node previousFocusOwner;
    private EventHandler<KeyEvent> overlayEscapeHandler;

    private String currentDescriptionText = "";

    public MainView() {
        setLeft(createSidebar());
        setCenter(createCenterPanel());
        setRight(createRightPanel());
        loadTasks();
        
        // Регистрируем callback для обновления WebView при смене темы
        SettingsDialog.setThemeChangeCallback(this::refreshWebViewsTheme);
        
        // Show welcome dialog on first launch
        Platform.runLater(WelcomeDialog::showIfFirstLaunch);
    }

    /** Обновляет тему WebView при смене темы приложения */
    private void refreshWebViewsTheme() {
        // Перезагружаем контент WebView с новыми цветами темы
        if (aiInsightWebView != null) {
            String html = convertMarkdownToHtml(currentInsightText);
            String fullHtml = getHtmlTemplate(html);
            aiInsightWebView.getEngine().loadContent(fullHtml);
        }
        if (descriptionWebView != null) {
            String html = convertMarkdownToHtml(currentDescriptionText);
            String fullHtml = getDescriptionHtmlTemplate(html);
            descriptionWebView.getEngine().loadContent(fullHtml);
        }
    }

    private ScrollPane createSidebar() {
        VBox sidebarContent = new VBox(0);
        sidebarContent.getStyleClass().add("sidebar-content");
        sidebarContent.setPadding(new Insets(20));

        // --- Header ---
        VBox headerBox = new VBox(10);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(0, 0, 20, 0));
        
        try {
            String logoPath = ConfigManager.isDarkTheme() 
                ? "/com/example/neuroflowplanner/images/logo_mocha.png"
                : "/com/example/neuroflowplanner/images/logo_latte.png";
            ImageView logoImg = new ImageView(new Image(getClass().getResourceAsStream(logoPath)));
            logoImg.setFitHeight(100);
            logoImg.setPreserveRatio(true);
            headerBox.getChildren().add(logoImg);
        } catch (Exception ignored) {}
        
        Label appName = new Label("НейроФлоу");
        appName.getStyleClass().add("logo");
        Label subtitle = new Label("ИИ-Планировщик");
        subtitle.getStyleClass().add("subtitle");
        headerBox.getChildren().addAll(appName, subtitle);

        // --- Sections ---
        
        // 1. MAIN
        VBox mainSection = createSidebarSection("ГЛАВНОЕ");
        Button taskPanelBtn = createSidebarButton("Панель задач", MaterialDesignH.HOME, "sidebar-btn-primary", "Вернуться к списку задач");
        taskPanelBtn.setOnAction(e -> { closeInline(); refreshTree(); });
        Button addBtn = createSidebarButton("Добавить задачу", MaterialDesignP.PLUS_CIRCLE_OUTLINE, "sidebar-btn-success", "Создание новой задачи");
        addBtn.setOnAction(e -> handleAddTask(null));
        Button addSubBtn = createSidebarButton("Добавить подзадачу", MaterialDesignP.PLUS_BOX_OUTLINE, "sidebar-btn", "Создание подзадачи для выбранной");
        addSubBtn.setOnAction(e -> handleAddSubtask());
        Button dashboardBtn = createSidebarButton("Дашборд", MaterialDesignV.VIEW_DASHBOARD, "sidebar-btn", "Обзор ключевых показателей");
        dashboardBtn.setOnAction(e -> { InlineView view = DashboardDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button allBtn = createSidebarButton("Все задачи", MaterialDesignV.VIEW_LIST_OUTLINE, "sidebar-btn", "Полный список всех задач");
        allBtn.setOnAction(e -> { closeInline(); refreshTree(); });
        Button scheduledBtn = createSidebarButton("В планах", MaterialDesignC.CALENDAR_CLOCK, "sidebar-btn", "Задачи с будущей датой старта");
        scheduledBtn.setOnAction(e -> filterScheduled());
        Button calendarBtn = createSidebarButton("Календарь", MaterialDesignC.CALENDAR_MONTH, "sidebar-btn", "Календарный вид задач");
        calendarBtn.setOnAction(e -> { InlineView view = CalendarDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button kanbanBtn = createSidebarButton("Канбан-доска", MaterialDesignV.VIEW_COLUMN, "sidebar-btn", "Доска с колонками по статусам");
        kanbanBtn.setOnAction(e -> { InlineView view = KanbanDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button ganttBtn = createSidebarButton("Диаграмма Ганта", MaterialDesignC.CHART_GANTT, "sidebar-btn", "Временная шкала задач");
        ganttBtn.setOnAction(e -> { InlineView view = GanttChartDialog.inline(tasks); showInline(view, view.getTitle()); });
        
        mainSection.getChildren().addAll(taskPanelBtn, addBtn, addSubBtn, dashboardBtn, allBtn, scheduledBtn, calendarBtn, kanbanBtn, ganttBtn);

        // 2. ANALYSIS
        VBox analysisSection = createSidebarSection("АНАЛИТИКА");
        Button statsBtn = createSidebarButton("Статистика", MaterialDesignC.CHART_BAR, "sidebar-btn", "Общая статистика продуктивности");
        statsBtn.setOnAction(e -> { InlineView view = StatisticsDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button timeStatsBtn = createSidebarButton("Оценка времени", MaterialDesignC.CLOCK_OUTLINE, "sidebar-btn", "Анализ затрат времени");
        timeStatsBtn.setOnAction(e -> { InlineView view = TimeStatsDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button workloadBtn = createSidebarButton("Загруженность", MaterialDesignC.CHART_LINE, "sidebar-btn", "Прогноз нагрузки на месяц");
        workloadBtn.setOnAction(e -> { InlineView view = WorkloadDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button heatmapBtn = createSidebarButton("Тепловая карта", MaterialDesignG.GRID, "sidebar-btn", "История активности по дням");
        heatmapBtn.setOnAction(e -> { InlineView view = HeatmapDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button projectsBtn = createSidebarButton("Прогресс проектов", MaterialDesignP.PROGRESS_CHECK, "sidebar-btn", "Статус задач с подзадачами");
        projectsBtn.setOnAction(e -> { InlineView view = ProjectProgressDialog.inline(tasks); showInline(view, view.getTitle()); });
        
        analysisSection.getChildren().addAll(statsBtn, timeStatsBtn, workloadBtn, heatmapBtn, projectsBtn);

        // 3. TOOLS
        VBox toolsSection = createSidebarSection("ИНСТРУМЕНТЫ");
        Button pomodoroBtn = createSidebarButton("Помодоро", MaterialDesignT.TIMER_OUTLINE, "sidebar-btn", "Таймер для фокусировки");
        pomodoroBtn.setOnAction(e -> { InlineView view = PomodoroDialog.inline(); showInline(view, view.getTitle()); });
        Button trackerBtn = createSidebarButton("Трекинг времени", MaterialDesignT.TIMER_SAND, "sidebar-btn", "Учет времени работы над задачами");
        trackerBtn.setOnAction(e -> { InlineView view = TimeTrackerDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button workHoursBtn = createSidebarButton("Рабочие часы", MaterialDesignC.CLOCK_TIME_EIGHT_OUTLINE, "sidebar-btn", "Настройка рабочего времени");
        workHoursBtn.setOnAction(e -> { InlineView view = WorkHoursDialog.inline(); showInline(view, view.getTitle()); });
        Button fromTemplateBtn = createSidebarButton("Из шаблона", MaterialDesignF.FILE_DOCUMENT_OUTLINE, "sidebar-btn", "Создание задачи из шаблона");
        fromTemplateBtn.setOnAction(e -> handleCreateFromTemplate());
        Button saveTemplateBtn = createSidebarButton("Сохранить шаблон", MaterialDesignC.CONTENT_SAVE_OUTLINE, "sidebar-btn", "Сохранить задачу как шаблон");
        saveTemplateBtn.setOnAction(e -> handleSaveAsTemplate());
        
        toolsSection.getChildren().addAll(pomodoroBtn, trackerBtn, workHoursBtn, fromTemplateBtn, saveTemplateBtn);

        // 4. INTELLIGENCE (AI)
        VBox aiSection = createSidebarSection("ИИ-ФУНКЦИИ");
        Button chatBtn = createSidebarButton("ИИ-Ассистент", MaterialDesignC.CHAT, "sidebar-btn-primary", "Чат с умным помощником");
        chatBtn.setOnAction(e -> { InlineView view = ChatBotDialog.inline(); showInline(view, view.getTitle()); });
        Button remindersBtn = createSidebarButton("Напоминания", MaterialDesignB.BELL_RING_OUTLINE, "sidebar-btn", "Умные уведомления о дедлайнах");
        remindersBtn.setOnAction(e -> { InlineView view = SmartRemindersDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button analyzeBtn = createSidebarButton("Центр Анализа", MaterialDesignB.BRAIN, "sidebar-btn", "ИИ-центр анализа задач");
        analyzeBtn.setOnAction(e -> { InlineView view = AIAnalysisDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button autoPriorityBtn = createSidebarButton("Авто-приоритет", MaterialDesignP.PRIORITY_HIGH, "sidebar-btn", "Автоматическая расстановка приоритетов");
        autoPriorityBtn.setOnAction(e -> handleAutoPrioritization());
        Button autoScheduleBtn = createSidebarButton("Авто-планирование", MaterialDesignC.CALENDAR_SYNC, "sidebar-btn", "ИИ составляет расписание");
        autoScheduleBtn.setOnAction(e -> handleAutoSchedule());
        Button categorizationBtn = createSidebarButton("Категоризация", MaterialDesignS.SHAPE_OUTLINE, "sidebar-btn", "Автоматическое распределение по категориям");
        categorizationBtn.setOnAction(e -> { InlineView view = SmartCategorizationDialog.inline(tasks); showInline(view, view.getTitle()); });
        
        aiSection.getChildren().addAll(chatBtn, remindersBtn, analyzeBtn, autoPriorityBtn, autoScheduleBtn, categorizationBtn);

        // 5. MANAGE (Bulk & Actions)
        VBox manageSection = createSidebarSection("УПРАВЛЕНИЕ");
        Button urgentBtn = createSidebarButton("Срочные", MaterialDesignA.ALERT_CIRCLE_OUTLINE, "sidebar-btn-danger", "Фильтр задач с высоким приоритетом");
        urgentBtn.setOnAction(e -> filterUrgent());
        Button tagFilterBtn = createSidebarButton("По тегу...", MaterialDesignT.TAG_OUTLINE, "sidebar-btn", "Фильтр задач по тегу");
        tagFilterBtn.setOnAction(e -> filterByTag());
        Button archiveBtn = createSidebarButton("В архив", MaterialDesignA.ARCHIVE_OUTLINE, "sidebar-btn", "Перемещение задачи в архив");
        archiveBtn.setOnAction(e -> handleArchiveTask());
        Button showArchiveBtn = createSidebarButton("Показать архив", MaterialDesignA.ARCHIVE, "sidebar-btn", "Просмотр архивированных задач");
        showArchiveBtn.setOnAction(e -> showArchivedTasks());
        
        VBox bulkActionsSection = createSidebarSection("МАССОВЫЕ");
        Button bulkArchiveBtn = createSidebarButton("Архивировать", MaterialDesignA.ARCHIVE_ARROW_DOWN, "sidebar-btn", "Массовая архивация");
        bulkArchiveBtn.setOnAction(e -> bulkArchive());
        Button bulkDeleteBtn = createSidebarButton("Удалить", MaterialDesignD.DELETE_SWEEP, "sidebar-btn-danger", "Массовое удаление");
        bulkDeleteBtn.setOnAction(e -> bulkDelete());
        Button bulkTagBtn = createSidebarButton("Добавить тег", MaterialDesignT.TAG_PLUS, "sidebar-btn", "Массовое добавление тега");
        bulkTagBtn.setOnAction(e -> bulkAddTag());
        
        bulkActionsSection.getChildren().addAll(bulkArchiveBtn, bulkDeleteBtn, bulkTagBtn);

        Button exportBtn = createSidebarButton("Экспорт", MaterialDesignF.FILE_EXPORT_OUTLINE, "sidebar-btn", "Сохранение данных в файл");
        exportBtn.setOnAction(e -> { InlineView view = ExportDialog.inline(tasks); showInline(view, view.getTitle()); });
        Button settingsBtn = createSidebarButton("Настройки", MaterialDesignC.COG_OUTLINE, "sidebar-btn", "Параметры приложения");
        settingsBtn.setOnAction(e -> { InlineView view = SettingsDialog.inline(); showInline(view, view.getTitle()); });
        Button helpBtn = createSidebarButton("Справка", MaterialDesignH.HELP_CIRCLE_OUTLINE, "sidebar-btn", "Руководство пользователя");
        helpBtn.setOnAction(e -> { InlineView view = HelpDialog.inline(); showInline(view, view.getTitle()); });

        manageSection.getChildren().addAll(urgentBtn, tagFilterBtn, archiveBtn, showArchiveBtn);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("1.0");
        footer.getStyleClass().add("sidebar-version");
        footer.setAlignment(Pos.CENTER);
        footer.setMaxWidth(Double.MAX_VALUE);

        sidebarContent.getChildren().addAll(headerBox, mainSection, aiSection, toolsSection, analysisSection, manageSection, bulkActionsSection, exportBtn, settingsBtn, helpBtn, spacer, footer);

        ScrollPane scrollPane = new ScrollPane(sidebarContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("sidebar-scroll");
        scrollPane.setMinWidth(260);
        scrollPane.setPrefWidth(260);
        
        return scrollPane;
    }

    private VBox createSidebarSection(String title) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(10, 0, 5, 0));
        Label label = new Label(title);
        label.getStyleClass().add("sidebar-section-label");
        section.getChildren().add(label);
        return section;
    }

    private Button createSidebarButton(String text, Enum<?> iconEnum, String styleClass, String tooltipText) {
        Button btn = new Button(text);
        FontIcon icon = FontIcon.of((org.kordamp.ikonli.Ikon) iconEnum, 18);
        btn.setGraphic(icon);
        btn.getStyleClass().addAll("sidebar-btn", styleClass);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        
        if (tooltipText != null && !tooltipText.isEmpty()) {
            Tooltip tooltip = new Tooltip(tooltipText);
            tooltip.getStyleClass().add("sidebar-tooltip");
            tooltip.setShowDelay(javafx.util.Duration.millis(300));
            btn.setTooltip(tooltip);
        }
        return btn;
    }

    private StackPane createCenterPanel() {
        VBox centerContent = new VBox(15);
        centerContent.getStyleClass().add("center-panel");

        Label header = new Label("📋 Панель задач");
        header.getStyleClass().add("panel-header");

        setupTaskTable();
        VBox.setVgrow(taskTable, Priority.ALWAYS);

        centerContent.getChildren().addAll(header, taskTable);

        StackPane centerStack = new StackPane(centerContent, createOverlayHost());
        centerStack.setAlignment(Pos.CENTER);
        return centerStack;
    }

    @SuppressWarnings("unchecked")
    private void setupTaskTable() {
        TreeTableColumn<Task, String> titleCol = new TreeTableColumn<>("Название");
        titleCol.setCellValueFactory(c -> c.getValue().getValue().titleProperty());
        titleCol.setPrefWidth(240);
        titleCol.setStyle("-fx-alignment: CENTER-LEFT;");
        titleCol.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    Task task = getTreeTableRow().getItem();
                    if (task != null) {
                        setText(item);
                        setAlignment(Pos.CENTER_LEFT);
                        setGraphicTextGap(6);
                        if (task.isCompleted()) {
                            FontIcon checkIcon = FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 14);
                            checkIcon.getStyleClass().add("task-icon-completed");
                            setGraphic(checkIcon);
                            setStyle("-fx-opacity: 0.7; -fx-text-fill: #40a02b;");
                            String tooltip = "Выполнено";
                            if (task.getCompletedDate() != null) {
                                tooltip += " " + task.getCompletedDate();
                            }
                            setTooltip(new Tooltip(tooltip));
                        } else if (!task.isStarted()) {
                            FontIcon clockIcon = FontIcon.of(MaterialDesignC.CLOCK_OUTLINE, 14);
                            clockIcon.getStyleClass().add("task-icon-scheduled");
                            setGraphic(clockIcon);
                            setStyle("-fx-opacity: 0.6; -fx-text-fill: #7f8c8d;");
                            setTooltip(new Tooltip("Запланировано на " + task.getStartDate()));
                        } else {
                            setGraphic(null);
                            setStyle("");
                            setTooltip(null);
                        }
                    } else {
                        setText(item);
                        setGraphic(null);
                        setStyle("");
                    }
                }
            }
        });

        TreeTableColumn<Task, LocalDate> deadlineCol = new TreeTableColumn<>("Дедлайн");
        deadlineCol.setCellValueFactory(c -> c.getValue().getValue().deadlineProperty());
        deadlineCol.setPrefWidth(110);

        TreeTableColumn<Task, Number> complexityCol = new TreeTableColumn<>("Сложность");
        complexityCol.setCellValueFactory(c -> c.getValue().getValue().complexityProperty());
        complexityCol.setPrefWidth(90);
        complexityCol.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label lbl = new Label(item + "/10");
                    lbl.setStyle("-fx-text-fill: #7f8c8d;");
                    setGraphic(lbl);
                }
            }
        });

        TreeTableColumn<Task, Number> priorityCol = new TreeTableColumn<>("ИИ-Приоритет");
        priorityCol.setCellValueFactory(c -> c.getValue().getValue().smartPriorityProperty());
        priorityCol.setPrefWidth(90);
        priorityCol.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(String.format("%.1f", item.doubleValue()));
                    double val = item.doubleValue();
                    if (val >= 7) badge.getStyleClass().add("priority-high");
                    else if (val >= 4) badge.getStyleClass().add("priority-medium");
                    else badge.getStyleClass().add("priority-low");
                    setGraphic(badge);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Колонка тегов/категорий
        TreeTableColumn<Task, String> tagsCol = new TreeTableColumn<>("Теги");
        tagsCol.setCellValueFactory(c -> c.getValue().getValue().tagsProperty());
        tagsCol.setPrefWidth(150);
        tagsCol.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox tagsBox = new HBox(4);
                    tagsBox.setAlignment(Pos.CENTER_LEFT);
                    String[] tagArray = item.split(",");
                    int shown = 0;
                    for (String tag : tagArray) {
                        String trimmed = tag.trim();
                        if (trimmed.isEmpty()) continue;
                        if (shown >= 2) {
                            Label more = new Label("+" + (tagArray.length - shown));
                            more.getStyleClass().add("tag-badge-more");
                            tagsBox.getChildren().add(more);
                            break;
                        }
                        Label tagLabel = new Label(trimmed);
                        tagLabel.getStyleClass().add("tag-badge");
                        // Цвет по категории
                        String lower = trimmed.toLowerCase();
                        if (lower.contains("работа") || lower.contains("проект")) {
                            tagLabel.getStyleClass().add("tag-work");
                        } else if (lower.contains("личн") || lower.contains("дом")) {
                            tagLabel.getStyleClass().add("tag-personal");
                        } else if (lower.contains("срочн") || lower.contains("важн")) {
                            tagLabel.getStyleClass().add("tag-urgent");
                        } else if (lower.contains("учёб") || lower.contains("учеб")) {
                            tagLabel.getStyleClass().add("tag-study");
                        } else if (lower.contains("финанс") || lower.contains("деньг")) {
                            tagLabel.getStyleClass().add("tag-finance");
                        } else if (lower.contains("идея") || lower.contains("план")) {
                            tagLabel.getStyleClass().add("tag-idea");
                        }
                        tagsBox.getChildren().add(tagLabel);
                        shown++;
                    }
                    setGraphic(tagsBox);
                    setText(null);
                    // Tooltip с полным списком тегов
                    if (tagArray.length > 2) {
                        setTooltip(new Tooltip(item));
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });

        TreeTableColumn<Task, Void> actionsCol = new TreeTableColumn<>("");
        actionsCol.setPrefWidth(130);
        actionsCol.setMinWidth(126);
        actionsCol.setMaxWidth(140);
        actionsCol.setResizable(false);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TreeTableCell<>() {
            private final HBox actionsBox = new HBox(4);
            private final Button completeBtn = new Button();
            private final Button editBtn = new Button();
            private final Button deleteBtn = new Button();
            {
                // Complete button
                completeBtn.getStyleClass().add("complete-btn");
                completeBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                completeBtn.setMinSize(32, 32);
                completeBtn.setMaxSize(36, 36);
                completeBtn.setOnAction(e -> {
                    TreeItem<Task> item = getTreeTableRow().getTreeItem();
                    if (item != null && item.getValue() != null) {
                        handleToggleComplete(item.getValue());
                    }
                });
                
                // Edit button
                FontIcon editIcon = FontIcon.of(MaterialDesignP.PENCIL_OUTLINE, 16);
                editIcon.setIconColor(javafx.scene.paint.Color.web("#3498db"));
                editBtn.setGraphic(editIcon);
                editBtn.getStyleClass().add("edit-btn");
                editBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                editBtn.setMinSize(32, 32);
                editBtn.setMaxSize(36, 36);
                editBtn.setTooltip(new Tooltip("Редактировать"));
                editBtn.setOnAction(e -> {
                    TreeItem<Task> item = getTreeTableRow().getTreeItem();
                    if (item != null && item.getValue() != null) {
                        handleEditTask(item.getValue());
                    }
                });
                
                // Delete button
                FontIcon icon = FontIcon.of(MaterialDesignD.DELETE_OUTLINE, 16);
                icon.setIconColor(javafx.scene.paint.Color.web("#e74c3c"));
                deleteBtn.setGraphic(icon);
                deleteBtn.getStyleClass().add("delete-btn");
                deleteBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                deleteBtn.setMinSize(32, 32);
                deleteBtn.setMaxSize(36, 36);
                deleteBtn.setTooltip(new Tooltip("Удалить"));
                deleteBtn.setOnAction(e -> {
                    TreeItem<Task> item = getTreeTableRow().getTreeItem();
                    if (item != null && item.getValue() != null) {
                        Task task = item.getValue();
                        showDeleteConfirmDialog(task);
                    }
                });
                
                actionsBox.setAlignment(Pos.CENTER);
                actionsBox.getChildren().addAll(completeBtn, editBtn, deleteBtn);
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                TreeItem<Task> treeItem = getTreeTableRow().getTreeItem();
                if (empty || treeItem == null || treeItem.getValue() == null) {
                    setGraphic(null);
                } else {
                    Task task = treeItem.getValue();
                    // Update complete button icon based on task state
                    FontIcon completeIcon;
                    if (task.isCompleted()) {
                        completeIcon = FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 16);
                        completeIcon.setIconColor(javafx.scene.paint.Color.web("#40a02b"));
                        completeBtn.setTooltip(new Tooltip("Отменить выполнение"));
                    } else {
                        completeIcon = FontIcon.of(MaterialDesignC.CHECKBOX_BLANK_CIRCLE_OUTLINE, 16);
                        completeIcon.setIconColor(javafx.scene.paint.Color.web("#7f8c8d"));
                        completeBtn.setTooltip(new Tooltip("Отметить выполненной"));
                    }
                    completeBtn.setGraphic(completeIcon);
                    setGraphic(actionsBox);
                }
                setAlignment(Pos.CENTER);
            }
        });

        taskTable.getColumns().addAll(titleCol, tagsCol, deadlineCol, complexityCol, priorityCol, actionsCol);
        rootItem.setExpanded(true);
        taskTable.setRoot(rootItem);
        taskTable.setShowRoot(false);
        taskTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        taskTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        taskTable.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item != null && item.getValue() != null) updateDetailPanel(item.getValue());
        });
    }

    private void filterScheduled() {
        rootItem.getChildren().clear();
        for (Task task : tasks) {
            if (!task.isArchived() && !task.isStarted()) {
                TreeItem<Task> item = new TreeItem<>(task);
                item.setExpanded(true);
                for (Task sub : task.getSubtasks()) {
                    // Show subtasks of scheduled tasks, or check subtask start date too?
                    // For now, show structure.
                    item.getChildren().add(new TreeItem<>(sub));
                }
                rootItem.getChildren().add(item);
            }
        }
    }

    private void removeTaskFromList(Task task) {
        tasks.remove(task);
        for (Task t : tasks) {
            t.getSubtasks().remove(task);
        }
    }
    
    private void showDeleteConfirmDialog(Task task) {
        boolean isDark = ConfigManager.isDarkTheme();
        
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Удаление задачи");
        dialog.setHeaderText(null);
        
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().add("styled-alert");
        dialogPane.setPrefWidth(420);
        
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);
        
        // Warning icon
        StackPane iconBox = new StackPane();
        iconBox.setMinSize(64, 64);
        iconBox.setMaxSize(64, 64);
        iconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(243,139,168,0.15)" : "rgba(210,15,57,0.1)") + "; -fx-background-radius: 50%;");
        FontIcon warningIcon = FontIcon.of(MaterialDesignD.DELETE_ALERT, 32);
        warningIcon.setIconColor(javafx.scene.paint.Color.web(isDark ? "#f38ba8" : "#d20f39"));
        iconBox.getChildren().add(warningIcon);
        
        // Title
        Label titleLbl = new Label("Удалить задачу?");
        titleLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        
        // Task name
        Label taskNameLbl = new Label("\"" + task.getTitle() + "\"");
        taskNameLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#f38ba8" : "#d20f39") + ";");
        taskNameLbl.setWrapText(true);
        taskNameLbl.setMaxWidth(350);
        taskNameLbl.setAlignment(Pos.CENTER);
        
        // Warning message
        VBox warningBox = new VBox(6);
        warningBox.setAlignment(Pos.CENTER);
        warningBox.setPadding(new Insets(12));
        warningBox.setStyle("-fx-background-color: " + (isDark ? "rgba(243,139,168,0.1)" : "rgba(210,15,57,0.05)") + "; -fx-background-radius: 10;");
        
        Label warningLbl = new Label("⚠️ Это действие нельзя отменить");
        warningLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#f9e2af" : "#df8e1d") + ";");
        
        int subtaskCount = task.getSubtasks().size();
        if (subtaskCount > 0) {
            Label subtaskWarning = new Label("Также будут удалены " + subtaskCount + " подзадач");
            subtaskWarning.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
            warningBox.getChildren().addAll(warningLbl, subtaskWarning);
        } else {
            warningBox.getChildren().add(warningLbl);
        }
        
        content.getChildren().addAll(iconBox, titleLbl, taskNameLbl, warningBox);
        dialogPane.setContent(content);
        
        // Buttons
        ButtonType deleteBtn = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(deleteBtn, cancelBtn);
        
        // Style delete button
        Button deleteButton = (Button) dialogPane.lookupButton(deleteBtn);
        deleteButton.setStyle("-fx-background-color: " + (isDark ? "#f38ba8" : "#d20f39") + "; " +
                             "-fx-text-fill: " + (isDark ? "#11111b" : "white") + "; " +
                             "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 24;");
        deleteButton.setOnMouseEntered(e -> deleteButton.setStyle("-fx-background-color: " + (isDark ? "#f5a0b5" : "#e8304a") + "; " +
                             "-fx-text-fill: " + (isDark ? "#11111b" : "white") + "; " +
                             "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 24;"));
        deleteButton.setOnMouseExited(e -> deleteButton.setStyle("-fx-background-color: " + (isDark ? "#f38ba8" : "#d20f39") + "; " +
                             "-fx-text-fill: " + (isDark ? "#11111b" : "white") + "; " +
                             "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 24;"));
        
        // Style cancel button
        Button cancelButton = (Button) dialogPane.lookupButton(cancelBtn);
        cancelButton.setStyle("-fx-background-color: " + (isDark ? "#45475a" : "#ccd0da") + "; " +
                             "-fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + "; " +
                             "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 24;");
        
        dialog.showAndWait().ifPresent(result -> {
            if (result == deleteBtn) {
                db.deleteTask(task.getId());
                removeTaskFromList(task);
                refreshTree();
            }
        });
    }

    private void refreshTree() {
        rootItem.getChildren().clear();
        for (Task task : tasks) {
            if (task.isArchived()) continue;
            TreeItem<Task> item = new TreeItem<>(task);
            item.setExpanded(true);
            for (Task sub : task.getSubtasks()) {
                if (!sub.isArchived()) item.getChildren().add(new TreeItem<>(sub));
            }
            rootItem.getChildren().add(item);
        }
        taskTable.refresh();
    }

    private Node createRightPanel() {
        // --- SplitPane для всех трёх секций ---
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mainSplitPane.getStyleClass().add("description-ai-splitpane");

        // --- Details Section (Детали задачи) ---
        VBox detailsSection = new VBox(10);
        detailsSection.getStyleClass().add("details-section");
        detailsSection.setPadding(new Insets(15));
        
        HBox detailsHeader = new HBox(8);
        detailsHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon infoIcon = FontIcon.of(MaterialDesignI.INFORMATION_OUTLINE, 18);
        infoIcon.getStyleClass().add("panel-header-icon");
        Label detailsTitle = new Label("Детали задачи");
        detailsTitle.getStyleClass().add("section-title-main");
        detailsHeader.getChildren().addAll(infoIcon, detailsTitle);
        
        detailTitle.getStyleClass().add("detail-title-large");
        detailTitle.setWrapText(true);
        
        VBox propertiesContent = new VBox(8);
        propertiesContent.getChildren().addAll(
            createDetailRow(MaterialDesignC.CALENDAR_CLOCK, "Дедлайн", detailDeadline),
            createDetailRow(MaterialDesignT.TIMER_SAND, "Сложность", detailComplexity),
            createDetailRow(MaterialDesignT.TARGET, "Приоритет", detailPriority),
            createDetailRow(MaterialDesignT.TAG_OUTLINE, "Теги", detailTags),
            createDetailRow(MaterialDesignR.REPEAT, "Повтор", detailRecurrence),
            createDetailRow(MaterialDesignL.LINK_VARIANT, "Зависит от", detailDependsOn),
            createDetailRow(MaterialDesignP.PLAY_CIRCLE_OUTLINE, "Старт", detailStartDate)
        );
        
        ScrollPane propertiesScroll = new ScrollPane(propertiesContent);
        propertiesScroll.setFitToWidth(true);
        propertiesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        propertiesScroll.getStyleClass().add("properties-scroll");
        VBox.setVgrow(propertiesScroll, Priority.ALWAYS);
        
        detailsSection.getChildren().addAll(detailsHeader, detailTitle, propertiesScroll);

        // --- Description Section ---
        VBox descriptionSection = new VBox(10);
        descriptionSection.getStyleClass().add("description-section");
        descriptionSection.setPadding(new Insets(10));
        
        HBox descHeader = new HBox(8);
        descHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon descIcon = FontIcon.of(MaterialDesignT.TEXT_SUBJECT, 16);
        descIcon.getStyleClass().add("section-icon");
        Label descTitle = new Label("Описание");
        descTitle.getStyleClass().add("section-title");
        descHeader.getChildren().addAll(descIcon, descTitle);
        
        descriptionWebView = new WebView();
        descriptionWebView.getStyleClass().add("description-webview");
        descriptionWebView.setMaxWidth(Double.MAX_VALUE);
        descriptionWebView.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(descriptionWebView, Priority.ALWAYS);
        setDescriptionContent("Нет описания");
        
        descriptionSection.getChildren().addAll(descHeader, descriptionWebView);

        // --- AI Insight Section ---
        VBox insightCard = new VBox(10);
        insightCard.getStyleClass().add("insight-card");
        insightCard.setPadding(new Insets(10));

        HBox insightHeader = new HBox(8);
        insightHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon aiIcon = FontIcon.of(MaterialDesignB.BRAIN, 18);
        aiIcon.getStyleClass().add("insight-icon");
        Label insightTitle = new Label("ИИ-Анализ");
        insightTitle.getStyleClass().add("insight-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Кнопка ИИ-анализ
        Button analyzeBtn = new Button();
        analyzeBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 14));
        analyzeBtn.getStyleClass().add("insight-action-btn");
        analyzeBtn.setTooltip(new Tooltip("Запустить ИИ-анализ"));
        analyzeBtn.setOnAction(e -> runAIAnalysisForSelected(analyzeBtn));
        
        // Кнопка копирования
        Button copyBtn = new Button();
        copyBtn.setGraphic(FontIcon.of(MaterialDesignC.CONTENT_COPY, 14));
        copyBtn.getStyleClass().add("insight-action-btn");
        copyBtn.setTooltip(new Tooltip("Копировать"));
        copyBtn.setOnAction(e -> copyInsightToClipboard(copyBtn));
        
        // Кнопка экспорта
        Button exportBtn = new Button();
        exportBtn.setGraphic(FontIcon.of(MaterialDesignE.EXPORT_VARIANT, 14));
        exportBtn.getStyleClass().add("insight-action-btn");
        exportBtn.setTooltip(new Tooltip("Экспорт"));
        exportBtn.setOnAction(e -> exportInsight());

        insightHeader.getChildren().addAll(aiIcon, insightTitle, spacer, analyzeBtn, copyBtn, exportBtn);

        // WebView для рендеринга Markdown
        aiInsightWebView = new WebView();
        aiInsightWebView.getStyleClass().add("insight-webview");
        aiInsightWebView.setMaxWidth(Double.MAX_VALUE);
        aiInsightWebView.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(aiInsightWebView, Priority.ALWAYS);
        setInsightContent("Выберите задачу для получения рекомендаций...");

        insightCard.getChildren().addAll(insightHeader, aiInsightWebView);

        // Добавляем все три секции в SplitPane
        mainSplitPane.getItems().addAll(detailsSection, descriptionSection, insightCard);
        mainSplitPane.setDividerPositions(0.30, 0.55);

        // Используем BorderPane для правильного растягивания
        BorderPane wrapper = new BorderPane();
        wrapper.setCenter(mainSplitPane);
        wrapper.getStyleClass().add("right-panel-scroll");
        wrapper.setMinWidth(300);
        wrapper.setPrefWidth(320);
        
        return wrapper;
    }

    private HBox createDetailRow(org.kordamp.ikonli.Ikon iconCode, String labelText, Label valueLabel) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("detail-row");

        FontIcon icon = FontIcon.of(iconCode, 16);
        icon.getStyleClass().add("detail-icon");

        VBox text = new VBox(2);
        Label label = new Label(labelText);
        label.getStyleClass().add("detail-label-small");
        
        valueLabel.getStyleClass().add("detail-value-text");
        valueLabel.setWrapText(true);
        
        text.getChildren().addAll(label, valueLabel);
        HBox.setHgrow(text, Priority.ALWAYS);

        row.getChildren().addAll(icon, text);
        return row;
    }

    private VBox createCollapsibleSection(String titleText, org.kordamp.ikonli.Ikon iconCode, Node content, boolean expandedByDefault) {
        VBox section = new VBox(0);
        section.getStyleClass().add("collapsible-section");
        
        // Header (clickable)
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getStyleClass().add("collapsible-header");
        headerBox.setPadding(new Insets(12, 15, 12, 15));
        headerBox.setCursor(javafx.scene.Cursor.HAND);
        
        FontIcon chevron = FontIcon.of(expandedByDefault ? MaterialDesignC.CHEVRON_DOWN : MaterialDesignC.CHEVRON_RIGHT, 16);
        chevron.getStyleClass().add("collapsible-chevron");
        
        FontIcon icon = FontIcon.of(iconCode, 16);
        icon.getStyleClass().add("collapsible-icon");
        
        Label titleLabel = new Label(titleText);
        titleLabel.getStyleClass().add("collapsible-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        headerBox.getChildren().addAll(chevron, icon, titleLabel, spacer);
        
        // Content wrapper
        VBox contentWrapper = new VBox(content);
        contentWrapper.getStyleClass().add("collapsible-content");
        contentWrapper.setPadding(new Insets(0, 15, 15, 15));
        contentWrapper.setVisible(expandedByDefault);
        contentWrapper.setManaged(expandedByDefault);
        
        // Toggle on click
        headerBox.setOnMouseClicked(e -> {
            boolean isExpanded = contentWrapper.isVisible();
            contentWrapper.setVisible(!isExpanded);
            contentWrapper.setManaged(!isExpanded);
            chevron.setIconCode(isExpanded ? MaterialDesignC.CHEVRON_RIGHT : MaterialDesignC.CHEVRON_DOWN);
        });
        
        section.getChildren().addAll(headerBox, contentWrapper);
        return section;
    }

    private void updateDetailPanel(Task task) {
        String titleSuffix = task.isSubtask() ? " (подзадача)" : "";
        if (task.isCompleted()) {
            titleSuffix += " ✓";
        }
        detailTitle.setText(task.getTitle() + titleSuffix);
        String desc = task.getDescription();
        setDescriptionContent(desc != null && !desc.isEmpty() ? desc : "Нет описания");
        
        // Дедлайн с информацией о выполнении
        if (task.isCompleted() && task.getCompletedDate() != null) {
            long daysBeforeDeadline = java.time.temporal.ChronoUnit.DAYS.between(task.getCompletedDate(), task.getDeadline());
            String completionInfo = daysBeforeDeadline >= 0 
                ? " (выполнено за " + daysBeforeDeadline + " дн. до срока)"
                : " (просрочено на " + Math.abs(daysBeforeDeadline) + " дн.)";
            detailDeadline.setText(task.getDeadline().toString() + completionInfo);
        } else {
            detailDeadline.setText(task.getDeadline().toString());
        }
        
        detailComplexity.setText(task.getComplexity() + "/10");
        detailPriority.setText(String.format("%.1f", task.getSmartPriority()));
        detailTags.setText(task.getTags().isEmpty() ? "-" : task.getTags());
        detailRecurrence.setText(switch (task.getRecurrence()) {
            case "daily" -> "Ежедневно";
            case "weekly" -> "Еженедельно";
            case "monthly" -> "Ежемесячно";
            case "yearly" -> "Ежегодно";
            default -> "-";
        });
        detailDependsOn.setText(getDependencyNames(task.getDependsOn()));
        detailStartDate.setText(task.hasStartDate() ? 
            (task.isStarted() ? task.getStartDate() + " ✓" : task.getStartDate() + " (ожидает)") : "Сразу");
        setInsightContent(task.getAiInsight() != null ? task.getAiInsight() : "Нажмите 'ИИ-Анализ'");
    }

    private String getDependencyNames(String dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (String id : dependsOn.split(",")) {
            for (Task t : tasks) {
                if (t.getId().equals(id.trim())) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(t.getTitle());
                    break;
                }
                for (Task sub : t.getSubtasks()) {
                    if (sub.getId().equals(id.trim())) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(sub.getTitle());
                        break;
                    }
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "-";
    }

    private void handleDuplicateTask() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для дублирования");
            return;
        }
        Task original = selected.getValue();
        Task copy = new Task(
            java.util.UUID.randomUUID().toString(),
            original.getTitle() + " (копия)",
            original.getDescription(),
            original.getDeadline(),
            original.getComplexity(),
            original.getParentId(),
            original.getTags(),
            original.getRecurrence()
        );
        aiEngine.calculatePriority(copy);
        db.saveTask(copy);
        if (copy.getParentId() == null) {
            tasks.add(copy);
        } else {
            for (Task t : tasks) {
                if (t.getId().equals(copy.getParentId())) {
                    t.getSubtasks().add(copy);
                    break;
                }
            }
        }
        refreshTree();
        showAlert("Задача дублирована: " + copy.getTitle());
    }

    private void handleAddTask(String parentId) {
        InlineView view = AddTaskDialog.inline(parentId, task -> {
            aiEngine.calculatePriority(task);
            db.saveTask(task);
            if (parentId == null) {
                tasks.add(task);
            } else {
                for (Task t : tasks) {
                    if (t.getId().equals(parentId)) {
                        t.getSubtasks().add(task);
                        break;
                    }
                }
            }
            refreshTree();
        }, null);
        showInline(view, view.getTitle());
    }

    private void handleAddSubtask() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите родительскую задачу");
            return;
        }
        Task parent = selected.getValue();
        if (parent.isSubtask()) {
            showAlert("Нельзя создать подзадачу для подзадачи");
            return;
        }
        handleAddTask(parent.getId());
    }
    
    private void handleEditTask(Task task) {
        InlineView view = EditTaskDialog.inline(task, updatedTask -> {
            aiEngine.calculatePriority(updatedTask);
            db.saveTask(updatedTask);
            refreshTree();
            updateDetailPanel(updatedTask);
        }, null);
        showInline(view, view.getTitle());
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        styleAlert(alert);
        alert.showAndWait();
    }

    private void styleAlert(Alert alert) {
        styleDialog(alert);
    }

    private void styleDialog(javafx.scene.control.Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (ConfigManager.isDarkTheme()) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("styled-alert");
        dialog.initOwner(taskTable.getScene().getWindow());
    }

    private void runAIAnalysisForSelected(Button triggerBtn) {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для анализа");
            return;
        }
        
        Task task = selected.getValue();
        
        // Меняем состояние кнопки
        triggerBtn.setDisable(true);
        triggerBtn.getStyleClass().add("insight-action-btn-loading");
        FontIcon loadingIcon = FontIcon.of(MaterialDesignL.LOADING, 14);
        triggerBtn.setGraphic(loadingIcon);
        
        setInsightContent("⏳ Анализирую задачу...");
        
        // Запускаем анализ
        aiEngine.analyzeTaskWithAI(task).thenAccept(insight -> {
            Platform.runLater(() -> {
                task.setAiInsight(insight);
                db.saveTask(task);
                setInsightContent(insight);
                
                // Успех
                triggerBtn.getStyleClass().remove("insight-action-btn-loading");
                triggerBtn.getStyleClass().add("insight-action-btn-success");
                triggerBtn.setGraphic(FontIcon.of(MaterialDesignC.CHECK, 14));
                triggerBtn.setDisable(false);
                
                // Через 1.5 сек возвращаем нормальное состояние
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> {
                        triggerBtn.getStyleClass().remove("insight-action-btn-success");
                        triggerBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 14));
                    });
                }).start();
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                setInsightContent("Ошибка анализа. Попробуйте позже.");
                triggerBtn.getStyleClass().remove("insight-action-btn-loading");
                triggerBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 14));
                triggerBtn.setDisable(false);
            });
            return null;
        });
    }

    /** Установить контент в WebView с рендерингом Markdown */
    private void setInsightContent(String markdown) {
        currentInsightText = markdown != null ? markdown : "";
        String html = convertMarkdownToHtml(currentInsightText);
        String fullHtml = getHtmlTemplate(html);
        aiInsightWebView.getEngine().loadContent(fullHtml);
    }
    
    /** Установить описание задачи в WebView с рендерингом Markdown */
    private void setDescriptionContent(String markdown) {
        currentDescriptionText = markdown != null ? markdown : "";
        String html = convertMarkdownToHtml(currentDescriptionText);
        String fullHtml = getDescriptionHtmlTemplate(html);
        descriptionWebView.getEngine().loadContent(fullHtml);
    }
    
    /** HTML шаблон для описания (компактный) */
    private String getDescriptionHtmlTemplate(String content) {
        boolean isDark = ConfigManager.isDarkTheme();
        
        String bgColor = isDark ? "#313244" : "#ccd0da";
        String textColor = isDark ? "#cdd6f4" : "#4c4f69";
        String headingColor = isDark ? "#89b4fa" : "#1e66f5";
        String codeColor = isDark ? "#a6e3a1" : "#40a02b";
        String codeBg = isDark ? "#1e1e2e" : "#e6e9ef";
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    font-size: 12px;
                    line-height: 1.5;
                    color: %s;
                    background-color: %s;
                    padding: 8px 10px;
                    border-radius: 8px;
                }
                p { margin-bottom: 6px; }
                h1, h2, h3 { color: %s; margin: 8px 0 4px 0; }
                h1 { font-size: 14px; }
                h2 { font-size: 13px; }
                h3 { font-size: 12px; }
                strong { font-weight: 600; }
                code {
                    background: %s;
                    color: %s;
                    padding: 1px 4px;
                    border-radius: 3px;
                    font-family: 'JetBrains Mono', monospace;
                    font-size: 11px;
                }
                ul, ol { margin: 4px 0 4px 16px; }
                li { margin: 2px 0; }
            </style>
            </head>
            <body>%s</body>
            </html>
            """.formatted(textColor, bgColor, headingColor, codeBg, codeColor, content);
    }
    
    /** Конвертация Markdown в HTML */
    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        
        String html = markdown;
        
        // Экранируем HTML
        html = html.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        
        // Заголовки
        html = html.replaceAll("(?m)^### (.+)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^## (.+)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^# (.+)$", "<h1>$1</h1>");
        
        // Жирный текст
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__(.+?)__", "<strong>$1</strong>");
        
        // Курсив
        html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        html = html.replaceAll("_(.+?)_", "<em>$1</em>");
        
        // Код inline
        html = html.replaceAll("`(.+?)`", "<code>$1</code>");
        
        // Списки
        html = html.replaceAll("(?m)^- (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\* (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\d+\\. (.+)$", "<li>$1</li>");
        html = html.replaceAll("(<li>.*</li>\\n?)+", "<ul>$0</ul>");
        
        // Переносы строк
        html = html.replace("\n\n", "</p><p>");
        html = html.replace("\n", "<br>");
        html = "<p>" + html + "</p>";
        
        // Убираем пустые параграфы
        html = html.replace("<p></p>", "");
        html = html.replace("<p><br></p>", "");
        
        return html;
    }
    
    /** HTML шаблон с CSS стилями */
    private String getHtmlTemplate(String content) {
        boolean isDark = ConfigManager.isDarkTheme();
        
        String bgColor = isDark ? "#1e1e2e" : "#eff1f5";
        String textColor = isDark ? "#cdd6f4" : "#4c4f69";
        String headingColor = isDark ? "#cba6f7" : "#8839ef";
        String codeColor = isDark ? "#a6e3a1" : "#40a02b";
        String codeBg = isDark ? "#313244" : "#e6e9ef";
        String linkColor = isDark ? "#89b4fa" : "#1e66f5";
        String listColor = isDark ? "#f9e2af" : "#df8e1d";
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                body {
                    font-family: 'Segoe UI', system-ui, sans-serif;
                    font-size: 13px;
                    line-height: 1.6;
                    color: %s;
                    background-color: %s;
                    margin: 0;
                    padding: 12px;
                }
                h1, h2, h3 {
                    color: %s;
                    margin: 12px 0 8px 0;
                    font-weight: 600;
                }
                h1 { font-size: 18px; }
                h2 { font-size: 16px; }
                h3 { font-size: 14px; }
                p { margin: 8px 0; }
                strong { font-weight: 600; }
                em { font-style: italic; }
                code {
                    background-color: %s;
                    color: %s;
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-family: 'JetBrains Mono', 'Consolas', monospace;
                    font-size: 12px;
                }
                ul, ol {
                    margin: 8px 0;
                    padding-left: 20px;
                }
                li {
                    margin: 4px 0;
                }
                li::marker {
                    color: %s;
                }
                a {
                    color: %s;
                    text-decoration: none;
                }
                a:hover {
                    text-decoration: underline;
                }
            </style>
            </head>
            <body>%s</body>
            </html>
            """.formatted(textColor, bgColor, headingColor, codeBg, codeColor, listColor, linkColor, content);
    }

    private void copyInsightToClipboard(Button btn) {
        String content = currentInsightText;
        if (content == null || content.isEmpty()) {
            showAlert("Нет данных для копирования");
            return;
        }
        
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
        clipboardContent.putString(content);
        clipboard.setContent(clipboardContent);
        
        // Визуальная обратная связь
        FontIcon checkIcon = FontIcon.of(MaterialDesignC.CHECK, 14);
        btn.setGraphic(checkIcon);
        btn.getStyleClass().add("insight-action-btn-success");
        
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            javafx.application.Platform.runLater(() -> {
                btn.setGraphic(FontIcon.of(MaterialDesignC.CONTENT_COPY, 14));
                btn.getStyleClass().remove("insight-action-btn-success");
            });
        }).start();
    }

    private void exportInsight() {
        String content = currentInsightText;
        if (content == null || content.isEmpty()) {
            showAlert("Нет данных для экспорта");
            return;
        }
        
        FileChooser fc = new FileChooser();
        fc.setTitle("Экспорт ИИ-рекомендаций");
        fc.setInitialFileName("ai_insight_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PDF", "*.pdf"),
            new FileChooser.ExtensionFilter("Markdown", "*.md"),
            new FileChooser.ExtensionFilter("Word", "*.docx")
        );
        
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        
        try {
            String path = file.getAbsolutePath();
            FileChooser.ExtensionFilter selected = fc.getSelectedExtensionFilter();
            String ext = selected.getExtensions().get(0).replace("*", "");
            
            if (!path.endsWith(ext)) {
                file = new File(path + ext);
            }
            
            if (ext.equals(".pdf")) exportToPdf(file, content);
            else if (ext.equals(".md")) exportToMd(file, content);
            else if (ext.equals(".docx")) exportToDocx(file, content);
            showAlert("Экспортировано: " + file.getName());
        } catch (Exception e) {
            showAlert("Ошибка экспорта: " + e.getMessage());
        }
    }

    private void exportToPdf(File file, String content) throws Exception {
        PdfWriter writer = new PdfWriter(file);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);
        
        PdfFont font = null;
        PdfFont boldFont = null;
        String[] fontPaths = {
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "C:/Windows/Fonts/arial.ttf"
        };
        String[] boldPaths = {
            "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
            "C:/Windows/Fonts/arialbd.ttf"
        };
        
        for (String path : fontPaths) {
            try {
                font = PdfFontFactory.createFont(path, PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                break;
            } catch (Exception ignored) {}
        }
        for (String path : boldPaths) {
            try {
                boldFont = PdfFontFactory.createFont(path, PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                break;
            } catch (Exception ignored) {}
        }
        if (boldFont == null) boldFont = font;
        
        if (font != null) doc.setFont(font);
        
        doc.add(new Paragraph("ИИ-Рекомендации NeuroFlow").setFont(boldFont).setFontSize(20));
        doc.add(new Paragraph(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).setFontSize(10));
        doc.add(new Paragraph("\n"));
        
        String[] lines = content.split("\n");
        List<String> tableRows = new ArrayList<>();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Собираем строки таблицы
            if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
                tableRows.add(line);
            } else {
                // Если накопились строки таблицы - рендерим таблицу
                if (!tableRows.isEmpty()) {
                    doc.add(createPdfTable(tableRows, font, boldFont));
                    tableRows.clear();
                }
                doc.add(parseMdLine(line, font, boldFont));
            }
        }
        // Последняя таблица если есть
        if (!tableRows.isEmpty()) {
            doc.add(createPdfTable(tableRows, font, boldFont));
        }
        
        doc.close();
    }
    
    private Table createPdfTable(List<String> rows, PdfFont font, PdfFont boldFont) {
        if (rows.isEmpty()) return new Table(1);
        
        // Определяем количество колонок по первой строке
        String[] firstCells = rows.get(0).split("\\|");
        int colCount = (int) java.util.Arrays.stream(firstCells).filter(s -> !s.trim().isEmpty()).count();
        if (colCount == 0) colCount = 1;
        
        Table table = new Table(UnitValue.createPercentArray(colCount)).useAllAvailableWidth();
        table.setFontSize(9);
        if (font != null) table.setFont(font);
        
        boolean isHeader = true;
        for (String row : rows) {
            // Пропускаем разделитель |---|---|
            if (row.matches("^\\|[-:\\s|]+\\|$")) continue;
            
            String[] cells = row.split("\\|");
            int added = 0;
            for (String cellText : cells) {
                if (cellText.trim().isEmpty() && added == 0) continue; // пропуск первого пустого
                if (added >= colCount) break;
                
                String text = cleanMdFormatting(cellText.trim());
                Cell cell = new Cell().add(new Paragraph(text));
                if (isHeader && boldFont != null) {
                    cell.setFont(boldFont);
                }
                table.addCell(cell);
                added++;
            }
            // Добавляем пустые ячейки если не хватает
            while (added < colCount) {
                table.addCell(new Cell().add(new Paragraph("")));
                added++;
            }
            isHeader = false;
        }
        
        table.setMarginTop(5).setMarginBottom(5);
        return table;
    }
    
    private Paragraph parseMdLine(String line, PdfFont font, PdfFont boldFont) {
        String cleaned = cleanMdFormatting(line);
        // Заголовки
        if (line.startsWith("### ")) {
            return new Paragraph(cleanMdFormatting(line.substring(4))).setFont(boldFont).setFontSize(12).setMarginTop(10);
        } else if (line.startsWith("## ")) {
            return new Paragraph(cleanMdFormatting(line.substring(3))).setFont(boldFont).setFontSize(14).setMarginTop(12);
        } else if (line.startsWith("# ")) {
            return new Paragraph(cleanMdFormatting(line.substring(2))).setFont(boldFont).setFontSize(16).setMarginTop(14);
        } else if (line.startsWith("---")) {
            return new Paragraph("─".repeat(50)).setFontSize(8).setMarginTop(5).setMarginBottom(5);
        } else if (line.startsWith("- ") || line.startsWith("* ")) {
            return new Paragraph("  • " + cleanMdFormatting(line.substring(2))).setFont(font).setFontSize(10);
        } else if (line.matches("^\\d+\\.\\s.*")) {
            return new Paragraph("  " + cleaned).setFont(font).setFontSize(10);
        } else {
            return new Paragraph(cleaned).setFont(font).setFontSize(10);
        }
    }
    
    private String cleanMdFormatting(String text) {
        return text
            .replace("\\u003cbr\\u003e", " ")          // \u003cbr\u003e as literal
            .replace("\\u003c", "<")                   // \u003c as literal
            .replace("\\u003e", ">")                   // \u003e as literal  
            .replace("\u003cbr\u003e", " ")            // actual unicode <br>
            .replace("<br>", " ")                      // html br
            .replace("<br/>", " ")
            .replace("<br />", " ")
            // Emoji числа -> обычные
            .replace("1️⃣", "1.")
            .replace("2️⃣", "2.")
            .replace("3️⃣", "3.")
            .replace("4️⃣", "4.")
            .replace("5️⃣", "5.")
            .replace("6️⃣", "6.")
            .replace("7️⃣", "7.")
            .replace("8️⃣", "8.")
            .replace("9️⃣", "9.")
            .replace("🔟", "10.")
            // Другие частые emoji
            .replace("✅", "[OK]")
            .replace("❌", "[X]")
            .replace("⚠️", "[!]")
            .replace("📊", "")
            .replace("📈", "")
            .replace("📉", "")
            .replace("💡", "*")
            .replace("🎯", "*")
            .replace("🔴", "*")
            .replace("🟠", "*")
            .replace("🟡", "*")
            .replace("🟢", "*")
            .replace("⏱", "")
            .replace("⏳", "")
            .replace("🤖", "")
            .replace("📋", "")
            .replaceAll("[\\x{1F300}-\\x{1F9FF}]", "")  // удалить остальные emoji
            .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")   // **bold**
            .replaceAll("\\*([^*]+)\\*", "$1")         // *italic*
            .replaceAll("`([^`]+)`", "$1")             // `code`
            .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1"); // [link](url)
    }

    private void exportToMd(File file, String content) throws Exception {
        String cleaned = content
            .replace("\\u003cbr\\u003e", "<br>")
            .replace("\\u003c", "<")
            .replace("\\u003e", ">");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("# ИИ-Рекомендации NeuroFlow\n\n");
            fw.write("*" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "*\n\n");
            fw.write("---\n\n");
            fw.write(cleaned);
        }
    }

    private void exportToDocx(File file, String content) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); FileOutputStream out = new FileOutputStream(file)) {
            XWPFParagraph title = doc.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setText("ИИ-Рекомендации NeuroFlow");
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            
            XWPFParagraph date = doc.createParagraph();
            date.createRun().setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            
            for (String line : content.split("\n")) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(line);
            }
            doc.write(out);
        }
    }

    private void handleSmartSort() {
        tasks.sort(Comparator.comparingDouble(Task::getSmartPriority).reversed());
        refreshTree();
    }

    private void handleAnalyzeAll() {
        if (tasks.isEmpty()) return;
        
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        Task toAnalyze = (selected != null && selected.getValue() != null) ? selected.getValue() : tasks.get(0);
        
        setInsightContent("⏳ Анализирую задачи с помощью ИИ...");
        
        tasks.forEach(task -> {
            aiEngine.calculatePriority(task);
            db.saveTask(task);
            task.getSubtasks().forEach(sub -> {
                aiEngine.calculatePriority(sub);
                db.saveTask(sub);
            });
        });
        refreshTree();
        
        aiEngine.analyzeTaskWithAI(toAnalyze).thenAccept(insight -> 
            javafx.application.Platform.runLater(() -> {
                toAnalyze.setAiInsight(insight);
                db.saveTask(toAnalyze);
                updateDetailPanel(toAnalyze);
            })
        );
    }

    private void handleAutoPrioritization() {
        if (tasks.isEmpty()) {
            showAlert("Нет задач для приоритизации");
            return;
        }
        setInsightContent("🤖 ИИ определяет приоритеты задач...");
        
        List<Task> allTasks = new ArrayList<>(tasks);
        tasks.forEach(t -> allTasks.addAll(t.getSubtasks()));
        
        autoPrioritizer.prioritizeWithAI(allTasks).thenAccept(result ->
            javafx.application.Platform.runLater(() -> {
                allTasks.forEach(db::saveTask);
                refreshTree();
                setInsightContent(result);
            })
        );
    }

    private void handleAutoSchedule() {
        if (tasks.isEmpty()) {
            showAlert("Нет задач для планирования");
            return;
        }
        
        setInsightContent("📅 ИИ составляет оптимальное расписание...");
        // Выполняем пересчёт на FX-потоке, чтобы не трогать ObservableList из фонового потока
        javafx.application.Platform.runLater(() -> {
            String result = aiEngine.autoSchedule(tasks, 15); // 15 complexity points per day
            tasks.forEach(db::saveTask);
            refreshTree();
            setInsightContent(result);
        });
    }

    private void handlePredictTime() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для оценки времени");
            return;
        }
        Task task = selected.getValue();
        setInsightContent("⏳ ИИ оценивает время выполнения...");
        
        timePrediction.predictTime(task).thenAccept(result ->
            javafx.application.Platform.runLater(() -> setInsightContent(result))
        );
    }

    private void handleRecommendations() {
        if (tasks.isEmpty()) {
            showAlert("Нет задач для анализа");
            return;
        }
        setInsightContent("💡 ИИ анализирует задачи...");
        
        List<Task> allTasks = new ArrayList<>(tasks);
        tasks.forEach(t -> allTasks.addAll(t.getSubtasks()));
        
        recommendations.getRecommendations(allTasks).thenAccept(result ->
            javafx.application.Platform.runLater(() -> setInsightContent(result))
        );
    }

    private void handleProductivityAnalysis() {
        if (tasks.isEmpty()) {
            showAlert("Нет данных для анализа");
            return;
        }
        setInsightContent("📈 ИИ анализирует паттерны работы...");
        
        List<Task> allTasks = new ArrayList<>(tasks);
        tasks.forEach(t -> allTasks.addAll(t.getSubtasks()));
        
        productivityAnalysis.analyzeProductivity(allTasks).thenAccept(result ->
            javafx.application.Platform.runLater(() -> setInsightContent(result))
        );
    }

    private void filterUrgent() {
        rootItem.getChildren().clear();
        for (Task task : tasks) {
            if (task.getSmartPriority() >= 6) {
                TreeItem<Task> item = new TreeItem<>(task);
                item.setExpanded(true);
                for (Task sub : task.getSubtasks()) {
                    if (sub.getSmartPriority() >= 6) {
                        item.getChildren().add(new TreeItem<>(sub));
                    }
                }
                rootItem.getChildren().add(item);
            }
        }
    }

    private void filterByTag() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Фильтр по тегу");
        dialog.setHeaderText(null);
        dialog.setContentText("Введите тег:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(tag -> {
            String searchTag = tag.trim().toLowerCase();
            if (searchTag.isEmpty()) {
                refreshTree();
                return;
            }
            rootItem.getChildren().clear();
            for (Task task : tasks) {
                boolean taskMatch = task.getTags().toLowerCase().contains(searchTag);
                TreeItem<Task> item = new TreeItem<>(task);
                item.setExpanded(true);
                for (Task sub : task.getSubtasks()) {
                    if (sub.getTags().toLowerCase().contains(searchTag)) {
                        item.getChildren().add(new TreeItem<>(sub));
                    }
                }
                if (taskMatch || !item.getChildren().isEmpty()) {
                    rootItem.getChildren().add(item);
                }
            }
        });
    }

    private void handleToggleComplete(Task task) {
        if (task == null) return;
        boolean newState = !task.isCompleted();
        task.setCompleted(newState);
        if (newState) {
            task.setCompletedDate(LocalDate.now());
        } else {
            task.setCompletedDate(null);
        }
        db.saveTask(task);
        taskTable.refresh();
        updateDetailPanel(task);
    }

    private void handleArchiveTask() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для архивирования");
            return;
        }
        Task task = selected.getValue();
        task.setArchived(true);
        db.saveTask(task);
        for (Task sub : task.getSubtasks()) {
            sub.setArchived(true);
            db.saveTask(sub);
        }
        refreshTree();
        showAlert("Задача перемещена в архив: " + task.getTitle());
    }

    private void showArchivedTasks() {
        rootItem.getChildren().clear();
        for (Task task : tasks) {
            if (!task.isArchived()) continue;
            TreeItem<Task> item = new TreeItem<>(task);
            item.setExpanded(true);
            for (Task sub : task.getSubtasks()) {
                if (sub.isArchived()) item.getChildren().add(new TreeItem<>(sub));
            }
            rootItem.getChildren().add(item);
        }
        taskTable.refresh();
    }

    private List<Task> getSelectedTasks() {
        List<Task> selected = new ArrayList<>();
        for (TreeItem<Task> item : taskTable.getSelectionModel().getSelectedItems()) {
            if (item != null && item.getValue() != null) selected.add(item.getValue());
        }
        return selected;
    }

    private void bulkArchive() {
        List<Task> selected = getSelectedTasks();
        if (selected.isEmpty()) { showAlert("Выберите задачи (Ctrl+клик)"); return; }
        for (Task task : selected) {
            task.setArchived(true);
            db.saveTask(task);
        }
        refreshTree();
        showAlert("Архивировано задач: " + selected.size());
    }

    private void bulkDelete() {
        List<Task> selected = getSelectedTasks();
        if (selected.isEmpty()) { showAlert("Выберите задачи (Ctrl+клик)"); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Удалить " + selected.size() + " задач?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        styleAlert(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            for (Task task : selected) {
                db.deleteTask(task.getId());
                removeTaskFromList(task);
            }
            refreshTree();
        }
    }

    private void bulkAddTag() {
        List<Task> selected = getSelectedTasks();
        if (selected.isEmpty()) { showAlert("Выберите задачи (Ctrl+клик)"); return; }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Добавить тег");
        dialog.setHeaderText(null);
        dialog.setContentText("Тег для " + selected.size() + " задач:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(tag -> {
            if (!tag.trim().isEmpty()) {
                for (Task task : selected) {
                    String current = task.getTags();
                    task.setTags(current.isEmpty() ? tag.trim() : current + ", " + tag.trim());
                    db.saveTask(task);
                }
                refreshTree();
                showAlert("Тег добавлен к " + selected.size() + " задачам");
            }
        });
    }

    private void handleCreateFromTemplate() {
        List<TaskTemplate> templates = db.loadAllTemplates();
        if (templates.isEmpty()) {
            showAlert("Нет сохранённых шаблонов");
            return;
        }
        ChoiceDialog<TaskTemplate> dialog = new ChoiceDialog<>(templates.get(0), templates);
        dialog.setTitle("Создать из шаблона");
        dialog.setHeaderText(null);
        dialog.setContentText("Выберите шаблон:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(template -> {
            Task task = template.createTask();
            aiEngine.calculatePriority(task);
            db.saveTask(task);
            tasks.add(task);
            refreshTree();
        });
    }

    private void handleSaveAsTemplate() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для сохранения как шаблон");
            return;
        }
        Task task = selected.getValue();
        TextInputDialog dialog = new TextInputDialog(task.getTitle());
        dialog.setTitle("Сохранить шаблон");
        dialog.setHeaderText(null);
        dialog.setContentText("Название шаблона:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                int days = (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline());
                TaskTemplate template = new TaskTemplate(name.trim(), task.getTitle(), task.getDescription(), 
                    task.getComplexity(), Math.max(1, days), task.getTags());
                db.saveTemplate(template);
                showAlert("Шаблон сохранён: " + name);
            }
        });
    }

    private void handleLinkDependency() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу, которая будет зависеть от другой");
            return;
        }
        Task dependentTask = selected.getValue();
        
        List<Task> availableTasks = new ArrayList<>();
        for (Task t : tasks) {
            if (!t.getId().equals(dependentTask.getId())) availableTasks.add(t);
            for (Task sub : t.getSubtasks()) {
                if (!sub.getId().equals(dependentTask.getId())) availableTasks.add(sub);
            }
        }
        if (availableTasks.isEmpty()) {
            showAlert("Нет доступных задач для связывания");
            return;
        }
        
        ChoiceDialog<Task> dialog = new ChoiceDialog<>(availableTasks.get(0), availableTasks);
        dialog.setTitle("Связать задачи");
        dialog.setHeaderText("Задача \"" + dependentTask.getTitle() + "\" будет зависеть от:");
        dialog.setContentText("Выберите задачу:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(blockerTask -> {
            String current = dependentTask.getDependsOn();
            String newDeps = current.isEmpty() ? blockerTask.getId() : current + "," + blockerTask.getId();
            dependentTask.setDependsOn(newDeps);
            db.saveTask(dependentTask);
            updateDetailPanel(dependentTask);
            showAlert("Зависимость добавлена: " + dependentTask.getTitle() + " → " + blockerTask.getTitle());
        });
    }

    private StackPane createOverlayHost() {
        overlayHost.getStyleClass().add("overlay-host");
        overlayHost.setVisible(false);
        overlayHost.setMouseTransparent(true);
        overlayHost.setPickOnBounds(false);
        overlayHost.managedProperty().bind(overlayHost.visibleProperty());

        overlayScrim.getStyleClass().add("overlay-scrim");
        overlayScrim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlayScrim.visibleProperty().bind(overlayHost.visibleProperty());
        overlayScrim.managedProperty().bind(overlayHost.visibleProperty());
        overlayScrim.setOnMouseClicked(e -> closeInline());
        StackPane.setAlignment(overlayScrim, Pos.CENTER);

        overlayTitle.getStyleClass().add("overlay-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button();
        closeBtn.setGraphic(FontIcon.of(MaterialDesignC.CLOSE, 16));
        closeBtn.getStyleClass().add("overlay-close-btn");
        closeBtn.setOnAction(e -> closeInline());

        HBox header = new HBox(8, overlayTitle, spacer, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("overlay-header");
        header.setPadding(new Insets(0, 0, 4, 0));

        overlayContentHolder.getStyleClass().add("overlay-content");
        overlayContentHolder.setMinSize(0, 0);
        overlayContentHolder.setMaxWidth(Double.MAX_VALUE);

        overlayContainer.getStyleClass().add("overlay-container");
        overlayContainer.setSpacing(6);
        overlayContainer.setPadding(new Insets(10, 12, 12, 12));
        overlayContainer.setMaxWidth(Double.MAX_VALUE);
        // Адаптивные размеры для низких разрешений: используем больший процент экрана
        overlayContainer.prefWidthProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(() -> {
                double hostWidth = overlayHost.getWidth();
                // На узких экранах (<1400px) используем 95%, иначе 90%
                return hostWidth < 1100 ? hostWidth * 0.98 : hostWidth * 0.92;
            }, overlayHost.widthProperty())
        );
        overlayContainer.maxWidthProperty().bind(overlayHost.widthProperty().multiply(0.98));
        // Адаптивная высота: на низких разрешениях используем больше пространства
        overlayContainer.maxHeightProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(() -> {
                double hostHeight = overlayHost.getHeight();
                // На низких экранах (<800px) используем 95%, иначе 90%
                return hostHeight < 750 ? hostHeight * 0.95 : hostHeight * 0.90;
            }, overlayHost.heightProperty())
        );
        overlayContainer.visibleProperty().bind(overlayHost.visibleProperty());
        overlayContainer.managedProperty().bind(overlayHost.visibleProperty());
        overlayContainer.getChildren().addAll(header, overlayContentHolder);

        // Центрируем диалог по центру экрана
        StackPane.setAlignment(overlayContainer, Pos.CENTER);
        
        overlayHost.getChildren().addAll(overlayScrim, overlayContainer);
        return overlayHost;
    }

    public void showInline(InlineView view, String title) {
        if (view == null) return;
        currentInlineView = view;
        view.setCloseAction(this::closeInline);
        String headerTitle = (title != null && !title.isEmpty()) ? title : view.getTitle();
        showInline(view.getContent(), view.getOnClose(), headerTitle);
    }

    public void showInline(Node content, Runnable onClose, String title) {
        if (content == null) return;

        if (overlayHost.isVisible()) {
            closeInline();
        }

        previousFocusOwner = getScene() != null ? getScene().getFocusOwner() : null;
        overlayOnClose = onClose;
        // Reset currentInlineView if called directly with Node (not through InlineView)
        if (currentInlineView != null && currentInlineView.getContent() != content) {
            currentInlineView = null;
        }

        overlayTitle.setText(title != null ? title : "");
        overlayContentHolder.getChildren().setAll(content);

        overlayHost.setVisible(true);
        overlayHost.setPickOnBounds(true);
        overlayHost.setMouseTransparent(false);

        registerEscapeHandler();

        Platform.runLater(() -> {
            Node target = content;
            if (target != null) {
                target.requestFocus();
            } else if (overlayContainer != null) {
                overlayContainer.requestFocus();
            }
        });
    }

    public void closeInline() {
        if (!overlayHost.isVisible()) return;

        // Check if view allows closing (may show confirmation dialog)
        if (currentInlineView != null && !currentInlineView.canClose()) {
            return; // View prevented closing
        }

        overlayHost.setVisible(false);
        overlayHost.setPickOnBounds(false);
        overlayHost.setMouseTransparent(true);
        overlayContentHolder.getChildren().clear();

        if (getScene() != null && overlayEscapeHandler != null) {
            getScene().removeEventFilter(KeyEvent.KEY_PRESSED, overlayEscapeHandler);
        }
        overlayEscapeHandler = null;

        if (overlayOnClose != null) {
            overlayOnClose.run();
            overlayOnClose = null;
        }
        
        currentInlineView = null;

        if (previousFocusOwner != null) {
            previousFocusOwner.requestFocus();
            previousFocusOwner = null;
        }
    }
    
    /**
     * Check if application can be closed. Shows confirmation dialog if there are unsaved changes.
     * @return true if close is allowed, false to prevent closing
     */
    public boolean canCloseApplication() {
        if (overlayHost.isVisible() && currentInlineView != null) {
            return currentInlineView.canClose();
        }
        return true;
    }

    private void registerEscapeHandler() {
        if (getScene() == null) return;
        if (overlayEscapeHandler != null) {
            getScene().removeEventFilter(KeyEvent.KEY_PRESSED, overlayEscapeHandler);
        }
        overlayEscapeHandler = event -> {
            if (event.getCode() == KeyCode.ESCAPE && overlayHost.isVisible()) {
                event.consume();
                closeInline();
            }
        };
        getScene().addEventFilter(KeyEvent.KEY_PRESSED, overlayEscapeHandler);
    }

    private void loadTasks() {
        tasks.addAll(db.loadAllTasks());
        processRecurringTasks();
        refreshTree();
    }

    private void processRecurringTasks() {
        LocalDate today = LocalDate.now();
        List<Task> newTasks = new ArrayList<>();
        for (Task task : tasks) {
            if (task.isRecurring() && task.getDeadline().isBefore(today)) {
                LocalDate newDeadline = switch (task.getRecurrence()) {
                    case "daily" -> task.getDeadline().plusDays(1);
                    case "weekly" -> task.getDeadline().plusWeeks(1);
                    case "monthly" -> task.getDeadline().plusMonths(1);
                    case "yearly" -> task.getDeadline().plusYears(1);
                    default -> task.getDeadline();
                };
                while (newDeadline.isBefore(today)) {
                    newDeadline = switch (task.getRecurrence()) {
                        case "daily" -> newDeadline.plusDays(1);
                        case "weekly" -> newDeadline.plusWeeks(1);
                        case "monthly" -> newDeadline.plusMonths(1);
                        case "yearly" -> newDeadline.plusYears(1);
                        default -> newDeadline;
                    };
                }
                Task newTask = new Task(java.util.UUID.randomUUID().toString(), task.getTitle(), 
                    task.getDescription(), newDeadline, task.getComplexity(), null, task.getTags(), task.getRecurrence());
                aiEngine.calculatePriority(newTask);
                db.saveTask(newTask);
                newTasks.add(newTask);
                task.setRecurrence("");
                db.saveTask(task);
            }
        }
        tasks.addAll(newTasks);
    }

}
