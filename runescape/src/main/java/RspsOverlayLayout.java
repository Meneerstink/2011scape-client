import com.jagex.graphics.Toolkit;
import rs2.client.event.keyboard.KeyboardMonitor;
import rs2.client.event.mouse.MouseLog;
import rs2.client.event.mouse.MouseMonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Movable overlays, RuneLite style (owner 2026-09-29: "als je alt indrukt kun je ze slepen", also for the Deadman HUD).
 *
 * Every panel asks {@link #place} where to draw itself each frame, giving its default spot, its size and the area it may
 * live in (the viewport for the information panels, the whole canvas for the Deadman HUD). While Alt is held every
 * placed panel gets an outline; Alt + left-drag moves it, Alt + right-click puts it back on its default spot. A press on
 * a panel handled here never reaches the game ({@link #mousePress} runs in the client's input loop before the press is
 * queued), so dragging or clicking a panel never walks or interacts.
 *
 * Positions are kept per player machine, like RuneLite's own overlay positions, in {@code overlays.properties} (the
 * rsps.preferencesDir when set, else ~/.rsps78), as the offset from the top-left of the panel's area, so a panel keeps
 * its place when the window is resized and is always clamped back inside it.
 */
final class RspsOverlayLayout {

    /** Client key code of Alt (SimpleKeyboardMonitor.KEY_CODE_ALT). */
    static final int KEY_ALT = 86;

    private static final int OUTLINE = 0xFFFFFF00;
    private static final int OUTLINE_DRAGGING = 0xFF00FF00;

    /** A panel's bounds in the frame it was last drawn. */
    private static final class Entry {
        int x;
        int y;
        int width;
        int height;
        int areaX;
        int areaY;
        int areaWidth;
        int areaHeight;
        long drawnAt;
    }

    /** A panel counts as on screen (clickable, outlined) this long after it was last drawn. */
    private static final long VISIBLE_MS = 250L;

    private static final Map<String, Entry> drawn = new HashMap<String, Entry>();
    private static final Map<String, int[]> saved = new HashMap<String, int[]>();
    private static boolean loaded;

    private static String dragging;
    private static int grabX;
    private static int grabY;

    private RspsOverlayLayout() {
    }

    static boolean altHeld() {
        return KeyboardMonitor.instance != null && KeyboardMonitor.instance.isPressed(KEY_ALT);
    }

    static boolean isMoved(String id) {
        load();
        return saved.containsKey(id) || id.equals(dragging);
    }

    /**
     * Where to draw panel {@code id} this frame: its saved spot (or, while it is being dragged, under the mouse), else
     * {@code defaultX/defaultY}; always clamped inside the area. Records the bounds for input and outlines.
     */
    static int[] place(String id, int defaultX, int defaultY, int width, int height, int areaX, int areaY, int areaWidth, int areaHeight) {
        int[] wanted = requested(id, defaultX, defaultY, areaX, areaY);
        int x = wanted[0];
        int y = wanted[1];
        x = Math.max(areaX, Math.min(x, areaX + areaWidth - width));
        y = Math.max(areaY, Math.min(y, areaY + areaHeight - height));
        Entry entry = drawn.get(id);
        if (entry == null) {
            entry = new Entry();
            drawn.put(id, entry);
        }
        entry.x = x;
        entry.y = y;
        entry.width = width;
        entry.height = height;
        entry.areaX = areaX;
        entry.areaY = areaY;
        entry.areaWidth = areaWidth;
        entry.areaHeight = areaHeight;
        entry.drawnAt = System.currentTimeMillis();
        return new int[]{x, y};
    }

    /**
     * Where panel {@code id} wants to be this frame before it is clamped: under the mouse while it is dragged, else its
     * saved spot (an offset from the area's top-left), else {@code defaultX/defaultY}. Lets a panel pick the area it is
     * clamped to from the spot the player chose (RuneLite OverlayRenderer: preferred location first, then clamp).
     */
    static int[] requested(String id, int defaultX, int defaultY, int areaX, int areaY) {
        load();
        if (id.equals(dragging) && MouseMonitor.instance != null) {
            return new int[]{MouseMonitor.instance.getRecordedX() - grabX, MouseMonitor.instance.getRecordedY() - grabY};
        }
        int[] offset = saved.get(id);
        if (offset != null) {
            return new int[]{areaX + offset[0], areaY + offset[1]};
        }
        return new int[]{defaultX, defaultY};
    }

    /** Draws the Alt outline of a placed panel (call after drawing the panel itself). */
    static void decorate(String id) {
        Entry entry = drawn.get(id);
        if (entry == null || !visible(entry) || Toolkit.active == null) {
            return;
        }
        if (id.equals(dragging)) {
            Toolkit.active.outlineRect(entry.x - 1, entry.y - 1, entry.width + 2, entry.height + 2, OUTLINE_DRAGGING);
        } else if (altHeld()) {
            Toolkit.active.outlineRect(entry.x - 1, entry.y - 1, entry.width + 2, entry.height + 2, OUTLINE);
        }
    }

    private static boolean visible(Entry entry) {
        return System.currentTimeMillis() - entry.drawnAt < VISIBLE_MS;
    }

    private static boolean contains(Entry entry, int x, int y) {
        return entry != null && visible(entry) && x >= entry.x && y >= entry.y && x < entry.x + entry.width && y < entry.y + entry.height;
    }

    /** The panel under (x, y). */
    private static Entry at(int x, int y) {
        for (Entry entry : drawn.values()) {
            if (contains(entry, x, y)) {
                return entry;
            }
        }
        return null;
    }

    private static String idOf(Entry entry) {
        for (Map.Entry<String, Entry> e : drawn.entrySet()) {
            if (e.getValue() == entry) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * A mouse press from the client's input loop. Returns true when an overlay took it (the game then never sees it).
     */
    static boolean mousePress(MouseLog log) {
        int x = log.getX();
        int y = log.getY();
        int type = log.getType();
        // Ground Items hotkey (RuneLite, Alt by default): hotkey + click on an item's [-] / [+] box hides / highlights it.
        if (type == MouseLog.TYPE_PRESS_LEFT && RspsHudConfig.enabled("grounditems") && RspsHudGroundItems.hotkeyHeld() && RspsHudGroundItems.boxClick(x, y)) {
            return true;
        }
        if (!altHeld()) {
            return false;
        }
        Entry entry = at(x, y);
        if (entry == null) {
            return false;
        }
        String id = idOf(entry);
        if (id == null) {
            return false;
        }
        if (type == MouseLog.TYPE_PRESS_LEFT) {
            dragging = id;
            grabX = x - entry.x;
            grabY = y - entry.y;
        } else if (type == MouseLog.TYPE_PRESS_RIGHT) {
            saved.remove(id);
            save();
        }
        return true;
    }

    /** Once per client loop: ends a drag when the button is released and remembers the spot. */
    static void tick() {
        if (dragging == null || MouseMonitor.instance == null) {
            return;
        }
        if (MouseMonitor.instance.isLeftDown()) {
            return;
        }
        Entry entry = drawn.get(dragging);
        if (entry != null) {
            saved.put(dragging, new int[]{entry.x - entry.areaX, entry.y - entry.areaY});
            save();
        }
        dragging = null;
    }

    static void resetAll() {
        load();
        saved.clear();
        dragging = null;
        save();
    }

    // ---- storage ----------------------------------------------------------------------------------------------------

    static File file() {
        String preferences = System.getProperty("rsps.preferencesDir");
        File dir = preferences != null ? new File(preferences) : new File(System.getProperty("user.home"), ".rsps78");
        return new File(dir, "overlays.properties");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = file();
        if (!file.isFile()) {
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                int eq = line.indexOf('=');
                int comma = line.indexOf(',', eq + 1);
                if (eq <= 0 || comma < 0 || line.startsWith("#")) {
                    continue;
                }
                try {
                    saved.put(line.substring(0, eq).trim(), new int[]{Integer.parseInt(line.substring(eq + 1, comma).trim()), Integer.parseInt(line.substring(comma + 1).trim())});
                } catch (NumberFormatException ignored) {
                    // a hand-edited line: skip it
                }
            }
        } catch (IOException ignored) {
            // no saved positions: every panel on its default spot
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        }
    }

    private static void save() {
        File file = file();
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new FileWriter(file));
            writer.println("# RSPS overlay positions (Alt + drag in the client; Alt + right-click resets one)");
            for (Map.Entry<String, int[]> e : saved.entrySet()) {
                writer.println(e.getKey() + "=" + e.getValue()[0] + "," + e.getValue()[1]);
            }
        } catch (IOException ignored) {
            // positions are a convenience: a failed write keeps them for this session only
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    /** Tests: forget everything in memory and read the file again on next use. */
    static void reloadForTest() {
        loaded = false;
        saved.clear();
        drawn.clear();
        dragging = null;
    }

    /** Tests: simulate a drag of {@code id} to (x, y) ending now. */
    static void dropForTest(String id, int x, int y) {
        Entry entry = drawn.get(id);
        if (entry == null) {
            return;
        }
        saved.put(id, new int[]{x - entry.areaX, y - entry.areaY});
        save();
    }
}
