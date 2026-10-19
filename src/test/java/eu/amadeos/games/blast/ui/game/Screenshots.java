package eu.amadeos.games.blast.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.blast.bootstrap.BlastModule;
import eu.amadeos.games.blast.domain.Danger;
import eu.amadeos.games.blast.domain.Field;
import eu.amadeos.games.blast.domain.Move;
import eu.amadeos.games.blast.domain.Stage;
import eu.amadeos.games.blast.domain.Game;
import eu.amadeos.games.blast.domain.Player;
import eu.amadeos.games.blast.repository.BlastSettings;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The screenshots of chapter 26, drawn without a screen: the title, the lobby, a chain reaction, the
 * computer player's map of danger, the flame pieces. {@code ./gradlew screenshots -Pshots=<folder>}.
 */
public final class Screenshots {
    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final BlastGame game;

    private Screenshots() throws Exception {
        Messages msg = Messages.load(BlastModule.class, BlastModule.MESSAGES, Locale.ENGLISH);
        long[] seeds = {5};
        game = new BlastGame(keyboard, Gamepads.none(), msg,
                new BlastSettings(Files.createTempDirectory("blast").resolve("blast.properties")), () -> seeds[0]++,
                new Mixer());
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "screenshots");
        dir.mkdirs();
        Screenshots shots = new Screenshots();
        shots.steps(40 * 120);
        shots.save(new File(dir, "title.png"), true, null);
        // A chain reaction, set up by hand: computer players seldom lay a bomb where a flame would reach it.
        BlastGame.TitleScene title = (BlastGame.TitleScene) shots.game.top();
        shots.chain(new File(dir, "chain.png"), title);
        // The danger a computer player sees: several bombs ticking, a player in their way.
        for (int i = 0; i < 1200 * 120; i++) {
            shots.steps(1);
            Game demo = title.demo();
            if (demo.phase() == Game.Phase.PLAYING && demo.timeLeft() < Field.ROUND_SECONDS - 20
                    && demo.bombs().size() >= 3 && demo.player(0).playing() && alone(demo) && fleeing(title)
                    && !Danger.of(demo).safe(demo.player(0).cell()) && !demo.flameCells().iterator().hasNext()) {
                break;
            }
        }
        shots.save(new File(dir, "danger.png"), false, title);
        flamePieces(new File(dir, "flames.png"));
        // The lobby: the first player has joined with the keys, a gamepad would be next.
        shots.tap(KeyEvent.VK_SPACE);
        shots.steps(10);
        shots.save(new File(dir, "lobby.png"), true, null);
        System.exit(0);
    }

    /** The white computer player is on its way to a tile other than its own. */
    private static boolean fleeing(BlastGame.TitleScene title) {
        Cell goal = title.seats().getFirst().bot().target();
        return goal != null && !goal.equals(title.demo().player(0).cell());
    }

    /** No other player within two tiles of the white one, so it is seen. */
    private static boolean alone(Game demo) {
        Player white = demo.player(0);
        return demo.players().stream().skip(1).filter(Player::playing).allMatch(p ->
                Math.abs(p.cell().col() - white.cell().col()) + Math.abs(p.cell().row() - white.cell().row()) > 2);
    }

    /** Four bombs in a row, a few steps after the first set off the others. */
    private void chain(File file, BlastGame.TitleScene title) throws Exception {
        BlastPainter painter = game.painter();
        Effects effects = game.effects();
        Consumer<Game> react = staged -> {
            effects.react(staged.events(), false, painter);
            effects.update(BlastGame.STEP);
        };
        Game staged = Stage.chain(5, react);
        List<Move> none = List.of(Move.NONE, Move.NONE, Move.NONE, Move.NONE);
        for (int i = 0; i < 6; i++) {
            staged.update(BlastGame.STEP, none);
            react.accept(staged);
        }
        write(file, g -> {
            painter.paint(g, staged, effects, 1);
            game.status(g, staged, title.seats());
        });
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(1);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (BlastGame.STEP * 1e9);
            game.update(BlastGame.STEP);
        }
    }

    /** World and overlay at twice the logical size; with the danger map of player 1 if {@code title} is given. */
    private void save(File file, boolean overlay, BlastGame.TitleScene title) throws Exception {
        write(file, g -> {
            game.render(g, 1);
            if (title != null) {
                danger(g, title.demo());
                game.status(g, title.demo(), title.seats());
            } else if (overlay) {
                game.renderOverlay(g, 1);
            } else if (game.top() instanceof BlastGame.TitleScene t) {
                game.status(g, t.demo(), t.seats());
            }
        });
    }

    /** At twice the logical size. */
    private static void write(File file, Consumer<Graphics2D> draw) throws Exception {
        int scale = 2;
        BufferedImage image = new BufferedImage((int) Field.WIDTH * scale, (int) Field.HEIGHT * scale,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);
        draw.accept(g);
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    /** Red tiles: flames will come, the darker the sooner, with the seconds; the white player's goal in green. */
    private void danger(Graphics2D g, Game demo) {
        BlastPainter painter = game.painter();
        Danger danger = Danger.of(demo);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        for (int row = 0; row < demo.tiles().height(); row++) {
            for (int col = 0; col < demo.tiles().width(); col++) {
                double seconds = danger.at(col, row);
                if (seconds == Danger.SAFE || !demo.tiles().get(col, row).open()) continue;
                int strength = (int) Math.round(200 - 50 * Math.min(2.5, seconds));
                g.setColor(new Color(255, 40, 30, Math.clamp(strength, 60, 200)));
                double x = painter.left + col * BlastPainter.TILE;
                double y = painter.top + row * BlastPainter.TILE;
                g.fill(new Rectangle2D.Double(x, y, BlastPainter.TILE, BlastPainter.TILE));
                g.setColor(Color.WHITE);
                g.drawString(String.format(Locale.ROOT, "%.1f", seconds), (float) (x + 7), (float) (y + 17));
            }
        }
        Player white = demo.player(0);
        BlastGame.TitleScene title = (BlastGame.TitleScene) game.top();
        Cell goal = title.seats().getFirst().bot().target();
        if (goal != null) {
            g.setColor(new Color(60, 255, 90));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Rectangle2D.Double(painter.left + goal.col() * BlastPainter.TILE + 2,
                    painter.top + goal.row() * BlastPainter.TILE + 2, BlastPainter.TILE - 4, BlastPainter.TILE - 4));
        }
        g.setColor(new Color(60, 255, 90));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Rectangle2D.Double(painter.left + white.cell().col() * BlastPainter.TILE + 1,
                painter.top + white.cell().row() * BlastPainter.TILE + 1, BlastPainter.TILE - 2, BlastPainter.TILE - 2));
    }

    /** The sixteen flame pieces by their mask, numbered. */
    private static void flamePieces(File file) throws Exception {
        int cell = 64;
        BufferedImage image = new BufferedImage(32 + 8 * (cell + 8), 2 * cell + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x1C1C24));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (int mask = 0; mask < 16; mask++) {
            int x = 20 + (mask % 8) * (cell + 8) - 4;
            int y = 10 + (mask / 8) * (cell + 25);
            Graphics2D piece = (Graphics2D) g.create();
            piece.translate(x, y);
            piece.setColor(BlastPainter.FLOOR);
            piece.fillRect(0, 0, cell, cell);
            piece.scale(cell / BlastPainter.TILE, cell / BlastPainter.TILE);
            BlastPainter.drawFlame(piece, mask, BlastPainter.FLAME_WIDTHS[0]);
            piece.dispose();
            g.setColor(Color.WHITE);
            String label = Integer.toString(mask);
            g.drawString(label, x + cell / 2 - g.getFontMetrics().stringWidth(label) / 2, y + cell + 17);
        }
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    static List<String> names() {
        return List.of("title", "lobby", "chain", "danger", "flames");
    }
}
