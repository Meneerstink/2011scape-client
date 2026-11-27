import com.jagex.game.runetek6.config.objtype.ObjType;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;

/**
 * 78Legacy Gear Switcher v3 - the saved gear sets (contents + names), shared by the key handler (client thread) and the
 * sidebar editor (Swing thread). Storage: Java Preferences node /78legacy/gearswitcher, keys set.N.items (csv of item ids,
 * the format v1/v2 already used, so old sets keep working) and set.N.name. Every write is flushed at once.
 *
 * Thread rules: the Preferences calls are thread-safe. Item NAMES come from the cache (ObjTypeList), which belongs to the
 * client thread: [itemName] only reads a name cache; unknown ids are queued and resolved by [tick] on the client thread,
 * after which the listeners run (they must hop to Swing themselves, e.g. SwingUtilities.invokeLater).
 */
final class RspsGearSets {

    /** Number of gear sets (1-based numbering everywhere). */
    static final int COUNT = 10;
    /** At most one item per equipment slot. */
    static final int MAX_ITEMS = 14;

    static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userRoot().node("/78legacy/gearswitcher");

    private static final java.util.Map<Integer, String> NAMES = new java.util.concurrent.ConcurrentHashMap<Integer, String>();
    private static final java.util.Set<Integer> WANTED = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>());
    private static final java.util.List<Runnable> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<Runnable>();
    /** Set by the editor's "Save worn" button (Swing thread), done by [tick] on the client thread: 0 = nothing pending. */
    private static volatile int saveWornRequest;

    private RspsGearSets() {
    }

    static String name(int n) {
        String s = PREFS.get("set." + n + ".name", "");
        return s.isEmpty() ? "Set " + n : s;
    }

    static void setName(int n, String name) {
        String clean = name == null ? "" : name.replaceAll("[\\r\\n,;|]", " ").trim();
        if (clean.length() > 24) {
            clean = clean.substring(0, 24);
        }
        PREFS.put("set." + n + ".name", clean);
        flush();
        changed();
    }

    static synchronized int[] items(int n) {
        return parse(PREFS.get("set." + n + ".items", ""));
    }

    /**
     * Replaces set [n] only if it still holds [expected] (the editor's copy): a "Save worn" that landed in between is never
     * overwritten by a stale edit (Codex review v3, finding 5). False = the set changed; the editor reloads it.
     */
    static boolean replace(int n, int[] expected, int[] ids) {
        synchronized (RspsGearSets.class) {
            if (!java.util.Arrays.equals(parse(PREFS.get("set." + n + ".items", "")), expected)) {
                return false;
            }
            store(n, ids);
        }
        flush();
        changed();
        return true;
    }

    static void setItems(int n, int[] ids) {
        synchronized (RspsGearSets.class) {
            store(n, ids);
        }
        flush();
        changed();
    }

    private static void store(int n, int[] ids) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int id : ids) {
            if (id >= 0 && count < MAX_ITEMS) {
                if (sb.length() > 0) sb.append(',');
                sb.append(id);
                count++;
            }
        }
        PREFS.put("set." + n + ".items", sb.toString());
    }

    // ---- v3.1 (user 2026-10-12): prayers per gear set, item ids per PK preset ------------------------------------------

    /** "NONE" or a RspsPkData.PRAYER_IDS id. */
    static final String NONE = "NONE";

    /** The two prayers set [n] turns on after its gear (RspsPkData ids, "NONE" = no prayer). Stored as set.N.prayers. */
    static String[] prayers(int n) {
        String[] out = {NONE, NONE};
        String[] parts = PREFS.get("set." + n + ".prayers", "").split(",");
        for (int i = 0; i < 2 && i < parts.length; i++) {
            String id = parts[i].trim();
            if (RspsPkData.prayer(id) >= 0) {
                out[i] = id;
            }
        }
        return out;
    }

    /** Sets the two prayers of set [n] (unknown ids become "NONE"). */
    static void setPrayers(int n, String one, String two) {
        String a = RspsPkData.prayer(one) >= 0 ? one : NONE;
        String b = RspsPkData.prayer(two) >= 0 ? two : NONE;
        PREFS.put("set." + n + ".prayers", a + "," + b);
        flush();
        changed();
    }

    /** Number of PK presets that can have their own item list. */
    static final int PRESETS = 8;

    /** The item ids PK preset [n] puts on (empty = use the preset's gear set). Stored as preset.N.items. */
    static synchronized int[] presetItems(int n) {
        return parse(PREFS.get("preset." + n + ".items", ""));
    }

    static void setPresetItems(int n, int[] ids) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int id : ids) {
            if (validId(id) && count < MAX_ITEMS) {
                if (sb.length() > 0) sb.append(',');
                sb.append(id);
                count++;
            }
        }
        synchronized (RspsGearSets.class) {
            PREFS.put("preset." + n + ".items", sb.toString());
        }
        flush();
        changed();
    }

    /** True for an item id the cache knows (ids are checked again on the client thread before any click). */
    static boolean validId(int id) {
        return id >= 0 && id < 65536;
    }

    /** "Abyssal whip" for a known id, "item 4151" while the name is still being looked up (it arrives via a listener). */
    static String itemName(int id) {
        String s = NAMES.get(id);
        if (s != null) {
            return s;
        }
        WANTED.add(id);
        return "item " + id;
    }

    /** The editor's "Save worn" button: saves what you wear as set [n] on the next client cycle. */
    static void requestSaveWorn(int n) {
        saveWornRequest = n;
    }

    static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    /** Client thread, every cycle (from RspsGearSwitcher.tick): resolves item names and does a requested "save worn". */
    static void tick() {
        saveWornRequest = 0; // GEARSWITCHER-WEG 27-11: no set editor any more, nothing asks to save worn gear
        if (!WANTED.isEmpty() && ObjTypeList.instance != null) {
            boolean any = false;
            for (Integer id : WANTED.toArray(new Integer[0])) {
                WANTED.remove(id);
                String s;
                try {
                    ObjType t = ObjTypeList.instance.list(id);
                    s = t == null || t.name == null || t.name.equals("null") ? "unknown (" + id + ")" : t.name;
                } catch (RuntimeException badId) {
                    s = "unknown (" + id + ")";
                }
                NAMES.put(id, s);
                any = true;
            }
            if (any) {
                changed();
            }
        }
    }

    static void changed() {
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // a broken listener must not break the game loop
            }
        }
    }

    static int[] parse(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new int[0];
        String[] parts = csv.split(",");
        int[] out = new int[Math.min(parts.length, MAX_ITEMS)];
        int k = 0;
        for (String p : parts) {
            if (k == out.length) break;
            try {
                int id = Integer.parseInt(p.trim());
                if (id >= 0) out[k++] = id;
            } catch (NumberFormatException ignored) {
                // skip junk
            }
        }
        return java.util.Arrays.copyOf(out, k);
    }

    static void flush() {
        try {
            PREFS.flush();
        } catch (java.util.prefs.BackingStoreException e) {
            System.out.println("[GEAR SWITCHER] could not store settings: " + e);
        }
    }
}
