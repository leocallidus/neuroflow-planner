package com.example.neuroflowplanner.util;

public final class TaskDependencyConfigDefaults {
    public static final String CONFIG_TASK_DEPENDENCIES_MODE = "task.dependencies.mode";

    public static final String MODE_LEGACY = "legacy";
    public static final String MODE_DUAL = "dual";
    public static final String MODE_NORMALIZED = "normalized";

    // Stage-7 default: normalized graph storage only.
    public static final String TASK_DEPENDENCIES_MODE_DEFAULT = MODE_NORMALIZED;

    private TaskDependencyConfigDefaults() {
    }
}
