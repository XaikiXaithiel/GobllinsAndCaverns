import java.util.Scanner;

public class GoblinDoors {

     public static void main(String[] args) {
        intro();
        for (int r = 1; r <= 5; r++) {
            if (r == 5) finalIntro();
            playRound(r);
        }
        finalMemory();
        finalChoice();
        in.close();
    }
    
    static Scanner in = new Scanner(System.in);
    
    static int hp = 3;
    static String[] inventory = new String[5];  
    static int itemCount = 0;

    static final int ITEM = 0;
    static final int TRAP = 1;

    static String[] ITEMS = {"Mana Potion", "Map of the Village", "Grimoire", "Old Charm", "Curse's Seal"};
    static String[] MEMORIES = {
        "A voice echoes: \"Promise me you'll use your magic to protect people.\"",
        "The village you are traveling to... you have been there before.",
        "The Grimoire shows you a spell you once used. Your hands remember it.",
        "The Old Charm was given to you by your master. You remember their smile.",
        "The Seal pulses in his hand. The darkness around him fades, and he only sees himself..."
    };
    static String[] MEMORY_NOTES = {
        "The potion reminds you of the days your magic began disappearing.",
        "Why would you forget a place you once visited?",
        "That spell was far too strong...",
        "\"Be careful, my student. Power is not the same as control.\"",
        "Not who he is now, but someone from the past."
    };

    static void pause(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    static void say(String s) {
        System.out.println(s);
        pause(700);
    }

    static void sys(String s) {
        System.out.println("  [" + s + "]");
        pause(450);
    }

    static void line() {
        System.out.println("------------------------------------------");
    }

    static void addItem(String item) {
        inventory[itemCount] = item;
        itemCount++;
    }

    static void printInventory() {
        System.out.print("[");
        for (int i = 0; i < itemCount; i++) {
            System.out.print(inventory[i]);
            if (i < itemCount - 1) {
                System.out.print(", ");
            }
        }
        System.out.println("]");
    }

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

    static String roman(int i) {
        switch (i) {
            case 0: return "I";
            case 1: return "II";
            case 2: return "III";
            case 3: return "IV";
            default: return "V";
        }
    }

    static int askPath(String question, boolean[] open) {
        sys(question);
        String opts = "OPTIONS:";
        for (int i = 0; i < open.length; i++) {
            if (open[i]) opts = opts + " " + roman(i);
        }
        sys(opts);
        while (true) {
            System.out.print("> ");
            String s = in.nextLine().trim().toUpperCase();
            int idx = -1;
            switch (s) {
                case "I": case "1": idx = 0; break;
                case "II": case "2": idx = 1; break;
                case "III": case "3": idx = 2; break;
                case "IV": case "4": idx = 3; break;
                case "V": case "5": idx = 4; break;
            }
            if (idx >= 0 && idx < open.length && open[idx]) return idx;
            System.out.println("  Invalid or closed path. Try again.");
        }
    }

    static void showHp() {
        sys("HP: " + hp + "/3");
    }

    static void gameOver() {
        line();
        say("His strength fails. The darkness swallows the mage...");
        sys("GAME OVER");
        System.exit(0);
    }

    static void loseHp() {
        hp--;
        showHp();
        if (hp == 0) gameOver();
    }

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
        addItem("Mana Potion");
        addItem("Map of the Village");
        addItem("Grimoire");
        addItem("Old Charm");
        addItem("Curse's Seal");
        for (int i = 0; i < itemCount; i++) sys(inventory[i]);
        say("He rests... until his eyes grow heavy.");
        say("");
        say("*Rustle.*");
        say("His eyes snap open. His belongings are gone!");
        itemCount = 0;
        say("Luckily, the thief was careless. Small footprints lead deeper into the cave.");
        System.out.println();
        say("The deeper he goes, the darker it becomes.");
        say("He casts \"Lumen\". A faint light appears above his palm.");
        say("\"I can't keep doing this,\" he mutters.");
        say("The footprints suddenly disappear. The cave splits into paths, each leading into darkness.");
        System.out.println();
        sys("A traveler has entered the Dungeon of Paths");
        sys("Objective: Retrieve the stolen belongings.");
        sys("Rule: Choose ONE path at every junction.");
        sys("Warning: Only one path leads to the thief's loot. Wrong paths cost 1 HP.");
        showHp();
        say("\"...What?\"");
    }

    static int[] makePaths(int count) {
        int[] paths = new int[count];
        for (int i = 0; i < count; i++) {
            paths[i] = TRAP;
        }
        paths[0] = ITEM;
        for (int i = count - 1; i > 0; i--) {
            int j = (int) (Math.random() * (i + 1));
            int t = paths[i];
            paths[i] = paths[j];
            paths[j] = t;
        }
        return paths;
    }

    static void playRound(int round) {
   
        int pathCount = 3;
        int maxSpells = 1;
        if (round >= 3) {
            pathCount = 5;
            maxSpells = 2;
        }
        int spells = maxSpells;

        line();
        sys("ROUND " + round);
        sys("PATHS: " + pathCount);
        sys("VISION SPELLS: " + spells + "/" + maxSpells);
        showHp();
        if (round == 2) sys("WARNING: Your memories may affect your choices.");
        if (round == 1) sys("Tip: Use your magic wisely.");

        
        int[] paths = makePaths(pathCount);
        boolean[] open = new boolean[pathCount];
        for (int i = 0; i < pathCount; i++) {
            open[i] = true;
        }
        boolean found = false;

        while (!found) {
            boolean chosen = false;
            while (spells > 0 && !chosen) {
                if (!askYesNo("Use Vision Spell?")) break;
                int v = askPath("Which path would you like to uncover?", open);
                spells--;
                sys("Vision Spell Used");
                sys("Remaining Spells: " + spells + "/" + maxSpells);
                switch (paths[v]) {
                    case ITEM:
                        say("The darkness fades. You glimpse something glinting on the floor of path " + roman(v) + "...");
                        break;
                    case TRAP:
                        say("The darkness fades. Path " + roman(v) + " is full of hissing, hungry snakes...");
                        break;
                }
                say("The vision fades little by little.");
                sys("VISION ENDED");
                if (askYesNo("Would you like to choose the path you'll walk on?")) {
                    chosen = true;
                } else if (spells > 0) {
                    sys("Remaining Vision Spells: " + spells + "/" + maxSpells);
                }
            }
            if (spells == 0 && !chosen) sys("No Vision Spells left. You must choose.");

            int p = askPath("Which path would you like to walk on?", open);
            say("He walks through tunnel " + roman(p) + "...");
            switch (paths[p]) {
                case ITEM:
                    found = true;
                    String item = ITEMS[round - 1];
                    addItem(item);
                    say("He finds his item on the floor: " + item + "!");
                    sys("One item recovered");
                    sys((5 - itemCount) + " items remaining.");
                    say("");
                    say("Upon touching it, a memory flashes through his mind...");
                    say(MEMORIES[round - 1]);
                    say(MEMORY_NOTES[round - 1]);
                    break;
                case TRAP:
                    say("The snake strikes at him! He is hurt, and stumbles back to the junction.");
                    open[p] = false;
                    loseHp();
                    break;
            }
        }
    }

    static void finalIntro() {
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
    }

    static void finalMemory() {
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
            say("He breaks the seal. Magic floods back, warm and steady.");
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
        System.out.print("Items recovered: ");
        printInventory();
        System.out.println("HP left: " + hp + "/3");
        System.out.println("THANK YOU FOR PLAYING!");
    }

   
}
