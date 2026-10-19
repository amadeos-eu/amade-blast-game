package eu.amadeos.games.blast.ui.game;

import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.Direction;
import eu.amadeos.gameengine.tile.TileMap;
import eu.amadeos.gameengine.tile.Walker;
import eu.amadeos.games.blast.domain.Board;
import eu.amadeos.games.blast.domain.Bomb;
import eu.amadeos.games.blast.domain.Field;
import eu.amadeos.games.blast.domain.Game;
import eu.amadeos.games.blast.domain.Player;
import eu.amadeos.games.blast.domain.PowerUp;
import eu.amadeos.games.blast.domain.Tile;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Draws the world in logical coordinates (640 × 400): the arena in the middle, 28 units per tile, with its
 * crates, power-ups, bombs, flames and players. Everything is a picture drawn in code once and cached at the
 * screen's size: the floor and the fixed walls as one picture, the other things a few pictures each.
 */
final class BlastPainter {
    /** The size of a tile in logical units. */
    static final double TILE = 28;
    static final Color[] PLAYERS = {new Color(0xF4F4F4), new Color(0xE83838), new Color(0x3C6CF0), new Color(0xF0D020)};
    static final Color FLOOR = new Color(0x3C7E3C);
    static final Color FLOOR_DARK = new Color(0x357235);
    static final Color STONE = new Color(0x8A92A2);
    static final Color STONE_LIGHT = new Color(0xC8D0DC);
    static final Color STONE_DARK = new Color(0x4E5668);
    static final Color WOOD = new Color(0xB27A3A);
    static final Color WOOD_DARK = new Color(0x6E4618);
    static final Color FLAME_OUTER = new Color(0xFF6A10);
    static final Color FLAME_MIDDLE = new Color(0xFFD030);
    static final Color FLAME_INNER = new Color(0xFFF8D0);
    static final Color BOMB = new Color(0x22222C);
    static final Color SHADOW = new Color(0, 0, 0, 70);
    /** The widths of the flames as they die down, as parts of a tile. */
    static final double[] FLAME_WIDTHS = {0.8, 0.62, 0.42};
    /** Walking: the feet, by turns. */
    static final int STEPS = 4;

    private final Board board;
    final double left;
    final double top;
    private final Sprite ground;
    private final Sprite wall;
    private final Sprite crate;
    private final Sprite[] burning = new Sprite[3];
    private final Sprite[] bombs = new Sprite[3];
    private final Sprite[][] flames = new Sprite[FLAME_WIDTHS.length][16];
    private final Sprite[] powerUps = new Sprite[PowerUp.values().length];
    private final Sprite[][][] players = new Sprite[Field.PLAYERS][4][STEPS];
    // The overlay's own pictures: a sprite keeps one picture at one size, and the overlay is drawn at
    // another scale than the world in pixel mode.
    private final Sprite[] portraits = new Sprite[Field.PLAYERS];
    private final Sprite[] icons = new Sprite[PowerUp.values().length];
    /** The size of the power-ups in the overlay. */
    static final double ICON = 16;
    private final Rectangle2D.Double screen = new Rectangle2D.Double(0, 0, Field.WIDTH, Field.HEIGHT);
    private final Ellipse2D.Double ellipse = new Ellipse2D.Double();
    private final List<Player> byRow = new ArrayList<>();

    BlastPainter(Board board) {
        this.board = board;
        double width = board.width() * TILE;
        double height = board.height() * TILE;
        left = Math.round((Field.WIDTH - width) / 2);
        top = Math.round(Field.HEIGHT - height - 4);
        ground = Sprite.drawn(width, height, (g, w, h) -> drawGround(g));
        wall = Sprite.drawn(TILE, TILE, (g, w, h) -> drawWall(g, 0, 0));
        crate = Sprite.drawn(TILE, TILE, (g, w, h) -> drawCrate(g, 0));
        for (int i = 0; i < burning.length; i++) {
            int stage = i + 1;
            burning[i] = Sprite.drawn(TILE, TILE, (g, w, h) -> drawCrate(g, stage));
        }
        for (int i = 0; i < bombs.length; i++) {
            double size = 18 + 2 * i;
            bombs[i] = Sprite.drawn(TILE, TILE, (g, w, h) -> drawBomb(g, size));
        }
        for (int i = 0; i < FLAME_WIDTHS.length; i++) {
            for (int mask = 0; mask < 16; mask++) {
                double width1 = FLAME_WIDTHS[i];
                int m = mask;
                flames[i][mask] = Sprite.drawn(TILE, TILE, (g, w, h) -> drawFlame(g, m, width1));
            }
        }
        for (PowerUp powerUp : PowerUp.values()) {
            powerUps[powerUp.ordinal()] = Sprite.drawn(TILE, TILE, (g, w, h) -> drawPowerUp(g, powerUp));
            icons[powerUp.ordinal()] = Sprite.drawn(ICON, ICON, (g, w, h) -> {
                g.scale(ICON / TILE, ICON / TILE);
                drawPowerUp(g, powerUp);
            });
        }
        for (int p = 0; p < Field.PLAYERS; p++) {
            for (Direction d : Direction.values()) {
                for (int step = 0; step < STEPS; step++) {
                    Color colour = PLAYERS[p];
                    int s = step;
                    players[p][d.ordinal()][step] = Sprite.drawn(TILE, TILE + 6, (g, w, h) -> drawPlayer(g, colour, d, s));
                }
            }
            Color colour = PLAYERS[p];
            portraits[p] = Sprite.drawn(TILE, TILE + 6, (g, w, h) -> drawPlayer(g, colour, Direction.DOWN, 0));
        }
    }

    // --8<-- [start:paint]
    /** @param alpha how far the time is between the last two update steps, 0 to 1 */
    void paint(Graphics2D g, Game game, Effects effects, double alpha) {
        Object smoothing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(Color.BLACK);
        g.fill(screen);
        AffineTransform t = g.getTransform();
        g.translate(effects.shakeX(), effects.shakeY());
        ground.draw(g, left, top);
        TileMap<Tile> tiles = game.tiles();
        for (int row = 0; row < tiles.height(); row++) {
            for (int col = 0; col < tiles.width(); col++) {
                Tile tile = tiles.get(col, row);
                double x = left + col * TILE;
                double y = top + row * TILE;
                if (tile == Tile.WALL && !board.wall(col, row)) wall.draw(g, x, y);      // fallen in a hurry
                else if (tile == Tile.CRATE) {
                    double burnt = 1 - game.burning(new Cell(col, row)) / Field.BURN_SECONDS;
                    if (burnt >= 1) crate.draw(g, x, y);
                    else burning[Math.min(burning.length - 1, (int) (burnt * burning.length))].draw(g, x, y);
                }
            }
        }
        boolean blink = (int) (effects.time() * 6) % 2 == 0;
        for (Map.Entry<Cell, PowerUp> entry : game.powerUps().entrySet()) {
            Cell at = entry.getKey();
            if (blink || game.phase() != Game.Phase.PLAYING) {
                powerUps[entry.getValue().ordinal()].draw(g, left + at.col() * TILE, top + at.row() * TILE);
            }
        }
        flames(g, game);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, smoothing);
        for (Bomb bomb : game.bombs()) drawBomb(g, game, bomb, effects);
        players(g, game, alpha);
        effects.particles.render(g, alpha);
        g.setTransform(t);
    }
    // --8<-- [end:paint]

    // --8<-- [start:flames]
    /**
     * Each tile in flames is drawn with arms towards its neighbours in flames, as the walls of Maze were:
     * the neighbour mask chooses one of sixteen pictures. The flames grow thinner as they die down.
     */
    private void flames(Graphics2D g, Game game) {
        TileMap<Tile> tiles = game.tiles();
        for (Cell cell : game.flameCells()) {
            int mask = 0;
            for (Direction d : Direction.values()) {
                if (game.flames(tiles.step(cell.col(), cell.row(), d)) > 0) mask |= 1 << d.ordinal();
            }
            double age = 1 - game.flames(cell) / Field.FLAME_SECONDS;
            int stage = Math.min(FLAME_WIDTHS.length - 1, (int) (age * FLAME_WIDTHS.length));
            flames[stage][mask].draw(g, left + cell.col() * TILE, top + cell.row() * TILE);
        }
    }
    // --8<-- [end:flames]

    /** A bomb swells faster as its fuse burns down; the spark on the fuse flickers. */
    private void drawBomb(Graphics2D g, Game game, Bomb bomb, Effects effects) {
        double x = left + bomb.cell().col() * TILE;
        double y = top + bomb.cell().row() * TILE;
        double fuse = game.fuse(bomb);
        double rate = fuse < 0.8 ? 10 : 4;
        int frame = (int) (Math.abs(Math.sin(effects.time() * rate)) * 2.99);
        shadow(g, x + TILE / 2, y + TILE - 4, 18, 5);
        bombs[frame].draw(g, x, y);
        double flicker = (int) (effects.time() * 20) % 2 == 0 ? 3.2 : 2.2;
        g.setColor((int) (effects.time() * 20) % 2 == 0 ? FLAME_MIDDLE : FLAME_OUTER);
        ellipse.setFrame(x + TILE * 0.72 - flicker / 2, y + TILE * 0.16 - flicker / 2, flicker, flicker);
        g.fill(ellipse);
    }

    // --8<-- [start:players]
    /** Front to back: the player lower on the screen covers the one above. */
    private void players(Graphics2D g, Game game, double alpha) {
        byRow.clear();
        for (Player player : game.players()) {
            if (player.state() != Player.State.OUT) byRow.add(player);
        }
        byRow.sort(Comparator.comparingDouble(p -> p.walker().y()));
        for (Player player : byRow) {
            Walker walker = player.walker();
            double cx = x(between(walker.previousX(), walker.x(), alpha));
            double cy = y(between(walker.previousY(), walker.y(), alpha));
            if (player.state() == Player.State.DYING) {
                // Hit: it blinks, faster and faster, and is gone.
                double t = player.dying() / Field.DYING_SECONDS;
                if ((int) (t * t * 24) % 2 == 1) continue;
            }
            shadow(g, cx, cy + TILE / 2 - 3, 18, 6);
            int step = walker.heading() == null ? 0 : (int) (player.walked() * 4) % STEPS;
            players[player.index()][player.facing().ordinal()][step].draw(g, cx - TILE / 2, cy - TILE / 2 - 8);
        }
    }
    // --8<-- [end:players]

    private void shadow(Graphics2D g, double cx, double cy, double width, double height) {
        g.setColor(SHADOW);
        ellipse.setFrame(cx - width / 2, cy - height / 2, width, height);
        g.fill(ellipse);
    }

    private static double between(double previous, double now, double alpha) {
        return previous + (now - previous) * alpha;
    }

    /** The centre of a column, in logical units. */
    double x(double col) {
        return left + (col + 0.5) * TILE;
    }

    double y(double row) {
        return top + (row + 0.5) * TILE;
    }

    /** A player looking down, for the overlay. */
    Sprite portrait(int player) {
        return portraits[player];
    }

    /** A power-up, small, for the overlay. */
    Sprite icon(PowerUp powerUp) {
        return icons[powerUp.ordinal()];
    }

    // ---- the pictures --------------------------------------------------------------------------

    /** The floor in a light checker, the fixed walls, and the shadow they cast on the floor below them. */
    private void drawGround(Graphics2D g) {
        for (int row = 0; row < board.height(); row++) {
            for (int col = 0; col < board.width(); col++) {
                double x = col * TILE;
                double y = row * TILE;
                if (board.wall(col, row)) {
                    drawWall(g, x, y);
                    continue;
                }
                g.setColor((col + row) % 2 == 0 ? FLOOR : FLOOR_DARK);
                g.fill(new Rectangle2D.Double(x, y, TILE, TILE));
                if (row > 0 && board.wall(col, row - 1)) {
                    g.setColor(new Color(0, 0, 0, 60));
                    g.fill(new Rectangle2D.Double(x, y, TILE, TILE * 0.22));
                }
            }
        }
    }

    /** A block of stone, light from the top left. */
    static void drawWall(Graphics2D g, double x, double y) {
        double b = 3;
        g.setColor(STONE);
        g.fill(new Rectangle2D.Double(x, y, TILE, TILE));
        g.setColor(STONE_LIGHT);
        g.fill(new Rectangle2D.Double(x, y, TILE, b));
        g.fill(new Rectangle2D.Double(x, y, b, TILE));
        g.setColor(STONE_DARK);
        g.fill(new Rectangle2D.Double(x, y + TILE - b, TILE, b));
        g.fill(new Rectangle2D.Double(x + TILE - b, y, b, TILE));
        g.setColor(STONE_DARK.brighter());
        g.fill(new Rectangle2D.Double(x + TILE / 2 - 1, y + b + 2, 2, TILE - 2 * b - 4));
    }

    /** A wooden crate: a frame, planks and a brace; burning, it darkens and flames lick up. */
    static void drawCrate(Graphics2D g, int stage) {
        double inset = 1.5;
        Color wood = stage == 0 ? WOOD : mix(WOOD, Color.BLACK, 0.25 * stage);
        Color dark = stage == 0 ? WOOD_DARK : mix(WOOD_DARK, Color.BLACK, 0.25 * stage);
        g.setColor(dark);
        g.fill(new Rectangle2D.Double(inset, inset, TILE - 2 * inset, TILE - 2 * inset));
        g.setColor(wood);
        g.fill(new Rectangle2D.Double(inset + 3, inset + 3, TILE - 2 * inset - 6, TILE - 2 * inset - 6));
        g.setColor(dark);
        g.setStroke(new BasicStroke(1f));
        for (int i = 1; i < 3; i++) {
            double y = inset + 3 + i * (TILE - 2 * inset - 6) / 3;
            g.draw(new Line2D.Double(inset + 3, y, TILE - inset - 3, y));
        }
        g.setStroke(new BasicStroke(3f));
        g.draw(new Line2D.Double(inset + 4, TILE - inset - 4, TILE - inset - 4, inset + 4));
        if (stage > 0) {
            Path2D.Double fire = new Path2D.Double();
            double h = TILE * (0.3 + 0.2 * stage);
            fire.moveTo(4, TILE - 2);
            fire.lineTo(8, TILE - h);
            fire.lineTo(11, TILE - h * 0.6);
            fire.lineTo(14, TILE - h * 1.2);
            fire.lineTo(17, TILE - h * 0.6);
            fire.lineTo(20, TILE - h);
            fire.lineTo(24, TILE - 2);
            fire.closePath();
            g.setColor(FLAME_OUTER);
            g.fill(fire);
            g.setColor(FLAME_MIDDLE);
            g.fill(AffineTransform.getTranslateInstance(TILE * 0.25, TILE * 0.3).createTransformedShape(
                    AffineTransform.getScaleInstance(0.5, 0.7).createTransformedShape(fire)));
        }
    }

    static Color mix(Color a, Color b, double part) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * part),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * part),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * part));
    }

    /** A black ball with a shine and a short fuse; the spark is drawn each frame. */
    static void drawBomb(Graphics2D g, double size) {
        double cx = TILE / 2;
        double cy = TILE / 2 + 2;
        g.setColor(new Color(0x8A6A40));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double fuse = new Path2D.Double();
        fuse.moveTo(cx + size * 0.25, cy - size * 0.38);
        fuse.quadTo(cx + size * 0.35, cy - size * 0.62, TILE * 0.72, TILE * 0.16);
        g.draw(fuse);
        g.setColor(BOMB);
        g.fill(new Ellipse2D.Double(cx - size / 2, cy - size / 2, size, size));
        g.setColor(new Color(0x5A5A6E));
        g.fill(new RoundRectangle2D.Double(cx + size * 0.12, cy - size * 0.56, size * 0.26, size * 0.2, 2, 2));
        g.setColor(new Color(255, 255, 255, 170));
        g.fill(new Ellipse2D.Double(cx - size * 0.3, cy - size * 0.3, size * 0.26, size * 0.18));
    }

    /**
     * A flame tile: a round centre and an arm to each neighbour in {@code mask} (1 up, 2 left, 4 down,
     * 8 right), in three layers from orange outside to nearly white inside.
     */
    static void drawFlame(Graphics2D g, int mask, double width) {
        Color[] colours = {FLAME_OUTER, FLAME_MIDDLE, FLAME_INNER};
        double[] parts = {1, 0.66, 0.33};
        double c = TILE / 2;
        for (int layer = 0; layer < colours.length; layer++) {
            double w = TILE * width * parts[layer];
            g.setColor(colours[layer]);
            g.fill(new Ellipse2D.Double(c - w / 2, c - w / 2, w, w));
            if ((mask & 1) != 0) g.fill(new Rectangle2D.Double(c - w / 2, 0, w, c));
            if ((mask & 2) != 0) g.fill(new Rectangle2D.Double(0, c - w / 2, c, w));
            if ((mask & 4) != 0) g.fill(new Rectangle2D.Double(c - w / 2, c, w, c));
            if ((mask & 8) != 0) g.fill(new Rectangle2D.Double(c, c - w / 2, c, w));
        }
    }

    /** A rounded square in the power-up's colour with its sign: a bomb, a flame, or a running shoe. */
    static void drawPowerUp(Graphics2D g, PowerUp powerUp) {
        Color back = switch (powerUp) {
            case BOMB -> new Color(0x3050C8);
            case FIRE -> new Color(0xD03020);
            case SPEED -> new Color(0x20A0B0);
        };
        RoundRectangle2D.Double box = new RoundRectangle2D.Double(3, 3, TILE - 6, TILE - 6, 7, 7);
        g.setColor(back);
        g.fill(box);
        g.setColor(back.brighter());
        g.setStroke(new BasicStroke(1.5f));
        g.draw(box);
        switch (powerUp) {
            case BOMB -> {
                AffineTransform t = g.getTransform();
                g.translate(TILE * 0.18, TILE * 0.16);
                g.scale(0.64, 0.64);
                drawBomb(g, 18);
                g.setTransform(t);
            }
            case FIRE -> {
                Path2D.Double flame = new Path2D.Double();
                flame.moveTo(14, 6);
                flame.curveTo(20, 12, 21, 16, 19, 20);
                flame.curveTo(17, 23, 11, 23, 9, 20);
                flame.curveTo(7, 16, 9, 12, 11, 10);
                flame.curveTo(11, 13, 12, 14, 13, 14);
                flame.curveTo(13, 11, 13, 9, 14, 6);
                g.setColor(FLAME_MIDDLE);
                g.fill(flame);
                g.setColor(FLAME_INNER);
                g.fill(new Ellipse2D.Double(12, 15, 4, 5));
            }
            case SPEED -> {
                Path2D.Double shoe = new Path2D.Double();
                shoe.moveTo(7, 9);
                shoe.lineTo(12, 9);
                shoe.lineTo(13, 14);
                shoe.lineTo(21, 16);
                shoe.lineTo(22, 20);
                shoe.lineTo(7, 20);
                shoe.closePath();
                g.setColor(Color.WHITE);
                g.fill(shoe);
                g.setColor(back.darker());
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new Line2D.Double(4, 12, 8, 12));
                g.draw(new Line2D.Double(3, 15, 8, 15));
            }
        }
    }

    /**
     * A player: a round helmet in its colour with a visor and eyes that look where it goes, a body, hands and
     * two feet that step by turns. 28 × 34, the feet at the bottom.
     */
    static void drawPlayer(Graphics2D g, Color colour, Direction facing, int step) {
        double cx = TILE / 2;
        Color dark = mix(colour, Color.BLACK, 0.35);
        Color outline = mix(colour, Color.BLACK, 0.6);
        // Feet: forward and back by turns while walking.
        double swing = switch (step) {
            case 1 -> 2.5;
            case 3 -> -2.5;
            default -> 0;
        };
        g.setColor(new Color(0x2A2A34));
        if (facing.horizontal()) {
            g.fill(new Ellipse2D.Double(cx - 5 + swing, 28, 8, 5));
            g.fill(new Ellipse2D.Double(cx - 3 - swing, 28.5, 8, 5));
        } else {
            g.fill(new Ellipse2D.Double(cx - 8, 28 - swing * 0.4, 7, 5));
            g.fill(new Ellipse2D.Double(cx + 1, 28 + swing * 0.4, 7, 5));
        }
        // Body and hands.
        g.setColor(dark);
        g.fill(new Ellipse2D.Double(cx - 7, 17, 14, 13));
        g.setColor(colour);
        g.fill(new Ellipse2D.Double(cx - 5, 18, 10, 9));
        g.setColor(Color.WHITE);
        double hand = facing.horizontal() ? swing : 0;
        g.fill(new Ellipse2D.Double(cx - 10 + hand, 20, 5, 5));
        g.fill(new Ellipse2D.Double(cx + 5 - hand, 20, 5, 5));
        // Head: helmet, visor, eyes.
        g.setColor(outline);
        g.fill(new Ellipse2D.Double(cx - 9, 1, 18, 18));
        g.setColor(colour);
        g.fill(new Ellipse2D.Double(cx - 8, 2, 16, 16));
        g.setColor(mix(colour, Color.WHITE, 0.5));
        g.fill(new Ellipse2D.Double(cx - 1.5, -1, 3, 4));         // the knob on top
        if (facing == Direction.UP) return;                     // from behind: no face
        double faceX = facing.dx * 3;
        g.setColor(new Color(0xFFD8B0));
        g.fill(new RoundRectangle2D.Double(cx - 6 + faceX, 7, 12, 8, 5, 5));
        g.setColor(new Color(0x202030));
        if (facing == Direction.DOWN) {
            g.fill(new Rectangle2D.Double(cx - 3.5, 9, 2, 4));
            g.fill(new Rectangle2D.Double(cx + 1.5, 9, 2, 4));
        } else {
            g.fill(new Rectangle2D.Double(cx + facing.dx * 4 - 1, 9, 2, 4));
        }
    }
}
