package eu.amadeos.games.blast.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Gamepads;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.games.blast.repository.BlastSettings;
import eu.amadeos.games.blast.ui.game.Arena;
import eu.amadeos.games.blast.ui.game.BlastGame;
import eu.amadeos.games.blast.ui.main.BlastPresenter;
import eu.amadeos.games.blast.ui.main.BlastView;
import eu.amadeos.games.blast.ui.main.BlastWindow;

import java.util.Locale;

/**
 * amade-blast-game bindings. Theme, language and the XDG directories come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class BlastModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.games.blast.messages";

    @Override
    protected void configure() {
        bind(BlastSettings.class).in(Scopes.SINGLETON);
        bind(Keyboard.class).in(Scopes.SINGLETON);
        bind(Mixer.class).in(Scopes.SINGLETON);
        bind(BlastGame.class).in(Scopes.SINGLETON);
        bind(Arena.class).to(BlastGame.class);
        bind(BlastWindow.class).in(Scopes.SINGLETON);
        bind(BlastView.class).to(BlastWindow.class);
        bind(BlastPresenter.class).in(Scopes.SINGLETON);
    }

    /** The gamepads of this computer, by the backend of its operating system. */
    @Provides
    @Singleton
    Gamepads gamepads() {
        return Gamepads.open();
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(BlastModule.class, MESSAGES, locale);
    }
}
