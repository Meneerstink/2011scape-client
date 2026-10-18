import com.jagex.StretchedMode;
import com.jagex.game.runetek6.client.GameShell;

import java.awt.Canvas;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A2 round 8 (Keurmeester A2 round 7 FAIL, ROOT_CAUSES 209/211): with Stretched Mode OFF an in-game fixed <-> resizable switch
 * showed a fully BLACK game area on the real screen (CopyFromScreen + PrintWindow). Measured on the Keurmeester's frames: the
 * black frames are EXACTLY the frames where the window or the AWT canvas changed size (944x581 window 1216x581 -> 1037x503 and
 * back; maximised 1920x989: the canvas 765x503 at 441,0 -> 1648x989). Windows shows a resized OpenGL surface black until the
 * next SwapBuffers, so every deferral of that resize (rounds 7 / 7b) only moved the black. With plain Stretched ON the canvas
 * and window keep their size in both modes, and that path was clean in every run.
 *
 * Donor: RuneLite ClientUI never sizes the frame on a mode change (applyGameSize only from the config; reference\runelite\
 * stretched\ClientUI.java l.1245-1262), and the canvas fills its parent; the fixed 765x503 game is drawn inside it.
 *
 * Invariant (whole class, OpenGL with framebuffer objects = the owner's renderer): for every window, Stretched OFF and ON, every
 * Keep aspect ratio / Integer scaling combination and several Resizable scaling values, the AWT canvas has the SAME size and
 * place in fixed and in resizable (= the window's game area), the client does not fit or restore the window at the switch
 * (StretchedMode.fillsWindow), and a click lands on the game pixel drawn under it. Other toolkits keep their old canvas sizes.
 */
public final class StretchedSwitchSurfaceRegressionTest {
    private static int assertions;
    private static final Map<String, Integer> kinds = new LinkedHashMap<String, Integer>();
    private static final List<String> examples = new ArrayList<String>();
    private static final java.io.PrintStream OUT = System.out;

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
            }
        }));
        GameShell.canvas = new Canvas();
        StretchedMode.toolkitChanged(true);

        List<int[]> windows = new ArrayList<int[]>();
        for (int w = 765; w <= 1920; w += 61) {
            for (int h = 503; h <= 1080; h += 47) {
                windows.add(new int[] {w, h});
            }
        }
        int[][] named = {{944, 581}, {1007, 581}, {1216, 581}, {765, 503}, {795, 503}, {1648, 989}, {1890, 989}, {1920, 989}, {1648, 1080}, {978, 709}};
        for (int[] w : named) {
            windows.add(w);
        }
        boolean[][] options = {{false, false}, {true, false}, {false, true}, {true, true}}; // keep aspect, integer
        int[] scalings = {0, 16, 50};
        int cases = 0;

        // 1. OpenGL with framebuffer objects: a switch never changes the canvas or the window.
        glToolkit(true);
        for (int[] w : windows) {
            for (boolean on : new boolean[] {false, true}) {
                for (boolean[] o : options) {
                    if (!on && (o[0] || o[1])) {
                        continue; // the options only act while stretching
                    }
                    for (int s : scalings) {
                        if (!on && s != 0) {
                            continue;
                        }
                        cases++;
                        String name = (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                            + (on ? " " + s + "%" : "") + " window " + w[0] + "x" + w[1];
                        Layout fixed = layout(w, true, on, o[0], o[1], s);
                        clicks(name + " fixed", fixed);
                        Layout resizable = layout(w, false, on, o[0], o[1], s);
                        clicks(name + " resizable", resizable);
                        check(fixed.canvasW == resizable.canvasW && fixed.canvasH == resizable.canvasH,
                            "canvas changes size at a fixed <-> resizable switch (black on the real screen until the next swap)",
                            name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " vs resizable " + resizable.canvasW + "x" + resizable.canvasH);
                        check(fixed.canvasX == resizable.canvasX && fixed.canvasY == resizable.canvasY,
                            "canvas moves at a fixed <-> resizable switch",
                            name + ": fixed canvas at " + fixed.canvasX + "," + fixed.canvasY + " vs resizable " + resizable.canvasX + "," + resizable.canvasY);
                        check(fixed.canvasW == w[0] && fixed.canvasH == w[1] && fixed.canvasX == 0 && fixed.canvasY == 0,
                            "the canvas does not fill the window's game area (RuneLite: the canvas fills its parent)",
                            name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " at " + fixed.canvasX + "," + fixed.canvasY);
                        check(fixed.fills && resizable.fills,
                            "the client fits / restores the WINDOW at a switch (InterfaceManager.changeWindowMode: RuneLite never sizes the frame)",
                            name + ": fillsWindow fixed=" + fixed.fills + " resizable=" + resizable.fills);
                    }
                }
            }
        }

        // 1b. The blit (StretchedMode.glPresent through the GL seam): a frame that is not 1:1 over the whole canvas is drawn offscreen
        //     at its real size and blitted exactly into its drawn rectangle (GL rows from the bottom), the margins cleared black.
        for (int[] w : new int[][] {{944, 581}, {1648, 989}, {1890, 989}, {765, 503}, {1216, 581}}) {
            for (boolean on : new boolean[] {false, true}) {
                for (boolean[] o : options) {
                    if (!on && (o[0] || o[1])) {
                        continue;
                    }
                    for (boolean fixedMode : new boolean[] {true, false}) {
                        cases++;
                        String name = "blit " + (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                            + (fixedMode ? " fixed" : " resizable") + " window " + w[0] + "x" + w[1];
                        RecordingGl gl = new RecordingGl();
                        StretchedMode.useGlForTest(gl);
                        Layout l = layout(w, fixedMode, on, o[0], o[1], 16);
                        java.awt.Dimension render = StretchedMode.renderSize(GameShell.canvas);
                        StretchedMode.glSync(true, render.width, render.height);
                        gl.events.clear();
                        StretchedMode.glPresent(false, l.canvasW, l.canvasH);
                        boolean oneToOne = l.drawX == 0 && l.drawY == 0 && l.realW == l.canvasW && l.realH == l.canvasH && l.drawnW == l.canvasW && l.drawnH == l.canvasH;
                        if (oneToOne) {
                            check(gl.blit == null, "a 1:1 frame over the whole canvas goes through the offscreen buffer", name);
                            continue;
                        }
                        check(render.width == l.realW && render.height == l.realH, "the toolkit does not render the blitted frame at its real size",
                            name + ": renders " + render.width + "x" + render.height + ", real " + l.realW + "x" + l.realH);
                        int[] want = {0, 0, l.realW, l.realH, l.drawX, l.canvasH - l.drawY - l.drawnH, l.drawX + l.drawnW, l.canvasH - l.drawY};
                        check(gl.blit != null && java.util.Arrays.equals(gl.blit, want), "the blit does not land on the drawn rectangle",
                            name + ": blit " + (gl.blit == null ? "none" : java.util.Arrays.toString(gl.blit)) + ", want " + java.util.Arrays.toString(want));
                        boolean margins = l.drawX > 0 || l.drawY > 0 || l.drawX + l.drawnW < l.canvasW || l.drawY + l.drawnH < l.canvasH;
                        if (margins) {
                            check(gl.clearedWindowBeforeBlit, "the black margins around the drawn game are not cleared before the blit", name);
                        }
                    }
                }
            }
        }
        StretchedMode.useGlForTest(new RecordingGl());

        // 2. Other toolkits (DirectX / software / Java): unchanged - the canvas is the drawn size, placed by the game's margins.
        glToolkit(false);
        for (int[] w : new int[][] {{944, 581}, {1648, 989}, {765, 503}}) {
            for (boolean on : new boolean[] {false, true}) {
                cases++;
                String name = "non-GL " + (on ? "Stretched ON" : "Stretched OFF") + " window " + w[0] + "x" + w[1];
                Layout fixed = layout(w, true, on, false, false, 0);
                int expectW = on ? w[0] : 765;
                int expectH = on ? w[1] : 503;
                check(fixed.canvasW == expectW && fixed.canvasH == expectH,
                    "non-GL toolkit: fixed canvas size changed by this fix",
                    name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + ", expected " + expectW + "x" + expectH);
                clicks(name + " fixed", fixed);
            }
        }

        System.setOut(OUT);
        int failed = 0;
        for (int n : kinds.values()) {
            failed += n;
        }
        if (failed > 0) {
            System.out.println("StretchedSwitchSurfaceRegressionTest: " + cases + " cases, " + assertions + " checks, " + failed + " failed");
            for (Map.Entry<String, Integer> e : kinds.entrySet()) {
                System.out.println("  " + e.getValue() + "x " + e.getKey());
            }
            for (String line : examples) {
                System.out.println("  e.g. " + line);
            }
            System.exit(1);
        }
        System.out.println("StretchedSwitchSurfaceRegressionTest: " + cases + " cases, " + assertions + " checks, all passed");
    }

    /** What the AWT canvas gets after the game's resize loop (Static712 / InterfaceManager.windowModeChanged) for one mode. */
    private static final class Layout {
        int canvasW, canvasH, canvasX, canvasY, realW, realH, drawnW, drawnH, drawX, drawY;
        boolean fills;
    }

    private static Layout layout(int[] w, boolean fixed, boolean on, boolean keepAspect, boolean integer, int scaling) throws Exception {
        StretchedMode.configure(on, keepAspect, false, integer, scaling);
        GameShell.frameWid = w[0];
        GameShell.frameHei = w[1];
        // The game's own layout for the mode (Static712.method9329): fixed = 765x503 centred at the top, resizable = the window.
        if (fixed) {
            GameShell.canvasWid = 765;
            GameShell.canvasHei = 503;
            GameShell.leftMargin = Math.max(0, (w[0] - 765) / 2);
            GameShell.topMargin = 0;
        } else {
            GameShell.canvasWid = w[0];
            GameShell.canvasHei = w[1];
            GameShell.leftMargin = 0;
            GameShell.topMargin = 0;
        }
        StretchedMode.layout(fixed);
        Layout l = new Layout();
        l.canvasW = StretchedMode.canvasWidth();
        l.canvasH = StretchedMode.canvasHeight();
        l.canvasX = GameShell.leftMargin;
        l.canvasY = GameShell.topMargin;
        l.realW = GameShell.canvasWid;
        l.realH = GameShell.canvasHei;
        l.drawnW = StretchedMode.stretchedWidth();
        l.drawnH = StretchedMode.stretchedHeight();
        l.drawX = intOr("drawX", 0);
        l.drawY = intOr("drawY", 0);
        l.fills = StretchedMode.fillsWindow();
        GameShell.canvas.setSize(l.canvasW, l.canvasH); // StretchedMode.drawnWidth / mouse use the canvas's own size
        return l;
    }

    /** A click on the window where game pixel (gx, gy) is drawn must reach the game as (gx, gy). */
    private static void clicks(String name, Layout l) throws Exception {
        check(l.drawX >= 0 && l.drawY >= 0 && l.drawX + l.drawnW <= l.canvasW && l.drawY + l.drawnH <= l.canvasH,
            "the drawn game lies outside the canvas", name + ": drawn " + l.drawnW + "x" + l.drawnH + " at " + l.drawX + "," + l.drawY
                + " in canvas " + l.canvasW + "x" + l.canvasH);
        int[][] pixels = {{0, 0}, {l.realW / 2, l.realH / 2}, {l.realW - 1, l.realH - 1}, {l.realW - 30, 20}, {20, l.realH - 40}};
        for (int[] p : pixels) {
            // Window position of the centre of that game pixel, relative to the canvas.
            double sx = l.drawX + (p[0] + 0.5) * l.drawnW / (double) l.realW;
            double sy = l.drawY + (p[1] + 0.5) * l.drawnH / (double) l.realH;
            int gx = StretchedMode.mouseX((int) sx);
            int gy = StretchedMode.mouseY((int) sy);
            check(Math.abs(gx - p[0]) <= 1 && Math.abs(gy - p[1]) <= 1, "a click lands on another game pixel than the one drawn under it",
                name + ": game pixel " + p[0] + "," + p[1] + " drawn at canvas " + (int) sx + "," + (int) sy + " -> mouse " + gx + "," + gy);
        }
    }

    /** A GL that records the window clear and the blit of one present (framebuffer objects always complete). */
    private static final class RecordingGl implements StretchedMode.Gl {
        final List<String> events = new ArrayList<String>();
        int drawFb;
        int nextId = 1;
        int[] blit;
        boolean clearedWindowBeforeBlit;

        public void glBindFramebufferEXT(int target, int framebuffer) {
            if (target == 0x8D40 || target == 0x8CA9) {
                drawFb = framebuffer;
            }
        }

        public void glBlitFramebufferEXT(int a, int b, int c, int d, int e, int f, int g, int h, int mask, int filter) {
            if (drawFb == 0) {
                blit = new int[] {a, b, c, d, e, f, g, h};
                clearedWindowBeforeBlit = events.contains("clear0");
            }
            events.add("blit" + drawFb);
        }

        public void glDisable(int cap) {
        }

        public void glEnable(int cap) {
        }

        public void glGenFramebuffersEXT(int n, int[] ids, int offset) {
            ids[offset] = nextId++;
        }

        public void glGenRenderbuffersEXT(int n, int[] ids, int offset) {
            ids[offset] = nextId++;
        }

        public void glBindRenderbufferEXT(int target, int renderbuffer) {
        }

        public void glRenderbufferStorageEXT(int target, int format, int width, int height) {
        }

        public void glFramebufferRenderbufferEXT(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        }

        public int glCheckFramebufferStatusEXT(int target) {
            return 0x8CD5;
        }

        public void glDeleteFramebuffersEXT(int n, int[] ids, int offset) {
        }

        public void glDeleteRenderbuffersEXT(int n, int[] ids, int offset) {
        }

        public void glClearColor(float red, float green, float blue, float alpha) {
        }

        public void glClear(int mask) {
            events.add("clear" + drawFb);
        }
    }

    /** The GL-toolkit seam of the fix (absent on the old code: then the old layout is what the test sees). */
    private static void glToolkit(boolean on) {
        try {
            Method m = StretchedMode.class.getMethod("glToolkit", boolean.class);
            m.invoke(null, on);
        } catch (NoSuchMethodException absent) {
            // old code: no notion of the toolkit; every layout is the one the client used for OpenGL
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int intOr(String method, int fallback) throws Exception {
        try {
            return (Integer) StretchedMode.class.getMethod(method).invoke(null);
        } catch (NoSuchMethodException absent) {
            return fallback;
        }
    }

    private static void check(boolean ok, String kind, String detail) {
        assertions++;
        if (ok) {
            return;
        }
        Integer n = kinds.get(kind);
        kinds.put(kind, n == null ? 1 : n + 1);
        if (n == null || examples.size() < 14) {
            examples.add(detail);
        }
    }
}
