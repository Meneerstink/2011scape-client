/**
 * Layout of the RSPS "78" login screen for one viewport size. Pure arithmetic, no client state, so it is
 * checked by {@code RspsLoginLayoutRegressionTest} at every aspect ratio.
 *
 * Artwork is measured in the 1672 x 941 approved Deadman login composition. The landscape is a separate
 * background; the hanging flag and login panel are cropped from the transparent foreground at load time.
 *
 * <ul>
 *   <li>Background: cover - scaled to fill the viewport with its aspect ratio kept, centred, the overflow
 *       cropped (only scenery at the sides or top/bottom ever leaves the screen).</li>
 *   <li>Flag and panel ("the group", reference y 10..903): at most as large as in the reference relative to the
 *       viewport height, and never larger than the background. Up to 16:9 that is exactly the background's
 *       scale and position, so the group sits where the reference has it and the background behind it never
 *       shows. On wider screens the background is cropped top and bottom, the group keeps the full viewport
 *       height instead. The group is never cropped: it is also limited to the viewport width.</li>
 *   <li>Compact (fixed mode 765 x 503 and other small viewports): its own fit - the panel larger (readable
 *       fields and buttons), the flag smaller above it, still overlapping the panel's top edge by the
 *       reference's 27 pixels.</li>
 * </ul>
 */
public final class RspsLoginLayout {

    public static final int REF_W = 1672;
    public static final int REF_H = 941;
    public static final double REF_CENTRE_X = REF_W / 2.0;
    public static final double REF_CENTRE_Y = REF_H / 2.0;

    /** Hanging flag layer: origin and size in the reference. */
    public static final int LOGO_X = 600, LOGO_Y = 10, LOGO_W = 472, LOGO_H = 525;

    /** Panel layer (frame, ornaments, fields, button, links): origin and size in the reference. */
    public static final int PANEL_X = 607, PANEL_Y = 508, PANEL_W = 458, PANEL_H = 395;

    /** The logo's lowest row lies this far below the panel layer's top (ornament band). */
    public static final int LOGO_PANEL_OVERLAP = LOGO_Y + LOGO_H - PANEL_Y;

    /** Top of the logo to the bottom of the panel in the reference. */
    public static final int GROUP_H = PANEL_Y + PANEL_H - LOGO_Y;

    /** Reference rectangles {x0, y0, x1, y1} (x1/y1 exclusive) of the parts the client interface drives. */
    public static final int[] BODY = {630, 535, 1042, 880};
    public static final int[] USERNAME = {667, 568, 1002, 612};
    public static final int[] PASSWORD = {667, 649, 1002, 694};
    public static final int[] BUTTON = {665, 711, 1005, 770};
    /** The button with its side ornaments and glow: what the hover/pressed/disabled states redraw. */
    public static final int[] BUTTON_ART = {658, 705, 1011, 777};
    public static final int[] RECOVER = {752, 789, 921, 822};
    public static final int[] CREATE = {755, 828, 917, 861};

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

    /** When both layers have the same fit, draw the exact approved screenshot as one backdrop. */
    public boolean referenceBackdrop() {
        return !compact && Math.abs(panelScale - bgScale) <= bgScale * 0.002;
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
