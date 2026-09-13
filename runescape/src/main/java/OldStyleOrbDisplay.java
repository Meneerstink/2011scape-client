/**
 * Old-style (pre-Constitution) rendering of the hitpoints and prayer minimap orbs.
 *
 * <p>The server publishes lifepoints and prayer points in 1:1 units through varbits 7198 and 9816.
 * The orb client scripts read those varbits directly - script 2923 for the hitpoints orb, script
 * 801 for the prayer orb.
 *
 * <p>Both scripts use the same varbit twice: once stringified into the orb's text component, and
 * once as the numerator of a {@code SCALE(value, max, 100)} percentage that fills the orb ring.
 * The text hook remains at the final component boundary, but is now an identity operation because
 * the server and cache use the same 1:1 unit.
 */
public final class OldStyleOrbDisplay {
    /** Text component of the hitpoints orb: interface 748, component 8. */
    public static final int HITPOINTS_ORB_TEXT = (748 << 16) | 8;

    /** Text component of the prayer orb: interface 749, component 4. */
    public static final int PRAYER_ORB_TEXT = (749 << 16) | 4;

    private OldStyleOrbDisplay() {
    }

    /**
     * @param id   the component's dynamic child index, or -1 for a static interface component.
     * @param slot the component's {@code (interfaceId << 16) | componentSlot} hash.
     */
    public static boolean isOrbText(int id, int slot) {
        return id == -1 && (slot == HITPOINTS_ORB_TEXT || slot == PRAYER_ORB_TEXT);
    }

    /** Returns the text unchanged for the two orb components only. */
    public static String text(int id, int slot, String text) {
        if (!isOrbText(id, slot)) {
            return text;
        }
        return scale(text);
    }

    /**
     * Identity compatibility helper retained for the narrow orb text boundary.
     */
    static String scale(String text) {
        if (text == null || text.isEmpty() || text.length() > 9) {
            return text;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return text;
            }
        }
        return text;
    }
}
