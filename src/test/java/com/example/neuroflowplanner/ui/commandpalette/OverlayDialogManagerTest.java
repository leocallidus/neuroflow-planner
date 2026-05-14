package com.example.neuroflowplanner.ui.commandpalette;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayDialogManagerTest {

    @Test
    void toggleClosesAlreadyOpenOverlay() {
        OverlayDialogManager manager = new OverlayDialogManager("test");
        StubHandle palette = new StubHandle();
        manager.register(OverlayDialogManager.OverlayId.COMMAND_PALETTE, palette);

        manager.toggle(OverlayDialogManager.OverlayId.COMMAND_PALETTE, null, OverlayDialogManager.OverlayRequest.empty());
        assertTrue(palette.opened.get());
        assertTrue(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));

        manager.toggle(OverlayDialogManager.OverlayId.COMMAND_PALETTE, null, OverlayDialogManager.OverlayRequest.empty());
        assertFalse(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));
        assertTrue(palette.closeCalls.get() >= 1);
    }

    @Test
    void openingDifferentOverlayClosesActiveTrackedOverlay() {
        OverlayDialogManager manager = new OverlayDialogManager("test");
        StubHandle palette = new StubHandle();
        StubHandle another = new StubHandle();
        manager.register(OverlayDialogManager.OverlayId.COMMAND_PALETTE, palette);
        manager.register(OverlayDialogManager.OverlayId.SHORTCUTS_HELP, another);

        manager.open(OverlayDialogManager.OverlayId.COMMAND_PALETTE, null, OverlayDialogManager.OverlayRequest.empty());
        assertTrue(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));

        manager.open(OverlayDialogManager.OverlayId.SHORTCUTS_HELP, null, OverlayDialogManager.OverlayRequest.empty());
        assertFalse(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));
        assertTrue(palette.closeCalls.get() >= 1);
    }

    @Test
    void openingSameOverlayTwiceDoesNotCloseActiveOverlay() {
        OverlayDialogManager manager = new OverlayDialogManager("test");
        StubHandle palette = new StubHandle();
        manager.register(OverlayDialogManager.OverlayId.COMMAND_PALETTE, palette);

        manager.open(OverlayDialogManager.OverlayId.COMMAND_PALETTE, null, new OverlayDialogManager.OverlayRequest("first"));
        manager.open(OverlayDialogManager.OverlayId.COMMAND_PALETTE, null, new OverlayDialogManager.OverlayRequest("second"));

        assertTrue(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));
        assertEquals(2, palette.openCalls.get());
        assertEquals(0, palette.closeCalls.get());
        assertEquals("second", palette.lastRequest.initialQuery());
    }

    @Test
    void openNullRequestIsNormalizedToEmptyRequest() {
        OverlayDialogManager manager = new OverlayDialogManager("test");
        StubHandle palette = new StubHandle();
        manager.register(OverlayDialogManager.OverlayId.COMMAND_PALETTE, palette);

        manager.open(OverlayDialogManager.OverlayId.COMMAND_PALETTE, null, null);

        assertTrue(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));
        assertTrue(palette.lastRequest != null);
        assertEquals("", palette.lastRequest.initialQuery());
    }

    @Test
    void closeInactiveOverlayIsNoop() {
        OverlayDialogManager manager = new OverlayDialogManager("test");
        StubHandle palette = new StubHandle();
        manager.register(OverlayDialogManager.OverlayId.COMMAND_PALETTE, palette);

        manager.close(OverlayDialogManager.OverlayId.COMMAND_PALETTE);

        assertFalse(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));
        assertEquals(0, palette.closeCalls.get());
    }

    private static final class StubHandle implements OverlayDialogManager.OverlayHandle {
        private final AtomicBoolean opened = new AtomicBoolean();
        private final AtomicInteger openCalls = new AtomicInteger();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private OverlayDialogManager.OverlayRequest lastRequest;

        @Override
        public void open(javafx.stage.Window owner, OverlayDialogManager.OverlayRequest request) {
            openCalls.incrementAndGet();
            lastRequest = request;
            opened.set(true);
        }

        @Override
        public boolean isOpen() {
            return opened.get();
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            opened.set(false);
        }
    }
}
