package eu.amadeos.games.blast.domain;

/** What a tile of the arena is during a round: a wall no flame breaks, a crate, or floor. */
public enum Tile {
    WALL, CRATE, FLOOR;

    /** Where a flame goes on, and a player may walk if no bomb lies there. */
    public boolean open() {
        return this == FLOOR;
    }
}
