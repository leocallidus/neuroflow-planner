package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.error.ErrorContext;
import com.example.neuroflowplanner.ui.mainview.MainViewContract;
import com.example.neuroflowplanner.ui.mainview.MainViewFactory;
import com.example.neuroflowplanner.ui.mainview.MainViewState;
import com.example.neuroflowplanner.util.StructuredLogger;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

public class MainView extends BorderPane {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(MainView.class);

    private final MainViewContract.View view;
    private final MainViewContract.Presenter presenter;

    public MainView() {
        MainViewContract.View createdView;
        MainViewContract.Presenter createdPresenter;
        try {
            MainViewFactory.Assembly assembly = MainViewFactory.createDefault();
            createdView = assembly.view();
            createdPresenter = assembly.presenter();
            setCenter(createdView.getRootNode());
            createdPresenter.initialize();
            LOG.info(
                "mainview.adapter.initialized",
                "component", "MainView",
                "operation", "bootstrap"
            );
        } catch (Throwable throwable) {
            ErrorContext context = ErrorContext.of(
                "MainView",
                "bootstrap",
                "entryPoint", "MainView",
                "screen", "main"
            );
            try {
                UiErrorNotifier.showMappedError(
                    null,
                    false,
                    "Ошибка загрузки главного экрана",
                    throwable,
                    ErrorCode.UNEXPECTED_ERROR,
                    "Не удалось инициализировать главный экран.",
                    false,
                    context
                );
            } catch (Throwable notifyError) {
                LOG.error(
                    "mainview.adapter.bootstrap.notification.failed",
                    ErrorCode.UNEXPECTED_ERROR,
                    notifyError,
                    "component", "MainView",
                    "operation", "bootstrap",
                    "stage", "error-notification"
                );
            }
            FallbackAdapter fallback = new FallbackAdapter();
            createdView = fallback;
            createdPresenter = fallback;
            setCenter(fallback.getRootNode());
        }
        this.view = createdView;
        this.presenter = createdPresenter;
    }

    public boolean canCloseApplication() {
        return presenter.canCloseApplication();
    }

    private static final class FallbackAdapter implements MainViewContract.View, MainViewContract.Presenter {
        private final StackPane rootNode;

        private FallbackAdapter() {
            Label message = new Label(
                "Главный экран временно недоступен.\n"
                    + "Проверьте логи и перезапустите приложение."
            );
            message.getStyleClass().add("notes-status-label");
            rootNode = new StackPane(message);
            rootNode.getStyleClass().add("mainview-fallback");
        }

        @Override
        public Node getRootNode() {
            return rootNode;
        }

        @Override
        public void bindPresenter(MainViewContract.Presenter presenter) {
            // no-op
        }

        @Override
        public void render(MainViewState state) {
            // no-op
        }

        @Override
        public boolean canCloseApplication() {
            return true;
        }

        @Override
        public void initialize() {
            // no-op
        }
    }
}
