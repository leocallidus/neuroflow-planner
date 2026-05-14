package com.example.neuroflowplanner.service.imageflow;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.util.StructuredLogger;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Thread-safe publisher for image request lifecycle events.
 */
public final class ImageRequestEventPublisher {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(ImageRequestEventPublisher.class);

    private final CopyOnWriteArrayList<Consumer<ImageRequestEvent>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<ImageRequestEvent> lastEvent = new AtomicReference<>();

    public ImageRequestSubscription subscribe(Consumer<ImageRequestEvent> listener) {
        if (listener == null) {
            return () -> {
            };
        }
        listeners.add(listener);
        ImageRequestEvent snapshot = lastEvent.get();
        if (snapshot != null) {
            notifyListener(listener, snapshot);
        }
        return () -> listeners.remove(listener);
    }

    public void publish(ImageRequestEvent event) {
        if (event == null) {
            return;
        }
        lastEvent.set(event);
        for (Consumer<ImageRequestEvent> listener : listeners) {
            notifyListener(listener, event);
        }
    }

    public ImageRequestEvent getLastEvent() {
        return lastEvent.get();
    }

    private void notifyListener(Consumer<ImageRequestEvent> listener, ImageRequestEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException ex) {
            LOG.warning(
                "image.lifecycle.listener.failed",
                ErrorCode.UNEXPECTED_ERROR,
                "listenerClass", listener.getClass().getName(),
                "state", event.state().name(),
                "jobId", event.jobId(),
                "requestId", event.requestId()
            );
        }
    }
}
