import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.core.util.TimeUtils;
import com.jagex.graphics.Sprite;

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
 * own position when that rectangle is not available yet.
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
    private static final int TEXT_SHADOW = 0xFF000000;

    private static final int ICON_GAP = 3;
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
    private static final int CHAT_MARGIN = 6;
    /** Horizontal centre of the column, measured from the chatbox's left edge. */
    private static final int CHAT_CENTRE_OFFSET = 32;

    static boolean isHudLayer(Component component) {
        return isZoneLayer(component) || isTimerLayer(component);
    }

    static boolean isZoneLayer(Component component) {
        return component != null && component.slot == ((INTERFACE_ID << 16) | ZONE_LAYER);
    }

    static boolean isTimerLayer(Component component) {
        return component != null && component.slot == ((INTERFACE_ID << 16) | TIMER_LAYER);
    }

    /**
     * @param layer the 381:0 or 381:3 layer being drawn
     * @param baseX screen x of interface 381's own origin
     * @param baseY screen y of interface 381's own origin
     */
    static void draw(Component layer, int baseX, int baseY) {
        // The whole HUD (zone block + timer row) is drawn from the zone layer so it can be anchored
        // as one column; the timer layer contributes nothing on its own.
        if (isZoneLayer(layer)) {
            drawAll(layer, baseX, baseY);
        }
    }

    private static String componentText(int slot) {
        Component component = InterfaceList.getComponent((INTERFACE_ID << 16) | slot, -1);
        String text = component == null ? null : component.text;
        return text == null ? "" : text.trim();
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
        return Sprites.deadmanSkull(pkIcon, 0);
    }

    private static void drawAll(Component zone, int baseX, int baseY) {
        String zoneText = componentText(ZONE_TEXT);
        if (zoneText.length() == 0) {
            return;
        }
        Font font = font();
        if (font == null) {
            return;
        }
        String label = label(zoneText);
        String bracket = bracket(zoneText);
        // 381:5 = "<skull m:ss>|<kill-grace m:ss>" (either part may be empty; owner 2026-09-18 #16).
        String timerField = componentText(TIMER_TEXT);
        int timerSeparator = timerField.indexOf('|');
        String time = (timerSeparator < 0 ? timerField : timerField.substring(0, timerSeparator)).trim();
        String grace = timerSeparator < 0 ? "" : timerField.substring(timerSeparator + 1).trim();
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

        int zoneHeight = LABEL_BASELINE + (bracket.length() > 0 ? LINE_HEIGHT : 0);
        int timerHeight = time.length() == 0 ? 0 : TIMER_GAP + Math.max(skullHeight, FONT_HEIGHT);
        int graceHeight = grace.length() == 0 ? 0 : TIMER_GAP + FONT_HEIGHT;
        int totalHeight = zoneHeight + timerHeight + graceHeight;

        int cx;
        int y;
        Rectangle chat = chatboxBounds();
        if (chat != null) {
            cx = chat.x + CHAT_CENTRE_OFFSET;
            y = chat.y - CHAT_MARGIN - totalHeight;
        } else {
            return; // the chatbox has not been laid out yet: draw nothing rather than somewhere else
        }
        if (y < 2) {
            y = 2;
        }

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
    }

    private DeadmanSkullHud() {
        /* empty */
    }
}
