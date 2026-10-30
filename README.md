# amade-blast-game

**amade-blast-game** is the bomb arena of the [amadeOS](https://github.com/amadeos-eu/amadeos) desktop: up to four players lay bombs among crates, blast their way through and try to be the last one standing. On the keyboard and on gamepads; the computer takes the empty seats. Built on [amade-game-engine](https://github.com/amadeos-eu/amade-game-engine); runs on other operating systems as well.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

## Playing

- **Space**, **Enter** or button **A** on a gamepad opens the lobby. Everybody who wants to play presses their bomb key or button once to take a seat, and once more to start. **C** switches the computer players in the empty seats on or off; without them two people are needed.
- Two players share the keyboard: **W, A, S, D** and **Space**, and the **arrow keys** and **Enter**. Gamepads steer with the d-pad or the left stick and lay bombs with **A**; **Start** pauses.
- A **bomb** explodes after two and a half seconds. Its flames run along the corridors, stop at walls and burn crates. A flame that reaches another bomb sets it off at once: a **chain reaction**.
- **Crates** hide power-ups: one **more bomb** at a time, **longer flames**, **faster feet**. A flame burns a power-up lying in the open.
- Whoever is hit by a flame is out of the round. The last one standing wins it; three won rounds win the match. After two minutes the **walls fall** in a spiral from the edge inwards, until somebody wins.
- **P** or **Esc** pauses, and so does leaving the window or unplugging a gamepad that is playing.
- On the title screen four computer players play.
- **F11** switches to full screen and back. *Game → Graphics* chooses how the field reaches the screen: **Direct** (at the screen's full size) or **Pixels** (the field at 640 × 400, enlarged by whole factors). **F12** shows how long each frame takes.
- Sound effects and music can be switched off under *Game → Sound*.

The keys can be changed in `~/.config/amadeos/blast/blast.properties`, with the names Java gives them, several separated by spaces:

```properties
keys.1.up=W
keys.1.left=A
keys.1.down=S
keys.1.right=D
keys.1.bomb=SPACE
keys.2.up=UP
keys.2.left=LEFT
keys.2.down=DOWN
keys.2.right=RIGHT
keys.2.bomb=ENTER
keys.pause=P ESCAPE
```

Texts in English, Deutsch, Français, Español and Italiano.

## Gamepads

Up to four, plugged in before or while the game runs; *Help → About* shows how many are connected.

- **Linux**: the game reads `/dev/input/js0` to `js3`, which needs read access to them (on most desktops the user at the screen has it). The buttons follow the Xbox layout of the `xpad` driver.
- **Windows**: XInput, so Xbox and compatible controllers.
- Elsewhere the game runs without gamepads.

## Files

| What | Where |
|---|---|
| Settings (graphics, sound, computer players, keys) | `~/.config/amadeos/blast/blast.properties` |

## Building

```bash
./gradlew test          # the window test needs a display (xvfb-run on servers)
./gradlew run
./gradlew deb           # build/distributions/amade-blast-game_<version>_all.deb
./gradlew installToAmadeOs   # copies the package to ../amadeos/config/packages.chroot/
```

Gamepads on Windows go through Java's foreign function interface, so the game starts with `--enable-native-access=ALL-UNNAMED` (set in `build.gradle` for `run`, the tests and the packages).

The arena is the text file `src/main/resources/eu/amadeos/games/blast/board.txt`: a letter per tile (wall, crate, floor, maybe a crate), where the four players start, how many of the possible crates are laid and which power-ups they hide. Change it without compiling; a mistake is reported with the file and the line. The players, bombs, flames and power-ups are drawn in code. The icon is drawn by `tools/MakeIcon.java`. `./gradlew screenshots -Pshots=<folder>` draws the handbook's screenshots.

With `../amade-game-engine` (and `../amade-commons`) checked out next to this repository, Gradle builds those checkouts instead of the JitPack versions.

## Structure

- `domain`: the rules without user interface or time of their own. `Game` (the round's tiles, players, bombs and flames; every wait is a timer, every consequence an `Event` on a queue, so a chain reaction runs wave by wave in one step; rounds and the match; chance from a seeded random generator), `Board` (reads `board.txt`, lays the crates and the hidden power-ups, the spiral of falling walls), `Player` (walks the corridors, its bombs, range and speed), `Bomb`, `Tile`, `PowerUp`, `Move`, `Event`, `Field` (times, speeds, limits), `Danger` (when flames will reach each tile, along chains), `Bot` (a computer player: flee, collect, lay bombs where they pay off and an escape is left), `MatchResult`.
- `repository`: `BlastSettings` (graphics, sound, computer players, keys).
- `ui.game`: the running game on the loop thread. `BlastGame` (loop, scenes: title with demo, lobby, play, pause, match over), `Seat` (who controls a player: keys, a gamepad or the computer), `BlastPainter` (arena, bombs, flames from sixteen pieces, power-ups, players), `Hud` (the player cards, the clock and the texts), `Effects` (events become particles, shaking, sounds and music), `SoundBank`, `Music` (the tunes, in MML), `Arena` (what the presenter may ask of the game).
- `ui.main`: Model-View-Presenter for everything around the field: `BlastPresenter`, `BlastView`, `BlastWindow`.

From the engine: `Gamepads` and `Gamepad`, `Timers`, `EventQueue`, `TileMap`, `Walker` and `Direction`, `DistanceMap`, `TextData`, `GameLoop`, `GameCanvas`, `Sprite`, `Particles`, `SeededRandom`, `Ticker`, `Sound`, `Tune` and `Mixer`, `Keyboard` and `Controls`, `SceneStack`, `FullScreen`.

Comments of the form `// --8<-- [start:name]` mark the excerpts the [Game Developer's Handbook](https://docs.amadeos.eu/amade-game-engine/) quotes; chapter 26 is about this game.

## Licence

[EUPL-1.2](LICENSE)
