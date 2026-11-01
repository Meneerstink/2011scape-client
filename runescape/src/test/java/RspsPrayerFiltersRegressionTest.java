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

        quickSelectionHitTests();

        System.out.println("RspsPrayerFiltersRegressionTest: " + assertions + " assertions OK");
    }

    /** N519: real InterfaceManager hit testing, using the geometry emitted by cache scripts 1237 / 1388. */
    static void quickSelectionHitTests() {
        InterfaceList.interfaces = new Component[800][];
        InterfaceList.cache = new Component[800][];
        InterfaceList.loaded = new boolean[800];
        Static511.varcs = new int[1100];
        Mouse mouse = new Mouse();
        rs2.client.event.mouse.MouseMonitor.instance = mouse;
        for (int book = 0; book < 3; book++) {
            RspsPrayerFilters.forcedVarbits.put(RspsPrayerFilters.BOOK_VARBIT, book == 0 ? 0 : 1);
            RspsPrayerFilters.forcedVarbits.put(RspsPrayerFilters.RUINOUS_VARBIT, book == 2 ? 1 : 0);
            for (int points : new int[] {0, 99}) {
                Static581.statLevels[RspsPrayerFilters.PRAYER] = points;
                for (boolean filtering : new boolean[] {false, true}) {
                    for (boolean resizable : new boolean[] {false, true}) {
                        InterfaceManager.resizableScreen = resizable;
                        RspsPrayerFilters.forcedVarp = (filtering ? 1 << RspsPrayerFilters.BIT_ENABLED : 0)
                            | 1 << RspsPrayerFilters.BIT_BLOCK_HEALING | 1 << RspsPrayerFilters.BIT_BLOCK_LEVEL;
                        RspsPrayerFilters.order.put(book, list(RspsPrayerFilters.LEVELS[book].length - 1, 0));
                        RspsPrayerFilters.hiddenSlots.put(book, new java.util.LinkedHashSet<>(shown(book)));
                        RspsPrayerFilters.panelOpen = true;
                        Static511.varcs[RspsPrayerFilters.QUICK_VARC] = 1;
                        Component[] components = quickFixture(book);
                        RspsPrayerFilters.layout();
                        Component[] icons = InterfaceList.list(RspsPrayerFilters.ICONS).staticComponents;
                        Component[] cells = InterfaceList.list(RspsPrayerFilters.QUICK).staticComponents;
                        for (int slot = 0; slot < cells.length; slot++) {
                            Component icon = icons[slot];
                            check(InterfaceManager.getOp(icon, 0) == null, "1237 quick icon has no native op");
                            mouse.x = cells[slot].x + 8;
                            mouse.y = cells[slot].y + 8;
                            hit(components, mouse);
                            check(selectionEntries(slot) == 1, "the native checkbox remains selectable");
                            // The icon centre lies outside the native 15px checkbox: the old hit test produced only Cancel.
                            mouse.x = icon.x + 15;
                            mouse.y = icon.y + 2 + 15;
                            hit(components, mouse);
                            check(selectionEntries(slot) == 1, "icon centre selects 271:42 slot " + slot + " book=" + book
                                + " points=" + points + " filters=" + filtering + " resize=" + resizable);
                            cells[slot].ops[0] = "Deselect";
                            mouse.x = cells[slot].x + 8;
                            mouse.y = cells[slot].y + 8;
                            hit(components, mouse);
                            check(selectionEntries(slot) == 1, "checkbox/icon overlap has exactly one Deselect entry");
                            check(RspsPrayerFilters.quickMenuComponent(icon) == cells[slot], "dispatch uses the original checkbox hook and server events");
                            check(InterfaceManager.serverActiveProperties(cells[slot]).isOpEnabled(0), "server OP1 retained");
                        }
                        Static511.varcs[RspsPrayerFilters.QUICK_VARC] = 0; // Confirm returned to the ordinary book.
                        for (Component icon : icons) {
                            check(RspsPrayerFilters.quickMenuComponent(icon) == icon, "Confirm restores ordinary menu dispatch");
                        }
                        Static511.varcs[RspsPrayerFilters.QUICK_VARC] = 1;
                        InterfaceList.list(RspsPrayerFilters.QUICK).hidden = true;
                        check(RspsPrayerFilters.quickMenuComponent(icons[0]) == null, "a hidden quick grid cannot be clicked");
                    }
                }
            }
        }
    }

    static Component[] quickFixture(int book) {
        Component[] all = new Component[50];
        InterfaceList.interfaces[271] = all;
        InterfaceList.loaded[271] = true;
        for (int part : new int[] {4, 5, 7, 8, 42}) {
            Component layer = new Component();
            layer.type = Component.TYPE_LAYER;
            layer.slot = 271 << 16 | part;
            layer.layer = part == 4 ? -1 : 271 << 16 | (part == 7 || part == 8 ? 5 : 4);
            layer.originalWidth = layer.width = layer.scrollWidth = 190;
            layer.originalHeight = layer.height = layer.scrollHeight = 261;
            if (part == 5) layer.originalY = layer.y = 2;
            all[part] = layer;
        }
        Component icons = all[8], cells = all[42];
        icons.staticComponents = icons.dynamicComponents = new Component[RspsPrayerFilters.LEVELS[book].length];
        cells.staticComponents = cells.dynamicComponents = new Component[icons.staticComponents.length];
        for (int slot = 0; slot < cells.staticComponents.length; slot++) {
            Component icon = new Component(), cell = new Component();
            for (Component c : new Component[] {icon, cell}) {
                c.type = Component.TYPE_GRAPHIC;
                c.id = slot;
                c.hasHook = true;
                c.clickMask = false; // opaque icon centre; no renderer or sprite archive needed
            }
            icon.slot = icon.layer = icons.slot;
            icon.originalX = icon.x = 8 + slot % 5 * 37;
            icon.originalY = icon.y = 30 + slot / 5 * 35;
            icon.originalWidth = icon.width = icon.originalHeight = icon.height = 30;
            cell.slot = cell.layer = cells.slot;
            cell.originalX = cell.x = 6 + slot % 5 * 37;
            cell.originalY = cell.y = 28 + slot / 5 * 35;
            cell.originalWidth = cell.width = cell.originalHeight = cell.height = 15;
            cell.ops = new String[] {"Select"};
            cell.onOp = new Object[] {2290, cells.slot, slot};
            InterfaceManager.serverActiveProperties.put(((long) cell.slot << 32) + slot,
                new com.jagex.game.runetek6.config.iftype.ServerActiveProperties(2, -1));
            icons.staticComponents[slot] = icon;
            cells.staticComponents[slot] = cell;
        }
        return all;
    }

    static void hit(Component[] components, Mouse mouse) {
        MiniMenu.open = false;
        MiniMenu.reset();
        InterfaceManager.logicComponentList(components, -1, 0, 0, 190, 261, 0, 0, mouse.x, mouse.y, mouse.x, mouse.y);
    }

    static int selectionEntries(int slot) {
        int count = 0;
        for (MiniMenuEntryInner e = (MiniMenuEntryInner) MiniMenu.innerEntryQueue.first(); e != null;
             e = (MiniMenuEntryInner) MiniMenu.innerEntryQueue.next()) {
            if (e.v3 == RspsPrayerFilters.QUICK && e.v2 == slot && e.v1 == 1
                && (e.op.equals("Select") || e.op.equals("Deselect"))) count++;
        }
        return count;
    }

    static final class Mouse extends rs2.client.event.mouse.MouseMonitor {
        int x, y;
        public void record() { }
        public boolean isLeftDown() { return false; }
        public boolean isRightDown() { return false; }
        public boolean isMiddleDown() { return false; }
        public void remove() { }
        public rs2.client.event.mouse.MouseLog removeFirstLog() { return null; }
        public int getRecordedX() { return x; }
        public int getRecordedY() { return y; }
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
