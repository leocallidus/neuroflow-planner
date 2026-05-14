package com.example.neuroflowplanner.service.chatflow;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.util.StructuredLogger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Thread-safe publisher for chat request lifecycle events.
 */
public final class ChatRequestEventPublisher {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(ChatRequestEventPublisher.class);

    private final CopyOnWriteArrayList<Consumer<ChatRequestEvent>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<ChatRequestEvent> lastEvent = new AtomicReference<>();

    public ChatRequestSubscription subscribe(Consumer<ChatRequestEvent> listener) {
        if (listener == null) {
            return () -> {
            };
        }
        listeners.add(listener);
        ChatRequestEvent snapshot = lastEvent.get();
        if (snapshot != null) {
            notifyListener(listener, snapshot);
        }
        return () -> listeners.remove(listener);
    }

    public void publish(ChatRequestEvent event) {
        if (event == null) {
            return;
        }
        lastEvent.set(event);
        for (Consumer<ChatRequestEvent> listener : listeners) {
            notifyListener(listener, event);
        }
    }

    public ChatRequestEvent getLastEvent() {
        return lastEvent.get();
    }

    private void notifyListener(Consumer<ChatRequestEvent> listener, ChatRequestEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException ex) {
            LOG.warning(
                "chat.lifecycle.listener.failed",
                ErrorCode.UNEXPECTED_ERROR,
                "listenerClass", listener.getClass().getName(),
                "state", event.state().name(),
                "requestId", event.requestId()
            );
        }
    }
}
