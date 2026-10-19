package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.Direction;
import eu.amadeos.gameengine.tile.Walker;

/**
 * One of the four players: where it walks, how many bombs it may lay at a time and how far their flames
 * reach, how fast it is, and the rounds it has won. Whether a person or the computer plays it is not its
 * business; it gets a {@link Move} in every step.
 */
public final class Player {
    /** Playing; hit and falling; out of this round. */
    public enum State { PLAYING, DYING, OUT }

    private final int index;
    private final boolean present;
    private Walker walker;
    private Direction facing = Direction.DOWN;
    private double walked;
    private State state = State.PLAYING;
    private double dying;
    private int bombs;
    private int fire;
    private int speedUps;
    private int laid;
    private int wins;
    private Game game;
    private Direction wanted;
    private final Walker.Steering steering = this::choose;

    Player(int index, boolean present) {
        this.index = index;
        this.present = present;
    }

    /** A new round: back to the start, with the bombs and flames of the start. */
    void reset(Cell start) {
        walker = new Walker(start.col(), start.row(), null);
        facing = Direction.DOWN;
        walked = 0;
        state = present ? State.PLAYING : State.OUT;
        dying = 0;
        bombs = Field.START_BOMBS;
        fire = Field.START_FIRE;
        speedUps = 0;
        laid = 0;
    }

    // --8<-- [start:move]
    /**
     * Walks for {@code seconds} in the way the player wants. It turns and stops only at the centre of a
     * tile, as in Maze; without a direction it stops at the next centre. Back the way it came works at once.
     */
    void move(double seconds, Direction wanted, Game game) {
        this.game = game;
        this.wanted = wanted;
        if (wanted != null && walker.heading() == wanted.opposite()) walker.turnAround();
        walked += walker.walk(speed() * seconds, game.tiles(), steering);
        if (walker.heading() != null) facing = walker.heading();
        else if (wanted != null) facing = wanted;           // against a wall or a bomb: it still looks there
    }

    private Direction choose(int col, int row, Direction heading) {
        if (wanted == null) return null;
        Cell next = game.tiles().step(col, row, wanted);
        return game.walkable(next) ? wanted : null;
    }
    // --8<-- [end:move]

    void hit() {
        state = State.DYING;
        dying = 0;
    }

    /** True when the fall is over and the player is out of the round. */
    boolean fall(double seconds) {
        dying += seconds;
        if (dying < Field.DYING_SECONDS) return false;
        state = State.OUT;
        return true;
    }

    void take(PowerUp powerUp) {
        switch (powerUp) {
            case BOMB -> bombs = Math.min(Field.MAX_BOMBS, bombs + 1);
            case FIRE -> fire = Math.min(Field.MAX_FIRE, fire + 1);
            case SPEED -> speedUps = Math.min(Field.MAX_SPEED_UPS, speedUps + 1);
        }
    }

    void laid(int change) {
        laid += change;
    }

    void won() {
        wins++;
    }

    /** False for a seat nobody takes: the player is out of every round. */
    public boolean present() {
        return present;
    }

    /** 0 to 3: the colour, the start, the slot in the overlay. */
    public int index() {
        return index;
    }

    public Walker walker() {
        return walker;
    }

    /** The tile it is on: the nearest centre. */
    public Cell cell() {
        return walker.cell();
    }

    public Direction facing() {
        return facing;
    }

    /** Tiles walked this round: the feet move with them, and stand still with the player. */
    public double walked() {
        return walked;
    }

    public State state() {
        return state;
    }

    public boolean playing() {
        return state == State.PLAYING;
    }

    /** Seconds since it was hit. */
    public double dying() {
        return dying;
    }

    /** Bombs it may have on the arena at the same time. */
    public int bombs() {
        return bombs;
    }

    /** How many of its bombs lie on the arena now. */
    public int laid() {
        return laid;
    }

    /** Tiles its flames reach. */
    public int fire() {
        return fire;
    }

    public int speedUps() {
        return speedUps;
    }

    /** Tiles per second. */
    public double speed() {
        return Field.SPEED + speedUps * Field.SPEED_UP;
    }

    public int wins() {
        return wins;
    }
}
