import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * N482 (owner 2026-10-31): the OSRS prayer filters (RspsPrayerFilters.passes, OSRS rev241 prayer_passfilter 3309) on our three books -
 * the truth table of the research report (team\dossiers\N482-ONDERZOEK-PRAYERFILTER.txt section 9) and the owner's choices: Saps and
 * Leeches never go by tier, Rapid Renewal is Rapid Healing, Ruinous only by level / its three OSRS tier links.
 */
public final class RspsPrayerFiltersRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        // Never the player's own prayer.properties / spellbook.properties: a fresh folder per run.
        System.setProperty("rsps.preferencesDir", new java.io.File(System.getProperty("java.io.tmpdir"), "rsps-filters-test-" + System.nanoTime()).getPath());
        Map<Integer, Integer> unlocked = new HashMap<>();
        unlocked.put(RspsPrayerFilters.KNIGHT_WAVES_VARBIT, 8);
        unlocked.put(RspsPrayerFilters.RIGOUR_VARBIT, 1);
        unlocked.put(RspsPrayerFilters.AUGURY_VARBIT, 1);
        RspsPrayerFilters.forcedVarbits = unlocked;
        levels(99, 99);
        int N = RspsPrayerFilters.NORMAL;
        int C = RspsPrayerFilters.CURSES;
        int R = RspsPrayerFilters.RUINOUS;

        set();
        check(shown(N).size() == 30 && shown(C).size() == 20 && shown(R).size() == 24, "no line crossed: every prayer of every book");
        set(RspsPrayerFilters.BIT_ALLOW_COMBINED);
        check(shown(N).size() == 30, "the second line alone hides nothing (OSRS 2758: it only works with the first)");

        set(RspsPrayerFilters.BIT_BLOCK_LOWER);
        check(shown(N).size() == 19, "99 / everything unlocked, lower tiers crossed: 19 of 30 (report 9.2 #4), got " + shown(N));
        check(!shown(N).contains(25) && shown(N).contains(27), "Chivalry goes for a usable Piety, Piety stays");
        check(shown(N).contains(13) && shown(N).contains(14) && shown(N).contains(15), "Steel Skin / Ultimate Strength / Incredible Reflexes stay next to the multi-skill prayers");
        set(RspsPrayerFilters.BIT_BLOCK_LOWER, RspsPrayerFilters.BIT_ALLOW_COMBINED);
        check(shown(N).size() == 14, "both tier lines crossed: 14 of 30, got " + shown(N));
        set(RspsPrayerFilters.BIT_BLOCK_LOWER, RspsPrayerFilters.BIT_ALLOW_COMBINED, RspsPrayerFilters.BIT_BLOCK_HEALING);
        check(shown(N).equals(list(10, 16, 17, 18, 19, 22, 23, 24, 27, 28, 29)), "and Rapid Healing crossed: the 11 of report 9.2 #4, got " + shown(N));

        // A higher tier only counts when it can be used.
        unlocked.put(RspsPrayerFilters.RIGOUR_VARBIT, 0);
        set(RspsPrayerFilters.BIT_BLOCK_LOWER, RspsPrayerFilters.BIT_ALLOW_COMBINED);
        check(shown(N).contains(20) && !shown(N).contains(21), "no Rigour scroll: Eagle Eye stays, Mystic Might goes for Augury");
        set(RspsPrayerFilters.BIT_BLOCK_LOCKED);
        check(!shown(N).contains(28) && shown(N).contains(29) && shown(N).size() == 29, "lacks requirements crossed: only the locked Rigour goes");
        unlocked.put(RspsPrayerFilters.RIGOUR_VARBIT, 1);
        unlocked.put(RspsPrayerFilters.KNIGHT_WAVES_VARBIT, 7);
        set(RspsPrayerFilters.BIT_BLOCK_LOWER);
        check(shown(N).contains(25), "no Knight Waves: Piety is locked, so Chivalry is not a lower tier of anything usable");
        set(RspsPrayerFilters.BIT_BLOCK_LOCKED);
        check(!shown(N).contains(25) && !shown(N).contains(27), "no Knight Waves + lacks requirements crossed: Chivalry and Piety go");
        unlocked.put(RspsPrayerFilters.KNIGHT_WAVES_VARBIT, 8);
        levels(99, 69);
        check(!shown(N).contains(27) && !shown(N).contains(28) && !shown(N).contains(29) && shown(N).contains(25), "Defence 69: Piety / Rigour / Augury locked, Chivalry (65) not");
        levels(99, 99);

        // Prayer level (base, never the points).
        levels(20, 99);
        set(RspsPrayerFilters.BIT_BLOCK_LEVEL);
        check(shown(N).equals(list(0, 1, 2, 3, 4, 5, 6, 7, 8)), "Prayer 20, lacks level crossed: Thick Skin .. Rapid Restore (report 9.2 #5), got " + shown(N));
        check(shown(C).isEmpty() && shown(R).isEmpty(), "Prayer 20: nothing of the Curses / Ruinous");
        levels(40, 99);
        set(RspsPrayerFilters.BIT_BLOCK_LOWER);
        check(shown(N).contains(11) && shown(N).contains(12) && !shown(N).contains(3), "Prayer 40: Hawk Eye / Mystic Lore stay (Eagle Eye 44 not usable), Sharp Eye goes");
        levels(99, 99);

        // Curses: Saps and Leeches stay whatever the tier lines say (owner 2026-10-31 18:0x).
        set(RspsPrayerFilters.BIT_BLOCK_LOWER, RspsPrayerFilters.BIT_ALLOW_COMBINED, RspsPrayerFilters.BIT_BLOCK_HEALING);
        check(shown(C).size() == 20, "Curses: the tier and healing lines hide nothing");
        // Ruinous: the OSRS cache's three links and Rejuvenation as Rapid Healing.
        set(RspsPrayerFilters.BIT_BLOCK_LOWER);
        List<Integer> r = shown(R);
        check(r.size() == 21 && !r.contains(1) && !r.contains(2) && !r.contains(3), "Ruinous 99, lower tiers crossed: only Ancient Strength / Sight / Will go");
        set(RspsPrayerFilters.BIT_BLOCK_HEALING);
        check(shown(R).size() == 23 && !shown(R).contains(0), "Ruinous Rapid Healing crossed: only Rejuvenation goes");
        levels(84, 99);
        set(RspsPrayerFilters.BIT_BLOCK_LOWER);
        check(shown(R).contains(3) && !shown(R).contains(1) && !shown(R).contains(2), "Ruinous 84: Vaporise (86) not usable, Ancient Will stays");

        // N494 / N495 (owner 2026-10-31 20:5x): the five lines work with filtering disabled; a hidden prayer shows while editing
        // (enabled) and is gone once filtering is disabled, the order stays.
        levels(99, 99);
        RspsPrayerFilters.load(); // the saved file first, so the test's own choices are not overwritten
        Map<Integer, List<Integer>> savedOrder = new HashMap<>(RspsPrayerFilters.order);
        Map<Integer, java.util.Set<Integer>> savedHidden = new HashMap<>(RspsPrayerFilters.hiddenSlots);
        RspsPrayerFilters.order.clear();
        RspsPrayerFilters.hiddenSlots.clear();
        RspsPrayerFilters.forcedVarp = 1 << RspsPrayerFilters.BIT_BLOCK_HEALING;
        check(!RspsPrayerFilters.shownSlots(N, 30, false).contains(9), "disabled: Rapid Healing crossed still hides Rapid Heal");
        RspsPrayerFilters.forcedVarp = 0;
        RspsPrayerFilters.hiddenSlots.put(N, new java.util.LinkedHashSet<>(list(0, 5)));
        RspsPrayerFilters.order.put(N, list(29, 28));
        List<Integer> editing = RspsPrayerFilters.shownSlots(N, 30, true);
        List<Integer> done = RspsPrayerFilters.shownSlots(N, 30, false);
        check(editing.size() == 30 && editing.get(0) == 29 && editing.get(1) == 28 && editing.contains(0), "editing: all 30, Augury / Rigour first, hidden Thick Skin still there");
        check(done.size() == 28 && done.get(0) == 29 && !done.contains(0) && !done.contains(5), "done: Thick Skin and Rock Skin gone, the order stays");
        check(RspsPrayerFilters.shownSlots(C, 20, false).size() == 20, "the other books keep their own layout");
        RspsPrayerFilters.reset(N);
        check(RspsPrayerFilters.shownSlots(N, 30, false).equals(shown(N)) && RspsPrayerFilters.shownSlots(N, 30, false).get(0) == 0, "N497 reset: the cache order, nothing hidden");
        RspsPrayerFilters.order.clear();
        RspsPrayerFilters.hiddenSlots.clear();
        RspsPrayerFilters.order.putAll(savedOrder);
        RspsPrayerFilters.hiddenSlots.putAll(savedHidden);
        RspsPrayerFilters.save();

        System.out.println("RspsPrayerFiltersRegressionTest: " + assertions + " assertions OK");
    }

    static void levels(int prayer, int defence) {
        Static498.statBaseLevels[RspsPrayerFilters.PRAYER] = prayer;
        Static498.statBaseLevels[RspsPrayerFilters.DEFENCE] = defence;
    }

    static void set(int... bits) {
        int v = 1 << RspsPrayerFilters.BIT_ENABLED;
        for (int b : bits) {
            v |= 1 << b;
        }
        RspsPrayerFilters.forcedVarp = v;
    }

    static List<Integer> shown(int book) {
        List<Integer> out = new ArrayList<>();
        for (int slot = 0; slot < RspsPrayerFilters.LEVELS[book].length; slot++) {
            if (RspsPrayerFilters.passes(book, slot)) {
                out.add(slot);
            }
        }
        return out;
    }

    static List<Integer> list(int... v) {
        List<Integer> out = new ArrayList<>();
        for (int i : v) {
            out.add(i);
        }
        return out;
    }

    static void check(boolean ok, String what) {
        assertions++;
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
