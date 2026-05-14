package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.util.TaskDependencyConfigDefaults;

import java.util.Locale;

public final class TaskDependencyModeResolver {
    private TaskDependencyModeResolver() {
    }

    public static TaskDependencyMode resolve(String rawMode) {
        if (rawMode == null) {
            return TaskDependencyMode.NORMALIZED;
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case TaskDependencyConfigDefaults.MODE_LEGACY -> TaskDependencyMode.LEGACY;
            case TaskDependencyConfigDefaults.MODE_NORMALIZED -> TaskDependencyMode.NORMALIZED;
            case TaskDependencyConfigDefaults.MODE_DUAL -> TaskDependencyMode.DUAL;
            default -> TaskDependencyMode.NORMALIZED;
        };
    }
}
