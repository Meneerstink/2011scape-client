import com.jagex.graphics.Fonts;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;

/**
 * RSPS Deadman skull-timer HUD (owner 2026-09-17, reference screenshot "Juiste skulltimer hud.png":
 * a small dark panel with the skull icon and the remaining time next to it).
 *
 * The server drives interface 381 exactly as before: it shows layer 381:3 while the player is
 * PK-skulled and writes the remaining time (5:00, 4:30, ...) into text component 381:5. The 2011
 * cache's own 381:3 layout (a plain white skull graphic above the text) is never drawn; instead
 * {@link InterfaceManager} calls {@link #draw} for that layer and this class renders the panel
 * directly under the zone panel 381:0 ("Guarded" / "Dangerous" / "Level: N"), so it sits in the
 * same spot in fixed, resizable and fullscreen mode.
 *
 * The skull is the local player's own head-icon sprite: the cache frame for ids 0-7 (0 white,
 * 1 red PK skull) and the tinted frame for the server's risk-tier ids 8-12, so the colour on the
 * HUD always matches the colour above the player's head.
 */
final class DeadmanSkullHud {

    static final int INTERFACE_ID = 381;
    static final int ZONE_LAYER = 0;
    static final int TIMER_LAYER = 3;
    static final int TIMER_TEXT = 5;

    private static final int PADDING = 4;
    private static final int GAP = 4;
    private static final int BACKGROUND = 0xCC0B1F0B;
    private static final int BORDER = 0xFF2E5A2E;
    private static final int TEXT_COLOUR = 0xFFF5E642;
    private static final int TEXT_SHADOW = 0xFF000000;

    static boolean isTimerLayer(Component component) {
        return component != null && component.slot == ((INTERFACE_ID << 16) | TIMER_LAYER);
    }

    /**
     * @param baseX screen x of interface 381's own origin (offsetX of the layer minus its x)
     * @param baseY screen y of interface 381's own origin
     */
    static void draw(Component timerLayer, int baseX, int baseY) {
        Component text = InterfaceList.getComponent((INTERFACE_ID << 16) | TIMER_TEXT, -1);
        String time = text == null ? null : text.text;
        if (time == null || time.trim().length() == 0) {
            return;
        }

        Component zone = InterfaceList.getComponent((INTERFACE_ID << 16) | ZONE_LAYER, -1);
        int x;
        int y;
        if (zone != null) {
            x = baseX + zone.x;
            y = baseY + zone.y + zone.height + 3;
        } else {
            x = baseX + timerLayer.x;
            y = baseY + timerLayer.y;
        }

        Sprite skull = null;
        int tint = 0;
        PlayerEntity self = PlayerEntity.self;
        Sprite[] icons = Sprites.headiconsPk;
        if (icons != null && icons.length > 0) {
            int icon = self == null ? -1 : self.pkIcon;
            if (icon >= 0 && icon < 8 && icons.length > icon) {
                skull = icons[icon];
            } else if (icon >= 8) {
                skull = icons[0];
                tint = OverlayManager.deadmanSkullTint(icon);
            } else if (icons.length > 1) {
                // The appearance update carrying the skull has not arrived yet: the PK skull is red.
                skull = icons[1];
            } else {
                skull = icons[0];
            }
        }

        int iconWidth = skull == null ? 0 : skull.getWidth();
        int iconHeight = skull == null ? 0 : skull.getHeight();
        int textWidth = Fonts.p12Metrics == null ? time.length() * 7 : Fonts.p12Metrics.stringWidth(time);
        int textHeight = 12;
        int width = PADDING + iconWidth + (iconWidth > 0 ? GAP : 0) + textWidth + PADDING;
        int height = Math.max(iconHeight, textHeight) + 2 * 3;

        Toolkit toolkit = Toolkit.active;
        toolkit.aa(x, y, width, height, BACKGROUND, 1);
        toolkit.outlineRect(x, y, width, height, BORDER, 0);

        int cursor = x + PADDING;
        if (skull != null) {
            int iconY = y + (height - iconHeight) / 2;
            if (tint == 0) {
                skull.render(cursor, iconY);
            } else {
                skull.render(cursor, iconY, 0, 0xFF000000 | tint, 1);
            }
            cursor += iconWidth + GAP;
        }
        if (Fonts.p12 != null) {
            // renderCentre draws the baseline at y; centre the 12px font inside the panel.
            Fonts.p12.renderCentre(time, cursor + textWidth / 2, y + (height + textHeight) / 2 - 1, TEXT_COLOUR, TEXT_SHADOW);
        }
    }

    private DeadmanSkullHud() {
        /* empty */
    }
}
