
/**
 * 78Legacy v4 "Bruine Bonen Technologia": the right-click entries (UG "Easy Setup") - client only, no packet is sent:
 * - an item in the backpack that can be worn: "Add Wear to Script N";
 * - a prayer in the prayer book: "Add Prayer ON / Prayer OFF / Prayer toggle to Script N";
 * - another player: "Set target" (Main Config -> Set target (right click)): you pick your target yourself.
 * N = Script Config -> "Add to". Hooks: InterfaceManager.addMiniMenuOptions (items, prayers), MiniMenu player options
 * (Set target), MiniMenu.doAction ([handle]).
 */
final class RspsScriptMenu {

    static final int ACTION_ADD_WEAR = 1960;
    static final int ACTION_PRAY_ON = 1961;
    static final int ACTION_PRAY_OFF = 1962;
    static final int ACTION_PRAY_TOGGLE = 1963;
    static final int ACTION_SET_TARGET = 1964;

    /** Low priority (MiniMenu: +2000 never becomes the left-click option). */
    private static final int LOW = 2000;

    private RspsScriptMenu() {
    }

    private static boolean setupOn() {
        return RspsHudConfig.enabled(RspsPkHotkeys.ID) && RspsHudConfig.bool(RspsPkHotkeys.ID + ".rightClickSetup");
    }

    static int addTo() {
        try {
            int n = Integer.parseInt(RspsHudConfig.get(RspsPkHotkeys.ID + ".addTo"));
            return n >= 1 && n <= RspsScripts.COUNT ? n : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** InterfaceManager.addMiniMenuOptions, first line: added first = shown at the bottom of the menu. */
    static void addComponentOptions(Component component) {
        if (component == null || !setupOn()) {
            return;
        }
        int n = addTo();
        String to = " to Script " + n;
        // Note: in this client Component.slot is the interface hash and Component.id the slot (see RspsGearSwitcher).
        if (component.slot == RspsGearSwitcher.inventoryHash() && component.invObject >= 0) {
            if (RspsGearSwitcher.equipOp(component, component.invObject) >= 0) {
                MiniMenu.addEntryInner(false, -1, component.invObject, component.id, component.slot, "<col=ffff00>Add Wear</col> action" + to,
                    ACTION_ADD_WEAR + LOW, true, -1, "<col=ff9040>" + RspsGearSets.itemName(component.invObject), ((long) component.id << 8) | component.slot, false);
            }
            return;
        }
        if (component.slot == RspsPkHotkeys.PRAYER_HASH) {
            int prayer = prayerAt(component.id);
            if (prayer < 0) {
                return;
            }
            String name = "<col=ff9040>" + RspsPkData.PRAYER_NAMES[prayer];
            long key = ((long) component.id << 8) | component.slot;
            // Added bottom-up: toggle, OFF, ON (ON ends up highest of the three).
            MiniMenu.addEntryInner(false, -1, prayer, component.id, component.slot, "Add <col=ff8c3a>Prayer toggle</col> action" + to, ACTION_PRAY_TOGGLE + LOW, true, -1, name, key, false);
            MiniMenu.addEntryInner(false, -1, prayer, component.id, component.slot, "Add <col=ff4040>Prayer OFF</col> action" + to, ACTION_PRAY_OFF + LOW, true, -1, name, key, false);
            MiniMenu.addEntryInner(false, -1, prayer, component.id, component.slot, "Add <col=ff4040>Prayer ON</col> action" + to, ACTION_PRAY_ON + LOW, true, -1, name, key, false);
        }
    }

    /** The prayer (RspsPkData index) at prayer-grid slot [slot] of the book that is open now, -1 = none. */
    static int prayerAt(int slot) {
        int book = RspsPkHotkeys.cursesActive() ? 1 : 0;
        for (int i = 0; i < RspsPkData.PRAYER_SLOT.length; i++) {
            if (RspsPkData.PRAYER_SLOT[i] == slot && RspsPkData.PRAYER_BOOK[i] == book) {
                return i;
            }
        }
        return -1;
    }

    /** MiniMenu player options: show "Set target"? */
    static boolean setTargetOption() {
        return RspsHudConfig.enabled(RspsPkHotkeys.ID) && RspsHudConfig.bool(RspsPkHotkeys.ID + ".setTargetRightClick");
    }

    /** MiniMenu.doAction (after the 2000 offset is removed): true = one of ours, handled here. */
    static boolean handle(int action, long v1) {
        if (action == ACTION_SET_TARGET) {
            RspsPkTarget.setFromMenu((int) v1);
            return true;
        }
        if (action < ACTION_ADD_WEAR || action > ACTION_PRAY_TOGGLE) {
            return false;
        }
        int n = addTo();
        RspsScripts.Step step;
        if (action == ACTION_ADD_WEAR) {
            step = new RspsScripts.Step(RspsScripts.WEAR, Long.toString(v1));
        } else {
            int prayer = (int) v1;
            if (prayer < 0 || prayer >= RspsPkData.PRAYER_IDS.length) {
                return true;
            }
            String type = action == ACTION_PRAY_ON ? RspsScripts.PRAY_ON : action == ACTION_PRAY_OFF ? RspsScripts.PRAY_OFF : RspsScripts.PRAY_TOGGLE;
            step = new RspsScripts.Step(type, RspsPkData.PRAYER_IDS[prayer]);
        }
        if (RspsScripts.add(n, step)) {
            RspsPkHotkeys.sayAlways("Added to " + RspsScriptRunner.title(n) + ": " + step.label() + ".");
        } else {
            RspsPkHotkeys.sayAlways(RspsScriptRunner.title(n) + " is full (" + RspsScripts.MAX_STEPS + " steps).");
        }
        return true;
    }
}
