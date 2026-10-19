package eu.amadeos.games.blast.repository;

import eu.amadeos.gameengine.swing.RenderMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlastSettingsTest {
    @TempDir
    Path dir;

    @Test
    void defaultsAndChangesAreKept() throws Exception {
        BlastSettings settings = new BlastSettings(dir.resolve("blast.properties"));
        assertEquals(RenderMode.DIRECT, settings.renderMode());
        assertTrue(settings.sound());
        assertTrue(settings.computers());
        settings.setRenderMode(RenderMode.PIXEL);
        settings.setSound(false);
        settings.setComputers(false);
        BlastSettings again = new BlastSettings(dir.resolve("blast.properties"));
        assertEquals(RenderMode.PIXEL, again.renderMode());
        assertFalse(again.sound());
        assertFalse(again.computers());
        Files.writeString(dir.resolve("blast.properties"), "keys.1.bomb=SPACE CONTROL\n", StandardOpenOption.APPEND);
        assertArrayEquals(new int[]{KeyEvent.VK_SPACE, KeyEvent.VK_CONTROL}, again.keys("1.bomb", KeyEvent.VK_SPACE));
        assertArrayEquals(new int[]{KeyEvent.VK_ENTER}, again.keys("2.bomb", KeyEvent.VK_ENTER));
    }
}
