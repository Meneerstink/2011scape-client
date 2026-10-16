import com.jagex.core.constants.MainLogicStep;
import com.jagex.game.runetek6.config.objtype.ObjType;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;
import rs2.client.event.keyboard.KeyLog;

/**
 * Claude + Qwen, 2026-10-11. VERIFIED in the client against the local server (SHIFT+F1/F2 switched 3 items each).
 *
 * 78Legacy gear switcher: one key press equips a saved gear set, exactly as if the player clicked "Wield"/"Wear" on each
 * item of that set in the inventory (the same IF_BUTTONx packets as a real click, all in one tick, like a fast human).
 * No automation: nothing happens without a key press, it never reacts to the opponent, never prays, never attacks.
 *
 * On/off and chat messages: 78 Plugins sidebar -> Gear Switcher (saved with the account). Sets: Java Preferences.
 *
 * Keys (v3): 10 sets, each with an equip key and a save key, bound to any key in the sidebar (RspsKeyBind). Defaults:
 *   SHIFT+F<n>         equip set n (1..5; sets 6-10 start unbound)
 *   CTRL+SHIFT+F<n>    save the currently WORN gear as set n
 * The sets can also be edited by hand (item ids, names, order) in the sidebar: RspsGearSetEditor / RspsGearSets.
 *
 * Hook (one line in client.java's key loop, next to RspsTeleportKeys, before RspsFunctionKeys):
 *   if (RspsGearSwitcher.key(log)) { continue; }
 * The old hooks (MainLogicManager.tick -> RspsGearSwitcher.tick, InterfaceManager.addMiniMenuOptions ->
 * observeMenuComponent) stay: tick() is empty, observeMenuComponent only learns the inventory interface hash.
 *
 * Field semantics in this client (verified in InterfaceList.java:54 and MiniMenu.java:1654):
 *   Component.slot = (interfaceId << 16) | componentIndex  (the "hash" / idAndSlot)
 *   Component.id   = child index inside that component (for the backpack: the inventory slot 0..27)
 *   InterfaceList.getComponent(hash, childIndex) and InterfaceManager.ifButtonXSend(hash, childIndex, opBase, op1Based)
 */
public final class RspsGearSwitcher {

    /** v3: 10 sets (RspsGearSets), each with an equip key and a save key on any key (settings gearswitcher.bind.*). */
    static final int MAX_SETS = RspsGearSets.COUNT;
    static final int BACKPACK_INV = 93;
    static final int WORN_INV = 94;
    static final int BACKPACK_SLOTS = 28;
    static final int DEFAULT_INVENTORY_HASH = 679 << 16;
    /** Modifier flags of KeyLog.getModifierFlags() (SimpleKeyboardMonitor.modifierFlags). */
    static final int SHIFT = 0x1, ALT = 0x2, CTRL = 0x4;

    private static final java.util.prefs.Preferences PREFS =
        java.util.prefs.Preferences.userRoot().node("/78legacy/gearswitcher");

    /** Backpack interface hash, learned from the first right-clicked backpack item (no hard-coded interface id). */
    private static int inventoryHash = PREFS.getInt("inventoryHash", -1);

    private RspsGearSwitcher() {
    }

    /** MainLogicManager hook (every client cycle): finishes a switch whose items waited for the inventory display. */
    static void tick() {
        RspsGearSets.tick(); // v3: item names for the editor, the editor's "Save worn"
        RspsPkTarget.tick(); // v3: the PK Hotkeys page's live "Target" line
        RspsScriptRunner.tick(); // v4: one-time move of the v3.2 presets into scripts, once the server sent them
        if (pending.length > 0 && (!RspsHudConfig.enabled("gearswitcher") || !MainLogicStep.isAtGameScreen(MainLogicManager.step))) {
            cancel(); // finding 2: nothing continues after the plugin is switched off or the game screen is left
            return;
        }
        if (pending.length > 0) {
            attempt(System.currentTimeMillis() > pendingUntil);
        }
    }

    /** Called for every keyboard event before the game sees it; true = handled (swallowed). */
    static boolean key(KeyLog log) {
        // The rest of a handled press (repeats, typed character, release) is swallowed in client.java (RspsKeyBind.swallowing).
        // On/off lives with the account: 78 Plugins sidebar -> Gear Switcher (server ClientHudConfig "gearswitcher.enabled").
        if (log.getType() != KeyLog.TYPE_PRESSED || !RspsHudConfig.enabled("gearswitcher") || debugconsole.isOpen()
            || !MainLogicStep.isAtGameScreen(MainLogicManager.step)) {
            return false;
        }
        for (int n = 1; n <= MAX_SETS; n++) {
            if (RspsKeyBind.pressed(binding("equip", n), log)) {
                equip(n); // v3.2: prayers live in the PK presets now (one place), not in the gear sets
                RspsKeyBind.swallowRestOf(log);
                return true;
            }
            if (RspsKeyBind.pressed(binding("save", n), log)) {
                saveWorn(n);
                RspsKeyBind.swallowRestOf(log);
                return true;
            }
        }
        return false;
    }

    /** The equip / save key of set [n] (v3 setting gearswitcher.bind.equipN / saveN; sets 1-5 default to the v1 keys). */
    static String binding(String kind, int n) {
        String bound = RspsHudConfig.get("gearswitcher.bind." + kind + n);
        if (bound != null && !bound.isEmpty()) {
            return bound;
        }
        if (n > 5) {
            return RspsKeyBind.NONE;
        }
        return (kind.equals("save") ? "CTRL+SHIFT+F" : "SHIFT+F") + n;
    }

    /** InterfaceManager.addMiniMenuOptions hook: remember which interface holds the backpack. */
    static void observeMenuComponent(Component component) {
        if (component == null || component.invObject < 0 || component.id < 0 || component.id >= BACKPACK_SLOTS) {
            return;
        }
        if (component.slot != inventoryHash && backpackSlotHolds(component.id, component.invObject)) {
            inventoryHash = component.slot;
            PREFS.putInt("inventoryHash", inventoryHash);
            flush();
            log("backpack interface learned: " + (inventoryHash >>> 16) + ":" + (inventoryHash & 0xFFFF));
        }
    }

    /** Items of the set being equipped that still wait for their backpack slot to show them (see [attempt]). */
    private static int[] pending = new int[0];
    /** Chat / log name of the switch in progress ("Gear set 3 (Mage)", "Preset 1"). */
    private static String pendingName = "";
    private static int pendingSent;
    private static int pendingStale;
    private static long pendingUntil;
    /** What a PK preset does after this set is on (prayers, attack/spell); null for a plain gear key. */
    private static Runnable pendingThen;
    /** How long a key press may wait for the inventory display to catch up (a fast previous switch moved the items). */
    static final long RETRY_MS = 600L;

    /** Equips every item of set [n] that is in the backpack and not worn yet. */
    static void equip(int n) {
        equip(n, null);
    }

    /**
     * Equips set [n]; [then] runs once the set is done (or could not be done), on the client thread - so a PK preset's
     * prayer/attack clicks never go out before its gear clicks (Codex review 2026-10-11, finding 1).
     */
    static void equip(int n, Runnable then) {
        cancel(); // a new press replaces a switch that is still waiting (its follow-up is dropped, not run)
        int[] items = RspsGearSets.items(n);
        if (items.length == 0) {
            say(label(n) + " is empty - fill it in 78 Plugins -> Gear Switcher, or wear the gear and press " + RspsKeyBind.display(binding("save", n)) + ".");
            if (then != null) {
                then.run();
            }
            return;
        }
        equipItems(label(n), items, then);
    }

    /**
     * v3.1: equips the item list [items] (a gear set, or a PK preset's own item ids), named [name] in chat; [then] runs once
     * the clicks are out. Same rules as a gear set: only items in the backpack and not worn, in list order.
     */
    static void equipItems(String name, int[] items, Runnable then) {
        cancel();
        if (items.length == 0) {
            if (then != null) {
                then.run();
            }
            return;
        }
        pendingThen = then;
        pending = items;
        pendingName = name;
        pendingSent = 0;
        pendingStale = 0;
        pendingUntil = System.currentTimeMillis() + RETRY_MS;
        attempt(false);
    }

    /**
     * Clicks Wield/Wear/Equip on every pending item whose backpack slot shows it. An item whose slot still shows the old
     * object (the display lags a moment behind the inventory after a fast switch) waits for the next client cycle, up to
     * [RETRY_MS]: we only ever click what the player can see in that slot, with the item id the slot shows.
     */
    private static void attempt(boolean last) {
        String n = pendingName;
        // The backpack interface: learned from a right-click, else the standard rev-667 backpack (679:0).
        int inv = inventoryHash();
        int[] left = new int[pending.length];
        int waiting = 0;
        // Once an item has to wait, every later item waits too: the clicks keep the saved order (weapon before shield).
        boolean blocked = false;
        // Items go on in the saved worn-slot order (head, cape, amulet, WEAPON, body, SHIELD, ...): the weapon always comes
        // before the shield, so a 2-handed weapon -> weapon + shield switch works in one press. (The Wield/Wear verb can't be
        // used for this: on this server dragon claws are "Wear" and the dragon defender is "Wield".)
        for (int item : pending) {
            if (isWorn(item)) {
                continue;
            }
            int slot = backpackSlotOf(item);
            if (slot < 0) {
                log(n + ": item " + item + " not in backpack");
                continue;
            }
            Component c = InterfaceList.getComponent(inv, slot);
            if (blocked && !last) {
                left[waiting++] = item;
                continue;
            }
            if (c != null && c.invObject != item && last && backpackSlotHolds(slot, item)) {
                // The slot's picture still shows the previous object long after the inventory update arrived (seen up to
                // several seconds after a fast switch). The client's own inventory says the item IS in this slot, so bring
                // the slot up to date and click it, instead of failing the key press.
                log(n + ": backpack slot " + slot + " display lagged (" + c.invObject + "), refreshed to " + item);
                c.invObject = item;
            }
            if (c == null || c.invObject != item) {
                blocked = true;
                if (last) {
                    pendingStale++;
                    log(n + ": backpack slot " + slot + " still shows " + (c == null ? "nothing" : String.valueOf(c.invObject))
                        + " instead of " + item + ", skipped");
                } else {
                    left[waiting++] = item;
                }
                continue;
            }
            int op = equipOp(c, item);
            if (op < 0) {
                log(n + ": no Wield/Wear/Equip option for item " + item);
                continue;
            }
            InterfaceManager.ifButtonXSend(inv, slot, c.opBase, op + 1);
            pendingSent++;
        }
        pending = java.util.Arrays.copyOf(left, waiting);
        if (pending.length == 0) {
            finish();
        }
    }

    /** Drops a waiting switch without sending anything more (new press, plugin switched off, left the game screen). */
    static void cancel() {
        if (pending.length > 0) {
            log(pendingName + ": waiting switch cancelled");
        }
        pending = new int[0];
        pendingThen = null;
        RspsScriptRunner.gearCancelled(); // v4: a script that waited for this batch is no longer running (Codex review v4 #3)
    }

    private static void finish() {
        String n = pendingName;
        int sent = pendingSent;
        pending = new int[0];
        Runnable then = pendingThen;
        pendingThen = null;
        log(n + ": " + sent + " item(s) switched");
        if (pendingStale > 0) {
            // The inventory on screen has not caught up with a very fast previous switch: we never click what isn't shown.
            // Usually the inventory tab is not open: its slots only refresh while it is shown, like for a player clicking them.
            say("Open your inventory tab - the items on screen are not up to date" + (sent > 0 ? " (" + sent + " items switched)." : "."));
        } else if (sent == 0) {
            info(n + ": nothing to switch (already worn or not in your inventory).");
        } else {
            info(n + " equipped (" + sent + (sent == 1 ? " item)." : " items)."));
        }
        if (then != null) {
            then.run(); // a PK preset's prayers and attack go out after the gear clicks
        }
    }

    /** v4: true while a switch still waits for its backpack slots to show the items. */
    static boolean busy() {
        return pending.length > 0;
    }

    /** Saves the currently worn items (ammo included) as set [n]. */
    static void saveWorn(int n) {
        ClientInventory worn = ClientInventory.get(WORN_INV, false);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        if (worn != null) {
            for (int obj : worn.objs) {
                if (obj >= 0) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(obj);
                    count++;
                }
            }
        }
        if (count == 0) {
            say("You are not wearing anything - " + label(n) + " not changed.");
            return;
        }
        RspsGearSets.setItems(n, parse(sb.toString())); // v3: the editor shows it at once (RspsGearSets listeners)
        String key = binding("equip", n);
        say(label(n) + " saved (" + count + " items)." + (RspsKeyBind.parse(key) == null ? "" : " " + RspsKeyBind.display(key) + " equips it."));
        log("set " + n + " = [" + sb + "]");
    }

    /** First menu option of [c] named Wield/Wear/Equip (component ops, else the item's inventory ops), 0-based; -1 = none. */
    static int equipOp(Component c, int item) {
        for (int i = 0; i < 10; i++) {
            if (isEquipVerb(InterfaceManager.getOp(c, i))) {
                return i;
            }
        }
        ObjType type = ObjTypeList.instance == null ? null : ObjTypeList.instance.list(item);
        if (type != null && type.iop != null) {
            for (int i = 0; i < type.iop.length; i++) {
                if (isEquipVerb(type.iop[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** The backpack interface hash (learned from a right-click; the standard rev-667 backpack 679:0 until then). */
    static int inventoryHash() {
        return inventoryHash >= 0 ? inventoryHash : DEFAULT_INVENTORY_HASH;
    }

    private static boolean isEquipVerb(String op) {
        return op != null && (op.equalsIgnoreCase("Wield") || op.equalsIgnoreCase("Wear") || op.equalsIgnoreCase("Equip"));
    }

    private static boolean isWorn(int item) {
        ClientInventory worn = ClientInventory.get(WORN_INV, false);
        if (worn == null) return false;
        for (int obj : worn.objs) {
            if (obj == item) return true;
        }
        return false;
    }

    private static int backpackSlotOf(int item) {
        ClientInventory pack = ClientInventory.get(BACKPACK_INV, false);
        if (pack == null) return -1;
        for (int slot = 0; slot < pack.objs.length && slot < BACKPACK_SLOTS; slot++) {
            if (pack.objs[slot] == item) return slot;
        }
        return -1;
    }

    private static boolean backpackSlotHolds(int slot, int item) {
        ClientInventory pack = ClientInventory.get(BACKPACK_INV, false);
        return pack != null && slot < pack.objs.length && pack.objs[slot] == item;
    }

    /** "4151,1079,..." -> ids; ignores junk and caps at 14 entries (one per equipment slot). */
    static int[] parse(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new int[0];
        String[] parts = csv.split(",");
        int[] out = new int[Math.min(parts.length, 14)];
        int n = 0;
        for (String p : parts) {
            if (n == out.length) break;
            try {
                int id = Integer.parseInt(p.trim());
                if (id >= 0) out[n++] = id;
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** Player-facing message in the chatbox (78 Plugins -> Gear Switcher -> Chat messages), always logged too. */
    /** Write through at once: Preferences otherwise syncs lazily and a killed client would lose the saved sets. */
    private static void flush() {
        try {
            PREFS.flush();
        } catch (java.util.prefs.BackingStoreException e) {
            log("could not store settings: " + e);
        }
    }

    /** v5: what was switched - only with Chat detail "All" (RspsPkHotkeys.chatAllowed). */
    private static void info(String message) {
        log(message);
        if (RspsPkHotkeys.chatAllowed("[Gear] " + message, true)) {
            try {
                ChatHistory.add(0, 0, "", "", "", "<col=" + CHAT_COLOUR + ">[Gear] " + message);
            } catch (RuntimeException noChat) {
                // Not in game: the log line is enough.
            }
        }
    }

    private static void say(String message) {
        log(message);
        if (RspsPkHotkeys.chatAllowed("[Gear] " + message, false)) { // v4: one Chat messages switch; v5: no repeats within 3 s
            try {
                ChatHistory.add(0, 0, "", "", "", "<col=" + CHAT_COLOUR + ">[Gear] " + message);
            } catch (RuntimeException noChat) {
                // Not in game: the log line is enough.
            }
        }
    }

    private static final String CHAT_COLOUR = "00c8ff";

    /** "Gear set 3" or "Gear set 3 (Mage)" once the player named it. */
    static String label(int n) {
        String name = RspsGearSets.name(n);
        return name.equals("Set " + n) ? "Gear set " + n : "Gear set " + n + " (" + name + ")";
    }

    private static void log(String message) {
        System.out.println("[GEAR SWITCHER] " + message);
    }
}
