import com.jagex.ClientProt;
import com.jagex.core.datastruct.key.HashTableIterator;
import com.jagex.game.runetek6.config.iftype.SubInterface;
import com.jagex.game.runetek6.config.vartype.TimedVarDomain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * N72 (owner 2026-10-16 "magic spellbook needs to exact same as osrs style layout options"; 2026-10-31 "werkt nog niet precies als
 * osrs"): the OSRS spellbook filters on the three 667 books (Standard 192, Ancient 193, Lunar 430).
 *
 * OSRS (Wiki "Spellbook"; cache 2686 / rev241 scripts 907 / 2603 / 2604 / 2606 / 2607 / 2611 / 2619 / 2620, interface 218; research
 * team\dossiers\N72-ONDERZOEK-SPELLFILTER.txt): the magic tab stone's right-click "Enable spell filtering" / "Disable spell filtering";
 * while enabled a "Filters" button at the bottom centre of the book (nine-tile grey button, red while open, white label under the mouse)
 * opens the "Spell Filters" menu in place of the spells: seven rows (full-row click "Change", hover / click light, sound 2266), green tick
 * = shown, red cross = hidden; the spells that pass close up in level order in the OSRS grid of script 2611 (icon resizing: 40 px for up
 * to 20 spells, then 24 px, 3-7 columns by count); "No spells match your selected filters." when none is left. The three filters work on
 * their own: a spell lacks the level only when both its base and its current level are below it (2619), the runes / staff (2620) and
 * the other requirements (4131) are separate questions.
 *
 * Owner's choices (N72.txt DEFINITIEF; N494 / N495 / N497 owner 2026-10-31 20:5x "Ja, zo"): icon resizing everywhere ("Enable icon
 * resizing", no PvP limit); the Filters button and its seven lines ALWAYS work (the OSRS grid always); "Enable spell filtering" = EDITING:
 * every spell the filters let through shows, the ones the player hid half see-through, right-click "Hide" / "Unhide" and Shift + drag
 * moves (RuneLite SpellbookPlugin, kept per book on this machine by {@link RspsSpellbookEdit}); "Disable spell filtering" = DONE: the
 * hidden spells leave the book, the order stays. "Reset spellbook layout" on the magic tab stone puts the open book back.
 *
 * The state is the server's (SpellFilters.kt, varp 1376, saved with the account): bits 9-17 are the 667 category filters the cache's own
 * layout script 2059 applies (a projection of the OSRS Combat / Teleport / Utility choice); bits 18-25 the OSRS state read here. This
 * class adds what 667 never had:
 * <ul>
 * <li>lacks level / runes / requirements: the cache's per-spell redraw script 21 (args: component, lit sprite, dark sprite, level,
 * base-level flag, 4x rune + amount) decides "castable". A probe run of script 21 with level 0 and marker sprites (graphic not
 * applied) tells runes (it failed after calling the rune counter, script 19 - pouch and staffs included) from requirements; for a spell
 * that fails its requirements the rune counter is asked per rune on its own, so the two filters stay independent;</li>
 * <li>layout: inside script 2059 the category lookup (enum 727) of a spell this class hides is answered with a category whose array entry
 * reads "hidden", so the cache script closes the gaps; Energy Transfer, Stat Spy and Dream are Combat as in OSRS; then the OSRS grid;</li>
 * <li>the tab-stone op (548:136 fixed, 746:46 resizable, "Magic Spellbook"), the Filters button and the menu, drawn as dynamic
 * components in the book's full-size layer. The 667 Filter / Sort / Defensive Casting row and the bottom bar art go while filtering is
 * enabled (OSRS has none); the spell area then takes the OSRS 240 px.</li>
 * </ul>
 * Every OSRS action goes to the server as IF_BUTTON1 on the book's full-size layer (param 688) with the action as slot.
 */
public final class RspsSpellFilters {

    static final int VARP = 1376;
    static final int BIT_ENABLED = 18;
    static final int BIT_HIDE_COMBAT = 19;
    static final int BIT_HIDE_TELEPORT = 20;
    static final int BIT_HIDE_UTILITY = 21;
    static final int BIT_HIDE_LEVEL = 22;
    static final int BIT_HIDE_RUNES = 23;
    static final int BIT_HIDE_REQUIREMENTS = 24;
    /** OSRS line 7 (varbit 6548): bigger icons for the spells that are left. Set = OFF, like every "hide" bit above. */
    static final int BIT_ICON_RESIZING_OFF = 25;

    static final int SPELL_SCRIPT = 21;
    static final int RUNE_SCRIPT = 19;
    static final int LAYOUT_SCRIPT = 2059;
    static final int CATEGORY_ENUM = 727;
    static final int CLIENT_SPELLBOOK_VARBIT = 357;
    static final int MAGIC = 6;

    /** Enum 727 categories: 0 combat, 1 skill, 2 misc, 3 teleport. */
    static final int COMBAT_CATEGORY = 0;
    /** OSRS spell_type 0 (Combat) where the 667 enum says misc: Lunar Energy Transfer, Stat Spy, Dream (research report 9a). */
    static final Set<Integer> OSRS_COMBAT = new LinkedHashSet<>(java.util.Arrays.asList((430 << 16) | 27, (430 << 16) | 31, (430 << 16) | 32));

    static final int TAB_FIXED = (548 << 16) | 136;
    static final int TAB_RESIZABLE = (746 << 16) | 46;

    static final int ACTION_TAB = 1970;
    static final int ACTION_FILTERS = 1971;
    static final int ACTION_OPTION = 1972;
    private static final int LOW = 2000;

    /** Spell status from the probe. */
    static final int OK = 0;
    static final int LACKS_RUNES = 1;
    static final int LACKS_REQUIREMENTS = 2;

    /** Marker sprites of a probe run of script 21 (never applied). */
    static final int PROBE_LIT = -7201;
    static final int PROBE_DARK = -7202;

    static final int LEGACY_FRAME_SPRITE = 852;
    static final int WIDTH = 190;
    /** OSRS 218: the spell area ("top") is 184 x 240 while filtering is enabled; the bottom strip is 17 high. */
    static final int OSRS_TOP_WIDTH = 184;
    static final int OSRS_TOP_HEIGHT = 240;
    static final int OSRS_BUTTON_HEIGHT = 17;
    static final int BOOK_HEIGHT = 261;
    static final int LINE = 25;

    /** {interface, menu layer (struct param 688), bottom-bar sprite, first Filter, first Sort, Defensive Casting, scrollbar layer}. */
    static final int[][] BOOKS = {
        {192, 97, 21, 7, 15, 2, 94},
        {193, 54, 15, 5, 9, 18, 51},
        {430, 66, 17, 5, 11, 20, 63},
    };

    /** OSRS script 2603 texts, menu slot 1-7 (slot 0 = enable/disable). "Show spells you have hidden" went with N495. */
    static final String[] OPTIONS = {
        "Show <col=ffffff>Combat</col> spells",
        "Show <col=ffffff>Teleport</col> spells",
        "Show <col=ffffff>Utility</col> spells",
        "Show spells you lack the Magic level to cast",
        "Show spells you lack the runes to cast",
        "Show spells you lack the requirements to cast",
        "Enable icon resizing",
    };
    static final int[] OPTION_BITS = {BIT_HIDE_COMBAT, BIT_HIDE_TELEPORT, BIT_HIDE_UTILITY, BIT_HIDE_LEVEL, BIT_HIDE_RUNES, BIT_HIDE_REQUIREMENTS, BIT_ICON_RESIZING_OFF};

    /** Menu line [i] shows the tick while its bit is clear (every bit is an "off" / "hide" bit). */
    static boolean optionTicked(int i) {
        return !bit(OPTION_BITS[i]);
    }

    /** Script 21 args per spell component hash (the last real run). */
    static final Map<Integer, int[]> args = new HashMap<>();
    static final Map<Integer, Integer> status = new HashMap<>();
    /** For a spell that fails its requirements: whether the rune counter finds its runes (null = not known). */
    static final Map<Integer, Boolean> runesOk = new HashMap<>();
    /** What the last layout decided per spell (to know when a re-layout is due). */
    static final Map<Integer, Boolean> laidOut = new HashMap<>();
    static final Set<Integer> dirty = new LinkedHashSet<>();
    /** Every spell the last layout went through. */
    static final Set<Integer> layoutSpells = new LinkedHashSet<>();

    static boolean probing;
    static boolean probeRuneCount;
    static int probeOutcome = -1;
    static boolean forceHide;
    static boolean layoutRan;
    static boolean panelOpen;
    static int forcedVarp = Integer.MIN_VALUE;
    static int lastVarp;
    static int lastBook = -1;
    static int lastChanges;

    static Component filtersButton;
    static final Component[] optionRows = new Component[OPTIONS.length];
    static final Component[] optionHighlights = new Component[OPTIONS.length];
    static long clickedAt;
    static int clickedRow = -1;

    private RspsSpellFilters() {
    }

    // ------------------------------------------------------------------------------------------------------------ state

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

    /** "Enable spell filtering" = the editing mode (N495): hidden spells shown half see-through, Hide / Unhide, moving. */
    static boolean enabled() {
        return bit(BIT_ENABLED);
    }

    static int currentBook() {
        int book = TimedVarDomain.instance == null ? 0 : TimedVarDomain.instance.getVarBitValue(CLIENT_SPELLBOOK_VARBIT);
        return book == 1 ? 193 : book == 2 ? 430 : 192;
    }

    static int[] book(int iface) {
        for (int[] b : BOOKS) {
            if (b[0] == iface) {
                return b;
            }
        }
        return null;
    }

    /** OSRS 2619: hidden for the level only when both the base and the current (boosted / drained) Magic level are below it. */
    static boolean lacksLevel(int[] a, int boosted, int base) {
        int level = a[3];
        return boosted < level && base < level;
    }

    /** OSRS 2620 on its own: the rune counter failed in the probe, or (a spell failing its requirements) asked rune by rune. */
    static boolean lacksRunes(int hash, int s) {
        return s == LACKS_RUNES || s == LACKS_REQUIREMENTS && Boolean.FALSE.equals(runesOk.get(hash));
    }

    /** True when the OSRS filters hide spell [hash] (N494: enabled or not); the panel being open hides the whole grid. */
    static boolean hidden(int hash) {
        if (panelOpen) {
            return true;
        }
        // The player's own Hide (owner 2026-10-31): gone once filtering is disabled, shown dimmed while editing (N495).
        if (RspsSpellbookEdit.isHidden(hash) && !enabled()) {
            return true;
        }
        int[] a = args.get(hash);
        if (a == null) {
            return false;
        }
        if (bit(BIT_HIDE_LEVEL) && lacksLevel(a, Static581.statLevels[MAGIC], Static498.statBaseLevels[MAGIC])) {
            return true;
        }
        Integer s = status.get(hash);
        if (s == null) {
            return false;
        }
        return bit(BIT_HIDE_REQUIREMENTS) && s == LACKS_REQUIREMENTS || bit(BIT_HIDE_RUNES) && lacksRunes(hash, s);
    }

    // ------------------------------------------------------------------------------------------- ScriptRunner hooks

    /** A script starts (hook or GOSUB) with [vars]: remember the args of every real spell redraw. */
    static void enter(int scriptId, int[] vars) {
        if (scriptId == RUNE_SCRIPT) {
            if (probing) {
                probeRuneCount = true;
            }
            return;
        }
        if (scriptId == LAYOUT_SCRIPT) {
            layoutRan = true;
            forceHide = false;
            return;
        }
        if (scriptId != SPELL_SCRIPT || probing || vars == null || vars.length < 13) {
            return;
        }
        int[] copy = new int[13];
        System.arraycopy(vars, 0, copy, 0, 13);
        args.put(copy[0], copy);
        dirty.add(copy[0]);
    }

    /** CC/IF_SETGRAPHIC: true = a probe result, do not apply it. */
    static boolean probeGraphic(int scriptId, int graphic) {
        if (!probing || scriptId != SPELL_SCRIPT) {
            return false;
        }
        if (probeOutcome == -1) {
            probeOutcome = graphic == PROBE_LIT ? OK : probeRuneCount ? LACKS_RUNES : LACKS_REQUIREMENTS;
        }
        return true;
    }

    /**
     * ENUM in script 2059: the category of spell [key] (Energy Transfer / Stat Spy / Dream are Combat as in OSRS); a spell this class
     * hides gets a category whose array entry is read as hidden.
     */
    static int layoutCategory(int scriptId, int enumId, int key, int value) {
        if (scriptId != LAYOUT_SCRIPT || enumId != CATEGORY_ENUM) {
            return value;
        }
        layoutSpells.add(key);
        int category = OSRS_COMBAT.contains(key) ? COMBAT_CATEGORY : value;
        boolean hide = hidden(key);
        laidOut.put(key, hide);
        if (!hide) {
            return category;
        }
        forceHide = true;
        return category < 0 ? 0 : category;
    }

    /** PUSH_ARRAY_INT in script 2059 right after a forced category: read as "hidden". */
    static int layoutArray(int scriptId, int value) {
        if (scriptId != LAYOUT_SCRIPT || !forceHide) {
            return value;
        }
        forceHide = false;
        return 1;
    }

    /** Main loop, after the hook queue ran (top level, no script running). */
    static void afterHooks() {
        // Owner 2026-10-22: the filter menu stayed open after a tab switch. OSRS closes it when the book leaves the screen; a new book
        // (altar / Spellbook Swap) starts closed as well.
        int bookNow = currentBook();
        if (panelOpen && (!bookShown(bookNow) || lastBook != -1 && bookNow != lastBook)) {
            panelOpen = false;
            trace("menu closed: the book left the screen");
            relayout();
        }
        lastBook = bookNow;
        // The book's own var trigger does not re-run the layout for every bit of varp 1376 (the OSRS bits 18-25 are new):
        // a changed varp re-lays the open book out here.
        int now = varp();
        if (now != lastVarp) {
            lastVarp = now;
            relayout();
        }
        // A hidden spell's own redraw (script 21 on its inventory / stat triggers) does not run while it is hidden, so it could never come
        // back when the runes arrive or the level goes up: every such change re-asks every spell of the open book.
        int changes = ClientInventory.updateCount * 31 + Static366.statUpdateCount;
        if (changes != lastChanges) {
            lastChanges = changes;
            for (Integer hash : args.keySet()) {
                if ((hash >>> 16) == bookNow) {
                    dirty.add(hash);
                }
            }
        }
        if (!dirty.isEmpty()) {
            boolean changed = false;
            for (Integer hash : dirty.toArray(new Integer[0])) {
                probe(hash);
                Boolean before = laidOut.get(hash);
                if (before != null && before != hidden(hash)) {
                    changed = true;
                }
            }
            dirty.clear();
            if (changed) {
                relayout();
            }
        }
        if (layoutRan) {
            layoutRan = false;
            decorate(currentBook());
        }
        hover();
    }

    static boolean bookShown(int iface) {
        return shown(iface, 0);
    }

    /**
     * [iface] is drawn: some mount of it (N482: after a fixed / resizable switch the old gameframe can still hold a mount of the same
     * interface) leads, through components that are not hidden, up to the top-level interface on screen.
     */
    static boolean shown(int iface, int depth) {
        if (iface == InterfaceManager.topLevelInterface) {
            return true;
        }
        if (depth > 16) {
            return false;
        }
        HashTableIterator iterator = new HashTableIterator(InterfaceManager.subInterfaces);
        for (SubInterface sub = (SubInterface) iterator.first(); sub != null; sub = (SubInterface) iterator.next()) {
            if (sub.id != iface) {
                continue;
            }
            int hash = (int) sub.key;
            boolean visible = true;
            for (int guard = 0; guard < 64; guard++) {
                Component c = InterfaceList.list(hash);
                if (c == null || c.hidden) {
                    visible = false;
                    break;
                }
                if (c.layer == -1) {
                    break;
                }
                hash = c.layer;
            }
            if (visible && shown(hash >>> 16, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /** One probe run of script 21 for [hash]: level 0, marker sprites; the outcome is recorded, nothing is drawn. */
    static void probe(int hash) {
        int[] a = args.get(hash);
        if (a == null) {
            return;
        }
        Object[] hookArgs = new Object[14];
        hookArgs[0] = SPELL_SCRIPT;
        for (int i = 0; i < 13; i++) {
            hookArgs[i + 1] = a[i];
        }
        hookArgs[2] = PROBE_LIT;
        hookArgs[3] = PROBE_DARK;
        hookArgs[4] = 0;
        hookArgs[5] = 0;
        HookRequest hook = new HookRequest();
        hook.arguments = hookArgs;
        probing = true;
        probeRuneCount = false;
        probeOutcome = -1;
        try {
            ScriptRunner.executeHookInner(hook);
        } catch (RuntimeException e) {
            probeOutcome = -1;
        } finally {
            probing = false;
        }
        if (probeOutcome != -1) {
            status.put(hash, probeOutcome);
        }
        if (probeOutcome == LACKS_REQUIREMENTS) {
            runesOk.put(hash, runesInHand(a));
        } else {
            runesOk.remove(hash);
        }
    }

    /** Every rune of script 21's args [a] (4 x rune + amount at 5..12) as far as the cache's rune counter (script 19) finds it. */
    static Boolean runesInHand(int[] a) {
        for (int i = 5; i + 1 < 13; i += 2) {
            int rune = a[i];
            int amount = a[i + 1];
            if (rune <= 0 || amount <= 0) {
                continue;
            }
            int have = runeCount(rune, a[0]);
            if (have < 0) {
                return null;
            }
            if (have < amount) {
                return false;
            }
        }
        return true;
    }

    /** Script 19 (rune, spell) on its own: the count it returns (-1 when it gave none). */
    static int runeCount(int rune, int hash) {
        HookRequest hook = new HookRequest();
        hook.arguments = new Object[] {RUNE_SCRIPT, rune, hash};
        try {
            ScriptRunner.executeHookInner(hook);
        } catch (RuntimeException e) {
            return -1;
        }
        return ScriptRunner.intStackPointer > 0 ? ScriptRunner.intStack[0] : -1;
    }

    /** Runs the cache layout of the open book again (script 2059, sort unchanged). */
    static void relayout() {
        int iface = currentBook();
        if (InterfaceList.list(iface << 16) == null) {
            return;
        }
        HookRequest hook = new HookRequest();
        hook.arguments = new Object[] {LAYOUT_SCRIPT, -1};
        ScriptRunner.executeHookInner(hook);
        layoutRan = false;
        decorate(iface);
    }

    // ------------------------------------------------------------------------------------------------------------- UI

    /** The cache size of every spell icon and the spell area, kept the first time this class sees them. */
    static final Map<Integer, int[]> normalSize = new HashMap<>();
    static final Map<Integer, int[]> normalArea = new HashMap<>();
    /** Width the spellbook scrollbar takes at the right of the spell area: 16 px wide, 1 px from the edge, 1 px gap to the icons. */
    static final int SCROLLBAR_SPACE = 18;

    /** After every layout: the 667 bottom row away, the OSRS grid, the Filters button / menu / "No spells match" text. */
    static void decorate(int iface) {
        int[] b = book(iface);
        if (b == null) {
            return;
        }
        for (int i = 3; i <= 5; i++) {
            Component button = InterfaceList.list((iface << 16) | b[i]);
            if (button != null && button.layer != -1) {
                Component row = InterfaceList.list(button.layer);
                if (row != null && !row.hidden) {
                    row.hidden = true;
                    InterfaceManager.redraw(row);
                }
            }
        }
        // The two 667 button frames (sprite 852) beside the bar sprite go with the buttons.
        for (int i = 1; i <= 2; i++) {
            Component frame = InterfaceList.list((iface << 16) | (b[2] + i));
            if (frame != null && frame.graphic == LEGACY_FRAME_SPRITE && !frame.hidden) {
                frame.hidden = true;
                InterfaceManager.redraw(frame);
            }
        }
        // N494: the book is always OSRS's (enabled() is only the editing mode, N495): no 667 bar art, no scrollbar (the OSRS grid
        // always fits), the 240 px spell area, the Filters button.
        setHidden(InterfaceList.list((iface << 16) | b[2]), true);
        setHidden(InterfaceList.list((iface << 16) | b[6]), true);
        Component bar = InterfaceList.list((iface << 16) | (b[2]));
        if (bar == null || bar.layer == -1) {
            return;
        }
        Component layer = InterfaceList.list(bar.layer);
        if (layer == null) {
            return;
        }
        layer.staticComponents = null;
        layer.dynamicComponents = null;
        filtersButton = null;
        for (int i = 0; i < OPTIONS.length; i++) {
            optionRows[i] = null;
            optionHighlights[i] = null;
        }
        RspsSpellbookEdit.apply(iface); // the player's own order, hidden spells dimmed while editing
        Component area = spellArea(iface);
        osrsGrid(iface, area, !panelOpen, !bit(BIT_ICON_RESIZING_OFF));
        // The Filters button, bottom centre (OSRS 218:207 / 209: 17 high, bottom-aligned, the label's width + 14).
        int w = RspsFilterUi.buttonWidth("Filters");
        int bx = (WIDTH - w) / 2;
        int by = BOOK_HEIGHT - OSRS_BUTTON_HEIGHT;
        RspsFilterUi.nineSlice(layer, null, panelOpen ? "btn-on" : "btn-off", bx, by, w, OSRS_BUTTON_HEIGHT);
        filtersButton = RspsFilterUi.text(layer, null, "Filters", bx, by, w, OSRS_BUTTON_HEIGHT, 1, RspsFilterUi.FONT, RspsFilterUi.ORANGE);
        filtersButton.hasHook = true;
        if (panelOpen) {
            // OSRS 2603 / 2604: 180 wide, 28 + 25 per row, centred in the 184 x 240 top; the indicator 17 x 17 at (5, row + 4).
            int height = RspsFilterUi.TITLE_HEIGHT + LINE * OPTIONS.length;
            int x = (WIDTH - RspsFilterUi.MENU_WIDTH) / 2;
            int y = Math.max(0, (OSRS_TOP_HEIGHT - height) / 2);
            RspsFilterUi.panel(layer, null, "Spell Filters", x, y, RspsFilterUi.MENU_WIDTH, height);
            for (int i = 0; i < OPTIONS.length; i++) {
                Component[] row = RspsFilterUi.row(layer, null, OPTIONS[i], x, y + 26 + i * LINE, RspsFilterUi.MENU_WIDTH, LINE,
                    optionTicked(i) ? "radio-tick" : "radio-cross", RspsFilterUi.ORANGE);
                optionHighlights[i] = row[0];
                optionRows[i] = row[1];
            }
        } else if (!layoutSpells.isEmpty() && noneShown(iface)) {
            // OSRS 2611: p12, orange, shadow, centred both ways over the whole spell layer.
            RspsFilterUi.text(layer, null, "No spells match your selected filters.", 5, 0, WIDTH - 10, OSRS_TOP_HEIGHT, 1, RspsFilterUi.FONT_P12, RspsFilterUi.ORANGE);
        }
        InterfaceManager.redraw(layer);
        trace("decorate book=" + iface + " varp=0x" + Integer.toHexString(varp()) + " editing=" + enabled() + " panel=" + panelOpen + " layer=" + (layer.slot >>> 16) + ":"
            + (layer.slot & 0xFFFF) + " children=" + (layer.staticComponents == null ? 0 : layer.staticComponents.length) + " spells=" + layoutSpells.size());
    }

    static void setHidden(Component c, boolean hide) {
        if (c != null && c.hidden != hide) {
            c.hidden = hide;
            InterfaceManager.redraw(c);
        }
    }

    /** The scroll layer the spells of [iface] sit in. */
    static Component spellArea(int iface) {
        for (Integer hash : layoutSpells) {
            if ((hash >>> 16) == iface) {
                Component spell = InterfaceList.getComponent(hash, -1);
                if (spell != null && spell.layer != -1) {
                    return InterfaceList.list(spell.layer);
                }
            }
        }
        return null;
    }

    /**
     * OSRS script 2611 while filtering is enabled ([on]): the spells left, in the order the cache layout put them (level order, or the
     * player's own), take the OSRS grid in the 184 x 240 top - [resize] = "Enable icon resizing" ticked. Off (or with the menu open)
     * every icon and the spell area get their cache size back; the cache layout script 2059 places them again.
     */
    static void osrsGrid(int iface, Component area, boolean on, boolean resize) {
        List<Component> shown = new ArrayList<>();
        for (Integer hash : layoutSpells) {
            if ((hash >>> 16) != iface) {
                continue;
            }
            Component spell = InterfaceList.getComponent(hash, -1);
            if (spell == null) {
                continue;
            }
            int[] normal = normalSize.get(hash);
            if (normal == null) {
                normal = new int[] {spell.originalWidth, spell.originalHeight};
                normalSize.put(hash, normal);
            }
            if (spell.originalWidth != normal[0] || spell.originalHeight != normal[1]) {
                spell.originalWidth = normal[0];
                spell.originalHeight = normal[1];
                InterfaceManager.resizeAndReposition(spell);
                InterfaceManager.redraw(spell);
            }
            if (!spell.hidden) {
                shown.add(spell);
            }
        }
        if (area == null) {
            return;
        }
        int[] native0 = normalArea.get(area.slot);
        if (native0 == null) {
            native0 = new int[] {area.originalY, area.originalHeight};
            normalArea.put(area.slot, native0);
        }
        int wantHeight = OSRS_TOP_HEIGHT;
        if (area.originalHeight != wantHeight) {
            area.originalHeight = wantHeight;
            InterfaceManager.resizeAndReposition(area);
            InterfaceManager.redraw(area);
        }
        if (!on || shown.isEmpty()) {
            return;
        }
        area.scrollY = 0;
        area.scrollHeight = 0;
        shown.sort((a, b) -> a.originalY != b.originalY ? Integer.compare(a.originalY, b.originalY) : Integer.compare(a.originalX, b.originalX));
        int[] g = grid(shown.size(), OSRS_TOP_WIDTH, OSRS_TOP_HEIGHT, resize);
        int columns = g[0];
        int size = g[2];
        int gapX = g[3];
        int gapY = g[4];
        int left = (area.width - g[5]) / 2;
        int top = (OSRS_TOP_HEIGHT - g[6]) / 2;
        for (int i = 0; i < shown.size(); i++) {
            Component spell = shown.get(i);
            int x = left + (i % columns) * (size + gapX);
            int y = top + (i / columns) * (size + gapY);
            if (spell.originalX != x || spell.originalY != y || spell.originalWidth != size || spell.originalHeight != size) {
                spell.originalX = x;
                spell.originalY = y;
                spell.originalWidth = size;
                spell.originalHeight = size;
                InterfaceManager.resizeAndReposition(spell);
                InterfaceManager.redraw(spell);
            }
        }
        InterfaceManager.redraw(area);
        trace("osrs grid book=" + iface + " spells=" + shown.size() + " columns=" + columns + " rows=" + g[1] + " size=" + size + " gaps=" + gapX + "," + gapY + " resize=" + resize);
    }

    /**
     * OSRS script 2611 for [count] spells in a [w] x [h] top: {columns, rows, icon size, gap x, gap y, grid width, grid height}.
     * Resizing: 1-15 spells 40 px in 3 columns, 16-20 40 px in 4, 21-36 24 px in 4, 37-45 in 5, 46-54 in 6, more in 7; gaps at most
     * 5/7 of the icon. Resizing off: 24 px, 4 columns up to 28 spells, else (count + 8) / 9 kept within 4..7; gaps at most 24, the grid
     * at least the top minus 30 high.
     */
    static int[] grid(int count, int w, int h, boolean resize) {
        int columns;
        int size;
        if (resize) {
            if (count <= 15) {
                size = 40;
                columns = 3;
            } else if (count <= 20) {
                size = 40;
                columns = 4;
            } else {
                size = 24;
                columns = count <= 36 ? 4 : count <= 45 ? 5 : count <= 54 ? 6 : 7;
            }
        } else {
            size = 24;
            columns = count <= 28 ? 4 : Math.max(4, Math.min(7, (count + 8) / 9));
        }
        int rows = Math.max(1, (count + columns - 1) / columns);
        int maxGap = resize ? 5 * size / 7 : 24;
        int gapX = columns > 1 ? Math.max(0, Math.min(maxGap, (w - size * columns) / (columns - 1))) : 0;
        int gapY = rows > 1 ? Math.max(0, Math.min(gapX, (h - size * rows) / (rows - 1))) : 0;
        int gridWidth = columns * size + (columns - 1) * gapX;
        int gridHeight = rows * size + (rows - 1) * gapY;
        if (!resize) {
            gridHeight = Math.max(gridHeight, h - 30);
        }
        return new int[] {columns, rows, size, gapX, gapY, gridWidth, gridHeight};
    }

    /** One client-log line per menu draw / click (the evidence: "[spellfilters]"). */
    static void trace(String line) {
        System.out.println("[spellfilters] " + line);
    }

    static boolean noneShown(int iface) {
        for (Integer hash : layoutSpells) {
            if ((hash >>> 16) != iface) {
                continue;
            }
            Component spell = InterfaceList.getComponent(hash, -1);
            if (spell != null && !spell.hidden) {
                return false;
            }
        }
        return true;
    }

    /** OSRS 2608 / 2609 / 835: row light under the mouse / after a click, the button label white under the mouse. */
    static void hover() {
        RspsFilterUi.hoverLabel(filtersButton);
        long now = System.currentTimeMillis();
        for (int i = 0; i < OPTIONS.length; i++) {
            RspsFilterUi.light(optionHighlights[i], optionRows[i], true, clickedRow == i && now - clickedAt < 800);
        }
    }

    // ---------------------------------------------------------------------------------------------------- menu + clicks

    /** InterfaceManager.addMiniMenuOptions: the tab-stone op, the Filters button / menu rows, Hide / Unhide on the spells. */
    static void addOptions(Component component) {
        if (component == null) {
            return;
        }
        if (component.id == -1 && (component.slot == TAB_FIXED || component.slot == TAB_RESIZABLE)) {
            String verb = enabled() ? "Disable spell filtering" : "Enable spell filtering";
            RspsSpellbookEdit.addResetOption(component); // N497: added first = listed under the switch
            MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, verb, ACTION_TAB + LOW, true, -1, "", 0L, false);
            return;
        }
        if (filtersButton != null && component == filtersButton) {
            MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, "Spell Filters", ACTION_FILTERS, true, -1, "", 0L, false);
            return;
        }
        for (int i = 0; i < optionRows.length; i++) {
            if (optionRows[i] != null && component == optionRows[i]) {
                MiniMenu.addEntryInner(false, -1, i + 1, component.id, component.slot, "Change", ACTION_OPTION, true, -1, "", 0L, false);
                return;
            }
        }
        RspsSpellbookEdit.addOptions(component);
    }

    /** MiniMenu action dispatch (action already without the low-priority offset). */
    static boolean handle(int action, long v1) {
        if (RspsSpellbookEdit.handle(action, v1)) {
            return true;
        }
        if (action == ACTION_TAB) {
            send(0);
            return true;
        }
        if (action == ACTION_FILTERS) {
            panelOpen = !panelOpen;
            RspsFilterUi.clickSound(); // OSRS 2602: opening and closing the menu click too
            relayout();
            return true;
        }
        if (action == ACTION_OPTION) {
            int line = (int) v1 - 1;
            if (line < 0 || line >= OPTIONS.length) {
                return true;
            }
            clickedRow = line;
            clickedAt = System.currentTimeMillis();
            RspsFilterUi.clickSound(); // OSRS 2607
            send(line + 1);
            return true;
        }
        return false;
    }

    static void send(int slot) {
        int[] b = book(currentBook());
        if (b == null || MainLogicManager.step != 11) {
            return;
        }
        ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON1, ServerConnection.GAME.isaac);
        ClientMessage.addComponentData((b[0] << 16) | b[1], -1, message, slot);
        ServerConnection.GAME.send(message);
        trace("send " + b[0] + ":" + b[1] + " slot=" + slot);
    }
}
