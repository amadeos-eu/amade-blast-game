package eu.amadeos.games.blast.ui.main;

import eu.amadeos.gameengine.swing.RenderMode;

/** What {@link BlastPresenter} needs from the window; tests replace it. The arena itself is the game's. */
public interface BlastView {
    /** Shows the settings in the menu. */
    void showOptions(boolean sound, RenderMode mode);

    /** Switches the canvas to a render mode. */
    void useRenderMode(RenderMode mode);

    /** The About dialog, with a line on the gamepads. */
    void showAbout(String gamepads);
}
