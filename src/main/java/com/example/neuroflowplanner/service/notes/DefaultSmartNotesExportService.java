package com.example.neuroflowplanner.service.notes;

import com.example.neuroflowplanner.util.LinkParser;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.ListNumberingType;
import com.itextpdf.layout.properties.ListSymbolAlignment;
import com.itextpdf.layout.properties.ListSymbolPosition;
import com.itextpdf.layout.properties.Property;

import java.io.File;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultSmartNotesExportService implements SmartNotesExportService {
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\s*\\d+\\.\\s+(.+)$");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.+)$");
    private static final Pattern HR_PATTERN = Pattern.compile("^\\s*([*_\\-])\\1\\1+\\s*$");

    @Override
    public void exportNoteToPdf(File file, String noteTitle, String noteContent) throws Exception {
        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {
            PdfRenderStyle style = createPdfRenderStyle();
            doc.setMargins(36, 36, 36, 36);

            Paragraph title = new Paragraph();
            appendInlineMarkdown(title, safe(noteTitle), style, InlineStyle.bold(), style.titleSize, style.titleColor);
            title.setMarginBottom(4);
            doc.add(title);

            String createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            Paragraph meta = new Paragraph();
            appendInlineMarkdown(meta, "Экспортировано: " + createdAt, style, InlineStyle.normal(), style.metaSize, style.metaColor);
            meta.setMarginBottom(12);
            doc.add(meta);

            String content = safe(noteContent);
            if (content.isBlank()) {
                Paragraph empty = new Paragraph();
                appendInlineMarkdown(empty, "(пустая заметка)", style, InlineStyle.italic(), style.bodySize, style.textColor);
                doc.add(empty);
            } else {
                renderMarkdownToPdf(doc, content, style);
            }
        }
    }

    @Override
    public void exportAllNotesToPdf(File file, List<NoteExport> notes) throws Exception {
        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {
            PdfRenderStyle style = createPdfRenderStyle();
            doc.setMargins(36, 36, 36, 36);

            Paragraph headerTitle = new Paragraph();
            appendInlineMarkdown(headerTitle, "Экспорт заметок", style, InlineStyle.bold(), style.titleSize, style.titleColor);
            headerTitle.setMarginBottom(4);
            doc.add(headerTitle);

            String createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            int count = notes == null ? 0 : notes.size();
            Paragraph meta = new Paragraph();
            appendInlineMarkdown(
                meta,
                "Количество заметок: " + count + " | Экспортировано: " + createdAt,
                style,
                InlineStyle.normal(),
                style.metaSize,
                style.metaColor
            );
            meta.setMarginBottom(12);
            doc.add(meta);

            if (notes == null) {
                return;
            }

            for (int i = 0; i < notes.size(); i++) {
                NoteExport note = notes.get(i);
                if (i > 0) {
                    SolidLine line = new SolidLine(1f);
                    line.setColor(style.separatorColor);
                    LineSeparator separator = new LineSeparator(line);
                    separator.setMarginTop(10);
                    separator.setMarginBottom(10);
                    doc.add(separator);
                }

                String titleText = note == null ? "" : safe(note.title());
                String contentText = note == null ? "" : safe(note.content());

                Paragraph noteTitle = new Paragraph();
                appendInlineMarkdown(noteTitle, titleText, style, InlineStyle.bold(), style.sectionTitleSize, style.titleColor);
                noteTitle.setMarginTop(i == 0 ? 0 : 4);
                noteTitle.setMarginBottom(6);
                doc.add(noteTitle);

                if (contentText.isBlank()) {
                    Paragraph empty = new Paragraph();
                    appendInlineMarkdown(empty, "(пустая заметка)", style, InlineStyle.italic(), style.bodySize, style.textColor);
                    doc.add(empty);
                } else {
                    renderMarkdownToPdf(doc, contentText, style);
                }
            }
        }
    }

    @Override
    public void exportNoteToMarkdown(File file, String noteTitle, String noteContent) throws Exception {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("# " + safe(noteTitle) + "\n\n");
            writer.write("*Экспортировано: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "*\n\n");
            writer.write(safe(noteContent));
        }
    }

    @Override
    public String renderPreviewHtml(String markdown, String searchQuery, boolean darkTheme) {
        String html = convertMarkdownToHtml(markdown);
        html = applySearchHighlight(html, searchQuery);
        return getNotesHtmlTemplate(html, darkTheme);
    }

    @Override
    public String sanitizeFileName(String name, String fallback) {
        String safeFallback = (fallback == null || fallback.isBlank()) ? "note" : fallback;
        String safe = name == null ? safeFallback : name.replaceAll("[^a-zA-Z0-9а-яА-Я _-]", "").trim();
        return safe.isEmpty() ? safeFallback : safe;
    }

    private PdfRenderStyle createPdfRenderStyle() {
        PdfFont bodyFont = loadPdfFontRegular();
        PdfFont boldFont = loadPdfFontBold(bodyFont);
        PdfFont monoFont = loadPdfMonoFont(bodyFont);
        PdfFont emojiFont = loadEmojiFont();
        return new PdfRenderStyle(bodyFont, boldFont, monoFont, emojiFont);
    }

    private void renderMarkdownToPdf(Document doc, String markdown, PdfRenderStyle style) {
        if (markdown == null) {
            return;
        }

        String normalized = markdown.replace("\r\n", "\n").replace("\r", "\n");
        List<String> lines = Arrays.asList(normalized.split("\n", -1));
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (line.trim().isEmpty()) {
                index++;
                continue;
            }

            if (isCodeFence(line)) {
                int end = findCodeFenceEnd(lines, index + 1);
                String code = joinLines(lines, index + 1, end);
                doc.add(buildCodeBlock(code, style));
                index = end + 1;
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                int level = headingMatcher.group(1).length();
                String headingText = headingMatcher.group(2).trim();
                doc.add(buildHeadingParagraph(headingText, level, style));
                index++;
                continue;
            }

            if (HR_PATTERN.matcher(line).matches()) {
                SolidLine ruleLine = new SolidLine(1f);
                ruleLine.setColor(style.separatorColor);
                LineSeparator separator = new LineSeparator(ruleLine);
                separator.setMarginTop(8);
                separator.setMarginBottom(8);
                doc.add(separator);
                index++;
                continue;
            }

            if (isBlockquoteLine(line)) {
                StringBuilder quoteBuffer = new StringBuilder();
                while (index < lines.size() && isBlockquoteLine(lines.get(index))) {
                    String raw = lines.get(index).trim();
                    String stripped = raw.replaceFirst("^>\\s?", "");
                    quoteBuffer.append(stripped).append("\n");
                    index++;
                }
                doc.add(buildBlockquote(quoteBuffer.toString().trim(), style));
                continue;
            }

            ListType listType = getListType(line);
            if (listType != null) {
                List<String> items = new ArrayList<>();
                StringBuilder current = new StringBuilder(stripListMarker(line, listType));
                index++;
                while (index < lines.size()) {
                    String next = lines.get(index);
                    if (next.trim().isEmpty()) {
                        items.add(current.toString());
                        current = null;
                        index++;
                        break;
                    }

                    ListType nextType = getListType(next);
                    if (nextType == listType) {
                        items.add(current.toString());
                        current = new StringBuilder(stripListMarker(next, listType));
                        index++;
                        continue;
                    }

                    if (nextType != null || isBlockStart(next)) {
                        break;
                    }

                    current.append(" ").append(next.trim());
                    index++;
                }
                if (current != null) {
                    items.add(current.toString());
                }
                doc.add(buildList(items, listType, style));
                continue;
            }

            StringBuilder paragraphBuffer = new StringBuilder(line.trim());
            index++;
            while (index < lines.size()) {
                String next = lines.get(index);
                if (next.trim().isEmpty()) {
                    index++;
                    break;
                }
                if (isBlockStart(next) || getListType(next) != null) {
                    break;
                }
                paragraphBuffer.append(" ").append(next.trim());
                index++;
            }
            doc.add(buildParagraph(paragraphBuffer.toString(), style));
        }
    }

    private Paragraph buildHeadingParagraph(String text, int level, PdfRenderStyle style) {
        float size = switch (level) {
            case 1 -> style.h1Size;
            case 2 -> style.h2Size;
            case 3 -> style.h3Size;
            case 4 -> style.h4Size;
            case 5 -> style.h5Size;
            default -> style.h6Size;
        };

        Paragraph heading = new Paragraph();
        appendInlineMarkdown(heading, text, style, InlineStyle.bold(), size, style.headingColor);
        heading.setMarginTop(10);
        heading.setMarginBottom(6);
        return heading;
    }

    private Paragraph buildParagraph(String text, PdfRenderStyle style) {
        Paragraph paragraph = new Paragraph();
        appendInlineMarkdown(paragraph, text, style, InlineStyle.normal(), style.bodySize, style.textColor);
        paragraph.setMarginBottom(6);
        return paragraph;
    }

    private Div buildBlockquote(String text, PdfRenderStyle style) {
        Div quote = new Div();
        quote.setBorderLeft(new SolidBorder(style.quoteBorderColor, 2f));
        quote.setBackgroundColor(style.quoteBackgroundColor);
        quote.setPaddingLeft(10);
        quote.setPaddingTop(6);
        quote.setPaddingBottom(6);
        quote.setMarginTop(6);
        quote.setMarginBottom(8);

        if (text == null || text.isBlank()) {
            Paragraph empty = new Paragraph();
            appendInlineMarkdown(empty, " ", style, InlineStyle.normal(), style.bodySize, style.textColor);
            quote.add(empty);
            return quote;
        }

        List<String> paragraphs = splitParagraphs(text);
        for (String paragraphText : paragraphs) {
            Paragraph paragraph = new Paragraph();
            appendInlineMarkdown(paragraph, paragraphText, style, InlineStyle.normal(), style.bodySize, style.textColor);
            paragraph.setMarginBottom(4);
            quote.add(paragraph);
        }
        return quote;
    }

    private Div buildCodeBlock(String code, PdfRenderStyle style) {
        String content = preserveCodeIndentation(code == null ? "" : code);
        Div container = new Div();
        container.setBackgroundColor(style.codeBackgroundColor);
        container.setBorder(new SolidBorder(style.separatorColor, 0.5f));
        container.setPaddingLeft(8);
        container.setPaddingRight(8);
        container.setPaddingTop(6);
        container.setPaddingBottom(6);
        container.setMarginTop(6);
        container.setMarginBottom(8);

        Paragraph paragraph = new Paragraph(content);
        paragraph.setFont(style.monoFont);
        paragraph.setFontSize(style.codeBlockSize);
        paragraph.setFontColor(style.codeTextColor);
        paragraph.setMarginTop(0);
        paragraph.setMarginBottom(0);
        paragraph.setMarginLeft(0);
        paragraph.setMarginRight(0);
        paragraph.setMultipliedLeading(1.2f);
        paragraph.setProperty(Property.NO_SOFT_WRAP_INLINE, true);
        container.add(paragraph);
        return container;
    }

    private com.itextpdf.layout.element.List buildList(List<String> items, ListType listType, PdfRenderStyle style) {
        com.itextpdf.layout.element.List list = listType == ListType.ORDERED
            ? new com.itextpdf.layout.element.List(ListNumberingType.DECIMAL)
            : new com.itextpdf.layout.element.List();
        if (listType == ListType.UNORDERED) {
            list.setListSymbol("•");
        }
        list.setListSymbolAlignment(ListSymbolAlignment.LEFT);
        list.setProperty(Property.LIST_SYMBOL_POSITION, ListSymbolPosition.OUTSIDE);
        list.setPostSymbolText(" ");
        list.setMarginTop(6);
        list.setMarginBottom(8);
        list.setSymbolIndent(16);
        list.setMarginLeft(24);
        list.setMarginRight(0);

        for (String itemText : items) {
            ListItem item = new ListItem();
            Paragraph paragraph = new Paragraph();
            appendInlineMarkdown(paragraph, itemText, style, InlineStyle.normal(), style.bodySize, style.textColor);
            paragraph.setMarginTop(0);
            paragraph.setMarginBottom(0);
            paragraph.setMarginLeft(0);
            paragraph.setMarginRight(0);
            item.add(paragraph);
            list.add(item);
        }
        return list;
    }

    private void appendInlineMarkdown(Paragraph paragraph, String text, PdfRenderStyle style, InlineStyle baseStyle, float fontSize, DeviceRgb color) {
        if (text == null || text.isEmpty()) {
            return;
        }

        int index = 0;
        StringBuilder buffer = new StringBuilder();
        while (index < text.length()) {
            if (text.startsWith("`", index)) {
                int end = text.indexOf("`", index + 1);
                if (end > index + 1) {
                    flushInlineBuffer(paragraph, buffer, style, baseStyle, fontSize, color);
                    String code = text.substring(index + 1, end);
                    InlineStyle codeStyle = baseStyle.asCode();
                    addStyledText(paragraph, code, style, codeStyle, fontSize, color);
                    index = end + 1;
                    continue;
                }
            }

            if (text.startsWith("[", index)) {
                int close = text.indexOf("]", index);
                if (close > index && close + 1 < text.length() && text.charAt(close + 1) == '(') {
                    int end = text.indexOf(")", close + 2);
                    if (end > close + 2) {
                        String label = text.substring(index + 1, close);
                        String url = text.substring(close + 2, end);
                        flushInlineBuffer(paragraph, buffer, style, baseStyle, fontSize, color);
                        appendInlineMarkdown(paragraph, label, style, baseStyle.withLink(url), fontSize, color);
                        index = end + 1;
                        continue;
                    }
                }
            }

            if (text.startsWith("**", index) || text.startsWith("__", index)) {
                String token = text.startsWith("**", index) ? "**" : "__";
                int end = text.indexOf(token, index + 2);
                if (end > index + 2) {
                    flushInlineBuffer(paragraph, buffer, style, baseStyle, fontSize, color);
                    String boldText = text.substring(index + 2, end);
                    appendInlineMarkdown(paragraph, boldText, style, baseStyle.withBold(), fontSize, color);
                    index = end + 2;
                    continue;
                }
            }

            if (text.startsWith("*", index) || text.startsWith("_", index)) {
                String token = text.startsWith("*", index) ? "*" : "_";
                int end = text.indexOf(token, index + 1);
                if (end > index + 1) {
                    flushInlineBuffer(paragraph, buffer, style, baseStyle, fontSize, color);
                    String italicText = text.substring(index + 1, end);
                    appendInlineMarkdown(paragraph, italicText, style, baseStyle.withItalic(), fontSize, color);
                    index = end + 1;
                    continue;
                }
            }

            buffer.append(text.charAt(index));
            index++;
        }
        flushInlineBuffer(paragraph, buffer, style, baseStyle, fontSize, color);
    }

    private void flushInlineBuffer(Paragraph paragraph, StringBuilder buffer, PdfRenderStyle style, InlineStyle baseStyle, float fontSize, DeviceRgb color) {
        if (buffer.length() == 0) {
            return;
        }
        String chunk = buffer.toString();
        buffer.setLength(0);
        addStyledText(paragraph, chunk, style, baseStyle, fontSize, color);
    }

    private void addStyledText(Paragraph paragraph, String text, PdfRenderStyle style, InlineStyle inlineStyle, float fontSize, DeviceRgb color) {
        if (text == null || text.isEmpty()) {
            return;
        }

        for (EmojiSegment segment : splitEmojiSegments(text, style.emojiFont != null)) {
            PdfFont font = resolveFont(style, inlineStyle, segment.isEmoji);
            Text chunk = inlineStyle.linkUrl == null ? new Text(segment.text) : new Link(segment.text, PdfAction.createURI(inlineStyle.linkUrl));

            chunk.setFont(font);
            chunk.setFontSize(inlineStyle.code ? Math.max(9f, fontSize - 1f) : fontSize);
            chunk.setFontColor(inlineStyle.code ? style.codeTextColor : color);

            if (inlineStyle.italic && !inlineStyle.code) {
                chunk.setItalic();
            }
            if (inlineStyle.linkUrl != null) {
                chunk.setFontColor(style.linkColor);
                chunk.setUnderline();
            }
            if (inlineStyle.code) {
                chunk.setBackgroundColor(style.codeBackgroundColor);
            }

            paragraph.add(chunk);
        }
    }

    private PdfFont resolveFont(PdfRenderStyle style, InlineStyle inlineStyle, boolean isEmoji) {
        if (isEmoji && style.emojiFont != null) {
            return style.emojiFont;
        }
        if (inlineStyle.code) {
            return style.monoFont;
        }
        if (inlineStyle.bold) {
            return style.boldFont;
        }
        return style.bodyFont;
    }

    private boolean isCodeFence(String line) {
        return line.trim().startsWith("```");
    }

    private int findCodeFenceEnd(List<String> lines, int start) {
        int index = start;
        while (index < lines.size()) {
            if (isCodeFence(lines.get(index))) {
                return index;
            }
            index++;
        }
        return lines.size();
    }

    private String joinLines(List<String> lines, int start, int end) {
        if (start >= end || start >= lines.size()) {
            return "";
        }
        int safeEnd = Math.min(end, lines.size());
        return String.join("\n", lines.subList(start, safeEnd));
    }

    private String preserveCodeIndentation(String code) {
        String normalized = code.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int idx = 0;
            while (idx < line.length()) {
                char ch = line.charAt(idx);
                if (ch == ' ') {
                    output.append('\u00A0');
                    idx++;
                    continue;
                }
                if (ch == '\t') {
                    output.append('\u00A0').append('\u00A0').append('\u00A0').append('\u00A0');
                    idx++;
                    continue;
                }
                break;
            }
            output.append(line.substring(idx));
            if (i < lines.length - 1) {
                output.append('\n');
            }
        }
        return output.toString();
    }

    private boolean isBlockquoteLine(String line) {
        return line.trim().startsWith(">");
    }

    private boolean isBlockStart(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (isCodeFence(trimmed)) {
            return true;
        }
        if (HEADING_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        if (HR_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        return trimmed.startsWith(">");
    }

    private ListType getListType(String line) {
        if (UNORDERED_LIST_PATTERN.matcher(line).matches()) {
            return ListType.UNORDERED;
        }
        if (ORDERED_LIST_PATTERN.matcher(line).matches()) {
            return ListType.ORDERED;
        }
        return null;
    }

    private String stripListMarker(String line, ListType type) {
        Matcher matcher = (type == ListType.ORDERED ? ORDERED_LIST_PATTERN : UNORDERED_LIST_PATTERN).matcher(line);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return line.trim();
    }

    private List<String> splitParagraphs(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = normalized.split("\\n\\s*\\n");
        List<String> paragraphs = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed.replace("\n", " "));
            }
        }
        return paragraphs.isEmpty() ? List.of(" ") : paragraphs;
    }

    private PdfFont loadPdfFontRegular() {
        String[] fontPaths = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
            "/usr/share/fonts/noto/NotoSans-Regular.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "/System/Library/Fonts/Helvetica.ttc"
        };
        PdfFont font = tryLoadFont(fontPaths, PdfEncodings.IDENTITY_H);
        if (font != null) {
            return font;
        }
        try {
            return PdfFontFactory.createFont("Helvetica", PdfEncodings.CP1252, PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось загрузить шрифт для PDF", ex);
        }
    }

    private PdfFont loadPdfFontBold(PdfFont fallback) {
        String[] fontPaths = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
            "/usr/share/fonts/noto/NotoSans-Bold.ttf",
            "C:/Windows/Fonts/arialbd.ttf",
            "/System/Library/Fonts/Helvetica.ttc"
        };
        PdfFont font = tryLoadFont(fontPaths, PdfEncodings.IDENTITY_H);
        return font != null ? font : fallback;
    }

    private PdfFont loadPdfMonoFont(PdfFont fallback) {
        String[] fontPaths = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
            "/usr/share/fonts/TTF/DejaVuSansMono.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
            "/usr/share/fonts/truetype/jetbrains/JetBrainsMono-Regular.ttf",
            "C:/Windows/Fonts/consola.ttf",
            "/System/Library/Fonts/Menlo.ttc"
        };
        PdfFont font = tryLoadFont(fontPaths, PdfEncodings.IDENTITY_H);
        return font != null ? font : fallback;
    }

    private PdfFont loadEmojiFont() {
        String[] fontPaths = {
            "/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf",
            "/usr/share/fonts/noto/NotoColorEmoji.ttf",
            "/usr/share/fonts/noto/NotoEmoji-Regular.ttf",
            "/usr/share/fonts/truetype/noto/NotoEmoji-Regular.ttf",
            "C:/Windows/Fonts/seguiemj.ttf",
            "/System/Library/Fonts/Apple Color Emoji.ttc"
        };
        return tryLoadFont(fontPaths, PdfEncodings.IDENTITY_H);
    }

    private PdfFont tryLoadFont(String[] paths, String encoding) {
        for (String path : paths) {
            try {
                File fontFile = new File(path);
                if (fontFile.exists()) {
                    return PdfFontFactory.createFont(path, encoding, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        Map<String, String> wikiLinks = new HashMap<>();
        String html = replaceWikiLinksWithPlaceholders(markdown, wikiLinks);

        html = html.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

        html = html.replaceAll("(?s)```(\\w*)\\n(.+?)```", "<pre><code>$2</code></pre>");
        html = html.replaceAll("(?s)```(.+?)```", "<pre><code>$1</code></pre>");

        html = html.replaceAll("(?m)^#### (.+)$", "<h4>$1</h4>");
        html = html.replaceAll("(?m)^### (.+)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^## (.+)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^# (.+)$", "<h1>$1</h1>");

        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__(.+?)__", "<strong>$1</strong>");

        html = html.replaceAll("(?<!\\*)\\*([^*]+?)\\*(?!\\*)", "<em>$1</em>");
        html = html.replaceAll("(?<!_)_([^_]+?)_(?!_)", "<em>$1</em>");

        html = html.replaceAll("`([^`]+?)`", "<code class=\"inline\">$1</code>");

        html = html.replaceAll("(?m)^> (.+)$", "<blockquote>$1</blockquote>");

        html = html.replaceAll("(?m)^---$", "<hr>");

        html = html.replaceAll("(?m)^- (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\* (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\d+\\. (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?s)(<li>.*?</li>\\s*)+", "<ul>$0</ul>");

        html = html.replaceAll("\\[(.+?)]\\((https?://[^\\s)]+)\\)", "<a href=\"$2\">$1</a>");

        html = html.replace("\n\n", "</p><p>");
        html = html.replace("\n", "<br>");
        html = "<p>" + html + "</p>";

        html = html.replace("<p></p>", "");
        html = html.replace("<p><br></p>", "");
        html = replacePlaceholders(html, wikiLinks);

        return html;
    }

    private String replaceWikiLinksWithPlaceholders(String markdown, Map<String, String> placeholders) {
        Matcher matcher = LinkParser.WIKI_LINK_PATTERN.matcher(markdown);
        StringBuffer buffer = new StringBuffer();
        int index = 0;
        while (matcher.find()) {
            String raw = matcher.group(1);
            LinkParser.LinkTarget target = LinkParser.parse(raw);
            String placeholder = "NFPLINKTOKEN" + index++ + "X";
            placeholders.put(placeholder, buildWikiAnchor(target));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replacePlaceholders(String html, Map<String, String> placeholders) {
        String output = html;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace(entry.getKey(), entry.getValue());
        }
        return output;
    }

    private String buildWikiAnchor(LinkParser.LinkTarget target) {
        if (target == null) {
            return "";
        }
        String label = escapeHtml(target.getRaw());
        String linkTarget = target.getTarget();
        String encoded = URLEncoder.encode(linkTarget == null ? "" : linkTarget, StandardCharsets.UTF_8);
        String href = target.getType() == LinkParser.LinkType.TASK ? "nfp-task:" + encoded : "nfp-note:" + encoded;
        return "<a href=\"" + href + "\" class=\"notes-wiki-link\">" + label + "</a>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String applySearchHighlight(String html, String query) {
        String normalizedQuery = normalizeQuery(query);
        if (html == null || html.isEmpty() || normalizedQuery.isEmpty()) {
            return html;
        }

        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < html.length()) {
            int tagStart = html.indexOf('<', index);
            if (tagStart == -1) {
                result.append(highlightPlainText(html.substring(index), normalizedQuery));
                break;
            }
            if (tagStart > index) {
                result.append(highlightPlainText(html.substring(index, tagStart), normalizedQuery));
            }
            int tagEnd = html.indexOf('>', tagStart);
            if (tagEnd == -1) {
                result.append(html.substring(tagStart));
                break;
            }
            result.append(html, tagStart, tagEnd + 1);
            index = tagEnd + 1;
        }
        return result.toString();
    }

    private String highlightPlainText(String text, String normalizedQuery) {
        if (text.isEmpty()) {
            return text;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int from = 0;
        int match;
        StringBuilder result = new StringBuilder();
        while ((match = lower.indexOf(normalizedQuery, from)) >= 0) {
            if (match > from) {
                result.append(text, from, match);
            }
            result.append("<mark class=\"notes-search-mark\">")
                .append(text, match, match + normalizedQuery.length())
                .append("</mark>");
            from = match + normalizedQuery.length();
        }
        if (from < text.length()) {
            result.append(text.substring(from));
        }
        return result.toString();
    }

    private String getNotesHtmlTemplate(String content, boolean darkTheme) {
        String bgColor = darkTheme ? "#313244" : "#ccd0da";
        String textColor = darkTheme ? "#cdd6f4" : "#4c4f69";
        String headingColor = darkTheme ? "#89b4fa" : "#1e66f5";
        String strongColor = darkTheme ? "#f9e2af" : "#df8e1d";
        String codeColor = darkTheme ? "#a6e3a1" : "#40a02b";
        String codeBg = darkTheme ? "#1e1e2e" : "#e6e9ef";
        String quoteBorder = darkTheme ? "#585b70" : "#9ca0b0";
        String quoteBg = darkTheme ? "rgba(255, 255, 255, 0.06)" : "rgba(76, 79, 105, 0.08)";
        String linkColor = darkTheme ? "#cba6f7" : "#8839ef";
        String markBg = darkTheme ? "rgba(249, 226, 175, 0.25)" : "rgba(223, 142, 29, 0.25)";
        String markColor = darkTheme ? "#f9e2af" : "#df8e1d";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: "PT Root UI", "Segoe UI", system-ui, sans-serif;
                    font-size: 13px;
                    line-height: 1.6;
                    color: %s;
                    background-color: %s;
                    padding: 12px 14px;
                    word-wrap: break-word;
                }
                h1, h2, h3, h4 {
                    color: %s;
                    margin: 10px 0 6px 0;
                    font-weight: 600;
                }
                h1 { font-size: 18px; }
                h2 { font-size: 16px; }
                h3 { font-size: 14px; }
                h4 { font-size: 13px; }
                p { margin: 6px 0; }
                strong { color: %s; font-weight: 600; }
                em { font-style: italic; }
                a { color: %s; text-decoration: none; }
                a:hover { text-decoration: underline; }
                a.notes-wiki-link { font-weight: 600; }
                code.inline {
                    background-color: %s;
                    color: %s;
                    padding: 1px 5px;
                    border-radius: 4px;
                    font-family: "JetBrains Mono", "Consolas", monospace;
                    font-size: 12px;
                }
                pre {
                    background-color: %s;
                    border-radius: 8px;
                    padding: 12px;
                    margin: 10px 0;
                    overflow-x: auto;
                }
                pre code {
                    color: %s;
                    font-family: "JetBrains Mono", "Consolas", monospace;
                    font-size: 12px;
                    white-space: pre-wrap;
                }
                ul, ol {
                    margin: 6px 0 6px 18px;
                }
                li { margin: 4px 0; }
                blockquote {
                    margin: 8px 0;
                    padding: 6px 10px;
                    border-left: 3px solid %s;
                    background: %s;
                }
                hr {
                    border: none;
                    border-top: 1px solid %s;
                    margin: 12px 0;
                }
                mark.notes-search-mark {
                    background: %s;
                    color: %s;
                    padding: 0 2px;
                    border-radius: 3px;
                }
            </style>
            </head>
            <body>%s</body>
            </html>
            """, textColor, bgColor, headingColor, strongColor, linkColor, codeBg, codeColor, codeBg, codeColor, quoteBorder, quoteBg, quoteBorder, markBg, markColor, content);
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private static final class PdfRenderStyle {
        private final PdfFont bodyFont;
        private final PdfFont boldFont;
        private final PdfFont monoFont;
        private final PdfFont emojiFont;
        private final DeviceRgb titleColor = new DeviceRgb(30, 102, 245);
        private final DeviceRgb headingColor = new DeviceRgb(30, 102, 245);
        private final DeviceRgb textColor = new DeviceRgb(76, 79, 105);
        private final DeviceRgb metaColor = new DeviceRgb(140, 143, 161);
        private final DeviceRgb linkColor = new DeviceRgb(136, 57, 239);
        private final DeviceRgb codeTextColor = new DeviceRgb(40, 160, 43);
        private final DeviceRgb codeBackgroundColor = new DeviceRgb(230, 233, 239);
        private final DeviceRgb quoteBorderColor = new DeviceRgb(156, 160, 176);
        private final DeviceRgb quoteBackgroundColor = new DeviceRgb(239, 241, 245);
        private final DeviceRgb separatorColor = new DeviceRgb(188, 192, 204);
        private final float bodySize = 11f;
        private final float metaSize = 10f;
        private final float titleSize = 18f;
        private final float sectionTitleSize = 14f;
        private final float h1Size = 20f;
        private final float h2Size = 18f;
        private final float h3Size = 16f;
        private final float h4Size = 14f;
        private final float h5Size = 13f;
        private final float h6Size = 12f;
        private final float codeBlockSize = 10.5f;

        private PdfRenderStyle(PdfFont bodyFont, PdfFont boldFont, PdfFont monoFont, PdfFont emojiFont) {
            this.bodyFont = bodyFont;
            this.boldFont = boldFont;
            this.monoFont = monoFont;
            this.emojiFont = emojiFont;
        }
    }

    private static final class InlineStyle {
        private final boolean bold;
        private final boolean italic;
        private final boolean code;
        private final String linkUrl;

        private InlineStyle(boolean bold, boolean italic, boolean code, String linkUrl) {
            this.bold = bold;
            this.italic = italic;
            this.code = code;
            this.linkUrl = linkUrl;
        }

        private static InlineStyle normal() {
            return new InlineStyle(false, false, false, null);
        }

        private static InlineStyle bold() {
            return new InlineStyle(true, false, false, null);
        }

        private static InlineStyle italic() {
            return new InlineStyle(false, true, false, null);
        }

        private InlineStyle withBold() {
            return new InlineStyle(true, italic, code, linkUrl);
        }

        private InlineStyle withItalic() {
            return new InlineStyle(bold, true, code, linkUrl);
        }

        private InlineStyle withLink(String url) {
            return new InlineStyle(bold, italic, code, url);
        }

        private InlineStyle asCode() {
            return new InlineStyle(false, false, true, linkUrl);
        }
    }

    private enum ListType {
        ORDERED,
        UNORDERED
    }

    private static final class EmojiSegment {
        private final String text;
        private final boolean isEmoji;

        private EmojiSegment(String text, boolean isEmoji) {
            this.text = text;
            this.isEmoji = isEmoji;
        }
    }

    private List<EmojiSegment> splitEmojiSegments(String text, boolean enableEmoji) {
        List<EmojiSegment> segments = new ArrayList<>();
        if (!enableEmoji || text == null || text.isEmpty()) {
            segments.add(new EmojiSegment(text == null ? "" : text, false));
            return segments;
        }

        StringBuilder buffer = new StringBuilder();
        boolean currentEmoji = false;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            boolean isEmoji = isEmojiCodePoint(codePoint);
            if (buffer.length() == 0) {
                currentEmoji = isEmoji;
            }
            if (isEmoji != currentEmoji) {
                segments.add(new EmojiSegment(buffer.toString(), currentEmoji));
                buffer.setLength(0);
                currentEmoji = isEmoji;
            }
            buffer.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }
        if (buffer.length() > 0) {
            segments.add(new EmojiSegment(buffer.toString(), currentEmoji));
        }
        return segments;
    }

    private boolean isEmojiCodePoint(int codePoint) {
        return (codePoint >= 0x1F300 && codePoint <= 0x1FAFF)
            || (codePoint >= 0x1F600 && codePoint <= 0x1F64F)
            || (codePoint >= 0x1F680 && codePoint <= 0x1F6FF)
            || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF)
            || (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF)
            || (codePoint >= 0x2600 && codePoint <= 0x26FF)
            || (codePoint >= 0x2700 && codePoint <= 0x27BF)
            || codePoint == 0x200D
            || codePoint == 0x20E3
            || (codePoint >= 0xFE00 && codePoint <= 0xFE0F);
    }
}
