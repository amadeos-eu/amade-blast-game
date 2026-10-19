package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.path.DistanceMap;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotTest {
    static final double STEP = 1.0 / 120;

    /** Four computer players play a match; the log says who was hit when, and who won. */
    static String match(long seed) {
        Game game = new Game(seed);
        List<Bot> bots = new ArrayList<>();
        for (int i = 0; i < Field.PLAYERS; i++) bots.add(new Bot(seed * 31 + i));
        StringBuilder log = new StringBuilder();
        for (int step = 0; step < 120 * 60 * 20 && game.phase() != Game.Phase.MATCH_OVER; step++) {
            List<Move> moves = new ArrayList<>();
            for (Player player : game.players()) moves.add(bots.get(player.index()).steer(game, player, STEP));
            game.update(STEP, moves);
            for (Event event : game.events()) {
                if (event instanceof Event.PlayerHit hit) {
                    log.append(String.format("hit %d at %.2f s%n", hit.player(), game.roundTime()));
                }
                if (event instanceof Event.RoundOver over) {
                    log.append(String.format("round %d: %d%n", over.round(), over.winner()));
                }
            }
        }
        return log.append(game.phase()).toString();
    }

    // --8<-- [start:test-danger]
    @Test
    void dangerFollowsTheChain() {
        Game game = new Game(1, BoardTest.read(BoardTest.SMALL), new boolean[]{true, true, true, true});
        game.skipReady();
        game.putPlayer(0, 1, 1);
        game.update(STEP, List.of(new Move(null, true), Move.NONE, Move.NONE, Move.NONE));
        game.update(1.0, List.of(Move.NONE, Move.NONE, Move.NONE, Move.NONE));
        game.putPlayer(1, 3, 1);
        game.update(STEP, List.of(Move.NONE, new Move(null, true), Move.NONE, Move.NONE));
        Danger danger = Danger.of(game);
        double first = Field.FUSE_SECONDS - 1.0 - STEP;    // laid, a second, the second bomb
        assertEquals(first, danger.at(1, 1), 1e-9);
        assertEquals(first, danger.at(5, 1), 1e-9, "the second bomb goes off with the first");
        assertEquals(first, danger.at(3, 2), 1e-9, "and so do the tiles only it reaches");
        assertTrue(danger.safe(new Cell(7, 1)));
        assertTrue(danger.safe(new Cell(7, 3)));
    }
    // --8<-- [end:test-danger]

    @Test
    void theWayToATileIsFoundBackwards() {
        Game game = new Game(1, BoardTest.read(BoardTest.SMALL), new boolean[]{true, true, true, true});
        Cell from = new Cell(1, 1);
        DistanceMap map = DistanceMap.search(game.tiles(), (c, r) -> game.tiles().get(c, r).open(), from);
        assertEquals(Direction.RIGHT, Bot.toward(game.tiles(), map, from, new Cell(5, 1)));
        assertEquals(Direction.DOWN, Bot.toward(game.tiles(), map, from, new Cell(1, 3)));
        assertEquals(null, Bot.toward(game.tiles(), map, from, from));
    }

    @Test
    void aBotOnItsBombRunsRoundTheCorner() {
        Game game = new Game(1, BoardTest.read(BoardTest.SMALL), new boolean[]{true, true, true, true});
        game.skipReady();
        game.putPlayer(0, 3, 1);
        game.update(STEP, List.of(new Move(null, true), Move.NONE, Move.NONE, Move.NONE));
        Bot bot = new Bot(3);
        Move[] moves = {Move.NONE, Move.NONE, Move.NONE, Move.NONE};
        for (int i = 0; i < (int) (Field.FUSE_SECONDS * 120) + 60; i++) {
            moves[0] = bot.steer(game, game.player(0), STEP);
            game.update(STEP, Arrays.asList(moves));
        }
        assertTrue(game.player(0).playing(), "it escaped its own bomb");
        Cell at = game.player(0).cell();
        assertFalse(at.row() == 1 && Math.abs(at.col() - 3) <= 2, "out of the flames' line: " + at);
    }

    // --8<-- [start:test-demo]
    @Test
    void computerPlayersPlayWholeMatchesAndSurviveTheirFirstBombs() {
        int early = 0;
        int rounds = 0;
        for (long seed = 1; seed <= 4; seed++) {
            String log = match(seed);
            assertTrue(log.endsWith("MATCH_OVER"), log);
            rounds += (int) log.lines().filter(line -> line.startsWith("round")).count();
            early += (int) log.lines().filter(line -> line.startsWith("hit") && seconds(line) < 10).count();
        }
        assertTrue(early <= rounds / 2, early + " hit in the first ten seconds of " + rounds + " rounds");
    }
    // --8<-- [end:test-demo]

    private static double seconds(String hit) {
        return Double.parseDouble(hit.substring(hit.indexOf(" at ") + 4, hit.indexOf(" s")));
    }
}
