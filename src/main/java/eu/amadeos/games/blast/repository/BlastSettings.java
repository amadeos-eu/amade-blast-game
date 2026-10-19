package eu.amadeos.games.blast.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.gameengine.swing.RenderMode;

import javax.swing.KeyStroke;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The settings in {@code ~/.config/amadeos/blast/blast.properties}: the graphics mode, the sound, whether
 * computer players fill the empty seats, and the keys of the two players at the keyboard. Keys are written
 * as Java names them, several separated by spaces, e.g. {@code keys.1.bomb=SPACE CONTROL}.
 */
public final class BlastSettings {
    private static final String GRAPHICS = "graphics";
    private static final String SOUND = "sound";
    private static final String COMPUTERS = "computers";

    private final Path file;

    @Inject
    public BlastSettings(AppDirectories directories) {
        this(directories.configFile("blast.properties"));
    }

    public BlastSettings(Path file) {
        this.file = file;
    }

    public RenderMode renderMode() {
        return RenderMode.ofId(PropertiesFile.load(file).getProperty(GRAPHICS, "")).orElse(RenderMode.DIRECT);
    }

    public void setRenderMode(RenderMode mode) {
        set(GRAPHICS, mode.id());
    }

    public boolean sound() {
        return !"off".equals(PropertiesFile.load(file).getProperty(SOUND, "on"));
    }

    public void setSound(boolean on) {
        set(SOUND, on ? "on" : "off");
    }

    /** True if computer players take the seats nobody takes. */
    public boolean computers() {
        return !"off".equals(PropertiesFile.load(file).getProperty(COMPUTERS, "on"));
    }

    public void setComputers(boolean on) {
        set(COMPUTERS, on ? "on" : "off");
    }

    private void set(String key, String value) {
        Properties properties = PropertiesFile.load(file);
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-blast-game settings");
    }

    /**
     * The keys of an action, e.g. {@code keys("1.left", KeyEvent.VK_A)} for the first player at the keyboard:
     * those in the file if it names any it understands, else the defaults.
     */
    public int[] keys(String action, int... defaults) {
        String names = PropertiesFile.load(file).getProperty("keys." + action, "").strip();
        List<Integer> codes = new ArrayList<>();
        for (String name : names.split("\\s+")) {
            if (name.isEmpty()) continue;
            KeyStroke stroke = KeyStroke.getKeyStroke(name.toUpperCase(Locale.ROOT));
            if (stroke != null && stroke.getKeyCode() != 0) codes.add(stroke.getKeyCode());
        }
        return codes.isEmpty() ? defaults : codes.stream().mapToInt(Integer::intValue).toArray();
    }
}
