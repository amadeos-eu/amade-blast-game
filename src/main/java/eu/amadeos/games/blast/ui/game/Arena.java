package eu.amadeos.games.blast.ui.game;

/**
 * What the presenter may ask of the running game. The game runs on its own thread; every method here is
 * safe to call from the Swing thread and takes effect at the start of the game's next step.
 */
public interface Arena {
    /** Back to the title screen, where the computer plays. */
    void showTitle();

    /** To the lobby, where the players join, with the seats of the last match. */
    void newGame();

    /** Pauses a running match; does nothing elsewhere. */
    void pause();

    void setSound(boolean on);

    /** Which gamepads the game sees, for the About dialog: "Gamepads: XInput, 1 connected". */
    String gamepads();
}
