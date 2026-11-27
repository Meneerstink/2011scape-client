import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 78Legacy v4 "Bruine Bonen Technologia" (user 2026-10-12, layout version C, after the UG Adv. Swapper screenshots): the
 * scripts. Hotkey N runs script N: an ordered list of steps (wear items or a gear set, prayers on/off/toggle, a spell or an
 * attack on the last target, special, eat, drink...). One key press = the clicks a player makes for those steps, in that
 * order; nothing runs without a key press and nothing reacts to the opponent.
 *
 * Storage: Java Preferences node /78legacy/scripts (client only, like the gear sets): script.N.name, script.N.on,
 * script.N.steps = "TYPE[:ARG];TYPE[:ARG]..." e.g. "WEAR:4151;PRAYON:N_PIETY;ATTACK". Thread rules as RspsGearSets: the
 * Preferences calls are thread-safe; listeners run on the calling thread and must hop to Swing themselves.
 */
final class RspsScripts {

    static final int COUNT = 15;
    /** A script never holds more steps than this. */
    static final int MAX_STEPS = 24;
    /** Above this many steps the clicks may not fit in one game tick (server: 30 packets per tick, incl. mouse moves). */
    static final int WARN_STEPS = 20;

    // ---- step types -----------------------------------------------------------------------------------------------------
    static final String WEAR = "WEAR";        // arg: item id
    static final String SET = "SET";          // arg: gear set 1-10
    static final String PRAY_ON = "PRAYON";   // arg: RspsPkData prayer id
    static final String PRAY_OFF = "PRAYOFF"; // arg: prayer id
    static final String PRAY_TOGGLE = "PRAYTOG"; // arg: prayer id
    static final String QUICK = "QUICK";      // quick prayers orb
    static final String CAST = "CAST";        // arg: RspsPkData spell id, cast on the last target
    static final String SELECT = "SELECT";    // arg: spell id, only select it
    static final String ATTACK = "ATTACK";    // attack the last target
    static final String STEP = "STEP";        // step under the last target
    static final String SPEC = "SPEC";        // special attack bar
    static final String EAT = "EAT";
    static final String BREW = "BREW";
    static final String RESTORE = "RESTORE";
    static final String VENG = "VENG";

    /** Every type in the order of the "+ Step" list, with its display name. */
    static final String[] TYPES = {WEAR, PRAY_ON, PRAY_OFF, PRAY_TOGGLE, QUICK, CAST, SELECT, ATTACK, STEP, SPEC, EAT, BREW, RESTORE, VENG};
    static final String[] TYPE_NAMES = {"Wear item", "Prayer ON", "Prayer OFF", "Prayer toggle", "Quick prayers",
        "Cast spell", "Select spell", "Attack target", "Step under target", "Special attack", "Eat", "Drink brew", "Drink restore", "Vengeance"};

    /** What the argument of a type is: "item", "set", "prayer", "spell" or "" (no argument). */
    static String argKind(String type) {
        if (WEAR.equals(type)) return "item";
        if (SET.equals(type)) return "set";
        if (PRAY_ON.equals(type) || PRAY_OFF.equals(type) || PRAY_TOGGLE.equals(type)) return "prayer";
        if (CAST.equals(type) || SELECT.equals(type)) return "spell";
        return "";
    }

    /** Colours like UG's right-click menu: wear yellow, prayer red (toggle orange), spells blue, target orange, rest green. */
    static int colour(String type) {
        if (WEAR.equals(type) || SET.equals(type)) return 0xFFFF00;
        if (PRAY_ON.equals(type) || PRAY_OFF.equals(type)) return 0xFF4040;
        if (PRAY_TOGGLE.equals(type)) return 0xFF8C3A;
        if (CAST.equals(type) || SELECT.equals(type)) return 0x66CCFF;
        if (ATTACK.equals(type) || STEP.equals(type)) return 0xFF9900;
        return 0x7FD36B;
    }

    /** One step. Immutable. */
    static final class Step {
        final String type;
        final String arg;

        Step(String type, String arg) {
            this.type = type;
            this.arg = arg == null ? "" : arg;
        }

        /** The verb shown in colour, e.g. "Wear", "Prayer ON", "Cast". */
        String verb() {
            if (WEAR.equals(type) || SET.equals(type)) return "Wear";
            if (PRAY_ON.equals(type)) return "Prayer ON";
            if (PRAY_OFF.equals(type)) return "Prayer OFF";
            if (PRAY_TOGGLE.equals(type)) return "Prayer toggle";
            if (QUICK.equals(type)) return "Quick prayers";
            if (CAST.equals(type)) return "Cast";
            if (SELECT.equals(type)) return "Select";
            if (ATTACK.equals(type)) return "Attack";
            if (STEP.equals(type)) return "Step under";
            if (SPEC.equals(type)) return "Special";
            if (EAT.equals(type)) return "Eat";
            if (BREW.equals(type)) return "Drink";
            if (RESTORE.equals(type)) return "Drink";
            if (VENG.equals(type)) return "Vengeance";
            return type;
        }

        /** The rest of the line in white, e.g. "Abyssal whip", "Augury", "Ice Barrage -> target", "" . */
        String object() {
            if (WEAR.equals(type)) {
                int id = intArg();
                return id < 0 ? "?" : RspsGearSets.itemName(id);
            }
            if (SET.equals(type)) {
                int n = intArg();
                int count = n >= 1 && n <= RspsGearSets.COUNT ? RspsGearSets.items(n).length : 0;
                return n >= 1 && n <= RspsGearSets.COUNT ? RspsGearSets.name(n) + " (" + count + (count == 1 ? " item)" : " items)") : "?";
            }
            if ("prayer".equals(argKind(type))) {
                int i = RspsPkData.prayer(arg);
                return i < 0 ? "?" : RspsPkData.PRAYER_NAMES[i];
            }
            if (CAST.equals(type)) {
                int i = RspsPkData.spell(arg);
                return (i < 0 ? "?" : RspsPkData.SPELL_NAMES[i]) + " -> target";
            }
            if (SELECT.equals(type)) {
                int i = RspsPkData.spell(arg);
                return i < 0 ? "?" : RspsPkData.SPELL_NAMES[i];
            }
            if (ATTACK.equals(type) || STEP.equals(type)) return "target";
            if (BREW.equals(type)) return "Saradomin brew";
            if (RESTORE.equals(type)) return "restore";
            return "";
        }

        String label() {
            String o = object();
            return o.isEmpty() ? verb() : verb() + " " + o;
        }

        int intArg() {
            try {
                return Integer.parseInt(arg.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        boolean valid() {
            String kind = argKind(type);
            if (indexOf(TYPES, type) < 0) return false;
            if ("item".equals(kind)) return RspsGearSets.validId(intArg());
            if ("set".equals(kind)) return intArg() >= 1 && intArg() <= RspsGearSets.COUNT;
            if ("prayer".equals(kind)) return RspsPkData.prayer(arg) >= 0;
            if ("spell".equals(kind)) return RspsPkData.spell(arg) >= 0;
            return true;
        }

        String encode() {
            return arg.isEmpty() ? type : type + ":" + arg;
        }

        static Step decode(String s) {
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            int c = s.indexOf(':');
            Step step = c < 0 ? new Step(s.toUpperCase(), "") : new Step(s.substring(0, c).toUpperCase(), s.substring(c + 1).trim());
            return step.valid() ? step : null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Step && ((Step) o).type.equals(type) && ((Step) o).arg.equals(arg);
        }

        @Override
        public int hashCode() {
            return type.hashCode() * 31 + arg.hashCode();
        }

        @Override
        public String toString() {
            return encode();
        }
    }

    // ---- templates ------------------------------------------------------------------------------------------------------
    /** {name, steps}. Gear lines are added by the player (right-click setup or "+ Step"), except the G maul ones. */
    static final String[][] TEMPLATES = {
        {"G maul double spec", "WEAR:22720;SPEC;SPEC;ATTACK"},
        {"G maul spec", "WEAR:4153;SPEC;ATTACK"},
        {"Mage (Ice Barrage)", "PRAYON:N_AUGURY;CAST:ICE_BARRAGE"},
        {"Range", "PRAYON:N_RIGOUR;ATTACK"},
        {"Melee", "PRAYON:N_PIETY;ATTACK"},
        {"Special attack", "SPEC;ATTACK"},
        {"Prayer toggle (Smite)", "PRAYTOG:N_SMITE"},
        {"Eat + brew", "EAT;BREW"},
    };

    static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userRoot().node("/78legacy/scripts");
    private static final List<Runnable> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<Runnable>();

    private RspsScripts() {
    }

    static String name(int n) {
        String s = PREFS.get("script." + n + ".name", "");
        return s.isEmpty() ? "Script " + n : s;
    }

    /** The name the player gave ("" = none), for "Hotkey 4 · G maul". */
    static String customName(int n) {
        return PREFS.get("script." + n + ".name", "");
    }

    static void setName(int n, String name) {
        String clean = name == null ? "" : name.replaceAll("[\\r\\n;|]", " ").trim();
        if (clean.length() > 20) clean = clean.substring(0, 20);
        PREFS.put("script." + n + ".name", clean);
        flushAndNotify();
    }

    static boolean enabled(int n) {
        return PREFS.getBoolean("script." + n + ".on", true);
    }

    static void setEnabled(int n, boolean on) {
        PREFS.putBoolean("script." + n + ".on", on);
        flushAndNotify();
    }

    static synchronized List<Step> steps(int n) {
        return parse(PREFS.get("script." + n + ".steps", ""));
    }

    static synchronized void setSteps(int n, List<Step> steps) {
        PREFS.put("script." + n + ".steps", encode(steps));
        flushAndNotify();
    }

    /** Replaces script [n] only if it still holds [expected] (an editor's copy): false = it changed meanwhile. */
    static boolean replace(int n, List<Step> expected, List<Step> steps) {
        synchronized (RspsScripts.class) {
            if (!parse(PREFS.get("script." + n + ".steps", "")).equals(expected)) {
                return false;
            }
            PREFS.put("script." + n + ".steps", encode(steps));
        }
        flushAndNotify();
        return true;
    }

    /** Adds one step at the end (right-click setup, "+ Step"); false when the script is full or the step is invalid. */
    static boolean add(int n, Step step) {
        synchronized (RspsScripts.class) {
            List<Step> list = steps(n);
            if (step == null || !step.valid() || list.size() >= MAX_STEPS) {
                return false;
            }
            list.add(step);
            PREFS.put("script." + n + ".steps", encode(list));
        }
        flushAndNotify();
        return true;
    }

    static void wipe(int n) {
        setSteps(n, Collections.<Step>emptyList());
    }

    /** Puts template [t] into script [n] (replaces its steps; the name becomes the template's when it had none). */
    static void applyTemplate(int n, int t) {
        if (t < 0 || t >= TEMPLATES.length) return;
        if (customName(n).isEmpty()) {
            PREFS.put("script." + n + ".name", TEMPLATES[t][0].length() > 20 ? TEMPLATES[t][0].substring(0, 20) : TEMPLATES[t][0]);
        }
        setSteps(n, parse(TEMPLATES[t][1]));
    }

    static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    static List<Step> parse(String s) {
        List<Step> out = new ArrayList<Step>();
        if (s == null) return out;
        for (String part : s.split(";")) {
            Step step = Step.decode(part);
            if (step != null && SET.equals(step.type)) {
                // GEARSWITCHER-WEG 27-11 (owner): no more gear sets in scripts - an old 'Wear gear set' step becomes the Wear item
                // steps of that set, so a saved script keeps switching the same gear (stored as Wear steps on the next save).
                int n = step.intArg();
                if (n >= 1 && n <= RspsGearSets.COUNT) {
                    for (int id : RspsGearSets.items(n)) if (out.size() < MAX_STEPS) out.add(new Step(WEAR, Integer.toString(id)));
                }
                continue;
            }
            if (step != null && out.size() < MAX_STEPS) out.add(step);
        }
        return out;
    }

    static String encode(List<Step> steps) {
        StringBuilder sb = new StringBuilder();
        int k = 0;
        for (Step s : steps) {
            if (s == null || !s.valid() || k >= MAX_STEPS) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(s.encode());
            k++;
        }
        return sb.toString();
    }

    /** One grey line for a folded view: "Wear 3 items · Prayer ON Augury · Cast Ice Barrage -> target". */
    static String summary(int n) {
        List<Step> list = steps(n);
        if (list.isEmpty()) return "Empty";
        StringBuilder sb = new StringBuilder();
        int wear = 0;
        for (Step s : list) {
            if (WEAR.equals(s.type)) {
                wear++;
                continue;
            }
            if (sb.length() > 0) sb.append(" · ");
            sb.append(s.label());
        }
        String gear = wear == 0 ? "" : "Wear " + wear + (wear == 1 ? " item" : " items");
        return gear.isEmpty() ? sb.toString() : sb.length() == 0 ? gear : gear + " · " + sb;
    }

    static boolean isMigrated() {
        return PREFS.getBoolean("migrated.v4", false);
    }

    static void markMigrated() {
        PREFS.putBoolean("migrated.v4", true);
        flushAndNotify();
    }

    static void flushAndNotify() {
        try {
            PREFS.flush();
        } catch (java.util.prefs.BackingStoreException e) {
            System.out.println("[SCRIPTS] could not store: " + e);
        }
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // a broken listener must not break the game loop
            }
        }
    }

    static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }
}
