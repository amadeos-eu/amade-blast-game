package eu.amadeos.games.blast.ui.main;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import eu.amadeos.gameengine.swing.GameTexts;
import eu.amadeos.games.blast.bootstrap.BlastModule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(BlastModule.class, BlastModule.MESSAGES));
    }

    @Test
    void textsAreFormattedInTheLanguage() {
        Messages german = Messages.load(BlastModule.class, BlastModule.MESSAGES, Locale.GERMANY);
        assertEquals("Rot gewinnt die Runde!", german.format("play.won", german.get("player.1")));
        assertEquals("Gamepads: XInput, 2 verbunden.", german.format("about.gamepads", "XInput", 2));
    }

    /** The Game menu mixes the engine's entries with the game's own; no two may share a letter. */
    @Test
    void mnemonicsAreUniqueInTheGameMenu() {
        Map<String, String> clashes = new HashMap<>();
        for (Language language : Language.values()) {
            Messages engine = GameTexts.load(language.locale());
            Messages game = Messages.load(BlastModule.class, BlastModule.MESSAGES, language.locale());
            List<String> texts = List.of(engine.get("menu.game.new"), game.get("menu.game.pause"),
                    game.get("menu.game.sound"), engine.get("menu.game.graphics"), engine.get("menu.game.frame-times"),
                    engine.get("menu.game.full-screen"), engine.get("menu.game.exit"));
            Map<Character, String> seen = new HashMap<>();
            for (String text : texts) {
                Mnemonics.Text t = Mnemonics.parse(text);
                if (t.index() < 0) {
                    clashes.put(language.code() + " " + text, "no mnemonic");
                    continue;
                }
                String other = seen.put(Character.toLowerCase(t.text().charAt(t.index())), text);
                if (other != null) clashes.put(language.code() + " " + text, other);
            }
        }
        assertEquals(Map.of(), clashes);
    }
}
