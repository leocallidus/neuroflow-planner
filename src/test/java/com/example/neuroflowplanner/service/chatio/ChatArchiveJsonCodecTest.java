package com.example.neuroflowplanner.service.chatio;

import com.example.neuroflowplanner.model.ChatContextState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Chat archive JSON codec")
class ChatArchiveJsonCodecTest {
    private final ChatArchiveJsonCodec codec = new ChatArchiveJsonCodec();

    @Test
    @DisplayName("reads legacy archive payload without schemaVersion and contextState")
    void readsLegacyArchiveWithoutSchemaVersionAndContextState() throws Exception {
        String payload = """
            {
              "schemaType": "chat-archive",
              "exported_at": "2026-03-08T20:00:00",
              "conversations": [
                {
                  "conversation_id": "legacy-conv",
                  "name": "Старый архив",
                  "created_at": "2026-03-08T18:00:00",
                  "messages": [
                    {
                      "message_id": "legacy-msg-1",
                      "conversation_id": "legacy-conv",
                      "sender": "user",
                      "text": "Привет",
                      "index": "4",
                      "created_at": "2026-03-08T18:01:00",
                      "legacyExtra": true
                    }
                  ],
                  "unusedField": "ignored"
                }
              ]
            }
            """;

        ChatArchiveDocument document = codec.read(payload);

        assertEquals("chat-archive", document.schemaType());
        assertEquals(1, document.schemaVersion());
        assertEquals("2026-03-08T20:00:00", document.exportedAt());
        assertEquals(1, document.conversations().size());
        ChatArchiveBundle bundle = document.conversations().get(0);
        assertEquals("legacy-conv", bundle.conversation().getId());
        assertEquals("Старый архив", bundle.conversation().getTitle());
        assertEquals("2026-03-08T18:00:00", bundle.conversation().getCreatedAt());
        assertEquals("2026-03-08T18:00:00", bundle.conversation().getUpdatedAt());
        assertEquals(1, bundle.messages().size());
        assertEquals("legacy-msg-1", bundle.messages().get(0).getId());
        assertEquals("user", bundle.messages().get(0).getRole());
        assertEquals("Привет", bundle.messages().get(0).getContent());
        assertEquals(4, bundle.messages().get(0).getSeq());
        assertNull(bundle.contextState());
    }

    @Test
    @DisplayName("reads forward compatible archive with aliases and ignores unknown fields")
    void readsForwardCompatibleArchiveWithAliases() throws Exception {
        String payload = """
            {
              "schemaTypeId": "chat-archive",
              "version": 3,
              "source": {
                "app": "NeuroFlow Planner",
                "module": "ai-assistant",
                "futureField": "ignored"
              },
              "chats": [
                {
                  "id": "future-conv",
                  "title": "Новая схема",
                  "createdAt": "2026-03-08T18:00:00",
                  "updatedAt": "2026-03-08T18:10:00",
                  "entries": [
                    {
                      "conversationId": "future-conv",
                      "content": "Сообщение без id",
                      "order": 2,
                      "timestamp": "2026-03-08T18:02:00",
                      "futureObject": {"safe": true}
                    }
                  ],
                  "context": {
                    "mode": "FULL",
                    "contextSummary": "Импортировано из будущей схемы",
                    "summary_covered_messages": "2",
                    "facts": ["Факт 1"]
                  }
                }
              ]
            }
            """;

        ChatArchiveDocument document = codec.read(payload);

        assertEquals(3, document.schemaVersion());
        assertEquals(1, document.conversations().size());
        ChatArchiveBundle bundle = document.conversations().get(0);
        assertEquals("future-conv", bundle.conversation().getId());
        assertEquals(1, bundle.messages().size());
        assertEquals("future-conv__legacy__msg__1", bundle.messages().get(0).getId());
        assertEquals("assistant", bundle.messages().get(0).getRole());
        assertEquals(2, bundle.messages().get(0).getSeq());
        assertEquals("2026-03-08T18:02:00", bundle.messages().get(0).getCreatedAt());
        ChatContextState contextState = bundle.contextState();
        assertNotNull(contextState);
        assertEquals("future-conv", contextState.getConversationId());
        assertEquals("FULL", contextState.getPreferredMode());
        assertEquals("Импортировано из будущей схемы", contextState.getSummary());
        assertEquals(2, contextState.getSummaryCoveredMessages());
        assertEquals(1, contextState.getPinnedFacts().size());
        assertEquals("2026-03-08T18:10:00", contextState.getUpdatedAt());
    }

    @Test
    @DisplayName("round-trips summary lifecycle metadata in contextState")
    void roundTripsSummaryLifecycleMetadata() throws Exception {
        ChatArchiveDocument document = new ChatArchiveDocument(
            "chat-archive",
            1,
            "2026-03-10T18:00:00",
            java.util.Map.of("app", "NeuroFlow Planner", "module", "ai-assistant"),
            java.util.List.of(
                new ChatArchiveBundle(
                    new com.example.neuroflowplanner.model.ChatConversation(
                        "conv-summary-lifecycle",
                        "Summary lifecycle",
                        "2026-03-10T17:00:00",
                        "2026-03-10T17:05:00"
                    ),
                    java.util.List.of(),
                    new ChatContextState(
                        "conv-summary-lifecycle",
                        "AUTO",
                        "Сводка предыдущего контекста\n## Ключевые решения\n- Решение.\n## Цели пользователя\n- Цель.\n## Важные факты\n- Факт.\n## Незавершенные вопросы\n- Вопрос.\n## Ограничения\n- Ограничение.\n## Вложения и артефакты\n- Артефакт.",
                        6,
                        java.util.List.of("Факт 1", "Факт 2"),
                        1050000,
                        412000,
                        12000,
                        "2026-03-10T17:04:30",
                        "SUMMARY_READY",
                        3,
                        "WARNING",
                        0.392,
                        "2026-03-10T17:05:00"
                    )
                )
            )
        );

        String payload = codec.write(document);
        ChatArchiveDocument decoded = codec.read(payload);

        ChatContextState contextState = decoded.conversations().get(0).contextState();
        assertNotNull(contextState);
        assertEquals(1050000, contextState.getLastContextWindowTokens());
        assertEquals(412000, contextState.getLastEstimatedUsageTokens());
        assertEquals(12000, contextState.getLastReservedCompletionTokens());
        assertEquals("2026-03-10T17:04:30", contextState.getLastSummarizeAt());
        assertEquals("SUMMARY_READY", contextState.getLastSummarizeStatus());
        assertEquals(3, contextState.getActiveSummaryRevision());
        assertEquals("WARNING", contextState.getLastBudgetSeverity());
        assertEquals(0.392, contextState.getLastUsageRatio());
    }
}
