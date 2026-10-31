import rs2.client.event.keyboard.KeyboardMonitor;
import rs2.client.event.mouse.MouseLog;
import rs2.client.event.mouse.MouseMonitor;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * N482 / N72 (owner 2026-10-31: with filtering enabled prayers and spells "kun je verslepen", RuneLite PrayerReorder /
 * SpellbookPlugin): moving a prayer or a spell to another place of its grid.
 *
 * Shift + drag: a plain click must stay a click on the press (1-tick prayer flicking, Vengeance, barrage switching - the server
 * handles a prayer click on its own tick), so a drag only starts with Shift held; the press then never reaches the game. While
 * dragging (past 5 px, the OSRS drag dead zone) the icon follows the mouse half see-through and the icon under the mouse - where it
 * lands - is lit; releasing over another icon of the same grid puts the dragged one in its place, anywhere else changes nothing.
 */
final class RspsIconDrag {

    static final int SHIFT = 81;
    static final int DEAD_ZONE = 5;
    static final int DRAG_TRANSPARENCY = 110;
    static final int TARGET_TRANSPARENCY = 140;

    /** Screen rectangles of the grid icons drawn this frame. */
    static final Map<Component, int[]> drawn = new IdentityHashMap<>();

    static Component dragged;
    static int pressX;
    static int pressY;
    static int startX;
    static int startY;
    static int startTransparency;
    static boolean active;
    static Component target;
    static int targetTransparency;

    private RspsIconDrag() {
    }

    /** A grid icon that can be moved now. */
    static boolean movable(Component c) {
        if (c == null || c.hidden) {
            return false;
        }
        if (RspsPrayerFilters.isPrayer(c)) {
            return RspsPrayerFilters.editable();
        }
        return RspsSpellbookEdit.movable(c);
    }

    /** InterfaceManager.draw: where [child] is on screen this frame. */
    static void drawn(Component child, int x, int y) {
        if (RspsPrayerFilters.isPrayer(child) || RspsSpellbookEdit.isSpellOfBook(child.slot)) {
            drawn.put(child, new int[] {x, y, child.width, child.height});
        }
    }

    /** Whether [c] is still in its grid (a rebuilt grid has new children; the old ones are dropped from {@link #drawn}). */
    static boolean current(Component c) {
        if (RspsPrayerFilters.isPrayer(c)) {
            Component layer = InterfaceList.list(RspsPrayerFilters.ICONS);
            return layer != null && layer.staticComponents != null && c.id < layer.staticComponents.length && layer.staticComponents[c.id] == c;
        }
        return InterfaceList.getComponent(c.slot, -1) == c;
    }

    static Component at(int x, int y, Component except) {
        drawn.keySet().removeIf(c -> !current(c));
        for (Map.Entry<Component, int[]> e : drawn.entrySet()) {
            Component c = e.getKey();
            int[] r = e.getValue();
            if (c != except && !c.hidden && x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3] && (except == null || sameGrid(c, except))) {
                return c;
            }
        }
        return null;
    }

    static boolean sameGrid(Component a, Component b) {
        return RspsPrayerFilters.isPrayer(a) ? RspsPrayerFilters.isPrayer(b) : a.slot >>> 16 == b.slot >>> 16 && !RspsPrayerFilters.isPrayer(b);
    }

    /** client.java input loop: a Shift + left press on a movable icon starts a drag (the game never sees it). */
    static boolean mousePress(MouseLog log) {
        if (log.getType() != MouseLog.TYPE_PRESS_LEFT || KeyboardMonitor.instance == null || !KeyboardMonitor.instance.isPressed(SHIFT)) {
            return false;
        }
        Component c = at(log.getX(), log.getY(), null);
        if (!movable(c)) {
            return false;
        }
        cancel();
        dragged = c;
        pressX = log.getX();
        pressY = log.getY();
        startX = c.originalX;
        startY = c.originalY;
        startTransparency = c.transparency;
        active = false;
        return true;
    }

    /** Once per client loop: the icon follows the mouse; a release ends the drag. */
    static void tick() {
        if (dragged == null || MouseMonitor.instance == null) {
            return;
        }
        int mx = MouseMonitor.instance.getRecordedX();
        int my = MouseMonitor.instance.getRecordedY();
        if (!movable(dragged) && !active) {
            cancel();
            return;
        }
        if (MouseMonitor.instance.isLeftDown()) {
            if (!active && (Math.abs(mx - pressX) > DEAD_ZONE || Math.abs(my - pressY) > DEAD_ZONE)) {
                active = true;
            }
            if (active) {
                follow(mx, my);
            }
            return;
        }
        Component drop = active ? at(mx, my, dragged) : null;
        Component moved = dragged;
        boolean was = active;
        cancel();
        if (was && drop != null) {
            if (RspsPrayerFilters.isPrayer(moved)) {
                RspsPrayerFilters.drop(moved.id, drop.id);
            } else {
                RspsSpellbookEdit.drop(moved.slot, drop.slot);
            }
        }
    }

    /** The dragged icon at the mouse (scaled screen distance back to interface pixels), the landing icon lit. */
    static void follow(int mx, int my) {
        // Mouse and draw positions are both canvas pixels (Stretched Mode maps the mouse before the game sees it).
        int dx = mx - pressX;
        int dy = my - pressY;
        if (dragged.originalX != startX + dx || dragged.originalY != startY + dy) {
            dragged.originalX = startX + dx;
            dragged.originalY = startY + dy;
            InterfaceManager.resizeAndReposition(dragged);
        }
        dragged.transparency = DRAG_TRANSPARENCY;
        InterfaceManager.redraw(dragged);
        Component over = at(mx, my, dragged);
        if (over != target) {
            unlight();
            target = over;
            if (target != null) {
                targetTransparency = target.transparency;
                target.transparency = TARGET_TRANSPARENCY;
                InterfaceManager.redraw(target);
            }
        }
    }

    static void unlight() {
        if (target != null) {
            target.transparency = targetTransparency;
            InterfaceManager.redraw(target);
            target = null;
        }
    }

    static void cancel() {
        unlight();
        if (dragged != null) {
            dragged.originalX = startX;
            dragged.originalY = startY;
            dragged.transparency = startTransparency;
            InterfaceManager.resizeAndReposition(dragged);
            InterfaceManager.redraw(dragged);
        }
        dragged = null;
        active = false;
    }
}
