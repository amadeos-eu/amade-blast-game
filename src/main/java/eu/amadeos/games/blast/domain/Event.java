package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.tile.Cell;

/**
 * Something that happened in a step. The game posts events to an {@link eu.amadeos.gameengine.event.EventQueue}
 * and handles them there: an explosion posts the explosions of the bombs its flames reach, and the hits of
 * the players standing in them. The user interface then shows and plays them all, in the order they
 * happened.
 */
// --8<-- [start:event]
public sealed interface Event {
    /** A round begins: "Round 2 · Ready?". */
    record Ready(int round) implements Event {
    }

    /** "Go!": the players may move. */
    record Go() implements Event {
    }

    record BombLaid(int player, Cell at) implements Event {
    }

    /** A bomb goes off: by its fuse, or because a flame reached it ({@code chained}). */
    record Exploded(Bomb bomb, boolean chained) implements Event {
    }

    /** A flame reached a crate; it burns for a moment, then it is gone. */
    record CrateHit(Cell at) implements Event {
    }

    /** A crate has burnt; {@code hidden} is what it held, {@code null} for nothing. */
    record CrateBurnt(Cell at, PowerUp hidden) implements Event {
    }

    /** A flame destroyed a power-up lying on the floor. */
    record PowerUpBurnt(Cell at, PowerUp powerUp) implements Event {
    }

    record PowerUpTaken(int player, PowerUp powerUp, Cell at) implements Event {
    }

    /** A flame or a falling wall hit a player. */
    record PlayerHit(int player, double x, double y) implements Event {
    }

    /** Time is up: walls begin to fall. */
    record Hurry() implements Event {
    }

    record WallFell(Cell at) implements Event {
    }

    /** {@code winner} -1 when the last players fell together. */
    record RoundOver(int round, int winner) implements Event {
    }

    record MatchOver(MatchResult result) implements Event {
    }
}
// --8<-- [end:event]
