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
 * Owner's own additions (N482.txt DEFINITIEF): with filtering enabled every prayer has right-click "Hide" / "Unhide" and can be
 * moved (RuneLite PrayerReorder); a sixth menu line "Show hidden prayers" shows the hidden ones dimmed so they can be unhidden.
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
    private static final int LOW = 2000;

    /** OSRS script 3156 lines (menu slots 1-5), then the owner's own line. */
    static final String[] OPTIONS = {
        "Show lower tiers of tiered prayers",
        "Show tiered prayers even if multi-skill prayers are available",
        "Show <col=ffffff>Rapid Healing</col> prayers",
        "Show prayers you lack the Prayer level to activate",
        "Show prayers you lack the requirements to activate",
        "Show prayers you have hidden",
    };
    static final int SHOW_HIDDEN_LINE = 5;
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

    /** Menu line [i] shows the tick (the line's bit is clear; line 6 is the local "show hidden"). */
    static boolean optionTicked(int i) {
        return i == SHOW_HIDDEN_LINE ? showHidden : !bit(OPTION_BITS[i]);
    }

    /** OSRS script 2758: the second line is grey (no effect) while the first is ticked. */
    static boolean optionGrey(int i) {
        return i == 1 && !bit(BIT_BLOCK_LOWER);
    }

    static boolean panelOpen;
    static boolean showHidden;
    static int lastVarp = Integer.MIN_VALUE;
    static int lastBook = -1;
    static int lastPrayerBase = -1;
    static int lastDefenceBase = -1;
    static int lastUnlocks = -1;
    static boolean lastQuick;
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
    static Component filtersLabel;
    static final Component[] optionRows = new Component[OPTIONS.length];
    static final Component[] optionHighlights = new Component[OPTIONS.length];
    static long clickedAt;
    static int clickedRow = -1;

    private RspsPrayerFilters() {
    }

    static boolean userHidden(int book, int slot) {
        load();
        Set<Integer> set = hiddenSlots.get(book);
        return set != null && set.contains(slot);
    }

    /** The slots of [book] that the grid shows, in the order shown (null: the cache grid untouched). */
    static List<Integer> shownSlots(int book, int count) {
        load();
        List<Integer> base = new ArrayList<>();
        for (int slot = 0; slot < count; slot++) {
            base.add(slot);
        }
        List<Integer> ordered = RspsSpellbookEdit.ordered(base, order.get(book));
        List<Integer> out = new ArrayList<>();
        for (Integer slot : ordered) {
            if (slot < LEVELS[book].length && passes(book, slot) && (showHidden || !userHidden(book, slot))) {
                out.add(slot);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------------ main loop

    /** ScriptRunner: a script starts. The grid builders mark the layout for redoing. */
    static void enter(int scriptId) {
        if (scriptId == 1237 || scriptId == 1293 || scriptId == 1388 || scriptId == 1717) {
            dirty = true;
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
        if (panelOpen && !RspsSpellFilters.bookShown(IFACE)) {
            panelOpen = false; // the tab left the screen (owner 2026-10-22 for the spell menu)
            dirty = true;
        }
        if (dirty) {
            dirty = false;
            layout();
        }
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
        boolean on = enabled() && !quick;
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
        List<Integer> shown = on ? shownSlots(book, count) : null;
        Set<Integer> visible = shown == null ? null : new LinkedHashSet<>(shown);
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
                moveTo(icon, icons[place]);
                if (glow != null && glows != null && place < glows.length) {
                    moveTo(glow, glows[place]);
                }
            }
            setDimmed(icon, on && showHidden && userHidden(book, slot));
        }
        InterfaceManager.redraw(iconLayer);
        InterfaceManager.redraw(glowLayer);
        decorate(on, book, shown == null ? count : shown.size());
        trace("layout book=" + BOOK_NAMES[book] + " on=" + on + " quick=" + quick + " panel=" + panelOpen + " varp=0x" + Integer.toHexString(varp())
            + " shown=" + (shown == null ? "all" : shown.toString()));
    }

    static void remember(Component c) {
        if (c != null && !nativePlace.containsKey(c)) {
            nativePlace.put(c, new int[] {c.originalX, c.originalY});
        }
    }

    /** [c] to the cache place of [placeOf]. */
    static void moveTo(Component c, Component placeOf) {
        int[] at = placeOf == null ? null : nativePlace.get(placeOf);
        if (at == null) {
            at = nativePlace.get(c);
        }
        if (at != null && (c.originalX != at[0] || c.originalY != at[1])) {
            c.originalX = at[0];
            c.originalY = at[1];
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

    /** RuneLite PrayerReorder: a hidden prayer shown while choosing is half see-through. */
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

    static final int ORANGE = 0xff981f;
    static final int GREY = 0x9f9f9f;
    static final int FONT = 494;
    static final int TITLE_FONT = 496;
    static final int WIDTH = 190;
    static final int MENU_WIDTH = 180;
    static final int BUTTON_HEIGHT = 18;
    /** The bottom row (271:0): y 239, 22 high. */
    static final int ROW_Y = 239;
    static final int ROW_HEIGHT = 22;
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
        filtersLabel = null;
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
        setStaticHidden(SCROLLBAR, false);
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
        FontMetrics metrics = Toolkit.active == null ? null : Fonts.metrics(FONT, Toolkit.active);
        int buttonWidth = (metrics == null ? 32 : metrics.stringWidth("Filters")) + 14;
        int levelWidth = levelText == null || levelIcon == null ? 69 : nativeX(levelText) + levelText.originalWidth - nativeX(levelIcon);
        int gap = Math.max(2, (WIDTH - buttonWidth - levelWidth) / 3);
        if (levelIcon != null && levelText != null) {
            int shift = gap - nativeX(levelIcon);
            place(levelIcon, nativeX(levelIcon) + shift, levelIcon.originalY, levelIcon.originalWidth, levelIcon.originalHeight);
            place(levelText, nativeX(levelText) + shift, levelText.originalY, levelText.originalWidth, levelText.originalHeight);
            InterfaceManager.redraw(levelIcon);
            InterfaceManager.redraw(levelText);
        }
        int bx = WIDTH - gap - buttonWidth;
        int by = ROW_Y + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
        nineSlice(root, panelOpen ? "btn-on" : "btn-off", bx, by, buttonWidth, BUTTON_HEIGHT);
        filtersLabel = text(root, "Filters", bx, by, buttonWidth, BUTTON_HEIGHT, 1, FONT, ORANGE);
        filtersButton = filtersLabel;
        filtersButton.hasHook = true;
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
     * OSRS 3156 / 3158 / 6067: a 180 wide stone panel (v2_borders_slim around the dark tile), the title "Prayer Filters" (b12, orange)
     * in its 28 px top, one row per line: max(25, lines * 11 + 3) high, a full-width white highlight, the 17 x 17 indicator at x 5,
     * the text from x 25 to 5 from the right (p11, orange, shadow; grey for a line without effect).
     */
    static void menu(Component root) {
        FontMetrics metrics = Toolkit.active == null ? null : Fonts.metrics(FONT, Toolkit.active);
        int[] rowHeights = new int[OPTIONS.length];
        int body = 0;
        for (int i = 0; i < OPTIONS.length; i++) {
            int lines = metrics == null ? 1 : Math.max(1, metrics.paraHeight(OPTIONS[i], Sprites.nameIcons, MENU_WIDTH - 30));
            rowHeights[i] = Math.max(25, lines * 11 + 3);
            body += rowHeights[i];
        }
        int height = 28 + body;
        int x = (WIDTH - MENU_WIDTH) / 2;
        int y = Math.max(0, (MENU_AREA - height) / 2);
        Component back = create(root, Component.TYPE_GRAPHIC);
        place(back, x, y, MENU_WIDTH, height);
        back.graphic = RspsSprites.osrs("tradebacking-dark");
        back.tiling = true;
        back.hasHook = true; // the panel takes the mouse: nothing under it reacts
        stonePanel(root, x, y, MENU_WIDTH, height);
        text(root, "Prayer Filters", x, y, MENU_WIDTH, 28, 1, TITLE_FONT, ORANGE);
        int rowY = y + 26;
        for (int i = 0; i < OPTIONS.length; i++) {
            boolean grey = optionGrey(i);
            Component highlight = create(root, Component.TYPE_RECTANGLE);
            place(highlight, x + 3, rowY, MENU_WIDTH - 6, rowHeights[i]);
            highlight.colour = 0xffffff;
            highlight.filled = true;
            highlight.transparency = 255;
            optionHighlights[i] = highlight;
            Component tick = create(root, Component.TYPE_GRAPHIC);
            place(tick, x + 5, rowY + (rowHeights[i] - 17) / 2, 17, 17);
            tick.graphic = RspsSprites.osrs(grey ? "radio-neutral" : optionTicked(i) ? "radio-tick" : "radio-cross");
            Component label = text(root, OPTIONS[i], x + 25, rowY, MENU_WIDTH - 30, rowHeights[i], 0, FONT, grey ? GREY : ORANGE);
            label.textAlignY = 1;
            // The whole row is one click (OSRS: a full-width rectangle with op "Change").
            Component hit = create(root, Component.TYPE_RECTANGLE);
            place(hit, x + 3, rowY, MENU_WIDTH - 6, rowHeights[i]);
            hit.transparency = 255;
            hit.hasHook = true;
            optionRows[i] = hit;
            rowY += rowHeights[i];
        }
    }

    /** OSRS stonepanel_resizable (6067): the v2_borders_slim edges / corners on 32 x 32 canvases. */
    static void stonePanel(Component root, int x, int y, int w, int h) {
        graphic(root, "border-0", x + 16, y - 13, w - 32, 32, true);
        graphic(root, "border-2", x + 16, y + h - 32 + 12, w - 32, 32, true);
        graphic(root, "border-1", x - 13, y + 16, 32, h - 32, true);
        graphic(root, "border-3", x + w - 32 + 12, y + 16, 32, h - 32, true);
        graphic(root, "border-4", x, y, 32, 32, false);
        graphic(root, "border-5", x + w - 32, y, 32, 32, false);
        graphic(root, "border-6", x, y + h - 32, 32, 32, false);
        graphic(root, "border-7", x + w - 32, y + h - 32, 32, 32, false);
        // The line under the title (argument 26: y - 20 / y - 18 on the 32 px canvases).
        graphic(root, "border-8", x, y + 26 - 20, w, 32, true);
        graphic(root, "border-9", x, y + 26 - 18, 32, 32, false);
        graphic(root, "border-10", x + w - 32, y + 26 - 18, 32, 32, false);
    }

    /** OSRS options_button_off / _on (835 / 836): nine tiles, 6 px corners. */
    static void nineSlice(Component root, String prefix, int x, int y, int w, int h) {
        graphic(root, prefix + "-4", x + 6, y + 6, w - 12, h - 12, true);
        graphic(root, prefix + "-1", x + 6, y, w - 12, 6, true);
        graphic(root, prefix + "-7", x + 6, y + h - 6, w - 12, 6, true);
        graphic(root, prefix + "-3", x, y + 6, 6, h - 12, true);
        graphic(root, prefix + "-5", x + w - 6, y + 6, 6, h - 12, true);
        graphic(root, prefix + "-0", x, y, 6, 6, false);
        graphic(root, prefix + "-2", x + w - 6, y, 6, 6, false);
        graphic(root, prefix + "-6", x, y + h - 6, 6, 6, false);
        graphic(root, prefix + "-8", x + w - 6, y + h - 6, 6, 6, false);
    }

    static Component graphic(Component root, String name, int x, int y, int w, int h, boolean tiled) {
        Component g = create(root, Component.TYPE_GRAPHIC);
        place(g, x, y, Math.max(0, w), Math.max(0, h));
        g.graphic = RspsSprites.osrs(name);
        g.tiling = tiled;
        return g;
    }

    static Component text(Component root, String s, int x, int y, int w, int h, int alignX, int font, int colour) {
        Component t = create(root, Component.TYPE_TEXT);
        place(t, x, y, w, h);
        t.text = s;
        t.fontGraphic = font;
        t.colour = colour;
        t.textShadow = true;
        t.textAlignX = alignX;
        t.textAlignY = 1;
        return t;
    }

    /** CC_CREATE in Java: appended to 271:4's own (otherwise empty) CC list. */
    static Component create(Component parent, int type) {
        int id = parent.staticComponents == null ? 0 : parent.staticComponents.length;
        Component[] grown = new Component[id + 1];
        if (parent.staticComponents != null) {
            System.arraycopy(parent.staticComponents, 0, grown, 0, id);
        }
        Component cc = new Component();
        cc.type = type;
        cc.layer = cc.slot = parent.slot;
        cc.id = id;
        grown[id] = cc;
        parent.staticComponents = parent.dynamicComponents = grown;
        ours.add(cc);
        return cc;
    }

    static void place(Component c, int x, int y, int w, int h) {
        c.originalX = x;
        c.originalY = y;
        c.originalWidth = w;
        c.originalHeight = h;
        InterfaceManager.resizeAndReposition(c);
    }

    /** OSRS 3652 / 3653 / 3654: rows light up under the mouse (225, a click 210 for 40 client cycles), the button text turns white. */
    static void hover() {
        if (filtersLabel != null) {
            int colour = filtersLabel.hovered || filtersButton.hovered ? 0xffffff : ORANGE;
            if (filtersLabel.colour != colour) {
                filtersLabel.colour = colour;
                InterfaceManager.redraw(filtersLabel);
            }
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < OPTIONS.length; i++) {
            Component row = optionRows[i];
            Component light = optionHighlights[i];
            if (row == null || light == null) {
                continue;
            }
            int trans = clickedRow == i && now - clickedAt < 800 ? 210 : row.hovered && !optionGrey(i) ? 225 : 255;
            if (light.transparency != trans) {
                light.transparency = trans;
                InterfaceManager.redraw(light);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- menu + clicks

    static boolean isPrayer(Component c) {
        return c != null && c.id != -1 && c.slot == ICONS;
    }

    /** InterfaceManager.addMiniMenuOptions: the tab-stone op, the Filters button, the menu rows, Hide / Unhide. */
    static boolean addOptions(Component component) {
        if (component == null) {
            return false;
        }
        if (component.id == -1 && (component.slot == TAB_FIXED || component.slot == TAB_RESIZABLE)) {
            String verb = enabled() ? "Disable prayer filtering" : "Enable prayer filtering";
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
            SoundManager.playSynthSound(2266, 1, 0, 255, 256); // OSRS prayer_filtertoggle_op 3157
            if (line == SHOW_HIDDEN_LINE) {
                showHidden = !showHidden;
                save();
                dirty = true;
            } else {
                send(line + 1);
            }
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
        showHidden = "true".equals(p.getProperty("showHidden"));
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
        p.setProperty("showHidden", String.valueOf(showHidden));
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
