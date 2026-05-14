package com.example.neuroflowplanner.ui.commandpalette;

import com.example.neuroflowplanner.util.StructuredLogger;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinates overlay/dialog lifecycle so UI entry points use one deterministic manager.
 */
public final class OverlayDialogManager {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OverlayDialogManager.class);

    private final String contextName;
    private final Map<OverlayId, OverlayHandle> handles = new LinkedHashMap<>();
    private OverlayId activeOverlayId;

    public OverlayDialogManager(String contextName) {
        this.contextName = contextName == null || contextName.isBlank() ? "unknown" : contextName.trim();
    }

    public void register(OverlayId overlayId, OverlayHandle handle) {
        OverlayId safeId = Objects.requireNonNull(overlayId, "overlayId");
        OverlayHandle safeHandle = Objects.requireNonNull(handle, "handle");
        handles.put(safeId, safeHandle);
        LOG.info("ux.overlay.registered", "context", contextName, "overlayId", safeId.name());
    }

    public boolean isOpen(OverlayId overlayId) {
        OverlayHandle handle = handles.get(overlayId);
        return handle != null && handle.isOpen();
    }

    public void toggle(OverlayId overlayId, Window owner, OverlayRequest request) {
        OverlayHandle handle = requireHandle(overlayId);
        if (handle.isOpen()) {
            handle.close();
            if (overlayId == activeOverlayId) {
                activeOverlayId = null;
            }
            LOG.info("ux.overlay.closed", "context", contextName, "overlayId", overlayId.name(), "reason", "toggle");
            return;
        }
        open(overlayId, owner, request);
    }

    public void open(OverlayId overlayId, Window owner, OverlayRequest request) {
        OverlayHandle handle = requireHandle(overlayId);
        if (activeOverlayId != null && activeOverlayId != overlayId) {
            OverlayHandle activeHandle = handles.get(activeOverlayId);
            if (activeHandle != null && activeHandle.isOpen()) {
                activeHandle.close();
                LOG.info(
                    "ux.overlay.closed",
                    "context", contextName,
                    "overlayId", activeOverlayId.name(),
                    "reason", "switch_overlay"
                );
            }
        }
        handle.open(owner, request == null ? OverlayRequest.empty() : request);
        activeOverlayId = handle.isOpen() ? overlayId : null;
        LOG.info("ux.overlay.opened", "context", contextName, "overlayId", overlayId.name());
    }

    public void close(OverlayId overlayId) {
        OverlayHandle handle = handles.get(overlayId);
        if (handle == null || !handle.isOpen()) {
            return;
        }
        handle.close();
        if (overlayId == activeOverlayId) {
            activeOverlayId = null;
        }
        LOG.info("ux.overlay.closed", "context", contextName, "overlayId", overlayId.name(), "reason", "explicit_close");
    }

    private OverlayHandle requireHandle(OverlayId overlayId) {
        OverlayHandle handle = handles.get(overlayId);
        if (handle == null) {
            throw new IllegalStateException("Overlay is not registered: " + overlayId);
        }
        return handle;
    }

    public enum OverlayId {
        COMMAND_PALETTE,
        SHORTCUTS_HELP
    }

    public record OverlayRequest(String initialQuery) {
        public OverlayRequest {
            initialQuery = initialQuery == null ? "" : initialQuery.trim();
        }

        public static OverlayRequest empty() {
            return new OverlayRequest("");
        }
    }

    @FunctionalInterface
    public interface OverlayHandle {
        void open(Window owner, OverlayRequest request);

        default boolean isOpen() {
            return false;
        }

        default void close() {
            // Optional close support.
        }
    }
}

