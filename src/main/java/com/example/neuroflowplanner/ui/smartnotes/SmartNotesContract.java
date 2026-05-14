package com.example.neuroflowplanner.ui.smartnotes;

import com.example.neuroflowplanner.model.Task;
import javafx.scene.Node;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface SmartNotesContract {

    interface View {
        Node getRootNode();

        void bindPresenter(Presenter presenter);

        void render(SmartNotesState state);

        void refreshTheme();

        void setTaskResolver(Function<String, Task> resolver);

        void setTaskNavigator(Consumer<String> navigator);

        void setTaskProvider(Supplier<List<Task>> provider);

        void openNoteByTitle(String title);

        Runnable getOnCloseAction();

        void setCloseAction(Runnable closeAction);

        String getTitle();
    }

    interface Presenter {
        void initialize();
    }
}
