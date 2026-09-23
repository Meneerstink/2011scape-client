import com.jagex.game.runetek6.config.defaults.GraphicsDefaults;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;
import com.jagex.core.constants.MainLogicStep;
import com.jagex.game.runetek6.client.GameShell;
import rs2.client.event.mouse.MouseMonitor;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * The RSPS "78" login screen: the owner's approved artwork drawn natively behind the cache login interface.
 *
 * <p>The login itself stays the cache interface ({@link GraphicsDefaults#login_interface}, 596) with all its
 * clientscripts: username/password fields (VARCSTR 32/33, focus VARC 174, masking, Enter), the Log In button
 * (script 2944 -> 2945 -> LOGIN_REQUEST), Recover Password (47), Create Account (61) and the status/error
 * boxes (8, 9). Nothing of the networking or authentication changes. This class only
 * <ul>
 *   <li>draws the background, the logo and the login panel ({@code /login78/*.png}, from
 *       {@code tools/login-screen}) with {@link RspsLoginLayout}, before the interface;</li>
 *   <li>hides the interface's 2011 decoration (frames, labels baked into the new panel, Facebook login) via
 *       {@link #hides};</li>
 *   <li>moves the working components onto the panel's fields, button and links via {@link #adjust}, so the
 *       hitboxes are exactly the artwork's;</li>
 *   <li>draws hover / pressed / disabled states, the focused field's glow and a matching frame behind the
 *       status boxes.</li>
 * </ul>
 *
 * <p>The PNGs keep full 8-bit alpha and colour (the 667 cache sprite format is a 256-colour palette, which
 * would band the gradients). Every layer is resampled once per viewport size with high-quality filtering in
 * premultiplied alpha and drawn 1:1, so it is equally sharp in the software, OpenGL and DirectX toolkits.
 * If the art or the expected interface is missing, everything here stays off and the stock screen shows.
 */
public final class RspsLoginScreen {

    /** Components of interface 596 used here (see the class comment for their roles). */
    static final int FORM_CONTAINER = 6, FORM_FRAME = 4, FORM = 5, STATUS_LAYER = 7, STATUS_BOX = 8, STATUS_SMALL = 9;
    static final int USERNAME = 40, PASSWORD = 42, LOGIN_BUTTON = 45, RECOVER = 47, CREATE = 61;
    static final int USERNAME_TEXT_LAYER = 72, USERNAME_TEXT = 73, USERNAME_CARET = 74;
    static final int PASSWORD_TEXT_LAYER = 78, PASSWORD_TEXT = 79, PASSWORD_CARET = 80;
    static final int STATUS_BUTTON_A = 15, STATUS_BUTTON_B = 63;
    static final int EXPECTED_COMPONENTS = 90;

    /** VARC 174: the focused field, set by script 2943 (3 username, 4 password). */
    static final int FOCUS_VARC = 174;

    /**
     * The interface's own 2011 decoration: frame/backdrop sprites, the texts and lines the new panel carries
     * itself (Login, Username, Password, button and link labels), Facebook login (50, 51) and its separators.
     */
    private static final int[] HIDDEN = {
        1, 2, 3, 12, 16, 17, 18, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 41, 43, 44, 46, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 62,
        64, 65, 66, 67, 69, 70, 71, 75, 76, 77, 81, 82, 83, 84, 85, 86, 87, 88, 89
    };
    private static final boolean[] HIDDEN_SET = new boolean[EXPECTED_COMPONENTS];

    static {
        for (int id : HIDDEN) {
            HIDDEN_SET[id] = true;
        }
    }

    /** Typed text: near-white with a hint of the panel's lilac, like the reference's labels. */
    static final int TEXT_COLOUR = 0xF1EAFB;

    private static final int FOCUS_OUTER = 0x70A64DFF;
    private static final int FOCUS_INNER = 0xFFC98BFF;
    private static final int STATUS_FILL = 0xEC0C0812;
    private static final int STATUS_EDGE = 0xFF8E3FE0;
    private static final int STATUS_EDGE_DARK = 0xFF2A1840;
    private static final int STATUS_BUTTON_FILL = 0xFF4A1C84;
    private static final int STATUS_BUTTON_HOVER = 0xFF6A2DBA;

    /** Rebuild the scaled sprites only once the viewport has been steady this long (window drag-resizes). */
    private static final long SETTLE_MS = 150;

    private static boolean loadTried;
    private static BufferedImage background, logo, panel;
    private static BufferedImage buttonHover, buttonPressed, buttonDisabled, recoverHover, createHover;

    private static Toolkit builtFor;
    private static RspsLoginLayout built;
    private static RspsLoginLayout pending;
    private static long pendingSince;
    private static Sprite bgSprite, logoSprite, panelSprite, shadeSprite;
    private static Sprite buttonHoverSprite, buttonPressedSprite, buttonDisabledSprite, recoverHoverSprite, createHoverSprite;

    private RspsLoginScreen() {
    }

    // ------------------------------------------------------------------ activation

    private static int loginInterface() {
        return GraphicsDefaults.instance == null ? -1 : GraphicsDefaults.instance.login_interface;
    }

    private static boolean artLoaded() {
        if (!loadTried) {
            loadTried = true;
            load();
        }
        return background != null && logo != null && panel != null;
    }

    /** The login interface is the top level, has the layout this class was written for, and the art exists. */
    public static boolean active() {
        int id = loginInterface();
        if (id < 0 || InterfaceManager.topLevelInterface != id) {
            return false;
        }
        Component[] components = InterfaceList.interfaces == null || id >= InterfaceList.interfaces.length ? null : InterfaceList.interfaces[id];
        return components != null && components.length == EXPECTED_COMPONENTS && artLoaded();
    }

    private static boolean ours(Component c) {
        return c != null && c.slot != -1 && (c.slot >>> 16) == loginInterface() && active();
    }

    private static int index(Component c) {
        return c.slot & 0xFFFF;
    }

    // ------------------------------------------------------------------ interface hooks

    /** {@link InterfaceManager#isHidden}: the 2011 decoration is never drawn nor clickable. */
    public static boolean hides(Component c) {
        if (!ours(c)) {
            return false;
        }
        int id = index(c);
        return id < HIDDEN_SET.length && HIDDEN_SET[id];
    }

    /** {@link InterfaceManager#reposition} (after the cache layout): puts the working components on the art. */
    public static void adjust(Component c) {
        if (!ours(c)) {
            return;
        }
        RspsLoginLayout l = layout();
        int[] body = l.panelRect(RspsLoginLayout.BODY);
        int inset = l.panelLength(RspsLoginLayout.FIELD_TEXT_INSET);
        switch (index(c)) {
            case FORM_CONTAINER:
                set(c, body[0], body[1], body[2], body[3]);
                break;
            case FORM_FRAME:
            case FORM:
                set(c, 0, 0, body[2], body[3]);
                break;
            case USERNAME:
                setInBody(c, l.panelRect(RspsLoginLayout.USERNAME), body);
                break;
            case PASSWORD:
                setInBody(c, l.panelRect(RspsLoginLayout.PASSWORD), body);
                break;
            case LOGIN_BUTTON:
                setInBody(c, l.panelRect(RspsLoginLayout.BUTTON), body);
                break;
            case RECOVER:
                setInBody(c, l.panelRect(RspsLoginLayout.RECOVER), body);
                break;
            case CREATE:
                setInBody(c, l.panelRect(RspsLoginLayout.CREATE), body);
                break;
            case USERNAME_TEXT_LAYER:
            case PASSWORD_TEXT_LAYER: {
                int fieldH = l.panelRect(RspsLoginLayout.USERNAME)[3];
                int fieldW = l.panelRect(RspsLoginLayout.USERNAME)[2];
                // The text component sits 6 px into this layer; the typed text then starts after the icon.
                c.x = inset - 6;
                c.y = 0;
                c.height = fieldH;
                c.width = Math.max(c.width, fieldW - inset - 4);
                break;
            }
            case USERNAME_TEXT:
            case PASSWORD_TEXT:
                c.y = 0;
                c.height = l.panelRect(RspsLoginLayout.USERNAME)[3];
                c.colour = TEXT_COLOUR;
                c.textAlignY = 1;
                break;
            case USERNAME_CARET:
            case PASSWORD_CARET:
                // The clientscript moves the caret along the text from 6 px; follow the text's new start.
                c.x += inset - 6;
                c.y = (l.panelRect(RspsLoginLayout.USERNAME)[3] - c.height) / 2;
                c.colour = TEXT_COLOUR;
                break;
            case STATUS_BOX:
            case STATUS_SMALL:
                centreOnBody(c, body);
                break;
            default:
                break;
        }
    }

    private static void set(Component c, int x, int y, int w, int h) {
        c.x = x;
        c.y = y;
        c.width = w;
        c.height = h;
    }

    private static void setInBody(Component c, int[] rect, int[] body) {
        set(c, rect[0] - body[0], rect[1] - body[1], rect[2], rect[3]);
    }

    /** Status boxes hang under the full-screen layer 7; centre them on the panel body. */
    private static void centreOnBody(Component c, int[] body) {
        Component[] components = InterfaceList.interfaces[loginInterface()];
        Component parent = components[STATUS_LAYER];
        int px = parent == null ? 0 : parent.x;
        int py = parent == null ? 0 : parent.y;
        c.x = body[0] + (body[2] - c.width) / 2 - px;
        c.y = body[1] + (body[3] - c.height) / 2 - py;
    }

    /** {@link InterfaceManager#draw}: the frame behind the status boxes and their buttons. */
    public static void beforeComponent(Component c, int x, int y) {
        if (!ours(c)) {
            return;
        }
        Toolkit t = Toolkit.active;
        switch (index(c)) {
            case STATUS_BOX:
            case STATUS_SMALL: {
                int pad = 10;
                int bx = x - pad, by = y - pad, bw = c.width + pad * 2, bh = c.height + pad * 2;
                t.aa(bx, by, bw, bh, STATUS_FILL, 1);
                t.outlineRect(bx, by, bw, bh, STATUS_EDGE_DARK, 1);
                t.outlineRect(bx + 1, by + 1, bw - 2, bh - 2, STATUS_EDGE, 1);
                t.outlineRect(bx + 2, by + 2, bw - 4, bh - 4, STATUS_EDGE_DARK, 1);
                break;
            }
            case STATUS_BUTTON_A:
            case STATUS_BUTTON_B: {
                boolean hover = inside(x, y, c.width, c.height);
                t.aa(x, y, c.width, c.height, hover ? STATUS_BUTTON_HOVER : STATUS_BUTTON_FILL, 1);
                t.outlineRect(x, y, c.width, c.height, STATUS_EDGE, 1);
                break;
            }
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ backdrop

    private static RspsLoginLayout layout() {
        RspsLoginLayout current = new RspsLoginLayout(GameShell.canvasWid, GameShell.canvasHei);
        if (current.sameAs(built)) {
            return built;
        }
        return current;
    }

    /**
     * {@link InterfaceManager#method3833}, before the top-level interface: background, logo, panel and the
     * states of the button, links and fields. Also marks the whole interface dirty, because the backdrop
     * repaints the full screen every frame.
     */
    public static void drawBackdrop(int originX, int originY) {
        if (!active()) {
            return;
        }
        Toolkit t = Toolkit.active;
        if (t == null) {
            return;
        }
        RspsLoginLayout wanted = new RspsLoginLayout(GameShell.canvasWid, GameShell.canvasHei);
        if (builtFor != t) {
            release();
            builtFor = t;
            build(t, wanted);
        } else if (!wanted.sameAs(built)) {
            long now = System.currentTimeMillis();
            if (!wanted.sameAs(pending)) {
                pending = wanted;
                pendingSince = now;
            }
            if (built == null || now - pendingSince >= SETTLE_MS) {
                build(t, wanted);
                pending = null;
            }
        }
        RspsLoginLayout l = built;
        if (l == null || bgSprite == null) {
            return;
        }

        t.KA(originX, originY, originX + GameShell.canvasWid, originY + GameShell.canvasHei);
        boolean exact = wanted.sameAs(l);
        if (exact) {
            bgSprite.render(originX + l.bgX, originY + l.bgY);
            if (shadeSprite != null) {
                int[] s = l.shade();
                shadeSprite.render(originX + s[0], originY + s[1]);
            }
            logoSprite.render(originX + l.logoX(), originY + l.logoY());
            panelSprite.render(originX + l.panelX(), originY + l.panelY());
        } else {
            // Mid-resize: stretch the last build until the new size has settled.
            bgSprite.render(originX + wanted.bgX, originY + wanted.bgY, wanted.bgW, wanted.bgH);
            if (shadeSprite != null && wanted.needsShade()) {
                int[] s = wanted.shade();
                shadeSprite.render(originX + s[0], originY + s[1], s[2], s[3]);
            }
            logoSprite.render(originX + wanted.logoX(), originY + wanted.logoY(), wanted.logoW(), wanted.logoH());
            panelSprite.render(originX + wanted.panelX(), originY + wanted.panelY(), wanted.panelW(), wanted.panelH());
            l = wanted;
        }

        drawStates(t, l, originX, originY, exact);

        for (int i = 0; i < InterfaceManager.currentlyDirtyRect.length; i++) {
            InterfaceManager.currentlyDirtyRect[i] = true;
            InterfaceManager.flipDirtyRect[i] = true;
        }
        InterfaceManager.redrawAll();
    }

    private static void drawStates(Toolkit t, RspsLoginLayout l, int ox, int oy, boolean exact) {
        boolean busy = LoginManager.inProgress() || MainLogicManager.step != MainLogicStep.STEP_LOGIN_SCREEN;

        int[] button = l.panelRect(RspsLoginLayout.BUTTON);
        int[] buttonArt = l.panelRect(RspsLoginLayout.BUTTON_ART);
        boolean overButton = inside(ox + button[0], oy + button[1], button[2], button[3]);
        Sprite state = null;
        int dy = 0;
        if (busy) {
            state = buttonDisabledSprite;
        } else if (overButton && MouseMonitor.instance != null && MouseMonitor.instance.isLeftDown()) {
            state = buttonPressedSprite;
            dy = Math.max(1, l.panelLength(2));
        } else if (overButton) {
            state = buttonHoverSprite;
        }
        if (state != null) {
            render(state, ox + buttonArt[0], oy + buttonArt[1] + dy, buttonArt[2], buttonArt[3], exact);
        }

        if (!busy) {
            link(t, l, RspsLoginLayout.RECOVER, recoverHoverSprite, ox, oy, exact);
            link(t, l, RspsLoginLayout.CREATE, createHoverSprite, ox, oy, exact);
        }

        int focus = Static511.varcs == null || FOCUS_VARC >= Static511.varcs.length ? -1 : Static511.varcs[FOCUS_VARC];
        if (focus == 3 || focus == 4) {
            int[] f = l.panelRect(focus == 3 ? RspsLoginLayout.USERNAME : RspsLoginLayout.PASSWORD);
            int x = ox + f[0], y = oy + f[1];
            t.outlineRect(x - 1, y - 1, f[2] + 2, f[3] + 2, FOCUS_OUTER, 1);
            t.outlineRect(x, y, f[2], f[3], FOCUS_INNER, 1);
        }
    }

    private static void link(Toolkit t, RspsLoginLayout l, int[] ref, Sprite hover, int ox, int oy, boolean exact) {
        int[] r = l.panelRect(ref);
        if (hover != null && inside(ox + r[0], oy + r[1], r[2], r[3])) {
            render(hover, ox + r[0], oy + r[1], r[2], r[3], exact);
        }
    }

    private static void render(Sprite sprite, int x, int y, int w, int h, boolean exact) {
        if (sprite == null) {
            return;
        }
        if (exact) {
            sprite.render(x, y);
        } else {
            sprite.render(x, y, w, h);
        }
    }

    private static boolean inside(int x, int y, int w, int h) {
        if (MouseMonitor.instance == null) {
            return false;
        }
        int mx = MouseMonitor.instance.getRecordedX();
        int my = MouseMonitor.instance.getRecordedY();
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ------------------------------------------------------------------ sprites

    private static void build(Toolkit t, RspsLoginLayout l) {
        Sprite bg = sprite(t, RspsLoginArt.scale(background, l.bgW, l.bgH), l.bgW, l.bgH);
        Sprite lg = sprite(t, RspsLoginArt.scale(logo, l.logoW(), l.logoH()), l.logoW(), l.logoH());
        Sprite pn = sprite(t, RspsLoginArt.scale(panel, l.panelW(), l.panelH()), l.panelW(), l.panelH());
        if (bg == null || lg == null || pn == null) {
            return;
        }
        bgSprite = bg;
        logoSprite = lg;
        panelSprite = pn;
        int[] shade = l.shade();
        shadeSprite = l.needsShade() ? sprite(t, l.shadePixels(), shade[2], shade[3]) : null;
        int[] art = l.panelRect(RspsLoginLayout.BUTTON_ART);
        buttonHoverSprite = sprite(t, RspsLoginArt.scale(buttonHover, art[2], art[3]), art[2], art[3]);
        buttonPressedSprite = sprite(t, RspsLoginArt.scale(buttonPressed, art[2], art[3]), art[2], art[3]);
        buttonDisabledSprite = sprite(t, RspsLoginArt.scale(buttonDisabled, art[2], art[3]), art[2], art[3]);
        int[] rec = l.panelRect(RspsLoginLayout.RECOVER);
        recoverHoverSprite = sprite(t, RspsLoginArt.scale(recoverHover, rec[2], rec[3]), rec[2], rec[3]);
        int[] cre = l.panelRect(RspsLoginLayout.CREATE);
        createHoverSprite = sprite(t, RspsLoginArt.scale(createHover, cre[2], cre[3]), cre[2], cre[3]);
        built = l;
    }

    private static Sprite sprite(Toolkit t, int[] argb, int w, int h) {
        if (argb == null) {
            return null;
        }
        try {
            return t.createSprite(w, w, h, argb);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void release() {
        bgSprite = logoSprite = panelSprite = shadeSprite = null;
        buttonHoverSprite = buttonPressedSprite = buttonDisabledSprite = recoverHoverSprite = createHoverSprite = null;
        built = null;
        pending = null;
    }

    /** Toolkit switch or shutdown: rebuilt on the next frame. */
    public static void reset() {
        release();
        builtFor = null;
    }

    private static void load() {
        background = read("background");
        logo = read("logo");
        panel = read("panel");
        if (panel == null) {
            return;
        }
        BufferedImage button = RspsLoginArt.crop(panel, RspsLoginLayout.BUTTON_ART, RspsLoginLayout.PANEL_X, RspsLoginLayout.PANEL_Y);
        int[] shape = RspsLoginLayout.BUTTON;
        int sx = shape[0] - RspsLoginLayout.BUTTON_ART[0], sy = shape[1] - RspsLoginLayout.BUTTON_ART[1];
        buttonHover = RspsLoginArt.buttonState(button, sx, sy, shape[2] - shape[0], shape[3] - shape[1], RspsLoginArt.State.HOVER);
        buttonPressed = RspsLoginArt.buttonState(button, sx, sy, shape[2] - shape[0], shape[3] - shape[1], RspsLoginArt.State.PRESSED);
        buttonDisabled = RspsLoginArt.buttonState(button, sx, sy, shape[2] - shape[0], shape[3] - shape[1], RspsLoginArt.State.DISABLED);
        recoverHover = RspsLoginArt.linkHover(RspsLoginArt.crop(panel, RspsLoginLayout.RECOVER, RspsLoginLayout.PANEL_X, RspsLoginLayout.PANEL_Y));
        createHover = RspsLoginArt.linkHover(RspsLoginArt.crop(panel, RspsLoginLayout.CREATE, RspsLoginLayout.PANEL_X, RspsLoginLayout.PANEL_Y));
    }

    private static BufferedImage read(String name) {
        try (InputStream in = RspsLoginScreen.class.getResourceAsStream("/login78/" + name + ".png")) {
            if (in == null) {
                return null;
            }
            BufferedImage png = ImageIO.read(in);
            return png == null ? null : RspsLoginArt.premultiplied(png);
        } catch (Exception e) {
            return null;
        }
    }

    /** Offscreen check: the three layers composed by {@link RspsLoginLayout} with Java2D, for previews. */
    static BufferedImage compose(RspsLoginLayout l, BufferedImage bg, BufferedImage lg, BufferedImage pn) {
        BufferedImage out = new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        draw(g, RspsLoginArt.scale(bg, l.bgW, l.bgH), l.bgX, l.bgY, l.bgW, l.bgH);
        if (l.needsShade()) {
            int[] s = l.shade();
            draw(g, l.shadePixels(), s[0], s[1], s[2], s[3]);
        }
        draw(g, RspsLoginArt.scale(lg, l.logoW(), l.logoH()), l.logoX(), l.logoY(), l.logoW(), l.logoH());
        draw(g, RspsLoginArt.scale(pn, l.panelW(), l.panelH()), l.panelX(), l.panelY(), l.panelW(), l.panelH());
        g.dispose();
        return out;
    }

    private static void draw(Graphics2D g, int[] argb, int x, int y, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        g.drawImage(img, x, y, null);
    }
}
