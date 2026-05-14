package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.service.chatio.ChatArchiveExportService;
import com.example.neuroflowplanner.service.chatio.ChatArchiveFormat;
import com.example.neuroflowplanner.service.chatio.DefaultChatArchiveExportService;

import java.io.File;

public class ChatPdfExporter {
    private final ChatArchiveExportService exportService;

    public ChatPdfExporter() {
        this(new DefaultChatArchiveExportService());
    }

    ChatPdfExporter(ChatArchiveExportService exportService) {
        this.exportService = exportService;
    }

    public void exportConversation(File file, ChatConversation conversation) throws Exception {
        if (conversation == null) {
            throw new IllegalArgumentException("Переписка не выбрана.");
        }
        exportService.exportConversation(file, ChatArchiveFormat.PDF, conversation.getId());
    }

    public void exportAllConversations(File file) throws Exception {
        exportService.exportAllConversations(file, ChatArchiveFormat.PDF);
    }
}
