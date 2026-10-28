import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.*;

/**
 * GOBLINS AND CAVERNS - GUI version (Java Swing, no external assets).
 * All pixel art is drawn in code on a 320x240 canvas and scaled up 3x.
 *
 * The whole game lives on a 960x720 virtual canvas. It runs 1:1 in a 960x720 window and scales
 * cleanly to Fullscreen 1920x1080 (the extra width is filled with cavern wall).
 *
 * Rules
 *  - 30 HP. Walking into a snake's cave costs 10 HP. 0 HP = game over.
 *  - Rounds 1-2 grant 1 Light Spell; rounds 3-4 grant 2. A spell lights one cave entrance.
 *  - Rounds 1-2: 3 cave entrances. Rounds 3-5: 5 cave entrances.
 *  - Items are usable: Mana Potion (+1 Light Spell), Map (seals off a dangerous cave),
 *    Grimoire (ward blocks the next snake bite), Old Charm (+10 HP).
 *
 * Controls: click / SPACE / ENTER to advance, Y / N for yes-no, click a cave or press 1-5,
 *           click an item (top right) or press P / M / G / C to use it, F11 = fullscreen.
 */
public class GoblinDoorsGUI extends JPanel {

    // ---------- Sizes ----------
    static final int W = 320, H = 240, SH = 160, S = 3;     // logical canvas, scene height, scale
    static final int PW = W * S, PH = H * S;
    static final int BLACK_OPENING_SCENE = -1;
    static final String WINDOW_ICON_PATH = "icons/favicon.png";
    static final int HP_MAX = 30, SNAKE_DMG = 10, MAXN = 5;
    static final int LOADING_FADE_MS = 350, LOADING_HOLD_MS = 2650;
    static final int LOADING_TIP_INTERVAL_MS = 900, LOADING_TIP_TRANSITION_MS = 200;
    static final int PAD_DPAD_UP = 0x0001, PAD_DPAD_DOWN = 0x0002;
    static final int PAD_DPAD_LEFT = 0x0004, PAD_DPAD_RIGHT = 0x0008;
    static final int PAD_START = 0x0010, PAD_BACK = 0x0020;
    static final int PAD_LEFT_SHOULDER = 0x0100, PAD_RIGHT_SHOULDER = 0x0200;
    static final int PAD_A = 0x1000, PAD_B = 0x2000, PAD_X = 0x4000, PAD_Y = 0x8000;
    static final String[] LOADING_TIPS = {
        "Light up a cave to peek inside.",
        "A snake bite costs " + SNAKE_DMG + " HP.",
        "Your Light Spells refill each round."
    };

    // ---------- Story data (index = round - 1) ----------
    static final String[] ITEMS = {"Mana Potion", "Map of the Village", "Grimoire", "Old Charm"};
    static final String[] ITEM_DESC = {
        "Refills 1 Light Spell.",
        "Marks a dangerous cave and seals it off.",
        "Casts a ward that blocks the next snake bite.",
        "Restores 10 HP."
    };
    static final char[] ITEM_KEY = {'P', 'M', 'G', 'C'};
    static final String[] ITEM_SHORT = {
        "POTION: +1 Light Spell [P]", "MAP: Seals a dangerous cave [M]",
        "GRIMOIRE: Blocks a snake bite [G]", "OLD CHARM: +10 HP [C]"
    };
    static final String[] MEMORIES = {
        "A voice echoes: \"Promise me you'll use your magic to protect people.\"",
        "The village you are traveling to... you have been there before.",
        "The Grimoire shows you a spell you once used. Your hands remember it.",
        "The Old Charm was given to you by your master. You remember their smile."
    };
    static final String[] MEMORY_NOTES = {
        "The potion reminds you of the days your mana began disappearing.",
        "Why would you forget a place you once visited?",
        "That spell was far too strong...",
        "\"Be careful, my student. Power is not the same as control.\""
    };
    static final String SEAL = "Curse's Seal";

    enum PathType { ITEM, SNAKE }
    enum Scene { TITLE, CAMP, TUNNEL, DOORS, FINAL, MEMORY, FORGIVE, ATONE }
    enum Mode { NONE, TEXT, AUTO, YESNO, DOOR, MENU, LOADING, END, OVER }
    enum Panel { NONE, SETTINGS, TUTORIAL, HELP, INFO }

    // ---------- Pixel art ----------
    static final Map<Character, Color> PAL = new HashMap<>();
    static {
        PAL.put('h', new Color(40, 50, 120));   PAL.put('H', new Color(75, 95, 175));
        PAL.put('s', new Color(240, 200, 160)); PAL.put('e', new Color(30, 30, 40));
        PAL.put('b', new Color(230, 230, 235)); PAL.put('r', new Color(95, 60, 155));
        PAL.put('R', new Color(60, 40, 110));   PAL.put('k', new Color(35, 30, 40));
        PAL.put('y', new Color(255, 241, 168)); PAL.put('g', new Color(95, 165, 70));
        PAL.put('G', new Color(50, 110, 50));   PAL.put('Y', new Color(255, 240, 90));
        PAL.put('c', new Color(130, 90, 50));   PAL.put('p', new Color(75, 45, 100));
        PAL.put('P', new Color(40, 20, 55));    PAL.put('x', new Color(225, 55, 55));
        PAL.put('w', new Color(250, 250, 250)); PAL.put('B', new Color(120, 200, 255));
        PAL.put('n', new Color(150, 100, 50));  PAL.put('N', new Color(100, 65, 30));
        PAL.put('M', new Color(225, 205, 150)); PAL.put('m', new Color(110, 30, 40));
    }

    static final String[][] ICONS = {
        { "..kkkk..", "...nn...", "...kk...", "..kBBk..", ".kBBBBk.", ".kBBwBk.", ".kBBBBk.", "..kkkk.." },
        { ".kkkkkk.", "kMMMMxMk", "kMxMMMMk", "kMMMxMMk", "kMMxxxMk", "kMMMMMMk", "kMxMMMMk", ".kkkkkk." },
        { ".kkkkkk.", "kRRRRRRk", "kRyyyyRk", "kRyrryRk", "kRyrryRk", "kRyyyyRk", "kRRRRRRk", ".kkkkkk." },
        { "...yy...", "..y..y..", "..y..y..", "..kkkk..", ".kyBByk.", ".kyBByk.", ".kyyyyk.", "..kkkk.." },
        { "..xxxx..", ".xxxxxx.", "xxxwwxxx", "xxwxxwxx", "xxwxxwxx", "xxxwwxxx", ".xxxxxx.", "..xxxx.." }
    };
    static void sprite(Graphics2D g, String[] rows, int x, int y, int sc, Color mono,
                       Map<Character, Color> ov, boolean flip) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int n = row.length();
            for (int c = 0; c < n; c++) {
                char ch = row.charAt(c);
                if (ch == '.') continue;
                Color col = mono != null ? mono : (ov != null && ov.containsKey(ch) ? ov.get(ch) : PAL.get(ch));
                if (col == null) continue;
                int cx = flip ? (n - 1 - c) : c;
                g.setColor(col);
                g.fillRect(x + cx * sc, y + r * sc, sc, sc);
            }
        }
    }

    void drawVisualCue(Graphics2D g) {
        if (visualCue == null) return;
        long elapsed = System.currentTimeMillis() - visualCueStartedAt;
        final long duration = 1500;
        if (elapsed >= duration) {
            visualCue = null;
            enteredDoor = -1;
            return;
        }

        float progress = elapsed / (float) duration;
        float alpha = Math.min(1f, Math.min(progress * 5f, (1f - progress) * 4f));
        int width = Math.min(700, g.getFontMetrics(pixelFont(Font.BOLD, 34)).stringWidth(visualCue) + 72);
        int height = 68;
        int x = (PW - width) / 2;
        int y = 205 + Math.round((1f - alpha) * -12);

        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, alpha)));
        g.setColor(new Color(10, 8, 24, 225));
        g.fillRect(x, y, width, height);
        g.setColor(new Color(255, 241, 168));
        drawPixelBorder(g, new Rectangle(x, y, width, height), 3);
        g.setFont(pixelFont(Font.BOLD, 34));
        centerText(g, visualCue, PW / 2, y + 45);
        g.setComposite(oldComposite);
    }

    static Image loadWindowIcon() {
        java.io.File file = GameAssets.resolveFile(WINDOW_ICON_PATH);
        if (file != null) {
            ImageIcon icon = new ImageIcon(file.getAbsolutePath());
            if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) return icon.getImage();
            System.err.println("Could not load window icon: " + file.getAbsolutePath());
        }

        BufferedImage placeholder = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = placeholder.createGraphics();
        g.setColor(new Color(35, 25, 50));
        g.fillRect(0, 0, 32, 32);
        g.setColor(new Color(255, 241, 168));
        g.drawRect(0, 0, 31, 31);
        ImageIcon goblin = GameAssets.loadImage("images/goblinNotStartled.gif");
        if (goblin != null && goblin.getIconWidth() > 0 && goblin.getIconHeight() > 0) {
            g.drawImage(goblin.getImage(), 2, 3, 28, 26, null);
        } else {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
            g.drawString("G", 8, 26);
        }
        g.dispose();
        return placeholder;
    }

    ImageIcon itemSpriteForIndex(int index) {
        if (index < 0 || index >= ITEMS.length) return null;
        ImageIcon[] icons = {potionIcon, mapIcon, grimoireIcon, charmIcon};
        return icons[index];
    }

    void drawItemIcon(Graphics2D g, int index, int x, int y, int sc, Color mono) {
        ImageIcon icon = itemSpriteForIndex(index);
        if (icon != null && icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
            int size = 8 * sc;
            g.drawImage(icon.getImage(), x, y, size, size, null);
            if (mono != null) {
                Composite oldComposite = g.getComposite();
                g.setComposite(AlphaComposite.SrcAtop);
                g.setColor(mono);
                g.fillRect(x, y, size, size);
                g.setComposite(oldComposite);
            }
            return;
        }
        sprite(g, ICONS[index], x, y, sc, mono, null, false);
    }

    static int iconFor(String name) {
        for (int i = 0; i < ITEMS.length; i++) if (ITEMS[i].equals(name)) return i;
        return 4;
    }

    static String roman(int i) { return new String[]{"I", "II", "III", "IV", "V"}[i]; }

    // ---------- Cave entrance geometry (logical px) ----------
    // Rounds 1-2 show 3 big entrances, rounds 3-5 show 5 narrower ones. They always end at the cavern floor line (y = 98).
    int dw() { return nDoors == 3 ? 48 : 38; }
    int dh() { return nDoors == 3 ? 64 : 60; }
    int dy() { return 98 - dh(); }
    int dx(int i) { return nDoors == 3 ? 56 + 80 * i : 23 + 59 * i; }
    int doorVisualWidth() { return Math.round(dw() * 1.15f); }
    int doorVisualX(int i) { return dx(i) - (doorVisualWidth() - dw()) / 2; }

    /** Irregular cave-mouth outline: jagged sides and a lumpy, rough top. The same seed always gives the same
     *  random sequence, so the opening (grow 0) and its rock surround (grow > 0) line up. */
    static List<Point2D> caveOutline(int x, int y, int w, int h, int grow, long seed) {
        List<Point2D> out = new ArrayList<>();
        Random r = new Random(seed);
        double cx = x + w / 2.0, cy = y + w / 2.0, rad = w / 2.0;
        int side = 5;
        for (int k = 0; k <= side; k++) {
            double yy = y + h - (h - w / 2.0) * k / side;
            double t = grow * (0.5 + r.nextDouble()), j = (r.nextDouble() - 0.5) * 3;
            out.add(new Point2D.Double(x - t + j, yy));
        }
        for (int k = 1; k <= 16; k++) {
            double ang = Math.PI + Math.PI * k / 17.0;
            double t = grow * (0.5 + r.nextDouble()), j1 = (r.nextDouble() - 0.5) * 3, j2 = (r.nextDouble() - 0.5) * 3;
            out.add(new Point2D.Double(cx + (rad + t) * Math.cos(ang) + j1, cy + (rad + t) * Math.sin(ang) + j2));
        }
        for (int k = 0; k <= side; k++) {
            double yy = y + w / 2.0 + (h - w / 2.0) * k / side;
            double t = grow * (0.5 + r.nextDouble()), j = (r.nextDouble() - 0.5) * 3;
            out.add(new Point2D.Double(x + w + t + j, yy));
        }
        return out;
    }

    static Path2D caveShape(int x, int y, int w, int h, int grow, long seed) {
        List<Point2D> pts = caveOutline(x, y, w, h, grow, seed);
        Path2D.Float p = new Path2D.Float();
        for (int i = 0; i < pts.size(); i++) {
            Point2D q = pts.get(i);
            if (i == 0) p.moveTo(q.getX(), q.getY()); else p.lineTo(q.getX(), q.getY());
        }
        p.closePath();
        return p;
    }

    // ---------- Game state ----------
    int hp = HP_MAX;
    double hpShown = HP_MAX;
    final List<String> inventory = new ArrayList<>();      // everything recovered so far
    final Set<String> used = new HashSet<>();              // consumed items
    boolean ward, practice, lastBlocked;
    int roundNo = 0, spells = 2, maxSpells = 2, nDoors = 3;
    final PathType[] types = new PathType[MAXN];
    final boolean[] open = new boolean[MAXN];
    final boolean[] peek = new boolean[MAXN];
    final boolean[] doorOpen = new boolean[MAXN];
    final int[] blocked = new int[MAXN];            // 0 none, 1 snake, 3 sealed by map
    int itemShow = -1;
    int itemDoor = -1;
    String endingName = "";
    boolean endingExit;
    String doorPurpose = "";                         // "light" or "walk"
    final Random rng = new Random();

    Scene scene = Scene.TITLE;
    Scene previousScene = Scene.TITLE;
    float sceneFade = 1f;
    boolean sleeping, goblinVisible;

    // ---------- Script engine ----------
    interface Step { void run(); }
    static class Log { final String t; final long at; Log(String t, long at) { this.t = t; this.at = at; } }

    final Deque<Step> queue = new ArrayDeque<>();
    final List<Log> logs = new ArrayList<>();
    Mode mode = Mode.NONE;
    boolean blocking;
    String cur = "";
    boolean curSys;
    double reveal;
    long autoAt;
    long loadingStartedAt;
    long loadingUntil;
    Consumer<Boolean> yesNoCb;
    IntConsumer doorCb;
    final GameAudio audio = new GameAudio(message -> System.err.println(message));
    boolean paused;
    boolean confirmingExit;
    boolean confirmingMainMenu;
    boolean confirmingMenuExit;
    boolean pauseSettings;
    long pauseStartedAt;

    // ---------- Visual state ----------
    int frame;
    double flash, hurt, shake;
    long castingHandUntil;
    String visualCue;
    long visualCueStartedAt;
    int enteredDoor = -1;
    Point mouse = new Point(-1, -1);
    final BufferedImage buf = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
    BufferedImage bgCave;
    BufferedImage dialogueIndicator;
    ImageIcon menuBackground;
    ImageIcon doorsBackground;
    ImageIcon menuBanner;
    ImageIcon[] storyScenes;
    ImageIcon wizardCharacter;
    ImageIcon holyWizard;
    ImageIcon rememberedWizard;
    ImageIcon goblinStartled;
    ImageIcon goblinNotStartled;
    ImageIcon snakeCharacter;
    ImageIcon doorOne;
    ImageIcon doorOneHighlighted;
    ImageIcon idleHand;
    ImageIcon castingHand;
    ImageIcon potionIcon;
    ImageIcon mapIcon;
    ImageIcon grimoireIcon;
    ImageIcon charmIcon;
    ImageIcon heartFilled, heartEmpty;
    Image menuPlay, menuHelp, menuInfo, menuSettings;
    int openingScene;
    final Font fText = pixelFont(Font.BOLD, 22);
    final Font fSys = pixelFont(Font.BOLD, 18);
    final Rectangle yesRect = new Rectangle(300, 628, 150, 50);
    final Rectangle noRect = new Rectangle(510, 628, 150, 50);
    final Rectangle againRect = new Rectangle(230, 600, 240, 56);
    final Rectangle menuRect = new Rectangle(490, 600, 240, 56);
    final Rectangle pauseRect = new Rectangle(20, 10, 44, 44);
    final Rectangle resumeRect = new Rectangle(340, 245, 280, 58);
    final Rectangle pauseSettingsRect = new Rectangle(340, 325, 280, 58);
    final Rectangle pauseMenuRect = new Rectangle(340, 405, 280, 58);
    final Rectangle pauseExitRect = new Rectangle(340, 485, 280, 58);
    final Rectangle menuExitCancelRect = new Rectangle(280, 390, 180, 56);
    final Rectangle menuExitConfirmRect = new Rectangle(500, 390, 180, 56);

    // ---------- Main menu (all coordinates are in the 960x720 virtual canvas) ----------
    static final int MENU_CENTER_X = PW / 2;
    final Rectangle tutorialRect = new Rectangle(MENU_CENTER_X - 306, 485, 196, 66);
    final Rectangle practiceRect = new Rectangle(MENU_CENTER_X - 98, 485, 196, 66);
    final Rectangle exitRect     = new Rectangle(MENU_CENTER_X + 110, 485, 196, 66);
    final Rectangle fsRect       = new Rectangle(520, 245, 170, 50);   // settings: fullscreen toggle
    final Rectangle muteRect     = new Rectangle(520, 315, 170, 50);   // settings: mute toggle
    final Rectangle volumeSliderRect = new Rectangle(520, 410, 170, 28);
    final Rectangle menuPlayRect = new Rectangle(MENU_CENTER_X - 100, 210, 200, 250);
    final Rectangle menuHelpRect = new Rectangle(371, 588, 56, 56);
    final Rectangle menuInfoRect = new Rectangle(452, 588, 56, 56);
    final Rectangle menuSettingsRect = new Rectangle(533, 588, 56, 56);
    final Rectangle backRect     = new Rectangle(380, 480, 200, 52);   // settings: back
    final Rectangle panelRect    = new Rectangle(110, 110, 740, 500);  // tutorial / help / info panel
    final Rectangle closeRect    = new Rectangle(390, 548, 180, 50);
    final Rectangle prevRect     = new Rectangle(140, 548, 170, 50);
    final Rectangle nextRect     = new Rectangle(650, 548, 170, 50);
    final Rectangle aboutTabRect = new Rectangle(300, 126, 170, 42);
    final Rectangle creditsTabRect = new Rectangle(490, 126, 170, 42);
    Panel panel = Panel.NONE;
    int tutPage;
    boolean aboutCreditsTab;
    int creditsPage;
    boolean fullscreen;
    final XInputController controller;
    boolean controllerConnected, controllerCursorVisible;
    JFrame window;                     // set by main(); null when rendered headless
    Rectangle windowedBounds;
    JPanel windowRoot, titleBar;
    double scale = 1, offX, offY;      // virtual canvas -> window mapping (letterboxed)

    static final String[] TUT_TITLES = {"THE CAVERN", "LIGHT SPELLS", "HP & SNAKES", "ITEMS"};
    static final String[] TUT_TEXT = {
        "A thief stole your belongings and fled into a big cavern full of dark cave entrances. "
        + "Each round you must choose ONE cave to walk into. Rounds 1-2 show 3 caves. Rounds 3-5 show 5 caves. "
        + "One cave holds your stolen item. Snakes guard every other cave.",
        "You get 1 Light Spell in rounds 1-2 and 2 in rounds 3-4. A Light Spell lights one cave entrance so you can peek inside: "
        + "you will see your item or a snake. The light fades after a moment, so remember what you saw! "
        + "Use all available Light Spells before choosing a cave to enter. Your Light Spells refill at the start of every round.",
        "You have 30 HP for the whole journey. Walking into a snake's cave costs 10 HP. "
        + "After a snake attacks, that cave is blocked and cannot be used again. "
        + "If your HP reaches 0, it is game over."
    };

    static Font loadPixelFont() {
        try {
            java.io.File file = GameAssets.resolveFile("fonts/Pixelpurl-0vBPP.ttf");
            if (file == null) throw new java.io.FileNotFoundException("fonts/Pixelpurl-0vBPP.ttf");
            Font font = Font.createFont(Font.TRUETYPE_FONT, file);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (Exception e) {
            System.err.println("Could not load Pixelpurl: " + e.getMessage());
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, 12);
    }

    static final Font PIXEL_FONT = loadPixelFont();

    static Font pixelFont(int style, float size) {
        return PIXEL_FONT.deriveFont(style, size * 1.2f);
    }

    // =====================================================================
    //  Script helpers
    // =====================================================================
    Step say(String t) {
        return () -> { cur = t; curSys = false; reveal = 0; mode = Mode.TEXT; blocking = true; };
    }
    void setOpeningScene(int sceneNo) {
        if (openingScene == sceneNo) return;
        boolean skipFade = openingScene == 3
            && (sceneNo == 4 || sceneNo == BLACK_OPENING_SCENE)
            || openingScene == BLACK_OPENING_SCENE && sceneNo == 4
            || openingScene == 4 && sceneNo == 5;
        openingScene = sceneNo;
        sceneFade = skipFade ? 1f : 0f;
        if (sceneNo == BLACK_OPENING_SCENE) audio.playRustle();
        if (sceneNo == 3) audio.playCaveAmbience();
    }
    Step sys(String t) {
        return () -> {
            toast(t);
            mode = Mode.AUTO; autoAt = System.currentTimeMillis() + 450; blocking = true;
        };
    }
    Step act(Runnable r) { return r::run; }
    Step askYesNo(String q, Consumer<Boolean> cb) {
        return () -> { cur = q; curSys = true; reveal = 0; yesNoCb = cb; mode = Mode.YESNO; blocking = true; };
    }
    Step askDoor(String purpose, String q, IntConsumer cb) {
        return () -> { cur = q; curSys = true; reveal = 0; doorCb = cb; doorPurpose = purpose; mode = Mode.DOOR; blocking = true; };
    }
    Step endStep()   {
        return () -> {
            if (!practice) audio.playWinner();
            mode = Mode.END;
            blocking = true;
        };
    }
    Step overStep()  {
        return () -> {
            audio.playGameOver();
            mode = Mode.OVER;
            blocking = true;
        };
    }

    void toast(String t) {
        logs.add(new Log(t, System.currentTimeMillis()));
        if (logs.size() > 5) logs.remove(0);
    }

    void showVisualCue(String text) {
        visualCue = text;
        visualCueStartedAt = System.currentTimeMillis();
    }

    void insert(Step... steps) { for (int i = steps.length - 1; i >= 0; i--) queue.addFirst(steps[i]); }
    void insert(List<Step> l) { insert(l.toArray(new Step[0])); }

    void proceed() {
        while (true) {
            Step s = queue.pollFirst();
            if (s == null) return;
            blocking = false;
            s.run();
            if (blocking) return;
        }
    }

    void answerYesNo(boolean v) {
        Consumer<Boolean> cb = yesNoCb; yesNoCb = null;
        mode = Mode.NONE;
        if (cb != null) cb.accept(v);
        proceed();
    }
    void answerDoor(int i) {
        IntConsumer cb = doorCb; doorCb = null;
        mode = Mode.NONE;
        if (cb != null) cb.accept(i);
        proceed();
    }

    // =====================================================================
    //  Game logic
    // =====================================================================
    /** A snake bite: -10 HP, unless a Grimoire ward is up. */
    void snakeBite() {
        if (ward) {
            ward = false; lastBlocked = true;
            flash = 0.5;
            toast("WARD ABSORBED THE BITE");
            return;
        }
        lastBlocked = false;
        hp = Math.max(0, hp - SNAKE_DMG);
        audio.playHpLost();
        hurt = 1; shake = 9;
        toast("SNAKE BITE: -" + SNAKE_DMG + " HP");
        if (hp == 0) {
            queue.clear();
            insert(say("The venom spreads fast. His light flickers out, and the mage collapses in the dark..."),
                   sys("GAME OVER"), overStep());
        }
    }

    void resetCaves() {
        Arrays.fill(open, true); Arrays.fill(peek, false);
        Arrays.fill(doorOpen, false); Arrays.fill(blocked, 0);
    }

    void resetRun() {
        audio.stopNarration();
        audio.stopMenuMusic();
        audio.stopCaveAmbience();
        hp = HP_MAX; hpShown = HP_MAX; ward = false; lastBlocked = false;
        inventory.clear(); used.clear(); logs.clear(); queue.clear();
        roundNo = 0; spells = 2; maxSpells = 2; nDoors = 3; itemShow = -1; itemDoor = -1;
        sleeping = false; goblinVisible = false;
        endingExit = false;
        Arrays.fill(types, PathType.SNAKE);
        resetCaves();
        flash = hurt = shake = 0;
        visualCue = null; visualCueStartedAt = 0; enteredDoor = -1;
        castingHandUntil = 0;
        cur = ""; reveal = 0; yesNoCb = null; doorCb = null; blocking = false;
        paused = false; confirmingExit = false; confirmingMainMenu = false;
        confirmingMenuExit = false; pauseSettings = false; pauseStartedAt = 0;
    }

    /** Shows the main menu. */
    void begin() {
        scene = Scene.TITLE;
        mode = Mode.MENU;
        panel = Panel.NONE;
        audio.playMenuMusic();
    }

    void toMenu() { resetRun(); begin(); }

    /** Leaves the menu and starts the story. */
    void launch() {
        practice = false;
        resetRun();
        mode = Mode.NONE;
        startGame();
        proceed();
    }

    /** Leaves the menu and starts a short practice run (no story, all items supplied). */
    void launchPractice() {
        practice = true;
        resetRun();
        mode = Mode.NONE;
        insert(
            act(() -> { scene = Scene.DOORS; }),
            sys("PRACTICE MODE"),
            sys("All 4 items are in your pack."),
            act(() -> {
                for (String it : ITEMS) inventory.add(it);
                startRound(1);
            })
        );
        proceed();
    }

    void restart() { if (practice) launchPractice(); else launch(); }

    void practiceDone() {
        endingName = "Practice";
        insert(sys("PRACTICE COMPLETE"), endStep());
    }

    void startGame() {
        insert(
            act(() -> { scene = Scene.CAMP; sleeping = false; setOpeningScene(1); }),
            act(() -> audio.playNarration("audio/theMage.wav")),
            say("A mage travels through the forest, hoping to reach a village before night falls."),
            say("Someone there is said to know how to lift his curse - the [Curse of Mana]."),
            say("The curse weakens his magic. He avoids casting unless absolutely necessary."),
            say("But after hours of walking, he still can't find the way out. Night has come."),
            act(() -> setOpeningScene(2)),
            say("But after hours of walking, he still can't find the way out. Night has come."),
            act(() -> setOpeningScene(3)),
            say("He finds a cave and sets down his things:"),
            act(() -> inventory.addAll(Arrays.asList(ITEMS[0], ITEMS[1], ITEMS[2], ITEMS[3], SEAL))),
            sys("Mana Potion"), sys("Map of the Village"), sys("Grimoire"), sys("Old Charm"), sys("Curse's Seal"),
            say("He rests... until his eyes grow heavy."),
            act(() -> { sleeping = true; setOpeningScene(BLACK_OPENING_SCENE); }),
            say("*Rustle.*"),
            act(() -> { sleeping = false; inventory.clear(); flash = 0.6; shake = 6; }),
            act(() -> setOpeningScene(4)),
            say("His eyes snap open. His belongings are gone!"),
            act(() -> setOpeningScene(5)),
            say("Luckily, the thief was careless. Small footprints lead deeper into the cave."),
            act(() -> { scene = Scene.TUNNEL; setOpeningScene(6); }),
            say("The deeper he goes, the darker it becomes."),
            say("He casts \"Lumen\". A faint light appears above his palm."),
            say("\"I can't keep doing this,\" he mutters. \"I have to make every spell count.\""),
            say("The footprints suddenly disappear. The tunnel opens into a huge cavern, its walls pocked with dark cave entrances."),
            act(() -> { scene = Scene.DOORS; roundNo = 0; nDoors = 3; resetCaves(); }),
            sys("Entered the Caverns of Paths"),
            sys("Objective: Retrieve your belongings."),
            sys("Rule: Choose ONE cave at every junction."),
            sys("Warning: Not every cave hides the thief."),
            say("\"...What?\""),
            act(this::showLoadingScreen),
            act(() -> startRound(1))
        );
    }

    void showLoadingScreen() {
        audio.pauseCaveAmbience(true);
        mode = Mode.LOADING;
        loadingStartedAt = System.currentTimeMillis();
        loadingUntil = loadingStartedAt + LOADING_FADE_MS + LOADING_HOLD_MS;
        blocking = true;
    }

    void startRound(int r) {
        roundNo = r;
        spells = maxSpells = r <= 2 ? 1 : 2;
        enteredDoor = -1;
        showVisualCue(r == 5 ? "FINAL ROUND" : "ROUND " + r);
        nDoors = r <= 2 ? 3 : 5;
        List<PathType> t = new ArrayList<>();
        t.add(PathType.ITEM);
        while (t.size() < nDoors) t.add(PathType.SNAKE);
        Collections.shuffle(t, rng);
        for (int i = 0; i < MAXN; i++) types[i] = i < nDoors ? t.get(i) : PathType.SNAKE;
        resetCaves();
        itemShow = -1; itemDoor = -1;
        scene = Scene.DOORS;
        if (practice) used.clear();

        List<Step> L = new ArrayList<>();
        if (r == 5 && !practice) L.add(say("The thief's trail ends here. Five dark caves... and one of them hides the Seal."));
        L.add(sys(r == 5 ? "FINAL ROUND" : "ROUND " + r));
        L.add(sys(nDoors + " CAVE ENTRANCES"));
        L.add(sys("LIGHT SPELLS: " + spells + "/" + maxSpells));
        if (r == 2 && !practice) L.add(sys("WARNING: Memories may affect your choices."));
        if (r == 3 && !practice) L.add(sys("Four snakes now lurk among the caves."));
        L.add(act(this::lightPhase));
        insert(L);
    }

    void lightPhase() {
        if (spells <= 0) {
            insert(sys("All Light Spells used. Choose a cave to enter."), act(this::walkPhase));
            return;
        }
        insert(askDoor("light", "Which cave entrance would you like to light? "
            + "Spells remaining: " + spells + "/" + maxSpells, v -> {
            final int left = spells - 1;
            final int r = roundNo;
            String desc = types[v] == PathType.ITEM
                ? (r == 5 ? "The light finds a faint red glow deep inside cave " + roman(v) + "... and something watching from the dark."
                          : "The darkness fades. Something glints on the floor of cave " + roman(v) + "...")
                : "The darkness fades. Cave " + roman(v) + " is alive with coiled shapes and a low, hungry hiss...";
            insert(
                act(() -> {
                    spells--;
                    flash = 0.3;
                    castingHandUntil = System.currentTimeMillis() + 1000;
                }),
                act(() -> peek[v] = true),
                sys("Light Spell Used"),
                sys("Remaining Spells: " + left + "/" + maxSpells),
                say(desc),
                say("The light fades little by little."),
                act(() -> peek[v] = false),
                sys("LIGHT ENDED"),
                act(this::lightPhase)
            );
        }));
    }

    void walkPhase() {
        insert(askDoor("walk", "Which cave would you like to walk into?", p -> {
            final int r = roundNo;
            List<Step> L = new ArrayList<>();
            L.add(say("He walks into cave " + roman(p) + "..."));
            L.add(act(() -> {
                doorOpen[p] = true;
                enteredDoor = p;
                showVisualCue("ENTERING CAVE " + roman(p));
                audio.playCaveReveal();
            }));
            switch (types[p]) {
                case ITEM: {
                    if (practice) {
                        L.add(say("Practice: you found the item for round " + r + "!"));
                        L.add(act(() -> {
                            itemShow = r - 1;
                            itemDoor = p;
                            flash = 0.5;
                            audio.playItemReveal();
                        }));
                        L.add(say(r < 5 ? "Nice work. On to the next round." : "That was the last round."));
                        L.add(act(() -> { itemShow = -1; itemDoor = -1; if (r < 5) startRound(r + 1); else practiceDone(); }));
                    } else if (r == 5) {
                        L.add(act(this::finalRound));
                    } else {
                        final String item = ITEMS[r - 1];
                        L.add(say("He finds his item on the cave floor: " + item + "!"));
                        L.add(act(() -> {
                            inventory.add(item);
                            itemShow = r - 1;
                            itemDoor = p;
                            flash = 0.5;
                            audio.playItemReveal();
                        }));
                        L.add(sys("One item recovered"));
                        L.add(sys((5 - r) + " items remaining."));
                        L.add(sys(ITEM_SHORT[r - 1]));
                        L.add(say("Upon touching it, a memory flashes through his mind..."));
                        L.add(say(MEMORIES[r - 1]));
                        L.add(say(MEMORY_NOTES[r - 1]));
                        L.add(act(() -> { itemShow = -1; itemDoor = -1; startRound(r + 1); }));
                    }
                    break;
                }
                case SNAKE:
                    L.add(say("A snake uncoils in the dark and lunges!"));
                    L.add(act(this::snakeBite));
                    L.add(act(() -> insert(say(lastBlocked
                        ? "The ward flares and the fangs glance off. He backs out into the cavern."
                        : "The fangs sink in. -" + SNAKE_DMG + " HP. He staggers back out into the cavern."))));
                    L.add(act(() -> { doorOpen[p] = false; open[p] = false; blocked[p] = 1; }));
                    L.add(act(this::lightPhase));
                    break;
            }
            insert(L);
        }));
    }

    void finalRound() {
        insert(
            act(() -> { scene = Scene.FINAL; goblinVisible = true; spells = 1; maxSpells = 1; }),
            say("The tunnel opens into the deepest chamber of the cavern."),
            say("The Thief is waiting, and beside it lies the Curse's Seal."),
            say("\"Give that back,\" the mage says."),
            say("The thief doesn't move. Then it suddenly vanishes."),
            act(() -> { goblinVisible = false; flash = 0.8; }),
            say("Each round led him deeper into the cave."),
            say("With every item he retrieved, another piece of his forgotten past returned."),
            sys("One last Light Spell remains"),
            askYesNo("Use the last Light Spell?", b -> { }),
            act(() -> spells = 0),
            act(() -> audio.playLightSpell()),
            say("He uses the last Light Spell."),
            say("The darkness disappears, but he only sees himself..."),
            say("Not who he is now, but someone from the past."),
            act(() -> { scene = Scene.MEMORY; flash = 1; }),
            sys("The Forgotten Memory"),
            say("He remembers. He had once been a powerful mage."),
            say("So confident, he attempted a spell far beyond his control. It went wrong."),
            say("Someone important to him was caught in the aftermath."),
            act(() -> { flash = 1; shake = 10; }),
            say("He remembers reaching out. He remembers screaming. He remembers the light. Then nothing."),
            say("The guilt was unbearable. He couldn't accept what he had done."),
            say("So he created the Curse of Mana himself, sealing away his own magic."),
            say("To protect himself, he also sealed the memories around the incident."),
            say("He forgot that he had cursed himself... and began to believe someone else had."),
            say("His journey had never been about finding a cure."),
            say("It was about finding someone to undo a curse he had chosen to create."),
            act(() -> {
                inventory.add(SEAL);
                audio.playItemReveal();
            }),
            act(this::finalChoice)
        );
    }

    void finalChoice() {
        insert(
            sys("The Final Choice"),
            sys("TRUTH IDENTIFIED."),
            sys("THE CURSE OF MANA WAS SELF-IMPOSED."),
            sys("MEMORY RESTORED."),
            askYesNo("WOULD YOU LIKE TO REMOVE THE CURSE?", remove -> insert(
                say("He looks at the Curse's Seal. For years he wanted nothing more than to be rid of it."),
                say("But now he understands why it existed."),
                say("For the first time, he doesn't need the screen to tell him what to do. He chooses."),
                sys("CHOICE ACCEPTED."),
                say("The screen disappears. The cavern becomes silent."),
                say("He takes the Curse's Seal in his hand."),
                act(() -> {
                    scene = remove ? Scene.FORGIVE : Scene.ATONE;
                    endingName = remove ? "Forgiveness" : "Atonement";
                    flash = 1;
                }),
                say(remove ? "[ENDING: Forgiveness]" : "[ENDING: Atonement]"),
                say(remove ? "He breaks the seal. Mana floods back, warm and steady."
                           : "He closes his fist around the seal and keeps the curse."),
                say(remove ? "He will carry the guilt, but this time he will protect people, just as he promised."
                           : "Not as a punishment he forgot, but as a weight he chooses to carry knowingly."),
                say("He turns toward the exit. Before he completely leaves the cave, one last message appears:"),
                act(() -> endingExit = true),
                sys("CAVERN CLEARED."),
                sys("OBJECTIVE COMPLETE: REMEMBER."),
                endStep()
            ))
        );
    }

    void setHomeMusicVolume(Point p) {
        if (!volumeSliderRect.contains(p)) return;
        audio.setMusicVolume((p.x - volumeSliderRect.x) / (float) volumeSliderRect.width);
    }

    // =====================================================================
    //  Items
    // =====================================================================
    /** Items can be used whenever the game is asking the player to choose (and a round is running). */
    boolean canUseItems() {
        return scene == Scene.DOORS && roundNo > 0 && (mode == Mode.YESNO || mode == Mode.DOOR);
    }

    boolean itemReady(int i) {
        return i < 4 && inventory.contains(ITEMS[i]) && !used.contains(ITEMS[i]);
    }

    Rectangle slotRect(int i) { return new Rectangle(PW - 20 - 5 * 50 + i * 50, 10, 44, 44); }

    void useItem(int i) {
        if (i < 0 || i > 3 || !itemReady(i)) return;
        if (!canUseItems()) { toast("Items can only be used while choosing."); return; }
        String name = ITEMS[i];
        switch (i) {
            case 0: // Mana Potion: +1 Light Spell
                if (spells >= maxSpells) { toast("Light Spells are already full."); return; }
                spells++; used.add(name); flash = 0.3;
                toast("POTION: +1 LIGHT SPELL (" + spells + "/" + maxSpells + ")");
                if (mode == Mode.DOOR && "walk".equals(doorPurpose)) {
                    // the player was about to walk in: give them the chance to cast the refilled spell first
                    doorCb = null; mode = Mode.NONE;
                    insert(act(this::lightPhase));
                    proceed();
                }
                break;
            case 1: { // Map: marks and seals off one dangerous cave
                List<Integer> cand = new ArrayList<>();
                for (int k = 0; k < nDoors; k++) if (open[k] && types[k] != PathType.ITEM) cand.add(k);
                if (cand.isEmpty()) { toast("The map has nothing more to rule out."); return; }
                int k = cand.get(rng.nextInt(cand.size()));
                open[k] = false; blocked[k] = 3; used.add(name); flash = 0.2;
                toast("MAP: CAVE " + roman(k) + " SEALED OFF");
                break;
            }
            case 2: // Grimoire: ward blocks the next snake bite
                if (ward) { toast("A ward is already active."); return; }
                ward = true; used.add(name); flash = 0.3;
                toast("WARD READY: BLOCKS NEXT BITE");
                break;
            default: // Old Charm: +10 HP
                if (hp >= HP_MAX) { toast("HP is already full."); return; }
                hp = Math.min(HP_MAX, hp + 10); used.add(name); flash = 0.3;
                toast("OLD CHARM: +10 HP");
                break;
        }
    }

    // =====================================================================
    //  Input
    // =====================================================================
    void advance() {
        audio.stopNarration();
        switch (mode) {
            case TEXT:
                if (reveal < cur.length()) reveal = cur.length();
                else {
                    mode = Mode.NONE;
                    audio.playDialogueBoop();
                    proceed();
                }
                break;
            case AUTO: autoAt = 0; break;
            case DOOR: reveal = cur.length(); break;
            case END: case OVER: restart(); break;
            default: break;
        }
    }

    boolean canPause() {
        return scene != Scene.TITLE && mode != Mode.MENU && mode != Mode.LOADING
            && mode != Mode.END && mode != Mode.OVER;
    }

    void setPaused(boolean pause) {
        if (pause == paused) return;
        if (pause) {
            paused = true;
            confirmingExit = false;
            confirmingMainMenu = false;
            pauseSettings = false;
            pauseStartedAt = System.currentTimeMillis();
            audio.pauseNarration();
            audio.pauseCaveAmbience(true);
        } else {
            long pausedFor = System.currentTimeMillis() - pauseStartedAt;
            if (autoAt > 0) autoAt += pausedFor;
            if (visualCue != null) visualCueStartedAt += pausedFor;
            audio.resumeNarration();
            audio.pauseCaveAmbience(false);
            confirmingExit = false;
            confirmingMainMenu = false;
            pauseSettings = false;
            paused = false;
        }
    }

    void clickPauseMenu(Point p) {
        if (pauseSettings) {
            if (backRect.contains(p)) {
                audio.playButtonBoop();
                pauseSettings = false;
            } else if (fsRect.contains(p)) {
                audio.playButtonBoop();
                toggleFullscreen();
            } else if (muteRect.contains(p)) {
                audio.playButtonBoop();
                audio.toggleMute();
            } else if (volumeSliderRect.contains(p)) {
                setHomeMusicVolume(p);
            }
            return;
        }
        if (confirmingExit || confirmingMainMenu) {
            if (pauseMenuRect.contains(p)) {
                audio.playButtonBoop();
                confirmingExit = false;
                confirmingMainMenu = false;
            } else if (pauseExitRect.contains(p)) {
                audio.playButtonBoop();
                if (confirmingExit) System.exit(0);
                else toMenu();
            }
            return;
        }
        if (resumeRect.contains(p)) {
            audio.playButtonBoop();
            setPaused(false);
        } else if (pauseMenuRect.contains(p)) {
            audio.playButtonBoop();
            confirmingMainMenu = true;
        } else if (pauseSettingsRect.contains(p)) {
            audio.playButtonBoop();
            pauseSettings = true;
        } else if (pauseExitRect.contains(p)) {
            audio.playButtonBoop();
            confirmingExit = true;
        }
    }

    Rectangle doorRect(int i) {
        return new Rectangle(doorVisualX(i) * S, dy() * S, doorVisualWidth() * S, dh() * S);
    }

    void pick(int i) {
        if (mode != Mode.DOOR || i >= nDoors || !open[i]) return;
        if ("light".equals(doorPurpose)) audio.playLightSpell();
        else audio.playButtonBoop();
        answerDoor(i);
    }

    boolean tryItemClick(Point p) {
        if (!canUseItems()) return false;
        for (int i = 0; i < 4; i++) {
            if (itemReady(i) && slotRect(i).contains(p)) {
                audio.playButtonBoop();
                useItem(i);
                return true;
            }
        }
        return false;
    }

    void click(Point p) {
        if (paused) {
            clickPauseMenu(p);
            return;
        }
        if (canPause() && pauseRect.contains(p)) {
            audio.playButtonBoop();
            setPaused(true);
            return;
        }
        switch (mode) {
            case MENU: {
                int hit = menuHit(p);
                if (hit != 0 && hit != 13) audio.playButtonBoop();
                menuClick(p);
                break;
            }
            case TEXT: case AUTO: advance(); break;
            case YESNO:
                if (tryItemClick(p)) break;
                if (yesRect.contains(p)) {
                    audio.playButtonBoop();
                    answerYesNo(true);
                } else if (noRect.contains(p)) {
                    audio.playButtonBoop();
                    answerYesNo(false);
                }
                break;
            case DOOR: {
                if (tryItemClick(p)) break;
                boolean pickedDoor = false;
                for (int i = 0; i < nDoors; i++) {
                    if (open[i] && doorRect(i).contains(p)) {
                        pick(i);
                        pickedDoor = true;
                        break;
                    }
                }
                if (!pickedDoor) advance();
                break;
            }
            case END: case OVER:
                if (againRect.contains(p)) {
                    audio.playButtonBoop();
                    restart();
                } else if (menuRect.contains(p)) {
                    audio.playButtonBoop();
                    toMenu();
                }
                break;
            default: break;
        }
    }

    boolean overClickable() {
        if (paused) {
            if (pauseSettings) return fsRect.contains(mouse) || muteRect.contains(mouse)
                || volumeSliderRect.contains(mouse) || backRect.contains(mouse);
            if (confirmingExit || confirmingMainMenu) {
                return pauseMenuRect.contains(mouse) || pauseExitRect.contains(mouse);
            }
            return resumeRect.contains(mouse) || pauseSettingsRect.contains(mouse)
                || pauseMenuRect.contains(mouse) || pauseExitRect.contains(mouse);
        }
        if (canPause() && pauseRect.contains(mouse)) return true;
        switch (mode) {
            case MENU: return menuHit(mouse) != 0;
            case YESNO: return yesRect.contains(mouse) || noRect.contains(mouse) || overItem();
            case DOOR:
                for (int i = 0; i < nDoors; i++) if (open[i] && doorRect(i).contains(mouse)) return true;
                return overItem();
            case END: case OVER: return againRect.contains(mouse) || menuRect.contains(mouse);
            default: return false;
        }
    }

    boolean overItem() {
        if (!canUseItems()) return false;
        for (int i = 0; i < 4; i++) if (itemReady(i) && slotRect(i).contains(mouse)) return true;
        return false;
    }

    // =====================================================================
    //  Construction
    // =====================================================================
    public GoblinDoorsGUI() {
        setPreferredSize(new Dimension(PW, PH));
        setFocusable(true);
        controller = XInputController.connect();
        menuBackground = GameAssets.loadImage("images/bgMenu.gif");
        doorsBackground = GameAssets.loadImage("images/gameBG.png");
        menuBanner = GameAssets.loadImage("icons/bannerMenu.png");
        storyScenes = new ImageIcon[] {
            GameAssets.loadImage("scenes/scene1.gif"),
            GameAssets.loadImage("scenes/scene2.gif"),
            GameAssets.loadImage("scenes/scene3.gif"),
            GameAssets.loadImage("scenes/scene4.gif"),
            GameAssets.loadImage("scenes/scene5.gif"),
            GameAssets.loadImage("scenes/scene6.gif")
        };
        wizardCharacter = GameAssets.loadImage("images/wizard_cs.gif");
        holyWizard = GameAssets.loadImage("images/holyWizard.gif");
        rememberedWizard = GameAssets.loadImage("images/wizardRemembered.gif");
        goblinStartled = GameAssets.loadImage("images/goblinStartled.gif");
        goblinNotStartled = GameAssets.loadImage("images/goblinNotStartled.gif");
        snakeCharacter = GameAssets.loadImage("images/snake.gif");
        doorOne = GameAssets.loadImage("images/doorOne.png");
        doorOneHighlighted = GameAssets.loadImage("images/doorOneHighlighted.png");
        idleHand = GameAssets.loadImage("images/hand1.png");
        castingHand = GameAssets.loadImage("images/hand2.png");
        potionIcon = GameAssets.loadImage("icons/potion.png");
        mapIcon = GameAssets.loadImage("icons/map.png");
        grimoireIcon = GameAssets.loadImage("icons/grimoire.png");
        charmIcon = GameAssets.loadImage("icons/charm.png");
        heartFilled = GameAssets.loadImage("icons/heart_filled.png");
        heartEmpty = GameAssets.loadImage("icons/heart_empty.png");
        dialogueIndicator = tintDialogueIndicator(GameAssets.loadImage("images/dialogueIndicator.png"));
        menuPlay = GameAssets.loadImage("icons/play.png").getImage();
        menuHelp = GameAssets.loadImage("icons/controls.png").getImage();
        menuInfo = GameAssets.loadImage("icons/about.png").getImage();
        menuSettings = GameAssets.loadImage("icons/settings.png").getImage();
        bgCave = makeCaveBg(SH, 98);
        resetRun();
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                controllerCursorVisible = false;
                if (SwingUtilities.isLeftMouseButton(e) && !isWindowResizePoint(e.getLocationOnScreen())) {
                    click(toVirtual(e.getPoint()));
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                controllerCursorVisible = false;
                mouse = toVirtual(e.getPoint());
            }
            @Override public void mouseDragged(MouseEvent e) {
                controllerCursorVisible = false;
                mouse = toVirtual(e.getPoint());
                if ((mode == Mode.MENU && panel == Panel.SETTINGS) || (paused && pauseSettings)) {
                    setHomeMusicVolume(mouse);
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F11
                    || (e.getKeyCode() == KeyEvent.VK_ENTER && e.isAltDown())) {
                    toggleFullscreen();
                    return;
                }
                if (paused) {
                    if (pauseSettings) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_N) {
                            pauseSettings = false;
                        }
                    } else if (confirmingExit || confirmingMainMenu) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_N) {
                            confirmingExit = false;
                            confirmingMainMenu = false;
                        } else if (e.getKeyCode() == KeyEvent.VK_Y) {
                            if (confirmingExit) System.exit(0);
                            else toMenu();
                        }
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER
                        || e.getKeyCode() == KeyEvent.VK_SPACE) setPaused(false);
                    return;
                }
                if (mode == Mode.MENU && confirmingMenuExit) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_N) {
                        confirmingMenuExit = false;
                    } else if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_Y) {
                        System.exit(0);
                    }
                    return;
                }
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE:
                        if (canPause()) setPaused(true);
                        else if (mode == Mode.MENU && confirmingMenuExit) confirmingMenuExit = false;
                        else if (mode == Mode.MENU && panel != Panel.NONE) panel = Panel.NONE;
                        else if (isFullscreen()) toggleFullscreen();
                        break;
                    case KeyEvent.VK_ENTER:
                        advance(); break;
                    case KeyEvent.VK_SPACE: advance(); break;
                    case KeyEvent.VK_Y: if (mode == Mode.YESNO) answerYesNo(true); break;
                    case KeyEvent.VK_N: if (mode == Mode.YESNO) answerYesNo(false); break;
                    case KeyEvent.VK_1: case KeyEvent.VK_NUMPAD1: pick(0); break;
                    case KeyEvent.VK_2: case KeyEvent.VK_NUMPAD2: pick(1); break;
                    case KeyEvent.VK_3: case KeyEvent.VK_NUMPAD3: pick(2); break;
                    case KeyEvent.VK_4: case KeyEvent.VK_NUMPAD4: pick(3); break;
                    case KeyEvent.VK_5: case KeyEvent.VK_NUMPAD5: pick(4); break;
                    case KeyEvent.VK_P: useItem(0); break;
                    case KeyEvent.VK_M: useItem(1); break;
                    case KeyEvent.VK_G: useItem(2); break;
                    case KeyEvent.VK_C: useItem(3); break;
                    default: break;
                }
            }
        });
        new javax.swing.Timer(33, e -> tick()).start();
        begin();
    }

    static BufferedImage tintDialogueIndicator(ImageIcon icon) {
        int width = icon.getIconWidth(), height = icon.getIconHeight();
        if (width <= 0 || height <= 0) return null;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.drawImage(icon.getImage(), 0, 0, null);
        g.dispose();
        int gold = new Color(255, 241, 168).getRGB() & 0x00ffffff;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                image.setRGB(x, y, (pixel & 0xff000000) | gold);
            }
        }
        return image;
    }

    void tick() {
        tickController();
        if (paused) {
            Cursor want = Cursor.getPredefinedCursor(overClickable() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
            if (getCursor().getType() != want.getType()) setCursor(want);
            repaint();
            return;
        }
        frame++;
        audio.updateMenuMusicFade();
        long now = System.currentTimeMillis();
        if (previousScene != scene) {
            previousScene = scene;
            sceneFade = 0f;
        } else if (sceneFade < 1f) {
            sceneFade = Math.min(1f, sceneFade + 0.035f);
        }
        if (mode == Mode.TEXT || mode == Mode.YESNO || mode == Mode.DOOR) reveal += 0.6;
        if (mode == Mode.AUTO && now >= autoAt) { mode = Mode.NONE; proceed(); }
        if (mode == Mode.LOADING && now >= loadingUntil) {
            mode = Mode.NONE;
            cur = "";
            reveal = 0;
            sceneFade = 0f;
            audio.pauseCaveAmbience(false);
            proceed();
        }
        if (hpShown > hp) hpShown = Math.max(hp, hpShown - 0.4);
        else if (hpShown < hp) hpShown = Math.min(hp, hpShown + 0.4);
        flash *= 0.92; hurt *= 0.9; shake *= 0.85;
        logs.removeIf(l -> now - l.at > 6000);
        Cursor want = Cursor.getPredefinedCursor(overClickable() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
        if (getCursor().getType() != want.getType()) setCursor(want);
        repaint();
    }

    void tickController() {
        if (controller == null) return;
        XInputController.PadState state = controller.poll();
        if (state == null) {
            controllerConnected = false;
            controllerCursorVisible = false;
            return;
        }
        if (!controllerConnected) {
            controllerConnected = true;
            controllerCursorVisible = true;
            mouse = new Point(PW / 2, PH / 2);
        }

        float x = Math.abs(state.leftX) < 0.18f ? 0f
            : Math.copySign((Math.abs(state.leftX) - 0.18f) / 0.82f, state.leftX);
        float y = Math.abs(state.leftY) < 0.18f ? 0f
            : -Math.copySign((Math.abs(state.leftY) - 0.18f) / 0.82f, state.leftY);
        if ((state.buttons & PAD_DPAD_LEFT) != 0) x -= 1f;
        if ((state.buttons & PAD_DPAD_RIGHT) != 0) x += 1f;
        if ((state.buttons & PAD_DPAD_UP) != 0) y -= 1f;
        if ((state.buttons & PAD_DPAD_DOWN) != 0) y += 1f;

        int moveX = Math.round(Math.max(-1f, Math.min(1f, x)) * 12);
        int moveY = Math.round(Math.max(-1f, Math.min(1f, y)) * 12);
        if (moveX != 0 || moveY != 0) {
            controllerCursorVisible = true;
            mouse = new Point(Math.max(0, Math.min(PW - 1, mouse.x + moveX)),
                Math.max(0, Math.min(PH - 1, mouse.y + moveY)));
        }

        int pressed = state.pressed;
        if (pressed != 0) controllerCursorVisible = true;
        if ((pressed & PAD_A) != 0) click(new Point(mouse));
        if ((pressed & PAD_B) != 0) controllerBack();
        if ((pressed & (PAD_START | PAD_BACK)) != 0) {
            if (paused && pauseSettings) pauseSettings = false;
            else if (paused) setPaused(false);
            else if (canPause()) setPaused(true);
        }
        if ((pressed & PAD_X) != 0) useItem(0);
        if ((pressed & PAD_Y) != 0) useItem(1);
        if ((pressed & PAD_LEFT_SHOULDER) != 0) useItem(2);
        if ((pressed & PAD_RIGHT_SHOULDER) != 0) useItem(3);
    }

    void controllerBack() {
        if (paused) {
            if (pauseSettings) pauseSettings = false;
            else if (confirmingExit || confirmingMainMenu) {
                confirmingExit = false;
                confirmingMainMenu = false;
            }
            else setPaused(false);
        } else if (mode == Mode.YESNO) {
            answerYesNo(false);
        } else if (mode == Mode.MENU && panel != Panel.NONE) {
            panel = Panel.NONE;
        } else if (mode == Mode.MENU && confirmingMenuExit) {
            confirmingMenuExit = false;
        } else if (canPause()) {
            setPaused(true);
        }
    }

    // =====================================================================
    //  Scene rendering (logical 320x240 canvas)
    // =====================================================================
    /** Big cavern wall: lumpy rock above the horizon line, pebbled floor below it, stalactites hanging down. */
    BufferedImage makeCaveBg(int h, int hor) {
        BufferedImage b = new BufferedImage(W, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        Random r = new Random(7);
        g.setColor(new Color(14, 12, 20));
        g.fillRect(0, 0, W, h);
        // rock wall: overlapping lumpy stones
        for (int i = 0; i < 4 * hor; i++) {
            int x = r.nextInt(W + 20) - 10, y = r.nextInt(hor + 6) - 3;
            int rw = 8 + r.nextInt(18), rh = 6 + r.nextInt(12);
            int v = 22 + r.nextInt(24);
            g.setColor(new Color(v, v - 2, v + 10));
            g.fillOval(x, y, rw, rh);
            if (r.nextInt(3) == 0) { g.setColor(new Color(v + 14, v + 11, v + 26)); g.fillRect(x + 2, y + 1, rw / 2, 1); }
        }
        for (int i = 0; i < hor / 2; i++) {
            int x = r.nextInt(W), y = r.nextInt(hor);
            g.setColor(new Color(9, 7, 14));
            g.drawLine(x, y, x + r.nextInt(14) - 7, y + 3 + r.nextInt(12));
        }
        // floor
        for (int y = hor; y < h; y++) {
            float t = Math.min(1f, (y - hor) / 62f);
            int v = (int) (46 - 22 * t);
            g.setColor(new Color(v + 4, v, v + 14));
            g.fillRect(0, y, W, 1);
        }
        int pebbles = 70 * Math.max(1, h - hor) / 62;
        for (int i = 0; i < pebbles; i++) {
            int v = 55 + r.nextInt(30);
            g.setColor(new Color(v, v - 4, v + 12));
            g.fillRect(r.nextInt(W), hor + 2 + r.nextInt(Math.max(1, h - hor - 4)), 1 + r.nextInt(3), 1 + r.nextInt(2));
        }
        g.setColor(new Color(10, 8, 16));
        g.fillRect(0, hor - 4, W, 4);
        // stalactites
        for (int i = 0; i < 16; i++) {
            int x = r.nextInt(W), len = 6 + r.nextInt(18);
            for (int k = 0; k < len; k++) {
                int w = Math.max(1, (len - k) / 3 + 1);
                g.setColor(new Color(20 + k, 18 + k, 30 + k));
                g.fillRect(x - w / 2, k, w, 1);
            }
        }
        g.dispose();
        return b;
    }

    void glow(Graphics2D g, float cx, float cy, float rad, Color c, int a) {
        Paint old = g.getPaint();
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), rad, new float[]{0f, 1f},
            new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, Math.max(0, a))),
                        new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Rectangle2D.Float(cx - rad, cy - rad, 2 * rad, 2 * rad));
        g.setPaint(old);
    }

    void vignette(Graphics2D g, float cx, float cy, float rad, int maxA, int height) {
        Paint old = g.getPaint();
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), rad, new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 60), new Color(0, 0, 0, maxA)}));
        g.fillRect(0, 0, W, height);
        g.setPaint(old);
    }

    /** Glowing cavern crystals - the cavern's own light (no torches, this is a cave, not a dungeon). */
    void drawCrystal(Graphics2D g, int x, int y, Color c) {
        int pulse = 60 + (int) (Math.sin(frame * 0.07 + x) * 20);
        glow(g, x, y - 8, 24, c, pulse);
        g.setColor(c.darker().darker());
        g.fillPolygon(new int[]{x - 7, x - 3, x}, new int[]{y, y - 12, y}, 3);
        g.setColor(c.darker());
        g.fillPolygon(new int[]{x + 1, x + 4, x + 8}, new int[]{y, y - 9, y}, 3);
        g.setColor(c);
        g.fillPolygon(new int[]{x - 3, x + 1, x + 4}, new int[]{y, y - 20, y}, 3);
        g.setColor(new Color(255, 255, 255, 170));
        g.fillRect(x, y - 15, 1, 5);
    }

    void drawMage(Graphics2D g, int x, int y, int sc) {
        drawWizard(g, wizardCharacter, x, y, sc);
    }

    void drawHolyWizard(Graphics2D g, int x, int y, int sc) {
        drawWizard(g, holyWizard, x, y, sc);
    }

    void drawWizard(Graphics2D g, ImageIcon wizard, int x, int y, int sc) {
        if (wizard == null || wizard.getIconWidth() <= 0 || wizard.getIconHeight() <= 0) {
            return;
        }
        int pixelHeight = 20 * sc;
        int pixelWidth = 16 * sc;
        int targetHeight = Math.max(1, Math.round(pixelHeight * 1.5f));
        int targetWidth = Math.max(1, Math.round(targetHeight
            * (float) wizard.getIconWidth() / wizard.getIconHeight()));
        g.drawImage(wizard.getImage(), x - (targetWidth - pixelWidth) / 2,
            y + pixelHeight - targetHeight, targetWidth, targetHeight, null);
    }

    // ---------- Cave entrances ----------
    void rockSurround(Graphics2D g, int x, int y, int w, int h, long seed) {
        g.setColor(new Color(46, 42, 60));
        g.fill(caveShape(x, y, w, h, 5, seed));
        Random r = new Random(seed * 31 + 7);
        for (Point2D p : caveOutline(x, y, w, h, 5, seed)) {
            int rad = 3 + r.nextInt(5), v = 62 + r.nextInt(38);
            g.setColor(new Color(v, v - 3, v + 12));
            g.fillOval((int) p.getX() - rad, (int) p.getY() - rad, rad * 2, rad * 2);
            g.setColor(new Color(v + 22, v + 18, v + 34));
            g.fillRect((int) p.getX() - rad + 1, (int) p.getY() - rad + 1, Math.max(1, rad - 1), 1);
        }
    }

    void drawFangs(Graphics2D g, int x, int y, int w) {
        double cx = x + w / 2.0, cy = y + w / 2.0, r = w / 2.0;
        int n = Math.max(3, w / 8);
        for (int k = 0; k < n; k++) {
            double tx = x + 3 + (w - 6) * (k + 0.5) / n;
            double dxx = tx - cx;
            double top = cy - Math.sqrt(Math.max(0, r * r - dxx * dxx));
            int len = 4 + (k * 5) % 6;
            g.setColor(new Color(34, 30, 44));
            g.fillPolygon(new int[]{(int) tx - 2, (int) tx + 2, (int) tx}, new int[]{(int) top - 1, (int) top - 1, (int) top + len}, 3);
        }
    }

    /** An unlit cave mouth: pure darkness. */
    void drawDark(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(5, 4, 9)); g.fillRect(x, y, w, h);
        g.setColor(new Color(14, 12, 22));
        g.fillRect(x, y + h - 4, w, 4);
    }

    /** A lit tunnel running away from the viewer (perspective walls, ceiling and floor). */
    void drawTunnel(Graphics2D g, int x, int y, int w, int h) {
        int bx0 = x + (int) (w * 0.22), bx1 = x + (int) (w * 0.78), by0 = y + (int) (h * 0.30), by1 = y + (int) (h * 0.62);
        g.setColor(new Color(20, 16, 28)); g.fillRect(x, y, w, h);
        g.setColor(new Color(12, 10, 18)); g.fillRect(bx0, by0, bx1 - bx0, by1 - by0);
        g.setColor(new Color(30, 25, 40)); g.fillPolygon(new int[]{x, x + w, bx1, bx0}, new int[]{y, y, by0, by0}, 4);
        g.setColor(new Color(50, 42, 62)); g.fillPolygon(new int[]{x, bx0, bx0, x}, new int[]{y, by0, by1, y + h}, 4);
        g.setColor(new Color(44, 37, 56)); g.fillPolygon(new int[]{x + w, bx1, bx1, x + w}, new int[]{y, by0, by1, y + h}, 4);
        g.setColor(new Color(74, 62, 84)); g.fillPolygon(new int[]{x, x + w, bx1, bx0}, new int[]{y + h, y + h, by1, by1}, 4);
        glow(g, x + w / 2f, y + h * 0.78f, w, new Color(255, 225, 150), 90);
    }

    /** What the light reveals inside entrance i. */
    void drawInterior(Graphics2D g, int i, int x, int y, int w, int h) {
        drawTunnel(g, x, y, w, h);
        int cx = x + w / 2;
        switch (types[i]) {
            case ITEM: {
                if (itemShow >= 0 && itemDoor == i) break;
                int bob = (int) (Math.sin(frame * 0.15) * 2);
                glow(g, cx, y + h - 22, w * 0.9f, new Color(255, 220, 90), 210);
                g.setColor(PAL.get('N')); g.fillRect(cx - 9, y + h - 12, 18, 6);
                g.setColor(PAL.get('n')); g.fillRect(cx - 9, y + h - 14, 18, 3);
                int ri = Math.max(0, Math.min(roundNo, 5) - 1);
                drawItemIcon(g, ri, cx - 8, y + h - 31 + bob, 2, null);
                for (int k = 0; k < 4; k++)
                    if ((frame / 5 + k) % 3 != 0) {
                        g.setColor(Color.WHITE);
                        g.fillRect(x + 6 + k * (w - 12) / 4, y + h / 2 + ((k * 13) % 20), 2, 2);
                    }
                break;
            }
            case SNAKE:
                glow(g, cx, y + h - 20, w * 0.8f, new Color(120, 220, 80), 80);
                if (snakeCharacter != null && snakeCharacter.getIconWidth() > 0
                    && snakeCharacter.getIconHeight() > 0) {
                    int availableWidth = Math.max(1, w - 6);
                    int availableHeight = Math.max(1, h - 6);
                    double imageScale = Math.min(availableWidth / (double) snakeCharacter.getIconWidth(),
                        availableHeight / (double) snakeCharacter.getIconHeight());
                    int imageWidth = (int) Math.round(snakeCharacter.getIconWidth() * imageScale);
                    int imageHeight = (int) Math.round(snakeCharacter.getIconHeight() * imageScale);
                    g.drawImage(snakeCharacter.getImage(), cx - imageWidth / 2,
                        y + h - imageHeight - 3, imageWidth, imageHeight, this);
                }
                break;
        }
    }

    void drawFireflies(Graphics2D g) {
        int[] xs = {8, 312, 14, 306};
        int[] ys = {116, 124, 144, 148};
        for (int i = 0; i < xs.length; i++) {
            double phase = frame * 0.045 + i * 1.7;
            int x = xs[i] + (int) (Math.sin(phase) * 2);
            int y = ys[i] + (int) (Math.cos(phase * 0.8) * 3);
            int alpha = 90 + (int) ((Math.sin(phase * 1.4) + 1) * 55);
            Color glowColor = new Color(255, 241, 168, alpha);
            glow(g, x, y, 7, glowColor, alpha);
            g.setColor(new Color(255, 250, 210, alpha));
            g.fillRect(x - 1, y - 1, 2, 2);
        }
    }

    void drawLightOrb(Graphics2D g, int cx, int cy) {
        int bob = (int) (Math.sin(frame * 0.2) * 2);
        glow(g, cx, cy + bob, 20, new Color(255, 245, 190), 200);
        g.setColor(new Color(255, 252, 215)); g.fillOval(cx - 3, cy + bob - 3, 6, 6);
    }

    void drawMouth(Graphics2D g, int x, int y, int w, int h, long seed, Consumer<Graphics2D> inner) {
        rockSurround(g, x, y, w, h, seed);
        Shape old = g.getClip();
        g.clip(caveShape(x, y, w, h, 0, seed));
        inner.accept(g);
        drawFangs(g, x, y, w);
        g.setClip(old);
    }

    void drawEntrance(Graphics2D g, int i, boolean hovered) {
        int x = dx(i), y = dy(), w = dw(), h = dh();
        long seed = 31 + i * 17 + nDoors;
        boolean lit = peek[i] || doorOpen[i];
        ImageIcon doorImage = hovered && doorOneHighlighted != null
            && doorOneHighlighted.getIconWidth() > 0 && doorOneHighlighted.getIconHeight() > 0
            ? doorOneHighlighted : doorOne;
        if (doorImage != null && doorImage.getIconWidth() > 0 && doorImage.getIconHeight() > 0) {
            int visualWidth = doorVisualWidth();
            int visualX = doorVisualX(i);
            g.drawImage(doorImage.getImage(), visualX, y, visualWidth, h, null);
            if (lit) {
                Shape oldClip = g.getClip();
                int openingX = x + Math.round(w * 0.18f);
                int openingY = y + Math.round(h * 0.12f);
                int openingW = Math.round(w * 0.64f);
                int openingH = Math.round(h * 0.84f);
                g.clip(caveShape(openingX, openingY, openingW, openingH, 0, seed));
                drawInterior(g, i, openingX, openingY, openingW, openingH);
                g.setClip(oldClip);
            }
        } else {
            drawMouth(g, x, y, w, h, seed, gg -> {
                if (lit) drawInterior(gg, i, x, y, w, h); else drawDark(gg, x, y, w, h);
            });
        }
        if (peek[i]) {
            glow(g, x + w / 2f, y + h * 0.55f, w * 1.1f, new Color(255, 230, 150), 70);
            drawLightOrb(g, x + w / 2, y + h / 2 - 6);
        }
        if (blocked[i] == 1) {                 // snake cave: bitten, now off limits
            g.setColor(new Color(160, 30, 30, 120)); g.fill(caveShape(x, y, w, h, 0, seed));
            g.setColor(new Color(235, 225, 200)); g.setStroke(new BasicStroke(3));
            g.drawLine(x + w / 4, y + h / 2, x + 3 * w / 4, y + h - 8);
            g.drawLine(x + 3 * w / 4, y + h / 2, x + w / 4, y + h - 8);
            g.setStroke(new BasicStroke(1));
        } else if (blocked[i] == 3) {          // sealed off by the map
            g.setColor(new Color(0, 0, 0, 150)); g.fill(caveShape(x, y, w, h, 0, seed));
            drawItemIcon(g, 1, x + w / 2 - 8, y + h / 2 - 8, 2, null);
            g.setColor(new Color(230, 60, 60)); g.setStroke(new BasicStroke(2));
            g.drawLine(x + w / 2 - 9, y + h / 2 - 9, x + w / 2 + 9, y + h / 2 + 9);
            g.drawLine(x + w / 2 + 9, y + h / 2 - 9, x + w / 2 - 9, y + h / 2 + 9);
            g.setStroke(new BasicStroke(1));
        }
        if (enteredDoor == i && visualCue != null
            && visualCue.startsWith("ENTERING CAVE")) {
            float pulse = (float) (0.65 + 0.35 * Math.sin(frame * 0.35));
            g.setColor(new Color(255, 241, 168, Math.round(255 * pulse)));
            g.setStroke(new BasicStroke(3));
            g.draw(caveShape(x, y, w, h, 0, seed));
            g.setStroke(new BasicStroke(1));
        } else if (hovered && (doorOne == null || doorOne.getIconWidth() <= 0
            || doorOne.getIconHeight() <= 0 || doorOneHighlighted == null
            || doorOneHighlighted.getIconWidth() <= 0 || doorOneHighlighted.getIconHeight() <= 0)) {
            g.setColor(new Color(255, 241, 168));
            g.setStroke(new BasicStroke(2));
            g.draw(caveShape(x, y, w, h, 0, seed));
            g.setStroke(new BasicStroke(1));
        }
    }

    void drawEntrances(Graphics2D g) {
        for (int i = 0; i < nDoors; i++) {
            boolean hov = mode == Mode.DOOR && open[i] && doorRect(i).contains(mouse);
            drawEntrance(g, i, hov);
        }
    }

    void drawStartledGoblinInDoor(Graphics2D g) {
        if (itemShow < 0 || itemDoor < 0 || itemDoor >= nDoors
            || goblinStartled.getIconWidth() <= 0 || goblinStartled.getIconHeight() <= 0) return;
        int x = dx(itemDoor), y = dy(), w = dw(), h = dh();
        int iconW = w - 4;
        int iconH = iconW * goblinStartled.getIconHeight() / goblinStartled.getIconWidth();
        Shape oldClip = g.getClip();
        g.clip(caveShape(x, y, w, h, 0, 31 + itemDoor * 17 + nDoors));
        g.drawImage(goblinStartled.getImage(), x + (w - iconW) / 2,
            y + (h - iconH) / 2, iconW, iconH, null);
        g.setClip(oldClip);
    }

    // ---------- Gameplay scenes ----------
    void drawScene(Graphics2D g) {
        g.setClip(null);
        g.setColor(new Color(8, 6, 12)); g.fillRect(0, 0, W, H);
        g.setClip(0, 0, W, SH);
        switch (scene) {
            case TITLE: break;
            case CAMP: {
                if (openingScene == BLACK_OPENING_SCENE) {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, W, SH);
                    break;
                }
                if (openingScene > 0 && openingScene <= storyScenes.length
                    && storyScenes[openingScene - 1] != null) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(storyScenes[openingScene - 1].getImage(), 0, 0, W, SH, null);
                    vignette(g, 160, 125, 175, 120, SH);
                    break;
                }
                g.drawImage(bgCave, 0, 0, null);
                glow(g, 200, 135, 80, new Color(255, 150, 50), 90);
                drawFire(g, 200, 138);
                if (sleeping) {
                    AffineTransform o = g.getTransform();
                    g.translate(110, 152); g.rotate(-Math.PI / 2);
                    drawMage(g, 0, 0, 2);
                    g.setTransform(o);
                    g.setFont(pixelFont(Font.BOLD, 9));
                    g.setColor(new Color(200, 220, 255));
                    int zz = (frame / 8) % 3;
                    g.drawString("z", 150, 112 - zz * 3);
                    g.drawString("Z", 158, 104 - zz * 3);
                } else {
                    drawMage(g, 112, 112, 2);
                }
                for (int i = 0; i < inventory.size(); i++)
                    drawItemIcon(g, iconFor(inventory.get(i)), 150 + i * 10, 150, 1, null);
                vignette(g, 200, 125, 190, 220, SH);
                break;
            }
            case TUNNEL: {
                if (openingScene > 0 && openingScene <= storyScenes.length
                    && storyScenes[openingScene - 1] != null
                    && storyScenes[openingScene - 1].getIconWidth() > 0
                    && storyScenes[openingScene - 1].getIconHeight() > 0) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(storyScenes[openingScene - 1].getImage(), 0, 0, W, SH, null);
                    vignette(g, 160, 125, 175, 120, SH);
                } else {
                    g.drawImage(bgCave, 0, 0, null);
                    g.setColor(new Color(20, 16, 24));
                    for (int k = 0; k < 10; k++) g.fillRect(104 + k * 13, 134 + (k % 2) * 6, 3, 2);
                    drawMage(g, 60, 112, 2);
                    drawFireflies(g);
                    vignette(g, 98, 120, 140, 240, SH);
                }
                break;
            }
            case DOORS: {
                if (doorsBackground.getIconWidth() > 0 && doorsBackground.getIconHeight() > 0) {
                    int imageWidth = doorsBackground.getIconWidth();
                    int imageHeight = doorsBackground.getIconHeight();
                    double targetAspect = (double) W / SH;
                    int sourceWidth = imageWidth;
                    int sourceHeight = imageHeight;
                    if ((double) imageWidth / imageHeight > targetAspect) {
                        sourceWidth = (int) Math.round(imageHeight * targetAspect);
                    } else {
                        sourceHeight = (int) Math.round(imageWidth / targetAspect);
                    }
                    int sourceX = (imageWidth - sourceWidth) / 2;
                    int sourceY = Math.min((imageHeight - sourceHeight) / 2 + 20 * sourceHeight / SH,
                        imageHeight - sourceHeight);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(doorsBackground.getImage(), 0, 0, W, SH,
                        sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight, null);
                } else {
                    g.drawImage(bgCave, 0, 0, null);
                    drawCrystal(g, 9, 132, new Color(90, 170, 255));
                    drawCrystal(g, 311, 134, new Color(170, 110, 255));
                }
                drawEntrances(g);
                ImageIcon hand = System.currentTimeMillis() < castingHandUntil ? castingHand : idleHand;
                if (hand.getIconWidth() > 0 && hand.getIconHeight() > 0) {
                    int handHeight = SH + 40;
                    int handWidth = handHeight * hand.getIconWidth() / hand.getIconHeight();
                    g.drawImage(hand.getImage(), (W - handWidth) / 2, 20,
                        handWidth, handHeight, null);
                }
                drawFireflies(g);
                vignette(g, 160, 125, 175, 150, SH);
                if (itemShow >= 0) {
                    int bob = (int) (Math.sin(frame * 0.12) * 3);
                    g.setColor(new Color(0, 0, 0, 120)); g.fillRect(0, 0, W, SH);
                    drawStartledGoblinInDoor(g);
                    glow(g, 160, 72, 70, new Color(255, 220, 100), 190);
                    drawItemIcon(g, itemShow, 160 - 24, 48 + bob, 6, null);
                }
                break;
            }
            case FINAL: {
                g.drawImage(bgCave, 0, 0, null);
                drawCrystal(g, 24, 132, new Color(90, 170, 255));
                drawCrystal(g, 296, 134, new Color(170, 110, 255));
                glow(g, 232, 140, 40, new Color(255, 40, 40), 90 + (int) (Math.sin(frame * 0.2) * 30));
                sprite(g, ICONS[4], 224, 132, 2, null, null, false);
                if (goblinVisible) {
                    drawImageContain(g, goblinNotStartled, 158, 95, 62, 42);
                }
                drawMage(g, 60, 112, 2);
                vignette(g, 160, 125, 190, 205, SH);
                break;
            }
            case MEMORY: {
                g.setColor(new Color(14, 10, 30)); g.fillRect(0, 0, W, SH);
                g.setColor(new Color(40, 30, 70, 120));
                for (int k = 0; k < 24; k++) {
                    double a = k * Math.PI / 12 + frame * 0.01;
                    g.drawLine(160, 80, 160 + (int) (Math.cos(a) * 200), 80 + (int) (Math.sin(a) * 200));
                }
                glow(g, 160, 80, 100, new Color(255, 230, 150), 150 + (int) (Math.sin(frame * 0.1) * 30));
                drawHolyWizard(g, 126, 62, 5);
                drawMage(g, 232, 106, 2);
                glow(g, 248, 120, 24, new Color(180, 160, 255), 60);
                g.setColor(new Color(60, 50, 90)); g.fillRect(0, 148, W, 12);
                break;
            }
            case FORGIVE: drawEnding(g, true); break;
            case ATONE:   drawEnding(g, false); break;
        }
    }

    void drawFire(Graphics2D g, int x, int y) {
        int f = frame / 3;
        int h1 = 12 + (f % 3) * 2, h2 = 8 + ((f + 1) % 3) * 2;
        g.setColor(PAL.get('N')); g.fillRect(x - 10, y + 2, 20, 4);
        g.setColor(PAL.get('n')); g.fillRect(x - 8, y, 16, 3);
        g.setColor(new Color(220, 70, 30));  g.fillOval(x - 7, y - h1 + 3, 14, h1);
        g.setColor(new Color(255, 160, 40)); g.fillOval(x - 5, y - h2 + 3, 10, h2);
        g.setColor(new Color(255, 241, 168)); g.fillOval(x - 2, y - 1, 5, 5);
    }

    void drawEnding(Graphics2D g, boolean forgive) {
        Color top = forgive ? new Color(255, 215, 130) : new Color(55, 75, 130);
        Color bot = forgive ? new Color(130, 70, 110) : new Color(20, 25, 50);
        g.setPaint(new GradientPaint(0, 0, top, 0, 110, bot));
        g.fillRect(0, 0, W, 112);
        g.setPaint(null);
        if (!forgive) {
            g.setColor(new Color(225, 235, 255)); g.fillOval(250, 14, 22, 22);
            g.setColor(new Color(55, 75, 130));   g.fillOval(257, 12, 20, 20);
            Random r = new Random(3);
            g.setColor(Color.WHITE);
            for (int i = 0; i < 25; i++) if ((frame / 8 + i) % 4 != 0) g.fillRect(r.nextInt(W), r.nextInt(70), 1, 1);
        } else {
            glow(g, 160, 100, 120, new Color(255, 240, 160), 120);
        }
        g.setColor(forgive ? new Color(40, 55, 45) : new Color(14, 20, 34));
        g.fillRect(0, 112, W, SH - 112);
        g.setColor(forgive ? new Color(20, 35, 30) : new Color(8, 12, 24));
        for (int i = 0; i < 9; i++) {
            int bx = i * 40 + 6 + (i % 2) * 10, base = 114, h = 36 + (i * 13) % 20;
            g.fillPolygon(new int[]{bx - 12, bx, bx + 12}, new int[]{base, base - h, base}, 3);
        }
        drawWizard(g, endingExit ? rememberedWizard : wizardCharacter, 144, 76, 2);
        if (forgive) {
            for (int k = 0; k < 22; k++) {
                int px = 100 + (k * 37) % 120;
                int py = 120 - ((frame * 2 + k * 17) % 100);
                g.setColor(new Color(255, 241, 168, 200));
                g.fillRect(px, py, 2, 2);
            }
        } else {
            glow(g, 190, 100, 20, new Color(255, 60, 60), 90);
            sprite(g, ICONS[4], 184, 94, 1, null, null, false);
        }
    }

    // =====================================================================
    //  Painting (full-res UI on top of the scaled scene)
    // =====================================================================
    /** Black bars that fill the window around the centered 4:3 game area. */
    void paintSides(Graphics2D g) {
        g.setPaint(null);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    void paintControllerCursor(Graphics2D g) {
        if (!controllerCursorVisible || mouse.x < 0 || mouse.y < 0) return;
        Graphics2D cursor = (Graphics2D) g.create();
        cursor.translate(offX, offY);
        cursor.scale(scale, scale);
        cursor.setColor(new Color(0, 0, 0, 190));
        cursor.fillOval(mouse.x - 10, mouse.y - 10, 22, 22);
        cursor.setColor(new Color(255, 241, 168));
        cursor.setStroke(new BasicStroke(2));
        cursor.drawOval(mouse.x - 10, mouse.y - 10, 22, 22);
        cursor.drawLine(mouse.x - 14, mouse.y, mouse.x + 14, mouse.y);
        cursor.drawLine(mouse.x, mouse.y - 14, mouse.x, mouse.y + 14);
        cursor.dispose();
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        computeScale();
        if (mode == Mode.MENU) {
            paintMenu(g);
            paintControllerCursor(g);
            g.dispose();
            return;
        }

        Graphics2D gb = buf.createGraphics();
        drawScene(gb);
        gb.setClip(0, 0, W, SH);
        if (hurt > 0.03) { gb.setColor(new Color(200, 0, 0, (int) (110 * Math.min(1, hurt)))); gb.fillRect(0, 0, W, SH); }
        if (flash > 0.03) { gb.setColor(new Color(255, 255, 255, (int) (255 * Math.min(1, flash)))); gb.fillRect(0, 0, W, SH); }
        gb.dispose();

        g.setColor(Color.BLACK); g.fillRect(0, 0, getWidth(), getHeight());
        paintSides(g);
        g.translate(offX, offY); g.scale(scale, scale);
        int sx = (int) ((Math.random() - 0.5) * 2 * shake), sy = (int) ((Math.random() - 0.5) * 2 * shake);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(buf, sx, sy, PW, PH, null);
        if (sceneFade < 1f) {
            g.setColor(new Color(0, 0, 0, Math.round(255 * (1f - sceneFade))));
            g.fillRect(0, 0, PW, PH);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawDoorNumerals(g); drawHud(g); drawLog(g); drawDialogue(g);
        drawVisualCue(g);
        if (mode == Mode.END) drawEndScreen(g);
        if (mode == Mode.OVER) drawOverScreen(g);
        if (canPause()) drawPauseButton(g);
        if (paused) drawPauseMenu(g);
        if (mode == Mode.LOADING) paintLoadingScreen(g);
        paintControllerCursor(g);
        g.dispose();
    }

    void drawPauseButton(Graphics2D g) {
        boolean hover = pauseRect.contains(mouse);
        g.setColor(hover ? new Color(255, 241, 168) : new Color(40, 34, 80));
        g.fillRect(pauseRect.x, pauseRect.y, pauseRect.width, pauseRect.height);
        g.setColor(new Color(255, 241, 168));
        drawPixelBorder(g, pauseRect, 3);
        g.setColor(hover ? new Color(40, 30, 10) : Color.WHITE);
        g.fillRect(pauseRect.x + 12, pauseRect.y + 11, 6, 22);
        g.fillRect(pauseRect.x + 26, pauseRect.y + 11, 6, 22);
    }

    void drawPauseMenu(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, 0, PW, PH);
        if (pauseSettings) {
            drawSettingsPanel(g, pauseSettingsHit(mouse));
            return;
        }
        g.setColor(new Color(16, 14, 34));
        g.fillRect(290, 150, 380, 440);
        g.setColor(new Color(255, 241, 168));
        drawPixelBorder(g, new Rectangle(290, 150, 380, 440), 3);
        g.setFont(pixelFont(Font.BOLD, 42));
        g.setColor(new Color(255, 241, 168));
        if (confirmingExit || confirmingMainMenu) {
            boolean exiting = confirmingExit;
            centerText(g, exiting ? "Exit to Desktop?" : "Return to Menu?", PW / 2, 245);
            g.setFont(pixelFont(Font.BOLD, 20));
            g.setColor(new Color(235, 230, 215));
            centerText(g, exiting ? "Are you sure you want to quit?"
                : "Your current game progress will be lost.", PW / 2, 300);
            menuButton(g, pauseMenuRect, "Cancel", true, pauseMenuRect.contains(mouse));
            menuButton(g, pauseExitRect, exiting ? "Exit" : "Main Menu", true, pauseExitRect.contains(mouse));
        } else {
            centerText(g, "Paused", PW / 2, 225);
            menuButton(g, resumeRect, "Resume", true, resumeRect.contains(mouse));
            menuButton(g, pauseSettingsRect, "Settings", true, pauseSettingsRect.contains(mouse));
            menuButton(g, pauseMenuRect, "Main Menu", true, pauseMenuRect.contains(mouse));
            menuButton(g, pauseExitRect, "Exit to Desktop", true, pauseExitRect.contains(mouse));
        }
    }

    int pauseSettingsHit(Point p) {
        if (fsRect.contains(p)) return 4;
        if (muteRect.contains(p)) return 12;
        if (volumeSliderRect.contains(p)) return 13;
        if (backRect.contains(p)) return 5;
        return 0;
    }

    void centerText(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    void shadowText(Graphics2D g, String s, int x, int y, Color c) {
        g.setColor(new Color(0, 0, 0, 200)); g.drawString(s, x + 2, y + 2);
        g.setColor(c); g.drawString(s, x, y);
    }

    void drawHud(Graphics2D g) {
        // ----- Hearts (10 HP each) -----
        int heartSize = 30;
        int heartGap = 6;
        int heartsWidth = 3 * heartSize + 2 * heartGap;
        int heartsX = slotRect(4).x + slotRect(4).width - heartsWidth;
        for (int i = 0; i < 3; i++) {
            ImageIcon heart = hp >= (3 - i) * 10 ? heartFilled : heartEmpty;
            g.drawImage(heart.getImage(), heartsX + i * (heartSize + heartGap), 68,
                heartSize, heartSize, null);
        }
        if (ward) {                                       // Grimoire ward indicator
            int sx = 22, sy = 100;
            g.setColor(new Color(0, 0, 0, 150)); g.fillRoundRect(sx - 6, sy - 4, 100, 34, 10, 10);
            g.setColor(new Color(120, 220, 255));
            g.fillPolygon(new int[]{sx, sx + 20, sx + 20, sx + 10, sx}, new int[]{sy, sy, sy + 14, sy + 24, sy + 14}, 5);
            g.setColor(Color.WHITE); g.drawLine(sx + 10, sy + 4, sx + 10, sy + 18);
            shadowText(g, "WARD", sx + 28, sy + 18, new Color(160, 230, 255));
        }

        // ----- Round + Light Spells -----
        if ((scene == Scene.DOORS && roundNo > 0) || scene == Scene.FINAL) {
            String rl = scene == Scene.FINAL ? "FINAL ROUND" : "ROUND " + roundNo;
            int statusY = 474;
            int spellSize = 14;
            int spellStep = 18;
            int groupX = 45;
            g.setFont(pixelFont(Font.BOLD, 14));
            shadowText(g, rl, groupX, statusY, new Color(255, 241, 168));
            String spellLabel = "LIGHT SPELLS";
            int labelX = groupX + g.getFontMetrics().stringWidth(rl) + 16;
            shadowText(g, spellLabel, labelX, statusY, new Color(255, 241, 168));
            int spellX = labelX + g.getFontMetrics().stringWidth(spellLabel) + 10;
            int spellY = statusY - spellSize + 1;
            for (int i = 0; i < maxSpells; i++) {
                int ox = spellX + i * spellStep;
                if (i < spells) {
                    g.setColor(new Color(255, 241, 168));
                    g.fillOval(ox, spellY, spellSize, spellSize);
                    g.setColor(Color.WHITE);
                    g.fillOval(ox + 3, spellY + 3, 4, 4);
                } else {
                    g.setColor(new Color(255, 241, 168, 120));
                    g.drawOval(ox, spellY, spellSize, spellSize);
                }
            }
        }

        // ----- Items (clickable) -----
        int hovered = -1;
        for (int i = 0; i < 5; i++) {
            Rectangle r = slotRect(i);
            String name = i < 4 ? ITEMS[i] : SEAL;
            boolean have = inventory.contains(name), spent = used.contains(name);
            boolean ready = itemReady(i) && canUseItems();
            boolean hov = r.contains(mouse);
            if (hov) hovered = i;
            g.setColor(new Color(0, 0, 0, 160)); g.fillRect(r.x, r.y, r.width, r.height);
            if (ready) {
                int a = 150 + (int) (Math.sin(frame * 0.2) * 90);
                g.setColor(new Color(255, 241, 168, Math.max(0, Math.min(255, a))));
            } else g.setColor(new Color(255, 241, 168, 160));
            drawPixelBorder(g, r, ready ? 3 : 1);
            Color mono = !have ? new Color(50, 50, 70) : spent ? new Color(80, 80, 100) : null;
            drawItemIcon(g, i, r.x + 6, 16, 4, mono);
            if (spent) { g.setColor(new Color(200, 60, 60, 200)); g.drawLine(r.x + 6, r.y + 6, r.x + 38, r.y + 38); }
            if (i < 4 && have && !spent) {
                g.setFont(pixelFont(Font.BOLD, 13));
                shadowText(g, String.valueOf(ITEM_KEY[i]), r.x + r.width - 13, r.y + r.height - 3, ready ? new Color(255, 241, 168) : new Color(150, 150, 170));
            }
        }
        if (hovered >= 0) drawItemTooltip(g, hovered);
    }

    void drawItemTooltip(Graphics2D g, int i) {
        String name = i < 4 ? ITEMS[i] : SEAL;
        String desc = i < 4 ? ITEM_DESC[i] : "The key to the mage's forgotten past.";
        String status;
        if (i == 4) status = inventory.contains(SEAL) ? "RECOVERED" : "Hidden in the deepest cave (round 5).";
        else if (!inventory.contains(name)) status = "Not recovered yet (found in round " + (i + 1) + ").";
        else if (used.contains(name)) status = "USED";
        else if (canUseItems()) status = "Click or press [" + ITEM_KEY[i] + "] to use it.";
        else status = "Use it when choosing a cave [" + ITEM_KEY[i] + "].";
        g.setFont(fSys);
        FontMetrics fm = g.getFontMetrics();
        int bw = Math.max(fm.stringWidth(name), Math.max(fm.stringWidth(desc), fm.stringWidth(status))) + 28;
        int bx = PW - 20 - bw, by = 62, bh = 92;
        g.setColor(new Color(10, 8, 24, 240)); g.fillRect(bx, by, bw, bh);
        g.setColor(new Color(255, 241, 168));
        drawPixelBorder(g, new Rectangle(bx, by, bw, bh), 1);
        g.setColor(new Color(255, 241, 168)); g.drawString(name, bx + 14, by + 26);
        g.setColor(new Color(235, 230, 215)); g.drawString(desc, bx + 14, by + 52);
        g.setColor(new Color(150, 230, 255)); g.drawString(status, bx + 14, by + 78);
    }

    void drawLog(Graphics2D g) {
        g.setFont(pixelFont(Font.BOLD, 15));
        FontMetrics fm = g.getFontMetrics();
        long now = System.currentTimeMillis();
        int y = 29;
        int n = logs.size();
        for (int i = Math.max(0, n - 2); i < n; i++) {
            Log l = logs.get(i);
            long age = now - l.at;
            float a = age < 4000 ? 1f : Math.max(0f, 1f - (age - 4000) / 2000f);
            String t = "[" + l.t + "]";
            int w = fm.stringWidth(t);
            int cx = 490;
            g.setColor(new Color(10, 30, 50, (int) (190 * a)));
            g.fillRoundRect(cx - w / 2 - 8, y - 16, w + 16, 24, 10, 10);
            g.setColor(new Color(130, 225, 255, (int) (255 * a)));
            g.drawString(t, cx - w / 2, y);
            y += 27;
        }
    }

    void drawDoorNumerals(Graphics2D g) {
        if (scene != Scene.DOORS || roundNo == 0) return;
        g.setFont(pixelFont(Font.BOLD, nDoors == 3 ? 22 : 19));
        for (int i = 0; i < nDoors; i++) {
            Color c = open[i] ? new Color(255, 241, 168) : new Color(110, 110, 125);
            centerText2(g, roman(i), (dx(i) + dw() / 2) * S, (dy() - 6) * S, c);
        }
    }

    void centerText2(Graphics2D g, String s, int cx, int y, Color c) {
        int x = cx - g.getFontMetrics().stringWidth(s) / 2;
        shadowText(g, s, x, y, c);
    }

    static List<String> wrap(String s, FontMetrics fm, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String t = line.length() == 0 ? w : line + " " + w;
            if (fm.stringWidth(t) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(t);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    int drawWrapped(Graphics2D g, String s, int x, int y, int maxW, int lineH) {
        for (String ln : wrap(s, g.getFontMetrics(), maxW)) { g.drawString(ln, x, y); y += lineH; }
        return y;
    }

    void drawButton(Graphics2D g, Rectangle r, String label, String key) {
        boolean hov = r.contains(mouse);
        g.setColor(hov ? new Color(255, 241, 168) : new Color(70, 60, 110));
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(new Color(255, 241, 168));
        drawPixelBorder(g, r, 3);
        g.setFont(fText);
        g.setColor(hov ? new Color(40, 30, 10) : Color.WHITE);
        centerText(g, label + (key.isEmpty() ? "" : "  (" + key + ")"), r.x + r.width / 2, r.y + r.height / 2 + 8);
    }

    void drawPixelBorder(Graphics2D g, Rectangle bounds, int thickness) {
        int gap = Math.min(7, Math.min(bounds.width, bounds.height) / 4);
        int horizontalWidth = bounds.width - gap * 2;
        int verticalHeight = bounds.height - gap * 2;
        if (horizontalWidth <= 0 || verticalHeight <= 0) return;
        g.fillRect(bounds.x + gap, bounds.y, horizontalWidth, thickness);
        g.fillRect(bounds.x + gap, bounds.y + bounds.height - thickness, horizontalWidth, thickness);
        g.fillRect(bounds.x, bounds.y + gap, thickness, verticalHeight);
        g.fillRect(bounds.x + bounds.width - thickness, bounds.y + gap, thickness, verticalHeight);
    }

    void drawDialogue(Graphics2D g) {
        int bx = 30, by = 500, bw = 900, bh = 200;
        Color border = curSys ? new Color(90, 200, 255) : new Color(255, 241, 168);
        g.setColor(new Color(14, 11, 30, 238));
        g.fillRect(bx, by, bw, bh);
        g.setColor(border);
        drawPixelBorder(g, new Rectangle(bx, by, bw, bh), 3);
        g.setColor(border.darker());
        drawPixelBorder(g, new Rectangle(bx + 6, by + 6, bw - 12, bh - 12), 1);

        g.setFont(fText);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrap(cur, fm, bw - 70);
        int remaining = (int) reveal, ty = by + 46;
        g.setColor(curSys ? new Color(150, 230, 255) : new Color(245, 235, 210));
        for (String ln : lines) {
            if (remaining <= 0) break;
            g.drawString(ln.length() <= remaining ? ln : ln.substring(0, remaining), bx + 32, ty);
            remaining -= ln.length() + 1;
            ty += 30;
        }

        if (mode == Mode.TEXT && reveal >= cur.length() && (frame / 12) % 2 == 0) {
            int tx = bx + bw - 44, tyy = by + bh - 36;
            if (dialogueIndicator != null) {
                g.drawImage(dialogueIndicator, tx, tyy, 20, 20, null);
            } else {
                g.setColor(new Color(255, 241, 168));
                g.fillPolygon(new int[]{tx, tx + 18, tx + 9}, new int[]{tyy + 6, tyy + 6, tyy + 18}, 3);
            }
        }
        boolean anyItem = canUseItems() && (itemReady(0) || itemReady(1) || itemReady(2) || itemReady(3));
        if (mode == Mode.YESNO) {
            drawButton(g, yesRect, "YES", "Y");
            drawButton(g, noRect, "NO", "N");
            if (anyItem) {
                g.setFont(pixelFont(Font.BOLD, 14));
                g.setColor(new Color(255, 241, 168));
                centerText(g, "Items: click one (top right) or press P / M / G / C", PW / 2, by + bh - 10);
            }
        }
        if (mode == Mode.DOOR) {
            g.setFont(fSys);
            g.setColor(new Color(160, 160, 190));
            centerText(g, "Click a cave entrance, or press 1-" + nDoors, PW / 2, by + bh - 40);
            if (anyItem) {
                g.setFont(pixelFont(Font.BOLD, 14));
                g.setColor(new Color(255, 241, 168));
                centerText(g, "Items: click one (top right) or press P / M / G / C", PW / 2, by + bh - 16);
            }
        }
    }

    // =====================================================================
    //  Window scaling + fullscreen
    // =====================================================================
    /** The whole game lives on a 960x720 virtual canvas that is scaled (and centered) in the window.
     *  960x720 window -> 1:1.  Fullscreen 1920x1080 -> 1.5x (1440x1080 centered, black bars on the sides). */
    void computeScale() {
        double sx = getWidth() / (double) PW, sy = getHeight() / (double) PH;
        scale = Math.min(sx, sy);
        if (scale <= 0) scale = 1;
        offX = (getWidth() - PW * scale) / 2;
        offY = (getHeight() - PH * scale) / 2;
    }

    Point toVirtual(Point p) {
        computeScale();
        return new Point((int) Math.round((p.x - offX) / scale), (int) Math.round((p.y - offY) / scale));
    }

    void toggleFullscreen() {
        if (window == null) return;
        boolean on = !isFullscreen();
        if (on) {
            if ((window.getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) {
                windowedBounds = window.getBounds();
            }
            window.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            window.setExtendedState(JFrame.NORMAL);
            if (windowedBounds != null) window.setBounds(windowedBounds);
        }
        fullscreen = on;
        updateWindowChrome();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    boolean isFullscreen() {
        return fullscreen || (window != null
            && (window.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0);
    }

    void updateWindowChrome() {
        if (titleBar == null || windowRoot == null || window == null) return;
        boolean maximized = (window.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
        boolean showTitleBar = !fullscreen && !maximized;
        titleBar.setVisible(showTitleBar);
        windowRoot.setBorder(showTitleBar
            ? BorderFactory.createLineBorder(new Color(255, 241, 168), 2)
            : BorderFactory.createEmptyBorder());
        windowRoot.revalidate();
        windowRoot.repaint();
    }

    JPanel createTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setPreferredSize(new Dimension(PW, 38));
        bar.setBackground(new Color(20, 17, 34));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(255, 241, 168)));

        JLabel title = new JLabel("  re:Memory");
        title.setForeground(new Color(255, 241, 168));
        title.setFont(pixelFont(Font.BOLD, 16));
        bar.add(title, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controls.setOpaque(false);
        JButton minimize = titleBarButton("_");
        JButton maximize = titleBarButton("[]");
        JButton close = titleBarButton("X");
        minimize.addActionListener(e -> window.setExtendedState(JFrame.ICONIFIED));
        maximize.addActionListener(e -> toggleWindowMaximize());
        close.addActionListener(e -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        controls.add(minimize);
        controls.add(maximize);
        controls.add(close);
        bar.add(controls, BorderLayout.EAST);

        addTitleBarDrag(bar);
        addTitleBarDrag(title);
        return bar;
    }

    JButton titleBarButton(String label) {
        JButton button = new JButton(label);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(36, 31, 54));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        button.setFocusable(false);
        button.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        button.setPreferredSize(new Dimension(label.equals("X") ? 48 : 44, 36));
        button.setOpaque(true);
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                button.setBackground(label.equals("X") ? new Color(170, 48, 48) : new Color(75, 62, 98));
            }
            @Override public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(36, 31, 54));
            }
        });
        return button;
    }

    void addTitleBarDrag(Component component) {
        Point[] dragOffset = new Point[1];
        component.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    toggleWindowMaximize();
                    return;
                }
                boolean resizingTop = e.getLocationOnScreen().y - window.getY() < 8;
                if (!resizingTop && (window.getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) {
                    windowedBounds = window.getBounds();
                    dragOffset[0] = SwingUtilities.convertPoint(component, e.getPoint(), window);
                }
            }
        });
        component.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (dragOffset[0] == null || (window.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) return;
                Point screen = e.getLocationOnScreen();
                window.setLocation(screen.x - dragOffset[0].x, screen.y - dragOffset[0].y);
                windowedBounds = window.getBounds();
            }
        });
        addWindowResizeHandlers(component);
    }

    void addWindowResizeHandlers(Component component) {
        int[] edges = new int[1];
        Point[] startPoint = new Point[1];
        Rectangle[] startBounds = new Rectangle[1];
        component.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                edges[0] = 0;
                startPoint[0] = null;
                startBounds[0] = null;
                if (fullscreen || (window.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) return;
                Point screen = e.getLocationOnScreen();
                Rectangle bounds = window.getBounds();
                int margin = 8;
                edges[0] = 0;
                if (screen.x - bounds.x < margin) edges[0] |= 1;
                if (bounds.x + bounds.width - screen.x < margin) edges[0] |= 2;
                if (screen.y - bounds.y < margin) edges[0] |= 4;
                if (bounds.y + bounds.height - screen.y < margin) edges[0] |= 8;
                if (edges[0] != 0) {
                    startPoint[0] = screen;
                    startBounds[0] = bounds;
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                edges[0] = 0;
                startPoint[0] = null;
                startBounds[0] = null;
            }
        });
        component.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                if (fullscreen || (window.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) return;
                Point screen = e.getLocationOnScreen();
                Rectangle bounds = window.getBounds();
                int margin = 8, cursor = Cursor.DEFAULT_CURSOR;
                boolean left = screen.x - bounds.x < margin;
                boolean right = bounds.x + bounds.width - screen.x < margin;
                boolean top = screen.y - bounds.y < margin;
                boolean bottom = bounds.y + bounds.height - screen.y < margin;
                if (left && top) cursor = Cursor.NW_RESIZE_CURSOR;
                else if (right && top) cursor = Cursor.NE_RESIZE_CURSOR;
                else if (left && bottom) cursor = Cursor.SW_RESIZE_CURSOR;
                else if (right && bottom) cursor = Cursor.SE_RESIZE_CURSOR;
                else if (left) cursor = Cursor.W_RESIZE_CURSOR;
                else if (right) cursor = Cursor.E_RESIZE_CURSOR;
                else if (top) cursor = Cursor.N_RESIZE_CURSOR;
                else if (bottom) cursor = Cursor.S_RESIZE_CURSOR;
                component.setCursor(Cursor.getPredefinedCursor(cursor));
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (edges[0] == 0 || startPoint[0] == null || fullscreen) return;
                Point screen = e.getLocationOnScreen();
                int dx = screen.x - startPoint[0].x;
                int dy = screen.y - startPoint[0].y;
                Rectangle bounds = new Rectangle(startBounds[0]);
                if ((edges[0] & 1) != 0) { bounds.x += dx; bounds.width -= dx; }
                if ((edges[0] & 2) != 0) bounds.width += dx;
                if ((edges[0] & 4) != 0) { bounds.y += dy; bounds.height -= dy; }
                if ((edges[0] & 8) != 0) bounds.height += dy;
                Dimension minimum = window.getMinimumSize();
                if (bounds.width < minimum.width) {
                    if ((edges[0] & 1) != 0) bounds.x -= minimum.width - bounds.width;
                    bounds.width = minimum.width;
                }
                if (bounds.height < minimum.height) {
                    if ((edges[0] & 4) != 0) bounds.y -= minimum.height - bounds.height;
                    bounds.height = minimum.height;
                }
                window.setBounds(bounds);
                windowedBounds = bounds;
            }
        });
    }

    boolean isWindowResizePoint(Point screen) {
        if (fullscreen || window == null || (window.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) return false;
        Rectangle bounds = window.getBounds();
        int margin = 8;
        return screen.x - bounds.x < margin || bounds.x + bounds.width - screen.x < margin
            || screen.y - bounds.y < margin || bounds.y + bounds.height - screen.y < margin;
    }

    void toggleWindowMaximize() {
        if ((window.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) {
            window.setExtendedState(JFrame.NORMAL);
            if (windowedBounds != null) window.setBounds(windowedBounds);
        } else {
            windowedBounds = window.getBounds();
            window.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    // =====================================================================
    //  Main menu
    // =====================================================================
    /** What the mouse is over: 1-11 main menu actions, 12 mute, 13 volume, 14 cancel exit, 15 confirm exit. */
    int menuHit(Point p) {
        if (mode != Mode.MENU || p == null) return 0;
        if (confirmingMenuExit) {
            if (menuExitCancelRect.contains(p)) return 14;
            if (menuExitConfirmRect.contains(p)) return 15;
            return 0;
        }
        if (panel == Panel.SETTINGS) {
            if (fsRect.contains(p)) return 4;
            if (muteRect.contains(p)) return 12;
            if (volumeSliderRect.contains(p)) return 13;
            if (backRect.contains(p)) return 5;
            return 0;
        }
        if (panel != Panel.NONE) {
            if (panel == Panel.INFO) {
                if (aboutTabRect.contains(p)) return 16;
                if (creditsTabRect.contains(p)) return 17;
                if (aboutCreditsTab && creditsPage > 0 && prevRect.contains(p)) return 18;
                if (aboutCreditsTab && creditsPage < 1 && nextRect.contains(p)) return 19;
            }
            if (closeRect.contains(p)) return 5;
            if (panel == Panel.TUTORIAL) {
                if (tutPage > 0 && prevRect.contains(p)) return 10;
                if (nextRect.contains(p)) return 11;
            }
            return 0;
        }
        if (menuPlayRect.contains(p)) return 1;
        if (tutorialRect.contains(p)) return 6;
        if (practiceRect.contains(p)) return 7;
        if (exitRect.contains(p)) return 2;
        if (menuSettingsRect.contains(p)) return 3;
        if (menuHelpRect.contains(p)) return 8;
        if (menuInfoRect.contains(p)) return 9;
        return 0;
    }

    void drawMenuIcon(Graphics2D g, Image image, Rectangle bounds, boolean hovered) {
        int sourceWidth = image.getWidth(this), sourceHeight = image.getHeight(this);
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        double iconScale = Math.min(bounds.width / (double) sourceWidth,
            bounds.height / (double) sourceHeight) * (hovered ? 1.08 : 1.0);
        int iconWidth = (int) Math.round(sourceWidth * iconScale);
        int iconHeight = (int) Math.round(sourceHeight * iconScale);
        g.drawImage(image, bounds.x + (bounds.width - iconWidth) / 2,
            bounds.y + (bounds.height - iconHeight) / 2, iconWidth, iconHeight, null);
    }

    void menuClick(Point p) {
        switch (menuHit(p)) {
            case 1: launch(); break;
            case 2: confirmingMenuExit = true; break;
            case 3: panel = Panel.SETTINGS; break;
            case 4: toggleFullscreen(); break;
            case 5: panel = Panel.NONE; break;
            case 12: audio.toggleMute(); break;
            case 13: setHomeMusicVolume(p); break;
            case 14: confirmingMenuExit = false; break;
            case 15: System.exit(0); break;
            case 16: aboutCreditsTab = false; break;
            case 17: aboutCreditsTab = true; break;
            case 18: creditsPage = 0; break;
            case 19: creditsPage = 1; break;
            case 6: panel = Panel.TUTORIAL; tutPage = 0; break;
            case 7: launchPractice(); break;
            case 8: panel = Panel.HELP; break;
            case 9: panel = Panel.INFO; aboutCreditsTab = false; creditsPage = 0; break;
            case 10: tutPage = Math.max(0, tutPage - 1); break;
            case 11:
                if (tutPage < TUT_TITLES.length - 1) tutPage++;
                else { panel = Panel.NONE; launchPractice(); }
                break;
            default: break;
        }
    }

    void menuButton(Graphics2D g, Rectangle r, String label, boolean enabled, boolean hover) {
        if (enabled) {
            g.setColor(hover ? new Color(255, 241, 168) : new Color(40, 34, 80));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(new Color(255, 241, 168));
            drawPixelBorder(g, r, 3);
            g.setColor(hover ? new Color(40, 30, 10) : Color.WHITE);
        } else {
            g.setColor(new Color(30, 28, 52));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(new Color(78, 74, 108));
            drawPixelBorder(g, r, 2);
            g.setColor(new Color(125, 122, 150));
        }
        g.setFont(pixelFont(Font.BOLD, 30));
        centerText(g, label, r.x + r.width / 2, r.y + r.height / 2 + 10);
    }

    void paintLoadingScreen(Graphics2D g) {
        long elapsed = System.currentTimeMillis() - loadingStartedAt;
        float fade = Math.max(0f, Math.min(1f, elapsed / (float) LOADING_FADE_MS));
        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(fade));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, PW, PH);

        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, (fade - 0.65f) / 0.35f))));
        g.setFont(pixelFont(Font.BOLD, 26));
        shadowText(g, "TIP", 48, 72, new Color(255, 241, 168));
        g.setFont(pixelFont(Font.BOLD, 20));
        long carouselTime = Math.max(0, elapsed - LOADING_FADE_MS);
        int tipIndex = (int) (carouselTime / LOADING_TIP_INTERVAL_MS) % LOADING_TIPS.length;
        long tipTime = carouselTime % LOADING_TIP_INTERVAL_MS;
        if (tipIndex == 0 || tipTime >= LOADING_TIP_TRANSITION_MS) {
            shadowText(g, LOADING_TIPS[tipIndex], 48, 116, Color.WHITE);
        } else {
            float progress = tipTime / (float) LOADING_TIP_TRANSITION_MS;
            Composite tipComposite = g.getComposite();
            g.setComposite(AlphaComposite.SrcOver.derive(1f - progress));
            shadowText(g, LOADING_TIPS[(tipIndex + LOADING_TIPS.length - 1) % LOADING_TIPS.length],
                48 - Math.round(progress * 40), 116, Color.WHITE);
            g.setComposite(AlphaComposite.SrcOver.derive(progress));
            shadowText(g, LOADING_TIPS[tipIndex], 48 + Math.round((1f - progress) * 40), 116, Color.WHITE);
            g.setComposite(tipComposite);
        }
        g.setFont(pixelFont(Font.BOLD, 16));
        int dots = (int) (elapsed / 250) % 4;
        String loadingText = "LOADING" + new String(new char[dots]).replace('\0', '.');
        shadowText(g, loadingText, 48, PH - 38, new Color(180, 180, 200));
        g.setComposite(oldComposite);
    }

    void paintMenu(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintSides(g);
        AffineTransform base = g.getTransform();
        g.translate(offX, offY); g.scale(scale, scale);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (menuBackground != null && menuBackground.getIconWidth() > 0 && menuBackground.getIconHeight() > 0) {
            double bgScale = Math.max(PW / (double) menuBackground.getIconWidth(),
                PH / (double) menuBackground.getIconHeight());
            int bgWidth = (int) Math.ceil(menuBackground.getIconWidth() * bgScale);
            int bgHeight = (int) Math.ceil(menuBackground.getIconHeight() * bgScale);
            g.drawImage(menuBackground.getImage(), (PW - bgWidth) / 2, (PH - bgHeight) / 2,
                bgWidth, bgHeight, null);
        }
        g.setColor(new Color(0, 0, 0, 55)); g.fillRect(0, 0, PW, PH);

        if (menuBanner.getIconWidth() > 0 && menuBanner.getIconHeight() > 0) {
            int bannerWidth = 600;
            int bannerHeight = bannerWidth * menuBanner.getIconHeight() / menuBanner.getIconWidth();
            g.drawImage(menuBanner.getImage(), MENU_CENTER_X - bannerWidth / 2, 74,
                bannerWidth, bannerHeight, null);
        }

        int hit = panel != Panel.NONE ? 0 : menuHit(mouse);
        double playScale = hit == 1 ? 1.08 : 1.0;
        int playW = (int) (menuPlayRect.width * playScale);
        int playH = (int) (menuPlayRect.height * playScale);
        int playX = menuPlayRect.x + (menuPlayRect.width - playW) / 2;
        int playY = menuPlayRect.y + (menuPlayRect.height - playH) / 2;
        g.drawImage(menuPlay, playX, playY, playW, playH, null);

        // Tutorial / Practice / Exit - all clickable
        menuButton(g, tutorialRect, "Tutorial", true, hit == 6);
        menuButton(g, practiceRect, "Practice", true, hit == 7);
        menuButton(g, exitRect, "Exit", true, hit == 2);

        drawMenuIcon(g, menuHelp, menuHelpRect, hit == 8);
        drawMenuIcon(g, menuInfo, menuInfoRect, hit == 9);
        drawMenuIcon(g, menuSettings, menuSettingsRect, hit == 3);

        if (panel != Panel.NONE) {
            g.setTransform(base);
            g.setColor(new Color(0, 0, 0, 175)); g.fillRect(0, 0, w, h);
            g.translate(offX, offY); g.scale(scale, scale);
            int ph = menuHit(mouse);
            if (panel == Panel.SETTINGS) drawSettingsPanel(g, ph);
            else drawBigPanel(g, ph);
        }
        if (confirmingMenuExit) {
            g.setTransform(base);
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect(0, 0, w, h);
            g.translate(offX, offY); g.scale(scale, scale);
            drawMenuExitConfirmation(g);
        }
        g.setTransform(base);
    }

    void drawMenuExitConfirmation(Graphics2D g) {
        Rectangle dialog = new Rectangle(230, 220, 500, 260);
        g.setColor(new Color(16, 14, 34));
        g.fillRect(dialog.x, dialog.y, dialog.width, dialog.height);
        g.setColor(new Color(255, 241, 168));
        drawPixelBorder(g, dialog, 3);
        g.setFont(pixelFont(Font.BOLD, 38));
        centerText(g, "Exit to Desktop?", PW / 2, 290);
        g.setFont(pixelFont(Font.BOLD, 21));
        g.setColor(new Color(235, 230, 215));
        centerText(g, "Are you sure you want to quit?", PW / 2, 335);
        menuButton(g, menuExitCancelRect, "Cancel", true, menuExitCancelRect.contains(mouse));
        menuButton(g, menuExitConfirmRect, "Exit", true, menuExitConfirmRect.contains(mouse));
    }

    void drawSettingsPanel(Graphics2D g, int ph) {
        g.setColor(new Color(16, 14, 34)); g.fillRect(230, 165, 500, 410);
        g.setColor(new Color(255, 241, 168));
        drawPixelBorder(g, new Rectangle(230, 165, 500, 410), 3);
        g.setFont(pixelFont(Font.BOLD, 38));
        g.setColor(new Color(255, 241, 168)); centerText(g, "SETTINGS", PW / 2, 220);
        g.setFont(pixelFont(Font.BOLD, 26));
        g.setColor(Color.WHITE); g.drawString("Fullscreen", 270, 278);
        menuButton(g, fsRect, isFullscreen() ? "ON" : "OFF", true, ph == 4);
        g.setFont(pixelFont(Font.BOLD, 26));
        g.setColor(Color.WHITE); g.drawString("Mute", 270, 348);
        menuButton(g, muteRect, audio.isMuted() ? "ON" : "OFF", true, ph == 12);
        Font volumeLabelFont = pixelFont(Font.BOLD, 26);
        g.setFont(volumeLabelFont);
        FontMetrics volumeLabelMetrics = g.getFontMetrics();
        int sliderCenterY = volumeSliderRect.y + volumeSliderRect.height / 2;
        int volumeLabelY = sliderCenterY + (volumeLabelMetrics.getAscent() - volumeLabelMetrics.getDescent()) / 2;
        g.setColor(Color.WHITE); g.drawString("Music volume", 270, volumeLabelY);
        int trackY = sliderCenterY - 4;
        g.setColor(new Color(40, 34, 80));
        g.fillRoundRect(volumeSliderRect.x, trackY, volumeSliderRect.width, 8, 8, 8);
        int filledWidth = Math.round(volumeSliderRect.width * audio.getMusicVolume());
        g.setColor(new Color(255, 241, 168));
        g.fillRoundRect(volumeSliderRect.x, trackY, filledWidth, 8, 8, 8);
        int knobX = volumeSliderRect.x + filledWidth;
        g.setColor(new Color(255, 241, 168));
        g.fillOval(knobX - 9, trackY - 5, 18, 18);
        g.setColor(Color.WHITE);
        g.drawOval(knobX - 9, trackY - 5, 18, 18);
        g.setFont(pixelFont(Font.BOLD, 16));
        g.setColor(new Color(160, 160, 190)); centerText(g, "F11 or Alt+Enter toggles fullscreen", PW / 2, 460);
        menuButton(g, backRect, "BACK", true, ph == 5);
    }

    void drawBigPanel(Graphics2D g, int ph) {
        Rectangle p = panelRect;
        g.setColor(new Color(16, 14, 34)); g.fillRect(p.x, p.y, p.width, p.height);
        g.setColor(new Color(255, 241, 168));
        drawPixelBorder(g, p, 3);
        int tx = p.x + 40, tw = p.width - 80;
        Color gold = new Color(255, 241, 168), light = new Color(235, 230, 215), cyan = new Color(150, 230, 255);

        if (panel == Panel.TUTORIAL) {
            g.setFont(pixelFont(Font.BOLD, 36));
            g.setColor(gold); centerText(g, "TUTORIAL - " + TUT_TITLES[tutPage], PW / 2, 168);
            g.setFont(pixelFont(Font.BOLD, 14));
            g.setColor(new Color(160, 160, 190)); centerText(g, "Page " + (tutPage + 1) + " of " + TUT_TITLES.length, PW / 2, 192);
            if (tutPage < 3) {
                g.setFont(pixelFont(Font.BOLD, 20));
                g.setColor(light);
                drawWrapped(g, TUT_TEXT[tutPage], tx, 232, tw, 30);
                drawTutorialArt(g, tutPage);
            } else {
                int y = 230;
                for (int i = 0; i < 5; i++) {
                    String name = i < 4 ? ITEMS[i] : SEAL;
                    String desc = i < 4 ? ITEM_DESC[i] + "  [" + ITEM_KEY[i] + "]" : "Recovered at the end. It holds the final choice.";
                    drawItemIcon(g, i, tx, y - 26, 4, null);
                    g.setFont(pixelFont(Font.BOLD, 20)); g.setColor(gold); g.drawString(name, tx + 50, y - 8);
                    g.setFont(pixelFont(Font.BOLD, 16)); g.setColor(light); g.drawString(desc, tx + 50, y + 14);
                    y += 62;
                }
                g.setFont(pixelFont(Font.BOLD, 14));
                g.setColor(cyan); centerText(g, "Click an item in the top-right bar, or press its key, when choosing a cave.", PW / 2, 540);
            }
            if (tutPage > 0) menuButton(g, prevRect, "BACK", true, ph == 10);
            menuButton(g, closeRect, "CLOSE", true, ph == 5);
            menuButton(g, nextRect, tutPage < TUT_TITLES.length - 1 ? "NEXT" : "PRACTICE", true, ph == 11);
        } else if (panel == Panel.HELP) {
            g.setFont(pixelFont(Font.BOLD, 36));
            g.setColor(gold); centerText(g, "CONTROLS", PW / 2, 168);
            String[][] rows = {
                {"Click / SPACE / ENTER", "Advance the story text"},
                {"Y / N  or  click", "Answer yes / no questions"},
                {"Click a cave / 1-5", "Choose a cave entrance"},
                {"P  M  G  C", "Use Potion, Map, Grimoire, Charm"},
                {"Click item bar", "Use that item (when choosing)"},
                {"XInput: stick/D-pad, A/B", "Move/select/back; Start pauses"},
                {"F11 / Alt+Enter", "Toggle fullscreen"},
                {"Esc", "Pause / resume; close menu panels"}
            };
            int y = 225;
            for (String[] r : rows) {
                g.setFont(pixelFont(Font.BOLD, 19)); g.setColor(gold); g.drawString(r[0], tx, y);
                g.setFont(pixelFont(Font.BOLD, 18)); g.setColor(light); g.drawString(r[1], tx + 320, y);
                y += 44;
            }
            menuButton(g, closeRect, "CLOSE", true, ph == 5);
        } else {
            menuButton(g, aboutTabRect, "ABOUT", true,
                aboutTabRect.contains(mouse) || !aboutCreditsTab);
            menuButton(g, creditsTabRect, "CREDITS", true,
                creditsTabRect.contains(mouse) || aboutCreditsTab);
            if (aboutCreditsTab) drawCreditsPage(g, gold, light);
            else {
                g.setFont(pixelFont(Font.BOLD, 20));
                g.setColor(light);
                int y = 215;
                y = drawWrapped(g, "GOBLINS AND CAVERNS", tx, y, tw, 30) + 6;
                g.setColor(light);
                y = drawWrapped(g, "A forgetful mage wakes in a cavern and finds his belongings stolen. Follow the thief through dark cave "
                    + "entrances, recover five lost items, and uncover the truth behind the Curse of Mana.", tx, y, tw, 30) + 10;
                g.setColor(cyan);
                y = drawWrapped(g, "30 HP  -  1 Light Spell in rounds 1-2, 2 in rounds 3-4  -  usable items",
                    tx, y, tw, 30) + 10;
                g.setColor(new Color(170, 170, 200));
                g.setFont(pixelFont(Font.BOLD, 16));
                drawWrapped(g, "Made with Java Swing. All art is drawn in code. Runs in a 960 x 720 window or Fullscreen 1920 x 1080.", tx, y, tw, 26);
            }
            if (aboutCreditsTab && creditsPage > 0) menuButton(g, prevRect, "BACK", true, ph == 18);
            menuButton(g, closeRect, "CLOSE", true, ph == 5);
            if (aboutCreditsTab && creditsPage < 1) menuButton(g, nextRect, "MORE", true, ph == 19);
        }
    }

    void drawCreditsPage(Graphics2D g, Color gold, Color light) {
        Graphics2D content = (Graphics2D) g.create();
        content.translate(0, creditsPage == 0 ? 24 : 48);
        int bannerWidth = 240;
        int bannerHeight = bannerWidth * menuBanner.getIconHeight() / menuBanner.getIconWidth();
        content.drawImage(menuBanner.getImage(), (PW - bannerWidth) / 2, 176, bannerWidth, bannerHeight, null);
        content.setFont(pixelFont(Font.BOLD, 17));
        content.setColor(light);
        centerText(content, "Computer Programming 1 Final Project", PW / 2, 251);

        int leftX = 145, rightX = 505, colWidth = 310, y = 276;
        if (creditsPage == 0) {
            y = drawCreditSection(content, gold, light, "CORE CREATIVE & NARRATIVE", leftX, y, colWidth,
                "Original Game Concept: Aeron Kayl Briones",
                "Narrative & Story Writer: Joren Marie Angeles");
            drawCreditSection(content, gold, light, "PROGRAMMING & DEVELOPMENT", leftX, y + 5, colWidth,
                "GUI & Interface Developers: Charles Tria, Aeron Kayl Briones, Mary Grace Zosimo",
                "Terminal & Core Logic Developers: Joren Marie Angeles, Aeron Kayl Briones, Charles Tria, Mary Grace Zosimo");

            y = drawCreditSection(content, gold, light, "ART, AUDIO & PERFORMANCE", rightX, 276, colWidth,
                "Visual Artists: Mary Grace Zosimo, Dyna Brez, Charles Tria",
                "Voice Talent / Voice Over: Dyna Brez",
                "Sound Engineering & Audio Design: Joren Marie Angeles, Charles Tria");
            drawCreditSection(content, gold, light, "QUALITY ASSURANCE & PLAYTESTING", rightX, y + 5, colWidth,
                "Quality Assurance Lead: Aeron Kayl Briones",
                "Playtester: Gabriel Tejada");
        } else {
            drawCreditSection(content, gold, light, "ENGINE & LIBRARIES", leftX, y, colWidth,
                "Core Language & GUI Framework: Java (Java Swing)");
            drawCreditSection(content, gold, light, "ASSETS & ATTRIBUTIONS", leftX, y + 65, colWidth,
                "Icon Assets: Sourced via Pinterest",
                "Main Menu Theme / Audio: \"Home\" - The Longing OST",
                "Sound Effects (SFX): Sourced via YouTube",
                "Cave Ambience & Environmental Audio: Sourced via YouTube");
            drawCreditSection(content, gold, light, "ACADEMIC ACKNOWLEDGMENTS & MENTORSHIP", rightX, y, colWidth,
                "Course: Computer Programming 1",
                "Instructor & Project Mentor: Richard Kim Sanchez",
                "Department: College of Computer Studies");
        }
        content.dispose();
        g.setFont(pixelFont(Font.BOLD, 13));
        g.setColor(new Color(160, 160, 190));
        centerText(g, "CREDITS " + (creditsPage + 1) + " / 2", PW / 2, 535);
    }

    int drawCreditSection(Graphics2D g, Color gold, Color light, String heading, int x, int y, int width,
                          String... credits) {
        g.setFont(pixelFont(Font.BOLD, 15));
        g.setColor(gold);
        y = drawWrapped(g, heading, x, y, width, 18) + 2;
        g.setFont(pixelFont(Font.BOLD, 13));
        g.setColor(light);
        for (String credit : credits) y = drawWrapped(g, credit, x + 8, y, width - 8, 16) + 2;
        return y + 5;
    }

    void drawTutorialArt(Graphics2D g, int page) {
        int cy = 470;
        if (page == 0) {
            drawImageContain(g, goblinNotStartled, PW / 2 - 170, cy - 50, 100, 95);
            for (int i = 0; i < 3; i++) {
                int x = PW / 2 - 60 + i * 72;
                if (doorOne != null && doorOne.getIconWidth() > 0 && doorOne.getIconHeight() > 0) {
                    drawImageContain(g, doorOne, x, cy - 55, 62, 100);
                } else {
                    g.setColor(new Color(60, 54, 76));
                    g.fill(caveShape(x, cy - 55, 62, 100, 3, 40L + i));
                    g.setColor(new Color(6, 5, 10));
                    g.fill(caveShape(x, cy - 55, 62, 100, 0, 40L + i));
                }
            }
        } else if (page == 1) {
            for (int i = 0; i < 3; i++) {
                int ox = PW / 2 - 100 + i * 100;
                glow(g, ox, cy, 50, new Color(255, 241, 168), 160);
                g.setColor(new Color(255, 252, 215)); g.fillOval(ox - 10, cy - 10, 20, 20);
            }
        } else {
            drawImageContain(g, snakeCharacter, PW / 2 - 200, cy - 50, 130, 100);
            g.setColor(new Color(30, 30, 50)); g.fillRect(PW / 2 - 30, cy - 12, 240, 28);
            g.setColor(new Color(70, 190, 90)); g.fillRect(PW / 2 - 30, cy - 12, 160, 28);
            g.setColor(Color.WHITE);
            drawPixelBorder(g, new Rectangle(PW / 2 - 30, cy - 12, 240, 28), 1);
            g.setFont(pixelFont(Font.BOLD, 18));
            centerText(g, "-10 HP per snake", PW / 2 + 90, cy + 42);
        }
    }

    void drawImageContain(Graphics2D g, ImageIcon icon, int x, int y, int width, int height) {
        if (icon == null || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) return;
        double scale = Math.min(width / (double) icon.getIconWidth(),
            height / (double) icon.getIconHeight());
        int imageWidth = (int) Math.round(icon.getIconWidth() * scale);
        int imageHeight = (int) Math.round(icon.getIconHeight() * scale);
        g.drawImage(icon.getImage(), x + (width - imageWidth) / 2, y + (height - imageHeight) / 2,
            imageWidth, imageHeight, this);
    }

    void drawEndScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 175)); g.fillRect(0, 0, PW, PH);
        g.setFont(pixelFont(Font.BOLD, 56));
        g.setColor(new Color(255, 241, 168)); centerText(g, practice ? "PRACTICE COMPLETE" : "CAVERN CLEARED", PW / 2, 150);
        g.setFont(fText);
        if (practice) {
            g.setColor(new Color(150, 230, 255)); centerText(g, "You cleared all 5 rounds.", PW / 2, 200);
            g.setColor(new Color(245, 235, 210)); centerText(g, "HP left: " + hp + "/" + HP_MAX, PW / 2, 300);
            g.setFont(pixelFont(Font.BOLD, 30));
            g.setColor(new Color(255, 241, 168)); centerText(g, "Ready for the real thing?", PW / 2, 420);
        } else {
            g.setColor(new Color(150, 230, 255)); centerText(g, "OBJECTIVE COMPLETE: REMEMBER.", PW / 2, 200);
            g.setColor(new Color(245, 235, 210)); centerText(g, "Ending: " + endingName, PW / 2, 245);
            int x0 = PW / 2 - 5 * 56 / 2;
            for (int i = 0; i < 5; i++) {
                String name = i < 4 ? ITEMS[i] : SEAL;
                drawItemIcon(g, i, x0 + i * 56, 285, 5,
                    inventory.contains(name) ? null : new Color(50, 50, 70));
            }
            g.setColor(new Color(245, 235, 210));
            centerText(g, "Items recovered: " + inventory.size() + "/5", PW / 2, 370);
            centerText(g, "HP left: " + hp + "/" + HP_MAX, PW / 2, 405);
            g.setFont(pixelFont(Font.BOLD, 30));
            g.setColor(new Color(255, 241, 168)); centerText(g, "THANK YOU FOR PLAYING!", PW / 2, 480);
        }
        drawButton(g, againRect, practice ? "TRY AGAIN" : "PLAY AGAIN", "");
        drawButton(g, menuRect, "MAIN MENU", "");
    }

    void drawOverScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 200)); g.fillRect(0, 0, PW, PH);
        g.setFont(pixelFont(Font.BOLD, 72));
        g.setColor(new Color(220, 50, 50)); centerText(g, "GAME OVER", PW / 2, 280);
        g.setFont(fText);
        g.setColor(new Color(200, 200, 225)); centerText(g, "Your HP drops to zero. The darkness swallows the mage...", PW / 2, 340);
        drawButton(g, againRect, "TRY AGAIN", "");
        drawButton(g, menuRect, "MAIN MENU", "");
    }

    // =====================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("re:Memory");
            f.setIconImage(loadWindowIcon());
            GoblinDoorsGUI panel = new GoblinDoorsGUI();
            panel.window = f;
            f.setUndecorated(true);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            panel.windowRoot = new JPanel(new BorderLayout());
            panel.windowRoot.setBorder(BorderFactory.createEmptyBorder());
            panel.titleBar = panel.createTitleBar();
            panel.titleBar.setVisible(false);
            panel.windowRoot.add(panel.titleBar, BorderLayout.NORTH);
            panel.windowRoot.add(panel, BorderLayout.CENTER);
            f.setContentPane(panel.windowRoot);
            f.setResizable(true);
            f.addWindowStateListener(e -> panel.updateWindowChrome());
            panel.addWindowResizeHandlers(panel);
            f.pack();
            f.setMinimumSize(new Dimension(480, 360));
            f.setLocationRelativeTo(null);
            panel.windowedBounds = f.getBounds();
            panel.fullscreen = true;
            f.setExtendedState(JFrame.MAXIMIZED_BOTH);
            f.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}
