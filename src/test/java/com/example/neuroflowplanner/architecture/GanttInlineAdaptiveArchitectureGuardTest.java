package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GanttInlineAdaptiveArchitectureGuardTest {
    private static final Path GANTT_DIALOG = Path.of(
            "src/main/java/com/example/neuroflowplanner/ui/GanttChartDialog.java"
    );
    private static final Path APP_CSS = Path.of(
            "src/main/resources/styles/app.css"
    );
    private static final Path DARK_CSS = Path.of(
            "src/main/resources/styles/dark-theme.css"
    );

    @Test
    void ganttDialogMustStayBoundToOverlayAdaptiveContract() throws IOException {
        String content = Files.readString(GANTT_DIALOG);

        assertTrue(content.contains("findAncestorWithStyleClass(root, \"overlay-container\")"));
        assertTrue(content.contains("inline-overlay-width-compact"));
        assertTrue(content.contains("inline-overlay-width-very-compact"));
        assertTrue(content.contains("inline-overlay-height-low"));
        assertTrue(content.contains("inline-overlay-height-very-low"));
        assertTrue(content.contains("resolveDensityConfig(adaptiveContext, dateRange, rows.size())"));
    }

    @Test
    void ganttCssMustKeepCoreSelectorsAndDensityVariants() throws IOException {
        String appCss = Files.readString(APP_CSS);
        String darkCss = Files.readString(DARK_CSS);

        for (String selector : List.of(
                ".gantt-root",
                ".gantt-header-panel",
                ".gantt-scroll-pane",
                ".gantt-row",
                ".gantt-bar",
                ".gantt-root.gantt-density-compact",
                ".gantt-root.gantt-density-very-compact"
        )) {
            assertTrue(appCss.contains(selector), "Missing selector in app.css: " + selector);
        }

        for (String selector : List.of(
                ".gantt-root",
                ".gantt-header-panel",
                ".gantt-row",
                ".gantt-bar",
                ".gantt-focusable-row:focused",
                ".gantt-focusable-bar:focused",
                ".gantt-root.gantt-density-compact",
                ".gantt-root.gantt-density-very-compact"
        )) {
            assertTrue(darkCss.contains(selector), "Missing selector in dark-theme.css: " + selector);
        }
    }
}
