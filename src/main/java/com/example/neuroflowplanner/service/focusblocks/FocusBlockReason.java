package com.example.neuroflowplanner.service.focusblocks;

public record FocusBlockReason(
        String title,
        String detail) {

    public FocusBlockReason {
        title = title == null ? "" : title.trim();
        detail = detail == null ? "" : detail.trim();
    }

    public boolean available() {
        return !title.isBlank() || !detail.isBlank();
    }
}
