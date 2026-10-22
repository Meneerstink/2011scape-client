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
            } else if (kind.equals("cfgadopt")) {
                // A2 round 3 / N93: the account never set these Stretched Mode settings - it takes this client's (RuneLite: the
                // client's config wins; a login never switches Stretched Mode off).
                RspsHudConfig.adopt(rest);
            } else if (kind.equals("boost")) {
                RspsHudBoosts.update(rest);
            } else if (kind.equals("opp")) {
                RspsHudOpponent.update(rest);
            } else if (kind.equals("pkh") || kind.equals("pkfight")) {
                RspsPkHud.handle(kind, rest); // v5 Bruine Bonen PK HUD (server PkHudSync)
            } else if (kind.equals("botsstaff") || kind.equals("bots")) {
                RspsBotPanel.handle(kind, rest); // Staff permission and bot-panel state.
            } else if (kind.equals("tmr")) {
                RspsHudTimers.update(rest);
            } else if (kind.equals("gi")) {
                RspsHudGroundItems.update(rest);
            } else if (kind.equals("gdelay")) {
                RspsHudGroundItems.setDespawnTicks(Integer.parseInt(rest));
            } else if (kind.equals("loot") || kind.equals("lootr") || kind.equals("lootclear")) {
                RspsLootTracker.handle(kind, rest);
            } else if (kind.equals("cine")) {
                // Cinematic mode for scripted scenes (IntroGuardScene).
                RspsCinematic.handle(rest);
            } else if (kind.startsWith("qh")) {
                // Quest Helper side panel (QuestHelperPanel.kt on the server).
                RspsQuestHelper.handle(kind, rest);
            } else if (kind.equals("open")) {
                RspsSidebar.openLater(rest.length() > 0 ? rest : null);
            } else if (kind.equals("close")) {
                RspsSidebar.closeLater();
            } else if (kind.equals("render")) {
                RspsRenderTest.handle(rest);
            } else if (kind.equals("grave")) {
                // The player's own gravestone on the world map (owner 2026-10-04 031637).
                GraveWorldMapMarker.update(rest);
            } else if (kind.equals("npcicons")) {
                // World-map icons of npcs with a map element, at their live posts (owner 2026-10-09 214334).
                NpcWorldMapIcons.update(rest);
            } else if (kind.equals("dmmz") || kind.equals("dmmt")) {
                // Deadman HUD state (E16, owner 2026-10-03: "DE DMM HUD MAG NOOOIT VERDWIJNEN"): the HUD is no interface any more.
                DeadmanSkullHud.update(kind, rest);
            }
        } catch (RuntimeException malformed) {
            // A malformed payload must never break the packet loop; the next update replaces the state anyway.
        }
    }

    /** Login, logout, reconnect: the server resends everything, so no state may survive. */
    static void reset() { reset(true); }

    /** Bot permission was cleared before the login handshake; retain this connection's first staff push. */
    static void resetForLogin() { reset(false); }

    private static void reset(boolean resetBots) {
        RspsHudBoosts.reset();
        RspsHudOpponent.reset();
        RspsPkHud.reset();
        if (resetBots) RspsBotPanel.reset(); // General resets still revoke permission and pending staff commands.
        RspsHudTimers.reset();
        RspsHudGroundItems.reset();
        DeadmanSkullHud.reset();
        GraveWorldMapMarker.reset();
        NpcWorldMapIcons.reset();
        RspsLootTracker.reset(LoginManager.username);
        RspsQuestHelper.reset();
        RspsQuestHelperHints.reset();
        RspsCinematic.reset();
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
        RspsBotPanel.tickInput(); // Same typed-command helper, on the game thread only.
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
        if (Toolkit.active != null && RspsCinematic.curtain) {
            Toolkit.active.fillRect(x, y, width, height, 0xFF000000);
        }
        if (Toolkit.active == null || PlayerEntity.self == null || RspsCinematic.active) {
            return;
        }
        if (RspsHudConfig.enabled("grounditems")) {
            RspsHudGroundItems.render(x, y);
        }
        RspsQuestHelperHints.drawNpcHighlight(x, y);
        RspsScriptOverlay.drawTarget(x, y); // v4 Bruine Bonen Technologia: box + name on your target
        int top = y + PANEL_GAP;
        if (InterfaceManager.optionsComponent != null) {
            top = Math.max(top, InterfaceManager.optionsY + HOVER_TEXT_CLEARANCE);
        }
        int stackY = top;
        // v5: against a player the PK HUD already shows name + health, so Opponent Information keeps to npcs then.
        if (RspsHudConfig.enabled("opponent") && !(RspsHudOpponent.playerIndex() >= 0 && RspsPkHud.showing())) {
            int opponentHeight = RspsHudOpponent.height();
            stackY = panel(RspsHudOpponent.ID, RspsHudOpponent.width(), opponentHeight, x, y, width, height, stackY);
        }
        // v4 Bruine Bonen Technologia: status panel (target, last script, special energy).
        // v5: height() first - it builds this frame's PK HUD rows that width() measures.
        int pkHudHeight = RspsScriptOverlay.height();
        stackY = panel(RspsScriptOverlay.ID, RspsScriptOverlay.width(), pkHudHeight, x, y, width, height, stackY);
        int fightHeight = RspsPkHud.fightHeight();
        stackY = panel(RspsPkHud.FIGHT_ID, RspsPkHud.fightWidth(), fightHeight, x, y, width, height, stackY);
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
            int[] hud = DeadmanSkullHud.recentBlock();
            if (hud != null) {
                infoboxesBesideHud(hud, x, y, width, height);
            } else {
                panel(RspsInfoBoxes.ID, RspsInfoBoxes.width(), RspsInfoBoxes.height(), x, y, width, height, stackY);
            }
            RspsInfoBoxes.drawTooltip(x, y, width, height);
        }
        // Developer place mode (owner 2026-10-06): the selected decoration's values and keys.
        if (RspsDev.placing) {
            RspsDev.drawOverlay(x, y, width, height);
        }
    }

    /** N105 layout ids of the infobox row beside the Deadman HUD (new ids: an old saved mid-screen "infoboxes" spot is not used). */
    static final String INFOBOXES_BESIDE_HUD = "infoboxes.dmm";

    /**
     * N105 (owner 2026-10-17: the floating timer box "netjes naast de DMM-HUD"): while the Deadman HUD is shown, the infobox row
     * defaults to beside the HUD block, level with its icon row - on its right when that fits in the view, else on its left - so
     * it follows a dragged HUD; Alt + drag / Alt + right-click reset keep working per window mode.
     */
    private static void infoboxesBesideHud(int[] hud, int viewX, int viewY, int viewWidth, int viewHeight) {
        int boxWidth = RspsInfoBoxes.width();
        int boxHeight = RspsInfoBoxes.height();
        int right = hud[0] + hud[2] + PANEL_GAP;
        int defaultX = right + boxWidth <= viewX + viewWidth ? right : hud[0] - PANEL_GAP - boxWidth;
        String id = InterfaceManager.getWindowMode() == com.jagex.core.constants.WindowMode.FIXED ? INFOBOXES_BESIDE_HUD : INFOBOXES_BESIDE_HUD + ".resizable";
        int[] at = RspsOverlayLayout.place(id, defaultX, hud[4], boxWidth, boxHeight, viewX, viewY, viewWidth, viewHeight);
        RspsInfoBoxes.draw(at[0], at[1]);
        RspsOverlayLayout.decorate(id);
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
        } else if (id.equals(RspsScriptOverlay.ID)) {
            RspsScriptOverlay.draw(at[0], at[1]);
        } else if (id.equals(RspsPkHud.FIGHT_ID)) {
            RspsPkHud.drawFight(at[0], at[1]);
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

    /** m:ss until {@code endMs} (never below 0:00) - RuneLite Timer "%d:%02d", seconds truncated like Timer.getText. */
    static String countdown(long endMs) {
        long left = Math.max(0L, endMs - now());
        long seconds = left / 1000L;
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

    /** RuneLite TooltipOverlay.UNDER_OFFSET and TooltipComponent.OFFSET. */
    private static final int TOOLTIP_UNDER = 24;
    private static final int TOOLTIP_PADDING = 4;

    /**
     * RuneLite TooltipComponent under the cursor (TooltipOverlay, "under cursor"): the overlay background, 4 px padding, one
     * line per {@code </br>}, white text with {@code <col=rrggbb>} colour changes, kept inside the game view.
     */
    static void tooltip(String text, int mouseX, int mouseY, int viewX, int viewY, int viewWidth, int viewHeight) {
        Font font = font();
        FontMetrics metrics = metrics();
        String[] lines = text.split("</?br>");
        int lineHeight = metrics == null ? 14 : metrics.verticalSpacing;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, width(metrics, stripTags(line)));
        }
        int boxWidth = textWidth + TOOLTIP_PADDING * 2;
        int boxHeight = lines.length * lineHeight + TOOLTIP_PADDING * 2;
        int bx = Math.max(viewX, Math.min(viewX + viewWidth - boxWidth, mouseX));
        int by = Math.max(viewY, Math.min(viewY + viewHeight - boxHeight, mouseY + TOOLTIP_UNDER));
        box(bx, by, boxWidth, boxHeight);
        int descent = metrics == null ? 3 : Math.max(0, lineHeight - metrics.paddingTop);
        for (int i = 0; i < lines.length; i++) {
            int lx = bx + TOOLTIP_PADDING;
            int ly = by + TOOLTIP_PADDING + (i + 1) * lineHeight - descent;
            int colour = 0xFFFFFFFF;
            String line = lines[i];
            int at = 0;
            while (at < line.length()) {
                int tag = line.indexOf('<', at);
                String part = tag < 0 ? line.substring(at) : line.substring(at, tag);
                if (part.length() > 0) {
                    text(font, part, lx, ly, colour, false);
                    lx += width(metrics, part);
                }
                if (tag < 0) {
                    break;
                }
                int end = line.indexOf('>', tag);
                if (end < 0) {
                    break;
                }
                String name = line.substring(tag + 1, end);
                if (name.startsWith("col=")) {
                    try {
                        colour = 0xFF000000 | Integer.parseInt(name.substring(4), 16);
                    } catch (NumberFormatException ignored) {
                        // a broken tag keeps the colour
                    }
                } else if (name.equals("/col")) {
                    colour = 0xFFFFFFFF;
                }
                at = end + 1;
            }
        }
    }

    private static String stripTags(String line) {
        return line.replaceAll("<[^>]*>", "");
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
