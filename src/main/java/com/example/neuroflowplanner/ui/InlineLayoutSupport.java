package com.example.neuroflowplanner.ui;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

/**
 * Shared helpers for inline dialogs embedded into the overlay host.
 */
public final class InlineLayoutSupport {

    private InlineLayoutSupport() {
    }

    public static void makeShrinkable(Region... regions) {
        if (regions == null) {
            return;
        }
        for (Region region : regions) {
            if (region == null) {
                continue;
            }
            region.setMinWidth(0);
            region.setMinHeight(0);
        }
    }

    public static ScrollPane createContentScroll(Region content, String... styleClasses) {
        makeShrinkable(content);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("inline-content-scroll");
        if (styleClasses != null) {
            for (String styleClass : styleClasses) {
                if (styleClass != null && !styleClass.isBlank()) {
                    scrollPane.getStyleClass().add(styleClass);
                }
            }
        }
        makeShrinkable(scrollPane);
        return scrollPane;
    }
}
