package eu.amadeos.games.blast.domain;

/** The numbers of the game: times in seconds, speeds in tiles per second. */
public final class Field {
    /** The logical size of the screen; the arena is drawn in its middle. */
    public static final double WIDTH = 640;
    public static final double HEIGHT = 400;

    public static final int PLAYERS = 4;
    /** Round wins that win the match. */
    public static final int WINS = 3;

    // --8<-- [start:times]
    /** A bomb explodes this long after it was laid, unless a flame reaches it first. */
    public static final double FUSE_SECONDS = 2.5;
    /** Flames stay on their tiles this long; whoever walks into them is hit. */
    public static final double FLAME_SECONDS = 0.5;
    /** A crate burns this long before it is gone and shows what was hidden in it. */
    public static final double BURN_SECONDS = 0.5;
    /** After this, walls fall into the arena, one by one, from the outside in. */
    public static final double ROUND_SECONDS = 120;
    public static final double HURRY_INTERVAL = 0.3;
    // --8<-- [end:times]

    /** What a player starts every round with, and the most power-ups can give. */
    public static final int START_BOMBS = 1;
    public static final int MAX_BOMBS = 8;
    public static final int START_FIRE = 2;
    public static final int MAX_FIRE = 8;
    public static final double SPEED = 3.6;
    public static final double SPEED_UP = 0.6;
    public static final int MAX_SPEED_UPS = 4;

    /** "Round 2 · Ready?" before a round. */
    public static final double READY_SECONDS = 2.0;
    /** A player hit by a flame. */
    public static final double DYING_SECONDS = 1.0;
    /** The winner of a round is shown this long. */
    public static final double ROUND_OVER_SECONDS = 3.0;

    private Field() {
    }
}
