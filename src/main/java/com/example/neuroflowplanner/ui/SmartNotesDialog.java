package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.error.ErrorContext;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.ui.smartnotes.SmartNotesContract;
import com.example.neuroflowplanner.ui.smartnotes.SmartNotesFactory;
import com.example.neuroflowplanner.ui.smartnotes.SmartNotesState;
import com.example.neuroflowplanner.util.StructuredLogger;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SmartNotesDialog implements InlineView {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SmartNotesDialog.class);
    private static SmartNotesDialog instance;

    private final SmartNotesContract.View view;
    private final SmartNotesContract.Presenter presenter;

    private SmartNotesDialog() {
        SmartNotesContract.View createdView;
        SmartNotesContract.Presenter createdPresenter;
        try {
            SmartNotesFactory.Assembly assembly = SmartNotesFactory.createDefault();
            createdView = assembly.view();
            createdPresenter = assembly.presenter();
            createdPresenter.initialize();
            LOG.info(
                "smartnotes.adapter.initialized",
                "component", "SmartNotesDialog",
                "operation", "bootstrap"
            );
        } catch (Throwable throwable) {
            ErrorContext context = ErrorContext.of(
                "SmartNotesDialog",
                "bootstrap",
                "entryPoint", "SmartNotesDialog.inline",
                "screen", "smartnotes"
            );
            try {
                UiErrorNotifier.showMappedError(
                    null,
                    false,
                    "Ошибка загрузки заметок",
                    throwable,
                    ErrorCode.UNEXPECTED_ERROR,
                    "Не удалось инициализировать экран умных заметок.",
                    false,
                    context
                );
            } catch (Throwable notifyError) {
                LOG.error(
                    "smartnotes.adapter.bootstrap.notification.failed",
                    ErrorCode.UNEXPECTED_ERROR,
                    notifyError,
                    "component", "SmartNotesDialog",
                    "operation", "bootstrap",
                    "stage", "error-notification"
                );
            }
            FallbackAdapter fallback = new FallbackAdapter();
            createdView = fallback;
            createdPresenter = fallback;
        }
        this.view = createdView;
        this.presenter = createdPresenter;
    }

    public static synchronized InlineView inline() {
        if (instance == null) {
            instance = new SmartNotesDialog();
        }
        instance.view.refreshTheme();
        return instance;
    }

    public void setTaskResolver(Function<String, Task> resolver) {
        view.setTaskResolver(resolver);
    }

    public void setTaskNavigator(Consumer<String> navigator) {
        view.setTaskNavigator(navigator);
    }

    public void setTaskProvider(Supplier<List<Task>> provider) {
        view.setTaskProvider(provider);
    }

    public void openNoteByTitle(String title) {
        view.openNoteByTitle(title);
    }

    @Override
    public Node getContent() {
        return view.getRootNode();
    }

    @Override
    public Runnable getOnClose() {
        return view.getOnCloseAction();
    }

    @Override
    public void setCloseAction(Runnable closeAction) {
        view.setCloseAction(closeAction);
    }

    @Override
    public String getTitle() {
        return view.getTitle();
    }

    private static final class FallbackAdapter implements SmartNotesContract.View, SmartNotesContract.Presenter {
        private final StackPane rootNode;
        private Runnable closeAction;

        private FallbackAdapter() {
            Label message = new Label(
                "Умные заметки временно недоступны.\n"
                    + "Проверьте логи и перезапустите приложение."
            );
            message.getStyleClass().add("notes-status-label");
            rootNode = new StackPane(message);
            rootNode.getStyleClass().add("smartnotes-fallback");
        }

        @Override
        public Node getRootNode() {
            return rootNode;
        }

        @Override
        public void bindPresenter(SmartNotesContract.Presenter presenter) {
            // no-op
        }

        @Override
        public void render(SmartNotesState state) {
            // no-op
        }

        @Override
        public void refreshTheme() {
            // no-op
        }

        @Override
        public void setTaskResolver(Function<String, Task> resolver) {
            // no-op
        }

        @Override
        public void setTaskNavigator(Consumer<String> navigator) {
            // no-op
        }

        @Override
        public void setTaskProvider(Supplier<List<Task>> provider) {
            // no-op
        }

        @Override
        public void openNoteByTitle(String title) {
            // no-op
        }

        @Override
        public Runnable getOnCloseAction() {
            return closeAction;
        }

        @Override
        public void setCloseAction(Runnable closeAction) {
            this.closeAction = closeAction;
        }

        @Override
        public String getTitle() {
            return "Умные заметки";
        }

        @Override
        public void initialize() {
            // no-op
        }
    }
}
