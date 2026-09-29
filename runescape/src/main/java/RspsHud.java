import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Toolkit;

/**
 * RuneLite-style information overlays (owner 2026-09-29): Boosts Information, Opponent Information, Ground Items and
 * Timers & Buffs. This class is the feature manager: it receives the server's state on the RSPS side channel
 * ({@link #PREFIX} console messages, handled in ServerConnectionReader next to {@code __RSPS_DEV__:}), resets everything
 * on login/logout, and is called once per frame from the scene pass (Static294, right after the native entity overlays)
 * with the viewport rectangle. Each feature keeps its own state and draws itself; nothing here acts for the player.
 *
 * Payloads are documented on the server in {@code gg.rsmod.game.model.hud.ClientHud}.
 */
final class RspsHud {

    static final String PREFIX = "__RSPS_HUD__:";

    /** One server tick. */
    static final long TICK_MS = 600L;

    /** RuneLite's standard overlay background (ComponentConstants.STANDARD_BACKGROUND_COLOR, 70/61/50 at alpha 156). */
    static final int PANEL_BACKGROUND = 0x9C463D32;
    static final int PANEL_BORDER = 0xC8382F26;
    static final int PANEL_WIDTH = 129;
    static final int PANEL_GAP = 4;
    static final int SHADOW = 0xFF000000;

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
            } else if (kind.equals("boost")) {
                RspsHudBoosts.update(rest);
            } else if (kind.equals("opp")) {
                RspsHudOpponent.update(rest);
            } else if (kind.equals("tmr")) {
                RspsHudTimers.update(rest);
            } else if (kind.equals("gi")) {
                RspsHudGroundItems.update(rest);
            } else if (kind.equals("open")) {
                RspsHudSettings.open();
            } else if (kind.equals("close")) {
                RspsHudSettings.close();
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
        RspsHudSettings.close();
    }

    /** Height of the mouse-over text line ("Walk here / 5 more options", b12 at optionsY + 16) plus a gap. */
    private static final int HOVER_TEXT_CLEARANCE = 22;

    /**
     * Called from the scene pass while the scene camera is still set, clipped to the viewport. The panels stack at the
     * top-left of the game view, below the mouse-over text; a panel the player moved (Alt + drag) keeps its own spot and
     * leaves the stack.
     */
    static void render(int x, int y, int width, int height) {
        if (Toolkit.active == null || PlayerEntity.self == null) {
            return;
        }
        if (RspsHudConfig.groundItems) {
            RspsHudGroundItems.render(x, y);
        }
        int top = y + PANEL_GAP;
        if (InterfaceManager.optionsComponent != null) {
            top = Math.max(top, InterfaceManager.optionsY + HOVER_TEXT_CLEARANCE);
        }
        int stackY = top;
        if (RspsHudConfig.opponent) {
            stackY = panel(RspsHudOpponent.ID, RspsHudOpponent.height(), x, y, width, height, stackY);
        }
        if (RspsHudConfig.boosts) {
            stackY = panel(RspsHudBoosts.ID, RspsHudBoosts.height(), x, y, width, height, stackY);
        }
        if (RspsHudConfig.timers) {
            panel(RspsHudTimers.ID, RspsHudTimers.height(), x, y, width, height, stackY);
        }
        RspsHudSettings.render(x, y, width, height, top);
    }

    /** Places and draws one panel; returns the next free y of the default stack. */
    private static int panel(String id, int panelHeight, int viewX, int viewY, int viewWidth, int viewHeight, int stackY) {
        if (panelHeight <= 0) {
            return stackY;
        }
        boolean moved = RspsOverlayLayout.isMoved(id);
        int[] at = RspsOverlayLayout.place(id, viewX + PANEL_GAP, stackY, PANEL_WIDTH, panelHeight, viewX, viewY, viewWidth, viewHeight);
        if (id.equals(RspsHudOpponent.ID)) {
            RspsHudOpponent.draw(at[0], at[1]);
        } else if (id.equals(RspsHudBoosts.ID)) {
            RspsHudBoosts.draw(at[0], at[1], panelHeight);
        } else {
            RspsHudTimers.draw(at[0], at[1], panelHeight);
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

    /** m:ss until {@code endMs}, rounded up like a countdown (never below 0:00). */
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

    /** RuneLite-style panel box: translucent brown fill and a thin dark outline. */
    static void box(int x, int y, int width, int height) {
        Toolkit.active.fillRect(x, y, width, height, PANEL_BACKGROUND);
        Toolkit.active.outlineRect(x, y, width, height, PANEL_BORDER);
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
