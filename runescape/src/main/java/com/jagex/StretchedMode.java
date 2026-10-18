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
     * RuneLite never makes the resizable game smaller than 765x503 (StretchedModeMixin.getRealDimensions, Constants
     * GAME_FIXED_HEIGHT). From 10-11 to 10-17 this was 520 (the 667 two-row side panel can cover the summoning orb / world map
     * button in a 503 px high game - the same picture an unstretched 503 px resizable window shows, the client's own minimum).
     * Owner 2026-10-17 (VRAGEN 23 C, "precies RuneLite", "nooit kleiner dan 765x503") and Keurmeester A2 round 2: RuneLite's
     * 503. On the owner's 944x581 window Resizable scaling now changes the game up to 16 % (was 12 %).
     */
    static final int RESIZABLE_MIN_HEIGHT = FIXED_HEIGHT;

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
     * OpenGL makes its window without multisampling whenever the driver has framebuffer objects: glBlitFramebuffer cannot
     * write into a multisampled window (INVALID_OPERATION - a black screen with the game's Anti-aliasing on), and RuneLite's
     * GPU plugin keeps its window plain as well. A2 round 8 review (nakijker FOUTEN 1): before, the window was plain only
     * while stretching, so with Stretched OFF and Anti-aliasing on the canvas still resized at every fixed <-> resizable
     * switch (black on the real screen) and switching Stretched on/off rebuilt the toolkit. The player's anti-aliasing now
     * lives in the offscreen framebuffer ({@link #glSamples}), the way RuneLite's GpuPlugin (initAAFbo + resolve blit) and
     * the 667 client's own post-processing (Class276: multisampled renderbuffers, glBlitFramebufferEXT 1:1 GL_NEAREST) do.
     */
    public static boolean glPlainWindow() {
        return !glFailed && !glNoFramebuffers;
    }

    /** Multisampling the OpenGL window is made with for the player's anti-aliasing [playerSamples] (0 = plain). */
    public static int glWindowSamples(int playerSamples) {
        return glPlainWindow() ? 0 : playerSamples;
    }

    /** Anti-aliasing samples of the offscreen frame (the player's setting while the window is plain; 0 = none). */
    private static int glSamples;

    /** The offscreen frame is multisampled with [samples] (the player's anti-aliasing; a plain window cannot be). */
    public static void glSamples(int samples) {
        int wanted = samples > 1 ? samples : 0;
        if (glSamples != wanted) {
            glSamples = wanted;
            Client.changingWindowMode = true;
        }
    }

    /** Tests / log: the anti-aliasing samples the current offscreen buffer was made with (0 = single-sample or none). */
    public static int glBufferSamples() {
        return bound ? bufferSamples : 0;
    }

    /** The GL driver has no framebuffer objects / blit (stays so for this run): OpenGL is never stretched. */
    public static void glUnsupported() {
        glNoFramebuffers = true;
    }

    // ---- A2 round 8 (Keurmeester A2 round 7 FAIL): with OpenGL the canvas always fills the window ----------------------------
    //
    // Every frame in which the window or the AWT canvas changed size was BLACK on the real screen (Windows shows a resized OpenGL
    // surface black until the next SwapBuffers): Stretched OFF fixed <-> resizable fitted the window (944x581 <-> 765x503) and
    // resized the canvas (maximised: 765x503 at 441,0 <-> 1648x989); Keep aspect / Integer scaling resized the canvas to the drawn
    // size. RuneLite never sizes the frame on a mode change (ClientUI.applyGameSize only from its own config) and its canvas fills
    // the parent; the fixed game is drawn inside it. So with OpenGL (framebuffer objects, a plain window) the canvas is ALWAYS the
    // window's game area: a frame that is not 1:1 over the whole canvas (fixed 765x503 in a bigger window, stretched, kept aspect,
    // integer scaled) is drawn offscreen at its real size and blitted into its rectangle (drawX, drawY, drawn size; the rest is
    // black). A mode switch then changes only what the blit draws - never the window, never the canvas.

    private static boolean glToolkit;
    private static boolean glPlainSurface;
    private static int drawX;
    private static int drawY;

    /** The active toolkit is OpenGL ([on]) and its window is not multisampled ([plain]: a blit cannot write a multisampled window). */
    public static void glToolkit(boolean on, boolean plain) {
        if (glToolkit != on || glPlainSurface != plain) {
            glToolkit = on;
            glPlainSurface = plain;
            Client.changingWindowMode = true;
        }
    }

    /** The OpenGL toolkit ([on]) with a plain window ([plain]) whose frames are drawn with [samples] anti-aliasing samples. */
    public static void glToolkit(boolean on, boolean plain, int samples) {
        glToolkit(on, plain);
        glSamples(on && plain ? samples : 0);
    }

    /** Tests: the OpenGL toolkit with a plain window ([on]) or another toolkit. */
    public static void glToolkit(boolean on) {
        glToolkit(on, true, 0);
    }

    /** The canvas fills the window's game area and the blit places the game in it (OpenGL, framebuffer objects, plain window). */
    public static boolean windowCanvas() {
        return glToolkit && glPlainSurface && toolkitCanStretch && !glFailed && !glNoFramebuffers;
    }

    /** Left / top of the drawn game inside the canvas (0 when the canvas is the drawn size). */
    public static int drawX() {
        return windowCanvas() ? drawX : 0;
    }

    public static int drawY() {
        return windowCanvas() ? drawY : 0;
    }

    /** The game frame is drawn offscreen and blitted: stretched, or not 1:1 over the whole window canvas. */
    static boolean drawsOffscreen() {
        if (active()) {
            return true;
        }
        if (!windowCanvas() || realWidth <= 0 || realHeight <= 0) {
            return false;
        }
        if (glSamples > 1) {
            return true; // anti-aliasing: the plain window has no samples, every frame is drawn multisampled offscreen
        }
        return drawX != 0 || drawY != 0 || realWidth != canvasWidth() || realHeight != canvasHeight();
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
        return enabled && toolkitCanStretch && !glFailed || windowCanvas();
    }

    /**
     * Called after the game picked its canvas size and margins for a window of frameWid x frameHei (fixed or resizable):
     * replaces them by RuneLite's real size and works out the stretched size the canvas is drawn at.
     */
    public static void layout(boolean fixed) {
        trackScalingWindow(fixed);
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
        layoutGameSizes(fixed);
        // A2 round 8: the drawn game's place = the margins the game (fixed: centred at the top) or the stretch (centred) picked;
        // with OpenGL the canvas itself then fills the window from its origin.
        drawX = GameShell.leftMargin;
        drawY = GameShell.topMargin;
        if (windowCanvas() && GameShell.frameWid > 0 && GameShell.frameHei > 0) {
            GameShell.leftMargin = 0;
            GameShell.topMargin = 0;
        } else {
            drawX = drawY = 0;
        }
    }

    private static void layoutGameSizes(boolean fixed) {
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
            long size = resizableSize(parentWidth, parentHeight, scalingFactor);
            realWidth = (int) (size >>> 32);
            realHeight = (int) size;
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
     * RuneLite getRealDimensions in resizable mode for a parent of [parentWidth] x [parentHeight]: the parent divided by the
     * scaling factor, never below the minimum game size (width in the high 32 bits, height in the low).
     */
    static long resizableSize(int parentWidth, int parentHeight, double factor) {
        int newWidth = (int) (parentWidth / factor);
        int newHeight = (int) (parentHeight / factor);
        // The floor never exceeds the window: RuneLite stretches, it never shrinks the game into a smaller window (night run
        // 2026-10-12: a 765x503 window drew the 791x520 game shrunk, and with Increased performance letters fell out of the
        // chat text). A window lower than 503 px shows the game 1:1, exactly as without the plugin.
        int minWidth = Math.min(FIXED_WIDTH, parentWidth);
        int minHeight = Math.min(RESIZABLE_MIN_HEIGHT, parentHeight);
        if (newWidth < minWidth || newHeight < minHeight) {
            double clamp = Math.min((double) parentWidth / minWidth, (double) parentHeight / minHeight);
            // Truncated like RuneLite ((int) (parent / factor)), then held on the floor (a truncated 502 px left the game a
            // pixel under its minimum). The 10-12 Math.ceil made the clamped size one pixel LARGER than the last step before it
            // (788x537: 3 % = 765x521, 4 % = 766x522 - the UI shrank again one step past the maximum; A2 round 2).
            newWidth = Math.max(minWidth, (int) (parentWidth / clamp));
            newHeight = Math.max(minHeight, (int) (parentHeight / clamp));
        }
        return ((long) newWidth << 32) | (newHeight & 0xFFFFFFFFL);
    }

    // ---- Resizable scaling maximum (owner 2026-10-17, VRAGEN 23 = "RuneLite + max") ------------------------------------
    //
    // RuneLite's spinner has no maximum: above the step where the game reaches its minimum size every value draws the same
    // picture (on the owner's 944x581 window everything from 12 % up, 150 % included). The owner chose RuneLite with a
    // maximum: the spinner stops at the last step that still changes the game for the current window, and follows the window
    // at once. The stored value is never rewritten by a window change (a smaller window shows the step in effect; the
    // player's own value comes back on a bigger window, like RuneLite keeps its config).

    /** The player's own window the resizable game is laid out in (not a fixed window the client fitted to 765x503). */
    private static int scalingWidth;
    private static int scalingHeight;
    private static volatile int scalingMax = Integer.MAX_VALUE;
    private static volatile Runnable scalingRangeListener;

    /** The 78 Plugins sidebar: told when the maximum changed (any thread; it refreshes on the event thread). */
    public static void onScalingRange(Runnable listener) {
        scalingRangeListener = listener;
    }

    /** The highest Resizable scaling percentage that still changes the game for the current window (no window yet: none). */
    public static int maxScalingPercent() {
        return scalingMax;
    }

    /** The first percentage whose resizable game size equals that of every larger percentage, for a parent of w x h. */
    public static int maxScalingPercent(int parentWidth, int parentHeight) {
        if (parentWidth <= 0 || parentHeight <= 0) {
            return Integer.MAX_VALUE;
        }
        long floor = resizableSize(parentWidth, parentHeight, Double.MAX_VALUE);
        // Above parent / minimum the game is always on its floor; 1000 % covers every window up to 7650x5200.
        for (int percent = 0; percent < 1000; percent++) {
            if (resizableSize(parentWidth, parentHeight, 1 + percent / 100D) == floor) {
                return percent;
            }
        }
        return 1000;
    }

    /** A layout for a window of frameWid x frameHei: the maximum follows the player's window. */
    private static void trackScalingWindow(boolean fixed) {
        int width = GameShell.frameWid;
        int height = GameShell.frameHei;
        // A fixed window without stretching is the client's own 765x503 fit, not the player's window: the last maximum stays.
        if (width <= 0 || height <= 0 || (fixed && !fillsWindow()) || (width == scalingWidth && height == scalingHeight)) {
            return;
        }
        scalingWidth = width;
        scalingHeight = height;
        int max = maxScalingPercent(width, height);
        if (max != scalingMax) {
            scalingMax = max;
            Runnable listener = scalingRangeListener;
            if (listener != null) {
                listener.run();
            }
        }
    }

    /**
     * The size the toolkit renders [canvas] at: the real (game) size for the stretched game canvas, else its AWT size. Every
     * place that sized a toolkit surface from {@code canvas.getSize()} asks this instead.
     */
    public static java.awt.Dimension renderSize(java.awt.Canvas canvas) {
        if (prerenderWidth > 0 && canvas == GameShell.canvas) {
            return new java.awt.Dimension(prerenderWidth, prerenderHeight);
        }
        if (drawsOffscreen() && canvas == GameShell.canvas) {
            return new java.awt.Dimension(realWidth, realHeight);
        }
        return canvas.getSize();
    }

    // ---- A2 round 7 part 2: the first frame of a window-mode switch, drawn offscreen (nakijker A2 round 7 FOUTEN 1) ---------
    //
    // A landed fixed <-> resizable switch with Stretched Mode OFF changes the canvas size (and the client may fit / restore the
    // window: back to a remembered maximised window). A resized OpenGL window is wiped, so resizing it before the first frame of
    // the new mode was drawn showed a black window for that whole draw (1468 ms seen on a loaded PC, maximised). Instead that
    // frame is drawn into the offscreen framebuffer at the canvas size the new mode will have (the RuneLite GPU plugin way, the
    // same buffer Stretched Mode draws into), the window keeps the old picture meanwhile, and only then the window and canvas
    // take the new size and the flip blits the finished frame (GameframeSwitch.beforeFlip). The next frame draws into the
    // window again.

    private static int prerenderWidth;
    private static int prerenderHeight;

    /** The game canvas renders at [width] x [height] offscreen until {@link #glPrerenderEnd}; false = not possible here. */
    public static boolean glPrerender(int width, int height) {
        if (glFailed || glNoFramebuffers || active() || width <= 0 || height <= 0) {
            return false;
        }
        prerenderWidth = width;
        prerenderHeight = height;
        return true;
    }

    /** Back to drawing into the window; true when an offscreen first frame was in use (the caller lays the window out again). */
    public static boolean glPrerenderEnd() {
        boolean was = prerenderWidth > 0;
        prerenderWidth = prerenderHeight = 0;
        return was;
    }

    /** The first frame of a switch is drawn offscreen (glPrerender). */
    public static boolean glPrerendering() {
        return prerenderWidth > 0;
    }

    /** Width the AWT canvas gets: the stretched width while stretching, else the game's own canvas width. */
    public static int canvasWidth() {
        if (windowCanvas() && GameShell.frameWid > 0 && GameShell.frameHei > 0) {
            return GameShell.frameWid;
        }
        return active() ? stretchedWidth : GameShell.canvasWid;
    }

    public static int canvasHeight() {
        if (windowCanvas() && GameShell.frameWid > 0 && GameShell.frameHei > 0) {
            return GameShell.frameHei;
        }
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
        if (windowCanvas() && stretchedWidth > 0 && realWidth > 0) {
            // A2 round 8: the game sits at drawX in the window canvas, drawn stretchedWidth wide (the blit's rectangle).
            return (int) Math.floor((x - drawX) * (double) realWidth / stretchedWidth);
        }
        return active() ? (int) (x / ((double) drawnWidth() / realWidth)) : x;
    }

    public static int mouseY(int y) {
        if (windowCanvas() && stretchedHeight > 0 && realHeight > 0) {
            return (int) Math.floor((y - drawY) * (double) realHeight / stretchedHeight);
        }
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

    /**
     * The OpenGL calls of the stretch framebuffer, behind one seam so StretchedFrameRegressionTest can follow every buffer
     * the game draws into and what the window shows (the real client: {@link jaggl.OpenGL}).
     */
    public interface Gl {
        void glBindFramebufferEXT(int target, int framebuffer);
        void glBlitFramebufferEXT(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
        void glDisable(int cap);
        void glEnable(int cap);
        void glGenFramebuffersEXT(int n, int[] ids, int offset);
        void glGenRenderbuffersEXT(int n, int[] ids, int offset);
        void glBindRenderbufferEXT(int target, int renderbuffer);
        void glRenderbufferStorageEXT(int target, int format, int width, int height);
        /** Multisampled storage (anti-aliasing); a GL that does not model samples stores a plain buffer. */
        default void glRenderbufferStorageMultisampleEXT(int target, int samples, int format, int width, int height) {
            glRenderbufferStorageEXT(target, format, width, height);
        }
        void glFramebufferRenderbufferEXT(int target, int attachment, int renderbufferTarget, int renderbuffer);
        int glCheckFramebufferStatusEXT(int target);
        void glDeleteFramebuffersEXT(int n, int[] ids, int offset);
        void glDeleteRenderbuffersEXT(int n, int[] ids, int offset);
        void glClearColor(float red, float green, float blue, float alpha);
        void glClear(int mask);
    }

    private static final class NativeGl implements Gl {
        public void glBindFramebufferEXT(int target, int framebuffer) { OpenGL.glBindFramebufferEXT(target, framebuffer); }
        public void glBlitFramebufferEXT(int a, int b, int c, int d, int e, int f, int g, int h, int mask, int filter) { OpenGL.glBlitFramebufferEXT(a, b, c, d, e, f, g, h, mask, filter); }
        public void glDisable(int cap) { OpenGL.glDisable(cap); }
        public void glEnable(int cap) { OpenGL.glEnable(cap); }
        public void glGenFramebuffersEXT(int n, int[] ids, int offset) { OpenGL.glGenFramebuffersEXT(n, ids, offset); }
        public void glGenRenderbuffersEXT(int n, int[] ids, int offset) { OpenGL.glGenRenderbuffersEXT(n, ids, offset); }
        public void glBindRenderbufferEXT(int target, int renderbuffer) { OpenGL.glBindRenderbufferEXT(target, renderbuffer); }
        public void glRenderbufferStorageEXT(int target, int format, int width, int height) { OpenGL.glRenderbufferStorageEXT(target, format, width, height); }
        public void glRenderbufferStorageMultisampleEXT(int target, int samples, int format, int width, int height) { OpenGL.glRenderbufferStorageMultisampleEXT(target, samples, format, width, height); }
        public void glFramebufferRenderbufferEXT(int target, int attachment, int rbTarget, int rb) { OpenGL.glFramebufferRenderbufferEXT(target, attachment, rbTarget, rb); }
        public int glCheckFramebufferStatusEXT(int target) { return OpenGL.glCheckFramebufferStatusEXT(target); }
        public void glDeleteFramebuffersEXT(int n, int[] ids, int offset) { OpenGL.glDeleteFramebuffersEXT(n, ids, offset); }
        public void glDeleteRenderbuffersEXT(int n, int[] ids, int offset) { OpenGL.glDeleteRenderbuffersEXT(n, ids, offset); }
        public void glClearColor(float red, float green, float blue, float alpha) { OpenGL.glClearColor(red, green, blue, alpha); }
        public void glClear(int mask) { OpenGL.glClear(mask); }
    }

    /** The real driver; tests put a recording one in ({@link #useGlForTest}). */
    private static Gl gl = new NativeGl();

    /** Tests: draw through [fake] instead of the driver (null = the driver again); forgets every framebuffer. */
    public static void useGlForTest(Gl fake) {
        gl = fake == null ? new NativeGl() : fake;
        glContextLost();
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
        return glSync(supported, width, height, false);
    }

    /**
     * {@link #glSync(boolean, int, int)} with the toolkit's scissor state ([scissor] = the scissor test is on now).
     *
     * A2 round 4 / N93 (Keurmeester A2 round 3 reason 1): the 2011 client never clears pixels no interface covers - the intro
     * hides the fixed side panel, minimap and chat tabs, so x 516..765 is simply not drawn. Every change of the buffer the game
     * draws into left an old picture there: Stretched OFF went back to the window, which still held the last stretched blit
     * (a frozen ghost of the scene and a second Deadman label); Stretched ON / a new real size / a new GL context stores new
     * renderbuffers, whose contents are undefined. The window's own pixels are black because the client cleared it at start.
     * So after every such change the target is cleared black for {@link #CLEAR_FLIPS} flips (both window buffers, also with
     * triple buffering) before the client draws into it; RuneLite's GPU plugin clears its framebuffers every frame.
     */
    public static boolean glSync(boolean supported, int width, int height, boolean scissor) {
        boolean changed = glSyncBuffer(supported, width, height);
        if (changed) {
            // A2 round 7 part 2: the offscreen first frame of a window-mode switch is cleared once, when its buffer is made - the
            // resize right before its flip syncs again and must not wipe the finished frame (in game: one fully black frame).
            clearFlips = prerenderWidth > 0 && bound ? 1 : CLEAR_FLIPS;
        } else if (prerenderWidth > 0) {
            clearFlips = 0;
        }
        if (clearFlips > 0) {
            clearFlips--;
            if (scissor) {
                gl.glDisable(GL_SCISSOR_TEST);
            }
            gl.glClearColor(0F, 0F, 0F, 1F);
            gl.glClear(GL_COLOR_BUFFER_BIT);
            if (scissor) {
                gl.glEnable(GL_SCISSOR_TEST);
            }
        }
        return changed;
    }

    /** Flips that still clear the target after a buffer change (front + back + one spare for triple buffering). */
    static final int CLEAR_FLIPS = 3;
    private static int clearFlips;
    private static boolean glSyncBuffer(boolean supported, int width, int height) {
        boolean want = supported && !glFailed && (drawsOffscreen() || prerenderWidth > 0) && width > 0 && height > 0;
        if (!want) {
            if (!bound) {
                return false;
            }
            gl.glBindFramebufferEXT(GL_FRAMEBUFFER, 0);
            deleteBuffer();
            return true;
        }
        if (bound && bufferWidth == width && bufferHeight == height && bufferSamples == wantedSamples()) {
            return false;
        }
        if (!createBuffer(width, height)) {
            // No offscreen buffer on this driver: lay the window out unstretched instead of drawing a broken picture.
            glFailed = true;
            System.out.println("[78] stretch: offscreen framebuffer incomplete at " + width + "x" + height + " - stretching off");
            Client.changingWindowMode = true;
            return true;
        }
        gl.glBindFramebufferEXT(GL_FRAMEBUFFER, framebuffer);
        bound = true;
        return true;
    }

    private static String lastPresent = "";

    /**
     * Before swapBuffers: blits the real frame over the whole canvas ([canvasWidth] x [canvasHeight], its drawable), then
     * binds the offscreen framebuffer again for the next frame. The scissor test clips blits, so it is off meanwhile.
     */
    public static void glPresent(boolean scissor, int canvasWidth, int canvasHeight) {
        String state = "stretch active=" + active() + " offscreen=" + bound + " real=" + bufferWidth + "x" + bufferHeight + " drawn=" + canvasWidth + "x" + canvasHeight
            + " samples=" + bufferSamples;
        if (!state.equals(lastPresent)) {
            lastPresent = state;
            System.out.println("[78] " + state);
        }
        if (!bound || canvasWidth <= 0 || canvasHeight <= 0) {
            return;
        }
        if (scissor) {
            gl.glDisable(GL_SCISSOR_TEST);
        }
        gl.glBindFramebufferEXT(GL_READ_FRAMEBUFFER, resolveSource());
        gl.glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER, 0);
        int x0 = 0;
        int y0 = 0;
        int x1 = canvasWidth;
        int y1 = canvasHeight;
        if (windowCanvas() && prerenderWidth <= 0 && stretchedWidth > 0 && stretchedHeight > 0) {
            // A2 round 8: the game's rectangle inside the window canvas (GL rows count from the bottom); the margins are black.
            x0 = drawX;
            x1 = drawX + stretchedWidth;
            y1 = canvasHeight - drawY;
            y0 = y1 - stretchedHeight;
            if (x0 > 0 || y0 > 0 || x1 < canvasWidth || y1 < canvasHeight) {
                gl.glClearColor(0F, 0F, 0F, 1F);
                gl.glClear(GL_COLOR_BUFFER_BIT);
            }
        }
        gl.glBlitFramebufferEXT(0, 0, bufferWidth, bufferHeight, x0, y0, x1, y1, GL_COLOR_BUFFER_BIT, fast ? GL_NEAREST : GL_LINEAR);
        gl.glBindFramebufferEXT(GL_FRAMEBUFFER, framebuffer);
        if (scissor) {
            gl.glEnable(GL_SCISSOR_TEST);
        }
    }

    /** The samples the offscreen buffer should have now (the player's anti-aliasing unless the driver refused it). */
    private static int wantedSamples() {
        return glSamples > 1 && !glNoMultisample ? glSamples : 0;
    }

    /**
     * The single-sample framebuffer a multisampled frame is resolved into before it is blitted / read: a multisampled
     * buffer may only be blitted 1:1 (same rectangle) - a scaled or moved blit out of it is INVALID_OPERATION.
     */
    private static int resolveFramebuffer;
    private static int resolveColourBuffer;
    /** Samples of the current offscreen buffer (0 = single-sample). */
    private static int bufferSamples;
    /** The driver could not make a complete multisampled framebuffer: the offscreen frame stays single-sample (this run). */
    private static boolean glNoMultisample;

    /**
     * The framebuffer that holds the finished single-sample frame: the offscreen buffer itself, or - when it is multisampled -
     * the resolve buffer after a 1:1 GL_NEAREST blit (the 667 client's Class276.method6245, RuneLite GpuPlugin's AA resolve).
     */
    private static int resolveSource() {
        if (bufferSamples <= 1 || resolveFramebuffer == 0) {
            return framebuffer;
        }
        gl.glBindFramebufferEXT(GL_READ_FRAMEBUFFER, framebuffer);
        gl.glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
        gl.glBlitFramebufferEXT(0, 0, bufferWidth, bufferHeight, 0, 0, bufferWidth, bufferHeight, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        return resolveFramebuffer;
    }

    /**
     * Before the toolkit reads pixels of "the window" - glReadPixels (Toolkit.na: hook / bug screenshots) and
     * glCopyTexImage2D / glCopyTexSubImage2D (Class93_Sub2: the minimap and every sprite copied from the frame): a multisampled
     * offscreen frame cannot be read (INVALID_OPERATION - in game the minimap stayed black), so it is resolved and the resolve
     * buffer is bound for reading. [scissor] = the toolkit's scissor test is on (it clips the resolve blit, so it is off for
     * it). True when {@link #glReadEnd} must bind the offscreen buffer again afterwards.
     */
    public static boolean glReadBegin(boolean scissor) {
        if (!bound || bufferSamples <= 1 || resolveFramebuffer == 0) {
            return false;
        }
        if (scissor) {
            gl.glDisable(GL_SCISSOR_TEST);
        }
        gl.glBindFramebufferEXT(GL_READ_FRAMEBUFFER, resolveSource());
        if (scissor) {
            gl.glEnable(GL_SCISSOR_TEST);
        }
        return true;
    }

    /** {@link #glReadBegin(boolean)} with the scissor test off. */
    public static boolean glReadBegin() {
        return glReadBegin(false);
    }

    public static void glReadEnd() {
        gl.glBindFramebufferEXT(GL_FRAMEBUFFER, framebuffer);
    }

    private static boolean createBuffer(int width, int height) {
        int samples = wantedSamples();
        if (allocateBuffer(width, height, samples)) {
            return true;
        }
        if (samples > 1) {
            // The player's anti-aliasing cannot be made offscreen on this driver: keep the picture, single-sample.
            System.out.println("[78] stretch: multisampled offscreen framebuffer (" + samples + " samples) incomplete at " + width + "x" + height
                + " - anti-aliasing off for the offscreen frame");
            glNoMultisample = true;
            return allocateBuffer(width, height, 0);
        }
        return false;
    }

    private static boolean allocateBuffer(int width, int height, int samples) {
        int[] ids = new int[1];
        if (framebuffer == 0) {
            gl.glGenFramebuffersEXT(1, ids, 0);
            framebuffer = ids[0];
            gl.glGenRenderbuffersEXT(1, ids, 0);
            colourBuffer = ids[0];
            gl.glGenRenderbuffersEXT(1, ids, 0);
            depthBuffer = ids[0];
        }
        gl.glBindRenderbufferEXT(GL_RENDERBUFFER, colourBuffer);
        storage(GL_RGBA8, width, height, samples);
        // The 3D scene needs a depth buffer like the window's (the client uses no stencil).
        gl.glBindRenderbufferEXT(GL_RENDERBUFFER, depthBuffer);
        storage(GL_DEPTH_COMPONENT24, width, height, samples);
        gl.glBindRenderbufferEXT(GL_RENDERBUFFER, 0);
        gl.glBindFramebufferEXT(GL_FRAMEBUFFER, framebuffer);
        gl.glFramebufferRenderbufferEXT(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colourBuffer);
        gl.glFramebufferRenderbufferEXT(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthBuffer);
        int status = gl.glCheckFramebufferStatusEXT(GL_FRAMEBUFFER);
        if (status == GL_FRAMEBUFFER_COMPLETE && samples > 1) {
            if (resolveFramebuffer == 0) {
                gl.glGenFramebuffersEXT(1, ids, 0);
                resolveFramebuffer = ids[0];
                gl.glGenRenderbuffersEXT(1, ids, 0);
                resolveColourBuffer = ids[0];
            }
            gl.glBindRenderbufferEXT(GL_RENDERBUFFER, resolveColourBuffer);
            gl.glRenderbufferStorageEXT(GL_RENDERBUFFER, GL_RGBA8, width, height);
            gl.glBindRenderbufferEXT(GL_RENDERBUFFER, 0);
            gl.glBindFramebufferEXT(GL_FRAMEBUFFER, resolveFramebuffer);
            gl.glFramebufferRenderbufferEXT(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, resolveColourBuffer);
            status = gl.glCheckFramebufferStatusEXT(GL_FRAMEBUFFER);
        } else if (samples <= 1) {
            deleteResolve();
        }
        gl.glBindFramebufferEXT(GL_FRAMEBUFFER, 0);
        bufferWidth = width;
        bufferHeight = height;
        bufferSamples = samples;
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            deleteBuffer();
            return false;
        }
        return true;
    }

    private static void storage(int format, int width, int height, int samples) {
        if (samples > 1) {
            gl.glRenderbufferStorageMultisampleEXT(GL_RENDERBUFFER, samples, format, width, height);
        } else {
            gl.glRenderbufferStorageEXT(GL_RENDERBUFFER, format, width, height);
        }
    }

    private static void deleteResolve() {
        if (resolveFramebuffer != 0) {
            int[] ids = new int[1];
            ids[0] = resolveFramebuffer;
            gl.glDeleteFramebuffersEXT(1, ids, 0);
            ids[0] = resolveColourBuffer;
            gl.glDeleteRenderbuffersEXT(1, ids, 0);
        }
        resolveFramebuffer = resolveColourBuffer = 0;
    }

    private static void deleteBuffer() {
        int[] ids = new int[1];
        if (framebuffer != 0) {
            ids[0] = framebuffer;
            gl.glDeleteFramebuffersEXT(1, ids, 0);
            ids[0] = colourBuffer;
            gl.glDeleteRenderbuffersEXT(1, ids, 0);
            ids[0] = depthBuffer;
            gl.glDeleteRenderbuffersEXT(1, ids, 0);
        }
        deleteResolve();
        framebuffer = colourBuffer = depthBuffer = 0;
        bufferWidth = bufferHeight = bufferSamples = 0;
        bound = false;
    }

    /** The GL context went away (toolkit change): its framebuffer ids are gone with it. */
    public static void glContextLost() {
        framebuffer = colourBuffer = depthBuffer = 0;
        resolveFramebuffer = resolveColourBuffer = 0;
        bufferWidth = bufferHeight = bufferSamples = 0;
        bound = false;
        prerenderWidth = prerenderHeight = 0;
    }
}
