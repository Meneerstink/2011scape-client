package com.jagex;

import com.jagex.game.runetek6.client.GameShell;
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
        enabled = on;
        keepAspectRatio = keepAspect;
        fast = increasedPerformance;
        integerScaling = integer;
        scalingFactor = factor;
        if (changed) {
            Client.changingWindowMode = true;
        }
        return changed;
    }

    /** Stretching is on and the window is laid out stretched. */
    public static boolean active() {
        return enabled && stretchedWidth > 0 && (stretchedWidth != realWidth || stretchedHeight != realHeight);
    }

    /**
     * Called after the game picked its canvas size and margins for a window of frameWid x frameHei (fixed or resizable):
     * replaces them by RuneLite's real size and works out the stretched size the canvas is drawn at.
     */
    public static void layout(boolean fixed) {
        if (!enabled) {
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
        // getRealDimensions
        if (fixed) {
            realWidth = FIXED_WIDTH;
            realHeight = FIXED_HEIGHT;
        } else {
            int newWidth = (int) (parentWidth / scalingFactor);
            int newHeight = (int) (parentHeight / scalingFactor);
            if (newWidth < FIXED_WIDTH || newHeight < FIXED_HEIGHT) {
                double factor = Math.min((double) parentWidth / FIXED_WIDTH, (double) parentHeight / FIXED_HEIGHT);
                newWidth = (int) (parentWidth / factor);
                newHeight = (int) (parentHeight / factor);
            }
            realWidth = newWidth;
            realHeight = newHeight;
        }
        // getStretchedDimensions
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

    /** TranslateMouseListener: a canvas x position in real (game) pixels. */
    public static int mouseX(int x) {
        return active() ? (int) (x / ((double) stretchedWidth / realWidth)) : x;
    }

    public static int mouseY(int y) {
        return active() ? (int) (y / ((double) stretchedHeight / realHeight)) : y;
    }

    // ---- OpenGL (GpuPlugin: the frame is rendered at the real size, then blitted to the stretched size) ----------------

    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_RENDERBUFFER = 0x8D41;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_COLOR_BUFFER_BIT = 0x4000;
    private static final int GL_NEAREST = 0x2600;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_SCISSOR_TEST = 0x0C11;

    private static int framebuffer;
    private static int renderbuffer;
    private static int bufferWidth;
    private static int bufferHeight;

    /**
     * Before swapBuffers: the frame sits in the bottom-left real-size corner of the window's back buffer. It is copied into
     * an offscreen framebuffer (a blit cannot read and write the same buffer) and blitted back over the whole stretched
     * window. Needs GL_EXT_framebuffer_object + GL_EXT_framebuffer_blit ([supported]); without them the frame stays unscaled.
     */
    private static String lastPresent = "";

    public static void glPresent(boolean supported, boolean scissor) {
        String state = "stretch active=" + active() + " supported=" + supported + " real=" + realWidth + "x" + realHeight + " stretched=" + stretchedWidth + "x" + stretchedHeight;
        if (!state.equals(lastPresent)) {
            lastPresent = state;
            System.out.println("[78] " + state);
        }
        if (!active() || !supported) {
            return;
        }
        int rw = realWidth;
        int rh = realHeight;
        if (framebuffer == 0 || bufferWidth != rw || bufferHeight != rh) {
            createBuffer(rw, rh);
        }
        if (scissor) {
            OpenGL.glDisable(GL_SCISSOR_TEST);
        }
        OpenGL.glBindFramebufferEXT(GL_READ_FRAMEBUFFER, 0);
        OpenGL.glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER, framebuffer);
        OpenGL.glBlitFramebufferEXT(0, 0, rw, rh, 0, 0, rw, rh, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        OpenGL.glBindFramebufferEXT(GL_READ_FRAMEBUFFER, framebuffer);
        OpenGL.glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER, 0);
        OpenGL.glBlitFramebufferEXT(0, 0, rw, rh, 0, 0, stretchedWidth, stretchedHeight, GL_COLOR_BUFFER_BIT, fast ? GL_NEAREST : GL_LINEAR);
        OpenGL.glBindFramebufferEXT(GL_FRAMEBUFFER, 0);
        if (scissor) {
            OpenGL.glEnable(GL_SCISSOR_TEST);
        }
    }

    private static void createBuffer(int width, int height) {
        int[] ids = new int[1];
        if (framebuffer == 0) {
            OpenGL.glGenFramebuffersEXT(1, ids, 0);
            framebuffer = ids[0];
            OpenGL.glGenRenderbuffersEXT(1, ids, 0);
            renderbuffer = ids[0];
        }
        OpenGL.glBindRenderbufferEXT(GL_RENDERBUFFER, renderbuffer);
        OpenGL.glRenderbufferStorageEXT(GL_RENDERBUFFER, GL_RGBA8, width, height);
        OpenGL.glBindFramebufferEXT(GL_FRAMEBUFFER, framebuffer);
        OpenGL.glFramebufferRenderbufferEXT(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, renderbuffer);
        OpenGL.glBindFramebufferEXT(GL_FRAMEBUFFER, 0);
        bufferWidth = width;
        bufferHeight = height;
    }

    /** The GL context went away (toolkit change): its framebuffer ids are gone with it. */
    public static void glContextLost() {
        framebuffer = 0;
        renderbuffer = 0;
        bufferWidth = bufferHeight = 0;
    }
}
