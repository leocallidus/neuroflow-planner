package com.example.neuroflowplanner.service.chatio;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.ChatContextState;
import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportAction;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportConflictPolicy;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportConversationPlan;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportOptions;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportPreview;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportResult;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultChatArchiveImportService implements ChatArchiveImportService {
    private final DatabaseManager db;
    private final ChatArchiveJsonCodec jsonCodec;

    public DefaultChatArchiveImportService() {
        this(DatabaseManager.getInstance(), new ChatArchiveJsonCodec());
    }

    DefaultChatArchiveImportService(DatabaseManager db, ChatArchiveJsonCodec jsonCodec) {
        this.db = db;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public ChatArchiveDocument readArchive(String payload, ChatArchiveFormat format) throws Exception {
        ensureJsonFormat(format);
        try {
            return jsonCodec.read(payload);
        } catch (ChatArchiveImportValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatArchiveImportValidationException("Не удалось разобрать chat archive JSON.", e, List.of(e.getMessage()));
        }
    }

    @Override
    public ChatArchiveDocument readArchive(File file, ChatArchiveFormat format) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("Archive file is required");
        }
        return readArchive(Files.readString(file.toPath(), StandardCharsets.UTF_8), format);
    }

    @Override
    public ChatArchiveImportPreview dryRun(String payload, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception {
        return planImport(readArchive(payload, format), format, options);
    }

    @Override
    public ChatArchiveImportPreview dryRun(File file, ChatArchiveFormat format, ChatArchiveImportOptions options) throws Exception {
        return planImport(readArchive(file, format), format, options);
    }

    @Override
    public ChatArchiveImportResult apply(ChatArchiveImportPreview preview) {
        if (preview == null) {
            throw new IllegalArgumentException("Import preview is required");
        }
        int importedConversationCount = 0;
        int importedMessageCount = 0;
        int skippedConversationCount = 0;
        List<String> warnings = new ArrayList<>(preview.warnings());

        for (ChatArchiveImportConversationPlan plan : preview.conversationPlans()) {
            if (plan == null) {
                continue;
            }
            if (!plan.importable()) {
                skippedConversationCount++;
                continue;
            }

            boolean replaceExisting = plan.action() == ChatArchiveImportAction.REPLACE;
            db.persistChatConversationBundle(
                plan.conversationToPersist(),
                plan.messagesToPersist(),
                plan.contextStateToPersist(),
                replaceExisting
            );
            importedConversationCount++;
            importedMessageCount += plan.messagesToPersist().size();
            warnings.addAll(plan.warnings());
        }

        return new ChatArchiveImportResult(
            preview,
            importedConversationCount,
            importedMessageCount,
            skippedConversationCount,
            warnings
        );
    }

    private ChatArchiveImportPreview planImport(
        ChatArchiveDocument document,
        ChatArchiveFormat format,
        ChatArchiveImportOptions rawOptions
    ) {
        ChatArchiveImportOptions options = rawOptions == null ? ChatArchiveImportOptions.defaults() : rawOptions;
        validateArchiveUniqueness(document);

        List<ChatConversation> existingConversations = db.loadChatConversations();
        Map<String, ChatConversation> existingById = new LinkedHashMap<>();
        Set<String> reservedConversationIds = new LinkedHashSet<>();
        Set<String> reservedTitles = new LinkedHashSet<>();
        for (ChatConversation conversation : existingConversations) {
            if (conversation == null || conversation.getId() == null || conversation.getId().isBlank()) {
                continue;
            }
            existingById.put(conversation.getId(), conversation);
            reservedConversationIds.add(conversation.getId());
            if (hasText(conversation.getTitle())) {
                reservedTitles.add(normalizeTitle(conversation.getTitle()));
            }
        }

        Set<String> reservedMessageIds = new LinkedHashSet<>(db.loadAllChatMessageIds());
        List<String> warnings = new ArrayList<>();
        List<ChatArchiveImportConversationPlan> plans = new ArrayList<>();
        int newConversationCount = 0;
        int conflictingConversationCount = 0;
        int titleCollisionCount = 0;
        int messageIdCollisionCount = 0;
        int totalMessageCount = 0;
        int skippedConversationCount = 0;

        for (ChatArchiveBundle bundle : document.conversations()) {
            if (bundle == null) {
                continue;
            }
            ChatConversation sourceConversation = bundle.conversation();
            String sourceConversationId = sourceConversation.getId();
            String normalizedTitle = normalizeTitle(sourceConversation.getTitle());
            boolean idConflict = existingById.containsKey(sourceConversationId);
            boolean titleCollision = normalizedTitle != null && reservedTitles.contains(normalizedTitle);
            ChatArchiveImportAction action = resolveAction(idConflict, options.conflictPolicy());
            if (idConflict) {
                conflictingConversationCount++;
            } else {
                newConversationCount++;
            }
            if (titleCollision) {
                titleCollisionCount++;
            }

            String resolvedConversationId = action == ChatArchiveImportAction.KEEP_BOTH_CREATE
                ? nextConversationId(sourceConversationId, reservedConversationIds)
                : sourceConversationId;

            Set<String> messageIdsReservedForPlan = new LinkedHashSet<>(reservedMessageIds);
            if (action == ChatArchiveImportAction.REPLACE && existingById.containsKey(sourceConversationId)) {
                for (ChatMessage existingMessage : db.loadChatMessages(sourceConversationId)) {
                    messageIdsReservedForPlan.remove(existingMessage.getId());
                }
            }

            List<ChatMessage> preparedMessages = new ArrayList<>();
            List<String> planWarnings = new ArrayList<>();
            int planMessageCollisionCount = 0;
            int ordinal = 1;
            Set<String> usedMessageIds = new LinkedHashSet<>();
            List<ChatMessage> sortedMessages = bundle.messages().stream()
                .sorted(Comparator.comparingInt(ChatMessage::getSeq))
                .toList();

            for (ChatMessage sourceMessage : sortedMessages) {
                String candidateMessageId = sourceMessage.getId();
                boolean collision = !hasText(candidateMessageId)
                    || !usedMessageIds.add(candidateMessageId)
                    || messageIdsReservedForPlan.contains(candidateMessageId);
                String resolvedMessageId = collision
                    ? nextMessageId(resolvedConversationId, ordinal, messageIdsReservedForPlan, usedMessageIds)
                    : candidateMessageId;
                if (collision) {
                    planMessageCollisionCount++;
                    usedMessageIds.add(resolvedMessageId);
                }
                preparedMessages.add(new ChatMessage(
                    resolvedMessageId,
                    resolvedConversationId,
                    sourceMessage.getRole(),
                    sourceMessage.getContent(),
                    sourceMessage.getSeq(),
                    sourceMessage.getCreatedAt()
                ));
                ordinal++;
            }

            ChatConversation conversationToPersist = new ChatConversation(
                resolvedConversationId,
                sourceConversation.getTitle(),
                sourceConversation.getCreatedAt(),
                sourceConversation.getUpdatedAt()
            );
            ChatContextState contextToPersist = bundle.contextState() == null
                ? null
                : new ChatContextState(
                    resolvedConversationId,
                    bundle.contextState().getPreferredMode(),
                    bundle.contextState().getSummary(),
                    bundle.contextState().getSummaryCoveredMessages(),
                    bundle.contextState().getPinnedFacts(),
                    bundle.contextState().getLastContextWindowTokens(),
                    bundle.contextState().getLastEstimatedUsageTokens(),
                    bundle.contextState().getLastReservedCompletionTokens(),
                    bundle.contextState().getLastSummarizeAt(),
                    bundle.contextState().getLastSummarizeStatus(),
                    bundle.contextState().getActiveSummaryRevision(),
                    bundle.contextState().getLastBudgetSeverity(),
                    bundle.contextState().getLastUsageRatio(),
                    bundle.contextState().getUpdatedAt()
                );

            if (titleCollision) {
                planWarnings.add("Совпадение title: " + sourceConversation.getTitle());
            }
            if (planMessageCollisionCount > 0) {
                planWarnings.add("Переназначено messageId: " + planMessageCollisionCount);
            }
            if (action == ChatArchiveImportAction.SKIP) {
                planWarnings.add("Переписка пропущена из-за conflict policy.");
                skippedConversationCount++;
            }

            plans.add(new ChatArchiveImportConversationPlan(
                action,
                sourceConversationId,
                resolvedConversationId,
                sourceConversation.getTitle(),
                preparedMessages.size(),
                titleCollision,
                planMessageCollisionCount,
                planWarnings,
                conversationToPersist,
                preparedMessages,
                contextToPersist
            ));

            totalMessageCount += preparedMessages.size();
            messageIdCollisionCount += planMessageCollisionCount;
            warnings.addAll(planWarnings);

            if (action != ChatArchiveImportAction.SKIP) {
                reservedConversationIds.add(resolvedConversationId);
                if (normalizedTitle != null) {
                    reservedTitles.add(normalizedTitle);
                }
                for (ChatMessage message : preparedMessages) {
                    reservedMessageIds.add(message.getId());
                }
            }
        }

        int acceptedCount = (int) plans.stream().filter(ChatArchiveImportConversationPlan::importable).count();
        return new ChatArchiveImportPreview(
            format,
            options,
            plans.size(),
            acceptedCount,
            newConversationCount,
            conflictingConversationCount,
            titleCollisionCount,
            messageIdCollisionCount,
            totalMessageCount,
            skippedConversationCount,
            warnings,
            plans
        );
    }

    private void ensureJsonFormat(ChatArchiveFormat format) {
        if (format != ChatArchiveFormat.JSON) {
            throw new ChatArchiveImportValidationException("Импорт переписок поддерживает только JSON-архивы.");
        }
    }

    private void validateArchiveUniqueness(ChatArchiveDocument document) {
        Set<String> conversationIds = new LinkedHashSet<>();
        for (ChatArchiveBundle bundle : document.conversations()) {
            if (bundle == null || bundle.conversation() == null) {
                continue;
            }
            String conversationId = bundle.conversation().getId();
            if (!conversationIds.add(conversationId)) {
                throw new ChatArchiveImportValidationException(
                    "В архиве найден дубликат conversationId: " + conversationId
                );
            }
        }
    }

    private ChatArchiveImportAction resolveAction(boolean idConflict, ChatArchiveImportConflictPolicy policy) {
        if (!idConflict) {
            return ChatArchiveImportAction.CREATE;
        }
        return switch (policy) {
            case KEEP_BOTH -> ChatArchiveImportAction.KEEP_BOTH_CREATE;
            case REPLACE_EXISTING -> ChatArchiveImportAction.REPLACE;
            case SKIP_EXISTING -> ChatArchiveImportAction.SKIP;
        };
    }

    private String nextConversationId(String baseId, Set<String> reservedConversationIds) {
        String seed = hasText(baseId) ? baseId.trim() : "imported-conversation";
        int suffix = 1;
        String candidate = seed + "__imported__" + suffix;
        while (reservedConversationIds.contains(candidate)) {
            suffix++;
            candidate = seed + "__imported__" + suffix;
        }
        return candidate;
    }

    private String nextMessageId(
        String conversationId,
        int ordinal,
        Set<String> messageIdsReservedForPlan,
        Set<String> usedMessageIds
    ) {
        String base = (hasText(conversationId) ? conversationId.trim() : "imported-conversation") + "__msg__" + ordinal;
        String candidate = base;
        int suffix = 1;
        while (messageIdsReservedForPlan.contains(candidate) || usedMessageIds.contains(candidate)) {
            suffix++;
            candidate = base + "_" + suffix;
        }
        return candidate;
    }

    private String normalizeTitle(String title) {
        if (!hasText(title)) {
            return null;
        }
        return title.trim().toLowerCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
