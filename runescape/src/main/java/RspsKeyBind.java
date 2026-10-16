import rs2.client.event.keyboard.KeyLog;
import rs2.client.event.keyboard.SimpleKeyboardMonitor;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 78Legacy PK Hotkeys / Gear Switcher v3 - key bindings on ANY keyboard key (owner request 2026-10-11).
 *
 * A binding is stored as text, e.g. "SHIFT+F6", "CTRL+SHIFT+Q", "1" or "NONE": the modifiers (always written in the
 * order CTRL, ALT, SHIFT) and the name of a java.awt.event.KeyEvent VK_ constant without "VK_". The sidebar records it by
 * a key press (RspsKeybindButton); the server only checks the format (ClientHudConfig Type.KEY).
 *
 * In game the key loop sees Jagex key codes (SimpleKeyboardMonitor.KEY_MAP[vk]) and modifier flags (SHIFT 1, ALT 2,
 * CTRL 4). A binding matches a PRESSED record with exactly that code and exactly those modifiers. After a match the rest of
 * that physical press (HELD repeats, the TYPED character, the RELEASED record) is swallowed too, so a letter binding never
 * types into the chatbox and never reaches the game.
 */
final class RspsKeyBind {

    static final String NONE = "NONE";
    static final int SHIFT = 0x1, ALT = 0x2, CTRL = 0x4;

    private static final Map<String, Integer> VK_BY_NAME = new HashMap<String, Integer>();
    private static final Map<Integer, String> NAME_BY_VK = new HashMap<Integer, String>();
    /** Parsed bindings: {jagexCode, modifiers}; {0, 0} = none / not readable by the client. */
    private static final Map<String, int[]> PARSED = new ConcurrentHashMap<String, int[]>();

    static {
        for (Field f : KeyEvent.class.getFields()) {
            if (f.getType() == int.class && Modifier.isStatic(f.getModifiers()) && f.getName().startsWith("VK_")) {
                try {
                    int vk = f.getInt(null);
                    String name = f.getName().substring(3);
                    VK_BY_NAME.put(name, vk);
                    if (!NAME_BY_VK.containsKey(vk)) {
                        NAME_BY_VK.put(vk, name);
                    }
                } catch (IllegalAccessException ignored) {
                    // public constants: cannot happen
                }
            }
        }
    }

    private RspsKeyBind() {
    }

    // ---- format ---------------------------------------------------------------------------------------------------------

    /** True for the modifier keys themselves (they are never a binding's key). */
    static boolean isModifierKey(int vk) {
        return vk == KeyEvent.VK_SHIFT || vk == KeyEvent.VK_CONTROL || vk == KeyEvent.VK_ALT || vk == KeyEvent.VK_ALT_GRAPH
            || vk == KeyEvent.VK_META || vk == KeyEvent.VK_WINDOWS || vk == KeyEvent.VK_CONTEXT_MENU;
    }

    /** True when the game client can see this key at all (it has a Jagex key code and is reported on press). */
    static boolean supported(int vk) {
        if (vk <= 0 || vk >= SimpleKeyboardMonitor.KEY_MAP.length || isModifierKey(vk) || !NAME_BY_VK.containsKey(vk)) {
            return false;
        }
        int code = SimpleKeyboardMonitor.KEY_MAP[vk];
        return code != 0 && (code & 0x80) == 0;
    }

    /** Modifier flags (SHIFT/ALT/CTRL) of a Swing key event. */
    static int modifiersOf(KeyEvent e) {
        int m = 0;
        if (e.isShiftDown()) m |= SHIFT;
        if (e.isAltDown()) m |= ALT;
        if (e.isControlDown()) m |= CTRL;
        return m;
    }

    /** "CTRL+SHIFT+F6" for VK_F6 with CTRL and SHIFT. */
    static String format(int vk, int mods) {
        String name = NAME_BY_VK.get(vk);
        if (name == null) {
            return NONE;
        }
        StringBuilder sb = new StringBuilder();
        if ((mods & CTRL) != 0) sb.append("CTRL+");
        if ((mods & ALT) != 0) sb.append("ALT+");
        if ((mods & SHIFT) != 0) sb.append("SHIFT+");
        return sb.append(name).toString();
    }

    /** {vk, modifiers} of a stored binding; null for NONE or anything unreadable. */
    static int[] parse(String bind) {
        if (bind == null) {
            return null;
        }
        String s = bind.trim().toUpperCase();
        if (s.isEmpty() || s.equals(NONE) || s.equals("OFF")) {
            return null;
        }
        int mods = 0;
        String[] parts = s.split("\\+");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("CTRL")) mods |= CTRL;
            else if (parts[i].equals("ALT")) mods |= ALT;
            else if (parts[i].equals("SHIFT")) mods |= SHIFT;
            else return null;
        }
        Integer vk = VK_BY_NAME.get(parts[parts.length - 1]);
        return vk == null ? null : new int[]{vk, mods};
    }

    /** "Ctrl+Shift+F6" / "Q" / "None" for the sidebar. */
    static String display(String bind) {
        int[] p = parse(bind);
        if (p == null) {
            return "None";
        }
        return displayModifiers(p[1]) + keyText(p[0]);
    }

    static String displayModifiers(int mods) {
        StringBuilder sb = new StringBuilder();
        if ((mods & CTRL) != 0) sb.append("Ctrl+");
        if ((mods & ALT) != 0) sb.append("Alt+");
        if ((mods & SHIFT) != 0) sb.append("Shift+");
        return sb.toString();
    }

    /** The key's own name: "F6", "Q", "1", "Space", "Numpad-5"... (Java's text, in English where Java has one). */
    static String keyText(int vk) {
        String name = NAME_BY_VK.get(vk);
        if (name != null && (name.length() == 1 || name.matches("F\\d{1,2}"))) {
            return name;
        }
        String text = KeyEvent.getKeyText(vk);
        return text == null || text.isEmpty() || text.startsWith("Unknown") ? String.valueOf(name) : text;
    }

    // ---- in game --------------------------------------------------------------------------------------------------------

    /** {jagexCode, modifiers, vk} of a binding, cached; {0, 0, 0} = nothing to match. */
    private static int[] jagex(String bind) {
        int[] cached = PARSED.get(bind);
        if (cached != null) {
            return cached;
        }
        int[] p = parse(bind);
        int[] out = new int[]{0, 0, 0};
        if (p != null && supported(p[0])) {
            out = new int[]{SimpleKeyboardMonitor.KEY_MAP[p[0]] & 0x7F, p[1], p[0]};
        }
        PARSED.put(bind, out);
        return out;
    }

    /**
     * True when [log] is the PRESS of [bind] (exact modifiers: SHIFT+F6 does not fire on CTRL+SHIFT+F6). While a house
     * teleport screen (nexus / jewellery box) is open its own 1-9 / A-Z shortcuts win over a plain letter or digit binding
     * (Codex review v3, finding 2).
     */
    static boolean pressed(String bind, KeyLog log) {
        if (log.getType() != KeyLog.TYPE_PRESSED || bind == null || bind.isEmpty()) {
            return false;
        }
        int[] j = jagex(bind);
        if (j[0] == 0 || log.getKeyCode() != j[0] || (log.getModifierFlags() & (SHIFT | ALT | CTRL)) != j[1]) {
            return false;
        }
        if (j[2] == KeyEvent.VK_BACK_QUOTE || j[2] == KeyEvent.VK_DEAD_GRAVE) {
            return false; // the debug console key always opens the console (Codex review v3 #2, finding 2)
        }
        boolean shortcutChar = (j[2] >= KeyEvent.VK_1 && j[2] <= KeyEvent.VK_9) || (j[2] >= KeyEvent.VK_A && j[2] <= KeyEvent.VK_Z);
        return !(j[1] == 0 && shortcutChar && RspsTeleportKeys.menuOpen());
    }

    /**
     * Keys whose press a hotkey handled (Jagex codes), until their RELEASED record: their HELD repeats are swallowed. Codex
     * review v3, finding 1: one entry per physical key, no timeout - focus loss sends a RELEASED for every held key
     * (SimpleKeyboardMonitor.record, TYPE_UNFOCUSED), which clears them.
     */
    private static final java.util.Set<Integer> HELD = new java.util.HashSet<Integer>();
    /**
     * True right after a swallowed PRESSED/HELD record: the TYPED record that follows it belongs to the same physical key
     * (Java sends keyTyped right after keyPressed, and record() keeps that order), so only that character is swallowed.
     */
    private static boolean typedBelongsToSwallowed;
    /**
     * Time of that swallowed record: Java fires keyTyped within a few ms of its keyPressed, so a TYPED record much later is
     * another key's character (e.g. a numpad key the monitor reports without a press; Codex review v3 #2, finding 1).
     */
    private static long swallowedAt;
    private static final long TYPED_WINDOW_MS = 30L;
    /** VK of the key whose character may follow (0 = unknown): the TYPED character must fit it (Codex review v3 #3). */
    private static int swallowedVk;

    /** The VK whose Jagex code is [code] (first match in SimpleKeyboardMonitor.KEY_MAP), 0 = none. */
    private static int vkOf(int code) {
        for (int vk = 1; vk < SimpleKeyboardMonitor.KEY_MAP.length; vk++) {
            int m = SimpleKeyboardMonitor.KEY_MAP[vk];
            if (m != 0 && (m & 0x80) == 0 && m == code && NAME_BY_VK.containsKey(vk)) {
                return vk;
            }
        }
        return 0;
    }

    /**
     * True when [c] can be the character of key [vk]: F-keys, arrows and other non-character keys never type, a letter key
     * types that letter (or a Ctrl control character), a digit key its digit or a shifted symbol. So a character from
     * another key (a numpad digit the monitor reports without a press) is not swallowed with a held hotkey.
     */
    static boolean typedFits(int vk, char c) {
        if (vk == 0) {
            return false;
        }
        if ((vk >= KeyEvent.VK_F1 && vk <= KeyEvent.VK_F12) || (vk >= KeyEvent.VK_F13 && vk <= KeyEvent.VK_F24)
            || vk == KeyEvent.VK_LEFT || vk == KeyEvent.VK_RIGHT || vk == KeyEvent.VK_UP || vk == KeyEvent.VK_DOWN
            || vk == KeyEvent.VK_HOME || vk == KeyEvent.VK_END || vk == KeyEvent.VK_PAGE_UP || vk == KeyEvent.VK_PAGE_DOWN
            || vk == KeyEvent.VK_INSERT || vk == KeyEvent.VK_PAUSE || vk == KeyEvent.VK_CAPS_LOCK) {
            return false;
        }
        if (vk >= KeyEvent.VK_A && vk <= KeyEvent.VK_Z) {
            return Character.toUpperCase(c) == vk || c == (char) (vk - KeyEvent.VK_A + 1);
        }
        if (vk >= KeyEvent.VK_0 && vk <= KeyEvent.VK_9) {
            return c == (char) vk || !Character.isLetterOrDigit(c);
        }
        if (vk == KeyEvent.VK_SPACE) {
            return c == ' ';
        }
        return true;
    }

    /** After a handled press: swallow its HELD repeats, its typed character and its release. */
    static void swallowRestOf(KeyLog log) {
        HELD.add(log.getKeyCode());
        typedBelongsToSwallowed = true;
        swallowedAt = log.getTime();
        swallowedVk = vkOf(log.getKeyCode());
    }

    /** Call first for every key record (client.java, before every shortcut handler): true = swallow it. */
    static boolean swallowing(KeyLog log) {
        int type = log.getType();
        if (type == KeyLog.TYPE_TYPED) {
            boolean mine = typedBelongsToSwallowed && Math.abs(log.getTime() - swallowedAt) <= TYPED_WINDOW_MS
                && typedFits(swallowedVk, log.getKeyChar());
            typedBelongsToSwallowed = false;
            return mine;
        }
        typedBelongsToSwallowed = false;
        if (HELD.isEmpty()) {
            return false;
        }
        int code = log.getKeyCode();
        if (type == KeyLog.TYPE_RELEASED) {
            return HELD.remove(code);
        }
        if (type == KeyLog.TYPE_HELD && HELD.contains(code)) {
            typedBelongsToSwallowed = true;
            swallowedAt = log.getTime();
            swallowedVk = vkOf(code);
            return true;
        }
        return false;
    }
}
