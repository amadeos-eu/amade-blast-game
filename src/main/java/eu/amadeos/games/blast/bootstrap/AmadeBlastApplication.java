package eu.amadeos.games.blast.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.games.blast.ui.game.BlastGame;
import eu.amadeos.games.blast.ui.main.BlastPresenter;
import eu.amadeos.games.blast.ui.main.BlastWindow;

public final class AmadeBlastApplication {
    public static final AppInfo APP = new AppInfo("amade-blast-game", "Blast", "blast");

    private AmadeBlastApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeBlastApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            Mixer mixer = injector.getInstance(Mixer.class);
            mixer.start();
            Gamepads gamepads = injector.getInstance(Gamepads.class);
            BlastWindow window = injector.getInstance(BlastWindow.class);
            BlastPresenter presenter = injector.getInstance(BlastPresenter.class);
            BlastGame game = injector.getInstance(BlastGame.class);
            window.bind(presenter);
            presenter.start();
            window.whenClosed(() -> {
                game.stop();
                gamepads.close();
                mixer.close();
                System.exit(0);
            });
            window.setLocationByPlatform(true);
            window.setVisible(true);
            game.start(window.canvas());
        }, new BlastModule());
    }
    // --8<-- [end:main]
}
