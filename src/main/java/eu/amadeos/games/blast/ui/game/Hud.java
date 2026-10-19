package eu.amadeos.games.blast.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.games.blast.domain.Field;
import eu.amadeos.games.blast.domain.Game;
import eu.amadeos.games.blast.domain.Player;
import eu.amadeos.games.blast.domain.PowerUp;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The overlay: a card for each player beside the arena, near its corner, with its seat, its round wins and
 * its power-ups; the clock above the arena; and the texts of the rounds and of the title, lobby, pause and
 * end screens. The canvas draws it after the world, at the full size of the screen, so the letters are sharp
 * in every render mode.
 */
final class Hud {
    static final Color CHALK = Color.WHITE;
    static final Color GOLD = new Color(0xFFD040);
    static final Color RED = new Color(0xFF5040);
    static final Color GREY = new Color(0x707080);
    /** A veil over the field behind texts. */
    static final Color VEIL = new Color(0, 0, 0, 160);
    static final Color CARD = new Color(0x20242E);
    /** Where each player's card is: beside its corner of the arena. */
    static final double[][] CARDS = {{0, 0}, {1, 1}, {1, 0}, {0, 1}};
    static final double CARD_WIDTH = 96;
    static final double CARD_HEIGHT = 150;

    private final Messages msg;
    private final BlastPainter painter;
    private final Font label = new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(11f);
    private final Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(10f);
    private final Font clock = new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(16f);
    private final Map<Integer, Font> fonts = new HashMap<>();
    private final Rectangle2D.Double veil = new Rectangle2D.Double(0, 0, Field.WIDTH, Field.HEIGHT);

    Hud(Messages msg, BlastPainter painter) {
        this.msg = msg;
        this.painter = painter;
    }

    // --8<-- [start:status]
    /** The four cards, the clock, and what the round has to say. */
    void status(Graphics2D g, Game game, List<Seat> seats, Effects effects) {
        for (Player player : game.players()) card(g, player, seats.get(player.index()));
        g.setFont(clock);
        double left = game.timeLeft();
        boolean hurry = left == 0;
        g.setColor(hurry ? RED : CHALK);
        int seconds = (int) Math.ceil(left);
        centred(g, String.format("%d:%02d", seconds / 60, seconds % 60), Field.WIDTH / 2, 23);
        double middle = painter.y(game.board().height() / 2.0 - 0.5);
        switch (game.phase()) {
            case READY -> {
                band(g, middle);
                text(g, msg.format("play.round", game.round()), middle - 4, 22, true, GOLD);
                text(g, msg.get("play.ready"), middle + 18, 14, false, CHALK);
            }
            case PLAYING -> {
                if (game.phaseTime() < 0.7) {
                    band(g, middle);
                    text(g, msg.get("play.go"), middle + 8, 26, true, GOLD);
                } else if (hurry && game.roundTime() - Field.ROUND_SECONDS < 3 && (int) (effects.time() * 4) % 2 == 0) {
                    text(g, msg.get("play.hurry"), middle + 8, 26, true, RED);
                }
            }
            case ROUND_OVER, MATCH_OVER -> {
                band(g, middle);
                int winner = roundWinner(game);
                String line = winner < 0 ? msg.get("play.draw") : msg.format("play.won", name(winner));
                text(g, line, middle + 8, 22, true, winner < 0 ? CHALK : BlastPainter.PLAYERS[winner]);
            }
        }
    }

    /** The one still playing at the end of a round; -1 for a draw. */
    private static int roundWinner(Game game) {
        for (Player player : game.players()) {
            if (player.playing()) return player.index();
        }
        return -1;
    }

    /** A player's card: the figure, the seat, the rounds won, the bombs, flames and speed. */
    private void card(Graphics2D g, Player player, Seat seat) {
        double[] place = CARDS[player.index()];
        double x = place[0] == 0 ? (painter.left - CARD_WIDTH) / 2 : Field.WIDTH - (painter.left + CARD_WIDTH) / 2;
        double y = place[1] == 0 ? painter.top : Field.HEIGHT - 4 - CARD_HEIGHT;
        g.setColor(CARD);
        g.fill(new RoundRectangle2D.Double(x, y, CARD_WIDTH, CARD_HEIGHT, 10, 10));
        Color colour = BlastPainter.PLAYERS[player.index()];
        g.setColor(colour);
        g.fill(new Rectangle2D.Double(x + 8, y + 4, CARD_WIDTH - 16, 2));
        double cx = x + CARD_WIDTH / 2;
        painter.portrait(player.index()).draw(g, cx - BlastPainter.TILE / 2, y + 10);
        g.setFont(label);
        g.setColor(colour);
        centred(g, name(player.index()), cx, y + 58);
        g.setFont(small);
        g.setColor(CHALK);
        centred(g, seatName(seat, player), cx, y + 72);
        for (int i = 0; i < Field.WINS; i++) {
            double wx = cx + (i - (Field.WINS - 1) / 2.0) * 16;
            g.setColor(i < player.wins() ? GOLD : GREY);
            g.fill(new Ellipse2D.Double(wx - 5, y + 80, 10, 10));
        }
        int[] values = {player.bombs(), player.fire(), player.speedUps()};
        for (PowerUp powerUp : PowerUp.values()) {
            double row = y + 98 + powerUp.ordinal() * 17;
            painter.icon(powerUp).draw(g, cx - 30, row);
            g.setFont(label);
            g.setColor(CHALK);
            g.drawString("× " + values[powerUp.ordinal()], (float) (cx - 10), (float) (row + 12));
        }
        if (!player.playing() && player.state() != Player.State.DYING) {
            g.setColor(VEIL);
            g.fill(new RoundRectangle2D.Double(x, y, CARD_WIDTH, CARD_HEIGHT, 10, 10));
        }
    }
    // --8<-- [end:status]

    String name(int player) {
        return msg.get("player." + player);
    }

    String seatName(Seat seat, Player player) {
        if (!player.present()) return msg.get("seat.empty");
        return switch (seat.kind) {
            case KEYS -> msg.get("seat.keys" + seat.number);
            case PAD -> msg.format("seat.pad", seat.number);
            case COMPUTER -> msg.get("seat.computer");
        };
    }

    static void centred(Graphics2D g, String text, double x, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0), (float) y);
    }

    /** A dark band across the arena behind a text. */
    private void band(Graphics2D g, double middle) {
        g.setColor(VEIL);
        g.fill(new Rectangle2D.Double(painter.left, middle - 30, Field.WIDTH - 2 * painter.left, 52));
    }

    /** Darkens the field so text over it stays readable. */
    void veil(Graphics2D g) {
        Object smoothing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(VEIL);
        g.fill(veil);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, smoothing);
    }

    /** A line of text centred horizontally, its baseline at {@code y}. */
    void text(Graphics2D g, String line, double y, int size, boolean bold, Color colour) {
        g.setFont(font(size, bold));
        g.setColor(colour);
        g.drawString(line, (float) (Field.WIDTH - g.getFontMetrics().stringWidth(line)) / 2, (float) y);
    }

    Font font(int size, boolean bold) {
        return fonts.computeIfAbsent(2 * size + (bold ? 1 : 0),
                key -> new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
    }
}
