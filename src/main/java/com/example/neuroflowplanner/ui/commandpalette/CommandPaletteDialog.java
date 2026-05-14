package com.example.neuroflowplanner.ui.commandpalette;

import com.example.neuroflowplanner.ui.interaction.ShortcutRegistry;
import com.example.neuroflowplanner.ui.layout.AdaptiveLayoutService;
import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteViewMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.NavSurfaceHeightBand;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.beans.value.ChangeListener;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.net.URL;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CommandPaletteDialog {
    private static final int DEFAULT_LIMIT = 18;
    private static final double WIDTH_COMPACT = 620.0;
    private static final double WIDTH_NORMAL = 760.0;
    private static final double WIDTH_WIDE = 860.0;
    private static final double HEIGHT_COMPACT = 430.0;
    private static final double HEIGHT_NORMAL = 520.0;
    private static final double HEIGHT_WIDE = 560.0;
    private static final double MIN_WIDTH = 520.0;
    private static final double MIN_HEIGHT = 340.0;
    private static final double HARD_MIN_WIDTH = 360.0;
    private static final double HARD_MIN_HEIGHT = 220.0;

    private final String title;
    private final CommandPaletteController controller;
    private final AdaptiveLayoutService adaptiveLayoutService = new AdaptiveLayoutService();
    private Dialog<Void> activeDialog;
    private CommandPaletteView activePaletteView;
    private Function<String, Boolean> sidebarRevealHandler;
    private boolean closeOnFocusLoss = true;
    private Scene activeDialogScene;
    private Window activeDialogWindow;
    private EventHandler<KeyEvent> activeDialogKeyHandler;
    private ChangeListener<Boolean> activeDialogFocusListener;
    private Consumer<Boolean> openStateListener;
    private Consumer<CommandPaletteViewMode> paletteViewModeListener;
    private Consumer<String> helperHintDismissListener;
    private CommandPaletteDisplayPolicy activeDisplayPolicy;
    private Window activeOwnerWindow;
    private ChangeListener<Number> activeOwnerWidthListener;
    private ChangeListener<Number> activeOwnerHeightListener;
    private Node previousOwnerFocusNode;

    public CommandPaletteDialog(String title, CommandPaletteController controller) {
        this.title = title == null || title.isBlank() ? "Командная палитра" : title.trim();
        this.controller = controller;
    }

    public void toggle(Window owner) {
        toggle(owner, null);
    }

    public void toggle(Window owner, String initialQuery) {
        if (isOpen()) {
            close();
            return;
        }
        open(owner, initialQuery);
    }

    public boolean isOpen() {
        return activeDialog != null && activeDialog.isShowing();
    }

    public void setCloseOnFocusLoss(boolean closeOnFocusLoss) {
        this.closeOnFocusLoss = closeOnFocusLoss;
    }

    public void setOpenStateListener(Consumer<Boolean> openStateListener) {
        this.openStateListener = openStateListener;
    }

    public void setPaletteViewModeListener(Consumer<CommandPaletteViewMode> paletteViewModeListener) {
        this.paletteViewModeListener = paletteViewModeListener;
        if (activePaletteView != null) {
            activePaletteView.setPaletteViewModeListener(paletteViewModeListener);
        }
    }

    public void setHelperHintDismissListener(Consumer<String> helperHintDismissListener) {
        this.helperHintDismissListener = helperHintDismissListener;
        if (activePaletteView != null) {
            activePaletteView.setHelperHintDismissListener(helperHintDismissListener);
        }
    }

    public void close() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::close);
            return;
        }
        Dialog<Void> dialog = activeDialog;
        if (dialog == null) {
            return;
        }
        try {
            Window dialogWindow = resolveDialogWindow(dialog);
            if (dialogWindow != null && dialogWindow.isShowing()) {
                dialogWindow.hide();
            } else if (dialog.isShowing()) {
                dialog.close();
            }
        } finally {
            detachLifecycleHandlers();
            if (activeDialog == dialog) {
                activeDialog = null;
                activePaletteView = null;
            }
            notifyOpenState(false);
        }
    }

    public void open(Window owner) {
        open(owner, null);
    }

    public void open(Window owner, String initialQuery) {
        if (activeDialog != null && activeDialog.isShowing()) {
            Platform.runLater(() -> {
                if (activePaletteView != null) {
                    if (initialQuery != null && !initialQuery.isBlank()) {
                        activePaletteView.setInitialQuery(initialQuery);
                    }
                    activePaletteView.activate();
                } else if (activeDialog.getDialogPane() != null) {
                    activeDialog.getDialogPane().requestFocus();
                }
            });
            return;
        }

        CommandPaletteView paletteView = new CommandPaletteView(controller, DEFAULT_LIMIT);
        paletteView.setInitialQuery(initialQuery);
        paletteView.setSidebarRevealHandler(sidebarRevealHandler);
        paletteView.setPaletteViewModeListener(paletteViewModeListener);
        paletteView.setHelperHintDismissListener(helperHintDismissListener);
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initModality(Modality.NONE);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText(null);
        pane.setGraphic(null);
        pane.getButtonTypes().clear();
        pane.setContent(paletteView);
        pane.getStyleClass().addAll("command-palette-dialog-pane", "overlay-host", "adaptive-overlay-host");
        pane.setMinSize(0, 0);
        UiLayoutBreakpoint breakpoint = resolveBreakpoint(owner);
        UiLayoutMode densityMode = UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode());
        applyAdaptiveDialogClasses(pane, breakpoint, densityMode, null);
        paletteView.applyAdaptiveMode(breakpoint, densityMode);
        paletteView.applyDisplayPolicy(null);
        previousOwnerFocusNode = resolveOwnerFocusNode(owner);
        activeOwnerWindow = owner;
        activeDisplayPolicy = null;
        pane.setPrefSize(resolveDialogWidth(owner, breakpoint), resolveDialogHeight(owner, breakpoint));
        dialog.setResizable(false);
        applyStyles(pane);

        paletteView.setCloseAction(this::close);
        dialog.setOnHidden(event -> {
            detachLifecycleHandlers();
            if (activeDialog == dialog) {
                activeDialog = null;
                activePaletteView = null;
            }
            restoreOwnerFocus();
            notifyOpenState(false);
        });
        activeDialog = dialog;
        activePaletteView = paletteView;

        dialog.show();
        attachLifecycleHandlers(dialog);
        attachOwnerResizeHandlers(owner);
        notifyOpenState(true);
        Platform.runLater(paletteView::activate);
    }

    public void setSidebarRevealHandler(Function<String, Boolean> sidebarRevealHandler) {
        this.sidebarRevealHandler = sidebarRevealHandler;
        if (activePaletteView != null) {
            activePaletteView.setSidebarRevealHandler(sidebarRevealHandler);
        }
    }

    public void applyDisplayPolicy(CommandPaletteDisplayPolicy policy, Window owner) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> applyDisplayPolicy(policy, owner));
            return;
        }
        if (activeDialog == null || activePaletteView == null) {
            return;
        }
        activeDisplayPolicy = policy;
        if (owner != null) {
            activeOwnerWindow = owner;
        }
        DialogPane pane = activeDialog.getDialogPane();
        if (pane == null) {
            return;
        }
        UiLayoutBreakpoint breakpoint = policy == null ? resolveBreakpoint(owner) : policy.breakpoint();
        UiLayoutMode densityMode = policy == null
            ? UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode())
            : policy.densityMode();
        NavSurfaceHeightBand heightBand = policy == null
            ? NavSurfaceHeightBand.fromHeight(owner == null ? HEIGHT_NORMAL : owner.getHeight())
            : policy.heightBand();
        applyAdaptiveDialogClasses(pane, breakpoint, densityMode, heightBand);
        activePaletteView.applyAdaptiveMode(breakpoint, densityMode);
        activePaletteView.applyDisplayPolicy(policy);
        pane.setPrefSize(
            resolveDialogWidth(owner, breakpoint),
            resolveDialogHeight(owner, breakpoint, heightBand)
        );
        pane.requestLayout();
    }

    private UiLayoutBreakpoint resolveBreakpoint(Window owner) {
        double width = owner == null ? WIDTH_NORMAL : owner.getWidth();
        if (!Double.isFinite(width) || width <= 0.0) {
            width = WIDTH_NORMAL;
        }
        return adaptiveLayoutService.resolveBreakpoint(width);
    }

    private double resolveDialogWidth(Window owner, UiLayoutBreakpoint breakpoint) {
        double targetWidth = switch (breakpoint) {
            case COMPACT -> WIDTH_COMPACT;
            case NORMAL -> WIDTH_NORMAL;
            case WIDE -> WIDTH_WIDE;
        };
        if (owner == null || !Double.isFinite(owner.getWidth()) || owner.getWidth() <= 0.0) {
            return Math.max(HARD_MIN_WIDTH, targetWidth);
        }
        double maxByOwner = Math.max(HARD_MIN_WIDTH, owner.getWidth() - 56.0);
        double dynamicMin = Math.min(MIN_WIDTH, maxByOwner);
        return clamp(targetWidth, dynamicMin, maxByOwner);
    }

    private double resolveDialogHeight(Window owner, UiLayoutBreakpoint breakpoint) {
        return resolveDialogHeight(owner, breakpoint, null);
    }

    private double resolveDialogHeight(Window owner, UiLayoutBreakpoint breakpoint, NavSurfaceHeightBand heightBand) {
        double targetHeight = switch (breakpoint) {
            case COMPACT -> HEIGHT_COMPACT;
            case NORMAL -> HEIGHT_NORMAL;
            case WIDE -> HEIGHT_WIDE;
        };
        if (heightBand == NavSurfaceHeightBand.LOW_HEIGHT) {
            targetHeight = Math.max(MIN_HEIGHT, targetHeight - 70.0);
        } else if (heightBand == NavSurfaceHeightBand.VERY_LOW_HEIGHT) {
            targetHeight = Math.max(MIN_HEIGHT, targetHeight - 120.0);
        }
        if (owner == null || !Double.isFinite(owner.getHeight()) || owner.getHeight() <= 0.0) {
            return Math.max(HARD_MIN_HEIGHT, targetHeight);
        }
        double maxByOwner = Math.max(HARD_MIN_HEIGHT, owner.getHeight() - 84.0);
        double dynamicMin = Math.min(MIN_HEIGHT, maxByOwner);
        return clamp(targetHeight, dynamicMin, maxByOwner);
    }

    private void applyAdaptiveDialogClasses(
        DialogPane pane,
        UiLayoutBreakpoint breakpoint,
        UiLayoutMode densityMode,
        NavSurfaceHeightBand heightBand
    ) {
        if (pane == null) {
            return;
        }
        pane.getStyleClass().removeAll(
            "layout-breakpoint-compact",
            "layout-breakpoint-normal",
            "layout-breakpoint-wide",
            "layout-density-compact",
            "layout-density-comfortable",
            "layout-height-tall",
            "layout-height-low",
            "layout-height-very-low"
        );
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        UiLayoutMode safeDensity = densityMode == null ? UiLayoutMode.COMFORTABLE : densityMode;
        NavSurfaceHeightBand safeHeightBand = heightBand == null ? NavSurfaceHeightBand.TALL : heightBand;
        pane.getStyleClass().add(switch (safeBreakpoint) {
            case COMPACT -> "layout-breakpoint-compact";
            case NORMAL -> "layout-breakpoint-normal";
            case WIDE -> "layout-breakpoint-wide";
        });
        pane.getStyleClass().add(safeDensity == UiLayoutMode.COMPACT
            ? "layout-density-compact"
            : "layout-density-comfortable");
        pane.getStyleClass().add(switch (safeHeightBand) {
            case TALL -> "layout-height-tall";
            case LOW_HEIGHT -> "layout-height-low";
            case VERY_LOW_HEIGHT -> "layout-height-very-low";
        });
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private void applyStyles(DialogPane pane) {
        URL appCss = getClass().getResource("/styles/app.css");
        if (appCss != null) {
            pane.getStylesheets().add(appCss.toExternalForm());
        }
        if (ConfigManager.isDarkTheme()) {
            URL darkCss = getClass().getResource("/styles/dark-theme.css");
            if (darkCss != null) {
                pane.getStylesheets().add(darkCss.toExternalForm());
            }
        }
    }

    private void attachLifecycleHandlers(Dialog<Void> dialog) {
        detachLifecycleHandlers();
        if (dialog == null || dialog.getDialogPane() == null) {
            return;
        }
        Scene scene = dialog.getDialogPane().getScene();
        if (scene == null) {
            return;
        }
        activeDialogScene = scene;
        activeDialogKeyHandler = event -> {
            if (event == null || event.isConsumed()) {
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE || ShortcutRegistry.isCommandPaletteToggleShortcut(event)) {
                event.consume();
                close();
            }
        };
        activeDialogScene.addEventFilter(KeyEvent.KEY_PRESSED, activeDialogKeyHandler);

        Window window = scene.getWindow();
        if (window == null) {
            return;
        }
        activeDialogWindow = window;
        activeDialogFocusListener = (obs, wasFocused, focused) -> {
            if (closeOnFocusLoss && Boolean.FALSE.equals(focused)) {
                Platform.runLater(this::close);
            }
        };
        activeDialogWindow.focusedProperty().addListener(activeDialogFocusListener);
    }

    private void detachLifecycleHandlers() {
        if (activeDialogScene != null && activeDialogKeyHandler != null) {
            activeDialogScene.removeEventFilter(KeyEvent.KEY_PRESSED, activeDialogKeyHandler);
        }
        if (activeDialogWindow != null && activeDialogFocusListener != null) {
            activeDialogWindow.focusedProperty().removeListener(activeDialogFocusListener);
        }
        detachOwnerResizeHandlers();
        activeDialogScene = null;
        activeDialogWindow = null;
        activeDialogKeyHandler = null;
        activeDialogFocusListener = null;
    }

    private Window resolveDialogWindow(Dialog<Void> dialog) {
        if (dialog == null || dialog.getDialogPane() == null) {
            return null;
        }
        Scene scene = dialog.getDialogPane().getScene();
        if (scene == null) {
            return null;
        }
        return scene.getWindow();
    }

    private void notifyOpenState(boolean open) {
        if (openStateListener == null) {
            return;
        }
        try {
            openStateListener.accept(open);
        } catch (RuntimeException ignored) {
            // Best-effort UI signal only.
        }
    }

    private Node resolveOwnerFocusNode(Window owner) {
        if (owner == null || owner.getScene() == null) {
            return null;
        }
        return owner.getScene().getFocusOwner();
    }

    private void restoreOwnerFocus() {
        Node target = previousOwnerFocusNode;
        previousOwnerFocusNode = null;
        if (target == null) {
            return;
        }
        Platform.runLater(() -> {
            try {
                if (target.getScene() != null) {
                    target.requestFocus();
                }
            } catch (RuntimeException ignored) {
                // Best-effort focus restore.
            }
        });
    }

    private void attachOwnerResizeHandlers(Window owner) {
        detachOwnerResizeHandlers();
        if (owner == null) {
            return;
        }
        activeOwnerWindow = owner;
        activeOwnerWidthListener = (obs, oldValue, newValue) -> scheduleOwnerResizeRefresh();
        activeOwnerHeightListener = (obs, oldValue, newValue) -> scheduleOwnerResizeRefresh();
        owner.widthProperty().addListener(activeOwnerWidthListener);
        owner.heightProperty().addListener(activeOwnerHeightListener);
    }

    private void detachOwnerResizeHandlers() {
        if (activeOwnerWindow != null && activeOwnerWidthListener != null) {
            activeOwnerWindow.widthProperty().removeListener(activeOwnerWidthListener);
        }
        if (activeOwnerWindow != null && activeOwnerHeightListener != null) {
            activeOwnerWindow.heightProperty().removeListener(activeOwnerHeightListener);
        }
        activeOwnerWidthListener = null;
        activeOwnerHeightListener = null;
        activeOwnerWindow = null;
    }

    private void scheduleOwnerResizeRefresh() {
        Platform.runLater(() -> {
            if (activeDialog == null || activePaletteView == null || !isOpen()) {
                return;
            }
            applyDisplayPolicy(activeDisplayPolicy, activeOwnerWindow);
        });
    }
}
