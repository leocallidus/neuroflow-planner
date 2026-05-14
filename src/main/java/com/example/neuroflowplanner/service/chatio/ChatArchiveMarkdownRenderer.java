package com.example.neuroflowplanner.service.chatio;

import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;

final class ChatArchiveMarkdownRenderer {
    private final ChatArchiveRenderSupport support = new ChatArchiveRenderSupport();

    String render(ChatArchiveDocument document) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ИИ-Ассистент — Архив переписок\n\n");
        markdown.append("Экспортировано: ").append(document.exportedAt()).append("\n\n");

        if (document.conversations().isEmpty()) {
            markdown.append("_Нет переписок для экспорта._\n");
            return markdown.toString();
        }

        boolean first = true;
        for (ChatArchiveBundle bundle : document.conversations()) {
            if (!first) {
                markdown.append("\n---\n\n");
            }
            first = false;

            ChatConversation conversation = bundle.conversation();
            markdown.append("## ").append(escape(conversation.getTitle())).append("\n\n");
            markdown.append("- ID: `").append(escapeCode(conversation.getId())).append("`\n");
            markdown.append("- Создано: ").append(valueOrDash(support.formatDateTime(conversation.getCreatedAt()))).append("\n");
            markdown.append("- Обновлено: ").append(valueOrDash(support.formatDateTime(conversation.getUpdatedAt()))).append("\n");
            markdown.append("- Сообщений: ").append(bundle.messages().size()).append("\n\n");

            if (bundle.messages().isEmpty()) {
                markdown.append("_Переписка пуста._\n");
                continue;
            }

            for (ChatMessage message : bundle.messages()) {
                ChatArchiveRenderSupport.ModelTaggedText tagged = support.parseModelTaggedText(message.getContent());
                boolean assistant = "assistant".equalsIgnoreCase(message.getRole());
                String role = assistant ? "Ассистент" : "Пользователь";
                if (assistant && tagged.model() != null && !tagged.model().isBlank()) {
                    role = role + " (" + tagged.model() + ")";
                }

                markdown.append("### ").append(role).append("\n\n");
                markdown.append("*").append(valueOrDash(support.formatDateTime(message.getCreatedAt()))).append("*\n\n");
                String content = support.normalizeMessageText(tagged.content());
                markdown.append(content.isBlank() ? "_(пустое сообщение)_" : content).append("\n\n");
            }
        }

        return markdown.toString();
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\n", " ").replace("\r", "").replace("|", "\\|");
    }

    private String escapeCode(String value) {
        return value == null ? "" : value.replace("`", "'");
    }
}
