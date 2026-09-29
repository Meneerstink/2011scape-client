import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Sprite;

import java.util.ArrayList;
import java.util.List;

/**
 * RuneLite-style infoboxes (InfoBoxComponent / InfoBoxOverlay): 35 px squares one pixel apart in the standard overlay
 * background, the icon centred, the text centred along the bottom (3 px up) in the regular font. Timers & Buffs always show as infoboxes; Boosts Information does when
 * "Display infoboxes" is on. All boxes of a frame form one row that moves as one overlay (Alt + drag), like RuneLite's
 * infobox overlay.
 */
final class RspsInfoBoxes {

    static final String ID = "infoboxes";
    /** RuneLite: infoBoxSize default 35, InfoBoxOverlay GAP 1, the regular font (infoboxFontType). */
    static final int SIZE = 35;
    static final int GAP = 1;
    private static final int SEPARATOR = 3;

    static final class Box {
        final int sprite;
        final String text;
        final int colour;

        Box(int sprite, String text, int colour) {
            this.sprite = sprite;
            this.text = text;
            this.colour = colour;
        }
    }

    private static final List<Box> boxes = new ArrayList<Box>();

    private RspsInfoBoxes() {
    }

    static void clear() {
        boxes.clear();
    }

    static void add(int sprite, String text, int colour) {
        boxes.add(new Box(sprite, text, colour));
    }

    static int count() {
        return boxes.size();
    }

    static int width() {
        return boxes.isEmpty() ? 0 : boxes.size() * (SIZE + GAP) - GAP;
    }

    static void draw(int x, int y) {
        Font font = RspsHud.font();
        FontMetrics metrics = RspsHud.metrics();
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            int bx = x + i * (SIZE + GAP);
            RspsHud.box(bx, y, SIZE, SIZE);
            Sprite sprite = RspsSprites.sprite(box.sprite);
            if (sprite != null) {
                sprite.render(bx + (SIZE - sprite.getWidth()) / 2, y + (SIZE - sprite.getHeight()) / 2);
            }
            if (box.text != null && box.text.length() > 0) {
                int width = RspsHud.width(metrics, box.text);
                RspsHud.text(font, box.text, bx + (SIZE - width) / 2, y + SIZE - SEPARATOR, box.colour, false);
            }
        }
    }
}
