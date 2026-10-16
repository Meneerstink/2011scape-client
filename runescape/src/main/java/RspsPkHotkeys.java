import com.jagex.core.constants.MainLogicStep;
import com.jagex.core.datastruct.key.HashTableIterator;
import com.jagex.game.runetek6.config.iftype.SubInterface;
import com.jagex.game.runetek6.config.objtype.ObjType;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;
import com.jagex.game.runetek6.config.vartype.TimedVarDomain;
import rs2.client.event.keyboard.KeyLog;

/**
 * 78Legacy PK hotkeys (78 Plugins -> "PK Hotkeys", plugin id {@code pkhotkeys}).
 *
 * One key press = the click a player makes by hand: the same IF_BUTTON packet as clicking the prayer, the special attack bar
 * or "Eat"/"Drink" on an inventory item, so every server rule (prayer level, food clocks, special energy, Wilderness
 * restrictions) still applies. No timers, no reaction to the opponent: nothing happens without a key press.
 *
 * Keys (v3): every action can be bound to any key with any Ctrl/Alt/Shift combination, or to None, in the sidebar (click the
 * button, press the key). Defaults: SHIFT + F6..F12, CTRL+SHIFT + F6..F11, CTRL + F1..F12 (plain F-keys stay the side
 * tabs, SHIFT+F1..F5 / CTRL+SHIFT+F1..F5 the gear switcher). A bound letter no longer types in the chatbox.
 *
 * Hook: client.java key loop, right after RspsGearSwitcher: {@code if (RspsPkHotkeys.key(log)) { continue; }}
 */
public final class RspsPkHotkeys {

    static final String ID = "pkhotkeys";
    static final int SHIFT = 0x1, ALT = 0x2, CTRL = 0x4;

    /**
     * Actions in priority order (first wins when two share a key): action id, default binding. The binding is the setting
     * "pkhotkeys.bind.<action id>" (v3: any key, RspsKeyBind text; "NONE" = off).
     */
    static final String[][] ACTIONS = {
        {"protectMagicKey", "SHIFT+F6"},
        {"protectMissilesKey", "SHIFT+F7"},
        {"protectMeleeKey", "SHIFT+F8"},
        {"specialKey", "SHIFT+F9"},
        {"eatKey", "SHIFT+F10"},
        {"brewKey", "SHIFT+F11"},
        {"restoreKey", "SHIFT+F12"},
        {"pietyKey", "CTRL+SHIFT+F6"},
        {"rigourKey", "CTRL+SHIFT+F7"},
        {"auguryKey", "CTRL+SHIFT+F8"},
        {"quickPrayersKey", "CTRL+SHIFT+F9"},
        {"vengeanceKey", "CTRL+SHIFT+F10"},
        {"attackKey", "CTRL+F1"},
        // v3: step under the last target (owner: "walk here moet onder de opponent lopen"). v4: "Walk here (mouse)" removed.
        {"stepUnderKey", "CTRL+F6"},
        // v4: Hotkey Config (UG): start/stop the plugin, cancel a waiting script, forget the target.
        {"toggleKey", "NONE"},
        {"cancelKey", "NONE"},
        {"clearTargetKey", "NONE"},
        // v4: Hotkey 1-15 run Script 1-15 (RspsScriptRunner). Scripts 1-8 took over the v3.2 presets, 9-12 Spell A-D.
        {"script1Key", "CTRL+F11"},
        {"script2Key", "CTRL+F12"},
        {"script3Key", "CTRL+SHIFT+F11"},
        {"script4Key", "CTRL+F7"},
        {"script5Key", "CTRL+F8"},
        {"script6Key", "CTRL+F9"},
        {"script7Key", "CTRL+F10"},
        {"script8Key", "NONE"},
        {"script9Key", "CTRL+F2"},
        {"script10Key", "CTRL+F3"},
        {"script11Key", "CTRL+F4"},
        {"script12Key", "CTRL+F5"},
        {"script13Key", "NONE"},
        {"script14Key", "NONE"},
        {"script15Key", "NONE"},
    };

    /** v3.2: number of presets (switch cards). */
    static final int PRESETS = 8;
    /** v3.2: what a preset does to the last target (when its "Target" box is ticked). */
    static final String[] TARGET_DO = {"ATTACK", "CAST", "SELECT", "STEP"};
    static final String[] TARGET_DO_NAMES = {"Attack", "Cast", "Select", "Step under"};
    /**
     * v3.2 preset defaults: {key, gear, prayer 1, prayer 2, toggle, target, targetDo, spell}. 1-3 are the v2 presets, 4-7
     * the old Prayer A-D keys (prayer only, on/off toggle), 8 is free. Same values as the server (ClientHudConfig.kt).
     */
    static final String[][] PRESET_DEFAULTS = {
        {"CTRL+F11", "1", "N_AUGURY", "NONE", "false", "true", "CAST", "ICE_BARRAGE"},
        {"CTRL+F12", "2", "N_RIGOUR", "NONE", "false", "true", "ATTACK", "ICE_BARRAGE"},
        {"CTRL+SHIFT+F11", "3", "N_PIETY", "NONE", "false", "true", "ATTACK", "ICE_BARRAGE"},
        {"CTRL+F7", "NONE", "N_SMITE", "NONE", "true", "false", "ATTACK", "ICE_BARRAGE"},
        {"CTRL+F8", "NONE", "N_REDEMPTION", "NONE", "true", "false", "ATTACK", "ICE_BARRAGE"},
        {"CTRL+F9", "NONE", "C_SOUL_SPLIT", "NONE", "true", "false", "ATTACK", "ICE_BARRAGE"},
        {"CTRL+F10", "NONE", "N_PROTECT_ITEM", "NONE", "true", "false", "ATTACK", "ICE_BARRAGE"},
        {"NONE", "NONE", "NONE", "NONE", "false", "false", "ATTACK", "ICE_BARRAGE"},
    };

    /** Preset gear choice: none or Gear Switcher set 1-10. */
    static final String[] PRESET_GEAR = {"NONE", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
    static final String[] PRESET_GEAR_NAMES = {"None", "Gear set 1", "Gear set 2", "Gear set 3", "Gear set 4", "Gear set 5",
        "Gear set 6", "Gear set 7", "Gear set 8", "Gear set 9", "Gear set 10"};

    /** Prayer tab grid (server prayers.plugin.kts: on_button(271, 8), slot = prayer). */
    static final int PRAYER_HASH = (271 << 16) | 8;
    /** Normal book: Protect from Magic / Missiles / Melee = slots 17 / 18 / 19 (server Prayer.slot). */
    static final int[] NORMAL_PROTECT = {17, 18, 19};
    /** Curses (varbit 6840 set): Deflect Magic / Missiles / Melee = slots 7 / 8 / 9 (server AncientCurse.slot). */
    static final int[] CURSE_DEFLECT = {7, 8, 9};
    static final int CURSES_VARBIT = 6840;
    /** Combat tab special attack bar (server attack_tab.plugin.kts: on_button(884, 4)). */
    static final int SPECIAL_HASH = (884 << 16) | 4;
    /** Normal book Piety / Rigour / Augury = slots 27 / 28 / 29 (server Prayer.slot); curses: Turmoil = slot 19. */
    static final int PIETY_SLOT = 27, RIGOUR_SLOT = 28, AUGURY_SLOT = 29, TURMOIL_SLOT = 19;
    /** Quick-prayer orb next to the minimap (server prayers.plugin.kts: on_button(749, 1)). */
    static final int QUICK_PRAYER_HASH = (749 << 16) | 1;
    /** Lunar spellbook Vengeance (server SpellbookData.VENGEANCE: 430:37); only while the lunar book is the magic tab. */
    static final int LUNAR = 430;
    static final int VENGEANCE_HASH = (LUNAR << 16) | 37;
    static final int PRAYER_BOOK = 271, COMBAT_TAB = 884, ORB = 749;

    static final int BACKPACK_INV = 93;
    static final int BACKPACK_SLOTS = 28;

    private RspsPkHotkeys() {
    }

    /** Called for every keyboard event before the game sees it; true = handled (swallowed). */
    static boolean key(KeyLog log) {
        // The rest of a handled press (repeats, typed character, release) is swallowed in client.java (RspsKeyBind.swallowing).
        if (log.getType() != KeyLog.TYPE_PRESSED || debugconsole.isOpen() || !MainLogicStep.isAtGameScreen(MainLogicManager.step)) {
            return false;
        }
        // Owner 2026-10-16: moderators only - for anyone else not even the Start/Stop key does anything (RspsHudConfig.enabled).
        if (!RspsBotPanel.isStaff()) {
            return false;
        }
        // v4: "Start/Stop plugin" works while the plugin is off (it is the way back on).
        if (RspsKeyBind.pressed(RspsHudConfig.get(ID + ".bind.toggleKey"), log)) {
            boolean on = !RspsHudConfig.enabled(ID);
            RspsSidebar.set(ID + ".enabled", Boolean.toString(on));
            sayAlways("Bruine Bonen Technologia " + (on ? "started." : "stopped."));
            RspsKeyBind.swallowRestOf(log);
            return true;
        }
        if (!RspsHudConfig.enabled(ID)) {
            return false;
        }
        java.util.List<String> actions = actionsFor(log);
        if (actions.isEmpty()) {
            return false;
        }
        // v3.2: a switched-off preset gives way to the other actions on its key; alone it says it is off (Codex review v3.2 #2).
        java.util.List<String> live = new java.util.ArrayList<String>();
        for (String action : actions) {
            if (!scriptOff(action)) {
                live.add(action);
            }
        }
        if (!live.isEmpty()) {
            actions = live;
        }
        // Several actions may share a key (e.g. a normal prayer and a curse, or an ancient and a standard spell): the first
        // one that fits the open prayer book / spellbook runs.
        boolean done = false;
        for (String action : actions) {
            if (run(action)) {
                done = true;
                break;
            }
        }
        if (!done) {
            say("Nothing on this key fits your current prayer book or spellbook.");
        }
        RspsKeyBind.swallowRestOf(log);
        return true;
    }

    /** v4: the script number of a "script<n>Key" action, else 0. */
    static int scriptOf(String action) {
        if (!action.startsWith("script") || !action.endsWith("Key")) {
            return 0;
        }
        try {
            return Integer.parseInt(action.substring(6, action.length() - 3));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** v4: true for the key of a script that is switched off or empty: it gives way to the other actions on that key. */
    static boolean scriptOff(String action) {
        int n = scriptOf(action);
        return n > 0 && (!RspsScripts.enabled(n) || RspsScripts.steps(n).isEmpty());
    }

    /** The binding of [action] ("CTRL+F1", "NONE"...): the player's setting, else the default. */
    static String binding(String[] action) {
        String bound = RspsHudConfig.get(ID + ".bind." + action[0]);
        return bound == null || bound.isEmpty() ? action[1] : bound;
    }

    /** Every action whose binding is this key press, in priority order. */
    static java.util.List<String> actionsFor(KeyLog log) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String[] a : ACTIONS) {
            if (RspsKeyBind.pressed(binding(a), log)) {
                out.add(a[0]);
            }
        }
        return out;
    }

    /** Runs [action]; false = it does not fit the current prayer book / spellbook (the next action on the key may). */
    static boolean run(String action) {
        switch (action) {
            case "attackKey": return targetKey() && attackOrSay();
            case "stepUnderKey": return stepUnderKey();
            case "cancelKey": RspsScriptRunner.cancel(); return true;
            case "clearTargetKey": RspsPkTarget.clear(); return true;
            default: break;
        }
        int script = scriptOf(action);
        if (script > 0) {
            return RspsScriptRunner.run(script);
        }
        runV1(action);
        return true;
    }

    // ---- v2: last target, walk, free prayer and spell keys, presets -------------------------------------------------------

    /** Server switch (owner decision, not editable by players): attack / spell keys on the last target. */
    private static boolean targetKey() {
        if (!RspsHudConfig.bool(ID + ".allowTargetKeys")) {
            say("Target hotkeys are switched off on this server.");
            return true; // handled (nothing else should run on this key)
        }
        return true;
    }

    private static boolean attackOrSay() {
        if (!RspsHudConfig.bool(ID + ".allowTargetKeys")) {
            return true;
        }
        RspsPkTarget.attack();
        return true;
    }

    /** Spell modes (setting pkhotkeys.spellXMode): cast on the last target, or only select the spell (v3.1). */
    static final String[] SPELL_MODES = {"TARGET", "SELECT"};
    static final String[] SPELL_MODE_NAMES = {"Cast on last target", "Select spell only (you click the target)"};

    /** v3: one "Walk here" click on the tile the last target stands on (the walk-key server switch covers it too). */
    private static boolean stepUnderKey() {
        if (!RspsHudConfig.bool(ID + ".allowWalkKey")) {
            say("The walk hotkey is switched off on this server.");
            return true;
        }
        RspsPkTarget.stepUnder();
        return true;
    }

    /** Toggles prayer [id] (N_... normal book, C_... curses); false when that book is not the active one. */
    static boolean togglePrayer(String id) {
        int i = RspsPkData.prayer(id);
        if (i < 0 || (RspsPkData.PRAYER_BOOK[i] == 1) != cursesActive()) {
            return false;
        }
        prayerSlot(RspsPkData.PRAYER_SLOT[i]);
        return true;
    }

    /** v3.2: prayer [id] exists and belongs to the prayer book that is open now (normal or curses). */
    static boolean inActiveBook(String id) {
        int i = RspsPkData.prayer(id);
        return i >= 0 && (RspsPkData.PRAYER_BOOK[i] == 1) == cursesActive();
    }

    /** When we last clicked a prayer on through a preset (prayer index -> time): no second toggle before the varbit updates. */
    private static final java.util.Map<Integer, Long> ENSURE_CLICKED = new java.util.HashMap<Integer, Long>();
    static final long ENSURE_HOLD_MS = 1200L;

    /** Turns prayer [id] on (never off) if it belongs to the active book. */
    static void ensurePrayerOn(String id) {
        int i = RspsPkData.prayer(id);
        if (i < 0) {
            return; // "NONE": nothing to do
        }
        if ((RspsPkData.PRAYER_BOOK[i] == 1) != cursesActive()) {
            say(RspsPkData.PRAYER_NAMES[i] + " is not in your active prayer book - skipped.");
            return;
        }
        int on = varbit(RspsPkData.PRAYER_VARBIT[i]);
        if (on == 1) {
            log(RspsPkData.PRAYER_NAMES[i] + " already on");
            return;
        }
        if (on < 0) {
            // Unknown state: a click could turn it OFF, so do nothing (Codex review finding 3).
            say(RspsPkData.PRAYER_NAMES[i] + ": state unknown, not clicked.");
            return;
        }
        Long last = ENSURE_CLICKED.get(i);
        long now = System.currentTimeMillis();
        if (last != null && now - last < ENSURE_HOLD_MS) {
            log(RspsPkData.PRAYER_NAMES[i] + " was just clicked on, waiting for the server");
            return;
        }
        if (noPrayerPoints()) {
            return; // not marked as clicked: a later press may try again (Codex silent-fail audit)
        }
        ENSURE_CLICKED.put(i, now);
        prayerSlot(RspsPkData.PRAYER_SLOT[i]);
    }

    /** Client value of varbit [id], -1 when the client doesn't know it. */
    static int varbit(int id) {
        try {
            return TimedVarDomain.instance == null ? -1 : TimedVarDomain.instance.getVarBitValue(id);
        } catch (RuntimeException unknown) {
            return -1;
        }
    }

    static void runV1(String action) {
        switch (action) {
            case "protectMagicKey": prayer(0); break;
            case "protectMissilesKey": prayer(1); break;
            case "protectMeleeKey": prayer(2); break;
            case "specialKey": special(); break;
            case "eatKey": eat(); break;
            case "brewKey": drink("brew", "No brew left."); break;
            case "restoreKey": drink("restore", "No restore left."); break;
            case "pietyKey": prayerSlot(cursesActive() ? TURMOIL_SLOT : PIETY_SLOT); break;
            case "rigourKey": normalBookOnly(RIGOUR_SLOT, "Rigour"); break;
            case "auguryKey": normalBookOnly(AUGURY_SLOT, "Augury"); break;
            case "quickPrayersKey": quickPrayers(); break;
            case "vengeanceKey": vengeance(); break;
            default: break;
        }
    }

    /** Toggles protect prayer [i] (0 magic, 1 missiles, 2 melee) - or the matching deflect curse - like a click on it. */
    static void prayer(int i) {
        prayerSlot((cursesActive() ? CURSE_DEFLECT : NORMAL_PROTECT)[i]);
    }

    /** Clicks [slot] of the prayer grid (whichever book is shown), like a click on that prayer. */
    /** v4: true when the click was sent. */
    static boolean prayerSlot(int slot) {
        if (noPrayerPoints()) {
            return false;
        }
        Component c = isOpen(PRAYER_BOOK) ? InterfaceList.getComponent(PRAYER_HASH, slot) : null;
        if (c == null) {
            say("The prayer book is not loaded.");
            return false;
        }
        int op = firstOp(c);
        InterfaceManager.ifButtonXSend(PRAYER_HASH, slot, c.opBase, op + 1);
        log((cursesActive() ? "curse slot " : "prayer slot ") + slot + " clicked (op " + (op + 1) + ")");
        return true;
    }

    /** Rigour / Augury exist only in the normal book. */
    static void normalBookOnly(int slot, String name) {
        if (cursesActive()) {
            say(name + " is not in the curses book.");
            return;
        }
        prayerSlot(slot);
    }

    /** Toggles the special attack bar like a click on it in the combat tab. */
    static void special() {
        Component c = isOpen(COMBAT_TAB) ? InterfaceList.getComponent(SPECIAL_HASH, -1) : null;
        if (c == null) {
            say("The combat tab is not loaded.");
            return;
        }
        InterfaceManager.ifButtonXSend(SPECIAL_HASH, -1, "", 1);
        log("special attack bar clicked");
    }

    /** Left-click on the quick-prayer orb (turns your selected quick prayers on or off). */
    static void quickPrayers() {
        if (noPrayerPoints()) {
            return;
        }
        Component c = isOpen(ORB) ? InterfaceList.getComponent(QUICK_PRAYER_HASH, -1) : null;
        if (c == null) {
            say("The quick-prayer orb is not loaded.");
            return;
        }
        int op = firstOp(c);
        InterfaceManager.ifButtonXSend(QUICK_PRAYER_HASH, -1, c.opBase, op + 1);
        log("quick-prayer orb clicked (op " + (op + 1) + " '" + InterfaceManager.getOp(c, op) + "')");
    }

    /** Casts Vengeance like a click on it in the lunar spellbook (the server checks level, runes and the 30 s delay). */
    static void vengeance() {
        Component c = isOpen(LUNAR) ? InterfaceList.getComponent(VENGEANCE_HASH, -1) : null;
        if (c == null) {
            say("Vengeance needs the Lunar spellbook.");
            return;
        }
        InterfaceManager.ifButtonXSend(VENGEANCE_HASH, -1, "", 1);
        log("vengeance clicked");
    }

    /** True when interface [id] is open as a sub-interface (a side tab counts, visible or not). */
    static boolean isOpen(int id) {
        HashTableIterator iterator = new HashTableIterator(InterfaceManager.subInterfaces);
        for (SubInterface sub = (SubInterface) iterator.first(); sub != null; sub = (SubInterface) iterator.next()) {
            if (sub.id == id) {
                return true;
            }
        }
        return false;
    }

    /** Eats one food (not karambwan; karambwan only when nothing else is left), plus a karambwan with "Combo eat". */
    static void eat() {
        int food = findItem("Eat", null, "karambwan", false);
        int karambwan = findItem("Eat", "karambwan", null, false);
        if (food < 0 && karambwan < 0) {
            say("No food left.");
            return;
        }
        if (food >= 0) {
            click(food, "Eat");
            if (karambwan >= 0 && RspsHudConfig.bool(ID + ".comboEat")) {
                click(karambwan, "Eat");
            }
        } else {
            click(karambwan, "Eat");
        }
    }

    /** Drinks the lowest-dose potion whose name matches [kind] ("brew" or "restore"). */
    static void drink(String kind, String none) {
        int slot = "brew".equals(kind)
            ? findItem("Drink", "saradomin brew", null, true)
            : findItem("Drink", "restore", null, true);
        if (slot < 0 && !"brew".equals(kind)) {
            slot = findItem("Drink", "prayer potion", null, true);
        }
        if (slot < 0) {
            say(none);
            return;
        }
        click(slot, "Drink");
    }

    /**
     * Backpack slot of the first item with inventory option [verb] whose name contains [include] (null = any) and not
     * [exclude]; with [lowestDose] the item with the smallest "(n)" in its name wins (drink the open potion first).
     */
    static int findItem(String verb, String include, String exclude, boolean lowestDose) {
        ClientInventory pack = ClientInventory.get(BACKPACK_INV, false);
        if (pack == null || ObjTypeList.instance == null) {
            return -1;
        }
        int best = -1;
        int bestDose = Integer.MAX_VALUE;
        for (int slot = 0; slot < pack.objs.length && slot < BACKPACK_SLOTS; slot++) {
            int obj = pack.objs[slot];
            if (obj < 0) {
                continue;
            }
            ObjType type = ObjTypeList.instance.list(obj);
            if (type == null || verbIndex(type, verb) < 0) {
                continue;
            }
            String name = type.name == null ? "" : type.name.toLowerCase();
            if ((include != null && !name.contains(include)) || (exclude != null && name.contains(exclude))) {
                continue;
            }
            if (!lowestDose) {
                return slot;
            }
            int dose = dose(name);
            if (dose < bestDose) {
                bestDose = dose;
                best = slot;
            }
        }
        return best;
    }

    /** Clicks [verb] on the item in backpack [slot] (same packet as the real menu option). */
    static void click(int slot, String verb) {
        ClientInventory pack = ClientInventory.get(BACKPACK_INV, false);
        int inv = RspsGearSwitcher.inventoryHash();
        Component c = InterfaceList.getComponent(inv, slot);
        if (pack == null || c == null || slot >= pack.objs.length || c.invObject != pack.objs[slot]) {
            // Codex silent-fail audit 2026-10-12: tell the player instead of only logging.
            say("Your inventory is still updating - open the inventory tab and press again.");
            return;
        }
        int op = -1;
        for (int i = 0; i < 10 && op < 0; i++) {
            if (verb.equalsIgnoreCase(InterfaceManager.getOp(c, i))) {
                op = i;
            }
        }
        if (op < 0) {
            ObjType type = ObjTypeList.instance.list(c.invObject);
            op = type == null ? -1 : verbIndex(type, verb);
        }
        if (op < 0) {
            say("That item has no " + verb + " option.");
            return;
        }
        InterfaceManager.ifButtonXSend(inv, slot, c.opBase, op + 1);
        log(verb + " slot " + slot + " (item " + c.invObject + ")");
    }

    private static int verbIndex(ObjType type, String verb) {
        if (type.iop != null) {
            for (int i = 0; i < type.iop.length; i++) {
                if (verb.equalsIgnoreCase(type.iop[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** "saradomin brew(3)" -> 3; no dose -> 99. */
    static int dose(String name) {
        int open = name.lastIndexOf('(');
        int close = name.lastIndexOf(')');
        if (open >= 0 && close > open + 1) {
            try {
                return Integer.parseInt(name.substring(open + 1, close).trim());
            } catch (NumberFormatException ignored) {
                // not a dose
            }
        }
        return 99;
    }

    private static int firstOp(Component c) {
        for (int i = 0; i < 10; i++) {
            if (InterfaceManager.getOp(c, i) != null) {
                return i;
            }
        }
        return 0;
    }

    static boolean cursesActive() {
        try {
            return TimedVarDomain.instance != null && TimedVarDomain.instance.getVarBitValue(CURSES_VARBIT) == 1;
        } catch (RuntimeException unknownVarbit) {
            return false;
        }
    }

    /** Server Prayers.PRAYER_POINTS_VARP: the prayer points the orb shows. */
    static final int PRAYER_POINTS_VARP = 2382;
    private static long noPointsSaidAt;

    /**
     * True (and a chat line, at most every 3 s) when the player has 0 prayer points: the server then ignores a prayer click
     * without any message, so a prayer key looked broken (user test 2026-10-12: 0/99 prayer, the keys "did nothing").
     */
    static boolean noPrayerPoints() {
        int points;
        try {
            points = TimedVarDomain.instance == null ? -1 : TimedVarDomain.instance.getVarValueInt(PRAYER_POINTS_VARP);
        } catch (RuntimeException unknown) {
            points = -1;
        }
        if (points != 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - noPointsSaidAt > 3000L) {
            noPointsSaidAt = now;
            say("You have no prayer points left - drink a prayer potion / super restore or recharge at an altar.");
        }
        return true;
    }

    /** v5 chat filter: when each line was last written (Codex v5 review #6: per line, not only the last one). */
    private static final java.util.Map<String, Long> CHAT_AT = new java.util.HashMap<String, Long>();

    /**
     * v5 (user 2026-10-12, "smooth"): whether a chat line may be written. Chat messages off = nothing; "Errors only" (the
     * default) drops [info] lines (what was switched or set); the same line within 3 s is dropped either way.
     */
    static boolean chatAllowed(String message, boolean info) {
        if (!RspsHudConfig.bool(ID + ".chatMessages")) {
            return false;
        }
        if (info && !RspsHudConfig.is(ID + ".chatLevel", "ALL")) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (CHAT_AT) {
            Long at = CHAT_AT.get(message);
            if (at != null && now - at < 3000L) {
                return false;
            }
            if (CHAT_AT.size() > 64) {
                CHAT_AT.clear();
            }
            CHAT_AT.put(message, now);
        }
        return true;
    }

    /** v5: an information line (what was set or switched): only with Chat detail "All". */
    static void info(String message) {
        log(message);
        if (chatAllowed(message, true)) {
            try {
                ChatHistory.add(0, 0, "", "", "", "<col=ff9040>[Bruine Bonen] " + message);
            } catch (RuntimeException noChat) {
                // Not in game: the log line is enough.
            }
        }
    }

    static void say(String message) {
        log(message);
        if (chatAllowed(message, false)) {
            try {
                ChatHistory.add(0, 0, "", "", "", "<col=ff9040>[Bruine Bonen] " + message);
            } catch (RuntimeException noChat) {
                // Not in game: the log line is enough.
            }
        }
    }

    /** v4: a chat line even with chat messages off (start/stop must always answer). */
    static void sayAlways(String message) {
        log(message);
        try {
            ChatHistory.add(0, 0, "", "", "", "<col=ff9040>[Bruine Bonen] " + message);
        } catch (RuntimeException noChat) {
            // Not in game.
        }
    }

    static void log(String message) {
        System.out.println("[PK HOTKEYS] " + message);
    }
}
