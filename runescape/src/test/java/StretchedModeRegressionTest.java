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

        // Integer scaling (RuneLite getStretchedDimensions): each side cut down to a whole multiple of the real size.
        StretchedMode.configure(true, false, false, true, 50);
        window(1600, 1100, 765, 503);
        StretchedMode.layout(true);
        check(StretchedMode.canvasWidth() == 1530 && StretchedMode.canvasHeight() == 1006, "Integer scaling: 2x");
        window(1648, 989, 765, 503);
        StretchedMode.layout(true);
        check(StretchedMode.canvasWidth() == 1530 && StretchedMode.canvasHeight() == 503, "Integer scaling: per side, like RuneLite");

        // Fixed mode (RuneLite, owner 2026-10-11 "net als de officiele versie"): Resizable scaling does nothing, the game always
        // fills the window - no black borders whatever the slider says.
        for (int percent : new int[] {0, 25, 50, 84, 100}) {
            StretchedMode.configure(true, false, false, false, percent);
            window(1648, 989, 765, 503);
            StretchedMode.layout(true);
            check(GameShell.canvasWid == 765 && GameShell.canvasHei == 503, "Fixed " + percent + "%: the game stays 765x503");
            check(StretchedMode.canvasWidth() == 1648 && StretchedMode.canvasHeight() == 989, "Fixed " + percent + "%: fills the window");
            check(GameShell.leftMargin == 0 && GameShell.topMargin == 0, "Fixed " + percent + "%: no black borders");
        }

        // Resizable: the game is the window divided by the scaling factor (50% = 1.5), never below 765x503.
        StretchedMode.configure(true, false, false, false, 50);
        window(1500, 1050, 1500, 1050);
        StretchedMode.layout(false);
        check(GameShell.canvasWid == 1000 && GameShell.canvasHei == 700, "Resizable 50%: 1500x1050 window -> 1000x700 game");
        window(900, 600, 900, 600);
        StretchedMode.layout(false);
        check(GameShell.canvasWid >= 765 && GameShell.canvasHei >= 503, "Resizable: the game never gets smaller than 765x503");
        // RuneLite floor 765x503 (A2 round 3; was the 667 layout floor 520 from 10-11 to 10-17): a truncated 502 is held on 503.
        window(1254, 761, 1254, 761);
        StretchedMode.configure(true, false, false, false, 58);
        StretchedMode.layout(false);
        check(GameShell.canvasHei == 503 && GameShell.canvasWid >= 765, "Resizable 58% in 1254x761: game 503 high, not 502");
        check(StretchedMode.active() && StretchedMode.canvasHeight() == 761, "Resizable 58%: still drawn over the window");
        // Every Resizable scaling step fills the window (no black borders) and the game shrinks as the percentage grows.
        int previous = Integer.MAX_VALUE;
        for (int percent = 0; percent <= 100; percent++) {
            StretchedMode.configure(true, false, false, false, percent);
            window(1648, 989, 1648, 989);
            StretchedMode.layout(false);
            check(StretchedMode.canvasWidth() == 1648 && StretchedMode.canvasHeight() == 989 && GameShell.leftMargin == 0 && GameShell.topMargin == 0,
                "Resizable " + percent + "%: fills the window");
            check(GameShell.canvasWid <= previous && GameShell.canvasHei >= 503, "Resizable " + percent + "%: game " + GameShell.canvasWid + "x" + GameShell.canvasHei);
            previous = GameShell.canvasWid;
        }
        // Night run 2026-10-12: never shrunk. A window lower than the (then 520) floor (765x503) drew the 791x520 game shrunk into it and
        // Increased performance dropped pixel rows out of the text; RuneLite never draws the game smaller than it is.
        StretchedMode.configure(true, false, false, false, 50);
        window(765, 503, 765, 503);
        StretchedMode.layout(false);
        check(GameShell.canvasWid == 765 && GameShell.canvasHei == 503, "Resizable in a 765x503 window: 1:1, not shrunk: " + GameShell.canvasWid + "x" + GameShell.canvasHei);
        window(900, 510, 900, 510);
        StretchedMode.layout(false);
        check(GameShell.canvasHei == 503 && GameShell.canvasWid <= 900, "Resizable 50% in a 510 px high window: the game is on the 503 floor: " + GameShell.canvasWid + "x" + GameShell.canvasHei);
        // Every window from the smallest the frame allows up to the laptop screen, every scaling step 0..200 % (RuneLite has no
        // maximum): the game is never larger than the window it is drawn over, and never lower than 503 where the window allows it.
        int[][] windows = {{765, 503}, {765, 519}, {765, 520}, {800, 560}, {854, 581}, {944, 581}, {1029, 761}, {1254, 761}, {1648, 989}, {1890, 989}, {1920, 1080}};
        for (int[] w : windows) {
            for (int percent = 0; percent <= 200; percent += 5) {
                StretchedMode.configure(true, false, false, false, percent);
                window(w[0], w[1], w[0], w[1]);
                StretchedMode.layout(false);
                check(GameShell.canvasWid <= w[0] && GameShell.canvasHei <= w[1],
                    "Resizable " + percent + "% in " + w[0] + "x" + w[1] + ": game " + GameShell.canvasWid + "x" + GameShell.canvasHei + " never larger than the window");
                check(GameShell.canvasWid >= Math.min(765, w[0]) && GameShell.canvasHei >= Math.min(503, w[1]),
                    "Resizable " + percent + "% in " + w[0] + "x" + w[1] + ": floor " + GameShell.canvasWid + "x" + GameShell.canvasHei);
                for (boolean keep : new boolean[] {false, true}) {
                    for (boolean integer : new boolean[] {false, true}) {
                        StretchedMode.configure(true, keep, false, integer, percent);
                        window(w[0], w[1], w[0], w[1]);
                        StretchedMode.layout(false);
                        check(StretchedMode.canvasWidth() <= w[0] && StretchedMode.canvasHeight() <= w[1] && GameShell.leftMargin >= 0 && GameShell.topMargin >= 0,
                            "Resizable " + percent + "% keep=" + keep + " integer=" + integer + " in " + w[0] + "x" + w[1] + ": drawn inside the window");
                        check(StretchedMode.canvasWidth() >= GameShell.canvasWid || !StretchedMode.active(),
                            "Resizable " + percent + "% keep=" + keep + " integer=" + integer + " in " + w[0] + "x" + w[1] + ": drawn " + StretchedMode.canvasWidth() + " not smaller than the game " + GameShell.canvasWid);
                    }
                }
            }
        }
        // Fixed mode with stretching on fills any window, so the client must not shrink the window to 765x503 (A2).
        check(StretchedMode.fillsWindow(), "Stretching on: the window is not fitted to the game");
        StretchedMode.configure(false, false, false, false, 50);
        check(!StretchedMode.fillsWindow(), "Stretching off: the fixed window fits the game again");

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
        // A2 round 8 review: also plain while not stretching - the player's anti-aliasing is drawn into a multisampled
        // offscreen framebuffer (StretchedSwitchSurfaceRegressionTest section 1c), so Stretched on/off never rebuilds the window.
        check(StretchedMode.glPlainWindow(), "AA: plain GL window while not stretching too (anti-aliasing offscreen)");
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
