/**
 * N1 / N120 (owner decision PWD 2026-10-13: "Recover Password" op het loginscherm blijft verborgen): the 2011 login box (cache
 * interface 596) without the Jagex account services 78 does not have - "Recover Your Password" (596:47, a link to Jagex's
 * passwordchoice page), the "Or log in with:" Facebook button with its "(Opens a popup window)" note (596:50, 51, 54) and
 * "Create Account Now" (596:61) - and their separator lines (596:48, 49, 52, 53). The box (596:4 frame, 596:5 content) ends just
 * under the Log In button; its frame pieces are anchored to the bottom (y mode 2) and stretch (h mode 1), so they follow.
 */
final class LoginScreenTrim {

    static final int INTERFACE = 596;
    static final int[] HIDDEN = {47, 48, 49, 50, 51, 52, 53, 54, 61};
    static final int FRAME = 4;
    static final int CONTENT = 5;
    /** Log In button layer 596:45 is at y 149, 26 high; 17 px below it like the space above the Username field. */
    static final int HEIGHT = 192;
    /** The box's backdrop 596:1 (326 high for the 305 box) shrinks by the same amount. */
    static final int BACKDROP = 1;
    static final int BACKDROP_HEIGHT = 326 - (305 - HEIGHT);

    private LoginScreenTrim() {
    }

    static boolean hidden(int slot) {
        for (int h : HIDDEN) {
            if (h == slot) {
                return true;
            }
        }
        return false;
    }

    /** InterfaceList: a cache component of [id] was just decoded. */
    static void apply(int id, int slot, Component component) {
        if (id != INTERFACE) {
            return;
        }
        if (hidden(slot)) {
            component.hidden = true;
        } else if (slot == FRAME || slot == CONTENT) {
            component.originalHeight = HEIGHT;
        } else if (slot == BACKDROP) {
            component.originalHeight = BACKDROP_HEIGHT;
        }
    }
}
