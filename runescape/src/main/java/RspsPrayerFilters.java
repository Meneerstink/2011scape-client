import com.jagex.ClientProt;
import com.jagex.game.runetek6.config.vartype.TimedVarDomain;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Toolkit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * N482 (owner 2026-10-31: "we moeten de prayer filtering perfect krijgen", for all three books - Normal 30, Ancient Curses 20,
 * Ruinous Powers 24): OSRS prayer filtering on the 667 prayer tab (interface 271), plus RuneLite-style Hide / Unhide and moving.
 *
 * OSRS (Wiki "Prayer" > Prayer filtering; osrs-dumps rev241 scripts 907 / 547 / 3154 / 3156 / 3158 / 3308 / 3309, interface 541):
 * <ul>
 *   <li>the prayer tab stone's right-click "Enable prayer filtering" / "Disable prayer filtering" (548:135 fixed, 746:45 resizable);</li>
 *   <li>while enabled a "Filters" button in the bottom row (the level on the left, the button on the right, script 3154) opens the
 *       "Prayer Filters" menu in the tab: the five "Show ..." lines (green tick = shown, red cross = hidden; the second line is
 *       grey and without effect while the first is ticked, script 2758);</li>
 *   <li>the prayers that pass take the first places of the grid in order, no gaps, the icons keep their size (script 547);</li>
 *   <li>the quick-prayer selection is never filtered (script 466).</li>
 * </ul>
 * Owner's own additions (N482.txt DEFINITIEF; N494 / N495 / N497 owner 2026-10-31 20:5x "Ja, zo"):
 * <ul>
 *   <li>the Filters button is ALWAYS there (not in the quick-prayer selection) and its five lines always work;</li>
 *   <li>"Enable prayer filtering" = EDITING: every prayer the filters let through shows, the ones the player hid half see-through,
 *       right-click "Hide" / "Unhide" and Shift + drag to move (RuneLite PrayerReorder);</li>
 *   <li>"Disable prayer filtering" = DONE: the hidden prayers leave the grid, the player's order stays;</li>
 *   <li>right-click on the prayer tab stone "Reset prayer layout": the open book's order and hidden prayers back to the cache's.</li>
 * </ul>
 *
 * State: the five OSRS choices and the master switch are the server's (varp 1376 bits 26-31, saved with the account, sent by
 * IF_BUTTON1 on 271:4); hidden prayers, order and "Show hidden prayers" are kept on this machine (prayer.properties beside
 * spellbook.properties), like RuneLite keeps its plugin config.
 *
 * The grid stays the cache's (scripts 1237 icons into 271:8, 1293 glows into 271:7, child id = the prayer's slot): this class
 * only moves and hides those children after every build, so every click, tooltip and glow still belongs to the prayer's own slot.
 */
public final class RspsPrayerFilters {

    static final int IFACE = 271;
    static final int ROOT = (IFACE << 16) | 4;
    static final int GRID = (IFACE << 16) | 5;
    static final int SCROLLBAR = (IFACE << 16) | 6;
    static final int GLOWS = (IFACE << 16) | 7;
    static final int ICONS = (IFACE << 16) | 8;
    static final int STATS = (IFACE << 16) | 9;
    static final int LEVEL_ICON = (IFACE << 16) | 1;
    static final int LEVEL_TEXT = (IFACE << 16) | 2;
    static final int QUICK = (IFACE << 16) | 42;

    static final int TAB_FIXED = (548 << 16) | 135;
    static final int TAB_RESIZABLE = (746 << 16) | 45;

    static final int VARP = 1376;
    static final int BIT_ENABLED = 26;
    /** F1-F5 (OSRS varbits 6574-6578): set = the "Show ..." line is crossed. */
    static final int BIT_BLOCK_LOWER = 27;
    static final int BIT_ALLOW_COMBINED = 28;
    static final int BIT_BLOCK_HEALING = 29;
    static final int BIT_BLOCK_LEVEL = 30;
    static final int BIT_BLOCK_LOCKED = 31;

    static final int BOOK_VARBIT = 6840;
    static final int RUINOUS_VARBIT = 9930;
    static final int QUICK_VARC = 181;
    static final int KNIGHT_WAVES_VARBIT = 3909;
    static final int RIGOUR_VARBIT = 5451;
    static final int AUGURY_VARBIT = 5452;
    static final int DEFENCE = 1;
    static final int PRAYER = 5;

    static final int NORMAL = 0;
    static final int CURSES = 1;
    static final int RUINOUS = 2;
    static final String[] BOOK_NAMES = {"normal", "curses", "ruinous"};

    static final int ACTION_TAB = 1980;
    static final int ACTION_FILTERS = 1981;
    static final int ACTION_OPTION = 1982;
    static final int ACTION_HIDE = 1983;
    static final int ACTION_RESET = 1984;
    private static final int LOW = 2000;

    /** OSRS script 3156 lines (menu slots 1-5). The owner's "Show prayers you have hidden" went with N495 (Enable = editing). */
    static final String[] OPTIONS = {
        "Show lower tiers of tiered prayers",
        "Show tiered prayers even if multi-skill prayers are available",
        "Show <col=ffffff>Rapid Healing</col> prayers",
        "Show prayers you lack the Prayer level to activate",
        "Show prayers you lack the requirements to activate",
    };
    static final int[] OPTION_BITS = {BIT_BLOCK_LOWER, BIT_ALLOW_COMBINED, BIT_BLOCK_HEALING, BIT_BLOCK_LEVEL, BIT_BLOCK_LOCKED};

    // ------------------------------------------------------------------------------------------------- the three books

    /** Prayer level per slot (the server's Prayer / AncientCurse / RuinousPrayer tables; OSRS param 1753). */
    static final int[][] LEVELS = {
        {1, 4, 7, 8, 9, 10, 13, 16, 19, 22, 25, 26, 27, 28, 31, 34, 35, 37, 40, 43, 44, 45, 46, 49, 52, 60, 65, 70, 74, 77},
        {50, 50, 52, 54, 56, 59, 62, 65, 68, 71, 74, 76, 78, 80, 82, 84, 86, 89, 92, 95},
        {60, 61, 62, 63, 65, 66, 67, 69, 71, 72, 74, 75, 77, 78, 80, 82, 84, 86, 87, 88, 89, 90, 91, 92},
    };

    /**
     * The next tier (OSRS param 1760; -1 none). Normal: the OSRS chains on our 30 (Thick Skin > Rock Skin > Steel Skin > Chivalry >
     * Piety, the same for Strength / Attack; Eagle Eye > Rigour and Mystic Might > Augury as we have no Deadeye / Mystic Vigour).
     * Curses: none (owner 2026-10-31 18:0x: Saps and Leeches stay). Ruinous: the OSRS cache's three (Ancient Strength > Decimate,
     * Ancient Sight > Annihilate, Ancient Will > Vaporise).
     */
    static final int[][] NEXT = {
        {5, 6, 7, 11, 12, 13, 14, 15, -1, -1, -1, 20, 21, 25, 25, 25, -1, -1, -1, -1, 28, 29, -1, -1, -1, 27, -1, -1, -1, -1},
        {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},
        {-1, 15, 16, 17, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},
    };

    /** OSRS param 1759, the multi-skill class: Chivalry, Piety, Rigour, Augury. */
    static boolean multi(int book, int slot) {
        return book == NORMAL && (slot == 25 || slot == 27 || slot == 28 || slot == 29);
    }

    /** OSRS param 1758, "Rapid Healing": Rapid Restore, Rapid Heal and (owner 2026-10-31) Rapid Renewal; Ruinous Rejuvenation. */
    static boolean healing(int book, int slot) {
        return book == NORMAL ? slot == 8 || slot == 9 || slot == 26 : book == RUINOUS && slot == 0;
    }

    /**
     * Whether the player lacks a requirement other than the Prayer level (OSRS "lacks the requirements"), only those that exist
     * here (owner 2026-10-31): Chivalry / Piety the Knight Waves (varbit 3909 = 8, server Prayers.knightWavesRefusal) and Defence 65 /
     * 70; Rigour / Augury their scroll (5451 / 5452, server PrayerScrolls) and Defence 70. The Curses are unlocked before the book can
     * be chosen; Ruinous has none yet (N490 will add Desert Treasure II + the book here).
     */
    static boolean lacksRequirements(int book, int slot) {
        if (book != NORMAL) {
            return false;
        }
        int defence = Static498.statBaseLevels[DEFENCE];
        switch (slot) {
            case 25:
                return varbit(KNIGHT_WAVES_VARBIT) < 8 || defence < 65;
            case 27:
                return varbit(KNIGHT_WAVES_VARBIT) < 8 || defence < 70;
            case 28:
                return varbit(RIGOUR_VARBIT) == 0 || defence < 70;
            case 29:
                return varbit(AUGURY_VARBIT) == 0 || defence < 70;
            default:
                return false;
        }
    }

    static boolean lacksLevel(int book, int slot) {
        return Static498.statBaseLevels[PRAYER] < LEVELS[book][slot];
    }

    /** OSRS script 10154: a prayer the player could use (base level + requirements; points and boosts do not count). */
    static boolean available(int book, int slot) {
        return !lacksLevel(book, slot) && !lacksRequirements(book, slot);
    }

    /** OSRS prayer_passfilter (rev241 script 3309) on our books. */
    static boolean passes(int book, int slot) {
        if (bit(BIT_BLOCK_HEALING) && healing(book, slot)) {
            return false;
        }
        if (bit(BIT_BLOCK_LEVEL) && lacksLevel(book, slot)) {
            return false;
        }
        if (bit(BIT_BLOCK_LOCKED) && lacksRequirements(book, slot)) {
            return false;
        }
        if (bit(BIT_BLOCK_LOWER)) {
            boolean combined = bit(BIT_ALLOW_COMBINED);
            int next = NEXT[book][slot];
            for (int guard = 0; guard < 10 && next != -1; guard++) {
                if ((combined || multi(book, next) == multi(book, slot)) && available(book, next)) {
                    return false;
                }
                next = NEXT[book][next];
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------ state

    static int forcedVarp = Integer.MIN_VALUE;

    static int varp() {
        if (forcedVarp != Integer.MIN_VALUE) {
            return forcedVarp;
        }
        TimedVarDomain vars = TimedVarDomain.instance;
        return vars == null || VARP >= vars.varValues.length ? 0 : vars.varValues[VARP];
    }

    static boolean bit(int b) {
        return (varp() & (1 << b)) != 0;
    }

    /** Tests: varbit values instead of the live ones. */
    static Map<Integer, Integer> forcedVarbits;

    static int varbit(int id) {
        if (forcedVarbits != null) {
            return forcedVarbits.getOrDefault(id, 0);
        }
        return TimedVarDomain.instance == null ? 0 : TimedVarDomain.instance.getVarBitValue(id);
    }

    /** "Enable prayer filtering" = the editing mode (N495): hidden prayers shown half see-through, Hide / Unhide, moving. */
    static boolean enabled() {
        return bit(BIT_ENABLED);
    }

    static int book() {
        if (varbit(BOOK_VARBIT) == 0) {
            return NORMAL;
        }
        return varbit(RUINOUS_VARBIT) == 1 ? RUINOUS : CURSES;
    }

    static boolean quickSelect() {
        return Static511.varcs != null && QUICK_VARC < Static511.varcs.length && Static511.varcs[QUICK_VARC] != 0;
    }

    /** Menu line [i] shows the tick (the line's bit is clear). */
    static boolean optionTicked(int i) {
        return !bit(OPTION_BITS[i]);
    }

    /** OSRS script 2758: the second line is grey (no effect) while the first is ticked. */
    static boolean optionGrey(int i) {
        return i == 1 && !bit(BIT_BLOCK_LOWER);
    }

    static boolean panelOpen;
    static int lastVarp = Integer.MIN_VALUE;
    static int lastBook = -1;
    static int lastPrayerBase = -1;
    static int lastDefenceBase = -1;
    static int lastUnlocks = -1;
    static boolean lastQuick;
    static int lastStatsTop = Integer.MIN_VALUE;
    static boolean dirty = true;

    /** Per book: the player's order (slots) and hidden prayers. */
    static final Map<Integer, List<Integer>> order = new HashMap<>();
    static final Map<Integer, Set<Integer>> hiddenSlots = new HashMap<>();
    private static boolean loaded;

    /** Where the cache put each grid child (a rebuilt child is a new object, so a new entry). */
    static final Map<Component, int[]> nativePlace = new WeakHashMap<>();
    /** What this class changed, to give it back. */
    static final Set<Component> hiddenByUs = Collections.newSetFromMap(new IdentityHashMap<>());
    static final Set<Component> dimmedByUs = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Our own components (CC children appended to 271:4). */
    static final List<Component> ours = new ArrayList<>();
    static Component filtersButton;
    static final Component[] optionRows = new Component[OPTIONS.length];
    static final Component[] optionHighlights = new Component[OPTIONS.length];
    /** N504: whether the prayer book was on screen last frame (the menu is laid out again when it comes back). */
    static boolean lastShown;
    static long clickedAt;
    static int clickedRow = -1;

    private RspsPrayerFilters() {
    }

    static boolean userHidden(int book, int slot) {
        load();
        Set<Integer> set = hiddenSlots.get(book);
        return set != null && set.contains(slot);
    }

    /**
     * The slots of [book] that the grid shows, in the order shown: the five filters always (N494); the player's hidden prayers only
     * while editing ([editing] = filtering enabled, N495), dimmed by the caller.
     */
    static List<Integer> shownSlots(int book, int count, boolean editing) {
        load();
        List<Integer> base = new ArrayList<>();
        for (int slot = 0; slot < count; slot++) {
            base.add(slot);
        }
        List<Integer> ordered = RspsSpellbookEdit.ordered(base, order.get(book));
        List<Integer> out = new ArrayList<>();
        for (Integer slot : ordered) {
            if (slot < LEVELS[book].length && passes(book, slot) && (editing || !userHidden(book, slot))) {
                out.add(slot);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------------ main loop

    /** The prayer tooltip (cache script 1160 -> 1163 on 271:49) and the icon it belongs to (N498). */
    static final int TOOLTIP = (IFACE << 16) | 49;
    static final int TOOLTIP_SCRIPT = 1163;
    static int tooltipSlot = -1;
    static boolean tooltipCheck;
    /** Cache script 1237 rebuilt the icons: a tooltip shown then lost its icon's mouse-leave (cache script 40). */
    static boolean iconsRebuilt;
    static final int TOOLTIP_CLEAR_SCRIPT = 41;
    /** varc 1052: "Show stat adjustments" is open (cache scripts 1237 / 1704). */
    static final int STATS_VARC = 1052;

    /** ScriptRunner: a script starts. The grid builders mark the layout for redoing; a prayer tooltip is checked after it ran. */
    static void enter(int scriptId, int[] vars) {
        if (scriptId == 1237 || scriptId == 1293 || scriptId == 1388 || scriptId == 1717) {
            dirty = true;
        }
        if (scriptId == 1237) {
            iconsRebuilt = true;
        }
        if (scriptId == TOOLTIP_SCRIPT && vars != null && vars.length > 2 && vars[2] == TOOLTIP) {
            tooltipSlot = vars[0] == ICONS ? vars[1] : -1;
            tooltipCheck = true;
        }
    }

    static boolean statsShown() {
        return Static511.varcs != null && STATS_VARC < Static511.varcs.length && Static511.varcs[STATS_VARC] != 0;
    }

    /** [c]'s top in the coordinates of the interface the prayer tab is drawn in (every layer's scroll taken off). */
    static int absY(Component c) {
        int y = 0;
        for (int guard = 0; c != null && guard < 32; guard++) {
            y += c.y;
            if (c.layer == -1) {
                break;
            }
            Component parent = InterfaceList.list(c.layer);
            if (parent != null) {
                y -= parent.scrollY;
            }
            c = parent;
        }
        return y;
    }

    /**
     * N498 (owner 2026-10-31 20:1x / 20:5x): the cache puts the tooltip of a prayer UNDER the icon and only flips it when it leaves the
     * tab, so for the bottom row it lay over the stat adjustments and the Filters button. Any part of it below the prayer area (the top
     * of the stat-adjustments bar, open or closed) puts it ABOVE the icon instead - every book, every row.
     */
    static void tooltip() {
        if (iconsRebuilt) {
            // N498 breaktest: a book switch / stat-adjustments toggle while a tooltip showed left it on screen for good (the old icon's
            // mouse-leave never comes). The cache's own clear (script 41: children gone, varc 2 = 0); hovering shows it again.
            iconsRebuilt = false;
            Component tip = InterfaceList.list(TOOLTIP);
            if (tip != null && tip.staticComponents != null && tip.staticComponents.length > 0 || tip != null && tip.dynamicComponents != null && tip.dynamicComponents.length > 0) {
                HookRequest hook = new HookRequest();
                hook.arguments = new Object[] {TOOLTIP_CLEAR_SCRIPT, TOOLTIP};
                ScriptRunner.executeHookInner(hook);
                tooltipCheck = false;
                return;
            }
        }
        if (!tooltipCheck) {
            return;
        }
        tooltipCheck = false;
        Component tip = InterfaceList.list(TOOLTIP);
        Component iconLayer = InterfaceList.list(ICONS);
        Component root = InterfaceList.list(ROOT);
        if (tip == null || tip.hidden || iconLayer == null || root == null || tooltipSlot < 0 || iconLayer.staticComponents == null
            || tooltipSlot >= iconLayer.staticComponents.length || tip.layer == -1) {
            return;
        }
        Component icon = iconLayer.staticComponents[tooltipSlot];
        Component stats = InterfaceList.list(STATS);
        if (icon == null) {
            return;
        }
        // The stat-adjustments bar (271:9: the "Show / Hide stat adjustments" strip, with the panel above it when open), else the bottom row.
        int limit = stats != null && !stats.hidden ? absY(stats) : absY(root) + ROW_Y;
        int tipTop = absY(tip);
        if (tipTop + tip.height <= limit) {
            return;
        }
        int iconTop = absY(icon);
        Component layer = InterfaceList.list(tip.layer);
        int layerTop = layer == null ? 0 : absY(layer) - layer.scrollY;
        int want = Math.max(layer == null ? 0 : layer.scrollY, iconTop - 2 - tip.height - layerTop);
        if (tip.originalY != want) {
            tip.originalY = want;
            InterfaceManager.resizeAndReposition(tip);
            InterfaceManager.redraw(tip);
            trace("tooltip slot " + tooltipSlot + " above its icon (y " + want + ", limit " + limit + ")");
        }
    }

    /** N491: the prayer tab icon of both gameframes (548:144 fixed, 746:29 resizable; 667 sprite 1823). */
    static final int[] TAB_ICONS = {(548 << 16) | 144, (746 << 16) | 29};
    static final int PRAYER_TAB_SPRITE = 1823;

    /**
     * N491 (owner 2026-10-31 + photo): with the Ruinous Powers book the prayer tab shows the purple OSRS icon (OSRS script 907:
     * sideicons_interface,56 for prayerbook 1 = sprite 4834, here scaled to the 22 x 22 of the 667 icon); Normal and Curses keep the
     * 667 star.
     */
    static void tabIcon() {
        int ruinousIcon = RspsSprites.osrs("ruinous-tab");
        int want = book() == RUINOUS ? ruinousIcon : PRAYER_TAB_SPRITE;
        for (int hash : TAB_ICONS) {
            int iface = hash >>> 16;
            if (InterfaceList.interfaces == null || iface >= InterfaceList.interfaces.length || InterfaceList.interfaces[iface] == null) {
                continue;
            }
            Component c = InterfaceList.list(hash);
            if (c != null && (c.graphic == PRAYER_TAB_SPRITE || c.graphic == ruinousIcon) && c.graphic != want) {
                c.graphic = want;
                InterfaceManager.redraw(c);
            }
        }
    }

    /** Main loop, after the hook queue ran (top level, no script running). */
    static void afterHooks() {
        tabIcon();
        if (InterfaceList.list(ROOT) == null) {
            return;
        }
        int now = varp();
        int book = book();
        int prayer = Static498.statBaseLevels[PRAYER];
        int defence = Static498.statBaseLevels[DEFENCE];
        int unlocks = varbit(KNIGHT_WAVES_VARBIT) | varbit(RIGOUR_VARBIT) << 8 | varbit(AUGURY_VARBIT) << 9;
        boolean quick = quickSelect();
        if (now != lastVarp || book != lastBook || prayer != lastPrayerBase || defence != lastDefenceBase || unlocks != lastUnlocks || quick != lastQuick) {
            if (book != lastBook && lastBook != -1 || quick && !lastQuick) {
                panelOpen = false; // OSRS 3156(null): a new book or the quick selection closes the menu
            }
            lastVarp = now;
            lastBook = book;
            lastPrayerBase = prayer;
            lastDefenceBase = defence;
            lastUnlocks = unlocks;
            lastQuick = quick;
            dirty = true;
        }
        // N492: the stat-adjustments bar gets its new place a frame after the cache scripts ran (1698 / 1704): the rows follow it.
        Component stats = InterfaceList.list(STATS);
        int statsTop = stats == null || stats.hidden ? Integer.MIN_VALUE : absY(stats) * 2 + (statsShown() ? 1 : 0);
        if (statsTop != lastStatsTop) {
            lastStatsTop = statsTop;
            dirty = true;
        }
        // N504 (owner 2026-11-01, bug 20261101-000111): the open Prayer Filters menu stays open while another tab is shown - it is part
        // of the prayer tab, so it is hidden with the tab and is there again when the tab comes back (10-22 the problem was the menu
        // over another tab, which cannot happen this way). Only a new book or the quick-prayer selection closes it (above).
        boolean shown = RspsSpellFilters.bookShown(IFACE);
        if (panelOpen && shown && !lastShown) {
            dirty = true; // back on screen: lay the menu out again
        }
        lastShown = shown;
        if (dirty) {
            dirty = false;
            layout();
        }
        tooltip();
        hover();
    }

    /** Hidden and moved children of the cache grid, the bottom row, the menu. */
    static void layout() {
        Component iconLayer = InterfaceList.list(ICONS);
        Component glowLayer = InterfaceList.list(GLOWS);
        if (iconLayer == null || glowLayer == null) {
            return;
        }
        int book = book();
        boolean quick = quickSelect();
        // N494: the filters and the Filters button work whether filtering is enabled or not; only the quick-prayer selection is the
        // cache's own grid (OSRS 466). N495: enabled = editing.
        boolean on = !quick;
        boolean editing = on && enabled();
        if (!on) {
            panelOpen = false;
        }
        Component[] icons = iconLayer.staticComponents;
        Component[] glows = glowLayer.staticComponents;
        int count = icons == null ? 0 : Math.min(icons.length, LEVELS[book].length);
        for (int i = 0; i < count; i++) {
            remember(icons[i]);
            remember(glows != null && i < glows.length ? glows[i] : null);
        }
        List<Integer> shown = on ? shownSlots(book, count, editing) : null;
        Set<Integer> visible = shown == null ? null : new LinkedHashSet<>(shown);
        int[] rows = rows(icons, shown == null ? count : shown.size(), quick);
        for (int slot = 0; slot < count; slot++) {
            Component icon = icons[slot];
            Component glow = glows != null && slot < glows.length ? glows[slot] : null;
            if (icon == null) {
                continue;
            }
            int place = shown == null ? slot : shown.indexOf(slot);
            boolean hide = shown != null && (panelOpen || !visible.contains(slot));
            setHidden(icon, hide);
            setHidden(glow, hide);
            if (!hide) {
                int dy = rows == null ? 0 : rows[place];
                moveTo(icon, icons[place], dy);
                if (glow != null && glows != null && place < glows.length) {
                    moveTo(glow, glows[place], dy);
                }
            }
            setDimmed(icon, editing && userHidden(book, slot));
        }
        InterfaceManager.redraw(iconLayer);
        InterfaceManager.redraw(glowLayer);
        decorate(on, book, shown == null ? count : shown.size());
        trace("layout book=" + BOOK_NAMES[book] + " on=" + on + " editing=" + editing + " quick=" + quick + " panel=" + panelOpen + " varp=0x" + Integer.toHexString(varp())
            + " shown=" + (shown == null ? "all" : shown.toString()));
    }

    static void remember(Component c) {
        if (c != null && !nativePlace.containsKey(c)) {
            nativePlace.put(c, new int[] {c.originalX, c.originalY});
        }
    }

    /**
     * N492 (owner 2026-10-31 20:5x "alles past, geen scrollen"): the Curses / Ruinous grid has no scrollbar (cache script 1704), so with
     * "Show stat adjustments" open the 24 Ruinous prayers (5 rows of the cache's 36 px) reached under the bar. Then the rows close up so
     * the last one ends above the bar: per place the y shift (null = the cache's own rows). Normal scrolls (cache), the 20 Curses fit.
     */
    static int[] rows(Component[] icons, int shownCount, boolean quick) {
        Component stats = InterfaceList.list(STATS);
        Component iconLayer = InterfaceList.list(ICONS);
        if (quick || shownCount < 1 || icons == null || stats == null || iconLayer == null || !statsShown() || varbit(BOOK_VARBIT) == 0) {
            return null;
        }
        int[] first = nativePlace.get(icons[0]);
        int[] last = shownCount - 1 < icons.length ? nativePlace.get(icons[shownCount - 1]) : null;
        if (first == null || last == null) {
            return null;
        }
        int limit = absY(stats) - (absY(iconLayer) - iconLayer.scrollY) - ROW_GAP;
        int height = icons[0].originalHeight;
        if (last[1] + height <= limit || last[1] <= first[1]) {
            return null;
        }
        double factor = Math.max(0.5, (limit - height - first[1]) / (double) (last[1] - first[1]));
        int[] out = new int[icons.length];
        for (int place = 0; place < icons.length; place++) {
            int[] at = nativePlace.get(icons[place]);
            out[place] = at == null ? 0 : (int) Math.round(first[1] + (at[1] - first[1]) * factor) - at[1];
        }
        trace("rows close up above the stat adjustments: limit " + limit + ", factor " + String.format("%.3f", factor));
        return out;
    }

    /** The space kept between the last row and the stat-adjustments bar. */
    static final int ROW_GAP = 3;

    /** [c] to the cache place of [placeOf]. */
    static void moveTo(Component c, Component placeOf) {
        moveTo(c, placeOf, 0);
    }

    /** [c] to the cache place of [placeOf], [dy] lower (N492: negative = closer rows). */
    static void moveTo(Component c, Component placeOf, int dy) {
        int[] at = placeOf == null ? null : nativePlace.get(placeOf);
        if (at == null) {
            at = nativePlace.get(c);
        }
        if (at != null && (c.originalX != at[0] || c.originalY != at[1] + dy)) {
            c.originalX = at[0];
            c.originalY = at[1] + dy;
            InterfaceManager.resizeAndReposition(c);
            InterfaceManager.redraw(c);
        }
    }

    static void setHidden(Component c, boolean hide) {
        if (c == null) {
            return;
        }
        if (hide && !c.hidden) {
            c.hidden = true;
            hiddenByUs.add(c);
            InterfaceManager.redraw(c);
        } else if (!hide && hiddenByUs.remove(c)) {
            c.hidden = false;
            InterfaceManager.redraw(c);
        }
    }

    /** RuneLite PrayerReorder: a hidden prayer shown while editing is half see-through. */
    static final int DIMMED = 160;

    static void setDimmed(Component c, boolean dim) {
        if (dim && c.transparency != DIMMED) {
            c.transparency = DIMMED;
            dimmedByUs.add(c);
            InterfaceManager.redraw(c);
        } else if (!dim && dimmedByUs.remove(c)) {
            c.transparency = 0;
            InterfaceManager.redraw(c);
        }
    }

    static void setStaticHidden(int hash, boolean hide) {
        Component c = InterfaceList.list(hash);
        setHidden(c, hide);
    }

    // ------------------------------------------------------------------------------------------------------------- UI

    static final int WIDTH = 190;
    /** The bottom row (271:0): y 239, 22 high; OSRS 3154: the Filters button 18 high. */
    static final int ROW_Y = 239;
    static final int ROW_HEIGHT = 22;
    static final int BUTTON_HEIGHT = 18;
    /** The area above the stat-adjustments bar (271:9 at y 219). */
    static final int MENU_AREA = 219;

    /** Our components out of 271:4 again. */
    static void clearOurs(Component root) {
        if (root.staticComponents != null && !ours.isEmpty()) {
            List<Component> keep = new ArrayList<>();
            for (Component c : root.staticComponents) {
                if (c != null && !ours.contains(c)) {
                    keep.add(c);
                }
            }
            Component[] left = keep.isEmpty() ? null : keep.toArray(new Component[0]);
            for (int i = 0; left != null && i < left.length; i++) {
                left[i].id = i;
            }
            root.staticComponents = root.dynamicComponents = left;
        }
        ours.clear();
        filtersButton = null;
        for (int i = 0; i < OPTIONS.length; i++) {
            optionRows[i] = null;
            optionHighlights[i] = null;
        }
    }

    static void decorate(boolean on, int book, int shownCount) {
        Component root = InterfaceList.list(ROOT);
        if (root == null) {
            return;
        }
        clearOurs(root);
        setStaticHidden(GRID, false);
        // The menu replaces the grid: its scrollbar and the stat-adjustments bar go while it is open (no overlap).
        setStaticHidden(SCROLLBAR, on && panelOpen);
        setStaticHidden(STATS, on && panelOpen);
        Component levelIcon = InterfaceList.list(LEVEL_ICON);
        Component levelText = InterfaceList.list(LEVEL_TEXT);
        remember(levelIcon);
        remember(levelText);
        if (!on) {
            moveTo(levelIcon, levelIcon);
            moveTo(levelText, levelText);
            InterfaceManager.redraw(root);
            return;
        }
        // OSRS script 3154: Filters = parawidth("Filters") + 14 wide, the level box 24 + parawidth("99 / 99"); three equal gaps.
        int buttonWidth = RspsFilterUi.buttonWidth("Filters");
        int levelWidth = levelText == null || levelIcon == null ? 69 : nativeX(levelText) + levelText.originalWidth - nativeX(levelIcon);
        int gap = Math.max(2, (WIDTH - buttonWidth - levelWidth) / 3);
        if (levelIcon != null && levelText != null) {
            int shift = gap - nativeX(levelIcon);
            RspsFilterUi.place(levelIcon, nativeX(levelIcon) + shift, levelIcon.originalY, levelIcon.originalWidth, levelIcon.originalHeight);
            RspsFilterUi.place(levelText, nativeX(levelText) + shift, levelText.originalY, levelText.originalWidth, levelText.originalHeight);
            InterfaceManager.redraw(levelIcon);
            InterfaceManager.redraw(levelText);
        }
        int bx = WIDTH - gap - buttonWidth;
        int by = ROW_Y + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
        filtersButton = RspsFilterUi.button(root, ours, "Filters", bx, by, buttonWidth, panelOpen);
        if (panelOpen) {
            menu(root);
        }
        InterfaceManager.redraw(root);
    }

    static int nativeX(Component c) {
        int[] at = nativePlace.get(c);
        return at == null ? c.originalX : at[0];
    }

    /**
     * OSRS 3156 / 3158 / 6067: a 180 wide stone panel, the title "Prayer Filters" in its 28 px top, one row per line: max(25, lines * 11
     * + 3) high (the second OSRS line wraps over three lines), grey with the pale tick while it has no effect (OSRS 2758).
     */
    static void menu(Component root) {
        FontMetrics metrics = Toolkit.active == null ? null : Fonts.metrics(RspsFilterUi.FONT, Toolkit.active);
        int[] rowHeights = new int[OPTIONS.length];
        int body = 0;
        for (int i = 0; i < OPTIONS.length; i++) {
            int lines = metrics == null ? 1 : Math.max(1, metrics.paraHeight(OPTIONS[i], Sprites.nameIcons, RspsFilterUi.MENU_WIDTH - 30));
            rowHeights[i] = Math.max(25, lines * 11 + 3);
            body += rowHeights[i];
        }
        int height = RspsFilterUi.TITLE_HEIGHT + body;
        int x = (WIDTH - RspsFilterUi.MENU_WIDTH) / 2;
        int y = Math.max(0, (MENU_AREA - height) / 2);
        RspsFilterUi.panel(root, ours, "Prayer Filters", x, y, RspsFilterUi.MENU_WIDTH, height);
        int rowY = y + 26;
        for (int i = 0; i < OPTIONS.length; i++) {
            boolean grey = optionGrey(i);
            Component[] row = RspsFilterUi.row(root, ours, OPTIONS[i], x, rowY, RspsFilterUi.MENU_WIDTH, rowHeights[i],
                grey ? "radio-neutral" : optionTicked(i) ? "radio-tick" : "radio-cross", grey ? RspsFilterUi.GREY : RspsFilterUi.ORANGE);
            optionHighlights[i] = row[0];
            optionRows[i] = row[1];
            rowY += rowHeights[i];
        }
    }

    /** OSRS 3652 / 3653 / 835: rows light up under the mouse (not a grey row), a click 210 for 800 ms, the button label white. */
    static void hover() {
        RspsFilterUi.hoverLabel(filtersButton);
        long now = System.currentTimeMillis();
        for (int i = 0; i < OPTIONS.length; i++) {
            RspsFilterUi.light(optionHighlights[i], optionRows[i], !optionGrey(i), clickedRow == i && now - clickedAt < 800);
        }
    }

    // ------------------------------------------------------------------------------------------------- menu + clicks

    static boolean isPrayer(Component c) {
        return c != null && c.id != -1 && c.slot == ICONS;
    }

    /**
     * N519: cache script 1388 gives only the 15x15 checkboxes on 271:42 Select/Deselect + onOp 2290. Script 1237
     * draws separate 30x30 icons on 271:8 and deliberately gives them no ops in quick mode. The icon's centre lies
     * outside its checkbox, so clicking the prayer yielded only Cancel, also before N482. Use the original checkbox
     * as the menu target: its own label, hook, child index and server events remain authoritative.
     */
    static Component quickMenuComponent(Component c) {
        if (!quickSelect() || !isPrayer(c)) {
            return c;
        }
        Component quick = InterfaceList.list(QUICK);
        if (quick == null || quick.hidden || quick.staticComponents == null || c.id >= quick.staticComponents.length) {
            return null;
        }
        Component cell = quick.staticComponents[c.id];
        return cell == null || cell.hidden ? null : cell;
    }

    /** InterfaceManager.addMiniMenuOptions: the tab-stone ops, the Filters button, the menu rows, Hide / Unhide. */
    static boolean addOptions(Component component) {
        if (component == null) {
            return false;
        }
        if (component.id == -1 && (component.slot == TAB_FIXED || component.slot == TAB_RESIZABLE)) {
            String verb = enabled() ? "Disable prayer filtering" : "Enable prayer filtering";
            // N497 (owner 20:5x): the open book's order and hidden prayers back to the cache's; added first = listed under the switch.
            MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, "Reset prayer layout", ACTION_RESET + LOW, true, -1, "", 0L, false);
            MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, verb, ACTION_TAB + LOW, true, -1, "", 0L, false);
            return false;
        }
        if (filtersButton != null && component == filtersButton) {
            MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, "Prayer Filters", ACTION_FILTERS, true, -1, "", 0L, false);
            return true;
        }
        for (int i = 0; i < optionRows.length; i++) {
            if (optionRows[i] != null && component == optionRows[i]) {
                if (!optionGrey(i)) {
                    MiniMenu.addEntryInner(false, -1, i + 1, component.id, component.slot, "Change", ACTION_OPTION, true, -1, "", 0L, false);
                }
                return true;
            }
        }
        if (ours.contains(component)) {
            return true; // the panel itself: no options, nothing beneath it
        }
        if (isPrayer(component) && enabled() && !quickSelect() && !panelOpen) {
            int book = book();
            String verb = userHidden(book, component.id) ? "Unhide" : "Hide";
            MiniMenu.addEntryInner(false, -1, component.id, component.id, component.slot, verb, ACTION_HIDE + LOW, true, -1, "", 0L, false);
        }
        return false;
    }

    /** MiniMenu action dispatch (action without the low-priority offset). */
    static boolean handle(int action, long v1) {
        if (action == ACTION_TAB) {
            send(0);
            return true;
        }
        if (action == ACTION_FILTERS) {
            panelOpen = !panelOpen;
            dirty = true;
            return true;
        }
        if (action == ACTION_OPTION) {
            int line = (int) v1 - 1;
            if (line < 0 || line >= OPTIONS.length || optionGrey(line)) {
                return true;
            }
            clickedRow = line;
            clickedAt = System.currentTimeMillis();
            RspsFilterUi.clickSound(); // OSRS prayer_filtertoggle_op 3157
            send(line + 1);
            return true;
        }
        if (action == ACTION_RESET) {
            reset(book());
            return true;
        }
        if (action == ACTION_HIDE) {
            int book = book();
            int slot = (int) v1;
            load();
            Set<Integer> set = hiddenSlots.computeIfAbsent(book, k -> new LinkedHashSet<>());
            if (!set.remove(slot)) {
                set.add(slot);
            }
            save();
            dirty = true;
            return true;
        }
        return false;
    }

    static void send(int slot) {
        if (MainLogicManager.step != 11) {
            return;
        }
        ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON1, ServerConnection.GAME.isaac);
        ClientMessage.addComponentData(ROOT, -1, message, slot);
        ServerConnection.GAME.send(message);
        trace("send 271:4 slot=" + slot);
    }

    /** N497: [book]'s order and hidden prayers back to the cache's (the other books keep theirs; the five filters stay). */
    static void reset(int book) {
        load();
        order.remove(book);
        hiddenSlots.remove(book);
        save();
        dirty = true;
        trace("reset " + BOOK_NAMES[book]);
    }

    // ------------------------------------------------------------------------------------------------- moving

    /** A dragged prayer was dropped on [target] (both slots of the open book): it takes the target's place. */
    static void drop(int moved, int target) {
        int book = book();
        Component iconLayer = InterfaceList.list(ICONS);
        int count = iconLayer == null || iconLayer.staticComponents == null ? LEVELS[book].length : Math.min(iconLayer.staticComponents.length, LEVELS[book].length);
        load();
        List<Integer> base = new ArrayList<>();
        for (int slot = 0; slot < count; slot++) {
            base.add(slot);
        }
        List<Integer> all = RspsSpellbookEdit.ordered(base, order.get(book));
        order.put(book, RspsSpellbookEdit.move(all, moved, target));
        save();
        dirty = true;
        trace("moved " + BOOK_NAMES[book] + " slot " + moved + " to the place of " + target);
    }

    /** Whether moving / hiding is on for the grid now (filtering enabled, the grid on screen, no menu, no quick selection). */
    static boolean editable() {
        return enabled() && !quickSelect() && !panelOpen && InterfaceList.list(ICONS) != null && RspsSpellFilters.bookShown(IFACE);
    }

    // ------------------------------------------------------------------------------------------------- storage

    static File file() {
        return new File(RspsOverlayLayout.file().getParentFile(), "prayer.properties");
    }

    static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = file();
        if (!file.isFile()) {
            return;
        }
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (IOException ignored) {
            return;
        }
        for (int book = 0; book < BOOK_NAMES.length; book++) {
            List<Integer> o = slots(p.getProperty(BOOK_NAMES[book] + ".order"), LEVELS[book].length);
            if (!o.isEmpty()) {
                order.put(book, o);
            }
            List<Integer> h = slots(p.getProperty(BOOK_NAMES[book] + ".hidden"), LEVELS[book].length);
            if (!h.isEmpty()) {
                hiddenSlots.put(book, new LinkedHashSet<>(h));
            }
        }
    }

    static List<Integer> slots(String value, int count) {
        List<Integer> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        for (String s : value.split(",")) {
            try {
                int slot = Integer.parseInt(s.trim());
                if (slot >= 0 && slot < count && !out.contains(slot)) {
                    out.add(slot);
                }
            } catch (NumberFormatException ignored) {
                // a hand-edited entry: skipped
            }
        }
        return out;
    }

    static void save() {
        Properties p = new Properties();
        for (int book = 0; book < BOOK_NAMES.length; book++) {
            if (order.containsKey(book)) {
                p.setProperty(BOOK_NAMES[book] + ".order", join(order.get(book)));
            }
            if (hiddenSlots.containsKey(book)) {
                p.setProperty(BOOK_NAMES[book] + ".hidden", join(hiddenSlots.get(book)));
            }
        }
        File file = file();
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "78 client: prayer order and hidden prayers per book (slots), N482");
        } catch (IOException ignored) {
            // not saved: it still holds for this session
        }
    }

    static String join(Iterable<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (Integer v : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(v);
        }
        return sb.toString();
    }

    static void trace(String line) {
        System.out.println("[prayerfilters] " + line);
    }
}
