import com.jagex.game.runetek6.config.defaults.GraphicsDefaults;
import com.jagex.game.runetek6.config.iftype.SubInterface;
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
 * <p>The login itself stays the cache interfaces - the login shell ({@link GraphicsDefaults#login_interface}, 744)
 * and the login form it opens as a sub-interface (596) - with all their clientscripts: username/password fields (VARCSTR 32/33, focus VARC 174, masking, Enter), the Log In button
 * (script 2944 -> 2945 -> LOGIN_REQUEST), Recover Password (47), Create Account (61) and the status/error
 * boxes (8, 9). Nothing of the networking or authentication changes. This class only
 * <ul>
 *   <li>draws the Deadman landscape and the approved transparent flag/panel art
 *       ({@code /login78/*.png}) with {@link RspsLoginLayout}, before the interface;</li>
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

    /**
     * The login form, interface 596. It is not the top level: the cache's login shell
     * ({@link GraphicsDefaults#login_interface}, 744 in this cache) opens it as a sub-interface.
     */
    static final int FORM_INTERFACE = 596;
    static final int EXPECTED_COMPONENTS = 90;
    static final int SHELL_COMPONENTS = 135;

    /** Components of interface 596 used here (see the class comment for their roles). */
    static final int FORM_CONTAINER = 6, FORM_FRAME = 4, FORM = 5, STATUS_LAYER = 7, STATUS_BOX = 8, STATUS_SMALL = 9;
    static final int USERNAME = 40, PASSWORD = 42, LOGIN_BUTTON = 45, RECOVER = 47, CREATE = 61;
    static final int USERNAME_TEXT_LAYER = 72, USERNAME_TEXT = 73, USERNAME_CARET = 74;
    static final int PASSWORD_TEXT_LAYER = 78, PASSWORD_TEXT = 79, PASSWORD_CARET = 80;
    static final int STATUS_BUTTON_A = 15, STATUS_BUTTON_B = 63;

    /** Shell (744) message boxes and their buttons: restyled like the form's status boxes. */
    static final int SHELL_BOX_A = 51, SHELL_BOX_B = 52, SHELL_BUTTON_A = 56, SHELL_BUTTON_B = 57;

    /** The shell's old background layer (tiles 8-15), drawn right after its full-screen black rectangle (20). */
    static final int SHELL_BACKGROUND = 21;

    /**
     * The shell's 2011 look: the background art tiles (8-15), the
     * RuneScape logo (23), the screen frame (22, 96-102) and the message boxes' frame and button sprites.
     * Its working parts (sound buttons 81, message texts and buttons, dialog 28, the bottom bar 103) stay. The login
     * scene (19, the animated background) and the shell's black backdrop (20, animated background off) stay; the art
     * is drawn over both - after the scene ({@link #drawOverScene}) and in place of the old background layer
     * ({@link #SHELL_BACKGROUND}).
     */
    private static final int[] SHELL_HIDDEN = {
        0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 22, 23, 58, 59, 60, 62, 63, 64,
        67, 68, 69, 70, 71, 72, 73, 74, 75, 96, 97, 98, 99, 100, 101, 102
    };
    private static final boolean[] SHELL_HIDDEN_SET = new boolean[SHELL_COMPONENTS];

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
        for (int id : SHELL_HIDDEN) {
            SHELL_HIDDEN_SET[id] = true;
        }
    }

    /** Typed text matches the foreground's ivory lettering. */
    static final int TEXT_COLOUR = 0xF7F0E4;

    private static final int FOCUS_OUTER = 0x70FF2DA7;
    private static final int FOCUS_INNER = 0xFFFF75CD;
    private static final int STATUS_FILL = 0xEC121318;
    private static final int STATUS_EDGE = 0xFFFFD65B;
    private static final int STATUS_EDGE_DARK = 0xFF33333B;
    private static final int STATUS_BUTTON_FILL = 0xFF9B681A;
    private static final int STATUS_BUTTON_HOVER = 0xFFCC9230;

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

    private static Component[] components(int id) {
        return InterfaceList.interfaces == null || id < 0 || id >= InterfaceList.interfaces.length ? null : InterfaceList.interfaces[id];
    }

    /** Set after any failure: the stock login screen shows again instead of a broken one. */
    private static boolean failed;
    private static boolean reportedActive;

    /** One line to the client's error stream (the launcher keeps it in launcher-logs/client-*.err.log). */
    static void log(String message) {
        System.err.println("[78 login] " + message);
    }

    private static void fail(String where, Throwable t) {
        if (!failed) {
            failed = true;
            log("disabled after an error in " + where + ": " + t);
            t.printStackTrace();
            InterfaceManager.redrawAll();
        }
    }

    public static boolean hides(Component c) {
        try {
            return !failed && hidesImpl(c);
        } catch (Throwable t) {
            fail("hides", t);
            return false;
        }
    }

    public static void adjust(Component c) {
        try {
            if (!failed) {
                adjustImpl(c);
            }
        } catch (Throwable t) {
            fail("adjust", t);
        }
    }

    /** Returns true when it drew the full-screen art (the caller then restores its clip). */
    public static boolean beforeComponent(Component c, int x, int y) {
        try {
            if (failed) {
                return false;
            }
            if (isShell(c) && index(c) == SHELL_BACKGROUND) {
                drawBackdropImpl(lastOriginX, lastOriginY);
                return true;
            }
            beforeComponentImpl(c, x, y);
            return false;
        } catch (Throwable t) {
            fail("beforeComponent", t);
            return false;
        }
    }

    private static int lastOriginX, lastOriginY;

    public static void drawBackdrop(int originX, int originY) {
        try {
            if (!failed) {
                lastOriginX = originX;
                lastOriginY = originY;
                drawBackdropImpl(originX, originY);
            }
        } catch (Throwable t) {
            fail("drawBackdrop", t);
        }
    }

    /**
     * {@link InterfaceManager#draw}, right after the shell's login scene (animated background on): the art over
     * the scene. Returns true when it drew (the caller then restores its clip).
     */
    public static boolean drawOverScene() {
        try {
            if (failed || !active()) {
                return false;
            }
            drawBackdropImpl(lastOriginX, lastOriginY);
            return true;
        } catch (Throwable t) {
            fail("drawOverScene", t);
            return false;
        }
    }
    /** The login shell is the top level, has the layout this class was written for, and the art exists. */
    public static boolean active() {
        if (failed) {
            return false;
        }
        int id = loginInterface();
        if (id < 0 || InterfaceManager.topLevelInterface != id) {
            return false;
        }
        Component[] shell = components(id);
        return shell != null && shell.length == SHELL_COMPONENTS && artLoaded();
    }

    private static boolean formLoaded() {
        Component[] form = components(FORM_INTERFACE);
        return form != null && form.length == EXPECTED_COMPONENTS;
    }

    private static boolean isShell(Component c) {
        return c != null && c.slot != -1 && (c.slot >>> 16) == loginInterface() && active();
    }

    private static boolean isForm(Component c) {
        return c != null && c.slot != -1 && (c.slot >>> 16) == FORM_INTERFACE && active() && formLoaded();
    }

    private static int index(Component c) {
        return c.slot & 0xFFFF;
    }

    /** Shell component that holds the form as a sub-interface, or null. */
    private static Component formContainer() {
        Component[] shell = components(loginInterface());
        if (shell == null) {
            return null;
        }
        for (Component c : shell) {
            if (c != null && isFormContainer(c)) {
                return c;
            }
        }
        return null;
    }

    private static boolean isFormContainer(Component c) {
        SubInterface sub = (SubInterface) InterfaceManager.subInterfaces.get(c.slot);
        return sub != null && sub.id == FORM_INTERFACE;
    }

    /** Screen x/y (relative to the interface origin) of a component, through its parents and sub-interface container. */
    private static int[] absolute(Component c) {
        int x = 0, y = 0;
        for (int guard = 0; c != null && guard < 64; guard++) {
            x += c.x;
            y += c.y;
            Component parent;
            if (c.layer != -1) {
                Component[] owner = components(c.layer >>> 16);
                parent = owner == null ? null : owner[c.layer & 0xFFFF];
            } else if ((c.slot >>> 16) == FORM_INTERFACE) {
                parent = formContainer();
            } else {
                parent = null;
            }
            if (parent != null && parent.type == Component.TYPE_LAYER) {
                x -= parent.scrollX;
                y -= parent.scrollY;
            }
            c = parent;
        }
        return new int[] {x, y};
    }

    // ------------------------------------------------------------------ interface hooks

    /** {@link InterfaceManager#isHidden}: the 2011 decoration is never drawn nor clickable. */
    private static boolean hidesImpl(Component c) {
        if (isShell(c)) {
            int id = index(c);
            return id < SHELL_HIDDEN_SET.length && SHELL_HIDDEN_SET[id];
        }
        if (isForm(c)) {
            int id = index(c);
            return id < HIDDEN_SET.length && HIDDEN_SET[id];
        }
        return false;
    }

    /** {@link InterfaceManager#reposition} (after the cache layout): puts the working components on the art. */
    private static void adjustImpl(Component c) {
        if (isShell(c)) {
            if (isFormContainer(c)) {
                // The form's container spans the whole screen, so the panel is never clipped by it.
                Component[] shell = components(loginInterface());
                Component parent = c.layer == -1 ? null : shell[c.layer & 0xFFFF];
                int[] p = parent == null ? new int[] {0, 0} : absolute(parent);
                set(c, -p[0], -p[1], GameShell.canvasWid, GameShell.canvasHei);
            }
            return;
        }
        if (!isForm(c)) {
            return;
        }
        RspsLoginLayout l = layout();
        int[] body = l.panelRect(RspsLoginLayout.BODY);
        int inset = l.panelLength(RspsLoginLayout.FIELD_TEXT_INSET);
        switch (index(c)) {
            case FORM_CONTAINER: {
                Component container = formContainer();
                int[] origin = container == null ? new int[] {0, 0} : absolute(container);
                set(c, body[0] - origin[0], body[1] - origin[1], body[2], body[3]);
                break;
            }
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
                // Script 3237 places the caret at the text layer's x + 6 + the typed width, so once it has run the
                // position already includes the icon inset; only the cache default (6) still needs it.
                if (c.originalX < inset) {
                    c.x = c.originalX + inset - 6;
                }
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
        Component parent = components(FORM_INTERFACE)[STATUS_LAYER];
        int[] p = parent == null ? new int[] {0, 0} : absolute(parent);
        c.x = body[0] + (body[2] - c.width) / 2 - p[0];
        c.y = body[1] + (body[3] - c.height) / 2 - p[1];
    }

    /** {@link InterfaceManager#draw}: the frame behind the status boxes and their buttons. */
    private static void beforeComponentImpl(Component c, int x, int y) {
        boolean shell = isShell(c);
        if (!shell && !isForm(c)) {
            return;
        }
        int id = index(c);
        if (shell) {
            // The shell's message boxes and buttons share the form's status styling.
            id = id == SHELL_BOX_A || id == SHELL_BOX_B ? STATUS_BOX : id == SHELL_BUTTON_A || id == SHELL_BUTTON_B ? STATUS_BUTTON_A : -1;
        }
        Toolkit t = Toolkit.active;
        switch (id) {
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
    private static void drawBackdropImpl(int originX, int originY) {
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
        if (!reportedActive) {
            reportedActive = true;
            Component container = formContainer();
            Component[] form = components(FORM_INTERFACE);
            String formInfo = "form " + (form == null ? "not loaded" : form.length + " components");
            if (formLoaded()) {
                int[] a6 = absolute(form[FORM_CONTAINER]);
                int[] a40 = absolute(form[USERNAME]);
                int[] a45 = absolute(form[LOGIN_BUTTON]);
                formInfo += ", panel body at " + a6[0] + "," + a6[1] + " " + form[FORM_CONTAINER].width + "x" + form[FORM_CONTAINER].height
                    + ", username at " + a40[0] + "," + a40[1] + ", button at " + a45[0] + "," + a45[1]
                    + ", hidden 6/40/45: " + form[FORM_CONTAINER].hidden + "/" + form[USERNAME].hidden + "/" + form[LOGIN_BUTTON].hidden;
            }
            log("active: toolkit " + t.getClass().getSimpleName() + ", canvas " + GameShell.canvasWid + "x" + GameShell.canvasHei
                + ", origin " + originX + "," + originY + ", sprites " + (bgSprite != null) + "/" + (logoSprite != null) + "/" + (panelSprite != null)
                + ", container " + (container == null ? "none" : (container.slot & 0xFFFF) + " " + container.width + "x" + container.height)
                + ", " + formInfo);
        }
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
        log("build " + l.width + "x" + l.height + (l.compact ? " compact" : "") + ": background " + (bg != null) + ", logo " + (lg != null) + ", panel " + (pn != null));
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
            log("sprite " + w + "x" + h + " failed: " + e);
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
        BufferedImage foreground = read("foreground");
        if (foreground != null && foreground.getWidth() == RspsLoginLayout.REF_W
            && foreground.getHeight() == RspsLoginLayout.REF_H) {
            logo = foreground.getSubimage(RspsLoginLayout.LOGO_X, RspsLoginLayout.LOGO_Y,
                RspsLoginLayout.LOGO_W, RspsLoginLayout.LOGO_H);
            panel = foreground.getSubimage(RspsLoginLayout.PANEL_X, RspsLoginLayout.PANEL_Y,
                RspsLoginLayout.PANEL_W, RspsLoginLayout.PANEL_H);
        }
        log("art loaded: background " + (background != null) + ", logo " + (logo != null) + ", panel " + (panel != null));
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
