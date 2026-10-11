import com.jagex.StretchedMode;
import com.jagex.game.runetek6.client.GameShell;

import java.awt.Canvas;

/**
 * RuneLite Stretched Mode (owner 2026-10-11: cut off / too big, clicks miss, flicker / black): the layout follows RuneLite's
 * StretchedModeMixin, only toolkits that can draw a stretched frame stretch, and the mouse is scaled by the size the frame
 * is drawn at. No display, server or GL context needed.
 */
public final class StretchedModeRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        Canvas canvas = new Canvas();
        GameShell.canvas = canvas;

        // Off: the game's own layout is left alone.
        StretchedMode.configure(false, false, false, false, 50);
        window(1100, 700, 765, 503);
        StretchedMode.layout(true);
        check(!StretchedMode.active(), "Off: not stretched");
        check(GameShell.canvasWid == 765 && GameShell.canvasHei == 503, "Off: the game keeps its canvas");

        // Fixed mode: the game stays 765x503 and is drawn over the whole window (RuneLite getRealDimensions / getStretchedDimensions).
        StretchedMode.toolkitChanged(true);
        StretchedMode.configure(true, false, false, false, 50);
        window(1100, 700, 765, 503);
        StretchedMode.layout(true);
        check(StretchedMode.active(), "Fixed: stretched");
        check(GameShell.canvasWid == 765 && GameShell.canvasHei == 503, "Fixed: the game renders at 765x503");
        check(StretchedMode.canvasWidth() == 1100 && StretchedMode.canvasHeight() == 700, "Fixed: drawn over the whole window");
        check(GameShell.leftMargin == 0 && GameShell.topMargin == 0, "Fixed: no margins without Keep aspect ratio");

        // Keep aspect ratio: the drawn size keeps 765:503 and is centred.
        StretchedMode.configure(true, true, false, false, 50);
        window(1100, 700, 765, 503);
        StretchedMode.layout(true);
        check(StretchedMode.canvasHeight() == 700 && StretchedMode.canvasWidth() == (int) (700 * (765D / 503)), "Keep aspect: width from the height");
        check(GameShell.leftMargin == (1100 - StretchedMode.canvasWidth()) / 2, "Keep aspect: centred");

        // Integer scaling: whole multiples of the real size only.
        StretchedMode.configure(true, false, false, true, 50);
        window(1600, 1100, 765, 503);
        StretchedMode.layout(true);
        check(StretchedMode.canvasWidth() == 1530 && StretchedMode.canvasHeight() == 1006, "Integer scaling: 2x");

        // Resizable: the game is the window divided by the scaling factor (50% = 1.5), never below 765x503.
        StretchedMode.configure(true, false, false, false, 50);
        window(1500, 1050, 1500, 1050);
        StretchedMode.layout(false);
        check(GameShell.canvasWid == 1000 && GameShell.canvasHei == 700, "Resizable 50%: 1500x1050 window -> 1000x700 game");
        window(900, 600, 900, 600);
        StretchedMode.layout(false);
        check(GameShell.canvasWid >= 765 && GameShell.canvasHei >= 503, "Resizable: the game never gets smaller than 765x503");

        // Mouse: scaled by the size the frame is drawn at (the canvas), so a click lands on what is shown.
        StretchedMode.configure(true, false, false, false, 50);
        window(1100, 700, 765, 503);
        StretchedMode.layout(true);
        canvas.setSize(StretchedMode.canvasWidth(), StretchedMode.canvasHeight());
        check(StretchedMode.mouseX(1100) == 765 && StretchedMode.mouseY(700) == 503, "Mouse: the far corner of the window is the game's corner");
        check(StretchedMode.mouseX(550) == 382 && StretchedMode.mouseY(350) == 251, "Mouse: the middle of the window is the game's middle");
        canvas.setSize(1000, 700);
        check(StretchedMode.mouseX(1000) == 765, "Mouse: follows the canvas the frame is drawn over, also before the next layout");

        // A toolkit that draws straight into the canvas (DirectX, SSE, GLX): stretching stays off - no cut-off picture with a
        // scaled mouse.
        StretchedMode.toolkitChanged(false);
        window(1100, 700, 765, 503);
        StretchedMode.layout(true);
        check(!StretchedMode.active(), "DirectX/SSE/GLX: not stretched");
        check(GameShell.canvasWid == 765 && StretchedMode.canvasWidth() == 765, "DirectX/SSE/GLX: the game's own layout");
        check(StretchedMode.mouseX(500) == 500, "DirectX/SSE/GLX: the mouse is not scaled");
        StretchedMode.toolkitChanged(true);

        // OpenGL without framebuffer support: nothing is bound, the toolkit draws into the window.
        check(!StretchedMode.glSync(false, 765, 503), "GL without framebuffers: no offscreen buffer");
        check(StretchedMode.glWindow() == 0 && !StretchedMode.glOwnsFrame(), "GL without framebuffers: the window is the target");

        // Anti-aliasing: OpenGL gets a plain window while stretching (a multisampled one cannot take the blit - black screen),
        // the player's anti-aliasing when it is off, and never stretches on a driver without framebuffers (no rebuild loop).
        StretchedMode.configure(true, false, false, false, 50);
        check(StretchedMode.glPlainWindow(), "AA: plain GL window while stretching");
        StretchedMode.configure(false, false, false, false, 50);
        check(!StretchedMode.glPlainWindow(), "AA: the player's anti-aliasing while not stretching");
        StretchedMode.configure(true, false, false, false, 50);
        StretchedMode.glUnsupported();
        check(!StretchedMode.glPlainWindow(), "AA: no framebuffers - the player's anti-aliasing, OpenGL unstretched");
        StretchedMode.glContextLost();
        check(!StretchedMode.glPlainWindow(), "AA: a new GL context does not flip it back (no rebuild loop)");

        System.out.println("StretchedModeRegressionTest: " + assertions + " assertions passed");
    }

    /** The window (frame) and the canvas size the game picked for it before Stretched Mode lays it out. */
    private static void window(int frameWidth, int frameHeight, int gameWidth, int gameHeight) {
        GameShell.frameWid = frameWidth;
        GameShell.frameHei = frameHeight;
        GameShell.canvasWid = gameWidth;
        GameShell.canvasHei = gameHeight;
        GameShell.leftMargin = 0;
        GameShell.topMargin = 0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        assertions++;
    }
}
