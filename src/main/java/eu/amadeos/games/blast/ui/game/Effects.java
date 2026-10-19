package eu.amadeos.games.blast.ui.game;

import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.fx.Particles;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.blast.domain.Event;

import java.awt.Color;
import java.util.List;

/**
 * What the events of a step look and sound like: the bang and a shake of the arena for every explosion,
 * splinters of burning crates, sparks of a burnt power-up, the sounds and the music. The rules know nothing
 * of this.
 */
final class Effects {
    /** The colours of the splinters and sparks. */
    static final Color[] PALETTE = {new Color(0xB07838), new Color(0x704818), new Color(0xFFD040), new Color(0xFF7020)};
    /** How far the arena shakes at most, in logical units, and how fast the shaking dies away. */
    static final double SHAKE = 3;
    static final double SHAKE_DECAY = 6;

    private final SoundBank sounds = new SoundBank();
    private final Music music = new Music();
    private final Mixer mixer;
    private final SeededRandom random = new SeededRandom(1);
    final Particles particles = new Particles(600, PALETTE);
    /** Seconds since the effects started: the clock of blinking and of the fuses' sparks. */
    private double time;
    private double shake;
    private double shakeX;
    private double shakeY;
    /** Explosions sound once per step, however many bombs a chain sets off in it. */
    private boolean bangThisStep;

    Effects(Mixer mixer) {
        this.mixer = mixer;
    }

    double time() {
        return time;
    }

    /** A new match or the title: no splinters and no music from before. */
    void clear() {
        particles.clear();
        shake = 0;
        mixer.stopMusic();
    }

    void update(double seconds) {
        time += seconds;
        particles.update(seconds, 0);
        shake = Math.max(0, shake - SHAKE_DECAY * seconds * shake - seconds);
        shakeX = shake > 0 ? (random.nextDouble() * 2 - 1) * shake : 0;
        shakeY = shake > 0 ? (random.nextDouble() * 2 - 1) * shake : 0;
    }

    double shakeX() {
        return shakeX;
    }

    double shakeY() {
        return shakeY;
    }

    // --8<-- [start:react]
    /** Shows and, if {@code audible}, plays what happened in the last step; the music too. */
    void react(List<Event> events, boolean audible, BlastPainter painter) {
        bangThisStep = false;
        for (Event event : events) {
            switch (event) {
                case Event.Ready ready -> {
                    if (audible) mixer.playMusic(music.ready, false);
                }
                case Event.Go go -> {
                    if (audible) mixer.play(sounds.go);
                }
                case Event.BombLaid laid -> {
                    if (audible) mixer.play(sounds.laid);
                }
                case Event.Exploded(var bomb, boolean chained) -> {
                    shake = Math.min(SHAKE, shake + (chained ? 0.8 : 1.6));
                    if (audible && !bangThisStep) mixer.play(chained ? sounds.chained : sounds.explosion);
                    bangThisStep = true;
                }
                case Event.CrateHit hit -> {
                }
                case Event.CrateBurnt(Cell at, var hidden) -> {
                    splinters(painter, at, 0, 1, 10);
                    if (audible) mixer.play(sounds.crate);
                }
                case Event.PowerUpBurnt(Cell at, var powerUp) -> {
                    splinters(painter, at, 2, 3, 8);
                    if (audible) mixer.play(sounds.burnt);
                }
                case Event.PowerUpTaken taken -> {
                    if (audible) mixer.play(sounds.powerUp);
                }
                case Event.PlayerHit hit -> {
                    if (audible) mixer.play(sounds.hit);
                }
                case Event.Hurry hurry -> {
                    if (audible) mixer.play(sounds.hurry);
                }
                case Event.WallFell fell -> {
                    shake = Math.min(SHAKE, shake + 0.4);
                    if (audible) mixer.play(sounds.wall);
                }
                case Event.RoundOver(int round, int winner) -> {
                    if (audible) mixer.playMusic(winner >= 0 ? music.roundWon : music.draw, false);
                }
                case Event.MatchOver over -> {
                    if (audible) mixer.playMusic(music.matchWon, false);
                }
            }
        }
    }
    // --8<-- [end:react]

    /** Splinters flying from a tile, in two of the palette's colours. */
    private void splinters(BlastPainter painter, Cell at, int colour, int other, int count) {
        double x = painter.x(at.col());
        double y = painter.y(at.row());
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double speed = 20 + random.nextDouble() * 50;
            particles.spawn(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed, 0.3 + random.nextDouble() * 0.4,
                    2 + random.nextDouble() * 2, random.nextBoolean() ? colour : other);
        }
    }
}
