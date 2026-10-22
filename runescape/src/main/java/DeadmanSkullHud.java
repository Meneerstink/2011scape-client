import com.jagex.game.runetek6.client.GameShell;
import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.core.util.TimeUtils;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;
import com.jagex.game.runetek6.config.objtype.ObjNumMode;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;

import java.awt.Rectangle;

/**
 * RSPS Deadman HUD (owner "deadmanmode vervijning", 2026-09-17: exactly the reference screenshots;
 * live retest: "put the hud above the chatbox in a fixed position"). Layout, top to bottom, one
 * column centred above the chatbox:
 *
 * <pre>
 *        [skull and crossbones] [!]      Wilderness / Deadman: the reference skull sprite;
 *                                        Guarded: the same skull under the red "no" circle.
 *               Deadman                  "Level: N" in the Wilderness, "Guarded" in a city.
 *                81-95                   The +/-14 combat bracket, everywhere.
 *           [skull] 5:00                 Only while PK-skulled: the player's own head-icon
 *                                        skull (same sprite, same colour) and the time left.
 * </pre>
 *
 * Anchor: the chatbox pane's own screen rectangle (gameframe child 548:192 in fixed mode, 746:73
 * in resizable/fullscreen - the slot interface 752 is mounted in), so the HUD always sits directly
 * above the chatbox, left-aligned with it, in every display mode. Falls back to interface 381's
 * own position when that rectangle is not available yet. That is the resizable / fullscreen default; in fixed mode the
 * default is the owner's spot 427,249 (block top-left, owner A30 2026-10-11). Each mode keeps its own Alt-dragged position.
 *
 * The server writes {@code "<label>|<lo>-<hi>"} to text component 381:2 and {@code m:ss} (or "")
 * to 381:5. Nothing is computed here. Text is the bold 12px game font in the reference orange
 * with a black shadow; every sprite is a real toolkit sprite, so the HUD looks the same in the
 * software, OpenGL and DirectX renderers.
 */
final class DeadmanSkullHud {

    static final int INTERFACE_ID = 381;
    static final int ZONE_LAYER = 0;
    static final int ZONE_TEXT = 2;
    static final int TIMER_LAYER = 3;
    static final int TIMER_TEXT = 5;

    private static final int FIXED_ROOT = 548;
    private static final int FIXED_CHAT_CHILD = 192;
    private static final int RESIZABLE_ROOT = 746;
    private static final int RESIZABLE_CHAT_CHILD = 73;

    /** Reference screenshot text colour (RuneScape interface orange). */
    private static final int TEXT_ORANGE = 0xFFFF981F;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GREEN = 0xFF00FF00;
    private static final int TEXT_GREY = 0xFFC8C0B4;

    /** Deadman emblem items 23864..23869 (server DeadmanEmblem.FIRST_EMBLEM). */
    private static final int FIRST_EMBLEM = 23864;
    /** Emblem row: the item icon drawn at 2/3 size (36 x 32 -> 24 x 21), the tier beside it, value and next tier below. */
    private static final int EMBLEM_ICON_W = 24;
    private static final int EMBLEM_ICON_H = 21;
    private static final int SMALL_FONT_HEIGHT = 11;
    /** Tier colours: dark iron, then deepening crimson, then aged gold. */
    private static final int[] TIER_COLOURS = {0xFFB4ACA0, 0xFFD2323C, 0xFFE8303A, 0xFFFF3C3C, 0xFFFFB84A, 0xFFFFD86A};
    /** Upgrade notification: the tier text flashes white for this long after the tier rises. */
    private static final long FLASH_MS = 3000L;
    private static int lastTier;
    private static long flashUntil;
    private static final int TEXT_SHADOW = 0xFF000000;

    private static final int ICON_GAP = 3;
    /**
     * RspsOverlayLayout ids, one per display mode (owner A30 2026-10-11: "per-mode storage"): fixed mode keeps the original
     * key, so the owner's approved deadman=427,249 stays his fixed position; resizable and fullscreen have their own.
     */
    // N195: new ids for the OSRS default spot - an old saved spot (A30, above the chatbox / mid-screen) would keep the HUD there.
    static final String LAYOUT_ID = "deadman.osrs";
    static final String LAYOUT_ID_RESIZABLE = "deadman.osrs.resizable";

    /** OSRS interface 90 component 44: the PvP block sits this many pixels above the bottom of the game area. */
    static final int OSRS_BOTTOM_GAP = 40;
    /** Room kept between the block and the side panel / chat. */
    static final int SIDE_MARGIN = 4;
    /** Resizable 746: the side-panel tab slots (bug reports: 746:90..108 hold the tab interfaces). */
    private static final int RESIZABLE_TAB_FIRST = 90;
    private static final int RESIZABLE_TAB_LAST = 108;
    /**
     * Owner A30 2026-10-11: the fixed-mode default for every player is the owner's own spot - the HUD block's top-left corner
     * at 427,249 of the 765x503 game (reference\owner-overlays-approved-2026-10-11.properties). Alt + right-click puts it back here.
     */
    static final int FIXED_DEFAULT_X = 427;
    static final int FIXED_DEFAULT_Y = 249;
    private static final int MIN_BLOCK_WIDTH = 64;
    // Native OSRS metrics, measured from the owner references (captured at 1.2x vertical scale):
    // skull top -> label baseline 40px, label -> bracket 12px, bracket baseline -> swords 9px,
    // exclamation mark flush with the skull's right edge and 3px lower (its OSRS canvas offset).
    private static final int LABEL_BASELINE = 40;
    private static final int LINE_HEIGHT = 12;
    private static final int MULTI_GAP = 9;
    private static final int EXCLAMATION_DROP = 3;
    private static final int FONT_HEIGHT = 12;
    private static final int TIMER_GAP = 3;
    /** Distance between the HUD's bottom edge and the chatbox top. */
    static final int CHAT_MARGIN = 6;
    /** Horizontal centre of the column, measured from the chatbox's left edge. */
    private static final int CHAT_CENTRE_OFFSET = 32;

    /** The overlay id of the HUD in fixed mode, or in resizable / fullscreen. */
    static String layoutId(boolean fixedMode) {
        return fixedMode ? LAYOUT_ID : LAYOUT_ID_RESIZABLE;
    }

    static boolean isHudLayer(Component component) {
        return isZoneLayer(component) || isTimerLayer(component);
    }

    static boolean isZoneLayer(Component component) {
        return component != null && component.slot == ((INTERFACE_ID << 16) | ZONE_LAYER);
    }

    static boolean isTimerLayer(Component component) {
        return component != null && component.slot == ((INTERFACE_ID << 16) | TIMER_LAYER);
    }

    // ---- E16 (owner 2026-10-03, all caps: "DE DMM HUD MOET ALTIJD IN BEELD ZIJN ... MAG NOOOIT VERDWIJNEN") ------------------
    // The HUD used to exist only while interface 381 sat in the shared PvP-overlay slot (548:19 / 746:10); Barrows, God Wars,
    // Fight Caves, Clan Wars, the Duel Arena and Pest Control take that slot, so the HUD was gone there (research
    // agent-handoffs/research-2026-10-03-dmm-hud.md). Now the server sends the two strings on the RSPS side channel
    // (`dmmz|<label|lo-hi[|M]>`, `dmmt|<skull|grace|emblem|grave>`, on change and every 5 ticks) and the HUD is drawn after the
    // whole interface tree on the full canvas (drawOverlay), over every top level, modal, bank, GE and the world map.

    private static volatile String zoneState = "";
    private static volatile String timerState = "";
    private static boolean loggedNotDrawn;
    private static String lastLoggedPlace = "";
    /** The chatbox top the last frame was placed against (-1 = none known), for the "dmmhud drawn at" trace. */
    private static int lastChatTop = -1;

    /** Side-channel payload (RspsHud): {@code dmmz} = the zone text, {@code dmmt} = the timer text. */
    static void update(String kind, String value) {
        String text = value == null ? "" : value.trim();
        boolean changed;
        if (kind.equals("dmmz")) {
            changed = !text.equals(zoneState);
            boolean wasPvp = inPvpArea();
            zoneState = text;
            if (wasPvp != inPvpArea()) {
                // N72: spell icon resizing works outside PvP areas only - lay the open book out again at the border.
                RspsSpellFilters.relayout();
            }
        } else {
            changed = !text.equals(timerState);
            timerState = text;
        }
        if (changed) {
            AvTrace.log("dmmhud state zone=" + zoneState + " timer=" + timerState);
            loggedNotDrawn = false;
        }
    }

    /** Login, logout, reconnect: nothing survives; the server pushes the state right after login. */
    static void reset() {
        zoneState = "";
        timerState = "";
        loggedNotDrawn = false;
    }

    private static String componentText(int slot) {
        return slot == ZONE_TEXT ? zoneState : timerState;
    }

    /** Canvas origin of the frame being drawn (InterfaceManager.method3833 sets it before the scene pass). */
    static int frameOriginX;
    static int frameOriginY;

    /**
     * N105 (owner 2026-10-17): called from the scene pass (Static294.method4339, after RspsHud), so every interface drawn after
     * the scene - bank, GE, drop viewer, info window, world map top level - covers the HUD (RuneLite OverlayLayer.UNDER_WIDGETS;
     * Darkan sends the wilderness skull HUD to the overlay sub, not the modal). The scene's clip is put back afterwards.
     */
    static void drawInScene(int sceneX, int sceneY, int sceneWidth, int sceneHeight) {
        viewX = sceneX;
        viewY = sceneY;
        viewWidth = sceneWidth;
        viewHeight = sceneHeight;
        try {
            drawOverlay(frameOriginX, frameOriginY);
        } finally {
            viewWidth = 0;
            if (Toolkit.active != null) {
                Toolkit.active.KA(sceneX, sceneY, sceneX + sceneWidth, sceneY + sceneHeight);
            }
        }
    }

    /** N105: where the HUD block was last drawn - x, y, width, height and the y of its icon row. */
    private static int[] lastBlock;
    private static long lastBlockTime;

    /** The HUD block of the last frame (null when the HUD was not drawn in the last second). */
    static int[] recentBlock() {
        return lastBlock != null && System.currentTimeMillis() - lastBlockTime < 1000L ? lastBlock : null;
    }

    /** The game view of the scene pass being drawn (width 0: none). */
    private static int viewX;
    private static int viewY;
    private static int viewWidth;
    private static int viewHeight;

    /**
     * N105: under the interfaces, a block placed (dragged) outside the game view would vanish behind the side panel or chat, so
     * it is pulled inside the view - never clipped away (the old C3 failure). Inside the view nothing moves (A30 spots stay).
     */
    static void clampIntoView(int[] at, int width, int height) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        at[0] = Math.max(viewX, Math.min(at[0], viewX + viewWidth - width));
        at[1] = Math.max(viewY, Math.min(at[1], viewY + viewHeight - height));
    }

    /** Draws the HUD with the whole canvas as clip ([drawInScene] is the only caller). */
    static void drawOverlay(int originX, int originY) {
        if (!com.jagex.core.constants.MainLogicStep.isAtGameScreen(MainLogicManager.step)) {
            return;
        }
        if (zoneState.length() == 0) {
            if (!loggedNotDrawn) {
                loggedNotDrawn = true;
                AvTrace.log("dmmhud NOT drawn: no state yet (top=" + InterfaceManager.topLevelInterface + ")");
            }
            return;
        }
        try {
            Toolkit.active.KA(originX, originY, originX + GameShell.canvasWid, originY + GameShell.canvasHei);
            int[] box = drawAll(null, originX, originY);
            if (box != null) {
                // Software renderer: only dirty rectangles reach the screen. Flip the HUD's area now and redraw it next frame.
                InterfaceManager.flipDirtyRectWithin(box[0], box[1], box[2], box[3]);
                for (int i = 0; i < InterfaceManager.rectangleCount; i++) {
                    Rectangle r = InterfaceManager.rectangles[i];
                    if (r != null && r.x + r.width > box[0] && r.x < box[0] + box[2] && r.y + r.height > box[1] && r.y < box[1] + box[3]) {
                        InterfaceManager.dirtyRectangles[i] = true;
                    }
                }
                String place = box[0] + "," + box[1] + " " + box[2] + "x" + box[3] + " mode=" + InterfaceManager.getWindowMode() + " top=" + InterfaceManager.topLevelInterface + " chat=" + lastChatTop;
                if (!place.equals(lastLoggedPlace)) {
                    lastLoggedPlace = place;
                    AvTrace.log("dmmhud drawn at " + place);
                }
            }
        } catch (RuntimeException e) {
            // Never let the HUD break the frame; name it once.
            if (!loggedNotDrawn) {
                loggedNotDrawn = true;
                System.out.println("[dmmhud] PROBLEM NOT drawn: " + e);
            }
        }
    }

    /** True in a PvP area: the HUD names a Deadman / Wilderness zone (anything but "Guarded"); false before the first state. */
    static boolean inPvpArea() {
        String l = label(zoneState);
        return l.length() > 0 && !l.equalsIgnoreCase("Guarded");
    }

    private static String label(String zoneText) {
        int separator = zoneText.indexOf('|');
        return separator < 0 ? zoneText : zoneText.substring(0, separator).trim();
    }

    private static String bracket(String zoneText) {
        int separator = zoneText.indexOf('|');
        if (separator < 0) {
            return "";
        }
        int end = zoneText.indexOf('|', separator + 1);
        return (end < 0 ? zoneText.substring(separator + 1) : zoneText.substring(separator + 1, end)).trim();
    }

    /** Third field "M": the player stands in a multicombat area. */
    private static boolean multi(String zoneText) {
        int separator = zoneText.indexOf('|');
        int second = separator < 0 ? -1 : zoneText.indexOf('|', separator + 1);
        return second >= 0 && zoneText.substring(second + 1).trim().equals("M");
    }

    private static int lastChatX = Integer.MIN_VALUE;
    private static int lastChatY;

    /**
     * The chatbox pane's screen rectangle. Whichever gameframe child (fixed or resizable) was laid
     * out most recently wins; the last good position is remembered so a frame in which neither has
     * been laid out yet never moves the HUD (that fallback was the "flicker on the right").
     */
    private static Rectangle chatboxBounds() {
        Component fixed = InterfaceList.getComponent((FIXED_ROOT << 16) | FIXED_CHAT_CHILD, -1);
        Component resizable = InterfaceList.getComponent((RESIZABLE_ROOT << 16) | RESIZABLE_CHAT_CHILD, -1);
        Component newest = null;
        if (fixed != null && fixed.rectangle >= 0) {
            newest = fixed;
        }
        if (resizable != null && resizable.rectangle >= 0 && (newest == null || resizable.lastUpdate > newest.lastUpdate)) {
            newest = resizable;
        }
        if (newest == null || newest.rectangle >= InterfaceManager.rectangles.length) {
            return remembered();
        }
        Rectangle rectangle = InterfaceManager.rectangles[newest.rectangle];
        if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0) {
            return remembered();
        }
        lastChatX = rectangle.x;
        lastChatY = rectangle.y;
        return rectangle;
    }

    /**
     * Resizable / fullscreen: the screen rectangle of the open side-panel tab (the 746:90..108 slot laid out most recently), or
     * null when none is laid out (intro curtain, fixed mode).
     */
    private static Rectangle sidePanelBounds() {
        Component newest = null;
        for (int child = RESIZABLE_TAB_FIRST; child <= RESIZABLE_TAB_LAST; child++) {
            Component slot = InterfaceList.getComponent((RESIZABLE_ROOT << 16) | child, -1);
            if (slot == null || slot.hidden || slot.rectangle < 0 || slot.rectangle >= InterfaceManager.rectangles.length) {
                continue;
            }
            if (newest == null || slot.lastUpdate > newest.lastUpdate) {
                newest = slot;
            }
        }
        if (newest == null) {
            return null;
        }
        Rectangle r = InterfaceManager.rectangles[newest.rectangle];
        return r == null || r.width <= 0 || r.height <= 0 ? null : r;
    }

    /** The bottom-right corner the block's default spot hangs from (OSRS: right-aligned, [OSRS_BOTTOM_GAP] above the bottom). */
    static int[] defaultCorner(boolean fixedMode, Rectangle side) {
        int right;
        int bottom;
        if (viewWidth > 0 && viewHeight > 0) {
            right = viewX + viewWidth;
            bottom = viewY + viewHeight - OSRS_BOTTOM_GAP;
        } else {
            right = GameShell.canvasWid;
            bottom = GameShell.canvasHei - OSRS_BOTTOM_GAP;
        }
        if (fixedMode) {
            return new int[] {right, bottom};
        }
        if (side != null) {
            // Left of the side panel, its bottom at most level with the panel's bottom (the tab stones below it reach further left).
            right = Math.min(right, side.x - SIDE_MARGIN);
            bottom = Math.min(bottom, side.y + side.height);
        }
        return new int[] {right, bottom};
    }

    /** N178: a block (default or Alt-dragged) never lies under the side panel or the chatbox - it is moved out, left / up. */
    static void keepClear(int[] at, int width, int height, Rectangle side, Rectangle chat) {
        if (side != null && overlaps(at[0], at[1], width, height, side)) {
            at[0] = Math.max(0, side.x - SIDE_MARGIN - width);
        }
        if (chat != null && chat.width > 1 && overlaps(at[0], at[1], width, height, chat)) {
            at[1] = Math.max(0, chat.y - CHAT_MARGIN - height);
        }
    }

    private static boolean overlaps(int x, int y, int width, int height, Rectangle r) {
        return x < r.x + r.width && x + width > r.x && y < r.y + r.height && y + height > r.y;
    }

    private static Rectangle remembered() {
        if (lastChatX == Integer.MIN_VALUE) {
            return null;
        }
        return new Rectangle(lastChatX, lastChatY, 1, 1);
    }

    private static Font font() {
        return Fonts.b12 != null ? Fonts.b12 : Fonts.p12;
    }

    private static int textWidth(String text) {
        FontMetrics metrics = Fonts.b12Metrics != null ? Fonts.b12Metrics : Fonts.p12Metrics;
        return metrics == null ? text.length() * 7 : metrics.stringWidth(text);
    }

    private static Sprite stateIcon(boolean guarded) {
        Sprite icon = guarded ? DeadmanIcons.guarded() : DeadmanIcons.skull();
        return icon;
    }

    private static Sprite ownSkull() {
        PlayerEntity self = PlayerEntity.self;
        int pkIcon = self == null || self.pkIcon < 0 ? DeadmanIcons.LAST_TIER_ID : self.pkIcon;
        return Sprites.deadmanSkull(pkIcon, 0, self != null && self.pkSkulled);
    }

    /** Draws the whole HUD; returns its screen box {x, y, w, h}, or null when nothing could be drawn. */
    private static int[] drawAll(Component zone, int baseX, int baseY) {
        String zoneText = componentText(ZONE_TEXT);
        if (zoneText.length() == 0) {
            return null;
        }
        Font font = font();
        if (font == null) {
            return null;
        }
        String label = label(zoneText);
        String bracket = bracket(zoneText);
        // 381:5 = "<skull m:ss>|<kill-grace m:ss>" (either part may be empty; owner 2026-09-18 #16).
        String timerField = componentText(TIMER_TEXT);
        int timerSeparator = timerField.indexOf('|');
        String time = (timerSeparator < 0 ? timerField : timerField.substring(0, timerSeparator)).trim();
        // Optional third field (owner 2026-09-25): the carried Deadman emblem, "tier:points:nextPoints".
        int emblemSeparator = timerSeparator < 0 ? -1 : timerField.indexOf('|', timerSeparator + 1);
        String grace = timerSeparator < 0 ? "" : (emblemSeparator < 0 ? timerField.substring(timerSeparator + 1) : timerField.substring(timerSeparator + 1, emblemSeparator)).trim();
        // Optional fourth field (2026-09-26): the gravestone timer "m:ss" (OSRS gravestone_hud_write).
        int graveSeparator = emblemSeparator < 0 ? -1 : timerField.indexOf('|', emblemSeparator + 1);
        String graveTime = graveSeparator < 0 ? "" : timerField.substring(graveSeparator + 1).trim();
        String[] emblem = emblemSeparator < 0 ? null : (graveSeparator < 0 ? timerField.substring(emblemSeparator + 1) : timerField.substring(emblemSeparator + 1, graveSeparator)).trim().split(":");
        int tier = 0;
        if (emblem != null && emblem.length == 3) {
            try {
                tier = Integer.parseInt(emblem[0]);
            } catch (NumberFormatException ignored) {
                tier = 0;
            }
        }
        if (tier < 1 || tier > TIER_COLOURS.length) {
            tier = 0;
        }
        if (tier > lastTier && lastTier > 0) {
            flashUntil = System.currentTimeMillis() + FLASH_MS;
        }
        lastTier = tier;
        boolean guarded = label.equalsIgnoreCase("Guarded");

        Sprite icon = stateIcon(guarded);
        // Reference screenshots: the exclamation mark belongs to the dangerous states only.
        Sprite exclamation = guarded ? null : DeadmanIcons.exclamation();
        // Owner 2026-09-18: the multi / single combat icon sits ON TOP of the HUD block, only in a
        // dangerous zone, and never changes the HUD's own layout ("this is perfect").
        Sprite swords = guarded ? null : (multi(zoneText) ? DeadmanIcons.multi() : DeadmanIcons.single());
        Sprite skull = time.length() == 0 ? null : ownSkull();

        int iconWidth = icon == null ? 0 : icon.getWidth();
        int iconHeight = icon == null ? 0 : icon.getHeight();
        int skullWidth = skull == null ? 0 : skull.getWidth();
        int skullHeight = skull == null ? 0 : skull.getHeight();

        int zoneHeight = zoneHeight(bracket.length() > 0);
        int timerHeight = time.length() == 0 ? 0 : timerRowHeight(skullHeight);
        int graceHeight = grace.length() == 0 ? 0 : graceRowHeight();
        int emblemHeight = tier == 0 ? 0 : emblemRowHeight();
        Sprite grave = graveTime.length() == 0 ? null : DeadmanIcons.grave();
        int graveIconHeight = grave == null ? 0 : grave.getHeight();
        int graveHeight = graveTime.length() == 0 ? 0 : graveRowHeight(graveIconHeight);
        int totalHeight = zoneHeight + timerHeight + graceHeight + emblemHeight + graveHeight;

        int cx;
        int y;
        Rectangle chat = chatboxBounds();
        lastChatTop = chat == null ? -1 : chat.y;
        if (chat != null) {
            cx = chat.x + CHAT_CENTRE_OFFSET;
            y = chat.y - CHAT_MARGIN - totalHeight;
        } else {
            // No chatbox laid out yet (a fullscreen top level straight after login): bottom-left of the canvas, above the chat strip.
            cx = baseX + CHAT_CENTRE_OFFSET + 8;
            y = baseY + GameShell.canvasHei - 165 - CHAT_MARGIN - totalHeight;
        }
        if (y < 2) {
            y = 2;
        }

        // Movable with Alt + drag like the RSPS information panels (owner 2026-09-29); unmoved it stays exactly above the
        // chatbox. The block always reserves the combat-icon space on top, so it does not jump when the zone changes.
        Sprite multiIcon = DeadmanIcons.multi();
        int swordsReserve = multiIcon == null ? 0 : MULTI_GAP + multiIcon.getHeight();
        int exclamationWidth = exclamation == null ? 0 : exclamation.getWidth();
        int blockWidth = Math.max(MIN_BLOCK_WIDTH, Math.max(iconWidth + 2 * exclamationWidth, Math.max(textWidth(label), textWidth(bracket))));
        if (grace.length() > 0) {
            blockWidth = Math.max(blockWidth, textWidth("Protected " + grace));
        }
        if (time.length() > 0) {
            blockWidth = Math.max(blockWidth, skullWidth + ICON_GAP + textWidth(time));
        }
        blockWidth += 8;
        boolean fixedMode = InterfaceManager.getWindowMode() == com.jagex.core.constants.WindowMode.FIXED;
        String layoutId = layoutId(fixedMode);
        // N195 (owner 2026-10-22, OSRS cache 2686 interface 90 component 44: right-aligned, 40 px above the bottom of the game
        // area): the block's default is the bottom-right of the game view in every mode - fixed: the 3D view; resizable and
        // fullscreen: left of the side panel (OSRS 161:97), never under it, the tab stones or the chat (N178).
        int blockHeight = swordsReserve + totalHeight;
        Rectangle side = fixedMode ? null : sidePanelBounds();
        int[] corner = defaultCorner(fixedMode, side);
        int defaultX = corner[0] - blockWidth;
        int defaultY = corner[1] - blockHeight;
        int[] at = placeBlock(fixedMode, defaultX, defaultY, blockWidth, blockHeight, chat,
            GameShell.canvasWid, GameShell.canvasHei);
        clampIntoView(at, blockWidth, blockHeight);
        keepClear(at, blockWidth, blockHeight, side, chat);
        lastBlock = new int[] {at[0], at[1], blockWidth, swordsReserve + totalHeight, at[1] + swordsReserve};
        lastBlockTime = System.currentTimeMillis();
        cx = at[0] + blockWidth / 2;
        y = at[1] + swordsReserve;

        // Icon row: the skull itself is centred exactly on the text column (owner: "center
        // perfectly under the text"); the exclamation mark hangs off its right edge like the
        // reference screenshot.
        int iconX = cx - iconWidth / 2;
        if (icon != null) {
            icon.render(iconX, y);
        }
        if (exclamation != null) {
            exclamation.render(iconX + iconWidth, y + EXCLAMATION_DROP);
        }

        // Label + bracket.
        int baseline = y + LABEL_BASELINE;
        font.renderCentre(label, cx, baseline, TEXT_ORANGE, TEXT_SHADOW);
        if (bracket.length() > 0) {
            font.renderCentre(bracket, cx, baseline + LINE_HEIGHT, TEXT_ORANGE, TEXT_SHADOW);
        }

        // Multi (crossed swords) or single (crossed swords under the "no" circle) combat icon,
        // centred above the zone icon; the HUD block itself is not moved by it.
        if (swords != null) {
            int swordsY = y - MULTI_GAP - swords.getHeight();
            if (swordsY >= 0) {
                swords.render(cx - swords.getWidth() / 2, swordsY);
            }
        }

        // Timer row.
        if (time.length() > 0) {
            int rowY = y + zoneHeight + TIMER_GAP;
            int rowHeight = Math.max(skullHeight, FONT_HEIGHT);
            int timeWidth = textWidth(time);
            int timerWidth = skullWidth + (skullWidth > 0 ? ICON_GAP : 0) + timeWidth;
            int timerX = cx - timerWidth / 2;
            if (skull != null) {
                skull.render(timerX, rowY + (rowHeight - skullHeight) / 2);
            }
            int textX = timerX + skullWidth + (skullWidth > 0 ? ICON_GAP : 0);
            font.renderCentre(time, textX + timeWidth / 2, rowY + (rowHeight + FONT_HEIGHT) / 2 - 1, TEXT_WHITE, TEXT_SHADOW);
        }

        // Kill-grace row: "Protected m:ss" while the post-kill protection runs.
        if (grace.length() > 0) {
            int graceBaseline = y + zoneHeight + timerHeight + TIMER_GAP + FONT_HEIGHT - 1;
            font.renderCentre("Protected " + grace, cx, graceBaseline, TEXT_GREEN, TEXT_SHADOW);
        }

        // Emblem row: [icon] T3, then "200 > 360" (points now, points at the next tier) or "1,150 MAX".
        if (tier > 0) {
            int rowY = y + zoneHeight + timerHeight + graceHeight + TIMER_GAP;
            int rowHeight = Math.max(EMBLEM_ICON_H, FONT_HEIGHT);
            String tierText = "T" + tier;
            int tierWidth = textWidth(tierText);
            int rowWidth = EMBLEM_ICON_W + ICON_GAP + tierWidth;
            int rowX = cx - rowWidth / 2;
            Sprite emblemSprite = emblemIcon(tier);
            if (emblemSprite != null) {
                emblemSprite.render(rowX, rowY, EMBLEM_ICON_W, EMBLEM_ICON_H);
            }
            boolean flash = System.currentTimeMillis() < flashUntil && (System.currentTimeMillis() / 250L) % 2L == 0L;
            int colour = flash ? TEXT_WHITE : TIER_COLOURS[tier - 1];
            font.renderCentre(tierText, rowX + EMBLEM_ICON_W + ICON_GAP + tierWidth / 2, rowY + (rowHeight + FONT_HEIGHT) / 2 - 1, colour, TEXT_SHADOW);
            String value = emblem[2].equals("0") ? formatPoints(emblem[1]) + " MAX" : formatPoints(emblem[1]) + " > " + formatPoints(emblem[2]);
            Font small = Fonts.p11 != null ? Fonts.p11 : font;
            small.renderCentre(value, cx, rowY + rowHeight + SMALL_FONT_HEIGHT - 1, TEXT_GREY, TEXT_SHADOW);
        }

        // Gravestone row: [grave icon] m:ss, the OSRS gravestone timer (gravestone_hud_init / gravestone_hud_write).
        if (graveTime.length() > 0) {
            int rowY = y + zoneHeight + timerHeight + graceHeight + emblemHeight + TIMER_GAP;
            int rowHeight = Math.max(graveIconHeight, FONT_HEIGHT);
            int iconW = grave == null ? 0 : grave.getWidth();
            int timeWidth = textWidth(graveTime);
            int rowWidth = iconW + (iconW > 0 ? ICON_GAP : 0) + timeWidth;
            int rowX = cx - rowWidth / 2;
            if (grave != null) {
                grave.render(rowX, rowY + (rowHeight - graveIconHeight) / 2);
            }
            int textX = rowX + iconW + (iconW > 0 ? ICON_GAP : 0);
            font.renderCentre(graveTime, textX + timeWidth / 2, rowY + (rowHeight + FONT_HEIGHT) / 2 - 1, graveColour(), TEXT_SHADOW);
        }
        RspsOverlayLayout.decorate(layoutId);
        return new int[] {at[0], at[1], blockWidth, swordsReserve + totalHeight};
    }

    // ---- block size and placement (pure; RspsHudRegressionTest deadmanHudClearOfChat) ---------------------------------

    /** Zone rows: icon + label, plus the combat bracket line when there is one. */
    static int zoneHeight(boolean bracket) {
        return LABEL_BASELINE + (bracket ? LINE_HEIGHT : 0);
    }

    /** The PK-skull timer row: [skull] m:ss. */
    static int timerRowHeight(int skullHeight) {
        return TIMER_GAP + Math.max(skullHeight, FONT_HEIGHT);
    }

    /** The post-kill protection row: "Protected m:ss". */
    static int graceRowHeight() {
        return TIMER_GAP + FONT_HEIGHT;
    }

    /** The emblem rows: [icon] T3, then "200 > 360". */
    static int emblemRowHeight() {
        return TIMER_GAP + Math.max(EMBLEM_ICON_H, FONT_HEIGHT) + SMALL_FONT_HEIGHT;
    }

    /** The gravestone row: [grave] m:ss. */
    static int graveRowHeight(int graveIconHeight) {
        return TIMER_GAP + Math.max(graveIconHeight, FONT_HEIGHT);
    }

    /**
     * Where the HUD block (top-left, {@code width x height} including the combat-icon reserve) is drawn this frame:
     * the saved / dragged / default spot of the current mode's layout id, clamped inside its area.
     *
     * A30 round 2 (Keurmeester FAIL 2026-10-15): in fixed mode the block is pinned by its top-left (427,249, owner decision
     * "anker = linkerbovenhoek") and grows down, so the skull timer, "Protected m:ss", emblem and gravestone rows ran over
     * the chatbox. Port of RuneLite OverlayRenderer.clampOverlayLocation: an overlay is kept inside its parent bounds by
     * moving it only as far as needed ({@code constrainToRange(y, parent.y, parent.height - overlayHeight)}); in fixed mode
     * the HUD's parent is the game view above the chatbox (RuneLite's fixed-mode snap area is the viewport). So a block
     * that fits stays exactly on its spot, a taller one moves up just enough to end {@link #CHAT_MARGIN} above the chatbox,
     * and the saved spot itself is never rewritten. A spot the player dragged onto the chatbox is his choice and only
     * stays inside the canvas; resizable / fullscreen keep their chatbox anchor (that block already grows upward).
     */
    static int[] placeBlock(boolean fixedMode, int defaultX, int defaultY, int width, int height, Rectangle chat, int canvasWid, int canvasHei) {
        String id = layoutId(fixedMode);
        int areaHeight = canvasHei;
        if (fixedMode && chat != null) {
            int[] wanted = RspsOverlayLayout.requested(id, defaultX, defaultY, 0, 0);
            if (wanted[1] < chat.y) {
                areaHeight = Math.max(0, Math.min(canvasHei, chat.y - CHAT_MARGIN));
            }
        }
        return RspsOverlayLayout.place(id, defaultX, defaultY, width, height, 0, 0, canvasWid, areaHeight);
    }

    /**
     * The OSRS gravestone timer colour (clientscript tob_hud_nameflasher): it pulses from red to white and back every
     * 90 client cycles (1.8 seconds).
     */
    private static int graveColour() {
        long cycle = (System.currentTimeMillis() / 20L) % 90L;
        if (cycle > 45) {
            cycle = 90 - cycle;
        }
        int level = (int) (Math.sqrt(cycle * 255.0) * 255.0 / Math.sqrt(11475.0));
        level = Math.max(0, Math.min(255, level));
        return 0xFF0000 | (level << 8) | level;
    }

    /** The emblem's own inventory icon (null until the client has rendered it once; the text still shows). */
    private static Sprite emblemIcon(int tier) {
        try {
            return ObjTypeList.instance == null ? null : ObjTypeList.instance.getCachedSprite(null, Toolkit.active, ObjNumMode.SHOWCOUNT_NEVER, FIRST_EMBLEM + tier - 1, 1, 1, 0xFF000000);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String formatPoints(String points) {
        try {
            return String.format(java.util.Locale.US, "%,d", Integer.parseInt(points));
        } catch (NumberFormatException ignored) {
            return points;
        }
    }

    private DeadmanSkullHud() {
        /* empty */
    }
}
