package com.example.neuroflowplanner.ui.layout;

import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

/**
 * Main shell component with explicit zones:
 * left rail, center workspace, right context drawer.
 */
public final class MainLayoutShell {
    private final BorderPane root = new BorderPane();

    public MainLayoutShell() {
        root.getStyleClass().add("main-layout-shell");
    }

    public BorderPane root() {
        return root;
    }

    public void setLeftRail(Node node) {
        root.setLeft(node);
    }

    public void setCenterWorkspace(Node node) {
        root.setCenter(node);
    }

    public void setRightContextDrawer(Node node) {
        root.setRight(node);
    }

    public Node rightContextDrawer() {
        return root.getRight();
    }
}

