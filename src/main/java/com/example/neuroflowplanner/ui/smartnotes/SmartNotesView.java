package com.example.neuroflowplanner.ui.smartnotes;

import com.example.neuroflowplanner.model.Task;
import javafx.scene.Node;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SmartNotesView implements SmartNotesContract.View {
    private final LegacySmartNotesDialog legacyView;
    private SmartNotesContract.Presenter presenter;

    public SmartNotesView(LegacySmartNotesDialog legacyView) {
        this.legacyView = legacyView;
    }

    @Override
    public Node getRootNode() {
        return legacyView.getContent();
    }

    @Override
    public void bindPresenter(SmartNotesContract.Presenter presenter) {
        this.presenter = presenter;
        if (presenter instanceof SmartNotesPresenter smartNotesPresenter) {
            legacyView.setPresenter(smartNotesPresenter);
        }
    }

    @Override
    public void render(SmartNotesState state) {
        legacyView.applyState(state);
    }

    @Override
    public void refreshTheme() {
        legacyView.refreshTheme();
    }

    @Override
    public void setTaskResolver(Function<String, Task> resolver) {
        if (presenter instanceof SmartNotesPresenter smartNotesPresenter) {
            smartNotesPresenter.setTaskResolver(resolver);
        }
    }

    @Override
    public void setTaskNavigator(Consumer<String> navigator) {
        if (presenter instanceof SmartNotesPresenter smartNotesPresenter) {
            smartNotesPresenter.setTaskNavigator(navigator);
        }
    }

    @Override
    public void setTaskProvider(Supplier<List<Task>> provider) {
        if (presenter instanceof SmartNotesPresenter smartNotesPresenter) {
            smartNotesPresenter.setTaskProvider(provider);
        }
    }

    @Override
    public void openNoteByTitle(String title) {
        legacyView.openNoteByTitle(title);
    }

    @Override
    public Runnable getOnCloseAction() {
        return legacyView.getOnClose();
    }

    @Override
    public void setCloseAction(Runnable closeAction) {
        legacyView.setCloseAction(closeAction);
    }

    @Override
    public String getTitle() {
        return legacyView.getTitle();
    }

    public SmartNotesContract.Presenter getPresenter() {
        return presenter;
    }

    LegacySmartNotesDialog getLegacyView() {
        return legacyView;
    }
}
