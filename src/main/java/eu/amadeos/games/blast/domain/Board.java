package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.path.DistanceMap;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.TileMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The arena as read from {@code board.txt}: the walls, where crates are certain, possible or never, where
 * the four players start, and which power-ups the crates hide. Each round {@linkplain #lay lays out} a fresh
 * arena from it, with crates and power-ups placed by chance.
 */
public final class Board {
    /** The letters of the file; {@link #lay} turns them into {@link Tile}s. */
    private enum Letter { WALL, FREE, MAYBE, CRATE }

    private static final Map<Character, Letter> LEGEND = Map.of('X', Letter.WALL, '_', Letter.FREE,
            '.', Letter.MAYBE, '+', Letter.CRATE);

    /** The arena of the game, from {@code board.txt} next to this class. */
    public static final Board STANDARD = read(TextData.load(Board.class, "/eu/amadeos/games/blast/board.txt"));

    private final TileMap<Letter> letters;
    private final List<Cell> starts;
    private final double crates;
    private final Map<PowerUp, Integer> hidden;
    private final List<Cell> spiral;

    private Board(TileMap<Letter> letters, List<Cell> starts, double crates, Map<PowerUp, Integer> hidden) {
        this.letters = letters;
        this.starts = List.copyOf(starts);
        this.crates = crates;
        this.hidden = hidden;
        this.spiral = spiral(letters);
    }

    // --8<-- [start:read]
    /** Reads an arena; every mistake is an error that names the file and the line. */
    public static Board read(List<TextData.Line> lines) {
        List<TextData.Line> rows = new ArrayList<>();
        List<TextData.Line> startLines = new ArrayList<>();
        double crates = 0;
        Map<PowerUp, Integer> hidden = new EnumMap<>(PowerUp.class);
        for (TextData.Line line : lines) {
            switch (line.keyword()) {
                case "row" -> rows.add(line);
                case "start" -> startLines.add(line);
                case "crates" -> {
                    crates = line.number(1);
                    if (crates < 0 || crates > 1) throw line.error("the chance of a crate is between 0 and 1");
                }
                case "hide" -> hidden.put(powerUp(line), line.integer(2));
                default -> throw line.error("\"" + line.keyword() + "\" is not a line of an arena");
            }
        }
        TileMap<Letter> letters = TileMap.read(rows, LEGEND);
        if (startLines.size() != Field.PLAYERS) {
            throw lines.getLast().error("an arena needs " + Field.PLAYERS + " starts, not " + startLines.size());
        }
        List<Cell> starts = new ArrayList<>();
        for (TextData.Line line : startLines) {
            Cell at = new Cell(line.integer(1), line.integer(2));
            if (!letters.contains(at.col(), at.row()) || letters.get(at) != Letter.FREE) {
                throw line.error("(" + at.col() + ", " + at.row() + ") is not a free tile, '_'");
            }
            if (!escapes(letters, at)) {
                throw line.error("a player at (" + at.col() + ", " + at.row()
                        + ") cannot escape a first bomb: it needs free tiles round a corner");
            }
            starts.add(at);
        }
        return new Board(letters, starts, crates, hidden);
    }

    /**
     * The flames of a bomb go straight: a tile round a corner is safe. Among the free tiles a player can
     * reach from the start, there must be two that are not in one row or column: lay the bomb on one, wait
     * on the other. Otherwise the player's first bomb is the end of the player.
     */
    private static boolean escapes(TileMap<Letter> letters, Cell start) {
        DistanceMap reach = DistanceMap.search(letters, (col, row) -> letters.get(col, row) == Letter.FREE, start);
        List<Cell> free = letters.cells(letter -> letter == Letter.FREE).stream()
                .filter(cell -> reach.distance(cell) != DistanceMap.UNREACHABLE).toList();
        for (Cell bomb : free) {
            for (Cell wait : free) {
                if (bomb.col() != wait.col() && bomb.row() != wait.row()) return true;
            }
        }
        return false;
    }
    // --8<-- [end:read]

    private static PowerUp powerUp(TextData.Line line) {
        for (PowerUp powerUp : PowerUp.values()) {
            if (powerUp.id().equals(line.word(1))) return powerUp;
        }
        throw line.error("\"" + line.word(1) + "\" is not a power-up: bomb, fire or speed");
    }

    // --8<-- [start:lay]
    /**
     * The arena of a round: walls as in the file, crates where the file says so and, by chance, where it
     * allows them; the power-ups go into crates chosen by chance, into {@code powerUps}.
     */
    public TileMap<Tile> lay(SeededRandom random, Map<Cell, PowerUp> powerUps) {
        TileMap<Tile> tiles = new TileMap<>(width(), height(), Tile.FLOOR);
        List<Cell> crateCells = new ArrayList<>();
        for (int row = 0; row < height(); row++) {
            for (int col = 0; col < width(); col++) {
                Tile tile = switch (letters.get(col, row)) {
                    case WALL -> Tile.WALL;
                    case FREE -> Tile.FLOOR;
                    case CRATE -> Tile.CRATE;
                    case MAYBE -> random.nextDouble() < crates ? Tile.CRATE : Tile.FLOOR;
                };
                tiles.set(col, row, tile);
                if (tile == Tile.CRATE) crateCells.add(new Cell(col, row));
            }
        }
        // Shuffle the crates with the game's generator, so the same seed hides the same power-ups.
        for (int i = crateCells.size() - 1; i > 0; i--) Collections.swap(crateCells, i, random.nextInt(i + 1));
        int next = 0;
        for (PowerUp powerUp : PowerUp.values()) {
            for (int n = 0; n < hidden.getOrDefault(powerUp, 0) && next < crateCells.size(); n++) {
                powerUps.put(crateCells.get(next++), powerUp);
            }
        }
        return tiles;
    }
    // --8<-- [end:lay]

    // --8<-- [start:spiral]
    /**
     * The order in which walls fall when time is up: ring after ring from the outside in, each clockwise
     * from its top left corner, all tiles that are not walls already.
     */
    private static List<Cell> spiral(TileMap<Letter> letters) {
        List<Cell> order = new ArrayList<>();
        int left = 0;
        int top = 0;
        int right = letters.width() - 1;
        int bottom = letters.height() - 1;
        while (left <= right && top <= bottom) {
            for (int col = left; col <= right; col++) order.add(new Cell(col, top));
            for (int row = top + 1; row <= bottom; row++) order.add(new Cell(right, row));
            if (top < bottom) for (int col = right - 1; col >= left; col--) order.add(new Cell(col, bottom));
            if (left < right) for (int row = bottom - 1; row > top; row--) order.add(new Cell(left, row));
            left++;
            top++;
            right--;
            bottom--;
        }
        order.removeIf(cell -> letters.get(cell) == Letter.WALL);
        return List.copyOf(order);
    }
    // --8<-- [end:spiral]

    public int width() {
        return letters.width();
    }

    public int height() {
        return letters.height();
    }

    /** Where the players start, in the order of the players. */
    public List<Cell> starts() {
        return starts;
    }

    /** True for the walls of the file, which stand from the start: the painter draws them once. */
    public boolean wall(int col, int row) {
        return letters.get(col, row) == Letter.WALL;
    }

    public List<Cell> spiral() {
        return spiral;
    }
}
