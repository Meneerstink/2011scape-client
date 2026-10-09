import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Sprite;
import rs2.client.event.mouse.MouseMonitor;

import java.util.ArrayList;
import java.util.List;

/**
 * RuneLite-style infoboxes (InfoBoxComponent / InfoBoxOverlay): 35 px squares one pixel apart in the standard overlay
 * background, the icon centred, the text centred along the bottom (3 px up) in the regular font. Timers & Buffs always show as infoboxes; Boosts Information does when
 * "Display infoboxes" is on. All boxes of a frame form one overlay that moves as one (Alt + drag), like RuneLite's infobox
 * overlay: at most {@link #WRAP} boxes per row (InfoBoxOverlay DEFAULT_WRAP_COUNT), then the next row. Hovering a box shows
 * its tooltip (RuneLite TooltipComponent).
 */
final class RspsInfoBoxes {

    static final String ID = "infoboxes";
    /** RuneLite: infoBoxSize default 35, InfoBoxOverlay GAP 1, the regular font (infoboxFontType). */
    static final int SIZE = 35;
    static final int GAP = 1;
    /** RuneLite InfoBoxOverlay.DEFAULT_WRAP_COUNT. */
    static final int WRAP = 4;
    private static final int SEPARATOR = 3;
    /** First row of a numbered icon's art: just inside the outside and inside border strokes. */
    private static final int ICON_TOP = 2;
    /** p11 digits: ink starts 1 px right of the pen and 1 row below the glyph top, 8 rows tall; 1 px advance after the last one. */
    private static final int DIGIT_INK_LEFT = 1;
    private static final int DIGIT_INK_TOP = 1;
    private static final int DIGIT_INK_HEIGHT = 8;
    private static final int DIGIT_TRAILING_ADVANCE = 1;

    static final class Box {
        final int sprite;
        final String text;
        final int colour;
        /** RuneLite tooltip text ({@code </br>} line breaks, {@code <col=rrggbb>} colours); null = none. */
        final String tooltip;
        /** Drawn in the middle of the icon in the small font, white with a black shadow (RuneLite PoisonPlugin.getSplat). */
        final String iconNumber;

        Box(int sprite, String text, int colour, String tooltip, String iconNumber) {
            this.sprite = sprite;
            this.text = text;
            this.colour = colour;
            this.tooltip = tooltip;
            this.iconNumber = iconNumber;
        }
    }

    private static final List<Box> boxes = new ArrayList<Box>();

    /** Where the boxes were drawn this frame (x, y per box), for the tooltip. */
    private static int[] drawnAt = new int[0];

    private RspsInfoBoxes() {
    }

    static void clear() {
        boxes.clear();
    }

    static void add(int sprite, String text, int colour) {
        boxes.add(new Box(sprite, text, colour, null, null));
    }

    static void add(int sprite, String text, int colour, String tooltip) {
        boxes.add(new Box(sprite, text, colour, tooltip, null));
    }

    static void add(int sprite, String text, int colour, String tooltip, String iconNumber) {
        boxes.add(new Box(sprite, text, colour, tooltip, iconNumber));
    }

    static int count() {
        return boxes.size();
    }

    static int width() {
        int columns = Math.min(boxes.size(), WRAP);
        return columns == 0 ? 0 : columns * (SIZE + GAP) - GAP;
    }

    static int height() {
        int rows = (boxes.size() + WRAP - 1) / WRAP;
        return rows == 0 ? 0 : rows * (SIZE + GAP) - GAP;
    }

    static void draw(int x, int y) {
        Font font = RspsHud.font();
        FontMetrics metrics = RspsHud.metrics();
        drawnAt = new int[boxes.size() * 2];
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            int bx = x + (i % WRAP) * (SIZE + GAP);
            int by = y + (i / WRAP) * (SIZE + GAP);
            drawnAt[i * 2] = bx;
            drawnAt[i * 2 + 1] = by;
            RspsHud.box(bx, by, SIZE, SIZE);
            Sprite sprite = RspsSprites.sprite(box.sprite);
            if (sprite != null) {
                int[] art = box.iconNumber != null ? RspsSprites.content(box.sprite) : null;
                if (art != null) {
                    // Owner 2026-10-09: the damage number in the middle of the splat and the timer neatly below it, never on top of it -
                    // the splat's art sits at the top of the box (inside the two border strokes), centred on its own pixels.
                    int ax = bx + (SIZE - art[2]) / 2;
                    int ay = by + ICON_TOP;
                    sprite.render(ax - art[0], ay - art[1]);
                    iconNumber(box.iconNumber, ax, ay, art[2], art[3]);
                } else {
                    int sx = bx + (SIZE - sprite.getWidth()) / 2;
                    int sy = by + (SIZE - sprite.getHeight()) / 2;
                    sprite.render(sx, sy);
                    if (box.iconNumber != null) {
                        iconNumber(box.iconNumber, sx, sy, sprite.getWidth(), sprite.getHeight());
                    }
                }
            }
            if (box.text != null && box.text.length() > 0) {
                int width = RspsHud.width(metrics, box.text);
                RspsHud.text(font, box.text, bx + (SIZE - width) / 2, by + SIZE - SEPARATOR, box.colour, false);
            }
        }
    }

    /**
     * RuneLite PoisonPlugin.getSplat: the number centred in the splat, small font, black at +1,+1 then white - centred on the digits'
     * own pixels: in p11 every digit is {@link #DIGIT_INK_HEIGHT} rows tall and starts {@link #DIGIT_INK_TOP} row below the glyph
     * top, which Font.render puts verticalSpacing above the baseline (cache p11_full, 2026-10-09).
     */
    private static void iconNumber(String number, int x, int y, int width, int height) {
        Font font = Fonts.p11 != null ? Fonts.p11 : RspsHud.font();
        FontMetrics metrics = Fonts.p11Metrics != null ? Fonts.p11Metrics : RspsHud.metrics();
        int textWidth = inkWidth(metrics, number);
        int lineHeight = metrics == null ? 10 : metrics.verticalSpacing;
        int tx = x + (width - textWidth) / 2 - DIGIT_INK_LEFT;
        int inkTop = y + (height - DIGIT_INK_HEIGHT) / 2;
        int ty = inkTop - DIGIT_INK_TOP + lineHeight;
        font.render(number, tx + 1, ty + 1, 0, 0xFF000000);
        font.render(number, tx, ty, 0, 0xFFFFFFFF);
    }

    /** Width of the digits' pixels: the advance widths without the first glyph's left bearing and the last one's trailing gap. */
    private static int inkWidth(FontMetrics metrics, String number) {
        return Math.max(1, RspsHud.width(metrics, number) - DIGIT_INK_LEFT - DIGIT_TRAILING_ADVANCE);
    }

    /** The hovered box's tooltip, drawn after every overlay (RuneLite TooltipOverlay, under the cursor). */
    static void drawTooltip(int viewX, int viewY, int viewWidth, int viewHeight) {
        if (MouseMonitor.instance == null || drawnAt.length != boxes.size() * 2) {
            return;
        }
        int mx = MouseMonitor.instance.getRecordedX();
        int my = MouseMonitor.instance.getRecordedY();
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            int bx = drawnAt[i * 2];
            int by = drawnAt[i * 2 + 1];
            if (box.tooltip != null && mx >= bx && mx < bx + SIZE && my >= by && my < by + SIZE) {
                RspsHud.tooltip(box.tooltip, mx, my, viewX, viewY, viewWidth, viewHeight);
                return;
            }
        }
    }
}
