package com.example.neuroflowplanner.service.chatio;

import com.example.neuroflowplanner.model.chatio.ChatArchiveImportOptions;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportPreview;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportResult;

import java.io.File;

public interface ChatArchiveImportService {

    ChatArchiveDocument readArchive(String payload, ChatArchiveFormat format) throws Exception;

    ChatArchiveDocument readArchive(File file, ChatArchiveFormat format) throws Exception;

    ChatArchiveImportPreview dryRun(String payload, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception;

    ChatArchiveImportPreview dryRun(File file, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception;

    ChatArchiveImportResult apply(ChatArchiveImportPreview preview) throws Exception;

    default ChatArchiveImportPreview previewArchive(String payload, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception {
        return dryRun(payload, format, options);
    }

    default ChatArchiveImportPreview previewArchive(File file, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception {
        return dryRun(file, format, options);
    }

    default ChatArchiveImportResult apply(String payload, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception {
        return apply(dryRun(payload, format, options));
    }

    default ChatArchiveImportResult apply(File file, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception {
        return apply(dryRun(file, format, options));
    }

    default ChatArchiveImportResult applyArchive(String payload, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception {
        return apply(payload, format, options);
    }

    default ChatArchiveImportResult applyArchive(File file, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception {
        return apply(file, format, options);
    }

    default ChatArchiveDocument readJson(String payload) throws Exception {
        return readArchive(payload, ChatArchiveFormat.JSON);
    }

    default ChatArchiveDocument readJson(File file) throws Exception {
        return readArchive(file, ChatArchiveFormat.JSON);
    }

    default ChatArchiveImportPreview previewJson(String payload, ChatArchiveImportOptions options) throws Exception {
        return dryRun(payload, ChatArchiveFormat.JSON, options);
    }

    default ChatArchiveImportPreview previewJson(File file, ChatArchiveImportOptions options) throws Exception {
        return dryRun(file, ChatArchiveFormat.JSON, options);
    }

    default ChatArchiveImportResult applyJson(String payload, ChatArchiveImportOptions options) throws Exception {
        return apply(payload, ChatArchiveFormat.JSON, options);
    }

    default ChatArchiveImportResult applyJson(File file, ChatArchiveImportOptions options) throws Exception {
        return apply(file, ChatArchiveFormat.JSON, options);
    }
}
