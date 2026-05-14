package com.example.neuroflowplanner.service.chatio;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.ChatContextState;
import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportConflictPolicy;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportOptions;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportPreview;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportResult;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Chat archive services")
class ChatArchiveServicesIntegrationTest extends IsolatedTestDataFixture {
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final DefaultChatArchiveExportService exportService = new DefaultChatArchiveExportService();
    private final DefaultChatArchiveImportService importService = new DefaultChatArchiveImportService();

    @Test
    @DisplayName("buildConversationArchive loads conversation messages and context from service layer")
    void buildConversationArchiveLoadsBundleFromDatabase() {
        ChatConversation conversation = seedConversation("Архив планирования");
        seedMessages(conversation.getId());
        db.saveChatContextState(new ChatContextState(
            conversation.getId(),
            "AUTO",
            "Сводка по диалогу",
            2,
            List.of("Релиз в пятницу"),
            1050000,
            412000,
            12000,
            "2026-03-10T17:04:30",
            "SUMMARY_READY",
            3,
            "WARNING",
            0.392,
            LocalDateTime.now().toString()
        ));

        ChatArchiveDocument archive = exportService.buildConversationArchive(conversation.getId());

        assertEquals("chat-archive", archive.schemaType());
        assertEquals(1, archive.schemaVersion());
        assertEquals(1, archive.conversations().size());
        ChatArchiveBundle bundle = archive.conversations().get(0);
        assertEquals(conversation.getId(), bundle.conversation().getId());
        assertEquals(2, bundle.messages().size());
        assertEquals("assistant", bundle.messages().get(1).getRole());
        assertNotNull(bundle.contextState());
        assertEquals("AUTO", bundle.contextState().getPreferredMode());
        assertEquals(1050000, bundle.contextState().getLastContextWindowTokens());
        assertEquals("SUMMARY_READY", bundle.contextState().getLastSummarizeStatus());
        assertEquals(3, bundle.contextState().getActiveSummaryRevision());
    }

    @Test
    @DisplayName("JSON export round-trips into import service without manual normalization")
    void exportJsonRoundTripsIntoImportService(@TempDir Path tempDir) throws Exception {
        ChatConversation conversation = seedConversation("Round-trip JSON");
        seedMessages(conversation.getId());

        Path file = tempDir.resolve("chat-archive.json");
        exportService.exportConversation(file.toFile(), ChatArchiveFormat.JSON, conversation.getId());
        String payload = Files.readString(file, StandardCharsets.UTF_8);
        ChatArchiveDocument imported = importService.readJson(payload);

        assertTrue(payload.contains("\"schemaType\" : \"chat-archive\""));
        assertEquals(1, imported.conversations().size());
        assertEquals(conversation.getId(), imported.conversations().get(0).conversation().getId());
        assertEquals(2, imported.conversations().get(0).messages().size());
    }

    @Test
    @DisplayName("Markdown and PDF export are available through chat archive domain layer")
    void exportMarkdownAndPdf(@TempDir Path tempDir) throws Exception {
        ChatConversation conversation = seedConversation("Мультиформат");
        seedMessages(conversation.getId());

        Path markdown = tempDir.resolve("chat-archive.md");
        Path pdf = tempDir.resolve("chat-archive.pdf");

        exportService.exportConversation(markdown.toFile(), ChatArchiveFormat.MARKDOWN, conversation.getId());
        exportService.exportConversation(pdf.toFile(), ChatArchiveFormat.PDF, conversation.getId());

        String markdownPayload = Files.readString(markdown, StandardCharsets.UTF_8);
        assertTrue(markdownPayload.contains("# ИИ-Ассистент — Архив переписок"));
        assertTrue(markdownPayload.contains("Мультиформат"));
        assertTrue(Files.size(pdf) > 0, "PDF export must produce a non-empty file");
    }

    @Test
    @DisplayName("preview counts new conversations conflicts and messages before import")
    void previewCountsArchiveSummary() throws Exception {
        ChatConversation existing = seedConversation("Уже есть");
        seedMessages(existing.getId());

        String payload = """
            {
              "schemaType": "chat-archive",
              "schemaVersion": 1,
              "exportedAt": "2026-03-08T18:40:00",
              "source": {"app": "NeuroFlow Planner", "module": "ai-assistant"},
              "conversations": [
                {
                  "id": "%s",
                  "title": "Конфликтующая",
                  "createdAt": "2026-03-08T10:00:00",
                  "updatedAt": "2026-03-08T10:05:00",
                  "messages": [
                    {"id": "msg-a", "conversationId": "%s", "role": "user", "content": "A", "seq": 1, "createdAt": "2026-03-08T10:00:10"}
                  ]
                },
                {
                  "id": "import-new-conversation",
                  "title": "Новая",
                  "createdAt": "2026-03-08T11:00:00",
                  "updatedAt": "2026-03-08T11:05:00",
                  "messages": [
                    {"id": "msg-b", "conversationId": "import-new-conversation", "role": "user", "content": "B", "seq": 1, "createdAt": "2026-03-08T11:00:10"},
                    {"id": "msg-c", "conversationId": "import-new-conversation", "role": "assistant", "content": "C", "seq": 2, "createdAt": "2026-03-08T11:00:20"}
                  ]
                }
              ]
            }
            """.formatted(existing.getId(), existing.getId());

        ChatArchiveImportPreview preview = importService.previewJson(
            payload,
            new ChatArchiveImportOptions(ChatArchiveImportConflictPolicy.SKIP_EXISTING)
        );

        assertEquals(2, preview.sourceCount());
        assertEquals(1, preview.newConversationCount());
        assertEquals(1, preview.conflictingConversationCount());
        assertEquals(3, preview.messageCount());
        assertEquals(1, preview.importableConversationCount());
        assertEquals(1, preview.skippedConversationCount());
    }

    @Test
    @DisplayName("apply with keep-both preserves existing conversation and imports duplicate as a new one")
    void applyKeepBothImportsConflictAsNewConversation() throws Exception {
        int conversationsBefore = db.loadChatConversations().size();
        ChatConversation existing = seedConversation("Конфликт по ID");
        seedMessages(existing.getId());
        ChatArchiveDocument document = exportService.buildConversationArchive(existing.getId());
        String payload = new ChatArchiveJsonCodec().write(document);

        ChatArchiveImportResult result = importService.applyJson(
            payload,
            new ChatArchiveImportOptions(ChatArchiveImportConflictPolicy.KEEP_BOTH)
        );

        assertEquals(1, result.importedConversationCount());
        assertEquals(2, result.importedMessageCount());
        assertEquals(0, result.skippedConversationCount());
        assertEquals(conversationsBefore + 2, db.loadChatConversations().size());

        ChatConversation imported = db.loadChatConversations().stream()
            .filter(conversation -> "Конфликт по ID".equals(conversation.getTitle()))
            .filter(conversation -> !existing.getId().equals(conversation.getId()))
            .findFirst()
            .orElseThrow();

        assertNotEquals(existing.getId(), imported.getId());
        assertEquals(2, db.loadChatMessages(imported.getId()).size());
    }

    @Test
    @DisplayName("dry-run counts title collisions and messageId collisions and apply restores context state")
    void dryRunCountsTitleAndMessageCollisionsAndApplyRestoresContext() throws Exception {
        ChatConversation existing = seedConversation("Общий заголовок");
        db.saveChatMessage(new ChatMessage(
            "shared-message-id",
            existing.getId(),
            "user",
            "Исходное сообщение",
            1,
            LocalDateTime.now().minusMinutes(3).toString()
        ));

        String payload = """
            {
              "schemaType": "chat-archive",
              "schemaVersion": 1,
              "exportedAt": "2026-03-08T19:00:00",
              "source": {"app": "NeuroFlow Planner", "module": "ai-assistant"},
              "conversations": [
                {
                  "id": "imported-conv",
                  "title": "Общий заголовок",
                  "createdAt": "2026-03-08T12:00:00",
                  "updatedAt": "2026-03-08T12:05:00",
                  "messages": [
                    {"id": "shared-message-id", "conversationId": "imported-conv", "role": "user", "content": "Новое сообщение", "seq": 1, "createdAt": "2026-03-08T12:00:10"}
                  ],
                  "contextState": {
                    "conversationId": "imported-conv",
                    "preferredMode": "FULL",
                    "summary": "Импортированная сводка",
                    "summaryCoveredMessages": 1,
                    "pinnedFacts": ["Новый факт"],
                    "lastContextWindowTokens": 1050000,
                    "lastEstimatedUsageTokens": 412000,
                    "lastReservedCompletionTokens": 12000,
                    "lastSummarizeAt": "2026-03-10T17:04:30",
                    "lastSummarizeStatus": "SUMMARY_READY",
                    "activeSummaryRevision": 4,
                    "lastBudgetSeverity": "WARNING",
                    "lastUsageRatio": 0.392,
                    "updatedAt": "2026-03-08T12:05:00"
                  }
                }
              ]
            }
            """;

        ChatArchiveImportPreview preview = importService.dryRun(
            payload,
            ChatArchiveFormat.JSON,
            ChatArchiveImportOptions.defaults()
        );

        assertEquals(1, preview.titleCollisionCount());
        assertEquals(1, preview.messageIdCollisionCount());
        assertEquals(1, preview.acceptedCount());

        ChatArchiveImportResult result = importService.apply(preview);
        assertEquals(1, result.importedConversationCount());

        List<ChatMessage> importedMessages = db.loadChatMessages("imported-conv");
        assertEquals(1, importedMessages.size());
        assertNotEquals("shared-message-id", importedMessages.get(0).getId());

        ChatContextState importedContext = db.loadChatContextState("imported-conv");
        assertNotNull(importedContext);
        assertEquals("FULL", importedContext.getPreferredMode());
        assertEquals(List.of("Новый факт"), importedContext.getPinnedFacts());
        assertEquals(1050000, importedContext.getLastContextWindowTokens());
        assertEquals(412000, importedContext.getLastEstimatedUsageTokens());
        assertEquals(12000, importedContext.getLastReservedCompletionTokens());
        assertEquals("2026-03-10T17:04:30", importedContext.getLastSummarizeAt());
        assertEquals("SUMMARY_READY", importedContext.getLastSummarizeStatus());
        assertEquals(4, importedContext.getActiveSummaryRevision());
        assertEquals("WARNING", importedContext.getLastBudgetSeverity());
        assertEquals(0.392, importedContext.getLastUsageRatio());
    }

    @Test
    @DisplayName("conflict policy resolution is deterministic for keep-both replace and skip")
    void conflictPolicyResolutionIsDeterministic() throws Exception {
        ChatConversation existing = seedConversation("Политика конфликта");
        seedMessages(existing.getId());

        String payload = """
            {
              "schemaType": "chat-archive",
              "schemaVersion": 1,
              "conversations": [
                {
                  "id": "%s",
                  "title": "Политика конфликта",
                  "createdAt": "2026-03-08T13:00:00",
                  "updatedAt": "2026-03-08T13:05:00",
                  "messages": [
                    {"id": "policy-msg", "conversationId": "%s", "role": "assistant", "content": "Новая версия", "seq": 1, "createdAt": "2026-03-08T13:00:10"}
                  ]
                }
              ]
            }
            """.formatted(existing.getId(), existing.getId());

        ChatArchiveImportPreview keepBothPreview = importService.previewJson(
            payload,
            new ChatArchiveImportOptions(ChatArchiveImportConflictPolicy.KEEP_BOTH)
        );
        ChatArchiveImportPreview replacePreview = importService.previewJson(
            payload,
            new ChatArchiveImportOptions(ChatArchiveImportConflictPolicy.REPLACE_EXISTING)
        );
        ChatArchiveImportPreview skipPreview = importService.previewJson(
            payload,
            new ChatArchiveImportOptions(ChatArchiveImportConflictPolicy.SKIP_EXISTING)
        );

        assertEquals("KEEP_BOTH_CREATE", keepBothPreview.conversationPlans().get(0).action().name());
        assertTrue(keepBothPreview.conversationPlans().get(0).resolvedConversationId().startsWith(existing.getId() + "__imported__"));
        assertEquals("REPLACE", replacePreview.conversationPlans().get(0).action().name());
        assertEquals(existing.getId(), replacePreview.conversationPlans().get(0).resolvedConversationId());
        assertEquals("SKIP", skipPreview.conversationPlans().get(0).action().name());
        assertEquals(0, skipPreview.importableConversationCount());
    }

    @Test
    @DisplayName("replace-existing import overwrites messages and context for collided conversation id")
    void replaceExistingImportOverwritesMessagesAndContext() throws Exception {
        ChatConversation existing = seedConversation("Заменяемая переписка");
        db.saveChatMessage(new ChatMessage(
            "existing-msg",
            existing.getId(),
            "assistant",
            "Старое сообщение",
            1,
            LocalDateTime.now().minusMinutes(10).toString()
        ));
        db.saveChatContextState(new ChatContextState(
            existing.getId(),
            "AUTO",
            "Старая сводка",
            1,
            List.of("Старый факт"),
            128000,
            21000,
            4000,
            "2026-03-08T13:59:00",
            "SUMMARY_READY",
            2,
            "NORMAL",
            0.164,
            LocalDateTime.now().minusMinutes(9).toString()
        ));

        String payload = """
            {
              "schemaType": "chat-archive",
              "schemaVersion": 1,
              "conversations": [
                {
                  "id": "%s",
                  "title": "Заменяемая переписка",
                  "createdAt": "2026-03-08T14:00:00",
                  "updatedAt": "2026-03-08T14:05:00",
                  "messages": [
                    {"id": "replacement-msg", "conversationId": "%s", "role": "user", "content": "Новый импорт", "seq": 1, "createdAt": "2026-03-08T14:00:10"}
                  ],
                  "contextState": {
                    "conversationId": "%s",
                    "preferredMode": "FULL",
                    "summary": "Новая сводка",
                    "summaryCoveredMessages": 1,
                    "pinnedFacts": ["Новый факт"],
                    "lastContextWindowTokens": 1050000,
                    "lastEstimatedUsageTokens": 412000,
                    "lastReservedCompletionTokens": 12000,
                    "lastSummarizeAt": "2026-03-10T17:04:30",
                    "lastSummarizeStatus": "SUMMARY_READY",
                    "activeSummaryRevision": 4,
                    "lastBudgetSeverity": "WARNING",
                    "lastUsageRatio": 0.392,
                    "updatedAt": "2026-03-08T14:05:00"
                  }
                }
              ]
            }
            """.formatted(existing.getId(), existing.getId(), existing.getId());

        ChatArchiveImportResult result = importService.applyJson(
            payload,
            new ChatArchiveImportOptions(ChatArchiveImportConflictPolicy.REPLACE_EXISTING)
        );

        assertEquals(1, result.importedConversationCount());
        List<ChatMessage> messages = db.loadChatMessages(existing.getId());
        assertEquals(1, messages.size());
        assertEquals("replacement-msg", messages.get(0).getId());
        assertEquals("Новый импорт", messages.get(0).getContent());
        ChatContextState contextState = db.loadChatContextState(existing.getId());
        assertNotNull(contextState);
        assertEquals("FULL", contextState.getPreferredMode());
        assertEquals(List.of("Новый факт"), contextState.getPinnedFacts());
        assertEquals(1050000, contextState.getLastContextWindowTokens());
        assertEquals("SUMMARY_READY", contextState.getLastSummarizeStatus());
        assertEquals(4, contextState.getActiveSummaryRevision());
    }

    @Test
    @DisplayName("export/import batch of conversations preserves all bundles")
    void exportImportBatchOfConversationsPreservesAllBundles(@TempDir Path tempDir) throws Exception {
        ChatConversation first = seedConversation("Batch A");
        seedMessages(first.getId());
        db.saveChatContextState(new ChatContextState(
            first.getId(),
            "AUTO",
            "Сводка A",
            2,
            List.of("Факт A"),
            1050000,
            412000,
            12000,
            "2026-03-10T17:04:30",
            "SUMMARY_READY",
            5,
            "WARNING",
            0.392,
            LocalDateTime.now().toString()
        ));
        ChatConversation second = seedConversation("Batch B");
        seedMessages(second.getId());

        Path file = tempDir.resolve("chat-batch.json");
        exportService.exportAllConversations(file.toFile(), ChatArchiveFormat.JSON);
        String payload = Files.readString(file);

        db.deleteChatConversation(first.getId());
        db.deleteChatConversation(second.getId());
        assertTrue(db.loadChatConversations().stream().noneMatch(c -> c.getId().equals(first.getId()) || c.getId().equals(second.getId())));

        ChatArchiveImportPreview preview = importService.previewJson(payload, ChatArchiveImportOptions.defaults());
        ChatArchiveImportResult result = importService.apply(preview);

        assertEquals(preview.importableConversationCount(), result.importedConversationCount());
        assertEquals(2, db.loadChatMessages(first.getId()).size());
        assertEquals(2, db.loadChatMessages(second.getId()).size());
        ChatContextState restoredFirstContext = db.loadChatContextState(first.getId());
        assertNotNull(restoredFirstContext);
        assertEquals(1050000, restoredFirstContext.getLastContextWindowTokens());
        assertEquals("SUMMARY_READY", restoredFirstContext.getLastSummarizeStatus());
        assertEquals(5, restoredFirstContext.getActiveSummaryRevision());
    }

    @Test
    @DisplayName("partial archive without context state imports successfully and does not create empty context")
    void partialArchiveWithoutContextStateImportsSuccessfully() throws Exception {
        String payload = """
            {
              "schemaType": "chat-archive",
              "schemaVersion": 1,
              "conversations": [
                {
                  "id": "partial-no-context",
                  "title": "Без контекста",
                  "createdAt": "2026-03-08T15:00:00",
                  "updatedAt": "2026-03-08T15:05:00",
                  "messages": [
                    {"id": "partial-msg", "conversationId": "partial-no-context", "role": "user", "content": "Только сообщения", "seq": 1, "createdAt": "2026-03-08T15:00:10"}
                  ]
                }
              ]
            }
            """;

        ChatArchiveImportPreview preview = importService.previewJson(payload, ChatArchiveImportOptions.defaults());
        ChatArchiveImportResult result = importService.apply(preview);

        assertEquals(1, result.importedConversationCount());
        assertEquals(1, db.loadChatMessages("partial-no-context").size());
        assertNull(db.loadChatContextState("partial-no-context"));
    }

    private ChatConversation seedConversation(String title) {
        return db.createChatConversation(title);
    }

    private void seedMessages(String conversationId) {
        db.saveChatMessage(new ChatMessage(
            "msg-" + UUID.randomUUID(),
            conversationId,
            "user",
            "Помоги подготовить итоги недели",
            1,
            LocalDateTime.now().minusMinutes(1).toString()
        ));
        db.saveChatMessage(new ChatMessage(
            "msg-" + UUID.randomUUID(),
            conversationId,
            "assistant",
            "Сформировал план и краткую сводку",
            2,
            LocalDateTime.now().toString()
        ));
    }
}
