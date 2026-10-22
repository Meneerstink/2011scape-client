import com.jagex.ClientProt;
import com.jagex.game.runetek6.config.vartype.TimedVarDomain;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * N72 (owner 2026-10-16 19:43: "magic spellbook needs to exact same as osrs style layout options"): the OSRS spellbook filters on
 * the three 667 books (Standard 192, Ancient 193, Lunar 430).
 *
 * OSRS (Wiki "Spellbook"; cache 2686 scripts 907 / 2603 / 2611, interface 218): the magic tab stone's right-click "Enable spell
 * filtering" / "Disable spell filtering"; while enabled a "Filters" button at the bottom of the book opens the filter menu
 * ("Show Combat spells", "Show Teleport spells", "Show Utility spells", "Show spells you lack the Magic level to cast", "Show spells
 * you lack the runes to cast", "Show spells you lack the requirements to cast"); the remaining spells close up; "No spells match
 * your selected filters." when none is left.
 *
 * The state is the server's (SpellFilters.kt, varp 1376, saved with the account): bits 9-17 are the 667 category filters the
 * cache's own layout script 2059 already applies; bits 18-25 are the OSRS state read here. This class adds what 667 never had:
 * <ul>
 * <li>lacks level / runes / requirements: the cache's per-spell redraw script 21 (args: component, lit sprite, dark sprite, level,
 * base-level flag, 4x rune + amount) decides "castable". A probe run of script 21 with level 0 and marker sprites (graphic not
 * applied) tells runes (it failed after calling the rune counter, script 19 - pouch and staffs included) from requirements;
 * the level test is script 21's own first check. Spells are re-probed whenever the client re-runs their script 21 (stat, inv,
 * var changes);</li>
 * <li>layout: inside script 2059 the category lookup (enum 727) of a spell this class hides is answered with a category whose
 * array entry reads "hidden", so the cache script itself closes the gaps and sizes the scroll area;</li>
 * <li>the tab-stone op (548:136 fixed, 746:46 resizable, "Magic Spellbook"), the Filters button and the menu, drawn as dynamic
 * components in the book's bottom-bar layer. The 667 Filter / Sort / Defensive Casting row is hidden (OSRS has none).</li>
 * </ul>
 * Every click goes to the server as IF_BUTTON1 on the book's full-size layer (param 688) with the action as slot.
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
    /** OSRS line 7 (varbit 6548): bigger icons for the spells that are left, outside PvP areas. Set = OFF (server SpellFilters:
     *  0 is the OSRS default, resizing on), like every "hide" bit above. */
    static final int BIT_ICON_RESIZING_OFF = 25;

    static final int SPELL_SCRIPT = 21;
    static final int RUNE_SCRIPT = 19;
    static final int LAYOUT_SCRIPT = 2059;
    static final int CATEGORY_ENUM = 727;
    static final int CLIENT_SPELLBOOK_VARBIT = 357;
    static final int MAGIC = 6;

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
    static final int MENU_WIDTH = 180;
    static final int MENU_PAD = 28;
    static final int LINE = 25;
    /** Height of the spell area (layer 0 of every book). */
    static final int SPELL_AREA = 229;
    static final int ORANGE = 0xff981f;
    static final int FONT = 494;

    /** {interface, menu layer (struct param 688), bottom-bar sprite, first Filter, first Sort, Defensive Casting}. */
    static final int[][] BOOKS = {
        {192, 97, 21, 7, 15, 2},
        {193, 54, 15, 5, 9, 18},
        {430, 66, 17, 5, 11, 20},
    };

    /** OSRS script 2603 texts, menu slot 1-6 (slot 0 = enable/disable). */
    static final String[] OPTIONS = {
        "Show <col=ffffff>Combat</col> spells",
        "Show <col=ffffff>Teleport</col> spells",
        "Show <col=ffffff>Utility</col> spells",
        "Show spells you lack the Magic level to cast",
        "Show spells you lack the runes to cast",
        "Show spells you lack the requirements to cast",
        "Enable icon resizing (outside PvP areas)",
    };
    static final int[] OPTION_BITS = {BIT_HIDE_COMBAT, BIT_HIDE_TELEPORT, BIT_HIDE_UTILITY, BIT_HIDE_LEVEL, BIT_HIDE_RUNES, BIT_HIDE_REQUIREMENTS, BIT_ICON_RESIZING_OFF};

    /** Menu line [i] shows the tick while its bit is clear (every bit is an "off" / "hide" bit). */
    static boolean optionTicked(int i) {
        return !bit(OPTION_BITS[i]);
    }

    /** Script 21 args per spell component hash (the last real run). */
    static final Map<Integer, int[]> args = new HashMap<>();
    static final Map<Integer, Integer> status = new HashMap<>();
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

    static Component filtersButton;
    static final Component[] optionLines = new Component[OPTIONS.length];
    static final Component[] optionBoxes = new Component[OPTIONS.length];

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

    static boolean lacksLevel(int[] a, int boosted, int base) {
        int level = a[3];
        return boosted < level && !(a[4] != 0 && base >= level);
    }

    /** True when the OSRS filters hide spell [hash]; the panel being open hides the whole grid. */
    static boolean hidden(int hash) {
        if (!enabled()) {
            return false;
        }
        if (panelOpen) {
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
        return s == LACKS_RUNES && bit(BIT_HIDE_RUNES) || s == LACKS_REQUIREMENTS && bit(BIT_HIDE_REQUIREMENTS);
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

    /** ENUM in script 2059: the category of spell [key]; a spell this class hides gets a category whose array entry is read as hidden. */
    static int layoutCategory(int scriptId, int enumId, int key, int value) {
        if (scriptId != LAYOUT_SCRIPT || enumId != CATEGORY_ENUM) {
            return value;
        }
        layoutSpells.add(key);
        boolean hide = hidden(key);
        laidOut.put(key, hide);
        if (!hide) {
            return value;
        }
        forceHide = true;
        return value < 0 ? 0 : value;
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
        // The book's own var trigger does not re-run the layout for every bit of varp 1376 (the OSRS bits 18-25 are new):
        // a changed varp re-lays the open book out here.
        int now = varp();
        if (now != lastVarp) {
            lastVarp = now;
            relayout();
        }
        if (!dirty.isEmpty() && enabled()) {
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

    /** After every layout: the 667 bottom row away, the Filters button / menu / "No spells match" text in the bar layer. */
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
            optionLines[i] = null;
            optionBoxes[i] = null;
        }
        resizeIcons(iface, enabled() && !panelOpen && !bit(BIT_ICON_RESIZING_OFF) && !DeadmanSkullHud.inPvpArea());
        if (!enabled()) {
            panelOpen = false;
            InterfaceManager.redraw(layer);
            trace("decorate book=" + iface + " varp=0x" + Integer.toHexString(varp()) + " filtering off");
            return;
        }
        // The Filters button, bottom centre (wiki image "Spellbook_filtering.png").
        Component frame = create(layer, Component.TYPE_RECTANGLE);
        place(frame, 63, 224, 64, 18);
        frame.colour = 0x0e0e0c;
        frame.filled = true;
        frame.transparency = 80;
        Component border = create(layer, Component.TYPE_RECTANGLE);
        place(border, 63, 224, 64, 18);
        border.colour = panelOpen ? 0xffffff : 0x726451;
        filtersButton = text(layer, "Filters", 63, 224, 64, 18, 1);
        // InterfaceManager.logicComponentList only hit-tests (hover, menu entries) components with hasHook / events / layers.
        filtersButton.hasHook = true;
        if (panelOpen) {
            // OSRS script 2603: a 180 wide box, 28 + 25 per line high; script 2604 per line: the 17x17 tick / cross at (5, y + 4),
            // the text (font 494, 0xff981f, shadow, left / centre) from x 25 to 5 from the right, 25 high.
            int boxHeight = MENU_PAD + LINE * OPTIONS.length;
            int boxX = (190 - MENU_WIDTH) / 2;
            int boxY = Math.max(0, (SPELL_AREA - boxHeight) / 2);
            Component back = create(layer, Component.TYPE_RECTANGLE);
            place(back, boxX, boxY, MENU_WIDTH, boxHeight);
            back.colour = 0x3e3529;
            back.filled = true;
            Component edge = create(layer, Component.TYPE_RECTANGLE);
            place(edge, boxX, boxY, MENU_WIDTH, boxHeight);
            edge.colour = 0x726451;
            for (int i = 0; i < OPTIONS.length; i++) {
                int y = boxY + MENU_PAD / 2 + i * LINE;
                boolean shown = optionTicked(i);
                Component tick = create(layer, Component.TYPE_GRAPHIC);
                place(tick, boxX + 5, y + 4, 17, 17);
                tick.graphic = shown ? RspsSprites.RES_FILTER_ON : RspsSprites.RES_FILTER_OFF;
                tick.hasHook = true;
                optionBoxes[i] = tick;
                optionLines[i] = text(layer, OPTIONS[i], boxX + 25, y, MENU_WIDTH - 30, LINE, 0);
                optionLines[i].hasHook = true;
            }
        } else if (!layoutSpells.isEmpty() && noneShown(iface)) {
            text(layer, "No spells match your selected filters.", 10, 90, 170, 40, 1);
        }
        InterfaceManager.redraw(layer);
        trace("decorate book=" + iface + " varp=0x" + Integer.toHexString(varp()) + " panel=" + panelOpen + " layer=" + (layer.slot >>> 16) + ":"
            + (layer.slot & 0xFFFF) + " children=" + (layer.staticComponents == null ? 0 : layer.staticComponents.length) + " spells=" + layoutSpells.size());
    }

    /** The cache size of every spell icon, kept the first time this class sees it (icon resizing changes the live size). */
    static final Map<Integer, int[]> normalSize = new HashMap<>();

    /**
     * N72 line 7, "Enable icon resizing (outside PvP areas)" (OSRS script 2611 after varbit 6548 and the PvP check): the spells left
     * after the filters are laid out again in a grid of 4 columns under 28 spells, else (count + 8) / 9 columns kept within 4..7, each
     * icon square and as big as its cell allows, in the order the cache layout put them. Off (or in a PvP area, or with the menu open)
     * every icon gets its cache size back; the cache layout script 2059 places them again.
     */
    static void resizeIcons(int iface, boolean on) {
        java.util.List<Component> shown = new java.util.ArrayList<>();
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
        if (!on || shown.isEmpty()) {
            return;
        }
        Component area = InterfaceList.list(shown.get(0).layer);
        if (area == null) {
            return;
        }
        shown.sort((a, b) -> a.originalY != b.originalY ? Integer.compare(a.originalY, b.originalY) : Integer.compare(a.originalX, b.originalX));
        int[] grid = iconGrid(shown.size(), area.width, area.height);
        int columns = grid[0];
        int size = grid[1];
        int cellWidth = grid[2];
        int cellHeight = grid[3];
        int[] normal = normalSize.get(shown.get(0).slot);
        if (normal != null && size <= Math.max(normal[0], normal[1])) {
            return; // resizing only ever makes icons bigger; a full book keeps the cache layout
        }
        for (int i = 0; i < shown.size(); i++) {
            int x = (i % columns) * cellWidth + (cellWidth - size) / 2;
            int y = (i / columns) * cellHeight + (cellHeight - size) / 2;
            place(shown.get(i), x, y, size, size);
            InterfaceManager.redraw(shown.get(i));
        }
        trace("icon resizing book=" + iface + " spells=" + shown.size() + " columns=" + columns + " size=" + size);
    }

    /** {columns, icon size, cell width, cell height} for [count] icons in a [width] x [height] area (OSRS 2611 column rule). */
    static int[] iconGrid(int count, int width, int height) {
        int columns = count < 28 ? 4 : Math.max(4, Math.min(7, (count + 8) / 9));
        int rows = Math.max(1, (count + columns - 1) / columns);
        int cellWidth = width / columns;
        int cellHeight = height / rows;
        int size = Math.max(1, Math.min(cellWidth, cellHeight) - 2);
        return new int[] {columns, size, cellWidth, cellHeight};
    }

    /** One client-log line per menu draw / click (the evidence the Keurmeester greps: "[spellfilters]"). */
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

    static Component text(Component layer, String s, int x, int y, int w, int h, int alignX) {
        Component t = create(layer, Component.TYPE_TEXT);
        place(t, x, y, w, h);
        t.text = s;
        t.fontGraphic = FONT;
        t.colour = ORANGE;
        t.textShadow = true;
        t.textAlignX = alignX;
        t.textAlignY = 1;
        return t;
    }

    /** CC_CREATE in Java (ScriptRunner.handleSmallOp CC_CREATE), next free index. */
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
        return cc;
    }

    static void place(Component c, int x, int y, int w, int h) {
        c.originalX = x;
        c.originalY = y;
        c.originalWidth = w;
        c.originalHeight = h;
        InterfaceManager.resizeAndReposition(c);
    }

    // ---------------------------------------------------------------------------------------------------- menu + clicks

    /** InterfaceManager.addMiniMenuOptions: the tab-stone op and the Filters button / menu lines. */
    static void addOptions(Component component) {
        if (component == null) {
            return;
        }
        if (component.id == -1 && (component.slot == TAB_FIXED || component.slot == TAB_RESIZABLE)) {
            String verb = enabled() ? "Disable spell filtering" : "Enable spell filtering";
            MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, verb, ACTION_TAB + LOW, true, -1, "", 0L, false);
            return;
        }
        if (component == filtersButton) {
            MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, "Spell Filters", ACTION_FILTERS, true, -1, "", 0L, false);
            return;
        }
        for (int i = 0; i < optionLines.length; i++) {
            if (component == optionLines[i] || component == optionBoxes[i]) {
                MiniMenu.addEntryInner(false, -1, i + 1, component.id, component.slot, "Toggle", ACTION_OPTION, true, -1, "", 0L, false);
                return;
            }
        }
    }

    /** MiniMenu action dispatch (action already without the low-priority offset). */
    static boolean handle(int action, long v1) {
        if (action == ACTION_TAB) {
            send(0);
            return true;
        }
        if (action == ACTION_FILTERS) {
            panelOpen = !panelOpen;
            relayout();
            return true;
        }
        if (action == ACTION_OPTION) {
            send((int) v1);
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
