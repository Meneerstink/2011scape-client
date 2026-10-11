package com.jagex;

import com.jagex.game.runetek6.client.GameShell;
import com.jagex.graphics.Toolkit;
import jaggl.OpenGL;

/**
 * RuneLite's Stretched Mode plugin (owner 2026-10-11, bug 012046: "we need the runelite stretched plugin"), ported from
 * RuneLite: StretchedModePlugin / StretchedModeConfig (config group "stretchedmode": keepAspectRatio, increasedPerformance,
 * integerScaling, scalingFactor 50%) and the client side it drives (OpenOSRS StretchedModeMixin getRealDimensions /
 * getStretchedDimensions / invalidateStretching, StretchedModeMaxSizeMixin, Hooks.draw and GpuPlugin's stretched blit,
 * TranslateMouseListener).
 *
 * The game runs at the "real" size - 765x503 in fixed mode, the window divided by the Resizable scaling in resizable mode -
 * and every frame is stretched over the window: OpenGL blits its frame through a framebuffer ({@link #glPresent}), the
 * software toolkit draws its image scaled. Mouse positions are scaled back to the real size ({@link #mouseX}).
 *
 * The settings come from the 78 Plugins sidebar (RspsHudConfig, saved with the account on the server), which calls
 * {@link #configure} - this class lives in a package so the mouse monitors can reach it.
 */
public final class StretchedMode {

    /** RuneLite Constants.GAME_FIXED_WIDTH / HEIGHT. */
    private static final int FIXED_WIDTH = 765;
    private static final int FIXED_HEIGHT = 503;
    /**
     * RuneLite never makes the resizable game smaller than OSRS's 765x503, the size OSRS's resizable layout fits in. The
     * 667 resizable layout (interface 746) is taller: below ~1000 px wide it puts the tabs in two rows, and the side panel
     * with both tab rows (345 px) then needs the minimap and its orbs (174 px) above it - measured 2026-10-11 in an
     * unstretched 850x561 client. At 503 px the panel covered the summoning orb and the world map button (owner A2:
     * "resizable: interfaces too big / cut off"), so the stretched resizable game is never lower than this.
     */
    static final int RESIZABLE_MIN_HEIGHT = 520;

    private static boolean enabled;
    private static boolean keepAspectRatio;
    private static boolean fast;
    private static boolean integerScaling;
    /** RuneLite setScalingFactor: 1 + percent / 100 (default 50% = 1.5). */
    private static double scalingFactor = 1.5;

    /** The game (real) size and the size it is drawn at on screen; equal while stretching is off. */
    private static int realWidth;
    private static int realHeight;
    private static int stretchedWidth;
    private static int stretchedHeight;

    private StretchedMode() {
    }

    /** New settings: true when anything changed (the caller then has the window laid out again). */
    public static boolean configure(boolean on, boolean keepAspect, boolean increasedPerformance, boolean integer, int scalingPercent) {
        double factor = 1 + scalingPercent / 100D;
        boolean changed = on != enabled || keepAspect != keepAspectRatio || increasedPerformance != fast || integer != integerScaling || factor != scalingFactor;
        if (on != enabled) {
            softwareSwap = true;
        }
        enabled = on;
        keepAspectRatio = keepAspect;
        fast = increasedPerformance;
        integerScaling = integer;
        scalingFactor = factor;
        if (changed) {
            Client.changingWindowMode = true;
        }
        // RuneLite never sizes the window for a setting (owner 2026-10-11: the maximising added that afternoon was wrong). The
        // window the player had is brought back by RspsHudConfig.applyStretchedMode (RuneLite rememberScreenBounds).
        return changed;
    }

    /**
     * RuneLite's only software renderer is its Java one, and it stretches. The 2011 "Software" choice runs the native SSE
     * renderer when it can, which draws straight into the canvas and cannot stretch, so Stretched Mode did nothing in Software
     * (night run 2026-10-12). While stretching is on, Software runs the Java software renderer instead; the player's choice
     * stays "Software" (Static595.setToolkit keeps SSE stored) and SSE comes back when stretching goes off.
     */
    public static int rendererFor(int requested) {
        return requested == com.jagex.graphics.ToolkitType.SSE && enabled ? com.jagex.graphics.ToolkitType.JAVA : requested;
    }

    /** Stretching went on or off: the game thread checks whether Software has to change renderer (client loop). */
    private static volatile boolean softwareSwap;

    /** True once after stretching went on or off (configure runs on the event thread as well as the game thread). */
    public static boolean takeSoftwareSwap() {
        boolean swap = softwareSwap;
        softwareSwap = false;
        return swap;
    }

    /**
     * The active toolkit can draw a stretched frame: RuneLite has only its software renderer (Hooks.draw) and its GPU plugin
     * (an offscreen framebuffer); here that is the Java toolkit and OpenGL with framebuffer objects + blit. DirectX, SSE and
     * the GLX toolkit draw the real-size frame straight into the canvas, so stretching them cut the picture off while the
     * mouse was still scaled (clicks landed next to what was shown) - for them stretching stays off.
     */
    private static boolean toolkitCanStretch = true;

    /** A new main toolkit was made (Static595.setToolkit, which lays the window out again afterwards). */
    public static void toolkitChanged(boolean canStretch) {
        toolkitCanStretch = canStretch;
        Client.changingWindowMode = true;
    }

    /** The plugin is switched on (whether or not the current toolkit can stretch). */
    public static boolean enabled() {
        return enabled;
    }

    /**
     * OpenGL makes its window without multisampling while stretching: glBlitFramebuffer cannot write into a multisampled
     * window (INVALID_OPERATION - a black screen with the game's Anti-aliasing on), and RuneLite's GPU plugin keeps its
     * window plain as well. The player's own anti-aliasing setting is kept and comes back when stretching goes off.
     */
    public static boolean glPlainWindow() {
        return enabled && !glFailed && !glNoFramebuffers;
    }

    /** The GL driver has no framebuffer objects / blit (stays so for this run): OpenGL is never stretched. */
    public static void glUnsupported() {
        glNoFramebuffers = true;
    }

    /** Stretching is on and the window is laid out stretched. */
    public static boolean active() {
        return enabled && toolkitCanStretch && !glFailed && stretchedWidth > 0 && (stretchedWidth != realWidth || stretchedHeight != realHeight);
    }

    /**
     * Stretching is switched on and the current toolkit can draw it: the game is stretched over whatever size the window has,
     * so the client must not size the window to the game (InterfaceManager.changeWindowMode).
     */
    public static boolean fillsWindow() {
        return enabled && toolkitCanStretch && !glFailed;
    }

    /**
     * Called after the game picked its canvas size and margins for a window of frameWid x frameHei (fixed or resizable):
     * replaces them by RuneLite's real size and works out the stretched size the canvas is drawn at.
     */
    public static void layout(boolean fixed) {
        layoutSizes(fixed);
        // One line per change, for every renderer (glPresent only logs OpenGL): the owner's log shows why it does not stretch.
        String state = "stretch layout " + (fixed ? "fixed" : "resizable") + " enabled=" + enabled + " toolkitCanStretch=" + toolkitCanStretch
            + " glFailed=" + glFailed + " toolkit=" + (Toolkit.active == null ? "none" : Toolkit.active.getClass().getSimpleName())
            + " window=" + GameShell.frameWid + "x" + GameShell.frameHei + " real=" + realWidth + "x" + realHeight + " drawn=" + stretchedWidth + "x" + stretchedHeight;
        if (!state.equals(lastLayout)) {
            lastLayout = state;
            System.out.println("[78] " + state);
        }
    }

    private static String lastLayout = "";

    private static void layoutSizes(boolean fixed) {
        if (!enabled || !toolkitCanStretch || glFailed) {
            realWidth = stretchedWidth = GameShell.canvasWid;
            realHeight = stretchedHeight = GameShell.canvasHei;
            return;
        }
        int parentWidth = GameShell.frameWid;
        int parentHeight = GameShell.frameHei;
        if (parentWidth <= 0 || parentHeight <= 0) {
            realWidth = stretchedWidth = GameShell.canvasWid;
            realHeight = stretchedHeight = GameShell.canvasHei;
            return;
        }
        // getRealDimensions (RuneLite StretchedModeMixin, exactly; owner 2026-10-11 "net als de officiele versie"). Fixed: the
        // game is 765x503 and Resizable scaling does nothing ("In resizable mode, ..."). Resizable: the parent divided by the
        // scaling factor, never below the minimum game size.
        if (fixed) {
            realWidth = FIXED_WIDTH;
            realHeight = FIXED_HEIGHT;
        } else {
            int newWidth = (int) (parentWidth / scalingFactor);
            int newHeight = (int) (parentHeight / scalingFactor);
            // The floor never exceeds the window: RuneLite stretches, it never shrinks the game into a smaller window (night run
            // 2026-10-12: a 765x503 window drew the 791x520 game shrunk, and with Increased performance letters fell out of the
            // chat text). A window lower than 520 px shows the game 1:1, exactly as without the plugin.
            int minWidth = Math.min(FIXED_WIDTH, parentWidth);
            int minHeight = Math.min(RESIZABLE_MIN_HEIGHT, parentHeight);
            if (newWidth < minWidth || newHeight < minHeight) {
                double factor = Math.min((double) parentWidth / minWidth, (double) parentHeight / minHeight);
                // Rounded up: a truncated 502 px left the game a pixel under its minimum.
                newWidth = Math.max(minWidth, (int) Math.ceil(parentWidth / factor));
                newHeight = Math.max(minHeight, (int) Math.ceil(parentHeight / factor));
            }
            realWidth = newWidth;
            realHeight = newHeight;
        }
        // getStretchedDimensions (RuneLite, exactly): the whole parent, then Keep aspect ratio, then Integer scaling per side.
        if (keepAspectRatio) {
            double aspectRatio = (double) realWidth / realHeight;
            int tempNewWidth = (int) (parentHeight * aspectRatio);
            if (tempNewWidth > parentWidth) {
                parentHeight = (int) (parentWidth / aspectRatio);
            } else {
                parentWidth = tempNewWidth;
            }
        }
        if (integerScaling) {
            if (parentWidth > realWidth) {
                parentWidth = parentWidth - (parentWidth % realWidth);
            }
            if (parentHeight > realHeight) {
                parentHeight = parentHeight - (parentHeight % realHeight);
            }
        }
        stretchedWidth = parentWidth;
        stretchedHeight = parentHeight;
        GameShell.canvasWid = realWidth;
        GameShell.canvasHei = realHeight;
        // The stretched canvas is centred in the window (RuneLite: the canvas fills its parent; centred when the aspect is kept).
        GameShell.leftMargin = Math.max(0, (GameShell.frameWid - stretchedWidth) / 2);
        GameShell.topMargin = Math.max(0, (GameShell.frameHei - stretchedHeight) / 2);
    }

    /**
     * The size the toolkit renders [canvas] at: the real (game) size for the stretched game canvas, else its AWT size. Every
     * place that sized a toolkit surface from {@code canvas.getSize()} asks this instead.
     */
    public static java.awt.Dimension renderSize(java.awt.Canvas canvas) {
        if (active() && canvas == GameShell.canvas) {
            return new java.awt.Dimension(realWidth, realHeight);
        }
        return canvas.getSize();
    }

    /** Width the AWT canvas gets: the stretched width while stretching, else the game's own canvas width. */
    public static int canvasWidth() {
        return active() ? stretchedWidth : GameShell.canvasWid;
    }

    public static int canvasHeight() {
        return active() ? stretchedHeight : GameShell.canvasHei;
    }

    public static int realWidth() {
        return realWidth;
    }

    public static int realHeight() {
        return realHeight;
    }

    public static int stretchedWidth() {
        return stretchedWidth;
    }

    public static int stretchedHeight() {
        return stretchedHeight;
    }

    /** RuneLite "Increased performance mode": nearest neighbour instead of bilinear. */
    public static boolean fast() {
        return fast;
    }

    /**
     * TranslateMouseListener: a canvas x position in real (game) pixels. Scaled by the canvas's own size - the area the frame
     * is drawn over ({@link #glPresent}, JavaDefaultSurface) - so the mouse and the picture always use the same scale, also
     * between a window resize and the next layout.
     */
    public static int mouseX(int x) {
        return active() ? (int) (x / ((double) drawnWidth() / realWidth)) : x;
    }

    public static int mouseY(int y) {
        return active() ? (int) (y / ((double) drawnHeight() / realHeight)) : y;
    }

    /** The width the frame is drawn at on screen: the canvas's own width. */
    public static int drawnWidth() {
        java.awt.Canvas canvas = GameShell.canvas;
        return canvas != null && canvas.getWidth() > 0 ? canvas.getWidth() : stretchedWidth;
    }

    public static int drawnHeight() {
        java.awt.Canvas canvas = GameShell.canvas;
        return canvas != null && canvas.getHeight() > 0 ? canvas.getHeight() : stretchedHeight;
    }

    // ---- OpenGL (RuneLite GpuPlugin) ------------------------------------------------------------------------------------
    //
    // The game draws into an offscreen framebuffer at its real size, bound as the toolkit's "window"; every flip blits it
    // over the stretched canvas. Before (until 2026-10-11) the frame was drawn into the window's own back buffer and copied
    // out of it, which broke three ways: a window smaller than the game (78 Plugins sidebar open: 765x503 into 523 px) has
    // no pixels for the rest of the frame - black / cut off parts and clicks on things not shown; a swap leaves the back
    // buffer undefined, so the client's partial redraws landed on stale stretched pixels - flicker; and pixels of the window
    // covered by another window are not owned by GL - black areas. The offscreen buffer keeps the whole real frame from one
    // flip to the next, like the window buffer the 2011 client expects.

    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_RENDERBUFFER = 0x8D41;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;
    private static final int GL_COLOR_BUFFER_BIT = 0x4000;
    private static final int GL_NEAREST = 0x2600;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_SCISSOR_TEST = 0x0C11;

    private static int framebuffer;
    private static int colourBuffer;
    private static int depthBuffer;
    private static int bufferWidth;
    private static int bufferHeight;
    /** The offscreen framebuffer is bound as the toolkit's window. */
    private static boolean bound;
    /** The driver refused the offscreen framebuffer: stretching stays off with OpenGL (for this run - the driver will not change). */
    private static boolean glFailed;
    /** The driver lacks GL_EXT_framebuffer_object / GL_EXT_framebuffer_blit (for this run). */
    private static boolean glNoFramebuffers;

    /**
     * The framebuffer that stands for the window: the offscreen one while stretching, else the window itself (0). Every
     * place the toolkit returns from one of its own render targets to "the window" binds this.
     */
    public static int glWindow() {
        return bound ? framebuffer : 0;
    }

    /** The real frame lives in the offscreen framebuffer (it survives swaps; nothing has to be copied back). */
    public static boolean glOwnsFrame() {
        return bound;
    }

    /** The GL toolkit could not make the offscreen framebuffer: stretching is off for it. */
    public static boolean glFailed() {
        return glFailed;
    }

    /**
     * Matches the offscreen framebuffer to the layout: bound at the toolkit's surface size [width] x [height] while
     * stretching, dropped when stretching goes off. Called after every resize of the GL surface and at every flip, before
     * the client draws its next frame, with none of the toolkit's own render targets pushed. True when the buffer the
     * client draws into changed, so every interface has to be drawn again.
     */
    public static boolean glSync(boolean supported, int width, int height) {
        boolean want = supported && !glFailed && active() && width > 0 && height > 0;
        if (!want) {
            if (!bound) {
                return false;
            }
            OpenGL.glBindFramebufferEXT(GL_FRAMEBUFFER, 0);
            deleteBuffer();
            return true;
        }
        if (bound && bufferWidth == width && bufferHeight == height) {
            return false;
        }
        if (!createBuffer(width, height)) {
            // No offscreen buffer on this driver: lay the window out unstretched instead of drawing a broken picture.
            glFailed = true;
            System.out.println("[78] stretch: offscreen framebuffer incomplete at " + width + "x" + height + " - stretching off");
            Client.changingWindowMode = true;
            return true;
        }
        OpenGL.glBindFramebufferEXT(GL_FRAMEBUFFER, framebuffer);
        bound = true;
        return true;
    }

    private static String lastPresent = "";

    /**
     * Before swapBuffers: blits the real frame over the whole canvas ([canvasWidth] x [canvasHeight], its drawable), then
     * binds the offscreen framebuffer again for the next frame. The scissor test clips blits, so it is off meanwhile.
     */
    public static void glPresent(boolean scissor, int canvasWidth, int canvasHeight) {
        String state = "stretch active=" + active() + " offscreen=" + bound + " real=" + bufferWidth + "x" + bufferHeight + " drawn=" + canvasWidth + "x" + canvasHeight;
        if (!state.equals(lastPresent)) {
            lastPresent = state;
            System.out.println("[78] " + state);
        }
        if (!bound || canvasWidth <= 0 || canvasHeight <= 0) {
            return;
        }
        if (scissor) {
            OpenGL.glDisable(GL_SCISSOR_TEST);
        }
        OpenGL.glBindFramebufferEXT(GL_READ_FRAMEBUFFER, framebuffer);
        OpenGL.glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER, 0);
        OpenGL.glBlitFramebufferEXT(0, 0, bufferWidth, bufferHeight, 0, 0, canvasWidth, canvasHeight, GL_COLOR_BUFFER_BIT, fast ? GL_NEAREST : GL_LINEAR);
        OpenGL.glBindFramebufferEXT(GL_FRAMEBUFFER, framebuffer);
        if (scissor) {
            OpenGL.glEnable(GL_SCISSOR_TEST);
        }
    }

    private static boolean createBuffer(int width, int height) {
        int[] ids = new int[1];
        if (framebuffer == 0) {
            OpenGL.glGenFramebuffersEXT(1, ids, 0);
            framebuffer = ids[0];
            OpenGL.glGenRenderbuffersEXT(1, ids, 0);
            colourBuffer = ids[0];
            OpenGL.glGenRenderbuffersEXT(1, ids, 0);
            depthBuffer = ids[0];
        }
        OpenGL.glBindRenderbufferEXT(GL_RENDERBUFFER, colourBuffer);
        OpenGL.glRenderbufferStorageEXT(GL_RENDERBUFFER, GL_RGBA8, width, height);
        // The 3D scene needs a depth buffer like the window's (the client uses no stencil).
        OpenGL.glBindRenderbufferEXT(GL_RENDERBUFFER, depthBuffer);
        OpenGL.glRenderbufferStorageEXT(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
        OpenGL.glBindRenderbufferEXT(GL_RENDERBUFFER, 0);
        OpenGL.glBindFramebufferEXT(GL_FRAMEBUFFER, framebuffer);
        OpenGL.glFramebufferRenderbufferEXT(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colourBuffer);
        OpenGL.glFramebufferRenderbufferEXT(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthBuffer);
        int status = OpenGL.glCheckFramebufferStatusEXT(GL_FRAMEBUFFER);
        OpenGL.glBindFramebufferEXT(GL_FRAMEBUFFER, 0);
        bufferWidth = width;
        bufferHeight = height;
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            deleteBuffer();
            return false;
        }
        return true;
    }

    private static void deleteBuffer() {
        int[] ids = new int[1];
        if (framebuffer != 0) {
            ids[0] = framebuffer;
            OpenGL.glDeleteFramebuffersEXT(1, ids, 0);
            ids[0] = colourBuffer;
            OpenGL.glDeleteRenderbuffersEXT(1, ids, 0);
            ids[0] = depthBuffer;
            OpenGL.glDeleteRenderbuffersEXT(1, ids, 0);
        }
        framebuffer = colourBuffer = depthBuffer = 0;
        bufferWidth = bufferHeight = 0;
        bound = false;
    }

    /** The GL context went away (toolkit change): its framebuffer ids are gone with it. */
    public static void glContextLost() {
        framebuffer = colourBuffer = depthBuffer = 0;
        bufferWidth = bufferHeight = 0;
        bound = false;
    }
}
