/**
 * Layout of the RSPS "78" login screen for one viewport size. Pure arithmetic, no client state, so it is
 * checked by {@code RspsLoginLayoutRegressionTest} at every aspect ratio.
 *
 * All artwork is measured in the pixels of the approved reference screenshot
 * ({@code tools/login-screen/reference_login.png}, 1672 x 941). Three layers come from it
 * ({@code tools/login-screen/extract_layers.py}): the background, the logo and the login panel; placed back
 * at their reference positions they reproduce the reference pixel for pixel.
 *
 * <ul>
 *   <li>Background: cover - scaled to fill the viewport with its aspect ratio kept, centred, the overflow
 *       cropped (only scenery at the sides or top/bottom ever leaves the screen).</li>
 *   <li>Logo and panel ("the group", reference y 0..897): at most as large as in the reference relative to the
 *       viewport height, and never larger than the background. Up to 16:9 that is exactly the background's
 *       scale and position, so the group sits where the reference has it and the background behind it never
 *       shows. On wider screens the background is cropped top and bottom, the group keeps the full viewport
 *       height instead. The group is never cropped: it is also limited to the viewport width.</li>
 *   <li>Compact (fixed mode 765 x 503 and other small viewports): its own fit - the panel larger (readable
 *       fields and buttons), the logo smaller above it, still overlapping the panel's top edge by the
 *       reference's 40 pixels.</li>
 * </ul>
 */
public final class RspsLoginLayout {

    public static final int REF_W = 1672;
    public static final int REF_H = 941;
    public static final double REF_CENTRE_X = REF_W / 2.0;
    public static final double REF_CENTRE_Y = REF_H / 2.0;

    /** Logo layer: origin and size in the reference. */
    public static final int LOGO_X = 562, LOGO_Y = 0, LOGO_W = 548, LOGO_H = 385;

    /** Panel layer (frame, ornaments, fields, button, links): origin and size in the reference. */
    public static final int PANEL_X = 604, PANEL_Y = 345, PANEL_W = 465, PANEL_H = 552;

    /** The logo's lowest row lies this far below the panel layer's top (ornament band). */
    public static final int LOGO_PANEL_OVERLAP = LOGO_Y + LOGO_H - PANEL_Y;

    /** Top of the logo to the bottom of the panel in the reference. */
    public static final int GROUP_H = PANEL_Y + PANEL_H - LOGO_Y;

    /** Reference rectangles {x0, y0, x1, y1} (x1/y1 exclusive) of the parts the client interface drives. */
    public static final int[] BODY = {633, 387, 1041, 864};
    public static final int[] USERNAME = {672, 497, 1001, 541};
    public static final int[] PASSWORD = {672, 580, 1001, 624};
    public static final int[] BUTTON = {672, 641, 999, 706};
    /** The button with its side ornaments and glow: what the hover/pressed/disabled states redraw. */
    public static final int[] BUTTON_ART = {660, 631, 1011, 716};
    public static final int[] RECOVER = {752, 729, 918, 760};
    public static final int[] CREATE = {764, 810, 907, 841};

    /** Width of the user/lock icon area at the left of each field; typed text starts after it. */
    public static final int FIELD_TEXT_INSET = 44;

    /** Viewports lower than this (fixed mode is 503) or narrower than {@link #COMPACT_MAX_W} use the compact fit. */
    public static final int COMPACT_MAX_H = 600;
    public static final int COMPACT_MAX_W = 700;

    /** Screen margin kept around the group whenever it is limited by the viewport. */
    public static final int MARGIN = 6;

    /** Group scale limits: never microscopic, never enormous (the reference itself is 1.0). */
    public static final double MIN_SCALE = 0.40;
    public static final double MAX_SCALE = 2.60;

    public final int width;
    public final int height;
    public final boolean compact;

    public final double bgScale;
    public final int bgX, bgY, bgW, bgH;

    public final double logoScale;
    /** Screen position of reference point (0, 0) for the logo layer's transform. */
    private final double logoOriginX, logoOriginY;

    public final double panelScale;
    private final double panelOriginX, panelOriginY;

    public RspsLoginLayout(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);

        bgScale = Math.max(this.width / (double) REF_W, this.height / (double) REF_H);
        bgW = (int) Math.ceil(REF_W * bgScale);
        bgH = (int) Math.ceil(REF_H * bgScale);
        bgX = (int) Math.floor((this.width - REF_W * bgScale) / 2.0);
        bgY = (int) Math.floor((this.height - REF_H * bgScale) / 2.0);

        compact = this.height < COMPACT_MAX_H || this.width < COMPACT_MAX_W;
        double cx = this.width / 2.0;
        if (!compact) {
            double s = Math.min(bgScale, this.height / (double) REF_H);
            s = Math.min(s, (this.width - 2.0 * MARGIN) / LOGO_W);
            s = clamp(s, MIN_SCALE, MAX_SCALE);
            logoScale = s;
            panelScale = s;
            // Centre-anchored: the reference centre maps to the viewport centre, as it does for the background.
            logoOriginX = cx - REF_CENTRE_X * s;
            logoOriginY = this.height / 2.0 - REF_CENTRE_Y * s;
            panelOriginX = logoOriginX;
            panelOriginY = logoOriginY;
        } else {
            double ps = clamp((this.height - 10.0) / 800.0, MIN_SCALE, 0.75);
            ps = Math.max(MIN_SCALE * 0.75, Math.min(ps, (this.width - 2.0 * MARGIN) / PANEL_W));
            double panelH = PANEL_H * ps;
            double ls = (this.height - 2.0 * MARGIN - panelH + LOGO_PANEL_OVERLAP * ps) / LOGO_H;
            ls = Math.max(0.2, Math.min(ls, ps));
            ls = Math.min(ls, (this.width - 2.0 * MARGIN) / LOGO_W);
            double total = LOGO_H * ls - LOGO_PANEL_OVERLAP * ps + panelH;
            double top = Math.max(0, (this.height - total) / 2.0);
            logoScale = ls;
            panelScale = ps;
            logoOriginX = cx - REF_CENTRE_X * ls;
            logoOriginY = top - LOGO_Y * ls;
            double panelTop = top + LOGO_H * ls - LOGO_PANEL_OVERLAP * ps;
            panelOriginX = cx - REF_CENTRE_X * ps;
            panelOriginY = panelTop - PANEL_Y * ps;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** True when the group sits exactly where the background has it (the reference composition). */
    public boolean locked() {
        return !compact && Math.abs(logoScale - bgScale) < 1e-9;
    }

    /**
     * Reference area under the panel whose pixels the screenshot does not have (filled from its surroundings in
     * {@code extract_layers.py}). Wider than 16:9 the group is smaller than the background, so this area shows
     * around the panel; {@link #shade()} then darkens it softly.
     */
    public static final int[] HIDDEN_PANEL_AREA = {604, 345, 1068, 916};

    /** True when the panel is smaller than the background's panel and the filled area would show. */
    public boolean needsShade() {
        return !compact && panelScale < bgScale * 0.995;
    }

    /**
     * Screen rectangle {x, y, w, h} of the shade: the hidden area in background space plus a soft edge of
     * {@link #shadeFeather()} on every side, clipped to the viewport.
     */
    public int[] shade() {
        int f = shadeFeather();
        int x0 = (int) Math.floor(bgX + HIDDEN_PANEL_AREA[0] * bgScale) - f;
        int y0 = (int) Math.floor(bgY + HIDDEN_PANEL_AREA[1] * bgScale) - f;
        int x1 = (int) Math.ceil(bgX + HIDDEN_PANEL_AREA[2] * bgScale) + f;
        int y1 = (int) Math.ceil(bgY + HIDDEN_PANEL_AREA[3] * bgScale) + f;
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(width, x1);
        y1 = Math.min(height, y1);
        return new int[] {x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0)};
    }

    public int shadeFeather() {
        return (int) Math.round(56 * bgScale);
    }

    /** The shade's pixels (straight ARGB, {@code shade()[2] x shade()[3]}). */
    public int[] shadePixels() {
        int[] r = shade();
        int f = shadeFeather();
        int ix0 = (int) Math.floor(bgX + HIDDEN_PANEL_AREA[0] * bgScale) - r[0];
        int iy0 = (int) Math.floor(bgY + HIDDEN_PANEL_AREA[1] * bgScale) - r[1];
        int ix1 = (int) Math.ceil(bgX + HIDDEN_PANEL_AREA[2] * bgScale) - r[0];
        int iy1 = (int) Math.ceil(bgY + HIDDEN_PANEL_AREA[3] * bgScale) - r[1];
        return RspsLoginArt.shade(r[2], r[3], ix0, iy0, ix1, iy1, f);
    }

    public int logoX() {
        return (int) Math.round(logoOriginX + LOGO_X * logoScale);
    }

    public int logoY() {
        return (int) Math.round(logoOriginY + LOGO_Y * logoScale);
    }

    public int logoW() {
        return Math.max(1, (int) Math.round(LOGO_W * logoScale));
    }

    public int logoH() {
        return Math.max(1, (int) Math.round(LOGO_H * logoScale));
    }

    public int panelX() {
        return (int) Math.round(panelOriginX + PANEL_X * panelScale);
    }

    public int panelY() {
        return (int) Math.round(panelOriginY + PANEL_Y * panelScale);
    }

    public int panelW() {
        return Math.max(1, (int) Math.round(PANEL_W * panelScale));
    }

    public int panelH() {
        return Math.max(1, (int) Math.round(PANEL_H * panelScale));
    }

    /**
     * Screen rectangle {x, y, w, h} of a panel reference rectangle. Edges are rounded from the drawn panel
     * layer's own origin, so hitboxes line up with the scaled artwork to the pixel.
     */
    public int[] panelRect(int[] ref) {
        int px = panelX();
        int py = panelY();
        int x0 = px + (int) Math.round((ref[0] - PANEL_X) * panelScale);
        int y0 = py + (int) Math.round((ref[1] - PANEL_Y) * panelScale);
        int x1 = px + (int) Math.round((ref[2] - PANEL_X) * panelScale);
        int y1 = py + (int) Math.round((ref[3] - PANEL_Y) * panelScale);
        return new int[] {x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0)};
    }

    /** Panel reference distance in screen pixels. */
    public int panelLength(int ref) {
        return (int) Math.round(ref * panelScale);
    }

    public boolean sameAs(RspsLoginLayout other) {
        return other != null && other.width == width && other.height == height;
    }
}
