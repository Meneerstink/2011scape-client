/**
 * 78Legacy v4 "Bruine Bonen Technologia": what the prayers WILL be once the prayer clicks already sent reach the server.
 *
 * The client's prayer varbits only change when the server answers (next tick), so a script with several prayer steps in
 * one key press (OFF then ON, Piety then Protect then Piety...) and a fast second press must count the clicks that are
 * already on their way, including the prayers the server switches OFF because another one goes ON. Every prayer click
 * is counted here: script steps and single prayer keys (RspsPkHotkeys.prayerSlot) and the player's own mouse clicks in
 * the prayer book (MiniMenu.doAction). A prediction gives way to the real varbit as soon as the server confirms it, or
 * after [HOLD_MS]. Quick prayers change a set this client does not model: after an orb click the prayer steps of a script
 * wait for the server ([quickPending]). No timers, no retries: this only decides whether a step needs a click at all.
 *
 * Exclusion rules = the server's (Prayers.CLOSES for the normal book, AncientCurse.conflictsWith + Turmoil for curses),
 * indexed like RspsPkData (0-29 normal book, 30-49 curses). Client thread only.
 */
final class RspsPrayerModel {

    static final long HOLD_MS = 1200L;
    private static final int[] PREDICTED = new int[RspsPkData.PRAYER_IDS.length];
    private static final long[] AT = new long[RspsPkData.PRAYER_IDS.length];
    private static long quickAt;

    // Normal book groups (RspsPkData indices).
    private static final int[] ATT = {2, 7, 15}, STR = {1, 6, 14}, DEF = {0, 5, 13}, RNG = {3, 11, 20}, MAG = {4, 12, 21};
    private static final int[] ALL_ROUND = {25, 27, 28, 29};
    private static final int[] HEAL = {9, 26};
    private static final int[] PROTECTS = {17, 18, 19};
    private static final int[] SMITE_GROUP = {22, 23, 24};
    private static final int SUMMONING = 16;

    // Curses (RspsPkData indices): drains per sap / leech (A att, S str, D def, R rng, M mag, P spec, E run energy).
    private static final String[] CURSE_DRAINS = new String[50];
    private static final int TURMOIL = 49, DEFLECT_SUMMONING = 36, WRATH = 47, SOUL_SPLIT = 48, SAP_SPIRIT = 34,
        LEECH_ENERGY = 45, LEECH_SPECIAL = 46;
    private static final int[] DEFLECTS = {37, 38, 39};
    private static final int[] STYLE_SAPS = {31, 32, 33}, STYLE_LEECHES = {44, 41, 42};

    static {
        java.util.Arrays.fill(PREDICTED, -1);
        CURSE_DRAINS[31] = "ASD";
        CURSE_DRAINS[32] = "RD";
        CURSE_DRAINS[33] = "MD";
        CURSE_DRAINS[34] = "P";
        CURSE_DRAINS[40] = "A";
        CURSE_DRAINS[41] = "R";
        CURSE_DRAINS[42] = "M";
        CURSE_DRAINS[43] = "D";
        CURSE_DRAINS[44] = "S";
        CURSE_DRAINS[45] = "E";
        CURSE_DRAINS[46] = "P";
    }

    private RspsPrayerModel() {
    }

    /** PREDICTED value: a click went out whose effect this client cannot know (state() answers -1 meanwhile). */
    private static final int UNKNOWN = 2;

    /** Predicted state of prayer [i] (1 on, 0 off, -1 unknown). */
    static int state(int i) {
        if (i < 0 || i >= PREDICTED.length) return -1;
        if (PREDICTED[i] >= 0 && System.currentTimeMillis() - AT[i] < HOLD_MS) {
            return PREDICTED[i] == UNKNOWN ? -1 : PREDICTED[i];
        }
        return RspsPkHotkeys.varbit(RspsPkData.PRAYER_VARBIT[i]);
    }

    /** True while an orb click may still change prayers this client cannot predict. */
    static boolean quickPending() {
        return System.currentTimeMillis() - quickAt < HOLD_MS;
    }

    static void quickClicked() {
        quickAt = System.currentTimeMillis();
        java.util.Arrays.fill(PREDICTED, -1);
    }

    /**
     * InterfaceManager.ifButtonXSend, right where the packet goes out (Codex review v4-4 #1): a click on a prayer (271:8,
     * child = grid slot) or on the quick-prayer orb's toggle. Op 1 is the toggle of both; other ops (e.g. "Select quick
     * prayers") change nothing.
     */
    static void sent(int hash, int child, int op) {
        if (op != 1) return;
        if (hash == RspsPkHotkeys.QUICK_PRAYER_HASH) {
            quickClicked();
        } else if (hash == RspsPkHotkeys.PRAYER_HASH) {
            clicked(RspsScriptMenu.prayerAt(child));
        }
    }

    /** A click on prayer [i] went out (it flips the prayer; switching it ON also switches its conflicts OFF). */
    static void clicked(int i) {
        if (i < 0 || i >= PREDICTED.length) return;
        long t = System.currentTimeMillis();
        if (quickPending()) {
            set(i, UNKNOWN, t); // the orb's change is still on its way: no trusted guess (Codex review v4-4 #2)
            return;
        }
        int now = state(i);
        if (now == 1) {
            set(i, 0, t);
            return;
        }
        if (now < 0 || !eligible(i)) {
            // Unknown before, or the server may refuse it (level, quest, no points): no guessing about it or what it
            // would switch off (Codex review v4-4 #3).
            set(i, UNKNOWN, t);
            return;
        }
        for (int k = 0; k < PREDICTED.length; k++) {
            if (k != i && closes(i, k)) {
                set(k, 0, t);
            }
        }
        set(i, 1, t);
    }

    /** Prayer level needed per RspsPkData index (server Prayer.kt / AncientCurse.kt; Turmoil 95). */
    private static final int[] LEVEL = {1, 4, 7, 8, 9, 10, 13, 16, 19, 22, 25, 26, 27, 28, 31, 34, 35, 37, 40, 43, 44, 45, 46, 49,
        52, 60, 65, 70, 74, 77, 50, 50, 52, 54, 56, 59, 62, 65, 68, 71, 74, 76, 78, 80, 82, 84, 86, 89, 92, 95};
    private static final int PRAYER_SKILL = 5, DEFENCE_SKILL = 1;
    /** Server Prayers.KING_RANSOMS_QUEST_VARBIT (Chivalry / Piety need it at 8). */
    private static final int KNIGHT_WAVES_VARBIT = 3909;

    /** What this client can tell about the server's activation checks (prayer level, Chivalry/Piety needs, points). */
    static boolean eligible(int i) {
        try {
            if (Static498.statBaseLevels[PRAYER_SKILL] < LEVEL[i]) return false;
            if (i == 25 || i == 27) { // Chivalry 65 / Piety 70 Defence + King's Ransom Knight Waves
                if (Static498.statBaseLevels[DEFENCE_SKILL] < (i == 27 ? 70 : 65)) return false;
                if (RspsPkHotkeys.varbit(KNIGHT_WAVES_VARBIT) < 8) return false;
            }
            int points = com.jagex.game.runetek6.config.vartype.TimedVarDomain.instance == null ? -1
                : com.jagex.game.runetek6.config.vartype.TimedVarDomain.instance.getVarValueInt(RspsPkHotkeys.PRAYER_POINTS_VARP);
            return points != 0;
        } catch (RuntimeException unknown) {
            return false;
        }
    }

    private static void set(int i, int v, long t) {
        PREDICTED[i] = v;
        AT[i] = t;
    }

    /** Client thread, every cycle: predictions the server has confirmed (or that ran out) give way to the varbits. */
    static void reconcile() {
        long t = System.currentTimeMillis();
        for (int i = 0; i < PREDICTED.length; i++) {
            if (PREDICTED[i] < 0) continue;
            if (t - AT[i] >= HOLD_MS || (PREDICTED[i] != UNKNOWN && RspsPkHotkeys.varbit(RspsPkData.PRAYER_VARBIT[i]) == PREDICTED[i])) {
                PREDICTED[i] = -1;
            }
        }
    }

    /** True when switching [on] ON switches [other] OFF on the server. */
    static boolean closes(int on, int other) {
        if (on == other || RspsPkData.PRAYER_BOOK[on] != RspsPkData.PRAYER_BOOK[other]) return false;
        return on < 30 ? normalCloses(on, other) : curseCloses(on, other);
    }

    private static boolean in(int[] set, int v) {
        for (int s : set) if (s == v) return true;
        return false;
    }

    /** Server Prayers.CLOSES (Darkan PrayerManager.activatePrayer). */
    private static boolean normalCloses(int on, int o) {
        if (in(ATT, on)) return in(ATT, o) || in(RNG, o) || in(MAG, o) || in(ALL_ROUND, o);
        if (in(STR, on)) return in(STR, o) || in(RNG, o) || in(MAG, o) || in(ALL_ROUND, o);
        if (in(DEF, on)) return in(DEF, o) || in(ALL_ROUND, o);
        if (in(RNG, on) || in(MAG, on)) return in(ATT, o) || in(STR, o) || in(RNG, o) || in(MAG, o) || in(ALL_ROUND, o);
        if (in(ALL_ROUND, on)) return in(ATT, o) || in(STR, o) || in(RNG, o) || in(MAG, o) || in(DEF, o) || in(ALL_ROUND, o);
        if (in(HEAL, on)) return in(HEAL, o);
        if (in(PROTECTS, on)) return in(PROTECTS, o) || in(SMITE_GROUP, o);
        if (on == SUMMONING) return in(SMITE_GROUP, o);
        if (in(SMITE_GROUP, on)) return in(PROTECTS, o) || o == SUMMONING || in(SMITE_GROUP, o);
        return false; // Rapid Restore, Protect Item close nothing
    }

    private static String category(int i) {
        if (i == 30 || i == 35) return "FREE";
        if (i >= 31 && i <= 34) return "SAP";
        if (i >= 40 && i <= 46) return "LEECH";
        if (i == DEFLECT_SUMMONING) return "DS";
        if (in(DEFLECTS, i)) return "DC";
        if (i == WRATH) return "WRATH";
        if (i == SOUL_SPLIT) return "SS";
        return "TURMOIL";
    }

    private static boolean overhead(String c) {
        return c.equals("DC") || c.equals("DS") || c.equals("WRATH") || c.equals("SS");
    }

    private static boolean deflect(String c) {
        return c.equals("DC") || c.equals("DS");
    }

    private static boolean shareDrain(int a, int b) {
        String x = CURSE_DRAINS[a], y = CURSE_DRAINS[b];
        if (x == null || y == null) return false;
        for (char ch : x.toCharArray()) if (y.indexOf(ch) >= 0) return true;
        return false;
    }

    /** Server AncientCurse.conflictsWith + the Turmoil rules (AncientCurses: Turmoil vs Saps / Leeches). */
    private static boolean curseCloses(int on, int o) {
        String a = category(on), b = category(o);
        if (on == TURMOIL) return b.equals("SAP") || b.equals("LEECH");
        if (o == TURMOIL) return a.equals("SAP") || a.equals("LEECH");
        if ((on == SAP_SPIRIT && o == LEECH_ENERGY) || (on == LEECH_ENERGY && o == SAP_SPIRIT)) return true;
        if (on == SAP_SPIRIT && o == LEECH_SPECIAL) return true;
        if (on == LEECH_SPECIAL && o == SAP_SPIRIT) return false;
        if (a.equals("SAP") && b.equals("LEECH")) return shareDrain(on, o);
        if (a.equals("LEECH") && b.equals("SAP")) return shareDrain(on, o);
        if (in(STYLE_LEECHES, on) && in(STYLE_LEECHES, o)) return true;
        if (in(STYLE_SAPS, on) && in(STYLE_SAPS, o)) return true;
        if (a.equals("DC") && b.equals("DC")) return true;
        return overhead(a) && overhead(b) && !(a.equals("DC") && b.equals("DC")) && !(deflect(a) && deflect(b));
    }
}
