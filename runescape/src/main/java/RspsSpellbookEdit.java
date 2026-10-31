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
 * N189 / N72 (owner 2026-10-22 "spells verbergen en verslepen/herordenen zoals de RuneLite Spellbook-plugin ... volgorde wordt onthouden,
 * per boek"; 2026-10-31 18:2x DEFINITIEF: "met enable spellfiltering kun je vervolgens rechtermuisknop op spell doen hide unhide en je
 * kunt em ook verslepen"): RuneLite's Spellbook plugin inside the OSRS spell filtering of the three 667 books.
 * <ul>
 *   <li>With spell filtering enabled (= editing, N495 owner 2026-10-31 20:5x) every spell of the open book has right-click "Hide" /
 *       "Unhide" and a hidden spell stays, half see-through; Shift + drag puts a spell in another spell's place ({@link RspsIconDrag}).</li>
 *   <li>Filtering disabled (= done): the hidden spells leave the book (the same layout route as the OSRS filters,
 *       {@link RspsSpellFilters#hidden}); the player's order stays.</li>
 *   <li>Right-click on the magic tab stone: "Reset spellbook layout" (N497: every spell back, the cache's order) for the open book.</li>
 * </ul>
 * Kept per book on the player's machine ({@code spellbook.properties} beside RuneLite-style {@code overlays.properties}), as RuneLite
 * keeps its plugin config.
 */
final class RspsSpellbookEdit {

    static final int ACTION_RESET = 1974;
    static final int ACTION_HIDE = 1975;
    private static final int LOW = 2000;
    /** Transparency of a hidden spell while shown (0 opaque .. 255 invisible). */
    static final int DIMMED = 170;

    /** Per book: the player's order (component hashes) and hidden spells. */
    static final Map<Integer, List<Integer>> order = new HashMap<>();
    static final Map<Integer, Set<Integer>> hiddenSpells = new HashMap<>();
    private static boolean loaded;

    private RspsSpellbookEdit() {
    }

    // ------------------------------------------------------------------------------------------------------- state

    static boolean isSpellOfBook(int hash) {
        return RspsSpellFilters.layoutSpells.contains(hash);
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

    /** Moving / hiding is on: filtering enabled, no filter menu open, the book on screen. */
    static boolean editable(int iface) {
        return RspsSpellFilters.enabled() && !RspsSpellFilters.panelOpen && iface == RspsSpellFilters.currentBook() && RspsSpellFilters.bookShown(iface);
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
     * RspsSpellFilters.decorate, before the OSRS grid: the shown spells take the cache's places in the player's order (N495: enabled
     * or not); while editing the hidden ones (still shown) are half see-through.
     */
    static void apply(int iface) {
        load();
        boolean editing = RspsSpellFilters.enabled();
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

    // ------------------------------------------------------------------------------------------------- moving

    /** RspsIconDrag: [c] is a spell of the open book that may be moved now. */
    static boolean movable(Component c) {
        return c != null && c.id == -1 && isSpellOfBook(c.slot) && editable(c.slot >>> 16);
    }

    /** RspsIconDrag: spell [moved] dropped on spell [target] - it takes the target's place in the order on screen. */
    static void drop(int moved, int target) {
        if (moved == target || (target >>> 16) != (moved >>> 16)) {
            return;
        }
        int iface = moved >>> 16;
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
        RspsSpellFilters.trace("moved " + (moved >>> 16) + ":" + (moved & 0xFFFF) + " to the place of " + (target & 0xFFFF));
        RspsSpellFilters.relayout();
    }

    // ------------------------------------------------------------------------------------------------- menu

    /** InterfaceManager.addMiniMenuOptions (through RspsSpellFilters.addOptions): Hide / Unhide under the spell's own options. */
    static void addOptions(Component component) {
        if (component.id == -1 && isSpellOfBook(component.slot) && editable(component.slot >>> 16)) {
            MiniMenu.addEntryInner(false, -1, component.slot, component.id, component.slot, isHidden(component.slot) ? "Unhide" : "Hide", ACTION_HIDE + LOW, true, -1, "", 0L, false);
        }
    }

    /** N497, the magic tab stone's right-click: the open book's order and hidden spells back to the cache's. */
    static void addResetOption(Component component) {
        MiniMenu.addEntryInner(false, -1, 0L, component.id, component.slot, "Reset spellbook layout", ACTION_RESET + LOW, true, -1, "", 0L, false);
    }

    static boolean handle(int action, long v1) {
        if (action == ACTION_RESET) {
            int iface = RspsSpellFilters.currentBook();
            load();
            order.remove(iface);
            hiddenSpells.remove(iface);
            save();
            RspsSpellFilters.trace("reset book " + iface);
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
