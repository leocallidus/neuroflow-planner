package com.example.neuroflowplanner.ui.mainview;

import javafx.scene.Node;

public interface MainViewContract {

    interface View {
        Node getRootNode();

        void bindPresenter(Presenter presenter);

        void render(MainViewState state);

        boolean canCloseApplication();

        default boolean openTaskById(String taskId) {
            return false;
        }

        default boolean openNoteByTitle(String noteTitle) {
            return false;
        }
    }

    interface Presenter {
        void initialize();

        boolean canCloseApplication();
    }
}
