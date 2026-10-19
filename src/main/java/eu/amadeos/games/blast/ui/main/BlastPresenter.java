package eu.amadeos.games.blast.ui.main;

import com.google.inject.Inject;
import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.blast.repository.BlastSettings;
import eu.amadeos.games.blast.ui.game.Arena;

/**
 * Everything around the arena, without Swing: the menu commands, the settings, and pausing when the window
 * loses the focus. All methods run on the Swing thread. Blast keeps no high scores: a match against friends
 * is won or lost, not counted.
 */
public final class BlastPresenter {
    private final BlastView view;
    private final Arena arena;
    private final BlastSettings settings;

    @Inject
    public BlastPresenter(BlastView view, Arena arena, BlastSettings settings) {
        this.view = view;
        this.arena = arena;
        this.settings = settings;
    }

    public void start() {
        boolean sound = settings.sound();
        RenderMode mode = settings.renderMode();
        view.showOptions(sound, mode);
        view.useRenderMode(mode);
        arena.setSound(sound);
        arena.showTitle();
    }

    public void newGame() {
        arena.newGame();
    }

    public void pause() {
        arena.pause();
    }

    /** The window lost the focus: nobody is at the keys, so a running match waits. */
    public void focusLost() {
        arena.pause();
    }

    public void setSound(boolean on) {
        settings.setSound(on);
        arena.setSound(on);
    }

    public void setRenderMode(RenderMode mode) {
        settings.setRenderMode(mode);
        view.useRenderMode(mode);
    }

    public void showAbout() {
        view.showAbout(arena.gamepads());
    }
}
