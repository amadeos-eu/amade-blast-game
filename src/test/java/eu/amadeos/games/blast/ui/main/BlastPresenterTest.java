package eu.amadeos.games.blast.ui.main;

import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.blast.repository.BlastSettings;
import eu.amadeos.games.blast.ui.game.Arena;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlastPresenterTest {
    @TempDir
    Path dir;

    private final List<String> log = new ArrayList<>();
    private BlastPresenter presenter;
    private BlastSettings settings;

    private final BlastView view = new BlastView() {
        @Override
        public void showOptions(boolean sound, RenderMode mode) {
            log.add("options " + sound + " " + mode);
        }

        @Override
        public void useRenderMode(RenderMode mode) {
            log.add("mode " + mode);
        }

        @Override
        public void showAbout(String gamepads) {
            log.add("about " + gamepads);
        }
    };

    private final Arena arena = new Arena() {
        @Override
        public void showTitle() {
            log.add("title");
        }

        @Override
        public void newGame() {
            log.add("new");
        }

        @Override
        public void pause() {
            log.add("pause");
        }

        @Override
        public void setSound(boolean on) {
            log.add("sound " + on);
        }

        @Override
        public String gamepads() {
            return "Gamepads: none, 0 connected.";
        }
    };

    @BeforeEach
    void create() {
        settings = new BlastSettings(dir.resolve("blast.properties"));
        presenter = new BlastPresenter(view, arena, settings);
        presenter.start();
    }

    @Test
    void theSettingsAreAppliedAtStartAndKept() {
        assertEquals(List.of("options true DIRECT", "mode DIRECT", "sound true", "title"), log);
        log.clear();
        presenter.setRenderMode(RenderMode.PIXEL);
        presenter.setSound(false);
        assertEquals(List.of("mode PIXEL", "sound false"), log);
        assertEquals(RenderMode.PIXEL, settings.renderMode());
    }

    @Test
    void losingTheFocusPausesTheMatch() {
        log.clear();
        presenter.focusLost();
        presenter.newGame();
        presenter.showAbout();
        assertEquals(List.of("pause", "new", "about Gamepads: none, 0 connected."), log);
    }
}
