package com.example.neuroflowplanner.ui.smartnotes;

import com.example.neuroflowplanner.service.notes.DefaultSmartNotesAiService;
import com.example.neuroflowplanner.service.notes.DefaultSmartNotesApplicationService;
import com.example.neuroflowplanner.service.notes.DefaultSmartNotesExportService;
import com.example.neuroflowplanner.service.notes.SmartNotesAiService;
import com.example.neuroflowplanner.service.notes.SmartNotesApplicationService;
import com.example.neuroflowplanner.service.notes.SmartNotesExportService;
import com.example.neuroflowplanner.service.search.DefaultGlobalSearchService;
import com.example.neuroflowplanner.service.search.GlobalSearchService;
import com.example.neuroflowplanner.service.task.DefaultTaskApplicationService;

public record SmartNotesServices(
    SmartNotesApplicationService applicationService,
    SmartNotesAiService aiService,
    SmartNotesExportService exportService,
    GlobalSearchService globalSearchService
) {
    public SmartNotesServices(
        SmartNotesApplicationService applicationService,
        SmartNotesAiService aiService,
        SmartNotesExportService exportService
    ) {
        this(
            applicationService,
            aiService,
            exportService,
            new DefaultGlobalSearchService(
                new DefaultTaskApplicationService(),
                applicationService == null ? new DefaultSmartNotesApplicationService() : applicationService
            )
        );
    }

    public static SmartNotesServices createDefault() {
        SmartNotesApplicationService applicationService = new DefaultSmartNotesApplicationService();
        return new SmartNotesServices(
            applicationService,
            new DefaultSmartNotesAiService(),
            new DefaultSmartNotesExportService(),
            new DefaultGlobalSearchService(
                new DefaultTaskApplicationService(),
                applicationService
            )
        );
    }
}
