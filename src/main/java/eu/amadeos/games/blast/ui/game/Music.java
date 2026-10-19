package eu.amadeos.games.blast.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;
import eu.amadeos.gameengine.audio.Tune;

/**
 * The music of Blast, written in MML and made into sounds once at start: a short call before each round, a
 * jingle for the winner of a round, a fanfare for the winner of the match, and a sad one for a draw.
 */
final class Music {
    /** "Ready?": a rising call over a bass. */
    final Sound ready = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.1, "t160 o4 l8 g >c e g4 e8 g2"),
            Tune.voice(Wave.TRIANGLE, 0.24, "t160 o2 l4 c g c2"));

    final Sound roundWon = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.1, "t180 o5 l16 c e g >c8 < g >c4"),
            Tune.voice(Wave.TRIANGLE, 0.22, "t180 o3 l8 c g c4 c4"));

    final Sound draw = Tune.voice(Wave.SQUARE, 0.1, "t140 o4 l8 g f e d c2");

    /** The match is won. */
    final Sound matchWon = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.1, "t150 o5 l8 c c c4 < a- b- > c4. < b- > c2"),
            Tune.voice(Wave.TRIANGLE, 0.24, "t150 o3 l8 c g c4 < a- >e- c4. d c2"));
}
