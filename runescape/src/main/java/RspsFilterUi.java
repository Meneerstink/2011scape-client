import java.util.List;

/**
 * N482 / N72: the OSRS filter-menu pieces both filter menus (prayer tab 271, spellbooks 192 / 193 / 430) draw, as client
 * components built in Java (CC_CREATE semantics): the "Filters" button (OSRS options_button_off / _on 835 / 836: nine tiles with 6 px
 * corners, grey closed / red open, orange p11 label that turns white under the mouse), the menu's stone panel (OSRS
 * stonepanel_resizable 6067: v2_borders_slim edges and corners on 32 x 32 canvases over the dark tradebacking tile, a line under the
 * 28 px title) and plain text. The art is OSRS cache 2686 sprites kept as client resources ({@link RspsSprites#osrs}).
 */
final class RspsFilterUi {

    static final int ORANGE = 0xff981f;
    static final int WHITE = 0xffffff;
    static final int GREY = 0x9f9f9f;
    /** p11_full / p12_full / b12_full. */
    static final int FONT = 494;
    static final int FONT_P12 = 495;
    static final int TITLE_FONT = 496;
    static final int BUTTON_HEIGHT = 18;
    static final int MENU_WIDTH = 180;
    static final int TITLE_HEIGHT = 28;

    private RspsFilterUi() {
    }

    /** CC_CREATE in Java: appended to [parent]'s CC list; [track] (may be null) remembers it. */
    static Component create(Component parent, int type, List<Component> track) {
        int id = parent.staticComponents == null ? 0 : parent.staticComponents.length;
        Component[] grown = new Component[id + 1];
        if (parent.staticComponents != null) {
            System.arraycopy(parent.staticComponents, 0, grown, 0, id);
        }
        Component cc = new Component();
        cc.type = type;
        cc.layer = cc.slot = parent.slot;
        cc.id = id;
        grown[id] = cc;
        parent.staticComponents = parent.dynamicComponents = grown;
        if (track != null) {
            track.add(cc);
        }
        return cc;
    }

    static void place(Component c, int x, int y, int w, int h) {
        c.originalX = x;
        c.originalY = y;
        c.originalWidth = w;
        c.originalHeight = h;
        InterfaceManager.resizeAndReposition(c);
    }

    static Component graphic(Component parent, List<Component> track, String name, int x, int y, int w, int h, boolean tiled) {
        Component g = create(parent, Component.TYPE_GRAPHIC, track);
        place(g, x, y, Math.max(0, w), Math.max(0, h));
        g.graphic = RspsSprites.osrs(name);
        g.tiling = tiled;
        return g;
    }

    static Component text(Component parent, List<Component> track, String s, int x, int y, int w, int h, int alignX, int font, int colour) {
        Component t = create(parent, Component.TYPE_TEXT, track);
        place(t, x, y, w, h);
        t.text = s;
        t.fontGraphic = font;
        t.colour = colour;
        t.textShadow = true;
        t.textAlignX = alignX;
        t.textAlignY = 1;
        return t;
    }

    /** OSRS script 3154 / 2611: the button is the label's width + 14. */
    static int buttonWidth(String label) {
        com.jagex.graphics.FontMetrics metrics = com.jagex.graphics.Toolkit.active == null ? null : com.jagex.graphics.Fonts.metrics(FONT, com.jagex.graphics.Toolkit.active);
        return (metrics == null ? 32 : metrics.stringWidth(label)) + 14;
    }

    /** The Filters button; returns its label (the component that takes the mouse). */
    static Component button(Component parent, List<Component> track, String label, int x, int y, int w, boolean open) {
        nineSlice(parent, track, open ? "btn-on" : "btn-off", x, y, w, BUTTON_HEIGHT);
        Component t = text(parent, track, label, x, y, w, BUTTON_HEIGHT, 1, FONT, ORANGE);
        t.hasHook = true;
        return t;
    }

    static void nineSlice(Component parent, List<Component> track, String prefix, int x, int y, int w, int h) {
        graphic(parent, track, prefix + "-4", x + 6, y + 6, w - 12, h - 12, true);
        graphic(parent, track, prefix + "-1", x + 6, y, w - 12, 6, true);
        graphic(parent, track, prefix + "-7", x + 6, y + h - 6, w - 12, 6, true);
        graphic(parent, track, prefix + "-3", x, y + 6, 6, h - 12, true);
        graphic(parent, track, prefix + "-5", x + w - 6, y + 6, 6, h - 12, true);
        graphic(parent, track, prefix + "-0", x, y, 6, 6, false);
        graphic(parent, track, prefix + "-2", x + w - 6, y, 6, 6, false);
        graphic(parent, track, prefix + "-6", x, y + h - 6, 6, 6, false);
        graphic(parent, track, prefix + "-8", x + w - 6, y + h - 6, 6, 6, false);
    }

    /** The dark panel, its stone frame and the title; the panel takes the mouse (nothing beneath it reacts). */
    static Component panel(Component parent, List<Component> track, String title, int x, int y, int w, int h) {
        Component back = graphic(parent, track, "tradebacking-dark", x, y, w, h, true);
        back.hasHook = true;
        graphic(parent, track, "border-0", x + 16, y - 13, w - 32, 32, true);
        graphic(parent, track, "border-2", x + 16, y + h - 32 + 12, w - 32, 32, true);
        graphic(parent, track, "border-1", x - 13, y + 16, 32, h - 32, true);
        graphic(parent, track, "border-3", x + w - 32 + 12, y + 16, 32, h - 32, true);
        graphic(parent, track, "border-4", x, y, 32, 32, false);
        graphic(parent, track, "border-5", x + w - 32, y, 32, 32, false);
        graphic(parent, track, "border-6", x, y + h - 32, 32, 32, false);
        graphic(parent, track, "border-7", x + w - 32, y + h - 32, 32, 32, false);
        // The line under the title (stonepanel argument 26: y - 20 / y - 18 on the 32 px canvases).
        graphic(parent, track, "border-8", x, y + 26 - 20, w, 32, true);
        graphic(parent, track, "border-9", x, y + 26 - 18, 32, 32, false);
        graphic(parent, track, "border-10", x + w - 32, y + 26 - 18, 32, 32, false);
        text(parent, track, title, x, y, w, TITLE_HEIGHT, 1, TITLE_FONT, ORANGE);
        return back;
    }

    /**
     * One menu row (OSRS 3158 / 2604): a full-width white highlight (see-through until hovered), the 17 x 17 indicator at x 5, the
     * text from x 25 to 5 from the right; returns {highlight, hit area}. The hit area is the row's one click.
     */
    static Component[] row(Component parent, List<Component> track, String label, int x, int y, int w, int h, String indicator, int colour) {
        Component highlight = create(parent, Component.TYPE_RECTANGLE, track);
        place(highlight, x + 3, y, w - 6, h);
        highlight.colour = WHITE;
        highlight.filled = true;
        highlight.transparency = 255;
        graphic(parent, track, indicator, x + 5, y + (h - 17) / 2, 17, 17, false);
        text(parent, track, label, x + 25, y, w - 30, h, 0, FONT, colour);
        Component hit = create(parent, Component.TYPE_RECTANGLE, track);
        place(hit, x + 3, y, w - 6, h);
        hit.transparency = 255;
        hit.hasHook = true;
        return new Component[] {highlight, hit};
    }

    /** OSRS 3652 / 3653: a row under the mouse 225, a clicked row 210 for 40 client cycles (800 ms), else see-through. */
    static void light(Component highlight, Component hit, boolean active, boolean clicked) {
        if (highlight == null || hit == null) {
            return;
        }
        int trans = clicked ? 210 : hit.hovered && active ? 225 : 255;
        if (highlight.transparency != trans) {
            highlight.transparency = trans;
            InterfaceManager.redraw(highlight);
        }
    }

    /** OSRS 835: the button label white under the mouse, orange otherwise. */
    static void hoverLabel(Component label) {
        if (label == null) {
            return;
        }
        int colour = label.hovered ? WHITE : ORANGE;
        if (label.colour != colour) {
            label.colour = colour;
            InterfaceManager.redraw(label);
        }
    }

    /** The click sound of a filter row / the Filters button (OSRS sound_synth 2266). */
    static void clickSound() {
        SoundManager.playSynthSound(2266, 1, 0, 255, 256);
    }
}
