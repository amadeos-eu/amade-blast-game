package eu.amadeos.games.blast.domain;

import java.util.Locale;

/** What a crate can hide: one bomb more at a time, flames one tile longer, faster feet. */
public enum PowerUp {
    BOMB, FIRE, SPEED;

    /** The name in {@code board.txt}: {@code bomb}, {@code fire}, {@code speed}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
