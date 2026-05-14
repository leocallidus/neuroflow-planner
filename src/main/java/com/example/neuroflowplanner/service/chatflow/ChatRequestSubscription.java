package com.example.neuroflowplanner.service.chatflow;

/**
 * Subscription handle for chat request lifecycle observer.
 */
@FunctionalInterface
public interface ChatRequestSubscription extends AutoCloseable {
    @Override
    void close();
}
