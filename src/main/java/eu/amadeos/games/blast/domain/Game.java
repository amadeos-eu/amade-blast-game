package eu.amadeos.games.blast.domain;

import eu.amadeos.gameengine.event.EventQueue;
import eu.amadeos.gameengine.loop.Ticker;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.Direction;
import eu.amadeos.gameengine.tile.TileMap;
import eu.amadeos.gameengine.time.Timers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A match of Blast: rounds on the arena until a player has won {@link Field#WINS} of them. Plain Java
 * without a clock: {@link #update} advances everything by a step, with the four players' moves in that step,
 * and the {@link #events()} tell what happened in it.
 *
 * <p>Everything that waits has a timer: the fuse of each bomb, the flames on each tile, each burning crate.
 * Everything that happens is an {@link Event} on a queue, handled in order: an explosion posts the
 * explosions of the bombs its flames reach, which is all a chain reaction is. Chance (where crates stand,
 * what they hide) comes from a {@link SeededRandom} made from the seed, so a test can play the same match
 * twice.
 */
public final class Game {
    /** "Ready?" before a round; playing; the round is decided and its winner shown; the match is over. */
    public enum Phase { READY, PLAYING, ROUND_OVER, MATCH_OVER }

    private final long seed;
    private final SeededRandom random;
    private final Board board;
    private final List<Player> players = new ArrayList<>();
    // --8<-- [start:state]
    private final EventQueue<Event> events = new EventQueue<>();
    private final Timers<Bomb> fuses = new Timers<>();
    private final Timers<Cell> flames = new Timers<>();
    private final Timers<Cell> burning = new Timers<>();
    private final Map<Cell, Bomb> bombs = new LinkedHashMap<>();
    private final Map<Cell, PowerUp> hidden = new HashMap<>();
    private final Map<Cell, PowerUp> powerUps = new LinkedHashMap<>();
    // --8<-- [end:state]
    private final Ticker hurry = new Ticker(Field.HURRY_INTERVAL);
    private TileMap<Tile> tiles;
    private Phase phase = Phase.READY;
    private double phaseTime;
    private double roundTime;
    private int round;
    /** Walls fallen this round, the place in the spiral. */
    private int wallsFallen;
    private MatchResult result;

    /** A match of four on the standard arena. */
    public Game(long seed) {
        this(seed, Board.STANDARD, new boolean[]{true, true, true, true});
    }

    /** @param present which of the four players take part; at least two */
    public Game(long seed, Board board, boolean[] present) {
        this.seed = seed;
        this.random = new SeededRandom(seed);
        this.board = board;
        int count = 0;
        for (int i = 0; i < Field.PLAYERS; i++) {
            players.add(new Player(i, present[i]));
            if (present[i]) count++;
        }
        if (count < 2) throw new IllegalArgumentException("a match needs two players at least");
        startRound();
    }

    // --8<-- [start:update]
    /** One step of {@code seconds}; {@code moves} holds what each of the four players wants. */
    public void update(double seconds, List<Move> moves) {
        events.clear();
        phaseTime += seconds;
        switch (phase) {
            case READY -> {
                if (phaseTime >= Field.READY_SECONDS) {
                    phase(Phase.PLAYING);
                    events.post(new Event.Go());
                }
            }
            case PLAYING -> play(seconds, moves);
            case ROUND_OVER -> {
                if (phaseTime >= Field.ROUND_OVER_SECONDS) {
                    if (result != null) phase(Phase.MATCH_OVER);
                    else startRound();
                }
            }
            case MATCH_OVER -> {
            }
        }
        events.process(this::handle);
    }

    private void play(double seconds, List<Move> moves) {
        roundTime += seconds;
        // Whatever ran out in this step, in the order it ran out.
        fuses.update(seconds, bomb -> detonate(bomb, false));
        burning.update(seconds, this::burnt);
        flames.update(seconds, cell -> { });
        for (Player player : players) {
            if (!player.playing()) continue;
            Move move = moves.get(player.index());
            player.move(seconds, move.direction(), this);
            take(player);
            if (move.bomb()) lay(player);
        }
        hurry(seconds);
        events.process(this::handle);             // the explosions, and all they cause
        for (Player player : players) {
            if (player.playing() && flames.running(player.cell())) hit(player);
        }
        events.process(this::handle);
        for (Player player : players) {
            if (player.state() == Player.State.DYING) player.fall(seconds);
        }
        decide();
    }
    // --8<-- [end:update]

    // --8<-- [start:handle]
    /** The one place where events have consequences; posting more events is how one causes the next. */
    private void handle(Event event) {
        switch (event) {
            case Event.Exploded(Bomb bomb, boolean chained) -> explode(bomb);
            case Event.CrateHit(Cell at) -> burning.start(at, Field.BURN_SECONDS);
            case Event.PlayerHit hit -> {
                Player player = players.get(hit.player());
                if (player.playing()) player.hit();
            }
            default -> {
            }
        }
    }

    /** Takes a bomb off the arena and posts its explosion; a bomb already gone does nothing. */
    private void detonate(Bomb bomb, boolean chained) {
        if (!bombs.remove(bomb.cell(), bomb)) return;
        fuses.cancel(bomb);
        players.get(bomb.owner()).laid(-1);
        events.post(new Event.Exploded(bomb, chained));
    }

    /** Flames on the bomb's tile and along the four directions, as far as {@link #reach} lets them go. */
    private void explode(Bomb bomb) {
        reach(bomb.cell(), bomb.range(), cell -> {
            switch (tiles.get(cell)) {
                case CRATE -> {
                    if (!burning.running(cell)) events.post(new Event.CrateHit(cell));
                }
                case FLOOR -> {
                    flames.start(cell, Field.FLAME_SECONDS);
                    Bomb other = bombs.get(cell);
                    if (other != null) detonate(other, true);          // the chain reaction
                    PowerUp powerUp = powerUps.remove(cell);
                    if (powerUp != null) events.post(new Event.PowerUpBurnt(cell, powerUp));
                }
                case WALL -> {
                }
            }
        });
    }
    // --8<-- [end:handle]

    // --8<-- [start:reach]
    /**
     * The tiles the flames of a bomb at {@code from} reach: its own, and up to {@code range} in each
     * direction. Walls stop the flames before them; a crate, a bomb or a power-up is reached and stops them
     * there. The explosion and the computer players' fear of it both use this, so they always agree.
     */
    public void reach(Cell from, int range, Consumer<Cell> reached) {
        reached.accept(from);
        for (Direction d : Direction.values()) {
            Cell cell = from;
            for (int i = 1; i <= range; i++) {
                cell = tiles.step(cell.col(), cell.row(), d);
                Tile tile = tiles.get(cell);
                if (tile == Tile.WALL) break;
                reached.accept(cell);
                if (tile == Tile.CRATE || bombs.containsKey(cell) || powerUps.containsKey(cell)) break;
            }
        }
    }
    // --8<-- [end:reach]

    private void lay(Player player) {
        Cell at = player.cell();
        if (player.laid() >= player.bombs() || bombs.containsKey(at) || flames.running(at)) return;
        Bomb bomb = new Bomb(player.index(), at, player.fire());
        bombs.put(at, bomb);
        fuses.start(bomb, Field.FUSE_SECONDS);
        player.laid(1);
        events.post(new Event.BombLaid(player.index(), at));
    }

    private void take(Player player) {
        Cell at = player.cell();
        PowerUp powerUp = powerUps.remove(at);
        if (powerUp == null) return;
        player.take(powerUp);
        events.post(new Event.PowerUpTaken(player.index(), powerUp, at));
    }

    /** A crate has burnt down: floor, and what it hid lies there now. */
    private void burnt(Cell at) {
        tiles.set(at.col(), at.row(), Tile.FLOOR);
        PowerUp powerUp = hidden.remove(at);
        if (powerUp != null) powerUps.put(at, powerUp);
        events.post(new Event.CrateBurnt(at, powerUp));
    }

    private void hit(Player player) {
        events.post(new Event.PlayerHit(player.index(), player.walker().x(), player.walker().y()));
    }

    // --8<-- [start:hurry]
    /** When time is up, a wall falls on the next tile of the spiral, on whatever lies there. */
    private void hurry(double seconds) {
        if (roundTime < Field.ROUND_SECONDS) return;
        if (roundTime - seconds < Field.ROUND_SECONDS) events.post(new Event.Hurry());
        for (int due = hurry.advance(seconds); due > 0 && wallsFallen < board.spiral().size(); due--) {
            Cell at = board.spiral().get(wallsFallen);
            wallsFallen++;
            if (tiles.get(at) == Tile.WALL) continue;
            tiles.set(at.col(), at.row(), Tile.WALL);
            Bomb bomb = bombs.remove(at);
            if (bomb != null) {
                fuses.cancel(bomb);
                players.get(bomb.owner()).laid(-1);
            }
            powerUps.remove(at);
            hidden.remove(at);
            burning.cancel(at);
            flames.cancel(at);
            for (Player player : players) {
                if (player.playing() && player.cell().equals(at)) hit(player);
            }
            events.post(new Event.WallFell(at));
        }
    }
    // --8<-- [end:hurry]

    // --8<-- [start:decide]
    /** The round is over when at most one player is left and nobody is still falling. */
    private void decide() {
        List<Player> left = new ArrayList<>();
        boolean falling = false;
        for (Player player : players) {
            if (player.playing()) left.add(player);
            if (player.state() == Player.State.DYING) falling = true;
        }
        if (left.size() > 1 || falling) return;
        int winner = left.isEmpty() ? -1 : left.getFirst().index();
        if (winner >= 0) players.get(winner).won();
        phase(Phase.ROUND_OVER);
        events.post(new Event.RoundOver(round, winner));
        if (winner >= 0 && players.get(winner).wins() >= Field.WINS) {
            int[] wins = players.stream().mapToInt(Player::wins).toArray();
            result = new MatchResult(winner, wins);
            events.post(new Event.MatchOver(result));
        }
    }
    // --8<-- [end:decide]

    /** A fresh arena, everybody back at the start. */
    private void startRound() {
        round++;
        bombs.clear();
        fuses.clear();
        flames.clear();
        burning.clear();
        hidden.clear();
        powerUps.clear();
        tiles = board.lay(random, hidden);
        for (Player player : players) player.reset(board.starts().get(player.index()));
        roundTime = 0;
        wallsFallen = 0;
        hurry.reset();
        phase(Phase.READY);
        events.post(new Event.Ready(round));
    }

    private void phase(Phase phase) {
        this.phase = phase;
        this.phaseTime = 0;
    }

    // ---- for the players, the computer players and the painter ---------------------------------

    /** True if a player may walk onto {@code cell}: floor, without a bomb. */
    public boolean walkable(Cell cell) {
        return tiles.get(cell).open() && !bombs.containsKey(cell);
    }

    public long seed() {
        return seed;
    }

    public Board board() {
        return board;
    }

    /** The arena of this round. Read only. */
    public TileMap<Tile> tiles() {
        return tiles;
    }

    public Player player(int index) {
        return players.get(index);
    }

    public List<Player> players() {
        return Collections.unmodifiableList(players);
    }

    public Collection<Bomb> bombs() {
        return Collections.unmodifiableCollection(bombs.values());
    }

    public Bomb bomb(Cell cell) {
        return bombs.get(cell);
    }

    /** Seconds until a bomb explodes by its fuse. */
    public double fuse(Bomb bomb) {
        return fuses.left(bomb);
    }

    /** Seconds the flames on a tile still burn; 0 without flames. */
    public double flames(Cell cell) {
        return flames.left(cell);
    }

    public Iterable<Cell> flameCells() {
        return flames.keys();
    }

    /** Seconds a crate still burns; 0 if it does not burn. */
    public double burning(Cell cell) {
        return burning.left(cell);
    }

    public Map<Cell, PowerUp> powerUps() {
        return Collections.unmodifiableMap(powerUps);
    }

    /** The tiles where walls fall next, the next first: the computer players keep away from them. */
    public List<Cell> nextWalls(int count) {
        if (roundTime < Field.ROUND_SECONDS - 3) return List.of();
        int from = Math.min(wallsFallen, board.spiral().size());
        return board.spiral().subList(from, Math.min(board.spiral().size(), from + count));
    }

    public Phase phase() {
        return phase;
    }

    public double phaseTime() {
        return phaseTime;
    }

    public int round() {
        return round;
    }

    /** Seconds since the round began. */
    public double roundTime() {
        return roundTime;
    }

    /** Seconds until the walls fall; 0 when they do. */
    public double timeLeft() {
        return Math.max(0, Field.ROUND_SECONDS - roundTime);
    }

    /** The end of the match, {@code null} while it goes on. */
    public MatchResult result() {
        return result;
    }

    /** What happened in the last step, in the order it happened. */
    public List<Event> events() {
        return events.happened();
    }

    SeededRandom random() {
        return random;
    }

    // ---- for tests ---------------------------------------------------------------------------------

    /** Sets a tile, e.g. to clear the way. */
    void put(int col, int row, Tile tile) {
        tiles.set(col, row, tile);
    }

    /** Puts a player somewhere. */
    void putPlayer(int index, int col, int row) {
        players.get(index).walker().place(col, row, null);
    }

    /** Hides a power-up in a crate, or lays one on the floor. */
    void putPowerUp(int col, int row, PowerUp powerUp) {
        Cell at = new Cell(col, row);
        if (tiles.get(at) == Tile.CRATE) hidden.put(at, powerUp);
        else powerUps.put(at, powerUp);
    }

    /** Skips "Ready?". */
    void skipReady() {
        if (phase == Phase.READY) phase(Phase.PLAYING);
    }

    /** Lets the clock run on, to the walls. */
    void passTime(double seconds) {
        roundTime += seconds;
    }
}
