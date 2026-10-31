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
        // Never the player's own prayer.properties / spellbook.properties: a fresh folder per run.
        System.setProperty("rsps.preferencesDir", new java.io.File(System.getProperty("java.io.tmpdir"), "rsps-filters-test-" + System.nanoTime()).getPath());
        // N72 (2026-10-31): OSRS script 2611's grid in the 184 x 240 top - the native thresholds (research report section 2).
        int[][] want = { // count -> columns, size (resizing on)
            {1, 3, 40}, {15, 3, 40}, {16, 4, 40}, {20, 4, 40}, {21, 4, 24}, {36, 4, 24}, {37, 5, 24}, {45, 5, 24}, {46, 6, 24}, {54, 6, 24}, {55, 7, 24}, {70, 7, 24},
        };
        for (int[] w : want) {
            int[] g = RspsSpellFilters.grid(w[0], 184, 240, true);
            check(g[0] == w[1] && g[2] == w[2], w[0] + " spells: " + g[0] + " columns of " + g[2] + " px, OSRS " + w[1] + " of " + w[2]);
            check(g[3] <= 5 * g[2] / 7 && g[4] <= g[3], w[0] + " spells: gaps " + g[3] + "," + g[4] + " within 5/7 of the icon and gapY <= gapX");
        }
        check(RspsSpellFilters.grid(28, 184, 240, false)[0] == 4 && RspsSpellFilters.grid(29, 184, 240, false)[0] == 4
            && RspsSpellFilters.grid(45, 184, 240, false)[0] == 5 && RspsSpellFilters.grid(70, 184, 240, false)[0] == 7, "resizing off: 4 columns up to 28, then (n + 8) / 9 within 4..7");
        // No overlap and nothing outside the book: every count of every 667 book (Standard 68, Lunar 52, Ancient 29) fits 184 x 240.
        for (int count = 1; count <= 70; count++) {
            for (boolean resize : new boolean[] {true, false}) {
                int[] g = RspsSpellFilters.grid(count, 184, 240, resize);
                int rowsUsed = g[1] * g[2] + (g[1] - 1) * g[4];
                check(g[5] <= 184 && rowsUsed <= 240, count + " spells (resize " + resize + "): grid " + g[5] + " x " + rowsUsed + " outside the 184 x 240 top");
                check(g[0] * g[1] >= count, count + " spells: " + g[0] + " x " + g[1] + " cells");
            }
        }
        reset();
        // Script 21 args: component, lit, dark, level, base-level flag, 4x rune + amount.
        RspsSpellFilters.enter(21, new int[] {FIRE_SURGE, 1, 2, 95, 0, 554, 10, 556, 7, 560, 1, 565, 1});
        RspsSpellFilters.enter(21, new int[] {ICE_BARRAGE, 1, 2, 94, 0, 555, 6, 565, 2, 560, 4, -1, 0});
        RspsSpellFilters.enter(21, new int[] {VENGEANCE, 1, 2, 94, 0, 557, 10, 9075, 4, 560, 2, -1, 0});
        check(RspsSpellFilters.args.size() == 3 && RspsSpellFilters.dirty.size() == 3, "every real spell redraw is remembered and marked for a probe");

        // N494 (owner 2026-10-31 20:5x): the filters work with filtering disabled too ("Enable" is only the editing mode).
        Static581.statLevels[6] = 1;
        Static498.statBaseLevels[6] = 1;
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_HIDE_LEVEL, RspsSpellFilters.BIT_HIDE_RUNES);
        check(RspsSpellFilters.hidden(FIRE_SURGE), "filtering disabled: lacks level crossed still hides Fire Surge at Magic 1");
        RspsSpellFilters.forcedVarp = 0;
        check(!RspsSpellFilters.hidden(FIRE_SURGE), "filtering disabled, every line ticked: nothing hidden");

        // N495: a spell the player hid stays (dimmed) while editing and leaves the book once filtering is disabled.
        RspsSpellbookEdit.isHidden(FIRE_SURGE); // loads the saved file first, so the test's own choice is not overwritten
        java.util.Map<Integer, java.util.Set<Integer>> savedHidden = new java.util.HashMap<>(RspsSpellbookEdit.hiddenSpells);
        RspsSpellbookEdit.hiddenSpells.clear();
        RspsSpellbookEdit.hiddenSpells.put(FIRE_SURGE >>> 16, new java.util.LinkedHashSet<>(java.util.Collections.singleton(FIRE_SURGE)));
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_ENABLED);
        check(!RspsSpellFilters.hidden(FIRE_SURGE), "editing (enabled): the hidden Fire Surge still shows");
        RspsSpellFilters.forcedVarp = 0;
        check(RspsSpellFilters.hidden(FIRE_SURGE) && !RspsSpellFilters.hidden(ICE_BARRAGE), "done (disabled): the hidden Fire Surge is gone, the rest stays");
        RspsSpellbookEdit.hiddenSpells.clear();
        RspsSpellbookEdit.hiddenSpells.putAll(savedHidden);

        // Lacks the Magic level (boosted level, base level when the spell's flag says so) - every book.
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_ENABLED, RspsSpellFilters.BIT_HIDE_LEVEL);
        check(RspsSpellFilters.hidden(FIRE_SURGE) && RspsSpellFilters.hidden(ICE_BARRAGE) && RspsSpellFilters.hidden(VENGEANCE),
            "a level-1 account: lacks-level off hides Fire Surge, Ice Barrage and Vengeance");
        Static581.statLevels[6] = 99;
        Static498.statBaseLevels[6] = 99;
        check(!RspsSpellFilters.hidden(FIRE_SURGE), "99 Magic: Fire Surge shows");
        Static581.statLevels[6] = 90;
        check(!RspsSpellFilters.hidden(FIRE_SURGE), "OSRS 2619: a drained 99 (current 90) still shows Fire Surge - base AND current must be below");
        Static581.statLevels[6] = 99;
        Static498.statBaseLevels[6] = 90;
        check(!RspsSpellFilters.hidden(FIRE_SURGE), "a boosted 90 -> 99 shows Fire Surge");
        Static581.statLevels[6] = 90;
        check(RspsSpellFilters.hidden(FIRE_SURGE), "base and current 90: Fire Surge (95) hidden");
        Static581.statLevels[6] = 99;
        Static498.statBaseLevels[6] = 99;

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
        // The two filters on their own (OSRS 2620 / 4131): a spell failing its requirements is asked about its runes separately.
        RspsSpellFilters.forcedVarp = bits(RspsSpellFilters.BIT_ENABLED, RspsSpellFilters.BIT_HIDE_RUNES);
        RspsSpellFilters.runesOk.put(VENGEANCE, false);
        check(RspsSpellFilters.hidden(VENGEANCE), "requirements failed AND no runes: hidden by lacks-runes alone");
        RspsSpellFilters.runesOk.put(VENGEANCE, true);
        check(!RspsSpellFilters.hidden(VENGEANCE), "requirements failed but runes in hand: lacks-runes does not hide it");
        RspsSpellFilters.runesOk.clear();
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
        check(RspsSpellFilters.OPTIONS.length == 7 && RspsSpellFilters.OPTION_BITS.length == 7, "seven OSRS filter lines; 'Show spells you have hidden' went with N495");
        check(RspsSpellFilters.OPTIONS[6].equals("Enable icon resizing"), "owner 2026-10-31: resizing everywhere, no '(outside PvP areas)'");
        check(RspsSpellFilters.layoutCategory(2059, 727, (430 << 16) | 27, 2) == 0 && RspsSpellFilters.layoutCategory(2059, 727, (430 << 16) | 31, 2) == 0
            && RspsSpellFilters.layoutCategory(2059, 727, (430 << 16) | 32, 2) == 0, "OSRS: Energy Transfer, Stat Spy and Dream are Combat spells");
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
        RspsSpellFilters.runesOk.clear();
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
