import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Toolkit;
import rs2.client.event.mouse.MouseLog;
import rs2.client.event.mouse.MouseMonitor;

import java.util.ArrayList;
import java.util.List;

/**
 * "78 Plugins" - the RuneLite-style configuration panel of the information overlays (owner 2026-09-29: "de plugins moeten
 * net als runelite aan en uit geschakeld kunnen worden en sommige instellingen moet je kunnen veranderen").
 *
 * Main view: every plugin with its on/off switch and a ">" that opens its settings. Settings views: check boxes, values
 * (value tiers, "hide under"), colours and the highlighted / hidden item lists. A click on a switch or check box takes
 * effect here at once and sends a quiet `hud set ...`; values, colours and list entries open the game's own input box
 * through the server (`hud edit ...`). The server saves everything with the account and pushes it back (`cfg`, `hl`,
 * `hide`), which is always the final state. Opened with `hud` / `plugins`, or Alt + the "78 Plugins" button. Movable with
 * Alt like every overlay; every press on the open panel is taken, so it never reaches the game.
 */
final class RspsHudSettings {

    static final String ID = "settings";

    static final int WIDTH = 214;
    static final int TITLE = 20;
    static final int ROW = 17;
    static final int PAD = 6;
    /** Width of the click zone at the right end of a row (">" open, "x" remove, a colour swatch). */
    static final int RIGHT_ZONE = 24;
    /** The two buttons of a list header: "Add" (right) and "Clear" left of it. */
    static final int ADD_ZONE = 34;
    static final int CLEAR_ZONE = 42;
    private static final int MAX_LIST_ROWS = 8;

    private static final int TITLE_COLOUR = 0xFFFF981F;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFB4ACA0;
    private static final int VALUE = 0xFFFFFF00;
    private static final int ON = 0xFF00A040;
    private static final int OFF = 0xFF5A5046;
    private static final int BOX = 0xFFC8C0B4;
    private static final int REMOVE = 0xFFFF4040;
    private static final int HOVER = 0x40FFFFFF;

    static final String MAIN = "main";
    static final String BOOSTS = "boosts";
    static final String OPPONENT = "opponent";
    static final String GROUND = "ground";
    static final String TIMERS = "timers";

    static final int PLUGIN = 0;
    static final int CHECK = 1;
    static final int VALUE_ROW = 2;
    static final int COLOUR = 3;
    static final int LIST_HEADER = 4;
    static final int LIST_ENTRY = 5;
    static final int HINT_ROW = 6;
    static final int BUTTON = 7;

    /** One line of the panel: what it shows and what a click on it (or on its right-hand zones) does. */
    static final class Row {
        final int kind;
        final String label;
        boolean on;
        String value;
        int colour;
        String action;
        String rightAction;
        String secondAction;

        Row(int kind, String label) {
            this.kind = kind;
            this.label = label;
        }
    }

    private static boolean open;
    static String view = MAIN;

    private static int panelX;
    private static int panelY;
    private static int panelHeight;
    private static List<Row> rows = new ArrayList<Row>();
    private static int altButtonX = -1;
    private static int altButtonY;
    private static int altButtonWidth;

    /** Last command sent (tests). */
    static String lastSent;

    private RspsHudSettings() {
    }

    static void open() {
        open = true;
        view = MAIN;
    }

    static void close() {
        open = false;
    }

    static boolean isOpen() {
        return open;
    }

    // ---- rows ---------------------------------------------------------------------------------------------------------

    private static Row plugin(String label, boolean on, String flag, String settings) {
        Row row = new Row(PLUGIN, label);
        row.on = on;
        row.action = "set:" + flag;
        row.rightAction = "view:" + settings;
        return row;
    }

    private static Row check(String label, boolean on, String flag) {
        Row row = new Row(CHECK, label);
        row.on = on;
        row.action = "set:" + flag;
        return row;
    }

    private static Row colour(String label, int colour, String name, String value, String valueAction) {
        Row row = new Row(COLOUR, label);
        row.colour = colour;
        row.value = value;
        row.action = valueAction != null ? valueAction : "cmd:hud edit colour " + name;
        row.rightAction = "cmd:hud edit colour " + name;
        return row;
    }

    private static void list(List<Row> out, String label, List<String> names, String list) {
        Row header = new Row(LIST_HEADER, label + " (" + names.size() + ")");
        header.rightAction = "cmd:hud edit " + list;
        header.secondAction = "cmd:hud clear " + list;
        out.add(header);
        for (int i = 0; i < names.size() && i < MAX_LIST_ROWS; i++) {
            Row entry = new Row(LIST_ENTRY, names.get(i));
            entry.rightAction = "cmd:hud remove " + list + " " + names.get(i);
            out.add(entry);
        }
        if (names.size() > MAX_LIST_ROWS) {
            out.add(new Row(HINT_ROW, "... and " + (names.size() - MAX_LIST_ROWS) + " more (hud status lists all)"));
        } else if (names.isEmpty()) {
            out.add(new Row(HINT_ROW, "none - Add, then type a name (* = anything)"));
        }
    }

    /** The rows of the current view. */
    static List<Row> build() {
        List<Row> out = new ArrayList<Row>();
        if (view.equals(BOOSTS)) {
            out.add(check("Show all five combat stats", RspsHudConfig.boostsAll, "boostsall"));
            out.add(check("Show next boost drop / restore", RspsHudConfig.boostTimers, "boosttimers"));
        } else if (view.equals(OPPONENT)) {
            out.add(check("Exact monster health (cur / max)", RspsHudConfig.opponentExact, "oppexact"));
            out.add(new Row(HINT_ROW, "Players always show a percentage."));
        } else if (view.equals(GROUND)) {
            out.add(check("Show values", RspsHudConfig.value, "value"));
            out.add(check("Show despawn timers", RspsHudConfig.despawn, "despawn"));
            Row under = new Row(VALUE_ROW, "Hide items worth under");
            under.value = RspsHudConfig.hideUnder <= 0L ? "off" : RspsHud.formatValue(RspsHudConfig.hideUnder);
            under.action = "cmd:hud edit hideunder";
            out.add(under);
            out.add(colour("Default colour", RspsHudConfig.colours[0], "default", null, null));
            String[] tiers = {"Low", "Medium", "High", "Insane"};
            for (int i = 0; i < tiers.length; i++) {
                String name = tiers[i].toLowerCase();
                out.add(colour(tiers[i] + " value", RspsHudConfig.colours[i + 1], name, RspsHud.formatValue(RspsHudConfig.tiers[i]), "cmd:hud edit tier " + name));
            }
            out.add(colour("Highlight colour", RspsHudConfig.colours[5], "highlight", null, null));
            list(out, "Highlighted items", RspsHudConfig.highlightedNames, "highlight");
            list(out, "Hidden items", RspsHudConfig.hiddenNames, "hide");
        } else if (view.equals(TIMERS)) {
            out.add(check("Vengeance active", RspsHudConfig.timerVengeance, "tveng"));
            out.add(check("Vengeance cooldown", RspsHudConfig.timerVengeanceCooldown, "tvengcd"));
            out.add(check("Teleblock", RspsHudConfig.timerTeleblock, "tteleblock"));
            out.add(check("Frozen", RspsHudConfig.timerFrozen, "tfrozen"));
        } else {
            out.add(plugin("Boosts Information", RspsHudConfig.boosts, "boosts", BOOSTS));
            out.add(plugin("Opponent Information", RspsHudConfig.opponent, "opponent", OPPONENT));
            out.add(plugin("Ground Items", RspsHudConfig.groundItems, "grounditems", GROUND));
            out.add(plugin("Timers & Buffs", RspsHudConfig.timers, "timers", TIMERS));
            out.add(new Row(HINT_ROW, "Hold Alt and drag a panel to move it."));
            out.add(new Row(HINT_ROW, "Alt + right-click a panel: back to its place."));
            Row positions = new Row(BUTTON, "Reset all positions");
            positions.action = "resetpos";
            out.add(positions);
            Row settings = new Row(BUTTON, "Reset all settings");
            settings.action = "cmd:hud resetsettings";
            out.add(settings);
        }
        return out;
    }

    private static String title() {
        if (view.equals(BOOSTS)) {
            return "Boosts Information";
        } else if (view.equals(OPPONENT)) {
            return "Opponent Information";
        } else if (view.equals(GROUND)) {
            return "Ground Items";
        } else if (view.equals(TIMERS)) {
            return "Timers & Buffs";
        }
        return "78 Plugins";
    }

    // ---- actions ------------------------------------------------------------------------------------------------------

    /** Flips a switch here (the panel answers at once) and returns its new state. */
    private static boolean flip(String flag) {
        boolean on;
        if (flag.equals("boosts")) {
            on = RspsHudConfig.boosts = !RspsHudConfig.boosts;
        } else if (flag.equals("boostsall")) {
            on = RspsHudConfig.boostsAll = !RspsHudConfig.boostsAll;
        } else if (flag.equals("opponent")) {
            on = RspsHudConfig.opponent = !RspsHudConfig.opponent;
        } else if (flag.equals("grounditems")) {
            on = RspsHudConfig.groundItems = !RspsHudConfig.groundItems;
        } else if (flag.equals("despawn")) {
            on = RspsHudConfig.despawn = !RspsHudConfig.despawn;
        } else if (flag.equals("value")) {
            on = RspsHudConfig.value = !RspsHudConfig.value;
        } else if (flag.equals("timers")) {
            on = RspsHudConfig.timers = !RspsHudConfig.timers;
        } else if (flag.equals("boosttimers")) {
            on = RspsHudConfig.boostTimers = !RspsHudConfig.boostTimers;
        } else if (flag.equals("oppexact")) {
            on = RspsHudConfig.opponentExact = !RspsHudConfig.opponentExact;
        } else if (flag.equals("tveng")) {
            on = RspsHudConfig.timerVengeance = !RspsHudConfig.timerVengeance;
        } else if (flag.equals("tvengcd")) {
            on = RspsHudConfig.timerVengeanceCooldown = !RspsHudConfig.timerVengeanceCooldown;
        } else if (flag.equals("tteleblock")) {
            on = RspsHudConfig.timerTeleblock = !RspsHudConfig.timerTeleblock;
        } else {
            on = RspsHudConfig.timerFrozen = !RspsHudConfig.timerFrozen;
        }
        RspsHudGroundItems.markDirty();
        return on;
    }

    static void run(String action) {
        if (action == null) {
            return;
        }
        if (action.startsWith("set:")) {
            String flag = action.substring(4);
            send("hud set " + flag + " " + (flip(flag) ? "on" : "off"));
        } else if (action.startsWith("view:")) {
            view = action.substring(5);
        } else if (action.startsWith("cmd:")) {
            send(action.substring(4));
        } else if (action.equals("resetpos")) {
            RspsOverlayLayout.resetAll();
        }
    }

    private static void send(String command) {
        lastSent = command;
        try {
            RspsDev.send(command);
        } catch (RuntimeException notConnected) {
            // No game connection (reconnecting): the switch is set here; the server's login push restores its saved value.
        }
    }

    // ---- layout (pure, shared by drawing and clicks) -----------------------------------------------------------------

    private static int rowsTop() {
        return panelY + TITLE + 2;
    }

    private static int heightFor(List<Row> list) {
        return TITLE + 2 + list.size() * ROW + PAD;
    }

    /** Tests: the panel's spot and rows without a toolkit to render them. */
    static void layoutForTest(int x, int y) {
        rows = build();
        panelX = x;
        panelY = y;
        panelHeight = heightFor(rows);
    }

    /** A press without Alt: the panel's own clicks. Every press on the open panel is taken (never reaches the game). */
    static boolean click(int x, int y, int type) {
        if (!open || x < panelX || y < panelY || x >= panelX + WIDTH || y >= panelY + panelHeight) {
            return false;
        }
        if (type == MouseLog.TYPE_PRESS_LEFT) {
            leftClick(x, y);
            // The view or a value may have changed: the next click must see the rows as they are now, not at the last frame.
            rows = build();
            panelHeight = heightFor(rows);
        }
        return true;
    }

    private static void leftClick(int x, int y) {
        int right = panelX + WIDTH;
        if (y < panelY + TITLE) {
            if (x >= right - RIGHT_ZONE) {
                close();
            } else if (!view.equals(MAIN) && x < panelX + RIGHT_ZONE + 40) {
                view = MAIN;
            }
            return;
        }
        int index = (y - rowsTop()) / ROW;
        if (y < rowsTop() || index < 0 || index >= rows.size()) {
            return;
        }
        Row row = rows.get(index);
        if (row.kind == LIST_HEADER) {
            if (x >= right - ADD_ZONE) {
                run(row.rightAction);
            } else if (x >= right - ADD_ZONE - CLEAR_ZONE) {
                run(row.secondAction);
            }
        } else if (row.rightAction != null && x >= right - RIGHT_ZONE) {
            // ">" (settings), "x" (remove) or a colour swatch.
            run(row.rightAction);
        } else {
            run(row.action);
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

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
        rows = build();
        panelHeight = heightFor(rows);
        int[] at = RspsOverlayLayout.place(ID, viewX + (viewWidth - WIDTH) / 2, top + 24, WIDTH, panelHeight, viewX, viewY, viewWidth, viewHeight);
        panelX = at[0];
        panelY = at[1];
        int right = panelX + WIDTH;
        RspsHud.box(panelX, panelY, WIDTH, panelHeight);
        Toolkit.active.fillRect(panelX + 1, panelY + 1, WIDTH - 2, TITLE - 1, 0x60000000);
        if (view.equals(MAIN)) {
            font.render(title(), panelX + PAD, panelY + 15, RspsHud.SHADOW, TITLE_COLOUR);
        } else {
            font.render("<", panelX + PAD, panelY + 15, RspsHud.SHADOW, TEXT);
            font.render(title(), panelX + PAD + 12, panelY + 15, RspsHud.SHADOW, TITLE_COLOUR);
        }
        font.renderRight("x", right - PAD - 2, panelY + 15, TEXT, RspsHud.SHADOW);

        int mouseX = MouseMonitor.instance == null ? -1 : MouseMonitor.instance.getRecordedX();
        int mouseY = MouseMonitor.instance == null ? -1 : MouseMonitor.instance.getRecordedY();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = rowsTop() + i * ROW;
            int baseline = rowY + 13;
            boolean clickable = row.action != null || row.rightAction != null;
            if (clickable && mouseX >= panelX && mouseX < right && mouseY >= rowY && mouseY < rowY + ROW) {
                Toolkit.active.fillRect(panelX + 1, rowY, WIDTH - 2, ROW, HOVER);
            }
            switch (row.kind) {
                case PLUGIN: {
                    // RuneLite-style switch: a green or grey pill with the knob on the "on" or "off" side.
                    int sx = panelX + PAD;
                    int sy = rowY + 4;
                    Toolkit.active.fillRect(sx, sy, 20, 10, row.on ? ON : OFF);
                    Toolkit.active.outlineRect(sx, sy, 20, 10, BOX);
                    Toolkit.active.fillRect(row.on ? sx + 11 : sx + 1, sy + 1, 8, 8, TEXT);
                    font.render(row.label, sx + 26, baseline, RspsHud.SHADOW, row.on ? TEXT : HINT);
                    font.renderRight(">", right - PAD - 2, baseline, TITLE_COLOUR, RspsHud.SHADOW);
                    break;
                }
                case CHECK: {
                    int bx = panelX + PAD;
                    Toolkit.active.outlineRect(bx, rowY + 4, 9, 9, BOX);
                    if (row.on) {
                        Toolkit.active.fillRect(bx + 2, rowY + 6, 5, 5, ON);
                    }
                    font.render(row.label, bx + 14, baseline, RspsHud.SHADOW, TEXT);
                    break;
                }
                case VALUE_ROW:
                    font.render(row.label, panelX + PAD, baseline, RspsHud.SHADOW, TEXT);
                    font.renderRight(row.value, right - PAD, baseline, VALUE, RspsHud.SHADOW);
                    break;
                case COLOUR: {
                    font.render(row.label, panelX + PAD, baseline, RspsHud.SHADOW, TEXT);
                    int swatchX = right - PAD - 14;
                    Toolkit.active.fillRect(swatchX, rowY + 3, 14, 11, row.colour);
                    Toolkit.active.outlineRect(swatchX, rowY + 3, 14, 11, BOX);
                    if (row.value != null) {
                        font.renderRight(row.value, swatchX - 6, baseline, VALUE, RspsHud.SHADOW);
                    }
                    break;
                }
                case LIST_HEADER: {
                    font.render(row.label, panelX + PAD, baseline, RspsHud.SHADOW, TITLE_COLOUR);
                    button(font, "Add", right - ADD_ZONE + 2, rowY + 1, ADD_ZONE - 4);
                    button(font, "Clear", right - ADD_ZONE - CLEAR_ZONE + 2, rowY + 1, CLEAR_ZONE - 4);
                    break;
                }
                case LIST_ENTRY:
                    font.render(row.label, panelX + PAD + 10, baseline, RspsHud.SHADOW, TEXT);
                    font.renderRight("x", right - PAD - 2, baseline, REMOVE, RspsHud.SHADOW);
                    break;
                case HINT_ROW:
                    small.render(row.label, panelX + PAD, baseline - 1, RspsHud.SHADOW, HINT);
                    break;
                default:
                    button(font, row.label, panelX + PAD, rowY + 1, RspsHud.width(metrics, row.label) + 12);
                    break;
            }
        }
        RspsOverlayLayout.decorate(ID);
    }

    private static void button(Font font, String text, int x, int y, int width) {
        Toolkit.active.fillRect(x, y, width, ROW - 2, 0x80000000);
        Toolkit.active.outlineRect(x, y, width, ROW - 2, BOX);
        font.renderCentre(text, x + width / 2, y + 12, TEXT, RspsHud.SHADOW);
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
