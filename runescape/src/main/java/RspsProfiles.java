import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * 78Legacy v4 "Bruine Bonen Technologia": the Presets block (UG's blue Preset / green Save buttons). A profile is a copy of
 * the whole setup: the plugin's key bindings and options (server settings pkhotkeys.* and gearswitcher.*, except the
 * owner's allow* switches), the 15 scripts and the 10 gear sets. Storage: Java Preferences /78legacy/profiles/pN with keys
 * "setting.<key>", "script.<key>" (the /78legacy/scripts entries) and "gear.<key>" (the /78legacy/gearswitcher entries).
 * Save and load run on the Swing thread (the sidebar buttons); settings are written with RspsSidebar.set (to the server).
 */
final class RspsProfiles {

    static final int COUNT = 6;
    static final Preferences ROOT = Preferences.userRoot().node("/78legacy/profiles");
    private static final List<Runnable> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<Runnable>();

    private RspsProfiles() {
    }

    private static Preferences node(int n) {
        return ROOT.node("p" + n);
    }

    static String name(int n) {
        String s = ROOT.get("name." + n, "");
        return s.isEmpty() ? "Preset " + n : s;
    }

    static void setName(int n, String name) {
        String clean = name == null ? "" : name.replaceAll("[\\r\\n]", " ").trim();
        if (clean.length() > 16) clean = clean.substring(0, 16);
        ROOT.put("name." + n, clean);
        flushAndNotify();
    }

    static boolean exists(int n) {
        return ROOT.getBoolean("saved." + n, false);
    }

    static String notes() {
        return ROOT.get("notes", "");
    }

    static void setNotes(String s) {
        String clean = s == null ? "" : s.replaceAll("[\\r\\n]", " ");
        if (clean.length() > 200) clean = clean.substring(0, 200);
        ROOT.put("notes", clean);
        flushAndNotify();
    }

    /** True for a setting that belongs in a profile (the player's own keys and options, never the owner's switches). */
    static boolean profiled(RspsHudConfig.Setting s) {
        return (s.plugin.equals("pkhotkeys") || s.plugin.equals("gearswitcher")) && RspsHudConfig.stored(s.type)
            && !s.key.startsWith("pkhotkeys.allow");
    }

    static void save(int n) {
        Preferences p = node(n);
        try {
            p.clear();
            for (RspsHudConfig.Setting s : RspsHudConfig.SETTINGS) {
                if (profiled(s)) {
                    p.put("setting." + s.key, RspsHudConfig.get(s.key));
                }
            }
            copy(RspsScripts.PREFS, p, "script.", true);
            copy(RspsGearSets.PREFS, p, "gear.", true);
        } catch (BackingStoreException e) {
            System.out.println("[PROFILES] could not save: " + e);
            return;
        }
        ROOT.putBoolean("saved." + n, true);
        flushAndNotify();
    }

    /** Loads profile [n]; false when nothing was saved there. */
    static boolean load(int n) {
        if (!exists(n)) {
            return false;
        }
        Preferences p = node(n);
        try {
            for (String key : p.keys()) {
                String value = p.get(key, null);
                if (value == null) continue;
                if (key.startsWith("setting.")) {
                    String k = key.substring(8);
                    RspsHudConfig.Setting s = RspsHudConfig.setting(k);
                    if (s != null && profiled(s) && !value.equals(RspsHudConfig.get(k))) {
                        RspsSidebar.set(k, value);
                    }
                }
            }
            restore(p, "script.", RspsScripts.PREFS);
            restore(p, "gear.", RspsGearSets.PREFS);
        } catch (BackingStoreException e) {
            System.out.println("[PROFILES] could not load: " + e);
            return false;
        }
        RspsScripts.flushAndNotify();
        RspsGearSets.flush();
        RspsGearSets.changed();
        flushAndNotify();
        return true;
    }

    /** Copies every entry of [from] (except migration markers) into [to] under [prefix]. */
    private static void copy(Preferences from, Preferences to, String prefix, boolean skipMarkers) throws BackingStoreException {
        for (String key : from.keys()) {
            if (skipMarkers && key.startsWith("migrated")) continue;
            String v = from.get(key, null);
            if (v != null) to.put(prefix + key, v);
        }
    }

    /** Replaces the entries of [to] (except migration markers) with the [prefix] entries of profile [p]. */
    private static void restore(Preferences p, String prefix, Preferences to) throws BackingStoreException {
        for (String key : to.keys()) {
            if (!key.startsWith("migrated")) to.remove(key);
        }
        for (String key : p.keys()) {
            if (key.startsWith(prefix)) {
                String v = p.get(key, null);
                if (v != null) to.put(key.substring(prefix.length()), v);
            }
        }
        to.flush();
    }

    static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    private static void flushAndNotify() {
        try {
            ROOT.flush();
        } catch (BackingStoreException e) {
            System.out.println("[PROFILES] could not store: " + e);
        }
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // a broken listener must not break the game loop
            }
        }
    }
}
