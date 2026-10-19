package eu.amadeos.games.blast.domain;

import java.util.Arrays;
import java.util.function.Consumer;

/** Scenes set up by hand for the screenshots, with the helpers only the tests of this package may use. */
public final class Stage {
    private static final double STEP = 1.0 / 120;

    private Stage() {
    }

    /**
     * Four bombs in the top corridor, laid a third of a second apart; returned in the step the first one
     * explodes and sets off the other three. {@code each} sees the game after every step.
     */
    public static Game chain(long seed, Consumer<Game> each) {
        Game game = new Game(seed);
        game.skipReady();
        for (int col = 1; col <= 9; col++) game.put(col, 1, Tile.FLOOR);
        for (int col = 1; col <= 9; col += 2) {
            game.put(col, 2, Tile.FLOOR);
            game.put(col, 3, Tile.FLOOR);
        }
        Move[] moves = {Move.NONE, Move.NONE, Move.NONE, Move.NONE};
        int[] at = {1, 3, 5, 7};
        int[][] away = {{1, 9}, {13, 5}, {11, 11}, {13, 9}};
        for (int[] cell : away) game.put(cell[0], cell[1], Tile.FLOOR);
        for (int p = 0; p < 4; p++) {
            game.putPlayer(p, at[p], 1);
            moves[p] = new Move(null, true);
            step(game, moves, each);
            moves[p] = Move.NONE;
            game.putPlayer(p, away[p][0], away[p][1]);
            for (int i = 0; i < Math.round(1.0 / 3 / STEP); i++) step(game, moves, each);
        }
        while (game.events().stream().noneMatch(e -> e instanceof Event.Exploded)) step(game, moves, each);
        return game;
    }

    private static void step(Game game, Move[] moves, Consumer<Game> each) {
        game.update(STEP, Arrays.asList(moves));
        each.accept(game);
    }
}
