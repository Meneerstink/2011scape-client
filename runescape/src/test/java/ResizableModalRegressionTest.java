/**
 * Guards N193 (owner 2026-10-22: "deadman donator and loyalty store when opening them in resizable mode the shop doesnt layout
 * correctly in the middle of the screen it now opens a little bit to the right"). The resizable modal slot 746:12 is centred on the
 * view; the gameframe script pushes it against the minimap when that centre would run into the right-hand column. In the owner's
 * window (Stretched Mode 16 %: an 872 x 503 game) the store then stood at x 137..649, against the minimap (746:13, left edge
 * width - 223). It is centred in the free area left of the column instead; a wide view keeps the game's own position.
 */
public final class ResizableModalRegressionTest {

    public static void main(String[] args) {
        // The owner's view: 872 wide, minimap from 649, side panel (746:79, 200 wide) from 672.
        require(InterfaceManager.narrowModalX(872, 512, 872 - 223, 872 - 200) == (649 - 512) / 2, "owner's view: centred left of the minimap");
        // The smallest resizable game (RuneLite floor 765 x 503): as far left as the free area allows, never off-screen.
        require(InterfaceManager.narrowModalX(765, 512, 765 - 223, 765 - 200) == 15, "765 wide: centred in 542 px");
        require(InterfaceManager.narrowModalX(600, 512, 600 - 223, 600 - 200) == 0, "free area narrower than the window: x 0");
        // A wide view: the centred window is clear of the column - the game's own position stays (-1).
        require(InterfaceManager.narrowModalX(1250, 512, 1250 - 223, 1250 - 200) == -1, "1250 wide: unchanged");
        require(InterfaceManager.narrowModalX(957, 512, 957 - 223, 957 - 200) == -1, "957 wide: the centre just fits");
        require(InterfaceManager.narrowModalX(956, 512, 956 - 223, 956 - 200) == (733 - 512) / 2, "956 wide: one pixel short");
        // A missing block (-1) never moves the window.
        require(InterfaceManager.narrowModalX(872, 512, -1, -1) == -1, "no right column: unchanged");
        require(InterfaceManager.narrowModalX(872, 512, -1, 672) == (672 - 512) / 2, "side panel only");

        // The slots are the packed ids InterfaceList uses (component | interface << 16).
        require(InterfaceManager.RESIZABLE_MODAL == ((746 << 16) | 12), "modal slot 746:12");
        require(InterfaceManager.RESIZABLE_MINIMAP == ((746 << 16) | 13), "minimap 746:13");
        require(InterfaceManager.RESIZABLE_SIDE_PANEL == ((746 << 16) | 79), "side panel 746:79");
        System.out.println("ResizableModalRegressionTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
