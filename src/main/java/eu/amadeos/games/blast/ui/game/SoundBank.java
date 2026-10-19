package eu.amadeos.games.blast.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;

/** The sound effects of Blast, made once at start from tones: no recordings. */
final class SoundBank {
    /** A bomb is laid: a short, low knock. */
    final Sound laid = Sound.tone(Wave.TRIANGLE, 180, 120, 0.06, 0.3);
    /** The bang: a burst of noise over a falling rumble. */
    final Sound explosion = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.45, 0.32),
            Sound.tone(Wave.TRIANGLE, 110, 40, 0.5, 0.35));
    /** A bomb in a chain: shorter, so that ten of them do not drown everything. */
    final Sound chained = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.2, 0.2),
            Sound.tone(Wave.TRIANGLE, 90, 40, 0.22, 0.25));
    final Sound crate = Sound.tone(Wave.NOISE, 0, 0, 0.12, 0.1);
    final Sound powerUp = Sound.tone(Wave.SQUARE, 660, 660, 0.05, 0.1)
            .then(Sound.tone(Wave.SQUARE, 880, 880, 0.05, 0.1))
            .then(Sound.tone(Wave.SQUARE, 1320, 1320, 0.08, 0.1));
    final Sound burnt = Sound.tone(Wave.SQUARE, 400, 150, 0.15, 0.08);
    /** A player is hit: a wail down. */
    final Sound hit = Sound.tone(Wave.SQUARE, 900, 200, 0.5, 0.1);
    /** Time is up: an alarm, three times. */
    final Sound hurry = alarm().then(alarm()).then(alarm());
    final Sound wall = Sound.tone(Wave.TRIANGLE, 90, 60, 0.08, 0.3);
    final Sound go = Sound.tone(Wave.SQUARE, 1046, 1046, 0.25, 0.1);

    private static Sound alarm() {
        return Sound.tone(Wave.SQUARE, 880, 880, 0.12, 0.09).then(Sound.tone(Wave.SQUARE, 660, 660, 0.12, 0.09));
    }
}
