package com.example.neuroflowplanner.service.chatio;

import java.io.File;

public interface ChatArchiveExportService {

    ChatArchiveDocument buildConversationArchive(String conversationId);

    ChatArchiveDocument buildAllConversationsArchive();

    void exportConversation(File file, ChatArchiveFormat format, String conversationId) throws Exception;

    void exportAllConversations(File file, ChatArchiveFormat format) throws Exception;
}
