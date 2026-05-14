package com.example.neuroflowplanner.ui.mainview;

public final class MainViewFactory {
    private MainViewFactory() {
    }

    public static Assembly createDefault() {
        return create(MainViewServices.createDefault());
    }

    static Assembly create(MainViewServices services) {
        MainViewView view = new MainViewView(new LegacyMainView());
        MainViewPresenter presenter = new MainViewPresenter(view, services);
        view.bindPresenter(presenter);
        return new Assembly(view, presenter, services);
    }

    public record Assembly(
        MainViewContract.View view,
        MainViewContract.Presenter presenter,
        MainViewServices services
    ) {
    }
}
