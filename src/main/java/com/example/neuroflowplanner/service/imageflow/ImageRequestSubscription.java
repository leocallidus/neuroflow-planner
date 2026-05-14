package com.example.neuroflowplanner.service.imageflow;

/**
 * Subscription handle for image request lifecycle observer.
 */
@FunctionalInterface
public interface ImageRequestSubscription extends AutoCloseable {
    @Override
    void close();
}
