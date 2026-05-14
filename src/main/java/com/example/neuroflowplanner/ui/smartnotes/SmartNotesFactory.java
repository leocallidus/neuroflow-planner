package com.example.neuroflowplanner.ui.smartnotes;

public final class SmartNotesFactory {
    private SmartNotesFactory() {
    }

    public static Assembly createDefault() {
        return create(SmartNotesServices.createDefault());
    }

    static Assembly create(SmartNotesServices services) {
        LegacySmartNotesDialog legacyView = (LegacySmartNotesDialog) LegacySmartNotesDialog.inline();
        SmartNotesView view = new SmartNotesView(legacyView);
        SmartNotesPresenter presenter = new SmartNotesPresenter(view, services);
        view.bindPresenter(presenter);
        return new Assembly(view, presenter, services);
    }

    public record Assembly(
        SmartNotesContract.View view,
        SmartNotesContract.Presenter presenter,
        SmartNotesServices services
    ) {
    }
}
