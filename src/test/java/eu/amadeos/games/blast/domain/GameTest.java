package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {
    static final double STEP = 1.0 / 120;

    private final List<Event> events = new ArrayList<>();
    private final Move[] moves = {Move.NONE, Move.NONE, Move.NONE, Move.NONE};

    /** A match on the small arena of {@link BoardTest}, past "Ready?". */
    private Game small() {
        Game game = new Game(1, BoardTest.read(BoardTest.SMALL), new boolean[]{true, true, true, true});
        game.skipReady();
        return game;
    }

    /** One step with the moves set; they are used up. */
    private void step(Game game) {
        game.update(STEP, Arrays.asList(moves));
        events.addAll(game.events());
        Arrays.fill(moves, Move.NONE);
    }

    /** Steps for {@code seconds}, with the moves set held all the time. */
    private void seconds(Game game, double seconds) {
        for (int i = 0; i < Math.round(seconds / STEP); i++) {
            game.update(STEP, Arrays.asList(moves));
            events.addAll(game.events());
        }
        Arrays.fill(moves, Move.NONE);
    }

    private void bomb(Game game, int player) {
        moves[player] = new Move(null, true);
        step(game);
    }

    // --8<-- [start:test-explode]
    @Test
    void aBombExplodesAfterItsFuseAndItsFlamesStopAtWallsAndCrates() {
        Game game = small();
        game.put(4, 1, Tile.CRATE);
        game.putPowerUp(4, 1, PowerUp.FIRE);
        game.putPlayer(0, 2, 1);
        game.putPlayer(2, 7, 2);                      // out of the way
        bomb(game, 0);
        Bomb bomb = game.bomb(new Cell(2, 1));
        assertNotNull(bomb);
        assertEquals(2, bomb.range());
        moves[0] = new Move(Direction.LEFT, false);
        seconds(game, 0.5);                           // to (1, 1), then down: round the corner
        moves[0] = new Move(Direction.DOWN, false);
        seconds(game, 0.5);
        assertEquals(new Cell(1, 3), game.player(0).cell());
        seconds(game, Field.FUSE_SECONDS - 1.0 - STEP);
        assertEquals(0, game.flames(new Cell(2, 1)), "not yet");
        step(game);
        assertTrue(game.flames(new Cell(2, 1)) > 0, "the bomb's own tile");
        assertTrue(game.flames(new Cell(3, 1)) > 0);
        assertEquals(0, game.flames(new Cell(2, 2)), "a pillar stops the flame");
        assertEquals(0, game.flames(new Cell(4, 1)), "a crate stops it, and burns");
        assertTrue(game.burning(new Cell(4, 1)) > 0);
        assertTrue(game.player(0).playing(), "safe round the corner");
        seconds(game, Field.BURN_SECONDS);
        assertEquals(Tile.FLOOR, game.tiles().get(4, 1));
        assertEquals(PowerUp.FIRE, game.powerUps().get(new Cell(4, 1)), "what the crate hid");
        assertTrue(events.contains(new Event.CrateBurnt(new Cell(4, 1), PowerUp.FIRE)));
    }
    // --8<-- [end:test-explode]

    // --8<-- [start:test-chain]
    @Test
    void aFlameSetsOffTheBombsItReachesInTheSameStep() {
        Game game = small();
        game.put(5, 1, Tile.FLOOR);
        game.putPlayer(0, 1, 1);
        bomb(game, 0);                                // at (1, 1)
        game.putPlayer(1, 3, 1);
        seconds(game, 1.0);
        bomb(game, 1);                                // at (3, 1), a second later
        game.putPlayer(2, 5, 1);
        bomb(game, 2);                                // at (5, 1), out of reach of the first
        game.putPlayer(0, 7, 2);
        game.putPlayer(1, 7, 2);
        game.putPlayer(2, 7, 2);
        game.putPlayer(3, 7, 2);
        events.clear();
        seconds(game, game.fuse(game.bomb(new Cell(1, 1))) - STEP);
        assertTrue(events.stream().noneMatch(e -> e instanceof Event.Exploded));
        step(game);
        List<Event> exploded = events.stream().filter(e -> e instanceof Event.Exploded).toList();
        assertEquals(3, exploded.size(), "all three in one step");
        Event.Exploded first = (Event.Exploded) exploded.get(0);
        Event.Exploded third = (Event.Exploded) exploded.get(2);
        assertEquals(new Cell(1, 1), first.bomb().cell());
        assertFalse(first.chained());
        assertEquals(new Cell(5, 1), third.bomb().cell(), "the second set off the third");
        assertTrue(third.chained());
        assertTrue(game.bombs().isEmpty());
        assertEquals(0, game.player(1).laid(), "the bombs are given back");
    }
    // --8<-- [end:test-chain]

    @Test
    void playersDoNotWalkOntoBombsButLeaveTheirOwn() {
        Game game = small();
        game.putPlayer(0, 3, 1);
        bomb(game, 0);
        moves[0] = new Move(Direction.RIGHT, false);
        seconds(game, 0.5);
        assertEquals(new Cell(5, 1), game.player(0).cell(), "off its own bomb");
        moves[0] = new Move(Direction.LEFT, false);
        seconds(game, 0.8);
        assertEquals(new Cell(4, 1), game.player(0).cell(), "stopped in front of it");
        assertTrue(game.player(0).walker().atCentre());
        bomb(game, 0);
        assertNull(game.bomb(new Cell(4, 1)), "one bomb at a time");
    }

    // --8<-- [start:test-round]
    @Test
    void aPlayerInTheFlamesFallsAndTheLastOneWinsTheRound() {
        Game game = new Game(1, BoardTest.read(BoardTest.SMALL), new boolean[]{true, true, false, false});
        game.skipReady();
        game.putPlayer(0, 3, 1);
        game.putPlayer(1, 7, 3);
        bomb(game, 0);
        seconds(game, Field.FUSE_SECONDS);
        assertEquals(Player.State.DYING, game.player(0).state(), "it stayed on its bomb");
        assertTrue(events.stream().anyMatch(e -> e instanceof Event.PlayerHit(int p, double x, double y) && p == 0));
        seconds(game, Field.DYING_SECONDS + STEP);
        assertEquals(Game.Phase.ROUND_OVER, game.phase());
        assertTrue(events.contains(new Event.RoundOver(1, 1)));
        assertEquals(1, game.player(1).wins());
        seconds(game, Field.ROUND_OVER_SECONDS + STEP);
        assertEquals(2, game.round());
        assertEquals(Game.Phase.READY, game.phase());
        assertEquals(new Cell(1, 1), game.player(0).cell(), "back at the start");
        assertTrue(game.player(0).playing());
        assertEquals(Player.State.OUT, game.player(2).state(), "nobody took this seat");
    }
    // --8<-- [end:test-round]

    @Test
    void powerUpsAreTakenByWalkingOverThem() {
        Game game = small();
        game.putPlayer(0, 1, 1);
        game.putPowerUp(2, 1, PowerUp.BOMB);
        game.putPowerUp(3, 1, PowerUp.SPEED);
        moves[0] = new Move(Direction.RIGHT, false);
        seconds(game, 0.6);
        assertEquals(2, game.player(0).bombs());
        assertEquals(1, game.player(0).speedUps());
        assertEquals(Field.SPEED + Field.SPEED_UP, game.player(0).speed());
        assertTrue(game.powerUps().isEmpty());
    }

    // --8<-- [start:test-hurry]
    @Test
    void whenTimeIsUpWallsFallOnWhateverIsThere() {
        Game game = small();
        game.putPlayer(1, 2, 1);                       // the second tile of the spiral
        game.passTime(Field.ROUND_SECONDS - STEP);
        step(game);
        assertInstanceOf(Event.Hurry.class, events.getFirst());
        seconds(game, 2 * Field.HURRY_INTERVAL);
        assertEquals(Tile.WALL, game.tiles().get(1, 1));
        assertEquals(Tile.WALL, game.tiles().get(2, 1));
        assertEquals(Player.State.DYING, game.player(1).state());
        assertTrue(events.contains(new Event.WallFell(new Cell(2, 1))));
    }
    // --8<-- [end:test-hurry]

    // --8<-- [start:test-determinism]
    @Test
    void theSameSeedAndTheSameMovesPlayTheSameMatch() {
        assertEquals(BotTest.match(42), BotTest.match(42));
    }
    // --8<-- [end:test-determinism]

    @Test
    void aMatchNeedsTwoPlayers() {
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Game(1, Board.STANDARD, new boolean[]{true, false, false, false}))
                .getMessage().contains("two players"));
    }
}
