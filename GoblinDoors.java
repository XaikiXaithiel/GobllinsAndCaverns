import java.util.*;

/**
 * GOBLIN DOORS - Dungeon of Paths
 * Console text-adventure. Pure Java (java.util only).
 * Compile: javac GoblinDoors.java
 * Run:     java GoblinDoors
 */
public class GoblinDoors {

    static final Scanner in = new Scanner(System.in);
    static final Random rng = new Random();

    // ---------- Game state ----------
    static int mana = 100;
    static final List<String> inventory = new ArrayList<>();
    static final List<String> recovered = new ArrayList<>();

    // ---------- Item data (index = round - 1) ----------
    static final String[] ITEMS = {"Mana Potion", "Map of the Village", "Grimoire", "Old Charm"};
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

    enum PathType { ITEM, TRAP, DEAD_END }

    // ---------- Helpers ----------
    static void pause(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void say(String s) { System.out.println(s); pause(700); }
    static void sys(String s) { System.out.println("  [" + s + "]"); pause(450); }
    static void line() { System.out.println("------------------------------------------"); }

    static boolean askYesNo(String question) {
        sys(question);
        sys("YES/NO");
        while (true) {
            System.out.print("> ");
            String s = in.nextLine().trim().toLowerCase();
            if (s.equals("y") || s.equals("yes")) return true;
            if (s.equals("n") || s.equals("no")) return false;
            System.out.println("  Please type YES or NO.");
        }
    }

    /** Asks for a path number among the still-open paths. */
    static int askPath(String question, boolean[] open) {
        sys(question);
        StringBuilder opts = new StringBuilder("OPTIONS: ");
        for (int i = 0; i < 3; i++) if (open[i]) opts.append(roman(i)).append(" ");
        sys(opts.toString().trim());
        while (true) {
            System.out.print("> ");
            String s = in.nextLine().trim().toUpperCase();
            int idx = -1;
            switch (s) {
                case "I": case "1": idx = 0; break;
                case "II": case "2": idx = 1; break;
                case "III": case "3": idx = 2; break;
            }
            if (idx >= 0 && open[idx]) return idx;
            System.out.println("  Invalid or closed path. Try again.");
        }
    }

    static String roman(int i) { return new String[]{"I", "II", "III"}[i]; }

    static void showMana() {
        sys("MANA: " + mana + "/100");
    }

    static void gameOver() {
        line();
        say("Your mana runs dry. The darkness swallows the mage...");
        sys("GAME OVER");
        System.exit(0);
    }

    static void loseMana(int amt) {
        mana = Math.max(0, mana - amt);
        showMana();
        if (mana == 0) gameOver();
    }

    // ---------- Story parts ----------
    static void intro() {
        line();
        System.out.println("          G O B L I N   D O O R S");
        line();
        say("A mage travels through the forest, hoping to reach a village before night falls.");
        say("Someone there is said to know how to lift his curse - the [Curse of Mana].");
        say("The curse weakens his magic. He avoids casting unless absolutely necessary.");
        say("");
        say("But after hours of walking, he still can't find the way out. Night has come.");
        say("He finds a cave and sets down his things:");
        inventory.addAll(Arrays.asList("Mana Potion", "Map of the Village", "Grimoire", "Old Charm", "Curse's Seal"));
        for (String it : inventory) sys(it);
        say("He rests... until his eyes grow heavy.");
        say("");
        say("*Rustle.*");
        say("His eyes snap open. His belongings are gone!");
        inventory.clear();
        say("Luckily, the thief was careless. Small footprints lead deeper into the cave.");
        System.out.println();
        say("The deeper he goes, the darker it becomes.");
        say("He casts \"Lumen\". A faint light appears above his palm.");
        say("\"I can't keep doing this,\" he mutters.");
        loseMana(5);
        say("The footprints suddenly disappear. The cave splits into three paths, each leading into darkness.");
        System.out.println();
        sys("A traveler has entered the Dungeon of Paths");
        sys("Objective: Retrieve the stolen belongings.");
        sys("Rule: Choose ONE path at every junction.");
        sys("Warning: Not every path leads to the thief.");
        say("\"...What?\"");
    }

    /** One normal round. Returns after the item is found. */
    static void playRound(int round) {
        line();
        sys("ROUND " + round);
        int spells = 2;
        sys("VISION SPELLS: " + spells + "/2");
        if (round == 2) sys("WARNING: Your memories may affect your choices.");
        if (round == 1) sys("Tip: Use your magic wisely.");

        // Randomly assign path types: exactly one item, one trap, one dead end
        PathType[] paths = {PathType.ITEM, PathType.TRAP, PathType.DEAD_END};
        for (int i = paths.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            PathType t = paths[i]; paths[i] = paths[j]; paths[j] = t;
        }
        boolean[] open = {true, true, true};
        boolean found = false;

        while (!found) {
            // --- Vision phase ---
            boolean chosen = false;
            while (spells > 0 && !chosen) {
                if (!askYesNo("Use Vision Spell?")) break;
                int v = askPath("Which path would you like to uncover?", open);
                spells--;
                loseMana(5);
                sys("Vision Spell Used");
                sys("Remaining Spells: " + spells + "/2");
                switch (paths[v]) {
                    case ITEM:     say("The darkness fades. You glimpse something glinting on the floor of path " + roman(v) + "..."); break;
                    case TRAP:     say("The darkness fades. Path " + roman(v) + " is full of whispering, hungry shadows..."); break;
                    case DEAD_END: say("The darkness fades. Path " + roman(v) + " ends in a solid wall of stone..."); break;
                }
                say("The vision fades little by little.");
                sys("VISION ENDED");
                if (askYesNo("Would you like to choose the path you'll walk on?")) chosen = true;
                else if (spells > 0) sys("Remaining Vision Spells: " + spells + "/2");
            }
            if (spells == 0 && !chosen) sys("No Vision Spells left. You must choose.");

            // --- Walk phase ---
            int p = askPath("Which path would you like to walk on?", open);
            say("He walks through tunnel " + roman(p) + "...");
            switch (paths[p]) {
                case ITEM:
                    found = true;
                    String item = ITEMS[round - 1];
                    inventory.add(item);
                    say("He finds his item on the floor: " + item + "!");
                    sys("One item recovered");
                    sys((5 - inventory.size()) + " items remaining.");
                    say("");
                    say("Upon touching it, a memory flashes through his mind...");
                    say(MEMORIES[round - 1]);
                    say(MEMORY_NOTES[round - 1]);
                    break;
                case TRAP:
                    say("Shadows claw at him! His mana is drained, and he stumbles back to the junction.");
                    loseMana(20);
                    open[p] = false;
                    break;
                case DEAD_END:
                    say("A dead end. He wastes energy turning back to the junction.");
                    loseMana(10);
                    open[p] = false;
                    break;
            }
        }
    }

    static void finalRound() {
        line();
        say("Each round leads him deeper into the cave.");
        say("With every item he retrieves, another piece of his forgotten past returns.");
        say("");
        say("Until finally, he reaches the deepest part of the dungeon.");
        say("The Thief is waiting, and beside it lies the Curse's Seal.");
        say("\"Give that back,\" the mage says.");
        say("The thief doesn't move. Then it suddenly vanishes.");
        System.out.println();
        sys("THE FINAL ROUND");
        sys("VISION SPELLS: 1/1");
        sys("One final path available");
        askYesNo("Use the last Vision Spell?");   // answer doesn't matter: there is only one path
        say("He uses the last vision spell.");
        say("The darkness disappears, but he only sees himself...");
        say("Not who he is now, but someone from the past.");
        System.out.println();
        sys("The Forgotten Memory");
        say("He remembers. He had once been a powerful mage.");
        say("So confident, he attempted a spell far beyond his control. It went wrong.");
        say("Someone important to him was caught in the aftermath.");
        say("He remembers reaching out. He remembers screaming. He remembers the light. Then nothing.");
        say("");
        say("The guilt was unbearable. He couldn't accept what he had done.");
        say("So he created the Curse of Mana himself, sealing away his own magic.");
        say("To protect himself, he also sealed the memories around the incident.");
        say("He forgot that he had cursed himself... and began to believe someone else had.");
        say("His journey had never been about finding a cure.");
        say("It was about finding someone to undo a curse he had chosen to create.");
        inventory.add("Curse's Seal");
    }

    static void finalChoice() {
        line();
        sys("The Final Choice");
        sys("TRUTH IDENTIFIED.");
        sys("THE CURSE OF MANA WAS SELF-IMPOSED.");
        sys("MEMORY RESTORED.");
        boolean remove = askYesNo("WOULD YOU LIKE TO REMOVE THE CURSE?");
        say("He looks at the Curse's Seal. For years he wanted nothing more than to be rid of it.");
        say("But now he understands why it existed.");
        say("For the first time, he doesn't need the screen to tell him what to do. He chooses.");
        System.out.println();
        sys("CHOICE ACCEPTED.");
        say("The screen disappears. The dungeon becomes silent.");
        say("He takes the Curse's Seal in his hand.");
        System.out.println();
        if (remove) {
            say("[ENDING: Forgiveness]");
            say("He breaks the seal. Mana floods back, warm and steady.");
            say("He will carry the guilt, but this time he will protect people, just as he promised.");
        } else {
            say("[ENDING: Atonement]");
            say("He closes his fist around the seal and keeps the curse.");
            say("Not as a punishment he forgot, but as a weight he chooses to carry knowingly.");
        }
        System.out.println();
        say("He turns toward the exit. Before he completely leaves the cave, one last message appears:");
        System.out.println();
        sys("DUNGEON CLEARED.");
        sys("OBJECTIVE COMPLETE: REMEMBER.");
        line();
        System.out.println("Items recovered: " + inventory);
        System.out.println("Mana left: " + mana + "/100");
        System.out.println("THANK YOU FOR PLAYING!");
    }

    // ---------- Main ----------
    public static void main(String[] args) {
        intro();
        for (int r = 1; r <= 4; r++) playRound(r);
        finalRound();
        finalChoice();
        in.close();
    }
}
