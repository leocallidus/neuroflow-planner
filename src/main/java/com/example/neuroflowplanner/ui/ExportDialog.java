package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import java.math.BigInteger;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.io.font.PdfEncodings;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Inline export view.
 */
public class ExportDialog implements InlineView {

    private final ScrollPane root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;

    private ExportDialog(List<Task> tasks) {
        VBox content = new VBox(25);
        content.setPadding(new Insets(30));
        content.getStyleClass().add("export-content");

        // --- Header ---
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("export-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignE.EXPORT, 24);
        icon.getStyleClass().add("export-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(4);
        Label title = new Label("Экспорт Данных");
        title.getStyleClass().add("export-title");
        Label subtitle = new Label("Сохраните ваши задачи в удобном формате");
        subtitle.getStyleClass().add("export-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        content.getChildren().add(header);

        // --- Export Options Grid ---
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(20);
        
        VBox excelCard = createExportCard(
            "Excel (.xlsx)", 
            "Полный отчет с форматированием", 
            MaterialDesignF.FILE_EXCEL, 
            "export-card-excel",
            () -> exportExcel(tasks)
        );
        
        VBox pdfCard = createExportCard(
            "PDF Документ", 
            "Версия для печати и просмотра", 
            MaterialDesignF.FILE_PDF, 
            "export-card-pdf",
            () -> exportPdf(tasks)
        );
        
        VBox csvCard = createExportCard(
            "CSV Файл", 
            "Простой текст с разделителями", 
            MaterialDesignF.FILE_DELIMITED, 
            "export-card-csv",
            () -> exportCsv(tasks)
        );
        
        VBox docxCard = createExportCard(
            "Word (.docx)", 
            "Документ Microsoft Word", 
            MaterialDesignF.FILE_WORD, 
            "export-card-docx",
            () -> exportDocx(tasks)
        );
        
        VBox mdCard = createExportCard(
            "Markdown (.md)", 
            "Текстовый формат с разметкой", 
            MaterialDesignL.LANGUAGE_MARKDOWN, 
            "export-card-md",
            () -> exportMarkdown(tasks)
        );

        grid.add(excelCard, 0, 0);
        grid.add(pdfCard, 1, 0);
        grid.add(csvCard, 0, 1);
        grid.add(docxCard, 1, 1);
        grid.add(mdCard, 0, 2);
        
        content.getChildren().add(grid);

        root = new ScrollPane(content);
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(350, 350);
        root.getStyleClass().add("export-root");
        
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new ExportDialog(tasks);
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
        return "Экспорт";
    }

    private VBox createExportCard(String title, String desc, org.kordamp.ikonli.Ikon iconCode, String styleClass, Runnable action) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(20));
        card.setPrefWidth(240);
        card.getStyleClass().addAll("export-card", styleClass);
        card.setOnMouseClicked(e -> action.run());

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon icon = FontIcon.of(iconCode, 28);
        icon.getStyleClass().add("export-card-icon");
        
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("export-card-title");
        
        top.getChildren().addAll(icon, titleLbl);
        
        Label descLbl = new Label(desc);
        descLbl.getStyleClass().add("export-card-desc");
        descLbl.setWrapText(true);

        card.getChildren().addAll(top, descLbl);
        return card;
    }

    private void exportExcel(List<Task> tasks) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить Excel");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        fc.setInitialFileName(generateFileName("xlsx"));
        File file = fc.showSaveDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (file == null) return;

        try (XSSFWorkbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(file)) {
            Sheet sheet = wb.createSheet("Задачи");
            
            // Создаём стили
            XSSFCellStyle titleStyle = createTitleStyle(wb);
            XSSFCellStyle subtitleStyle = createSubtitleStyle(wb);
            XSSFCellStyle headerStyle = createHeaderStyle(wb);
            XSSFCellStyle dataStyle = createDataStyle(wb);
            XSSFCellStyle dataAltStyle = createDataAltStyle(wb);
            XSSFCellStyle statusActiveStyle = createStatusActiveStyle(wb);
            XSSFCellStyle statusDoneStyle = createStatusDoneStyle(wb);
            
            int rowNum = 0;
            
            // Заголовок документа
            Row titleRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("НейроФлоу Планировщик");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
            
            // Подзаголовок
            Row subtitleRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell subtitleCell = subtitleRow.createCell(0);
            subtitleCell.setCellValue("Отчёт по задачам • " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            subtitleCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));
            
            // Статистика
            long totalTasks = tasks.size() + tasks.stream().mapToLong(t -> t.getSubtasks().size()).sum();
            long activeTasks = tasks.stream().filter(t -> !t.isArchived()).count() +
                tasks.stream().flatMap(t -> t.getSubtasks().stream()).filter(t -> !t.isArchived()).count();
            long completedTasks = totalTasks - activeTasks;
            
            Row statsRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell statsCell = statsRow.createCell(0);
            statsCell.setCellValue("Всего: " + totalTasks + " | Активных: " + activeTasks + " | Завершённых: " + completedTasks);
            statsCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 5));
            
            rowNum++; // Пустая строка
            
            // Заголовки таблицы
            Row headerRow = sheet.createRow(rowNum++);
            String[] cols = {"Название", "Описание", "Дедлайн", "Сложность", "Приоритет", "Статус"};
            for (int i = 0; i < cols.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            // Данные
            boolean alternate = false;
            for (Task t : tasks) {
                rowNum = addExcelTaskRow(sheet, t, rowNum, "", alternate, dataStyle, dataAltStyle, statusActiveStyle, statusDoneStyle);
                alternate = !alternate;
                for (Task sub : t.getSubtasks()) {
                    rowNum = addExcelTaskRow(sheet, sub, rowNum, "  ↳ ", alternate, dataStyle, dataAltStyle, statusActiveStyle, statusDoneStyle);
                    alternate = !alternate;
                }
            }

            // Автоширина колонок
            sheet.setColumnWidth(0, 8000);  // Название
            sheet.setColumnWidth(1, 12000); // Описание
            sheet.setColumnWidth(2, 3500);  // Дедлайн
            sheet.setColumnWidth(3, 3000);  // Сложность
            sheet.setColumnWidth(4, 3000);  // Приоритет
            sheet.setColumnWidth(5, 3500);  // Статус
            
            wb.write(fos);
            showSuccessDialog("Excel успешно сохранён", file.getName(), file.getAbsolutePath());
        } catch (Exception ex) {
            showErrorDialog("Ошибка экспорта Excel", ex.getMessage());
        }
    }
    
    private XSSFCellStyle createTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 18);
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{30, 102, (byte) 245}, null));
        style.setFont(font);
        return style;
    }
    
    private XSSFCellStyle createSubtitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 11);
        font.setColor(new XSSFColor(new byte[]{108, 111, (byte) 133}, null));
        style.setFont(font);
        return style;
    }
    
    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{30, 102, (byte) 245}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private XSSFCellStyle createDataStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFColor borderColor = new XSSFColor(new byte[]{(byte) 220, (byte) 220, (byte) 220}, null);
        style.setBottomBorderColor(borderColor);
        style.setTopBorderColor(borderColor);
        style.setLeftBorderColor(borderColor);
        style.setRightBorderColor(borderColor);
        return style;
    }
    
    private XSSFCellStyle createDataAltStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = createDataStyle(wb);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 239, (byte) 241, (byte) 245}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    private XSSFCellStyle createStatusActiveStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{30, 102, (byte) 245}, null));
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private XSSFCellStyle createStatusDoneStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{64, (byte) 160, 43}, null));
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private int addExcelTaskRow(Sheet sheet, Task t, int rowNum, String prefix, boolean alternate,
                                 XSSFCellStyle dataStyle, XSSFCellStyle dataAltStyle,
                                 XSSFCellStyle statusActiveStyle, XSSFCellStyle statusDoneStyle) {
        Row row = sheet.createRow(rowNum);
        XSSFCellStyle style = alternate ? dataAltStyle : dataStyle;
        
        // Название
        org.apache.poi.ss.usermodel.Cell titleCellExcel = row.createCell(0);
        titleCellExcel.setCellValue(prefix + t.getTitle());
        titleCellExcel.setCellStyle(style);
        
        // Описание
        org.apache.poi.ss.usermodel.Cell descCell = row.createCell(1);
        descCell.setCellValue(t.getDescription() != null ? t.getDescription() : "");
        descCell.setCellStyle(style);
        
        // Дедлайн
        org.apache.poi.ss.usermodel.Cell deadlineCell = row.createCell(2);
        deadlineCell.setCellValue(t.getDeadline() != null ? t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "-");
        deadlineCell.setCellStyle(style);
        
        // Сложность
        org.apache.poi.ss.usermodel.Cell complexityCell = row.createCell(3);
        complexityCell.setCellValue(t.getComplexity() + "/10");
        complexityCell.setCellStyle(style);
        
        // Приоритет
        org.apache.poi.ss.usermodel.Cell priorityCell = row.createCell(4);
        priorityCell.setCellValue(String.format("%.1f", t.getSmartPriority()));
        priorityCell.setCellStyle(style);
        
        // Статус
        org.apache.poi.ss.usermodel.Cell statusCell = row.createCell(5);
        statusCell.setCellValue(t.isArchived() ? "Завершена" : "Активна");
        statusCell.setCellStyle(t.isArchived() ? statusDoneStyle : statusActiveStyle);
        
        return rowNum + 1;
    }

    private void exportPdf(List<Task> tasks) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName(generateFileName("pdf"));
        File file = fc.showSaveDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (file == null) return;

        try {
            PdfWriter writer = new PdfWriter(file);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);

            // Загружаем шрифт с поддержкой кириллицы
            PdfFont font;
            PdfFont fontBold;
            try {
                // Пробуем системные шрифты с кириллицей
                String[] fontPaths = {
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                    "/usr/share/fonts/TTF/DejaVuSans.ttf",
                    "C:/Windows/Fonts/arial.ttf",
                    "/System/Library/Fonts/Helvetica.ttc"
                };
                PdfFont loadedFont = null;
                for (String path : fontPaths) {
                    try {
                        File f = new File(path);
                        if (f.exists()) {
                            loadedFont = PdfFontFactory.createFont(path, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                            break;
                        }
                    } catch (Exception ignored) {}
                }
                if (loadedFont != null) {
                    font = loadedFont;
                    fontBold = loadedFont;
                } else {
                    font = PdfFontFactory.createFont("Helvetica", PdfEncodings.CP1252, PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
                    fontBold = font;
                }
            } catch (Exception e) {
                font = PdfFontFactory.createFont("Helvetica", PdfEncodings.CP1252, PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
                fontBold = font;
            }

            // Заголовок
            DeviceRgb primaryColor = new DeviceRgb(30, 102, 245);
            DeviceRgb headerBg = new DeviceRgb(239, 241, 245);
            DeviceRgb textColor = new DeviceRgb(76, 79, 105);
            
            doc.add(new Paragraph("НейроФлоу Планировщик")
                .setFont(fontBold)
                .setFontSize(22)
                .setFontColor(primaryColor)
                .setMarginBottom(5));
            
            doc.add(new Paragraph("Отчёт по задачам")
                .setFont(font)
                .setFontSize(14)
                .setFontColor(textColor)
                .setMarginBottom(5));
            
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            doc.add(new Paragraph("Дата создания: " + dateStr)
                .setFont(font)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(140, 143, 161))
                .setMarginBottom(20));

            // Статистика
            long totalTasks = tasks.size() + tasks.stream().mapToLong(t -> t.getSubtasks().size()).sum();
            long activeTasks = tasks.stream().filter(t -> !t.isArchived()).count() +
                tasks.stream().flatMap(t -> t.getSubtasks().stream()).filter(t -> !t.isArchived()).count();
            long completedTasks = totalTasks - activeTasks;
            
            doc.add(new Paragraph("Всего задач: " + totalTasks + " | Активных: " + activeTasks + " | Завершённых: " + completedTasks)
                .setFont(font)
                .setFontSize(11)
                .setFontColor(textColor)
                .setMarginBottom(15));

            // Таблица - 6 колонок без сокращений
            Table table = new Table(UnitValue.createPercentArray(new float[]{22, 30, 13, 10, 10, 15}))
                .setWidth(UnitValue.createPercentValue(100));
            
            // Заголовки таблицы
            String[] headers = {"Название", "Описание", "Дедлайн", "Сложность", "Приоритет", "Статус"};
            for (String h : headers) {
                com.itextpdf.layout.element.Cell headerCell = new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setFont(fontBold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(primaryColor)
                    .setPadding(6)
                    .setTextAlignment(TextAlignment.CENTER);
                table.addHeaderCell(headerCell);
            }

            // Данные
            boolean alternate = false;
            for (Task t : tasks) {
                addPdfRow(table, t, font, "", alternate, headerBg);
                alternate = !alternate;
                for (Task sub : t.getSubtasks()) {
                    addPdfRow(table, sub, font, "  ↳ ", alternate, headerBg);
                    alternate = !alternate;
                }
            }

            doc.add(table);
            
            // Футер
            doc.add(new Paragraph("\n© НейроФлоу Планировщик " + LocalDate.now().getYear())
                .setFont(font)
                .setFontSize(8)
                .setFontColor(new DeviceRgb(140, 143, 161))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20));
            
            doc.close();
            showSuccessDialog("PDF успешно сохранён", file.getName(), file.getAbsolutePath());
        } catch (Exception ex) {
            showErrorDialog("Ошибка экспорта PDF", ex.getMessage());
        }
    }

    private void addPdfRow(Table table, Task t, PdfFont font, String prefix, boolean alternate, DeviceRgb altBg) {
        DeviceRgb textColor = new DeviceRgb(76, 79, 105);
        DeviceRgb bg = alternate ? altBg : new DeviceRgb(255, 255, 255);
        
        // Название
        table.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(prefix + t.getTitle()).setFont(font).setFontSize(9).setFontColor(textColor))
            .setBackgroundColor(bg).setPadding(5));
        
        // Описание (полностью, без обрезания)
        String desc = t.getDescription();
        table.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(desc != null && !desc.isEmpty() ? desc : "-").setFont(font).setFontSize(8).setFontColor(textColor))
            .setBackgroundColor(bg).setPadding(5));
        
        // Дедлайн
        String deadline = t.getDeadline() != null ? t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yy")) : "-";
        table.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(deadline).setFont(font).setFontSize(9).setFontColor(textColor))
            .setBackgroundColor(bg).setPadding(5).setTextAlignment(TextAlignment.CENTER));
        
        // Сложность
        table.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(t.getComplexity() + "/10").setFont(font).setFontSize(9).setFontColor(textColor))
            .setBackgroundColor(bg).setPadding(5).setTextAlignment(TextAlignment.CENTER));
        
        // Приоритет
        String priority = String.format("%.1f", t.getSmartPriority());
        table.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(priority).setFont(font).setFontSize(9).setFontColor(textColor))
            .setBackgroundColor(bg).setPadding(5).setTextAlignment(TextAlignment.CENTER));
        
        // Статус
        String status = t.isArchived() ? "Завершена" : "Активна";
        DeviceRgb statusColor = t.isArchived() ? new DeviceRgb(64, 160, 43) : new DeviceRgb(30, 102, 245);
        table.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(status).setFont(font).setFontSize(9).setFontColor(statusColor))
            .setBackgroundColor(bg).setPadding(5).setTextAlignment(TextAlignment.CENTER));
    }

    private void exportCsv(List<Task> tasks) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        fc.setInitialFileName(generateFileName("csv"));
        File file = fc.showSaveDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            pw.println("Название;Описание;Дедлайн;Сложность;Приоритет;Теги;Статус");
            for (Task t : tasks) {
                writeCsvRow(pw, t, "");
                for (Task sub : t.getSubtasks()) {
                    writeCsvRow(pw, sub, "  ");
                }
            }
            showSuccessDialog("CSV успешно сохранён", file.getName(), file.getAbsolutePath());
        } catch (Exception ex) {
            showErrorDialog("Ошибка экспорта CSV", ex.getMessage());
        }
    }

    private void writeCsvRow(PrintWriter pw, Task t, String prefix) {
        pw.printf("%s%s;%s;%s;%d;%.1f;%s;%s%n",
            prefix, escape(t.getTitle()), escape(t.getDescription()), t.getDeadline(),
            t.getComplexity(), t.getSmartPriority(), escape(t.getTags()),
            t.isArchived() ? "Выполнено" : "Активна");
    }

    private String escape(String s) {
        return s == null ? "" : s.replace(";", ",").replace("\n", " ");
    }
    
    private void exportDocx(List<Task> tasks) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить Word");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word", "*.docx"));
        fc.setInitialFileName(generateFileName("docx"));
        File file = fc.showSaveDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (file == null) return;

        try (XWPFDocument doc = new XWPFDocument(); FileOutputStream fos = new FileOutputStream(file)) {
            // Заголовок
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("НейроФлоу Планировщик");
            titleRun.setBold(true);
            titleRun.setFontSize(24);
            titleRun.setColor("1E66F5");
            titleRun.setFontFamily("Arial");
            
            // Подзаголовок
            XWPFParagraph subtitlePara = doc.createParagraph();
            subtitlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subtitleRun = subtitlePara.createRun();
            subtitleRun.setText("Отчёт по задачам • " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            subtitleRun.setFontSize(12);
            subtitleRun.setColor("6C6F85");
            subtitleRun.setFontFamily("Arial");
            
            // Статистика
            long totalTasks = tasks.size() + tasks.stream().mapToLong(t -> t.getSubtasks().size()).sum();
            long activeTasks = tasks.stream().filter(t -> !t.isArchived()).count() +
                tasks.stream().flatMap(t -> t.getSubtasks().stream()).filter(t -> !t.isArchived()).count();
            long completedTasks = totalTasks - activeTasks;
            
            XWPFParagraph statsPara = doc.createParagraph();
            statsPara.setAlignment(ParagraphAlignment.CENTER);
            statsPara.setSpacingAfter(400);
            XWPFRun statsRun = statsPara.createRun();
            statsRun.setText("Всего: " + totalTasks + " | Активных: " + activeTasks + " | Завершённых: " + completedTasks);
            statsRun.setFontSize(11);
            statsRun.setColor("8C8FA1");
            statsRun.setFontFamily("Arial");
            
            // Таблица
            XWPFTable table = doc.createTable(1, 6);
            table.setWidth("100%");
            
            // Заголовки таблицы
            String[] headers = {"Название", "Описание", "Дедлайн", "Сложность", "Приоритет", "Статус"};
            XWPFTableRow headerRow = table.getRow(0);
            for (int i = 0; i < headers.length; i++) {
                XWPFTableCell cell = headerRow.getCell(i);
                cell.setColor("1E66F5");
                XWPFParagraph para = cell.getParagraphs().get(0);
                para.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun run = para.createRun();
                run.setText(headers[i]);
                run.setBold(true);
                run.setFontSize(10);
                run.setColor("FFFFFF");
                run.setFontFamily("Arial");
            }
            
            // Данные
            for (Task t : tasks) {
                addDocxRow(table, t, "");
                for (Task sub : t.getSubtasks()) {
                    addDocxRow(table, sub, "  ↳ ");
                }
            }
            
            // Футер
            XWPFParagraph footerPara = doc.createParagraph();
            footerPara.setAlignment(ParagraphAlignment.CENTER);
            footerPara.setSpacingBefore(400);
            XWPFRun footerRun = footerPara.createRun();
            footerRun.setText("© НейроФлоу Планировщик " + LocalDate.now().getYear());
            footerRun.setFontSize(9);
            footerRun.setColor("8C8FA1");
            footerRun.setFontFamily("Arial");
            
            doc.write(fos);
            showSuccessDialog("Word успешно сохранён", file.getName(), file.getAbsolutePath());
        } catch (Exception ex) {
            showErrorDialog("Ошибка экспорта Word", ex.getMessage());
        }
    }
    
    private void addDocxRow(XWPFTable table, Task t, String prefix) {
        XWPFTableRow row = table.createRow();
        
        // Название
        setCellText(row.getCell(0), prefix + t.getTitle(), "4C4F69", false);
        
        // Описание
        setCellText(row.getCell(1), t.getDescription() != null ? t.getDescription() : "-", "4C4F69", false);
        
        // Дедлайн
        String deadline = t.getDeadline() != null ? t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yy")) : "-";
        setCellText(row.getCell(2), deadline, "4C4F69", false);
        
        // Сложность
        setCellText(row.getCell(3), t.getComplexity() + "/10", "4C4F69", false);
        
        // Приоритет
        setCellText(row.getCell(4), String.format("%.1f", t.getSmartPriority()), "4C4F69", false);
        
        // Статус
        String status = t.isArchived() ? "Завершена" : "Активна";
        String statusColor = t.isArchived() ? "40A02B" : "1E66F5";
        setCellText(row.getCell(5), status, statusColor, true);
    }
    
    private void setCellText(XWPFTableCell cell, String text, String color, boolean bold) {
        XWPFParagraph para = cell.getParagraphs().get(0);
        para.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(9);
        run.setColor(color);
        run.setBold(bold);
        run.setFontFamily("Arial");
    }
    
    private void exportMarkdown(List<Task> tasks) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить Markdown");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        fc.setInitialFileName(generateFileName("md"));
        File file = fc.showSaveDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            // Заголовок
            pw.println("# 🧠 НейроФлоу Планировщик");
            pw.println();
            pw.println("## Отчёт по задачам");
            pw.println();
            pw.println("**Дата создания:** " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            pw.println();
            
            // Статистика
            long totalTasks = tasks.size() + tasks.stream().mapToLong(t -> t.getSubtasks().size()).sum();
            long activeTasks = tasks.stream().filter(t -> !t.isArchived()).count() +
                tasks.stream().flatMap(t -> t.getSubtasks().stream()).filter(t -> !t.isArchived()).count();
            long completedTasks = totalTasks - activeTasks;
            
            pw.println("### 📊 Статистика");
            pw.println();
            pw.println("| Показатель | Значение |");
            pw.println("|------------|----------|");
            pw.println("| Всего задач | " + totalTasks + " |");
            pw.println("| Активных | " + activeTasks + " |");
            pw.println("| Завершённых | " + completedTasks + " |");
            pw.println();
            
            // Таблица задач
            pw.println("### 📋 Список задач");
            pw.println();
            pw.println("| Название | Описание | Дедлайн | Сложность | Приоритет | Статус |");
            pw.println("|----------|----------|---------|-----------|-----------|--------|");
            
            for (Task t : tasks) {
                writeMarkdownRow(pw, t, "");
                for (Task sub : t.getSubtasks()) {
                    writeMarkdownRow(pw, sub, "↳ ");
                }
            }
            
            pw.println();
            pw.println("---");
            pw.println();
            pw.println("*© НейроФлоу Планировщик " + LocalDate.now().getYear() + "*");
            
            showSuccessDialog("Markdown успешно сохранён", file.getName(), file.getAbsolutePath());
        } catch (Exception ex) {
            showErrorDialog("Ошибка экспорта Markdown", ex.getMessage());
        }
    }
    
    private void writeMarkdownRow(PrintWriter pw, Task t, String prefix) {
        String title = escapeMarkdown(prefix + t.getTitle());
        String desc = escapeMarkdown(t.getDescription() != null ? t.getDescription() : "-");
        String deadline = t.getDeadline() != null ? t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yy")) : "-";
        String complexity = t.getComplexity() + "/10";
        String priority = String.format("%.1f", t.getSmartPriority());
        String status = t.isArchived() ? "✅ Завершена" : "🔵 Активна";
        
        pw.println("| " + title + " | " + desc + " | " + deadline + " | " + complexity + " | " + priority + " | " + status + " |");
    }
    
    private String escapeMarkdown(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }
    
    private String generateFileName(String extension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        return "tasks_report_" + timestamp + "." + extension;
    }

    private void showSuccessDialog(String title, String fileName, String filePath) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Экспорт завершён");
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
        
        // Success icon
        StackPane iconBox = new StackPane();
        iconBox.setMinSize(64, 64);
        iconBox.setMaxSize(64, 64);
        iconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(166,227,161,0.15)" : "rgba(64,160,43,0.1)") + "; -fx-background-radius: 50%;");
        FontIcon successIcon = FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 32);
        successIcon.setIconColor(javafx.scene.paint.Color.web(isDark ? "#a6e3a1" : "#40a02b"));
        iconBox.getChildren().add(successIcon);
        
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        
        Label fileNameLbl = new Label(fileName);
        fileNameLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#a6e3a1" : "#40a02b") + ";");
        
        // File path info
        VBox pathBox = new VBox(4);
        pathBox.setAlignment(Pos.CENTER);
        pathBox.setPadding(new Insets(12));
        pathBox.setStyle("-fx-background-color: " + (isDark ? "rgba(166,227,161,0.08)" : "rgba(64,160,43,0.05)") + "; -fx-background-radius: 10;");
        
        Label pathLabel = new Label("Путь к файлу:");
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        
        Label pathValue = new Label(filePath);
        pathValue.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isDark ? "#bac2de" : "#5c5f77") + ";");
        pathValue.setWrapText(true);
        pathValue.setMaxWidth(350);
        
        pathBox.getChildren().addAll(pathLabel, pathValue);
        
        content.getChildren().addAll(iconBox, titleLbl, fileNameLbl, pathBox);
        dialogPane.setContent(content);
        
        ButtonType okBtn = new ButtonType("Отлично", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().add(okBtn);
        
        Button okButton = (Button) dialogPane.lookupButton(okBtn);
        okButton.setStyle("-fx-background-color: " + (isDark ? "#a6e3a1" : "#40a02b") + "; -fx-text-fill: " + (isDark ? "#1e1e2e" : "#ffffff") + "; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8 24;");
        
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            dialog.initOwner(root.getScene().getWindow());
        }
        dialog.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ошибка экспорта");
        dialog.setHeaderText(null);
        
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().add("styled-alert");
        dialogPane.setPrefWidth(400);
        
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);
        
        // Error icon
        StackPane iconBox = new StackPane();
        iconBox.setMinSize(64, 64);
        iconBox.setMaxSize(64, 64);
        iconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(243,139,168,0.15)" : "rgba(210,15,57,0.1)") + "; -fx-background-radius: 50%;");
        FontIcon errorIcon = FontIcon.of(MaterialDesignA.ALERT_CIRCLE, 32);
        errorIcon.setIconColor(javafx.scene.paint.Color.web(isDark ? "#f38ba8" : "#d20f39"));
        iconBox.getChildren().add(errorIcon);
        
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        
        VBox msgBox = new VBox(4);
        msgBox.setAlignment(Pos.CENTER);
        msgBox.setPadding(new Insets(12));
        msgBox.setStyle("-fx-background-color: " + (isDark ? "rgba(243,139,168,0.08)" : "rgba(210,15,57,0.05)") + "; -fx-background-radius: 10;");
        
        Label msgLabel = new Label(message != null ? message : "Неизвестная ошибка");
        msgLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#f38ba8" : "#d20f39") + ";");
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(320);
        
        msgBox.getChildren().add(msgLabel);
        
        content.getChildren().addAll(iconBox, titleLbl, msgBox);
        dialogPane.setContent(content);
        
        ButtonType okBtn = new ButtonType("Понятно", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().add(okBtn);
        
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            dialog.initOwner(root.getScene().getWindow());
        }
        dialog.showAndWait();
    }
}
