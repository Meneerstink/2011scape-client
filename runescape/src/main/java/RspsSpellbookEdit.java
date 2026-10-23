import rs2.client.event.mouse.MouseLog;
import rs2.client.event.mouse.MouseMonitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * N189 (owner 2026-10-22: "spells verbergen en verslepen/herordenen zoals de RuneLite Spellbook-plugin ... volgorde wordt onthouden,
 * per boek, samen met spell filtering"): RuneLite's Spellbook plugin on the three 667 books.
 * <ul>
 *   <li>The magic tab stone gets "Edit spellbook" (and "Finish editing" / "Reset spellbook" while editing), like the plugin's
 *       unlock on the tab.</li>
 *   <li>While editing every spell is shown, hidden ones dimmed; drag a spell onto another to put it there; right-click a spell for
 *       "Hide" / "Unhide".</li>
 *   <li>Outside editing hidden spells leave the book and the others close up (the same layout route as the OSRS filters,
 *       {@link RspsSpellFilters#hidden}), in the player's own order.</li>
 * </ul>
 * Kept per book on the player's machine ({@code spellbook.properties} beside RuneLite-style {@code overlays.properties}), as
 * RuneLite keeps its plugin config.
 */
final class RspsSpellbookEdit {

    static final int ACTION_EDIT = 1973;
    static final int ACTION_RESET = 1974;
    static final int ACTION_HIDE = 1975;
    private static final int LOW = 2000;
    /** Transparency of a hidden spell while editing (0 opaque .. 255 invisible). */
    static final int DIMMED = 170;

    static boolean editing;

    /** Per book: the player's order (component hashes) and hidden spells. */
    static final Map<Integer, List<Integer>> order = new HashMap<>();
    static final Map<Integer, Set<Integer>> hiddenSpells = new HashMap<>();
    private static boolean loaded;

    /** Screen bounds of every spell drawn this frame: hash -> {x, y, w, h}. */
    static final Map<Integer, int[]> drawn = new HashMap<>();
    static int dragging = -1;

    private RspsSpellbookEdit() {
    }

    // ------------------------------------------------------------------------------------------------------- state

    static boolean isSpellOfBook(int hash) {
        return RspsSpellFilters.layoutSpells.contains(hash);
    }

    /** Outside editing: the player hid this spell. */
    static boolean userHidden(int hash) {
        load();
        if (editing) {
            return false;
        }
        Set<Integer> set = hiddenSpells.get(hash >>> 16);
        return set != null && set.contains(hash);
    }

    static boolean isHidden(int hash) {
        load();
        Set<Integer> set = hiddenSpells.get(hash >>> 16);
        return set != null && set.contains(hash);
    }

    static void toggleHidden(int hash) {
        load();
        Set<Integer> set = hiddenSpells.computeIfAbsent(hash >>> 16, k -> new LinkedHashSet<>());
        if (!set.remove(hash)) {
            set.add(hash);
        }
        save();
    }

    /**
     * The shown spells of a book in the player's order: the saved order first (spells still shown), then every other shown spell in
     * the cache's own order.
     */
    static List<Integer> ordered(List<Integer> cacheOrder, List<Integer> saved) {
        List<Integer> out = new ArrayList<>();
        if (saved != null) {
            for (Integer hash : saved) {
                if (cacheOrder.contains(hash) && !out.contains(hash)) {
                    out.add(hash);
                }
            }
        }
        for (Integer hash : cacheOrder) {
            if (!out.contains(hash)) {
                out.add(hash);
            }
        }
        return out;
    }

    /** Moves [moved] to the place of [target] in [list] (the rest shifts one place). */
    static List<Integer> move(List<Integer> list, int moved, int target) {
        List<Integer> out = new ArrayList<>(list);
        if (moved == target || !out.contains(moved) || !out.contains(target)) {
            return out;
        }
        int to = out.indexOf(target);
        out.remove(Integer.valueOf(moved));
        out.add(to, moved);
        return out;
    }

    // ------------------------------------------------------------------------------------- after every cache layout

    /**
     * RspsSpellFilters.decorate, before the icon resizing: the shown spells take the cache's places in the player's order, and while
     * editing the hidden ones are dimmed.
     */
    static void apply(int iface) {
        load();
        List<Component> shown = new ArrayList<>();
        for (Integer hash : RspsSpellFilters.layoutSpells) {
            if ((hash >>> 16) != iface) {
                continue;
            }
            Component spell = InterfaceList.getComponent(hash, -1);
            if (spell == null) {
                continue;
            }
            int wanted = editing && isHidden(hash) ? DIMMED : 0;
            if (spell.transparency != wanted) {
                spell.transparency = wanted;
                InterfaceManager.redraw(spell);
            }
            if (!spell.hidden) {
                shown.add(spell);
            }
        }
        if (shown.size() < 2) {
            return;
        }
        shown.sort((a, b) -> a.originalY != b.originalY ? Integer.compare(a.originalY, b.originalY) : Integer.compare(a.originalX, b.originalX));
        List<Integer> cacheOrder = new ArrayList<>();
        List<int[]> places = new ArrayList<>();
        Map<Integer, Component> byHash = new HashMap<>();
        for (Component spell : shown) {
            cacheOrder.add(spell.slot);
            places.add(new int[] {spell.originalX, spell.originalY});
            byHash.put(spell.slot, spell);
        }
        List<Integer> mine = ordered(cacheOrder, order.get(iface));
        for (int i = 0; i < mine.size(); i++) {
            Component spell = byHash.get(mine.get(i));
            int[] at = places.get(i);
            if (spell.originalX != at[0] || spell.originalY != at[1]) {
                spell.originalX = at[0];
                spell.originalY = at[1];
                InterfaceManager.resizeAndReposition(spell);
                InterfaceManager.redraw(spell);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- input

    /** InterfaceManager.draw: a spell of the open book was drawn at [x], [y]. */
    static void drawn(Component child, int x, int y) {
        if (editing && isSpellOfBook(child.slot)) {
            drawn.put(child.slot, new int[] {x, y, child.width, child.height});
        }
    }

    private static int spellAt(int x, int y) {
        for (Map.Entry<Integer, int[]> e : drawn.entrySet()) {
            int[] r = e.getValue();
            if ((e.getKey() >>> 16) == RspsSpellFilters.currentBook() && x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3]) {
                return e.getKey();
            }
        }
        return -1;
    }

    /** client.java input loop: a left press on a spell while editing starts a drag (the game never sees it). */
    static boolean mousePress(MouseLog log) {
        if (!editing || log.getType() != MouseLog.TYPE_PRESS_LEFT || !RspsSpellFilters.bookShown(RspsSpellFilters.currentBook())) {
            return false;
        }
        int hash = spellAt(log.getX(), log.getY());
        if (hash == -1) {
            return false;
        }
        dragging = hash;
        return true;
    }

    /** Once per client loop: a drag ends when the button is released, over the spell it lands on. */
    static void tick() {
        if (dragging == -1 || MouseMonitor.instance == null || MouseMonitor.instance.isLeftDown()) {
            return;
        }
        int moved = dragging;
        dragging = -1;
        int target = spellAt(MouseMonitor.instance.getRecordedX(), MouseMonitor.instance.getRecordedY());
        if (target == -1 || target == moved || (target >>> 16) != (moved >>> 16)) {
            return;
        }
        int iface = moved >>> 16;
        // The order on screen (every spell is shown while editing), top to bottom, left to right: move within that and keep it.
        List<Component> spells = new ArrayList<>();
        for (Integer hash : RspsSpellFilters.layoutSpells) {
            Component spell = (hash >>> 16) == iface ? InterfaceList.getComponent(hash, -1) : null;
            if (spell != null && !spell.hidden) {
                spells.add(spell);
            }
        }
        spells.sort((a, b) -> a.originalY != b.originalY ? Integer.compare(a.originalY, b.originalY) : Integer.compare(a.originalX, b.originalX));
        List<Integer> onScreen = new ArrayList<>();
        for (Component spell : spells) {
            onScreen.add(spell.slot);
        }
        List<Integer> next = move(onScreen, moved, target);
        // Spells a filter keeps out of the book right now keep their saved place at the end of the list.
        List<Integer> saved = order.get(iface);
        if (saved != null) {
            for (Integer hash : saved) {
                if (!next.contains(hash)) {
                    next.add(hash);
                }
            }
        }
        order.put(iface, next);
        save();
        RspsSpellFilters.relayout();
    }

    // ------------------------------------------------------------------------------------------------- menu

    /** InterfaceManager.addMiniMenuOptions (through RspsSpellFilters.addOptions). True when it added the component's entries. */
    static boolean addOptions(Component component) {
        if (component.id == -1 && (component.slot == RspsSpellFilters.TAB_FIXED || component.slot == RspsSpellFilters.TAB_RESIZABLE)) {
            MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, editing ? "Finish editing" : "Edit spellbook", ACTION_EDIT + LOW, true, -1, "", 0L, false);
            if (editing) {
                MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, "Reset spellbook", ACTION_RESET + LOW, true, -1, "", 0L, false);
            }
            return false;
        }
        if (editing && isSpellOfBook(component.slot)) {
            MiniMenu.addEntryInner(false, -1, component.slot, component.id, component.slot, isHidden(component.slot) ? "Unhide" : "Hide", ACTION_HIDE, true, -1, "", 0L, false);
            return true;
        }
        return false;
    }

    static boolean handle(int action, long v1) {
        if (action == ACTION_EDIT) {
            editing = !editing;
            dragging = -1;
            drawn.clear();
            RspsSpellFilters.relayout();
            return true;
        }
        if (action == ACTION_RESET) {
            int iface = RspsSpellFilters.currentBook();
            order.remove(iface);
            hiddenSpells.remove(iface);
            save();
            RspsSpellFilters.relayout();
            return true;
        }
        if (action == ACTION_HIDE) {
            toggleHidden((int) v1);
            RspsSpellFilters.relayout();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------- storage

    static File file() {
        return new File(RspsOverlayLayout.file().getParentFile(), "spellbook.properties");
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
        for (String key : p.stringPropertyNames()) {
            String[] parts = key.split("\\.");
            if (parts.length != 2) {
                continue;
            }
            try {
                int iface = Integer.parseInt(parts[0]);
                List<Integer> hashes = new ArrayList<>();
                for (String s : p.getProperty(key).split(",")) {
                    if (!s.trim().isEmpty()) {
                        hashes.add((iface << 16) | Integer.parseInt(s.trim()));
                    }
                }
                if (parts[1].equals("order")) {
                    order.put(iface, hashes);
                } else if (parts[1].equals("hidden")) {
                    hiddenSpells.put(iface, new LinkedHashSet<>(hashes));
                }
            } catch (NumberFormatException ignored) {
                // a hand-edited line: skip it
            }
        }
    }

    static void save() {
        Properties p = new Properties();
        for (Map.Entry<Integer, List<Integer>> e : order.entrySet()) {
            p.setProperty(e.getKey() + ".order", components(e.getValue()));
        }
        for (Map.Entry<Integer, Set<Integer>> e : hiddenSpells.entrySet()) {
            p.setProperty(e.getKey() + ".hidden", components(e.getValue()));
        }
        File file = file();
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "78 client: spellbook order and hidden spells per book (interface.order / interface.hidden = component ids)");
        } catch (IOException ignored) {
            // not saved: the order still holds for this session
        }
    }

    private static String components(Iterable<Integer> hashes) {
        StringBuilder sb = new StringBuilder();
        for (Integer h : hashes) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(h & 0xFFFF);
        }
        return sb.toString();
    }
}
