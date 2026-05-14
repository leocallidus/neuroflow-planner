package com.example.neuroflowplanner.service.chatio;

import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class ChatArchivePdfRenderer {
    private final ChatArchiveRenderSupport support = new ChatArchiveRenderSupport();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final DeviceRgb titleColor = new DeviceRgb(30, 102, 245);
    private final DeviceRgb metaColor = new DeviceRgb(140, 143, 161);
    private final DeviceRgb userColor = new DeviceRgb(52, 84, 209);
    private final DeviceRgb assistantColor = new DeviceRgb(124, 127, 147);

    void render(File file, ChatArchiveDocument document) throws Exception {
        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {
            PdfRenderStyle style = createPdfRenderStyle();
            doc.setFont(style.bodyFont);

            Paragraph header = new Paragraph("ИИ-Ассистент — Переписки")
                .setFont(style.boldFont)
                .setFontSize(16)
                .setFontColor(titleColor)
                .setTextAlignment(TextAlignment.LEFT)
                .setMarginBottom(6);
            doc.add(header);
            doc.add(new Paragraph("Переписок: " + document.conversations().size() + " | Экспортировано: " +
                    LocalDateTime.now().format(dateTimeFormatter))
                .setFont(style.bodyFont)
                .setFontSize(9)
                .setFontColor(metaColor)
                .setMarginBottom(12));

            boolean first = true;
            for (ChatArchiveBundle bundle : document.conversations()) {
                if (!first) {
                    doc.add(new AreaBreak());
                }
                first = false;
                renderConversation(doc, bundle.conversation(), bundle.messages(), style);
            }
        }
    }

    private void renderConversation(Document doc, ChatConversation conversation, java.util.List<ChatMessage> messages, PdfRenderStyle style) {
        Paragraph title = new Paragraph(conversation.getTitle())
            .setFont(style.boldFont)
            .setFontSize(14)
            .setFontColor(titleColor)
            .setMarginBottom(4);
        doc.add(title);

        String created = support.formatDateTime(conversation.getCreatedAt());
        String updated = support.formatDateTime(conversation.getUpdatedAt());
        if (created != null || updated != null) {
            String meta = "Создано: " + (created != null ? created : "—") +
                " • Обновлено: " + (updated != null ? updated : "—");
            doc.add(new Paragraph(meta)
                .setFont(style.bodyFont)
                .setFontSize(9)
                .setFontColor(metaColor)
                .setMarginBottom(8));
        }

        doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginBottom(10));

        if (messages.isEmpty()) {
            doc.add(new Paragraph("(переписка пуста)")
                .setFont(style.bodyFont)
                .setFontSize(style.bodySize)
                .setItalic()
                .setMarginBottom(8));
            return;
        }

        for (ChatMessage message : messages) {
            boolean assistant = "assistant".equalsIgnoreCase(message.getRole());
            ChatArchiveRenderSupport.ModelTaggedText tagged = support.parseModelTaggedText(message.getContent());
            String roleLabel = assistant ? "Ассистент" : "Пользователь";
            if (assistant && tagged.model() != null && !tagged.model().isBlank()) {
                roleLabel = roleLabel + " (" + tagged.model() + ")";
            }

            doc.add(new Paragraph(roleLabel)
                .setFont(style.boldFont)
                .setFontSize(11)
                .setFontColor(assistant ? assistantColor : userColor)
                .setMarginBottom(2));

            String timestamp = support.formatDateTime(message.getCreatedAt());
            if (timestamp != null) {
                doc.add(new Paragraph(timestamp)
                    .setFont(style.bodyFont)
                    .setFontSize(8)
                    .setFontColor(metaColor)
                    .setMarginBottom(4));
            }

            String content = support.normalizeMessageText(tagged.content());
            doc.add(new Paragraph(content.isBlank() ? "(пустое сообщение)" : content)
                .setFont(style.bodyFont)
                .setFontSize(style.bodySize)
                .setMarginBottom(8));
        }
    }

    private PdfRenderStyle createPdfRenderStyle() {
        PdfFont bodyFont = loadFont(
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "C:/Windows/Fonts/arial.ttf"
        );
        if (bodyFont == null) {
            bodyFont = loadBuiltinFont();
        }
        PdfFont boldFont = bodyFont;
        return new PdfRenderStyle(bodyFont, boldFont, 10f);
    }

    private PdfFont loadFont(String... candidates) {
        for (String path : candidates) {
            try {
                return PdfFontFactory.createFont(path, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private PdfFont loadBuiltinFont() {
        try {
            return PdfFontFactory.createFont("Helvetica", PdfEncodings.CP1252, PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize PDF font", e);
        }
    }

    private record PdfRenderStyle(PdfFont bodyFont, PdfFont boldFont, float bodySize) {
    }
}
