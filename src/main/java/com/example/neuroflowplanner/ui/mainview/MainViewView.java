package com.example.neuroflowplanner.ui.mainview;

import javafx.scene.Node;

public final class MainViewView implements MainViewContract.View {
    private final LegacyMainView legacyView;
    private MainViewContract.Presenter presenter;

    public MainViewView(LegacyMainView legacyView) {
        this.legacyView = legacyView;
    }

    @Override
    public Node getRootNode() {
        return legacyView;
    }

    @Override
    public void bindPresenter(MainViewContract.Presenter presenter) {
        this.presenter = presenter;
        if (presenter instanceof MainViewPresenter mainViewPresenter) {
            legacyView.setPresenter(mainViewPresenter);
        }
    }

    @Override
    public void render(MainViewState state) {
        legacyView.applyState(state);
    }

    @Override
    public boolean canCloseApplication() {
        return legacyView.canCloseApplication();
    }

    @Override
    public boolean openTaskById(String taskId) {
        return legacyView.openTaskById(taskId);
    }

    @Override
    public boolean openNoteByTitle(String noteTitle) {
        return legacyView.openNoteByTitle(noteTitle);
    }

    public MainViewContract.Presenter getPresenter() {
        return presenter;
    }

    LegacyMainView getLegacyView() {
        return legacyView;
    }
}
