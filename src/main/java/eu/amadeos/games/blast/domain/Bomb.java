package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.tile.Cell;

/**
 * A bomb on the arena: who laid it, where, and how far its flames reach. Its fuse is a timer of the game.
 * Not a record: two bombs laid on the same tile one after the other are two bombs, with two fuses.
 */
public final class Bomb {
    private final int owner;
    private final Cell cell;
    private final int range;

    Bomb(int owner, Cell cell, int range) {
        this.owner = owner;
        this.cell = cell;
        this.range = range;
    }

    /** The player who laid it, 0 to 3. */
    public int owner() {
        return owner;
    }

    public Cell cell() {
        return cell;
    }

    /** Tiles the flames reach in each direction. */
    public int range() {
        return range;
    }

    @Override
    public String toString() {
        return "Bomb[" + owner + " at " + cell.col() + "," + cell.row() + "]";
    }
}
