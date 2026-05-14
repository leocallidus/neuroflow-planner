package com.example.neuroflowplanner.model;

public record TaskDependencyEdge(String dependentTaskId, String blockerTaskId) {
}
