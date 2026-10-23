/**
 * Guards N176 (owner 2026-10-22: the Quest Helper window that opens when a quest starts could not be moved with Alt like the other
 * overlays). The panel is the server's cache interface 1166 (QuestHelperPanelImportTool: component 1 is the panel layer inside the
 * main-screen overlay slot); InterfaceManager.draw places that layer through RspsOverlayLayout, the same Alt + drag / Alt +
 * right-click code as the HUD panels and the Deadman HUD. Checked in the client: screens\N176 (07 Alt outline, 08 moved, 09 back).
 */
public final class QuestHelperPanelMoveRegressionTest {

    public static void main(String[] args) {
        require(InterfaceManager.QUEST_HELPER_PANEL == ((1166 << 16) | 1), "the Quest Helper panel layer is 1166:1");
        require("questhelper".equals(InterfaceManager.QUEST_HELPER_LAYOUT), "its spot is kept under 'questhelper' in overlays.properties");
        require(!InterfaceManager.QUEST_HELPER_LAYOUT.equals(DeadmanSkullHud.layoutId(true)) && !InterfaceManager.QUEST_HELPER_LAYOUT.equals(DeadmanSkullHud.layoutId(false)),
            "it never shares a spot with the Deadman HUD");
        System.out.println("QuestHelperPanelMoveRegressionTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
