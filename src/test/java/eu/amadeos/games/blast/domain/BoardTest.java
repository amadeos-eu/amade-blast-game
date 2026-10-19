package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.TileMap;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {
    /** A small arena without crates: a corridor round a row of pillars. */
    static final String SMALL = """
            row XXXXXXXXX
            row X_______X
            row X_X_X_X_X
            row X_______X
            row XXXXXXXXX
            start 1 1
            start 7 3
            start 7 1
            start 1 3
            """;

    static Board read(String text) {
        return Board.read(TextData.parse("board.txt", new StringReader(text)));
    }

    // --8<-- [start:test-lay]
    @Test
    void theSameSeedLaysTheSameArena() {
        Map<Cell, PowerUp> hidden = new HashMap<>();
        TileMap<Tile> tiles = Board.STANDARD.lay(new SeededRandom(7), hidden);
        Map<Cell, PowerUp> again = new HashMap<>();
        TileMap<Tile> same = Board.STANDARD.lay(new SeededRandom(7), again);
        assertEquals(tiles.cells(t -> t == Tile.CRATE), same.cells(t -> t == Tile.CRATE));
        assertEquals(hidden, again);
        assertEquals(26, hidden.size(), "10 bombs, 10 flames, 6 shoes");
        for (Cell at : hidden.keySet()) assertEquals(Tile.CRATE, tiles.get(at), "hidden in crates");
        for (Cell start : Board.STANDARD.starts()) assertEquals(Tile.FLOOR, tiles.get(start));
        assertNotEquals(tiles.cells(t -> t == Tile.CRATE),
                Board.STANDARD.lay(new SeededRandom(8), new HashMap<>()).cells(t -> t == Tile.CRATE));
    }
    // --8<-- [end:test-lay]

    // --8<-- [start:test-errors]
    @Test
    void mistakesInTheFileNameTheLine() {
        assertEquals("board.txt:7: (2, 2) is not a free tile, '_'",
                assertThrows(TextData.DataException.class, () -> read(SMALL.replace("start 7 3", "start 2 2")))
                        .getMessage());
        assertEquals("board.txt:6: a player at (1, 1) cannot escape a first bomb: it needs free tiles round a corner",
                assertThrows(TextData.DataException.class, () -> read(SMALL
                        .replace("row X_______X\nrow X_X", "row X_++++++X\nrow X+X"))).getMessage(),
                "walled in by crates: the first bomb would be the last");
        assertEquals("board.txt:3: \"shield\" is not a power-up: bomb, fire or speed",
                assertThrows(TextData.DataException.class, () -> read("crates 0.5\nhide bomb 2\nhide shield 1\n"))
                        .getMessage());
    }
    // --8<-- [end:test-errors]

    @Test
    void wallsFallFromTheOutsideIn() {
        Board board = read(SMALL);
        List<Cell> spiral = board.spiral();
        assertEquals(new Cell(1, 1), spiral.getFirst());
        assertEquals(new Cell(7, 1), spiral.get(6), "along the top first");
        assertEquals(new Cell(7, 2), spiral.get(7), "then down the right side");
        assertEquals(18, spiral.size(), "every tile that is not a wall");
        assertTrue(Board.STANDARD.spiral().indexOf(new Cell(1, 1)) < Board.STANDARD.spiral().indexOf(new Cell(7, 6)),
                "the middle comes last");
        assertEquals(new Cell(7, 6), Board.STANDARD.spiral().getLast());
    }
}
