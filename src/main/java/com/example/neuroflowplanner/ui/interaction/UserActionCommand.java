package com.example.neuroflowplanner.ui.interaction;

/**
 * Base contract for user-triggered actions that can be executed and optionally undone.
 */
public interface UserActionCommand {

    String actionId();

    String label();

    default String category() {
        return "general";
    }

    default boolean canExecute() {
        return true;
    }

    default boolean canUndo() {
        return true;
    }

    void execute();

    default void undo() {
        throw new UnsupportedOperationException("Undo is not supported for action: " + actionId());
    }
}
