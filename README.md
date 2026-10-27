# Goblins and Caverns

The game and its required images, audio, and font are included in this folder.

## Run

- Windows: double-click `run.bat`.
- macOS/Linux: run `sh run.sh` from a terminal.

A Java JDK (Java 8 or newer) is required. The launch scripts compile the source
into `.build/`, keeping generated class files separate from the game files.

To move the game to another computer, copy the entire `GoblinsAndCaverns`
folder and run the launcher for that computer's operating system.

## Controller support

Windows XInput controllers are supported. Use the left stick or D-pad to move
the on-screen cursor, A to select, and B to go back or pause. Start or Back
pauses/resumes. X/Y and the shoulder buttons use the Potion, Map, Grimoire,
and Old Charm, respectively. The launch scripts download JNA on the first run;
an internet connection is required for that initial download. Controller
support is XInput-specific and is not available on macOS/Linux.

## Window icon

The window uses a Goblin placeholder icon by default. To use a custom icon,
add a PNG image at `icons/favicon.png`; it will be picked up the next time the
game starts.
