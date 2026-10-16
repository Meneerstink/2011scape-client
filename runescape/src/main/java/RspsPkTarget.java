import com.jagex.Client;
import com.jagex.ClientConfig;
import com.jagex.core.constants.MiniMenuAction;
import rs2.client.event.keyboard.KeyboardMonitor;
import rs2.client.event.keyboard.SimpleKeyboardMonitor;
import rs2.client.event.mouse.MouseMonitor;

/**
 * 78Legacy PK hotkeys v2 - "last target" (owner request 2026-10-11).
 *
 * The last target is the last player the user himself clicked "Attack" on, or cast a spell on (MiniMenu.doAction calls
 * [observe] for every real menu click). The hotkeys then repeat exactly those clicks on that player: the same menu
 * entries go through the same MiniMenu.doAction code, so the packets (OPPLAYERx, a spell's target mode + OPPLAYERT,
 * MOVE_GAMECLICK) and the client path-finding are identical to a mouse click. The server checks everything as usual
 * (range, multi-combat, Wilderness level, runes).
 *
 * Never picks a target by itself: if the last target is gone (logged out, out of view, another player in that slot) the
 * key does nothing.
 */
public final class RspsPkTarget {

    /** Player slot of the last target (PlayerList.highResolutionPlayers index), -1 = none. */
    static int slot = -1;
    /** Its name when it became the target: a reused slot never redirects an attack to someone else. */
    static String name = "";

    private RspsPkTarget() {
    }

    /** MiniMenu.doAction hook: remembers the player of a real Attack click or spell-on-player click. */
    static void observe(int action, int v1) {
        if (action == MiniMenuAction.TGT_PLAYER) {
            set(v1);
            return;
        }
        int op = playerOpOf(action);
        if (op >= 0 && isAttack(MiniMenu.playerOps[op])) {
            set(v1);
        }
    }

    /** v4 "Set target (right click)": the player picked [playerSlot] as target in the right-click menu. */
    static void setFromMenu(int playerSlot) {
        set(playerSlot);
    }

    /** v4 "Clear target" key: forgets the target. */
    static void clear() {
        if (slot >= 0) {
            RspsPkHotkeys.info("Target cleared.");
        }
        slot = -1;
        name = "";
    }

    private static void set(int playerSlot) {
        PlayerEntity p = playerSlot >= 0 && playerSlot < PlayerList.highResolutionPlayers.length ? PlayerList.highResolutionPlayers[playerSlot] : null;
        if (p == null || p == PlayerEntity.self) {
            return;
        }
        String n = nameOf(p);
        if (playerSlot != slot || !n.equals(name)) {
            slot = playerSlot;
            name = n;
            RspsPkHotkeys.log("last target: " + n + " (slot " + playerSlot + ")");
            RspsPkHotkeys.info("Target: " + n);
        }
    }

    /** Why a target key does nothing: no target yet, or the target is out of view (v3 test 2026-10-12). */
    private static String noTarget() {
        return slot < 0 || name.isEmpty() ? "No target - attack a player once first." : name + " is out of view.";
    }

    /** The sidebar's "Target" line, made on the client thread by [tick] (Codex review v3, finding 4): Swing only reads it. */
    private static volatile String description = "None yet - attack a player";

    /** Client thread, every cycle (RspsGearSwitcher.tick): refreshes the "Target" line when it changes. */
    static void tick() {
        String d = slot < 0 || name.isEmpty() ? "None yet - attack a player" : target() != null ? name : name + " (out of view)";
        if (!d.equals(description)) {
            description = d;
            RspsSidebar.refreshLater();
        }
    }

    /** The "Target" line: the name, and whether that player is in view right now (any thread). */
    static String describe() {
        return description;
    }

    /** The last target if it is still in view and still the same player, else null. */
    static PlayerEntity target() {
        if (slot < 0 || slot >= PlayerList.highResolutionPlayers.length) {
            return null;
        }
        PlayerEntity p = PlayerList.highResolutionPlayers[slot];
        if (p == null || !nameOf(p).equals(name)) {
            return null;
        }
        return p;
    }

    /** "Attack" on the last target (melee or ranged: whatever is wielded), like the menu click. */
    static boolean attack() {
        PlayerEntity p = target();
        if (p == null) {
            RspsPkHotkeys.say(noTarget());
            return false;
        }
        int op = attackOp();
        if (op < 0) {
            RspsPkHotkeys.say("You can't attack " + name + " here.");
            return false;
        }
        click(entry(MiniMenu.playerOps[op], MiniMenuAction.PLAYER_OPS[op], slot, 0, 0));
        RspsPkHotkeys.log("attack " + name);
        return true;
    }

    /**
     * Casts the spell at [book]:[component] on the last target: the two clicks a player makes (the spell in the open
     * spellbook, then the player), through the same MiniMenu.doAction entries.
     */
    static boolean cast(int book, int component, String spellName) {
        PlayerEntity p = target();
        if (p == null) {
            RspsPkHotkeys.say(noTarget());
            return true; // handled: no other action on this key should run instead
        }
        if (!RspsPkHotkeys.isOpen(book)) {
            return false; // another spellbook: the caller tries the next action on this key
        }
        int hash = (book << 16) | component;
        if (InterfaceList.getComponent(hash, -1) == null) {
            return false;
        }
        InterfaceManager.endTargetMode();
        try {
            click(entry("Cast", MiniMenuAction.TGT_BUTTON, 0, -1, hash));
            if (!InterfaceManager.targetMode) {
                RspsPkHotkeys.say("You can't cast " + spellName + " now.");
                return true;
            }
            click(entry(InterfaceManager.targetVerb, MiniMenuAction.TGT_PLAYER, slot, 0, 0));
        } finally {
            InterfaceManager.endTargetMode(); // never leave the spell selected
        }
        RspsPkHotkeys.log("cast " + spellName + " on " + name);
        return true;
    }

    /**
     * v3.1 "Select spell only": the first of the two clicks of a cast - the spell in the open spellbook - so the spell is
     * selected (target cursor) and the player clicks the target himself. False when the spell is not in the open spellbook.
     */
    static boolean select(int book, int component, String spellName) {
        if (!RspsPkHotkeys.isOpen(book)) {
            return false;
        }
        int hash = (book << 16) | component;
        if (InterfaceList.getComponent(hash, -1) == null) {
            return false;
        }
        InterfaceManager.endTargetMode();
        click(entry("Cast", MiniMenuAction.TGT_BUTTON, 0, -1, hash));
        if (!InterfaceManager.targetMode) {
            RspsPkHotkeys.say("You can't cast " + spellName + " now.");
        } else {
            RspsPkHotkeys.log("selected " + spellName);
        }
        return true;
    }

    /** "Walk here" on the tile under the mouse: runs the walk entry the menu already holds for that tile. */
    static boolean walkHere() {
        if (MiniMenu.open) {
            // The open right-click menu holds an older tile, not the one under the mouse (Codex review finding 4).
            RspsPkHotkeys.say("Close the menu first.");
            return true;
        }
        for (MiniMenuEntryInner e = (MiniMenuEntryInner) MiniMenu.innerEntryQueue.first(); e != null; e = (MiniMenuEntryInner) MiniMenu.innerEntryQueue.next()) {
            int action = e.action >= 2000 ? e.action - 2000 : e.action;
            if (action == MiniMenuAction.WALK) {
                click(e);
                RspsPkHotkeys.log("walk here " + e.v2 + "," + e.v3);
                return true;
            }
        }
        RspsPkHotkeys.say("Move the mouse over the game world first.");
        return false;
    }

    /**
     * v3 "Step under target": one "Walk here" click on the tile the last target stands on (the tile the client last saw it
     * move to). Same menu entry, same MOVE_GAMECLICK packet and client path-finding as clicking that tile by hand; the server
     * walks you there as usual (it may stop you next to a player that blocks the tile, exactly as for a click).
     */
    static boolean stepUnder() {
        PlayerEntity p = target();
        if (p == null) {
            RspsPkHotkeys.say(noTarget());
            return false;
        }
        if (MiniMenu.open) {
            RspsPkHotkeys.say("Close the menu first.");
            return true;
        }
        // In MiniMenu.doAction a walk click with Ctrl+Shift held is the admins' teleport: never teleport from a hotkey.
        KeyboardMonitor k = KeyboardMonitor.instance;
        if (k != null && k.isPressed(SimpleKeyboardMonitor.KEY_CODE_CONTROL) && k.isPressed(SimpleKeyboardMonitor.KEY_CODE_SHIFT)
            && Client.staffModLevel >= ClientConfig.TELEPORT_MIN_STAFF_LEVEL) {
            RspsPkHotkeys.say("Ctrl+Shift+walk is the staff teleport: bind Step under target to a key without Ctrl+Shift.");
            return true;
        }
        int x = p.pathX[0];
        int z = p.pathZ[0];
        if (x < 0 || z < 0) {
            RspsPkHotkeys.say(name + "'s position is not known yet.");
            return true;
        }
        click(entry(Static331.moveText, MiniMenuAction.WALK, 0L, x, z));
        RspsPkHotkeys.log("step under " + name + " " + x + "," + z);
        return true;
    }

    private static void click(MiniMenuEntryInner e) {
        MouseMonitor m = MouseMonitor.instance;
        MiniMenu.doAction(m == null ? 0 : m.getRecordedY(), e, m == null ? 0 : m.getRecordedX());
    }

    private static MiniMenuEntryInner entry(String op, int action, long v1, int v2, int v3) {
        return new MiniMenuEntryInner(op == null ? "" : op, "", -1, action, -1, v1, v2, v3, true, false, v1, false);
    }

    private static int playerOpOf(int action) {
        for (int op = 0; op < MiniMenuAction.PLAYER_OPS.length && op < MiniMenu.playerOps.length; op++) {
            if (action == MiniMenuAction.PLAYER_OPS[op]) {
                return op;
            }
        }
        return -1;
    }

    /** Index of the "Attack" player option the server set for this area (it moves around), -1 = none. */
    static int attackOp() {
        for (int op = 0; op < MiniMenu.playerOps.length && op < MiniMenuAction.PLAYER_OPS.length; op++) {
            if (isAttack(MiniMenu.playerOps[op])) {
                return op;
            }
        }
        return -1;
    }

    private static boolean isAttack(String op) {
        return op != null && op.replaceAll("<[^>]*>", "").trim().equalsIgnoreCase("Attack");
    }

    private static String nameOf(PlayerEntity p) {
        String n = p.displayName != null ? p.displayName : p.getNameUnfiltered();
        // Names may carry colour tags (e.g. the PK-bot marker): compare and show the plain name.
        return n == null ? "" : n.replaceAll("<[^>]*>", "");
    }
}
