package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.path.DistanceMap;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.Direction;
import eu.amadeos.gameengine.tile.TileMap;
import eu.amadeos.gameengine.tile.Walker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * A computer player, for the seats nobody takes and for the title screen. Its rules, in order:
 *
 * <ol>
 *   <li>Standing where flames will come: run to the nearest safe tile, by breadth-first search.</li>
 *   <li>Where a bomb would reach a crate or another player, and there is a way out from it: lay one.</li>
 *   <li>Otherwise walk to the nearest power-up, the nearest place to blast a crate from, or the nearest
 *       player, only over tiles no flame will reach.</li>
 * </ol>
 *
 * It decides for the centre of the tile it reaches next, because only there can it turn. It is not perfect:
 * it does not see that another player could lock it in, and now and then it stands still for a moment.
 */
public final class Bot {
    /** Tiles flames reach sooner than this are not entered, not even to flee. */
    static final double IMMINENT = 0.4;
    /** Power-ups farther than this are not worth the way. */
    static final int GREED = 12;

    private final SeededRandom random;
    /** The tile where it means to lay its next bomb, {@code null} for none. */
    private Cell bombAt;
    private double dawdle;
    private Danger danger;
    private Cell target;

    public Bot(long seed) {
        this.random = new SeededRandom(seed);
    }

    // --8<-- [start:steer]
    /** What the player {@code me} does in this step. */
    public Move steer(Game game, Player me, double seconds) {
        if (!me.playing() || game.phase() != Game.Phase.PLAYING) return Move.NONE;
        if (bombAt != null && me.walker().atCentre() && me.cell().equals(bombAt)) {
            bombAt = null;
            return new Move(null, true);
        }
        Cell next = nextCentre(me.walker());
        danger = Danger.of(game);
        if (!danger.safe(next)) {
            bombAt = null;
            return new Move(flee(game, next), false);
        }
        if (dawdle > 0) {
            dawdle -= seconds;
            return Move.NONE;
        }
        if (random.nextDouble() < 0.004) dawdle = 0.2 + random.nextDouble() * 0.6;
        return new Move(hunt(game, me, next), false);
    }

    /** To the nearest tile no flame will reach, over tiles not yet burning. */
    private Direction flee(Game game, Cell from) {
        DistanceMap map = DistanceMap.search(game.tiles(),
                (col, row) -> game.walkable(new Cell(col, row)) && danger.at(col, row) > IMMINENT, from);
        target = nearest(game.tiles(), map, cell -> danger.safe(cell));
        return toward(game.tiles(), map, from, target);
    }
    // --8<-- [end:steer]

    // --8<-- [start:hunt]
    private Direction hunt(Game game, Player me, Cell from) {
        DistanceMap map = DistanceMap.search(game.tiles(),
                (col, row) -> game.walkable(new Cell(col, row)) && danger.at(col, row) == Danger.SAFE, from);
        if (bombAt == null && me.laid() < me.bombs() && worth(game, me, from) && escapes(game, me, from)) {
            bombAt = from;
        }
        if (bombAt != null) {
            target = bombAt;
            return toward(game.tiles(), map, from, bombAt);
        }
        Cell powerUp = nearest(game.tiles(), map, game.powerUps()::containsKey);
        if (powerUp != null && map.distance(powerUp) <= GREED) target = powerUp;
        else if (me.laid() < me.bombs()) {
            target = nearest(game.tiles(), map, cell -> worth(game, me, cell) && escapes(game, me, cell));
        } else target = null;
        if (target == null) target = nearest(game.tiles(), map, cell -> enemyAt(game, me, cell));
        if (target == null) return wander(game, from);
        return toward(game.tiles(), map, from, target);
    }

    /** A bomb here would reach a crate nobody else's bomb will reach, or another player. */
    private boolean worth(Game game, Player me, Cell at) {
        boolean[] worth = {false};
        game.reach(at, me.fire(), cell -> {
            if (game.tiles().get(cell) == Tile.CRATE && danger.safe(cell)) worth[0] = true;
            if (enemyAt(game, me, cell)) worth[0] = true;
        });
        return worth[0];
    }

    /** With a bomb here, a safe tile is near enough to reach before the flames come. */
    private boolean escapes(Game game, Player me, Cell at) {
        Danger after = Danger.of(game, at, me.fire());
        DistanceMap map = DistanceMap.search(game.tiles(),
                (col, row) -> game.walkable(new Cell(col, row)) && after.at(col, row) > IMMINENT, at);
        Cell refuge = nearest(game.tiles(), map, after::safe);
        return refuge != null && map.distance(refuge) / me.speed() < Field.FUSE_SECONDS * 0.7;
    }
    // --8<-- [end:hunt]

    private static boolean enemyAt(Game game, Player me, Cell cell) {
        for (Player other : game.players()) {
            if (other != me && other.playing() && other.cell().equals(cell)) return true;
        }
        return false;
    }

    /** Nothing to do: a step to a random safe neighbour, now and then. */
    private Direction wander(Game game, Cell from) {
        if (random.nextDouble() > 0.02) return null;
        Direction d = Direction.values()[random.nextInt(4)];
        Cell next = game.tiles().step(from.col(), from.row(), d);
        return game.walkable(next) && danger.safe(next) ? d : null;
    }

    /**
     * The reachable tile nearest to the search's start that {@code wanted} accepts; {@code null} for none.
     * The tiles are tried nearest first, so an expensive test runs only until one passes.
     */
    private static Cell nearest(TileMap<Tile> tiles, DistanceMap map, Predicate<Cell> wanted) {
        List<Cell> reachable = new ArrayList<>();
        for (int row = 0; row < tiles.height(); row++) {
            for (int col = 0; col < tiles.width(); col++) {
                if (map.distance(col, row) != DistanceMap.UNREACHABLE) reachable.add(new Cell(col, row));
            }
        }
        reachable.sort(Comparator.comparingInt(map::distance));      // stable: row by row among equals
        for (Cell cell : reachable) {
            if (wanted.test(cell)) return cell;
        }
        return null;
    }

    // --8<-- [start:toward]
    /**
     * The first step from {@code from} to {@code to}. The search began at {@code from}, so downhill from
     * {@code to} leads back to it; the last step of that way, turned round, is the first one there.
     */
    static Direction toward(TileMap<Tile> tiles, DistanceMap map, Cell from, Cell to) {
        if (to == null || to.equals(from) || map.distance(to) == DistanceMap.UNREACHABLE) return null;
        Cell cell = to;
        while (true) {
            Direction back = map.downhill(cell.col(), cell.row());
            if (back == null) return null;
            Cell previous = tiles.step(cell.col(), cell.row(), back);
            if (previous.equals(from)) return back.opposite();
            cell = previous;
        }
    }
    // --8<-- [end:toward]

    /** The centre the walker reaches next: its own tile while it stands, else the one ahead. */
    static Cell nextCentre(Walker walker) {
        Direction heading = walker.heading();
        if (walker.atCentre() || heading == null) return walker.cell();
        double along = heading.horizontal() ? walker.x() : walker.y();
        int sign = heading.dx + heading.dy;
        int ahead = (int) (sign > 0 ? Math.floor(along) + 1 : Math.ceil(along) - 1);
        return heading.horizontal() ? new Cell(ahead, walker.row()) : new Cell(walker.col(), ahead);
    }

    /** What it saw in the last step, for the screenshot of the chapter. */
    public Danger danger() {
        return danger;
    }

    public Cell target() {
        return target;
    }
}
