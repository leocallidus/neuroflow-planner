package com.example.neuroflowplanner.service.chatio;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.ChatContextState;
import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultChatArchiveExportService implements ChatArchiveExportService {
    private static final int SCHEMA_VERSION = 1;

    private final DatabaseManager db;
    private final ChatArchiveJsonCodec jsonCodec;
    private final ChatArchiveMarkdownRenderer markdownRenderer;
    private final ChatArchivePdfRenderer pdfRenderer;

    public DefaultChatArchiveExportService() {
        this(DatabaseManager.getInstance(), new ChatArchiveJsonCodec(), new ChatArchiveMarkdownRenderer(), new ChatArchivePdfRenderer());
    }

    DefaultChatArchiveExportService(
        DatabaseManager db,
        ChatArchiveJsonCodec jsonCodec,
        ChatArchiveMarkdownRenderer markdownRenderer,
        ChatArchivePdfRenderer pdfRenderer
    ) {
        this.db = db;
        this.jsonCodec = jsonCodec;
        this.markdownRenderer = markdownRenderer;
        this.pdfRenderer = pdfRenderer;
    }

    @Override
    public ChatArchiveDocument buildConversationArchive(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Conversation id is required");
        }
        ChatConversation conversation = db.loadChatConversation(conversationId.trim());
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }
        return new ChatArchiveDocument(
            "chat-archive",
            SCHEMA_VERSION,
            LocalDateTime.now().toString(),
            sourceMetadata("single"),
            List.of(loadBundle(conversation))
        );
    }

    @Override
    public ChatArchiveDocument buildAllConversationsArchive() {
        List<ChatConversation> conversations = db.loadChatConversations();
        if (conversations.isEmpty()) {
            throw new IllegalStateException("Нет переписок для экспорта.");
        }
        List<ChatArchiveBundle> bundles = new ArrayList<>();
        for (ChatConversation conversation : conversations) {
            bundles.add(loadBundle(conversation));
        }
        return new ChatArchiveDocument(
            "chat-archive",
            SCHEMA_VERSION,
            LocalDateTime.now().toString(),
            sourceMetadata("all"),
            bundles
        );
    }

    @Override
    public void exportConversation(File file, ChatArchiveFormat format, String conversationId) throws Exception {
        export(file, format, buildConversationArchive(conversationId));
    }

    @Override
    public void exportAllConversations(File file, ChatArchiveFormat format) throws Exception {
        export(file, format, buildAllConversationsArchive());
    }

    private void export(File file, ChatArchiveFormat format, ChatArchiveDocument document) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("Export file is required");
        }
        if (format == null) {
            throw new IllegalArgumentException("Chat archive format is required");
        }
        switch (format) {
            case JSON -> Files.writeString(file.toPath(), jsonCodec.write(document), StandardCharsets.UTF_8);
            case MARKDOWN -> Files.writeString(file.toPath(), markdownRenderer.render(document), StandardCharsets.UTF_8);
            case PDF -> pdfRenderer.render(file, document);
        }
    }

    private ChatArchiveBundle loadBundle(ChatConversation conversation) {
        List<ChatMessage> messages = db.loadChatMessages(conversation.getId());
        ChatContextState contextState = db.loadChatContextState(conversation.getId());
        return new ChatArchiveBundle(conversation, messages, contextState);
    }

    private Map<String, String> sourceMetadata(String scope) {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("app", "NeuroFlow Planner");
        source.put("module", "ai-assistant");
        source.put("scope", scope);
        return source;
    }
}
