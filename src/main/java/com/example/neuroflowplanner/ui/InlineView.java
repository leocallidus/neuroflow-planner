package com.example.neuroflowplanner.ui;

import javafx.scene.Node;

/**
 * Contract for inline panels hosted inside MainView overlay.
 */
public interface InlineView {
    /**
     * Provide the root node that should be embedded.
     */
    Node getContent();

    /**
     * Check if the view can be closed. Override to show confirmation dialogs.
     * @return true if close is allowed, false to prevent closing
     */
    default boolean canClose() {
        return true;
    }

    /**
     * Optional close callback invoked when overlay is dismissed.
     */
    default Runnable getOnClose() {
        return null;
    }

    /**
     * Optional submit callback for panels that commit changes.
     */
    default Runnable getOnSubmit() {
        return null;
    }

    /**
     * Optional title hint for overlay header.
     */
    default String getTitle() {
        return "";
    }

    /**
     * MainView can inject overlay close action to allow view to dismiss itself.
     */
    default void setCloseAction(Runnable closeAction) {
        // no-op by default
    }

    /**
     * Optional hint whether view performs blocking/export operations.
     */
    default boolean isBlocking() {
        return false;
    }

    /**
     * Optional hint to cleanup timers/resources on close.
     */
    default void onDispose() {
        // no-op
    }
}
