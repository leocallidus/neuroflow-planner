package com.example.neuroflowplanner.service.chatflow;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.util.StructuredLogger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Thread-safe observer publisher for incremental chat response chunks.
 */
public final class ChatResponseChunkPublisher {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(ChatResponseChunkPublisher.class);

    private final CopyOnWriteArrayList<Consumer<ChatResponseChunk>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<ChatResponseChunk> lastChunk = new AtomicReference<>();

    public ChatRequestSubscription subscribe(Consumer<ChatResponseChunk> listener) {
        if (listener == null) {
            return () -> {
            };
        }
        listeners.add(listener);
        ChatResponseChunk snapshot = lastChunk.get();
        if (snapshot != null) {
            notifyListener(listener, snapshot);
        }
        return () -> listeners.remove(listener);
    }

    public void publish(ChatResponseChunk chunk) {
        if (chunk == null) {
            return;
        }
        lastChunk.set(chunk);
        for (Consumer<ChatResponseChunk> listener : listeners) {
            notifyListener(listener, chunk);
        }
    }

    public ChatResponseChunk getLastChunk() {
        return lastChunk.get();
    }

    private void notifyListener(Consumer<ChatResponseChunk> listener, ChatResponseChunk chunk) {
        try {
            listener.accept(chunk);
        } catch (RuntimeException ex) {
            LOG.warning(
                    "chat.response.chunk.listener.failed",
                    ErrorCode.UNEXPECTED_ERROR,
                    "listenerClass", listener.getClass().getName(),
                    "requestId", chunk.requestId(),
                    "terminal", String.valueOf(chunk.terminal()));
        }
    }
}
