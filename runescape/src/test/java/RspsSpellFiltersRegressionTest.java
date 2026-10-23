/**
 * N72 (owner 2026-10-16 "magic spellbook needs to exact same as osrs style layout options"): the client half of the OSRS spell
 * filters (RspsSpellFilters) for every spell of every book - which spells the filters hide (category bits stay the cache's; lacks
 * level / runes / requirements and the open menu are decided here), how the decision reaches the cache layout script 2059, and how a
 * probe run of the spell redraw script 21 tells runes from requirements.
 */
public final class RspsSpellFiltersRegressionTest {
    private static int assertions;

    private static final int FIRE_SURGE = (192 << 16) | 91;
    private static final int ICE_BARRAGE = (193 << 16) | 23;
    private static final int VENGEANCE = (430 << 16) | 37;

    public static void main(String[] args) {
        reset();
        // Script 21 args: component, lit, dark, level, base-level flag, 4x rune + amount.
        RspsSpellFilters.enter(21, new int[] {FIRE_SURGE, 1, 2, 95, 0, 554, 10, 556, 7, 560, 1, 565, 1});
        RspsSpellFilters.enter(21, new int[] {ICE_BARRAGE, 1, 2, 94, 0, 555, 6, 565, 2, 560, 4, -1, 0});
        RspsSpellFilters.enter(21, new int[] {VENGEANCE, 1, 2, 94, 0, 557, 10, 9075, 4, 560, 2, -1, 0});
        check(RspsSpellFilters.args.size() == 3 && RspsSpellFilters.dirty.size() == 3, "every real spell redraw is remembered and marked for a probe");

        // Filtering off: nothing is hidden, whatever the menu says (OSRS: filters apply only once enabled).
        Static581.statLevels[6] = 1;
        Static498.statBaseLevels[6] = 1;
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_HIDE_LEVEL, RspsSpellFilters.BIT_HIDE_RUNES);
        check(!RspsSpellFilters.hidden(FIRE_SURGE), "filtering disabled hides nothing");

        // Lacks the Magic level (boosted level, base level when the spell's flag says so) - every book.
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_ENABLED, RspsSpellFilters.BIT_HIDE_LEVEL);
        check(RspsSpellFilters.hidden(FIRE_SURGE) && RspsSpellFilters.hidden(ICE_BARRAGE) && RspsSpellFilters.hidden(VENGEANCE),
            "a level-1 account: lacks-level off hides Fire Surge, Ice Barrage and Vengeance");
        Static581.statLevels[6] = 99;
        Static498.statBaseLevels[6] = 99;
        check(!RspsSpellFilters.hidden(FIRE_SURGE), "99 Magic: Fire Surge shows");
        Static581.statLevels[6] = 90;
        check(RspsSpellFilters.hidden(FIRE_SURGE), "a drained (boosted 90) level hides Fire Surge like the cache script's STAT check");
        Static581.statLevels[6] = 99;

        // Probe outcomes: lit marker = castable, dark after the rune counter = runes, dark without it = requirements.
        RspsSpellFilters.probing = true;
        RspsSpellFilters.probeRuneCount = false;
        RspsSpellFilters.probeOutcome = -1;
        RspsSpellFilters.enter(19, new int[] {554, FIRE_SURGE});
        check(RspsSpellFilters.probeRuneCount, "a rune-counter call inside the probe is seen");
        check(RspsSpellFilters.probeGraphic(21, RspsSpellFilters.PROBE_DARK), "the probe's graphic is swallowed (nothing drawn)");
        check(RspsSpellFilters.probeOutcome == RspsSpellFilters.LACKS_RUNES, "dark after the rune counter = lacks runes");
        RspsSpellFilters.probeOutcome = -1;
        RspsSpellFilters.probeRuneCount = false;
        RspsSpellFilters.probeGraphic(21, RspsSpellFilters.PROBE_DARK);
        check(RspsSpellFilters.probeOutcome == RspsSpellFilters.LACKS_REQUIREMENTS, "dark before any rune count = lacks requirements");
        RspsSpellFilters.probeOutcome = -1;
        RspsSpellFilters.probeGraphic(21, RspsSpellFilters.PROBE_LIT);
        check(RspsSpellFilters.probeOutcome == RspsSpellFilters.OK, "lit marker = castable");
        RspsSpellFilters.enter(21, new int[] {(192 << 16) | 99, 1, 2, 1, 0, -1, 0, -1, 0, -1, 0, -1, 0});
        check(!RspsSpellFilters.args.containsKey((192 << 16) | 99), "the probe's own script 21 run is not taken as real args");
        RspsSpellFilters.probing = false;
        check(!RspsSpellFilters.probeGraphic(21, 5), "outside a probe every graphic is applied");

        // Lacks runes / requirements filters.
        RspsSpellFilters.status.put(FIRE_SURGE, RspsSpellFilters.LACKS_RUNES);
        RspsSpellFilters.status.put(VENGEANCE, RspsSpellFilters.LACKS_REQUIREMENTS);
        RspsSpellFilters.status.put(ICE_BARRAGE, RspsSpellFilters.OK);
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_ENABLED, RspsSpellFilters.BIT_HIDE_RUNES);
        check(RspsSpellFilters.hidden(FIRE_SURGE) && !RspsSpellFilters.hidden(VENGEANCE) && !RspsSpellFilters.hidden(ICE_BARRAGE),
            "lacks-runes off hides only the spells the rune counter fails");
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_ENABLED, RspsSpellFilters.BIT_HIDE_REQUIREMENTS);
        check(!RspsSpellFilters.hidden(FIRE_SURGE) && RspsSpellFilters.hidden(VENGEANCE), "lacks-requirements off hides only those");
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_ENABLED);
        check(!RspsSpellFilters.hidden(FIRE_SURGE) && !RspsSpellFilters.hidden(VENGEANCE), "all shown: nothing hidden");
        RspsSpellFilters.panelOpen = true;
        check(RspsSpellFilters.hidden(ICE_BARRAGE), "the open filter menu replaces the whole grid");
        RspsSpellFilters.panelOpen = false;

        // Layout script 2059: the category lookup (enum 727) of a hidden spell forces the array read to "hidden".
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_ENABLED, RspsSpellFilters.BIT_HIDE_RUNES);
        int category = RspsSpellFilters.layoutCategory(2059, 727, FIRE_SURGE, -1);
        check(category == 0, "a hidden spell without a 667 category gets one, so the cache script reads its array");
        check(RspsSpellFilters.layoutArray(2059, 0) == 1, "... and that array read says hidden");
        check(RspsSpellFilters.layoutArray(2059, 0) == 0, "the next array read is the cache's own again");
        check(RspsSpellFilters.layoutCategory(2059, 727, ICE_BARRAGE, 3) == 3 && RspsSpellFilters.layoutArray(2059, 0) == 0,
            "a shown spell keeps its category and the 667 category filter decides");
        check(RspsSpellFilters.layoutCategory(2058, 727, FIRE_SURGE, 2) == 2, "other scripts' enum 727 lookups are untouched");
        check(RspsSpellFilters.layoutCategory(2059, 743, FIRE_SURGE, 1) == 1, "other enums in 2059 are untouched");
        check(Boolean.TRUE.equals(RspsSpellFilters.laidOut.get(FIRE_SURGE)), "the layout decision is remembered for the re-layout check");

        // OSRS menu texts (cache 2686 script 2603) and bits line up 1:1 with the server's SpellFilters actions 1-6.
        // N72 (owner 2026-10-22) added OSRS line 7, "Enable icon resizing (outside PvP areas)" (varbit 6548 -> bit 25).
        check(RspsSpellFilters.OPTIONS.length == 7 && RspsSpellFilters.OPTION_BITS.length == 7, "seven filter lines");
        check(RspsSpellFilters.OPTION_BITS[6] == RspsSpellFilters.BIT_ICON_RESIZING_OFF, "line 7 = icon resizing");
        check(RspsSpellFilters.OPTIONS[3].equals("Show spells you lack the Magic level to cast"), "OSRS text of the level filter");
        for (int i = 0; i < 6; i++) {
            check(RspsSpellFilters.OPTION_BITS[i] == RspsSpellFilters.BIT_HIDE_COMBAT + i, "line " + (i + 1) + " = server slot " + (i + 1) + " = bit " + (19 + i));
        }
        check(RspsSpellFilters.book(192)[1] == 97 && RspsSpellFilters.book(193)[1] == 54 && RspsSpellFilters.book(430)[1] == 66,
            "menu layers = book struct param 688 (735/736/737)");

        reset();
        System.out.println("RspsSpellFiltersRegressionTest OK (" + assertions + " assertions)");
    }

    private static int bits(int... b) {
        int v = 0;
        for (int i : b) {
            v |= 1 << i;
        }
        return v;
    }

    private static void reset() {
        RspsSpellFilters.args.clear();
        RspsSpellFilters.status.clear();
        RspsSpellFilters.laidOut.clear();
        RspsSpellFilters.dirty.clear();
        RspsSpellFilters.layoutSpells.clear();
        RspsSpellFilters.probing = false;
        RspsSpellFilters.panelOpen = false;
        RspsSpellFilters.forceHide = false;
        RspsSpellFilters.forcedVarp = Integer.MIN_VALUE;
    }

    private static void check(boolean condition, String what) {
        assertions++;
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
