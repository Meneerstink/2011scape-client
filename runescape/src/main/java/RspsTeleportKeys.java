import com.jagex.core.constants.MainLogicStep;
import com.jagex.core.datastruct.key.HashTableIterator;
import rs2.client.event.keyboard.KeyLog;
import com.jagex.game.runetek6.config.iftype.SubInterface;

/**
 * RSPS house teleport screens (owner bug 2026-10-06 183223): the OSRS keyboard shortcuts of the Portal Nexus Teleport Menu
 * (OSRS clientscript 2675 / telenexus_keyinput_listener) and the jewellery box (OSRS clientscript 1688 /
 * poh_jewellery_box_keypress). The server builds both screens as 667 interfaces [NEXUS] / [JEWELLERY_BOX]
 * (PohTeleportInterfaceImportTool) and writes each destination's key in front of its name: the k-th visible destination has
 * key 1-9 then A-Z. A typed key picks that destination exactly as a left-click does (IF_BUTTON1 on its component), and the key
 * never reaches the chat box. Any other key passes through.
 */
public final class RspsTeleportKeys {

    static final int NEXUS = 1178;
    static final int JEWELLERY_BOX = 1179;

    /** PohTeleportInterfaceImportTool.Nexus: rows start at 20, five components each, 80 rows. */
    private static final int NEXUS_ROW_FIRST = 20;
    private static final int NEXUS_ROW_STRIDE = 5;
    private static final int NEXUS_ROWS = 80;

    /** PohTeleportInterfaceImportTool.Box: six boxes from 14, twelve components each, buttons at +6..+11. */
    private static final int BOX_FIRST = 14;
    private static final int BOX_STRIDE = 12;
    private static final int BOX_BUTTON_FIRST = 6;
    private static final int BOX_BUTTONS = 6;

    private RspsTeleportKeys() {
    }

    /** Called for every keyboard event before the game sees it; true = handled (swallowed). */
    static boolean key(KeyLog log) {
        if (debugconsole.isOpen() || !MainLogicStep.isAtGameScreen(MainLogicManager.step)) {
            return false;
        }
        int type = log.getType();
        if (type != KeyLog.TYPE_TYPED && type != KeyLog.TYPE_HELD) {
            return false;
        }
        int index = keyIndex(log.getKeyChar());
        if (index < 0) {
            return false;
        }
        int interfaceId;
        if (isOpen(NEXUS)) {
            interfaceId = NEXUS;
        } else if (isOpen(JEWELLERY_BOX)) {
            interfaceId = JEWELLERY_BOX;
        } else {
            return false;
        }
        int component = interfaceId == NEXUS ? nexusComponent(index) : boxComponent(index);
        if (component < 0) {
            return false;
        }
        // A typed key arrives as a HELD record plus a TYPED one (SimpleKeyboardMonitor.record): act once, swallow both.
        if (type == KeyLog.TYPE_TYPED) {
            AvTrace.log("teleport key '" + log.getKeyChar() + "' -> " + interfaceId + ":" + component);
            InterfaceManager.ifButtonXSend((interfaceId << 16) | component, -1, "", 1);
        }
        return true;
    }

    /** v3 (PK Hotkeys): true while the nexus or jewellery box teleport screen is open (its letter/digit shortcuts win). */
    static boolean menuOpen() {
        return isOpen(NEXUS) || isOpen(JEWELLERY_BOX);
    }

    /** OSRS enum 1401/1402: 1-9 are 0..8, A-Z (either case) 9..34. */
    static int keyIndex(char c) {
        if (c >= '1' && c <= '9') {
            return c - '1';
        }
        char upper = Character.toUpperCase(c);
        if (upper >= 'A' && upper <= 'Z') {
            return 9 + (upper - 'A');
        }
        return -1;
    }

    private static int nexusComponent(int index) {
        int visible = 0;
        for (int row = 0; row < NEXUS_ROWS; row++) {
            int component = NEXUS_ROW_FIRST + row * NEXUS_ROW_STRIDE;
            if (shown(NEXUS, component)) {
                if (visible == index) {
                    return component;
                }
                visible++;
            }
        }
        return -1;
    }

    private static int boxComponent(int index) {
        int visible = 0;
        for (int box = 0; box < 6; box++) {
            for (int button = 0; button < BOX_BUTTONS; button++) {
                int component = BOX_FIRST + box * BOX_STRIDE + BOX_BUTTON_FIRST + button;
                if (shown(JEWELLERY_BOX, component)) {
                    if (visible == index) {
                        return component;
                    }
                    visible++;
                }
            }
        }
        return -1;
    }

    private static boolean shown(int interfaceId, int component) {
        Component c = InterfaceList.getComponent((interfaceId << 16) | component, -1);
        return c != null && !c.hidden;
    }

    private static boolean isOpen(int id) {
        HashTableIterator iterator = new HashTableIterator(InterfaceManager.subInterfaces);
        for (SubInterface sub = (SubInterface) iterator.first(); sub != null; sub = (SubInterface) iterator.next()) {
            if (sub.id == id) {
                return true;
            }
        }
        return false;
    }
}
