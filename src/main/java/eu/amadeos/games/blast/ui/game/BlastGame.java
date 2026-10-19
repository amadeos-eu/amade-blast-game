package eu.amadeos.games.blast.ui.game;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Controls;
import eu.amadeos.gameengine.input.Gamepad;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.loop.GameLoop;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.scene.Scene;
import eu.amadeos.gameengine.scene.SceneStack;
import eu.amadeos.gameengine.swing.GameCanvas;
import eu.amadeos.games.blast.domain.Board;
import eu.amadeos.games.blast.domain.Field;
import eu.amadeos.games.blast.domain.Game;
import eu.amadeos.games.blast.domain.Move;
import eu.amadeos.games.blast.domain.Player;
import eu.amadeos.games.blast.repository.BlastSettings;
import eu.amadeos.games.blast.ui.game.Seat.Action;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The running game: the loop, the seats and their controls, the effects and the scenes (title with the
 * computer playing, the lobby where players join, a match, the pause screen, the end of a match). Everything
 * here runs on the game loop thread, except the {@link Arena} methods, which only post commands to it.
 */
public final class BlastGame implements Arena {
    /** Keys that belong to no player: pause, back, the computer players, go on. */
    enum Key { PAUSE, BACK, COMPUTERS, CONTINUE }

    static final int UPDATES_PER_SECOND = 120;
    static final int FRAMES_PER_SECOND = 60;
    static final double STEP = 1.0 / UPDATES_PER_SECOND;

    private final Keyboard keyboard;
    private final Gamepads gamepads;
    private final Messages msg;
    private final BlastSettings settings;
    private final LongSupplier seeds;
    private final Mixer mixer;
    private final BlastPainter painter = new BlastPainter(Board.STANDARD);
    private final Hud hud;
    private final Effects effects;
    private final SceneStack scenes = new SceneStack();
    private final Controls<Key> keys;
    /** The ways to play: the two sets of keys, then the four gamepads. */
    private final List<Seat> inputs = new ArrayList<>();
    /** Who sat where in the last lobby, for the next match; {@code null} for an empty seat. */
    private final Seat[] chosen = new Seat[Field.PLAYERS];
    private boolean computers;
    private GameLoop loop;

    @Inject
    public BlastGame(Keyboard keyboard, Gamepads gamepads, Messages msg, BlastSettings settings, Mixer mixer) {
        this(keyboard, gamepads, msg, settings, SeededRandom::freshSeed, mixer);
    }

    /** @param seeds the seed of each new match */
    BlastGame(Keyboard keyboard, Gamepads gamepads, Messages msg, BlastSettings settings, LongSupplier seeds,
              Mixer mixer) {
        this.keyboard = keyboard;
        this.gamepads = gamepads;
        this.msg = msg;
        this.settings = settings;
        this.seeds = seeds;
        this.mixer = mixer;
        this.hud = new Hud(msg, painter);
        this.effects = new Effects(mixer);
        this.computers = settings.computers();
        keys = new Controls<>(Key.class, keyboard)
                .bind(Key.PAUSE, settings.keys("pause", KeyEvent.VK_P, KeyEvent.VK_ESCAPE))
                .bind(Key.BACK, KeyEvent.VK_ESCAPE)
                .bind(Key.COMPUTERS, KeyEvent.VK_C)
                .bind(Key.CONTINUE, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE);
        // --8<-- [start:inputs]
        inputs.add(Seat.keys(1, new Controls<>(Action.class, keyboard)
                .bind(Action.UP, settings.keys("1.up", KeyEvent.VK_W))
                .bind(Action.LEFT, settings.keys("1.left", KeyEvent.VK_A))
                .bind(Action.DOWN, settings.keys("1.down", KeyEvent.VK_S))
                .bind(Action.RIGHT, settings.keys("1.right", KeyEvent.VK_D))
                .bind(Action.BOMB, settings.keys("1.bomb", KeyEvent.VK_SPACE))));
        inputs.add(Seat.keys(2, new Controls<>(Action.class, keyboard)
                .bind(Action.UP, settings.keys("2.up", KeyEvent.VK_UP))
                .bind(Action.LEFT, settings.keys("2.left", KeyEvent.VK_LEFT))
                .bind(Action.DOWN, settings.keys("2.down", KeyEvent.VK_DOWN))
                .bind(Action.RIGHT, settings.keys("2.right", KeyEvent.VK_RIGHT))
                .bind(Action.BOMB, settings.keys("2.bomb", KeyEvent.VK_ENTER))));
        for (Gamepad pad : gamepads.pads()) {
            inputs.add(Seat.pad(pad, new Controls<>(Action.class, keyboard)
                    .bind(Action.UP, pad, Gamepad.Button.UP)
                    .bind(Action.LEFT, pad, Gamepad.Button.LEFT)
                    .bind(Action.DOWN, pad, Gamepad.Button.DOWN)
                    .bind(Action.RIGHT, pad, Gamepad.Button.RIGHT)
                    .bind(Action.BOMB, pad, Gamepad.Button.A, Gamepad.Button.B)
                    .bind(Action.PAUSE, pad, Gamepad.Button.START)));
        }
        // --8<-- [end:inputs]
        scenes.push(new TitleScene());
    }

    /** Starts the loop; the canvas draws the scenes' worlds and then their overlays. */
    public void start(GameCanvas canvas) {
        loop = new GameLoop(UPDATES_PER_SECOND, FRAMES_PER_SECOND, this::update, canvas::render);
        loop.start();
    }

    public void stop() {
        if (loop != null) loop.stop();
    }

    void update(double seconds) {
        keyboard.poll();
        gamepads.poll();
        scenes.update(seconds);
    }

    /** The world of all scenes, in logical coordinates. */
    public void render(Graphics2D g, double alpha) {
        scenes.render(g, alpha);
    }

    /** Cards and texts of all scenes, drawn after the world at the screen's full size. */
    public void renderOverlay(Graphics2D g, double alpha) {
        scenes.renderOverlay(g, alpha);
    }

    // ---- Arena: called on the Swing thread, carried out on the loop thread -----------------------

    @Override
    public void showTitle() {
        run(() -> scenes.replace(new TitleScene()));
    }

    @Override
    public void newGame() {
        run(() -> scenes.replace(new LobbyScene()));
    }

    @Override
    public void pause() {
        run(() -> {
            if (scenes.top() instanceof PlayScene) scenes.push(new PauseScene(null));
        });
    }

    @Override
    public void setSound(boolean on) {
        mixer.setMuted(!on);
    }

    @Override
    public String gamepads() {
        return msg.format("about.gamepads", gamepads.backend(), gamepads.connected());
    }

    /** On the loop thread when it runs; directly before the loop starts and in tests. */
    private void run(Runnable command) {
        if (loop != null && loop.running()) loop.post(command);
        else command.run();
    }

    // ---- for tests -------------------------------------------------------------------------------

    Scene top() {
        return scenes.top();
    }

    int sceneCount() {
        return scenes.size();
    }

    BlastPainter painter() {
        return painter;
    }

    Effects effects() {
        return effects;
    }

    /** The cards and the round's texts of any match, for the screenshots. */
    void status(Graphics2D g, Game game, List<Seat> seats) {
        hud.status(g, game, seats, effects);
    }

    /** The input that pressed its bomb key or button in this step; {@code null} for none. */
    private Seat joining() {
        for (Seat input : inputs) {
            if (!input.missing() && input.pressed(Action.BOMB)) return input;
        }
        return null;
    }

    // ---- the scenes ------------------------------------------------------------------------------

    /** The computer plays, silently, match after match; a bomb key or button A leads to the lobby. */
    final class TitleScene implements Scene {
        private final List<Seat> seats = new ArrayList<>();
        private Game demo;

        TitleScene() {
            effects.clear();
            startDemo();
        }

        private void startDemo() {
            demo = new Game(seeds.getAsLong());
            seats.clear();
            for (int i = 0; i < Field.PLAYERS; i++) {
                Seat seat = Seat.computer();
                seat.start(demo.seed() + i);
                seats.add(seat);
            }
        }

        @Override
        public void update(double seconds) {
            Seat joining = joining();
            if (joining != null) {
                Arrays.fill(chosen, null);
                chosen[0] = joining;
                scenes.replace(new LobbyScene());
                return;
            }
            demo.update(seconds, moves(demo, seats, seconds));
            effects.react(demo.events(), false, painter);
            effects.update(seconds);
            if (demo.phase() == Game.Phase.MATCH_OVER || demo.round() > 3) startDemo();
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, demo, effects, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("window.title"), 150, 72, true, Hud.GOLD);
            hud.text(g, msg.get("title.start"), 210, 18, false, Hud.CHALK);
            hud.text(g, msg.get("title.keys"), 262, 13, false, Hud.CHALK);
            hud.text(g, msg.get("title.rules"), 284, 13, false, Hud.CHALK);
            hud.text(g, msg.get("title.screen"), 306, 13, false, Hud.CHALK);
        }

        Game demo() {
            return demo;
        }

        List<Seat> seats() {
            return seats;
        }
    }

    // --8<-- [start:lobby]
    /**
     * Who plays? Each person presses the bomb key of their keys, or button A of their gamepad, and takes the
     * next free seat; pressing it again starts the match. Computer players take the seats left empty,
     * unless C has switched them off.
     */
    final class LobbyScene implements Scene {
        private final Game backdrop = new Game(1);

        LobbyScene() {
            effects.clear();
        }

        @Override
        public void update(double seconds) {
            if (keys.wasPressed(Key.BACK)) {
                scenes.replace(new TitleScene());
                return;
            }
            if (keys.wasPressed(Key.COMPUTERS)) {
                computers = !computers;
                settings.setComputers(computers);
            }
            Seat pressed = joining();
            boolean start = false;
            for (Seat seat : chosen) start |= seat != null && seat.pressed(Action.PAUSE);   // Start on a pad
            if (pressed != null) {
                int at = Arrays.asList(chosen).indexOf(pressed);
                if (at >= 0) start = true;
                else {
                    int free = Arrays.asList(chosen).indexOf(null);
                    if (free >= 0) chosen[free] = pressed;
                }
            }
            if (start && ready()) scenes.replace(new PlayScene(seats()));
        }

        /** Somebody at the keys or a pad; and somebody to play against. */
        boolean ready() {
            int people = 0;
            for (Seat seat : chosen) if (seat != null) people++;
            return people >= 1 && (computers || people >= 2);
        }

        /** The seats of the match: the people where they sat, computers or nobody in the others. */
        private List<Seat> seats() {
            List<Seat> seats = new ArrayList<>();
            for (Seat seat : chosen) seats.add(seat != null ? seat : computers ? Seat.computer() : null);
            return seats;
        }
        // --8<-- [end:lobby]

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, backdrop, effects, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("lobby.title"), 70, 34, true, Hud.GOLD);
            for (int i = 0; i < Field.PLAYERS; i++) {
                double x = Field.WIDTH / 2 + (i - 1.5) * 124 - 52;
                double y = 100;
                g.setColor(Hud.CARD);
                g.fill(new RoundRectangle2D.Double(x, y, 104, 120, 10, 10));
                g.setColor(BlastPainter.PLAYERS[i]);
                g.fill(new java.awt.geom.Rectangle2D.Double(x + 8, y + 4, 88, 2));
                painter.portrait(i).draw(g, x + 52 - BlastPainter.TILE / 2, y + 16);
                g.setFont(hud.font(13, true));
                Hud.centred(g, hud.name(i), x + 52, y + 72);
                Seat seat = chosen[i];
                String who = seat != null ? seatName(seat) : computers ? msg.get("seat.computer") : msg.get("seat.empty");
                g.setFont(hud.font(11, false));
                g.setColor(seat != null ? Hud.CHALK : Hud.GREY);
                Hud.centred(g, who, x + 52, y + 96);
            }
            hud.text(g, msg.get("lobby.join"), 268, 14, false, Hud.CHALK);
            hud.text(g, msg.get(computers ? "lobby.computers.on" : "lobby.computers.off"), 292, 13, false, Hud.CHALK);
            if (!ready()) hud.text(g, msg.get("lobby.two"), 316, 13, false, Hud.RED);
            hud.text(g, msg.get("lobby.back"), 360, 12, false, Hud.GREY);
        }

        private String seatName(Seat seat) {
            return switch (seat.kind) {
                case KEYS -> msg.get("seat.keys" + seat.number);
                case PAD -> msg.format("seat.pad", seat.number);
                case COMPUTER -> msg.get("seat.computer");
            };
        }
    }

    /** What each player wants in this step; an empty seat stands still. */
    private static List<Move> moves(Game game, List<Seat> seats, double seconds) {
        List<Move> moves = new ArrayList<>(Field.PLAYERS);
        for (Player player : game.players()) {
            Seat seat = seats.get(player.index());
            moves.add(seat == null ? Move.NONE : seat.move(game, player, seconds));
        }
        return moves;
    }

    // --8<-- [start:play]
    /** A match. A gamepad that is unplugged pauses it until it is back. */
    final class PlayScene implements Scene {
        private final List<Seat> seats;
        private final Game game;

        PlayScene(List<Seat> seats) {
            this.seats = seats;
            boolean[] present = new boolean[Field.PLAYERS];
            for (int i = 0; i < Field.PLAYERS; i++) present[i] = seats.get(i) != null;
            game = new Game(seeds.getAsLong(), Board.STANDARD, present);
            for (int i = 0; i < Field.PLAYERS; i++) {
                if (seats.get(i) != null) seats.get(i).start(game.seed() * 31 + i);
            }
            effects.clear();
            effects.react(game.events(), true, painter);    // "Round 1 · Ready?" and its call
        }

        @Override
        public void update(double seconds) {
            for (Seat seat : seats) {
                if (seat != null && seat.missing()) {
                    scenes.push(new PauseScene(seat));
                    return;
                }
            }
            boolean pause = keys.wasPressed(Key.PAUSE);
            for (Seat seat : seats) pause |= seat != null && seat.pressed(Action.PAUSE);
            if (pause) {
                scenes.push(new PauseScene(null));
                return;
            }
            game.update(seconds, moves(game, seats, seconds));
            effects.react(game.events(), true, painter);
            effects.update(seconds);
            if (game.phase() == Game.Phase.MATCH_OVER) scenes.replace(new OverScene(game, seats));
        }
        // --8<-- [end:play]

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, game, effects, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.status(g, game, seats, effects);
        }

        Game game() {
            return game;
        }

        List<Seat> seats() {
            return seats;
        }
    }

    /** Drawn over the frozen match until P, Enter, Space or Start; a missing gamepad must come back first. */
    final class PauseScene implements Scene {
        private final Seat missing;

        PauseScene(Seat missing) {
            this.missing = missing;
        }

        @Override
        public void update(double seconds) {
            if (missing != null && missing.missing()) return;
            boolean go = keys.wasPressed(Key.PAUSE) || keys.wasPressed(Key.CONTINUE);
            for (Seat input : inputs) go |= input.kind == Seat.Kind.PAD && input.pressed(Action.PAUSE);
            if (go) scenes.pop();
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("pause.title"), 190, 40, true, Hud.CHALK);
            if (missing != null && missing.missing()) {
                hud.text(g, msg.format("pause.pad", missing.number), 235, 18, false, Hud.RED);
            } else {
                hud.text(g, msg.get("pause.continue"), 235, 18, false, Hud.CHALK);
            }
        }
    }

    /** The end: the arena stays, the winner is named; a bomb key or Enter leads back to the lobby. */
    final class OverScene implements Scene {
        private final Game game;
        private final List<Seat> seats;
        private double shown;

        OverScene(Game game, List<Seat> seats) {
            this.game = game;
            this.seats = seats;
        }

        @Override
        public void update(double seconds) {
            effects.update(seconds);
            shown += seconds;
            if (shown < 1.5) return;      // a short wait, so a bomb meant for the arena does not skip the end
            if (keys.wasPressed(Key.BACK)) scenes.replace(new TitleScene());
            else if (keys.wasPressed(Key.CONTINUE) || joining() != null) scenes.replace(new LobbyScene());
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, game, effects, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.status(g, game, seats, effects);
            hud.veil(g);
            int winner = game.result().winner();
            Color colour = BlastPainter.PLAYERS[winner];
            hud.text(g, msg.format("over.won", hud.name(winner)), 180, 34, true, colour);
            hud.text(g, msg.format("over.rounds", game.round()), 220, 16, false, Hud.CHALK);
            hud.text(g, msg.get("over.continue"), 262, 15, false, Hud.CHALK);
        }
    }
}
