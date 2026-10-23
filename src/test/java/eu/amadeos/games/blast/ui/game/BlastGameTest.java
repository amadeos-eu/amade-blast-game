package eu.amadeos.games.blast.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Gamepad;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.blast.bootstrap.BlastModule;
import eu.amadeos.games.blast.domain.Field;
import eu.amadeos.games.blast.domain.Game;
import eu.amadeos.games.blast.domain.Player;
import eu.amadeos.games.blast.repository.BlastSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game without a window and without the loop thread: the test presses keys and buttons and calls the
 * steps itself, so everything happens in order on the test thread.
 */
class BlastGameTest {
    private static final double STEP = BlastGame.STEP;

    @TempDir
    Path dir;

    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final Gamepads gamepads = Gamepads.none();
    private BlastSettings settings;
    private BlastGame game;

    @BeforeEach
    void create() {
        Messages msg = Messages.load(BlastModule.class, BlastModule.MESSAGES, Locale.ENGLISH);
        settings = new BlastSettings(dir.resolve("blast.properties"));
        game = new BlastGame(keyboard, gamepads, msg, settings, () -> 77L, new Mixer());
    }

    /** Pressed and let go in one step, then a few steps more, so a second tap is not taken for key repeat. */
    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(5);
    }

    /** Button pressed and let go between two steps. */
    private void tap(Gamepad pad, Gamepad.Button button) {
        pad.report(Gamepad.bits(button), 0, 0);
        pad.report(0, 0, 0);
        steps(1);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (STEP * 1e9);
            game.update(STEP);
        }
    }

    private void seconds(double seconds) {
        steps((int) Math.round(seconds * BlastGame.UPDATES_PER_SECOND));
    }

    // --8<-- [start:test-lobby]
    @Test
    void playersJoinWithTheirBombKeyOrButtonAndTheComputerFillsTheRest() {
        Gamepad pad = gamepads.pad(0);
        pad.connect("Test pad");
        tap(KeyEvent.VK_SPACE);                                   // keys 1, from the title
        assertInstanceOf(BlastGame.LobbyScene.class, game.top());
        tap(pad, Gamepad.Button.A);                               // the gamepad takes the next seat
        tap(pad, Gamepad.Button.START);                           // and starts the match
        BlastGame.PlayScene play = assertInstanceOf(BlastGame.PlayScene.class, game.top());
        assertSame(Seat.Kind.KEYS, play.seats().get(0).kind);
        assertSame(Seat.Kind.PAD, play.seats().get(1).kind);
        assertSame(Seat.Kind.COMPUTER, play.seats().get(2).kind);
        assertSame(Seat.Kind.COMPUTER, play.seats().get(3).kind);
    }
    // --8<-- [end:test-lobby]

    @Test
    void withoutComputerPlayersTwoPeopleAreNeeded() {
        tap(KeyEvent.VK_ENTER);                                   // keys 2 come first
        tap(KeyEvent.VK_C);
        assertFalse(settings.computers(), "kept for the next time");
        tap(KeyEvent.VK_ENTER);
        assertInstanceOf(BlastGame.LobbyScene.class, game.top(), "nobody to play against");
        tap(KeyEvent.VK_SPACE);                                   // keys 1 join
        tap(KeyEvent.VK_SPACE);
        BlastGame.PlayScene play = assertInstanceOf(BlastGame.PlayScene.class, game.top());
        assertEquals(2, play.seats().get(0).number);
        assertNull(play.seats().get(2));
        assertEquals(Player.State.OUT, play.game().player(2).state());
    }

    @Test
    void theKeysMoveTheirPlayerAndLayBombs() {
        tap(KeyEvent.VK_SPACE);
        tap(KeyEvent.VK_SPACE);
        BlastGame.PlayScene play = assertInstanceOf(BlastGame.PlayScene.class, game.top());
        seconds(Field.READY_SECONDS + 0.05);
        assertEquals(Game.Phase.PLAYING, play.game().phase());
        keyboard.pressed(KeyEvent.VK_D, now[0]);
        seconds(0.4);
        keyboard.released(KeyEvent.VK_D, now[0]);
        seconds(0.3);
        Player white = play.game().player(0);
        assertEquals(new Cell(2, 1), white.cell(), "one tile to the right, stopped at the next centre");
        tap(KeyEvent.VK_SPACE);
        assertEquals(1, white.laid());
    }

    // --8<-- [start:test-unplugged]
    @Test
    void anUnpluggedGamepadPausesTheMatchUntilItIsBack() {
        Gamepad pad = gamepads.pad(2);
        pad.connect("Test pad");
        steps(1);
        tap(pad, Gamepad.Button.A);
        tap(pad, Gamepad.Button.A);
        BlastGame.PlayScene play = assertInstanceOf(BlastGame.PlayScene.class, game.top());
        assertEquals(3, play.seats().getFirst().number, "gamepad 3");
        pad.disconnect();
        steps(2);
        assertEquals(2, game.sceneCount(), "paused");
        tap(KeyEvent.VK_ENTER);
        assertEquals(2, game.sceneCount(), "not without the pad");
        pad.connect("Test pad");
        steps(1);
        tap(pad, Gamepad.Button.START);
        assertEquals(1, game.sceneCount(), "back in the match");
    }
    // --8<-- [end:test-unplugged]

    @Test
    void escapeLeavesAMatchWhosePadIsGoneAndFreesItsSeat() {
        Gamepad pad = gamepads.pad(0);
        pad.connect("Test pad");
        steps(1);
        tap(pad, Gamepad.Button.A);
        tap(KeyEvent.VK_SPACE);
        tap(pad, Gamepad.Button.A);
        assertInstanceOf(BlastGame.PlayScene.class, game.top());
        pad.disconnect();
        steps(2);
        assertEquals(2, game.sceneCount(), "paused");
        tap(KeyEvent.VK_ESCAPE);
        assertInstanceOf(BlastGame.LobbyScene.class, game.top());
        assertEquals(1, game.sceneCount());
        tap(KeyEvent.VK_SPACE);
        BlastGame.PlayScene play = assertInstanceOf(BlastGame.PlayScene.class, game.top(), "keys play alone");
        assertEquals(Seat.Kind.KEYS, play.seats().get(1).kind);
    }

    @Test
    void pFreezesTheMatchAndEnterContinuesIt() {
        tap(KeyEvent.VK_SPACE);
        tap(KeyEvent.VK_SPACE);
        BlastGame.PlayScene play = assertInstanceOf(BlastGame.PlayScene.class, game.top());
        seconds(1);
        tap(KeyEvent.VK_P);
        assertEquals(2, game.sceneCount());
        double time = play.game().phaseTime();
        seconds(1);
        assertEquals(time, play.game().phaseTime(), "frozen");
        tap(KeyEvent.VK_ENTER);
        steps(1);
        assertTrue(play.game().phaseTime() > time, "goes on");
    }

    @Test
    void escapeInTheLobbyGoesBackToTheTitle() {
        tap(KeyEvent.VK_SPACE);
        tap(KeyEvent.VK_ESCAPE);
        assertInstanceOf(BlastGame.TitleScene.class, game.top());
    }

    @Test
    void theDemoBlastsCrates() {
        BlastGame.TitleScene title = assertInstanceOf(BlastGame.TitleScene.class, game.top());
        int crates = title.demo().tiles().count(tile -> tile == eu.amadeos.games.blast.domain.Tile.CRATE);
        seconds(20);
        assertTrue(title.demo().tiles().count(tile -> tile == eu.amadeos.games.blast.domain.Tile.CRATE) < crates - 5);
    }

    @Test
    void everySceneDrawsWorldAndOverlay() {
        BufferedImage image = new BufferedImage((int) Field.WIDTH, (int) Field.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        seconds(2);
        game.render(g, 0.5);
        game.renderOverlay(g, 0.5);
        tap(KeyEvent.VK_SPACE);
        game.render(g, 0.5);
        game.renderOverlay(g, 0.5);
        tap(KeyEvent.VK_SPACE);
        game.render(g, 0.5);
        BlastPainter painter = game.painter();
        // The white player at its start: the top of its helmet, above the face.
        int x = (int) Math.round(painter.x(1));
        int y = (int) Math.round(painter.y(1)) - 18;
        assertEquals(BlastPainter.PLAYERS[0].getRGB() & 0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF, "the white player");
        game.renderOverlay(g, 0.5);
        seconds(Field.READY_SECONDS + 4);
        game.render(g, 0.5);
        tap(KeyEvent.VK_P);
        game.render(g, 0.5);
        game.renderOverlay(g, 0.5);
        g.dispose();
    }
}
