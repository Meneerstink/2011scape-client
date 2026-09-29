import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Toolkit;
import rs2.client.event.mouse.MouseLog;
import rs2.client.event.mouse.MouseMonitor;

/**
 * "78 Plugins": switch every information overlay on or off with a click (owner 2026-09-29: "de plugins moeten ook uit en
 * ingeschakeld kunnen worden"). Opened with the `hud` (or `plugins`) command, or by holding Alt and clicking the
 * "78 Plugins" button that then shows at the top of the game view. A click sets the switch at once and sends the same
 * `hud ...` command the player could type, so the server saves it with the account and pushes it back.
 * The panel itself is movable with Alt like every other overlay.
 */
final class RspsHudSettings {

    static final String ID = "settings";

    private static final int WIDTH = 196;
    private static final int TITLE = 20;
    private static final int ROW = 17;
    private static final int PAD = 6;
    private static final int TITLE_COLOUR = 0xFFFF981F;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFB4ACA0;
    private static final int ON = 0xFF00C040;
    private static final int BOX = 0xFFC8C0B4;
    private static final int HOVER = 0x40FFFFFF;

    /** Rows: label, indent, the switch it shows. */
    private static final String[] LABELS = {
        "Boosts Information", "Show all five combat stats", "Opponent Information", "Ground Items", "Show values",
        "Show despawn timers", "Timers & Buffs",
    };
    private static final boolean[] INDENTED = {false, true, false, false, true, true, false};

    private static boolean open;

    private static int panelX;
    private static int panelY;
    private static int panelHeight;
    private static int altButtonX = -1;
    private static int altButtonY;
    private static int altButtonWidth;

    private RspsHudSettings() {
    }

    static void open() {
        open = true;
    }

    static void close() {
        open = false;
    }

    static boolean isOpen() {
        return open;
    }

    private static boolean value(int row) {
        switch (row) {
            case 0: return RspsHudConfig.boosts;
            case 1: return RspsHudConfig.boostsAll;
            case 2: return RspsHudConfig.opponent;
            case 3: return RspsHudConfig.groundItems;
            case 4: return RspsHudConfig.value;
            case 5: return RspsHudConfig.despawn;
            default: return RspsHudConfig.timers;
        }
    }

    /** Sets the switch here at once and sends the matching `hud` command (the server saves it and pushes it back). */
    static String toggle(int row) {
        boolean on = !value(row);
        String command;
        switch (row) {
            case 0: RspsHudConfig.boosts = on; command = "hud boosts " + onOff(on); break;
            case 1: RspsHudConfig.boostsAll = on; command = "hud boosts " + (on ? "all" : "changed"); break;
            case 2: RspsHudConfig.opponent = on; command = "hud opponent " + onOff(on); break;
            case 3: RspsHudConfig.groundItems = on; command = "hud grounditems " + onOff(on); break;
            case 4: RspsHudConfig.value = on; command = "hud value " + onOff(on); break;
            case 5: RspsHudConfig.despawn = on; command = "hud despawn " + onOff(on); break;
            default: RspsHudConfig.timers = on; command = "hud timers " + onOff(on); break;
        }
        RspsHudGroundItems.markDirty();
        return command;
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }

    private static int rowsTop() {
        return panelY + TITLE + 2;
    }

    private static int resetButtonY() {
        return rowsTop() + LABELS.length * ROW + 4 + 2 * 12 + 4;
    }

    private static int height() {
        return TITLE + 2 + LABELS.length * ROW + 4 + 2 * 12 + 4 + 18 + PAD;
    }

    static void render(int viewX, int viewY, int viewWidth, int viewHeight, int top) {
        altButtonX = -1;
        Font font = RspsHud.font();
        Font small = Fonts.p11 != null ? Fonts.p11 : font;
        FontMetrics metrics = RspsHud.metrics();
        if (!open) {
            if (RspsOverlayLayout.altHeld()) {
                // Hold Alt: a small button at the top of the game view opens the panel.
                String text = "78 Plugins";
                altButtonWidth = RspsHud.width(metrics, text) + 12;
                altButtonX = viewX + (viewWidth - altButtonWidth) / 2;
                altButtonY = top;
                RspsHud.box(altButtonX, altButtonY, altButtonWidth, 18);
                font.render(text, altButtonX + 6, altButtonY + 14, RspsHud.SHADOW, TITLE_COLOUR);
            }
            return;
        }
        panelHeight = height();
        int[] at = RspsOverlayLayout.place(ID, viewX + (viewWidth - WIDTH) / 2, top + 24, WIDTH, panelHeight, viewX, viewY, viewWidth, viewHeight);
        panelX = at[0];
        panelY = at[1];
        RspsHud.box(panelX, panelY, WIDTH, panelHeight);
        Toolkit.active.fillRect(panelX + 1, panelY + 1, WIDTH - 2, TITLE - 1, 0x60000000);
        font.render("78 Plugins", panelX + PAD, panelY + 15, RspsHud.SHADOW, TITLE_COLOUR);
        font.renderRight("x", panelX + WIDTH - PAD, panelY + 15, TEXT, RspsHud.SHADOW);

        int mouseX = MouseMonitor.instance == null ? -1 : MouseMonitor.instance.getRecordedX();
        int mouseY = MouseMonitor.instance == null ? -1 : MouseMonitor.instance.getRecordedY();
        for (int i = 0; i < LABELS.length; i++) {
            int rowY = rowsTop() + i * ROW;
            if (mouseX >= panelX && mouseX < panelX + WIDTH && mouseY >= rowY && mouseY < rowY + ROW) {
                Toolkit.active.fillRect(panelX + 1, rowY, WIDTH - 2, ROW, HOVER);
            }
            int boxX = panelX + PAD + (INDENTED[i] ? 14 : 0);
            int boxY = rowY + 4;
            Toolkit.active.outlineRect(boxX, boxY, 9, 9, BOX);
            if (value(i)) {
                Toolkit.active.fillRect(boxX + 2, boxY + 2, 5, 5, ON);
            }
            boolean parentOff = INDENTED[i] && !value(i == 1 ? 0 : 3);
            font.render(LABELS[i], boxX + 14, rowY + 13, RspsHud.SHADOW, parentOff ? HINT : TEXT);
        }
        int hintY = rowsTop() + LABELS.length * ROW + 4;
        small.render("Hold Alt and drag a panel to move it.", panelX + PAD, hintY + 10, RspsHud.SHADOW, HINT);
        small.render("Alt + right-click a panel: back to its place.", panelX + PAD, hintY + 22, RspsHud.SHADOW, HINT);
        int buttonY = resetButtonY();
        String reset = "Reset all positions";
        int buttonWidth = RspsHud.width(metrics, reset) + 12;
        Toolkit.active.fillRect(panelX + PAD, buttonY, buttonWidth, 18, 0x80000000);
        Toolkit.active.outlineRect(panelX + PAD, buttonY, buttonWidth, 18, BOX);
        font.render(reset, panelX + PAD + 6, buttonY + 13, RspsHud.SHADOW, TEXT);
        RspsOverlayLayout.decorate(ID);
    }

    /** A press without Alt: the panel's own clicks. Every press on the open panel is taken (never reaches the game). */
    static boolean click(int x, int y, int type) {
        if (!open || x < panelX || y < panelY || x >= panelX + WIDTH || y >= panelY + panelHeight) {
            return false;
        }
        if (type != MouseLog.TYPE_PRESS_LEFT) {
            return true;
        }
        if (y < panelY + TITLE && x >= panelX + WIDTH - PAD - 14) {
            close();
            return true;
        }
        int row = (y - rowsTop()) / ROW;
        if (y >= rowsTop() && row >= 0 && row < LABELS.length) {
            send(toggle(row));
            return true;
        }
        int buttonY = resetButtonY();
        if (y >= buttonY && y < buttonY + 18) {
            RspsOverlayLayout.resetAll();
        }
        return true;
    }

    /** Last command sent (tests). */
    static String lastSent;

    private static void send(String command) {
        lastSent = command;
        try {
            RspsDev.send(command);
        } catch (RuntimeException notConnected) {
            // No game connection (reconnecting): the switch is set here; the server's login push restores its saved value.
        }
    }

    /** Tests: the panel's spot without a toolkit to render it. */
    static void layoutForTest(int x, int y) {
        panelX = x;
        panelY = y;
        panelHeight = height();
    }

    /** Alt + click on the "78 Plugins" button (shown while Alt is held). */
    static boolean altButtonClick(int x, int y, int type) {
        if (altButtonX < 0 || x < altButtonX || y < altButtonY || x >= altButtonX + altButtonWidth || y >= altButtonY + 18) {
            return false;
        }
        if (type == MouseLog.TYPE_PRESS_LEFT) {
            open();
        }
        return true;
    }
}
