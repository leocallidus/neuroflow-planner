package com.example.neuroflowplanner.ui;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageOutputOpenCommandTest {

    @Test
    void linuxUsesXdgOpen() throws Exception {
        List<String> command = invokeBuildCommand("Linux", Path.of("/tmp/test-image.png"));
        assertEquals("xdg-open", command.get(0));
        assertTrue(command.get(1).endsWith("/tmp/test-image.png"));
    }

    @Test
    void macUsesOpenCommand() throws Exception {
        List<String> command = invokeBuildCommand("Mac OS X", Path.of("/tmp/test-image.png"));
        assertEquals(List.of("open", "/tmp/test-image.png"), command);
    }

    @Test
    void windowsUsesExplorer() throws Exception {
        List<String> command = invokeBuildCommand("Windows 11", Path.of("C:/temp/test-image.png"));
        assertEquals("explorer.exe", command.get(0));
        assertTrue(command.get(1).contains("test-image.png"));
    }

    @Test
    void unsupportedOsReturnsEmptyCommand() throws Exception {
        List<String> command = invokeBuildCommand("Plan9", Path.of("/tmp/test-image.png"));
        assertTrue(command.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeBuildCommand(String osName, Path path) throws Exception {
        Method method = ChatBotDialog.class.getDeclaredMethod("buildGeneratedImageOpenCommand", String.class, Path.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, osName, path);
    }
}
