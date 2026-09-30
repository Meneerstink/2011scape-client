import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Toolkit;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * RuneLite-style information overlays (owner 2026-09-29): Boosts Information, Opponent Information, Ground Items and
 * Timers & Buffs, with RuneLite's own settings in the 78 Plugins sidebar (RspsSidebar). This class is the feature
 * manager: it receives the server's state on the RSPS side channel ({@link #PREFIX} console messages, handled in
 * ServerConnectionReader next to {@code __RSPS_DEV__:}), resets everything on login/logout, sends the sidebar's changes
 * to the server on the game thread, and is called once per frame from the scene pass (Static294, right after the native
 * entity overlays) with the viewport rectangle. Each feature keeps its own state and draws itself; nothing here acts for
 * the player.
 *
 * Payloads are documented on the server in {@code gg.rsmod.game.model.hud.ClientHud}.
 */
final class RspsHud {

    static final String PREFIX = "__RSPS_HUD__:";

    /** One server tick. */
    static final long TICK_MS = 600L;

    /** RuneLite ComponentConstants.STANDARD_BACKGROUND_COLOR (70, 61, 50, 156). */
    static final int PANEL_BACKGROUND = 0x9C463D32;
    /** RuneLite BackgroundComponent strokes: RGB x 0.8 (outside) and x 1.2 (inside), alpha x 1.4 = 218. */
    static final int OUTSIDE_STROKE = 0xDA383028;
    static final int INSIDE_STROKE = 0xDA54493C;
    /** RuneLite ComponentConstants.STANDARD_WIDTH / STANDARD_BORDER. */
    static final int PANEL_WIDTH = 129;
    static final int PANEL_BORDER = 4;
    /**
     * Line height of RuneLite's regular RuneScape font at 16 pt (measured: height 16, ascent 14); RuneLite's LineComponent and
     * TitleComponent put each line's baseline at its top + this height.
     */
    static final int LINE = 16;
    static final int PANEL_GAP = 4;
    static final int SHADOW = 0xFF000000;
    /** Game-message colour of a notification (RuneLite posts its notifications to the chat as well). */
    private static final String NOTIFY_COLOUR = "<col=ef1020>";

    /** Commands from the sidebar (Swing thread), sent on the game thread by {@link #tickInput}. */
    private static final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<String>();

    private RspsHud() {
    }

    static void handle(String payload) {
        int bar = payload.indexOf('|');
        String kind = bar < 0 ? payload : payload.substring(0, bar);
        String rest = bar < 0 ? "" : payload.substring(bar + 1);
        try {
            if (kind.equals("cfg") || kind.equals("hl") || kind.equals("hide")) {
                RspsHudConfig.handle(kind, rest);
                RspsHudGroundItems.markDirty();
                RspsSidebar.refreshLater();
            } else if (kind.equals("boost")) {
                RspsHudBoosts.update(rest);
            } else if (kind.equals("opp")) {
                RspsHudOpponent.update(rest);
            } else if (kind.equals("tmr")) {
                RspsHudTimers.update(rest);
            } else if (kind.equals("gi")) {
                RspsHudGroundItems.update(rest);
            } else if (kind.equals("gdelay")) {
                RspsHudGroundItems.setDespawnTicks(Integer.parseInt(rest));
            } else if (kind.equals("loot") || kind.equals("lootr") || kind.equals("lootclear")) {
                RspsLootTracker.handle(kind, rest);
            } else if (kind.equals("open")) {
                RspsSidebar.openLater(rest.length() > 0 ? rest : null);
            } else if (kind.equals("close")) {
                RspsSidebar.closeLater();
            } else if (kind.equals("render")) {
                RspsRenderTest.handle(rest);
            }
        } catch (RuntimeException malformed) {
            // A malformed payload must never break the packet loop; the next update replaces the state anyway.
        }
    }

    /** Login, logout, reconnect: the server resends everything, so no state may survive. */
    static void reset() {
        RspsHudBoosts.reset();
        RspsHudOpponent.reset();
        RspsHudTimers.reset();
        RspsHudGroundItems.reset();
        RspsLootTracker.reset(LoginManager.username);
        commands.clear();
        RspsRenderTest.cull = true;
        RspsRenderTest.alphaDepth = false;
    }

    /** Queues a `hud ...` command for the server (any thread); it goes out on the next client cycle. */
    static void command(String command) {
        commands.add(command);
    }

    /** Last command sent (tests). */
    static String lastSent;

    /** Client input loop (game thread): sends the queued commands while in game. */
    static void tickInput() {
        if (!com.jagex.core.constants.MainLogicStep.isAtGameScreen(MainLogicManager.step)) {
            return;
        }
        for (String command = commands.poll(); command != null; command = commands.poll()) {
            lastSent = command;
            try {
                RspsDev.send(command);
            } catch (RuntimeException notConnected) {
                // Reconnecting: the server's login push restores the saved values anyway.
            }
        }
    }

    /** Tests: sends what is queued without a game connection. */
    static String drainForTest() {
        String last = null;
        for (String command = commands.poll(); command != null; command = commands.poll()) {
            last = command;
        }
        return last;
    }

    /** A RuneLite notification: here a game message in the chatbox. */
    /** Last notification (tests). */
    static String lastNotification;

    static void notify(String message) {
        lastNotification = message;
        try {
            ChatHistory.add(0, 0, "", "", "", NOTIFY_COLOUR + message);
        } catch (RuntimeException noChat) {
            // Not in game: nothing to show it in.
        }
    }

    /** Height of the mouse-over text line ("Walk here / 5 more options", b12 at optionsY + 16) plus a gap. */
    private static final int HOVER_TEXT_CLEARANCE = 22;

    /**
     * Called from the scene pass while the scene camera is still set, clipped to the viewport. The panels stack at the
     * top-left of the game view, below the mouse-over text - Opponent, Boosts, then the infobox row (Timers & Buffs and
     * the boost infoboxes); a panel the player moved (Alt + drag) keeps its own spot and leaves the stack.
     */
    static void render(int x, int y, int width, int height) {
        if (Toolkit.active == null || PlayerEntity.self == null) {
            return;
        }
        if (RspsHudConfig.enabled("grounditems")) {
            RspsHudGroundItems.render(x, y);
        }
        int top = y + PANEL_GAP;
        if (InterfaceManager.optionsComponent != null) {
            top = Math.max(top, InterfaceManager.optionsY + HOVER_TEXT_CLEARANCE);
        }
        int stackY = top;
        if (RspsHudConfig.enabled("opponent")) {
            int opponentHeight = RspsHudOpponent.height();
            stackY = panel(RspsHudOpponent.ID, RspsHudOpponent.width(), opponentHeight, x, y, width, height, stackY);
        }
        RspsInfoBoxes.clear();
        if (RspsHudConfig.enabled("boosts")) {
            RspsHudBoosts.checkThreshold();
            stackY = panel(RspsHudBoosts.ID, RspsHudBoosts.width(), RspsHudBoosts.height(), x, y, width, height, stackY);
            RspsHudBoosts.addInfoboxes();
        }
        if (RspsHudConfig.enabled("timers")) {
            RspsHudTimers.addInfoboxes();
        }
        if (RspsInfoBoxes.count() > 0) {
            panel(RspsInfoBoxes.ID, RspsInfoBoxes.width(), RspsInfoBoxes.SIZE, x, y, width, height, stackY);
        }
    }

    /** Places and draws one panel; returns the next free y of the default stack. */
    private static int panel(String id, int panelWidth, int panelHeight, int viewX, int viewY, int viewWidth, int viewHeight, int stackY) {
        if (panelHeight <= 0 || panelWidth <= 0) {
            return stackY;
        }
        boolean moved = RspsOverlayLayout.isMoved(id);
        int[] at = RspsOverlayLayout.place(id, viewX + PANEL_GAP, stackY, panelWidth, panelHeight, viewX, viewY, viewWidth, viewHeight);
        if (id.equals(RspsHudOpponent.ID)) {
            RspsHudOpponent.draw(at[0], at[1]);
        } else if (id.equals(RspsHudBoosts.ID)) {
            RspsHudBoosts.draw(at[0], at[1], panelHeight);
        } else {
            RspsInfoBoxes.draw(at[0], at[1]);
        }
        RspsOverlayLayout.decorate(id);
        return moved ? stackY : stackY + panelHeight + PANEL_GAP;
    }

    static long now() {
        return System.currentTimeMillis();
    }

    /** End time for a server countdown of {@code ticks} ticks received now; -1 when there is none. */
    static long endTime(int ticks) {
        return ticks <= 0 ? -1L : now() + ticks * TICK_MS;
    }

    /** m:ss until {@code endMs}, rounded up like a countdown (never below 0:00) - RuneLite Timer "%d:%02d". */
    static String countdown(long endMs) {
        long left = Math.max(0L, endMs - now());
        long seconds = (left + 999L) / 1000L;
        long s = seconds % 60L;
        return (seconds / 60L) + ":" + (s < 10L ? "0" : "") + s;
    }

    static Font font() {
        return Fonts.p12 != null ? Fonts.p12 : Fonts.b12;
    }

    static FontMetrics metrics() {
        return Fonts.p12Metrics != null ? Fonts.p12Metrics : Fonts.b12Metrics;
    }

    static int width(FontMetrics metrics, String text) {
        return metrics == null ? text.length() * 6 : metrics.stringWidth(text);
    }

    /** Text with RuneLite's shadow, or with a one-pixel black outline instead ("Text outline"). */
    static void text(Font font, String text, int x, int y, int colour, boolean outline) {
        if (outline) {
            font.render(text, x - 1, y, 0, SHADOW);
            font.render(text, x + 1, y, 0, SHADOW);
            font.render(text, x, y - 1, 0, SHADOW);
            font.render(text, x, y + 1, 0, SHADOW);
            font.render(text, x, y, 0, colour);
        } else {
            font.render(text, x, y, SHADOW, colour);
        }
    }

    /** RuneLite BackgroundComponent: the fill, an outside stroke on the edge and an inside stroke one pixel in. */
    static void box(int x, int y, int width, int height) {
        Toolkit.active.fillRect(x, y, width, height, PANEL_BACKGROUND);
        Toolkit.active.outlineRect(x, y, width, height, OUTSIDE_STROKE);
        Toolkit.active.outlineRect(x + 1, y + 1, width - 2, height - 2, INSIDE_STROKE);
    }

    /**
     * Compact value in the owner's format (2026-09-29 example: "2.4M", "18.4K", "4.8K"): below 1 000 the exact number,
     * then K / M / B with one decimal while it is below 100 of the unit.
     */
    static String formatValue(long value) {
        if (value < 0L) {
            value = 0L;
        }
        if (value < 1_000L) {
            return Long.toString(value);
        }
        if (value < 1_000_000L) {
            return oneDecimal(value, 1_000L) + "K";
        }
        if (value < 1_000_000_000L) {
            return oneDecimal(value, 1_000_000L) + "M";
        }
        return oneDecimal(value, 1_000_000_000L) + "B";
    }

    private static String oneDecimal(long value, long unit) {
        long tenths = value * 10L / unit;
        long whole = tenths / 10L;
        long decimal = tenths % 10L;
        return decimal == 0L || whole >= 100L ? Long.toString(whole) : whole + "." + decimal;
    }
}
