import com.jagex.core.constants.MainLogicStep;
import com.jagex.core.datastruct.key.HashTableIterator;
import com.jagex.game.runetek6.config.iftype.SubInterface;
import rs2.client.event.keyboard.KeyLog;

/**
 * OSRS keybinding (owner 2026-10-11: "f keys customize like osrs"): F1-F12 and Esc open the side tab the player bound to them
 * in the in-game Keybinding window (Settings > Keybinding, the server's port of OSRS interface 121 - owner: "in de client ipv
 * sidebar"; no donor has it, the source is OSRS itself).
 * The defaults are OSRS's (OSRS Wiki "Shortcut keys"): F1 combat, F2 skills, F3 quests, F4 equipment, F5 prayer, F6 magic,
 * F7 clan chat, F8 friends, F9 friends chat (OSRS: account), F10 settings, F11 emotes, F12 music, Esc inventory. The values
 * come from the server (ClientHudConfig.KEYBIND_TABS, saved with the account) in the same cfg push as the sidebar settings.
 *
 * The tab opens at once on this side, through the same clientscript 115 (tab slot) the server's focusTab sends. The key still
 * reaches the server: with "Esc closes the current interface" on, Esc closes an open window there and opens no tab here; with
 * no window open it opens its tab like any other key.
 */
final class RspsFunctionKeys {

    /** Setting key, tab name, default key, gameframe tab slot (clientscript 115; 548:204.. fixed, 746:90.. resizable, 99 logout). */
    static final String[][] TABS = {
        {"combat", "Combat Options", "F1", "0"},
        {"skills", "Skills", "F2", "2"},
        {"quests", "Quest List", "F3", "3"},
        {"inventory", "Inventory", "ESC", "4"},
        {"equipment", "Worn Equipment", "F4", "5"},
        {"prayer", "Prayer", "F5", "6"},
        {"magic", "Magic", "F6", "7"},
        {"friends", "Friends List", "F8", "9"},
        {"friendschat", "Friends Chat", "F9", "10"},
        {"logout", "Logout", "NONE", "99"},
        {"settings", "Settings", "F10", "12"},
        {"emotes", "Emotes", "F11", "13"},
        {"clanchat", "Clan Chat", "F7", "11"},
        {"music", "Music Player", "F12", "14"},
    };

    /** Clientscript that switches the side panel to a tab slot (server Player.focusTab sends the same). */
    private static final int TAB_SCRIPT = 115;

    private RspsFunctionKeys() {
    }

    /** The game-frame window slots (KeyTypedHandler closes their modal): fixed 548:9, resizable 746:12 and the full view 746:11. */
    private static final int[] WINDOW_SLOTS = {(548 << 16) | 9, (746 << 16) | 12, (746 << 16) | 11};

    /** True while a window (bank, shop, store ...) is open in one of [WINDOW_SLOTS]. */
    private static boolean windowOpen() {
        HashTableIterator iterator = new HashTableIterator(InterfaceManager.subInterfaces);
        for (SubInterface sub = (SubInterface) iterator.first(); sub != null; sub = (SubInterface) iterator.next()) {
            for (int slot : WINDOW_SLOTS) {
                if ((int) sub.key == slot) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True for F1-F12 while the OSRS keybinding is on: they belong to this binding only. The 667 cache gives the gameframe's
     * own tab buttons op keys F1-F5 (548:129,133-136 / 746:39,43-46, decoded from the cache 2026-10-11); left alone they
     * clicked the stock 667 tab right after this binding opened the chosen one, so F1-F5 never opened what the player picked.
     * OSRS has no such second binding, so InterfaceManager skips every component op key on F1-F12. Esc is not taken over:
     * interface 642:200 closes itself on Esc.
     */
    static boolean ownsKeyCode(int code) {
        return code >= 1 && code <= 12 && !"false".equals(RspsHudConfig.get("keybinding.enabled"))
            && MainLogicStep.isAtGameScreen(MainLogicManager.step);
    }

    /** Called for every keyboard event before the game sees it; never swallows (the server still gets the key). */
    static boolean key(KeyLog log) {
        if (log.getType() != KeyLog.TYPE_PRESSED || debugconsole.isOpen() || !MainLogicStep.isAtGameScreen(MainLogicManager.step)) {
            return false;
        }
        int code = log.getKeyCode();
        if (code < 1 || code > 13 || "false".equals(RspsHudConfig.get("keybinding.enabled"))) {
            return false;
        }
        // SimpleKeyboardMonitor.KEY_MAP: F1-F12 are 1-12, Esc is 13.
        String key = code == 13 ? "ESC" : "F" + code;
        if (code == 13 && !"false".equals(RspsHudConfig.get("keybinding.escCloses")) && windowOpen()) {
            return false;
        }
        for (String[] tab : TABS) {
            // Until the login push arrives a tab has its OSRS default.
            String bound = RspsHudConfig.get("keybinding." + tab[0]);
            if (key.equals(bound.isEmpty() ? tab[2] : bound)) {
                HookRequest hook = new HookRequest();
                hook.arguments = new Object[]{TAB_SCRIPT, Integer.valueOf(tab[3])};
                ScriptRunner.executeHookInner(hook);
                AvTrace.log("function key " + key + " -> tab " + tab[0] + " (slot " + tab[3] + ")");
                return false;
            }
        }
        return false;
    }
}
