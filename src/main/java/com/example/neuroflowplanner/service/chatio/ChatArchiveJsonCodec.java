package com.example.neuroflowplanner.service.chatio;

import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.model.ChatContextState;
import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChatArchiveJsonCodec {
    private static final int SCHEMA_VERSION = 1;
    private static final String DEFAULT_SCHEMA_TYPE = "chat-archive";
    private static final String DEFAULT_CONVERSATION_TITLE = "Импортированная переписка";
    private static final String DEFAULT_MESSAGE_ROLE = "assistant";

    String write(ChatArchiveDocument document) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaType", document.schemaType());
        payload.put("schemaVersion", document.schemaVersion());
        payload.put("exportedAt", document.exportedAt());
        payload.put("source", document.source());
        payload.put("conversations", document.conversations().stream().map(this::toMap).toList());
        return AiObjectMapperFactory.providerResponseMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(payload);
    }

    ChatArchiveDocument read(String payload) throws Exception {
        String normalized = payload == null ? "" : payload.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Chat archive payload is empty");
        }
        JsonNode root = AiObjectMapperFactory.providerResponseMapper().readTree(normalized);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Chat archive payload must be a JSON object");
        }

        String schemaType = firstText(root, "schemaType", "schemaTypeId", "type");
        if (schemaType == null) {
            schemaType = DEFAULT_SCHEMA_TYPE;
        }
        if (!DEFAULT_SCHEMA_TYPE.equals(schemaType)) {
            throw new IllegalArgumentException("Unsupported chat archive schemaType: " + schemaType);
        }

        int schemaVersion = firstInt(root, SCHEMA_VERSION, "schemaVersion", "version");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Unsupported chat archive schemaVersion: " + schemaVersion);
        }

        List<ChatArchiveBundle> conversations = new ArrayList<>();
        JsonNode conversationsNode = firstArrayNode(root, "conversations", "chats", "items");
        if (conversationsNode == null) {
            conversationsNode = AiObjectMapperFactory.providerResponseMapper().createArrayNode();
        }
        if (!conversationsNode.isArray()) {
            throw new IllegalArgumentException("Chat archive expects 'conversations' array");
        }
        String exportedAt = firstText(root, "exportedAt", "exported_at", "createdAt", "created_at");
        for (JsonNode conversationNode : conversationsNode) {
            conversations.add(parseConversation(conversationNode, exportedAt));
        }

        return new ChatArchiveDocument(
            schemaType,
            schemaVersion,
            exportedAt,
            parseSource(root.path("source")),
            conversations
        );
    }

    private Map<String, Object> toMap(ChatArchiveBundle bundle) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        ChatConversation conversation = bundle.conversation();
        mapped.put("id", conversation.getId());
        mapped.put("title", conversation.getTitle());
        mapped.put("createdAt", conversation.getCreatedAt());
        mapped.put("updatedAt", conversation.getUpdatedAt());
        mapped.put("messages", bundle.messages().stream().map(this::toMap).toList());
        if (bundle.contextState() != null) {
            mapped.put("contextState", toMap(bundle.contextState()));
        }
        return mapped;
    }

    private Map<String, Object> toMap(ChatMessage message) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("id", message.getId());
        mapped.put("conversationId", message.getConversationId());
        mapped.put("role", message.getRole());
        mapped.put("content", message.getContent());
        mapped.put("seq", message.getSeq());
        mapped.put("createdAt", message.getCreatedAt());
        return mapped;
    }

    private Map<String, Object> toMap(ChatContextState contextState) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("conversationId", contextState.getConversationId());
        mapped.put("preferredMode", contextState.getPreferredMode());
        mapped.put("summary", contextState.getSummary());
        mapped.put("summaryCoveredMessages", contextState.getSummaryCoveredMessages());
        mapped.put("pinnedFacts", contextState.getPinnedFacts());
        mapped.put("lastContextWindowTokens", contextState.getLastContextWindowTokens());
        mapped.put("lastEstimatedUsageTokens", contextState.getLastEstimatedUsageTokens());
        mapped.put("lastReservedCompletionTokens", contextState.getLastReservedCompletionTokens());
        mapped.put("lastSummarizeAt", contextState.getLastSummarizeAt());
        mapped.put("lastSummarizeStatus", contextState.getLastSummarizeStatus());
        mapped.put("activeSummaryRevision", contextState.getActiveSummaryRevision());
        mapped.put("lastBudgetSeverity", contextState.getLastBudgetSeverity());
        mapped.put("lastUsageRatio", contextState.getLastUsageRatio());
        mapped.put("updatedAt", contextState.getUpdatedAt());
        return mapped;
    }

    private ChatArchiveBundle parseConversation(JsonNode node, String fallbackExportedAt) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Conversation entry must be an object");
        }
        String conversationId = requiredText(node, "id", "conversationId", "conversation_id");
        String createdAt = firstText(node, "createdAt", "created_at", "startedAt", "started_at");
        String updatedAt = firstText(node, "updatedAt", "updated_at", "modifiedAt", "modified_at");
        ChatConversation conversation = new ChatConversation(
            conversationId,
            defaultIfBlank(firstText(node, "title", "name"), DEFAULT_CONVERSATION_TITLE),
            defaultTimestamp(createdAt, fallbackExportedAt),
            defaultTimestamp(updatedAt, createdAt, fallbackExportedAt)
        );

        JsonNode messagesNode = firstArrayNode(node, "messages", "entries", "items");
        List<ChatMessage> messages = new ArrayList<>();
        if (messagesNode != null && !messagesNode.isArray()) {
            throw new IllegalArgumentException("Conversation entry expects 'messages' array");
        }
        int ordinal = 1;
        if (messagesNode != null) {
            for (JsonNode messageNode : messagesNode) {
                messages.add(parseMessage(messageNode, conversation.getId(), conversation.getCreatedAt(), ordinal));
                ordinal++;
            }
        }
        messages.sort(Comparator.comparingInt(ChatMessage::getSeq));

        JsonNode contextNode = firstObjectNode(node, "contextState", "context_state", "context");
        ChatContextState contextState = contextNode == null ? null : parseContextState(contextNode, conversation.getId(), conversation.getUpdatedAt());
        return new ChatArchiveBundle(conversation, messages, contextState);
    }

    private ChatMessage parseMessage(JsonNode node, String fallbackConversationId, String fallbackCreatedAt, int ordinal) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Message entry must be an object");
        }
        String messageId = firstText(node, "id", "messageId", "message_id");
        String conversationId = firstText(node, "conversationId", "conversation_id");
        String role = defaultIfBlank(firstText(node, "role", "sender"), DEFAULT_MESSAGE_ROLE);
        String content = defaultIfBlank(firstText(node, "content", "text", "message"), "");
        int seq = firstInt(node, ordinal, "seq", "index", "order");
        String createdAt = defaultTimestamp(
            firstText(node, "createdAt", "created_at", "timestamp"),
            fallbackCreatedAt
        );
        return new ChatMessage(
            defaultIfBlank(messageId, fallbackConversationId + "__legacy__msg__" + ordinal),
            defaultIfBlank(conversationId, fallbackConversationId),
            role,
            content,
            seq,
            createdAt
        );
    }

    private ChatContextState parseContextState(JsonNode node, String fallbackConversationId, String fallbackUpdatedAt) {
        ChatContextState contextState = new ChatContextState(
            defaultIfBlank(firstText(node, "conversationId", "conversation_id"), fallbackConversationId),
            firstText(node, "preferredMode", "preferred_mode", "mode"),
            firstText(node, "summary", "contextSummary", "context_summary"),
            firstInt(node, 0, "summaryCoveredMessages", "summary_covered_messages"),
            stringList(firstArrayNode(node, "pinnedFacts", "pinned_facts", "facts")),
            nullableInt(node, "lastContextWindowTokens", "last_context_window_tokens"),
            nullableInt(node, "lastEstimatedUsageTokens", "last_estimated_usage_tokens"),
            nullableInt(node, "lastReservedCompletionTokens", "last_reserved_completion_tokens"),
            firstText(node, "lastSummarizeAt", "last_summarize_at"),
            firstText(node, "lastSummarizeStatus", "last_summarize_status"),
            nullableInt(node, "activeSummaryRevision", "active_summary_revision"),
            firstText(node, "lastBudgetSeverity", "last_budget_severity"),
            nullableDouble(node, "lastUsageRatio", "last_usage_ratio"),
            defaultTimestamp(firstText(node, "updatedAt", "updated_at"), fallbackUpdatedAt)
        );
        return isEmptyContextState(contextState) ? null : contextState;
    }

    private Map<String, String> parseSource(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of("app", "NeuroFlow Planner", "module", "ai-assistant");
        }
        Map<String, String> source = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && !value.isNull()) {
                source.put(entry.getKey(), value.asText());
            }
        });
        return source;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode entry : node) {
            if (entry != null && !entry.isNull()) {
                values.add(entry.asText());
            }
        }
        return values;
    }

    private Integer nullableInt(JsonNode node, String... fields) {
        JsonNode found = firstArrayNode(node, fields);
        if (found == null || found.isNull()) {
            return null;
        }
        if (found.isNumber()) {
            return found.intValue();
        }
        String text = found.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double nullableDouble(JsonNode node, String... fields) {
        JsonNode found = firstArrayNode(node, fields);
        if (found == null || found.isNull()) {
            return null;
        }
        if (found.isNumber()) {
            return found.doubleValue();
        }
        String text = found.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isEmptyContextState(ChatContextState contextState) {
        return contextState == null
            || (!hasText(contextState.getPreferredMode())
            && !hasText(contextState.getSummary())
            && contextState.getSummaryCoveredMessages() <= 0
            && (contextState.getPinnedFacts() == null || contextState.getPinnedFacts().isEmpty()));
    }

    private String requiredText(JsonNode node, String... fields) {
        String value = firstText(node, fields);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Chat archive field '" + fields[0] + "' is required");
        }
        return value;
    }

    private int firstInt(JsonNode node, int fallback, String... fields) {
        if (node == null || !node.isObject()) {
            return fallback;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
            if (value != null && value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Ignore malformed aliases and continue to the next field.
                }
            }
        }
        return fallback;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private JsonNode firstArrayNode(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode firstObjectNode(JsonNode node, String... fields) {
        JsonNode candidate = firstArrayNode(node, fields);
        return candidate != null && candidate.isObject() ? candidate : null;
    }

    private String defaultTimestamp(String... candidates) {
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate;
            }
        }
        return "1970-01-01T00:00:00";
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
