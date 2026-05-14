package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.service.notes.DefaultSmartNotesApplicationService;
import com.example.neuroflowplanner.service.search.DefaultGlobalSearchService;
import com.example.neuroflowplanner.service.search.GlobalSearchService;
import com.example.neuroflowplanner.service.task.DefaultTaskAnalysisService;
import com.example.neuroflowplanner.service.task.DefaultTaskApplicationService;
import com.example.neuroflowplanner.service.task.DefaultTaskExportService;
import com.example.neuroflowplanner.service.task.TaskAnalysisService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import com.example.neuroflowplanner.service.task.TaskExportService;

public record MainViewServices(
    TaskApplicationService taskApplicationService,
    TaskAnalysisService taskAnalysisService,
    TaskExportService taskExportService,
    GlobalSearchService globalSearchService
) {
    public MainViewServices(
        TaskApplicationService taskApplicationService,
        TaskAnalysisService taskAnalysisService,
        TaskExportService taskExportService
    ) {
        this(
            taskApplicationService,
            taskAnalysisService,
            taskExportService,
            new DefaultGlobalSearchService(
                taskApplicationService == null ? new DefaultTaskApplicationService() : taskApplicationService,
                new DefaultSmartNotesApplicationService()
            )
        );
    }

    public static MainViewServices createDefault() {
        TaskApplicationService taskApplicationService = new DefaultTaskApplicationService();
        return new MainViewServices(
            taskApplicationService,
            new DefaultTaskAnalysisService(),
            new DefaultTaskExportService(),
            new DefaultGlobalSearchService(
                taskApplicationService,
                new DefaultSmartNotesApplicationService()
            )
        );
    }
}
