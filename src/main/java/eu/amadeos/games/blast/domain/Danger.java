package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.TileMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * For every tile, how many seconds until flames reach it: what a computer player must know before it takes a
 * step. A bomb threatens the tiles its flames will {@linkplain Game#reach reach}, when its fuse runs out,
 * <i>or earlier</i>: a bomb in the flames of another goes off with that one. So the times are passed along
 * the chains first, until no bomb's time changes any more; then each tile gets the earliest time of the bombs
 * that reach it. Tiles in flames now have 0, tiles where walls are about to fall a little more.
 */
// --8<-- [start:danger]
public final class Danger {
    /** The time of a tile no flame will reach. */
    public static final double SAFE = Double.POSITIVE_INFINITY;

    private final TileMap<Tile> tiles;
    private final double[] doom;

    private Danger(TileMap<Tile> tiles) {
        this.tiles = tiles;
        this.doom = new double[tiles.width() * tiles.height()];
        Arrays.fill(doom, SAFE);
    }

    /** The danger on the arena as it is. */
    public static Danger of(Game game) {
        return of(game, null, 0);
    }

    /** The danger if a bomb with {@code range} were laid at {@code extra} now: could one escape it? */
    public static Danger of(Game game, Cell extra, int range) {
        Danger danger = new Danger(game.tiles());
        List<Cell> at = new ArrayList<>();
        List<Integer> ranges = new ArrayList<>();
        List<Double> times = new ArrayList<>();
        for (Bomb bomb : game.bombs()) {
            at.add(bomb.cell());
            ranges.add(bomb.range());
            times.add(game.fuse(bomb));
        }
        if (extra != null) {
            at.add(extra);
            ranges.add(range);
            times.add(Field.FUSE_SECONDS);
        }
        // Which tiles each bomb's flames reach.
        List<List<Cell>> reached = new ArrayList<>();
        for (int i = 0; i < at.size(); i++) {
            List<Cell> cells = new ArrayList<>();
            game.reach(at.get(i), ranges.get(i), cells::add);
            reached.add(cells);
        }
        // A bomb in the flames of an earlier one goes off with it; and a third one with that one.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < at.size(); i++) {
                for (int j = 0; j < at.size(); j++) {
                    if (times.get(j) > times.get(i) && reached.get(i).contains(at.get(j))) {
                        times.set(j, times.get(i));
                        changed = true;
                    }
                }
            }
        }
        for (int i = 0; i < at.size(); i++) {
            for (Cell cell : reached.get(i)) danger.lower(cell, times.get(i));
        }
        for (Cell cell : game.flameCells()) danger.lower(cell, 0);
        List<Cell> walls = game.nextWalls(8);
        for (int i = 0; i < walls.size(); i++) danger.lower(walls.get(i), i * Field.HURRY_INTERVAL);
        return danger;
    }
    // --8<-- [end:danger]

    private void lower(Cell cell, double seconds) {
        int index = cell.row() * tiles.width() + cell.col();
        doom[index] = Math.min(doom[index], seconds);
    }

    /** Seconds until flames reach the tile; {@link #SAFE} if none will. */
    public double at(int col, int row) {
        return doom[row * tiles.width() + col];
    }

    public double at(Cell cell) {
        return at(cell.col(), cell.row());
    }

    public boolean safe(Cell cell) {
        return at(cell) == SAFE;
    }
}
