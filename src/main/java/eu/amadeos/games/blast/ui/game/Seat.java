package eu.amadeos.games.blast.ui.game;

import eu.amadeos.gameengine.input.Controls;
import eu.amadeos.gameengine.input.Gamepad;
import eu.amadeos.gameengine.tile.Direction;
import eu.amadeos.games.blast.domain.Bot;
import eu.amadeos.games.blast.domain.Game;
import eu.amadeos.games.blast.domain.Move;
import eu.amadeos.games.blast.domain.Player;

/**
 * Who plays a player: one of the two sets of keys, a gamepad, or the computer. For the game they are all the
 * same: each gives a {@link Move} per step. The people's seats turn their {@link Controls} into moves, the
 * computer's asks its {@link Bot}.
 */
// --8<-- [start:seat]
final class Seat {
    enum Kind { KEYS, PAD, COMPUTER }

    /** The actions of a player; which keys or buttons trigger them is in the controls. */
    enum Action { UP, LEFT, DOWN, RIGHT, BOMB, PAUSE }

    final Kind kind;
    /** Keys 1 or 2, gamepad 1 to 4; 0 for the computer. */
    final int number;
    private final Controls<Action> controls;
    private final Gamepad pad;
    private Bot bot;
    private Direction wanted;

    private Seat(Kind kind, int number, Controls<Action> controls, Gamepad pad) {
        this.kind = kind;
        this.number = number;
        this.controls = controls;
        this.pad = pad;
    }

    static Seat keys(int number, Controls<Action> controls) {
        return new Seat(Kind.KEYS, number, controls, null);
    }

    static Seat pad(Gamepad pad, Controls<Action> controls) {
        return new Seat(Kind.PAD, pad.index() + 1, controls, pad);
    }

    static Seat computer() {
        return new Seat(Kind.COMPUTER, 0, null, null);
    }

    /** A new match: a fresh computer player, no key held over from before. */
    void start(long seed) {
        bot = kind == Kind.COMPUTER ? new Bot(seed) : null;
        wanted = null;
    }

    /** What the player in this seat does in this step. */
    Move move(Game game, Player player, double seconds) {
        if (kind == Kind.COMPUTER) return bot.steer(game, player, seconds);
        return new Move(wanted(), controls.wasPressed(Action.BOMB));
    }

    /**
     * The way the player wants to go: the direction pressed last, while it is held. Let go, the one still
     * held before it counts again, so a player can hold right and tap up to turn at the next crossing.
     */
    private Direction wanted() {
        for (Direction d : Direction.values()) {
            if (controls.wasPressed(action(d))) wanted = d;
        }
        if (wanted != null && !controls.isDown(action(wanted))) {
            wanted = null;
            for (Direction d : Direction.values()) {
                if (controls.isDown(action(d))) {
                    wanted = d;
                    break;
                }
            }
        }
        return wanted;
    }

    /** The first four actions are the directions, in the same order. */
    private static Action action(Direction d) {
        return Action.values()[d.ordinal()];
    }
    // --8<-- [end:seat]

    boolean pressed(Action action) {
        return controls != null && controls.wasPressed(action);
    }

    /** A gamepad that is not plugged in; keys and the computer are always there. */
    boolean missing() {
        return kind == Kind.PAD && !pad.connected();
    }

    boolean human() {
        return kind != Kind.COMPUTER;
    }

    Gamepad gamepad() {
        return pad;
    }

    Bot bot() {
        return bot;
    }
}
