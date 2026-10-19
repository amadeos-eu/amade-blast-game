package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.tile.Direction;

/**
 * What a player wants in a step: the way to go, {@code null} to stop at the next centre of a tile, and
 * whether to lay a bomb. The same for a person at the keys, at a gamepad, and for the computer.
 */
public record Move(Direction direction, boolean bomb) {
    public static final Move NONE = new Move(null, false);
}
