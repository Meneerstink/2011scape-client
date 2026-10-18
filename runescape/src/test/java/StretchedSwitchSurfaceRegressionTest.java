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

        // 1c. A2 round 8 review (nakijker FOUTEN 1): OpenGL with the player's Anti-aliasing on (x2 / x4 in Graphics Options). The
        //     window is made the way Static595.createWithHeap makes it (StretchedMode.glWindowSamples; the old code: plain only
        //     while stretching) and the toolkit is reported the way Static595 reports it. Whole class again: every window x
        //     Stretched OFF / ON x Keep aspect / Integer x scaling - the canvas never changes size or place at a switch and fills
        //     the window, the client never fits the window, clicks land; switching Stretched Mode on/off never needs another
        //     window (a toolkit rebuild = a new canvas); and the frame keeps the player's anti-aliasing: drawn into a multisampled
        //     offscreen buffer, resolved 1:1 (a multisampled buffer cannot be blitted scaled / moved or read with glReadPixels),
        //     then blitted onto its drawn rectangle.
        for (int aa : new int[] {2, 4}) {
            StretchedMode.configure(false, false, false, false, 0);
            int samplesOff = windowSamples(aa);
            StretchedMode.configure(true, false, false, false, 0);
            int samplesOn = windowSamples(aa);
            cases++;
            check(samplesOff == samplesOn, "AA: switching Stretched Mode on/off needs another OpenGL window (toolkit rebuild = new canvas)",
                "AA x" + aa + ": window samples Stretched OFF " + samplesOff + ", ON " + samplesOn);
            for (int[] w : windows) {
                for (boolean on : new boolean[] {false, true}) {
                    for (boolean[] o : options) {
                        if (!on && (o[0] || o[1])) {
                            continue;
                        }
                        for (int s : scalings) {
                            if (!on && s != 0) {
                                continue;
                            }
                            cases++;
                            String name = "AA x" + aa + " " + (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                                + (on ? " " + s + "%" : "") + " window " + w[0] + "x" + w[1];
                            StretchedMode.configure(on, o[0], false, o[1], s);
                            int windowSamples = windowSamples(aa);
                            glToolkitAa(windowSamples <= 1, windowSamples <= 1 ? aa : 0);
                            Layout fixed = layout(w, true, on, o[0], o[1], s);
                            clicks(name + " fixed", fixed);
                            Layout resizable = layout(w, false, on, o[0], o[1], s);
                            clicks(name + " resizable", resizable);
                            check(fixed.canvasW == resizable.canvasW && fixed.canvasH == resizable.canvasH,
                                "AA: canvas changes size at a fixed <-> resizable switch (black on the real screen until the next swap)",
                                name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " vs resizable " + resizable.canvasW + "x" + resizable.canvasH);
                            check(fixed.canvasX == resizable.canvasX && fixed.canvasY == resizable.canvasY,
                                "AA: canvas moves at a fixed <-> resizable switch",
                                name + ": fixed canvas at " + fixed.canvasX + "," + fixed.canvasY + " vs resizable " + resizable.canvasX + "," + resizable.canvasY);
                            check(fixed.canvasW == w[0] && fixed.canvasH == w[1] && fixed.canvasX == 0 && fixed.canvasY == 0,
                                "AA: the canvas does not fill the window's game area",
                                name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " at " + fixed.canvasX + "," + fixed.canvasY);
                            check(fixed.fills && resizable.fills, "AA: the client fits / restores the WINDOW at a switch",
                                name + ": fillsWindow fixed=" + fixed.fills + " resizable=" + resizable.fills);
                        }
                    }
                }
            }
            // The frame through the real glSync / glPresent / glReadBegin (GL seam that models multisampled renderbuffers).
            for (int[] w : new int[][] {{944, 581}, {1648, 989}, {1890, 989}, {765, 503}, {1216, 581}}) {
                for (boolean on : new boolean[] {false, true}) {
                    for (boolean[] o : options) {
                        if (!on && (o[0] || o[1])) {
                            continue;
                        }
                        for (boolean fixedMode : new boolean[] {true, false}) {
                            cases++;
                            String name = "AA x" + aa + " frame " + (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                                + (fixedMode ? " fixed" : " resizable") + " window " + w[0] + "x" + w[1];
                            StretchedMode.configure(on, o[0], false, o[1], 16);
                            int windowSamples = windowSamples(aa);
                            RecordingGl gl = new RecordingGl();
                            StretchedMode.useGlForTest(gl);
                            glToolkitAa(windowSamples <= 1, windowSamples <= 1 ? aa : 0);
                            Layout l = layout(w, fixedMode, on, o[0], o[1], 16);
                            java.awt.Dimension render = StretchedMode.renderSize(GameShell.canvas);
                            StretchedMode.glSync(true, render.width, render.height);
                            int drawSamples = gl.samplesOf(gl.drawFb, windowSamples);
                            check(drawSamples == aa, "AA: the frame is drawn without the player's anti-aliasing",
                                name + ": the game draws into " + (gl.drawFb == 0 ? "the window" : "framebuffer " + gl.drawFb) + " with " + drawSamples + " samples, wanted " + aa);
                            gl.events.clear();
                            gl.badBlits.clear();
                            StretchedMode.glPresent(false, l.canvasW, l.canvasH);
                            for (String bad : gl.badBlits) {
                                check(false, "AA: a multisampled buffer is blitted scaled / moved or into a multisampled window (INVALID_OPERATION = black)", name + ": " + bad);
                            }
                            boolean oneToOne = l.drawX == 0 && l.drawY == 0 && l.realW == l.canvasW && l.realH == l.canvasH && l.drawnW == l.canvasW && l.drawnH == l.canvasH;
                            if (gl.drawFb == 0 && oneToOne && windowSamples > 1) {
                                continue; // drawn straight into a multisampled window (the old path) - judged by the canvas rules above
                            }
                            int[] want = {0, 0, l.realW, l.realH, l.drawX, l.canvasH - l.drawY - l.drawnH, l.drawX + l.drawnW, l.canvasH - l.drawY};
                            check(gl.blit != null && java.util.Arrays.equals(gl.blit, want), "AA: the frame is not blitted onto its drawn rectangle",
                                name + ": blit " + (gl.blit == null ? "none" : java.util.Arrays.toString(gl.blit)) + ", want " + java.util.Arrays.toString(want));
                            if (gl.blit != null) {
                                check(gl.blitReadSamples <= 1, "AA: the window blit reads a multisampled buffer (must be resolved 1:1 first)",
                                    name + ": read framebuffer has " + gl.blitReadSamples + " samples");
                            }
                            // The minimap / sprites are copied from the frame (glCopyTexSubImage2D) while the toolkit's scissor test is on.
                            gl.scissorOn = true;
                            Boolean resolved = readBegin();
                            if (resolved != null) {
                                int readSamples = gl.samplesOf(gl.readFb, windowSamples);
                                check(readSamples <= 1, "AA: a screenshot (glReadPixels) reads a multisampled buffer",
                                    name + ": read framebuffer " + gl.readFb + " has " + readSamples + " samples");
                                check(gl.scissorOn, "AA: the toolkit's scissor test is left off after a read", name);
                                if (resolved) {
                                    readEnd();
                                    check(gl.drawFb != 0 && gl.samplesOf(gl.drawFb, windowSamples) == aa, "AA: after a screenshot the game no longer draws into its buffer",
                                        name + ": draw framebuffer " + gl.drawFb);
                                }
                            }
                        }
                    }
                }
            }
        }
        glToolkitAa(true, 0);
        StretchedMode.useGlForTest(new RecordingGl());

        // 2. A2 round 9 (Keurmeester A2 round 8 FAIL: DirectX switches fully black, 5 of 5 runs): DirectX gets the OpenGL rule. Whole
        //    class: every window x Stretched OFF / ON x Keep aspect / Integer x scaling x Increased performance - the canvas has the
        //    same size and place in fixed and resizable and fills the window, the client never fits the window, clicks land on the
        //    drawn pixel; the toolkit renders a frame that is not 1:1 at its real size and flip moves exactly that real-size corner
        //    of the back buffer onto the drawn rectangle (left/top/right/bottom), 1:1 without a filter, stretched linear / point.
        glToolkit(false);
        d3dToolkit(true);
        for (int[] w : windows) {
            for (boolean on : new boolean[] {false, true}) {
                for (boolean[] o : options) {
                    if (!on && (o[0] || o[1])) {
                        continue;
                    }
                    for (int s : scalings) {
                        for (boolean fast : new boolean[] {false, true}) {
                            if (!on && (s != 0 || fast)) {
                                continue;
                            }
                            cases++;
                            String name = "DirectX " + (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                                + (fast ? " +IncreasedPerformance" : "") + (on ? " " + s + "%" : "") + " window " + w[0] + "x" + w[1];
                            Layout[] both = new Layout[2];
                            for (int m = 0; m < 2; m++) {
                                boolean fixedMode = m == 0;
                                Layout l = layoutFast(w, fixedMode, on, o[0], o[1], s, fast);
                                both[m] = l;
                                String mode = name + (fixedMode ? " fixed" : " resizable");
                                clicks(mode, l);
                                java.awt.Dimension render = StretchedMode.renderSize(GameShell.canvas);
                                int[] plan = d3dPlan();
                                boolean oneToOne = l.drawX == 0 && l.drawY == 0 && l.realW == l.canvasW && l.realH == l.canvasH && l.drawnW == l.canvasW && l.drawnH == l.canvasH;
                                if (oneToOne) {
                                    check(plan == null, "DirectX moves a frame that is already 1:1 over the whole canvas", mode);
                                    continue;
                                }
                                check(render.width == l.realW && render.height == l.realH, "DirectX does not render the placed frame at its real size",
                                    mode + ": renders " + render.width + "x" + render.height + ", real " + l.realW + "x" + l.realH);
                                int filter = l.drawnW == l.realW && l.drawnH == l.realH ? 0 : fast ? 1 : 2;
                                int[] want = {l.realW, l.realH, l.drawX, l.drawY, l.drawX + l.drawnW, l.drawY + l.drawnH, filter};
                                check(plan != null && java.util.Arrays.equals(plan, want), "DirectX flip does not place the frame on its drawn rectangle",
                                    mode + ": plan " + (plan == null ? "none (the canvas is resized instead)" : java.util.Arrays.toString(plan)) + ", want " + java.util.Arrays.toString(want));
                            }
                            Layout fixed = both[0];
                            Layout resizable = both[1];
                            check(fixed.canvasW == resizable.canvasW && fixed.canvasH == resizable.canvasH,
                                "DirectX: canvas changes size at a fixed <-> resizable switch (black on the real screen until the first Present)",
                                name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " vs resizable " + resizable.canvasW + "x" + resizable.canvasH);
                            check(fixed.canvasX == resizable.canvasX && fixed.canvasY == resizable.canvasY,
                                "DirectX: canvas moves at a fixed <-> resizable switch",
                                name + ": fixed canvas at " + fixed.canvasX + "," + fixed.canvasY + " vs resizable " + resizable.canvasX + "," + resizable.canvasY);
                            check(fixed.canvasW == w[0] && fixed.canvasH == w[1] && fixed.canvasX == 0 && fixed.canvasY == 0,
                                "DirectX: the canvas does not fill the window's game area",
                                name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " at " + fixed.canvasX + "," + fixed.canvasY);
                            check(fixed.fills && resizable.fills,
                                "DirectX: the client fits / restores the WINDOW at a switch",
                                name + ": fillsWindow fixed=" + fixed.fills + " resizable=" + resizable.fills);
                        }
                    }
                }
            }
        }
        // A window smaller than the 765x503 fixed game cannot hold the real-size frame in its back buffer: the old path (the
        // client fits the window to the game), never a cut-off frame.
        for (int[] w : new int[][] {{700, 480}, {764, 503}, {765, 502}}) {
            cases++;
            Layout l = layout(w, true, false, false, false, 0);
            int[] plan = d3dPlan();
            check(plan == null && l.canvasW == 765 && l.canvasH == 503 && !l.fills,
                "DirectX: a window smaller than the fixed game cuts the frame off instead of fitting the window",
                "window " + w[0] + "x" + w[1] + ": canvas " + l.canvasW + "x" + l.canvasH + " fills=" + l.fills + " plan=" + java.util.Arrays.toString(plan));
        }
        d3dToolkit(false);

        // 3. Other toolkits (software / Java): unchanged - the canvas is the drawn size, placed by the game's margins.
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
        return layout(w, fixed, on, keepAspect, integer, scaling, false);
    }

    private static Layout layout(int[] w, boolean fixed, boolean on, boolean keepAspect, boolean integer, int scaling, boolean fast) throws Exception {
        StretchedMode.configure(on, keepAspect, fast, integer, scaling);
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

    /**
     * A GL that records the window clear and the blit of one present (framebuffer objects always complete). Models
     * multisampled renderbuffers: which renderbuffer is attached to which framebuffer, with how many samples; a blit out of a
     * multisampled framebuffer must be 1:1 into a single-sample one (else INVALID_OPERATION).
     */
    private static final class RecordingGl implements StretchedMode.Gl {
        final List<String> events = new ArrayList<String>();
        final List<String> badBlits = new ArrayList<String>();
        final Map<Integer, Integer> rbSamples = new java.util.HashMap<Integer, Integer>();
        final Map<Integer, Integer> fbColour = new java.util.HashMap<Integer, Integer>();
        int drawFb;
        int readFb;
        int boundRb;
        int nextId = 1;
        int[] blit;
        int blitReadSamples;
        boolean scissorOn;
        boolean clearedWindowBeforeBlit;

        /** Samples of framebuffer [fb]'s colour buffer; 0 = the window, which has [windowSamples]. */
        int samplesOf(int fb, int windowSamples) {
            if (fb == 0) {
                return windowSamples;
            }
            Integer rb = fbColour.get(fb);
            Integer n = rb == null ? null : rbSamples.get(rb);
            return n == null ? 0 : n;
        }

        public void glBindFramebufferEXT(int target, int framebuffer) {
            if (target == 0x8D40 || target == 0x8CA9) {
                drawFb = framebuffer;
            }
            if (target == 0x8D40 || target == 0x8CA8) {
                readFb = framebuffer;
            }
        }

        public void glBlitFramebufferEXT(int a, int b, int c, int d, int e, int f, int g, int h, int mask, int filter) {
            int rs = samplesOf(readFb, 0);
            int ds = samplesOf(drawFb, 0);
            if (scissorOn) {
                badBlits.add("blit from framebuffer " + readFb + " -> " + drawFb + " with the scissor test on (clipped: part of the frame stays old/black)");
            }
            if (rs > 1 && (a != e || b != f || c != g || d != h || ds > 1)) {
                badBlits.add("blit from framebuffer " + readFb + " (" + rs + " samples) " + a + "," + b + "-" + c + "," + d + " -> " + drawFb + " " + e + "," + f + "-" + g + "," + h);
            }
            if (drawFb == 0) {
                blit = new int[] {a, b, c, d, e, f, g, h};
                blitReadSamples = rs;
                clearedWindowBeforeBlit = events.contains("clear0");
            }
            events.add("blit" + drawFb);
        }

        public void glRenderbufferStorageMultisampleEXT(int target, int samples, int format, int width, int height) {
            rbSamples.put(boundRb, samples);
        }

        public void glDisable(int cap) {
            if (cap == 0x0C11) {
                scissorOn = false;
            }
        }

        public void glEnable(int cap) {
            if (cap == 0x0C11) {
                scissorOn = true;
            }
        }

        public void glGenFramebuffersEXT(int n, int[] ids, int offset) {
            ids[offset] = nextId++;
        }

        public void glGenRenderbuffersEXT(int n, int[] ids, int offset) {
            ids[offset] = nextId++;
        }

        public void glBindRenderbufferEXT(int target, int renderbuffer) {
            boundRb = renderbuffer;
        }

        public void glRenderbufferStorageEXT(int target, int format, int width, int height) {
            rbSamples.put(boundRb, 0);
        }

        public void glFramebufferRenderbufferEXT(int target, int attachment, int renderbufferTarget, int renderbuffer) {
            if (attachment == 0x8CE0) {
                fbColour.put(drawFb, renderbuffer);
            }
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

    /** Layout with Increased performance (the point filter of a stretched DirectX frame). */
    private static Layout layoutFast(int[] w, boolean fixed, boolean on, boolean keepAspect, boolean integer, int scaling, boolean fast) throws Exception {
        return layout(w, fixed, on, keepAspect, integer, scaling, fast);
    }

    /** Static595's report of the DirectX toolkit (absent on the old code: DirectX = "not OpenGL", the old canvas sizes). */
    private static void d3dToolkit(boolean on) {
        try {
            StretchedMode.class.getMethod("d3dToolkit", boolean.class).invoke(null, on);
        } catch (NoSuchMethodException absent) {
            // old code
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** StretchedMode.d3dPresentPlan (null when absent: the old code never moved a DirectX frame). */
    private static int[] d3dPlan() {
        try {
            return (int[]) StretchedMode.class.getMethod("d3dPresentPlan").invoke(null);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    /** The window samples Static595.createWithHeap asks for (old code: plain only while stretching - glPlainWindow). */
    private static int windowSamples(int playerSamples) {
        try {
            return (Integer) StretchedMode.class.getMethod("glWindowSamples", int.class).invoke(null, playerSamples);
        } catch (NoSuchMethodException absent) {
            return StretchedMode.glPlainWindow() ? 0 : playerSamples;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Static595's report of the OpenGL toolkit: plain window or not, offscreen anti-aliasing samples (old code: no samples). */
    private static void glToolkitAa(boolean plain, int samples) {
        try {
            StretchedMode.class.getMethod("glToolkit", boolean.class, boolean.class, int.class).invoke(null, true, plain, samples);
        } catch (NoSuchMethodException absent) {
            try {
                StretchedMode.class.getMethod("glToolkit", boolean.class, boolean.class).invoke(null, true, plain);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** StretchedMode.glReadBegin (null when absent: the old code never had a multisampled offscreen frame to resolve). */
    private static Boolean readBegin() {
        try {
            return (Boolean) StretchedMode.class.getMethod("glReadBegin", boolean.class).invoke(null, true);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void readEnd() {
        try {
            StretchedMode.class.getMethod("glReadEnd").invoke(null);
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
